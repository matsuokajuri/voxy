package me.cortex.voxy.forge;

public record ForgeVoxyBuiltSectionStats(
        String dimension,
        int chunkX,
        int chunkZ,
        int sourceCpuEntries,
        int sectionsBuilt,
        int cacheEntriesWritten,
        int emptySections,
        long totalQuads,
        long geometryBytes,
        long occupancyBytes,
        String offsetsSemantic,
        String geometryFormat,
        String offsetsSummary,
        String offsetsNamed,
        String samplePosition,
        String aabbSample,
        boolean occupancyPresent,
        boolean finalRendererFormat,
        double elapsedMs
) {
    public ForgeVoxyBuiltSectionStats withCacheEntriesWritten(int cacheEntriesWritten) {
        return new ForgeVoxyBuiltSectionStats(
                this.dimension,
                this.chunkX,
                this.chunkZ,
                this.sourceCpuEntries,
                this.sectionsBuilt,
                cacheEntriesWritten,
                this.emptySections,
                this.totalQuads,
                this.geometryBytes,
                this.occupancyBytes,
                this.offsetsSemantic,
                this.geometryFormat,
                this.offsetsSummary,
                this.offsetsNamed,
                this.samplePosition,
                this.aabbSample,
                this.occupancyPresent,
                this.finalRendererFormat,
                this.elapsedMs
        );
    }

    public ForgeVoxyBuiltSectionStats withElapsedMs(double elapsedMs) {
        return new ForgeVoxyBuiltSectionStats(
                this.dimension,
                this.chunkX,
                this.chunkZ,
                this.sourceCpuEntries,
                this.sectionsBuilt,
                this.cacheEntriesWritten,
                this.emptySections,
                this.totalQuads,
                this.geometryBytes,
                this.occupancyBytes,
                this.offsetsSemantic,
                this.geometryFormat,
                this.offsetsSummary,
                this.offsetsNamed,
                this.samplePosition,
                this.aabbSample,
                this.occupancyPresent,
                this.finalRendererFormat,
                elapsedMs
        );
    }
}
