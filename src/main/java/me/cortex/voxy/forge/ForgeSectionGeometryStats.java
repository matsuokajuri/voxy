package me.cortex.voxy.forge;

public record ForgeSectionGeometryStats(
        String activeDimension,
        int activeSections,
        int maxSections,
        long usedGeometryItems,
        long usedGeometryBytes,
        long arenaSizeItems,
        long arenaLimitItems,
        int uploadIntents,
        int removeIntents,
        int dirtyMetadataIds,
        int metadataSlots,
        long totalUploads,
        long totalRemoves,
        long totalReplacements,
        long clearCount,
        int sampleSectionId,
        String sampleMetadataWords,
        String sampleMetadataDecoded
) {
}
