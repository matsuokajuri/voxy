package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class ForgeFormalCmdgenRealSectionDryRun {
    static final String STAGE = "K6_FORMAL_CMDGEN_REAL_SECTION_DRY_RUN_NO_DRAW";
    private static final int DRAW_BUFFER_BINDING = 1;
    private static final int DRAW_COUNT_BUFFER_BINDING = 2;
    private static final int SECTION_METADATA_BUFFER_BINDING = 3;
    private static final int VISIBILITY_BUFFER_BINDING = 4;
    private static final int INDIRECT_LOOKUP_BUFFER_BINDING = 5;
    private static final int POSITION_SCRATCH_BUFFER_BINDING = 6;
    private static final int DRAW_COMMAND_FIELD_COUNT = 5;
    private static final int DRAW_COMMAND_STRIDE_BYTES = DRAW_COMMAND_FIELD_COUNT * Integer.BYTES;
    private static final int DRAW_COUNT_WORDS = 11;
    private static final int SECTION_METADATA_WORDS = ForgeSectionGeometryMetadata.METADATA_WORDS;
    private static final int MAX_ACCEPTED_SECTIONS = 8;
    private static final int SEARCH_RADIUS_CHUNKS = 2;
    private static final String REAL_SECTION_INPUT_SOURCE = "realSectionCandidateSnapshot";
    private static final String COMPUTE_SOURCE = """
            #version 430 core
            layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;

            struct DrawCommand {
                uint count;
                uint instanceCount;
                uint firstIndex;
                int baseVertex;
                uint baseInstance;
            };

            struct SectionMeta {
                uvec4 a;
                uvec4 b;
            };

            layout(std430, binding = 1) writeonly buffer DrawBuffer {
                DrawCommand cmdBuffer[];
            };

            layout(std430, binding = 2) buffer DrawCountBuffer {
                uint cmdGenDispatchX;
                uint cmdGenDispatchY;
                uint cmdGenDispatchZ;
                uint opaqueDrawCount;
                uint translucentDrawCount;
                uint temporalOpaqueDrawCount;
                DrawCommand cullDrawIndirectCommand;
            };

            layout(std430, binding = 3) readonly buffer SectionMetadataBuffer {
                SectionMeta sectionData[];
            };

            layout(std430, binding = 4) readonly buffer VisibilityBuffer {
                uint visibilityData[];
            };

            layout(std430, binding = 5) readonly buffer IndirectLookupBuffer {
                uint sectionCount;
                uint indirectLookup[];
            };

            layout(std430, binding = 6) writeonly buffer PositionScratchBuffer {
                uvec2 positionBuffer[];
            };

            bool firstRenderableBucket(uvec4 counts, out uint bucketIndex, out uint localOffset, out uint quadCount) {
                uint offset = 0u;
                uint count = counts.x & 0xFFFFu;
                offset += count;

                count = (counts.x >> 16) & 0xFFFFu;
                if (count != 0u) {
                    bucketIndex = 1u;
                    localOffset = offset;
                    quadCount = count;
                    return true;
                }
                offset += count;

                count = counts.y & 0xFFFFu;
                if (count != 0u) {
                    bucketIndex = 2u;
                    localOffset = offset;
                    quadCount = count;
                    return true;
                }
                offset += count;

                count = (counts.y >> 16) & 0xFFFFu;
                if (count != 0u) {
                    bucketIndex = 3u;
                    localOffset = offset;
                    quadCount = count;
                    return true;
                }
                offset += count;

                count = counts.z & 0xFFFFu;
                if (count != 0u) {
                    bucketIndex = 4u;
                    localOffset = offset;
                    quadCount = count;
                    return true;
                }
                offset += count;

                count = (counts.z >> 16) & 0xFFFFu;
                if (count != 0u) {
                    bucketIndex = 5u;
                    localOffset = offset;
                    quadCount = count;
                    return true;
                }
                offset += count;

                count = counts.w & 0xFFFFu;
                if (count != 0u) {
                    bucketIndex = 6u;
                    localOffset = offset;
                    quadCount = count;
                    return true;
                }
                offset += count;

                count = (counts.w >> 16) & 0xFFFFu;
                if (count != 0u) {
                    bucketIndex = 7u;
                    localOffset = offset;
                    quadCount = count;
                    return true;
                }
                return false;
            }

            void main() {
                uint index = gl_GlobalInvocationID.x;
                if (index >= sectionCount) {
                    return;
                }

                uint sectionId = indirectLookup[index];
                if ((visibilityData[sectionId] & 1u) == 0u) {
                    return;
                }

                SectionMeta meta = sectionData[sectionId];
                uint bucketIndex = 0u;
                uint localOffset = 0u;
                uint quadCount = 0u;
                if (!firstRenderableBucket(meta.b, bucketIndex, localOffset, quadCount)) {
                    return;
                }

                uint commandIndex = atomicAdd(opaqueDrawCount, 1u);
                uint geometryOffset = meta.a.w + localOffset;

                DrawCommand cmd;
                cmd.count = quadCount * 6u;
                cmd.instanceCount = 1u;
                cmd.firstIndex = 0u;
                cmd.baseVertex = int(geometryOffset) << 2;
                cmd.baseInstance = index;
                cmdBuffer[commandIndex] = cmd;
                positionBuffer[index] = meta.a.xy;
            }
            """;
    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "P0_PRODUCTION_CMDGEN_NOT_OPERATIONAL", "Production cmdgen not operational", "K6 uses an audit-only dry-run compute program and does not run production cmdgen.comp as the live renderer path.", "Promote production cmdgen only after traversal, global formal model ids, and terrain shader integration are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_VISIBILITY_TRAVERSAL_IMPLEMENTATION_MISSING", "Formal visibility traversal implementation missing", "K6 consumes a conservative real section candidate snapshot, not the original hierarchical traversal.", "Implement formal visibility traversal before live command generation.", true),
            new ForgeFormalRendererBlocker("P0", "P0_GLOBAL_FORMAL_MODEL_ID_GEOMETRY_MISSING", "Global formal model-id geometry missing", "The real section metadata comes from the current Forge section manager, but live geometry is not globally encoded with formal model ids.", "Move formal model ids into the formal BuiltSection/RenderDataFactory path.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_TERRAIN_SHADER_INTEGRATION_MISSING", "Formal terrain shader integration missing", "K6 reads command output back to CPU and never binds a formal terrain shader.", "Integrate the formal terrain shader after production command generation is ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MDIC_DRAW_DISABLED", "Formal MDIC draw disabled", "K6 never submits generated commands to glMultiDrawElementsIndirectCountARB.", "Keep draw disabled until formal command and shader paths are operational.", true),
            new ForgeFormalRendererBlocker("P1", "P1_HIERARCHICAL_OCCLUSION_MISSING", "Hierarchical occlusion missing", "K6 does not port the original hierarchical occlusion queue.", "Port formal hierarchical occlusion traversal later.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RENDER_DISTANCE_TRACKER_INCOMPLETE", "Render distance tracker incomplete", "K6 does not own the formal render-distance tracker.", "Add formal render distance tracking before live traversal.", false),
            new ForgeFormalRendererBlocker("P1", "P1_LIGHTMAP_MISSING", "Lightmap missing", "Formal lightmap semantics remain incomplete.", "Add formal lightmap binding and semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_BIOME_TINT_MISSING", "Biome tint missing", "Formal biome tint/modelColour semantics remain incomplete.", "Complete formal biome tint handling.", false),
            new ForgeFormalRendererBlocker("P1", "P1_MATERIAL_ALPHA_SEMANTICS_MISSING", "Material alpha semantics missing", "Formal material, alpha, and cutout semantics remain incomplete.", "Implement formal material/alpha/cutout behavior.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RESOURCE_REBUILD_AUTOMATION_INCOMPLETE", "Resource rebuild automation incomplete", "K6 stales dry-run resources but does not automatically rebuild production resources.", "Add rebuild orchestration when production resources exist.", false)
    );

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long auditRuns;
    private long clearRuns;
    private long auditFailures;
    private boolean dryRunReady;
    private boolean inputSnapshotReady;
    private boolean realSectionMetadataUsed;
    private boolean realSectionCandidateSnapshotUsed;
    private String validationInputSource = "not-run";
    private boolean syntheticValidationFixtureUsed;
    private int candidateSectionCount;
    private int acceptedSectionCount;
    private int rejectedSectionCount;
    private String candidateSectionSource = "none";
    private boolean sectionIdsKnown;
    private boolean sectionPositionsKnown;
    private boolean sectionMetadataAvailable;
    private boolean sectionMetadataReadbackOk;
    private boolean bucketOffsetsKnown;
    private boolean geometryPointerKnown;
    private boolean geometryRecordCountKnown;
    private boolean visibilityInputWritten;
    private boolean indirectLookupInputWritten;
    private boolean positionScratchInputWritten;
    private int recordsRejectedNoMetadata;
    private int recordsRejectedNoGeometryPointer;
    private int recordsRejectedNoBucketOffsets;
    private int recordsRejectedNoVisibleBuckets;
    private int recordsRejectedUnsafeState;
    private boolean compileAttempted;
    private boolean compileOk;
    private boolean linkOk;
    private int programId;
    private int drawCommandBufferId;
    private int drawCountBufferId;
    private int visibilityBufferId;
    private int renderListBufferId;
    private int positionScratchBufferId;
    private int sectionMetadataBufferId;
    private boolean dispatchRun;
    private boolean readbackOk;
    private boolean auditOk;
    private int generatedCommandCount;
    private int generatedDrawCount;
    private boolean drawCommandReadbackOk;
    private boolean drawCountReadbackOk;
    private boolean positionScratchReadbackOk;
    private boolean firstCommandMatchesAcceptedSection;
    private boolean firstCommandUsesRealSectionMetadata;
    private int firstCommandCount;
    private int firstCommandInstanceCount;
    private int firstCommandFirstIndex;
    private int firstCommandBaseVertex;
    private int firstCommandBaseInstance;
    private int expectedFirstCommandCount;
    private int expectedFirstCommandBaseVertex;
    private int expectedFirstCommandBaseInstance;
    private int firstAcceptedSectionId = -1;
    private String firstAcceptedSectionPosition = "none";
    private int firstAcceptedGeometryPtr;
    private int firstAcceptedBucketIndex = -1;
    private int firstAcceptedBucketQuadCount;
    private int positionScratchWord0;
    private int positionScratchWord1;
    private int expectedPositionScratchWord0;
    private int expectedPositionScratchWord1;
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String lastGlError = "none";
    private String lastFailureReason = "none";
    private ForgeSectionGeometryManager.RealMetadataScan lastScan =
            new ForgeSectionGeometryManager.RealMetadataScan("none", 0, 0, 0, 0, 0, 0, 0, 0, -1, "not-run", List.of());
    private ForgeSectionGeometryManager.RealMetadataSnapshot firstAcceptedSnapshot;
    private ForgeFormalCmdgenRealSectionDryRunAuditResult lastAudit =
            ForgeFormalCmdgenRealSectionDryRunAuditResult.failure("not-audited", 0.0D);

    ForgeFormalCmdgenRealSectionDryRun(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalCmdgenRealSectionDryRunStats build() {
        this.buildRuns++;
        this.lifecycleState = "BUILDING";
        this.lastLifecycleEvent = "build";
        this.lastFailureReason = "none";
        this.resetDryRunFlags();

        ForgeFormalTerrainRendererStats k1 = this.instance.getFormalTerrainRendererOwner().enable("k6-real-section-dry-run");
        ForgeFormalMdicViewportStats k2 = this.instance.getFormalMdicViewportOwner().enable("k6-real-section-dry-run");
        ForgeFormalCommandGenerationStats k3 = this.instance.getFormalCommandGenerationOwner().enable("k6-real-section-dry-run");
        ForgeFormalVisibilityStats k4 = this.instance.getFormalVisibilityOwner().enable("k6-real-section-dry-run");
        ForgeFormalCmdgenGpuValidationStats k5 = this.instance.getFormalCmdgenGpuValidator().build();

        if (!k1.formalTerrainRendererOwnerReady()
                || !k2.formalViewportOwnerReady()
                || !k3.formalCommandGenerationOwnerReady()
                || !k4.formalVisibilityOwnerReady()
                || !k5.cmdgenValidationProgramReady()
                || !k5.cmdgenValidationReadbackOk()
                || !k5.cmdgenValidationAuditOk()) {
            this.fail("formal-owner-or-k5-prerequisites-not-ready");
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("k6-real-section-dry-run-prerequisite-failed");
            return this.createStatusSnapshot();
        }

        if (!RenderSystem.isOnRenderThread()) {
            this.fail("not-render-thread");
            this.audit();
            return this.createStatusSnapshot();
        }

        if (!GL.getCapabilities().OpenGL43) {
            this.fail("opengl-4.3-compute-unavailable");
            this.audit();
            return this.createStatusSnapshot();
        }

        this.lastScan = this.collectRealSectionMetadata();
        this.captureScan(this.lastScan);
        if (this.lastScan.acceptedSections().isEmpty()) {
            this.fail("no-real-section-metadata-accepted:" + this.lastScan.lastRejectedReason());
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("k6-real-section-dry-run-no-real-section");
            return this.createStatusSnapshot();
        }

        try {
            this.closeOwnedResourcesOnRenderThread(false);
            this.firstAcceptedSnapshot = this.lastScan.acceptedSections().get(0);
            this.prepareExpectedFirstCommand(this.firstAcceptedSnapshot);
            this.compileAttempted = true;
            this.programId = this.compileProgram();
            this.compileOk = true;
            this.linkOk = true;
            this.allocateValidationBuffers(this.lastScan);
            this.dispatchValidationProgram(this.lastScan.acceptedSectionCount());
            this.readbackValidationBuffers(this.firstAcceptedSnapshot);
            this.readbackOk = this.drawCommandReadbackOk
                    && this.drawCountReadbackOk
                    && this.positionScratchReadbackOk
                    && this.sectionMetadataReadbackOk
                    && this.firstCommandMatchesAcceptedSection
                    && this.firstCommandUsesRealSectionMetadata;
            if (this.readbackOk) {
                this.dryRunReady = true;
                this.stale = false;
                this.requiresRebuild = false;
                this.lifecycleState = "BUILT";
                this.lastLifecycleEvent = "build-complete";
                this.lastFailureReason = "none";
            } else {
                this.fail("real-section-dry-run-readback-mismatch");
            }
        } catch (RuntimeException e) {
            this.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("k6-real-section-dry-run-build");
        return this.createStatusSnapshot();
    }

    ForgeFormalCmdgenRealSectionDryRunAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalCmdgenRealSectionDryRunAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
        }
        this.auditOk = this.lastAudit.success();
        if (!this.auditOk) {
            this.auditFailures++;
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalCmdgenRealSectionDryRunAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalCmdgenRealSectionDryRunStats createStatusSnapshot() {
        ForgeFormalTerrainRendererStats k1 = this.instance.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalMdicViewportStats k2 = this.instance.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalCommandGenerationStats k3 = this.instance.getFormalCommandGenerationOwner().createStatusSnapshot();
        ForgeFormalVisibilityStats k4 = this.instance.getFormalVisibilityOwner().createStatusSnapshot();
        ForgeFormalCmdgenGpuValidationStats k5 = this.instance.getFormalCmdgenGpuValidator().createStatusSnapshot();
        return new ForgeFormalCmdgenRealSectionDryRunStats(
                STAGE,
                this.buildRuns,
                this.auditRuns,
                this.clearRuns,
                this.auditFailures,
                k1.formalTerrainRendererOwnerReady(),
                k2.formalViewportOwnerReady(),
                k3.formalCommandGenerationOwnerReady(),
                k4.formalVisibilityOwnerReady(),
                k5.cmdgenValidationProgramReady(),
                this.dryRunReady && !this.stale,
                this.inputSnapshotReady,
                this.realSectionMetadataUsed,
                this.realSectionCandidateSnapshotUsed,
                this.validationInputSource,
                this.syntheticValidationFixtureUsed,
                this.candidateSectionCount,
                this.acceptedSectionCount,
                this.rejectedSectionCount,
                this.candidateSectionSource,
                this.sectionIdsKnown,
                this.sectionPositionsKnown,
                this.sectionMetadataAvailable,
                this.sectionMetadataReadbackOk,
                this.bucketOffsetsKnown,
                this.geometryPointerKnown,
                this.geometryRecordCountKnown,
                this.visibilityInputWritten,
                this.indirectLookupInputWritten,
                this.positionScratchInputWritten,
                this.recordsRejectedNoMetadata,
                this.recordsRejectedNoGeometryPointer,
                this.recordsRejectedNoBucketOffsets,
                this.recordsRejectedNoVisibleBuckets,
                this.recordsRejectedUnsafeState,
                this.compileAttempted,
                this.compileOk,
                this.linkOk,
                this.programId,
                this.dispatchRun,
                this.readbackOk,
                this.auditOk,
                this.generatedCommandCount,
                this.generatedDrawCount,
                this.drawCommandReadbackOk,
                this.drawCountReadbackOk,
                this.positionScratchReadbackOk,
                this.firstCommandMatchesAcceptedSection,
                this.firstCommandUsesRealSectionMetadata,
                DRAW_COMMAND_STRIDE_BYTES,
                DRAW_COMMAND_FIELD_COUNT,
                this.firstCommandCount,
                this.firstCommandInstanceCount,
                this.firstCommandFirstIndex,
                this.firstCommandBaseVertex,
                this.firstCommandBaseInstance,
                this.expectedFirstCommandCount,
                this.expectedFirstCommandBaseVertex,
                this.expectedFirstCommandBaseInstance,
                this.firstAcceptedSectionId,
                this.firstAcceptedSectionPosition,
                this.firstAcceptedGeometryPtr,
                this.firstAcceptedBucketIndex,
                this.firstAcceptedBucketQuadCount,
                this.positionScratchWord0,
                this.positionScratchWord1,
                this.expectedPositionScratchWord0,
                this.expectedPositionScratchWord1,
                this.drawCommandBufferId != 0,
                this.drawCountBufferId != 0,
                this.visibilityBufferId != 0,
                this.renderListBufferId != 0,
                this.positionScratchBufferId != 0,
                this.sectionMetadataBufferId != 0,
                true,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                this.lifecycleState,
                this.lastLifecycleEvent,
                this.stale,
                this.requiresRebuild,
                BLOCKERS.size(),
                countBlockers("P0"),
                countBlockers("P1"),
                countBlockers("P2"),
                compactBlockers(),
                this.lastGlError,
                this.lastFailureReason,
                this.lastAudit.success(),
                this.lastAudit.error()
        );
    }

    String dump() {
        ForgeFormalCmdgenRealSectionDryRunStats status = this.createStatusSnapshot();
        return "K6 formal cmdgen real-section dry-run: stage=" + status.stage()
                + " validationInputSource=" + status.validationInputSource()
                + " candidateSectionSource=" + status.candidateSectionSource()
                + " candidateSectionCount=" + status.candidateSectionCount()
                + " acceptedSectionCount=" + status.acceptedSectionCount()
                + " firstAcceptedSectionId=" + status.firstAcceptedSectionId()
                + " firstAcceptedSectionPosition=" + status.firstAcceptedSectionPosition()
                + " generatedCommandCount=" + status.generatedCommandCount()
                + " generatedDrawCount=" + status.generatedDrawCount()
                + " firstCommand=[" + status.firstCommandCount()
                + "," + status.firstCommandInstanceCount()
                + "," + status.firstCommandFirstIndex()
                + "," + status.firstCommandBaseVertex()
                + "," + status.firstCommandBaseInstance() + "]"
                + " expectedFirstCommand=[" + status.expectedFirstCommandCount()
                + ",1,0," + status.expectedFirstCommandBaseVertex()
                + "," + status.expectedFirstCommandBaseInstance() + "]"
                + " positionScratch=[" + Integer.toUnsignedString(status.positionScratchWord0())
                + "," + Integer.toUnsignedString(status.positionScratchWord1()) + "]"
                + " expectedPositionScratch=[" + Integer.toUnsignedString(status.expectedPositionScratchWord0())
                + "," + Integer.toUnsignedString(status.expectedPositionScratchWord1()) + "]"
                + " syntheticValidationFixtureUsed=false draw=false formalRendererReady=false";
    }

    void clear() {
        this.clearRuns++;
        this.dryRunReady = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.lastFailureReason = "none";
        this.resetDryRunFlags();
        this.lastAudit = ForgeFormalCmdgenRealSectionDryRunAuditResult.failure("cleared", 0.0D);
        this.scheduleCleanup("clear");
    }

    void markResourceReload() {
        this.markStale("resource-reload");
    }

    void markWorldUnload() {
        this.markStale("world-unload");
    }

    void markDimensionSwitch() {
        this.markStale("dimension-switch");
    }

    void markDebugPipelineClear() {
        this.markStale("debug-pipeline-clear");
    }

    void markPresetOff() {
        this.markStale("preset-off");
    }

    void markPresetClear() {
        this.markStale("preset-clear");
    }

    boolean isRealSectionDryRunReady() {
        return this.dryRunReady && !this.stale;
    }

    private ForgeFormalCmdgenRealSectionDryRunAuditResult auditInternal(long startNanos) {
        ForgeFormalTerrainRendererStats k1 = this.instance.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalMdicViewportStats k2 = this.instance.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalCommandGenerationStats k3 = this.instance.getFormalCommandGenerationOwner().createStatusSnapshot();
        ForgeFormalVisibilityStats k4 = this.instance.getFormalVisibilityOwner().createStatusSnapshot();
        ForgeFormalCmdgenGpuValidationStats k5 = this.instance.getFormalCmdgenGpuValidator().createStatusSnapshot();
        boolean originalCmdgenInspected = originalCmdgenChecked();
        boolean sectionContractInspected = originalSectionMetadataChecked();
        boolean buffersIsolated = this.drawCommandBufferId != 0
                && this.drawCountBufferId != 0
                && this.visibilityBufferId != 0
                && this.renderListBufferId != 0
                && this.positionScratchBufferId != 0
                && this.sectionMetadataBufferId != 0;
        boolean success = this.dryRunReady
                && !this.stale
                && k1.formalTerrainRendererOwnerReady()
                && k2.formalViewportOwnerReady()
                && k3.formalCommandGenerationOwnerReady()
                && k4.formalVisibilityOwnerReady()
                && k5.cmdgenValidationProgramReady()
                && originalCmdgenInspected
                && sectionContractInspected
                && this.realSectionMetadataUsed
                && this.realSectionCandidateSnapshotUsed
                && !this.syntheticValidationFixtureUsed
                && buffersIsolated
                && this.dispatchRun
                && this.readbackOk
                && this.acceptedSectionCount >= 1
                && this.generatedCommandCount >= 1
                && this.generatedDrawCount >= 1
                && this.drawCommandReadbackOk
                && this.drawCountReadbackOk
                && this.positionScratchReadbackOk
                && this.firstCommandMatchesAcceptedSection
                && this.firstCommandUsesRealSectionMetadata;
        return new ForgeFormalCmdgenRealSectionDryRunAuditResult(
                success,
                success ? "none" : "formal-cmdgen-real-section-dry-run-audit-failed",
                elapsedMs(startNanos),
                k1.formalTerrainRendererOwnerReady(),
                k2.formalViewportOwnerReady(),
                k3.formalCommandGenerationOwnerReady(),
                k4.formalVisibilityOwnerReady(),
                k5.cmdgenValidationProgramReady(),
                originalCmdgenInspected,
                sectionContractInspected,
                this.realSectionMetadataUsed,
                this.syntheticValidationFixtureUsed,
                buffersIsolated,
                false,
                this.dispatchRun,
                false,
                this.drawCommandReadbackOk,
                this.drawCountReadbackOk,
                this.positionScratchReadbackOk,
                this.firstCommandMatchesAcceptedSection,
                this.firstCommandUsesRealSectionMetadata,
                this.acceptedSectionCount,
                this.generatedCommandCount,
                this.generatedDrawCount,
                false,
                false,
                false,
                false,
                false,
                false
        );
    }

    private ForgeSectionGeometryManager.RealMetadataScan collectRealSectionMetadata() {
        ForgeSectionGeometryManager manager = this.instance.getSectionGeometryManager();
        ForgeSectionGeometryManager.RealMetadataScan scan = manager.createRealMetadataScan(MAX_ACCEPTED_SECTIONS);
        if (!scan.acceptedSections().isEmpty()) {
            return scan;
        }

        int seeded = this.seedSectionGeometryManagerFromCurrentWorld();
        ForgeSectionGeometryManager.RealMetadataScan seededScan = manager.createRealMetadataScan(MAX_ACCEPTED_SECTIONS);
        if (!seededScan.acceptedSections().isEmpty()) {
            return new ForgeSectionGeometryManager.RealMetadataScan(
                    seeded > 0 ? "realSectionCandidateSnapshot:current-world-built-section-seeded" : seededScan.source(),
                    seededScan.candidateSectionCount(),
                    seededScan.acceptedSectionCount(),
                    seededScan.rejectedSectionCount(),
                    seededScan.recordsRejectedNoMetadata(),
                    seededScan.recordsRejectedNoGeometryPointer(),
                    seededScan.recordsRejectedNoBucketOffsets(),
                    seededScan.recordsRejectedNoVisibleBuckets(),
                    seededScan.recordsRejectedUnsafeState(),
                    seededScan.maxAcceptedSectionId(),
                    seededScan.lastRejectedReason(),
                    seededScan.acceptedSections()
            );
        }
        return seededScan;
    }

    private int seedSectionGeometryManagerFromCurrentWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            this.lastFailureReason = "no-active-client-world";
            return 0;
        }
        if (!this.instance.ensureActiveWorldSkeletonForCurrentWorldIfAllowed()) {
            this.lastFailureReason = "world-engine-skeleton-not-ready";
            return 0;
        }
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isEmpty()) {
            this.lastFailureReason = "no-active-world-engine";
            return 0;
        }

        String dimension = level.dimension().location().toString();
        BlockPos playerPos = minecraft.player.blockPosition();
        ChunkPos center = minecraft.player.chunkPosition();
        this.instance.getVoxyGeometryCache().setActiveDimension(dimension);
        this.instance.getSectionGeometryManager().setActiveDimension(dimension);

        int consumed = 0;
        for (int radius = 0; radius <= SEARCH_RADIUS_CHUNKS && consumed == 0; radius++) {
            for (int dx = -radius; dx <= radius && consumed == 0; dx++) {
                for (int dz = -radius; dz <= radius && consumed == 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    consumed += this.buildAndConsumeLoadedChunk(
                            engine.get(),
                            level,
                            dimension,
                            center.x + dx,
                            center.z + dz
                    );
                }
            }
        }
        if (consumed == 0 && "none".equals(this.lastFailureReason)) {
            this.lastFailureReason = "no-real-section-candidates-near-player:" + playerPos.getX() + "," + playerPos.getY() + "," + playerPos.getZ();
        }
        return consumed;
    }

    private int buildAndConsumeLoadedChunk(WorldEngine engine, ClientLevel level, String dimension, int chunkX, int chunkZ) {
        LevelChunk chunk = getLoadedChunk(level, chunkX, chunkZ);
        if (chunk == null) {
            return 0;
        }

        ForgeCpuMeshBuildResult cpuResult = null;
        try {
            VoxelIngestService.ingestChunkWithStats(engine, chunk);
            cpuResult = ForgeCpuMeshBuilder.buildCurrentChunk(engine, chunk, level, dimension);
            if (cpuResult.stats().sectionsFound() == 0 || cpuResult.sections().isEmpty()) {
                return 0;
            }
            ForgeVoxyBuiltSectionBuildResult builtResult = ForgeVoxyBuiltSectionBuilder.fromCpuMesh(cpuResult);
            if (builtResult.sections().isEmpty()) {
                return 0;
            }
            this.instance.getVoxyGeometryCache().putAll(builtResult.sections());
            int consumed = 0;
            for (ForgeVoxyBuiltSection section : builtResult.sections()) {
                ForgeSectionGeometryManager.SectionConsumeResult result = this.instance.getSectionGeometryManager().consumeSection(section);
                if (result.consumed()) {
                    consumed++;
                }
            }
            return consumed;
        } catch (RuntimeException e) {
            this.lastFailureReason = "real-section-source-build-error:" + e.getClass().getSimpleName() + ":" + safeReason(e.getMessage());
            return 0;
        } finally {
            closeTemporaryCpuMesh(cpuResult);
        }
    }

    private void captureScan(ForgeSectionGeometryManager.RealMetadataScan scan) {
        this.candidateSectionSource = scan.source();
        this.candidateSectionCount = scan.candidateSectionCount();
        this.acceptedSectionCount = scan.acceptedSectionCount();
        this.rejectedSectionCount = scan.rejectedSectionCount();
        this.recordsRejectedNoMetadata = scan.recordsRejectedNoMetadata();
        this.recordsRejectedNoGeometryPointer = scan.recordsRejectedNoGeometryPointer();
        this.recordsRejectedNoBucketOffsets = scan.recordsRejectedNoBucketOffsets();
        this.recordsRejectedNoVisibleBuckets = scan.recordsRejectedNoVisibleBuckets();
        this.recordsRejectedUnsafeState = scan.recordsRejectedUnsafeState();
        this.inputSnapshotReady = !scan.acceptedSections().isEmpty();
        this.realSectionMetadataUsed = this.inputSnapshotReady;
        this.realSectionCandidateSnapshotUsed = this.inputSnapshotReady;
        this.validationInputSource = this.inputSnapshotReady ? REAL_SECTION_INPUT_SOURCE : "none";
        this.syntheticValidationFixtureUsed = false;
        this.sectionIdsKnown = this.inputSnapshotReady;
        this.sectionPositionsKnown = this.inputSnapshotReady;
        this.sectionMetadataAvailable = this.inputSnapshotReady;
        this.bucketOffsetsKnown = this.inputSnapshotReady;
        this.geometryPointerKnown = this.inputSnapshotReady;
        this.geometryRecordCountKnown = this.inputSnapshotReady;
    }

    private void prepareExpectedFirstCommand(ForgeSectionGeometryManager.RealMetadataSnapshot snapshot) {
        int[] deltas = snapshot.deltas();
        int offset = snapshot.geometryPtr();
        offset += deltas[0];
        this.firstAcceptedBucketIndex = -1;
        this.firstAcceptedBucketQuadCount = 0;
        for (int bucket = 1; bucket < deltas.length; bucket++) {
            int delta = deltas[bucket];
            if (delta > 0) {
                this.firstAcceptedBucketIndex = bucket;
                this.firstAcceptedBucketQuadCount = delta;
                break;
            }
            offset += delta;
        }
        if (this.firstAcceptedBucketIndex < 0) {
            throw new IllegalStateException("accepted section has no non-translucent bucket");
        }

        int[] words = snapshot.metadataWords();
        this.firstAcceptedSectionId = snapshot.sectionId();
        this.firstAcceptedSectionPosition = Long.toUnsignedString(snapshot.position());
        this.firstAcceptedGeometryPtr = snapshot.geometryPtr();
        this.expectedFirstCommandCount = this.firstAcceptedBucketQuadCount * 6;
        this.expectedFirstCommandBaseVertex = offset << 2;
        this.expectedFirstCommandBaseInstance = 0;
        this.expectedPositionScratchWord0 = words[0];
        this.expectedPositionScratchWord1 = words[1];
    }

    private int compileProgram() {
        int shader = 0;
        int program = 0;
        try {
            shader = GL20C.glCreateShader(GL43C.GL_COMPUTE_SHADER);
            GL20C.glShaderSource(shader, COMPUTE_SOURCE);
            GL20C.glCompileShader(shader);
            if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
                throw new IllegalStateException("compute-compile-failed:" + sanitize(GL20C.glGetShaderInfoLog(shader)));
            }

            program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, shader);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                throw new IllegalStateException("compute-link-failed:" + sanitize(GL20C.glGetProgramInfoLog(program)));
            }
            return program;
        } finally {
            if (program != 0 && shader != 0) {
                GL20C.glDetachShader(program, shader);
            }
            if (shader != 0) {
                GL20C.glDeleteShader(shader);
            }
        }
    }

    private void allocateValidationBuffers(ForgeSectionGeometryManager.RealMetadataScan scan) {
        int accepted = Math.max(1, scan.acceptedSectionCount());
        int maxSectionId = Math.max(0, scan.maxAcceptedSectionId());
        int sectionSlots = maxSectionId + 1;
        int[] sectionMetadata = new int[sectionSlots * SECTION_METADATA_WORDS];
        int[] visibility = new int[sectionSlots];
        int[] renderList = new int[accepted + 1];
        renderList[0] = accepted;

        List<ForgeSectionGeometryManager.RealMetadataSnapshot> sections = scan.acceptedSections();
        for (int i = 0; i < sections.size(); i++) {
            ForgeSectionGeometryManager.RealMetadataSnapshot section = sections.get(i);
            int[] words = section.metadataWords();
            System.arraycopy(words, 0, sectionMetadata, section.sectionId() * SECTION_METADATA_WORDS, SECTION_METADATA_WORDS);
            visibility[section.sectionId()] = 1;
            renderList[i + 1] = section.sectionId();
        }

        this.drawCommandBufferId = GL45C.glCreateBuffers();
        this.drawCountBufferId = GL45C.glCreateBuffers();
        this.visibilityBufferId = GL45C.glCreateBuffers();
        this.renderListBufferId = GL45C.glCreateBuffers();
        this.positionScratchBufferId = GL45C.glCreateBuffers();
        this.sectionMetadataBufferId = GL45C.glCreateBuffers();

        uploadInts(this.drawCommandBufferId, new int[accepted * DRAW_COMMAND_FIELD_COUNT], GL15C.GL_DYNAMIC_READ);
        uploadInts(this.drawCountBufferId, new int[DRAW_COUNT_WORDS], GL15C.GL_DYNAMIC_READ);
        uploadInts(this.visibilityBufferId, visibility, GL15C.GL_STATIC_DRAW);
        uploadInts(this.renderListBufferId, renderList, GL15C.GL_STATIC_DRAW);
        uploadInts(this.positionScratchBufferId, new int[accepted * 2], GL15C.GL_DYNAMIC_READ);
        uploadInts(this.sectionMetadataBufferId, sectionMetadata, GL15C.GL_STATIC_DRAW);
        this.visibilityInputWritten = true;
        this.indirectLookupInputWritten = true;
        this.positionScratchInputWritten = true;
    }

    private void dispatchValidationProgram(int acceptedSections) {
        int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int oldDrawCommand = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, DRAW_BUFFER_BINDING);
        int oldDrawCount = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, DRAW_COUNT_BUFFER_BINDING);
        int oldSectionMetadata = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, SECTION_METADATA_BUFFER_BINDING);
        int oldVisibility = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, VISIBILITY_BUFFER_BINDING);
        int oldRenderList = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, INDIRECT_LOOKUP_BUFFER_BINDING);
        int oldPositionScratch = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, POSITION_SCRATCH_BUFFER_BINDING);
        try {
            clearGlErrors();
            GL20C.glUseProgram(this.programId);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, DRAW_BUFFER_BINDING, this.drawCommandBufferId);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, DRAW_COUNT_BUFFER_BINDING, this.drawCountBufferId);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, SECTION_METADATA_BUFFER_BINDING, this.sectionMetadataBufferId);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, VISIBILITY_BUFFER_BINDING, this.visibilityBufferId);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, INDIRECT_LOOKUP_BUFFER_BINDING, this.renderListBufferId);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, POSITION_SCRATCH_BUFFER_BINDING, this.positionScratchBufferId);
            GL43C.glDispatchCompute(Math.max(1, acceptedSections), 1, 1);
            GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
            GL11C.glFinish();
            this.dispatchRun = true;
            int error = GL11C.glGetError();
            this.lastGlError = error == GL11C.GL_NO_ERROR ? "none" : glErrorName(error);
            if (error != GL11C.GL_NO_ERROR) {
                throw new IllegalStateException("gl-error-" + this.lastGlError);
            }
        } finally {
            GL20C.glUseProgram(oldProgram);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, DRAW_BUFFER_BINDING, oldDrawCommand);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, DRAW_COUNT_BUFFER_BINDING, oldDrawCount);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, SECTION_METADATA_BUFFER_BINDING, oldSectionMetadata);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, VISIBILITY_BUFFER_BINDING, oldVisibility);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, INDIRECT_LOOKUP_BUFFER_BINDING, oldRenderList);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, POSITION_SCRATCH_BUFFER_BINDING, oldPositionScratch);
        }
    }

    private void readbackValidationBuffers(ForgeSectionGeometryManager.RealMetadataSnapshot first) {
        int[] command = readInts(this.drawCommandBufferId, DRAW_COMMAND_FIELD_COUNT);
        int[] count = readInts(this.drawCountBufferId, DRAW_COUNT_WORDS);
        int[] position = readInts(this.positionScratchBufferId, 2);
        int[] firstMetadataWords = readIntsAt(this.sectionMetadataBufferId, first.sectionId() * SECTION_METADATA_WORDS, SECTION_METADATA_WORDS);
        this.firstCommandCount = command[0];
        this.firstCommandInstanceCount = command[1];
        this.firstCommandFirstIndex = command[2];
        this.firstCommandBaseVertex = command[3];
        this.firstCommandBaseInstance = command[4];
        this.generatedCommandCount = count[3];
        this.generatedDrawCount = count[3];
        this.positionScratchWord0 = position[0];
        this.positionScratchWord1 = position[1];
        this.sectionMetadataReadbackOk = Arrays.equals(firstMetadataWords, first.metadataWords());
        this.drawCommandReadbackOk = command[0] == this.expectedFirstCommandCount
                && command[1] == 1
                && command[2] == 0
                && command[3] == this.expectedFirstCommandBaseVertex
                && command[4] == this.expectedFirstCommandBaseInstance;
        this.drawCountReadbackOk = count[3] >= 1;
        this.positionScratchReadbackOk = position[0] == this.expectedPositionScratchWord0
                && position[1] == this.expectedPositionScratchWord1;
        this.firstCommandMatchesAcceptedSection = this.drawCommandReadbackOk
                && this.positionScratchReadbackOk
                && this.sectionMetadataReadbackOk;
        this.firstCommandUsesRealSectionMetadata = this.firstCommandMatchesAcceptedSection && this.realSectionMetadataUsed;
    }

    private void resetDryRunFlags() {
        this.dryRunReady = false;
        this.inputSnapshotReady = false;
        this.realSectionMetadataUsed = false;
        this.realSectionCandidateSnapshotUsed = false;
        this.validationInputSource = "not-run";
        this.syntheticValidationFixtureUsed = false;
        this.candidateSectionCount = 0;
        this.acceptedSectionCount = 0;
        this.rejectedSectionCount = 0;
        this.candidateSectionSource = "none";
        this.sectionIdsKnown = false;
        this.sectionPositionsKnown = false;
        this.sectionMetadataAvailable = false;
        this.sectionMetadataReadbackOk = false;
        this.bucketOffsetsKnown = false;
        this.geometryPointerKnown = false;
        this.geometryRecordCountKnown = false;
        this.visibilityInputWritten = false;
        this.indirectLookupInputWritten = false;
        this.positionScratchInputWritten = false;
        this.recordsRejectedNoMetadata = 0;
        this.recordsRejectedNoGeometryPointer = 0;
        this.recordsRejectedNoBucketOffsets = 0;
        this.recordsRejectedNoVisibleBuckets = 0;
        this.recordsRejectedUnsafeState = 0;
        this.compileAttempted = false;
        this.compileOk = false;
        this.linkOk = false;
        this.dispatchRun = false;
        this.readbackOk = false;
        this.auditOk = false;
        this.generatedCommandCount = 0;
        this.generatedDrawCount = 0;
        this.drawCommandReadbackOk = false;
        this.drawCountReadbackOk = false;
        this.positionScratchReadbackOk = false;
        this.firstCommandMatchesAcceptedSection = false;
        this.firstCommandUsesRealSectionMetadata = false;
        this.firstCommandCount = 0;
        this.firstCommandInstanceCount = 0;
        this.firstCommandFirstIndex = 0;
        this.firstCommandBaseVertex = 0;
        this.firstCommandBaseInstance = 0;
        this.expectedFirstCommandCount = 0;
        this.expectedFirstCommandBaseVertex = 0;
        this.expectedFirstCommandBaseInstance = 0;
        this.firstAcceptedSectionId = -1;
        this.firstAcceptedSectionPosition = "none";
        this.firstAcceptedGeometryPtr = 0;
        this.firstAcceptedBucketIndex = -1;
        this.firstAcceptedBucketQuadCount = 0;
        this.positionScratchWord0 = 0;
        this.positionScratchWord1 = 0;
        this.expectedPositionScratchWord0 = 0;
        this.expectedPositionScratchWord1 = 0;
        this.lastGlError = "none";
        this.firstAcceptedSnapshot = null;
    }

    private void fail(String reason) {
        this.dryRunReady = false;
        this.lifecycleState = "FAILED";
        this.lastFailureReason = safeReason(reason);
    }

    private void markStale(String reason) {
        this.resetDryRunFlags();
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = this.lastLifecycleEvent;
        this.lastAudit = ForgeFormalCmdgenRealSectionDryRunAuditResult.failure(this.lastFailureReason, 0.0D);
        this.scheduleCleanup(this.lastLifecycleEvent);
    }

    private void scheduleCleanup(String reason) {
        if (RenderSystem.isOnRenderThread()) {
            this.closeOwnedResourcesOnRenderThread(true);
        } else {
            RenderSystem.recordRenderCall(() -> this.closeOwnedResourcesOnRenderThread(true));
        }
        this.lastLifecycleEvent = safeReason(reason);
    }

    private void closeOwnedResourcesOnRenderThread(boolean resetProgramState) {
        if (this.programId != 0) {
            GL20C.glDeleteProgram(this.programId);
        }
        if (this.drawCommandBufferId != 0) {
            GL15C.glDeleteBuffers(this.drawCommandBufferId);
        }
        if (this.drawCountBufferId != 0) {
            GL15C.glDeleteBuffers(this.drawCountBufferId);
        }
        if (this.visibilityBufferId != 0) {
            GL15C.glDeleteBuffers(this.visibilityBufferId);
        }
        if (this.renderListBufferId != 0) {
            GL15C.glDeleteBuffers(this.renderListBufferId);
        }
        if (this.positionScratchBufferId != 0) {
            GL15C.glDeleteBuffers(this.positionScratchBufferId);
        }
        if (this.sectionMetadataBufferId != 0) {
            GL15C.glDeleteBuffers(this.sectionMetadataBufferId);
        }
        this.programId = 0;
        this.drawCommandBufferId = 0;
        this.drawCountBufferId = 0;
        this.visibilityBufferId = 0;
        this.renderListBufferId = 0;
        this.positionScratchBufferId = 0;
        this.sectionMetadataBufferId = 0;
        if (resetProgramState) {
            this.compileOk = false;
            this.linkOk = false;
        }
    }

    private static LevelChunk getLoadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        if (!level.hasChunk(chunkX, chunkZ)) {
            return null;
        }
        return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    private static void closeTemporaryCpuMesh(ForgeCpuMeshBuildResult cpuResult) {
        if (cpuResult == null) {
            return;
        }
        for (ForgeCpuBuiltSection section : cpuResult.sections()) {
            if (section != null) {
                section.close();
            }
        }
    }

    private static void uploadInts(int bufferId, int[] values, int usage) {
        long ptr = MemoryUtil.nmemAlloc((long) values.length * Integer.BYTES);
        try {
            for (int i = 0; i < values.length; i++) {
                MemoryUtil.memPutInt(ptr + ((long) i * Integer.BYTES), values[i]);
            }
            GL45C.nglNamedBufferData(bufferId, (long) values.length * Integer.BYTES, ptr, usage);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    private static int[] readInts(int bufferId, int words) {
        return readIntsAt(bufferId, 0, words);
    }

    private static int[] readIntsAt(int bufferId, int offsetWords, int words) {
        int[] values = new int[words];
        long ptr = MemoryUtil.nmemAlloc((long) words * Integer.BYTES);
        try {
            GL45C.nglGetNamedBufferSubData(bufferId, (long) offsetWords * Integer.BYTES, (long) words * Integer.BYTES, ptr);
            for (int i = 0; i < words; i++) {
                values[i] = MemoryUtil.memGetInt(ptr + ((long) i * Integer.BYTES));
            }
            return values;
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    private static boolean originalCmdgenChecked() {
        return fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "uint sectionId = indirectLookup")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "SectionMeta meta = sectionData[sectionId]")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "writeCmd")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "positionBuffer[drawId]");
    }

    private static boolean originalSectionMetadataChecked() {
        return fileContains("src/main/resources/assets/voxy/shaders/lod/section.glsl", "struct SectionMeta")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/section.glsl", "uvec4 a")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/section.glsl", "uvec4 b")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicAsyncGeometryManager.java", "writeMetadataSplitParts")
                && fileContains("src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicSectionGeometryData.java", "SECTION_METADATA_SIZE");
    }

    private static boolean fileContains(String relativePath, String marker) {
        for (Path path : List.of(Path.of(relativePath), Path.of("..").resolve(relativePath))) {
            if (!Files.exists(path)) {
                continue;
            }
            try {
                return Files.readString(path, StandardCharsets.UTF_8).contains(marker);
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private static int countBlockers(String severity) {
        int count = 0;
        for (ForgeFormalRendererBlocker blocker : BLOCKERS) {
            if (blocker.severity().equals(severity)) {
                count++;
            }
        }
        return count;
    }

    private static String compactBlockers() {
        return BLOCKERS.stream()
                .map(ForgeFormalRendererBlocker::compact)
                .collect(Collectors.joining("|"));
    }

    private static void clearGlErrors() {
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
            // Drain stale GL errors before the audit-only dry-run dispatch.
        }
    }

    private static String glErrorName(int error) {
        return switch (error) {
            case GL11C.GL_NO_ERROR -> "none";
            case GL11C.GL_INVALID_ENUM -> "GL_INVALID_ENUM";
            case GL11C.GL_INVALID_VALUE -> "GL_INVALID_VALUE";
            case GL11C.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION";
            case GL11C.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY";
            default -> "0x" + Integer.toHexString(error);
        };
    }

    private static String sanitize(String log) {
        if (log == null || log.isBlank()) {
            return "none";
        }
        return log.replace('\n', ' ').replace('\r', ' ').replace(' ', '_');
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
    }

    private static double elapsedMs(long startNanos) {
        return startNanos == 0L ? 0.0D : (System.nanoTime() - startNanos) / 1_000_000.0D;
    }
}
