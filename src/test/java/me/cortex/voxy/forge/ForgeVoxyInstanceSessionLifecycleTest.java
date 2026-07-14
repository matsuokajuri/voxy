package me.cortex.voxy.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeVoxyInstanceSessionLifecycleTest {
    @Test
    void processEventShellDoesNotOwnRuntimeResourcesBeforeLogin() {
        ForgeVoxyInstance instance = ForgeVoxyInstance.INSTANCE;

        assertFalse(instance.isSessionRuntimeActive());
        assertFalse(instance.isRunning());
        assertNull(instance.getOriginalVoxyModelPipeline());
        assertNull(instance.getIngestService());
        assertNull(instance.getImportManager());
        assertNull(instance.createEmbeddiumBuilderSemaphoreBlock());
        assertEquals(-1L, instance.originalVoxyChunkBoundOwnerGeneration());
    }

    @Test
    void sessionLevelGuardUsesWeakIdentityMembership() throws ReflectiveOperationException {
        ForgeVoxyInstance.WeakIdentitySet<EqualValue> levels = new ForgeVoxyInstance.WeakIdentitySet<>();
        EqualValue first = new EqualValue(1);
        EqualValue equalButDistinct = new EqualValue(1);
        Object connection = new Object();
        Object otherConnection = new Object();

        assertFalse(ForgeVoxyInstance.acceptsSessionLevel(
                connection, otherConnection, levels, first, first));
        assertFalse(levels.contains(first));
        assertTrue(ForgeVoxyInstance.acceptsSessionLevel(
                connection, connection, levels, first, first));
        assertTrue(ForgeVoxyInstance.acceptsSessionLevel(
                connection, connection, levels, first, equalButDistinct));
        assertFalse(ForgeVoxyInstance.acceptsSessionLevel(
                connection, connection, levels, equalButDistinct, first));
        assertFalse(levels.add(first));

        Field valuesField = ForgeVoxyInstance.WeakIdentitySet.class.getDeclaredField("values");
        valuesField.setAccessible(true);
        List<?> storedValues = assertInstanceOf(List.class, valuesField.get(levels));
        WeakReference<?> storedReference = assertInstanceOf(WeakReference.class, storedValues.get(0));
        assertSame(first, storedReference.get());
        storedReference.clear();
        assertFalse(levels.contains(first));
        assertTrue(storedValues.isEmpty());
        assertTrue(levels.add(first));
    }

    @Test
    void clientLevelSwitchUsesOriginalSetLevelHeadIdentityBoundaryAndOneTeardownOwner() throws IOException {
        String mixin = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java"));
        String instance = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java"));

        String hook = between(
                mixin,
                "private void voxy$shutdownOriginalRendererForLevelSwitch",
                "private void voxy$captureOriginalRawProjection");
        String normalizedMixin = mixin.replaceAll("\\s+", " ");
        assertTrue(normalizedMixin.contains(
                "@Mixin(LevelRenderer.class) public class ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin"));
        assertTrue(normalizedMixin.contains("@Shadow private @Nullable ClientLevel level;"));
        assertTrue(hook.contains("if (this.level != level)"));
        assertTrue(hook.contains("ForgeVoxyInstance.INSTANCE.onOriginalVoxyClientLevelSwitch(level);"));
        assertEquals(1, occurrences(hook, "onOriginalVoxyClientLevelSwitch(level)"));
        assertFalse(hook.contains(".equals("));
        assertFalse(hook.contains("Minecraft.getInstance()"));

        String transition = between(
                instance,
                "public void onOriginalVoxyClientLevelSwitch(ClientLevel targetLevel)",
                "public boolean ensureOriginalVoxyActiveWorldForCurrentWorld()");
        int clearRetry = transition.indexOf("this.ingestRetryQueue.clear()");
        int staleRenderer = transition.indexOf("runtime.modelPipeline.markDimensionSwitch()");
        int closeWorld = transition.indexOf("this.closeActiveWorld(runtime)");
        int runtimeGuard = transition.indexOf("if (runtime == null || !runtime.running)");
        int guardReturn = transition.indexOf("return;", runtimeGuard);
        assertTrue(runtimeGuard >= 0 && guardReturn > runtimeGuard && clearRetry > guardReturn);
        assertTrue(staleRenderer > clearRetry && closeWorld > staleRenderer);
        assertEquals(1, occurrences(transition, "this.ingestRetryQueue.clear()"));
        assertEquals(1, occurrences(transition, "runtime.modelPipeline.markDimensionSwitch()"));
        assertEquals(1, occurrences(transition, "this.closeActiveWorld(runtime)"));
        assertFalse(transition.contains("ensureOriginalVoxyActiveWorldForCurrentWorld"));
        assertFalse(transition.contains("selectOrCreateWorld"));

        String tick = between(
                instance,
                "private void onClientTick(TickEvent.ClientTickEvent event)",
                "private void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event)");
        assertFalse(tick.contains("markDimensionSwitch"));
        assertFalse(tick.contains("closeActiveWorld"));
        assertFalse(tick.contains("activeClientLevel"));
        int ensureWorld = tick.indexOf("this.ensureOriginalVoxyActiveWorldForCurrentWorld()");
        int modelTick = tick.indexOf("runtime.modelPipeline.clientTick()");
        assertTrue(ensureWorld >= 0 && modelTick > ensureWorld);
    }

    @Test
    void gpuTimingHasOneTerminalOwnerAndDoubleFreeFailsFast()
            throws IOException, ReflectiveOperationException {
        String instance = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java"));
        String timingSource = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/GPUTiming.java"));
        String terminal = between(
                instance,
                "private void completeTerminalShutdown()",
                "private synchronized void closeActiveWorld");

        assertEquals(1, occurrences(instance, "GPUTiming.INSTANCE.free();"));
        int guard = terminal.indexOf(
                "if (this.terminalCleanupComplete || this.sessionShutdownInProgress)");
        int armed = terminal.indexOf("this.terminalCleanupComplete = true;");
        int free = terminal.indexOf("GPUTiming.INSTANCE.free();");
        assertTrue(guard >= 0 && armed > guard && free > armed);
        assertFalse(timingSource.contains("private boolean freed;"));
        assertFalse(timingSource.contains("this.freed"));

        var constructor = GPUTiming.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        GPUTiming timing = constructor.newInstance();
        timing.free();
        IllegalStateException failure = assertThrows(IllegalStateException.class, timing::free);
        assertTrue(failure.getMessage().contains("double freed"));
    }

    @Test
    void sharedIndexBuffersKeepOneTerminalOwnerAndExposeDuplicateFree() throws IOException {
        String shared = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java"));
        String buffer = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/GlBuffer.java"));
        String runtime = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java"));
        String instance = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java"));

        assertFalse(shared.contains("private boolean freed;"));
        assertFalse(shared.contains("this.freed"));
        String sharedFree = between(shared, "void free()", "static void freeAll()");
        assertEquals(1, occurrences(sharedFree, "this.indexBuffer.free();"));
        assertFalse(sharedFree.contains("if ("));

        String bufferFree = between(buffer, "public void free()", "static int getCount()");
        int trackedFree = bufferFree.indexOf("this.free0();");
        int deleteBuffer = bufferFree.indexOf("glDeleteBuffers(this.id);");
        assertTrue(trackedFree >= 0 && deleteBuffer > trackedFree);

        String initialize = between(
                runtime,
                "static synchronized boolean initialize()",
                "static synchronized boolean isAvailable()");
        int initializeShared = initialize.indexOf("SharedIndexBuffer.INSTANCE.id();");
        int markInitialized = initialize.indexOf("sharedIndexInitialized = true;");
        assertTrue(initializeShared >= 0 && markInitialized > initializeShared);

        String shutdown = between(
                runtime,
                "static synchronized void shutdown()",
                "private static boolean acquireExclusiveLock()");
        int initializedGuard = shutdown.indexOf("if (sharedIndexInitialized)");
        int freeAll = shutdown.indexOf("SharedIndexBuffer.freeAll();", initializedGuard);
        int clearInitialized = shutdown.indexOf("sharedIndexInitialized = false;", freeAll);
        assertTrue(initializedGuard >= 0 && freeAll > initializedGuard && clearInitialized > freeAll);
        assertEquals(1, occurrences(runtime, "SharedIndexBuffer.freeAll();"));

        String terminal = between(
                instance,
                "private void completeTerminalShutdown()",
                "private synchronized void closeActiveWorld");
        int terminalGuard = terminal.indexOf("this.terminalCleanupComplete || this.sessionShutdownInProgress");
        int terminalArmed = terminal.indexOf("this.terminalCleanupComplete = true;", terminalGuard);
        int runtimeShutdown = terminal.indexOf("ForgeOriginalVoxyClientRuntime.shutdown();", terminalArmed);
        assertTrue(terminalGuard >= 0 && terminalArmed > terminalGuard && runtimeShutdown > terminalArmed);
        assertEquals(1, occurrences(instance, "ForgeOriginalVoxyClientRuntime.shutdown();"));
    }

    @Test
    void chunkBoundSeedTracksTheActiveOriginalRenderOwnerGeneration() throws IOException {
        String pipeline = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java"));
        String instance = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java"));
        String mixin = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java"));

        String publication = between(
                pipeline,
                "private void startOnRenderThread(long expectedGeneration)",
                "public void renderEmbeddiumCutout");
        int publishSystem = publication.indexOf("this.renderSystem = renderSystem;");
        int ownerReady = publication.indexOf("this.ownerReady = true;", publishSystem);
        int generation = publication.indexOf("this.chunkBoundOwnerGeneration++;", ownerReady);
        int replay = publication.indexOf(
                "this.replayPendingChunkBoundSections(renderSystem.chunkBoundRenderer());",
                generation);
        assertTrue(publishSystem >= 0 && ownerReady > publishSystem);
        assertTrue(generation > ownerReady && replay > generation);
        assertEquals(1, occurrences(pipeline, "this.chunkBoundOwnerGeneration++;"));

        String generationGetter = between(
                pipeline,
                "synchronized long chunkBoundOwnerGeneration()",
                "synchronized boolean hasActiveRenderOwner()");
        assertTrue(generationGetter.contains("this.ownerReady && !this.stale && this.renderSystem != null"));
        assertTrue(generationGetter.contains("? this.chunkBoundOwnerGeneration"));
        assertTrue(generationGetter.contains(": -1L;"));

        String delegate = between(
                instance,
                "public long originalVoxyChunkBoundOwnerGeneration()",
                "public void trackOriginalVoxyChunkBoundSection");
        assertTrue(delegate.contains("pipeline == null ? -1L : pipeline.chunkBoundOwnerGeneration()"));

        assertTrue(mixin.contains("private long voxy$chunkBoundSeededOwnerGeneration = -1L;"));
        assertTrue(mixin.contains("@Inject(method = \"renderLayer\", at = @At(\"HEAD\"))"));
        String seed = between(
                mixin,
                "private void voxy$seedChunkBoundTracker",
                "private void voxy$ingestOnChunkAdd");
        assertTrue(seed.contains("renderPass != DefaultTerrainRenderPasses.CUTOUT"));
        int readGeneration = seed.indexOf("originalVoxyChunkBoundOwnerGeneration()");
        int sameGenerationGuard = seed.indexOf(
                "this.voxy$chunkBoundSeededOwnerGeneration == ownerGeneration",
                readGeneration);
        int reset = seed.indexOf("resetOriginalVoxyChunkBoundTracker()", sameGenerationGuard);
        int snapshot = seed.indexOf("for (RenderSection section : this.sectionByPosition.values())", reset);
        int recordGeneration = seed.indexOf(
                "this.voxy$chunkBoundSeededOwnerGeneration = ownerGeneration;",
                snapshot);
        assertTrue(readGeneration >= 0 && sameGenerationGuard > readGeneration);
        assertTrue(reset > sameGenerationGuard && snapshot > reset && recordGeneration > snapshot);
        assertFalse(seed.contains("if (seeded == 0)"));
        assertFalse(mixin.contains("@Inject(method = \"update\", at = @At(\"TAIL\"))"));
    }

    @Test
    void processedMixinConfigurationLoadsTheLevelSwitchHookOwner() throws IOException {
        try (InputStream stream = ForgeVoxyInstanceSessionLifecycleTest.class
                .getClassLoader()
                .getResourceAsStream("voxy.forge.mixins.json")) {
            assertNotNull(stream);
            String processedConfig = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            JsonArray clientMixins = JsonParser.parseString(processedConfig)
                    .getAsJsonObject()
                    .getAsJsonArray("client");
            assertNotNull(clientMixins);
            boolean levelSwitchConfigured = false;
            boolean chunkBoundSeedConfigured = false;
            for (JsonElement mixin : clientMixins) {
                if ("ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin".equals(mixin.getAsString())) {
                    levelSwitchConfigured = true;
                }
                if ("ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin".equals(mixin.getAsString())) {
                    chunkBoundSeedConfigured = true;
                }
            }
            assertTrue(levelSwitchConfigured);
            assertTrue(chunkBoundSeedConfigured);
        }

        try (InputStream stream = ForgeVoxyInstanceSessionLifecycleTest.class
                .getClassLoader()
                .getResourceAsStream("META-INF/mods.toml")) {
            assertNotNull(stream);
            String processedMetadata = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(processedMetadata.matches(
                    "(?s).*\\[\\[mixins]]\\s*config\\s*=\\s*\"voxy\\.forge\\.mixins\\.json\".*"));
        }
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertTrue(startIndex >= 0, "missing source anchor: " + start);
        assertTrue(endIndex > startIndex, "missing source anchor: " + end);
        return source.substring(startIndex, endIndex);
    }

    private static int occurrences(String source, String target) {
        return (source.length() - source.replace(target, "").length()) / target.length();
    }

    private record EqualValue(int value) {
    }
}
