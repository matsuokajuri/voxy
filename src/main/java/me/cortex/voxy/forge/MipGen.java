package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.bytes.ByteArrayFIFOQueue;
import me.cortex.voxy.common.util.MemoryBuffer;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayDeque;
import java.util.Arrays;

final class MipGen {
    private static final int MODEL_TEXTURE_SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    private static final int MODEL_TILE_WIDTH = MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X;
    private static final int MODEL_TILE_HEIGHT = MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_Y;
    static final int LAYERS = Integer.numberOfTrailingZeros(MODEL_TEXTURE_SIZE);
    static final long UPLOADED_MIP_CHAIN_BYTES = uploadedMipChainBytes();
    static final long ORIGINAL_MODEL_TEXTURE_BUFFER_BYTES = (long) ForgeModelAtlasLayout.FACES_PER_MODEL_Y
            * ForgeModelAtlasLayout.FACES_PER_MODEL_X
            * computeSizeWithMips(MODEL_TEXTURE_SIZE)
            * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL;

    private static final short[] SCRATCH = new short[MODEL_TEXTURE_SIZE * MODEL_TEXTURE_SIZE];
    private static final ByteArrayFIFOQueue QUEUE = new ByteArrayFIFOQueue(MODEL_TEXTURE_SIZE * MODEL_TEXTURE_SIZE);

    private MipGen() {
    }

    static byte[][] putTextures(boolean darkened, ColourDepthTextureData[] textures) {
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
                    levels[level][px + py * width] = TextureUtils.mipColours(darkened, c00, c01, c10, c11);
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

    static MemoryBuffer putTexturesBuffer(boolean darkened, ColourDepthTextureData[] textures) {
        MemoryBuffer buffer = new MemoryBuffer(ORIGINAL_MODEL_TEXTURE_BUFFER_BYTES).zero();
        putTextures(darkened, textures, buffer);
        return buffer;
    }

    static void putTextures(boolean darkened, ColourDepthTextureData[] textures, MemoryBuffer into) {
        if (MODEL_TEXTURE_SIZE > 16) {
            throw new IllegalStateException("original MipGen port assumes 16 or smaller model texture size");
        }
        if (into.size < UPLOADED_MIP_CHAIN_BYTES) {
            throw new IllegalArgumentException("model texture mip buffer too small");
        }

        long addr = into.address;
        byte solidMask = 0;
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            int x = (face >> 1) * MODEL_TEXTURE_SIZE;
            int y = (face & 1) * MODEL_TEXTURE_SIZE;
            int pixel = 0;
            boolean anyTransparent = false;
            for (int colour : textures[face].colour()) {
                int offset = ((y + (pixel >> LAYERS)) * MODEL_TILE_WIDTH
                        + ((pixel & (MODEL_TEXTURE_SIZE - 1)) + x)) * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL;
                MemoryUtil.memPutInt(addr + offset, colour);
                anyTransparent |= (colour & 0xFF000000) == 0;
                pixel++;
            }
            solidMask |= (anyTransparent ? 1 : 0) << face;
        }

        if (!darkened) {
            solidify(addr, solidMask);
        }

        long destAddr = addr;
        for (int level = 0; level < LAYERS - 1; level++) {
            long sourceAddr = destAddr;
            destAddr += ((long) MODEL_TEXTURE_SIZE * MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X
                    * ForgeModelAtlasLayout.FACES_PER_MODEL_Y
                    * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL) >> (level << 1);
            int width = MODEL_TILE_WIDTH >> (level + 1);
            int sourceWidth = MODEL_TILE_WIDTH >> level;
            int height = MODEL_TILE_HEIGHT >> (level + 1);
            for (int px = 0; px < width; px++) {
                for (int py = 0; py < height; py++) {
                    long base = sourceAddr + (px * 2L + py * 2L * sourceWidth) * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL;
                    int c00 = MemoryUtil.memGetInt(base);
                    int c01 = MemoryUtil.memGetInt(base + (long) sourceWidth * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL);
                    int c10 = MemoryUtil.memGetInt(base + ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL);
                    int c11 = MemoryUtil.memGetInt(base + ((long) sourceWidth + 1L) * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL);
                    MemoryUtil.memPutInt(
                            destAddr + (px + py * (long) width) * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL,
                            TextureUtils.mipColours(darkened, c00, c01, c10, c11));
                }
            }
        }
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

    private static void solidify(long baseAddr, byte mask) {
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            if (((mask >> face) & 1) == 0) {
                continue;
            }
            int tileX = (face >> 1) * MODEL_TEXTURE_SIZE;
            int tileY = (face & 1) * MODEL_TEXTURE_SIZE;
            long faceAddr = baseAddr + (long) (tileX + tileY * MODEL_TILE_WIDTH) * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL;
            Arrays.fill(SCRATCH, (short) -1);
            QUEUE.clear();
            for (int y = 0; y < MODEL_TEXTURE_SIZE; y++) {
                for (int x = 0; x < MODEL_TEXTURE_SIZE; x++) {
                    int colour = MemoryUtil.memGetInt(faceAddr + (long) (x + y * MODEL_TILE_WIDTH) * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL);
                    if ((colour & 0xFF000000) != 0) {
                        int pos = x + y * MODEL_TEXTURE_SIZE;
                        SCRATCH[pos] = (short) pos;
                        QUEUE.enqueue((byte) pos);
                    }
                }
            }

            while (!QUEUE.isEmpty()) {
                int pos = Byte.toUnsignedInt(QUEUE.dequeueByte());
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
                        QUEUE.enqueue((byte) pos2);
                    }
                }
            }

            for (int i = 0; i < MODEL_TEXTURE_SIZE * MODEL_TEXTURE_SIZE; i++) {
                int d = Short.toUnsignedInt(SCRATCH[i]);
                if ((d & 0xFF00) != 0) {
                    int colour = MemoryUtil.memGetInt(baseAddr + getOffset(tileX, tileY, d & 0xFF) * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL) & 0x00FFFFFF;
                    MemoryUtil.memPutInt(baseAddr + getOffset(tileX, tileY, i) * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL, colour);
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
        byte[] out = new byte[pixels.length * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int offset = i * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL;
            out[offset] = (byte) (pixel & 0xFF);
            out[offset + 1] = (byte) ((pixel >>> 8) & 0xFF);
            out[offset + 2] = (byte) ((pixel >>> 16) & 0xFF);
            out[offset + 3] = (byte) ((pixel >>> 24) & 0xFF);
        }
        return out;
    }

    private static long uploadedMipChainBytes() {
        long bytes = 0L;
        for (int level = 0; level < LAYERS; level++) {
            bytes += ((long) MODEL_TILE_WIDTH * MODEL_TILE_HEIGHT * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL) >> (level << 1);
        }
        return bytes;
    }

    private static int computeSizeWithMips(int size) {
        int total = 0;
        for (; size != 0; size >>= 1) {
            total += size * size;
        }
        return total;
    }
}
