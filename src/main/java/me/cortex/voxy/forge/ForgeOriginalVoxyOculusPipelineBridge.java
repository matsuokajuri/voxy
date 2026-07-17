package me.cortex.voxy.forge;

import me.cortex.voxy.config.ForgeVoxyConfig;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;

import java.io.IOException;

public final class ForgeOriginalVoxyOculusPipelineBridge {
    private static volatile ForgeOriginalVoxyOculusShaderPatch currentShaderpackPatch;

    private ForgeOriginalVoxyOculusPipelineBridge() {
    }

    public static boolean shaderpackActive() {
        if (!ForgeOculusAvailability.installed()) {
            return false;
        }
        return shaderpackActive0();
    }

    private static boolean shaderpackActive0() {
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
        if (!ForgeOculusAvailability.installed()) {
            return;
        }
        try {
            net.irisshaders.iris.api.v0.IrisApi.getInstance().getConfig().setShadersEnabledAndApply(false);
        } catch (RuntimeException | LinkageError e) {
            VoxyForge.LOGGER.error("Failed to disable Oculus shaders after Voxy render-system construction failure.", e);
        }
    }

    //Forge/Oculus equivalent of original IrisUtil.reload(), used by enabled/rendering changes so
    //the presence of the Voxy patch in ProgramSet is rebuilt from the newly saved configuration.
    static void reloadShaders() {
        if (!ForgeOculusAvailability.installed()) {
            return;
        }
        var api = net.irisshaders.iris.api.v0.IrisApi.getInstance();
        if (!api.isShaderPackInUse() && !api.getConfig().areShadersEnabled()) {
            return;
        }
        try {
            Iris.reload();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to reload Oculus after applying Voxy configuration", e);
        }
    }

    public static boolean shouldExposeVoxyShaderpackPatch() {
        //The active MDIC path owns the complete original patch/output contract.
        return ForgeVoxyConfig.isEnabledEarlySafe();
    }

    public static void captureCurrentShaderpackPatch(ForgeOriginalVoxyOculusShaderPatch patch) {
        currentShaderpackPatch = patch;
    }

    public static ForgeOriginalVoxyOculusShaderPatch currentShaderpackPatch() {
        return currentShaderpackPatch;
    }

    static Result captureCurrentData() {
        if (!ForgeOculusAvailability.installed()) {
            return Result.inactive(null, "oculus-not-installed");
        }
        return captureCurrentData0();
    }

    private static Result captureCurrentData0() {
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
        if (!ForgeOculusAvailability.installed()) {
            return false;
        }
        try {
            return Iris.getPipelineManager().getPipelineNullable() != capturedPipelineInstance;
        } catch (RuntimeException ignored) {
            return false;
        }
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
