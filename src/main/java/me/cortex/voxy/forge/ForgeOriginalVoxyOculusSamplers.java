package me.cortex.voxy.forge;

import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;

public final class ForgeOriginalVoxyOculusSamplers {
    private static GlSampler mippedNearestNearest;

    private ForgeOriginalVoxyOculusSamplers() {
    }

    public static void addSamplers(IrisRenderingPipeline pipeline, SamplerHolder samplers) {
        ForgeOriginalVoxyOculusShaderPatch patchData =
                ((ForgeOriginalVoxyOculusPatchDataAccess) pipeline).voxy$getPatchData();
        if (patchData == null) {
            patchData = ForgeOriginalVoxyOculusPipelineBridge.currentShaderpackPatch();
        }
        if (patchData == null) {
            return;
        }

        samplers.addDynamicSampler(
                TextureType.TEXTURE_2D,
                () -> {
                    ForgeOriginalVoxyOculusRenderPipelineData data =
                            ((ForgeOriginalVoxyOculusPipelineDataAccess) pipeline).voxy$getPipelineData();
                    return data == null ? 0 : data.opaqueDepthTextureId();
                },
                mippedNearestNearest(),
                "vxDepthTexOpaque");
        samplers.addDynamicSampler(
                TextureType.TEXTURE_2D,
                () -> {
                    ForgeOriginalVoxyOculusRenderPipelineData data =
                            ((ForgeOriginalVoxyOculusPipelineDataAccess) pipeline).voxy$getPipelineData();
                    return data == null ? 0 : data.translucentDepthTextureId();
                },
                mippedNearestNearest(),
                "vxDepthTexTrans");
    }

    private static GlSampler mippedNearestNearest() {
        if (mippedNearestNearest == null) {
            mippedNearestNearest = new GlSampler(false, true, false, false);
        }
        return mippedNearestNearest;
    }
}
