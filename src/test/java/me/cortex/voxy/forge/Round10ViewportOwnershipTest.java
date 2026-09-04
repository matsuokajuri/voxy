package me.cortex.voxy.forge;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class Round10ViewportOwnershipTest {
    @Test
    void preparationBelongsToOneOwnerViewCaptureAndSize() {
        Object owner = new Object(), a = new Object(), b = new Object();
        PreparedViewportState prepared = new PreparedViewportState(owner, a, 7, 1920, 1080);
        assertTrue(prepared.matches(owner, a, 7, 1920, 1080));
        assertFalse(prepared.matches(owner, b, 7, 1920, 1080));
        assertFalse(prepared.matches(new Object(), a, 7, 1920, 1080));
        assertFalse(prepared.matches(owner, a, 8, 1920, 1080));
        assertFalse(prepared.matches(owner, a, 7, 960, 540));
        assertFalse(new PreparedViewportState(owner, a, 7, 0, 0).matches(owner, a, 7, 0, 0));
    }

    @Test
    void equalValuesCannotReplaceViewportIdentity() {
        Object owner = new Object();
        String a = new String("same-view-value"), b = new String("same-view-value");
        assertEquals(a, b);
        assertFalse(new PreparedViewportState(owner, a, 0, 1, 1).matches(owner, b, 0, 1, 1));
    }

    @Test
    void capturesAndCallerMatricesRemainIndependentAcrossABA() {
        try {
            Matrix4f projectionA = new Matrix4f().perspective(1.0f, 1.5f, 0.05f, 4096);
            Matrix4f viewA = new Matrix4f().rotateY(0.3f);
            ForgeOriginalVoxyRenderStateCapture.captureViewport(projectionA, viewA, -0.25, 90, 32.5);
            var a = ForgeOriginalVoxyRenderStateCapture.viewportCopy();
            projectionA.zero();
            viewA.zero();
            assertNotEquals(projectionA, a.projection());
            ForgeOriginalVoxyRenderStateCapture.captureViewport(new Matrix4f(), new Matrix4f(), 1024, 20, -2048);
            var b = ForgeOriginalVoxyRenderStateCapture.viewportCopy();
            assertTrue(b.generation() > a.generation());
            ForgeOriginalVoxyRenderStateCapture.captureViewport(a.projection(), a.modelView(), a.cameraX(), a.cameraY(), a.cameraZ());
            var again = ForgeOriginalVoxyRenderStateCapture.viewportCopy();
            assertEquals(a.projection(), again.projection());
            assertEquals(a.modelView(), again.modelView());
            assertEquals(a.cameraX(), again.cameraX());
            assertTrue(again.generation() > b.generation());
            again.projection().zero();
            assertEquals(a.projection(), ForgeOriginalVoxyRenderStateCapture.viewportCopy().projection());
        } finally {
            ForgeOriginalVoxyRenderStateCapture.clear();
        }
    }

    @Test
    void teardownDropsThePreparedOwnersBeforeDeferredGpuCleanup() throws Exception {
        String source = Files.readString(Path.of("src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java"));
        int teardown = source.indexOf("private void markStaleAndClear(String event, Runnable continuation)");
        int detach = source.indexOf("this.renderSystem = null;", teardown);
        int releasePrepared = source.indexOf("this.preparedOculusViewport = null;", detach);
        int deferCleanup = source.indexOf("this.runOnRenderThread(", detach);
        assertTrue(teardown >= 0 && detach > teardown && releasePrepared > detach && deferCleanup > releasePrepared,
                "Prepared state must not keep a shutdown world/viewport alive after render owner teardown");
    }

    @Test
    void formalOwnerRetainsSerialWholeFrameLifetimeForSharedScratch() throws Exception {
        String pipeline = Files.readString(Path.of("src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java"));
        assertTrue(pipeline.contains("if (this.renderEmbeddiumCutoutActive)"));
        assertTrue(pipeline.contains("!RenderSystem.isOnRenderThread()"));
        assertTrue(pipeline.contains("this.preparedOculusViewport.matches("));
        int opaque = pipeline.indexOf("sectionRenderer.renderOpaque(");
        assertTrue(opaque > 0);
        int commands = pipeline.indexOf("sectionRenderer.buildDrawCalls(", opaque);
        int temporal = pipeline.indexOf("sectionRenderer.renderTemporal(", commands);
        int translucent = pipeline.indexOf("sectionRenderer.renderTranslucent(", temporal);
        int finish = pipeline.indexOf("renderPipeline.finish(", translucent);
        assertTrue(commands > opaque && temporal > commands && translucent > temporal && finish > translucent);
    }
}
