package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeLightPacketCompletionContractTest {
    @Test
    void vanillaCoordinatesAreCapturedAfterClientThreadDispatch() throws IOException {
        String source = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyVanillaLightPacketMixin.java");
        int captureStart = source.indexOf("@Inject(");
        int wrapStart = source.indexOf("@ModifyArg(", captureStart);
        assertTrue(captureStart >= 0 && wrapStart > captureStart);
        String capture = source.substring(captureStart, wrapStart);

        assertTrue(capture.contains("method = \"handleLevelChunkWithLight\""));
        assertTrue(capture.contains(
                "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"));
        assertTrue(capture.contains("shift = At.Shift.AFTER"));
        assertFalse(capture.contains("@At(\"HEAD\")"));
        assertTrue(capture.contains("this.voxy$pendingLightChunkX = packet.getX()"));
        assertTrue(capture.contains("this.voxy$pendingLightChunkZ = packet.getZ()"));

        String wrapper = source.substring(wrapStart);
        int closure = wrapper.indexOf("return () -> {");
        int levelCapture = wrapper.indexOf("ClientLevel packetLevel = this.level;");
        int xCapture = wrapper.indexOf("int chunkX = this.voxy$pendingLightChunkX;");
        int zCapture = wrapper.indexOf("int chunkZ = this.voxy$pendingLightChunkZ;");
        assertTrue(levelCapture >= 0 && levelCapture < closure);
        assertTrue(xCapture >= 0 && xCapture < closure);
        assertTrue(zCapture >= 0 && zCapture < closure);
        String deferredBody = wrapper.substring(closure, wrapper.indexOf("};", closure));
        assertFalse(deferredBody.contains("this.level"));
        assertFalse(deferredBody.contains("this.voxy$pendingLightChunk"));
    }

    @Test
    void platformCompletionAdaptersShareOneTrustedIngestOwner() throws IOException {
        String vanilla = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyVanillaLightPacketMixin.java");
        String starlight = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyStarlightLightPacketMixin.java");
        String plugin = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeVoxyMixinPlugin.java");
        String instance = source("src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java");
        String retry = source("src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java");
        String ingest = source(
                "src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java");
        String manager = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java");

        assertTrue(vanilla.contains("ClientLevel;queueLightUpdate(Ljava/lang/Runnable;)V"));
        assertTrue(vanilla.indexOf("applyLightData.run()")
                < vanilla.indexOf("voxy$ingestCompletedChunk(packetLevel, chunkX, chunkZ)"));
        assertTrue(starlight.contains("priority = 900"));
        assertTrue(starlight.contains("at = @At(\"RETURN\")"));
        assertTrue(starlight.contains("ingestChunkAfterLightUpdate(chunk)"));
        assertTrue(plugin.contains("return !modPresent.test(\"starlight\")"));
        assertTrue(plugin.contains("return modPresent.test(\"starlight\")"));
        assertTrue(instance.contains("this.ingestRetryQueue.ingestChunkAfterLightUpdate(chunk)"));
        assertTrue(retry.contains("tryAutoIngestTrustedChunkWithStats(chunk)"));
        assertTrue(retry.contains("cancelDeferredRetryState(this.queuedChunks"));
        assertTrue(ingest.contains("ingestChunkWithStats(engine, chunk, true)"));
        assertFalse(manager.contains("Backfilled"));
        assertFalse(manager.contains("voxy$forEachLoadedChunk"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
