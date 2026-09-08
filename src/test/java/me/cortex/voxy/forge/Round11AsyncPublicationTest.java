package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** CPU publication packet proof only; real GPU/client acceptance is a separate gate. */
class Round11AsyncPublicationTest {
    @Test
    void deterministicUploadReplacementRemovalAndReuseKeepsOneLatestCopyPerDestination() throws Exception {
        Class<?> type = Class.forName("me.cortex.voxy.forge.AsyncNodeManager$ComputeMemoryCopy");
        Method upload = method(type, "upload", int.class, MemoryBuffer.class);
        Method remove = method(type, "remove", int.class);
        Method reset = method(type, "reset");
        Method free = method(type, "free");
        for (int seed = 0; seed < 32; seed++) {
            Object copy = construct(type);
            Map<Integer, long[]> expected = new HashMap<>();
            Random random = new Random(0x11A5C000L + seed);
            try {
                for (int step = 0; step < 2_000; step++) {
                    int point = random.nextInt(64) * 64;
                    int operation = random.nextInt(16);
                    if (operation == 0) {
                        reset.invoke(copy);
                        expected.clear();
                    } else if (operation < 6) {
                        remove.invoke(copy, point);
                        expected.remove(point);
                    } else {
                        long[] values = new long[1 + random.nextInt(32)];
                        MemoryBuffer input = new MemoryBuffer(values.length * 8L);
                        try {
                            for (int i = 0; i < values.length; i++) {
                                values[i] = ((long) seed << 48) | ((long) step << 16) | i;
                                MemoryUtil.memPutLong(input.address + i * 8L, values[i]);
                            }
                            upload.invoke(copy, point, input);
                            expected.put(point, values);
                        } finally {
                            input.free();
                        }
                    }
                    assertCopies(copy, expected, "seed=" + seed + ", step=" + step);
                }
                reset.invoke(copy);
                assertCopies(copy, Map.of(), "drained seed=" + seed);
            } finally {
                free.invoke(copy);
            }
        }
    }

    @Test
    void scatterGrowthAndRepeatedLocationsRetainLatestSixteenByteRecords() throws Exception {
        Class<?> type = Class.forName("me.cortex.voxy.forge.AsyncNodeManager$SyncResults");
        Object sync = construct(type);
        Method pointer = method(type, "getScatterWritePtr", int.class, int.class);
        Method free = method(type, "free");
        Map<Integer, Long> expected = new HashMap<>();
        try {
            for (int step = 0; step < 10_000; step++) {
                int location = step < 6_000 ? step : step - 6_000;
                // Include both node and geometry metadata's existing signed buffer selector.
                if ((location & 1) != 0) {
                    location |= Integer.MIN_VALUE;
                }
                long address = (long) pointer.invoke(sync, location, 0);
                long value = 0x11AA000000000000L | step;
                MemoryUtil.memPutLong(address, value);
                MemoryUtil.memPutLong(address + 8, ~value);
                expected.put(location, value);
            }
            Int2IntOpenHashMap locations = (Int2IntOpenHashMap) get(sync, "scatterWriteLocationMap");
            MemoryBuffer buffer = (MemoryBuffer) get(sync, "scatterWriteBuffer");
            assertEquals(expected.size(), locations.size());
            for (var entry : expected.entrySet()) {
                long offset = locations.get(entry.getKey().intValue()) * 16L;
                assertTrue(offset >= 16 && offset + 16 <= buffer.size);
                assertEquals(entry.getValue().longValue(), MemoryUtil.memGetLong(buffer.address + offset));
                assertEquals(~entry.getValue(), MemoryUtil.memGetLong(buffer.address + offset + 8));
            }
        } finally {
            free.invoke(sync);
        }
    }

    private static void assertCopies(Object copy, Map<Integer, long[]> expected, String replay) throws Exception {
        Int2IntOpenHashMap points = (Int2IntOpenHashMap) get(copy, "dataUploadPoints");
        MemoryBuffer headers = (MemoryBuffer) get(copy, "scratchHeaderBuffer");
        MemoryBuffer scratch = (MemoryBuffer) get(copy, "scratchDataBuffer");
        assertEquals(expected.size(), points.size(), replay);
        int elements = 0;
        boolean[] seenHeader = new boolean[points.size()];
        for (var entry : expected.entrySet()) {
            int header = points.get(entry.getKey().intValue());
            assertTrue(header >= 0 && header < seenHeader.length, replay);
            assertFalse(seenHeader[header], replay);
            seenHeader[header] = true;
            long address = headers.address + header * 16L;
            int source = MemoryUtil.memGetInt(address);
            assertEquals(entry.getKey().intValue(), MemoryUtil.memGetInt(address + 4), replay);
            assertEquals(entry.getValue().length, MemoryUtil.memGetInt(address + 8), replay);
            assertTrue(source >= 0 && (source + (long) entry.getValue().length) * 8 <= scratch.size, replay);
            for (int index = 0; index < entry.getValue().length; index++) {
                assertEquals(entry.getValue()[index], MemoryUtil.memGetLong(scratch.address + (source + index) * 8L), replay);
            }
            elements += entry.getValue().length;
        }
        assertEquals(elements, get(copy, "currentElemCopyAmount"), replay);
    }

    private static Object construct(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) throws Exception {
        Method method = type.getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
