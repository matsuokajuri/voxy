package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;

record ForgeDirectGpuGeometryDrawItem(
        int sectionId,
        long position,
        int geometryPtr,
        int recordOffset,
        int recordCount,
        int bucketMask,
        int distanceChunks,
        float originX,
        float originY,
        float originZ,
        float scale
) {
    private static final int SECTION_SIZE = 32;

    static ForgeDirectGpuGeometryDrawItem create(int sectionId, ForgeGpuGeometryDecodedMetadata metadata, int recordOffset, int recordCount, int distanceChunks) {
        int level = Math.max(0, WorldEngine.getLevel(metadata.position()));
        int scale = 1 << Math.min(12, level);
        return new ForgeDirectGpuGeometryDrawItem(
                sectionId,
                metadata.position(),
                metadata.geometryPtr(),
                Math.max(0, recordOffset),
                Math.max(0, recordCount),
                bucketMask(metadata),
                Math.max(0, distanceChunks),
                WorldEngine.getX(metadata.position()) * (float) SECTION_SIZE * scale,
                WorldEngine.getY(metadata.position()) * (float) SECTION_SIZE * scale,
                WorldEngine.getZ(metadata.position()) * (float) SECTION_SIZE * scale,
                scale
        );
    }

    int baseRecord() {
        return this.geometryPtr + this.recordOffset;
    }

    int vertexCount() {
        return this.recordCount * 6;
    }

    private static int bucketMask(ForgeGpuGeometryDecodedMetadata metadata) {
        int mask = 0;
        for (int bucket = 0; bucket < ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT; bucket++) {
            int start = metadata.offsets()[bucket];
            int end = bucket == ForgeGpuGeometryDecodedMetadata.BUCKET_COUNT - 1
                    ? metadata.itemCount()
                    : metadata.offsets()[bucket + 1];
            if (end > start) {
                mask |= 1 << bucket;
            }
        }
        return mask;
    }
}
