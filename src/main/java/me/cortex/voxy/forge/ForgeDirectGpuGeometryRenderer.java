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
    private ForgeDirectGpuGeometryDrawList drawList = ForgeDirectGpuGeometryDrawList.empty();
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
        this.ticksUntilNextAutoPlan = 0;
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

    public ForgeDirectGpuGeometryRendererStats createStatusSnapshot() {
        ForgeGpuGeometryHeap heap = this.instance.getGpuGeometryUploadManager().getHeapForDebugReadback();
        boolean hasHeap = heap != null;
        boolean heapCreated = hasHeap && heap.isCreated();
        long currentHeapGeneration = hasHeap ? heap.generation() : -1L;
        boolean drawListStale = this.drawList.isStale(currentHeapGeneration);
        ForgeDirectGpuGeometryShader.ShaderStatus shaderStatus = this.shader.createStatusSnapshot();
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
                shaderStatus.shaderCompiled(),
                shaderStatus.programCreated(),
                shaderStatus.lastShaderError(),
                shaderStatus.unsupportedReason(),
                shaderStatus.glVersion(),
                shaderStatus.glslVersion(),
                shaderStatus.usesSsbo(),
                this.drawList.isValid() && this.state.drawListValid() && !drawListStale,
                drawListStale,
                this.drawList.heapGeneration(),
                currentHeapGeneration,
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
                this.state.lastFrameDrawItems(),
                this.state.lastFrameVertices(),
                this.state.lastDrawDurationMs(),
                this.state.lastDrawError(),
                this.state.lastGlError(),
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
        this.drawList = ForgeDirectGpuGeometryDrawList.of(items, heapGeneration, skippedSections, skippedRecords, selectionMode);
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
        if (this.drawList.isStale(heap.generation())) {
            this.state.recordDrawListStale(heap.generation());
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.state.recordDrawSkip("not-render-thread");
            return;
        }

        long start = System.nanoTime();
        int drawCalls = 0;
        int drawItems = 0;
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
                    drawItems++;
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
            VoxyForge.LOGGER.error("Failed during G5.3 direct GL geometry debug draw", e);
            return;
        } finally {
            this.shader.unbind();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }

        this.state.recordFrameDraws(drawItems, drawCalls, vertices, elapsedMs(start), lastGlError);
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
            double durationMs
    ) {
        private static DrawListBuildResult failure(long start, String skippedReason, String error, int candidates, int invalidMetadata) {
            return new DrawListBuildResult(false, skippedReason, error, candidates, 0, 0, 0, 0, 0, skippedReason, -1L, invalidMetadata, 0, 0, 0, 0, false, "none", "none", elapsedMs(start));
        }
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
