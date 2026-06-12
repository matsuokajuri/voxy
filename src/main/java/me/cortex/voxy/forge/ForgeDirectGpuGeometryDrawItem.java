package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;

record ForgeDirectGpuGeometryDrawItem(
        int sectionId,
        long position,
        int geometryPtr,
        int recordOffset,
        int recordCount,
        float originX,
        float originY,
        float originZ,
        float scale
) {
    private static final int SECTION_SIZE = 32;

    static ForgeDirectGpuGeometryDrawItem create(int sectionId, ForgeGpuGeometryDecodedMetadata metadata, int recordOffset, int recordCount) {
        int level = Math.max(0, WorldEngine.getLevel(metadata.position()));
        int scale = 1 << Math.min(12, level);
        return new ForgeDirectGpuGeometryDrawItem(
                sectionId,
                metadata.position(),
                metadata.geometryPtr(),
                Math.max(0, recordOffset),
                Math.max(0, recordCount),
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
}
