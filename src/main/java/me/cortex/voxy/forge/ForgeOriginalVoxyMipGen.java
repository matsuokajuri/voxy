package me.cortex.voxy.forge;

import java.util.ArrayDeque;
import java.util.Arrays;

final class ForgeOriginalVoxyMipGen {
    static final int LAYERS = Integer.numberOfTrailingZeros(ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE);

    private static final int MODEL_TEXTURE_SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    private static final int MODEL_TILE_WIDTH = MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X;
    private static final int MODEL_TILE_HEIGHT = MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_Y;
    private static final short[] SCRATCH = new short[MODEL_TEXTURE_SIZE * MODEL_TEXTURE_SIZE];

    private ForgeOriginalVoxyMipGen() {
    }

    static byte[][] putTextures(boolean darkened, ForgeSoftwareModelTextureBakery.FaceTexture[] textures) {
        if (MODEL_TEXTURE_SIZE > 16) {
            throw new IllegalStateException("original MipGen port assumes 16 or smaller model texture size");
        }
        int[][] levels = new int[LAYERS][];
        levels[0] = new int[MODEL_TILE_WIDTH * MODEL_TILE_HEIGHT];
        byte solidMask = 0;
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            int tileX = (face >> 1) * MODEL_TEXTURE_SIZE;
            int tileY = (face & 1) * MODEL_TEXTURE_SIZE;
            boolean anyTransparent = false;
            int pixel = 0;
            for (int colour : textures[face].colour()) {
                int x = pixel & (MODEL_TEXTURE_SIZE - 1);
                int y = pixel >> LAYERS;
                levels[0][tileX + x + (tileY + y) * MODEL_TILE_WIDTH] = colour;
                anyTransparent |= (colour & 0xFF000000) == 0;
                pixel++;
            }
            solidMask |= (anyTransparent ? 1 : 0) << face;
        }

        if (!darkened) {
            solidify(levels[0], solidMask);
        }

        int sourceWidth = MODEL_TILE_WIDTH;
        int sourceHeight = MODEL_TILE_HEIGHT;
        for (int level = 1; level < LAYERS; level++) {
            int width = sourceWidth >> 1;
            int height = sourceHeight >> 1;
            levels[level] = new int[width * height];
            int[] source = levels[level - 1];
            for (int px = 0; px < width; px++) {
                for (int py = 0; py < height; py++) {
                    int base = px * 2 + py * 2 * sourceWidth;
                    int c00 = source[base];
                    int c01 = source[base + sourceWidth];
                    int c10 = source[base + 1];
                    int c11 = source[base + sourceWidth + 1];
                    levels[level][px + py * width] = ForgeOriginalVoxyTextureUtils.mipColours(darkened, c00, c01, c10, c11);
                }
            }
            sourceWidth = width;
            sourceHeight = height;
        }

        byte[][] out = new byte[LAYERS][];
        for (int level = 0; level < LAYERS; level++) {
            out[level] = toRgbaBytes(levels[level]);
        }
        return out;
    }

    private static void solidify(int[] base, byte mask) {
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            if (((mask >> face) & 1) == 0) {
                continue;
            }
            int tileX = (face >> 1) * MODEL_TEXTURE_SIZE;
            int tileY = (face & 1) * MODEL_TEXTURE_SIZE;
            Arrays.fill(SCRATCH, (short) -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>(MODEL_TEXTURE_SIZE * MODEL_TEXTURE_SIZE);
            for (int y = 0; y < MODEL_TEXTURE_SIZE; y++) {
                for (int x = 0; x < MODEL_TEXTURE_SIZE; x++) {
                    int colour = base[getOffset(tileX, tileY, x + y * MODEL_TEXTURE_SIZE)];
                    if ((colour & 0xFF000000) != 0) {
                        int pos = x + y * MODEL_TEXTURE_SIZE;
                        SCRATCH[pos] = (short) pos;
                        queue.add(pos);
                    }
                }
            }

            while (!queue.isEmpty()) {
                int pos = queue.removeFirst();
                int x = pos & (MODEL_TEXTURE_SIZE - 1);
                int y = pos / MODEL_TEXTURE_SIZE;
                short newVal = (short) (SCRATCH[pos] + (short) 0x0100);
                for (int direction = 3; direction != -1; direction--) {
                    int d = 2 * (direction & 1) - 1;
                    int x2 = x + (((direction & 2) == 2) ? d : 0);
                    int y2 = y + (((direction & 2) == 0) ? d : 0);
                    if (x2 < 0 || x2 >= MODEL_TEXTURE_SIZE || y2 < 0 || y2 >= MODEL_TEXTURE_SIZE) {
                        continue;
                    }
                    int pos2 = x2 + y2 * MODEL_TEXTURE_SIZE;
                    if ((newVal & 0xFF00) < (SCRATCH[pos2] & 0xFF00)) {
                        SCRATCH[pos2] = newVal;
                        queue.add(pos2);
                    }
                }
            }

            for (int i = 0; i < MODEL_TEXTURE_SIZE * MODEL_TEXTURE_SIZE; i++) {
                int d = Short.toUnsignedInt(SCRATCH[i]);
                if ((d & 0xFF00) != 0) {
                    int colour = base[getOffset(tileX, tileY, d & 0xFF)] & 0x00FFFFFF;
                    base[getOffset(tileX, tileY, i)] = colour;
                }
            }
        }
    }

    private static int getOffset(int tileX, int tileY, int i) {
        tileX += i & (MODEL_TEXTURE_SIZE - 1);
        tileY += i / MODEL_TEXTURE_SIZE;
        return tileX + tileY * MODEL_TILE_WIDTH;
    }

    private static byte[] toRgbaBytes(int[] pixels) {
        byte[] out = new byte[pixels.length * ForgeModelAtlasPixelSample.BYTES_PER_PIXEL];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int offset = i * ForgeModelAtlasPixelSample.BYTES_PER_PIXEL;
            out[offset] = (byte) (pixel & 0xFF);
            out[offset + 1] = (byte) ((pixel >>> 8) & 0xFF);
            out[offset + 2] = (byte) ((pixel >>> 16) & 0xFF);
            out[offset + 3] = (byte) ((pixel >>> 24) & 0xFF);
        }
        return out;
    }
}
