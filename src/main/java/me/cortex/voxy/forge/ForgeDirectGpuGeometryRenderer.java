package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ForgeDirectGpuGeometryRenderer {
    private final ForgeVoxyInstance instance;
    private final ForgeDirectGpuGeometryRenderState state = new ForgeDirectGpuGeometryRenderState();
    private final ForgeDirectGpuGeometryShader shader = new ForgeDirectGpuGeometryShader();
    private ForgeDirectGpuGeometryDrawList drawList = ForgeDirectGpuGeometryDrawList.empty();

    ForgeDirectGpuGeometryRenderer(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    public void register() {
        MinecraftForge.EVENT_BUS.addListener(this::onRenderLevelStage);
    }

    public void markEnabledRuntime() {
        this.state.markInitialized();
    }

    public void clear() {
        this.drawList = ForgeDirectGpuGeometryDrawList.empty();
        this.state.clear();
        this.shader.close();
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
        DrawListBuildResult result = this.buildDrawListInternal();
        this.state.recordDrawList(result);
        if (!result.success()) {
            this.drawList = ForgeDirectGpuGeometryDrawList.empty();
        }
        return result;
    }

    public ForgeDirectGpuGeometryShader.ShaderStatus createShaderStatusSnapshot() {
        return this.shader.createStatusSnapshot();
    }

    public ForgeDirectGpuGeometryShader.ShaderStatus prepareShader() {
        this.shader.ensureReady();
        return this.shader.createStatusSnapshot();
    }

    public ForgeDirectGpuGeometryRendererStats createStatusSnapshot() {
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        boolean hasHeap = heap != null;
        boolean heapCreated = hasHeap && heap.isCreated();
        ForgeDirectGpuGeometryShader.ShaderStatus shaderStatus = this.shader.createStatusSnapshot();
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
                ForgeDirectGpuGeometryRendererConfig.maxDrawSections(),
                ForgeDirectGpuGeometryRendererConfig.maxDrawRecords(),
                ForgeDirectGpuGeometryRendererConfig.debugAlpha(),
                ForgeDirectGpuGeometryRendererConfig.ignoreDepth(),
                ForgeDirectGpuGeometryRendererConfig.doubleSided(),
                shaderStatus.shaderSupported(),
                shaderStatus.shaderCompiled(),
                shaderStatus.programCreated(),
                shaderStatus.lastShaderError(),
                shaderStatus.unsupportedReason(),
                shaderStatus.glVersion(),
                shaderStatus.glslVersion(),
                shaderStatus.usesSsbo(),
                this.drawList.isValid() && this.state.drawListValid(),
                this.state.drawItems(),
                this.state.drawListRecords(),
                this.state.drawListVertices(),
                this.state.drawListBuildRuns(),
                this.state.drawListBuildFailures(),
                this.state.lastDrawListBuildDurationMs(),
                this.state.lastDrawListError(),
                this.state.lastDrawListSkippedReason(),
                this.state.drawCallsIssued(),
                this.state.verticesDrawn(),
                this.state.lastFrameDrawCalls(),
                this.state.lastFrameVertices(),
                this.state.lastDrawDurationMs(),
                this.state.lastDrawError(),
                this.state.lastGlError(),
                ForgeDirectGpuGeometryRendererConfig.actualDrawEnabled(),
                ForgeDirectGpuGeometryRenderState.STAGE
        );
    }

    private DrawListBuildResult buildDrawListInternal() {
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

        int maxSections = ForgeDirectGpuGeometryRendererConfig.maxDrawSections();
        int remainingRecords = ForgeDirectGpuGeometryRendererConfig.maxDrawRecords();
        int invalidMetadata = 0;
        String lastError = "none";
        ArrayList<ForgeDirectGpuGeometryDrawItem> items = new ArrayList<>();

        for (Integer sectionId : sectionIds) {
            if (sectionId == null || sectionId < 0 || items.size() >= maxSections || remainingRecords <= 0) {
                break;
            }
            try {
                int[] words = heap.readbackMetadata(sectionId);
                ForgeGpuGeometryDecodedMetadata metadata = ForgeGpuGeometryDecodedMetadata.decode(words);
                String validation = metadata.validate(heap, this.instance.getSectionGeometryManager(), sectionId, words);
                if (!"none".equals(validation)) {
                    invalidMetadata++;
                    lastError = "sectionId=" + sectionId + ' ' + validation;
                    continue;
                }
                int recordCount = Math.min(metadata.itemCount(), remainingRecords);
                if (recordCount <= 0) {
                    continue;
                }
                items.add(ForgeDirectGpuGeometryDrawItem.create(sectionId, metadata, 0, recordCount));
                remainingRecords -= recordCount;
            } catch (RuntimeException e) {
                invalidMetadata++;
                lastError = "sectionId=" + sectionId + ' ' + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        this.drawList = ForgeDirectGpuGeometryDrawList.of(items);
        boolean success = this.drawList.isValid() && invalidMetadata == 0;
        String skippedReason = this.drawList.isValid() ? "none" : "NO_VALID_SECTIONS";
        return new DrawListBuildResult(
                success,
                skippedReason,
                success ? "none" : lastError,
                sectionIds.size(),
                this.drawList.itemCount(),
                this.drawList.recordCount(),
                this.drawList.vertexCount(),
                invalidMetadata,
                elapsedMs(start)
        );
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!ForgeDirectGpuGeometryRendererConfig.isEnabled()) {
            return;
        }
        if (!ForgeDirectGpuGeometryRendererConfig.actualDrawEnabled()) {
            this.state.recordDrawSkip("actual-draw-disabled");
            return;
        }
        if (!this.drawList.isValid()) {
            this.state.recordDrawSkip("draw-list-invalid");
            return;
        }
        if (this.instance.getCurrentEngineOptional().isEmpty()) {
            this.state.recordDrawSkip("engine-missing");
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || event.getCamera() == null) {
            this.state.recordDrawSkip("world-missing");
            return;
        }

        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        if (heap == null || !heap.isCreated() || heap.geometryBufferIdForDirectRenderer() == 0) {
            this.state.recordDrawSkip("heap-missing");
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.state.recordDrawSkip("not-render-thread");
            return;
        }

        long start = System.nanoTime();
        int drawCalls = 0;
        long vertices = 0;
        String lastGlError = "none";
        try {
            if (!this.shader.ensureReady()) {
                this.state.recordDrawSkip("shader-unavailable");
                return;
            }

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
                for (ForgeDirectGpuGeometryDrawItem item : this.drawList.items()) {
                    int glError = this.shader.drawItem(geometryBufferId, modelView, projection, item, alpha);
                    drawCalls++;
                    vertices += item.vertexCount();
                    if (glError != 0) {
                        lastGlError = ForgeDirectGpuGeometryShader.formatGlError(glError);
                        break;
                    }
                }
            } finally {
                poseStack.popPose();
            }
        } catch (RuntimeException e) {
            this.state.recordDrawException(e.getClass().getSimpleName() + ": " + e.getMessage());
            VoxyForge.LOGGER.error("Failed during G5.1 direct GL geometry debug draw", e);
            return;
        } finally {
            this.shader.unbind();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }

        this.state.recordFrameDraws(drawCalls, vertices, elapsedMs(start), lastGlError);
    }

    private static double elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000.0D;
    }

    record DrawListBuildResult(
            boolean success,
            String skippedReason,
            String error,
            int uploadedSectionCandidates,
            int drawItems,
            long drawRecords,
            long drawVertices,
            int invalidMetadata,
            double durationMs
    ) {
        private static DrawListBuildResult failure(long start, String skippedReason, String error, int candidates, int invalidMetadata) {
            return new DrawListBuildResult(false, skippedReason, error, candidates, 0, 0, 0, invalidMetadata, elapsedMs(start));
        }
    }
}
