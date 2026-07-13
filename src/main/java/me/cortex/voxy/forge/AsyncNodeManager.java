package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindBufferRange;
import static org.lwjgl.opengl.GL30C.glUniform1ui;
import static org.lwjgl.opengl.GL42C.GL_UNIFORM_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;

final class AsyncNodeManager {
    private static final int GEOMETRY_UPLOAD_LIMIT_PER_RUN = 300;
    private static final long GEOMETRY_UPLOAD_BATCH_LIMIT_BYTES = 1_000L << 10;
    private static final long GEOMETRY_UPLOAD_HEADROOM_BYTES = 50_000_000L;
    private static final int NODE_CLEANER_OUTPUT_COUNT = 256;

    final int maxNodeCount;
    private final BasicAsyncGeometryManager geometryManager;
    private final BasicSectionGeometryData geometryData;
    private final SectionUpdateRouter router;
    private final NodeManager nodeManager;
    private final GeometryCache geometryCache = new GeometryCache(1L << 32);
    private final ConcurrentLinkedDeque<MemoryBuffer> requestBatchQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<WorldSection> childUpdateQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<BuiltSection> geometryUpdateQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<MemoryBuffer> removeBatchQueue = new ConcurrentLinkedDeque<>();
    private final LongOpenHashSet topLevelNodeAdds = new LongOpenHashSet();
    private final LongOpenHashSet topLevelNodeRemoves = new LongOpenHashSet();
    private final Object topLevelNodeLock = new Object();
    private final IntOpenHashSet topLevelNodeIdChanges = new IntOpenHashSet();
    private final IntOpenHashSet cleanerIdResetClear = new IntOpenHashSet();
    private final AtomicInteger workCounter = new AtomicInteger();
    private final AtomicReference<SyncResults> results = new AtomicReference<>();
    private final AtomicReference<SyncResults> resultCache1 = new AtomicReference<>(new SyncResults());
    private final AtomicReference<SyncResults> resultCache2 = new AtomicReference<>(new SyncResults());
    private final Thread workerThread;
    private volatile boolean running = true;
    private volatile Throwable uncaughtException;
    private boolean needsWaitForSync;
    private int multiMemcpyProgramId;
    private int scatterProgramId;
    private int currentMaxNodeId;
    private long usedGeometryBytes;
    private IntConsumer topLevelNodeAddCallback;
    private IntConsumer topLevelNodeRemoveCallback;

    AsyncNodeManager(
            int maxNodeCount,
            BasicSectionGeometryData geometryData,
            RenderGenerationService renderGenerationService) {
        this.maxNodeCount = maxNodeCount;
        this.geometryData = geometryData;
        this.geometryManager = new BasicAsyncGeometryManager(
                geometryData.getMaxSectionCount(),
                geometryData.getGeometryCapacityBytes());
        this.router = new SectionUpdateRouter();
        this.router.setCallbacks(pos -> {
            BuiltSection cachedGeometry = this.geometryCache.remove(pos);
            if (cachedGeometry != null) {
                this.submitGeometryResult(cachedGeometry);
            } else {
                renderGenerationService.enqueueTask(pos);
            }
        }, renderGenerationService::enqueueTask, this::submitChildChange);
        renderGenerationService.setResultConsumer(this::submitGeometryResult);
        this.nodeManager = new NodeManager(maxNodeCount, this.geometryManager, this.router);
        this.nodeManager.setClear(new NodeManager.Cleaner() {
            @Override
            public void alloc(int id) {
                AsyncNodeManager.this.cleanerIdResetClear.remove(id);
                AsyncNodeManager.this.cleanerIdResetClear.add(id | (1 << 31));
            }

            @Override
            public void move(int from, int to) {
                // Original Voxy intentionally leaves cleaner move as a no-op in AsyncNodeManager.
            }

            @Override
            public void free(int id) {
                AsyncNodeManager.this.cleanerIdResetClear.remove(id | (1 << 31));
                AsyncNodeManager.this.cleanerIdResetClear.add(id);
            }
        });
        this.nodeManager.setTLNCallbacks(id -> {
            if (!this.topLevelNodeIdChanges.remove(id)) {
                if (!this.topLevelNodeIdChanges.add(id | (1 << 31))) {
                    throw new IllegalStateException();
                }
            }
        }, id -> {
            if (!this.topLevelNodeIdChanges.remove(id | (1 << 31))) {
                if (!this.topLevelNodeIdChanges.add(id)) {
                    throw new IllegalStateException();
                }
            }
        });
        this.workerThread = new Thread(this::workerMain, "Original Voxy Async Node Geometry Sync");
        this.workerThread.setUncaughtExceptionHandler((thread, throwable) -> {
            this.uncaughtException = throwable == null ? new RuntimeException("async-node-geometry-sync-null-exception") : throwable;
            this.running = false;
        });
    }

    void start() {
        this.workerThread.start();
    }

    void setTLNAddRemoveCallbacks(IntConsumer add, IntConsumer remove) {
        this.topLevelNodeAddCallback = add;
        this.topLevelNodeRemoveCallback = remove;
    }

    void submitGeometryResult(BuiltSection section) {
        if (!this.running) {
            section.free();
            return;
        }
        this.geometryUpdateQueue.add(section);
        this.addWork();
    }

    void submitRequestBatch(MemoryBuffer batch) {
        if (!this.running) {
            batch.free();
            return;
        }
        this.requestBatchQueue.add(batch);
        this.addWork();
    }

    void submitRemoveBatch(MemoryBuffer batch) {
        if (!this.running) {
            batch.free();
            return;
        }
        this.removeBatchQueue.add(batch);
        this.addWork();
    }

    private void submitChildChange(WorldSection section) {
        if (!this.running) {
            return;
        }
        section.acquire();
        this.childUpdateQueue.add(section);
        this.addWork();
    }

    void addTopLevel(long sectionPosition) {
        if (!this.running) {
            return;
        }
        int state = 0;
        synchronized (this.topLevelNodeLock) {
            if (!this.topLevelNodeRemoves.remove(sectionPosition)) {
                state += this.topLevelNodeAdds.add(sectionPosition) ? 1 : 0;
            } else {
                state -= 1;
            }
        }
        this.addTopLevelWork(state);
    }

    void removeTopLevel(long sectionPosition) {
        if (!this.running) {
            return;
        }
        int state = 0;
        synchronized (this.topLevelNodeLock) {
            if (!this.topLevelNodeAdds.remove(sectionPosition)) {
                state += this.topLevelNodeRemoves.add(sectionPosition) ? 1 : 0;
            } else {
                state -= 1;
            }
        }
        this.addTopLevelWork(state);
    }

    void worldEvent(WorldSection section, int flags, int neighborMask) {
        this.geometryCache.clear(section.key);
        this.router.forwardEvent(section, flags);
        if (neighborMask != 0) {
            if ((neighborMask & 0b000001) != 0) {
                this.router.triggerRemesh(WorldEngine.getWorldSectionId(section.lvl, section.x, section.y - 1, section.z));
            }
            if ((neighborMask & 0b000010) != 0) {
                this.router.triggerRemesh(WorldEngine.getWorldSectionId(section.lvl, section.x, section.y + 1, section.z));
            }
            if ((neighborMask & 0b000100) != 0) {
                this.router.triggerRemesh(WorldEngine.getWorldSectionId(section.lvl, section.x - 1, section.y, section.z));
            }
            if ((neighborMask & 0b001000) != 0) {
                this.router.triggerRemesh(WorldEngine.getWorldSectionId(section.lvl, section.x + 1, section.y, section.z));
            }
            if ((neighborMask & 0b010000) != 0) {
                this.router.triggerRemesh(WorldEngine.getWorldSectionId(section.lvl, section.x, section.y, section.z - 1));
            }
            if ((neighborMask & 0b100000) != 0) {
                this.router.triggerRemesh(WorldEngine.getWorldSectionId(section.lvl, section.x, section.y, section.z + 1));
            }
        }
    }

    void tick(GlBuffer nodeBuffer, NodeCleaner nodeCleaner) {
        requireRenderThread("tick original async node geometry sync");
        if (this.uncaughtException != null) {
            Throwable throwable = this.uncaughtException;
            this.recordFailure("async-node-geometry-sync-" + throwable.getClass().getSimpleName() + ":" + throwable.getMessage());
            return;
        }
        this.ensurePrograms();
        SyncResults sync = this.results.getAndSet(null);
        if (sync == null) {
            return;
        }
        this.applyTopLevelNodeDeltas(sync);
        this.geometryData.setSectionCount(sync.geometrySectionCount);
        this.uploadGeometryCopies(sync);
        this.scatterMetadataWrites(sync, nodeBuffer);
        if (nodeCleaner != null) {
            nodeCleaner.updateIds(sync.cleanerOperations);
        }
        this.currentMaxNodeId = sync.currentMaxNodeId;
        this.usedGeometryBytes = sync.usedGeometry;
        this.returnResultObject(sync);
    }

    private void applyTopLevelNodeDeltas(SyncResults sync) {
        if (sync.topLevelNodeIdChanges.isEmpty()) {
            return;
        }
        var iter = sync.topLevelNodeIdChanges.intIterator();
        while (iter.hasNext()) {
            int value = iter.nextInt();
            if ((value & (1 << 31)) != 0) {
                if (this.topLevelNodeAddCallback != null) {
                    this.topLevelNodeAddCallback.accept(value & (-1 >>> 1));
                }
            } else if (this.topLevelNodeRemoveCallback != null) {
                this.topLevelNodeRemoveCallback.accept(value);
            }
        }
    }

    int getCurrentMaxNodeId() {
        return this.currentMaxNodeId;
    }

    long getUsedGeometryCapacity() {
        return this.usedGeometryBytes;
    }

    long getGeometryCapacity() {
        return this.geometryData.getGeometryCapacityBytes();
    }

    void addDebug(List<String> debug) {
        debug.add("UC/GC,#N: "
                + this.getUsedGeometryCapacity() / (1 << 20)
                + "/" + this.getGeometryCapacity() / (1 << 20)
                + "," + this.geometryData.getSectionCount());
    }

    boolean hasWork() {
        if (this.workCounter.get() != 0 || this.results.get() != null) {
            return true;
        }
        synchronized (this.topLevelNodeLock) {
            if (!this.topLevelNodeAdds.isEmpty() || !this.topLevelNodeRemoves.isEmpty()) {
                return true;
            }
        }
        return !this.requestBatchQueue.isEmpty()
                || !this.childUpdateQueue.isEmpty()
                || !this.geometryUpdateQueue.isEmpty()
                || !this.removeBatchQueue.isEmpty();
    }

    void stop() {
        requireRenderThread("stop original async node geometry sync");
        this.running = false;
        LockSupport.unpark(this.workerThread);
        try {
            while (this.workerThread.isAlive()) {
                LockSupport.unpark(this.workerThread);
                this.workerThread.join(1000L);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.recordFailure("async-node-geometry-sync-stop-interrupted");
        }
        while (true) {
            BuiltSection section = this.geometryUpdateQueue.poll();
            if (section == null) {
                break;
            }
            section.free();
        }
        while (true) {
            WorldSection section = this.childUpdateQueue.poll();
            if (section == null) {
                break;
            }
            section.release();
        }
        while (true) {
            MemoryBuffer batch = this.requestBatchQueue.poll();
            if (batch == null) {
                break;
            }
            batch.free();
        }
        while (true) {
            MemoryBuffer batch = this.removeBatchQueue.poll();
            if (batch == null) {
                break;
            }
            batch.free();
        }
        this.freeSyncResult(this.results.getAndSet(null));
        this.freeSyncResult(this.resultCache1.getAndSet(null));
        this.freeSyncResult(this.resultCache2.getAndSet(null));
        if (this.multiMemcpyProgramId != 0) {
            glDeleteProgram(this.multiMemcpyProgramId);
            this.multiMemcpyProgramId = 0;
        }
        if (this.scatterProgramId != 0) {
            glDeleteProgram(this.scatterProgramId);
            this.scatterProgramId = 0;
        }
        this.geometryManager.clear();
        this.geometryCache.free();
        synchronized (this.topLevelNodeLock) {
            this.topLevelNodeAdds.clear();
            this.topLevelNodeRemoves.clear();
        }
    }

    private void workerMain() {
        try {
            while (this.running) {
                this.workerRun();
            }
        } catch (Throwable throwable) {
            this.uncaughtException = throwable;
            this.running = false;
        }
    }

    private void workerRun() {
        if (this.workCounter.get() <= 0) {
            LockSupport.park();
            if (this.workCounter.get() <= 0 || !this.running) {
                return;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("async-node-geometry-sync-interrupted", e);
            }
        }
        int workDone = 0;
        LongOpenHashSet adds = null;
        LongOpenHashSet removes = null;
        synchronized (this.topLevelNodeLock) {
            if (!this.topLevelNodeAdds.isEmpty()) {
                adds = new LongOpenHashSet(this.topLevelNodeAdds);
                this.topLevelNodeAdds.clear();
            }
            if (!this.topLevelNodeRemoves.isEmpty()) {
                removes = new LongOpenHashSet(this.topLevelNodeRemoves);
                this.topLevelNodeRemoves.clear();
            }
        }
        if (removes != null) {
            var iter = removes.longIterator();
            while (iter.hasNext()) {
                this.nodeManager.removeTopLevelNode(iter.nextLong());
                workDone++;
            }
        }
        if (adds != null) {
            var iter = adds.longIterator();
            while (iter.hasNext()) {
                long position = iter.nextLong();
                this.nodeManager.insertTopLevelNode(position);
                workDone++;
            }
        }
        while (true) {
            WorldSection section = this.childUpdateQueue.poll();
            if (section == null) {
                break;
            }
            workDone++;
            this.nodeManager.processChildChange(section.key, section.getNonEmptyChildren());
            section.release();
        }
        long estimatedGeometryUploadAmount = 0L;
        for (int limit = 0;
             limit < GEOMETRY_UPLOAD_LIMIT_PER_RUN
                     && this.hasGeometryCapacityHeadroom()
                     && estimatedGeometryUploadAmount < GEOMETRY_UPLOAD_BATCH_LIMIT_BYTES;
             limit++) {
            BuiltSection section = this.geometryUpdateQueue.poll();
            if (section == null) {
                break;
            }
            workDone++;
            if (section.geometryBuffer != null) {
                estimatedGeometryUploadAmount += section.geometryBuffer.size;
            }
            this.processGeometryResult(section);
        }
        while (true) {
            MemoryBuffer batch = this.requestBatchQueue.poll();
            if (batch == null) {
                break;
            }
            workDone++;
            long ptr = batch.address;
            int count = MemoryUtil.memGetInt(ptr);
            ptr += 8L;
            if (batch.size < count * 8L + 8L) {
                batch.free();
                throw new IllegalStateException("async-node-geometry-sync-request-batch-too-small");
            }
            for (int i = 0; i < count; i++) {
                long pos = ((long) MemoryUtil.memGetInt(ptr)) << 32;
                ptr += Integer.BYTES;
                pos |= Integer.toUnsignedLong(MemoryUtil.memGetInt(ptr));
                ptr += Integer.BYTES;
                this.nodeManager.processRequest(pos);
            }
            batch.free();
        }
        while (true) {
            MemoryBuffer batch = this.removeBatchQueue.poll();
            if (batch == null) {
                break;
            }
            workDone++;
            long ptr = batch.address;
            int zeroCount = 0;
            for (int i = 0; i < NODE_CLEANER_OUTPUT_COUNT; i++) {
                long pos = ((long) MemoryUtil.memGetInt(ptr)) << 32;
                ptr += Integer.BYTES;
                pos |= Integer.toUnsignedLong(MemoryUtil.memGetInt(ptr));
                ptr += Integer.BYTES;
                if (pos == -1L) {
                    continue;
                }
                if (pos == 0L && zeroCount++ > 0) {
                    VoxyForge.LOGGER.error("Remove node pos is 0 {} times, this is really bad", zeroCount);
                    continue;
                }
                this.nodeManager.removeNodeGeometry(pos);
            }
            batch.free();
        }
        if (this.workCounter.addAndGet(-workDone) < 0) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("async-node-geometry-sync-negative-work-counter-interrupted", e);
            }
            if (this.workCounter.get() < 0) {
                VoxyForge.LOGGER.error("Work counter less than zero, hope it fixes itself...");
            }
        }
        if (workDone == 0) {
            return;
        }
        this.publishSyncResults();
    }

    private boolean hasGeometryCapacityHeadroom() {
        //Direct reads: this runs in the worker's 300-iteration upload loop condition, where the
        // previous synchronized createStatusSnapshot call allocated a stats record per iteration
        // (original AsyncNodeManager reads a cached capacity field here).
        return this.geometryManager.geometryCapacityBytes()
                - this.geometryManager.getGeometryUsedBytes() > GEOMETRY_UPLOAD_HEADROOM_BYTES;
    }

    private void processGeometryResult(BuiltSection section) {
        try {
            this.nodeManager.processGeometryResult(section);
        } catch (RuntimeException e) {
            this.recordFailure("async-node-geometry-sync-" + e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }

    private void publishSyncResults() {
        if (this.needsWaitForSync) {
            while (this.results.get() != null && this.running) {
                try {
                    Thread.sleep(10L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("async-node-geometry-sync-wait-interrupted", e);
                }
            }
        }
        SyncResults previous = this.results.getAndSet(null);
        SyncResults sync = previous == null ? this.getMakeResultObject() : previous;
        if (previous == null) {
            this.needsWaitForSync = false;
        }
        this.mergeGeometryEvents(sync, previous != null);
        sync.geometrySectionCount = this.geometryManager.getSectionCount();
        sync.usedGeometry = this.geometryManager.getGeometryUsedBytes();
        sync.currentMaxNodeId = this.nodeManager.getCurrentMaxNodeId();
        this.needsWaitForSync |= sync.geometryUpload.currentElemCopyAmount * Long.BYTES > 2L << 20;
        this.needsWaitForSync |= sync.cleanerOperations.size() > 1024;
        this.needsWaitForSync |= sync.scatterWriteLocationMap.size() > 4096;
        this.needsWaitForSync |= sync.topLevelNodeIdChanges.size() > 10;
        if (!this.results.compareAndSet(null, sync)) {
            throw new IllegalStateException("async-node-geometry-sync-result-publish-race");
        }
    }

    private SyncResults getMakeResultObject() {
        SyncResults sync = this.resultCache1.getAndSet(null);
        if (sync == null) {
            sync = this.resultCache2.getAndSet(null);
        }
        if (sync == null) {
            throw new IllegalStateException("There should always be an object in the result set cache pair");
        }
        sync.reset();
        return sync;
    }

    private void mergeGeometryEvents(SyncResults sync, boolean mergeWithPrevious) {
        if (!this.topLevelNodeIdChanges.isEmpty()) {
            var iter = this.topLevelNodeIdChanges.intIterator();
            while (iter.hasNext()) {
                int value = iter.nextInt();
                if (!sync.topLevelNodeIdChanges.remove(value ^ (1 << 31))) {
                    sync.topLevelNodeIdChanges.add(value);
                }
            }
            this.topLevelNodeIdChanges.clear();
        }
        if (!this.cleanerIdResetClear.isEmpty()) {
            var iter = this.cleanerIdResetClear.intIterator();
            while (iter.hasNext()) {
                int value = iter.nextInt();
                sync.cleanerOperations.remove(value ^ (1 << 31));
                sync.cleanerOperations.add(value);
            }
            this.cleanerIdResetClear.clear();
        }
        IntOpenHashSet removals = this.geometryManager.getHeapRemovals();
        if (!removals.isEmpty() && mergeWithPrevious) {
            var iter = removals.intIterator();
            while (iter.hasNext()) {
                sync.geometryUpload.remove(iter.nextInt());
            }
            removals.clear();
        } else if (!removals.isEmpty()) {
            removals.clear();
        }
        Int2ObjectOpenHashMap<MemoryBuffer> uploads = this.geometryManager.getUploads();
        if (!uploads.isEmpty()) {
            var iter = uploads.int2ObjectEntrySet().fastIterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                sync.geometryUpload.upload(entry.getIntKey(), entry.getValue());
                entry.getValue().free();
            }
            uploads.clear();
        }
        IntOpenHashSet updates = this.geometryManager.getUpdateIds();
        if (!updates.isEmpty()) {
            var iter = updates.intIterator();
            while (iter.hasNext()) {
                int sectionId = iter.nextInt();
                int scatterAddress = (sectionId << 1) | (1 << 31);
                long ptrA = sync.getScatterWritePtr(scatterAddress, 1);
                long ptrB = sync.getScatterWritePtr(scatterAddress + 1, 0);
                this.geometryManager.writeMetadataSplit(sectionId, ptrA, ptrB);
            }
            updates.clear();
        }
        IntOpenHashSet nodeUpdates = this.nodeManager.getNodeUpdates();
        if (!nodeUpdates.isEmpty()) {
            var iter = nodeUpdates.intIterator();
            while (iter.hasNext()) {
                int nodeId = iter.nextInt();
                long ptr = sync.getScatterWritePtr(nodeId, 0);
                this.nodeManager.writeNode(nodeId, ptr);
            }
            nodeUpdates.clear();
        }
    }

    private void uploadGeometryCopies(SyncResults sync) {
        ComputeMemoryCopy upload = sync.geometryUpload;
        if (upload.dataUploadPoints.isEmpty()) {
            return;
        }
        this.geometryData.ensureAccessable(upload.maxElementAccess);
        int copies = upload.dataUploadPoints.size();
        int upCopies = UploadStream.alignUpAlloc(copies * 16);
        int scratchSize = (int) upload.arena.getSize() * Long.BYTES;
        int upScratchSize = UploadStream.alignUpAlloc(scratchSize);
        long ptr = UploadStream.instance().rawUploadAddress(upScratchSize + upCopies);
        MemoryUtil.memCopy(
                upload.scratchHeaderBuffer.address,
                UploadStream.instance().getBaseAddress() + ptr,
                copies * 16L);
        MemoryUtil.memCopy(
                upload.scratchDataBuffer.address,
                UploadStream.instance().getBaseAddress() + ptr + upCopies,
                scratchSize);
        UploadStream.instance().commit();

        glUseProgram(this.multiMemcpyProgramId);
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, UploadStream.instance().getRawBufferId(), ptr, upCopies);
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 1, UploadStream.instance().getRawBufferId(), ptr + upCopies, upScratchSize);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.geometryData.getGeometryBuffer());
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute(copies, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(0);

    }

    private void scatterMetadataWrites(SyncResults sync, GlBuffer nodeBuffer) {
        if (sync.scatterWriteLocationMap.isEmpty()) {
            return;
        }
        int count = sync.scatterWriteLocationMap.size();
        int chunks = (count + 3) / 4;
        int streamSize = chunks * 80;
        long ptr = UploadStream.instance().rawUploadAddress(streamSize);
        MemoryUtil.memCopy(
                sync.scatterWriteBuffer.address,
                UploadStream.instance().getBaseAddress() + ptr,
                streamSize);
        UploadStream.instance().commit();

        glUseProgram(this.scatterProgramId);
        glBindBufferRange(
                GL_SHADER_STORAGE_BUFFER,
                0,
                UploadStream.instance().getRawBufferId(),
                ptr,
                UploadStream.alignUpAlloc(streamSize));
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, nodeBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.geometryData.getMetadataBuffer());
        glUniform1ui(0, count);
        glMemoryBarrier(GL_UNIFORM_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute((count + 127) / 128, 1, 1);
        glMemoryBarrier(GL_UNIFORM_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(0);

    }

    private void returnResultObject(SyncResults sync) {
        if (!this.resultCache1.compareAndSet(null, sync) && !this.resultCache2.compareAndSet(null, sync)) {
            sync.free();
            this.recordFailure("async-node-geometry-sync-result-cache-full");
        }
    }

    private void ensurePrograms() {
        if (this.multiMemcpyProgramId == 0) {
            this.multiMemcpyProgramId = compileComputeProgram(withDefines(
                    ShaderLoader.parse("voxy:util/memcpy.comp"),
                    "INPUT_HEADER_BUFFER_BINDING", 0,
                    "INPUT_DATA_BUFFER_BINDING", 1,
                    "OUTPUT_BUFFER_BINDING", 2));
        }
        if (this.scatterProgramId == 0) {
            this.scatterProgramId = compileComputeProgram(withDefines(
                    ShaderLoader.parse("voxy:util/scatter.comp"),
                    "INPUT_BUFFER_BINDING", 0,
                    "OUTPUT_BUFFER1_BINDING", 1,
                    "OUTPUT_BUFFER2_BINDING", 2));
        }
    }

    private void addWork() {
        if (this.workCounter.getAndIncrement() == 0) {
            LockSupport.unpark(this.workerThread);
        }
    }

    private void addTopLevelWork(int delta) {
        if (delta != 0 && this.workCounter.getAndAdd(delta) == 0) {
            LockSupport.unpark(this.workerThread);
        }
    }

    private void recordFailure(String reason) {
        String normalized = reason == null || reason.isBlank() ? "unspecified" : reason;
        VoxyForge.LOGGER.error("Original async node geometry sync failure: {}", normalized);
    }

    private void freeSyncResult(SyncResults sync) {
        if (sync != null) {
            sync.free();
        }
    }

    private static String withDefines(String source, Object... defines) {
        StringBuilder builder = new StringBuilder("#version 460 core\n");
        for (int i = 0; i < defines.length; i += 2) {
            builder.append("#define ").append(defines[i]).append(' ').append(defines[i + 1]).append('\n');
        }
        builder.append(source.substring(source.indexOf('\n') + 1));
        return builder.toString();
    }

    private static int compileComputeProgram(String source) {
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(shader, ForgeOriginalVoxyShaderCompiler.prepareSource(source));
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Original async node compute shader compile failed: " + log);
        }
        int program = glCreateProgram();
        glAttachShader(program, shader);
        glLinkProgram(program);
        glDeleteShader(shader);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Original async node compute shader link failed: " + log);
        }
        return program;
    }

    private static void requireRenderThread(String action) {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Cannot " + action + " outside the render thread");
        }
    }

    private static final class SyncResults {
        private int currentMaxNodeId;
        private int geometrySectionCount;
        private long usedGeometry;
        private final IntOpenHashSet topLevelNodeIdChanges = new IntOpenHashSet();
        private final IntOpenHashSet cleanerOperations = new IntOpenHashSet();
        private final ComputeMemoryCopy geometryUpload = new ComputeMemoryCopy();
        private MemoryBuffer scatterWriteBuffer = new MemoryBuffer(8192 * 2L);
        private final Int2IntOpenHashMap scatterWriteLocationMap = new Int2IntOpenHashMap(1024);

        private SyncResults() {
            this.scatterWriteLocationMap.defaultReturnValue(-1);
        }

        void reset() {
            this.topLevelNodeIdChanges.clear();
            this.cleanerOperations.clear();
            this.scatterWriteLocationMap.clear();
            this.currentMaxNodeId = 0;
            this.geometrySectionCount = 0;
            this.usedGeometry = 0L;
            this.geometryUpload.reset();
        }

        long getScatterWritePtr(int location, int ensureExtra) {
            int loc = this.scatterWriteLocationMap.get(location);
            if (loc == -1) {
                this.ensureScatterBufferCapacity(1 + ensureExtra);
                int baseId = this.scatterWriteLocationMap.size();
                int chunkBase = (baseId / 4) * 5;
                int innerId = baseId & 3;
                MemoryUtil.memPutInt(this.scatterWriteBuffer.address + (chunkBase * 16L) + (innerId * 4L), location);
                int writeLocation = chunkBase + 1 + innerId;
                this.scatterWriteLocationMap.put(location, writeLocation);
                return this.scatterWriteBuffer.address + (writeLocation * 16L);
            }
            return this.scatterWriteBuffer.address + (16L * loc);
        }

        void free() {
            this.geometryUpload.free();
            this.scatterWriteBuffer.free();
        }

        private void ensureScatterBufferCapacity(int extra) {
            int requiredChunks = ((this.scatterWriteLocationMap.size() + extra) + 3) / 4;
            long requiredSize = requiredChunks * 5L * 16L;
            if (this.scatterWriteBuffer.size <= requiredSize) {
                long newSize = (long) ((this.scatterWriteBuffer.size * 1.5D) + extra * 80L);
                newSize = ((newSize + 79L) / 80L) * 80L;
                MemoryBuffer newBuffer = new MemoryBuffer(newSize);
                this.scatterWriteBuffer.cpyTo(newBuffer.address);
                this.scatterWriteBuffer.free();
                this.scatterWriteBuffer = newBuffer;
            }
        }
    }

    private static final class ComputeMemoryCopy {
        private int currentElemCopyAmount;
        private int maxElementAccess;
        private MemoryBuffer scratchHeaderBuffer = new MemoryBuffer(1 << 16);
        private MemoryBuffer scratchDataBuffer = new MemoryBuffer(1 << 20);
        private final AllocationArena arena = new AllocationArena();
        private final Int2IntOpenHashMap dataUploadPoints = new Int2IntOpenHashMap();

        private ComputeMemoryCopy() {
            this.dataUploadPoints.defaultReturnValue(-1);
        }

        void remove(int point) {
            int header = this.dataUploadPoints.remove(point);
            if (header == -1) {
                return;
            }
            int size = MemoryUtil.memGetInt(this.scratchHeaderBuffer.address + header * 16L + 8L);
            this.currentElemCopyAmount -= size;
            if (this.arena.free(MemoryUtil.memGetInt(this.scratchHeaderBuffer.address + header * 16L)) != size) {
                throw new IllegalStateException("Freed memory not same size as expected");
            }
            if (MemoryUtil.memGetInt(this.scratchHeaderBuffer.address + header * 16L + 4L) != point) {
                throw new IllegalStateException("Destination not the same as point");
            }
            if (header == this.dataUploadPoints.size()) {
                long address = this.scratchHeaderBuffer.address + header * 16L;
                MemoryUtil.memPutLong(address, 0L);
                MemoryUtil.memPutLong(address + 8L, 0L);
                return;
            }
            int endingPoint = MemoryUtil.memGetInt(this.scratchHeaderBuffer.address + this.dataUploadPoints.size() * 16L + 4L);
            if (this.dataUploadPoints.get(endingPoint) != this.dataUploadPoints.size()) {
                throw new IllegalStateException("ending header not pointing at end point");
            }
            long source = this.scratchHeaderBuffer.address + this.dataUploadPoints.size() * 16L;
            long target = this.scratchHeaderBuffer.address + header * 16L;
            MemoryUtil.memPutLong(target, MemoryUtil.memGetLong(source));
            MemoryUtil.memPutLong(source, 0L);
            MemoryUtil.memPutLong(target + 8L, MemoryUtil.memGetLong(source + 8L));
            MemoryUtil.memPutLong(source + 8L, 0L);
            this.dataUploadPoints.put(endingPoint, header);
        }

        void upload(int point, MemoryBuffer data) {
            if (data.size % Long.BYTES != 0) {
                throw new IllegalStateException("Data must be of size multiple 8");
            }
            int elemSize = (int) (data.size / Long.BYTES);
            this.maxElementAccess = Math.max(this.maxElementAccess, point + elemSize);
            int header = this.dataUploadPoints.get(point);
            if (header != -1) {
                long headerPtr = this.scratchHeaderBuffer.address + header * 16L;
                if (MemoryUtil.memGetInt(headerPtr + 4L) != point) {
                    throw new IllegalStateException("Existing destination not the point");
                }
                int previousSize = MemoryUtil.memGetInt(headerPtr + 8L);
                if (previousSize == elemSize) {
                    data.cpyTo(this.scratchDataBuffer.address + MemoryUtil.memGetInt(headerPtr) * 8L);
                } else {
                    if (this.arena.free(MemoryUtil.memGetInt(headerPtr)) != previousSize) {
                        throw new IllegalStateException("Freed allocation not size as expected");
                    }
                    this.currentElemCopyAmount -= previousSize;
                    this.currentElemCopyAmount += elemSize;
                    int alloc = this.allocScratchDataPos(elemSize);
                    data.cpyTo(this.scratchDataBuffer.address + alloc * 8L);
                    MemoryUtil.memPutInt(headerPtr, alloc);
                    MemoryUtil.memPutInt(headerPtr + 8L, elemSize);
                }
            } else {
                header = this.dataUploadPoints.size();
                this.dataUploadPoints.put(point, header);
                if (this.scratchHeaderBuffer.size <= header * 16L) {
                    long newSize = Math.max(this.scratchHeaderBuffer.size * 2L, header * 16L);
                    MemoryBuffer newScratch = new MemoryBuffer(newSize);
                    this.scratchHeaderBuffer.cpyTo(newScratch.address);
                    this.scratchHeaderBuffer.free();
                    this.scratchHeaderBuffer = newScratch;
                }
                long headerPtr = this.scratchHeaderBuffer.address + header * 16L;
                this.currentElemCopyAmount += elemSize;
                int alloc = this.allocScratchDataPos(elemSize);
                data.cpyTo(this.scratchDataBuffer.address + alloc * 8L);
                MemoryUtil.memPutInt(headerPtr, alloc);
                MemoryUtil.memPutInt(headerPtr + 4L, point);
                MemoryUtil.memPutInt(headerPtr + 8L, elemSize);
            }
        }

        void reset() {
            this.maxElementAccess = 0;
            this.currentElemCopyAmount = 0;
            this.dataUploadPoints.clear();
            this.arena.reset();
        }

        void free() {
            this.scratchHeaderBuffer.free();
            this.scratchDataBuffer.free();
        }

        private int allocScratchDataPos(int size) {
            int pos = (int) this.arena.alloc(size);
            if (this.scratchDataBuffer.size <= (pos + size) * 8L) {
                long newSize = Math.max(this.scratchDataBuffer.size * 2L, (pos + size) * 8L);
                MemoryBuffer newScratch = new MemoryBuffer(newSize);
                this.scratchDataBuffer.cpyTo(newScratch.address);
                this.scratchDataBuffer.free();
                this.scratchDataBuffer = newScratch;
            }
            return pos;
        }
    }

}
