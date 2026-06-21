package me.cortex.voxy.forge;

import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

final class ForgeOriginalVoxyRenderPipeline {
    static final String STAGE = "VII_ORIGINAL_TERRAIN_SHADER_CONTRACT_CHAIN";

    private final ForgeOriginalVoxyRenderProperties properties;
    private final ForgeOriginalVoxyPipelineDepthStage depthStage;
    private final ForgeOriginalVoxyNormalPipelineTargets normalTargets;
    private final ForgeOriginalVoxyFullscreenBlit finalBlit;
    private long setupCount;
    private long setupAndBindOpaqueCount;
    private long setupAndBindTranslucentCount;
    private long finishCount;
    private String lifecycleState = "CREATED";
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyRenderPipeline(ForgeOriginalVoxyRenderProperties properties) {
        this.properties = properties;
        ForgeOriginalVoxyPipelineDepthStage createdDepthStage = new ForgeOriginalVoxyPipelineDepthStage(properties);
        ForgeOriginalVoxyNormalPipelineTargets createdNormalTargets = new ForgeOriginalVoxyNormalPipelineTargets();
        ForgeOriginalVoxyFullscreenBlit createdFinalBlit;
        try {
            createdFinalBlit = new ForgeOriginalVoxyFullscreenBlit(
                    properties,
                    "voxy:post/fullscreen.vert",
                    "voxy:post/blit_texture_depth_cutout.frag",
                    "EMIT_COLOUR");
        } catch (RuntimeException e) {
            createdNormalTargets.freeOnRenderThread();
            createdDepthStage.freeOnRenderThread();
            throw e;
        }
        this.depthStage = createdDepthStage;
        this.normalTargets = createdNormalTargets;
        this.finalBlit = createdFinalBlit;
    }

    ForgeOriginalVoxyRenderProperties properties() {
        return this.properties;
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

    void finish(int sourceFramebuffer) {
        glDisable(GL_STENCIL_TEST);
        glBindFramebuffer(GL_FRAMEBUFFER, sourceFramebuffer);
        this.finishCount++;
        this.lastLifecycleEvent = "finish";
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
        return this.depthStage.ready();
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
        return this.normalTargets.ssaoComputeReady();
    }

    boolean finalBlitReady() {
        return false;
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

    long finishCount() {
        return this.finishCount;
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
        this.finalBlit.free();
        this.normalTargets.freeOnRenderThread();
        this.depthStage.freeOnRenderThread();
        this.lifecycleState = "FREED";
        this.lastLifecycleEvent = "free-on-render-thread";
    }
}
