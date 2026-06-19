package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Deprecated validation-only route: kept only as historical cmdgen buffer
 * evidence. New formal work must port original cmdgen.comp ownership and input
 * contracts instead of extending this synthetic validator.
 */
@Deprecated(forRemoval = false)
final class ForgeFormalCmdgenGpuValidator {
    static final String STAGE = "K5_FORMAL_CMDGEN_GPU_VALIDATION_NO_DRAW";
    private static final int DRAW_BUFFER_BINDING = 1;
    private static final int DRAW_COUNT_BUFFER_BINDING = 2;
    private static final int SECTION_METADATA_BUFFER_BINDING = 3;
    private static final int VISIBILITY_BUFFER_BINDING = 4;
    private static final int INDIRECT_LOOKUP_BUFFER_BINDING = 5;
    private static final int POSITION_SCRATCH_BUFFER_BINDING = 6;
    private static final int DRAW_COMMAND_FIELD_COUNT = 5;
    private static final int DRAW_COMMAND_STRIDE_BYTES = DRAW_COMMAND_FIELD_COUNT * Integer.BYTES;
    private static final int DRAW_COUNT_WORDS = 11;
    private static final int SECTION_METADATA_WORDS = 8;
    private static final int EXPECTED_SECTION_ID = 0;
    private static final int EXPECTED_QUAD_COUNT = 6;
    private static final int EXPECTED_COMMAND_COUNT = EXPECTED_QUAD_COUNT * 6;
    private static final int EXPECTED_GEOMETRY_OFFSET = 7;
    private static final int EXPECTED_BASE_VERTEX = EXPECTED_GEOMETRY_OFFSET << 2;
    private static final int EXPECTED_POSITION_WORD_0 = 0x12345678;
    private static final int EXPECTED_POSITION_WORD_1 = 0x0ABCDEF0;
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
                uint sectionMeta[];
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

            const uint WORDS_PER_SECTION = 8u;

            void main() {
                uint index = gl_GlobalInvocationID.x;
                if (index >= sectionCount) {
                    return;
                }
                uint sectionId = indirectLookup[index];
                if ((visibilityData[sectionId] & 1u) == 0u) {
                    return;
                }

                uint metaBase = sectionId * WORDS_PER_SECTION;
                uint quadCount = sectionMeta[metaBase + 1u];
                uint geometryOffset = sectionMeta[metaBase + 2u];
                uint rawPos0 = sectionMeta[metaBase + 3u];
                uint rawPos1 = sectionMeta[metaBase + 4u];
                uint commandIndex = atomicAdd(opaqueDrawCount, 1u);

                DrawCommand cmd;
                cmd.count = quadCount * 6u;
                cmd.instanceCount = 1u;
                cmd.firstIndex = 0u;
                cmd.baseVertex = int(geometryOffset) << 2;
                cmd.baseInstance = index;
                cmdBuffer[commandIndex] = cmd;
                positionBuffer[index] = uvec2(rawPos0, rawPos1);
            }
            """;
    private static final List<ForgeFormalRendererBlocker> BLOCKERS = List.of(
            new ForgeFormalRendererBlocker("P0", "P0_PRODUCTION_CMDGEN_NOT_OPERATIONAL", "Production cmdgen not operational", "K5 validates a tiny audit-only command-generation program, not the production cmdgen.comp renderer path.", "Promote command generation only after formal traversal, geometry id ownership, and shader integration are ready.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_VISIBILITY_TRAVERSAL_IMPLEMENTATION_MISSING", "Formal visibility traversal implementation missing", "K5 uses a synthetic validation fixture when traversal output is not available.", "Implement formal visibility traversal before live command generation.", true),
            new ForgeFormalRendererBlocker("P0", "P0_GLOBAL_FORMAL_MODEL_ID_GEOMETRY_MISSING", "Global formal model-id geometry missing", "Live geometry is not globally encoded with formal model ids.", "Move formal model ids into the formal BuiltSection/RenderDataFactory path.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_TERRAIN_SHADER_INTEGRATION_MISSING", "Formal terrain shader integration missing", "Generated validation commands are not consumed by a formal terrain shader.", "Integrate formal terrain shader semantics before draw.", true),
            new ForgeFormalRendererBlocker("P0", "P0_FORMAL_MDIC_DRAW_DISABLED", "Formal MDIC draw disabled", "K5 never submits generated commands to glMultiDrawElementsIndirectCountARB.", "Keep draw disabled until formal command and shader paths are operational.", true),
            new ForgeFormalRendererBlocker("P1", "P1_HIERARCHICAL_OCCLUSION_MISSING", "Hierarchical occlusion missing", "K5 does not port the original traversal queue.", "Port formal hierarchical occlusion traversal later.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RENDER_DISTANCE_TRACKER_INCOMPLETE", "Render distance tracker incomplete", "K5 does not own the formal render-distance tracker.", "Add formal render distance tracking before live traversal.", false),
            new ForgeFormalRendererBlocker("P1", "P1_LIGHTMAP_MISSING", "Lightmap missing", "Formal lightmap semantics remain incomplete.", "Add formal lightmap binding and semantics.", false),
            new ForgeFormalRendererBlocker("P1", "P1_BIOME_TINT_MISSING", "Biome tint missing", "Formal biome tint/modelColour semantics remain incomplete.", "Complete formal biome tint handling.", false),
            new ForgeFormalRendererBlocker("P1", "P1_MATERIAL_ALPHA_SEMANTICS_MISSING", "Material alpha semantics missing", "Formal material, alpha, and cutout semantics remain incomplete.", "Implement formal material/alpha/cutout behavior.", false),
            new ForgeFormalRendererBlocker("P1", "P1_RESOURCE_REBUILD_AUTOMATION_INCOMPLETE", "Resource rebuild automation incomplete", "K5 stales validation resources but does not automatically rebuild production resources.", "Add rebuild orchestration after production resources exist.", false)
    );

    private final ForgeVoxyInstance instance;
    private long buildRuns;
    private long auditRuns;
    private long clearRuns;
    private long auditFailures;
    private boolean programReady;
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
    private String inputSource = "not-run";
    private boolean syntheticFixtureUsed;
    private boolean cpuCandidateSnapshotUsed;
    private int generatedCommandCount;
    private int generatedDrawCount;
    private boolean drawCommandReadbackOk;
    private boolean drawCountReadbackOk;
    private boolean positionScratchReadbackOk;
    private int firstCommandCount;
    private int firstCommandInstanceCount;
    private int firstCommandFirstIndex;
    private int firstCommandBaseVertex;
    private int firstCommandBaseInstance;
    private int positionScratchWord0;
    private int positionScratchWord1;
    private boolean stale;
    private boolean requiresRebuild;
    private String lifecycleState = "UNINITIALIZED";
    private String lastLifecycleEvent = "initialized";
    private String lastGlError = "none";
    private String lastFailureReason = "none";
    private ForgeFormalCmdgenGpuValidationAuditResult lastAudit =
            ForgeFormalCmdgenGpuValidationAuditResult.failure("not-audited", 0.0D);

    ForgeFormalCmdgenGpuValidator(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeFormalCmdgenGpuValidationStats build() {
        this.buildRuns++;
        this.lifecycleState = "BUILDING";
        this.lastLifecycleEvent = "build";
        this.lastFailureReason = "none";
        this.resetValidationFlags();

        ForgeFormalTerrainRendererStats k1 = this.instance.getFormalTerrainRendererOwner().enable("k5-cmdgen-gpu-validation");
        ForgeFormalMdicViewportStats k2 = this.instance.getFormalMdicViewportOwner().enable("k5-cmdgen-gpu-validation");
        ForgeFormalCommandGenerationStats k3 = this.instance.getFormalCommandGenerationOwner().enable("k5-cmdgen-gpu-validation");
        ForgeFormalVisibilityStats k4 = this.instance.getFormalVisibilityOwner().enable("k5-cmdgen-gpu-validation");
        this.cpuCandidateSnapshotUsed = false;
        this.syntheticFixtureUsed = true;
        this.inputSource = "syntheticValidationFixture";

        if (!k1.formalTerrainRendererOwnerReady()
                || !k2.formalViewportOwnerReady()
                || !k3.formalCommandGenerationOwnerReady()
                || !k4.formalVisibilityOwnerReady()
                || !k4.formalVisibilityContractReady()
                || !k4.formalRenderListContractReady()) {
            this.fail("formal-owner-prerequisites-not-ready");
            this.audit();
            this.instance.getFormalRendererManager().checkReadiness("k5-cmdgen-gpu-validation-prerequisite-failed");
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

        try {
            this.closeOwnedResourcesOnRenderThread(false);
            this.compileAttempted = true;
            this.programId = this.compileProgram();
            this.compileOk = true;
            this.linkOk = true;
            this.programReady = true;
            this.allocateValidationBuffers();
            this.dispatchValidationProgram();
            this.readbackValidationBuffers();
            this.readbackOk = this.drawCommandReadbackOk
                    && this.drawCountReadbackOk
                    && this.positionScratchReadbackOk;
            if (this.readbackOk) {
                this.stale = false;
                this.requiresRebuild = false;
                this.lifecycleState = "BUILT";
                this.lastFailureReason = "none";
            } else {
                this.fail("validation-readback-mismatch");
            }
        } catch (RuntimeException e) {
            this.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        this.audit();
        this.instance.getFormalRendererManager().checkReadiness("k5-cmdgen-gpu-validation-build");
        return this.createStatusSnapshot();
    }

    ForgeFormalCmdgenGpuValidationAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        try {
            this.lastAudit = this.auditInternal(start);
        } catch (RuntimeException e) {
            this.lastAudit = ForgeFormalCmdgenGpuValidationAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
        }
        this.auditOk = this.lastAudit.success();
        if (!this.auditOk) {
            this.auditFailures++;
            this.lastFailureReason = this.lastAudit.error();
        }
        return this.lastAudit;
    }

    ForgeFormalCmdgenGpuValidationAuditResult createAuditStatusSnapshot() {
        return this.lastAudit;
    }

    ForgeFormalCmdgenGpuValidationStats createStatusSnapshot() {
        ForgeFormalTerrainRendererStats k1 = this.instance.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalMdicViewportStats k2 = this.instance.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalCommandGenerationStats k3 = this.instance.getFormalCommandGenerationOwner().createStatusSnapshot();
        ForgeFormalVisibilityStats k4 = this.instance.getFormalVisibilityOwner().createStatusSnapshot();
        return new ForgeFormalCmdgenGpuValidationStats(
                STAGE,
                this.buildRuns,
                this.auditRuns,
                this.clearRuns,
                this.auditFailures,
                k1.formalTerrainRendererOwnerReady(),
                k2.formalViewportOwnerReady(),
                k3.formalCommandGenerationOwnerReady(),
                k4.formalVisibilityOwnerReady(),
                this.programReady && !this.stale,
                this.compileAttempted,
                this.compileOk,
                this.linkOk,
                this.programId,
                this.dispatchRun,
                this.readbackOk,
                this.auditOk,
                false,
                false,
                this.inputSource,
                this.syntheticFixtureUsed,
                this.cpuCandidateSnapshotUsed,
                this.drawCommandBufferId != 0,
                this.drawCountBufferId != 0,
                this.visibilityBufferId != 0,
                this.renderListBufferId != 0,
                this.positionScratchBufferId != 0,
                this.sectionMetadataBufferId != 0,
                true,
                false,
                false,
                true,
                DRAW_COMMAND_STRIDE_BYTES,
                DRAW_COMMAND_FIELD_COUNT,
                true,
                this.generatedCommandCount,
                this.generatedDrawCount,
                this.drawCommandReadbackOk,
                this.drawCountReadbackOk,
                this.positionScratchReadbackOk,
                this.firstCommandCount,
                this.firstCommandInstanceCount,
                this.firstCommandFirstIndex,
                this.firstCommandBaseVertex,
                this.firstCommandBaseInstance,
                this.positionScratchWord0,
                this.positionScratchWord1,
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
        ForgeFormalCmdgenGpuValidationStats status = this.createStatusSnapshot();
        return "K5 formal cmdgen GPU validator: stage=" + status.stage()
                + " inputSource=" + status.validationInputSource()
                + " programId=" + status.cmdgenValidationProgramId()
                + " generatedCommandCount=" + status.generatedCommandCount()
                + " generatedDrawCount=" + status.generatedDrawCount()
                + " firstCommand=[" + status.firstCommandCount()
                + "," + status.firstCommandInstanceCount()
                + "," + status.firstCommandFirstIndex()
                + "," + status.firstCommandBaseVertex()
                + "," + status.firstCommandBaseInstance() + "]"
                + " positionScratch=[" + Integer.toUnsignedString(status.positionScratchWord0())
                + "," + Integer.toUnsignedString(status.positionScratchWord1()) + "]"
                + " validationOnly=true liveRendererBuffer=false debugBuffer=false draw=false formalRendererReady=false";
    }

    void clear() {
        this.clearRuns++;
        this.programReady = false;
        this.stale = false;
        this.requiresRebuild = false;
        this.lifecycleState = "CLEARED";
        this.lastLifecycleEvent = "clear";
        this.lastFailureReason = "none";
        this.resetValidationFlags();
        this.lastAudit = ForgeFormalCmdgenGpuValidationAuditResult.failure("cleared", 0.0D);
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

    boolean isValidationProgramReady() {
        return this.programReady && !this.stale;
    }

    boolean isValidationReadbackOk() {
        return this.readbackOk && !this.stale;
    }

    boolean isValidationAuditOk() {
        return this.auditOk && !this.stale;
    }

    private ForgeFormalCmdgenGpuValidationAuditResult auditInternal(long startNanos) {
        ForgeFormalTerrainRendererStats k1 = this.instance.getFormalTerrainRendererOwner().createStatusSnapshot();
        ForgeFormalMdicViewportStats k2 = this.instance.getFormalMdicViewportOwner().createStatusSnapshot();
        ForgeFormalCommandGenerationStats k3 = this.instance.getFormalCommandGenerationOwner().createStatusSnapshot();
        ForgeFormalVisibilityStats k4 = this.instance.getFormalVisibilityOwner().createStatusSnapshot();
        boolean cmdgenInspected = originalCmdgenChecked();
        boolean bindingsInspected = originalBindingsChecked();
        boolean buffersIsolated = this.drawCommandBufferId != 0
                && this.drawCountBufferId != 0
                && this.visibilityBufferId != 0
                && this.renderListBufferId != 0
                && this.positionScratchBufferId != 0
                && this.sectionMetadataBufferId != 0;
        boolean success = k1.formalTerrainRendererOwnerReady()
                && k2.formalViewportOwnerReady()
                && k3.formalCommandGenerationOwnerReady()
                && k4.formalVisibilityOwnerReady()
                && cmdgenInspected
                && bindingsInspected
                && buffersIsolated
                && this.programReady
                && this.compileOk
                && this.linkOk
                && this.dispatchRun
                && this.readbackOk
                && this.generatedCommandCount == 1
                && this.generatedDrawCount == 1
                && this.drawCommandReadbackOk
                && this.drawCountReadbackOk
                && this.positionScratchReadbackOk
                && !this.stale;
        return new ForgeFormalCmdgenGpuValidationAuditResult(
                success,
                success ? "none" : "formal-cmdgen-gpu-validation-audit-failed",
                elapsedMs(startNanos),
                k1.formalTerrainRendererOwnerReady(),
                k2.formalViewportOwnerReady(),
                k3.formalCommandGenerationOwnerReady(),
                k4.formalVisibilityOwnerReady(),
                cmdgenInspected,
                bindingsInspected,
                buffersIsolated,
                false,
                this.dispatchRun,
                false,
                this.drawCommandReadbackOk,
                this.drawCountReadbackOk,
                this.positionScratchReadbackOk,
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

    private void allocateValidationBuffers() {
        this.drawCommandBufferId = GL45C.glCreateBuffers();
        this.drawCountBufferId = GL45C.glCreateBuffers();
        this.visibilityBufferId = GL45C.glCreateBuffers();
        this.renderListBufferId = GL45C.glCreateBuffers();
        this.positionScratchBufferId = GL45C.glCreateBuffers();
        this.sectionMetadataBufferId = GL45C.glCreateBuffers();

        uploadInts(this.drawCommandBufferId, new int[DRAW_COMMAND_FIELD_COUNT], GL15C.GL_DYNAMIC_READ);
        uploadInts(this.drawCountBufferId, new int[DRAW_COUNT_WORDS], GL15C.GL_DYNAMIC_READ);
        uploadInts(this.visibilityBufferId, new int[]{1}, GL15C.GL_STATIC_DRAW);
        uploadInts(this.renderListBufferId, new int[]{1, EXPECTED_SECTION_ID}, GL15C.GL_STATIC_DRAW);
        uploadInts(this.positionScratchBufferId, new int[]{0, 0}, GL15C.GL_DYNAMIC_READ);
        uploadInts(this.sectionMetadataBufferId, new int[]{
                0,
                EXPECTED_QUAD_COUNT,
                EXPECTED_GEOMETRY_OFFSET,
                EXPECTED_POSITION_WORD_0,
                EXPECTED_POSITION_WORD_1,
                0,
                0,
                0
        }, GL15C.GL_STATIC_DRAW);
    }

    private void dispatchValidationProgram() {
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
            GL43C.glDispatchCompute(1, 1, 1);
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

    private void readbackValidationBuffers() {
        int[] command = readInts(this.drawCommandBufferId, DRAW_COMMAND_FIELD_COUNT);
        int[] count = readInts(this.drawCountBufferId, DRAW_COUNT_WORDS);
        int[] position = readInts(this.positionScratchBufferId, 2);
        this.firstCommandCount = command[0];
        this.firstCommandInstanceCount = command[1];
        this.firstCommandFirstIndex = command[2];
        this.firstCommandBaseVertex = command[3];
        this.firstCommandBaseInstance = command[4];
        this.generatedCommandCount = count[3];
        this.generatedDrawCount = count[3];
        this.positionScratchWord0 = position[0];
        this.positionScratchWord1 = position[1];
        this.drawCommandReadbackOk = command[0] == EXPECTED_COMMAND_COUNT
                && command[1] == 1
                && command[2] == 0
                && command[3] == EXPECTED_BASE_VERTEX
                && command[4] == 0;
        this.drawCountReadbackOk = count[3] == 1;
        this.positionScratchReadbackOk = position[0] == EXPECTED_POSITION_WORD_0
                && position[1] == EXPECTED_POSITION_WORD_1;
    }

    private void resetValidationFlags() {
        this.programReady = false;
        this.compileAttempted = false;
        this.compileOk = false;
        this.linkOk = false;
        this.dispatchRun = false;
        this.readbackOk = false;
        this.auditOk = false;
        this.inputSource = "not-run";
        this.syntheticFixtureUsed = false;
        this.cpuCandidateSnapshotUsed = false;
        this.generatedCommandCount = 0;
        this.generatedDrawCount = 0;
        this.drawCommandReadbackOk = false;
        this.drawCountReadbackOk = false;
        this.positionScratchReadbackOk = false;
        this.firstCommandCount = 0;
        this.firstCommandInstanceCount = 0;
        this.firstCommandFirstIndex = 0;
        this.firstCommandBaseVertex = 0;
        this.firstCommandBaseInstance = 0;
        this.positionScratchWord0 = 0;
        this.positionScratchWord1 = 0;
        this.lastGlError = "none";
    }

    private void fail(String reason) {
        this.programReady = false;
        this.lifecycleState = "FAILED";
        this.lastFailureReason = safeReason(reason);
    }

    private void markStale(String reason) {
        this.resetValidationFlags();
        this.stale = true;
        this.requiresRebuild = true;
        this.lifecycleState = "STALE";
        this.lastLifecycleEvent = safeReason(reason);
        this.lastFailureReason = this.lastLifecycleEvent;
        this.lastAudit = ForgeFormalCmdgenGpuValidationAuditResult.failure(this.lastFailureReason, 0.0D);
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
        int[] values = new int[words];
        long ptr = MemoryUtil.nmemAlloc((long) words * Integer.BYTES);
        try {
            GL45C.nglGetNamedBufferSubData(bufferId, 0L, (long) words * Integer.BYTES, ptr);
            for (int i = 0; i < words; i++) {
                values[i] = MemoryUtil.memGetInt(ptr + ((long) i * Integer.BYTES));
            }
            return values;
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
    }

    private static boolean originalCmdgenChecked() {
        return fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "#define DRAW_BUFFER_BINDING 1")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "writeCmd")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "visibilityData[sectionId]")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp", "positionBuffer[drawId]");
    }

    private static boolean originalBindingsChecked() {
        return fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "struct DrawCommand")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "uint opaqueDrawCount")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "uint sectionCount")
                && fileContains("src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl", "uint visibilityData[]");
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
            // Drain stale GL errors before the audit-only validation dispatch.
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
