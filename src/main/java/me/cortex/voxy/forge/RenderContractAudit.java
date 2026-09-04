package me.cortex.voxy.forge;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL46C.*;

/** Opt-in, bounded observation of the real frame owner; never changes rendering state. */
final class RenderContractAudit {
    private static final boolean ENABLED = Boolean.getBoolean("voxy.forge.auditRound10");
    private static final Map<String, Integer> SAMPLES = new HashMap<>();

    private RenderContractAudit() {
    }

    static void record(String phase, String key, MDICViewport viewport,
                       ForgeOriginalVoxyRenderPipeline pipeline, long capturedGeneration,
                       boolean reusedPreparation, int sourceFramebuffer, int alternateSourceFramebuffer,
                       int preparedDepthTexture) {
        if (!ENABLED) return;
        String identity = phase + ':' + System.identityHashCode(pipeline) + ':'
                + System.identityHashCode(viewport) + ':' + viewport.width + 'x' + viewport.height;
        if (SAMPLES.size() >= 128 && !SAMPLES.containsKey(identity)) return;
        int samples = SAMPLES.getOrDefault(identity, 0);
        if (samples >= 4) return;
        SAMPLES.put(identity, samples + 1);
        VoxyForge.LOGGER.info(
                "ROUND10_FRAME phase={} key={} view={} frame={} capture={} prepared={} size={}x{} properties={} clipDepth={} depthFunc={} clear={} sourceDraw={} sourceRead={} preparedDepth={} opaque={} translucent={} hiz={}",
                phase, key, System.identityHashCode(viewport), viewport.frameId, capturedGeneration,
                reusedPreparation, viewport.width, viewport.height, pipeline.properties(),
                glGetInteger(GL_CLIP_DEPTH_MODE), glGetInteger(GL_DEPTH_FUNC), glGetFloat(GL_DEPTH_CLEAR_VALUE),
                depthAttachmentInfo(sourceFramebuffer), depthAttachmentInfo(alternateSourceFramebuffer),
                textureInfo(preparedDepthTexture), textureInfo(pipeline.oculusOpaqueDepthTextureId()),
                textureInfo(pipeline.oculusTranslucentDepthTextureId()), textureInfo(viewport.hizTextureId()));
    }

    static String depthAttachmentInfo(int framebuffer) {
        if (framebuffer == 0) return "default-framebuffer";
        if (!glIsFramebuffer(framebuffer)) return "missing-framebuffer:" + framebuffer;
        int type = glGetNamedFramebufferAttachmentParameteri(
                framebuffer, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type == GL_NONE) return framebuffer + ":depth=none";
        int object = glGetNamedFramebufferAttachmentParameteri(
                framebuffer, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        if (type == GL_TEXTURE) {
            int level = glGetNamedFramebufferAttachmentParameteri(
                    framebuffer, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
            return framebuffer + ":texture=" + textureInfo(object, level);
        }
        if (type == GL_RENDERBUFFER) {
            return framebuffer + ":renderbuffer=" + object
                    + ":format=" + glGetNamedRenderbufferParameteri(object, GL_RENDERBUFFER_INTERNAL_FORMAT)
                    + ":size=" + glGetNamedRenderbufferParameteri(object, GL_RENDERBUFFER_WIDTH) + 'x'
                    + glGetNamedRenderbufferParameteri(object, GL_RENDERBUFFER_HEIGHT)
                    + ":samples=" + glGetNamedRenderbufferParameteri(object, GL_RENDERBUFFER_SAMPLES);
        }
        return framebuffer + ":depth-type=" + type;
    }

    private static String textureInfo(int texture) {
        return textureInfo(texture, 0);
    }

    private static String textureInfo(int texture, int level) {
        if (texture == 0 || !glIsTexture(texture)) return "none";
        return texture + ":level=" + level + ":format=" + glGetTextureLevelParameteri(texture, level, GL_TEXTURE_INTERNAL_FORMAT)
                + ":size=" + glGetTextureLevelParameteri(texture, level, GL_TEXTURE_WIDTH) + 'x'
                + glGetTextureLevelParameteri(texture, level, GL_TEXTURE_HEIGHT)
                + ":samples=" + glGetTextureLevelParameteri(texture, level, GL_TEXTURE_SAMPLES);
    }
}
