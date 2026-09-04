package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Round10GpuBufferLayoutTest {
    @Test
    void hocBuffersRetainOriginalRecordCountsAndHeaders() {
        assertEquals(12, GpuBufferLayout.DISPATCH_BYTES);
        assertEquals(WorldEngine.MAX_LOD_LAYER + 1, GpuBufferLayout.MAX_ITERATIONS);
        assertEquals(408, GpuBufferLayout.HOC_REQUEST_BUFFER_BYTES);
        assertEquals(800_000, GpuBufferLayout.HOC_QUEUE_BUFFER_BYTES);
        assertEquals(80, GpuBufferLayout.HOC_METADATA_BUFFER_BYTES);
        assertEquals(800_004, GpuBufferLayout.HOC_RENDER_LIST_BYTES);
        assertEquals(GpuBufferLayout.HOC_REQUEST_BUFFER_BYTES,
                GpuBufferLayout.HOC_REQUEST_HEADER_BYTES
                        + (long) GpuBufferLayout.HOC_REQUEST_CAPACITY * GpuBufferLayout.HOC_REQUEST_BYTES);
    }

    @Test
    void commandBucketsPartitionExactlyAndDoNotConfuseSectionsWithCommands() {
        assertEquals(20, GpuBufferLayout.DRAW_COMMAND_BYTES);
        assertEquals(8_000_000L, GpuBufferLayout.TRANSLUCENT_OFFSET * 20L);
        assertEquals(10_000_000L, GpuBufferLayout.TEMPORAL_OFFSET * 20L);
        assertEquals(12_000_000L, GpuBufferLayout.DRAW_BUFFER_BYTES);
        assertEquals(GpuBufferLayout.TRANSLUCENT_OFFSET,
                GpuBufferLayout.OPAQUE_CAPACITY);
        assertEquals(GpuBufferLayout.TEMPORAL_OFFSET,
                GpuBufferLayout.TRANSLUCENT_OFFSET + GpuBufferLayout.TRANSLUCENT_CAPACITY);
        assertTrue(7L * GpuBufferLayout.HOC_RENDER_LIST_CAPACITY > GpuBufferLayout.OPAQUE_CAPACITY,
                "A section limit alone cannot bound the seven-command reservation");
        assertEquals(3_200_000, GpuBufferLayout.POSITION_BUFFER_BYTES);
        assertTrue(GpuBufferLayout.POSITION_CAPACITY >= GpuBufferLayout.HOC_RENDER_LIST_CAPACITY);
        assertEquals(404_096, GpuBufferLayout.TRANSLUCENT_DISTANCE_ABI_BYTES);
        assertEquals(408_192, GpuBufferLayout.TRANSLUCENT_DISTANCE_BYTES);
        assertEquals(GpuBufferLayout.TRANSLUCENT_DISTANCE_ABI_BYTES,
                GpuBufferLayout.TRANSLUCENT_PREFIX_SNAPSHOT_BASE * 4L);
    }

    @Test
    void drawCommandAndCountAbiKeepTheirOriginalPackedPrefix() throws Exception {
        ByteBuffer command = ByteBuffer.allocate(GpuBufferLayout.DRAW_COMMAND_BYTES).order(ByteOrder.nativeOrder());
        command.putInt(12).putInt(1).putInt(0).putInt(-16).putInt(42);
        assertEquals(20, command.position());
        assertEquals(-16, command.getInt(12));
        assertEquals(42, command.getInt(16));
        assertEquals(44, GpuBufferLayout.DRAW_COUNT_ABI_BYTES);
        assertTrue(GpuBufferLayout.DRAW_COUNT_BYTES >= GpuBufferLayout.DRAW_COUNT_ABI_BYTES);
        String source = Files.readString(Path.of("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl"));
        String struct = source.substring(source.indexOf("struct DrawCommand"), source.indexOf("};", source.indexOf("struct DrawCommand")));
        assertEquals(5, struct.chars().filter(c -> c == ';').count());
        assertTrue(struct.contains("int  baseVertex"));
        assertTrue(struct.contains("uint  baseInstance"));
    }

    @Test
    void cleanerDownloadIsTheFixedPositionRegionNotAnAppendBuffer() {
        assertEquals(512, GpuBufferLayout.CLEANER_LOCAL_SIZE * GpuBufferLayout.CLEANER_ELEMENTS_PER_THREAD);
        assertEquals(1024, GpuBufferLayout.CLEANER_ID_BYTES);
        assertEquals(2048, GpuBufferLayout.CLEANER_POSITION_BYTES);
        assertEquals(3072, GpuBufferLayout.CLEANER_OUTPUT_BYTES);
        assertEquals(GpuBufferLayout.CLEANER_OUTPUT_BYTES,
                GpuBufferLayout.CLEANER_ID_BYTES + GpuBufferLayout.CLEANER_POSITION_BYTES);
    }
}
