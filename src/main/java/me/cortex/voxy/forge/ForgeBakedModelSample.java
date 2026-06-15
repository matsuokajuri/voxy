package me.cortex.voxy.forge;

record ForgeBakedModelSample(
        int modelId,
        int blockStateId,
        String blockState,
        boolean fluidLike,
        boolean airLike,
        String bakedModelClass,
        String renderLayer,
        int quadCount,
        ForgeBakedQuadSample quadSample,
        boolean hasRealModelMetadata,
        boolean hasAtlasUpload,
        String note
) {
    static ForgeBakedModelSample missing(int modelId, int blockStateId, String blockState, String note) {
        return new ForgeBakedModelSample(
                modelId,
                blockStateId,
                blockState,
                false,
                false,
                "missing",
                "unknown",
                0,
                ForgeBakedQuadSample.none(),
                false,
                false,
                note
        );
    }
}
