package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL11C.GL_ALWAYS;
import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_EQUAL;
import static org.lwjgl.opengl.GL11C.GL_KEEP;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.glColorMask;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glStencilFunc;
import static org.lwjgl.opengl.GL11C.glStencilMask;
import static org.lwjgl.opengl.GL11C.glStencilOp;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL20C.glUniform4f;
import static org.lwjgl.opengl.GL20C.nglUniformMatrix4fv;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;
import static org.lwjgl.opengl.GL45C.glBlitNamedFramebuffer;
import static org.lwjgl.opengl.GL45C.glTextureBarrier;

final class ForgeOriginalVoxyRenderPipeline {
    static final String STAGE = "VII_ORIGINAL_TERRAIN_SHADER_CONTRACT_CHAIN";

    private final ForgeOriginalVoxyRenderProperties properties;
    private final ForgeOriginalVoxyPipelineDepthStage depthStage;
    private final ForgeOriginalVoxyNormalPipelineTargets normalTargets;
    private final ForgeOriginalVoxyFullscreenBlit finalBlit;
    private final ForgeOriginalVoxySSAO ssao;
    private final boolean useEnvFog;
    private final ForgeOriginalVoxyOculusPipelineBridge.Result oculusBridgeResult;
    private final ForgeOriginalVoxyOculusRenderPipelineData oculusPipelineData;
    //The Oculus pipeline instance the data above was captured from; used by the frame path to
    // detect that Oculus swapped pipelines after this owner was built (see the bridge comment).
    private final Object oculusSourcePipelineInstance;
    private final ForgeOriginalVoxyGlBuffer oculusShaderUniforms;
    private final ForgeOriginalVoxyFullscreenBlit shaderpackDepthBlit;
    private final ForgeOriginalVoxyFullscreenBlit shaderDepthHackFixTransformBlit;
    private static final boolean AUDIT_SOURCE_DEPTH = Boolean.getBoolean("voxy.forge.auditSourceDepth");
    private int drawTargetAuditCount;
    private long setupCount;
    private long setupAndBindOpaqueCount;
    private long setupAndBindTranslucentCount;
    private long postOpaquePreTranslucentCount;
    private long finishCount;
    private String lifecycleState = "CREATED";
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    private String lastLoggedFailureReason = "";

    //Per-frame guard failures were previously recorded silently, which made "LOD invisible"
    // states undiagnosable from logs. Log once per distinct reason (deduped against the last
    // LOGGED reason, since lastFailureReason is reset to "none" by successful paths), never
    // per frame.
    private void recordSilentFailure(String reason) {
        if (!reason.equals(this.lastLoggedFailureReason)) {
            this.lastLoggedFailureReason = reason;
            me.cortex.voxy.common.Logger.warn("Original Voxy render pipeline failure: " + reason);
        }
        this.lastFailureReason = reason;
    }

    ForgeOriginalVoxyRenderPipeline(ForgeOriginalVoxyRenderProperties properties) {
        this.properties = properties;
        this.useEnvFog = ForgeVoxyConfig.ORIGINAL_VOXY_USE_ENVIRONMENTAL_FOG.get();
        ForgeOriginalVoxyOculusPipelineBridge.Result capturedOculusData =
                ForgeOriginalVoxyOculusPipelineBridge.captureCurrentData();
        if (!capturedOculusData.ready()) {
            throw new IllegalStateException(capturedOculusData.failureReason());
        }
        ForgeOriginalVoxyOculusRenderPipelineData capturedPipelineData = capturedOculusData.data();
        ForgeOriginalVoxyPipelineDepthStage createdDepthStage = new ForgeOriginalVoxyPipelineDepthStage(properties);
        ForgeOriginalVoxyNormalPipelineTargets createdNormalTargets = new ForgeOriginalVoxyNormalPipelineTargets();
        ForgeOriginalVoxyFullscreenBlit createdFinalBlit = null;
        ForgeOriginalVoxySSAO createdSsao = null;
        ForgeOriginalVoxyGlBuffer createdOculusShaderUniforms = null;
        ForgeOriginalVoxyFullscreenBlit createdShaderpackDepthBlit = null;
        ForgeOriginalVoxyFullscreenBlit createdShaderDepthHackFixTransformBlit = null;
        boolean boundOculusPipelineData = false;
        try {
            String[] finalBlitDefines = this.useEnvFog
                    ? new String[]{"USE_ENV_FOG", "EMIT_COLOUR"}
                    : new String[]{"EMIT_COLOUR"};
            createdFinalBlit = new ForgeOriginalVoxyFullscreenBlit(
                    properties,
                    "voxy:post/fullscreen.vert",
                    "voxy:post/blit_texture_depth_cutout.frag",
                    finalBlitDefines);
            createdSsao = ForgeOriginalVoxySSAO.create(properties, ForgeOriginalVoxySSAO.SSAOMode.AUTO);
            if (capturedPipelineData != null) {
                if (capturedPipelineData.getUniforms() != null) {
                    createdOculusShaderUniforms = new ForgeOriginalVoxyGlBuffer(capturedPipelineData.getUniforms().size()).zero();
                }
                createdShaderpackDepthBlit = new ForgeOriginalVoxyFullscreenBlit(
                        properties,
                        "voxy:post/fullscreen.vert",
                        "voxy:post/blit_texture_depth_cutout.frag");
                if (!capturedPipelineData.skipShaderDepthHackFix) {
                    createdShaderDepthHackFixTransformBlit = new ForgeOriginalVoxyFullscreenBlit(
                            properties,
                            "voxy:post/fullscreen2.vert",
                            "voxy:post/noop.frag");
                }
                capturedPipelineData.bindPipeline(this);
                boundOculusPipelineData = true;
            }
        } catch (RuntimeException e) {
            if (boundOculusPipelineData) {
                capturedPipelineData.unbindPipeline(this);
            }
            if (createdShaderDepthHackFixTransformBlit != null) {
                createdShaderDepthHackFixTransformBlit.free();
            }
            if (createdShaderpackDepthBlit != null) {
                createdShaderpackDepthBlit.free();
            }
            if (createdOculusShaderUniforms != null) {
                createdOculusShaderUniforms.free();
            }
            if (createdSsao != null) {
                createdSsao.free();
            }
            if (createdFinalBlit != null) {
                createdFinalBlit.free();
            }
            createdNormalTargets.freeOnRenderThread();
            createdDepthStage.freeOnRenderThread();
            throw e;
        }
        this.depthStage = createdDepthStage;
        this.normalTargets = createdNormalTargets;
        this.finalBlit = createdFinalBlit;
        this.ssao = createdSsao;
        this.oculusBridgeResult = capturedOculusData;
        this.oculusPipelineData = capturedPipelineData;
        this.oculusSourcePipelineInstance = capturedOculusData.pipelineInstance();
        this.oculusShaderUniforms = createdOculusShaderUniforms;
        this.shaderpackDepthBlit = createdShaderpackDepthBlit;
        this.shaderDepthHackFixTransformBlit = createdShaderDepthHackFixTransformBlit;
    }

    boolean oculusPipelineGenerationStale() {
        return ForgeOriginalVoxyOculusPipelineBridge.pipelineGenerationChanged(this.oculusSourcePipelineInstance);
    }

    ForgeOriginalVoxyRenderProperties properties() {
        return this.properties;
    }

    void preSetup(ForgeOriginalVoxyMdicViewport viewport) {
        this.lastLifecycleEvent = "pre-setup";
        this.lastFailureReason = "none";
        ForgeOriginalVoxyOculusVoxyUniforms.captureViewport(viewport);
        if (this.oculusShaderUniforms != null) {
            long ptr = ForgeOriginalVoxyUploadStream.instance()
                    .upload(this.oculusShaderUniforms.id, 0L, this.oculusShaderUniforms.size());
            this.oculusPipelineData.getUniforms().updater().accept(ptr);
            ForgeOriginalVoxyUploadStream.instance().commit();
            this.lastLifecycleEvent = "pre-setup-oculus-shaderpack-uniforms";
        }
    }

    int setup(
            ForgeOriginalVoxyMdicViewport viewport,
            int sourceFramebuffer,
            int alternateSourceFramebuffer,
            int srcWidth,
            int srcHeight) {
        if (this.oculusPipelineData != null && !this.oculusPipelineData.useViewportDims) {
            srcWidth = viewport.width;
            srcHeight = viewport.height;
        }
        int depthTexture = this.depthStage.setupDepthTexture(
                sourceFramebuffer,
                alternateSourceFramebuffer,
                srcWidth,
                srcHeight,
                viewport.width,
                viewport.height);
        this.normalTargets.resize(this.depthStage, viewport.width, viewport.height, this.oculusPipelineData);
        if (AUDIT_SOURCE_DEPTH && this.oculusPipelineData != null && this.drawTargetAuditCount < 8) {
            this.drawTargetAuditCount++;
            ForgeOriginalVoxyOculusPipelineBridge.auditDrawTargets(
                    this.drawTargetAuditCount,
                    this.oculusSourcePipelineInstance,
                    this.oculusPipelineData,
                    this.depthStage.framebufferId(),
                    this.normalTargets.translucentFramebufferId());
        }
        this.setupCount++;
        this.lifecycleState = "SETUP";
        this.lastLifecycleEvent = this.oculusPipelineData == null
                ? "setup-normal-pipeline-targets"
                : "setup-oculus-shaderpack-targets";
        this.lastFailureReason = "none";
        return depthTexture;
    }

    void setupAndBindOpaque(ForgeOriginalVoxyMdicViewport viewport) {
        this.setupAndBindOpaqueCount++;
        this.lastLifecycleEvent = "setup-and-bind-opaque";
        if (!this.opaqueDrawTargetReady()) {
            this.recordSilentFailure("original-normal-pipeline-colour-target-not-ready");
            return;
        }
        this.normalTargets.bindOpaqueFramebuffer(this.depthStage);
        this.applyTerrainDepthStencilState();
        if (this.oculusPipelineData != null) {
            this.bindOculusShaderpackBindings();
        }
    }

    void setupAndBindTranslucent(ForgeOriginalVoxyMdicViewport viewport) {
        this.setupAndBindTranslucentCount++;
        this.lastLifecycleEvent = "setup-and-bind-translucent";
        if (!this.translucentDrawTargetReady()) {
            this.recordSilentFailure("original-normal-pipeline-translucent-target-not-ready");
            return;
        }
        this.normalTargets.bindTranslucentFramebuffer();
        this.applyTerrainDepthStencilState();
        if (this.oculusPipelineData != null) {
            this.bindOculusShaderpackBindings();
            if (this.oculusPipelineData.getBlender() != null) {
                this.oculusPipelineData.getBlender().run();
            }
        }
    }

    void postOpaquePreTranslucent(ForgeOriginalVoxyMdicViewport viewport, int sourceFramebuffer, boolean emitDepthOutput) {
        this.postOpaquePreTranslucentCount++;
        this.lastLifecycleEvent = "post-opaque-pre-translucent";
        if (!emitDepthOutput) {
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_DEPTH_TEST);
            this.lastLifecycleEvent = "post-opaque-pre-translucent-skipped-no-draw-output";
            this.lastFailureReason = "none";
            return;
        }
        if (this.oculusPipelineData != null) {
            this.postOpaquePreTranslucentOculus(viewport);
            return;
        }
        if (!this.opaqueDrawTargetReady() || !this.translucentDrawTargetReady()) {
            this.recordSilentFailure("original-normal-pipeline-ssao-target-not-ready");
            return;
        }
        this.ssao.compute(
                viewport,
                this.normalTargets.colourSsaoTextureId(),
                this.normalTargets.colourTextureId(),
                this.depthStage.depthTextureId(),
                sourceFramebuffer);
        this.normalTargets.bindTranslucentFramebuffer();
        this.lastFailureReason = "none";
    }

    void finish(ForgeOriginalVoxyMdicViewport viewport, int sourceFramebuffer, int srcWidth, int srcHeight, boolean emitDepthOutput) {
        if (!emitDepthOutput) {
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_BLEND);
            glBindFramebuffer(GL_FRAMEBUFFER, sourceFramebuffer);
            this.finishCount++;
            this.lastLifecycleEvent = "finish-skipped-no-draw-output";
            this.lastFailureReason = "none";
            return;
        }
        if (this.oculusPipelineData != null) {
            this.finishOculus(viewport, sourceFramebuffer, srcWidth, srcHeight);
            return;
        }
        this.finalBlit.bind();
        glBindTextureUnit(3, this.normalTargets.colourSsaoTextureId());
        boolean fogCoversAllRendering = viewport.fogParameters.environmentalEnd() < minecraftRenderDistance();
        if (this.useEnvFog) {
            uploadEnvironmentalFogUniforms(viewport.fogParameters);
        }
        if (!fogCoversAllRendering) {
            glEnable(GL_BLEND);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            try {
                transformBlitDepth(
                        this.finalBlit,
                        this.depthStage.depthTextureId(),
                        sourceFramebuffer,
                        viewport,
                        new Matrix4f(viewport.vanillaProjection).mul(viewport.modelView));
            } finally {
                glDisable(GL_BLEND);
            }
        } else {
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_BLEND);
        }
        this.finishCount++;
        this.lastLifecycleEvent = "finish";
        this.lastFailureReason = "none";
    }

    boolean hasTAA() {
        return this.oculusPipelineData != null && this.oculusPipelineData.TAA != null;
    }

    float[] renderScalingFactor() {
        return this.oculusPipelineData == null ? null : this.oculusPipelineData.resolutionScale;
    }

    String taaFunction(String functionName) {
        return this.taaFunction(ForgeOriginalVoxyOculusRenderPipelineData.UNIFORM_BINDING_POINT, functionName);
    }

    void bindUniforms() {
        if (this.oculusShaderUniforms != null) {
            glBindBufferBase(
                    GL_UNIFORM_BUFFER,
                    ForgeOriginalVoxyOculusRenderPipelineData.UNIFORM_BINDING_POINT,
                    this.oculusShaderUniforms.id);
        }
    }

    String taaFunction(int uboBindingPoint, String functionName) {
        if (this.oculusPipelineData == null || this.oculusPipelineData.TAA == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        if (this.oculusPipelineData.getUniforms() != null) {
            builder.append("layout(binding = ")
                    .append(uboBindingPoint)
                    .append(", std140) uniform ShaderUniformBindings ")
                    .append(this.oculusPipelineData.getUniforms().layout())
                    .append(";\n\n");
        }
        builder.append("vec2 ").append(functionName).append("()\n");
        builder.append(this.oculusPipelineData.TAA);
        builder.append('\n');
        return builder.toString();
    }

    //Diagnostic bisect switch: render with the NORMAL terrain programs while a shaderpack is
    // active, separating patched-program defects from pack draw-target/blend/depth integration
    // defects. Never a formal route.
    private static final boolean FORCE_NORMAL_TERRAIN_SHADERS =
            Boolean.getBoolean("voxy.forge.forceNormalTerrainShaders");

    String patchOpaqueShader(ForgeOriginalVoxyMdicSectionRenderer renderer, String input) {
        if (this.oculusPipelineData == null || FORCE_NORMAL_TERRAIN_SHADERS) {
            return null;
        }
        StringBuilder builder = this.buildOculusShaderHeader(input);
        builder.append(this.oculusPipelineData.opaqueFragPatch());
        return builder.toString();
    }

    String patchTranslucentShader(ForgeOriginalVoxyMdicSectionRenderer renderer, String input) {
        if (this.oculusPipelineData == null || this.oculusPipelineData.translucentFragPatch() == null || FORCE_NORMAL_TERRAIN_SHADERS) {
            return null;
        }
        StringBuilder builder = this.buildOculusShaderHeader(input);
        builder.append(this.oculusPipelineData.translucentFragPatch());
        return builder.toString();
    }

    boolean ready() {
        boolean normalReady = this.depthStage.ready() && this.ssao.ready() && this.finalBlit != null;
        if (this.oculusPipelineData == null) {
            return normalReady;
        }
        return normalReady
                && this.shaderpackDepthBlit != null
                && (this.oculusPipelineData.getUniforms() == null || this.oculusShaderUniforms != null)
                && (this.oculusPipelineData.skipShaderDepthHackFix || this.shaderDepthHackFixTransformBlit != null);
    }

    boolean terrainShaderSourceParityReady() {
        return true;
    }

    boolean renderPipelineShaderBindingOwnerReady() {
        return this.ready();
    }

    boolean opaqueDrawTargetReady() {
        return this.normalTargets.opaqueDrawTargetReady();
    }

    boolean translucentDrawTargetReady() {
        return this.normalTargets.translucentDrawTargetReady();
    }

    boolean ssaoComputeReady() {
        return this.ssao.ready();
    }

    boolean finalBlitReady() {
        return this.finalBlit != null;
    }

    boolean finalBlitShaderReady() {
        return this.finalBlit != null;
    }

    boolean useEnvironmentalFog() {
        return this.oculusPipelineData == null && this.useEnvFog;
    }

    boolean oculusShaderpackActive() {
        return this.oculusBridgeResult.shaderpackActive();
    }

    boolean oculusPipelineDataReady() {
        return this.oculusBridgeResult.pipelineDataReady() && this.oculusPipelineData != null;
    }

    boolean oculusShaderPatchReady() {
        return this.oculusPipelineData != null
                && this.oculusPipelineData.opaqueFragPatch() != null
                && this.oculusPipelineData.opaqueDrawTargets != null
                && this.oculusPipelineData.translucentDrawTargets != null;
    }

    boolean oculusShaderBindingsReady() {
        return this.oculusPipelineData != null
                && (this.oculusPipelineData.getUniforms() == null || this.oculusShaderUniforms != null);
    }

    boolean oculusDrawTargetsReady() {
        return this.oculusPipelineData != null && this.normalTargets.usingExternalDrawTargets();
    }

    boolean oculusUniformsReady() {
        return this.oculusPipelineData != null
                && (this.oculusPipelineData.getUniforms() == null || this.oculusShaderUniforms != null);
    }

    boolean oculusSsboBindingsReady() {
        return this.oculusPipelineData != null
                && (this.oculusPipelineData.getSsboSet() == null || this.oculusPipelineData.hasSsbos());
    }

    boolean oculusImageBindingsReady() {
        return this.oculusPipelineData != null
                && (this.oculusPipelineData.getImageSet() == null
                || (this.oculusPipelineData.hasImages() && this.oculusPipelineData.getImageSet().ready()));
    }

    boolean oculusBlendReady() {
        return this.oculusPipelineData != null && this.oculusPipelineData.hasBlendSetup();
    }

    boolean oculusTaaReady() {
        return this.oculusPipelineData != null && this.oculusPipelineData.hasTaa();
    }

    String oculusPipelineSource() {
        return this.oculusBridgeResult.source();
    }

    String oculusPipelineFailureReason() {
        String imageFailure = this.oculusImageSetFailureReason();
        if (!"none".equals(imageFailure)) {
            return imageFailure;
        }
        return this.oculusBridgeResult.failureReason();
    }

    int colourTextureId() {
        return this.normalTargets.colourTextureId();
    }

    int colourSsaoTextureId() {
        return this.normalTargets.colourSsaoTextureId();
    }

    int oculusOpaqueDepthTextureId() {
        return this.depthStage.depthTextureId();
    }

    int oculusTranslucentDepthTextureId() {
        int translucentDepthTexture = this.normalTargets.translucentDepthTextureId();
        return translucentDepthTexture == 0 ? this.depthStage.depthTextureId() : translucentDepthTexture;
    }

    long normalTargetResizeCount() {
        return this.normalTargets.resizeCount();
    }

    long setupCount() {
        return this.setupCount;
    }

    long setupAndBindOpaqueCount() {
        return this.setupAndBindOpaqueCount;
    }

    long setupAndBindTranslucentCount() {
        return this.setupAndBindTranslucentCount;
    }

    long postOpaquePreTranslucentCount() {
        return this.postOpaquePreTranslucentCount;
    }

    long finishCount() {
        return this.finishCount;
    }

    long ssaoComputeCount() {
        return this.ssao.computeCount();
    }

    String ssaoModeName() {
        return this.ssao.modeName();
    }

    String lifecycleState() {
        return this.lifecycleState;
    }

    String lastLifecycleEvent() {
        return this.lastLifecycleEvent;
    }

    String lastFailureReason() {
        if (!"none".equals(this.lastFailureReason)) {
            return this.lastFailureReason;
        }
        String targetFailure = this.normalTargets.lastFailureReason();
        return "none".equals(targetFailure) ? this.depthStage.lastFailureReason() : targetFailure;
    }

    void freeOnRenderThread() {
        if (this.oculusPipelineData != null) {
            this.oculusPipelineData.unbindPipeline(this);
        }
        if (this.shaderDepthHackFixTransformBlit != null) {
            this.shaderDepthHackFixTransformBlit.free();
        }
        if (this.shaderpackDepthBlit != null) {
            this.shaderpackDepthBlit.free();
        }
        if (this.oculusShaderUniforms != null) {
            this.oculusShaderUniforms.free();
        }
        this.ssao.free();
        this.finalBlit.free();
        this.normalTargets.freeOnRenderThread();
        this.depthStage.freeOnRenderThread();
        this.lifecycleState = "FREED";
        this.lastLifecycleEvent = "free-on-render-thread";
    }

    private void postOpaquePreTranslucentOculus(ForgeOriginalVoxyMdicViewport viewport) {
        if (!this.opaqueDrawTargetReady() || !this.translucentDrawTargetReady()) {
            this.recordSilentFailure("oculus-shaderpack-draw-target-not-ready");
            return;
        }
        if (this.shaderDepthHackFixTransformBlit != null) {
            this.normalTargets.bindOpaqueFramebuffer(this.depthStage);
            this.applyTerrainDepthStencilState();
            glColorMask(false, false, false, false);
            try {
                glDepthFunc(GL_ALWAYS);
                glStencilFunc(GL_EQUAL, 0, 0xFF);
                this.shaderDepthHackFixTransformBlit.blit();
            } finally {
                glStencilFunc(GL_EQUAL, 1, 0xFF);
                glDepthFunc(this.properties.closerEqualDepthCompare());
                glColorMask(true, true, true, true);
            }
        }
        glTextureBarrier();
        glBlitNamedFramebuffer(
                this.depthStage.framebufferId(),
                this.normalTargets.translucentFramebufferId(),
                0,
                0,
                viewport.width,
                viewport.height,
                0,
                0,
                viewport.width,
                viewport.height,
                GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT,
                GL_NEAREST);
        this.lastFailureReason = "none";
    }

    private void applyTerrainDepthStencilState() {
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(this.properties.closerEqualDepthCompare());
        glEnable(GL_STENCIL_TEST);
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
        glStencilFunc(GL_EQUAL, 1, 0xFF);
        glStencilMask(0xFF);
    }

    //Diagnostic bisect switch: skip writing Voxy LOD depth back into the hook-time framebuffer's
    // depth on the shaderpack path. If post-switch holes vanish with this, the written-back depth
    // is surviving into the next frame's stencil setup and self-culling the LOD. Never a formal route.
    private static final boolean SKIP_VANILLA_DEPTH_FEEDBACK =
            Boolean.getBoolean("voxy.forge.skipVanillaDepthFeedback");

    private void finishOculus(ForgeOriginalVoxyMdicViewport viewport, int sourceFramebuffer, int srcWidth, int srcHeight) {
        if (!SKIP_VANILLA_DEPTH_FEEDBACK
                && this.oculusPipelineData.renderToVanillaDepth && srcWidth == viewport.width && srcHeight == viewport.height) {
            int shaderpackDepthTexture = this.normalTargets.translucentDepthTextureId();
            if (shaderpackDepthTexture == 0) {
                shaderpackDepthTexture = this.depthStage.depthTextureId();
            }
            glColorMask(false, false, false, false);
            try {
                transformBlitDepth(
                        this.shaderpackDepthBlit,
                        shaderpackDepthTexture,
                        sourceFramebuffer,
                        viewport,
                        new Matrix4f(viewport.vanillaProjection).mul(viewport.modelView));
            } finally {
                glColorMask(true, true, true, true);
            }
        } else {
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_DEPTH_TEST);
        }
        this.finishCount++;
        this.lastLifecycleEvent = "finish-oculus-shaderpack";
        this.lastFailureReason = "none";
    }

    private void bindOculusShaderpackBindings() {
        if (this.oculusShaderUniforms != null) {
            glBindBufferBase(
                    GL_UNIFORM_BUFFER,
                    ForgeOriginalVoxyOculusRenderPipelineData.UNIFORM_BINDING_POINT,
                    this.oculusShaderUniforms.id);
        }
        if (this.oculusPipelineData.getSsboSet() != null) {
            this.oculusPipelineData.getSsboSet()
                    .bindingFunction()
                    .accept(ForgeOriginalVoxyOculusRenderPipelineData.BUFFER_BINDING_INDEX_BASE);
        }
        if (this.oculusPipelineData.getImageSet() != null) {
            this.oculusPipelineData.getImageSet()
                    .bindingFunction()
                    .accept(ForgeOriginalVoxyOculusRenderPipelineData.BASE_SAMPLER_BINDING_INDEX);
            String imageFailure = this.oculusImageSetFailureReason();
            if (!"none".equals(imageFailure)) {
                this.lastFailureReason = imageFailure;
            }
        }
    }

    private String oculusImageSetFailureReason() {
        if (this.oculusPipelineData == null || this.oculusPipelineData.getImageSet() == null) {
            return "none";
        }
        return this.oculusPipelineData.getImageSet().lastFailureReason();
    }

    private StringBuilder buildOculusShaderHeader(String input) {
        StringBuilder builder = new StringBuilder(input).append("\n\n\n");
        if (this.oculusPipelineData.getUniforms() != null) {
            builder.append("layout(binding = ")
                    .append(ForgeOriginalVoxyOculusRenderPipelineData.UNIFORM_BINDING_POINT)
                    .append(", std140) uniform ShaderUniformBindings ")
                    .append(this.oculusPipelineData.getUniforms().layout())
                    .append(";\n\n");
        }
        if (this.oculusPipelineData.getSsboSet() != null) {
            builder.append("#define BUFFER_BINDING_INDEX_BASE ")
                    .append(ForgeOriginalVoxyOculusRenderPipelineData.BUFFER_BINDING_INDEX_BASE)
                    .append('\n');
            builder.append(this.oculusPipelineData.getSsboSet().layout()).append("\n\n");
        }
        if (this.oculusPipelineData.getImageSet() != null) {
            builder.append("#define BASE_SAMPLER_BINDING_INDEX ")
                    .append(ForgeOriginalVoxyOculusRenderPipelineData.BASE_SAMPLER_BINDING_INDEX)
                    .append('\n');
            builder.append(this.oculusPipelineData.getImageSet().layout()).append("\n\n");
        }
        return builder.append("\n\n");
    }

    private static void transformBlitDepth(
            ForgeOriginalVoxyFullscreenBlit blitShader,
            int sourceDepthTexture,
            int destinationFramebuffer,
            ForgeOriginalVoxyMdicViewport viewport,
            Matrix4f targetTransform) {
        glDisable(GL_STENCIL_TEST);
        glBindFramebuffer(GL_FRAMEBUFFER, destinationFramebuffer);
        blitShader.bind();
        glBindTextureUnit(0, sourceDepthTexture);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long matrix = stack.nmalloc(4 * 4 * 4);
            new Matrix4f(viewport.MVP).invert().getToAddress(matrix);
            nglUniformMatrix4fv(1, 1, false, matrix);
            targetTransform.getToAddress(matrix);
            nglUniformMatrix4fv(2, 1, false, matrix);
        }
        glEnable(GL_DEPTH_TEST);
        blitShader.blit();
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_DEPTH_TEST);
    }

    private static void uploadEnvironmentalFogUniforms(ForgeOriginalVoxyFogParameters fogParameters) {
        float start = fogParameters.environmentalStart();
        float end = fogParameters.environmentalEnd();
        if (Math.abs(end - start) > 1.0F) {
            float invEndFogDelta = 1.0F / (end - start);
            float endDistance = Math.max(minecraftRenderDistance(), 20.0F * 16.0F);
            endDistance *= (float) Math.sqrt(3.0D);
            float startDelta = -start * invEndFogDelta;
            float endParameter = clamp01(endDistance * invEndFogDelta + startDelta);
            glUniform4f(4, invEndFogDelta, startDelta, endParameter, 0.0F);
            glUniform4f(5, fogParameters.red(), fogParameters.green(), fogParameters.blue(), fogParameters.alpha());
        } else {
            glUniform4f(4, 0.0F, 0.0F, 0.0F, 0.0F);
            glUniform4f(5, 0.0F, 0.0F, 0.0F, 0.0F);
        }
    }

    private static float minecraftRenderDistance() {
        return Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(value, 1.0F));
    }
}
