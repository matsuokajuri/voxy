package me.cortex.voxy.forge;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL20C.nglUniformMatrix4fv;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;

final class ForgeOriginalVoxyRenderPipeline {
    static final String STAGE = "VII_ORIGINAL_TERRAIN_SHADER_CONTRACT_CHAIN";

    private final ForgeOriginalVoxyRenderProperties properties;
    private final ForgeOriginalVoxyPipelineDepthStage depthStage;
    private final ForgeOriginalVoxyNormalPipelineTargets normalTargets;
    private final ForgeOriginalVoxyFullscreenBlit finalBlit;
    private final ForgeOriginalVoxySSAO ssao;
    private long setupCount;
    private long setupAndBindOpaqueCount;
    private long setupAndBindTranslucentCount;
    private long postOpaquePreTranslucentCount;
    private long finishCount;
    private String lifecycleState = "CREATED";
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyRenderPipeline(ForgeOriginalVoxyRenderProperties properties) {
        this.properties = properties;
        ForgeOriginalVoxyPipelineDepthStage createdDepthStage = new ForgeOriginalVoxyPipelineDepthStage(properties);
        ForgeOriginalVoxyNormalPipelineTargets createdNormalTargets = new ForgeOriginalVoxyNormalPipelineTargets();
        ForgeOriginalVoxyFullscreenBlit createdFinalBlit = null;
        ForgeOriginalVoxySSAO createdSsao = null;
        try {
            createdFinalBlit = new ForgeOriginalVoxyFullscreenBlit(
                    properties,
                    "voxy:post/fullscreen.vert",
                    "voxy:post/blit_texture_depth_cutout.frag",
                    "EMIT_COLOUR");
            createdSsao = ForgeOriginalVoxySSAO.create(properties, ForgeOriginalVoxySSAO.SSAOMode.AUTO);
        } catch (RuntimeException e) {
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
    }

    ForgeOriginalVoxyRenderProperties properties() {
        return this.properties;
    }

    void preSetup(ForgeOriginalVoxyMdicViewport viewport) {
        this.lastLifecycleEvent = "pre-setup";
        this.lastFailureReason = "none";
    }

    int setup(ForgeOriginalVoxyMdicViewport viewport, int sourceFramebuffer, int srcWidth, int srcHeight) {
        int depthTexture = this.depthStage.setupDepthTexture(sourceFramebuffer, srcWidth, srcHeight, viewport.width, viewport.height);
        this.normalTargets.resize(this.depthStage, viewport.width, viewport.height);
        this.setupCount++;
        this.lifecycleState = "SETUP";
        this.lastLifecycleEvent = "setup-normal-pipeline-targets";
        this.lastFailureReason = "none";
        return depthTexture;
    }

    void setupAndBindOpaque(ForgeOriginalVoxyMdicViewport viewport) {
        this.setupAndBindOpaqueCount++;
        this.lastLifecycleEvent = "setup-and-bind-opaque";
        if (!this.opaqueDrawTargetReady()) {
            this.lastFailureReason = "original-normal-pipeline-colour-target-not-ready";
            return;
        }
        this.normalTargets.bindOpaqueFramebuffer(this.depthStage);
    }

    void setupAndBindTranslucent(ForgeOriginalVoxyMdicViewport viewport) {
        this.setupAndBindTranslucentCount++;
        this.lastLifecycleEvent = "setup-and-bind-translucent";
        if (!this.translucentDrawTargetReady()) {
            this.lastFailureReason = "original-normal-pipeline-translucent-target-not-ready";
            return;
        }
        this.normalTargets.bindTranslucentFramebuffer();
    }

    void postOpaquePreTranslucent(ForgeOriginalVoxyMdicViewport viewport, int sourceFramebuffer) {
        this.postOpaquePreTranslucentCount++;
        this.lastLifecycleEvent = "post-opaque-pre-translucent";
        if (!this.opaqueDrawTargetReady() || !this.translucentDrawTargetReady()) {
            this.lastFailureReason = "original-normal-pipeline-ssao-target-not-ready";
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

    void finish(ForgeOriginalVoxyMdicViewport viewport, int sourceFramebuffer, int srcWidth, int srcHeight) {
        this.finalBlit.bind();
        glBindTextureUnit(3, this.normalTargets.colourSsaoTextureId());
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
        this.finishCount++;
        this.lastLifecycleEvent = "finish";
        this.lastFailureReason = "none";
    }

    boolean hasTAA() {
        return false;
    }

    String taaFunction(String functionName) {
        return this.taaFunction(-1, functionName);
    }

    String taaFunction(int uboBindingPoint, String functionName) {
        return null;
    }

    String patchOpaqueShader(ForgeOriginalVoxyMdicSectionRenderer renderer, String input) {
        return null;
    }

    String patchTranslucentShader(ForgeOriginalVoxyMdicSectionRenderer renderer, String input) {
        return null;
    }

    boolean ready() {
        return this.depthStage.ready() && this.ssao.ready() && this.finalBlit != null;
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

    int colourTextureId() {
        return this.normalTargets.colourTextureId();
    }

    int colourSsaoTextureId() {
        return this.normalTargets.colourSsaoTextureId();
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
        this.ssao.free();
        this.finalBlit.free();
        this.normalTargets.freeOnRenderThread();
        this.depthStage.freeOnRenderThread();
        this.lifecycleState = "FREED";
        this.lastLifecycleEvent = "free-on-render-thread";
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
}
