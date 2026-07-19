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
        final long cacheEpoch;
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
        if (!this.service.isLive()) {
            return;
        }
        boolean[] isOurs = new boolean[1];
        long stamp = this.taskMapLock.writeLock();
        BuildTask task;
        try {
            task = this.taskMap.computeIfAbsent(pos, p -> {
                isOurs[0] = true;
                GeometryCache cache = this.geometryCache;
                return new BuildTask(p, cache == null ? Long.MIN_VALUE : cache.snapshotEpoch(p));
            });
        } finally {
            this.taskMapLock.unlockWrite(stamp);
        }

        if (isOurs[0]) {
            task.updatePriority();
            this.taskQueue.add(task);
            this.taskQueueCount.incrementAndGet();
            this.service.execute();
        }
    }

    int getTaskCount() {
        return this.taskQueueCount.get();
    }

    void shutdown() {
        while (this.service.numJobs() != 0) {
            int taskCount = this.service.drain();
            if (taskCount == 0) {
                break;
            }
            long stamp = this.taskMapLock.writeLock();
            try {
                for (int i = 0; i < taskCount; i++) {
                    BuildTask task = this.taskQueue.remove();
                    if (task.section != null) {
                        task.section.release();
                        this.holdingSectionCount.decrementAndGet();
                    }
                    if (this.taskMap.remove(task.position) != task) {
                        throw new IllegalStateException("render-generation-task-map-mismatch");
                    }
                }
                this.taskQueueCount.addAndGet(-taskCount);
            } finally {
                this.taskMapLock.unlockWrite(stamp);
            }
        }
        this.service.shutdown();
        this.drainQueuedTasks();
        if (this.taskQueueCount.get() != 0) {
            throw new IllegalStateException("render-generation-task-queue-count-mismatch");
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
        BuildTask task = this.taskQueue.poll();
        this.taskQueueCount.decrementAndGet();
        boolean shouldFreeSection = true;
        WorldSection section = task.section == null ? this.acquireSection(task.position) : task.section;
        this.removeTaskFromMap(task);

        if (section == null) {
            this.emitResult(BuiltSection.empty(task.position).withCacheEpoch(task.cacheEpoch));
            return;
        }

        section.assertNotFree();
        BuiltSection mesh = null;
        try {
            mesh = factory.generateMesh(section);
        } catch (IdNotYetComputedException e) {
            task = this.handleMissingModel(task, section, e, seenMissedIds);
            shouldFreeSection = task == null || task.section == null;
        } catch (RuntimeException e) {
            throw e;
        }

        if (shouldFreeSection) {
            if (task != null && task.section != null) {
                this.holdingSectionCount.decrementAndGet();
            }
            section.release();
        }

        if (mesh != null) {
            this.emitResult(mesh.withCacheEpoch(task.cacheEpoch));
        }
    }

    private BuildTask handleMissingModel(
            BuildTask task,
            WorldSection section,
            IdNotYetComputedException e,
        IntOpenHashSet seenMissedIds) {
        BuildTask currentTask = task;
        boolean replacedTaskNeedsModelRequest = false;
        long stamp = this.taskMapLock.writeLock();
        try {
            BuildTask other = this.taskMap.putIfAbsent(task.position, task);
            if (other != null) {
                replacedTaskNeedsModelRequest = true;
                if (task.hasDoneModelRequestInner) {
                    other.hasDoneModelRequestInner = true;
                }
                if (task.hasDoneModelRequestOuter) {
                    other.hasDoneModelRequestOuter = true;
                }
                if (task.section != null) {
                    this.holdingSectionCount.decrementAndGet();
                }
                task.section = null;
                currentTask = null;
            }
        } finally {
            this.taskMapLock.unlockWrite(stamp);
        }
        if (replacedTaskNeedsModelRequest) {
            this.requestMissingModel(e, seenMissedIds);
        }

        if (currentTask == null) {
            return null;
        }

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

        if (currentTask.section == null) {
            if (this.holdingSectionCount.get() < MAX_HOLDING_SECTION_COUNT) {
                this.holdingSectionCount.incrementAndGet();
                currentTask.section = section;
            }
        }

        currentTask.updatePriority();
        this.taskQueue.add(currentTask);
        this.taskQueueCount.incrementAndGet();
        if (this.service.isLive()) {
            this.service.execute();
        }
        return currentTask;
    }

    private void requestMissingModel(IdNotYetComputedException e, IntOpenHashSet seenMissedIds) {
        if (e.isIdBlockId && !this.modelFactory.hasModelForBlockId(e.id) && seenMissedIds.add(e.id)) {
            this.modelBakery.requestBlockBake(e.id);
        }
    }

    private void removeTaskFromMap(BuildTask task) {
        long stamp = this.taskMapLock.writeLock();
        try {
            BuildTask removed = this.taskMap.remove(task.position);
            if (removed != task) {
                throw new IllegalStateException("render-generation-task-map-mismatch");
            }
        } finally {
            this.taskMapLock.unlockWrite(stamp);
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
