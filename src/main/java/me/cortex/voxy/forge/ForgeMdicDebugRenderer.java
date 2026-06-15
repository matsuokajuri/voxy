package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cortex.voxy.config.SimpleGpuMeshSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.GL11C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11C.glGetError;

final class ForgeMdicDebugRenderer {
    static final String STAGE = "G6_1_MDIC_DEBUG_DRAW_HARDENED";
    static final String DEBUG_DRAW_MODE = "LOOP_PER_MDIC_COMMAND";

    private final ForgeVoxyInstance instance;
    private final ForgeMdicDebugShader shader = new ForgeMdicDebugShader();
    private int lastFrameDrawCalls;
    private int lastFrameCommands;
    private long lastFrameVertices;
    private long drawCallsIssued;
    private long commandsDrawn;
    private long verticesDrawn;
    private double lastFrameRenderMs;
    private double maxFrameRenderMs;
    private double totalFrameRenderMs;
    private long frameSamples;
    private boolean lastFrameOverBudget;
    private long overBudgetFrames;
    private String lastGlError = "none";
    private String lastGlErrorStage = "none";
    private long glErrorCount;
    private long stateRestoreFailures;
    private String lastStateRestoreError = "none";
    private String lastRenderSkippedReason = "none";
    private String lastDrawError = "none";
    private long stressRuns;
    private long stressFailures;
    private String lastStressError = "none";
    private double lastStressDurationMs;
    private boolean lastStressPlanOk;
    private boolean lastStressBuildOk;
    private boolean lastStressAuditOk;
    private boolean lastStressDrawEnableOk;
    private boolean lastStressDrawStatusOk;
    private boolean lastStressDrawDisableOk;
    private boolean lastStressDrawClearOk;
    private boolean lastStressHeapClearOk;
    private boolean lastStressRebuildOk;
    private boolean lastStressRedrawOk;
    private boolean lastStressSourceRegressionOk;
    private long lastStressGlErrorCount;
    private long lastStressStateRestoreFailures;

    ForgeMdicDebugRenderer(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevelStage);
    }

    void enableDraw() {
        ForgeVoxyRuntimeOverrides.setMdicDebugDrawActualDraw(true);
        if (RenderSystem.isOnRenderThread()) {
            this.shader.ensureReady();
        }
    }

    void disableDraw() {
        ForgeVoxyRuntimeOverrides.setMdicDebugDrawActualDraw(false);
        this.recordSkip("ACTUAL_DRAW_DISABLED");
    }

    void clear() {
        this.shader.close();
        this.lastFrameDrawCalls = 0;
        this.lastFrameCommands = 0;
        this.lastFrameVertices = 0L;
        this.drawCallsIssued = 0L;
        this.commandsDrawn = 0L;
        this.verticesDrawn = 0L;
        this.lastFrameRenderMs = 0.0D;
        this.maxFrameRenderMs = 0.0D;
        this.totalFrameRenderMs = 0.0D;
        this.frameSamples = 0L;
        this.lastFrameOverBudget = false;
        this.overBudgetFrames = 0L;
        this.lastGlError = "none";
        this.lastGlErrorStage = "none";
        this.glErrorCount = 0L;
        this.stateRestoreFailures = 0L;
        this.lastStateRestoreError = "none";
        this.lastRenderSkippedReason = "CLEARED";
        this.lastDrawError = "none";
    }

    void clearStressStats() {
        this.stressRuns = 0L;
        this.stressFailures = 0L;
        this.lastStressError = "none";
        this.lastStressDurationMs = 0.0D;
        this.lastStressPlanOk = false;
        this.lastStressBuildOk = false;
        this.lastStressAuditOk = false;
        this.lastStressDrawEnableOk = false;
        this.lastStressDrawStatusOk = false;
        this.lastStressDrawDisableOk = false;
        this.lastStressDrawClearOk = false;
        this.lastStressHeapClearOk = false;
        this.lastStressRebuildOk = false;
        this.lastStressRedrawOk = false;
        this.lastStressSourceRegressionOk = false;
        this.lastStressGlErrorCount = 0L;
        this.lastStressStateRestoreFailures = 0L;
    }

    ForgeMdicDebugShader.ShaderStatus createShaderStatusSnapshot() {
        return this.shader.createStatusSnapshot();
    }

    ForgeMdicDebugDrawStats stressOnce() {
        this.stressRuns++;
        long start = System.nanoTime();
        this.lastStressError = "running";
        this.lastStressPlanOk = false;
        this.lastStressBuildOk = false;
        this.lastStressAuditOk = false;
        this.lastStressDrawEnableOk = false;
        this.lastStressDrawStatusOk = false;
        this.lastStressDrawDisableOk = false;
        this.lastStressDrawClearOk = false;
        this.lastStressHeapClearOk = false;
        this.lastStressRebuildOk = false;
        this.lastStressRedrawOk = false;
        this.lastStressSourceRegressionOk = false;
        this.lastStressGlErrorCount = this.glErrorCount;
        this.lastStressStateRestoreFailures = this.stateRestoreFailures;

        String error = "none";
        try {
            if (!RenderSystem.isOnRenderThread()) {
                error = "not-render-thread";
            } else {
                ForgeMdicCommandManager manager = this.instance.getMdicCommandManager();
                ForgeGpuGeometryUploadManager uploadManager = this.instance.getGpuGeometryUploadManager();
                ForgeVoxyRuntimeOverrides.applyMdicDebugPreset();
                this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
                uploadManager.processForDebugCommand(8);

                ForgeMdicCommandPlanner.PlanResult firstPlan = manager.planSample();
                this.lastStressPlanOk = firstPlan.success();
                this.lastStressBuildOk = this.lastStressPlanOk && manager.buildBuffer();
                ForgeMdicCommandAuditResult firstAudit = manager.audit();
                this.lastStressAuditOk = this.lastStressBuildOk && firstAudit.success();
                if (!this.lastStressPlanOk && "none".equals(error)) {
                    error = "plan=" + firstPlan.error();
                } else if (!this.lastStressBuildOk && "none".equals(error)) {
                    error = "build=" + manager.createStatusSnapshot().lastError();
                } else if (!this.lastStressAuditOk && "none".equals(error)) {
                    error = "audit=" + firstAudit.error();
                }

                this.enableDraw();
                ForgeMdicDebugDrawStats enabledStatus = this.createStatusSnapshot();
                this.lastStressDrawEnableOk = enabledStatus.actualDrawEnabled()
                        && enabledStatus.commandListValid()
                        && enabledStatus.commandBufferCreated()
                        && enabledStatus.shaderSupported()
                        && enabledStatus.programCreated();
                this.lastStressDrawStatusOk = this.lastStressDrawEnableOk
                        && "none".equals(enabledStatus.lastGlError())
                        && enabledStatus.stateRestoreFailures() == this.lastStressStateRestoreFailures;
                if (!this.lastStressDrawEnableOk && "none".equals(error)) {
                    error = "draw-enable-not-ready:" + enabledStatus.lastRenderSkippedReason();
                } else if (!this.lastStressDrawStatusOk && "none".equals(error)) {
                    error = "draw-status-error:" + enabledStatus.lastGlError();
                }

                this.disableDraw();
                ForgeMdicDebugDrawStats disabledStatus = this.createStatusSnapshot();
                this.lastStressDrawDisableOk = !disabledStatus.actualDrawEnabled()
                        && "ACTUAL_DRAW_DISABLED".equals(disabledStatus.lastRenderSkippedReason());
                if (!this.lastStressDrawDisableOk && "none".equals(error)) {
                    error = "draw-disable-failed";
                }

                this.clear();
                ForgeMdicDebugDrawStats clearedStatus = this.createStatusSnapshot();
                this.lastStressDrawClearOk = clearedStatus.lastFrameDrawCalls() == 0
                        && clearedStatus.drawCallsIssued() == 0
                        && !clearedStatus.shaderCompiled();
                if (!this.lastStressDrawClearOk && "none".equals(error)) {
                    error = "draw-clear-failed";
                }

                this.enableDraw();
                uploadManager.clear();
                manager.clear();
                this.recordSkip("COMMAND_LIST_MISSING");
                ForgeMdicDebugDrawStats afterHeapClear = this.createStatusSnapshot();
                this.lastStressHeapClearOk = !afterHeapClear.commandListValid()
                        && !afterHeapClear.commandBufferCreated()
                        && afterHeapClear.lastFrameDrawCalls() == 0
                        && ("COMMAND_LIST_MISSING".equals(afterHeapClear.lastRenderSkippedReason())
                        || "HEAP_MISSING".equals(afterHeapClear.lastRenderSkippedReason()));
                if (!this.lastStressHeapClearOk && "none".equals(error)) {
                    error = "heap-clear-did-not-stop-draw";
                }

                ForgeVoxyRuntimeOverrides.setGeometryGpuUpload(true);
                uploadManager.processForDebugCommand(8);
                ForgeMdicCommandPlanner.PlanResult secondPlan = manager.planSample();
                boolean secondBuild = secondPlan.success() && manager.buildBuffer();
                ForgeMdicCommandAuditResult secondAudit = manager.audit();
                this.lastStressRebuildOk = secondPlan.success() && secondBuild && secondAudit.success();
                if (!this.lastStressRebuildOk && "none".equals(error)) {
                    error = "rebuild=" + (secondPlan.success() ? manager.createStatusSnapshot().lastError() : secondPlan.error());
                }

                this.enableDraw();
                ForgeMdicDebugDrawStats redrawStatus = this.createStatusSnapshot();
                this.lastStressRedrawOk = this.lastStressRebuildOk
                        && redrawStatus.actualDrawEnabled()
                        && redrawStatus.commandListValid()
                        && redrawStatus.commandBufferCreated()
                        && redrawStatus.programCreated();
                if (!this.lastStressRedrawOk && "none".equals(error)) {
                    error = "redraw-not-ready:" + redrawStatus.lastRenderSkippedReason();
                }

                ForgeVoxyRuntimeOverrides.applyLodBuiltSectionPreset();
                this.instance.getGpuMeshUploadManager().clear();
                boolean builtSectionSourceOk = ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.BUILT_SECTION;
                ForgeVoxyRuntimeOverrides.applyGlHeapReadbackPreset();
                this.instance.getGpuMeshUploadManager().clear();
                boolean glHeapSourceOk = ForgeGpuMeshUploadManager.getConfiguredSource() == SimpleGpuMeshSource.GL_HEAP_READBACK;
                this.lastStressSourceRegressionOk = builtSectionSourceOk && glHeapSourceOk;
                if (!this.lastStressSourceRegressionOk && "none".equals(error)) {
                    error = "source-regression-failed";
                }

                ForgeVoxyRuntimeOverrides.applyMdicDebugPreset();
                this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
                uploadManager.processForDebugCommand(4);
                ForgeMdicCommandPlanner.PlanResult finalPlan = manager.planSample();
                boolean finalBuild = finalPlan.success() && manager.buildBuffer();
                ForgeMdicCommandAuditResult finalAudit = manager.audit();
                if (finalPlan.success() && finalBuild && finalAudit.success()) {
                    this.enableDraw();
                }
            }
        } catch (RuntimeException e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        this.lastStressDurationMs = elapsedMs(start);
        this.lastStressGlErrorCount = this.glErrorCount;
        this.lastStressStateRestoreFailures = this.stateRestoreFailures;
        boolean success = "none".equals(error)
                && this.lastStressPlanOk
                && this.lastStressBuildOk
                && this.lastStressAuditOk
                && this.lastStressDrawEnableOk
                && this.lastStressDrawStatusOk
                && this.lastStressDrawDisableOk
                && this.lastStressDrawClearOk
                && this.lastStressHeapClearOk
                && this.lastStressRebuildOk
                && this.lastStressRedrawOk
                && this.lastStressSourceRegressionOk;
        this.lastStressError = success ? "none" : error;
        if (!success) {
            this.stressFailures++;
        }
        return this.createStatusSnapshot();
    }

    ForgeMdicDebugDrawStats createStatusSnapshot() {
        ForgeMdicCommandManager manager = this.instance.getMdicCommandManager();
        ForgeMdicCommandList commandList = manager.commandListForDebugDraw();
        ForgeMdicCommandBuffer commandBuffer = manager.commandBufferForDebugDraw();
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        boolean heapCreated = heap != null && heap.isCreated();
        long currentHeapGeneration = heapCreated ? heap.generation() : -1L;
        String currentDimension = currentDimensionId();
        boolean commandListValid = commandList.isValid();
        boolean commandListStale = commandListValid && (commandList.isStale(currentHeapGeneration) || commandList.isDimensionMismatch(currentDimension));
        boolean commandBufferStale = commandBuffer.isStale(currentHeapGeneration, currentDimension);
        ForgeMdicDebugShader.ShaderStatus shaderStatus = this.shader.createStatusSnapshot();
        return new ForgeMdicDebugDrawStats(
                STAGE,
                ForgeMdicDebugDrawConfig.isEnabled(),
                ForgeMdicDebugDrawConfig.actualDrawEnabled(),
                shaderStatus.shaderSupported(),
                shaderStatus.shaderCompiled(),
                shaderStatus.programCreated(),
                commandBuffer.isCreated(),
                commandListValid,
                commandListStale,
                commandBufferStale,
                heap != null,
                heapCreated,
                currentHeapGeneration,
                currentDimension,
                commandList.heapGeneration(),
                commandBuffer.heapGeneration(),
                commandList.dimensionId(),
                commandBuffer.dimensionId(),
                commandList.commandCount(),
                commandList.recordCount(),
                ForgeMdicDebugDrawConfig.maxCommands(),
                ForgeMdicDebugDrawConfig.maxRecords(),
                ForgeMdicDebugDrawConfig.alpha(),
                ForgeMdicDebugDrawConfig.ignoreDepth(),
                ForgeMdicDebugDrawConfig.doubleSided(),
                this.lastFrameDrawCalls,
                this.lastFrameCommands,
                this.lastFrameVertices,
                this.drawCallsIssued,
                this.commandsDrawn,
                this.verticesDrawn,
                this.lastFrameRenderMs,
                this.maxFrameRenderMs,
                this.frameSamples == 0L ? 0.0D : this.totalFrameRenderMs / this.frameSamples,
                this.lastFrameOverBudget,
                this.overBudgetFrames,
                ForgeMdicDebugDrawConfig.frameBudgetMs(),
                this.lastGlError,
                this.lastGlErrorStage,
                this.glErrorCount,
                this.stateRestoreFailures,
                this.lastStateRestoreError,
                this.lastRenderSkippedReason,
                this.lastDrawError,
                DEBUG_DRAW_MODE,
                this.stressRuns,
                this.stressFailures,
                this.lastStressError,
                this.lastStressDurationMs,
                this.lastStressPlanOk,
                this.lastStressBuildOk,
                this.lastStressAuditOk,
                this.lastStressDrawEnableOk,
                this.lastStressDrawStatusOk,
                this.lastStressDrawDisableOk,
                this.lastStressDrawClearOk,
                this.lastStressHeapClearOk,
                this.lastStressRebuildOk,
                this.lastStressRedrawOk,
                this.lastStressSourceRegressionOk,
                this.lastStressGlErrorCount,
                this.lastStressStateRestoreFailures
        );
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ForgeMdicDebugDrawConfig.isEnabled()) {
            this.recordSkip("DISABLED");
            return;
        }
        if (!ForgeMdicDebugDrawConfig.actualDrawEnabled()) {
            this.recordSkip("ACTUAL_DRAW_DISABLED");
            return;
        }
        if (this.instance.getCurrentEngineOptional().isEmpty()) {
            this.recordSkip("WORLD_MISSING");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || event.getCamera() == null) {
            this.recordSkip("WORLD_MISSING");
            return;
        }
        String currentDimension = currentDimensionId(minecraft);
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        if (heap == null || !heap.isCreated() || heap.geometryBufferIdForDirectRenderer() == 0) {
            this.recordSkip("HEAP_MISSING");
            return;
        }

        ForgeMdicCommandManager manager = this.instance.getMdicCommandManager();
        ForgeMdicCommandList commandList = manager.commandListForDebugDraw();
        ForgeMdicCommandBuffer commandBuffer = manager.commandBufferForDebugDraw();
        if (!commandList.isValid()) {
            this.recordSkip("COMMAND_LIST_MISSING");
            return;
        }
        if (!commandBuffer.isCreated() || commandBuffer.bufferIdForDebugRenderer() == 0) {
            this.recordSkip("COMMAND_BUFFER_MISSING");
            return;
        }
        if (commandList.isStale(heap.generation())) {
            this.recordSkip("COMMAND_LIST_STALE");
            return;
        }
        if (commandBuffer.heapGeneration() != heap.generation()) {
            this.recordSkip("COMMAND_BUFFER_STALE");
            return;
        }
        if (commandList.isDimensionMismatch(currentDimension) || !commandBuffer.dimensionId().equals(currentDimension)) {
            this.recordSkip("DIMENSION_MISMATCH");
            return;
        }
        if (commandBuffer.isStale(heap.generation(), currentDimension)) {
            this.recordSkip("COMMAND_BUFFER_STALE");
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.recordSkip("NOT_RENDER_THREAD");
            return;
        }
        if (!this.shader.ensureReady()) {
            this.recordSkip("SHADER_UNAVAILABLE");
            return;
        }

        long start = System.nanoTime();
        int drawCalls = 0;
        int commands = 0;
        long vertices = 0L;
        String drawException = null;
        String glError = "none";
        String glErrorStage = "none";
        String stateRestoreError = "none";
        boolean stateRestoreFailed = false;
        ForgeMdicDebugRenderStateGuard guard = null;
        drainGlErrors();
        try {
            guard = ForgeMdicDebugRenderStateGuard.capture();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            if (ForgeMdicDebugDrawConfig.ignoreDepth()) {
                RenderSystem.disableDepthTest();
            } else {
                RenderSystem.enableDepthTest();
            }
            if (ForgeMdicDebugDrawConfig.doubleSided()) {
                RenderSystem.disableCull();
            } else {
                RenderSystem.enableCull();
            }
            RenderSystem.depthMask(false);

            PoseStack poseStack = event.getPoseStack();
            Vec3 cameraPos = event.getCamera().getPosition();
            poseStack.pushPose();
            try {
                poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                Matrix4f modelView = poseStack.last().pose();
                Matrix4f projection = event.getProjectionMatrix();
                float alpha = (float) ForgeMdicDebugDrawConfig.alpha();
                int geometryBufferId = heap.geometryBufferIdForDirectRenderer();
                int commandBufferId = commandBuffer.bufferIdForDebugRenderer();
                int maxCommands = Math.min(ForgeMdicDebugDrawConfig.maxCommands(), commandList.commandCount());
                long recordsLeft = ForgeMdicDebugDrawConfig.maxRecords();
                for (int i = 0; i < maxCommands && recordsLeft > 0; i++) {
                    ForgeMdicCommand command = commandList.commands().get(i);
                    int records = Math.max(0, command.recordCount());
                    if (records == 0) {
                        continue;
                    }
                    int drawnRecords = (int) Math.min(recordsLeft, records);
                    int vertexCount = drawnRecords * 6;
                    ForgeMdicDebugShader.DrawCallResult result = this.shader.drawCommandWithDiagnostics(
                            geometryBufferId,
                            commandBufferId,
                            modelView,
                            projection,
                            i,
                            vertexCount,
                            alpha
                    );
                    commands++;
                    drawCalls++;
                    vertices += vertexCount;
                    recordsLeft -= drawnRecords;
                    if (!result.ok()) {
                        glError = result.formattedError();
                        glErrorStage = result.stage();
                        break;
                    }
                }
            } finally {
                poseStack.popPose();
            }
        } catch (RuntimeException e) {
            drawException = e.getClass().getSimpleName() + ": " + e.getMessage();
            VoxyForge.LOGGER.error("Failed during G6.0 MDIC command-buffer debug draw", e);
        } finally {
            try {
                this.shader.unbind();
                if (guard != null) {
                    ForgeMdicDebugRenderStateGuard.RestoreResult restoreResult = guard.restore();
                    if (!restoreResult.success()) {
                        stateRestoreError = restoreResult.error();
                        stateRestoreFailed = true;
                    }
                }
            } catch (RuntimeException e) {
                stateRestoreError = e.getClass().getSimpleName() + ": " + e.getMessage();
                stateRestoreFailed = true;
            }
        }

        if (drawException != null) {
            this.lastDrawError = drawException;
            this.recordSkip("DRAW_EXCEPTION");
            return;
        }

        this.recordFrame(drawCalls, commands, vertices, elapsedMs(start), glError, glErrorStage, stateRestoreError, stateRestoreFailed);
    }

    private void recordSkip(String reason) {
        this.lastFrameDrawCalls = 0;
        this.lastFrameCommands = 0;
        this.lastFrameVertices = 0L;
        this.lastFrameRenderMs = 0.0D;
        this.lastFrameOverBudget = false;
        this.lastRenderSkippedReason = reason;
    }

    private void recordFrame(int drawCalls, int commands, long vertices, double durationMs, String glError, String glErrorStage, String stateRestoreError, boolean stateRestoreFailed) {
        this.lastFrameDrawCalls = drawCalls;
        this.lastFrameCommands = commands;
        this.lastFrameVertices = vertices;
        this.drawCallsIssued += drawCalls;
        this.commandsDrawn += commands;
        this.verticesDrawn += vertices;
        this.lastFrameRenderMs = durationMs;
        this.maxFrameRenderMs = Math.max(this.maxFrameRenderMs, durationMs);
        this.totalFrameRenderMs += durationMs;
        this.frameSamples++;
        this.lastFrameOverBudget = durationMs > ForgeMdicDebugDrawConfig.frameBudgetMs();
        if (this.lastFrameOverBudget) {
            this.overBudgetFrames++;
        }
        this.lastGlError = glError;
        this.lastGlErrorStage = glErrorStage;
        this.lastStateRestoreError = stateRestoreError;
        if (!"none".equals(glError)) {
            this.glErrorCount++;
        }
        if (stateRestoreFailed) {
            this.stateRestoreFailures++;
            this.lastGlError = stateRestoreError;
            this.lastGlErrorStage = "MDIC_RESTORE_STATE";
        }
        this.lastRenderSkippedReason = "none";
        this.lastDrawError = "none";
        if (ForgeMdicDebugDrawConfig.debugLog()) {
            VoxyForge.LOGGER.info("G6.0 MDIC debug draw frame: commands={} drawCalls={} vertices={} durationMs={} glError={} restoreError={}",
                    commands,
                    drawCalls,
                    vertices,
                    durationMs,
                    glError,
                    stateRestoreError);
        }
    }

    private static String currentDimensionId() {
        Minecraft minecraft = Minecraft.getInstance();
        return currentDimensionId(minecraft);
    }

    private static String currentDimensionId(Minecraft minecraft) {
        if (minecraft.level == null) {
            return "none";
        }
        return minecraft.level.dimension().location().toString();
    }

    private static void drainGlErrors() {
        while (glGetError() != GL_NO_ERROR) {
            // Drain pre-existing errors so this debug path reports its own failures.
        }
    }

    private static double elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000.0D;
    }
}
