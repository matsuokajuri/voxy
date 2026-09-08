package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.voxy.common.util.AllocationArena;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Explicit microbenchmark of the real CPU owners, compiled unchanged against both git baseline and
 * candidate sources. Not part of a renderer, not a fake state machine, and never runtime readiness.
 * Allocation, generated input and callback bookkeeping costs are intentionally included equally.
 */
public final class Round11CpuBenchmark {
    private static volatile long sink;
    private static final int WARMUP = Integer.getInteger("round11.bench.warmup", 12);
    private static final int SAMPLES = Integer.getInteger("round11.bench.samples", 32);
    private static final long MIN_WARMUP_MILLIS = Long.getLong("round11.bench.minWarmupMillis", 2000L);

    public static void main(String[] args) throws Throwable {
        String label = args.length == 0 ? "unspecified" : args[0];
        String selected = args.length > 1 ? args[1] : "all";
        String[] names = {"arena_churn", "geometry_same_size", "geometry_resize", "geometry_publish",
                "cache_churn", "node_split_remove", "sync_copy_replace", "async_worker_cached_split"};
        for (String name : names) {
            if (!selected.equals("all") && !selected.equals(name)) continue;
            int scenarioBuffers = MemoryBuffer.getCount();
            long scenarioBytes = MemoryBuffer.getTotalSize();
            try (Work work = create(name)) {
                long warmupStarted = System.nanoTime();
                int warmupBatches = 0;
                while (warmupBatches < WARMUP || System.nanoTime() - warmupStarted < MIN_WARMUP_MILLIS * 1_000_000L) {
                    work.run();
                    warmupBatches++;
                }
                System.out.println("ROUND11_CPU_PHASE " + label + " " + name + " warmup_done batches=" + warmupBatches + " uptime_ms="
                        + ManagementFactory.getRuntimeMXBean().getUptime());
                double[] elapsed = new double[SAMPLES];
                double[] allocated = new double[SAMPLES];
                double[] workerTimes = work.workerNanos == null ? null : new double[SAMPLES * work.workerNanos.length];
                com.sun.management.ThreadMXBean bean =
                        (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
                boolean allocationSupported = bean.isThreadAllocatedMemorySupported();
                if (allocationSupported) bean.setThreadAllocatedMemoryEnabled(true);
                int buffersBefore = MemoryBuffer.getCount();
                long bytesBefore = MemoryBuffer.getTotalSize();
                for (int i = 0; i < SAMPLES; i++) {
                    long beforeBytes = allocationSupported ? bean.getCurrentThreadAllocatedBytes() : 0;
                    long start = System.nanoTime();
                    long operations = work.run();
                    long duration = System.nanoTime() - start;
                    long afterBytes = allocationSupported ? bean.getCurrentThreadAllocatedBytes() : 0;
                    elapsed[i] = (double) (work.measuredNanos < 0 ? duration : work.measuredNanos) / operations;
                    allocated[i] = allocationSupported ? (double) (afterBytes - beforeBytes) / operations : -1;
                    if (workerTimes != null) {
                        for (int run = 0; run < work.workerNanos.length; run++) {
                            workerTimes[i * work.workerNanos.length + run] = work.workerNanos[run];
                        }
                    }
                    require(MemoryBuffer.getCount() == buffersBefore, "native buffer count drift: " + name);
                    require(MemoryBuffer.getTotalSize() == bytesBefore, "native byte drift: " + name);
                }
                Arrays.sort(elapsed);
                Arrays.sort(allocated);
                if (workerTimes != null) Arrays.sort(workerTimes);
                System.out.println(String.format(java.util.Locale.ROOT,
                        "ROUND11_CPU {\"label\":\"%s\",\"scenario\":\"%s\",\"samples\":%d,"
                                + "\"warmup\":%d,\"p50_ns_per_op\":%.3f,\"p95_ns_per_op\":%.3f,"
                                + "\"p50_heap_bytes_per_op\":%.3f,\"retained_native_buffers\":%d,"
                                + "\"retained_native_bytes\":%d,\"peak_live_geometry_bytes\":%d,"
                                + "\"cache_hits\":%d,\"cache_misses\":%d,\"cache_evictions\":%d,"
                                + "\"isolated_worker_run_p50_ns\":%.3f,\"isolated_worker_run_p95_ns\":%.3f}",
                        label, name, SAMPLES, warmupBatches, percentile(elapsed, .5), percentile(elapsed, .95),
                        percentile(allocated, .5), MemoryBuffer.getCount() - buffersBefore,
                        MemoryBuffer.getTotalSize() - bytesBefore, work.peakGeometry, work.hits, work.misses,
                        work.evictions, workerTimes == null ? -1 : percentile(workerTimes, .5),
                        workerTimes == null ? -1 : percentile(workerTimes, .95)));
            }
            require(MemoryBuffer.getCount() == scenarioBuffers && MemoryBuffer.getTotalSize() == scenarioBytes,
                    "scenario owner close retained native buffers: " + name);
        }
        System.out.println("ROUND11_CPU_ENV java=" + System.getProperty("java.runtime.version")
                + " vm=" + System.getProperty("java.vm.name") + " sink=" + sink);
    }

    private static double percentile(double[] values, double fraction) {
        return values[Math.min(values.length - 1, (int) Math.ceil(values.length * fraction) - 1)];
    }

    private static Work create(String name) throws Throwable {
        return switch (name) {
            case "arena_churn" -> arenaWork();
            case "geometry_same_size" -> geometryWork(false, false);
            case "geometry_resize" -> geometryWork(true, false);
            case "geometry_publish" -> geometryWork(true, true);
            case "cache_churn" -> cacheWork();
            case "node_split_remove" -> nodeWork();
            case "sync_copy_replace" -> copyWork();
            case "async_worker_cached_split" -> workerWork();
            default -> throw new IllegalArgumentException(name);
        };
    }

    private abstract static class Work implements AutoCloseable {
        long peakGeometry;
        long hits;
        long misses;
        long evictions;
        long measuredNanos = -1;
        long[] workerNanos;
        abstract long run() throws Throwable;
        @Override public void close() throws Exception {}
    }

    private static Work arenaWork() {
        return new Work() {
            final AllocationArena arena = new AllocationArena();
            final long[] addresses = new long[4096];
            long run() {
                for (int i = 0; i < addresses.length; i++) addresses[i] = arena.alloc((i & 127) + 1);
                for (int i = 0; i < addresses.length; i += 2) arena.free(addresses[i]);
                for (int i = 0; i < addresses.length; i += 2) addresses[i] = arena.alloc((i & 63) + 1);
                for (long address : addresses) arena.free(address);
                require(arena.getSize() == 0, "arena retained allocation");
                sink += addresses[100];
                return addresses.length * 3L;
            }
        };
    }

    private static Work geometryWork(boolean resize, boolean publish) throws Throwable {
        return new Work() {
            final BasicAsyncGeometryManager geometry = new BasicAsyncGeometryManager(4096, 256L << 20);
            final int[] ids = new int[512];
            final Copy copy = publish ? new Copy() : null;
            long run() throws Throwable {
                long operations = 0;
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = geometry.uploadSection(section(i, 64, 0));
                    operations++;
                }
                for (int pass = 0; pass < 8; pass++) {
                    int bytes = resize ? new int[]{2048, 512, 4096, 128}[pass & 3] : 64;
                    for (int i = 0; i < ids.length; i++) {
                        ids[i] = geometry.uploadReplaceSection(ids[i], section(i, bytes, 0));
                        operations++;
                    }
                    peakGeometry = Math.max(peakGeometry, geometry.getGeometryUsedBytes());
                    if (publish) {
                        for (int pointer : geometry.getHeapRemovals()) copy.remove(pointer);
                        geometry.getHeapRemovals().clear();
                        var iterator = geometry.getUploads().int2ObjectEntrySet().fastIterator();
                        while (iterator.hasNext()) {
                            var entry = iterator.next();
                            copy.upload(entry.getIntKey(), entry.getValue());
                            entry.getValue().free();
                            iterator.remove();
                        }
                        geometry.getUpdateIds().clear();
                        copy.reset();
                    }
                }
                for (int id : ids) { geometry.removeSection(id); operations++; }
                require(geometry.getSectionCount() == 0 && geometry.getGeometryUsedBytes() == 0,
                        "geometry retained allocation");
                geometry.clear();
                sink += ids[0];
                return operations;
            }
            public void close() throws Exception {
                geometry.clear();
                if (copy != null) copy.close();
            }
        };
    }

    private static Work cacheWork() {
        return new Work() {
            // free() is terminal in the candidate, so use take/invalidate to reset each sample.
            final GeometryCache cache = new GeometryCache(8L << 10, 32);
            long run() {
                long oldHits = cache.hits(), oldMisses = cache.misses(), oldEvictions = cache.evictions();
                for (int i = 0; i < 4096; i++) {
                    long position = i & 255;
                    cache.put(section(position, 256, 0), cache.snapshotEpoch(position));
                    if ((i & 3) != 0) {
                        BuiltSection hit = cache.take(position);
                        require(hit != null, "expected immediate cache hit");
                        hit.free();
                    } else {
                        BuiltSection miss = cache.take(4096 + position);
                        require(miss == null, "expected cache miss");
                    }
                }
                for (int i = 0; i < 256; i++) cache.invalidate(i);
                require(cache.entryCount() == 0 && cache.currentSize() == 0, "cache retained allocation");
                hits = cache.hits() - oldHits;
                misses = cache.misses() - oldMisses;
                evictions = cache.evictions() - oldEvictions;
                sink += hits;
                return 4096L * 2 + 256;
            }
            public void close() { cache.free(); }
        };
    }

    private static Work nodeWork() {
        return new Work() {
            final BasicAsyncGeometryManager geometry = new BasicAsyncGeometryManager(4096, 64L << 20);
            final Watcher watcher = new Watcher();
            final NodeManager nodes = new NodeManager(4096, geometry, watcher);
            {
                nodes.setTLNCallbacks(id -> sink += id, id -> sink -= id);
            }
            long run() {
                for (int i = 0; i < 128; i++) {
                    long parent = WorldEngine.getWorldSectionId(2, i, 0, -7);
                    require(nodes.insertTopLevelNode(parent), "duplicate top-level benchmark position");
                    require(nodes.processGeometryResult(section(parent, 64, 255)), "initial mesh rejected");
                    nodes.processRequest(parent);
                    for (int bit = 0; bit < 8; bit++) {
                        long child = WorldEngine.getWorldSectionId(1, (i << 1) | (bit & 1),
                                (bit >>> 2) & 1, (-7 << 1) | ((bit >>> 1) & 1));
                        require(nodes.processGeometryResult(section(child, 64, 255)), "child mesh rejected");
                    }
                    peakGeometry = Math.max(peakGeometry, geometry.getGeometryUsedBytes());
                    nodes.removeTopLevelNode(parent);
                }
                require(watcher.types.isEmpty() && geometry.getSectionCount() == 0,
                        "node sample retained watcher/geometry");
                nodes.getNodeUpdates().clear();
                geometry.clear();
                return 128L * 12;
            }
            public void close() { geometry.clear(); }
        };
    }

    private static Work copyWork() throws Throwable {
        return new Work() {
            final Copy copy = new Copy();
            final MemoryBuffer small = new MemoryBuffer(64).zero();
            final MemoryBuffer large = new MemoryBuffer(4096).zero();
            long run() throws Throwable {
                for (int pass = 0; pass < 16; pass++) {
                    for (int i = 0; i < 512; i++) copy.upload(i * 512, (pass & 1) == 0 ? large : small);
                    if ((pass & 3) == 3) for (int i = 0; i < 512; i += 2) copy.remove(i * 512);
                }
                copy.reset();
                return 16L * 512 + 4L * 256;
            }
            public void close() throws Exception { small.free(); large.free(); copy.close(); }
        };
    }

    private static Work workerWork() throws Throwable {
        return new Work() {
            final AsyncNodeManager worker;
            final GeometryCache cache;
            final BasicAsyncGeometryManager geometry;
            final AtomicInteger counter;
            final AtomicReference<?> published;
            final MethodHandle runWorker;
            final MethodHandle returnResult;
            final Thread previousRenderThread;
            int workerIndex;
            {
                workerNanos = new long[4];
                // Same external-producer seam as Round11AsyncLifecycleTest. No GL is initialized;
                // the real constructor installs the real router/cache/node/publication owners.
                Field renderThread = RenderSystem.class.getDeclaredField("renderThread");
                renderThread.setAccessible(true);
                previousRenderThread = (Thread) renderThread.get(null);
                renderThread.set(null, Thread.currentThread());
                BasicSectionGeometryData data = new BasicSectionGeometryData(4096);
                setField(data, "geometryCapacityBytes", 128L << 20);
                Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                Unsafe unsafe = (Unsafe) unsafeField.get(null);
                RenderGenerationService externalProducer = (RenderGenerationService)
                        unsafe.allocateInstance(RenderGenerationService.class);
                worker = new AsyncNodeManager(4096, data, externalProducer);
                cache = (GeometryCache) getField(worker, "geometryCache");
                geometry = (BasicAsyncGeometryManager) getField(worker, "geometryManager");
                counter = (AtomicInteger) getField(worker, "workCounter");
                published = (AtomicReference<?>) getField(worker, "results");
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(AsyncNodeManager.class, MethodHandles.lookup());
                runWorker = lookup.findVirtual(AsyncNodeManager.class, "workerRun", MethodType.methodType(void.class));
                Class<?> resultType = Class.forName("me.cortex.voxy.forge.AsyncNodeManager$SyncResults");
                returnResult = lookup.findVirtual(AsyncNodeManager.class, "returnResultObject",
                        MethodType.methodType(void.class, resultType))
                        .asType(MethodType.methodType(void.class, AsyncNodeManager.class, Object.class));
            }
            void tickCpu() throws Throwable {
                require(counter.get() > 0, "benchmark attempted idle workerRun; producer schedule is invalid");
                long start = System.nanoTime();
                runWorker.invokeExact(worker);
                long duration = System.nanoTime() - start;
                workerNanos[workerIndex++] = duration;
                measuredNanos += duration;
                Object packet = published.getAndSet(null);
                require(packet != null, "worker did not publish its CPU packet");
                // The benchmark ends at CPU publication, never pretending to upload or draw it.
                returnResult.invokeExact(worker, packet);
            }
            long run() throws Throwable {
                measuredNanos = 0;
                workerIndex = 0;
                long beforeHits = cache.hits(), beforeMisses = cache.misses(), beforeEvictions = cache.evictions();
                final int roots = 32;
                long[] positions = new long[roots];
                for (int i = 0; i < roots; i++) {
                    long parent = positions[i] = WorldEngine.getWorldSectionId(2, i, 0, -7);
                    cache.put(section(parent, 64, 255), cache.snapshotEpoch(parent));
                    for (int bit = 0; bit < 8; bit++) {
                        long child = WorldEngine.getWorldSectionId(1, (i << 1) | (bit & 1),
                                (bit >>> 2) & 1, (-7 << 1) | ((bit >>> 1) & 1));
                        cache.put(section(child, 64, 255), cache.snapshotEpoch(child));
                    }
                    worker.addTopLevel(parent);
                }
                tickCpu();
                MemoryBuffer request = new MemoryBuffer(8 + roots * 8L).zero();
                MemoryUtil.memPutInt(request.address, roots);
                for (int i = 0; i < roots; i++) {
                    MemoryUtil.memPutInt(request.address + 8 + i * 8L, (int) (positions[i] >>> 32));
                    MemoryUtil.memPutInt(request.address + 12 + i * 8L, (int) positions[i]);
                }
                worker.submitRequestBatch(request);
                tickCpu();
                tickCpu();
                peakGeometry = Math.max(peakGeometry, geometry.getGeometryUsedBytes());
                for (long position : positions) worker.removeTopLevel(position);
                tickCpu();
                require(counter.get() == 0, "worker retained event reservations");
                require(geometry.getSectionCount() == 0 && cache.entryCount() == 0,
                        "worker retained node geometry or cached result");
                hits = cache.hits() - beforeHits;
                misses = cache.misses() - beforeMisses;
                evictions = cache.evictions() - beforeEvictions;
                require(hits == roots * 9L && misses == 0 && evictions == 0,
                        "worker input/cached result counters diverged");
                return roots * 11L + 1;
            }
            public void close() throws Exception {
                worker.stop();
                Field field = RenderSystem.class.getDeclaredField("renderThread");
                field.setAccessible(true);
                field.set(null, previousRenderThread);
            }
        };
    }

    private static Object getField(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void setField(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static final class Copy implements AutoCloseable {
        private static final Class<?> TYPE;
        private static final MethodHandle CONSTRUCTOR, UPLOAD, REMOVE, RESET, FREE;
        static {
            try {
                TYPE = Class.forName("me.cortex.voxy.forge.AsyncNodeManager$ComputeMemoryCopy");
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(TYPE, MethodHandles.lookup());
                CONSTRUCTOR = lookup.findConstructor(TYPE, MethodType.methodType(void.class))
                        .asType(MethodType.methodType(Object.class));
                UPLOAD = lookup.findVirtual(TYPE, "upload", MethodType.methodType(void.class, int.class, MemoryBuffer.class))
                        .asType(MethodType.methodType(void.class, Object.class, int.class, MemoryBuffer.class));
                REMOVE = lookup.findVirtual(TYPE, "remove", MethodType.methodType(void.class, int.class))
                        .asType(MethodType.methodType(void.class, Object.class, int.class));
                RESET = lookup.findVirtual(TYPE, "reset", MethodType.methodType(void.class))
                        .asType(MethodType.methodType(void.class, Object.class));
                FREE = lookup.findVirtual(TYPE, "free", MethodType.methodType(void.class))
                        .asType(MethodType.methodType(void.class, Object.class));
            } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
        }
        private final Object owner;
        Copy() throws Throwable { owner = (Object) CONSTRUCTOR.invokeExact(); }
        void upload(int point, MemoryBuffer buffer) throws Throwable { UPLOAD.invokeExact(owner, point, buffer); }
        void remove(int point) throws Throwable { REMOVE.invokeExact(owner, point); }
        void reset() throws Throwable { RESET.invokeExact(owner); }
        public void close() throws Exception {
            try { FREE.invokeExact(owner); }
            catch (Throwable e) { throw new Exception(e); }
        }
    }

    private static final class Watcher implements ISectionWatcher {
        final Long2IntOpenHashMap types = new Long2IntOpenHashMap();
        public boolean watch(long position, int flags) {
            int previous = types.get(position);
            types.put(position, previous | flags);
            return (flags & ~previous) != 0;
        }
        public boolean unwatch(long position, int flags) {
            int remaining = types.get(position) & ~flags;
            if (remaining == 0) types.remove(position); else types.put(position, remaining);
            return remaining == 0;
        }
        public int get(long position) { return types.get(position); }
    }

    private static BuiltSection section(long position, int bytes, int mask) {
        return new BuiltSection(position, (byte) mask, 0, new MemoryBuffer(bytes).zero(), new int[8], null);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
