package me.cortex.voxy.forge;

record ForgeOriginalUploadedModelSummary(
        int blockStateId,
        String blockState,
        int originalModelId,
        String dedupeSignature,
        String primarySprite,
        int modelRecordBytes,
        long voxyMetadata,
        int fluidModelId
) {
}
