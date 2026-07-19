package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MipGenDarkCutoutParityTest {
    private static final int SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    private static final int TILE_WIDTH = SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X;

    @Test
    void darkCutoutUsesModernMinecraftDarkFillWithoutChangingAlphaOrMipMode() {
        ColourDepthTextureData[] faces = facesWithDarkCutoutSample();

        byte[][] mips = MipGen.putTextures(true, faces);

        assertEquals(0xFF301008, rgbaPixel(mips[0], 0, 0, TILE_WIDTH));
        assertEquals(0x00240C06, rgbaPixel(mips[0], 1, 0, TILE_WIDTH));
        assertEquals(
                TextureUtils.mipColours(true, 0xFF301008, 0x00240C06, 0x00240C06, 0x00240C06),
                rgbaPixel(mips[1], 0, 0, TILE_WIDTH / 2));
    }

    @Test
    void productionBufferPathUsesTheSameDarkCutoutBaseFill() {
        MemoryBuffer buffer = MipGen.putTexturesBuffer(true, facesWithDarkCutoutSample());
        try {
            assertEquals(0xFF301008, MemoryUtil.memGetInt(buffer.address));
            assertEquals(0x00240C06, MemoryUtil.memGetInt(buffer.address + 4));
        } finally {
            buffer.free();
        }
    }

    private static ColourDepthTextureData[] facesWithDarkCutoutSample() {
        ColourDepthTextureData[] faces = new ColourDepthTextureData[ForgeModelAtlasLayout.FACE_COUNT];
        for (int face = 0; face < faces.length; face++) {
            int[] colour = new int[SIZE * SIZE];
            Arrays.fill(colour, 0);
            colour[0] = 0xFF301008;
            colour[2] = 0xFF786450;
            faces[face] = new ColourDepthTextureData(colour, new int[colour.length], SIZE, SIZE);
        }
        return faces;
    }

    private static int rgbaPixel(byte[] bytes, int x, int y, int width) {
        int offset = (x + y * width) * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL;
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
