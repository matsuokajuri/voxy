package me.cortex.voxy.forge;

public record ForgeVoxyBuiltSectionStats(
        String dimension,
        int chunkX,
        int chunkZ,
        int sourceCpuEntries,
        int sectionsBuilt,
        int cacheEntriesWritten,
        long totalQuads,
        long geometryBytes,
        String offsetsSummary,
        String aabbSample,
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
                this.totalQuads,
                this.geometryBytes,
                this.offsetsSummary,
                this.aabbSample,
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
                this.totalQuads,
                this.geometryBytes,
                this.offsetsSummary,
                this.aabbSample,
                this.finalRendererFormat,
                elapsedMs
        );
    }
}
