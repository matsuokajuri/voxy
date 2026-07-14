package me.cortex.voxy.forge;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

final class ForgeOriginalVoxyQuadMaterialBridge {
    private static final String BAKED_QUAD_VIEW_CLASS = "me.jellysquid.mods.sodium.client.model.quad.BakedQuadView";

    private static final Class<?> BAKED_QUAD_VIEW;
    private static final Method GET_X;
    private static final Method GET_Y;
    private static final Method GET_Z;
    private static final Method GET_TEX_U;
    private static final Method GET_TEX_V;
    private static final Method GET_COLOR_INDEX;
    private static final Method GET_SPRITE;
    private static final Method HAS_SHADE;
    private static final Set<RenderType> WARNED_UNKNOWN_RENDER_LAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<SpriteContents, String> TRANSPARENCY_LEVELS =
            Collections.synchronizedMap(new WeakHashMap<>());

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
        Object view = BAKED_QUAD_VIEW.cast(quad);
        TextureAtlasSprite sprite = (TextureAtlasSprite) invoke(GET_SPRITE, view);
        MaterialLayer layer = materialLayer(renderType, sprite);
        int metadata = discardMetadata(layer, forceSolid);
        if (intValue(GET_COLOR_INDEX, view) != -1) {
            metadata |= 4;
        }
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

    static boolean isTranslucentLayer(BakedQuad quad, RenderType renderType) {
        if (!BAKED_QUAD_VIEW.isInstance(quad)) {
            throw new IllegalStateException("embeddium-baked-quad-view-missing");
        }
        TextureAtlasSprite sprite = (TextureAtlasSprite) invoke(GET_SPRITE, BAKED_QUAD_VIEW.cast(quad));
        // Original Voxy chooses the translucent consumer from materialInfo().layer() before
        // passing forceSolid to ReuseVertexConsumer; forceSolid only removes the discard bit.
        return routesToTranslucent(materialLayer(renderType, sprite));
    }

    private static MaterialLayer materialLayer(
            RenderType renderType,
            @Nullable TextureAtlasSprite sprite) {
        if (renderType == RenderType.solid()) {
            return MaterialLayer.SOLID;
        }
        if (renderType == RenderType.translucent()) {
            return MaterialLayer.TRANSLUCENT;
        }
        if (renderType == RenderType.cutout()
                || renderType == RenderType.cutoutMipped()
                || renderType == RenderType.tripwire()) {
            return MaterialLayer.CUTOUT;
        }

        // Forge 1.20.1 has no per-quad ChunkSectionLayer equivalent to the materialInfo()
        // owner used by current original Voxy. Embeddium's own default material mapper only
        // recognizes the five vanilla chunk layers, so a third-party BakedModel that exposes a
        // custom RenderType previously made the entire Voxy model bake return empty. Preserve
        // the original per-quad classification shape by deriving the least-lossy layer from
        // Embeddium's existing per-sprite transparency analysis.
        warnCustomRenderLayer(renderType, "per-quad sprite transparency");
        return fallbackMaterialLayer(transparencyLevel(sprite));
    }

    static MaterialLayer fallbackMaterialLayer(String transparency) {
        if ("TRANSLUCENT".equals(transparency)) {
            return MaterialLayer.TRANSLUCENT;
        }
        if ("TRANSPARENT".equals(transparency)) {
            return MaterialLayer.CUTOUT;
        }
        return MaterialLayer.SOLID;
    }

    static boolean routesToTranslucent(MaterialLayer layer) {
        return layer == MaterialLayer.TRANSLUCENT;
    }

    static int discardMetadata(MaterialLayer layer, boolean forceSolid) {
        return forceSolid || layer == MaterialLayer.SOLID ? 0 : 1;
    }

    static MaterialLayer materialLayerForFluid(
            RenderType renderType,
            TextureAtlasSprite[] sprites,
            int tintColor) {
        if (renderType == RenderType.solid()) {
            return MaterialLayer.SOLID;
        }
        if (renderType == RenderType.translucent()) {
            return MaterialLayer.TRANSLUCENT;
        }
        if (renderType == RenderType.cutout()
                || renderType == RenderType.cutoutMipped()
                || renderType == RenderType.tripwire()) {
            return MaterialLayer.CUTOUT;
        }

        String[] transparency = new String[sprites == null ? 0 : sprites.length];
        for (int i = 0; i < transparency.length; i++) {
            transparency[i] = transparencyLevel(sprites[i]);
        }
        warnCustomRenderLayer(renderType, "fluid sprite/tint transparency");
        return fallbackFluidMaterialLayer(
                transparency,
                tintColor >>> 24,
                String.valueOf(renderType));
    }

    static MaterialLayer fallbackFluidMaterialLayer(
            String[] spriteTransparency,
            int tintAlpha,
            String renderTypeDescription) {
        if (tintAlpha < 255) {
            return MaterialLayer.TRANSLUCENT;
        }
        MaterialLayer spriteLayer = MaterialLayer.SOLID;
        for (String transparency : spriteTransparency) {
            MaterialLayer layer = fallbackMaterialLayer(transparency);
            if (layer == MaterialLayer.TRANSLUCENT) {
                return layer;
            }
            if (layer == MaterialLayer.CUTOUT) {
                spriteLayer = layer;
            }
        }
        String description = renderTypeDescription == null
                ? ""
                : renderTypeDescription.toUpperCase(Locale.ROOT);
        if (description.contains("TRANSLUCENT") || description.contains("ADDITIVE")) {
            return MaterialLayer.TRANSLUCENT;
        }
        if (description.contains("CUTOUT") || description.contains("TRIPWIRE")) {
            return MaterialLayer.CUTOUT;
        }
        return spriteLayer;
    }

    private static void warnCustomRenderLayer(RenderType renderType, String evidence) {
        if (WARNED_UNKNOWN_RENDER_LAYERS.add(renderType)) {
            VoxyForge.LOGGER.warn(
                    "Classifying custom Forge render layer {} from {} for Voxy LOD baking.",
                    renderType,
                    evidence);
        }
    }

    private static String transparencyLevel(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null) {
            return "OPAQUE";
        }
        return TRANSPARENCY_LEVELS.computeIfAbsent(
                sprite.contents(),
                ForgeOriginalVoxyQuadMaterialBridge::computeTransparencyLevel);
    }

    private static String computeTransparencyLevel(SpriteContents contents) {
        NativeImage image = contents.getOriginalImage();
        int width = image.getWidth();
        int pixelCount = width * image.getHeight();
        return classifyTransparency(
                pixelCount,
                pixelIndex -> FastColor.ABGR32.alpha(
                        image.getPixelRGBA(pixelIndex % width, pixelIndex / width)));
    }

    // Embeddium 0.3.32 caches this exact three-level classification on SpriteContents. The
    // declared minimum 0.3.31 runtime predates that internal API, so the Forge adapter mirrors
    // its alpha contract locally and caches the result weakly for the atlas lifetime.
    static String classifyTransparency(int pixelCount, IntUnaryOperator alphaAt) {
        boolean hasTransparentPixel = false;
        for (int pixelIndex = 0; pixelIndex < pixelCount; pixelIndex++) {
            int alpha = alphaAt.applyAsInt(pixelIndex);
            if (alpha > 0 && alpha < 255) {
                return "TRANSLUCENT";
            }
            if (alpha == 0) {
                hasTransparentPixel = true;
            }
        }
        return hasTransparentPixel ? "TRANSPARENT" : "OPAQUE";
    }

    private static boolean usesEmbeddiumDarkCutoutEquivalent(@Nullable TextureAtlasSprite sprite) {
        if (sprite == null || Minecraft.getInstance().options.mipmapLevels().get() <= 0) {
            return false;
        }
        SpriteContents contents = sprite.contents();
        if ("OPAQUE".equals(transparencyLevel(sprite))) {
            return false;
        }
        ResourceLocation name = spriteName(contents);
        return name != null && name.getPath().contains("leaves");
    }

    enum MaterialLayer {
        SOLID,
        CUTOUT,
        TRANSLUCENT
    }

    private static ResourceLocation spriteName(SpriteContents contents) {
        return contents.name();
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
