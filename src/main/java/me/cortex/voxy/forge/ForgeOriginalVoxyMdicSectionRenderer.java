package me.cortex.voxy.forge;

import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_COLOR_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL11C.GL_FRONT_AND_BACK;
import static org.lwjgl.opengl.GL11C.GL_LINE;
import static org.lwjgl.opengl.GL11C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetBoolean;
import static org.lwjgl.opengl.GL11C.glGetBooleanv;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL11C.glPolygonMode;
import static org.lwjgl.opengl.GL11C.GL_FILL;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL14C.GL_ONE;
import static org.lwjgl.opengl.GL14C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL14C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL32C.GL_FIRST_VERTEX_CONVENTION;
import static org.lwjgl.opengl.GL32C.glProvokingVertex;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL40C.glDrawElementsIndirect;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_COMMAND_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.GL_DISPATCH_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL43C.glDispatchComputeIndirect;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.nglGetNamedBufferSubData;
import static org.lwjgl.opengl.NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV;

final class ForgeOriginalVoxyMdicSectionRenderer {
    static final String STAGE = "VI_ORIGINAL_MDIC_SECTION_RENDERER_CHAIN";
    private static final int OPAQUE_DRAW_COUNT = ForgeOriginalVoxyMdicViewport.OPAQUE_DRAW_COUNT;
    private static final int TRANSLUCENT_DRAW_COUNT = ForgeOriginalVoxyMdicViewport.TRANSLUCENT_DRAW_COUNT;
    private static final int TRANSLUCENT_OFFSET = OPAQUE_DRAW_COUNT;
    private static final int TEMPORAL_OFFSET = TRANSLUCENT_OFFSET + TRANSLUCENT_DRAW_COUNT;
    private static final int TRANSLUCENT_WRITE_BASE = 1024;
    private static final int DRAW_COMMAND_WORDS = 5;
    private static final int DRAW_COMMAND_BYTES = DRAW_COMMAND_WORDS * Integer.BYTES;
    private static final int DRAW_COUNT_WORDS = 11;
    private static final int CULL_COMMAND_OFFSET_BYTES = 6 * Integer.BYTES;
    private static final int CULL_COMMAND_COUNT = 6 * 2 * 3;
    private static final int DRAW_BUFFER_BINDING = 1;
    private static final int DRAW_COUNT_BUFFER_BINDING = 2;
    private static final int SECTION_METADATA_BUFFER_BINDING = 3;
    private static final int VISIBILITY_BUFFER_BINDING = 4;
    private static final int INDIRECT_SECTION_LOOKUP_BINDING = 5;
    private static final int POSITION_SCRATCH_BINDING = 6;
    private static final int TRANSLUCENT_DISTANCE_BUFFER_BINDING = 7;
    private static final int STATISTICS_BUFFER_BINDING = 8;
    private static final int TRANSLUCENT_INDIRECT_SECTION_LOOKUP_BINDING = 4;
    private static final int TRANSLUCENT_BUILD_DISTANCE_BUFFER_BINDING = 5;

    private final ForgeOriginalVoxyGlBuffer uniformBuffer = new ForgeOriginalVoxyGlBuffer(1024).zero();
    private final ForgeOriginalVoxyGlBuffer distanceCountBuffer =
            new ForgeOriginalVoxyGlBuffer(TRANSLUCENT_WRITE_BASE * 4L + TRANSLUCENT_DRAW_COUNT * 4L).zero();
    private final ForgeOriginalVoxyGlBuffer statisticsBuffer = new ForgeOriginalVoxyGlBuffer(1024).zero();
    private final ForgeOriginalVoxySharedIndexBuffer sharedIndexBuffer = new ForgeOriginalVoxySharedIndexBuffer();
    private final int vertexArrayId = glGenVertexArrays();
    private int terrainProgramId;
    private int translucentTerrainProgramId;
    private int prepProgramId;
    private int cullProgramId;
    private int cmdgenProgramId;
    private int prefixSumProgramId;
    private int translucentGenProgramId;
    private boolean readbackAuditRequested;
    private long opaqueRenderCallCount;
    private long translucentRenderCallCount;
    private long temporalRenderCallCount;
    private long prepDispatchCount;
    private long cullRasterCount;
    private long cmdgenDispatchCount;
    private long prefixSumDispatchCount;
    private long translucentGenDispatchCount;
    private long readbackAuditRuns;
    private long readbackAuditFailures;
    private int renderListSectionCount;
    private int cmdGenDispatchX;
    private int cmdGenDispatchY;
    private int cmdGenDispatchZ;
    private int opaqueDrawCount;
    private int translucentDrawCount;
    private int temporalOpaqueDrawCount;
    private int cullCommandCount;
    private int cullCommandInstanceCount;
    private int cullCommandFirstIndex;
    private int firstCommandCount;
    private int firstCommandInstanceCount;
    private int firstCommandFirstIndex;
    private int firstCommandBaseVertex;
    private int firstCommandBaseInstance;
    private int firstPositionScratchWord0;
    private int firstPositionScratchWord1;
    private boolean inputParityReady;
    private boolean outputParityReady;
    private boolean readbackAuditReady;
    private boolean barrierAuditReady;
    private boolean positionScratchReadbackOk;
    private int lastGlError = GL_NO_ERROR;
    private ForgeOriginalVoxyRenderProperties properties;
    private ForgeOriginalVoxyRenderPipeline pipeline;
    private String lifecycleState = "CREATED";
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    String buildOnRenderThread(ForgeOriginalVoxyRenderPipeline pipeline) {
        try {
            this.freePrograms();
            this.pipeline = pipeline;
            this.properties = pipeline.properties();
            String vertexSource = this.properties.injectDefines(ForgeOriginalVoxyShaderSource.parse("voxy:lod/gl46/quads3.vert"));
            String taa = pipeline.taaFunction("taaShift");
            if (taa != null) {
                vertexSource += "\n" + taa;
                vertexSource = withDefines(vertexSource, "TAA_PATCH", 1);
            }
            vertexSource = injectDirectionalFaceTint(vertexSource);
            String fragmentSource = ForgeOriginalVoxyShaderSource.parse("voxy:lod/gl46/quads.frag");
            String opaqueFragmentSource = pipeline.patchOpaqueShader(this, fragmentSource);
            opaqueFragmentSource = opaqueFragmentSource == null ? fragmentSource : opaqueFragmentSource;
            this.terrainProgramId = compilePatchedOrNormal(vertexSource, opaqueFragmentSource, fragmentSource, "quads");

            String translucentVertexSource = withDefines(vertexSource, "TRANSLUCENT", 1);
            String normalTranslucentFragmentSource = withDefines(fragmentSource, "TRANSLUCENT", 1);
            String translucentFragmentSource = pipeline.patchTranslucentShader(this, fragmentSource);
            translucentFragmentSource = translucentFragmentSource == null
                    ? normalTranslucentFragmentSource
                    : withDefines(translucentFragmentSource, "TRANSLUCENT", 1);
            this.translucentTerrainProgramId = compilePatchedOrNormal(
                    translucentVertexSource,
                    translucentFragmentSource,
                    normalTranslucentFragmentSource,
                    "quads translucent");
            this.prepProgramId = compileComputeProgram(ForgeOriginalVoxyShaderSource.parse("voxy:lod/gl46/prep.comp"), "prep.comp");
            String cullVertexSource = this.properties.injectDefines(ForgeOriginalVoxyShaderSource.parse("voxy:lod/gl46/cull/raster.vert"));
            String cullFragmentSource = ForgeOriginalVoxyShaderSource.parse("voxy:lod/gl46/cull/raster.frag");
            this.cullProgramId = compileProgram(cullVertexSource, cullFragmentSource, "cull/raster");
            String cmdgenSource = withDefines(
                    ForgeOriginalVoxyShaderSource.parse("voxy:lod/gl46/cmdgen.comp"),
                    "TRANSLUCENT_WRITE_BASE", TRANSLUCENT_WRITE_BASE,
                    "TEMPORAL_OFFSET", TEMPORAL_OFFSET,
                    "TRANSLUCENT_DISTANCE_BUFFER_BINDING", TRANSLUCENT_DISTANCE_BUFFER_BINDING);
            if (ForgeOriginalVoxyRenderStatistics.enabled) {
                cmdgenSource = withDefines(
                        cmdgenSource,
                        "HAS_STATISTICS", 1,
                        "STATISTICS_BUFFER_BINDING", STATISTICS_BUFFER_BINDING);
            }
            this.cmdgenProgramId = compileComputeProgram(cmdgenSource, "cmdgen.comp");
            String prefixSumSource = withDefines(
                    ForgeOriginalVoxyShaderSource.parse(supportsSubgroupPrefixSum()
                            ? "voxy:util/prefixsum/inital3.comp"
                            : "voxy:util/prefixsum/simple.comp"),
                    "IO_BUFFER", 0);
            this.prefixSumProgramId = compileComputeProgram(prefixSumSource, "prefixsum");
            String translucentGenSource = withDefines(
                    ForgeOriginalVoxyShaderSource.parse("voxy:lod/gl46/buildtranslucents.comp"),
                    "TRANSLUCENT_WRITE_BASE", TRANSLUCENT_WRITE_BASE,
                    "TRANSLUCENT_DISTANCE_BUFFER_BINDING", TRANSLUCENT_BUILD_DISTANCE_BUFFER_BINDING,
                    "TRANSLUCENT_OFFSET", TRANSLUCENT_OFFSET);
            this.translucentGenProgramId = compileComputeProgram(translucentGenSource, "buildtranslucents.comp");
            this.lifecycleState = "READY";
            this.lastLifecycleEvent = "build-on-render-thread";
            this.lastFailureReason = "none";
            this.lastGlError = GL_NO_ERROR;
            return "none";
        } catch (RuntimeException e) {
            this.freePrograms();
            this.recordFailure("original-mdic-cmdgen-build-" + e.getClass().getSimpleName() + ":" + e.getMessage());
            return this.lastFailureReason;
        }
    }

    void requestReadbackAudit() {
        this.readbackAuditRequested = true;
        this.lastLifecycleEvent = "readback-audit-requested";
    }

    boolean ready() {
        return this.terrainProgramId != 0
                && this.translucentTerrainProgramId != 0
                && this.prepProgramId != 0
                && this.cullProgramId != 0
                && this.cmdgenProgramId != 0
                && this.prefixSumProgramId != 0
                && this.translucentGenProgramId != 0
                && this.uniformBuffer.id != 0
                && this.distanceCountBuffer.id != 0
                && this.statisticsBuffer.id != 0
                && this.sharedIndexBuffer.ready()
                && this.vertexArrayId != 0;
    }

    void renderOpaque(
            ForgeOriginalVoxyMdicViewport viewport,
            ForgeOriginalVoxyBasicSectionGeometryData geometryData,
            ForgeOriginalVoxyModelStore modelStore,
            ForgeOriginalVoxyRenderPipeline pipeline) {
        if (geometryData == null || geometryData.sectionCount() == 0) {
            return;
        }
        this.uploadUniformBuffer(viewport);
        int maxDrawCount = Math.min((int) (geometryData.sectionCount() * 4.4D + 128), OPAQUE_DRAW_COUNT);
        this.renderTerrain(
                viewport,
                geometryData,
                modelStore,
                pipeline,
                this.terrainProgramId,
                0L,
                4L * 3L,
                maxDrawCount);
        this.opaqueRenderCallCount++;
    }

    void renderTemporal(
            ForgeOriginalVoxyMdicViewport viewport,
            ForgeOriginalVoxyBasicSectionGeometryData geometryData,
            ForgeOriginalVoxyModelStore modelStore,
            ForgeOriginalVoxyRenderPipeline pipeline) {
        if (geometryData == null || geometryData.sectionCount() == 0) {
            return;
        }
        this.renderTerrain(
                viewport,
                geometryData,
                modelStore,
                pipeline,
                this.terrainProgramId,
                TEMPORAL_OFFSET * 5L * 4L,
                4L * 5L,
                Math.min(geometryData.sectionCount(), ForgeOriginalVoxyMdicViewport.TEMPORAL_DRAW_COUNT));
        this.temporalRenderCallCount++;
    }

    void renderTranslucent(
            ForgeOriginalVoxyMdicViewport viewport,
            ForgeOriginalVoxyBasicSectionGeometryData geometryData,
            ForgeOriginalVoxyModelStore modelStore,
            ForgeOriginalVoxyRenderPipeline pipeline) {
        if (geometryData == null || geometryData.sectionCount() == 0) {
            return;
        }
        if (!pipeline.translucentDrawTargetReady()) {
            this.recordFailure("original-mdic-section-renderer-translucent-target-not-ready");
            return;
        }
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(this.properties.closerEqualDepthCompare());
        glUseProgram(this.translucentTerrainProgramId);
        glBindVertexArray(this.vertexArrayId);
        pipeline.setupAndBindTranslucent(viewport);
        this.bindRenderingBuffers(viewport, geometryData, modelStore);
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);
        glMultiDrawElementsIndirectCountARB(
                GL_TRIANGLES,
                GL_UNSIGNED_SHORT,
                TRANSLUCENT_OFFSET * 5L * 4L,
                4L * 4L,
                Math.min(geometryData.sectionCount(), TRANSLUCENT_DRAW_COUNT),
                0);
        glEnable(GL_CULL_FACE);
        glBindVertexArray(0);
        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        glBindSampler(1, 0);
        glBindTextureUnit(1, 0);
        glDisable(GL_BLEND);
        this.translucentRenderCallCount++;
    }

    void buildDrawCalls(
            ForgeOriginalVoxyMdicViewport viewport,
            ForgeOriginalVoxyBasicSectionGeometryData geometryData,
            ForgeOriginalVoxyRenderProperties properties) {
        if (!this.ready()) {
            this.recordFailure("original-mdic-cmdgen-not-ready");
            return;
        }
        if (geometryData == null || geometryData.metadataBufferId() == 0 || geometryData.geometryBufferId() == 0) {
            this.recordFailure("original-mdic-cmdgen-geometry-data-not-ready");
            return;
        }
        if (viewport == null || !viewport.ready()) {
            this.recordFailure("original-mdic-cmdgen-viewport-not-ready");
            return;
        }
        try {
            this.uploadUniformBuffer(viewport);
            this.dispatchPrep(viewport);
            this.rasterCullVisibility(viewport, geometryData, properties);
            this.dispatchCmdgen(viewport, geometryData);
            this.dispatchTranslucentCommandGeneration(viewport, geometryData);
            if (this.readbackAuditRequested) {
                this.readbackAuditRequested = false;
                this.readbackAudit(viewport);
            }
            this.inputParityReady = true;
            this.lifecycleState = "RUNNING_ORIGINAL_MDIC_CMDGEN";
            this.lastLifecycleEvent = "build-draw-calls";
            if (this.readbackAuditRuns == 0 || this.readbackAuditReady) {
                this.lastFailureReason = "none";
            }
            this.lastGlError = glGetError();
        } catch (RuntimeException e) {
            this.recordFailure("original-mdic-cmdgen-run-" + e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }

    ForgeOriginalVoxyMdicCommandGenerationStats createStatusSnapshot() {
        boolean ownerReady = this.ready();
        boolean drawCountLayoutMatches = this.cmdGenDispatchY == 1
                && this.cmdGenDispatchZ == 1
                && this.cmdGenDispatchX == ((this.renderListSectionCount + 127) / 128);
        boolean cullLayoutMatches = this.cullCommandCount == CULL_COMMAND_COUNT
                && this.cullCommandInstanceCount == this.renderListSectionCount
                && this.cullCommandFirstIndex == ForgeOriginalVoxySharedIndexBuffer.CUBE_INDEX_OFFSET;
        return new ForgeOriginalVoxyMdicCommandGenerationStats(
                STAGE,
                ownerReady,
                this.inputParityReady,
                this.outputParityReady,
                this.readbackAuditReady,
                this.barrierAuditReady,
                this.cmdgenProgramId != 0,
                this.prepProgramId != 0,
                this.cullProgramId != 0,
                false,
                false,
                false,
                false,
                this.prepProgramId != 0,
                this.cullProgramId != 0,
                this.cmdgenProgramId != 0,
                this.uniformBuffer.id != 0,
                this.distanceCountBuffer.id != 0,
                this.sharedIndexBuffer.ready(),
                this.vertexArrayId != 0,
                ownerReady,
                this.inputParityReady,
                this.inputParityReady,
                this.inputParityReady,
                this.inputParityReady,
                this.inputParityReady,
                this.prepDispatchCount,
                this.cullRasterCount,
                this.cmdgenDispatchCount,
                this.readbackAuditRuns,
                this.readbackAuditFailures,
                this.renderListSectionCount,
                this.cmdGenDispatchX,
                this.cmdGenDispatchY,
                this.cmdGenDispatchZ,
                this.opaqueDrawCount,
                this.translucentDrawCount,
                this.temporalOpaqueDrawCount,
                this.cullCommandCount,
                this.cullCommandInstanceCount,
                this.cullCommandFirstIndex,
                this.firstCommandCount,
                this.firstCommandInstanceCount,
                this.firstCommandFirstIndex,
                this.firstCommandBaseVertex,
                this.firstCommandBaseInstance,
                this.firstPositionScratchWord0,
                this.firstPositionScratchWord1,
                true,
                drawCountLayoutMatches,
                cullLayoutMatches,
                this.positionScratchReadbackOk,
                true,
                false,
                false,
                false,
                this.lastGlError,
                this.lifecycleState,
                this.lastLifecycleEvent,
                this.lastFailureReason
        );
    }

    void freeOnRenderThread() {
        this.freePrograms();
        this.uniformBuffer.free();
        this.distanceCountBuffer.free();
        this.statisticsBuffer.free();
        this.sharedIndexBuffer.free();
        if (this.vertexArrayId != 0) {
            glDeleteVertexArrays(this.vertexArrayId);
        }
        this.lifecycleState = "FREED";
        this.lastLifecycleEvent = "free-on-render-thread";
    }

    private void bindRenderingBuffers(
            ForgeOriginalVoxyMdicViewport viewport,
            ForgeOriginalVoxyBasicSectionGeometryData geometryData,
            ForgeOriginalVoxyModelStore modelStore) {
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, geometryData.geometryBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, geometryData.metadataBufferId());
        modelStore.bind(3, 4, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, viewport.positionScratchBuffer.id);
        bindLightmap(1);
        glBindTextureUnit(2, viewport.depthBoundingTextureId());
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.sharedIndexBuffer.id());
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, viewport.drawCallBuffer.id);
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, viewport.drawCountCallBuffer.id);
    }

    private void renderTerrain(
            ForgeOriginalVoxyMdicViewport viewport,
            ForgeOriginalVoxyBasicSectionGeometryData geometryData,
            ForgeOriginalVoxyModelStore modelStore,
            ForgeOriginalVoxyRenderPipeline pipeline,
            int programId,
            long indirectOffset,
            long drawCountOffset,
            int maxDrawCount) {
        if (!this.ready()) {
            this.recordFailure("original-mdic-section-renderer-not-ready");
            return;
        }
        if (viewport == null || !viewport.ready()) {
            this.recordFailure("original-mdic-section-renderer-viewport-not-ready");
            return;
        }
        if (geometryData == null || geometryData.geometryBufferId() == 0 || geometryData.metadataBufferId() == 0) {
            this.recordFailure("original-mdic-section-renderer-geometry-not-ready");
            return;
        }
        if (modelStore == null) {
            this.recordFailure("original-mdic-section-renderer-model-store-not-ready");
            return;
        }
        if (!pipeline.opaqueDrawTargetReady()) {
            this.recordFailure("original-mdic-section-renderer-opaque-target-not-ready");
            return;
        }
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(this.properties.closerEqualDepthCompare());
        glUseProgram(programId);
        glBindVertexArray(this.vertexArrayId);
        pipeline.setupAndBindOpaque(viewport);
        this.bindRenderingBuffers(viewport, geometryData, modelStore);
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);
        glMultiDrawElementsIndirectCountARB(
                GL_TRIANGLES,
                GL_UNSIGNED_SHORT,
                indirectOffset,
                drawCountOffset,
                maxDrawCount,
                0);
        glEnable(GL_CULL_FACE);
        glBindVertexArray(0);
        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        glBindSampler(1, 0);
        glBindTextureUnit(1, 0);
        this.lastGlError = glGetError();
    }

    private void uploadUniformBuffer(ForgeOriginalVoxyMdicViewport viewport) {
        long ptr = ForgeOriginalVoxyUploadStream.instance().upload(this.uniformBuffer.id, 0L, 1024L);

        Matrix4f mvp = new Matrix4f(viewport.MVP);
        mvp.translate(-viewport.innerTranslation.x, -viewport.innerTranslation.y, -viewport.innerTranslation.z);
        mvp.getToAddress(ptr);
        ptr += 4L * 4L * 4L;

        viewport.section.getToAddress(ptr);
        ptr += 4L * 3L;
        if (viewport.frameId < 0) {
            VoxyForge.LOGGER.error("Original MDIC frame id is negative; wrapping as original renderer expects");
            viewport.frameId &= 0x7fffffff;
        }
        MemoryUtil.memPutInt(ptr, viewport.frameId & 0x7fffffff);
        ptr += 4L;
        viewport.innerTranslation.getToAddress(ptr);
        ForgeOriginalVoxyUploadStream.instance().commit();
    }

    private void dispatchPrep(ForgeOriginalVoxyMdicViewport viewport) {
        glUseProgram(this.prepProgramId);
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, viewport.drawCountCallBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, viewport.renderListBufferId());
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute(1, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glUseProgram(0);
        this.prepDispatchCount++;
    }

    private void rasterCullVisibility(
            ForgeOriginalVoxyMdicViewport viewport,
            ForgeOriginalVoxyBasicSectionGeometryData geometryData,
            ForgeOriginalVoxyRenderProperties properties) {
        boolean depthWasEnabled = glIsEnabled(GL_DEPTH_TEST);
        boolean cullWasEnabled = glIsEnabled(GL_CULL_FACE);
        boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        int depthFunc = glGetInteger(GL_DEPTH_FUNC);
        boolean representativeFragmentEnabled = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var colorMask = stack.malloc(4);
            glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
            boolean colorMaskR = colorMask.get(0) != 0;
            boolean colorMaskG = colorMask.get(1) != 0;
            boolean colorMaskB = colorMask.get(2) != 0;
            boolean colorMaskA = colorMask.get(3) != 0;
            try {
                glUseProgram(this.cullProgramId);
                glBindVertexArray(this.vertexArrayId);
                glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, geometryData.metadataBufferId());
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, viewport.visibilityBuffer.id);
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, viewport.indirectLookupBuffer.id);
                glBindBuffer(GL_DRAW_INDIRECT_BUFFER, viewport.drawCountCallBuffer.id);
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.sharedIndexBuffer.id());
                if (representativeFragmentTestSupported()) {
                    glEnable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
                    representativeFragmentEnabled = true;
                }
                glEnable(GL_DEPTH_TEST);
                glDepthFunc(properties.closerEqualDepthCompare());
                glColorMask(false, false, false, false);
                glDepthMask(false);
                glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);
                glDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_BYTE, CULL_COMMAND_OFFSET_BYTES);
            } finally {
                if (representativeFragmentEnabled) {
                    glDisable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
                }
                glDepthMask(depthMask);
                glColorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA);
            }
        } finally {
            if (!depthWasEnabled) {
                glDisable(GL_DEPTH_TEST);
            }
            if (cullWasEnabled) {
                glEnable(GL_CULL_FACE);
            } else {
                glDisable(GL_CULL_FACE);
            }
            glDepthFunc(depthFunc);
            glBindVertexArray(0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
            glUseProgram(0);
        }
        this.cullRasterCount++;
    }

    private void dispatchCmdgen(
            ForgeOriginalVoxyMdicViewport viewport,
            ForgeOriginalVoxyBasicSectionGeometryData geometryData) {
        this.distanceCountBuffer.clearRange(0L, TRANSLUCENT_WRITE_BASE * 4L);
        glUseProgram(this.cmdgenProgramId);
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, DRAW_BUFFER_BINDING, viewport.drawCallBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, DRAW_COUNT_BUFFER_BINDING, viewport.drawCountCallBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SECTION_METADATA_BUFFER_BINDING, geometryData.metadataBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, VISIBILITY_BUFFER_BINDING, viewport.visibilityBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, INDIRECT_SECTION_LOOKUP_BINDING, viewport.indirectLookupBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, POSITION_SCRATCH_BINDING, viewport.positionScratchBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TRANSLUCENT_DISTANCE_BUFFER_BINDING, this.distanceCountBuffer.id);
        if (ForgeOriginalVoxyRenderStatistics.enabled) {
            this.statisticsBuffer.zero();
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, STATISTICS_BUFFER_BINDING, this.statisticsBuffer.id);
        }

        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, viewport.drawCountCallBuffer.id);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchComputeIndirect(0L);
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        if (ForgeOriginalVoxyRenderStatistics.enabled) {
            ForgeOriginalVoxyDownloadStream.instance().download(
                    this.statisticsBuffer.id,
                    this.statisticsBuffer.size(),
                    down -> {
                        final int layers = WorldEngine.MAX_LOD_LAYER + 1;
                        for (int i = 0; i < layers; i++) {
                            ForgeOriginalVoxyRenderStatistics.visibleSections[i] = MemoryUtil.memGetInt(down.address + i * 4L);
                        }
                        for (int i = 0; i < layers; i++) {
                            ForgeOriginalVoxyRenderStatistics.quadCount[i] = MemoryUtil.memGetInt(down.address + layers * 4L + i * 4L);
                        }
                    });
        }
        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, 0);
        glUseProgram(0);
        this.barrierAuditReady = true;
        this.cmdgenDispatchCount++;
    }

    private void dispatchTranslucentCommandGeneration(
            ForgeOriginalVoxyMdicViewport viewport,
            ForgeOriginalVoxyBasicSectionGeometryData geometryData) {
        glUseProgram(this.prefixSumProgramId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, this.distanceCountBuffer.id);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchCompute(1, 1, 1);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
        this.prefixSumDispatchCount++;

        glUseProgram(this.translucentGenProgramId);
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, DRAW_BUFFER_BINDING, viewport.drawCallBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, DRAW_COUNT_BUFFER_BINDING, viewport.drawCountCallBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SECTION_METADATA_BUFFER_BINDING, geometryData.metadataBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TRANSLUCENT_INDIRECT_SECTION_LOOKUP_BINDING, viewport.indirectLookupBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TRANSLUCENT_BUILD_DISTANCE_BUFFER_BINDING, this.distanceCountBuffer.id);
        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, viewport.drawCountCallBuffer.id);
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        glDispatchComputeIndirect(0L);
        glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, 0);
        glUseProgram(0);
        this.translucentGenDispatchCount++;
    }

    private void readbackAudit(ForgeOriginalVoxyMdicViewport viewport) {
        this.readbackAuditRuns++;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long drawCountPtr = stack.nmalloc(DRAW_COUNT_WORDS * Integer.BYTES);
            nglGetNamedBufferSubData(
                    viewport.drawCountCallBuffer.id,
                    0L,
                    DRAW_COUNT_WORDS * (long) Integer.BYTES,
                    drawCountPtr);
            this.cmdGenDispatchX = MemoryUtil.memGetInt(drawCountPtr);
            this.cmdGenDispatchY = MemoryUtil.memGetInt(drawCountPtr + 4L);
            this.cmdGenDispatchZ = MemoryUtil.memGetInt(drawCountPtr + 8L);
            this.opaqueDrawCount = MemoryUtil.memGetInt(drawCountPtr + 12L);
            this.translucentDrawCount = MemoryUtil.memGetInt(drawCountPtr + 16L);
            this.temporalOpaqueDrawCount = MemoryUtil.memGetInt(drawCountPtr + 20L);
            this.cullCommandCount = MemoryUtil.memGetInt(drawCountPtr + 24L);
            this.cullCommandInstanceCount = MemoryUtil.memGetInt(drawCountPtr + 28L);
            this.cullCommandFirstIndex = MemoryUtil.memGetInt(drawCountPtr + 32L);

            long renderListPtr = stack.nmalloc(Integer.BYTES);
            nglGetNamedBufferSubData(viewport.indirectLookupBuffer.id, 0L, Integer.BYTES, renderListPtr);
            this.renderListSectionCount = MemoryUtil.memGetInt(renderListPtr);

            if (this.opaqueDrawCount > 0) {
                long commandPtr = stack.nmalloc(DRAW_COMMAND_BYTES);
                nglGetNamedBufferSubData(viewport.drawCallBuffer.id, 0L, DRAW_COMMAND_BYTES, commandPtr);
                this.firstCommandCount = MemoryUtil.memGetInt(commandPtr);
                this.firstCommandInstanceCount = MemoryUtil.memGetInt(commandPtr + 4L);
                this.firstCommandFirstIndex = MemoryUtil.memGetInt(commandPtr + 8L);
                this.firstCommandBaseVertex = MemoryUtil.memGetInt(commandPtr + 12L);
                this.firstCommandBaseInstance = MemoryUtil.memGetInt(commandPtr + 16L);

                long positionPtr = stack.nmalloc(2 * Integer.BYTES);
                nglGetNamedBufferSubData(viewport.positionScratchBuffer.id, 0L, 2L * Integer.BYTES, positionPtr);
                this.firstPositionScratchWord0 = MemoryUtil.memGetInt(positionPtr);
                this.firstPositionScratchWord1 = MemoryUtil.memGetInt(positionPtr + 4L);
            } else {
                this.firstCommandCount = 0;
                this.firstCommandInstanceCount = 0;
                this.firstCommandFirstIndex = 0;
                this.firstCommandBaseVertex = 0;
                this.firstCommandBaseInstance = 0;
                this.firstPositionScratchWord0 = 0;
                this.firstPositionScratchWord1 = 0;
            }
        }

        boolean hasRenderListInput = this.renderListSectionCount > 0;
        boolean hasOpaqueOutput = this.opaqueDrawCount > 0;
        boolean drawCountLayoutMatches = this.cmdGenDispatchY == 1
                && this.cmdGenDispatchZ == 1
                && this.cmdGenDispatchX == ((this.renderListSectionCount + 127) / 128);
        boolean cullLayoutMatches = this.cullCommandCount == CULL_COMMAND_COUNT
                && this.cullCommandInstanceCount == this.renderListSectionCount
                && this.cullCommandFirstIndex == ForgeOriginalVoxySharedIndexBuffer.CUBE_INDEX_OFFSET;
        boolean commandLayoutMatches = hasOpaqueOutput
                && this.firstCommandCount > 0
                && this.firstCommandInstanceCount == 1
                && this.firstCommandFirstIndex == 0
                && (this.firstCommandBaseVertex & 3) == 0;
        this.positionScratchReadbackOk = hasOpaqueOutput && this.firstCommandBaseInstance >= 0;
        this.outputParityReady = hasRenderListInput && drawCountLayoutMatches && cullLayoutMatches && commandLayoutMatches;
        this.readbackAuditReady = this.outputParityReady && this.positionScratchReadbackOk && this.barrierAuditReady;
        if (!this.readbackAuditReady) {
            this.readbackAuditFailures++;
            if (!hasRenderListInput) {
                this.lastFailureReason = "original-mdic-cmdgen-no-render-list-input";
            } else if (!hasOpaqueOutput) {
                this.lastFailureReason = "original-mdic-cmdgen-no-opaque-output";
            } else {
                this.lastFailureReason = "original-mdic-cmdgen-readback-audit-failed";
            }
        } else {
            this.lastFailureReason = "none";
        }
        this.lastLifecycleEvent = "readback-audit";
        this.lastGlError = glGetError();
    }

    private void freePrograms() {
        if (this.terrainProgramId != 0) {
            glDeleteProgram(this.terrainProgramId);
            this.terrainProgramId = 0;
        }
        if (this.translucentTerrainProgramId != 0) {
            glDeleteProgram(this.translucentTerrainProgramId);
            this.translucentTerrainProgramId = 0;
        }
        if (this.prepProgramId != 0) {
            glDeleteProgram(this.prepProgramId);
            this.prepProgramId = 0;
        }
        if (this.cullProgramId != 0) {
            glDeleteProgram(this.cullProgramId);
            this.cullProgramId = 0;
        }
        if (this.cmdgenProgramId != 0) {
            glDeleteProgram(this.cmdgenProgramId);
            this.cmdgenProgramId = 0;
        }
        if (this.prefixSumProgramId != 0) {
            glDeleteProgram(this.prefixSumProgramId);
            this.prefixSumProgramId = 0;
        }
        if (this.translucentGenProgramId != 0) {
            glDeleteProgram(this.translucentGenProgramId);
            this.translucentGenProgramId = 0;
        }
    }

    private static String withDefines(String source, Object... defines) {
        StringBuilder builder = new StringBuilder();
        int split = source.indexOf('\n');
        builder.append(source, 0, split + 1);
        for (int i = 0; i < defines.length; i += 2) {
            builder.append("#define ").append(defines[i]).append(' ').append(defines[i + 1]).append('\n');
        }
        builder.append(source.substring(split + 1));
        return builder.toString();
    }

    private static String injectDirectionalFaceTint(String source) {
        float noShade = 1.0F;
        float up = 1.0F;
        float down = 1.0F;
        float zAxis = 1.0F;
        float xAxis = 1.0F;
        var level = Minecraft.getInstance().level;
        if (level != null) {
            noShade = level.getShade(Direction.UP, false);
            up = level.getShade(Direction.UP, false);
            down = level.getShade(Direction.DOWN, true);
            zAxis = level.getShade(Direction.NORTH, true);
            xAxis = level.getShade(Direction.EAST, true);
        }
        return withDefines(
                source,
                "NO_SHADE_FACE_TINT", floatLiteral(noShade),
                "UP_FACE_TINT", floatLiteral(up),
                "DOWN_FACE_TINT", floatLiteral(down),
                "Z_AXIS_FACE_TINT", floatLiteral(zAxis),
                "X_AXIS_FACE_TINT", floatLiteral(xAxis));
    }

    private static void bindLightmap(int lightingIndex) {
        glBindSampler(lightingIndex, 0);
        glBindTextureUnit(lightingIndex, ForgeOriginalVoxyRenderStateCapture.lightTextureId());
    }

    private static boolean representativeFragmentTestSupported() {
        return GL.getCapabilities().GL_NV_representative_fragment_test;
    }

    private static boolean supportsSubgroupPrefixSum() {
        if (!GL.getCapabilities().GL_KHR_shader_subgroup) {
            return false;
        }
        int shader = 0;
        try {
            shader = compileShader(GL_COMPUTE_SHADER, """
                    #version 430
                    #extension GL_KHR_shader_subgroup_basic : require
                    #extension GL_KHR_shader_subgroup_arithmetic : require
                    layout(local_size_x=32) in;
                    void main() {
                        uint value = subgroupExclusiveAdd(gl_LocalInvocationIndex);
                    }
                    """, "subgroup-prefix-sum-probe");
            return true;
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            if (shader != 0) {
                glDeleteShader(shader);
            }
        }
    }

    private static String floatLiteral(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "1.0";
        }
        return Float.toString(value);
    }

    private static int compilePatchedOrNormal(
            String vertexSource,
            String fragmentSource,
            String normalFragmentSource,
            String name) {
        boolean patched = fragmentSource != normalFragmentSource;
        try {
            return compileProgram(
                    vertexSource,
                    patched ? withDefines(fragmentSource, "PATCHED_SHADER", 1) : fragmentSource,
                    name);
        } catch (RuntimeException e) {
            if (patched) {
                VoxyForge.LOGGER.error("Failed to compile original Voxy terrain shader patch; using normal shader path", e);
                return compilePatchedOrNormal(vertexSource, normalFragmentSource, normalFragmentSource, name);
            }
            throw e;
        }
    }

    private static int compileComputeProgram(String source, String name) {
        int shader = compileShader(GL_COMPUTE_SHADER, source, name);
        int program = glCreateProgram();
        glAttachShader(program, shader);
        glLinkProgram(program);
        glDeleteShader(shader);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException(name + " link failed: " + log);
        }
        return program;
    }

    private static int compileProgram(String vertexSource, String fragmentSource, String name) {
        int vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource, name + " vertex");
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource, name + " fragment");
        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException(name + " link failed: " + log);
        }
        return program;
    }

    private static int compileShader(int type, String source, String name) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(name + " compile failed: " + log);
        }
        return shader;
    }

    private void recordFailure(String reason) {
        this.lastGlError = glGetError();
        this.lifecycleState = "FAILED";
        this.lastLifecycleEvent = "failure";
        this.lastFailureReason = reason == null || reason.isBlank() ? "unspecified" : reason.replace(' ', '-');
        VoxyForge.LOGGER.error("Original MDIC command generation failure: {}", this.lastFailureReason);
    }
}
