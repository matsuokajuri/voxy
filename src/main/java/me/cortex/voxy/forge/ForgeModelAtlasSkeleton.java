package me.cortex.voxy.forge;

final class ForgeModelAtlasSkeleton {
    static final String STAGE = "G6_14_MODEL_ATLAS_OWNERSHIP_SKELETON";

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long clearRuns;
    private long auditRuns;
    private long auditFailures;
    private String lastBuildError = "none";
    private double lastBuildDurationMs;
    private boolean atlasSkeletonReady;
    private boolean atlasSkeletonStale;
    private boolean lastReloadInvalidatedAtlasSkeleton;
    private Sample sample = Sample.empty();
    private ForgeModelAtlasAuditResult lastAudit = ForgeModelAtlasAuditResult.failure("none", 0.0D);

    ForgeModelAtlasSkeleton(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeModelAtlasStats build() {
        this.buildRuns++;
        long start = System.nanoTime();
        ForgeRealModelStoreSampleStats realSample = this.instance.getRealModelStoreSample().createStatusSnapshot();
        this.sample = sampleFrom(realSample);
        this.atlasSkeletonReady = true;
        this.atlasSkeletonStale = false;
        this.lastReloadInvalidatedAtlasSkeleton = false;
        this.lastBuildError = "none";
        this.lastBuildDurationMs = elapsedMs(start);
        this.lastAudit = ForgeModelAtlasAuditResult.failure("none", 0.0D);
        return this.createStatusSnapshot();
    }

    ForgeModelAtlasAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        int invalidLayout = 0;
        int invalidModelCoordinate = 0;
        int invalidFaceTileCoordinate = 0;

        if (ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE != 16
                || ForgeModelAtlasLayout.FACES_PER_MODEL_X != 3
                || ForgeModelAtlasLayout.FACES_PER_MODEL_Y != 2
                || ForgeModelAtlasLayout.MODEL_GRID_WIDTH != 256
                || ForgeModelAtlasLayout.MODEL_GRID_HEIGHT != 256
                || ForgeModelAtlasLayout.ATLAS_WIDTH != 12288
                || ForgeModelAtlasLayout.ATLAS_HEIGHT != 8192) {
            invalidLayout++;
        }

        if (this.sample.modelId() >= 0) {
            if (!ForgeModelAtlasLayout.isValidModelId(this.sample.modelId())) {
                invalidModelCoordinate++;
            } else {
                ForgeModelAtlasLayout.Tile base = ForgeModelAtlasLayout.modelBaseTile(this.sample.modelId());
                if (!ForgeModelAtlasLayout.isValidFaceTile(base)) {
                    invalidModelCoordinate++;
                }
                for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
                    if (!ForgeModelAtlasLayout.isValidFaceTile(ForgeModelAtlasLayout.faceTile(this.sample.modelId(), face))) {
                        invalidFaceTileCoordinate++;
                    }
                }
            }
        }

        boolean success = this.atlasSkeletonReady
                && invalidLayout == 0
                && invalidModelCoordinate == 0
                && invalidFaceTileCoordinate == 0;
        ForgeModelAtlasAuditResult result = new ForgeModelAtlasAuditResult(
                success,
                success ? "none" : "atlas-skeleton-layout-mismatch",
                elapsedMs(start),
                invalidLayout,
                invalidModelCoordinate,
                invalidFaceTileCoordinate
        );
        this.lastAudit = result;
        if (!result.success()) {
            this.auditFailures++;
        }
        return result;
    }

    ForgeModelAtlasStats createStatusSnapshot() {
        ForgeModelAtlasLayout.Tile base = this.sample.modelId() < 0
                ? new ForgeModelAtlasLayout.Tile(-1, -1)
                : ForgeModelAtlasLayout.modelBaseTile(this.sample.modelId());
        return new ForgeModelAtlasStats(
                STAGE,
                this.buildRuns,
                this.clearRuns,
                this.auditRuns,
                this.auditFailures,
                this.lastBuildError,
                this.lastBuildDurationMs,
                this.atlasSkeletonReady,
                this.atlasSkeletonReady,
                this.atlasSkeletonReady,
                false,
                false,
                0,
                0,
                ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE,
                ForgeModelAtlasLayout.MODEL_GRID_WIDTH,
                ForgeModelAtlasLayout.MODEL_GRID_HEIGHT,
                ForgeModelAtlasLayout.ATLAS_WIDTH,
                ForgeModelAtlasLayout.ATLAS_HEIGHT,
                ForgeModelAtlasLayout.FACES_PER_MODEL_X,
                ForgeModelAtlasLayout.FACES_PER_MODEL_Y,
                ForgeModelAtlasLayout.FACE_TILE_ORDER_KNOWN,
                false,
                false,
                false,
                false,
                false,
                this.atlasSkeletonStale,
                this.lastReloadInvalidatedAtlasSkeleton,
                ForgeModelAtlasLayout.LAYOUT_VERSION,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs(),
                this.lastAudit.invalidLayout(),
                this.lastAudit.invalidModelCoordinate(),
                this.lastAudit.invalidFaceTileCoordinate(),
                this.sample.modelId(),
                this.sample.blockState(),
                this.sample.sprite(),
                this.sample.spriteAtlas(),
                base.x(),
                base.y(),
                faceTileString(0),
                faceTileString(1),
                faceTileString(2),
                faceTileString(3),
                faceTileString(4),
                faceTileString(5)
        );
    }

    ForgeModelAtlasAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    String dumpSample() {
        ForgeModelAtlasStats status = this.createStatusSnapshot();
        return String.format(
                "Voxy model atlas skeleton sample: sampleModelId=%d sourceBlockState=\"%s\" sourceSprite=%s minecraftSpriteAtlas=%s voxyAtlasBaseX=%d voxyAtlasBaseY=%d face0Tile=%s face1Tile=%s face2Tile=%s face3Tile=%s face4Tile=%s face5Tile=%s modelTextureSize=%d facesPerModelX=%d facesPerModelY=%d modelGrid=%dx%d atlas=%dx%d faceTileOrderKnown=%s atlasPixelsUploaded=false minecraftAtlasReadable=%s realTextureDataReady=false customAtlasUploadReady=false formalTextureAtlasReady=false formalModelBridgeReady=false draw=false",
                status.sampleModelId(),
                status.sourceBlockState(),
                status.sourceSprite(),
                status.minecraftSpriteAtlas(),
                status.voxyAtlasBaseX(),
                status.voxyAtlasBaseY(),
                status.face0Tile(),
                status.face1Tile(),
                status.face2Tile(),
                status.face3Tile(),
                status.face4Tile(),
                status.face5Tile(),
                status.modelTextureSize(),
                status.facesPerModelX(),
                status.facesPerModelY(),
                status.modelGridWidth(),
                status.modelGridHeight(),
                status.atlasWidth(),
                status.atlasHeight(),
                status.faceTileOrderKnown(),
                !"none".equals(status.minecraftSpriteAtlas())
        );
    }

    void markStale(String reason) {
        this.atlasSkeletonReady = false;
        this.atlasSkeletonStale = true;
        this.lastReloadInvalidatedAtlasSkeleton = true;
        this.lastBuildError = reason == null || reason.isBlank() ? "stale" : reason;
        this.lastAudit = ForgeModelAtlasAuditResult.failure(this.lastBuildError, 0.0D);
    }

    void clear() {
        this.clearRuns++;
        this.atlasSkeletonReady = false;
        this.atlasSkeletonStale = false;
        this.lastReloadInvalidatedAtlasSkeleton = false;
        this.lastBuildError = "none";
        this.lastBuildDurationMs = 0.0D;
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.sample = Sample.empty();
        this.lastAudit = ForgeModelAtlasAuditResult.failure("none", 0.0D);
    }

    private static Sample sampleFrom(ForgeRealModelStoreSampleStats status) {
        if (status.realModelRecordSampleReady() && ForgeModelAtlasLayout.isValidModelId(status.sourceModelId())) {
            return new Sample(
                    status.sourceModelId(),
                    status.sourceBlockState(),
                    status.sourceSprite(),
                    status.sourceSpriteAtlas()
            );
        }
        return Sample.empty();
    }

    private String faceTileString(int faceIndex) {
        if (this.sample.modelId() < 0) {
            return "none";
        }
        return ForgeModelAtlasLayout.faceTile(this.sample.modelId(), faceIndex).format();
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private record Sample(int modelId, String blockState, String sprite, String spriteAtlas) {
        static Sample empty() {
            return new Sample(-1, "none", "none", "none");
        }
    }
}
