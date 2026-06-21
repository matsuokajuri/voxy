package me.cortex.voxy.forge;

import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

final class ForgeOriginalVoxyRenderPipeline {
    static final String STAGE = "VII_ORIGINAL_TERRAIN_SHADER_CONTRACT_CHAIN";

    private final ForgeOriginalVoxyRenderProperties properties;
    private final ForgeOriginalVoxyPipelineDepthStage depthStage;
    private long setupCount;
    private long setupAndBindOpaqueCount;
    private long setupAndBindTranslucentCount;
    private long finishCount;
    private String lifecycleState = "CREATED";
    private String lastLifecycleEvent = "created";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyRenderPipeline(ForgeOriginalVoxyRenderProperties properties) {
        this.properties = properties;
        this.depthStage = new ForgeOriginalVoxyPipelineDepthStage(properties);
    }

    ForgeOriginalVoxyRenderProperties properties() {
        return this.properties;
    }

    int setup(ForgeOriginalVoxyMdicViewport viewport, int sourceFramebuffer, int srcWidth, int srcHeight) {
        int depthTexture = this.depthStage.setupDepthTexture(sourceFramebuffer, srcWidth, srcHeight, viewport.width, viewport.height);
        this.setupCount++;
        this.lifecycleState = "SETUP";
        this.lastLifecycleEvent = "setup-depth-stencil";
        this.lastFailureReason = "none";
        return depthTexture;
    }

    void setupAndBindOpaque(ForgeOriginalVoxyMdicViewport viewport) {
        this.setupAndBindOpaqueCount++;
        this.lastLifecycleEvent = "setup-and-bind-opaque";
        if (!this.opaqueDrawTargetReady()) {
            this.lastFailureReason = "original-normal-pipeline-colour-target-not-ported";
            return;
        }
        this.depthStage.bindFramebuffer();
    }

    void setupAndBindTranslucent(ForgeOriginalVoxyMdicViewport viewport) {
        this.setupAndBindTranslucentCount++;
        this.lastLifecycleEvent = "setup-and-bind-translucent";
        if (!this.translucentDrawTargetReady()) {
            this.lastFailureReason = "original-normal-pipeline-translucent-target-not-ported";
            return;
        }
        this.depthStage.bindFramebuffer();
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
        return false;
    }

    boolean translucentDrawTargetReady() {
        return false;
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
        return "none".equals(this.lastFailureReason) ? this.depthStage.lastFailureReason() : this.lastFailureReason;
    }

    void freeOnRenderThread() {
        this.depthStage.freeOnRenderThread();
        this.lifecycleState = "FREED";
        this.lastLifecycleEvent = "free-on-render-thread";
    }
}
