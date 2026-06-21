package me.cortex.voxy.forge;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;

final class ForgeOriginalVoxyOculusPipelineBridge {
    private ForgeOriginalVoxyOculusPipelineBridge() {
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
