package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;

import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_BINDING_ARB;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetBoolean;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glGetIntegeri;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER_BINDING;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER_BINDING;

final class ForgeTexturedMdicDebugRenderer {
    static final String STAGE = "G6_18_TEXTURED_MDIC_DEBUG_ONE_MODEL";

    private static final int MATCHING_COUNT_MAX_RECORDS = 65536;
    private static final float ALPHA = 1.0F;

    private final ForgeVoxyInstance instance;
    private final ForgeTexturedMdicDebugShader shader = new ForgeTexturedMdicDebugShader();
    private final ForgeMdicDebugSharedIndexBuffer sharedIndexBuffer = new ForgeMdicDebugSharedIndexBuffer();
    private final ForgeMdicDebugElementsIndirectCommandBuffer elementsIndirectCommandBuffer = new ForgeMdicDebugElementsIndirectCommandBuffer();
    private final ForgeMdicDebugDrawCountBuffer drawCountBuffer = new ForgeMdicDebugDrawCountBuffer();
    private ForgeTexturedMdicDebugDrawMode configuredDrawMode = ForgeTexturedMdicDebugDrawMode.AUTO;
    private String effectiveDrawMode = "UNSUPPORTED";
    private boolean enabled;
    private boolean sampleReady;
    private boolean atlasTextureReady;
    private boolean atlasPixelsUploaded;
    private boolean geometryHeapReady;
    private boolean metadataReady;
    private boolean mdicCommandReady;
    private boolean stale;
    private int atlasTextureId;
    private boolean fullAtlasTextureCreated;
    private int atlasTextureWidth;
    private int atlasTextureHeight;
    private int sampleModelId = -1;
    private int[] sampleModelIds = new int[0];
    private int sourceBlockStateId = -1;
    private String sourceBlockState = "none";
    private String sourceSprite = "none";
    private String sourceSpriteAtlas = "none";
    private boolean matchingRecordsKnown;
    private int matchingRecords;
    private int commandCount;
    private int lastFrameApiDrawCalls;
    private int lastFrameLogicalCommands;
    private long lastFrameVertices;
    private long drawCallsIssued;
    private long verticesDrawn;
    private String lastGlError = "none";
    private String lastGlErrorStage = "none";
    private long glErrorCount;
    private long stateRestoreFailures;
    private String lastStateRestoreError = "none";
    private String lastRenderSkippedReason = "CLEARED";
    private String lastUnsupportedReason = "not-built";

    ForgeTexturedMdicDebugRenderer(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevelStage);
    }

    ForgeTexturedMdicDebugStats build() {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordSkip("NOT_RENDER_THREAD");
            return this.createStatusSnapshot();
        }

        ForgeModelAtlasUploadStats atlasStatus = this.instance.getModelAtlasPixelUploader().createStatusSnapshot();
        ForgeModelAtlasSampleSetUploadStats sampleSetAtlasStatus = this.instance.getModelAtlasSampleSetUploader().createStatusSnapshot();
        boolean useSampleSet = sampleSetAtlasStatus.sampleSetAtlasUploadReady();
        if (!useSampleSet && !atlasStatus.sampleAtlasPixelsUploaded()) {
            atlasStatus = this.instance.getModelAtlasPixelUploader().uploadSample();
        }
        sampleSetAtlasStatus = this.instance.getModelAtlasSampleSetUploader().createStatusSnapshot();
        useSampleSet = sampleSetAtlasStatus.sampleSetAtlasUploadReady();
        if (!useSampleSet && (!atlasStatus.sampleAtlasPixelsUploaded() || atlasStatus.atlasTextureObjectId() == 0)) {
            this.markStale("ATLAS_SAMPLE_MISSING");
            return this.createStatusSnapshot();
        }

        this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed();
        this.instance.getGpuGeometryUploadManager().processForDebugCommand(8);
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        this.geometryHeapReady = heap != null && heap.isCreated() && heap.geometryBufferIdForDirectRenderer() != 0;
        if (!this.geometryHeapReady) {
            this.markStale("HEAP_MISSING");
            return this.createStatusSnapshot();
        }

        ForgeMdicCommandManager commandManager = this.instance.getMdicCommandManager();
        ForgeMdicCommandList commandList = commandManager.commandListForDebugDraw();
        String currentDimension = currentDimensionId();
        if (!commandList.isValid() || commandList.isStale(heap.generation()) || commandList.isDimensionMismatch(currentDimension)) {
            commandManager.planSample();
            commandList = commandManager.commandListForDebugDraw();
        }
        ForgeMdicCommandBuffer commandBuffer = commandManager.commandBufferForDebugDraw();
        if (!commandList.isValid()) {
            this.markStale("COMMAND_LIST_MISSING");
            return this.createStatusSnapshot();
        }
        if (!commandBuffer.matches(commandList)) {
            commandManager.buildBuffer();
            commandBuffer = commandManager.commandBufferForDebugDraw();
        }
        this.metadataReady = commandList.planAcceptedSections() > 0 && commandList.commandCount() > 0;
        this.mdicCommandReady = commandBuffer.isCreated() && commandBuffer.bufferIdForDebugRenderer() != 0 && commandBuffer.matches(commandList);
        if (!this.mdicCommandReady) {
            this.markStale("COMMAND_BUFFER_MISSING");
            return this.createStatusSnapshot();
        }

        ForgeTexturedMdicDebugShader.ShaderStatus shaderStatus = this.shader.createStatusSnapshot();
        this.effectiveDrawMode = this.resolveEffectiveDrawMode(shaderStatus).name();
        if (!this.prepareResources(commandList)) {
            this.markStale("DRAW_RESOURCES_MISSING");
            return this.createStatusSnapshot();
        }
        if (!this.shader.ensureReady()) {
            this.lastUnsupportedReason = this.shader.createStatusSnapshot().lastShaderError();
            this.markStale("SHADER_UNAVAILABLE");
            return this.createStatusSnapshot();
        }

        int[] activeModelIds = useSampleSet ? this.instance.getModelAtlasSampleSetUploader().modelIds() : new int[]{atlasStatus.sampleModelId()};
        int activeTextureId = useSampleSet ? sampleSetAtlasStatus.atlasTextureObjectId() : atlasStatus.atlasTextureObjectId();
        boolean activeFullAtlas = useSampleSet ? sampleSetAtlasStatus.fullAtlasTextureCreated() : atlasStatus.fullAtlasTextureCreated();
        int activeAtlasWidth = useSampleSet ? sampleSetAtlasStatus.actualTextureWidth() : atlasStatus.actualTextureWidth();
        int activeAtlasHeight = useSampleSet ? sampleSetAtlasStatus.actualTextureHeight() : atlasStatus.actualTextureHeight();
        int activeSampleModelId = activeModelIds.length == 0 ? -1 : activeModelIds[0];
        int activeBlockStateId = useSampleSet ? -1 : atlasStatus.sourceBlockStateId();
        String activeBlockState = useSampleSet ? sampleSetAtlasStatus.sampleBlockStates() : atlasStatus.sourceBlockState();
        String activeSprite = useSampleSet ? sampleSetAtlasStatus.sampleSprites() : atlasStatus.sourceSprite();
        String activeSpriteAtlas = useSampleSet ? "sample-set" : atlasStatus.sourceSpriteAtlas();

        MatchingCount count = this.countMatchingRecords(heap, commandList, activeModelIds);
        this.sampleReady = true;
        this.atlasTextureReady = true;
        this.atlasPixelsUploaded = true;
        this.atlasTextureId = activeTextureId;
        this.fullAtlasTextureCreated = activeFullAtlas;
        this.atlasTextureWidth = activeAtlasWidth;
        this.atlasTextureHeight = activeAtlasHeight;
        this.sampleModelIds = sanitizeModelIds(activeModelIds);
        this.sampleModelId = activeSampleModelId;
        this.sourceBlockStateId = activeBlockStateId;
        this.sourceBlockState = activeBlockState;
        this.sourceSprite = activeSprite;
        this.sourceSpriteAtlas = activeSpriteAtlas;
        this.matchingRecordsKnown = count.known();
        this.matchingRecords = count.records();
        this.commandCount = commandList.commandCount();
        this.stale = false;
        this.lastRenderSkippedReason = "none";
        this.lastUnsupportedReason = shaderStatus.unsupportedReason();
        return this.createStatusSnapshot();
    }

    void enable() {
        if (!this.sampleReady || !this.atlasTextureReady || !this.atlasPixelsUploaded || !this.mdicCommandReady) {
            this.enabled = false;
            this.recordSkip("SAMPLE_OR_COMMAND_MISSING");
            return;
        }
        this.enabled = true;
        this.lastRenderSkippedReason = "none";
    }

    void disable() {
        this.enabled = false;
        this.recordSkip("DISABLED");
    }

    void markStale(String reason) {
        this.enabled = false;
        this.sampleReady = false;
        this.atlasTextureReady = false;
        this.atlasPixelsUploaded = false;
        this.mdicCommandReady = false;
        this.atlasTextureId = 0;
        this.commandCount = 0;
        this.matchingRecordsKnown = false;
        this.matchingRecords = 0;
        this.stale = true;
        this.sharedIndexBuffer.close();
        this.elementsIndirectCommandBuffer.close();
        this.drawCountBuffer.close();
        this.recordSkip(reason == null || reason.isBlank() ? "STALE" : reason);
    }

    void clear() {
        this.enabled = false;
        this.sampleReady = false;
        this.atlasTextureReady = false;
        this.atlasPixelsUploaded = false;
        this.geometryHeapReady = false;
        this.metadataReady = false;
        this.mdicCommandReady = false;
        this.stale = false;
        this.atlasTextureId = 0;
        this.fullAtlasTextureCreated = false;
        this.atlasTextureWidth = 0;
        this.atlasTextureHeight = 0;
        this.sampleModelId = -1;
        this.sourceBlockStateId = -1;
        this.sampleModelIds = new int[0];
        this.sourceBlockState = "none";
        this.sourceSprite = "none";
        this.sourceSpriteAtlas = "none";
        this.matchingRecordsKnown = false;
        this.matchingRecords = 0;
        this.commandCount = 0;
        this.lastFrameApiDrawCalls = 0;
        this.lastFrameLogicalCommands = 0;
        this.lastFrameVertices = 0L;
        this.drawCallsIssued = 0L;
        this.verticesDrawn = 0L;
        this.lastGlError = "none";
        this.lastGlErrorStage = "none";
        this.glErrorCount = 0L;
        this.stateRestoreFailures = 0L;
        this.lastStateRestoreError = "none";
        this.lastRenderSkippedReason = "CLEARED";
        this.lastUnsupportedReason = "none";
        this.sharedIndexBuffer.close();
        this.elementsIndirectCommandBuffer.close();
        this.drawCountBuffer.close();
        this.shader.close();
    }

    ForgeTexturedMdicDebugStats createStatusSnapshot() {
        ForgeTexturedMdicDebugShader.ShaderStatus shaderStatus = this.shader.createStatusSnapshot();
        ForgeTexturedMdicDebugDrawMode effective = this.resolveEffectiveDrawMode(shaderStatus);
        this.effectiveDrawMode = effective.name();
        this.lastUnsupportedReason = shaderStatus.unsupportedReason();
        boolean actual = this.enabled
                && this.sampleReady
                && this.atlasTextureReady
                && this.atlasPixelsUploaded
                && this.geometryHeapReady
                && this.metadataReady
                && this.mdicCommandReady
                && !this.stale
                && (effective == ForgeTexturedMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT || effective == ForgeTexturedMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT);
        return new ForgeTexturedMdicDebugStats(
                STAGE,
                this.enabled,
                actual,
                shaderStatus.shaderCompiled(),
                shaderStatus.programCreated(),
                shaderStatus.lastShaderError(),
                this.sampleReady,
                this.atlasTextureReady,
                this.atlasPixelsUploaded,
                this.geometryHeapReady,
                this.metadataReady,
                this.mdicCommandReady,
                this.configuredDrawMode.name(),
                this.effectiveDrawMode,
                shaderStatus.elementsIndirectCountSupported(),
                this.lastUnsupportedReason,
                true,
                false,
                true,
                true,
                this.sampleModelIds.length > 1,
                this.sampleModelIds.length,
                this.sampleModelIdsString(),
                this.sampleModelId,
                this.sourceBlockStateId,
                this.sourceBlockState,
                this.sourceSprite,
                this.sourceSpriteAtlas,
                this.matchingRecordsKnown,
                this.matchingRecords,
                this.commandCount,
                this.lastFrameApiDrawCalls,
                this.lastFrameLogicalCommands,
                this.lastFrameVertices,
                this.drawCallsIssued,
                this.verticesDrawn,
                this.lastGlError,
                this.lastGlErrorStage,
                this.glErrorCount,
                this.stateRestoreFailures,
                this.lastStateRestoreError,
                this.lastRenderSkippedReason,
                this.lastFrameApiDrawCalls > 0 && (!this.matchingRecordsKnown || this.matchingRecords > 0) && "none".equals(this.lastGlError),
                this.stale,
                false,
                false
        );
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!this.enabled) {
            this.recordSkip("DISABLED");
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.recordSkip("NOT_RENDER_THREAD");
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || event.getCamera() == null) {
            this.recordSkip("WORLD_MISSING");
            return;
        }
        ForgeModelAtlasSampleSetUploadStats sampleSetAtlasStatus = this.instance.getModelAtlasSampleSetUploader().createStatusSnapshot();
        ForgeModelAtlasUploadStats atlasStatus = this.instance.getModelAtlasPixelUploader().createStatusSnapshot();
        boolean atlasStillValid = sampleSetAtlasStatus.sampleSetAtlasUploadReady()
                ? sampleSetAtlasStatus.atlasTextureObjectId() == this.atlasTextureId
                : atlasStatus.sampleAtlasPixelsUploaded() && atlasStatus.atlasTextureObjectId() == this.atlasTextureId;
        if (!atlasStillValid || this.atlasTextureId == 0) {
            this.markStale("ATLAS_STALE");
            return;
        }
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        if (heap == null || !heap.isCreated() || heap.geometryBufferIdForDirectRenderer() == 0) {
            this.recordSkip("HEAP_MISSING");
            return;
        }
        ForgeMdicCommandManager commandManager = this.instance.getMdicCommandManager();
        ForgeMdicCommandList commandList = commandManager.commandListForDebugDraw();
        ForgeMdicCommandBuffer commandBuffer = commandManager.commandBufferForDebugDraw();
        String currentDimension = currentDimensionId();
        if (!commandList.isValid()) {
            this.recordSkip("COMMAND_LIST_MISSING");
            return;
        }
        if (!commandBuffer.isCreated() || commandBuffer.bufferIdForDebugRenderer() == 0) {
            this.recordSkip("COMMAND_BUFFER_MISSING");
            return;
        }
        if (commandList.isStale(heap.generation()) || commandBuffer.isStale(heap.generation(), currentDimension)) {
            this.recordSkip("COMMAND_STALE");
            return;
        }
        if (commandList.isDimensionMismatch(currentDimension) || !commandBuffer.dimensionId().equals(currentDimension)) {
            this.recordSkip("DIMENSION_MISMATCH");
            return;
        }
        if (!this.prepareResources(commandList)) {
            this.recordSkip("DRAW_RESOURCES_MISSING");
            return;
        }
        if (!this.shader.ensureReady()) {
            this.recordSkip("SHADER_UNAVAILABLE");
            return;
        }

        String stateRestoreError = "none";
        boolean stateRestoreFailed = false;
        StateGuard guard = null;
        ForgeTexturedMdicDebugShader.DrawCallResult result = ForgeTexturedMdicDebugShader.DrawCallResult.empty();
        drainGlErrors();
        try {
            guard = StateGuard.capture();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);

            PoseStack poseStack = event.getPoseStack();
            Vec3 cameraPos = event.getCamera().getPosition();
            poseStack.pushPose();
            try {
                poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
                Matrix4f modelView = poseStack.last().pose();
                Matrix4f projection = event.getProjectionMatrix();
                result = this.draw(commandList, heap.geometryBufferIdForDirectRenderer(), commandBuffer.bufferIdForDebugRenderer(), modelView, projection);
            } finally {
                poseStack.popPose();
            }
        } catch (RuntimeException e) {
            this.lastGlError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.lastGlErrorStage = "TEXTURED_MDIC_DRAW_EXCEPTION";
            this.glErrorCount++;
            this.recordSkip("DRAW_EXCEPTION");
            return;
        } finally {
            try {
                this.shader.unbind();
                if (guard != null) {
                    StateGuard.RestoreResult restore = guard.restore();
                    if (!restore.success()) {
                        stateRestoreError = restore.error();
                        stateRestoreFailed = true;
                    }
                }
            } catch (RuntimeException e) {
                stateRestoreError = e.getClass().getSimpleName() + ": " + e.getMessage();
                stateRestoreFailed = true;
            }
        }

        if (stateRestoreFailed) {
            this.stateRestoreFailures++;
            this.lastStateRestoreError = stateRestoreError;
        } else {
            this.lastStateRestoreError = "none";
        }
        this.lastFrameApiDrawCalls = result.logicalCommands() > 0 ? 1 : 0;
        this.lastFrameLogicalCommands = result.logicalCommands();
        this.lastFrameVertices = result.vertices();
        this.drawCallsIssued += this.lastFrameApiDrawCalls;
        this.verticesDrawn += result.vertices();
        if (!result.ok()) {
            this.lastGlError = result.formattedError();
            this.lastGlErrorStage = result.stage();
            this.glErrorCount++;
        } else {
            this.lastGlError = "none";
            this.lastGlErrorStage = "none";
        }
        this.lastRenderSkippedReason = "none";
    }

    private ForgeTexturedMdicDebugShader.DrawCallResult draw(ForgeMdicCommandList commandList, int geometryBufferId, int commandBufferId, Matrix4f modelView, Matrix4f projection) {
        ForgeTexturedMdicDebugDrawMode effective = this.resolveEffectiveDrawMode(this.shader.createStatusSnapshot());
        if (effective == ForgeTexturedMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT) {
            return this.shader.drawElementsIndirectCountWithDiagnostics(
                    geometryBufferId,
                    commandBufferId,
                    this.sharedIndexBuffer.bufferId(),
                    this.elementsIndirectCommandBuffer.bufferId(),
                    this.drawCountBuffer.bufferId(),
                    this.drawCountBuffer.drawCountValue(),
                    this.drawCountBuffer.maxDrawCount(),
                    this.atlasTextureId,
                    this.fullAtlasTextureCreated,
                    this.atlasTextureWidth,
                    this.atlasTextureHeight,
                    this.sampleModelId,
                    this.sampleModelIds,
                    modelView,
                    projection,
                    commandList,
                    ForgeMdicDebugDrawConfig.maxCommands(),
                    ForgeMdicDebugDrawConfig.maxRecords(),
                    ALPHA
            );
        }
        return this.shader.drawElementsIndirectWithDiagnostics(
                geometryBufferId,
                commandBufferId,
                this.sharedIndexBuffer.bufferId(),
                this.elementsIndirectCommandBuffer.bufferId(),
                this.atlasTextureId,
                this.fullAtlasTextureCreated,
                this.atlasTextureWidth,
            this.atlasTextureHeight,
            this.sampleModelId,
            this.sampleModelIds,
            modelView,
                projection,
                commandList,
                ForgeMdicDebugDrawConfig.maxCommands(),
                ForgeMdicDebugDrawConfig.maxRecords(),
                ALPHA
        );
    }

    private boolean prepareResources(ForgeMdicCommandList commandList) {
        if (commandList == null || !commandList.isValid()) {
            return false;
        }
        DrawBudget budget = DrawBudget.from(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords());
        int requiredRecords = (int) Math.min((long) ForgeMdicDebugDrawConfig.maxIndexedRecords(), Math.max(1L, budget.logicalVertices() / ForgeMdicDebugSharedIndexBuffer.VERTICES_PER_QUAD));
        int configuredRecords = Math.max(requiredRecords, Math.min(ForgeMdicDebugDrawConfig.maxIndexedRecords(), ForgeMdicDebugDrawConfig.maxRecords()));
        if (!this.sharedIndexBuffer.ensure(configuredRecords)) {
            this.lastUnsupportedReason = this.sharedIndexBuffer.lastUploadError();
            return false;
        }
        if (!this.elementsIndirectCommandBuffer.matches(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords())
                && !this.elementsIndirectCommandBuffer.upload(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords())) {
            this.lastUnsupportedReason = this.elementsIndirectCommandBuffer.lastUploadError();
            return false;
        }
        ForgeTexturedMdicDebugDrawMode effective = this.resolveEffectiveDrawMode(this.shader.createStatusSnapshot());
        if (effective == ForgeTexturedMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT
                && !this.drawCountBuffer.matches(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords(), ForgeMdicDebugDrawConfig.maxDrawCount())
                && !this.drawCountBuffer.upload(commandList, ForgeMdicDebugDrawConfig.maxCommands(), ForgeMdicDebugDrawConfig.maxRecords(), ForgeMdicDebugDrawConfig.maxDrawCount())) {
            this.lastUnsupportedReason = this.drawCountBuffer.lastUploadError();
            return false;
        }
        this.effectiveDrawMode = effective.name();
        return effective == ForgeTexturedMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT
                || effective == ForgeTexturedMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT;
    }

    private ForgeTexturedMdicDebugDrawMode resolveEffectiveDrawMode(ForgeTexturedMdicDebugShader.ShaderStatus status) {
        return switch (this.configuredDrawMode) {
            case MULTI_DRAW_ELEMENTS_INDIRECT -> status.elementsIndirectSupported()
                    ? ForgeTexturedMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT
                    : ForgeTexturedMdicDebugDrawMode.AUTO;
            case MULTI_DRAW_ELEMENTS_INDIRECT_COUNT -> status.elementsIndirectCountSupported()
                    ? ForgeTexturedMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT
                    : status.elementsIndirectSupported() ? ForgeTexturedMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT : ForgeTexturedMdicDebugDrawMode.AUTO;
            case AUTO -> status.elementsIndirectCountSupported()
                    ? ForgeTexturedMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT_COUNT
                    : status.elementsIndirectSupported() ? ForgeTexturedMdicDebugDrawMode.MULTI_DRAW_ELEMENTS_INDIRECT : ForgeTexturedMdicDebugDrawMode.AUTO;
        };
    }

    private MatchingCount countMatchingRecords(ForgeGpuGeometryHeap heap, ForgeMdicCommandList commandList, int[] modelIds) {
        if (heap == null || !heap.isCreated() || commandList == null || !commandList.isValid() || modelIds == null || modelIds.length == 0) {
            return new MatchingCount(false, 0);
        }
        int matches = 0;
        long recordsLeft = Math.min(MATCHING_COUNT_MAX_RECORDS, Math.max(0, ForgeMdicDebugDrawConfig.maxRecords()));
        try {
            for (ForgeMdicCommand command : commandList.commands()) {
                if (recordsLeft <= 0L) {
                    break;
                }
                int records = Math.max(0, command.recordCount());
                int count = (int) Math.min(recordsLeft, records);
                long[] raw = heap.readbackGeometry(command.geometryPtr() + command.recordStart(), count);
                for (long record : raw) {
                    if (containsModelId(modelIds, ForgeVoxyQuadEncoder.extractModelId(record))) {
                        matches++;
                    }
                }
                recordsLeft -= count;
            }
            return new MatchingCount(true, matches);
        } catch (RuntimeException ignored) {
            return new MatchingCount(false, matches);
        }
    }

    private void recordSkip(String reason) {
        this.lastFrameApiDrawCalls = 0;
        this.lastFrameLogicalCommands = 0;
        this.lastFrameVertices = 0L;
        this.lastRenderSkippedReason = reason == null || reason.isBlank() ? "UNKNOWN" : reason;
    }

    private String sampleModelIdsString() {
        if (this.sampleModelIds.length == 0) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (int id : this.sampleModelIds) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    private static int[] sanitizeModelIds(int[] ids) {
        if (ids == null || ids.length == 0) {
            return new int[0];
        }
        int limit = Math.min(16, ids.length);
        int[] result = new int[limit];
        System.arraycopy(ids, 0, result, 0, limit);
        return result;
    }

    private static boolean containsModelId(int[] ids, int modelId) {
        for (int id : ids) {
            if (id == modelId) {
                return true;
            }
        }
        return false;
    }

    private static String currentDimensionId() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "none" : minecraft.level.dimension().location().toString();
    }

    private static void drainGlErrors() {
        while (glGetError() != GL_NO_ERROR) {
            // Drain stale errors before measuring this debug draw.
        }
    }

    private record MatchingCount(boolean known, int records) {
    }

    private record DrawBudget(int logicalVertices) {
        static DrawBudget from(ForgeMdicCommandList commandList, int maxCommands, int maxRecords) {
            int commandLimit = Math.min(Math.max(0, maxCommands), commandList.commandCount());
            long recordsLeft = Math.max(0, maxRecords);
            long vertices = 0L;
            for (int i = 0; i < commandLimit && recordsLeft > 0L; i++) {
                ForgeMdicCommand command = commandList.commands().get(i);
                int records = Math.max(0, command.recordCount());
                int drawnRecords = (int) Math.min(recordsLeft, records);
                vertices += (long) drawnRecords * ForgeMdicDebugSharedIndexBuffer.VERTICES_PER_QUAD;
                recordsLeft -= drawnRecords;
            }
            return new DrawBudget((int) Math.min(Integer.MAX_VALUE, vertices));
        }
    }

    private record StateGuard(
            int currentProgram,
            int vertexArrayBinding,
            int arrayBufferBinding,
            int elementArrayBufferBinding,
            int drawIndirectBufferBinding,
            int parameterBufferBinding,
            int geometryStorageBinding,
            int directDrawItemStorageBinding,
            int mdicCommandStorageBinding,
            int activeTexture,
            int texture0Binding,
            boolean depthTestEnabled,
            boolean depthMask,
            boolean blendEnabled,
            boolean cullEnabled
    ) {
        static StateGuard capture() {
            int active = glGetInteger(GL_ACTIVE_TEXTURE);
            glActiveTexture(GL_TEXTURE0);
            int textureBinding = glGetInteger(GL_TEXTURE_BINDING_2D);
            glActiveTexture(active);
            return new StateGuard(
                    glGetInteger(GL_CURRENT_PROGRAM),
                    glGetInteger(GL_VERTEX_ARRAY_BINDING),
                    glGetInteger(GL_ARRAY_BUFFER_BINDING),
                    glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING),
                    glGetInteger(GL_DRAW_INDIRECT_BUFFER_BINDING),
                    glGetInteger(GL_PARAMETER_BUFFER_BINDING_ARB),
                    glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, 0),
                    glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, ForgeDirectGpuGeometryDrawItemBuffer.BINDING_INDEX),
                    glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, ForgeMdicDebugShader.COMMAND_BINDING_INDEX),
                    active,
                    textureBinding,
                    glIsEnabled(GL_DEPTH_TEST),
                    glGetBoolean(GL_DEPTH_WRITEMASK),
                    glIsEnabled(GL_BLEND),
                    glIsEnabled(GL_CULL_FACE)
            );
        }

        RestoreResult restore() {
            try {
                glUseProgram(this.currentProgram);
                glBindVertexArray(this.vertexArrayBinding);
                glBindBuffer(GL_ARRAY_BUFFER, this.arrayBufferBinding);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.elementArrayBufferBinding);
                glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.drawIndirectBufferBinding);
                glBindBuffer(GL_PARAMETER_BUFFER_ARB, this.parameterBufferBinding);
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.geometryStorageBinding);
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeDirectGpuGeometryDrawItemBuffer.BINDING_INDEX, this.directDrawItemStorageBinding);
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeMdicDebugShader.COMMAND_BINDING_INDEX, this.mdicCommandStorageBinding);
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, this.texture0Binding);
                glActiveTexture(this.activeTexture);
                if (this.depthTestEnabled) {
                    glEnable(GL_DEPTH_TEST);
                } else {
                    glDisable(GL_DEPTH_TEST);
                }
                if (this.blendEnabled) {
                    glEnable(GL_BLEND);
                } else {
                    glDisable(GL_BLEND);
                }
                if (this.cullEnabled) {
                    glEnable(GL_CULL_FACE);
                } else {
                    glDisable(GL_CULL_FACE);
                }
                RenderSystem.depthMask(this.depthMask);
                int error = glGetError();
                return error == GL_NO_ERROR
                        ? new RestoreResult(true, "none")
                        : new RestoreResult(false, ForgeTexturedMdicDebugShader.formatGlError(error));
            } catch (RuntimeException e) {
                return new RestoreResult(false, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }

        record RestoreResult(boolean success, String error) {
        }
    }
}
