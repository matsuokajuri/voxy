package me.cortex.voxy.forge.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest {
    @Test
    void levelSwitchHookTargetsOriginalSetLevelHeadBoundary() throws ReflectiveOperationException {
        Field oldLevel = ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.class
                .getDeclaredField("level");
        assertTrue(Modifier.isPrivate(oldLevel.getModifiers()));
        assertEquals(ClientLevel.class, oldLevel.getType());
        assertNotNull(oldLevel.getAnnotation(Shadow.class));

        Method hook = ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.class.getDeclaredMethod(
                "voxy$shutdownOriginalRendererForLevelSwitch",
                ClientLevel.class,
                CallbackInfo.class);
        assertTrue(Modifier.isPrivate(hook.getModifiers()));
        Inject injection = hook.getAnnotation(Inject.class);
        assertNotNull(injection);
        assertArrayEquals(new String[]{"setLevel"}, injection.method());
        assertEquals(1, injection.at().length);
        assertEquals("HEAD", injection.at()[0].value());
    }

    @Test
    void viewportPredicateIsPrivateStaticAndMatchesTheRuntimeContract() throws ReflectiveOperationException {
        Method predicate = ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.class.getDeclaredMethod(
                "voxy$shouldResetViewport",
                boolean.class,
                boolean.class,
                boolean.class);
        assertTrue(Modifier.isPrivate(predicate.getModifiers()));
        assertTrue(Modifier.isStatic(predicate.getModifiers()));
        predicate.setAccessible(true);

        assertTrue(invoke(predicate, true, true, false));
        assertFalse(invoke(predicate, false, true, false));
        assertFalse(invoke(predicate, true, false, false));
        assertFalse(invoke(predicate, true, true, true));
    }

    private static boolean invoke(Method predicate, boolean live, boolean shaderpack, boolean shadow)
            throws ReflectiveOperationException {
        return (boolean) predicate.invoke(null, live, shaderpack, shadow);
    }
}
