package me.cortex.voxy.forge;

public final class ForgeDirectGpuGeometryRenderer {
    private static final int DRAW_CALLS_ISSUED = 0;
    private static final int VERTICES_DRAWN = 0;
    private static final boolean ACTUAL_DRAW_ENABLED = false;

    private final ForgeVoxyInstance instance;
    private final ForgeDirectGpuGeometryRenderState state = new ForgeDirectGpuGeometryRenderState();

    ForgeDirectGpuGeometryRenderer(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        // G5.0 deliberately registers no render listener. Future stages can attach a world render event here.
    }

    public void markEnabledRuntime() {
        this.state.markInitialized();
    }

    public void clear() {
        this.state.clear();
    }

    public ForgeDirectGpuGeometryDrawPlanner.PlanResult planSample() {
        ForgeDirectGpuGeometryDrawPlanner.PlanResult result = ForgeDirectGpuGeometryDrawPlanner.plan(
                this.instance,
                ForgeDirectGpuGeometryRendererConfig.maxSections(),
                ForgeDirectGpuGeometryRendererConfig.maxRecords()
        );
        this.state.recordPlan(result);
        if (ForgeDirectGpuGeometryRendererConfig.debugLog()) {
            VoxyForge.LOGGER.info(
                    "Voxy direct GL renderer skeleton planned sections={} records={} success={} reason={} draws=false",
                    result.plannedSections(),
                    result.plannedRecords(),
                    result.success(),
                    result.success() ? "none" : result.error()
            );
        }
        return result;
    }

    public ForgeDirectGpuGeometryRendererStats createStatusSnapshot() {
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        boolean hasHeap = heap != null;
        boolean heapCreated = hasHeap && heap.isCreated();
        return new ForgeDirectGpuGeometryRendererStats(
                ForgeDirectGpuGeometryRendererConfig.isEnabled(),
                this.state.initialized(),
                hasHeap,
                heapCreated,
                this.state.plannedSections(),
                this.state.plannedRecords(),
                this.state.uploadedSectionCandidates(),
                this.state.invalidMetadata(),
                this.state.lastPlanDurationMs(),
                this.state.lastPlanError(),
                this.state.lastSkippedReason(),
                this.state.planRuns(),
                this.state.planFailures(),
                this.state.clearCount(),
                ForgeDirectGpuGeometryRendererConfig.maxSections(),
                ForgeDirectGpuGeometryRendererConfig.maxRecords(),
                DRAW_CALLS_ISSUED,
                VERTICES_DRAWN,
                ACTUAL_DRAW_ENABLED,
                ForgeDirectGpuGeometryRenderState.STAGE
        );
    }
}
