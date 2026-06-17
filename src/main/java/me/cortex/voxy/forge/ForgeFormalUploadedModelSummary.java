package me.cortex.voxy.forge;

record ForgeFormalUploadedModelSummary(
        int blockStateId,
        String blockState,
        int formalModelId,
        String dedupeSignature,
        String primarySprite,
        int modelRecordBytes
) {
}
