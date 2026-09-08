package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.cortex.voxy.common.thread.Service;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.util.Pair;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;

import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

final class RenderGenerationService {
    private static final int MAX_HOLDING_SECTION_COUNT = 1000;
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final AtomicInteger MESH_FAILED_COUNTER = new AtomicInteger();

    private static final class BuildTask {
        WorldSection section;
        final long position;
        boolean hasDoneModelRequestInner;
        boolean hasDoneModelRequestOuter;
        int attempts;
        int addin;
        long cacheEpoch;
        long watchToken;
        long priority = Long.MIN_VALUE;

        private BuildTask(long position, long cacheEpoch) {
            this.position = position;
            this.cacheEpoch = cacheEpoch;
        }

        private void updatePriority() {
            int unique = COUNTER.incrementAndGet();
            int lvl = WorldEngine.MAX_LOD_LAYER - WorldEngine.getLevel(this.position);
            lvl = Math.min(lvl, 3);
            this.priority = (((lvl * 3L + Math.min(this.attempts, 3)) * 2 + this.addin) << 32)
                    + Integer.toUnsignedLong(unique);
            this.addin = 0;
        }
    }

    private final AtomicInteger holdingSectionCount = new AtomicInteger();
    private final AtomicInteger taskQueueCount = new AtomicInteger();
    private final PriorityBlockingQueue<BuildTask> taskQueue =
            new PriorityBlockingQueue<>(5000, (a, b) -> Long.compareUnsigned(a.priority, b.priority));
    private final StampedLock taskMapLock = new StampedLock();
    private final Long2ObjectOpenHashMap<BuildTask> taskMap = new Long2ObjectOpenHashMap<>(5000);
    private final WorldEngine world;
    private final ModelBakerySubsystem modelBakery;
    private final ModelFactory modelFactory;
    private final boolean emitMeshlets;
    private final Service service;
    private Consumer<BuiltSection> resultConsumer;
    private GeometryCache geometryCache;
    private long lastChangedTime;
    private boolean acceptingTasks = true;
    private TokenValidator tokenValidator;

    interface TokenValidator { boolean isCurrent(long position, long token); }
    interface MeshGenerator { BuiltSection generate(WorldSection section); }

    void setTokenValidator(TokenValidator validator) {
        if (this.tokenValidator != null) throw new IllegalStateException("render-generation-token-validator-already-set");
        this.tokenValidator = validator;
    }

    //Original RenderGenerationService receives the ModelBakerySubsystem and reads its factory.
    RenderGenerationService(
            WorldEngine world,
            ModelBakerySubsystem modelBakery,
            ServiceManager serviceManager,
            boolean emitMeshlets) {
        this.world = world;
        this.modelBakery = modelBakery;
        this.modelFactory = modelBakery.factory;
        this.emitMeshlets = emitMeshlets;
        this.service = serviceManager.createService(() -> {
            RenderDataFactory factory = new RenderDataFactory(
                    this.world,
                    this.modelFactory,
                    this.emitMeshlets);
            IntOpenHashSet seenMissedIds = new IntOpenHashSet(128);
            return new Pair<>(() -> this.processJob(factory, seenMissedIds), factory::free);
        }, 10, "Section mesh generation service");
    }

    void setResultConsumer(Consumer<BuiltSection> consumer) {
        this.resultConsumer = consumer;
    }

    void setGeometryCache(GeometryCache geometryCache) {
        if (this.geometryCache != null) {
            throw new IllegalStateException("render-generation-cache-already-set");
        }
        this.geometryCache = geometryCache;
    }

    void enqueueTask(long pos) {
        this.enqueueTask(pos, 0);
    }

    void enqueueTask(long pos, long token) {
        WorldSection displaced = null;
        long stamp = this.taskMapLock.writeLock();
        try {
            if (!this.acceptingTasks || !this.service.isLive() || !this.isCurrent(pos, token)) return;
            GeometryCache cache = this.geometryCache;
            long epoch = cache == null ? Long.MIN_VALUE : cache.snapshotEpoch(pos);
            BuildTask task = this.taskMap.get(pos);
            if (task != null) {
                // A map entry is still queued, never currently executing. Coalesce dirty
                // revisions without creating unbounded obsolete permits or losing rewatch.
                if (task.watchToken != token || task.cacheEpoch != epoch) {
                    task.watchToken = token;
                    task.cacheEpoch = epoch;
                    task.hasDoneModelRequestInner = false;
                    task.hasDoneModelRequestOuter = false;
                    task.attempts = 0;
                    displaced = task.section;
                    task.section = null;
                    if (displaced != null) this.holdingSectionCount.decrementAndGet();
                }
                return;
            }
            task = new BuildTask(pos, epoch);
            task.watchToken = token;
            task.updatePriority();
            this.taskMap.put(pos, task);
            this.publishTask(task);
        } finally {
            this.taskMapLock.unlockWrite(stamp);
            if (displaced != null) displaced.release();
        }
    }

    private boolean isCurrent(long pos, long token) {
        return this.tokenValidator == null || this.tokenValidator.isCurrent(pos, token);
    }

    /** Called with taskMapLock held; reserve before publishing and waking a worker. */
    private void publishTask(BuildTask task) {
        this.taskQueueCount.incrementAndGet();
        try {
            this.taskQueue.add(task);
            this.service.execute();
        } catch (RuntimeException | Error failure) {
            this.taskQueue.remove(task);
            this.taskQueueCount.decrementAndGet();
            this.taskMap.remove(task.position, task);
            throw failure;
        }
    }

    int getTaskCount() {
        return this.taskQueueCount.get();
    }

    void shutdown() {
        long stamp = this.taskMapLock.writeLock();
        try {
            if (!this.acceptingTasks) throw new IllegalStateException("render-generation-already-stopped");
            this.acceptingTasks = false;
        } finally { this.taskMapLock.unlockWrite(stamp); }
        try {
            // The original Service retracts pending permits and waits for claimed jobs. Those
            // jobs may no longer requeue, so draining objects afterwards has no consumer race.
            this.service.shutdown();
        } finally {
            this.drainQueuedTasks();
            if (this.taskQueueCount.get() != 0 || this.holdingSectionCount.get() != 0 || !this.taskMap.isEmpty()) {
                throw new IllegalStateException("render-generation-task-queue-count-mismatch");
            }
        }
    }

    private void computeAndRequestRequiredModels(IntOpenHashSet seenMissedIds, int bitMsk, long[] auxData) {
        for (int i = 0; i < 6; i++) {
            if ((bitMsk & (1 << i)) == 0) {
                continue;
            }
            for (int j = 0; j < 32 * 32; j++) {
                int block = Mapper.getBlockId(auxData[j + (i * 32 * 32)]);
                if (block != 0 && !this.modelFactory.hasModelForBlockId(block) && seenMissedIds.add(block)) {
                    this.modelBakery.requestBlockBake(block);
                }
            }
        }
    }

    private void computeAndRequestRequiredModels(IntOpenHashSet seenMissedIds, WorldSection section) {
        for (long state : section._unsafeGetRawDataArray()) {
            int block = Mapper.getBlockId(state);
            if (block != 0 && !this.modelFactory.hasModelForBlockId(block) && seenMissedIds.add(block)) {
                this.modelBakery.requestBlockBake(block);
            }
        }
    }

    private WorldSection acquireSection(long pos) {
        return this.world.acquireIfExists(pos);
    }

    private void processJob(
            RenderDataFactory factory,
            IntOpenHashSet seenMissedIds) {
        this.processJob(factory::generateMesh, seenMissedIds);
    }

    void processJob(MeshGenerator generator, IntOpenHashSet seenMissedIds) {
        BuildTask task;
        WorldSection section;
        long stamp = this.taskMapLock.writeLock();
        try {
            task = this.taskQueue.poll();
            if (task == null) return;
            this.taskQueueCount.decrementAndGet();
            if (this.taskMap.remove(task.position) != task) throw new IllegalStateException("render-generation-task-map-mismatch");
            section = task.section;
            task.section = null;
            if (section != null) this.holdingSectionCount.decrementAndGet();
        } finally { this.taskMapLock.unlockWrite(stamp); }
        boolean retained = false;
        BuiltSection mesh = null;
        try {
            if (!this.isCurrent(task.position, task.watchToken)) return;
            if (section == null) section = this.acquireSection(task.position);
            if (section == null) {
                mesh = BuiltSection.empty(task.position);
            } else {
                section.assertNotFree();
                try {
                    mesh = generator.generate(section);
                } catch (IdNotYetComputedException missing) {
                    retained = this.handleMissingModel(task, section, missing, seenMissedIds);
                }
            }
        } finally {
            if (!retained && section != null) section.release();
        }
        if (mesh != null) {
            this.emitResult(mesh.withCacheEpoch(task.cacheEpoch).withWatchToken(task.watchToken));
        }
    }

    private boolean handleMissingModel(
            BuildTask task,
            WorldSection section,
            IdNotYetComputedException e,
            IntOpenHashSet seenMissedIds) {
        BuildTask currentTask = task;
        this.requestMissingModel(e, seenMissedIds);
        if (currentTask.hasDoneModelRequestOuter || currentTask.hasDoneModelRequestInner) {
            MESH_FAILED_COUNTER.incrementAndGet();
        }
        if (currentTask.hasDoneModelRequestInner && currentTask.hasDoneModelRequestOuter) {
            currentTask.attempts++;
        } else {
            if (currentTask.hasDoneModelRequestInner) {
                currentTask.attempts++;
            }
            if (!currentTask.hasDoneModelRequestInner) {
                if (e.auxData == null) {
                    this.computeAndRequestRequiredModels(seenMissedIds, section);
                }
                currentTask.hasDoneModelRequestInner = true;
            }
            if (currentTask.hasDoneModelRequestOuter) {
                currentTask.attempts++;
            }
            if (!currentTask.hasDoneModelRequestOuter && e.auxData != null) {
                this.computeAndRequestRequiredModels(seenMissedIds, e.auxBitMsk, e.auxData);
                currentTask.hasDoneModelRequestOuter = true;
            }
            currentTask.addin = WorldEngine.getLevel(currentTask.position) > 2 ? 1 : 0;
        }

        long stamp = this.taskMapLock.writeLock();
        try {
            if (!this.acceptingTasks || !this.service.isLive() || !this.isCurrent(task.position, task.watchToken)) return false;
            BuildTask other = this.taskMap.get(task.position);
            if (other != null) {
                if (other.watchToken == task.watchToken) {
                    other.hasDoneModelRequestInner |= task.hasDoneModelRequestInner;
                    other.hasDoneModelRequestOuter |= task.hasDoneModelRequestOuter;
                }
                return false;
            }
            if (this.holdingSectionCount.get() < MAX_HOLDING_SECTION_COUNT) {
                this.holdingSectionCount.incrementAndGet();
                task.section = section;
            }
            task.updatePriority();
            this.taskMap.put(task.position, task);
            try {
                this.publishTask(task);
            } catch (RuntimeException | Error failure) {
                if (task.section != null) {
                    this.holdingSectionCount.decrementAndGet();
                    task.section = null; // caller's finally retains responsibility on failure
                }
                throw failure;
            }
            // Latch transfer while holding the map lock. A coalescing producer may detach
            // task.section immediately after unlock and is then its only release owner.
            return task.section != null;
        } finally {
            this.taskMapLock.unlockWrite(stamp);
        }
    }

    private void requestMissingModel(IdNotYetComputedException e, IntOpenHashSet seenMissedIds) {
        if (e.isIdBlockId && !this.modelFactory.hasModelForBlockId(e.id) && seenMissedIds.add(e.id)) {
            this.modelBakery.requestBlockBake(e.id);
        }
    }

    private void emitResult(BuiltSection mesh) {
        Consumer<BuiltSection> consumer = this.resultConsumer;
        if (consumer != null) {
            consumer.accept(mesh);
        } else {
            mesh.free();
        }
    }

    private void drainQueuedTasks() {
        while (!this.taskQueue.isEmpty()) {
            BuildTask task = this.taskQueue.remove();
            this.taskQueueCount.decrementAndGet();
            if (task.section != null) {
                task.section.release();
                this.holdingSectionCount.decrementAndGet();
                task.section = null;
            }
            long stamp = this.taskMapLock.writeLock();
            try {
                if (this.taskMap.remove(task.position) != task) {
                    throw new IllegalStateException("render-generation-task-map-mismatch");
                }
            } finally {
                this.taskMapLock.unlockWrite(stamp);
            }
        }
    }

    void addDebugData(List<String> debug) {
        if (System.currentTimeMillis() - this.lastChangedTime > 100L) {
            MESH_FAILED_COUNTER.set(0);
            this.lastChangedTime = System.currentTimeMillis();
        }
        debug.add("RSSQ/TFC: " + this.taskQueueCount.get() + "/" + MESH_FAILED_COUNTER.get());
    }
}
