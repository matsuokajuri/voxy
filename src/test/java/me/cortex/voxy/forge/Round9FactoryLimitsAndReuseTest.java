package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldSection;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Round9FactoryLimitsAndReuseTest {
    private static final int MODEL_ID = 41;
    private static final int MAX_QUADS_PER_BUCKET = (1 << 16) - 1;
    private static final long MODEL_METADATA = repeatedFaceMetadata(0b1_0111, 0b100_0000);
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void bucketOverflowFailsBeforeWritingAnUnrepresentableSixteenBitCount() throws Exception {
        FakeModelFactory fake = fakeModelFactory();
        RenderDataFactory factory = new RenderDataFactory(null, fake.factory, false);
        try {
            int[] counters = field(factory, "quadCounters", int[].class);
            counters[0] = MAX_QUADS_PER_BUCKET - 1;
            setInt(factory, "quadCount", MAX_QUADS_PER_BUCKET - 1);

            long pointer = field(factory, "quadBufferPtr", Long.class);
            long firstUnrepresentableSlot = pointer + (long) MAX_QUADS_PER_BUCKET * Long.BYTES;
            long sentinel = 0x1357_9BDF_2468_ACE0L;
            MemoryUtil.memPutLong(firstUnrepresentableSlot, sentinel);

            Object mesher = field(factory, "blockMesher", Object.class);
            Method emit = mesher.getClass().getDeclaredMethod(
                    "emitQuad", int.class, int.class, int.class, int.class, long.class);
            emit.setAccessible(true);
            emit.invoke(mesher, 0, 0, 1, 1, (long) MODEL_ID << 26);
            assertEquals(MAX_QUADS_PER_BUCKET, counters[0]);
            assertEquals(MAX_QUADS_PER_BUCKET, intField(factory, "quadCount"));

            InvocationTargetException wrapper = assertThrows(
                    InvocationTargetException.class,
                    () -> emit.invoke(mesher, 0, 0, 1, 1, (long) MODEL_ID << 26));
            IllegalStateException overflow = assertInstanceOf(
                    IllegalStateException.class,
                    wrapper.getCause());
            assertTrue(overflow.getMessage().contains("render-quad-bucket-capacity-exceeded"));
            assertEquals(MAX_QUADS_PER_BUCKET, counters[0]);
            assertEquals(MAX_QUADS_PER_BUCKET, intField(factory, "quadCount"));
            assertEquals(sentinel, MemoryUtil.memGetLong(firstUnrepresentableSlot));
        } finally {
            factory.free();
        }
    }

    @Test
    void oneFactoryRecoversAfterMissingModelAndResetsBoundsOffsetsAndEmptyState() throws Exception {
        FakeModelFactory fake = fakeModelFactory();
        RenderDataFactory factory = new RenderDataFactory(null, fake.factory, false);
        WorldSection section = newSection();
        long[] raw = section._unsafeGetRawDataArray();
        try {
            Arrays.fill(raw, 0L);
            raw[index(4, 5, 6)] = rawState(1, 0x29);
            BuiltSection first = factory.generateMesh(section);
            try {
                assertSingleBlockResult(first, section, 4, 5, 6);
            } finally {
                first.free();
            }

            Arrays.fill(raw, 0L);
            raw[index(12, 13, 14)] = rawState(2, 0x7C);
            IdNotYetComputedException missing = assertThrows(
                    IdNotYetComputedException.class,
                    () -> factory.generateMesh(section));
            assertEquals(2, missing.id);
            assertTrue(missing.isIdBlockId);

            fake.mappings[2] = MODEL_ID;
            Arrays.fill(raw, 0L);
            raw[index(20, 21, 22)] = rawState(2, 0xE1);
            BuiltSection recovered = factory.generateMesh(section);
            try {
                assertSingleBlockResult(recovered, section, 20, 21, 22);
            } finally {
                recovered.free();
            }

            Arrays.fill(raw, 0L);
            BuiltSection empty = factory.generateMesh(section);
            try {
                assertTrue(empty.isEmpty());
                assertEquals(-1, empty.aabb);
                assertNull(empty.offsets);
                assertNull(empty.geometryBuffer);
            } finally {
                empty.free();
            }
        } finally {
            factory.free();
            section.release();
        }
    }

    private static void assertSingleBlockResult(
            BuiltSection section,
            WorldSection source,
            int x,
            int y,
            int z
    ) {
        assertEquals(source.key, section.position);
        assertEquals(x | (y << 5) | (z << 10), section.aabb);
        assertArrayEquals(new int[]{0, 0, 0, 1, 2, 3, 4, 5}, section.offsets);
        assertEquals(6L * Long.BYTES, section.geometryBuffer.size);
    }

    private static WorldSection newSection() throws Exception {
        Constructor<?> constructor = Arrays.stream(WorldSection.class.getDeclaredConstructors())
                .filter(candidate -> candidate.getParameterCount() == 5)
                .findFirst()
                .orElseThrow();
        constructor.setAccessible(true);
        WorldSection section = (WorldSection) constructor.newInstance(0, 0, 0, 0, null);
        section.acquire();
        return section;
    }

    private static long rawState(int blockId, int light) {
        return ((long) blockId << 27) | ((long) light << 56);
    }

    private static int index(int x, int y, int z) {
        return x | (z << 5) | (y << 10);
    }

    private static FakeModelFactory fakeModelFactory() throws Exception {
        ModelFactory factory = (ModelFactory) UNSAFE.allocateInstance(ModelFactory.class);
        int[] mappings = new int[1 << 20];
        Arrays.fill(mappings, -1);
        mappings[1] = MODEL_ID;
        long[] metadata = new long[1 << 16];
        metadata[MODEL_ID] = MODEL_METADATA;
        long[][] masks = new long[1 << 16][];
        masks[MODEL_ID] = new long[FaceOcclusionMask.WORDS_PER_MODEL];
        Arrays.fill(masks[MODEL_ID], -1L);
        int[] fluidLut = new int[1 << 16];
        Arrays.fill(fluidLut, -1);
        putObject(factory, "idMappings", mappings);
        putObject(factory, "metadataCache", metadata);
        putObject(factory, "faceOcclusionMasks", masks);
        putObject(factory, "fluidStateLUT", fluidLut);
        return new FakeModelFactory(factory, mappings);
    }

    private static long repeatedFaceMetadata(int faceByte, int global) {
        long metadata = ((long) global & 0xFFFFL) << 48;
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            metadata |= ((long) faceByte & 0xFFL) << (face * Byte.SIZE);
        }
        return metadata;
    }

    private static void putObject(Object target, String fieldName, Object value) throws Exception {
        Field field = ModelFactory.class.getDeclaredField(fieldName);
        UNSAFE.putObject(target, UNSAFE.objectFieldOffset(field), value);
    }

    private static void setInt(RenderDataFactory factory, String name, int value) throws Exception {
        Field field = RenderDataFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(factory, value);
    }

    private static int intField(RenderDataFactory factory, String name) throws Exception {
        Field field = RenderDataFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(factory);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(RenderDataFactory factory, String name, Class<T> type) throws Exception {
        Field field = RenderDataFactory.class.getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(factory);
        if (type == Long.class) {
            return (T) Long.valueOf((long) value);
        }
        return type.cast(value);
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record FakeModelFactory(ModelFactory factory, int[] mappings) {
    }
}
