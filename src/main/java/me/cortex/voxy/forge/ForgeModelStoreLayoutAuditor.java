package me.cortex.voxy.forge;

final class ForgeModelStoreLayoutAuditor {
    private long layoutAuditRuns;
    private long layoutAuditFailures;
    private ForgeModelStoreLayoutAuditResult lastResult = ForgeModelStoreLayoutAuditResult.failure("none", 0.0D);

    ForgeModelStoreLayoutAuditResult audit() {
        this.layoutAuditRuns++;
        long start = System.nanoTime();
        boolean recordBytesMatch = ForgeOriginalVoxyModelStoreLayoutSpec.MODEL_RECORD_BYTES == ForgeModelStoreLayout.BYTES;
        boolean success = recordBytesMatch
                && ForgeOriginalVoxyModelStoreLayoutSpec.FORMAL_LAYOUT_KNOWN
                && ForgeOriginalVoxyModelStoreLayoutSpec.FACE_DATA_LAYOUT_KNOWN
                && ForgeOriginalVoxyModelStoreLayoutSpec.FLAGS_LAYOUT_KNOWN
                && ForgeOriginalVoxyModelStoreLayoutSpec.COLOUR_TINT_LAYOUT_KNOWN
                && ForgeOriginalVoxyModelStoreLayoutSpec.CUSTOM_ID_LAYOUT_KNOWN;
        ForgeModelStoreLayoutAuditResult result = new ForgeModelStoreLayoutAuditResult(
                success,
                success ? "none" : "layout-field-mapping-incomplete",
                elapsedMs(start),
                ForgeOriginalVoxyModelStoreLayoutSpec.LAYOUT_VERSION,
                ForgeOriginalVoxyModelStoreLayoutSpec.MODEL_RECORD_BYTES,
                ForgeModelStoreLayout.BYTES,
                ForgeOriginalVoxyModelStoreLayoutSpec.FORMAL_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.knownFieldCount(),
                ForgeOriginalVoxyModelStoreLayoutSpec.unknownFieldCount(),
                ForgeOriginalVoxyModelStoreLayoutSpec.FACE_DATA_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.FLAGS_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.COLOUR_TINT_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.CUSTOM_ID_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.ATLAS_UV_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.MATERIAL_LAYOUT_KNOWN,
                ForgeOriginalVoxyModelStoreLayoutSpec.FIELD_MAPPING_READY,
                false,
                "placeholder has modelId/blockStateId/debug-colour only; formal record needs faceData[6], flagsA, colourTint, customId, atlas tile ownership, and real tint/UV data"
        );
        this.lastResult = result;
        if (!result.success()) {
            this.layoutAuditFailures++;
        }
        return result;
    }

    ForgeModelStoreLayoutAuditResult lastResult() {
        return this.lastResult;
    }

    long layoutAuditRuns() {
        return this.layoutAuditRuns;
    }

    long layoutAuditFailures() {
        return this.layoutAuditFailures;
    }

    void clear() {
        this.layoutAuditRuns = 0L;
        this.layoutAuditFailures = 0L;
        this.lastResult = ForgeModelStoreLayoutAuditResult.failure("none", 0.0D);
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }
}
