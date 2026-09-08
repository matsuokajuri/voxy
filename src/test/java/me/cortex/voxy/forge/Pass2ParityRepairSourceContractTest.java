package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pass2ParityRepairSourceContractTest {
    @Test
    void postDynamicFailuresPropagateToTheRenderBoundary() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java");

        assertTrue(source.contains(
                "this.runOriginalPostDynamicWorkAfterCommandGeneration(postDynamicCameraX, postDynamicCameraZ);"));
        assertFalse(source.contains("original-post-dynamic-"));
    }

    @Test
    void mappedMinecraftMembersUseReobfuscatableDirectAccess() throws IOException {
        String quadBridge = source(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java");
        String renderState = source(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java");
        String modelStore = source("src/main/java/me/cortex/voxy/forge/ModelStore.java");
        String accessTransformer = source("src/main/resources/META-INF/accesstransformer.cfg");

        assertTrue(quadBridge.contains("return contents.name();"));
        assertFalse(quadBridge.contains("getMethod(\"name\")"));
        assertTrue(renderState.contains("return lightTexture.lightTexture.getId();"));
        assertFalse(renderState.contains("getDeclaredField(\"lightTexture\")"));
        assertFalse(renderState.contains("getMethod(\"getId\")"));
        assertTrue(accessTransformer.contains(
                "public net.minecraft.client.renderer.LightTexture f_109870_ # lightTexture"));
        assertTrue(modelStore.contains("return blockAtlas.mipLevel;"));
        assertFalse(modelStore.contains("readIntField"));
        assertFalse(modelStore.contains("maxMipmapLevels"));
        assertTrue(accessTransformer.contains(
                "public net.minecraft.client.renderer.texture.TextureAtlas f_276072_ # mipLevel"));
    }

    @Test
    void shaderLoaderValidatesResourceLocationsAndUsesClasspathOnly() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/ShaderLoader.java");

        assertTrue(source.contains("parseRoot(new ResourceLocation(id))"));
        assertTrue(source.contains(
                "new ResourceLocation(match.group(\"namespace\"), match.group(\"path\"))"));
        assertFalse(source.contains("Files.exists"));
        assertFalse(source.contains("Path.of"));
        assertFalse(source.contains("src\", \"main\", \"resources"));
    }

    @Test
    void rasterizerNoLongerFabricatesTransparentSamples() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java");

        assertFalse(source.contains("this.samplerTexture == null"));
        assertFalse(source.contains("sampler unset"));
        assertTrue(source.contains("return this.samplerTexture[this.samplerWidth * pv + pu];"));
    }

    @Test
    void oculusShadowRejectionPrecedesVivecraftSelection() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/ViewportSelector.java");
        int method = source.indexOf("MDICViewport getViewport()");
        int end = source.indexOf("boolean ready()", method);
        String body = source.substring(method, end);

        int shadow = body.indexOf("ForgeOculusShadowStateBridge.shadowActive()");
        int vivecraft = body.indexOf("ForgeVivecraftRenderPassBridge.currentSelection()");
        assertTrue(shadow >= 0 && vivecraft > shadow);
    }

    @Test
    void worldImporterUsesOriginalMalformedNbtContracts() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/WorldImporter.java");

        assertTrue(source.contains("getIntOrSentinel(chunk, \"xPos\")"));
        assertTrue(source.contains("getIntOrSentinel(chunk, \"zPos\")"));
        assertTrue(source.contains("requireList(chunk, \"sections\")"));
        assertTrue(source.contains("getIntOrSentinel(section, \"Y\")"));

        int biomeDecode = source.indexOf("biomes = this.biomeCodec");
        String biomeEnd = ".orElse(this.defaultBiomeProvider);";
        int biomeEndIndex = source.indexOf(biomeEnd, biomeDecode);
        assertTrue(biomeEndIndex >= biomeDecode);
        // The optional above-section block-state decoder is not part of the biome contract.
        String biomeBlock = source.substring(biomeDecode, biomeEndIndex + biomeEnd.length());
        assertTrue(biomeBlock.contains(".result()"));
        assertFalse(biomeBlock.contains("resultOrPartial"));
    }

    @Test
    void chunkyForgeHookConsumesTheExactFullFutureResult() throws IOException {
        String source = source(
                "src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java");

        assertTrue(source.contains("@Redirect("));
        assertTrue(source.contains("ChunkHolder;getOrScheduleFuture"));
        assertTrue(source.contains("ChunkHolder;m_140049_"));
        assertTrue(source.contains("result.left().ifPresent(chunk ->"));
        assertTrue(source.contains("chunk instanceof LevelChunk levelChunk"));
        assertTrue(source.contains("tryAutoIngestTrustedChunkWithStats(levelChunk)"));
        assertFalse(source.contains("tryAutoIngestChunk(levelChunk)"));
        assertFalse(source.contains("getChunkNow"));
        assertFalse(source.contains("thenRunAsync"));
        assertFalse(source.contains("CallbackInfoReturnable"));
        assertFalse(source.contains("mixinextras"));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
