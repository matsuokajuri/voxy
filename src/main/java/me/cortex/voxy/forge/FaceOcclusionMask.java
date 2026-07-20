package me.cortex.voxy.forge;

import java.util.Arrays;

/**
 * Exact 16x16 software-bakery coverage for all six model faces.
 *
 * <p>The original bakery view matrices deliberately orient each opposite-face pair in the
 * same raster coordinate system. A target face and the touching face of its neighbour can
 * therefore be compared word-for-word without a mirror or rotation.</p>
 */
final class FaceOcclusionMask {
    static final int RESOLUTION = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    static final int WORDS_PER_FACE = RESOLUTION * RESOLUTION / Long.SIZE;
    static final int WORDS_PER_MODEL = WORDS_PER_FACE * ForgeModelAtlasLayout.FACE_COUNT;

    private FaceOcclusionMask() {
    }

    static long[] fromTextures(ColourDepthTextureData[] textures, int checkMode) {
        if (textures == null || textures.length != ForgeModelAtlasLayout.FACE_COUNT) {
            throw new IllegalArgumentException("Original Voxy occlusion data requires exactly six faces");
        }
        long[] modelMask = new long[WORDS_PER_MODEL];
        long[] faceMask = new long[WORDS_PER_FACE];
        for (int face = 0; face < textures.length; face++) {
            ColourDepthTextureData texture = textures[face];
            if (texture == null) {
                continue;
            }
            if (texture.width() != RESOLUTION || texture.height() != RESOLUTION) {
                throw new IllegalArgumentException(
                        "Original Voxy occlusion face must be " + RESOLUTION + "x" + RESOLUTION);
            }
            TextureUtils.generateMask(texture, checkMode, faceMask);
            System.arraycopy(faceMask, 0, modelMask, faceOffset(face), WORDS_PER_FACE);
        }
        return modelMask;
    }

    static boolean covers(long[] occluderModel, int occluderFace, long[] targetModel, int targetFace) {
        if (!isModelMask(occluderModel) || !isModelMask(targetModel)
                || !isFace(occluderFace) || !isFace(targetFace)) {
            return false;
        }
        int occluderOffset = faceOffset(occluderFace);
        int targetOffset = faceOffset(targetFace);
        boolean targetHasPixels = false;
        for (int word = 0; word < WORDS_PER_FACE; word++) {
            long target = targetModel[targetOffset + word];
            targetHasPixels |= target != 0L;
            if ((target & ~occluderModel[occluderOffset + word]) != 0L) {
                return false;
            }
        }
        return targetHasPixels;
    }

    static boolean isFullFace(long[] modelMask, int face) {
        if (!isModelMask(modelMask) || !isFace(face)) {
            return false;
        }
        int offset = faceOffset(face);
        for (int word = 0; word < WORDS_PER_FACE; word++) {
            if (modelMask[offset + word] != -1L) {
                return false;
            }
        }
        return true;
    }

    static long[] copy(long[] modelMask) {
        if (!isModelMask(modelMask)) {
            throw new IllegalArgumentException("Invalid Original Voxy model occlusion mask");
        }
        return Arrays.copyOf(modelMask, modelMask.length);
    }

    static int faceOffset(int face) {
        if (!isFace(face)) {
            throw new IllegalArgumentException("Invalid Original Voxy face index: " + face);
        }
        return face * WORDS_PER_FACE;
    }

    private static boolean isModelMask(long[] modelMask) {
        return modelMask != null && modelMask.length == WORDS_PER_MODEL;
    }

    private static boolean isFace(int face) {
        return face >= 0 && face < ForgeModelAtlasLayout.FACE_COUNT;
    }
}
