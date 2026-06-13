package me.cortex.voxy.forge;

final class ForgeDirectGpuGeometryRenderState {
    static final String STAGE = "G5_3_CAMERA_AWARE_DIRECT_DRAW";

    private boolean initialized;
    private int plannedSections;
    private long plannedRecords;
    private long plannedVertices;
    private int skippedSections;
    private long skippedRecords;
    private String selectionMode = "none";
    private int uploadedSectionCandidates;
    private int candidateSections;
    private int acceptedSections;
    private int rejectedByRadius;
    private int rejectedByFrustum;
    private boolean frustumAvailable;
    private String cameraChunk = "none";
    private String cameraSection = "none";
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
    private boolean drawListStale;
    private long drawListHeapGeneration = -1L;
    private long currentHeapGeneration = -1L;
    private long drawListBuildRuns;
    private long drawListBuildFailures;
    private double lastDrawListBuildDurationMs;
    private String lastDrawListError = "none";
    private String lastDrawListSkippedReason = "none";
    private long drawCallsIssued;
    private long verticesDrawn;
    private int lastFrameDrawCalls;
    private int lastFrameDrawItems;
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
        this.plannedVertices = result.plannedVertices();
        this.skippedSections = result.skippedSections();
        this.skippedRecords = result.skippedRecords();
        this.selectionMode = result.selectionMode();
        this.uploadedSectionCandidates = result.uploadedSectionCandidates();
        this.candidateSections = result.uploadedSectionCandidates();
        this.acceptedSections = result.plannedSections();
        this.rejectedByRadius = 0;
        this.rejectedByFrustum = 0;
        this.frustumAvailable = false;
        this.cameraChunk = "none";
        this.cameraSection = "none";
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
        this.plannedVertices = result.drawVertices();
        this.skippedSections = result.skippedSections();
        this.skippedRecords = result.skippedRecords();
        this.selectionMode = result.selectionMode();
        this.candidateSections = result.candidateSections();
        this.acceptedSections = result.acceptedSections();
        this.rejectedByRadius = result.rejectedByRadius();
        this.rejectedByFrustum = result.rejectedByFrustum();
        this.frustumAvailable = result.frustumAvailable();
        this.cameraChunk = result.cameraChunk();
        this.cameraSection = result.cameraSection();
        this.drawItems = result.drawItems();
        this.drawListRecords = result.drawRecords();
        this.drawListVertices = result.drawVertices();
        this.drawListValid = result.success();
        this.drawListStale = false;
        this.drawListHeapGeneration = result.heapGeneration();
        this.currentHeapGeneration = result.heapGeneration();
        this.lastDrawListBuildDurationMs = result.durationMs();
        this.lastDrawListError = result.success() ? "none" : result.error();
        this.lastDrawListSkippedReason = result.skippedReason();
        if (!result.success()) {
            this.drawListBuildFailures++;
        }
    }

    void recordFrameDraws(int drawItems, int drawCalls, long vertices, double durationMs, String glError) {
        this.lastFrameDrawItems = Math.max(0, drawItems);
        this.lastFrameDrawCalls = Math.max(0, drawCalls);
        this.lastFrameVertices = Math.max(0, vertices);
        this.lastDrawDurationMs = Math.max(0.0D, durationMs);
        this.lastDrawError = "none";
        this.lastGlError = glError == null ? "none" : glError;
        this.drawCallsIssued += this.lastFrameDrawCalls;
        this.verticesDrawn += this.lastFrameVertices;
    }

    void recordDrawSkip(String reason) {
        this.lastFrameDrawItems = 0;
        this.lastFrameDrawCalls = 0;
        this.lastFrameVertices = 0;
        this.lastDrawDurationMs = 0.0D;
        this.lastDrawError = reason == null ? "skipped" : reason;
        this.lastGlError = "none";
    }

    void recordDrawException(String error) {
        this.lastFrameDrawItems = 0;
        this.lastFrameDrawCalls = 0;
        this.lastFrameVertices = 0;
        this.lastDrawDurationMs = 0.0D;
        this.lastDrawError = error == null ? "unknown" : error;
    }

    void recordDrawListStale(long currentHeapGeneration) {
        this.drawListStale = true;
        this.currentHeapGeneration = currentHeapGeneration;
        this.recordDrawSkip("draw-list-stale");
    }

    void clear() {
        this.initialized = false;
        this.plannedSections = 0;
        this.plannedRecords = 0;
        this.plannedVertices = 0;
        this.skippedSections = 0;
        this.skippedRecords = 0;
        this.selectionMode = "none";
        this.uploadedSectionCandidates = 0;
        this.candidateSections = 0;
        this.acceptedSections = 0;
        this.rejectedByRadius = 0;
        this.rejectedByFrustum = 0;
        this.frustumAvailable = false;
        this.cameraChunk = "none";
        this.cameraSection = "none";
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
        this.drawListStale = false;
        this.drawListHeapGeneration = -1L;
        this.currentHeapGeneration = -1L;
        this.drawListBuildRuns = 0;
        this.drawListBuildFailures = 0;
        this.lastDrawListBuildDurationMs = 0.0D;
        this.lastDrawListError = "none";
        this.lastDrawListSkippedReason = "none";
        this.drawCallsIssued = 0;
        this.verticesDrawn = 0;
        this.lastFrameDrawItems = 0;
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

    long plannedVertices() {
        return this.plannedVertices;
    }

    int skippedSections() {
        return this.skippedSections;
    }

    long skippedRecords() {
        return this.skippedRecords;
    }

    String selectionMode() {
        return this.selectionMode;
    }

    int uploadedSectionCandidates() {
        return this.uploadedSectionCandidates;
    }

    int candidateSections() {
        return this.candidateSections;
    }

    int acceptedSections() {
        return this.acceptedSections;
    }

    int rejectedByRadius() {
        return this.rejectedByRadius;
    }

    int rejectedByFrustum() {
        return this.rejectedByFrustum;
    }

    boolean frustumAvailable() {
        return this.frustumAvailable;
    }

    String cameraChunk() {
        return this.cameraChunk;
    }

    String cameraSection() {
        return this.cameraSection;
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

    boolean drawListStale() {
        return this.drawListStale;
    }

    long drawListHeapGeneration() {
        return this.drawListHeapGeneration;
    }

    long currentHeapGeneration() {
        return this.currentHeapGeneration;
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

    int lastFrameDrawItems() {
        return this.lastFrameDrawItems;
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
