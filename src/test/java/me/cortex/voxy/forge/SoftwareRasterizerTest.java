package me.cortex.voxy.forge;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SoftwareRasterizerTest {
    private static final int TARGET_SIZE = 4;

    @Test
    void quadCoverageMatchesOriginalTriangleEdgeOwnership() {
        SoftwareRasterizer rasterizer = new SoftwareRasterizer(TARGET_SIZE);
        rasterizer.setFaceCull(true);
        rasterizer.setSamplerTexture(new int[]{0xFFFFFFFF}, 1, 1);
        rasterizer.clear();

        long vertices = MemoryUtil.nmemAlloc(4L * ReuseVertexConsumer.VERTEX_FORMAT_SIZE);
        try {
            putVertex(vertices, 0, -0.75F, -0.75F);
            putVertex(vertices, 1, -0.75F, 0.75F);
            putVertex(vertices, 2, 0.75F, 0.75F);
            putVertex(vertices, 3, 0.75F, -0.75F);

            rasterizer.raster(new Matrix4f(), vertices, 1);
        } finally {
            MemoryUtil.nmemFree(vertices);
        }

        int[] covered = Arrays.stream(rasterizer.getRawFramebuffer())
                .mapToInt(value -> (int) value == 0xFFFFFFFF ? 1 : 0)
                .toArray();
        assertArrayEquals(new int[]{
                1, 1, 1, 1,
                0, 1, 1, 0,
                0, 1, 1, 0,
                0, 0, 0, 1
        }, covered, Arrays.toString(covered));
    }

    @Test
    void packedColourMixUsesOriginal255WeightAndLaneRounding() {
        assertEquals(0x01010101, SoftwareRasterizer.mix(0x00000000, 0x01010101, 128));

        int unchanged = 0x44332211;
        assertEquals(unchanged, SoftwareRasterizer.mix(unchanged, unchanged, 0));
        assertEquals(unchanged, SoftwareRasterizer.mix(unchanged, unchanged, 127));
        assertEquals(unchanged, SoftwareRasterizer.mix(unchanged, unchanged, 255));
    }

    @Test
    void missingSamplerSurfacesTheInvalidRasterizerState() {
        SoftwareRasterizer rasterizer = new SoftwareRasterizer(TARGET_SIZE);
        rasterizer.setFaceCull(true);
        rasterizer.clear();

        long vertices = MemoryUtil.nmemAlloc(4L * ReuseVertexConsumer.VERTEX_FORMAT_SIZE);
        try {
            putVertex(vertices, 0, -0.75F, -0.75F);
            putVertex(vertices, 1, -0.75F, 0.75F);
            putVertex(vertices, 2, 0.75F, 0.75F);
            putVertex(vertices, 3, 0.75F, -0.75F);

            assertThrows(NullPointerException.class, () -> rasterizer.raster(new Matrix4f(), vertices, 1));
        } finally {
            MemoryUtil.nmemFree(vertices);
        }
    }

    private static void putVertex(long base, int index, float x, float y) {
        long address = base + (long) index * ReuseVertexConsumer.VERTEX_FORMAT_SIZE;
        MemoryUtil.memPutFloat(address, x);
        MemoryUtil.memPutFloat(address + 4L, y);
        MemoryUtil.memPutFloat(address + 8L, 0.0F);
        MemoryUtil.memPutInt(address + 12L, 0);
        MemoryUtil.memPutFloat(address + 16L, 0.5F);
        MemoryUtil.memPutFloat(address + 20L, 0.5F);
    }
}
