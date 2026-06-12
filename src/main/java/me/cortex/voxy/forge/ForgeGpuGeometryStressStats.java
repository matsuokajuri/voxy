package me.cortex.voxy.forge;

public record ForgeGpuGeometryStressStats(
        long stressRuns,
        long stressFailures,
        String lastStressError,
        String lastStressStartedAt,
        String lastStressFinishedAt,
        double lastStressDurationMs,
        long lastBeforeClearUploadedSections,
        long lastAfterReenableUploadedSections,
        long lastBeforeClearGeometryBytes,
        long lastAfterReenableGeometryBytes,
        boolean lastValidationAfterReenableMetadataMatch,
        boolean lastValidationAfterReenableGeometryMatch
) {
    public static ForgeGpuGeometryStressStats empty() {
        return new ForgeGpuGeometryStressStats(
                0,
                0,
                "none",
                "none",
                "none",
                0.0D,
                0,
                0,
                0,
                0,
                false,
                false
        );
    }
}
