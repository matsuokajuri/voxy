package me.cortex.voxy.forge;

import java.util.Arrays;

final class ForgeOriginalVoxyTextureUtils {
    static final int WRITE_CHECK_STENCIL = 1;
    static final int WRITE_CHECK_DEPTH = 2;
    static final int WRITE_CHECK_ALPHA = 3;
    static final int DEPTH_MODE_AVG = 1;
    static final int DEPTH_MODE_MAX = 2;
    static final int DEPTH_MODE_MIN = 3;

    private ForgeOriginalVoxyTextureUtils() {
    }

    static int getWrittenPixelCount(ForgeOriginalVoxyColourDepthTextureData texture, int checkMode) {
        int count = 0;
        for (int i = 0; i < texture.colour().length; i++) {
            count += wasPixelWritten(texture, checkMode, i) ? 1 : 0;
        }
        return count;
    }

    static boolean hasTranslucentPixel(ForgeOriginalVoxyColourDepthTextureData texture) {
        for (int i = 0; i < texture.colour().length; i++) {
            int alpha = texture.colour()[i] >>> 24;
            int depth = texture.depth()[i];
            if ((depth & 0xFF) != 0 && alpha != 0 && alpha != 255) {
                return true;
            }
        }
        return false;
    }

    static boolean isSolidWhereDrawn(ForgeOriginalVoxyColourDepthTextureData texture) {
        for (int i = 0; i < texture.colour().length; i++) {
            int alpha = texture.colour()[i] >>> 24;
            int depth = texture.depth()[i];
            if ((depth & 0xFF) != 0 && alpha != 255) {
                return false;
            }
        }
        return true;
    }

    static int computeFaceTint(ForgeOriginalVoxyColourDepthTextureData texture, int checkMode) {
        boolean allTinted = true;
        boolean someTinted = false;
        boolean wasWritten = false;
        int[] colourData = texture.colour();
        int[] depthData = texture.depth();
        for (int i = 0; i < colourData.length; i++) {
            if (!wasPixelWritten(texture, checkMode, i)) {
                continue;
            }
            if ((colourData[i] & 0xFFFFFF) == 0 || (colourData[i] >>> 24) == 0) {
                continue;
            }
            boolean pixelTinted = (depthData[i] & (1 << 7)) != 0;
            wasWritten = true;
            allTinted &= pixelTinted;
            someTinted |= pixelTinted;
        }
        if (!wasWritten) {
            return 0;
        }
        return someTinted ? (allTinted ? 3 : 2) : 1;
    }

    static float computeDepth(ForgeOriginalVoxyColourDepthTextureData texture, int mode, int checkMode) {
        int[] colourData = texture.colour();
        int[] depthData = texture.depth();
        long a = 0;
        long b = 0;
        if (mode == DEPTH_MODE_MIN) {
            a = Long.MAX_VALUE;
        }
        if (mode == DEPTH_MODE_MAX) {
            a = Long.MIN_VALUE;
        }
        for (int i = 0; i < colourData.length; i++) {
            if (!wasPixelWritten(texture, checkMode, i)) {
                continue;
            }
            int depth = depthData[i] >>> 8;
            if (mode == DEPTH_MODE_AVG) {
                a++;
                b += depth;
            } else if (mode == DEPTH_MODE_MAX) {
                a = Math.max(a, depth);
            } else if (mode == DEPTH_MODE_MIN) {
                a = Math.min(a, depth);
            }
        }
        if (mode == DEPTH_MODE_AVG) {
            return a == 0 ? -1 : u2fdepth((int) (b / a));
        } else if (mode == DEPTH_MODE_MAX) {
            return a == Long.MIN_VALUE ? -1 : u2fdepth((int) a);
        } else if (mode == DEPTH_MODE_MIN) {
            return a == Long.MAX_VALUE ? -1 : u2fdepth((int) a);
        }
        throw new IllegalArgumentException();
    }

    static long[] generateMask(ForgeOriginalVoxyColourDepthTextureData texture, int checkMode) {
        return generateMask(texture, checkMode, new long[texture.width() * texture.height() / 64]);
    }

    static long[] generateMask(ForgeOriginalVoxyColourDepthTextureData texture, int checkMode, long[] outMsk) {
        Arrays.fill(outMsk, 0L);
        int i = 0;
        for (int y = 0; y < texture.height(); y++) {
            for (int x = 0; x < texture.width(); x++) {
                if (wasPixelWritten(texture, checkMode, i)) {
                    outMsk[i / 64] |= 1L << (i & 63);
                }
                i++;
            }
        }
        return outMsk;
    }

    static int[] computeBounds(ForgeOriginalVoxyColourDepthTextureData texture, int checkMode) {
        int minX = 0;
        minXCheck:
        do {
            for (int y = 0; y < texture.height(); y++) {
                int idx = minX + (y * texture.width());
                if (wasPixelWritten(texture, checkMode, idx)) {
                    break minXCheck;
                }
            }
            minX++;
        } while (minX != texture.width());

        int maxX = texture.width() - 1;
        maxXCheck:
        do {
            for (int y = texture.height() - 1; y != -1; y--) {
                int idx = maxX + (y * texture.width());
                if (wasPixelWritten(texture, checkMode, idx)) {
                    break maxXCheck;
                }
            }
            maxX--;
        } while (maxX != -1);

        int minY = 0;
        minYCheck:
        do {
            for (int x = 0; x < texture.width(); x++) {
                int idx = (minY * texture.height()) + x;
                if (wasPixelWritten(texture, checkMode, idx)) {
                    break minYCheck;
                }
            }
            minY++;
        } while (minY != texture.height());

        int maxY = texture.height() - 1;
        maxYCheck:
        do {
            for (int x = texture.width() - 1; x != -1; x--) {
                int idx = (maxY * texture.height()) + x;
                if (wasPixelWritten(texture, checkMode, idx)) {
                    break maxYCheck;
                }
            }
            maxY--;
        } while (maxY != -1);

        return new int[]{minX, maxX, minY, maxY};
    }

    static int mipColours(boolean darkened, int c00, int c01, int c10, int c11) {
        darkened = !darkened;
        float r = 0.0F;
        float g = 0.0F;
        float b = 0.0F;
        float a = 0.0F;
        if (darkened || (c00 >>> 24) != 0) {
            r += ForgeOriginalVoxyColorSRGB.srgbToLinear(c00 & 0xFF);
            g += ForgeOriginalVoxyColorSRGB.srgbToLinear((c00 >>> 8) & 0xFF);
            b += ForgeOriginalVoxyColorSRGB.srgbToLinear((c00 >>> 16) & 0xFF);
            a += darkened ? (c00 >>> 24) : ForgeOriginalVoxyColorSRGB.srgbToLinear(c00 >>> 24);
        }
        if (darkened || (c01 >>> 24) != 0) {
            r += ForgeOriginalVoxyColorSRGB.srgbToLinear(c01 & 0xFF);
            g += ForgeOriginalVoxyColorSRGB.srgbToLinear((c01 >>> 8) & 0xFF);
            b += ForgeOriginalVoxyColorSRGB.srgbToLinear((c01 >>> 16) & 0xFF);
            a += darkened ? (c01 >>> 24) : ForgeOriginalVoxyColorSRGB.srgbToLinear(c01 >>> 24);
        }
        if (darkened || (c10 >>> 24) != 0) {
            r += ForgeOriginalVoxyColorSRGB.srgbToLinear(c10 & 0xFF);
            g += ForgeOriginalVoxyColorSRGB.srgbToLinear((c10 >>> 8) & 0xFF);
            b += ForgeOriginalVoxyColorSRGB.srgbToLinear((c10 >>> 16) & 0xFF);
            a += darkened ? (c10 >>> 24) : ForgeOriginalVoxyColorSRGB.srgbToLinear(c10 >>> 24);
        }
        if (darkened || (c11 >>> 24) != 0) {
            r += ForgeOriginalVoxyColorSRGB.srgbToLinear(c11 & 0xFF);
            g += ForgeOriginalVoxyColorSRGB.srgbToLinear((c11 >>> 8) & 0xFF);
            b += ForgeOriginalVoxyColorSRGB.srgbToLinear((c11 >>> 16) & 0xFF);
            a += darkened ? (c11 >>> 24) : ForgeOriginalVoxyColorSRGB.srgbToLinear(c11 >>> 24);
        }
        return ForgeOriginalVoxyColorSRGB.linearToSrgb(
                r / 4.0F,
                g / 4.0F,
                b / 4.0F,
                darkened ? ((int) a) / 4 : ForgeOriginalVoxyColorSRGB.linearToSrgb8(a / 4.0F)
        );
    }

    static boolean byteForByteAuditReady() {
        return ForgeOriginalVoxyColorSRGB.byteForByteAuditReady()
                && mipColours(false, 0xff000000, 0xff000000, 0xff000000, 0xff000000) == 0xff000000
                && mipColours(false, 0xffffffff, 0xffffffff, 0xffffffff, 0xffffffff) == 0xffffffff
                && mipColours(true, 0x00000000, 0xffffffff, 0xff00ff00, 0xffff0000) == 0xe1bcbc89;
    }

    private static boolean wasPixelWritten(ForgeOriginalVoxyColourDepthTextureData data, int mode, int index) {
        if (mode == WRITE_CHECK_STENCIL) {
            return (data.depth()[index] & 0xFF) != 0;
        } else if (mode == WRITE_CHECK_DEPTH) {
            return (data.depth()[index] >>> 8) != ((1 << 24) - 1);
        } else if (mode == WRITE_CHECK_ALPHA) {
            return ((data.colour()[index] >>> 24) & 0xFF) > 1;
        }
        throw new IllegalArgumentException();
    }

    private static float u2fdepth(int depth) {
        return (float) ((double) depth / ((1 << 24) - 1));
    }

}
