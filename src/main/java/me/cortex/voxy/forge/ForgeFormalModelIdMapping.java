package me.cortex.voxy.forge;

record ForgeFormalModelIdMapping(
        int blockStateId,
        int formalModelId,
        String blockState,
        String metadataCacheEntry,
        int fluidStateLutValue,
        String modelTextureKey
) {
}
