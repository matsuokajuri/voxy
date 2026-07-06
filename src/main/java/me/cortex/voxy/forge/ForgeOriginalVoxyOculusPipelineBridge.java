package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import me.cortex.voxy.forge.mixin.ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;

import java.util.Arrays;

import static org.lwjgl.opengl.GL11C.glIsTexture;

public final class ForgeOriginalVoxyOculusPipelineBridge {
    private static final boolean FORMAL_SHADERPACK_PATCH_OUTPUT_READY = true;
    private static volatile ForgeOriginalVoxyOculusShaderPatch currentShaderpackPatch;

    private ForgeOriginalVoxyOculusPipelineBridge() {
    }

    public static boolean shaderpackActive() {
        try {
            return Iris.getCurrentPack().isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean shadowActive() {
        return ForgeOculusShadowStateBridge.shadowActive();
    }

    //Original IrisUtil.disableIrisShaders(): used when render-system construction fails with an
    // active shaderpack so the Oculus-triggered reload can retry on the normal path.
    public static void disableShaders() {
        try {
            net.irisshaders.iris.api.v0.IrisApi.getInstance().getConfig().setShadersEnabledAndApply(false);
        } catch (RuntimeException | LinkageError e) {
            VoxyForge.LOGGER.error("Failed to disable Oculus shaders after Voxy render-system construction failure.", e);
        }
    }

    public static boolean shouldExposeVoxyShaderpackPatch() {
        /*
         * Original Voxy exposes VOXY/patch data when rendering is enabled because
         * its MDIC terrain path can already write the requested shaderpack buffers.
         * The Forge port still audits zero MDIC output on the normal Oculus path,
         * so exposing only the render-target table is the safe original-equivalent
         * subset until the formal draw output owner is ready.
         */
        return ForgeVoxyConfig.isEnabledEarlySafe() && FORMAL_SHADERPACK_PATCH_OUTPUT_READY;
    }

    public static void captureCurrentShaderpackPatch(ForgeOriginalVoxyOculusShaderPatch patch) {
        currentShaderpackPatch = patch;
    }

    public static ForgeOriginalVoxyOculusShaderPatch currentShaderpackPatch() {
        return currentShaderpackPatch;
    }

    static Result captureCurrentData() {
        try {
            WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
            if (pipeline == null) {
                return Result.inactive(null, "oculus-pipeline-null");
            }
            if (!(pipeline instanceof IrisRenderingPipeline)) {
                return Result.inactive(pipeline, pipeline.getClass().getName());
            }
            if (!(pipeline instanceof ForgeOriginalVoxyOculusPipelineDataAccess access)) {
                return Result.failure("oculus-pipeline-data-access-missing");
            }
            ForgeOriginalVoxyOculusRenderPipelineData data = access.voxy$getPipelineData();
            if (data == null) {
                return new Result(true, true, false, null, pipeline, "oculus-shaderpack-no-voxy-patch", "none");
            }
            return new Result(true, true, true, data, pipeline, "oculus-shaderpack-voxy-patch", "none");
        } catch (RuntimeException e) {
            VoxyForge.LOGGER.error("Failed to inspect Oculus Voxy pipeline data.", e);
            return Result.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    //Original ordering guarantee: Iris preparePipeline creates the new pipeline and THEN calls
    // levelRenderer.allChanged(), so original VoxyRenderSystem always captures the freshly
    // created pipeline. The Forge reload-edge trigger can rebuild before Oculus lazily creates
    // the new pipeline, capturing data from a generation that is about to be destroyed. This
    // check lets the frame path detect that and restart the owner against the live pipeline.
    static boolean pipelineGenerationChanged(Object capturedPipelineInstance) {
        try {
            return Iris.getPipelineManager().getPipelineNullable() != capturedPipelineInstance;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    //Draw-target staleness audit: our FBOs attach texture ids BAKED at pipeline-data build time
    // (main-vs-alt resolved via getFlippedAfterPrepare), while Iris recreates target textures on
    // resize and creates targets lazily. Logs the baked ids' liveness and the live main/alt table
    // so a mismatch (stale id, or flipped side) is directly visible per lifecycle.
    static void auditDrawTargets(
            int run,
            Object capturedPipelineInstance,
            ForgeOriginalVoxyOculusRenderPipelineData data,
            int voxyOpaqueFramebufferId,
            int voxyTranslucentFramebufferId) {
        try {
            WorldRenderingPipeline live = Iris.getPipelineManager().getPipelineNullable();
            String liveTable = "pipeline-not-accessible";
            int liveDepthTexture = -1;
            int liveNoTranslucentsDepthTexture = -1;
            if (live instanceof ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor access) {
                RenderTargets targets = access.voxy$getRenderTargets();
                StringBuilder table = new StringBuilder();
                for (int i = 0; i < targets.getRenderTargetCount(); i++) {
                    RenderTarget target = targets.get(i);
                    if (target == null) {
                        continue;
                    }
                    table.append(i)
                            .append(":m=").append(target.getMainTexture())
                            .append(",a=").append(target.getAltTexture())
                            .append(' ');
                }
                liveTable = table.toString().trim();
                liveDepthTexture = targets.getDepthTexture();
                liveNoTranslucentsDepthTexture = targets.getDepthTextureNoTranslucents().getTextureId();
            }
            VoxyForge.LOGGER.info(
                    "Original Oculus draw-target audit: run={} samePipelineInstance={} voxyOpaqueFbo={} voxyTranslucentFbo={} opaqueBaked={} opaqueIsTexture={} translucentBaked={} translucentIsTexture={} liveDepthTex={} liveNoTransDepthTex={} liveTargets=[{}]",
                    run,
                    live == capturedPipelineInstance,
                    voxyOpaqueFramebufferId,
                    voxyTranslucentFramebufferId,
                    Arrays.toString(data.opaqueDrawTargets),
                    isTextureFlags(data.opaqueDrawTargets),
                    Arrays.toString(data.translucentDrawTargets),
                    isTextureFlags(data.translucentDrawTargets),
                    liveDepthTexture,
                    liveNoTranslucentsDepthTexture,
                    liveTable);
        } catch (RuntimeException | LinkageError e) {
            VoxyForge.LOGGER.warn("Original Oculus draw-target audit failed: {}", e.toString());
        }
    }

    private static String isTextureFlags(int[] textureIds) {
        StringBuilder flags = new StringBuilder("[");
        for (int i = 0; i < textureIds.length; i++) {
            if (i > 0) {
                flags.append(',');
            }
            flags.append(glIsTexture(textureIds[i]));
        }
        return flags.append(']').toString();
    }

    record Result(
            boolean ready,
            boolean shaderpackActive,
            boolean pipelineDataReady,
            ForgeOriginalVoxyOculusRenderPipelineData data,
            Object pipelineInstance,
            String source,
            String failureReason) {
        static Result inactive(Object pipelineInstance, String source) {
            return new Result(true, false, false, null, pipelineInstance, source, "none");
        }

        static Result failure(String failureReason) {
            return new Result(false, true, false, null, null, "oculus-pipeline-inspection", failureReason);
        }
    }
}
