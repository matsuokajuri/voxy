package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.lwjgl.opengl.GL11C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11C.glGetError;

public final class ForgeDirectGpuGeometryRenderer {
    static final String REASON_MANUAL_COMMAND = "MANUAL_COMMAND";
    static final String REASON_PRESET = "PRESET";
    static final String REASON_COOLDOWN = "COOLDOWN";
    static final String REASON_DIMENSION_CHANGE = "DIMENSION_CHANGE";
    static final String REASON_SOURCE_SWITCH = "SOURCE_SWITCH";
    static final String REASON_UPLOAD_CLEAR_REBUILD = "UPLOAD_CLEAR_REBUILD";

    private final ForgeVoxyInstance instance;
    private final ForgeDirectGpuGeometryRenderState state = new ForgeDirectGpuGeometryRenderState();
    private final ForgeDirectGpuGeometryShader shader = new ForgeDirectGpuGeometryShader();
    private final ForgeDirectGpuGeometryDrawItemBuffer drawItemBuffer = new ForgeDirectGpuGeometryDrawItemBuffer();
    private final ForgeDirectGpuGeometryIndirectCommandBuffer indirectCommandBuffer = new ForgeDirectGpuGeometryIndirectCommandBuffer();
    private ForgeDirectGpuGeometryDrawList drawList = ForgeDirectGpuGeometryDrawList.empty();
    private ForgeDirectGpuGeometryDrawMode configuredDrawMode = ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION;
    private int ticksUntilNextAutoPlan;
    private String requestedAutoPlanReason;
    private long autoPlanRuns;
    private long autoPlanSkipped;
    private long autoPlanFailures;
    private String lastAutoPlanReason = "none";
    private String lastAutoPlanSkippedReason = "none";
    private double lastAutoPlanDurationMs;
    private String lastAutoPlanError = "none";
    private double lastAutoPlanX = Double.NaN;
    private double lastAutoPlanY = Double.NaN;
    private double lastAutoPlanZ = Double.NaN;
    private long lastAutoPlanHeapGeneration = -1L;
    private long stressRuns;
    private long stressFailures;
    private String lastStressError = "none";
    private double lastStressDurationMs;
    private int lastStressPlannedSections;
    private long lastStressPlannedRecords;
    private boolean lastStressDrawListValid;
    private boolean lastStressShaderSupported;
    private boolean lastStressSourceRegressionOk;
    private boolean lastStressLoopOk;
    private boolean lastStressMultiDrawOk;
    private boolean lastStressIndirectOk;
    private boolean lastStressIndirectAuditOk;
    private long lastStressGlErrorCount;
    private long lastStressStateRestoreFailures;
    private long auditRuns;
    private long auditFailures;
    private String lastIndirectAuditError = "none";
    private double lastIndirectAuditDurationMs;
    private int lastAuditedDrawItems;
    private long lastAuditedCommandBytes;
    private long lastAuditedDrawItemBytes;
    private boolean lastCommandBufferMatch;
    private boolean lastDrawItemBufferMatch;
    private long lastAuditHeapGeneration = -1L;
    private String lastAuditDimension = "none";
    private int lastInvalidCommands;
    private int lastInvalidDrawItems;
    private long lastAuditedVertices;

    ForgeDirectGpuGeometryRenderer(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    public void markEnabledRuntime() {
        this.state.markInitialized();
    }

    public void clear() {
        this.drawList = ForgeDirectGpuGeometryDrawList.empty();
        this.state.clear();
        this.shader.close();
        this.drawItemBuffer.close();
        this.indirectCommandBuffer.close();
        this.configuredDrawMode = ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION;
        this.ticksUntilNextAutoPlan = ForgeDirectGpuGeometryRendererConfig.autoPlanCooldownTicks();
        this.requestedAutoPlanReason = null;
        this.autoPlanRuns = 0;
        this.autoPlanSkipped = 0;
        this.autoPlanFailures = 0;
        this.lastAutoPlanReason = "none";
        this.lastAutoPlanSkippedReason = "none";
        this.lastAutoPlanError = "none";
        this.lastAutoPlanDurationMs = 0.0D;
        this.lastAutoPlanX = Double.NaN;
        this.lastAutoPlanY = Double.NaN;
        this.lastAutoPlanZ = Double.NaN;
        this.lastAutoPlanHeapGeneration = -1L;
        this.clearIndirectAuditStats();
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
                    "Voxy direct GL renderer planned sections={} records={} success={} reason={} actualDraw={}",
                    result.plannedSections(),
                    result.plannedRecords(),
                    result.success(),
                    result.success() ? "none" : result.error(),
                    ForgeDirectGpuGeometryRendererConfig.actualDrawEnabled()
            );
        }
        return result;
    }

    public DrawListBuildResult buildDrawList() {
        DrawListBuildResult result = this.buildDrawListInternal(
                ForgeDirectGpuGeometryRendererConfig.maxPlanCandidates(),
                ForgeDirectGpuGeometryRendererConfig.maxDrawSections(),
                ForgeDirectGpuGeometryRendererConfig.maxDrawRecords(),
                ForgeDirectGpuGeometryRendererConfig.maxRecordsPerSection(),
                ForgeDirectGpuGeometryRendererConfig.renderDistanceChunks()
        );
        this.state.recordDrawList(result);
        if (!result.success()) {
            this.drawList = ForgeDirectGpuGeometryDrawList.empty();
            this.drawItemBuffer.close();
            this.indirectCommandBuffer.close();
        }
        return result;
    }

    public void requestAutoPlan(String reason) {
        this.requestedAutoPlanReason = normalizeReason(reason);
    }

    public DrawListBuildResult autoPlanNow(String reason) {
        return this.runAutoPlan(normalizeReason(reason), true);
    }

    public ForgeDirectGpuGeometryShader.ShaderStatus createShaderStatusSnapshot() {
        return this.shader.createStatusSnapshot();
    }

    public ForgeDirectGpuGeometryShader.ShaderStatus prepareShader() {
        this.shader.ensureReady();
        return this.shader.createStatusSnapshot();
    }

    public ForgeDirectGpuGeometryShader.ShaderStatus prepareShaderForConfiguredMode() {
        if (this.configuredDrawMode == ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT) {
            this.shader.ensureIndirectReady();
        } else if (this.configuredDrawMode == ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS) {
            this.shader.ensureMultiDrawReady();
        } else if (this.configuredDrawMode == ForgeDirectGpuGeometryDrawMode.AUTO) {
            if (!this.shader.ensureIndirectReady() && !this.shader.ensureMultiDrawReady()) {
                this.shader.ensureReady();
            }
        } else {
            this.shader.ensureReady();
        }
        return this.shader.createStatusSnapshot();
    }

    public void setDrawMode(ForgeDirectGpuGeometryDrawMode mode) {
        this.configuredDrawMode = mode == null ? ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION : mode;
        if (this.configuredDrawMode == ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION) {
            this.drawItemBuffer.close();
            this.indirectCommandBuffer.close();
        } else if (this.configuredDrawMode == ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS) {
            this.indirectCommandBuffer.close();
        }
    }

    private ForgeDirectGpuGeometryDrawMode effectiveDrawMode(ForgeDirectGpuGeometryShader.ShaderStatus shaderStatus) {
        if (this.configuredDrawMode == ForgeDirectGpuGeometryDrawMode.AUTO) {
            if (shaderStatus.indirectSupported()) {
                return ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT;
            }
            return shaderStatus.multiDrawSupported() ? ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS : ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION;
        }
        return this.configuredDrawMode;
    }

    private String autoModeSelectedReason(ForgeDirectGpuGeometryShader.ShaderStatus shaderStatus) {
        if (this.configuredDrawMode != ForgeDirectGpuGeometryDrawMode.AUTO) {
            return "configured:" + this.configuredDrawMode.name();
        }
        if (shaderStatus.indirectSupported()) {
            return "indirect-supported";
        }
        if (shaderStatus.multiDrawSupported()) {
            return "multi-draw-supported";
        }
        return "loop-fallback";
    }

    private String autoModeFallbackReason(ForgeDirectGpuGeometryShader.ShaderStatus shaderStatus) {
        if (this.configuredDrawMode != ForgeDirectGpuGeometryDrawMode.AUTO) {
            return "none";
        }
        if (shaderStatus.indirectSupported()) {
            return "none";
        }
        String indirectReason = shaderStatus.indirectUnsupportedReason() == null ? "unknown" : shaderStatus.indirectUnsupportedReason();
        if (shaderStatus.multiDrawSupported()) {
            return "indirect:" + indirectReason;
        }
        String multiReason = shaderStatus.multiDrawUnsupportedReason() == null ? "unknown" : shaderStatus.multiDrawUnsupportedReason();
        return "indirect:" + indirectReason + ",multi:" + multiReason;
    }

    public void recordStressResult(StressResult result) {
        this.stressRuns++;
        this.lastStressDurationMs = result.durationMs();
        this.lastStressPlannedSections = result.plannedSections();
        this.lastStressPlannedRecords = result.plannedRecords();
        this.lastStressDrawListValid = result.drawListValid();
        this.lastStressShaderSupported = result.shaderSupported();
        this.lastStressSourceRegressionOk = result.sourceRegressionOk();
        this.lastStressLoopOk = result.loopOk();
        this.lastStressMultiDrawOk = result.multiDrawOk();
        this.lastStressIndirectOk = result.indirectOk();
        this.lastStressIndirectAuditOk = result.indirectAuditOk();
        this.lastStressGlErrorCount = result.glErrorCount();
        this.lastStressStateRestoreFailures = result.stateRestoreFailures();
        this.lastStressError = result.success() ? "none" : result.error();
        if (!result.success()) {
            this.stressFailures++;
        }
    }

    public void clearStressStats() {
        this.stressRuns = 0;
        this.stressFailures = 0;
        this.lastStressError = "none";
        this.lastStressDurationMs = 0.0D;
        this.lastStressPlannedSections = 0;
        this.lastStressPlannedRecords = 0;
        this.lastStressDrawListValid = false;
        this.lastStressShaderSupported = false;
        this.lastStressSourceRegressionOk = false;
        this.lastStressLoopOk = false;
        this.lastStressMultiDrawOk = false;
        this.lastStressIndirectOk = false;
        this.lastStressIndirectAuditOk = false;
        this.lastStressGlErrorCount = 0L;
        this.lastStressStateRestoreFailures = 0L;
    }

    public ForgeDirectGpuGeometryIndirectAuditResult auditIndirectBuffers() {
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            return this.recordIndirectAuditResult(ForgeDirectGpuGeometryIndirectAuditResult.failure("not-render-thread", elapsedMs(start)));
        }
        try {
            ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
            if (heap == null || !heap.isCreated()) {
                return this.recordIndirectAuditResult(ForgeDirectGpuGeometryIndirectAuditResult.failure("heap-missing", elapsedMs(start)));
            }
            if (!this.drawList.isValid()) {
                return this.recordIndirectAuditResult(ForgeDirectGpuGeometryIndirectAuditResult.failure("draw-list-invalid", elapsedMs(start)));
            }
            if (this.drawList.isStale(heap.generation())) {
                return this.recordIndirectAuditResult(ForgeDirectGpuGeometryIndirectAuditResult.failure("draw-list-stale", elapsedMs(start)));
            }
            String currentDimension = currentDimensionId(Minecraft.getInstance());
            if (this.drawList.isDimensionMismatch(currentDimension)) {
                return this.recordIndirectAuditResult(ForgeDirectGpuGeometryIndirectAuditResult.failure("dimension-mismatch:" + this.drawList.dimensionId() + "!=" + currentDimension, elapsedMs(start)));
            }
            ForgeDirectGpuGeometryShader.ShaderStatus shaderStatus = this.prepareShaderForConfiguredMode();
            ForgeDirectGpuGeometryDrawMode effectiveDrawMode = this.effectiveDrawMode(shaderStatus);
            if (effectiveDrawMode != ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT) {
                return this.recordIndirectAuditResult(ForgeDirectGpuGeometryIndirectAuditResult.failure("effective-mode-not-indirect:" + effectiveDrawMode.name(), elapsedMs(start)));
            }
            if (!this.drawItemBuffer.matches(this.drawList) && !this.drawItemBuffer.upload(this.drawList)) {
                return this.recordIndirectAuditResult(ForgeDirectGpuGeometryIndirectAuditResult.failure("draw-item-buffer-upload-failed:" + this.drawItemBuffer.lastUploadError(), elapsedMs(start)));
            }
            if (!this.indirectCommandBuffer.matches(this.drawList) && !this.indirectCommandBuffer.upload(this.drawList)) {
                return this.recordIndirectAuditResult(ForgeDirectGpuGeometryIndirectAuditResult.failure("indirect-command-buffer-upload-failed:" + this.indirectCommandBuffer.lastUploadError(), elapsedMs(start)));
            }

            int[] commandWords = this.indirectCommandBuffer.readbackWords();
            int[] drawItemWords = this.drawItemBuffer.readbackWords();
            AuditCounts counts = compareIndirectBuffers(commandWords, drawItemWords, this.drawList);
            boolean commandMatch = counts.invalidCommands() == 0
                    && commandWords.length == this.drawList.itemCount() * 4
                    && this.indirectCommandBuffer.bytes() == this.drawList.itemCount() * 16L;
            boolean drawItemMatch = counts.invalidDrawItems() == 0
                    && drawItemWords.length == this.drawList.itemCount() * 8
                    && this.drawItemBuffer.bytes() == this.drawList.itemCount() * 32L;
            boolean success = commandMatch && drawItemMatch && counts.auditedVertices() == this.drawList.vertexCount();
            String error = success ? "none" : "mismatch";
            return this.recordIndirectAuditResult(new ForgeDirectGpuGeometryIndirectAuditResult(
                    success,
                    error,
                    elapsedMs(start),
                    this.drawList.itemCount(),
                    this.indirectCommandBuffer.bytes(),
                    this.drawItemBuffer.bytes(),
                    commandMatch,
                    drawItemMatch,
                    this.drawList.heapGeneration(),
                    this.drawList.dimensionId(),
                    counts.invalidCommands(),
                    counts.invalidDrawItems(),
                    counts.auditedVertices()
            ));
        } catch (RuntimeException e) {
            return this.recordIndirectAuditResult(ForgeDirectGpuGeometryIndirectAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start)));
        }
    }

    public void clearIndirectAuditStats() {
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.lastIndirectAuditError = "none";
        this.lastIndirectAuditDurationMs = 0.0D;
        this.lastAuditedDrawItems = 0;
        this.lastAuditedCommandBytes = 0L;
        this.lastAuditedDrawItemBytes = 0L;
        this.lastCommandBufferMatch = false;
        this.lastDrawItemBufferMatch = false;
        this.lastAuditHeapGeneration = -1L;
        this.lastAuditDimension = "none";
        this.lastInvalidCommands = 0;
        this.lastInvalidDrawItems = 0;
        this.lastAuditedVertices = 0L;
    }

    private ForgeDirectGpuGeometryIndirectAuditResult recordIndirectAuditResult(ForgeDirectGpuGeometryIndirectAuditResult result) {
        this.auditRuns++;
        this.lastIndirectAuditError = result.success() ? "none" : result.error();
        this.lastIndirectAuditDurationMs = result.durationMs();
        this.lastAuditedDrawItems = result.auditedDrawItems();
        this.lastAuditedCommandBytes = result.commandBytes();
        this.lastAuditedDrawItemBytes = result.drawItemBytes();
        this.lastCommandBufferMatch = result.commandBufferMatch();
        this.lastDrawItemBufferMatch = result.drawItemBufferMatch();
        this.lastAuditHeapGeneration = result.heapGeneration();
        this.lastAuditDimension = result.dimensionId();
        this.lastInvalidCommands = result.invalidCommands();
        this.lastInvalidDrawItems = result.invalidDrawItems();
        this.lastAuditedVertices = result.auditedVertices();
        if (!result.success()) {
            this.auditFailures++;
        }
        return result;
    }

    private static AuditCounts compareIndirectBuffers(int[] commandWords, int[] drawItemWords, ForgeDirectGpuGeometryDrawList drawList) {
        int invalidCommands = 0;
        int invalidDrawItems = 0;
        long auditedVertices = 0L;
        int drawIndex = 0;
        for (ForgeDirectGpuGeometryDrawItem item : drawList.items()) {
            int commandBase = drawIndex * 4;
            if (commandBase + 3 >= commandWords.length
                    || commandWords[commandBase] != item.vertexCount()
                    || commandWords[commandBase + 1] != 1
                    || commandWords[commandBase + 2] != 0
                    || commandWords[commandBase + 3] != drawIndex) {
                invalidCommands++;
            } else {
                auditedVertices += Integer.toUnsignedLong(commandWords[commandBase]);
            }

            int itemBase = drawIndex * 8;
            if (itemBase + 7 >= drawItemWords.length
                    || drawItemWords[itemBase] != item.baseRecord()
                    || drawItemWords[itemBase + 1] != item.recordCount()
                    || drawItemWords[itemBase + 2] != item.sectionId()
                    || drawItemWords[itemBase + 3] != item.bucketMask()
                    || drawItemWords[itemBase + 4] != Float.floatToRawIntBits(item.originX())
                    || drawItemWords[itemBase + 5] != Float.floatToRawIntBits(item.originY())
                    || drawItemWords[itemBase + 6] != Float.floatToRawIntBits(item.originZ())
                    || drawItemWords[itemBase + 7] != Float.floatToRawIntBits(item.scale())) {
                invalidDrawItems++;
            }
            drawIndex++;
        }
        if (commandWords.length != drawList.itemCount() * 4) {
            invalidCommands += Math.abs(commandWords.length - drawList.itemCount() * 4);
        }
        if (drawItemWords.length != drawList.itemCount() * 8) {
            invalidDrawItems += Math.abs(drawItemWords.length - drawList.itemCount() * 8);
        }
        return new AuditCounts(invalidCommands, invalidDrawItems, auditedVertices);
    }

    public ForgeDirectGpuGeometryRendererStats createStatusSnapshot() {
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        boolean hasHeap = heap != null;
        boolean heapCreated = hasHeap && heap.isCreated();
        long currentHeapGeneration = hasHeap ? heap.generation() : -1L;
        boolean drawListStale = this.drawList.isStale(currentHeapGeneration);
        String currentDimension = currentDimensionId(Minecraft.getInstance());
        boolean drawListDimensionMismatch = this.drawList.isDimensionMismatch(currentDimension);
        ForgeDirectGpuGeometryShader.ShaderStatus shaderStatus = this.shader.createStatusSnapshot();
        ForgeDirectGpuGeometryDrawMode effectiveDrawMode = this.effectiveDrawMode(shaderStatus);
        return new ForgeDirectGpuGeometryRendererStats(
                ForgeDirectGpuGeometryRendererConfig.isEnabled(),
                this.state.initialized(),
                hasHeap,
                heapCreated,
                this.state.plannedSections(),
                this.state.plannedRecords(),
                this.state.plannedVertices(),
                this.state.skippedSections(),
                this.state.skippedRecords(),
                this.state.selectionMode(),
                this.state.uploadedSectionCandidates(),
                this.state.candidateSections(),
                this.state.acceptedSections(),
                this.state.rejectedByRadius(),
                this.state.rejectedByFrustum(),
                this.state.frustumAvailable(),
                this.state.cameraChunk(),
                this.state.cameraSection(),
                this.state.invalidMetadata(),
                this.state.lastPlanDurationMs(),
                this.state.lastPlanError(),
                this.state.lastSkippedReason(),
                this.state.planRuns(),
                this.state.planFailures(),
                this.state.clearCount(),
                ForgeDirectGpuGeometryRendererConfig.maxSections(),
                ForgeDirectGpuGeometryRendererConfig.maxRecords(),
                ForgeDirectGpuGeometryRendererConfig.maxPlanCandidates(),
                ForgeDirectGpuGeometryRendererConfig.maxDrawSections(),
                ForgeDirectGpuGeometryRendererConfig.maxDrawRecords(),
                ForgeDirectGpuGeometryRendererConfig.maxRecordsPerSection(),
                ForgeDirectGpuGeometryRendererConfig.renderDistanceChunks(),
                ForgeDirectGpuGeometryRendererConfig.debugAlpha(),
                ForgeDirectGpuGeometryRendererConfig.ignoreDepth(),
                ForgeDirectGpuGeometryRendererConfig.doubleSided(),
                ForgeDirectGpuGeometryRendererConfig.autoPlanEnabled(),
                ForgeDirectGpuGeometryRendererConfig.autoPlanCooldownTicks(),
                this.ticksUntilNextAutoPlan,
                this.lastAutoPlanReason,
                this.lastAutoPlanSkippedReason,
                this.lastAutoPlanDurationMs,
                this.autoPlanRuns,
                this.autoPlanSkipped,
                this.autoPlanFailures,
                ForgeDirectGpuGeometryRendererConfig.autoPlanMaxCandidates(),
                ForgeDirectGpuGeometryRendererConfig.autoPlanMaxSections(),
                ForgeDirectGpuGeometryRendererConfig.autoPlanMaxRecords(),
                ForgeDirectGpuGeometryRendererConfig.autoPlanMoveThresholdBlocks(),
                shaderStatus.shaderSupported(),
                shaderStatus.multiDrawSupported(),
                shaderStatus.multiDrawUnsupportedReason(),
                shaderStatus.indirectSupported(),
                shaderStatus.multiDrawIndirectSupported(),
                shaderStatus.drawIndirectBufferSupported(),
                shaderStatus.baseInstanceSupported(),
                shaderStatus.indirectUnsupportedReason(),
                shaderStatus.drawIdSupported(),
                shaderStatus.shaderCompiled(),
                shaderStatus.programCreated(),
                shaderStatus.loopShaderCompiled(),
                shaderStatus.multiDrawShaderCompiled(),
                shaderStatus.multiDrawProgramCreated(),
                shaderStatus.indirectShaderCompiled(),
                shaderStatus.indirectProgramCreated(),
                shaderStatus.lastShaderError(),
                shaderStatus.lastMultiDrawShaderError(),
                shaderStatus.lastIndirectShaderError(),
                shaderStatus.unsupportedReason(),
                shaderStatus.glVersion(),
                shaderStatus.glslVersion(),
                shaderStatus.usesSsbo(),
                this.configuredDrawMode.name(),
                effectiveDrawMode.name(),
                this.autoModeSelectedReason(shaderStatus),
                this.autoModeFallbackReason(shaderStatus),
                this.drawItemBuffer.isCreated(),
                this.drawItemBuffer.bytes(),
                this.drawItemBuffer.drawListBuildTimeMillis(),
                this.indirectCommandBuffer.isCreated(),
                this.indirectCommandBuffer.bytes(),
                this.indirectCommandBuffer.drawListBuildTimeMillis(),
                this.lastCommandBufferMatch && this.lastDrawItemBufferMatch && this.lastInvalidCommands == 0 && this.lastInvalidDrawItems == 0 && this.auditRuns > 0 && "none".equals(this.lastIndirectAuditError),
                this.auditRuns,
                this.auditFailures,
                this.lastIndirectAuditError,
                this.lastIndirectAuditDurationMs,
                this.lastAuditedDrawItems,
                this.lastAuditedCommandBytes,
                this.lastAuditedDrawItemBytes,
                this.lastCommandBufferMatch,
                this.lastDrawItemBufferMatch,
                this.lastAuditHeapGeneration,
                this.lastAuditDimension,
                this.lastInvalidCommands,
                this.lastInvalidDrawItems,
                this.lastAuditedVertices,
                this.drawList.isValid() && this.state.drawListValid() && !drawListStale && !drawListDimensionMismatch,
                drawListStale || drawListDimensionMismatch,
                this.drawList.heapGeneration(),
                currentHeapGeneration,
                this.drawList.dimensionId(),
                currentDimension,
                this.state.drawItems(),
                this.state.drawListRecords(),
                this.state.drawListVertices(),
                this.state.drawListBuildRuns(),
                this.state.drawListBuildFailures(),
                this.state.lastDrawListBuildDurationMs(),
                this.state.maxDrawListBuildDurationMs(),
                this.state.lastDrawListError(),
                this.state.lastDrawListSkippedReason(),
                this.state.drawCallsIssued(),
                this.state.logicalDrawItemsIssued(),
                this.state.verticesDrawn(),
                this.state.lastFrameDrawCalls(),
                this.state.lastFrameDrawItems(),
                this.state.lastFrameVertices(),
                this.state.lastDrawDurationMs(),
                this.state.maxFrameRenderMs(),
                this.state.avgFrameRenderMs(),
                this.state.lastFrameOverBudget(),
                this.state.overBudgetFrames(),
                this.state.lastDrawError(),
                this.state.lastRenderSkippedReason(),
                this.state.lastGlError(),
                this.state.lastGlErrorStage(),
                this.state.glErrorCount(),
                this.state.lastPreExistingGlError(),
                this.state.lastStateRestoreError(),
                this.state.stateRestoreFailures(),
                ForgeDirectGpuGeometryRendererConfig.frameBudgetMs(),
                ForgeDirectGpuGeometryRendererConfig.planBudgetMs(),
                this.stressRuns,
                this.stressFailures,
                this.lastStressError,
                this.lastStressDurationMs,
                this.lastStressPlannedSections,
                this.lastStressPlannedRecords,
                this.lastStressDrawListValid,
                this.lastStressShaderSupported,
                this.lastStressSourceRegressionOk,
                this.lastStressLoopOk,
                this.lastStressMultiDrawOk,
                this.lastStressIndirectOk,
                this.lastStressIndirectAuditOk,
                this.lastStressGlErrorCount,
                this.lastStressStateRestoreFailures,
                ForgeDirectGpuGeometryRendererConfig.actualDrawEnabled(),
                ForgeDirectGpuGeometryRenderState.STAGE
        );
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!ForgeDirectGpuGeometryRendererConfig.autoPlanEnabled()) {
            return;
        }

        String reason = this.requestedAutoPlanReason;
        if (reason != null) {
            this.requestedAutoPlanReason = null;
            this.runAutoPlan(reason, true);
            return;
        }

        if (this.ticksUntilNextAutoPlan > 0) {
            this.ticksUntilNextAutoPlan--;
            return;
        }

        if (!this.shouldAutoPlanForCameraMove()) {
            this.recordAutoPlanSkipped("MOVE_THRESHOLD");
            this.ticksUntilNextAutoPlan = ForgeDirectGpuGeometryRendererConfig.autoPlanCooldownTicks();
            return;
        }
        this.runAutoPlan(REASON_COOLDOWN, false);
    }

    private DrawListBuildResult runAutoPlan(String reason, boolean ignoreCooldown) {
        long start = System.nanoTime();
        String normalizedReason = normalizeReason(reason);
        if (!ignoreCooldown && this.ticksUntilNextAutoPlan > 0) {
            this.recordAutoPlanSkipped("COOLDOWN");
            return DrawListBuildResult.failure(start, "COOLDOWN", "auto-plan-cooldown", 0, 0);
        }
        String blockedReason = this.autoPlanBlockedReason();
        if (!"none".equals(blockedReason)) {
            this.recordAutoPlanSkipped(blockedReason);
            this.ticksUntilNextAutoPlan = ForgeDirectGpuGeometryRendererConfig.autoPlanCooldownTicks();
            return DrawListBuildResult.failure(start, blockedReason, blockedReason.toLowerCase(), 0, 0);
        }

        this.lastAutoPlanReason = normalizedReason;
        DrawListBuildResult result = this.buildDrawListInternal(
                ForgeDirectGpuGeometryRendererConfig.autoPlanMaxCandidates(),
                ForgeDirectGpuGeometryRendererConfig.autoPlanMaxSections(),
                ForgeDirectGpuGeometryRendererConfig.autoPlanMaxRecords(),
                ForgeDirectGpuGeometryRendererConfig.maxRecordsPerSection(),
                ForgeDirectGpuGeometryRendererConfig.renderDistanceChunks()
        );
        this.state.recordDrawList(result);
        this.autoPlanRuns++;
        this.lastAutoPlanDurationMs = result.durationMs();
        this.lastAutoPlanError = result.success() ? "none" : result.error();
        this.lastAutoPlanSkippedReason = result.success() ? "none" : result.skippedReason();
        if (!result.success()) {
            this.autoPlanFailures++;
            this.drawList = ForgeDirectGpuGeometryDrawList.empty();
            this.drawItemBuffer.close();
            this.indirectCommandBuffer.close();
        } else {
            this.rememberAutoPlanCameraAndHeap();
        }
        this.ticksUntilNextAutoPlan = ForgeDirectGpuGeometryRendererConfig.autoPlanCooldownTicks();
        return result;
    }

    private String autoPlanBlockedReason() {
        if (ForgeDirectGpuGeometryRendererConfig.autoPlanOnlyWhenEnabled() && !ForgeDirectGpuGeometryRendererConfig.isEnabled()) {
            return "DIRECT_RENDERER_DISABLED";
        }
        if (ForgeDirectGpuGeometryRendererConfig.autoPlanOnlyWhenActualDrawEnabled() && !ForgeDirectGpuGeometryRendererConfig.actualDrawEnabled()) {
            return "ACTUAL_DRAW_DISABLED";
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return "WORLD_MISSING";
        }
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        if (heap == null || !heap.isCreated()) {
            return "HEAP_MISSING";
        }
        if (this.instance.getGpuGeometryUploadManager().createUploadedSectionIdSnapshot().isEmpty()) {
            return "NO_UPLOADED_SECTIONS";
        }
        return "none";
    }

    private void recordAutoPlanSkipped(String reason) {
        this.autoPlanSkipped++;
        this.lastAutoPlanSkippedReason = reason == null ? "skipped" : reason;
        this.lastAutoPlanReason = "none";
        this.lastAutoPlanDurationMs = 0.0D;
        this.lastAutoPlanError = "none";
    }

    private boolean shouldAutoPlanForCameraMove() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return false;
        }
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        if (heap != null && heap.isCreated() && heap.generation() != this.lastAutoPlanHeapGeneration) {
            return true;
        }
        Vec3 position = cameraPositionOrPlayer(minecraft);
        if (Double.isNaN(this.lastAutoPlanX)) {
            return true;
        }
        double dx = position.x - this.lastAutoPlanX;
        double dy = position.y - this.lastAutoPlanY;
        double dz = position.z - this.lastAutoPlanZ;
        double threshold = ForgeDirectGpuGeometryRendererConfig.autoPlanMoveThresholdBlocks();
        return dx * dx + dy * dy + dz * dz >= threshold * threshold;
    }

    private void rememberAutoPlanCameraAndHeap() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            Vec3 position = cameraPositionOrPlayer(minecraft);
            this.lastAutoPlanX = position.x;
            this.lastAutoPlanY = position.y;
            this.lastAutoPlanZ = position.z;
        }
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        this.lastAutoPlanHeapGeneration = heap != null && heap.isCreated() ? heap.generation() : -1L;
    }

    private DrawListBuildResult buildDrawListInternal(int maxPlanCandidates, int maxSections, int maxRecords, int maxRecordsPerSection, int renderDistanceChunks) {
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            return DrawListBuildResult.failure(start, "NOT_RENDER_THREAD", "not-render-thread", 0, 0);
        }
        if (!ForgeDirectGpuGeometryRendererConfig.isEnabled()) {
            return DrawListBuildResult.failure(start, "DISABLED", "direct-renderer-disabled", 0, 0);
        }

        ForgeGpuGeometryUploadManager uploadManager = this.instance.getGpuGeometryUploadManager();
        ForgeGpuGeometryHeap heap = uploadManager.getHeapForDebugReadback();
        if (heap == null || !heap.isCreated()) {
            return DrawListBuildResult.failure(start, "HEAP_MISSING", "heap-not-created", 0, 0);
        }

        List<Integer> sectionIds = uploadManager.createUploadedSectionIdSnapshot();
        Collections.sort(sectionIds);
        if (sectionIds.isEmpty()) {
            return DrawListBuildResult.failure(start, "NO_UPLOADED_SECTIONS", "no-uploaded-sections", 0, 0);
        }

        int remainingRecords = Math.max(1, maxRecords);
        int inspectLimit = Math.max(1, maxPlanCandidates);
        int invalidMetadata = 0;
        int skippedSections = 0;
        long skippedRecords = 0;
        int rejectedByRadius = 0;
        int rejectedByFrustum = 0;
        String lastError = "none";
        ArrayList<DrawCandidate> candidates = new ArrayList<>();

        Minecraft minecraft = Minecraft.getInstance();
        CameraContext camera = CameraContext.create(minecraft);
        String selectionMode = camera.available() ? "RADIUS" : "FALLBACK_FIRST_N";
        boolean frustumAvailable = false;

        int inspected = 0;
        for (Integer sectionId : sectionIds) {
            if (sectionId == null || sectionId < 0) {
                skippedSections++;
                continue;
            }
            if (inspected >= inspectLimit) {
                skippedSections += Math.max(0, sectionIds.size() - inspected);
                break;
            }
            inspected++;
            try {
                int[] words = heap.readbackMetadata(sectionId);
                ForgeGpuGeometryDecodedMetadata metadata = ForgeGpuGeometryDecodedMetadata.decode(words);
                String validation = metadata.validate(heap, this.instance.getSectionGeometryManager(), sectionId, words);
                if (!"none".equals(validation)) {
                    invalidMetadata++;
                    lastError = "sectionId=" + sectionId + ' ' + validation;
                    continue;
                }
                int distanceChunks = camera.available() ? distanceChunks(metadata.position(), camera.chunkX(), camera.chunkZ()) : 0;
                if (camera.available() && distanceChunks > renderDistanceChunks) {
                    skippedSections++;
                    rejectedByRadius++;
                    continue;
                }
                double distanceSquared = camera.available() ? sectionDistanceSquared(metadata.position(), camera.x(), camera.y(), camera.z()) : sectionId;
                candidates.add(new DrawCandidate(sectionId, metadata, distanceChunks, distanceSquared));
            } catch (RuntimeException e) {
                invalidMetadata++;
                lastError = "sectionId=" + sectionId + ' ' + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        if (camera.available()) {
            candidates.sort((left, right) -> {
                int distanceCompare = Double.compare(left.distanceSquared(), right.distanceSquared());
                if (distanceCompare != 0) {
                    return distanceCompare;
                }
                return Integer.compare(left.sectionId(), right.sectionId());
            });
        }

        ArrayList<ForgeDirectGpuGeometryDrawItem> items = new ArrayList<>();
        int acceptedSections = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (items.size() >= maxSections) {
                skippedSections += candidates.size() - i;
                break;
            }
            if (remainingRecords <= 0) {
                skippedSections += candidates.size() - i;
                break;
            }
            DrawCandidate candidate = candidates.get(i);
            int itemCount = candidate.metadata().itemCount();
            int recordCount = Math.min(itemCount, Math.min(maxRecordsPerSection, remainingRecords));
            if (recordCount <= 0) {
                skippedSections++;
                continue;
            }
            if (itemCount > recordCount) {
                skippedRecords += itemCount - recordCount;
            }
            items.add(ForgeDirectGpuGeometryDrawItem.create(candidate.sectionId(), candidate.metadata(), 0, recordCount, candidate.distanceChunks()));
            acceptedSections++;
            remainingRecords -= recordCount;
        }

        long heapGeneration = heap.generation();
        String dimensionId = currentDimensionId(minecraft);
        double cameraX = camera.available() ? camera.x() : Double.NaN;
        double cameraY = camera.available() ? camera.y() : Double.NaN;
        double cameraZ = camera.available() ? camera.z() : Double.NaN;
        this.drawList = ForgeDirectGpuGeometryDrawList.of(items, heapGeneration, dimensionId, cameraX, cameraY, cameraZ, skippedSections, skippedRecords, selectionMode);
        if (this.drawList.isValid() && this.configuredDrawMode != ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION) {
            ForgeDirectGpuGeometryShader.ShaderStatus shaderStatus = this.prepareShaderForConfiguredMode();
            ForgeDirectGpuGeometryDrawMode effectiveDrawMode = this.effectiveDrawMode(shaderStatus);
            if (effectiveDrawMode == ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS
                    || effectiveDrawMode == ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT) {
                this.drawItemBuffer.upload(this.drawList);
            }
            if (effectiveDrawMode == ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT) {
                this.indirectCommandBuffer.upload(this.drawList);
            } else {
                this.indirectCommandBuffer.close();
            }
        } else if (this.configuredDrawMode == ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION) {
            this.drawItemBuffer.close();
            this.indirectCommandBuffer.close();
        }
        boolean success = this.drawList.isValid();
        String skippedReason = this.drawList.isValid() ? "none" : "NO_VALID_SECTIONS";
        return new DrawListBuildResult(
                success,
                skippedReason,
                success ? "none" : lastError,
                sectionIds.size(),
                this.drawList.itemCount(),
                this.drawList.recordCount(),
                this.drawList.vertexCount(),
                skippedSections,
                skippedRecords,
                selectionMode,
                heapGeneration,
                invalidMetadata,
                candidates.size(),
                acceptedSections,
                rejectedByRadius,
                rejectedByFrustum,
                frustumAvailable,
                camera.chunkLabel(),
                camera.sectionLabel(),
                dimensionId,
                elapsedMs(start)
        );
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ForgeDirectGpuGeometryRendererConfig.isEnabled()) {
            this.state.recordDrawSkip("DISABLED");
            return;
        }
        if (!ForgeDirectGpuGeometryRendererConfig.actualDrawEnabled()) {
            this.state.recordDrawSkip("ACTUAL_DRAW_DISABLED");
            return;
        }
        if (!this.drawList.isValid()) {
            this.state.recordDrawSkip("NO_DRAW_LIST");
            return;
        }
        if (this.instance.getCurrentEngineOptional().isEmpty()) {
            this.state.recordDrawSkip("ENGINE_MISSING");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || event.getCamera() == null) {
            this.state.recordDrawSkip("WORLD_MISSING");
            return;
        }
        String currentDimension = currentDimensionId(minecraft);
        this.state.recordCurrentDimension(currentDimension);

        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        if (heap == null || !heap.isCreated() || heap.geometryBufferIdForDirectRenderer() == 0) {
            this.state.recordDrawSkip("HEAP_MISSING");
            return;
        }
        if (this.drawList.isStale(heap.generation())) {
            this.state.recordDrawListStale(heap.generation());
            return;
        }
        if (this.drawList.isDimensionMismatch(currentDimension)) {
            this.state.recordDimensionMismatch(this.drawList.dimensionId(), currentDimension);
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.state.recordDrawSkip("NOT_RENDER_THREAD");
            return;
        }
        ForgeDirectGpuGeometryDrawMode effectiveDrawMode = this.resolveRenderDrawMode();
        if (effectiveDrawMode == null) {
            return;
        }

        long start = System.nanoTime();
        int drawCalls = 0;
        int drawItems = 0;
        long vertices = 0;
        String lastGlError = "none";
        String lastGlErrorStage = "none";
        String preExistingGlError = drainGlErrors();
        String stateRestoreError = "none";
        boolean stateRestoreFailed = false;
        String drawException = null;
        ForgeDirectGpuGeometryRenderStateGuard guard = null;
        try {
            guard = ForgeDirectGpuGeometryRenderStateGuard.capture();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            if (ForgeDirectGpuGeometryRendererConfig.ignoreDepth()) {
                RenderSystem.disableDepthTest();
            } else {
                RenderSystem.enableDepthTest();
            }
            if (ForgeDirectGpuGeometryRendererConfig.doubleSided()) {
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
                int geometryBufferId = heap.geometryBufferIdForDirectRenderer();
                float alpha = (float) ForgeDirectGpuGeometryRendererConfig.debugAlpha();
                if (effectiveDrawMode == ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT) {
                    if (!this.drawItemBuffer.matches(this.drawList) && !this.drawItemBuffer.upload(this.drawList)) {
                        this.state.recordDrawFailure("DRAW_ITEM_SSBO_UPLOAD_FAILED:" + this.drawItemBuffer.lastUploadError(), ForgeDirectGpuGeometryShader.ERROR_STAGE_DRAW_ITEM_SSBO_UPLOAD);
                        return;
                    }
                    if (!this.indirectCommandBuffer.matches(this.drawList) && !this.indirectCommandBuffer.upload(this.drawList)) {
                        this.state.recordDrawFailure("INDIRECT_COMMAND_BUFFER_UPLOAD_FAILED:" + this.indirectCommandBuffer.lastUploadError(), ForgeDirectGpuGeometryShader.ERROR_STAGE_INDIRECT_COMMAND_BUFFER_UPLOAD);
                        return;
                    }
                    ForgeDirectGpuGeometryShader.DrawCallResult drawResult = this.shader.drawIndirectWithDiagnostics(geometryBufferId, this.drawItemBuffer.bufferId(), this.indirectCommandBuffer.bufferId(), modelView, projection, this.drawList, alpha);
                    drawItems = this.drawList.itemCount();
                    drawCalls = drawItems == 0 ? 0 : 1;
                    vertices = this.drawList.vertexCount();
                    if (!drawResult.ok()) {
                        lastGlError = drawResult.formattedError();
                        lastGlErrorStage = drawResult.stage();
                    }
                } else if (effectiveDrawMode == ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS) {
                    if (!this.drawItemBuffer.matches(this.drawList) && !this.drawItemBuffer.upload(this.drawList)) {
                        this.state.recordDrawFailure("DRAW_ITEM_SSBO_UPLOAD_FAILED:" + this.drawItemBuffer.lastUploadError(), ForgeDirectGpuGeometryShader.ERROR_STAGE_DRAW_ITEM_SSBO_UPLOAD);
                        return;
                    }
                    ForgeDirectGpuGeometryShader.DrawCallResult drawResult = this.shader.drawMultiWithDiagnostics(geometryBufferId, this.drawItemBuffer.bufferId(), modelView, projection, this.drawList, alpha);
                    drawItems = this.drawList.itemCount();
                    drawCalls = drawItems == 0 ? 0 : 1;
                    vertices = this.drawList.vertexCount();
                    if (!drawResult.ok()) {
                        lastGlError = drawResult.formattedError();
                        lastGlErrorStage = drawResult.stage();
                    }
                } else {
                    for (ForgeDirectGpuGeometryDrawItem item : this.drawList.items()) {
                        ForgeDirectGpuGeometryShader.DrawCallResult drawResult = this.shader.drawItemWithDiagnostics(geometryBufferId, modelView, projection, item, alpha);
                        drawItems++;
                        drawCalls++;
                        vertices += item.vertexCount();
                        if (!drawResult.ok()) {
                            lastGlError = drawResult.formattedError();
                            lastGlErrorStage = drawResult.stage();
                            break;
                        }
                    }
                }
            } finally {
                poseStack.popPose();
            }
        } catch (RuntimeException e) {
            drawException = e.getClass().getSimpleName() + ": " + e.getMessage();
            VoxyForge.LOGGER.error("Failed during G5.4 direct GL geometry debug draw", e);
        } finally {
            try {
                this.shader.unbind();
                if (guard != null) {
                    ForgeDirectGpuGeometryRenderStateGuard.RestoreResult restoreResult = guard.restore();
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
            this.state.recordDrawException(drawException);
            return;
        }

        this.state.recordFrameDraws(
                drawItems,
                drawCalls,
                vertices,
                elapsedMs(start),
                ForgeDirectGpuGeometryRendererConfig.frameBudgetMs(),
                lastGlError,
                lastGlErrorStage,
                preExistingGlError,
                stateRestoreError,
                stateRestoreFailed
        );
    }

    private ForgeDirectGpuGeometryDrawMode resolveRenderDrawMode() {
        if (this.configuredDrawMode == ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION) {
            if (!this.shader.ensureReady()) {
                this.state.recordDrawSkip("SHADER_UNAVAILABLE");
                return null;
            }
            return ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION;
        }
        if (this.configuredDrawMode == ForgeDirectGpuGeometryDrawMode.AUTO) {
            if (this.shader.ensureIndirectReady()) {
                return ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT;
            }
            if (this.shader.ensureMultiDrawReady()) {
                return ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS;
            }
            if (!this.shader.ensureReady()) {
                this.state.recordDrawSkip("SHADER_UNAVAILABLE");
                return null;
            }
            return ForgeDirectGpuGeometryDrawMode.LOOP_PER_SECTION;
        }
        if (this.configuredDrawMode == ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT) {
            if (!this.shader.ensureIndirectReady()) {
                ForgeDirectGpuGeometryShader.ShaderStatus status = this.shader.createStatusSnapshot();
                this.state.recordDrawFailure("INDIRECT_UNSUPPORTED:" + status.indirectUnsupportedReason(), ForgeDirectGpuGeometryShader.ERROR_STAGE_NONE);
                return null;
            }
            return ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS_INDIRECT;
        }
        if (!this.shader.ensureMultiDrawReady()) {
            ForgeDirectGpuGeometryShader.ShaderStatus status = this.shader.createStatusSnapshot();
            this.state.recordDrawFailure("MULTI_DRAW_UNSUPPORTED:" + status.multiDrawUnsupportedReason(), ForgeDirectGpuGeometryShader.ERROR_STAGE_NONE);
            return null;
        }
        return ForgeDirectGpuGeometryDrawMode.MULTI_DRAW_ARRAYS;
    }

    private static double elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000.0D;
    }

    private static String currentDimensionId(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            return "none";
        }
        return minecraft.level.dimension().location().toString();
    }

    private static String drainGlErrors() {
        String last = "none";
        for (int i = 0; i < 16; i++) {
            int error = glGetError();
            if (error == GL_NO_ERROR) {
                break;
            }
            last = ForgeDirectGpuGeometryShader.formatGlError(error);
        }
        return last;
    }

    record DrawListBuildResult(
            boolean success,
            String skippedReason,
            String error,
            int uploadedSectionCandidates,
            int drawItems,
            long drawRecords,
            long drawVertices,
            int skippedSections,
            long skippedRecords,
            String selectionMode,
            long heapGeneration,
            int invalidMetadata,
            int candidateSections,
            int acceptedSections,
            int rejectedByRadius,
            int rejectedByFrustum,
            boolean frustumAvailable,
            String cameraChunk,
            String cameraSection,
            String dimensionId,
            double durationMs
    ) {
        private static DrawListBuildResult failure(long start, String skippedReason, String error, int candidates, int invalidMetadata) {
            return new DrawListBuildResult(false, skippedReason, error, candidates, 0, 0, 0, 0, 0, skippedReason, -1L, invalidMetadata, 0, 0, 0, 0, false, "none", "none", "none", elapsedMs(start));
        }
    }

    record StressResult(
            boolean success,
            String error,
            double durationMs,
            int plannedSections,
            long plannedRecords,
            boolean drawListValid,
            boolean shaderSupported,
            boolean sourceRegressionOk,
            boolean loopOk,
            boolean multiDrawOk,
            boolean indirectOk,
            boolean indirectAuditOk,
            long glErrorCount,
            long stateRestoreFailures
    ) {
    }

    private record AuditCounts(int invalidCommands, int invalidDrawItems, long auditedVertices) {
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? REASON_MANUAL_COMMAND : reason;
    }

    private static Vec3 cameraPositionOrPlayer(Minecraft minecraft) {
        if (minecraft.gameRenderer != null && minecraft.gameRenderer.getMainCamera() != null) {
            return minecraft.gameRenderer.getMainCamera().getPosition();
        }
        return minecraft.player == null ? Vec3.ZERO : minecraft.player.position();
    }

    private static int distanceChunks(long position, int playerChunkX, int playerChunkZ) {
        int level = Math.max(0, WorldEngine.getLevel(position));
        int scale = 1 << Math.min(12, level);
        int sectionChunkX = WorldEngine.getX(position) * 2 * scale;
        int sectionChunkZ = WorldEngine.getZ(position) * 2 * scale;
        return Math.max(Math.abs(sectionChunkX - playerChunkX), Math.abs(sectionChunkZ - playerChunkZ));
    }

    private static double sectionDistanceSquared(long position, double cameraX, double cameraY, double cameraZ) {
        int level = Math.max(0, WorldEngine.getLevel(position));
        int scale = 1 << Math.min(12, level);
        double sectionSize = 32.0D * scale;
        double centerX = WorldEngine.getX(position) * sectionSize + sectionSize * 0.5D;
        double centerY = WorldEngine.getY(position) * sectionSize + sectionSize * 0.5D;
        double centerZ = WorldEngine.getZ(position) * sectionSize + sectionSize * 0.5D;
        double dx = centerX - cameraX;
        double dy = centerY - cameraY;
        double dz = centerZ - cameraZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private record CameraContext(boolean available, double x, double y, double z, int chunkX, int chunkZ, String chunkLabel, String sectionLabel) {
        private static CameraContext create(Minecraft minecraft) {
            if (minecraft.level == null || minecraft.player == null) {
                return new CameraContext(false, 0.0D, 0.0D, 0.0D, 0, 0, "none", "none");
            }
            Vec3 position = cameraPositionOrPlayer(minecraft);
            int chunkX = floorDiv16(position.x);
            int chunkZ = floorDiv16(position.z);
            int sectionY = (int) Math.floor(position.y / 32.0D);
            return new CameraContext(true, position.x, position.y, position.z, chunkX, chunkZ, chunkX + "," + chunkZ, chunkX + "," + sectionY + "," + chunkZ);
        }

        private static int floorDiv16(double value) {
            return (int) Math.floor(value / 16.0D);
        }
    }

    private record DrawCandidate(int sectionId, ForgeGpuGeometryDecodedMetadata metadata, int distanceChunks, double distanceSquared) {
    }
}
