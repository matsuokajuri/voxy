package me.cortex.voxy.forge;

final class ForgeDirectGpuGeometryRenderState {
    static final String STAGE = "G5_0_SKELETON_ONLY";

    private boolean initialized;
    private int plannedSections;
    private long plannedRecords;
    private int uploadedSectionCandidates;
    private int invalidMetadata;
    private double lastPlanDurationMs;
    private String lastPlanError = "none";
    private String lastSkippedReason = "none";
    private long planRuns;
    private long planFailures;
    private long clearCount;

    void markInitialized() {
        this.initialized = true;
    }

    void recordPlan(ForgeDirectGpuGeometryDrawPlanner.PlanResult result) {
        this.initialized = true;
        this.planRuns++;
        this.plannedSections = result.plannedSections();
        this.plannedRecords = result.plannedRecords();
        this.uploadedSectionCandidates = result.uploadedSectionCandidates();
        this.invalidMetadata = result.invalidMetadata();
        this.lastPlanDurationMs = result.durationMs();
        this.lastPlanError = result.success() ? "none" : result.error();
        this.lastSkippedReason = result.skippedReason();
        if (!result.success()) {
            this.planFailures++;
        }
    }

    void clear() {
        this.initialized = false;
        this.plannedSections = 0;
        this.plannedRecords = 0;
        this.uploadedSectionCandidates = 0;
        this.invalidMetadata = 0;
        this.lastPlanDurationMs = 0.0D;
        this.lastPlanError = "none";
        this.lastSkippedReason = "none";
        this.planRuns = 0;
        this.planFailures = 0;
        this.clearCount++;
    }

    boolean initialized() {
        return this.initialized;
    }

    int plannedSections() {
        return this.plannedSections;
    }

    long plannedRecords() {
        return this.plannedRecords;
    }

    int uploadedSectionCandidates() {
        return this.uploadedSectionCandidates;
    }

    int invalidMetadata() {
        return this.invalidMetadata;
    }

    double lastPlanDurationMs() {
        return this.lastPlanDurationMs;
    }

    String lastPlanError() {
        return this.lastPlanError;
    }

    String lastSkippedReason() {
        return this.lastSkippedReason;
    }

    long planRuns() {
        return this.planRuns;
    }

    long planFailures() {
        return this.planFailures;
    }

    long clearCount() {
        return this.clearCount;
    }
}
