package me.cortex.voxy.forge;

final class ForgeDirectGpuGeometryRenderState {
    static final String STAGE = "G5_1_MINIMAL_DIRECT_DRAW";

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
    private int drawItems;
    private long drawListRecords;
    private long drawListVertices;
    private boolean drawListValid;
    private long drawListBuildRuns;
    private long drawListBuildFailures;
    private double lastDrawListBuildDurationMs;
    private String lastDrawListError = "none";
    private String lastDrawListSkippedReason = "none";
    private long drawCallsIssued;
    private long verticesDrawn;
    private int lastFrameDrawCalls;
    private long lastFrameVertices;
    private double lastDrawDurationMs;
    private String lastDrawError = "none";
    private String lastGlError = "none";

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

    void recordDrawList(ForgeDirectGpuGeometryRenderer.DrawListBuildResult result) {
        this.initialized = true;
        this.drawListBuildRuns++;
        this.uploadedSectionCandidates = result.uploadedSectionCandidates();
        this.invalidMetadata = result.invalidMetadata();
        this.plannedSections = result.drawItems();
        this.plannedRecords = result.drawRecords();
        this.drawItems = result.drawItems();
        this.drawListRecords = result.drawRecords();
        this.drawListVertices = result.drawVertices();
        this.drawListValid = result.success();
        this.lastDrawListBuildDurationMs = result.durationMs();
        this.lastDrawListError = result.success() ? "none" : result.error();
        this.lastDrawListSkippedReason = result.skippedReason();
        if (!result.success()) {
            this.drawListBuildFailures++;
        }
    }

    void recordFrameDraws(int drawCalls, long vertices, double durationMs, String glError) {
        this.lastFrameDrawCalls = Math.max(0, drawCalls);
        this.lastFrameVertices = Math.max(0, vertices);
        this.lastDrawDurationMs = Math.max(0.0D, durationMs);
        this.lastDrawError = "none";
        this.lastGlError = glError == null ? "none" : glError;
        this.drawCallsIssued += this.lastFrameDrawCalls;
        this.verticesDrawn += this.lastFrameVertices;
    }

    void recordDrawSkip(String reason) {
        this.lastFrameDrawCalls = 0;
        this.lastFrameVertices = 0;
        this.lastDrawDurationMs = 0.0D;
        this.lastDrawError = reason == null ? "skipped" : reason;
        this.lastGlError = "none";
    }

    void recordDrawException(String error) {
        this.lastFrameDrawCalls = 0;
        this.lastFrameVertices = 0;
        this.lastDrawDurationMs = 0.0D;
        this.lastDrawError = error == null ? "unknown" : error;
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
        this.drawItems = 0;
        this.drawListRecords = 0;
        this.drawListVertices = 0;
        this.drawListValid = false;
        this.drawListBuildRuns = 0;
        this.drawListBuildFailures = 0;
        this.lastDrawListBuildDurationMs = 0.0D;
        this.lastDrawListError = "none";
        this.lastDrawListSkippedReason = "none";
        this.drawCallsIssued = 0;
        this.verticesDrawn = 0;
        this.lastFrameDrawCalls = 0;
        this.lastFrameVertices = 0;
        this.lastDrawDurationMs = 0.0D;
        this.lastDrawError = "none";
        this.lastGlError = "none";
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

    int drawItems() {
        return this.drawItems;
    }

    long drawListRecords() {
        return this.drawListRecords;
    }

    long drawListVertices() {
        return this.drawListVertices;
    }

    boolean drawListValid() {
        return this.drawListValid;
    }

    long drawListBuildRuns() {
        return this.drawListBuildRuns;
    }

    long drawListBuildFailures() {
        return this.drawListBuildFailures;
    }

    double lastDrawListBuildDurationMs() {
        return this.lastDrawListBuildDurationMs;
    }

    String lastDrawListError() {
        return this.lastDrawListError;
    }

    String lastDrawListSkippedReason() {
        return this.lastDrawListSkippedReason;
    }

    long drawCallsIssued() {
        return this.drawCallsIssued;
    }

    long verticesDrawn() {
        return this.verticesDrawn;
    }

    int lastFrameDrawCalls() {
        return this.lastFrameDrawCalls;
    }

    long lastFrameVertices() {
        return this.lastFrameVertices;
    }

    double lastDrawDurationMs() {
        return this.lastDrawDurationMs;
    }

    String lastDrawError() {
        return this.lastDrawError;
    }

    String lastGlError() {
        return this.lastGlError;
    }
}
