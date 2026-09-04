package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Round10DepthStageClearContractTest {
    @Test
    void reusableDepthTargetEstablishesWriteMasksBeforeClearAndRestoresTheCaller() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java"));
        int capture = source.indexOf("GlState state = GlState.capture();");
        int depthWrite = source.indexOf("glDepthMask(true);", capture);
        int stencilWrite = source.indexOf("glStencilMask(0xFF);", capture);
        int clear = source.indexOf("glClearNamedFramebufferfi(", capture);
        int blit = source.indexOf("this.depthStencilSetup.blit();", clear);
        int restore = source.indexOf("state.restore();", blit);
        assertTrue(capture >= 0 && depthWrite > capture && stencilWrite > capture);
        assertTrue(depthWrite < clear && stencilWrite < clear,
                "both write masks must be established before a masked named clear can retain old pixels");
        assertTrue(blit > clear && restore > blit);
        assertTrue(source.substring(blit, restore).contains("finally"));
        assertTrue(source.contains("glGetBoolean(GL_DEPTH_WRITEMASK)"));
        assertTrue(source.contains("glGetInteger(GL_STENCIL_WRITEMASK)"));
        assertTrue(source.contains("glDepthMask(this.depthMask);"));
        assertTrue(source.contains("glStencilMask(this.stencilWriteMask);"));
    }

    @Test
    void fixPreservesTheOriginalFullscreenNearFarStencilContract() throws IOException {
        String forge = compact(Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java")));
        String original = compact(Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/client/core/AbstractRenderPipeline.java")));
        String shader = compact(Files.readString(Path.of(
                "src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag")));
        assertTrue(forge.contains("newDepthFramebuffer(GL_DEPTH24_STENCIL8)"));
        assertTrue(original.contains("newDepthFramebuffer(GL_DEPTH24_STENCIL8)"));
        assertTrue(forge.contains("GL_DEPTH_STENCIL,0,this.properties.clearDepth(),1"));
        assertTrue(original.contains("GL_DEPTH_STENCIL,0,this.properties.clearDepth(),1"));
        assertTrue(forge.contains("\"voxy:post/fullscreen2.vert\",\"voxy:post/setup_stencil_depth.frag\""));
        assertTrue(original.contains("\"voxy:post/fullscreen2.vert\",\"voxy:post/setup_stencil_depth.frag\""));
        assertTrue(shader.contains("gl_FragDepth=NEAR;"));
        assertTrue(shader.contains("if(texture(depthTex,UV*scaleFactor).r==FAR){discard;}"));
        assertFalse(forge.contains("glBlitNamedFramebuffer"), "do not replace the portable fullscreen path with a cross-format blit");
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }
}
