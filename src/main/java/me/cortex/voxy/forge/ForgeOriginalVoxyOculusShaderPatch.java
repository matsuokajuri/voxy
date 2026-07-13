package me.cortex.voxy.forge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import org.lwjgl.opengl.ARBDrawBuffersBlend;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntSupplier;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_DST_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_DST_COLOR;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_DST_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_DST_COLOR;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_COLOR;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA_SATURATE;
import static org.lwjgl.opengl.GL11C.GL_SRC_COLOR;
import static org.lwjgl.opengl.GL11C.GL_ZERO;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL30C.glDisablei;
import static org.lwjgl.opengl.GL30C.glEnablei;
import static org.lwjgl.opengl.GL33C.GL_ONE_MINUS_SRC1_ALPHA;
import static org.lwjgl.opengl.GL33C.GL_ONE_MINUS_SRC1_COLOR;
import static org.lwjgl.opengl.GL33C.GL_SRC1_COLOR;

public final class ForgeOriginalVoxyOculusShaderPatch {
    public static final int VERSION = ((IntSupplier) () -> 1).getAsInt();
    public static final int SHADER_DEFINE_VERSION = 2;

    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .setLenient()
            .create();

    private final PatchGson patchData;
    private final ShaderPack pack;
    private final Int2ObjectMap<String> ssbos;

    private ForgeOriginalVoxyOculusShaderPatch(PatchGson patchData, ShaderPack pack) {
        this.patchData = patchData;
        this.pack = pack;
        this.ssbos = patchData.ssbos == null ? new Int2ObjectOpenHashMap<>() : patchData.ssbos;
    }

    public static ForgeOriginalVoxyOculusShaderPatch makePatch(
            ShaderPack ipack,
            AbsolutePackPath directory,
            Function<AbsolutePackPath, String> sourceProvider) {
        String voxyPatchData = readPatchDataSource(directory, sourceProvider);
        if (voxyPatchData == null) {
            return null;
        }

        PatchGson patchData;
        try {
            patchData = parsePatchData(voxyPatchData);

            String opaque = sourceProvider.apply(directory.resolve("voxy_opaque.glsl"));
            if (opaque != null) {
                VoxyForge.LOGGER.info("External Voxy opaque shader patch applied from Oculus shaderpack.");
                patchData.opaquePatchData = opaque;
            }
            String translucent = sourceProvider.apply(directory.resolve("voxy_translucent.glsl"));
            if (translucent != null) {
                VoxyForge.LOGGER.info("External Voxy translucent shader patch applied from Oculus shaderpack.");
                patchData.translucentPatchData = translucent;
            }
            String taa = sourceProvider.apply(directory.resolve("voxy_taa.glsl"));
            if (taa != null) {
                VoxyForge.LOGGER.info("External Voxy TAA shader patch applied from Oculus shaderpack.");
                patchData.taaOffset = taa;
            }

            String invalidPatchDataReason = patchData.checkValid();
            if (invalidPatchDataReason != null) {
                throw new IllegalStateException("voxy json patch not valid: " + invalidPatchDataReason);
            }
        } catch (RuntimeException e) {
            throw malformedPatch(voxyPatchData, e);
        }

        if (patchData.version != VERSION) {
            throw new ForgeOriginalVoxyShaderLoadError(
                    "Shader has Voxy patch data, but patch version is incorrect. expected "
                            + VERSION + " got " + patchData.version,
                    null);
        }
        return new ForgeOriginalVoxyOculusShaderPatch(patchData, ipack);
    }

    public static Set<Integer> collectRequestedRenderTargets(
            AbsolutePackPath directory,
            Function<AbsolutePackPath, String> sourceProvider) {
        String voxyPatchData = readPatchDataSource(directory, sourceProvider);
        if (voxyPatchData == null) {
            return Set.of();
        }
        PatchGson patchData;
        try {
            patchData = parsePatchData(voxyPatchData);
        } catch (RuntimeException e) {
            throw malformedPatch(voxyPatchData, e);
        }
        Set<Integer> targets = new LinkedHashSet<>();
        collectDrawBufferTargets(patchData.opaqueDrawBuffers, targets);
        collectDrawBufferTargets(patchData.translucentDrawBuffers, targets);
        if (patchData.samplers != null) {
            for (String sampler : patchData.samplers.keySet()) {
                int target = parseColortexTarget(sampler);
                if (target >= 0) {
                    targets.add(target);
                }
            }
        }
        return targets;
    }

    private static ForgeOriginalVoxyShaderLoadError malformedPatch(String patchData, RuntimeException cause) {
        VoxyForge.LOGGER.error("Failed to parse Voxy Oculus shaderpack patch data; dumping JSON.", cause);
        try {
            Files.writeString(Path.of("JSON_DUMP.txt"), patchData);
        } catch (IOException dumpFailure) {
            cause.addSuppressed(dumpFailure);
        }
        return new ForgeOriginalVoxyShaderLoadError(
                "Failed to parse Voxy shaderpack patch data; dumped JSON_DUMP.txt",
                cause);
    }

    private static String readPatchDataSource(
            AbsolutePackPath directory,
            Function<AbsolutePackPath, String> sourceProvider) {
        String voxyPatchData = sourceProvider.apply(directory.resolve("voxy.json"));
        return voxyPatchData == null || voxyPatchData.isBlank() ? null : voxyPatchData;
    }

    private static PatchGson parsePatchData(String voxyPatchData) {
        voxyPatchData = voxyPatchData.replace("\\", "\\\\");
        StringBuilder builder = new StringBuilder(voxyPatchData.length());
        for (String line : voxyPatchData.split("\n")) {
            int idx = line.indexOf("//");
            if (idx != -1) {
                builder.append(line, 0, idx);
                builder.append(line.substring(idx).replace("\"", "\\\""));
            } else {
                builder.append(line);
            }
            builder.append('\n');
        }
        voxyPatchData = builder.toString();
        voxyPatchData = voxyPatchData.replaceAll("void _cfi_ignoreMarker\\(\\) \\{\\}", "");

        PatchGson patchData = GSON.fromJson(voxyPatchData, PatchGson.class);
        if (patchData == null) {
            throw new IllegalStateException("Voxy shaderpack patch json returned null");
        }
        return patchData;
    }

    private static void collectDrawBufferTargets(int[] drawBuffers, Set<Integer> targets) {
        if (drawBuffers == null) {
            return;
        }
        for (int target : drawBuffers) {
            targets.add(target);
        }
    }

    private static int parseColortexTarget(String sampler) {
        String prefix = "colortex";
        if (sampler == null || !sampler.startsWith(prefix)) {
            return -1;
        }
        try {
            return Integer.parseInt(sampler.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public ShaderPack pack() {
        return this.pack;
    }

    public boolean useViewportDims() {
        return this.patchData.useViewportDims;
    }

    public boolean skipShaderDepthHackFix() {
        return this.patchData.skipShaderDepthHackFix;
    }

    public Int2ObjectMap<String> getSSBOs() {
        return new Int2ObjectLinkedOpenHashMap<>(this.ssbos);
    }

    public String getPatchOpaqueSource() {
        return this.patchData.opaquePatchData;
    }

    public String getPatchTranslucentSource() {
        return this.patchData.translucentPatchData;
    }

    public String getTAAShift() {
        return this.patchData.taaOffset;
    }

    public String[] getUniformList() {
        return this.patchData.uniforms;
    }

    public Object2ObjectLinkedOpenHashMap<String, String> getSamplerSet() {
        return this.patchData.samplers;
    }

    public int[] getOpqaueTargets() {
        return this.patchData.opaqueDrawBuffers;
    }

    public int[] getOpaqueTargets() {
        return this.patchData.opaqueDrawBuffers;
    }

    public int[] getTranslucentTargets() {
        return this.patchData.translucentDrawBuffers;
    }

    public boolean emitToVanillaDepth() {
        return !this.patchData.excludeLodsFromVanillaDepth;
    }

    public float[] getRenderScale() {
        if (this.patchData.renderScale == null || this.patchData.renderScale.length == 0) {
            return new float[]{1.0F, 1.0F};
        }
        if (this.patchData.renderScale.length == 1) {
            return new float[]{this.patchData.renderScale[0], this.patchData.renderScale[0]};
        }
        return new float[]{
                Math.max(0.01F, this.patchData.renderScale[0]),
                Math.max(0.01F, this.patchData.renderScale[1])
        };
    }

    public boolean deferedTranslucentRendering() {
        return false;
    }

    public Runnable createBlendSetup() {
        if (this.patchData.blending == null || this.patchData.blending.isEmpty()) {
            return () -> {
            };
        }
        return () -> {
            Int2ObjectOpenHashMap<BlendState> states = this.patchData.blending;
            BlendState init = states.getOrDefault(-1, null);
            if (init != null) {
                if (init.off) {
                    glDisable(GL_BLEND);
                } else {
                    glEnable(GL_BLEND);
                    glBlendFuncSeparate(init.sRGB, init.dRGB, init.sA, init.dA);
                }
            }
            for (var entry : states.int2ObjectEntrySet()) {
                if (entry.getIntKey() == -1) {
                    continue;
                }
                BlendState state = entry.getValue();
                if (state.off) {
                    glDisablei(GL_BLEND, state.buffer);
                } else {
                    glEnablei(GL_BLEND, state.buffer);
                    ARBDrawBuffersBlend.glBlendFuncSeparateiARB(
                            state.buffer,
                            state.sRGB,
                            state.dRGB,
                            state.sA,
                            state.dA);
                }
            }
        };
    }

    public record BlendState(int buffer, boolean off, int sRGB, int dRGB, int sA, int dA) {
        public static final BlendState ALL_OFF = new BlendState(-1, true, 0, 0, 0, 0);
    }

    private static final class SSBODeserializer implements JsonDeserializer<Int2ObjectOpenHashMap<String>> {
        @Override
        public Int2ObjectOpenHashMap<String> deserialize(
                JsonElement json,
                Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            if (json == null) {
                return null;
            }
            Int2ObjectOpenHashMap<String> ret = new Int2ObjectOpenHashMap<>();
            try {
                for (var entry : json.getAsJsonObject().entrySet()) {
                    ret.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
                }
            } catch (RuntimeException e) {
                VoxyForge.LOGGER.error("Failed to parse Voxy Oculus SSBO shaderpack mapping.", e);
            }
            return ret;
        }
    }

    private static final class SamplerDeserializer
            implements JsonDeserializer<Object2ObjectLinkedOpenHashMap<String, String>> {
        @Override
        public Object2ObjectLinkedOpenHashMap<String, String> deserialize(
                JsonElement json,
                Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            if (json == null) {
                return null;
            }
            Object2ObjectLinkedOpenHashMap<String, String> ret = new Object2ObjectLinkedOpenHashMap<>();
            try {
                if (json.isJsonArray()) {
                    for (JsonElement entry : json.getAsJsonArray()) {
                        String name = entry.getAsString();
                        ret.put(name, defaultSamplerType(name));
                    }
                } else {
                    for (var entry : json.getAsJsonObject().entrySet()) {
                        String type = entry.getValue().isJsonNull()
                                ? defaultSamplerType(entry.getKey())
                                : entry.getValue().getAsString();
                        ret.put(entry.getKey(), type);
                    }
                }
            } catch (RuntimeException e) {
                VoxyForge.LOGGER.error("Failed to parse Voxy Oculus sampler shaderpack mapping.", e);
            }
            return ret;
        }

        private static String defaultSamplerType(String name) {
            return name.matches("shadowtex") ? "sampler2DShadow" : "sampler2D";
        }
    }

    private static final class BlendStateDeserializer implements JsonDeserializer<Int2ObjectOpenHashMap<BlendState>> {
        @Override
        public Int2ObjectOpenHashMap<BlendState> deserialize(
                JsonElement json,
                Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            if (json == null) {
                return null;
            }
            Int2ObjectOpenHashMap<BlendState> ret = new Int2ObjectOpenHashMap<>();
            try {
                if (json.isJsonPrimitive()) {
                    if ("off".equalsIgnoreCase(json.getAsString())) {
                        ret.put(-1, BlendState.ALL_OFF);
                    }
                    return ret;
                }
                if (json.isJsonObject()) {
                    for (var entry : json.getAsJsonObject().entrySet()) {
                        int buffer = Integer.parseInt(entry.getKey());
                        BlendState state = parseBlendState(buffer, entry.getValue());
                        ret.put(buffer, state);
                    }
                    return ret;
                }
            } catch (RuntimeException e) {
                VoxyForge.LOGGER.error("Failed to parse Voxy Oculus blend shaderpack mapping.", e);
            }
            VoxyForge.LOGGER.error("Failed to parse Voxy Oculus blend state: {}", json);
            return ret;
        }

        private static BlendState parseBlendState(int buffer, JsonElement value) {
            List<String> blendParts = null;
            if (value.isJsonArray()) {
                blendParts = new ArrayList<>();
                for (JsonElement element : value.getAsJsonArray()) {
                    blendParts.add(element.getAsString());
                }
            } else if (value.isJsonPrimitive()) {
                String str = value.getAsString();
                if ("off".equalsIgnoreCase(str)) {
                    return new BlendState(buffer, true, 0, 0, 0, 0);
                }
                String[] parts = str.split(" ");
                if (parts.length < 4) {
                    return new BlendState(buffer, true, -1, -1, -1, -1);
                }
                blendParts = List.of(parts);
            } else {
                VoxyForge.LOGGER.error("Unknown Voxy Oculus blend state value {}", value);
                return null;
            }

            if (blendParts.size() < 4) {
                return new BlendState(buffer, true, -1, -1, -1, -1);
            }
            int[] values = blendParts.stream().mapToInt(BlendStateDeserializer::parseType).toArray();
            return new BlendState(buffer, false, values[0], values[1], values[2], values[3]);
        }

        private static int parseType(String type) {
            String normalized = type.toUpperCase();
            if (!normalized.startsWith("GL_")) {
                normalized = "GL_" + normalized;
            }
            return switch (normalized) {
                case "GL_ZERO" -> GL_ZERO;
                case "GL_ONE" -> GL_ONE;
                case "GL_SRC_COLOR" -> GL_SRC_COLOR;
                case "GL_ONE_MINUS_SRC_COLOR" -> GL_ONE_MINUS_SRC_COLOR;
                case "GL_SRC_ALPHA" -> GL_SRC_ALPHA;
                case "GL_ONE_MINUS_SRC_ALPHA" -> GL_ONE_MINUS_SRC_ALPHA;
                case "GL_DST_ALPHA" -> GL_DST_ALPHA;
                case "GL_ONE_MINUS_DST_ALPHA" -> GL_ONE_MINUS_DST_ALPHA;
                case "GL_DST_COLOR" -> GL_DST_COLOR;
                case "GL_ONE_MINUS_DST_COLOR" -> GL_ONE_MINUS_DST_COLOR;
                case "GL_SRC_ALPHA_SATURATE" -> GL_SRC_ALPHA_SATURATE;
                case "GL_SRC1_COLOR" -> GL_SRC1_COLOR;
                case "GL_ONE_MINUS_SRC1_COLOR" -> GL_ONE_MINUS_SRC1_COLOR;
                case "GL_ONE_MINUS_SRC1_ALPHA" -> GL_ONE_MINUS_SRC1_ALPHA;
                default -> {
                    VoxyForge.LOGGER.error("Unknown Voxy Oculus blend option {}", type);
                    yield -1;
                }
            };
        }
    }

    private static final class PatchGson {
        public int version;
        public int[] opaqueDrawBuffers;
        public int[] translucentDrawBuffers;
        public String[] uniforms;
        @JsonAdapter(SamplerDeserializer.class)
        public Object2ObjectLinkedOpenHashMap<String, String> samplers;
        public String opaquePatchData;
        public String translucentPatchData;
        @JsonAdapter(SSBODeserializer.class)
        public Int2ObjectOpenHashMap<String> ssbos;
        @JsonAdapter(BlendStateDeserializer.class)
        public Int2ObjectOpenHashMap<BlendState> blending;
        public String taaOffset;
        public boolean excludeLodsFromVanillaDepth;
        public float[] renderScale;
        public boolean useViewportDims;
        public boolean skipShaderDepthHackFix;

        public String checkValid() {
            if (this.opaquePatchData == null) {
                return "Opaque patch data is null";
            }
            if (this.uniforms == null) {
                return "Uniforms are null";
            }
            if (this.opaqueDrawBuffers == null) {
                return "Opaque draw buffers are null";
            }
            if (this.translucentDrawBuffers == null) {
                return "Translucent draw buffers are null";
            }
            if (this.blending != null) {
                int i = 0;
                for (BlendState state : this.blending.values()) {
                    if (state != null && state.buffer != -1
                            && (state.buffer < 0 || this.translucentDrawBuffers.length <= state.buffer)) {
                        if (state.buffer < 0) {
                            return "Blending buffer is <0 at index: " + i;
                        }
                        return "Blending buffer index out of bounds at " + i
                                + " was " + state.buffer
                                + " maximum is " + (this.translucentDrawBuffers.length - 1);
                    }
                    i++;
                }
            }
            return null;
        }
    }
}
