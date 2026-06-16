package me.cortex.voxy.forge;

record ForgeTexturedReadbackMesh(
        boolean success,
        String reason,
        boolean geometryHeapReady,
        boolean metadataReady,
        boolean geometryBacked,
        boolean fallbackFixedQuad,
        int sampleModelId,
        int sourceBlockStateId,
        String sourceBlockState,
        String sourceSprite,
        String sourceSpriteAtlas,
        int matchingSections,
        int matchingRecords,
        int builtQuads,
        int builtVertices,
        float[] vertices,
        boolean screenSpace
) {
    static ForgeTexturedReadbackMesh failure(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "unknown" : reason;
        return new ForgeTexturedReadbackMesh(
                false,
                safeReason,
                false,
                false,
                false,
                false,
                -1,
                -1,
                "none",
                "none",
                "none",
                0,
                0,
                0,
                0,
                new float[0],
                false
        );
    }
}
