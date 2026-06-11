package me.cortex.voxy.forge;

import me.cortex.voxy.config.SimpleGpuMeshLoadedChunkSkipMode;

final class ForgeGpuMeshLoadedChunkFilter {
    private ForgeGpuMeshLoadedChunkFilter() {
    }

    static SkipReason evaluate(
            boolean renderLoadedChunks,
            SimpleGpuMeshLoadedChunkSkipMode skipMode,
            int loadedChunkMargin,
            int chunkX,
            int chunkZ,
            int centerChunkX,
            int centerChunkZ,
            int clientRenderDistance,
            ChunkLoadChecker chunkLoadChecker
    ) {
        if (renderLoadedChunks || skipMode == SimpleGpuMeshLoadedChunkSkipMode.DISABLED) {
            return SkipReason.NONE;
        }
        if (skipMode == SimpleGpuMeshLoadedChunkSkipMode.BY_RENDER_DISTANCE) {
            if (isInsideRenderDistance(chunkX, chunkZ, centerChunkX, centerChunkZ, clientRenderDistance, loadedChunkMargin)) {
                return SkipReason.RENDER_DISTANCE;
            }
            return SkipReason.NONE;
        }
        if (isLoaded(chunkLoadChecker, chunkX, chunkZ)) {
            return SkipReason.LOADED_STATE;
        }
        return SkipReason.NONE;
    }

    static boolean isLoaded(ChunkLoadChecker chunkLoadChecker, int chunkX, int chunkZ) {
        if (chunkLoadChecker == null) {
            return false;
        }
        try {
            return chunkLoadChecker.isLoaded(chunkX, chunkZ);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean isInsideRenderDistance(int chunkX, int chunkZ, int centerChunkX, int centerChunkZ, int renderDistance, int margin) {
        if (renderDistance < 0) {
            return false;
        }
        int distance = chunkDistance(chunkX, chunkZ, centerChunkX, centerChunkZ);
        return distance <= renderDistance + Math.max(0, margin);
    }

    static int chunkDistance(int chunkX, int chunkZ, int centerChunkX, int centerChunkZ) {
        return Math.max(Math.abs(chunkX - centerChunkX), Math.abs(chunkZ - centerChunkZ));
    }

    enum SkipReason {
        NONE,
        LOADED_STATE,
        RENDER_DISTANCE
    }

    @FunctionalInterface
    interface ChunkLoadChecker {
        boolean isLoaded(int chunkX, int chunkZ);
    }
}
