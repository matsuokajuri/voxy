package me.cortex.voxy.forge;

import net.minecraft.client.resources.model.BakedModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForgeSoftwareModelTextureBakeryTest {
    @Test
    void customRendererIsAnIntentionalEmptyModelButMissingModelRemainsFatal() {
        assertEquals(
                ForgeSoftwareModelTextureBakery.ModelRoute.MISSING,
                ForgeSoftwareModelTextureBakery.classifyModel(null));
        assertEquals(
                ForgeSoftwareModelTextureBakery.ModelRoute.CUSTOM_RENDERER_EMPTY,
                ForgeSoftwareModelTextureBakery.classifyModel(modelWithCustomRenderer(true)));
        assertEquals(
                ForgeSoftwareModelTextureBakery.ModelRoute.BAKED_QUADS,
                ForgeSoftwareModelTextureBakery.classifyModel(modelWithCustomRenderer(false)));
    }

    private static BakedModel modelWithCustomRenderer(boolean customRenderer) {
        return (BakedModel) Proxy.newProxyInstance(
                BakedModel.class.getClassLoader(),
                new Class<?>[]{BakedModel.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("isCustomRenderer")) {
                        return customRenderer;
                    }
                    throw new AssertionError("Unexpected BakedModel method: " + method.getName());
                });
    }
}
