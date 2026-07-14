package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ForgeOriginalVoxyQuadMaterialBridgeTest {
    @Test
    void transparencyClassificationMatchesEmbeddium032AlphaContract() {
        assertEquals(
                "OPAQUE",
                ForgeOriginalVoxyQuadMaterialBridge.classifyTransparency(3, pixel -> 255));
        assertEquals(
                "TRANSPARENT",
                ForgeOriginalVoxyQuadMaterialBridge.classifyTransparency(
                        3,
                        pixel -> pixel == 1 ? 0 : 255));
        assertEquals(
                "TRANSLUCENT",
                ForgeOriginalVoxyQuadMaterialBridge.classifyTransparency(
                        3,
                        pixel -> pixel == 1 ? 127 : 255));
        assertEquals(
                "TRANSLUCENT",
                ForgeOriginalVoxyQuadMaterialBridge.classifyTransparency(
                        3,
                        pixel -> pixel == 0 ? 0 : pixel == 1 ? 1 : 255));
        assertEquals(
                "OPAQUE",
                ForgeOriginalVoxyQuadMaterialBridge.classifyTransparency(0, pixel -> 0));
    }

    @Test
    void lowestSupportedEmbeddiumDoesNotRequirePost031TransparencyApi() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java"));

        assertFalse(source.contains("SpriteTransparencyLevelHolder"));
        assertFalse(source.contains("org.embeddedt.embeddium.impl.render.chunk.sprite"));
        assertTrue(source.contains("contents.getOriginalImage()"));
    }

    @Test
    void customRenderLayersUseEmbeddiumSpriteTransparencyClassification() {
        assertEquals(
                ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer.SOLID,
                ForgeOriginalVoxyQuadMaterialBridge.fallbackMaterialLayer("OPAQUE"));
        assertEquals(
                ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer.CUTOUT,
                ForgeOriginalVoxyQuadMaterialBridge.fallbackMaterialLayer("TRANSPARENT"));
        assertEquals(
                ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer.TRANSLUCENT,
                ForgeOriginalVoxyQuadMaterialBridge.fallbackMaterialLayer("TRANSLUCENT"));
        assertEquals(
                ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer.SOLID,
                ForgeOriginalVoxyQuadMaterialBridge.fallbackMaterialLayer("UNKNOWN"));

        var translucent = ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer.TRANSLUCENT;
        assertEquals(true, ForgeOriginalVoxyQuadMaterialBridge.routesToTranslucent(translucent));
        assertEquals(1, ForgeOriginalVoxyQuadMaterialBridge.discardMetadata(translucent, false));
        assertEquals(0, ForgeOriginalVoxyQuadMaterialBridge.discardMetadata(translucent, true));

        assertEquals(
                ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer.TRANSLUCENT,
                ForgeOriginalVoxyQuadMaterialBridge.fallbackFluidMaterialLayer(
                        new String[]{"OPAQUE"}, 127, "custom"));
        assertEquals(
                ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer.TRANSLUCENT,
                ForgeOriginalVoxyQuadMaterialBridge.fallbackFluidMaterialLayer(
                        new String[]{"OPAQUE"}, 255, "custom_translucent"));
        assertEquals(
                ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer.CUTOUT,
                ForgeOriginalVoxyQuadMaterialBridge.fallbackFluidMaterialLayer(
                        new String[]{"TRANSPARENT"}, 255, "custom"));
        assertEquals(
                ForgeOriginalVoxyQuadMaterialBridge.MaterialLayer.SOLID,
                ForgeOriginalVoxyQuadMaterialBridge.fallbackFluidMaterialLayer(
                        new String[]{"OPAQUE"}, 255, "custom"));
    }
}
