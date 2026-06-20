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

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
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
import static org.lwjgl.opengl.GL30C.GL_R32UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL42C.GL_UNIFORM_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL45C.glCreateBuffers;
import static org.lwjgl.opengl.GL45C.nglClearNamedBufferData;
import static org.lwjgl.opengl.GL45C.nglNamedBufferStorage;

final class ForgeOriginalVoxyAsyncNodeGeometrySync {
    private static final int GEOMETRY_UPLOAD_LIMIT_PER_RUN = 300;
    private static final long GEOMETRY_UPLOAD_BATCH_LIMIT_BYTES = 1_000L << 10;
    private static final long GEOMETRY_UPLOAD_HEADROOM_BYTES = 50_000_000L;
    private static final int ORIGINAL_MAX_NODE_COUNT = 1 << 21;
    private static final int NODE_CLEANER_OUTPUT_COUNT = 256;

    private final ForgeOriginalVoxyBasicAsyncGeometryManager geometryManager;
    private final ForgeOriginalVoxyBasicSectionGeometryData geometryData;
    private final ForgeOriginalVoxyRenderGenerationService renderGenerationService;
    private final ForgeOriginalVoxySectionUpdateRouter router;
    private final ForgeOriginalVoxyNodeManager nodeManager;
    private final ForgeOriginalVoxyGeometryCache geometryCache = new ForgeOriginalVoxyGeometryCache(1L << 32);
    private final ConcurrentLinkedDeque<MemoryBuffer> requestBatchQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<WorldSection> childUpdateQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<ForgeOriginalVoxyBuiltSection> geometryUpdateQueue = new ConcurrentLinkedDeque<>();
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
    private long submittedGeometryResultCount;
    private long processedGeometryResultCount;
    private long publishedSyncResultCount;
    private long renderThreadTickCount;
    private long geometryUploadCopyDispatchCount;
    private long metadataScatterDispatchCount;
    private long uploadedGeometryCopyCount;
    private long uploadedGeometryBytes;
    private long metadataScatterWriteCount;
    private long cleanerOperationSyncCount;
    private long topLevelNodeCallbackSyncCount;
    private int currentMaxNodeId;
    private int nodeBufferId;
    private boolean nodeBufferExternallyOwned;
    private long usedGeometryBytes;
    private IntConsumer topLevelNodeAddCallback;
    private IntConsumer topLevelNodeRemoveCallback;
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyAsyncNodeGeometrySync(
            ForgeOriginalVoxyBasicAsyncGeometryManager geometryManager,
            ForgeOriginalVoxyBasicSectionGeometryData geometryData,
            ForgeOriginalVoxyRenderGenerationService renderGenerationService) {
        this.geometryManager = geometryManager;
        this.geometryData = geometryData;
        this.renderGenerationService = renderGenerationService;
        this.router = new ForgeOriginalVoxySectionUpdateRouter();
        this.router.setCallbacks(pos -> {
            ForgeOriginalVoxyBuiltSection cachedGeometry = this.geometryCache.remove(pos);
            if (cachedGeometry != null) {
                this.submitGeometryResult(cachedGeometry);
            } else {
                renderGenerationService.enqueueTask(pos);
            }
        }, renderGenerationService::enqueueTask, this::submitChildChange);
        this.nodeManager = new ForgeOriginalVoxyNodeManager(ORIGINAL_MAX_NODE_COUNT, geometryManager, this.router);
        this.nodeManager.setClear(new ForgeOriginalVoxyNodeManager.Cleaner() {
            @Override
            public void alloc(int id) {
                ForgeOriginalVoxyAsyncNodeGeometrySync.this.cleanerIdResetClear.remove(id);
                ForgeOriginalVoxyAsyncNodeGeometrySync.this.cleanerIdResetClear.add(id | (1 << 31));
            }

            @Override
            public void move(int from, int to) {
                // Original Voxy intentionally leaves cleaner move as a no-op in AsyncNodeManager.
            }

            @Override
            public void free(int id) {
                ForgeOriginalVoxyAsyncNodeGeometrySync.this.cleanerIdResetClear.remove(id | (1 << 31));
                ForgeOriginalVoxyAsyncNodeGeometrySync.this.cleanerIdResetClear.add(id);
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
        this.lastLifecycleEvent = "start";
    }

    void setTopLevelNodeCallbacks(IntConsumer add, IntConsumer remove) {
        this.topLevelNodeAddCallback = add;
        this.topLevelNodeRemoveCallback = remove;
    }

    void setExternalNodeBuffer(int nodeBufferId) {
        requireRenderThread("set original async node external node buffer");
        if (this.nodeBufferId != 0 && !this.nodeBufferExternallyOwned && this.nodeBufferId != nodeBufferId) {
            glDeleteBuffers(this.nodeBufferId);
        }
        this.nodeBufferId = nodeBufferId;
        this.nodeBufferExternallyOwned = nodeBufferId != 0;
    }

    void submitGeometryResult(ForgeOriginalVoxyBuiltSection section) {
        if (!this.running) {
            section.free();
            return;
        }
        this.geometryUpdateQueue.add(section);
        this.submittedGeometryResultCount++;
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

    void tickOnRenderThread() {
        this.tickOnRenderThread(null);
    }

    void tickOnRenderThread(ForgeOriginalVoxyNodeCleaner nodeCleaner) {
        requireRenderThread("tick original async node geometry sync");
        if (this.uncaughtException != null) {
            Throwable throwable = this.uncaughtException;
            this.recordFailure("async-node-geometry-sync-" + throwable.getClass().getSimpleName() + ":" + throwable.getMessage());
            return;
        }
        this.ensurePrograms();
        this.ensureNodeBuffer();
        SyncResults sync = this.results.getAndSet(null);
        if (sync == null) {
            return;
        }
        this.renderThreadTickCount++;
        this.applyTopLevelNodeDeltas(sync);
        this.geometryData.setSectionCount(sync.geometrySectionCount);
        this.uploadGeometryCopies(sync);
        this.scatterMetadataWrites(sync);
        if (nodeCleaner != null) {
            nodeCleaner.updateIds(sync.cleanerOperations);
        }
        this.cleanerOperationSyncCount += sync.cleanerOperations.size();
        this.currentMaxNodeId = sync.currentMaxNodeId;
        this.usedGeometryBytes = sync.usedGeometry;
        this.returnResultObject(sync);
        this.lastLifecycleEvent = "render-thread-sync-tick";
        this.lastFailureReason = "none";
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
            this.topLevelNodeCallbackSyncCount++;
        }
    }

    ForgeOriginalVoxyAsyncNodeGeometrySyncStats createStatusSnapshot() {
        return new ForgeOriginalVoxyAsyncNodeGeometrySyncStats(
                this.running && this.workerThread.isAlive() && this.uncaughtException == null,
                true,
                true,
                true,
                this.multiMemcpyProgramId != 0 && this.scatterProgramId != 0,
                this.multiMemcpyProgramId != 0,
                this.scatterProgramId != 0,
                this.results.get() != null,
                true,
                this.geometryUpdateQueue.size(),
                this.nodeManager.getActiveSectionCount(),
                this.submittedGeometryResultCount,
                this.processedGeometryResultCount,
                this.publishedSyncResultCount,
                this.renderThreadTickCount,
                this.geometryUploadCopyDispatchCount,
                this.metadataScatterDispatchCount,
                this.uploadedGeometryCopyCount,
                this.uploadedGeometryBytes,
                this.metadataScatterWriteCount,
                this.currentMaxNodeId,
                this.usedGeometryBytes,
                this.lastLifecycleEvent,
                this.lastFailureReason
        );
    }

    boolean nodeBufferReady() {
        return this.nodeBufferId != 0;
    }

    int nodeBufferId() {
        return this.nodeBufferId;
    }

    int currentMaxNodeId() {
        return this.currentMaxNodeId;
    }

    long usedGeometryBytes() {
        return this.usedGeometryBytes;
    }

    long geometryCapacityBytes() {
        return this.geometryData.geometryCapacityBytes();
    }

    int maxNodeCount() {
        return ORIGINAL_MAX_NODE_COUNT;
    }

    void stopOnRenderThread() {
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
            ForgeOriginalVoxyBuiltSection section = this.geometryUpdateQueue.poll();
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
        if (this.nodeBufferId != 0 && !this.nodeBufferExternallyOwned) {
            glDeleteBuffers(this.nodeBufferId);
        }
        this.nodeBufferId = 0;
        this.nodeBufferExternallyOwned = false;
        this.geometryCache.free();
        synchronized (this.topLevelNodeLock) {
            this.topLevelNodeAdds.clear();
            this.topLevelNodeRemoves.clear();
        }
        this.lastLifecycleEvent = "stop";
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
            ForgeOriginalVoxyBuiltSection section = this.geometryUpdateQueue.poll();
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
        return this.geometryManager.createStatusSnapshot(true).geometryCapacityBytes()
                - this.geometryManager.getGeometryUsedBytes() > GEOMETRY_UPLOAD_HEADROOM_BYTES;
    }

    private void processGeometryResult(ForgeOriginalVoxyBuiltSection section) {
        try {
            this.nodeManager.processGeometryResult(section);
            this.processedGeometryResultCount++;
            this.lastLifecycleEvent = "worker-process-geometry-result";
            this.lastFailureReason = "none";
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
        this.mergeGeometryEvents(sync);
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
        this.publishedSyncResultCount++;
        this.lastLifecycleEvent = "publish-sync-results";
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

    private void mergeGeometryEvents(SyncResults sync) {
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
        if (!removals.isEmpty()) {
            var iter = removals.intIterator();
            while (iter.hasNext()) {
                sync.geometryUpload.remove(iter.nextInt());
            }
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
        int upCopies = ForgeOriginalVoxyUploadStream.alignUpAlloc(copies * 16);
        int scratchSize = (int) upload.arena.getSize() * Long.BYTES;
        int upScratchSize = ForgeOriginalVoxyUploadStream.alignUpAlloc(scratchSize);
        long ptr = ForgeOriginalVoxyUploadStream.instance().rawUploadAddress(upScratchSize + upCopies);
        MemoryUtil.memCopy(
                upload.scratchHeaderBuffer.address,
                ForgeOriginalVoxyUploadStream.instance().getBaseAddress() + ptr,
                copies * 16L);
        MemoryUtil.memCopy(
                upload.scratchDataBuffer.address,
                ForgeOriginalVoxyUploadStream.instance().getBaseAddress() + ptr + upCopies,
                scratchSize);
        ForgeOriginalVoxyUploadStream.instance().commit();

        glUseProgram(this.multiMemcpyProgramId);
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, ForgeOriginalVoxyUploadStream.instance().getRawBufferId(), ptr, upCopies);
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 1, ForgeOriginalVoxyUploadStream.instance().getRawBufferId(), ptr + upCopies, upScratchSize);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.geometryData.geometryBufferId());
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute(copies, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(0);

        this.geometryUploadCopyDispatchCount++;
        this.uploadedGeometryCopyCount += copies;
        this.uploadedGeometryBytes += scratchSize;
    }

    private void scatterMetadataWrites(SyncResults sync) {
        if (sync.scatterWriteLocationMap.isEmpty()) {
            return;
        }
        int count = sync.scatterWriteLocationMap.size();
        int chunks = (count + 3) / 4;
        int streamSize = chunks * 80;
        long ptr = ForgeOriginalVoxyUploadStream.instance().rawUploadAddress(streamSize);
        MemoryUtil.memCopy(
                sync.scatterWriteBuffer.address,
                ForgeOriginalVoxyUploadStream.instance().getBaseAddress() + ptr,
                streamSize);
        ForgeOriginalVoxyUploadStream.instance().commit();

        glUseProgram(this.scatterProgramId);
        glBindBufferRange(
                GL_SHADER_STORAGE_BUFFER,
                0,
                ForgeOriginalVoxyUploadStream.instance().getRawBufferId(),
                ptr,
                ForgeOriginalVoxyUploadStream.alignUpAlloc(streamSize));
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.nodeBufferId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.geometryData.metadataBufferId());
        glUniform1ui(0, count);
        glMemoryBarrier(GL_UNIFORM_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute((count + 127) / 128, 1, 1);
        glMemoryBarrier(GL_UNIFORM_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(0);

        this.metadataScatterDispatchCount++;
        this.metadataScatterWriteCount += count;
    }

    private void returnResultObject(SyncResults sync) {
        if (!this.resultCache1.compareAndSet(null, sync) && !this.resultCache2.compareAndSet(null, sync)) {
            sync.free();
            this.recordFailure("async-node-geometry-sync-result-cache-full");
        }
    }

    private void ensurePrograms() {
        if (this.multiMemcpyProgramId == 0) {
            this.multiMemcpyProgramId = compileComputeProgram(withDefines(MEMCPY_COMPUTE_SOURCE,
                    "INPUT_HEADER_BUFFER_BINDING", 0,
                    "INPUT_DATA_BUFFER_BINDING", 1,
                    "OUTPUT_BUFFER_BINDING", 2));
        }
        if (this.scatterProgramId == 0) {
            this.scatterProgramId = compileComputeProgram(withDefines(SCATTER_COMPUTE_SOURCE,
                    "INPUT_BUFFER_BINDING", 0,
                    "OUTPUT_BUFFER1_BINDING", 1,
                    "OUTPUT_BUFFER2_BINDING", 2));
        }
    }

    private void ensureNodeBuffer() {
        if (this.nodeBufferId == 0) {
            this.nodeBufferId = glCreateBuffers();
            this.nodeBufferExternallyOwned = false;
            nglNamedBufferStorage(this.nodeBufferId, ORIGINAL_MAX_NODE_COUNT * 16L, 0L, 0);
            long scratch = MemoryUtil.nmemAlloc(Integer.BYTES);
            try {
                MemoryUtil.memPutInt(scratch, -1);
                nglClearNamedBufferData(this.nodeBufferId, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, scratch);
            } finally {
                MemoryUtil.nmemFree(scratch);
            }
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
        this.lastLifecycleEvent = "failure";
        this.lastFailureReason = reason == null || reason.isBlank() ? "unspecified" : reason;
        VoxyForge.LOGGER.error("Original async node geometry sync failure: {}", this.lastFailureReason);
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
        glShaderSource(shader, source);
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

    private static final String MEMCPY_COMPUTE_SOURCE = """
            #version 460 core
            #define WORK_SIZE 256
            layout(local_size_x=WORK_SIZE) in;

            layout(binding = INPUT_HEADER_BUFFER_BINDING, std430) restrict readonly buffer InputHeaderBuffer {
                uvec4[] dataCopyHeader;
            };

            layout(binding = INPUT_DATA_BUFFER_BINDING, std430) restrict readonly buffer InputDataBuffer {
                uvec2[] dataInBuffer;
            };

            layout(binding = OUTPUT_BUFFER_BINDING, std430) restrict writeonly buffer OutputBuffer {
                uvec2[] outputBuffer;
            };

            void main() {
                uvec4 job = dataCopyHeader[gl_WorkGroupID.x];
                uint src = job.x;
                uint dst = job.y;
                uint siz = job.z;

                uint workPerThread = (siz+255)>>8;
                uint start = gl_LocalInvocationID.x*workPerThread+src;
                uint diff = dst-src;
                for (uint i = start; i < min(start+workPerThread,siz+src); i++) {
                    outputBuffer[i+diff] = dataInBuffer[i];
                }
            }
            """;

    private static final String SCATTER_COMPUTE_SOURCE = """
            #version 460 core

            layout(local_size_x=128) in;

            layout(binding = INPUT_BUFFER_BINDING, std430) restrict readonly buffer InputBuffer {
                uvec4[] inData;
            };

            layout(binding = OUTPUT_BUFFER1_BINDING, std430) restrict writeonly buffer OutputBuffer1 {
                uvec4[] output1;
            };

            layout(binding = OUTPUT_BUFFER2_BINDING, std430) restrict writeonly buffer OutputBuffer2 {
                uvec4[] output2;
            };

            layout(location=0) uniform uint count;

            void main() {
                uint id = gl_GlobalInvocationID.x;
                if (count <= id) {
                    return;
                }
                uint chunkBase = (id>>2u)*5u;
                uint innerId = id&3u;

                uint writeLocation = inData[chunkBase][innerId];
                uvec4 writeData = inData[chunkBase+1+innerId];

                uint writeBuffer = writeLocation>>31;
                writeLocation &= (1u<<31)-1;

                if (writeBuffer == 0) {
                    output1[writeLocation] = writeData;
                } else {
                    output2[writeLocation] = writeData;
                }
            }
            """;
}
