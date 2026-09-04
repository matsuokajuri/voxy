package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CPU oracles and source/ABI checks; actual GLSL execution is qualified separately. */
final class Round10MdicCommandSafetyTest {
    private static final int SENTINEL = 0x4A71_B93D;
    private static final Path SHADERS = Path.of("src/main/resources/assets/voxy/shaders/lod/gl46");

    @Test
    void drawApiBoundsCoverSevenCommandsPerSectionWithoutIntegerOverflow() {
        for (int capacity : new int[]{GpuBufferLayout.OPAQUE_CAPACITY, GpuBufferLayout.TEMPORAL_CAPACITY}) {
            for (int sections : new int[]{0, 1, capacity / 7, capacity / 7 + 1, Integer.MAX_VALUE}) {
                assertEquals((int) Math.min(7L * sections, capacity),
                        MDICSectionRenderer.maximumDirectionalCommands(sections, capacity));
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> MDICSectionRenderer.maximumDirectionalCommands(-1, 100));
        assertThrows(IllegalArgumentException.class,
                () -> MDICSectionRenderer.maximumDirectionalCommands(1, -1));
    }

    @Test
    void capacityDefinesAndTwentyByteCommandAbiAgreeWithAllocationOwners() throws Exception {
        String defined = MDICSectionRenderer.withCommandCapacityDefines("#version 450\nvoid main() {}\n");
        assertTrue(defined.startsWith("#version 450\n"));
        assertTrue(defined.contains("#define RENDER_LIST_CAPACITY " + GpuBufferLayout.HOC_RENDER_LIST_CAPACITY + "\n"));
        assertTrue(defined.contains("#define OPAQUE_DRAW_CAPACITY " + GpuBufferLayout.OPAQUE_CAPACITY + "\n"));
        assertTrue(defined.contains("#define TRANSLUCENT_DRAW_CAPACITY " + GpuBufferLayout.TRANSLUCENT_CAPACITY + "\n"));
        assertTrue(defined.contains("#define TEMPORAL_DRAW_CAPACITY " + GpuBufferLayout.TEMPORAL_CAPACITY + "\n"));
        assertTrue(defined.contains("#define TRANSLUCENT_PREFIX_SNAPSHOT_BASE "
                + GpuBufferLayout.TRANSLUCENT_PREFIX_SNAPSHOT_BASE + "\n"));

        String bindings = Files.readString(SHADERS.resolve("bindings.glsl"));
        int commandStart = bindings.indexOf("struct DrawCommand {");
        String command = bindings.substring(commandStart, bindings.indexOf("};", commandStart));
        assertEquals(5, command.lines().filter(line -> line.trim().matches("(?:u?int)\\s+\\w+;")).count());
        ByteBuffer bytes = ByteBuffer.allocate(GpuBufferLayout.DRAW_COMMAND_BYTES * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : new int[]{6, 1, 0, 44, 9, 12, 1, 0, 88, 10}) bytes.putInt(value);
        assertEquals(20, GpuBufferLayout.DRAW_COMMAND_BYTES);
        assertEquals(12, bytes.getInt(20));
        assertEquals(10, bytes.getInt(36));
        assertEquals(44, MDICViewport.COMMAND_DIAGNOSTICS_OFFSET);
        assertEquals(24, MDICViewport.COMMAND_DIAGNOSTICS_BYTES);
        assertTrue(MDICViewport.COMMAND_DIAGNOSTICS_OFFSET + MDICViewport.COMMAND_DIAGNOSTICS_BYTES
                <= GpuBufferLayout.DRAW_COUNT_BYTES);
        assertEquals(GpuBufferLayout.TRANSLUCENT_DISTANCE_ABI_BYTES + 4096,
                GpuBufferLayout.TRANSLUCENT_DISTANCE_BYTES);
    }

    @Test
    void originalCommandCountersUseBoundedCompareExchangeNotAttemptedAppend() throws Exception {
        String cmdgen = Files.readString(SHADERS.resolve("cmdgen.comp"));
        for (String counter : new String[]{"opaqueDrawCount", "temporalOpaqueDrawCount", "translucentDrawCount"}) {
            assertTrue(cmdgen.contains("atomicCompSwap(" + counter + ", observed, observed + "));
            assertFalse(cmdgen.matches("(?s).*atomicAdd\\(" + counter + ", (?:cmdCnt|1(?:u)?)\\).*"));
        }
        assertTrue(cmdgen.contains("count > capacity - observed"));
        assertTrue(cmdgen.contains("if (tp != INVALID_COMMAND_INDEX)"));
        assertTrue(cmdgen.contains("uint validSectionCount = min(sectionCount"));
        assertTrue(cmdgen.contains("sectionId >= uint(sectionData.length())"));
        String prep = Files.readString(SHADERS.resolve("prep.comp"));
        assertTrue(prep.contains("cullDrawIndirectCommand.instanceCount = validSectionCount;"));
        for (String counter : new String[]{"opaqueDrawCount", "temporalOpaqueDrawCount", "translucentDrawCount"}) {
            assertTrue(prep.contains(counter + " = 0;"));
        }
        assertFalse(prep.contains("commandOverflowFlags = 0"), "sampling must not miss a reset overflow flag");
        String builder = Files.readString(SHADERS.resolve("buildtranslucents.comp"));
        assertTrue(builder.contains("observed < start || observed >= end"));
        assertTrue(builder.contains("atomicExchange(translucentDrawCount, 0u)"));
        assertTrue(builder.contains("if (drawPtr != INVALID_COMMAND_INDEX)"));
    }

    @Test
    void everyMdicIndirectConsumerUsesTheViewportReadOnlyDispatchSnapshot() throws Exception {
        assertEquals(12, GpuBufferLayout.DISPATCH_BYTES);
        String renderer = Files.readString(Path.of("src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java"));
        assertTrue(renderer.contains("copyDispatchArguments(viewport.drawCountCallBuffer.id, viewport.commandDispatchBuffer.id)"));
        assertTrue(renderer.contains("glCopyNamedBufferSubData(countBuffer, dispatchBuffer, 0L, 0L, GpuBufferLayout.DISPATCH_BYTES)"));
        assertFalse(renderer.contains("glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, viewport.drawCountCallBuffer.id)"));
        assertEquals(2, renderer.lines().filter(line -> line.contains(
                "glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, viewport.commandDispatchBuffer.id)")).count());
        String viewport = Files.readString(Path.of("src/main/java/me/cortex/voxy/forge/MDICViewport.java"));
        assertTrue(viewport.contains("commandDispatchBuffer = new GlBuffer(GpuBufferLayout.DISPATCH_BYTES)"));
        assertTrue(viewport.contains("&& this.commandDispatchBuffer.id != 0"));
        assertEquals(1, viewport.lines().filter(line -> line.contains("this.commandDispatchBuffer.free();")).count());
    }

    @Test
    void capacityMinusOneExactCapacityAndOverCapacityNeverWriteAnotherBucket() {
        for (int capacity : new int[]{GpuBufferLayout.OPAQUE_CAPACITY,
                GpuBufferLayout.TRANSLUCENT_CAPACITY, GpuBufferLayout.TEMPORAL_CAPACITY}) {
            for (int remaining : new int[]{0, 1, 6, 7}) {
                BoundedBucket bucket = new BoundedBucket(capacity);
                bucket.count.set(capacity - remaining);
                int before = bucket.count.get();
                int reservation = bucket.reserve(7);
                if (remaining == 7) {
                    assertEquals(before, reservation);
                    bucket.write(reservation, 7, 19);
                    assertEquals(capacity, bucket.count.get());
                } else {
                    assertEquals(-1, reservation);
                    assertEquals(before, bucket.count.get());
                    assertEquals(7, bucket.rejected.get());
                }
                assertEquals(SENTINEL, bucket.commands.get(0));
                assertEquals(SENTINEL, bucket.commands.get(capacity + 1));
            }
            BoundedBucket bucket = new BoundedBucket(capacity);
            bucket.count.set(capacity - 1);
            bucket.write(bucket.reserve(1), 1, 20);
            assertEquals(capacity, bucket.count.get());
            assertEquals(-1, bucket.reserve(1));
            assertEquals(capacity, bucket.count.get());
            assertEquals(20, bucket.commands.get(capacity));
            assertEquals(SENTINEL, bucket.commands.get(capacity + 1));
        }
    }

    @Test
    void competingBatchesPublishOnlyContiguousWrittenCommandsAndRecoverNextFrame() throws Exception {
        BoundedBucket bucket = new BoundedBucket(513);
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < 512; i++) {
            int marker = i;
            int amount = i % 7 + 1;
            jobs.add(() -> {
                int slot = bucket.reserve(amount);
                if (slot != -1) bucket.write(slot, amount, marker);
                return null;
            });
        }
        runConcurrent(jobs);
        assertTrue(bucket.count.get() <= bucket.capacity);
        assertTrue(bucket.rejected.get() > 0);
        for (int i = 0; i < bucket.count.get(); i++) assertTrue(bucket.commands.get(i + 1) != SENTINEL);
        assertEquals(SENTINEL, bucket.commands.get(0));
        assertEquals(SENTINEL, bucket.commands.get(bucket.capacity + 1));

        int rejected = bucket.rejected.get();
        bucket.prep();
        assertEquals(0, bucket.count.get());
        assertEquals(rejected, bucket.rejected.get(), "sticky diagnostics survive ordinary prep");
        int slot = bucket.reserve(7);
        bucket.overwrite(slot, 7, 9999);
        assertEquals(7, bucket.count.get());
        for (int i = 0; i < bucket.count.get(); i++) assertEquals(9999, bucket.commands.get(i + 1));
        // Old commands still exist in storage but are beyond this frame's admitted draw count.
        assertTrue(bucket.commands.get(8) != 9999);
    }

    @Test
    void allTranslucentItemsInOneBucketUseExactlyTheirSuccessfulListCount() {
        for (int bucket : new int[]{0, 511, 1023}) {
            for (int attempted : new int[]{0, 126, 127, 128}) {
                TranslucentOracle oracle = new TranslucentOracle(127, attempted);
                for (int i = 0; i < attempted; i++) oracle.admit(i, bucket);
                int accepted = Math.min(attempted, 127);
                assertEquals(accepted, oracle.prefix());
                for (int i = 0; i < accepted; i++) oracle.build(i);
                oracle.assertComplete(accepted);
            }
        }
    }

    @Test
    void realTranslucentCapacityRejectsOnlyTheExtraEntryAndPreservesItsSentinel() {
        int capacity = GpuBufferLayout.TRANSLUCENT_CAPACITY;
        TranslucentOracle oracle = new TranslucentOracle(capacity, capacity + 1);
        for (int i = 0; i <= capacity; i++) oracle.admit(i, 1023);
        assertEquals(capacity, oracle.prefix());
        assertEquals(1, oracle.list.rejected.get());
        for (int i = 0; i < capacity; i++) oracle.build(i);
        oracle.assertComplete(capacity);
    }

    @Test
    void sparseAndDenseDistanceBucketsRemainDisjointUnderConcurrentBuilders() throws Exception {
        TranslucentOracle oracle = new TranslucentOracle(8192, 8192);
        Random random = new Random(0x10_C0_4D);
        for (int i = 0; i < 8192; i++) oracle.admit(i, (i & 3) == 0 ? 7 : random.nextInt(1024));
        int accepted = oracle.prefix();
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            int from = i * 128;
            jobs.add(() -> {
                for (int j = from; j < from + 128; j++) oracle.build(j);
                return null;
            });
        }
        runConcurrent(jobs);
        oracle.assertComplete(accepted);
    }

    @Test
    void badBucketCursorOrUnwrittenListEntryFailsClosedAndNextPrepRecovers() {
        TranslucentOracle oracle = new TranslucentOracle(3, 3);
        oracle.admit(0, 0);
        oracle.admit(1, 1);
        oracle.prefix();
        oracle.cursors.set(0, 1); // Exactly the first slot belonging to the next bucket.
        oracle.build(0);
        assertEquals(0, oracle.drawCount.get());
        assertEquals(SENTINEL, oracle.commands.get(2), "must not cross into next distance bucket");

        oracle.prep();
        oracle.admit(2, 1023);
        oracle.prefix();
        oracle.build(0);
        oracle.assertComplete(1);

        oracle.prep();
        oracle.admit(0, 1);
        oracle.prefix();
        oracle.list.commands.set(1, SENTINEL);
        oracle.build(0);
        assertEquals(0, oracle.drawCount.get(), "cannot publish a count containing an unwritten command");
    }

    private static void runConcurrent(List<Callable<Void>> jobs) throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            for (var result : pool.invokeAll(jobs)) result.get();
        } finally {
            pool.shutdownNow();
        }
    }

    /** Same admitted-only CAS contract as the GLSL, with guard words around the bucket. */
    private static final class BoundedBucket {
        final int capacity;
        final AtomicInteger count = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        final AtomicIntegerArray commands;

        BoundedBucket(int capacity) {
            this.capacity = capacity;
            this.commands = new AtomicIntegerArray(capacity + 2);
            for (int i = 0; i < this.commands.length(); i++) this.commands.set(i, SENTINEL);
        }

        int reserve(int amount) {
            int observed = this.count.get();
            while (true) {
                if (observed > this.capacity || amount > this.capacity - observed) {
                    this.rejected.addAndGet(amount);
                    return -1;
                }
                if (this.count.compareAndSet(observed, observed + amount)) return observed;
                observed = this.count.get();
            }
        }

        void write(int offset, int amount, int marker) {
            for (int i = 0; i < amount; i++) {
                assertEquals(SENTINEL, this.commands.getAndSet(offset + i + 1, marker), "duplicate slot");
            }
        }

        void overwrite(int offset, int amount, int marker) {
            for (int i = 0; i < amount; i++) this.commands.set(offset + i + 1, marker);
        }

        void prep() {
            this.count.set(0);
        }
    }

    /** CPU reference of admitted list -> exclusive prefix -> bounded bucket builder. */
    private static final class TranslucentOracle {
        final BoundedBucket list;
        final int[] distanceByDrawId;
        final int[] histogram = new int[1024];
        final int[] starts = new int[1024];
        final AtomicIntegerArray cursors = new AtomicIntegerArray(1024);
        final AtomicIntegerArray commands;
        final AtomicInteger drawCount = new AtomicInteger();

        TranslucentOracle(int capacity, int drawIds) {
            this.list = new BoundedBucket(capacity);
            this.distanceByDrawId = new int[drawIds];
            this.commands = new AtomicIntegerArray(capacity + 2);
            this.clearCommands();
        }

        void admit(int drawId, int distance) {
            int slot = this.list.reserve(1);
            if (slot == -1) return;
            this.list.overwrite(slot, 1, drawId);
            this.distanceByDrawId[drawId] = distance;
            this.histogram[distance]++;
        }

        int prefix() {
            int total = 0;
            for (int i = 0; i < 1024; i++) {
                this.starts[i] = total;
                this.cursors.set(i, total);
                total += this.histogram[i];
            }
            assertEquals(this.list.count.get(), total);
            this.drawCount.set(total);
            return total;
        }

        void build(int index) {
            int count = this.drawCount.get();
            if (index >= count) return;
            int drawId = this.list.commands.get(index + 1);
            if (drawId < 0 || drawId >= this.distanceByDrawId.length) {
                this.drawCount.set(0);
                return;
            }
            int distance = this.distanceByDrawId[drawId];
            int start = this.starts[distance];
            int end = distance == 1023 ? count : this.starts[distance + 1];
            if (start > end || end > count || this.starts[0] != 0) {
                this.drawCount.set(0);
                return;
            }
            int observed = this.cursors.get(distance);
            while (true) {
                if (observed < start || observed >= end) {
                    this.drawCount.set(0);
                    return;
                }
                if (this.cursors.compareAndSet(distance, observed, observed + 1)) {
                    assertEquals(SENTINEL, this.commands.getAndSet(observed + 1, drawId));
                    return;
                }
                observed = this.cursors.get(distance);
            }
        }

        void assertComplete(int expected) {
            assertEquals(expected, this.drawCount.get());
            boolean[] found = new boolean[this.distanceByDrawId.length];
            for (int i = 0; i < expected; i++) {
                int drawId = this.commands.get(i + 1);
                assertTrue(drawId >= 0 && drawId < found.length);
                assertFalse(found[drawId], "duplicate translucent draw");
                found[drawId] = true;
                int distance = this.distanceByDrawId[drawId];
                int end = distance == 1023 ? expected : this.starts[distance + 1];
                assertTrue(i >= this.starts[distance] && i < end);
            }
            assertEquals(SENTINEL, this.commands.get(0));
            assertEquals(SENTINEL, this.commands.get(this.list.capacity + 1));
            assertEquals(SENTINEL, this.list.commands.get(this.list.capacity + 1));
        }

        void prep() {
            this.list.prep();
            this.drawCount.set(0);
            Arrays.fill(this.histogram, 0);
            this.clearCommands();
        }

        void clearCommands() {
            for (int i = 0; i < this.commands.length(); i++) this.commands.set(i, SENTINEL);
        }
    }
}
