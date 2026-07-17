package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeCompatibilityDebtHardeningTest {
    @Test
    void installedOculusContractsUseGuardedDirectTypedAccess() throws IOException {
        String worldSettings = source(
                "src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java");
        String shadowState = source(
                "src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java");

        assertTrue(worldSettings.contains("WorldRenderingSettings.INSTANCE.getBlockStateIds()"));
        assertTrue(worldSettings.contains("WorldRenderingSettings.INSTANCE.isReloadRequired()"));
        assertTrue(shadowState.contains("ShadowRenderer.ACTIVE"));
        assertTrue(worldSettings.indexOf("ForgeOculusAvailability.installed()")
                < worldSettings.indexOf("WorldRenderingSettings.INSTANCE.getBlockStateIds()"));
        assertTrue(shadowState.indexOf("ForgeOculusAvailability.installed()")
                < shadowState.indexOf("ShadowRenderer.ACTIVE"));
        assertFalse(worldSettings.contains("Class.forName"));
        assertFalse(shadowState.contains("Class.forName"));
    }

    @Test
    void optionalMixinContractsCannotSilentlyMissInstalledTargets() throws IOException {
        String chunky = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java");
        String acedium = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java");

        assertEquals(2, occurrences(chunky, "@Group(name = \"voxy$chunkyGetOrScheduleFuture\", min = 1)"));
        assertTrue(acedium.contains(
                "renderFrame(Lme/jellysquid/mods/sodium/client/render/viewport/Viewport;"));
        assertTrue(acedium.contains(
                "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderMatrices;DDD)V"));
        assertTrue(acedium.contains("cameraX,\n                cameraY,\n                cameraZ"));
        assertFalse(acedium.contains("viewport.getTransform()"));
        assertTrue(acedium.contains("require = 1"));
    }

    @Test
    void acediumForwardsItsExactEmbeddiumFrustumToVoxyTraversal() throws IOException {
        String acedium = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java");
        String viewportAccessor = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumViewportAccessor.java");
        String simpleFrustumAccessor = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumSimpleFrustumAccessor.java");
        String modelPipeline = source(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java");
        String mdicViewport = source("src/main/java/me/cortex/voxy/forge/MDICViewport.java");
        String mixins = source("src/main/resources/voxy.forge.mixins.json");
        String traversal = source(
                "src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp");

        assertTrue(viewportAccessor.contains("@Accessor(\"frustum\")"));
        assertTrue(simpleFrustumAccessor.contains("FrustumIntersection voxy$getFrustumIntersection()"));
        assertTrue(mixins.contains("ForgeOriginalVoxyEmbeddiumViewportAccessor"));
        assertTrue(mixins.contains("ForgeOriginalVoxyEmbeddiumSimpleFrustumAccessor"));
        assertTrue(acedium.contains("frustum instanceof SimpleFrustum"));
        assertTrue(acedium.contains(".voxy$getFrustumIntersection()"));
        assertTrue(modelPipeline.contains("viewport.copyFrustumFrom(suppliedFrustum)"));
        assertTrue(mdicViewport.contains("this.frustumPlanes[i].set(sourcePlanes[i])"));
        assertTrue(traversal.contains("if (outsideFrustum() || isCulledByHiz())"));
    }

    @Test
    void embeddiumBuilderThreadPolicyUsesOriginalAccessorOwnership() throws IOException {
        String policy = source(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java");
        String accessor = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumWorldRendererAccessor.java");
        String mixins = source("src/main/resources/voxy.forge.mixins.json");

        assertFalse(policy.contains("Class.forName"));
        assertFalse(policy.contains("java.lang.reflect"));
        assertTrue(policy.contains("SodiumWorldRenderer.instanceNullable()"));
        assertTrue(policy.contains("ForgeOriginalVoxyEmbeddiumWorldRendererAccessor"));
        assertTrue(accessor.contains("@Accessor(\"renderSectionManager\")"));
        assertTrue(mixins.contains("ForgeOriginalVoxyEmbeddiumWorldRendererAccessor"));
    }

    @Test
    void bobbyReforgedKeepsOriginalIdentityAndUnloadSplit() throws IOException {
        String clientCache = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java");
        String renderManager = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java");

        assertTrue(clientCache.contains("BOBBY_REFORGED_INSTALLED = ModList.get().isLoaded(\"bobby\")"));
        assertTrue(renderManager.contains("BOBBY_REFORGED_INSTALLED = ModList.get().isLoaded(\"bobby\")"));
        assertTrue(clientCache.contains("if (!VOXY$BOBBY_REFORGED_INSTALLED"));
        assertTrue(renderManager.contains("if (VOXY_BOBBY_REFORGED_INSTALLED"));
    }

    @Test
    void vivecraftApiShapeIsValidatedBeforeUse() throws ReflectiveOperationException {
        ForgeVivecraftRenderPassBridge.ApiMethods methods =
                ForgeVivecraftRenderPassBridge.inspectApiType(CompatibleVivecraftApi.class);

        assertEquals("instance", methods.instanceMethod().getName());
        assertEquals("getCurrentRenderPass", methods.currentRenderPassMethod().getName());
        assertEquals(TestRenderPass.class, methods.renderPassType());
        assertThrows(
                ReflectiveOperationException.class,
                () -> ForgeVivecraftRenderPassBridge.inspectApiType(IncompatibleVivecraftApi.class));
    }

    @Test
    void vivecraftAbiFailureSkipsInsteadOfSharingTheVanillaViewport() throws IOException {
        String bridge = source("src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java");
        String selector = source("src/main/java/me/cortex/voxy/forge/ViewportSelector.java");
        String pipeline = source("src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java");

        assertTrue(bridge.contains("ModList.get().isLoaded(VIVECRAFT_MOD_ID)"));
        assertTrue(bridge.contains("RenderPassSelection.incompatible()"));
        assertTrue(bridge.contains("getCurrentRenderPass() must return the RenderPass enum"));
        assertTrue(selector.contains("if (vivecraft.skipVoxy())"));
        assertTrue(selector.contains("VIVECRAFT_API_SKIPPED_KEY"));
        assertTrue(pipeline.contains("selector.lastSelectionWasExpectedSkip()"));
    }

    private static int occurrences(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    public static final class CompatibleVivecraftApi {
        public static CompatibleVivecraftApi instance() {
            return new CompatibleVivecraftApi();
        }

        public TestRenderPass getCurrentRenderPass() {
            return TestRenderPass.VANILLA;
        }
    }

    public static final class IncompatibleVivecraftApi {
        public IncompatibleVivecraftApi instance() {
            return this;
        }

        public void getCurrentRenderPass() {
        }
    }

    enum TestRenderPass {
        LEFT,
        RIGHT,
        VANILLA
    }
}
