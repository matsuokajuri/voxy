package me.cortex.voxy.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class ForgeOriginalVoxyQuadMaterialBridge {
    private static final String BAKED_QUAD_VIEW_CLASS = "me.jellysquid.mods.sodium.client.model.quad.BakedQuadView";
    private static final String SPRITE_TRANSPARENCY_HOLDER_CLASS = "org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevelHolder";

    private static final Class<?> BAKED_QUAD_VIEW;
    private static final Method GET_X;
    private static final Method GET_Y;
    private static final Method GET_Z;
    private static final Method GET_TEX_U;
    private static final Method GET_TEX_V;
    private static final Method GET_COLOR_INDEX;
    private static final Method GET_SPRITE;
    private static final Method HAS_SHADE;
    private static final Method GET_TRANSPARENCY_LEVEL;

    static {
        try {
            BAKED_QUAD_VIEW = Class.forName(BAKED_QUAD_VIEW_CLASS);
            GET_X = BAKED_QUAD_VIEW.getMethod("getX", int.class);
            GET_Y = BAKED_QUAD_VIEW.getMethod("getY", int.class);
            GET_Z = BAKED_QUAD_VIEW.getMethod("getZ", int.class);
            GET_TEX_U = BAKED_QUAD_VIEW.getMethod("getTexU", int.class);
            GET_TEX_V = BAKED_QUAD_VIEW.getMethod("getTexV", int.class);
            GET_COLOR_INDEX = BAKED_QUAD_VIEW.getMethod("getColorIndex");
            GET_SPRITE = BAKED_QUAD_VIEW.getMethod("getSprite");
            HAS_SHADE = BAKED_QUAD_VIEW.getMethod("hasShade");
            Class<?> holder = Class.forName(SPRITE_TRANSPARENCY_HOLDER_CLASS);
            GET_TRANSPARENCY_LEVEL = holder.getMethod("getTransparencyLevel", Class.forName("net.minecraft.client.renderer.texture.SpriteContents"));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ForgeOriginalVoxyQuadMaterialBridge() {
    }

    static QuadMaterialData read(BakedQuad quad, RenderType renderType, boolean forceSolid) {
        if (!BAKED_QUAD_VIEW.isInstance(quad)) {
            throw new IllegalStateException("embeddium-baked-quad-view-missing");
        }
        if (!isKnownRenderLayer(renderType)) {
            throw new IllegalArgumentException("unsupported-render-layer:" + renderType);
        }
        Object view = BAKED_QUAD_VIEW.cast(quad);
        int metadata = forceSolid || renderType == RenderType.solid() ? 0 : 1;
        if (intValue(GET_COLOR_INDEX, view) != -1) {
            metadata |= 4;
        }
        TextureAtlasSprite sprite = (TextureAtlasSprite) invoke(GET_SPRITE, view);
        float[] x = new float[4];
        float[] y = new float[4];
        float[] z = new float[4];
        float[] u = new float[4];
        float[] v = new float[4];
        for (int i = 0; i < 4; i++) {
            x[i] = floatValue(GET_X, view, i);
            y[i] = floatValue(GET_Y, view, i);
            z[i] = floatValue(GET_Z, view, i);
            u[i] = floatValue(GET_TEX_U, view, i);
            v[i] = floatValue(GET_TEX_V, view, i);
        }
        return new QuadMaterialData(
                x,
                y,
                z,
                u,
                v,
                metadata,
                booleanValue(HAS_SHADE, view),
                usesEmbeddiumDarkCutoutEquivalent(sprite)
        );
    }

    static boolean isTranslucentLayer(RenderType renderType) {
        return renderType == RenderType.translucent();
    }

    static boolean isDiscardLayer(RenderType renderType) {
        if (renderType == RenderType.solid()) {
            return false;
        }
        if (renderType == RenderType.cutout()
                || renderType == RenderType.cutoutMipped()
                || renderType == RenderType.tripwire()
                || renderType == RenderType.translucent()) {
            return true;
        }
        throw new IllegalArgumentException("unsupported-render-layer:" + renderType);
    }

    private static boolean isKnownRenderLayer(RenderType renderType) {
        return renderType == RenderType.solid()
                || renderType == RenderType.cutout()
                || renderType == RenderType.cutoutMipped()
                || renderType == RenderType.tripwire()
                || renderType == RenderType.translucent();
    }

    private static boolean usesEmbeddiumDarkCutoutEquivalent(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null || Minecraft.getInstance().options.mipmapLevels().get() <= 0) {
            return false;
        }
        Object contents = sprite.contents();
        Object transparencyLevel = invoke(GET_TRANSPARENCY_LEVEL, null, contents);
        if (transparencyLevel == null || "OPAQUE".equals(String.valueOf(transparencyLevel))) {
            return false;
        }
        ResourceLocation name = spriteName(contents);
        return name != null && name.getPath().contains("leaves");
    }

    @Nullable
    private static ResourceLocation spriteName(Object contents) {
        try {
            Method method = contents.getClass().getMethod("name");
            return (ResourceLocation) method.invoke(contents);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("sprite-contents-name-missing", e);
        }
    }

    private static float floatValue(Method method, Object target, int vertex) {
        return ((Number) invoke(method, target, vertex)).floatValue();
    }

    private static int intValue(Method method, Object target) {
        return ((Number) invoke(method, target)).intValue();
    }

    private static boolean booleanValue(Method method, Object target) {
        return (Boolean) invoke(method, target);
    }

    private static Object invoke(Method method, @Nullable Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("embeddium-quad-view-inaccessible", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("embeddium-quad-view-invocation-failed", cause);
        }
    }

    record QuadMaterialData(
            float[] x,
            float[] y,
            float[] z,
            float[] u,
            float[] v,
            int metadata,
            boolean shaded,
            boolean darkenedTexture
    ) {
    }
}
