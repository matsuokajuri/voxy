package me.cortex.voxy.forge;

abstract class ForgeOriginalVoxyModelQueries {
    static boolean faceExists(long metadata, int face) {
        return ((metadata >> (8 * face)) & 0xFF) != 0xFF;
    }

    static boolean faceCanBeOccluded(long metadata, int face) {
        return ((metadata >> (8 * face)) & 0b100) == 0b100;
    }

    static boolean faceOccludes(long metadata, int face) {
        return faceExists(metadata, face) && ((metadata >> (8 * face)) & 0b1) == 0b1;
    }

    static boolean faceUsesSelfLighting(long metadata, int face) {
        return ((metadata >> (8 * face)) & 0b1000) != 0;
    }

    static long _isDoubleSided(long metadata) {
        return (metadata >> (8 * 6 + 2)) & 1L;
    }

    static long _isTranslucent(long metadata) {
        return (metadata >> (8 * 6 + 1)) & 1L;
    }

    static boolean containsFluid(long metadata) {
        return ((metadata >> (8 * 6)) & 8) != 0;
    }

    static long _containsFluid(long metadata) {
        return (metadata >> (8 * 6 + 3)) & 1L;
    }

    static long _isFluid(long metadata) {
        return (metadata >> (8 * 6 + 4)) & 1L;
    }

    static long _notIsBiomeColoured(long metadata) {
        return ((~metadata) >> (8 * 6)) & 1L;
    }

    static boolean cullsSame(long metadata) {
        return ((metadata >> (8 * 6)) & 32) != 0;
    }

    static boolean isFullyOpaque(long metadata) {
        return ((metadata >> (8 * 6)) & 64) != 0;
    }

    static long _isFullyOpaque(long metadata) {
        return (metadata >> (8 * 6 + 6)) & 1L;
    }

    static long lightEmission(long meta) {
        return (meta >> (8 * 6 + 7)) & 0xFL;
    }
}
