package me.cortex.voxy.forge;

public record ForgeCpuMeshBuildStats(
        String dimension,
        int chunkX,
        int chunkZ,
        int sectionsFound,
        int sectionsBuilt,
        int cacheEntriesWritten,
        long blocksSampled,
        long bakedModelCount,
        long quads,
        long vertices,
        long tintedQuads,
        long tintLookups,
        long tintFailures,
        long solidQuads,
        long cutoutQuads,
        long translucentQuads,
        long otherLayerQuads,
        long unsupportedBlocks,
        long estimatedBytes,
        double elapsedMs
) {
    public static ForgeCpuMeshBuildStats empty(String dimension, int chunkX, int chunkZ) {
        return new ForgeCpuMeshBuildStats(dimension, chunkX, chunkZ, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0);
    }

    public ForgeCpuMeshBuildStats withCacheEntriesWritten(int cacheEntriesWritten) {
        return new ForgeCpuMeshBuildStats(
                this.dimension,
                this.chunkX,
                this.chunkZ,
                this.sectionsFound,
                this.sectionsBuilt,
                cacheEntriesWritten,
                this.blocksSampled,
                this.bakedModelCount,
                this.quads,
                this.vertices,
                this.tintedQuads,
                this.tintLookups,
                this.tintFailures,
                this.solidQuads,
                this.cutoutQuads,
                this.translucentQuads,
                this.otherLayerQuads,
                this.unsupportedBlocks,
                this.estimatedBytes,
                this.elapsedMs
        );
    }

    public String layerSummary() {
        StringBuilder builder = new StringBuilder();
        appendLayer(builder, "solid", this.solidQuads);
        appendLayer(builder, "cutout", this.cutoutQuads);
        appendLayer(builder, "translucent", this.translucentQuads);
        appendLayer(builder, "other", this.otherLayerQuads);
        return builder.length() == 0 ? "none" : builder.toString();
    }

    private static void appendLayer(StringBuilder builder, String name, long quads) {
        if (quads == 0) {
            return;
        }
        if (builder.length() != 0) {
            builder.append(',');
        }
        builder.append(name).append('=').append(quads);
    }
}
