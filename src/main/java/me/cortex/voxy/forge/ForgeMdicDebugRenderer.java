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
    static final String STAGE = "G6_7_ELEMENTS_INDIRECT_COUNT_MDIC_DEBUG_DRAW";

    private final ForgeVoxyInstance instance;
    private final ForgeMdicDebugShader shader = new ForgeMdicDebugShader();
    private final ForgeMdicDebugIndirectCommandBuffer derivedIndirectCommandBuffer = new ForgeMdicDebugIndirectCommandBuffer();
    private final ForgeMdicDebugSharedIndexBuffer sharedIndexBuffer = new ForgeMdicDebugSharedIndexBuffer();
    private final ForgeMdicDebugElementsIndirectCommandBuffer elementsIndirectCommandBuffer = new ForgeMdicDebugElementsIndirectCommandBuffer();
    private final ForgeMdicDebugDrawCountBuffer drawCountBuffer = new ForgeMdicDebugDrawCountBuffer();
    private ForgeMdicDebugDrawMode configuredDrawMode = ForgeMdicDebugDrawMode.LOOP_PER_COMMAND;
    private String lastAutoModeSelectedReason = "configured-loop";
    private String lastAutoModeFallbackReason = "none";
    private int lastFrameApiDrawCalls;
    private int lastFrameLogicalCommands;
    private long lastFrameVertices;
    private long lastFrameIndices;
    private long drawApiCallsIssued;
    private long logicalCommandsDrawn;
    private long verticesDrawn;
    private long indicesDrawn;
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
    private long indirectAuditRuns;
    private long indirectAuditFailures;
    private String lastIndirectAuditError = "none";
    private double lastIndirectAuditDurationMs;
    private int lastAuditedIndirectCommands;
    private long lastAuditedIndirectBytes;
    private int lastInvalidIndirectCommands;
    private boolean lastIndirectCommandBufferMatch;
    private long lastIndirectAuditVertices;
    private long elementsIndirectAuditRuns;
    private long elementsIndirectAuditFailures;
    private String lastElementsIndirectAuditError = "none";
    private double lastElementsIndirectAuditDurationMs;
    private int lastAuditedElementsIndirectCommands;
    private long lastAuditedElementsIndirectBytes;
    private int lastInvalidElementsIndirectCommands;
    private boolean lastElementsIndirectCommandBufferMatch;
    private long lastElementsIndirectAuditIndices;
    private long lastElementsIndirectAuditLogicalVertices;
    private long drawCountAuditRuns;
    private long drawCountAuditFailures;
    private String lastDrawCountAuditError = "none";
    private double lastDrawCountAuditDurationMs;
    private int lastAuditedDrawCount;
    private int lastAuditedMaxDrawCount;
    private int lastInvalidDrawCount;
    private boolean lastDrawCountBufferMatch;
    private long lastDrawCountAuditGeneration = -1L;
    private String lastDrawCountAuditDimension = "none";
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
    private boolean lastStressLoopOk;
    private boolean lastStressMultiDrawOk;
    private boolean lastStressIndirectOk;
    private boolean lastStressElementsIndirectOk;
    private boolean lastStressElementsIndirectCountOk;
    private boolean lastStressAutoOk;
    private boolean lastStressDerivedIndirectAuditOk;
    private boolean lastStressElementsIndirectAuditOk;
    private boolean lastStressDrawCountAuditOk;
    private boolean lastStressParameterBufferOk;
    private boolean lastStressSharedIndexBufferOk;
    private boolean lastStressSourceRegressionOk;
    private boolean lastStressBucketDrawOk;
    private boolean lastStressBucketIndirectAuditOk;
    private boolean lastStressDirectionalFaceMaskOk;
    private boolean lastStressFaceMaskAuditOk;
    private boolean lastStressVisibilityPlanOk;
    private boolean lastStressSelectionAuditOk;
    private boolean lastStressFrustumFallbackOk;
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
            this.prepareShaderForMode(this.resolveEffectiveDrawMode(this.shader.createStatusSnapshot()).effectiveMode());
        }
    }

    void disableDraw() {
        ForgeVoxyRuntimeOverrides.setMdicDebugDrawActualDraw(false);
        this.recordSkip("ACTUAL_DRAW_DISABLED");
    }

    void setDrawMode(ForgeMdicDebugDrawMode mode) {
        this.configuredDrawMode = mode == null ? ForgeMdicDebugDrawMode.LOOP_PER_COMMAND : mode;
    }

    ForgeMdicDebugDrawMode configuredDrawMode() {
        return this.configuredDrawMode;
    }

    void clear() {
        this.shader.close();
        this.derivedIndirectCommandBuffer.close();
        this.sharedIndexBuffer.close();
        this.elementsIndirectCommandBuffer.close();
        this.drawCountBuffer.close();
        this.lastFrameApiDrawCalls = 0;
        this.lastFrameLogicalCommands = 0;
        this.lastFrameVertices = 0L;
        this.lastFrameIndices = 0L;
        this.drawApiCallsIssued = 0L;
        this.logicalCommandsDrawn = 0L;
        this.verticesDrawn = 0L;
        this.indicesDrawn = 0L;
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
        this.clearIndirectAuditStats();
        this.clearElementsIndirectAuditStats();
        this.clearDrawCountAuditStats();
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
        this.lastStressLoopOk = false;
        this.lastStressMultiDrawOk = false;
        this.lastStressIndirectOk = false;
        this.lastStressElementsIndirectOk = false;
        this.lastStressElementsIndirectCountOk = false;
        this.lastStressAutoOk = false;
        this.lastStressDerivedIndirectAuditOk = false;
        this.lastStressElementsIndirectAuditOk = false;
        this.lastStressDrawCountAuditOk = false;
        this.lastStressParameterBufferOk = false;
        this.lastStressSharedIndexBufferOk = false;
        this.lastStressSourceRegressionOk = false;
        this.lastStressBucketDrawOk = false;
        this.lastStressBucketIndirectAuditOk = false;
        this.lastStressDirectionalFaceMaskOk = false;
        this.lastStressFaceMaskAuditOk = false;
        this.lastStressVisibilityPlanOk = false;
        this.lastStressSelectionAuditOk = false;
        this.lastStressFrustumFallbackOk = false;
        this.lastStressGlErrorCount = 0L;
        this.lastStressStateRestoreFailures = 0L;
    }

    void clearIndirectAuditStats() {
        this.indirectAuditRuns = 0L;
        this.indirectAuditFailures = 0L;
        this.lastIndirectAuditError = "none";
        this.lastIndirectAuditDurationMs = 0.0D;
        this.lastAuditedIndirectCommands = 0;
        this.lastAuditedIndirectBytes = 0L;
        this.lastInvalidIndirectCommands = 0;
        this.lastIndirectCommandBufferMatch = false;
        this.lastIndirectAuditVertices = 0L;
    }

    void clearElementsIndirectAuditStats() {
        this.elementsIndirectAuditRuns = 0L;
        this.elementsIndirectAuditFailures = 0L;
        this.lastElementsIndirectAuditError = "none";
        this.lastElementsIndirectAuditDurationMs = 0.0D;
        this.lastAuditedElementsIndirectCommands = 0;
        this.lastAuditedElementsIndirectBytes = 0L;
        this.lastInvalidElementsIndirectCommands = 0;
        this.lastElementsIndirectCommandBufferMatch = false;
        this.lastElementsIndirectAuditIndices = 0L;
        this.lastElementsIndirectAuditLogicalVertices = 0L;
    }

    void clearDrawCountAuditStats() {
        this.drawCountAuditRuns = 0L;
        this.drawCountAuditFailures = 0L;
        this.lastDrawCountAuditError = "none";
        this.lastDrawCountAuditDurationMs = 0.0D;
        this.lastAuditedDrawCount = 0;
        this.lastAuditedMaxDrawCount = 0;
        this.lastInvalidDrawCount = 0;
        this.lastDrawCountBufferMatch = false;
        this.lastDrawCountAuditGeneration = -1L;
        this.lastDrawCountAuditDimension = "none";
    }

    ForgeMdicDebugShader.ShaderStatus createShaderStatusSnapshot() {
        return this.shader.createStatusSnapshot();
    }

    ForgeMdicDebugIndirectAuditResult auditDerivedIndirectBuffer() {
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            return this.recordIndirectAuditResult(ForgeMdicDebugIndirectAuditResult.failure("not-render-thread", elapsedMs(start)));
        }
        try {
            ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
            if (heap == null || !heap.isCreated()) {
                return this.recordIndirectAuditResult(ForgeMdicDebugIndirectAuditResult.failure("heap-missing", elapsedMs(start)));
            }
            ForgeMdicCommandManager manager = this.instance.getMdicCommandManager();
            ForgeMdicCommandList commandList = manager.commandListForDebugDraw();
            if (!commandList.isValid()) {
                return this.recordIndirectAuditResult(ForgeMdicDebugIndirectAuditResult.failure("command-list-invalid", elapsedMs(start)));
            }
            if (commandList.isStale(heap.generation())) {
                return this.recordIndirectAuditResult(ForgeMdicDebugIndirectAuditResult.failure("command-list-stale", elapsedMs(start)));
            }
            String currentDimension = currentDimensionId(Minecraft.getInstance());
            if (commandList.isDimensionMismatch(currentDimension)) {
                return this.recordIndirectAuditResult(ForgeMdicDebugIndirectAuditResult.failure("dimension-mismatch:" + commandList.dimensionId() + "!=" + currentDimension, elapsedMs(start)));
            }
            ForgeMdicDebugShader.ShaderStatus shaderStatus = this.shader.createStatusSnapshot();
            ModeResolution mode = this.resolveEffectiveDrawMode(shaderStatus);
            if (mode.effectiveMode() != ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS_INDIRECT) {
                return this.recordIndirectAuditResult(ForgeMdicDebugIndirectAuditResult.failure("effective-mode-not-indirect:" + mode.effectiveMode().name(), elapsedMs(start)));
            }
            if (!this.ensureDerivedIndirectBuffer(commandList)) {
                return this.recordIndirectAuditResult(ForgeMdicDebugIndirectAuditResult.failure("indirect-command-buffer-upload-failed:" + this.derivedIndirectCommandBuffer.lastUploadError(), elapsedMs(start)));
            }

            int[] words = this.derivedIndirectCommandBuffer.readbackWords();
            IndirectAuditCounts counts = compareIndirectCommands(words, commandList);
            DrawBudget budget = DrawBudget.from(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords());
            boolean bytesMatch = this.derivedIndirectCommandBuffer.bytes() == (long) budget.logicalCommands() * ForgeMdicDebugIndirectCommandBuffer.BYTES_PER_COMMAND;
            boolean countMatch = words.length == budget.logicalCommands() * ForgeMdicDebugIndirectCommandBuffer.WORDS_PER_COMMAND;
            boolean commandMatch = bytesMatch && countMatch && counts.invalidCommands() == 0 && counts.auditedVertices() == budget.vertices();
            boolean success = commandMatch;
            return this.recordIndirectAuditResult(new ForgeMdicDebugIndirectAuditResult(
                    success,
                    success ? "none" : "mismatch",
                    elapsedMs(start),
                    budget.logicalCommands(),
                    this.derivedIndirectCommandBuffer.bytes(),
                    counts.invalidCommands(),
                    commandMatch,
                    counts.auditedVertices()
            ));
        } catch (RuntimeException e) {
            return this.recordIndirectAuditResult(ForgeMdicDebugIndirectAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start)));
        }
    }

    ForgeMdicDebugElementsIndirectAuditResult auditElementsIndirectBuffer() {
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            return this.recordElementsIndirectAuditResult(ForgeMdicDebugElementsIndirectAuditResult.failure("not-render-thread", elapsedMs(start)));
        }
        try {
            ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
            if (heap == null || !heap.isCreated()) {
                return this.recordElementsIndirectAuditResult(ForgeMdicDebugElementsIndirectAuditResult.failure("heap-missing", elapsedMs(start)));
            }
            ForgeMdicCommandManager manager = this.instance.getMdicCommandManager();
            ForgeMdicCommandList commandList = manager.commandListForDebugDraw();
            if (!commandList.isValid()) {
                return this.recordElementsIndirectAuditResult(ForgeMdicDebugElementsIndirectAuditResult.failure("command-list-invalid", elapsedMs(start)));
            }
            if (commandList.isStale(heap.generation())) {
                return this.recordElementsIndirectAuditResult(ForgeMdicDebugElementsIndirectAuditResult.failure("command-list-stale", elapsedMs(start)));
            }
            String currentDimension = currentDimensionId(Minecraft.getInstance());
            if (commandList.isDimensionMismatch(currentDimension)) {
                return this.recordElementsIndirectAuditResult(ForgeMdicDebugElementsIndirectAuditResult.failure("dimension-mismatch:" + commandList.dimensionId() + "!=" + currentDimension, elapsedMs(start)));
            }
            ForgeMdicDebugShader.ShaderStatus shaderStatus = this.shader.createStatusSnapshot();
            ModeResolution mode = this.resolveEffectiveDrawMode(shaderStatus);
            if (mode.effectiveMode() != ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT) {
                return this.recordElementsIndirectAuditResult(ForgeMdicDebugElementsIndirectAuditResult.failure("effective-mode-not-elements-indirect:" + mode.effectiveMode().name(), elapsedMs(start)));
            }
            if (!this.ensureElementsIndirectResources(commandList)) {
                return this.recordElementsIndirectAuditResult(ForgeMdicDebugElementsIndirectAuditResult.failure("elements-indirect-upload-failed:" + this.elementsIndirectCommandBuffer.lastUploadError() + "/" + this.sharedIndexBuffer.lastUploadError(), elapsedMs(start)));
            }

            int[] words = this.elementsIndirectCommandBuffer.readbackWords();
            ElementsIndirectAuditCounts counts = compareElementsIndirectCommands(words, commandList);
            DrawBudget budget = DrawBudget.from(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords());
            boolean bytesMatch = this.elementsIndirectCommandBuffer.bytes() == (long) budget.logicalCommands() * ForgeMdicDebugElementsIndirectCommandBuffer.BYTES_PER_COMMAND;
            boolean countMatch = words.length == budget.logicalCommands() * ForgeMdicDebugElementsIndirectCommandBuffer.WORDS_PER_COMMAND;
            boolean commandMatch = bytesMatch && countMatch && counts.invalidCommands() == 0 && counts.auditedIndices() == budget.vertices();
            boolean success = commandMatch;
            return this.recordElementsIndirectAuditResult(new ForgeMdicDebugElementsIndirectAuditResult(
                    success,
                    success ? "none" : "mismatch",
                    elapsedMs(start),
                    budget.logicalCommands(),
                    this.elementsIndirectCommandBuffer.bytes(),
                    counts.invalidCommands(),
                    commandMatch,
                    counts.auditedIndices(),
                    counts.auditedLogicalVertices()
            ));
        } catch (RuntimeException e) {
            return this.recordElementsIndirectAuditResult(ForgeMdicDebugElementsIndirectAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start)));
        }
    }

    ForgeMdicDebugDrawCountAuditResult auditDrawCountBuffer() {
        long start = System.nanoTime();
        if (!RenderSystem.isOnRenderThread()) {
            return this.recordDrawCountAuditResult(ForgeMdicDebugDrawCountAuditResult.failure("not-render-thread", elapsedMs(start)));
        }
        try {
            ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
            if (heap == null || !heap.isCreated()) {
                return this.recordDrawCountAuditResult(ForgeMdicDebugDrawCountAuditResult.failure("heap-missing", elapsedMs(start)));
            }
            ForgeMdicCommandManager manager = this.instance.getMdicCommandManager();
            ForgeMdicCommandList commandList = manager.commandListForDebugDraw();
            if (!commandList.isValid()) {
                return this.recordDrawCountAuditResult(ForgeMdicDebugDrawCountAuditResult.failure("command-list-invalid", elapsedMs(start)));
            }
            if (commandList.isStale(heap.generation())) {
                return this.recordDrawCountAuditResult(ForgeMdicDebugDrawCountAuditResult.failure("command-list-stale", elapsedMs(start)));
            }
            String currentDimension = currentDimensionId(Minecraft.getInstance());
            if (commandList.isDimensionMismatch(currentDimension)) {
                return this.recordDrawCountAuditResult(ForgeMdicDebugDrawCountAuditResult.failure("dimension-mismatch:" + commandList.dimensionId() + "!=" + currentDimension, elapsedMs(start)));
            }
            ForgeMdicDebugShader.ShaderStatus shaderStatus = this.shader.createStatusSnapshot();
            ModeResolution mode = this.resolveEffectiveDrawMode(shaderStatus);
            if (mode.effectiveMode() != ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT) {
                return this.recordDrawCountAuditResult(ForgeMdicDebugDrawCountAuditResult.failure("effective-mode-not-elements-indirect-count:" + mode.effectiveMode().name(), elapsedMs(start)));
            }
            if (!this.ensureElementsIndirectCountResources(commandList)) {
                return this.recordDrawCountAuditResult(ForgeMdicDebugDrawCountAuditResult.failure("draw-count-upload-failed:" + this.drawCountBuffer.lastUploadError() + "/" + this.elementsIndirectCommandBuffer.lastUploadError() + "/" + this.sharedIndexBuffer.lastUploadError(), elapsedMs(start)));
            }

            int auditedValue = this.drawCountBuffer.readbackValue();
            int invalid = 0;
            if (auditedValue < 0) {
                invalid++;
            }
            if (auditedValue > this.drawCountBuffer.maxDrawCount()) {
                invalid++;
            }
            if (auditedValue > commandList.commandCount()) {
                invalid++;
            }
            if (this.drawCountBuffer.heapGeneration() != heap.generation()) {
                invalid++;
            }
            if (!this.drawCountBuffer.dimensionId().equals(currentDimension)) {
                invalid++;
            }
            boolean match = invalid == 0
                    && auditedValue == this.drawCountBuffer.drawCountValue()
                    && this.drawCountBuffer.matches(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords(), ForgeMdicDebugDrawConfig.maxDrawCount());
            boolean success = match;
            return this.recordDrawCountAuditResult(new ForgeMdicDebugDrawCountAuditResult(
                    success,
                    success ? "none" : "mismatch",
                    elapsedMs(start),
                    auditedValue,
                    this.drawCountBuffer.maxDrawCount(),
                    invalid,
                    match,
                    this.drawCountBuffer.heapGeneration(),
                    this.drawCountBuffer.dimensionId()
            ));
        } catch (RuntimeException e) {
            return this.recordDrawCountAuditResult(ForgeMdicDebugDrawCountAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start)));
        }
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
        this.lastStressLoopOk = false;
        this.lastStressMultiDrawOk = false;
        this.lastStressIndirectOk = false;
        this.lastStressElementsIndirectOk = false;
        this.lastStressElementsIndirectCountOk = false;
        this.lastStressAutoOk = false;
        this.lastStressDerivedIndirectAuditOk = false;
        this.lastStressElementsIndirectAuditOk = false;
        this.lastStressDrawCountAuditOk = false;
        this.lastStressParameterBufferOk = false;
        this.lastStressSharedIndexBufferOk = false;
        this.lastStressSourceRegressionOk = false;
        this.lastStressBucketDrawOk = false;
        this.lastStressBucketIndirectAuditOk = false;
        this.lastStressDirectionalFaceMaskOk = false;
        this.lastStressFaceMaskAuditOk = false;
        this.lastStressVisibilityPlanOk = false;
        this.lastStressSelectionAuditOk = false;
        this.lastStressFrustumFallbackOk = false;
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

                this.lastStressLoopOk = this.prepareStressMode(ForgeMdicDebugDrawMode.LOOP_PER_COMMAND);
                this.lastStressMultiDrawOk = this.prepareStressMode(ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS);
                this.lastStressIndirectOk = this.prepareStressMode(ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS_INDIRECT);
                ForgeMdicDebugIndirectAuditResult derivedAudit = this.auditDerivedIndirectBuffer();
                this.lastStressDerivedIndirectAuditOk = derivedAudit.success();
                this.lastStressElementsIndirectOk = this.prepareStressMode(ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT);
                ForgeMdicDebugElementsIndirectAuditResult elementsAudit = this.auditElementsIndirectBuffer();
                this.lastStressElementsIndirectAuditOk = elementsAudit.success();
                this.lastStressElementsIndirectCountOk = this.prepareStressMode(ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT);
                ForgeMdicDebugShader.ShaderStatus countShaderStatus = this.shader.createStatusSnapshot();
                ForgeMdicDebugDrawCountAuditResult drawCountAudit = countShaderStatus.elementsIndirectCountSupported()
                        ? this.auditDrawCountBuffer()
                        : ForgeMdicDebugDrawCountAuditResult.failure("unsupported:" + countShaderStatus.elementsIndirectCountUnsupportedReason(), 0.0D);
                this.lastStressDrawCountAuditOk = countShaderStatus.elementsIndirectCountSupported() ? drawCountAudit.success() : this.lastStressElementsIndirectOk;
                this.lastStressParameterBufferOk = countShaderStatus.elementsIndirectCountSupported()
                        ? countShaderStatus.parameterBufferSupported()
                        && countShaderStatus.multiDrawElementsIndirectCountSupported()
                        && this.drawCountBuffer.isCreated()
                        && this.drawCountBuffer.drawCountValue() > 0
                        : this.lastStressElementsIndirectOk;
                this.lastStressSharedIndexBufferOk = this.sharedIndexBuffer.isCreated() && this.sharedIndexBuffer.maxRecords() >= 1;
                this.lastStressAutoOk = this.prepareStressMode(ForgeMdicDebugDrawMode.AUTO);
                ForgeMdicCommandList stressCommandList = manager.commandListForDebugDraw();
                this.lastStressBucketDrawOk = stressCommandList.bucketAware()
                        && stressCommandList.bucketCommands() > 0
                        && stressCommandList.sectionCommands() == 0
                        && this.lastStressLoopOk
                        && this.lastStressMultiDrawOk
                        && this.lastStressIndirectOk
                        && this.lastStressElementsIndirectOk
                        && this.lastStressElementsIndirectCountOk
                        && this.lastStressAutoOk;
                this.lastStressBucketIndirectAuditOk = this.lastStressDerivedIndirectAuditOk
                        && derivedAudit.invalidCommands() == 0
                        && derivedAudit.commandBufferMatch();
                this.lastStressDirectionalFaceMaskOk = stressCommandList.directionalFaceMask()
                        && (stressCommandList.faceMaskCommandsAccepted() > 0 || stressCommandList.faceMaskCommandsRejected() > 0 || !"none".equals(stressCommandList.faceMaskFallbackReason()));
                this.lastStressFaceMaskAuditOk = this.lastStressAuditOk
                        && firstAudit.faceMaskAuditOk()
                        && firstAudit.invalidFaceMaskCommands() == 0;
                this.lastStressVisibilityPlanOk = firstPlan.success() && visibilityPlanOk(stressCommandList);
                this.lastStressSelectionAuditOk = this.lastStressAuditOk
                        && firstAudit.selectionAuditOk()
                        && firstAudit.invalidSelectionCommands() == 0
                        && firstAudit.invalidRadiusCommands() == 0
                        && firstAudit.invalidFrustumCommands() == 0;
                this.lastStressFrustumFallbackOk = frustumFallbackOk(stressCommandList);
                ForgeMdicDebugDrawStats autoStatus = this.createStatusSnapshot();
                ForgeMdicDebugShader.ShaderStatus autoShaderStatus = this.shader.createStatusSnapshot();
                ModeResolution autoMode = this.resolveEffectiveDrawMode(autoShaderStatus);
                this.lastStressDrawEnableOk = autoStatus.actualDrawEnabled()
                        && autoStatus.commandListValid()
                        && autoStatus.commandBufferCreated()
                        && autoStatus.shaderSupported()
                        && autoShaderStatus.readyFor(autoMode.effectiveMode());
                this.lastStressDrawStatusOk = this.lastStressDrawEnableOk
                        && "none".equals(autoStatus.lastGlError())
                        && autoStatus.stateRestoreFailures() == this.lastStressStateRestoreFailures;
                if (!this.lastStressLoopOk && "none".equals(error)) {
                    error = "loop-mode-not-ready";
                } else if (!this.lastStressMultiDrawOk && "none".equals(error)) {
                    error = "multi-draw-mode-not-ready:" + autoStatus.autoModeFallbackReason();
                } else if (!this.lastStressIndirectOk && "none".equals(error)) {
                    error = "indirect-mode-not-ready:" + derivedAudit.error();
                } else if (!this.lastStressDerivedIndirectAuditOk && "none".equals(error)) {
                    error = "derived-indirect-audit=" + derivedAudit.error();
                } else if (!this.lastStressElementsIndirectOk && "none".equals(error)) {
                    error = "elements-indirect-mode-not-ready:" + elementsAudit.error();
                } else if (!this.lastStressElementsIndirectAuditOk && "none".equals(error)) {
                    error = "elements-indirect-audit=" + elementsAudit.error();
                } else if (!this.lastStressElementsIndirectCountOk && "none".equals(error)) {
                    error = "elements-indirect-count-mode-not-ready:" + drawCountAudit.error();
                } else if (!this.lastStressDrawCountAuditOk && countShaderStatus.elementsIndirectCountSupported() && "none".equals(error)) {
                    error = "draw-count-audit=" + drawCountAudit.error();
                } else if (!this.lastStressParameterBufferOk && countShaderStatus.elementsIndirectCountSupported() && "none".equals(error)) {
                    error = "parameter-buffer-not-ready:" + countShaderStatus.elementsIndirectCountUnsupportedReason();
                } else if (!this.lastStressSharedIndexBufferOk && "none".equals(error)) {
                    error = "shared-index-buffer-not-ready:" + this.sharedIndexBuffer.lastUploadError();
                } else if (!this.lastStressAutoOk && "none".equals(error)) {
                    error = "auto-mode-not-ready";
                } else if (!this.lastStressBucketDrawOk && "none".equals(error)) {
                    error = "bucket-draw-not-ready";
                } else if (!this.lastStressBucketIndirectAuditOk && "none".equals(error)) {
                    error = "bucket-derived-indirect-audit-failed";
                } else if (!this.lastStressDirectionalFaceMaskOk && "none".equals(error)) {
                    error = "directional-face-mask-plan-failed";
                } else if (!this.lastStressFaceMaskAuditOk && "none".equals(error)) {
                    error = "face-mask-audit-failed";
                } else if (!this.lastStressVisibilityPlanOk && "none".equals(error)) {
                    error = "visibility-plan-failed";
                } else if (!this.lastStressSelectionAuditOk && "none".equals(error)) {
                    error = "selection-audit-failed";
                } else if (!this.lastStressFrustumFallbackOk && "none".equals(error)) {
                    error = "frustum-fallback-failed";
                } else if (!this.lastStressDrawStatusOk && "none".equals(error)) {
                    error = "draw-status-error:" + autoStatus.lastGlError();
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
                this.lastStressDrawClearOk = clearedStatus.lastFrameApiDrawCalls() == 0
                        && clearedStatus.drawApiCallsIssued() == 0
                        && !clearedStatus.shaderCompiled()
                        && !clearedStatus.derivedIndirectCommandBufferCreated()
                        && !clearedStatus.elementsIndirectCommandBufferCreated()
                        && !clearedStatus.drawCountBufferCreated();
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
                        && afterHeapClear.lastFrameApiDrawCalls() == 0
                        && !afterHeapClear.derivedIndirectCommandBufferCreated()
                        && !afterHeapClear.elementsIndirectCommandBufferCreated()
                        && !afterHeapClear.drawCountBufferCreated()
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
                this.lastStressVisibilityPlanOk = this.lastStressVisibilityPlanOk
                        && secondPlan.success()
                        && visibilityPlanOk(manager.commandListForDebugDraw());
                this.lastStressSelectionAuditOk = this.lastStressSelectionAuditOk
                        && secondAudit.success()
                        && secondAudit.selectionAuditOk()
                        && secondAudit.invalidSelectionCommands() == 0
                        && secondAudit.invalidRadiusCommands() == 0
                        && secondAudit.invalidFrustumCommands() == 0;
                this.lastStressFrustumFallbackOk = this.lastStressFrustumFallbackOk && frustumFallbackOk(manager.commandListForDebugDraw());
                if (!this.lastStressRebuildOk && "none".equals(error)) {
                    error = "rebuild=" + (secondPlan.success() ? manager.createStatusSnapshot().lastError() : secondPlan.error());
                } else if (!this.lastStressVisibilityPlanOk && "none".equals(error)) {
                    error = "rebuild-visibility-plan-failed";
                } else if (!this.lastStressSelectionAuditOk && "none".equals(error)) {
                    error = "rebuild-selection-audit-failed";
                } else if (!this.lastStressFrustumFallbackOk && "none".equals(error)) {
                    error = "rebuild-frustum-fallback-failed";
                }

                this.setDrawMode(ForgeMdicDebugDrawMode.AUTO);
                this.enableDraw();
                ForgeMdicDebugShader.ShaderStatus redrawShaderBefore = this.shader.createStatusSnapshot();
                ModeResolution redrawMode = this.resolveEffectiveDrawMode(redrawShaderBefore);
                this.prepareShaderForMode(redrawMode.effectiveMode());
                ForgeMdicDebugShader.ShaderStatus redrawShaderAfter = this.shader.createStatusSnapshot();
                ForgeMdicDebugDrawStats redrawStatus = this.createStatusSnapshot();
                this.lastStressRedrawOk = this.lastStressRebuildOk
                        && redrawStatus.actualDrawEnabled()
                        && redrawStatus.commandListValid()
                        && redrawStatus.commandBufferCreated()
                        && redrawShaderAfter.readyFor(redrawMode.effectiveMode());
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
                    this.setDrawMode(ForgeMdicDebugDrawMode.AUTO);
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
                && this.lastStressLoopOk
                && this.lastStressMultiDrawOk
                && this.lastStressIndirectOk
                && this.lastStressElementsIndirectOk
                && this.lastStressElementsIndirectCountOk
                && this.lastStressAutoOk
                && this.lastStressDerivedIndirectAuditOk
                && this.lastStressElementsIndirectAuditOk
                && this.lastStressDrawCountAuditOk
                && this.lastStressParameterBufferOk
                && this.lastStressSharedIndexBufferOk
                && this.lastStressSourceRegressionOk
                && this.lastStressBucketDrawOk
                && this.lastStressBucketIndirectAuditOk
                && this.lastStressDirectionalFaceMaskOk
                && this.lastStressFaceMaskAuditOk
                && this.lastStressVisibilityPlanOk
                && this.lastStressSelectionAuditOk
                && this.lastStressFrustumFallbackOk;
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
        boolean derivedStale = this.derivedIndirectCommandBuffer.isCreated()
                && (!this.derivedIndirectCommandBuffer.matches(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords())
                || this.derivedIndirectCommandBuffer.isStale(currentHeapGeneration, currentDimension));
        boolean elementsStale = this.elementsIndirectCommandBuffer.isCreated()
                && (!this.elementsIndirectCommandBuffer.matches(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords())
                || this.elementsIndirectCommandBuffer.isStale(currentHeapGeneration, currentDimension));
        boolean drawCountStale = this.drawCountBuffer.isCreated()
                && (!this.drawCountBuffer.matches(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords(), ForgeMdicDebugDrawConfig.maxDrawCount())
                || this.drawCountBuffer.isStale(currentHeapGeneration, currentDimension));
        ForgeMdicDebugShader.ShaderStatus shaderStatus = this.shader.createStatusSnapshot();
        ModeResolution mode = this.resolveEffectiveDrawMode(shaderStatus);
        return new ForgeMdicDebugDrawStats(
                STAGE,
                ForgeMdicDebugDrawConfig.isEnabled(),
                ForgeMdicDebugDrawConfig.actualDrawEnabled(),
                shaderStatus.shaderSupported(),
                shaderStatus.shaderCompiledFor(mode.effectiveMode()),
                shaderStatus.programCreatedFor(mode.effectiveMode()),
                shaderStatus.multiDrawSupported(),
                shaderStatus.indirectSupported(),
                shaderStatus.elementsIndirectSupported(),
                shaderStatus.elementsIndirectCountSupported(),
                shaderStatus.indirectParametersSupported(),
                shaderStatus.parameterBufferSupported(),
                shaderStatus.multiDrawElementsIndirectCountSupported(),
                shaderStatus.drawCountBufferSupported(),
                shaderStatus.drawIdSupported(),
                shaderStatus.baseInstanceSupported(),
                shaderStatus.elementsIndirectUnsupportedReason(),
                shaderStatus.elementsIndirectCountUnsupportedReason(),
                this.configuredDrawMode.name(),
                mode.effectiveMode().name(),
                mode.selectedReason(),
                mode.fallbackReason(),
                shaderStatus.shaderCompiled(),
                shaderStatus.multiDrawShaderCompiled(),
                shaderStatus.indirectShaderCompiled(),
                shaderStatus.elementsIndirectShaderCompiled(),
                shaderStatus.elementsIndirectCountShaderCompiled(),
                shaderStatus.elementsIndirectUsesBaseInstance(),
                commandBuffer.isCreated(),
                commandListValid,
                commandListStale,
                commandBufferStale,
                this.derivedIndirectCommandBuffer.isCreated(),
                this.derivedIndirectCommandBuffer.bytes(),
                derivedStale,
                this.sharedIndexBuffer.isCreated(),
                this.sharedIndexBuffer.bytes(),
                this.sharedIndexBuffer.maxRecords(),
                ForgeMdicDebugSharedIndexBuffer.FORMAT,
                this.elementsIndirectCommandBuffer.isCreated(),
                this.elementsIndirectCommandBuffer.bytes(),
                elementsStale,
                this.drawCountBuffer.isCreated(),
                this.drawCountBuffer.bytes(),
                this.drawCountBuffer.drawCountValue(),
                this.drawCountBuffer.maxDrawCount(),
                this.drawCountBuffer.heapGeneration(),
                this.drawCountBuffer.dimensionId(),
                drawCountStale,
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
                this.lastFrameApiDrawCalls,
                this.lastFrameLogicalCommands,
                this.lastFrameVertices,
                this.lastFrameIndices,
                this.drawApiCallsIssued,
                this.logicalCommandsDrawn,
                this.verticesDrawn,
                this.indicesDrawn,
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
                this.lastIndirectCommandBufferMatch && this.lastInvalidIndirectCommands == 0 && this.indirectAuditRuns > 0 && "none".equals(this.lastIndirectAuditError),
                this.indirectAuditRuns,
                this.indirectAuditFailures,
                this.lastIndirectAuditError,
                this.lastIndirectAuditDurationMs,
                this.lastAuditedIndirectCommands,
                this.lastAuditedIndirectBytes,
                this.lastInvalidIndirectCommands,
                this.lastIndirectCommandBufferMatch,
                this.lastIndirectAuditVertices,
                this.lastElementsIndirectCommandBufferMatch && this.lastInvalidElementsIndirectCommands == 0 && this.elementsIndirectAuditRuns > 0 && "none".equals(this.lastElementsIndirectAuditError),
                this.elementsIndirectAuditRuns,
                this.elementsIndirectAuditFailures,
                this.lastElementsIndirectAuditError,
                this.lastElementsIndirectAuditDurationMs,
                this.lastAuditedElementsIndirectCommands,
                this.lastAuditedElementsIndirectBytes,
                this.lastInvalidElementsIndirectCommands,
                this.lastElementsIndirectCommandBufferMatch,
                this.lastElementsIndirectAuditIndices,
                this.lastElementsIndirectAuditLogicalVertices,
                this.lastDrawCountBufferMatch && this.lastInvalidDrawCount == 0 && this.drawCountAuditRuns > 0 && "none".equals(this.lastDrawCountAuditError),
                this.drawCountAuditRuns,
                this.drawCountAuditFailures,
                this.lastDrawCountAuditError,
                this.lastDrawCountAuditDurationMs,
                this.lastAuditedDrawCount,
                this.lastAuditedMaxDrawCount,
                this.lastInvalidDrawCount,
                this.lastDrawCountBufferMatch,
                this.lastDrawCountAuditGeneration,
                this.lastDrawCountAuditDimension,
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
                this.lastStressLoopOk,
                this.lastStressMultiDrawOk,
                this.lastStressIndirectOk,
                this.lastStressElementsIndirectOk,
                this.lastStressElementsIndirectCountOk,
                this.lastStressAutoOk,
                this.lastStressDerivedIndirectAuditOk,
                this.lastStressElementsIndirectAuditOk,
                this.lastStressDrawCountAuditOk,
                this.lastStressParameterBufferOk,
                this.lastStressSharedIndexBufferOk,
                this.lastStressSourceRegressionOk,
                this.lastStressBucketDrawOk,
                this.lastStressBucketIndirectAuditOk,
                this.lastStressDirectionalFaceMaskOk,
                this.lastStressFaceMaskAuditOk,
                this.lastStressVisibilityPlanOk,
                this.lastStressSelectionAuditOk,
                this.lastStressFrustumFallbackOk,
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

        ForgeMdicDebugShader.ShaderStatus shaderStatus = this.shader.createStatusSnapshot();
        ModeResolution mode = this.resolveEffectiveDrawMode(shaderStatus);
        if (!this.prepareShaderForMode(mode.effectiveMode())) {
            this.recordSkip("SHADER_UNAVAILABLE");
            return;
        }
        if (mode.effectiveMode() == ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS_INDIRECT && this.derivedIndirectCommandBuffer.isStale(heap.generation(), currentDimension)) {
            this.recordSkip("INDIRECT_COMMAND_BUFFER_STALE");
            return;
        }
        if (mode.effectiveMode() == ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT && this.elementsIndirectCommandBuffer.isStale(heap.generation(), currentDimension)) {
            this.recordSkip("ELEMENTS_INDIRECT_COMMAND_BUFFER_STALE");
            return;
        }
        if (mode.effectiveMode() == ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT
                && (this.elementsIndirectCommandBuffer.isStale(heap.generation(), currentDimension) || this.drawCountBuffer.isStale(heap.generation(), currentDimension))) {
            this.recordSkip("DRAW_COUNT_BUFFER_STALE");
            return;
        }

        long start = System.nanoTime();
        DrawPassResult passResult = DrawPassResult.empty();
        String drawException = null;
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
                passResult = this.drawCommandsForMode(
                        mode.effectiveMode(),
                        heap.geometryBufferIdForDirectRenderer(),
                        commandBuffer.bufferIdForDebugRenderer(),
                        modelView,
                        projection,
                        commandList,
                        (float) ForgeMdicDebugDrawConfig.alpha()
                );
            } finally {
                poseStack.popPose();
            }
        } catch (RuntimeException e) {
            drawException = e.getClass().getSimpleName() + ": " + e.getMessage();
            VoxyForge.LOGGER.error("Failed during G6.2 MDIC command-buffer debug draw", e);
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

        this.recordFrame(passResult.apiDrawCalls(), passResult.logicalCommands(), passResult.vertices(), passResult.indices(), elapsedMs(start), passResult.glError(), passResult.glErrorStage(), stateRestoreError, stateRestoreFailed);
    }

    private DrawPassResult drawCommandsForMode(
            ForgeMdicDebugDrawMode mode,
            int geometryBufferId,
            int commandBufferId,
            Matrix4f modelView,
            Matrix4f projection,
            ForgeMdicCommandList commandList,
            float alpha
    ) {
        return switch (mode) {
            case MULTI_DRAW_ARRAYS -> this.drawMulti(geometryBufferId, commandBufferId, modelView, projection, commandList, alpha);
            case MULTI_DRAW_ARRAYS_INDIRECT -> this.drawIndirect(geometryBufferId, commandBufferId, modelView, projection, commandList, alpha);
            case MULTI_DRAW_ELEMENTS_INDIRECT -> this.drawElementsIndirect(geometryBufferId, commandBufferId, modelView, projection, commandList, alpha);
            case MULTI_DRAW_ELEMENTS_INDIRECT_COUNT -> this.drawElementsIndirectCount(geometryBufferId, commandBufferId, modelView, projection, commandList, alpha);
            case AUTO -> throw new IllegalStateException("AUTO must be resolved before drawing");
            case LOOP_PER_COMMAND -> this.drawLoop(geometryBufferId, commandBufferId, modelView, projection, commandList, alpha);
        };
    }

    private DrawPassResult drawLoop(int geometryBufferId, int commandBufferId, Matrix4f modelView, Matrix4f projection, ForgeMdicCommandList commandList, float alpha) {
        int apiDrawCalls = 0;
        int logicalCommands = 0;
        long vertices = 0L;
        int maxCommands = Math.min(ForgeMdicDebugDrawConfig.maxCommands(), commandList.commandCount());
        long recordsLeft = ForgeMdicDebugDrawConfig.maxRecords();
        for (int i = 0; i < maxCommands && recordsLeft > 0L; i++) {
            ForgeMdicCommand command = commandList.commands().get(i);
            int records = Math.max(0, command.recordCount());
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
            logicalCommands++;
            apiDrawCalls++;
            vertices += vertexCount;
            recordsLeft -= drawnRecords;
            if (!result.ok()) {
                return new DrawPassResult(apiDrawCalls, logicalCommands, vertices, 0L, result.formattedError(), result.stage());
            }
        }
        return new DrawPassResult(apiDrawCalls, logicalCommands, vertices, 0L, "none", "none");
    }

    private DrawPassResult drawMulti(int geometryBufferId, int commandBufferId, Matrix4f modelView, Matrix4f projection, ForgeMdicCommandList commandList, float alpha) {
        ForgeMdicDebugShader.DrawCallResult result = this.shader.drawMultiWithDiagnostics(
                geometryBufferId,
                commandBufferId,
                modelView,
                projection,
                commandList,
                ForgeMdicDebugDrawConfig.maxCommands(),
                ForgeMdicDebugDrawConfig.maxRecords(),
                alpha
        );
        int apiDrawCalls = result.logicalCommands() > 0 ? 1 : 0;
        return new DrawPassResult(apiDrawCalls, result.logicalCommands(), result.vertices(), 0L, result.formattedError(), result.stage());
    }

    private DrawPassResult drawIndirect(int geometryBufferId, int commandBufferId, Matrix4f modelView, Matrix4f projection, ForgeMdicCommandList commandList, float alpha) {
        if (!this.ensureDerivedIndirectBuffer(commandList)) {
            this.lastDrawError = "indirect-command-buffer-upload-failed:" + this.derivedIndirectCommandBuffer.lastUploadError();
            return new DrawPassResult(0, 0, 0L, 0L, "none", ForgeMdicDebugShader.ERROR_STAGE_INDIRECT_COMMAND_BUFFER_UPLOAD);
        }
        ForgeMdicDebugShader.DrawCallResult result = this.shader.drawIndirectWithDiagnostics(
                geometryBufferId,
                commandBufferId,
                this.derivedIndirectCommandBuffer.bufferId(),
                modelView,
                projection,
                commandList,
                ForgeMdicDebugDrawConfig.maxCommands(),
                ForgeMdicDebugDrawConfig.maxRecords(),
                alpha
        );
        int apiDrawCalls = result.logicalCommands() > 0 ? 1 : 0;
        return new DrawPassResult(apiDrawCalls, result.logicalCommands(), result.vertices(), 0L, result.formattedError(), result.stage());
    }

    private DrawPassResult drawElementsIndirect(int geometryBufferId, int commandBufferId, Matrix4f modelView, Matrix4f projection, ForgeMdicCommandList commandList, float alpha) {
        if (!this.ensureElementsIndirectResources(commandList)) {
            this.lastDrawError = "elements-indirect-upload-failed:" + this.elementsIndirectCommandBuffer.lastUploadError() + "/" + this.sharedIndexBuffer.lastUploadError();
            return new DrawPassResult(0, 0, 0L, 0L, "none", ForgeMdicDebugShader.ERROR_STAGE_ELEMENTS_INDIRECT_COMMAND_BUFFER_UPLOAD);
        }
        ForgeMdicDebugShader.DrawCallResult result = this.shader.drawElementsIndirectWithDiagnostics(
                geometryBufferId,
                commandBufferId,
                this.sharedIndexBuffer.bufferId(),
                this.elementsIndirectCommandBuffer.bufferId(),
                modelView,
                projection,
                commandList,
                ForgeMdicDebugDrawConfig.maxCommands(),
                ForgeMdicDebugDrawConfig.maxRecords(),
                alpha
        );
        DrawBudget budget = DrawBudget.from(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords());
        int apiDrawCalls = result.logicalCommands() > 0 ? 1 : 0;
        long logicalVertices = budget.logicalVertices();
        long indices = budget.vertices();
        return new DrawPassResult(apiDrawCalls, result.logicalCommands(), logicalVertices, indices, result.formattedError(), result.stage());
    }

    private DrawPassResult drawElementsIndirectCount(int geometryBufferId, int commandBufferId, Matrix4f modelView, Matrix4f projection, ForgeMdicCommandList commandList, float alpha) {
        if (!this.ensureElementsIndirectCountResources(commandList)) {
            this.lastDrawError = "elements-indirect-count-upload-failed:" + this.drawCountBuffer.lastUploadError() + "/" + this.elementsIndirectCommandBuffer.lastUploadError() + "/" + this.sharedIndexBuffer.lastUploadError();
            return new DrawPassResult(0, 0, 0L, 0L, "none", ForgeMdicDebugShader.ERROR_STAGE_DRAW_COUNT_BUFFER_UPLOAD);
        }
        ForgeMdicDebugShader.DrawCallResult result = this.shader.drawElementsIndirectCountWithDiagnostics(
                geometryBufferId,
                commandBufferId,
                this.sharedIndexBuffer.bufferId(),
                this.elementsIndirectCommandBuffer.bufferId(),
                this.drawCountBuffer.bufferId(),
                this.drawCountBuffer.drawCountValue(),
                this.drawCountBuffer.maxDrawCount(),
                modelView,
                projection,
                commandList,
                ForgeMdicDebugDrawConfig.maxCommands(),
                ForgeMdicDebugDrawConfig.maxRecords(),
                alpha
        );
        DrawBudget budget = DrawBudget.from(commandList, Math.min(ForgeMdicDebugDrawConfig.maxCommands(), this.drawCountBuffer.drawCountValue()), ForgeMdicDebugDrawConfig.maxRecords());
        int apiDrawCalls = result.logicalCommands() > 0 ? 1 : 0;
        long logicalVertices = budget.logicalVertices();
        long indices = budget.vertices();
        return new DrawPassResult(apiDrawCalls, result.logicalCommands(), logicalVertices, indices, result.formattedError(), result.stage());
    }

    private boolean prepareStressMode(ForgeMdicDebugDrawMode mode) {
        this.setDrawMode(mode);
        this.enableDraw();
        ModeResolution resolution = this.resolveEffectiveDrawMode(this.shader.createStatusSnapshot());
        if (!this.prepareShaderForMode(resolution.effectiveMode())) {
            return false;
        }
        if (resolution.effectiveMode() == ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS_INDIRECT) {
            ForgeMdicCommandList commandList = this.instance.getMdicCommandManager().commandListForDebugDraw();
            return this.ensureDerivedIndirectBuffer(commandList);
        }
        if (resolution.effectiveMode() == ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT) {
            ForgeMdicCommandList commandList = this.instance.getMdicCommandManager().commandListForDebugDraw();
            return this.ensureElementsIndirectResources(commandList);
        }
        if (resolution.effectiveMode() == ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT) {
            ForgeMdicCommandList commandList = this.instance.getMdicCommandManager().commandListForDebugDraw();
            return this.ensureElementsIndirectCountResources(commandList);
        }
        return true;
    }

    private static boolean visibilityPlanOk(ForgeMdicCommandList commandList) {
        return commandList != null
                && commandList.isValid()
                && commandList.planCandidateSections() > 0
                && commandList.planAcceptedSections() > 0
                && commandList.commandCount() > 0
                && !"none".equals(commandList.effectiveSelectionMode());
    }

    private static boolean frustumFallbackOk(ForgeMdicCommandList commandList) {
        if (commandList == null || !commandList.isValid()) {
            return false;
        }
        if (commandList.frustumAvailable()) {
            return "FRUSTUM_RADIUS".equals(commandList.effectiveSelectionMode());
        }
        String reason = commandList.selectionFallbackReason();
        if ("AUTO".equals(commandList.selectionMode()) || "FRUSTUM_RADIUS".equals(commandList.selectionMode())) {
            return "RADIUS".equals(commandList.effectiveSelectionMode())
                    && ("FRUSTUM_UNAVAILABLE".equals(reason) || "FRUSTUM_DISABLED".equals(reason));
        }
        return true;
    }

    private boolean prepareShaderForMode(ForgeMdicDebugDrawMode mode) {
        return switch (mode) {
            case MULTI_DRAW_ARRAYS -> this.shader.ensureMultiDrawReady();
            case MULTI_DRAW_ARRAYS_INDIRECT -> this.shader.ensureIndirectReady();
            case MULTI_DRAW_ELEMENTS_INDIRECT -> this.shader.ensureElementsIndirectReady();
            case MULTI_DRAW_ELEMENTS_INDIRECT_COUNT -> this.shader.ensureElementsIndirectCountReady();
            case AUTO -> this.prepareShaderForMode(this.resolveEffectiveDrawMode(this.shader.createStatusSnapshot()).effectiveMode());
            case LOOP_PER_COMMAND -> this.shader.ensureReady();
        };
    }

    private boolean ensureDerivedIndirectBuffer(ForgeMdicCommandList commandList) {
        if (this.derivedIndirectCommandBuffer.matches(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords())) {
            return true;
        }
        return this.derivedIndirectCommandBuffer.upload(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords());
    }

    private boolean ensureElementsIndirectResources(ForgeMdicCommandList commandList) {
        DrawBudget budget = DrawBudget.from(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords());
        int requiredRecords = (int) Math.min((long) ForgeMdicDebugDrawConfig.maxIndexedRecords(), Math.max(1L, budget.logicalVertices() / ForgeMdicDebugSharedIndexBuffer.VERTICES_PER_QUAD));
        int configuredRecords = Math.max(requiredRecords, Math.min(ForgeMdicDebugDrawConfig.maxIndexedRecords(), ForgeMdicDebugDrawConfig.maxRecords()));
        if (!this.sharedIndexBuffer.ensure(configuredRecords)) {
            return false;
        }
        if (this.elementsIndirectCommandBuffer.matches(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords())) {
            return true;
        }
        return this.elementsIndirectCommandBuffer.upload(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords());
    }

    private boolean ensureElementsIndirectCountResources(ForgeMdicCommandList commandList) {
        if (!this.ensureElementsIndirectResources(commandList)) {
            return false;
        }
        if (this.drawCountBuffer.matches(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords(), ForgeMdicDebugDrawConfig.maxDrawCount())) {
            return true;
        }
        return this.drawCountBuffer.upload(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords(), ForgeMdicDebugDrawConfig.maxDrawCount());
    }

    private ModeResolution resolveEffectiveDrawMode(ForgeMdicDebugShader.ShaderStatus shaderStatus) {
        ModeResolution resolution = switch (this.configuredDrawMode) {
            case LOOP_PER_COMMAND -> new ModeResolution(ForgeMdicDebugDrawMode.LOOP_PER_COMMAND, "configured-loop", "none");
            case MULTI_DRAW_ARRAYS -> shaderStatus.multiDrawSupported()
                    ? new ModeResolution(ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS, "configured-multi-draw-supported", "none")
                    : new ModeResolution(ForgeMdicDebugDrawMode.LOOP_PER_COMMAND, "configured-multi-draw-unsupported", shaderStatus.multiDrawUnsupportedReason());
            case MULTI_DRAW_ARRAYS_INDIRECT -> this.resolveForcedIndirect(shaderStatus);
            case MULTI_DRAW_ELEMENTS_INDIRECT -> this.resolveForcedElementsIndirect(shaderStatus);
            case MULTI_DRAW_ELEMENTS_INDIRECT_COUNT -> this.resolveForcedElementsIndirectCount(shaderStatus);
            case AUTO -> this.resolveAuto(shaderStatus);
        };
        this.lastAutoModeSelectedReason = resolution.selectedReason();
        this.lastAutoModeFallbackReason = resolution.fallbackReason();
        return resolution;
    }

    private ModeResolution resolveForcedIndirect(ForgeMdicDebugShader.ShaderStatus shaderStatus) {
        if (shaderStatus.indirectSupported()) {
            return new ModeResolution(ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS_INDIRECT, "configured-indirect-supported", "none");
        }
        if (shaderStatus.multiDrawSupported()) {
            return new ModeResolution(ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS, "configured-indirect-unsupported", shaderStatus.indirectUnsupportedReason());
        }
        return new ModeResolution(ForgeMdicDebugDrawMode.LOOP_PER_COMMAND, "configured-indirect-unsupported", shaderStatus.indirectUnsupportedReason());
    }

    private ModeResolution resolveForcedElementsIndirect(ForgeMdicDebugShader.ShaderStatus shaderStatus) {
        if (shaderStatus.elementsIndirectSupported()) {
            return new ModeResolution(ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT, "configured-elements-indirect-supported", "none");
        }
        ModeResolution fallback = this.resolveForcedIndirect(shaderStatus);
        return new ModeResolution(fallback.effectiveMode(), "configured-elements-indirect-unsupported", shaderStatus.elementsIndirectUnsupportedReason());
    }

    private ModeResolution resolveForcedElementsIndirectCount(ForgeMdicDebugShader.ShaderStatus shaderStatus) {
        if (shaderStatus.elementsIndirectCountSupported()) {
            return new ModeResolution(ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT, "configured-elements-indirect-count-supported", "none");
        }
        ModeResolution fallback = this.resolveForcedElementsIndirect(shaderStatus);
        return new ModeResolution(fallback.effectiveMode(), "configured-elements-indirect-count-unsupported", shaderStatus.elementsIndirectCountUnsupportedReason());
    }

    private ModeResolution resolveAuto(ForgeMdicDebugShader.ShaderStatus shaderStatus) {
        if (shaderStatus.elementsIndirectCountSupported()) {
            return new ModeResolution(ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT, "auto-selected-elements-indirect-count-supported", "none");
        }
        if (shaderStatus.elementsIndirectSupported()) {
            return new ModeResolution(ForgeMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT, "auto-fallback-elements-indirect-supported", shaderStatus.elementsIndirectCountUnsupportedReason());
        }
        if (shaderStatus.indirectSupported()) {
            return new ModeResolution(ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS_INDIRECT, "auto-fallback-arrays-indirect-supported", shaderStatus.elementsIndirectUnsupportedReason());
        }
        if (shaderStatus.multiDrawSupported()) {
            return new ModeResolution(ForgeMdicDebugDrawMode.MULTI_DRAW_ARRAYS, "auto-fallback-multi-draw-supported", shaderStatus.indirectUnsupportedReason());
        }
        return new ModeResolution(ForgeMdicDebugDrawMode.LOOP_PER_COMMAND, "auto-fallback-loop", shaderStatus.multiDrawUnsupportedReason());
    }

    private ForgeMdicDebugIndirectAuditResult recordIndirectAuditResult(ForgeMdicDebugIndirectAuditResult result) {
        this.indirectAuditRuns++;
        this.lastIndirectAuditError = result.success() ? "none" : result.error();
        this.lastIndirectAuditDurationMs = result.durationMs();
        this.lastAuditedIndirectCommands = result.auditedCommands();
        this.lastAuditedIndirectBytes = result.auditedBytes();
        this.lastInvalidIndirectCommands = result.invalidCommands();
        this.lastIndirectCommandBufferMatch = result.commandBufferMatch();
        this.lastIndirectAuditVertices = result.auditedVertices();
        if (!result.success()) {
            this.indirectAuditFailures++;
        }
        return result;
    }

    private ForgeMdicDebugElementsIndirectAuditResult recordElementsIndirectAuditResult(ForgeMdicDebugElementsIndirectAuditResult result) {
        this.elementsIndirectAuditRuns++;
        this.lastElementsIndirectAuditError = result.success() ? "none" : result.error();
        this.lastElementsIndirectAuditDurationMs = result.durationMs();
        this.lastAuditedElementsIndirectCommands = result.auditedCommands();
        this.lastAuditedElementsIndirectBytes = result.auditedBytes();
        this.lastInvalidElementsIndirectCommands = result.invalidCommands();
        this.lastElementsIndirectCommandBufferMatch = result.commandBufferMatch();
        this.lastElementsIndirectAuditIndices = result.auditedIndices();
        this.lastElementsIndirectAuditLogicalVertices = result.auditedLogicalVertices();
        if (!result.success()) {
            this.elementsIndirectAuditFailures++;
        }
        return result;
    }

    private ForgeMdicDebugDrawCountAuditResult recordDrawCountAuditResult(ForgeMdicDebugDrawCountAuditResult result) {
        this.drawCountAuditRuns++;
        this.lastDrawCountAuditError = result.success() ? "none" : result.error();
        this.lastDrawCountAuditDurationMs = result.durationMs();
        this.lastAuditedDrawCount = result.auditedDrawCount();
        this.lastAuditedMaxDrawCount = result.auditedMaxDrawCount();
        this.lastInvalidDrawCount = result.invalidDrawCount();
        this.lastDrawCountBufferMatch = result.drawCountBufferMatch();
        this.lastDrawCountAuditGeneration = result.auditGeneration();
        this.lastDrawCountAuditDimension = result.auditDimension();
        if (!result.success()) {
            this.drawCountAuditFailures++;
        }
        return result;
    }

    private void recordSkip(String reason) {
        this.lastFrameApiDrawCalls = 0;
        this.lastFrameLogicalCommands = 0;
        this.lastFrameVertices = 0L;
        this.lastFrameIndices = 0L;
        this.lastFrameRenderMs = 0.0D;
        this.lastFrameOverBudget = false;
        this.lastRenderSkippedReason = reason;
    }

    private void recordFrame(int apiDrawCalls, int logicalCommands, long vertices, long indices, double durationMs, String glError, String glErrorStage, String stateRestoreError, boolean stateRestoreFailed) {
        this.lastFrameApiDrawCalls = apiDrawCalls;
        this.lastFrameLogicalCommands = logicalCommands;
        this.lastFrameVertices = vertices;
        this.lastFrameIndices = indices;
        this.drawApiCallsIssued += apiDrawCalls;
        this.logicalCommandsDrawn += logicalCommands;
        this.verticesDrawn += vertices;
        this.indicesDrawn += indices;
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
        if ("none".equals(glError)) {
            this.lastDrawError = "none";
        }
        if (ForgeMdicDebugDrawConfig.debugLog()) {
            VoxyForge.LOGGER.info("G6.2 MDIC debug draw frame: mode={} logicalCommands={} apiDrawCalls={} vertices={} durationMs={} glError={} restoreError={}",
                    this.configuredDrawMode,
                    logicalCommands,
                    apiDrawCalls,
                    vertices,
                    durationMs,
                    glError,
                    stateRestoreError);
        }
    }

    private static IndirectAuditCounts compareIndirectCommands(int[] words, ForgeMdicCommandList commandList) {
        DrawBudget budget = DrawBudget.from(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords());
        int invalid = 0;
        long vertices = 0L;
        long recordsLeft = ForgeMdicDebugDrawConfig.maxRecords();
        for (int i = 0; i < budget.logicalCommands(); i++) {
            int wordOffset = i * ForgeMdicDebugIndirectCommandBuffer.WORDS_PER_COMMAND;
            if (wordOffset + 3 >= words.length) {
                invalid++;
                continue;
            }
            ForgeMdicCommand command = commandList.commands().get(i);
            int records = Math.max(0, command.recordCount());
            int drawnRecords = (int) Math.min(recordsLeft, records);
            int expectedCount = drawnRecords * 6;
            vertices += expectedCount;
            recordsLeft -= drawnRecords;
            if (words[wordOffset] != expectedCount || words[wordOffset + 1] != 1 || words[wordOffset + 2] != 0 || words[wordOffset + 3] != i) {
                invalid++;
            }
        }
        if (words.length != budget.logicalCommands() * ForgeMdicDebugIndirectCommandBuffer.WORDS_PER_COMMAND) {
            invalid += Math.abs(words.length - budget.logicalCommands() * ForgeMdicDebugIndirectCommandBuffer.WORDS_PER_COMMAND);
        }
        return new IndirectAuditCounts(invalid, vertices);
    }

    private static ElementsIndirectAuditCounts compareElementsIndirectCommands(int[] words, ForgeMdicCommandList commandList) {
        DrawBudget budget = DrawBudget.from(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords());
        int invalid = 0;
        long indices = 0L;
        long logicalVertices = 0L;
        long recordsLeft = ForgeMdicDebugDrawConfig.maxRecords();
        for (int i = 0; i < budget.logicalCommands(); i++) {
            int wordOffset = i * ForgeMdicDebugElementsIndirectCommandBuffer.WORDS_PER_COMMAND;
            if (wordOffset + 4 >= words.length) {
                invalid++;
                continue;
            }
            ForgeMdicCommand command = commandList.commands().get(i);
            int records = Math.max(0, command.recordCount());
            int drawnRecords = (int) Math.min(recordsLeft, records);
            int expectedCount = drawnRecords * ForgeMdicDebugSharedIndexBuffer.INDICES_PER_QUAD;
            indices += expectedCount;
            logicalVertices += (long) drawnRecords * ForgeMdicDebugSharedIndexBuffer.VERTICES_PER_QUAD;
            recordsLeft -= drawnRecords;
            if (words[wordOffset] != expectedCount
                    || words[wordOffset + 1] != 1
                    || words[wordOffset + 2] != 0
                    || words[wordOffset + 3] != 0
                    || words[wordOffset + 4] != i) {
                invalid++;
            }
        }
        if (words.length != budget.logicalCommands() * ForgeMdicDebugElementsIndirectCommandBuffer.WORDS_PER_COMMAND) {
            invalid += Math.abs(words.length - budget.logicalCommands() * ForgeMdicDebugElementsIndirectCommandBuffer.WORDS_PER_COMMAND);
        }
        return new ElementsIndirectAuditCounts(invalid, indices, logicalVertices);
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

    private record ModeResolution(ForgeMdicDebugDrawMode effectiveMode, String selectedReason, String fallbackReason) {
    }

    private record DrawBudget(int logicalCommands, long vertices, long logicalVertices) {
        static DrawBudget from(ForgeMdicCommandList commandList, int maxCommands, int maxRecords) {
            int commandLimit = Math.min(Math.max(0, maxCommands), commandList.commandCount());
            long recordsLeft = Math.max(0, maxRecords);
            int logicalCommands = 0;
            long vertices = 0L;
            long logicalVertices = 0L;
            for (int i = 0; i < commandLimit && recordsLeft > 0L; i++) {
                ForgeMdicCommand command = commandList.commands().get(i);
                int records = Math.max(0, command.recordCount());
                int drawnRecords = (int) Math.min(recordsLeft, records);
                logicalCommands++;
                vertices += (long) drawnRecords * 6L;
                logicalVertices += (long) drawnRecords * ForgeMdicDebugSharedIndexBuffer.VERTICES_PER_QUAD;
                recordsLeft -= drawnRecords;
            }
            return new DrawBudget(logicalCommands, vertices, logicalVertices);
        }
    }

    private record DrawPassResult(int apiDrawCalls, int logicalCommands, long vertices, long indices, String glError, String glErrorStage) {
        static DrawPassResult empty() {
            return new DrawPassResult(0, 0, 0L, 0L, "none", "none");
        }
    }

    private record IndirectAuditCounts(int invalidCommands, long auditedVertices) {
    }

    private record ElementsIndirectAuditCounts(int invalidCommands, long auditedIndices, long auditedLogicalVertices) {
    }
}
