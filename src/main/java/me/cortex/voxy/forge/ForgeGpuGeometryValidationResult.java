package me.cortex.voxy.forge;

import java.util.Arrays;

public record ForgeGpuGeometryValidationResult(
        boolean success,
        String reason,
        int sectionId,
        int geometryPtr,
        int recordCount,
        boolean metadataMatch,
        boolean geometryMatch,
        int expectedMetadataHash,
        int actualMetadataHash,
        int expectedGeometrySampleHash,
        int actualGeometrySampleHash,
        String expectedMetadataWords,
        String actualMetadataWords,
        String expectedGeometryRecords,
        String actualGeometryRecords
) {
    public static ForgeGpuGeometryValidationResult failure(String reason) {
        return new ForgeGpuGeometryValidationResult(
                false,
                reason == null ? "unknown" : reason,
                -1,
                -1,
                0,
                false,
                false,
                0,
                0,
                0,
                0,
                "none",
                "none",
                "none",
                "none"
        );
    }

    public static ForgeGpuGeometryValidationResult success(
            int sectionId,
            int geometryPtr,
            int[] expectedMetadata,
            int[] actualMetadata,
            long[] expectedGeometry,
            long[] actualGeometry
    ) {
        boolean metadataMatch = Arrays.equals(expectedMetadata, actualMetadata);
        boolean geometryMatch = Arrays.equals(expectedGeometry, actualGeometry);
        return new ForgeGpuGeometryValidationResult(
                metadataMatch && geometryMatch,
                metadataMatch && geometryMatch ? "ok" : "mismatch",
                sectionId,
                geometryPtr,
                expectedGeometry == null ? 0 : expectedGeometry.length,
                metadataMatch,
                geometryMatch,
                Arrays.hashCode(expectedMetadata),
                Arrays.hashCode(actualMetadata),
                Arrays.hashCode(expectedGeometry),
                Arrays.hashCode(actualGeometry),
                formatMetadata(expectedMetadata),
                formatMetadata(actualMetadata),
                formatGeometry(expectedGeometry),
                formatGeometry(actualGeometry)
        );
    }

    private static String formatMetadata(int[] words) {
        if (words == null || words.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < words.length; i++) {
            if (i != 0) {
                builder.append(',');
            }
            builder.append(String.format("0x%08X", words[i]));
        }
        return builder.append(']').toString();
    }

    private static String formatGeometry(long[] records) {
        if (records == null || records.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < records.length; i++) {
            if (i != 0) {
                builder.append(',');
            }
            builder.append(String.format("0x%016X", records[i]));
        }
        return builder.append(']').toString();
    }
}
