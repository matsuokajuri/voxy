package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;

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
                return Result.inactive("oculus-pipeline-null");
            }
            if (!(pipeline instanceof IrisRenderingPipeline)) {
                return Result.inactive(pipeline.getClass().getName());
            }
            if (!(pipeline instanceof ForgeOriginalVoxyOculusPipelineDataAccess access)) {
                return Result.failure("oculus-pipeline-data-access-missing");
            }
            ForgeOriginalVoxyOculusRenderPipelineData data = access.voxy$getPipelineData();
            if (data == null) {
                return new Result(true, true, false, null, "oculus-shaderpack-no-voxy-patch", "none");
            }
            return new Result(true, true, true, data, "oculus-shaderpack-voxy-patch", "none");
        } catch (RuntimeException e) {
            VoxyForge.LOGGER.error("Failed to inspect Oculus Voxy pipeline data.", e);
            return Result.failure(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    record Result(
            boolean ready,
            boolean shaderpackActive,
            boolean pipelineDataReady,
            ForgeOriginalVoxyOculusRenderPipelineData data,
            String source,
            String failureReason) {
        static Result inactive(String source) {
            return new Result(true, false, false, null, source, "none");
        }

        static Result failure(String failureReason) {
            return new Result(false, true, false, null, "oculus-pipeline-inspection", failureReason);
        }
    }
}
