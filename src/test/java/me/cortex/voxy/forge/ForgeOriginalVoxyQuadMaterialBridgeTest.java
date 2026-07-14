package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForgeOriginalVoxyQuadMaterialBridgeTest {
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
