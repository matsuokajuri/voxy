package me.cortex.voxy.forge;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import kroppeb.stareval.expression.Expression;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import me.cortex.voxy.forge.mixin.ForgeOriginalVoxyOculusCustomUniformsAccessor;
import me.cortex.voxy.forge.mixin.ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.image.ImageHolder;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.gl.uniform.DynamicLocationalUniformHolder;
import net.irisshaders.iris.gl.uniform.LocationalUniformHolder;
import net.irisshaders.iris.gl.uniform.Uniform;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformType;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.uniforms.custom.cached.BooleanCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Float2VectorCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Float3VectorCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Float4MatrixCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Float4VectorCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.FloatCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Int2VectorCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.Int3VectorCachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.IntCachedUniform;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_1D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_RECTANGLE;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL45C.glBindTextureUnit;

public final class ForgeOriginalVoxyOculusRenderPipelineData {
    public static final int UNIFORM_BINDING_POINT = 7;
    public static final int BUFFER_BINDING_INDEX_BASE = 10;
    public static final int BASE_SAMPLER_BINDING_INDEX = 6;

    static int textureTarget(TextureType type) {
        if (type == TextureType.TEXTURE_1D) {
            return GL_TEXTURE_1D;
        }
        if (type == TextureType.TEXTURE_2D) {
            return GL_TEXTURE_2D;
        }
        if (type == TextureType.TEXTURE_3D) {
            return GL_TEXTURE_3D;
        }
        if (type == TextureType.TEXTURE_RECTANGLE) {
            return GL_TEXTURE_RECTANGLE;
        }
        throw new IllegalArgumentException("Unsupported Oculus texture type: " + type);
    }

    private Object boundPipeline;
    public final int[] opaqueDrawTargets;
    public final int[] translucentDrawTargets;
    private final String opaquePatch;
    private final String translucentPatch;
    private final StructLayout uniforms;
    private final Runnable blendingSetup;
    private final ImageSet imageSet;
    private final SSBOSet ssboSet;
    public final boolean renderToVanillaDepth;
    public final float[] resolutionScale;
    public final String TAA;
    public final boolean useViewportDims;
    public final boolean deferTranslucency;
    public final boolean skipShaderDepthHackFix;

    private ForgeOriginalVoxyOculusRenderPipelineData(
            ForgeOriginalVoxyOculusShaderPatch patch,
            int[] opaqueDrawTargets,
            int[] translucentDrawTargets,
            StructLayout uniformSet,
            Runnable blendingSetup,
            ImageSet imageSet,
            SSBOSet ssboSet) {
        this.opaqueDrawTargets = opaqueDrawTargets;
        this.translucentDrawTargets = translucentDrawTargets;
        this.opaquePatch = patch.getPatchOpaqueSource();
        this.translucentPatch = patch.getPatchTranslucentSource();
        this.uniforms = uniformSet;
        this.blendingSetup = blendingSetup;
        this.imageSet = imageSet;
        this.ssboSet = ssboSet;
        this.renderToVanillaDepth = patch.emitToVanillaDepth();
        this.TAA = patch.getTAAShift();
        this.resolutionScale = patch.getRenderScale();
        this.useViewportDims = patch.useViewportDims();
        this.deferTranslucency = patch.deferedTranslucentRendering();
        this.skipShaderDepthHackFix = patch.skipShaderDepthHackFix();
    }

    public static ForgeOriginalVoxyOculusRenderPipelineData buildPipeline(
            IrisRenderingPipeline irisPipeline,
            ForgeOriginalVoxyOculusShaderPatch patch,
            CustomUniforms customUniforms,
            FrameUpdateNotifier updateNotifier,
            ShaderStorageBufferHolder ssboHolder) {
        StructLayout uniforms = createUniformLayoutStructAndUpdater(createUniformSet(customUniforms, updateNotifier, patch));
        ImageSet imageSet = createImageSet(irisPipeline, patch);
        SSBOSet ssboSet = createSSBOLayouts(patch.getSSBOs(), ssboHolder);
        RenderTargets renderTargets = ((ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor) irisPipeline)
                .voxy$getRenderTargets();
        int[] opaqueDrawTargets = getDrawBuffers(
                patch.getOpqaueTargets(),
                irisPipeline.getFlippedAfterPrepare(),
                renderTargets);
        int[] translucentDrawTargets = getDrawBuffers(
                patch.getTranslucentTargets(),
                irisPipeline.getFlippedAfterPrepare(),
                renderTargets);
        return new ForgeOriginalVoxyOculusRenderPipelineData(
                patch,
                opaqueDrawTargets,
                translucentDrawTargets,
                uniforms,
                patch.createBlendSetup(),
                imageSet,
                ssboSet);
    }

    public void bindPipeline(Object pipeline) {
        if (this.boundPipeline != null) {
            throw new IllegalStateException("Oculus Voxy pipeline data already bound.");
        }
        this.boundPipeline = pipeline;
    }

    public void unbindPipeline(Object pipeline) {
        if (this.boundPipeline != pipeline) {
            throw new IllegalStateException("Oculus Voxy pipeline data bound to a different pipeline.");
        }
        this.boundPipeline = null;
    }

    public StructLayout getUniforms() {
        return this.uniforms;
    }

    public Runnable getBlender() {
        return this.blendingSetup;
    }

    public ImageSet getImageSet() {
        return this.imageSet;
    }

    public SSBOSet getSsboSet() {
        return this.ssboSet;
    }

    public String opaqueFragPatch() {
        return this.opaquePatch;
    }

    public String translucentFragPatch() {
        return this.translucentPatch;
    }

    int opaqueDepthTextureId() {
        if (this.boundPipeline instanceof ForgeOriginalVoxyRenderPipeline pipeline) {
            return pipeline.oculusOpaqueDepthTextureId();
        }
        return 0;
    }

    int translucentDepthTextureId() {
        if (this.boundPipeline instanceof ForgeOriginalVoxyRenderPipeline pipeline) {
            return pipeline.oculusTranslucentDepthTextureId();
        }
        return 0;
    }

    public boolean shouldDeferTranslucency() {
        return false;
    }

    public boolean hasUniforms() {
        return this.uniforms != null;
    }

    public boolean hasImages() {
        return this.imageSet != null;
    }

    public boolean hasSsbos() {
        return this.ssboSet != null;
    }

    public boolean hasBlendSetup() {
        return this.blendingSetup != null;
    }

    public boolean hasTaa() {
        return this.TAA != null;
    }

    private static int[] getDrawBuffers(int[] targets, ImmutableSet<Integer> stageWritesToAlt, RenderTargets renderTargets) {
        int[] targetTextures = new int[targets.length];
        for (int i = 0; i < targets.length; i++) {
            RenderTarget target = renderTargets.getOrCreate(targets[i]);
            targetTextures[i] = stageWritesToAlt.contains(targets[i])
                    ? target.getAltTexture()
                    : target.getMainTexture();
        }
        return targetTextures;
    }

    private static String convertToGlslType(UniformType type) {
        return switch (type) {
            case INT -> "int";
            case FLOAT -> "float";
            case MAT3 -> "mat3";
            case MAT4 -> "mat4";
            case VEC2 -> "vec2";
            case VEC2I -> "ivec2";
            case VEC3 -> "vec3";
            case VEC3I -> "ivec3";
            case VEC4 -> "vec4";
            case VEC4I -> "ivec4";
        };
    }

    public record StructLayout(int size, String layout, LongConsumer updater) {
    }

    private static StructLayout createUniformLayoutStructAndUpdater(List<UniformWritingHolder> uniforms) {
        if (uniforms.isEmpty()) {
            return null;
        }

        List<UniformWritingHolder>[] ordering = new List[]{
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        };
        for (UniformWritingHolder uniform : uniforms) {
            ordering[getUniformOrdering(uniform.type)].add(uniform);
        }

        int pos = 0;
        Int2ObjectLinkedOpenHashMap<UniformWritingHolder> layout = new Int2ObjectLinkedOpenHashMap<>();
        for (UniformWritingHolder uniform : ordering[0]) {
            layout.put(pos, uniform);
            pos += getSizeAndAlignment(uniform.type) >> 5;
        }
        if (!ordering[1].isEmpty() && (ordering[1].size() & 1) == 0) {
            for (UniformWritingHolder uniform : ordering[1]) {
                layout.put(pos, uniform);
                pos += getSizeAndAlignment(uniform.type) >> 5;
            }
            ordering[1].clear();
        }
        for (UniformWritingHolder uniform : ordering[2]) {
            layout.put(pos, uniform);
            pos += getSizeAndAlignment(uniform.type) >> 5;
            if (!ordering[3].isEmpty()) {
                UniformWritingHolder paddingUniform = ordering[3].remove(0);
                layout.put(pos, paddingUniform);
                pos += getSizeAndAlignment(paddingUniform.type) >> 5;
            } else {
                pos += 1;
            }
        }
        for (UniformWritingHolder uniform : ordering[1]) {
            layout.put(pos, uniform);
            pos += getSizeAndAlignment(uniform.type) >> 5;
        }
        for (UniformWritingHolder uniform : ordering[3]) {
            layout.put(pos, uniform);
            pos += getSizeAndAlignment(uniform.type) >> 5;
        }
        if (layout.size() != uniforms.size()) {
            throw new IllegalStateException("Oculus Voxy shader uniform layout lost entries.");
        }

        StringBuilder struct = new StringBuilder("{\n");
        for (var pair : layout.int2ObjectEntrySet()) {
            struct.append('\t')
                    .append(convertToGlslType(pair.getValue().type))
                    .append(' ')
                    .append(pair.getValue().name)
                    .append(";\n");
        }
        struct.append('}');

        LongConsumer[] updaters = new LongConsumer[uniforms.size()];
        int i = 0;
        for (var pair : layout.int2ObjectEntrySet()) {
            updaters[i++] = pair.getValue().writingFactory.get(pair.getIntKey() * 4L);
        }

        LongConsumer updater = ptr -> {
            for (LongConsumer uniformUpdater : updaters) {
                uniformUpdater.accept(ptr);
            }
        };
        return new StructLayout(pos * 4, struct.toString(), updater);
    }

    private static LongConsumer createWriter(long offset, FunctionReturn ret, CachedUniform uniform) {
        if (uniform instanceof BooleanCachedUniform bcu) {
            return ptr -> {
                ptr += offset;
                bcu.writeTo(ret);
                MemoryUtil.memPutInt(ptr, ret.booleanReturn ? 1 : 0);
            };
        }
        if (uniform instanceof FloatCachedUniform fcu) {
            return ptr -> {
                ptr += offset;
                fcu.writeTo(ret);
                MemoryUtil.memPutFloat(ptr, ret.floatReturn);
            };
        }
        if (uniform instanceof IntCachedUniform icu) {
            return ptr -> {
                ptr += offset;
                icu.writeTo(ret);
                MemoryUtil.memPutInt(ptr, ret.intReturn);
            };
        }
        if (uniform instanceof Float2VectorCachedUniform v2fcu) {
            return ptr -> {
                ptr += offset;
                v2fcu.writeTo(ret);
                ((Vector2f) ret.objectReturn).getToAddress(ptr);
            };
        }
        if (uniform instanceof Float3VectorCachedUniform v3fcu) {
            return ptr -> {
                ptr += offset;
                v3fcu.writeTo(ret);
                ((Vector3f) ret.objectReturn).getToAddress(ptr);
            };
        }
        if (uniform instanceof Float4VectorCachedUniform v4fcu) {
            return ptr -> {
                ptr += offset;
                v4fcu.writeTo(ret);
                ((Vector4f) ret.objectReturn).getToAddress(ptr);
            };
        }
        if (uniform instanceof Int2VectorCachedUniform v2icu) {
            return ptr -> {
                ptr += offset;
                v2icu.writeTo(ret);
                ((Vector2i) ret.objectReturn).getToAddress(ptr);
            };
        }
        if (uniform instanceof Int3VectorCachedUniform v3icu) {
            return ptr -> {
                ptr += offset;
                v3icu.writeTo(ret);
                ((Vector3i) ret.objectReturn).getToAddress(ptr);
            };
        }
        if (uniform instanceof Float4MatrixCachedUniform f4mcu) {
            return ptr -> {
                ptr += offset;
                f4mcu.writeTo(ret);
                ((Matrix4f) ret.objectReturn).getToAddress(ptr);
            };
        }
        throw new IllegalStateException("Unknown Oculus Voxy shader uniform type " + uniform.getClass().getName());
    }

    private static int packedSizeAndAlignment(int size, int align) {
        return size << 5 | align;
    }

    private static int getSizeAndAlignment(UniformType type) {
        return switch (type) {
            case INT, FLOAT -> packedSizeAndAlignment(1, 1);
            case MAT3 -> packedSizeAndAlignment(4 + 4 + 3, 4);
            case MAT4 -> packedSizeAndAlignment(4 * 4, 4);
            case VEC2, VEC2I -> packedSizeAndAlignment(2, 2);
            case VEC3, VEC3I -> packedSizeAndAlignment(3, 4);
            case VEC4, VEC4I -> packedSizeAndAlignment(4, 4);
        };
    }

    private static int getUniformOrdering(UniformType type) {
        return switch (type) {
            case MAT4, VEC4, VEC4I -> 0;
            case VEC2, VEC2I -> 1;
            case VEC3, VEC3I, MAT3 -> 2;
            case INT, FLOAT -> 3;
        };
    }

    private record UniformWritingHolder(
            String name,
            UniformType type,
            Long2ObjectFunction<LongConsumer> writingFactory) {
    }

    private static List<UniformWritingHolder> createUniformSet(
            CustomUniforms customUniforms,
            FrameUpdateNotifier updateNotifier,
            ForgeOriginalVoxyOculusShaderPatch patch) {
        List<UniformWritingHolder> uniforms = new ArrayList<>();
        Set<String> seenUniforms = new HashSet<>();
        DynamicLocationalUniformHolder uniformBuilder = new DynamicLocationalUniformHolder() {
            @Override
            public DynamicLocationalUniformHolder uniform1i(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    IntSupplier value) {
                return this.uniform1i(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform1i(
                    String name,
                    IntSupplier value,
                    ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.INT,
                        offset -> ptr -> MemoryUtil.memPutInt(ptr + offset, value.getAsInt()));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform1f(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    net.irisshaders.iris.gl.uniform.FloatSupplier value) {
                return this.uniform1f(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform1f(
                    String name,
                    net.irisshaders.iris.gl.uniform.FloatSupplier value,
                    ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.FLOAT,
                        offset -> ptr -> MemoryUtil.memPutFloat(ptr + offset, value.getAsFloat()));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform1f(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    IntSupplier value) {
                return this.uniform1f(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform1f(
                    String name,
                    IntSupplier value,
                    ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.FLOAT,
                        offset -> ptr -> MemoryUtil.memPutFloat(ptr + offset, value.getAsInt()));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform1f(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    DoubleSupplier value) {
                return this.uniform1f(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform1f(
                    String name,
                    DoubleSupplier value,
                    ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.FLOAT,
                        offset -> ptr -> MemoryUtil.memPutFloat(ptr + offset, (float) value.getAsDouble()));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform1b(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    BooleanSupplier value) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.INT,
                        offset -> ptr -> MemoryUtil.memPutInt(ptr + offset, value.getAsBoolean() ? 1 : 0));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform2f(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    Supplier<Vector2f> value) {
                return this.uniform2f(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform2f(
                    String name,
                    Supplier<Vector2f> value,
                    ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.VEC2,
                        offset -> ptr -> value.get().getToAddress(ptr + offset));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform2i(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    Supplier<Vector2i> value) {
                return this.uniform2i(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform2i(
                    String name,
                    Supplier<Vector2i> value,
                    ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.VEC2I,
                        offset -> ptr -> value.get().getToAddress(ptr + offset));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform3f(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    Supplier<Vector3f> value) {
                return this.uniform3f(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform3f(
                    String name,
                    Supplier<Vector3f> value,
                    ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.VEC3,
                        offset -> ptr -> value.get().getToAddress(ptr + offset));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform3i(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    Supplier<Vector3i> value) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.VEC3I,
                        offset -> ptr -> value.get().getToAddress(ptr + offset));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniformTruncated3f(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    Supplier<Vector4f> value) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.VEC3,
                        offset -> ptr -> {
                            Vector4f vector = value.get();
                            MemoryUtil.memPutFloat(ptr + offset, vector.x());
                            MemoryUtil.memPutFloat(ptr + offset + 4L, vector.y());
                            MemoryUtil.memPutFloat(ptr + offset + 8L, vector.z());
                        });
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform3d(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    Supplier<Vector3d> value) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.VEC3,
                        offset -> ptr -> {
                            Vector3d vector = value.get();
                            MemoryUtil.memPutFloat(ptr + offset, (float) vector.x());
                            MemoryUtil.memPutFloat(ptr + offset + 4L, (float) vector.y());
                            MemoryUtil.memPutFloat(ptr + offset + 8L, (float) vector.z());
                        });
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform4f(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    Supplier<Vector4f> value) {
                return this.uniform4f(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform4f(
                    String name,
                    Supplier<Vector4f> value,
                    ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.VEC4,
                        offset -> ptr -> value.get().getToAddress(ptr + offset));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform4fArray(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    Supplier<float[]> value) {
                return this.uniform4fArray(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniform4fArray(
                    String name,
                    Supplier<float[]> value,
                    ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.VEC4,
                        offset -> ptr -> {
                            float[] vector = value.get();
                            for (int i = 0; i < 4; i++) {
                                MemoryUtil.memPutFloat(ptr + offset + i * 4L, i < vector.length ? vector[i] : 0.0F);
                            }
                        });
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniform4i(
                    String name,
                    Supplier<Vector4i> value,
                    ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.VEC4I,
                        offset -> ptr -> value.get().getToAddress(ptr + offset));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniformMatrix(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    Supplier<Matrix4f> value) {
                return this.uniformMatrix(name, value, null);
            }

            @Override
            public DynamicLocationalUniformHolder uniformMatrix(
                    String name,
                    Supplier<Matrix4f> value,
                    ValueUpdateNotifier notifier) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.MAT4,
                        offset -> ptr -> value.get().getToAddress(ptr + offset));
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder uniformMatrixFromArray(
                    UniformUpdateFrequency updateFrequency,
                    String name,
                    Supplier<float[]> value) {
                this.injectDynamicUniformType(
                        name,
                        UniformType.MAT4,
                        offset -> ptr -> {
                            float[] matrix = value.get();
                            for (int i = 0; i < 16; i++) {
                                MemoryUtil.memPutFloat(ptr + offset + i * 4L, i < matrix.length ? matrix[i] : 0.0F);
                            }
                        });
                return this;
            }

            @Override
            public DynamicLocationalUniformHolder addDynamicUniform(
                    Uniform uniform,
                    ValueUpdateNotifier valueUpdateNotifier) {
                throw new IllegalStateException("Type not implemented for Voxy Oculus shader uniform: " + uniform);
            }

            @Override
            public LocationalUniformHolder addUniform(UniformUpdateFrequency updateFrequency, Uniform uniform) {
                return this;
            }

            @Override
            public OptionalInt location(String uniformName, UniformType uniformType) {
                String[] names = patch.getUniformList();
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(uniformName)) {
                        return OptionalInt.of(i);
                    }
                }
                return OptionalInt.empty();
            }

            @Override
            public UniformHolder externallyManagedUniform(String name, UniformType uniformType) {
                return null;
            }

            private void injectDynamicUniformType(
                    String name,
                    UniformType type,
                    Long2ObjectFunction<LongConsumer> supplier) {
                String[] names = patch.getUniformList();
                for (String expectedName : names) {
                    if (expectedName.equals(name)) {
                        if (!seenUniforms.add(name)) {
                            throw new IllegalArgumentException("Already added Voxy Oculus shader uniform: " + name);
                        }
                        uniforms.add(new UniformWritingHolder(name, type, supplier));
                        break;
                    }
                }
            }
        };

        ForgeOriginalVoxyOculusVoxyUniforms.addUniforms(uniformBuilder);
        CameraUniforms.addCameraUniforms(uniformBuilder, updateNotifier);
        CommonUniforms.addDynamicUniforms(uniformBuilder, FogMode.PER_FRAGMENT);
        customUniforms.assignTo(uniformBuilder);
        customUniforms.mapholderToPass(uniformBuilder, patch);

        FunctionReturn cachedReturn = new FunctionReturn();
        Object2IntMap<CachedUniform> customLocationMap =
                ((ForgeOriginalVoxyOculusCustomUniformsAccessor) customUniforms).getLocationMap().get(patch);
        if (customLocationMap != null) {
            customLocationMap.object2IntEntrySet().forEach(entry -> {
                CachedUniform cachedUniform = entry.getKey();
                if (!seenUniforms.add(cachedUniform.getName())) {
                    return;
                }
                uniforms.add(new UniformWritingHolder(
                        cachedUniform.getName(),
                        Type.convert(cachedUniform.getType()),
                        offset -> createWriter(offset, cachedReturn, cachedUniform)));
            });
        }
        for (String uniformName : patch.getUniformList()) {
            if (seenUniforms.contains(uniformName) || !customUniforms.hasVariable(uniformName)) {
                continue;
            }
            Expression variable = customUniforms.getVariable(uniformName);
            if (variable instanceof CachedUniform cachedUniform && seenUniforms.add(uniformName)) {
                uniforms.add(new UniformWritingHolder(
                        cachedUniform.getName(),
                        Type.convert(cachedUniform.getType()),
                        offset -> {
                            LongConsumer writer = createWriter(offset, cachedReturn, cachedUniform);
                            return ptr -> {
                                cachedUniform.update();
                                writer.accept(ptr);
                            };
                        }));
            }
        }

        if (uniforms.size() != patch.getUniformList().length) {
            Set<String> uniformsUnseen = new HashSet<>(List.of(patch.getUniformList()));
            for (UniformWritingHolder uniform : uniforms) {
                uniformsUnseen.remove(uniform.name);
            }
            VoxyForge.LOGGER.error(
                    "The following Voxy Oculus shader uniforms could not be found: [{}]",
                    uniformsUnseen.stream().sorted(String::compareToIgnoreCase).collect(Collectors.joining(",")));
        }
        return uniforms;
    }

    private static final class TextureWithSampler {
        private final String name;
        private final int textureTarget;
        private final IntSupplier texture;
        private final IntSupplier sampler;
        private final boolean requiredNonZeroTexture;
        private int lastTextureId = -1;

        private TextureWithSampler(
                String name,
                int textureTarget,
                IntSupplier texture,
                IntSupplier sampler,
                boolean requiredNonZeroTexture) {
            this.name = name;
            this.textureTarget = textureTarget;
            this.texture = texture;
            this.sampler = sampler;
            this.requiredNonZeroTexture = requiredNonZeroTexture;
        }

        private String name() {
            return this.name;
        }

        private int textureId() {
            this.lastTextureId = this.texture.getAsInt();
            return this.lastTextureId;
        }

        private String failureReason() {
            return this.requiredNonZeroTexture && this.lastTextureId == 0
                    ? "oculus-image-binding-texture-zero:" + this.name
                    : "none";
        }
    }

    public static final class ImageSet {
        private final String layout;
        private final TextureWithSampler[] samplers;
        private final int[] textureTargets;
        private final IntConsumer bindingFunction;
        private String lastFailureReason = "none";
        private boolean textureZeroLogged;

        private ImageSet(String layout, TextureWithSampler[] samplers) {
            this.layout = layout;
            this.samplers = samplers;
            this.textureTargets = new int[samplers.length];
            for (int i = 0; i < samplers.length; i++) {
                this.textureTargets[i] = samplers[i].textureTarget;
            }
            this.bindingFunction = this::bind;
        }

        public String layout() {
            return this.layout;
        }

        public IntConsumer bindingFunction() {
            return this.bindingFunction;
        }

        public int bindingCount() {
            return this.samplers.length;
        }

        String lastFailureReason() {
            return this.lastFailureReason;
        }

        ForgeOriginalVoxyTextureBindings.Binding[] captureNon2DTextureBindings(int base) {
            return ForgeOriginalVoxyTextureBindings.captureNon2D(base, this.textureTargets);
        }

        private void bind(int base) {
            this.lastFailureReason = "none";
            for (int j = 0; j < this.samplers.length; j++) {
                int unit = j + base;
                TextureWithSampler sampler = this.samplers[j];
                int textureId = sampler.textureId();
                if (sampler.requiredNonZeroTexture && textureId == 0) {
                    this.lastFailureReason = sampler.failureReason();
                    if (!this.textureZeroLogged) {
                        this.textureZeroLogged = true;
                        VoxyForge.LOGGER.warn(
                                "Voxy Oculus shaderpack sampler '{}' resolved to texture id 0; this image binding is unavailable.",
                                sampler.name);
                    }
                }
                if (textureId == 0) {
                    //glBindTextureUnit(unit, 0) clears every target on the unit. Oculus retains the
                    //TextureType for dynamic samplers, so clear only that target and leave unrelated
                    //Embeddium/mod bindings intact for the outer state guard to restore.
                    ForgeOriginalVoxyTextureBindings.bind(unit, sampler.textureTarget, 0);
                } else {
                    glBindTextureUnit(unit, textureId);
                }
                int samplerId = sampler.sampler.getAsInt();
                glBindSampler(unit, samplerId == -1 ? 0 : samplerId);
            }
        }
    }

    private static ImageSet createImageSet(
            IrisRenderingPipeline irisPipeline,
            ForgeOriginalVoxyOculusShaderPatch patch) {
        var samplerDataSet = patch.getSamplerSet();
        if (samplerDataSet == null) {
            return null;
        }
        Set<String> samplerNameSet = new LinkedHashSet<>(samplerDataSet.keySet());
        if (samplerNameSet.isEmpty()) {
            return null;
        }
        Set<TextureWithSampler> samplerSet = new LinkedHashSet<>();
        Map<String, IntSupplier> externalTextures = new HashMap<>();
        externalTextures.put("lightmap", ForgeOriginalVoxyRenderStateCapture::lightTextureId);

        SamplerHolder samplerBuilder = new SamplerHolder() {
            @Override
            public boolean hasSampler(String name) {
                return samplerNameSet.contains(name);
            }

            public boolean hasSampler(String... names) {
                for (String name : names) {
                    if (samplerNameSet.contains(name)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean addDefaultSampler(
                    TextureType type,
                    IntSupplier texture,
                    ValueUpdateNotifier notifier,
                    GlSampler sampler,
                    String... names) {
                VoxyForge.LOGGER.error("Unsupported Voxy Oculus default sampler request.");
                return false;
            }

            @Override
            public boolean addDynamicSampler(
                    TextureType type,
                    IntSupplier texture,
                    GlSampler sampler,
                    String... names) {
                return this.addDynamicSampler(type, texture, null, sampler, names);
            }

            @Override
            public boolean addDynamicSampler(
                    TextureType type,
                    IntSupplier texture,
                    ValueUpdateNotifier notifier,
                    GlSampler sampler,
                    String... names) {
                if (!this.hasSampler(names)) {
                    return false;
                }
                samplerSet.add(new TextureWithSampler(
                        this.name(names),
                        textureTarget(type),
                        texture,
                        sampler != null ? sampler::getId : () -> -1,
                        false));
                return true;
            }

            @Override
            public void addExternalSampler(int texture, String... names) {
                if (!this.hasSampler(names)) {
                    return;
                }
                String name = this.name(names);
                IntSupplier externalTexture = externalTextures.get(name);
                //Oculus 1.8's external-sampler API carries no TextureType; those bindings are the
                //2D lightmap/gbuffer contract. Dynamic samplers above retain their explicit type.
                if (externalTexture != null) {
                    samplerSet.add(new TextureWithSampler(
                            name,
                            textureTarget(TextureType.TEXTURE_2D),
                            externalTexture,
                            () -> 0,
                            true));
                } else {
                    samplerSet.add(new TextureWithSampler(
                            name,
                            textureTarget(TextureType.TEXTURE_2D),
                            () -> texture,
                            () -> -1,
                            false));
                }
            }

            private String name(String... names) {
                for (String name : names) {
                    if (samplerNameSet.contains(name)) {
                        return name;
                    }
                }
                return null;
            }
        };

        ImageHolder imageBuilder = new ImageHolder() {
            @Override
            public boolean hasImage(String name) {
                return false;
            }

            @Override
            public void addTextureImage(IntSupplier texture, InternalTextureFormat internalTextureFormat, String name) {
            }
        };

        irisPipeline.addGbufferOrShadowSamplers(
                samplerBuilder,
                imageBuilder,
                irisPipeline::getFlippedAfterPrepare,
                false,
                true,
                true,
                false);

        if (samplerSet.size() != samplerNameSet.size()) {
            VoxyForge.LOGGER.error(
                    "Did not find all requested Voxy Oculus samplers. Found [{}] expected {}",
                    samplerSet.stream().map(TextureWithSampler::name).collect(Collectors.joining(", ")),
                    samplerNameSet);
        }

        StringBuilder builder = new StringBuilder();
        TextureWithSampler[] samplers = new TextureWithSampler[samplerSet.size()];
        int i = 0;
        for (TextureWithSampler entry : samplerSet) {
            samplers[i] = entry;
            String samplerType = samplerDataSet.get(entry.name);
            builder.append("layout(binding=(BASE_SAMPLER_BINDING_INDEX+")
                    .append(i)
                    .append(")) uniform ")
                    .append(samplerType)
                    .append(' ')
                    .append(entry.name)
                    .append(";\n");
            i++;
        }

        return new ImageSet(builder.toString(), samplers);
    }

    public record SSBOSet(String layout, IntConsumer bindingFunction, int bindingCount) {
    }

    private record SSBOBinding(int irisIndex, int bindingOffset) {
    }

    private static SSBOSet createSSBOLayouts(Int2ObjectMap<String> ssbos, ShaderStorageBufferHolder ssboStore) {
        if (ssboStore == null || ssbos.isEmpty()) {
            return null;
        }
        String header = "";
        if (ssbos.containsKey(-1)) {
            header = ssbos.remove(-1);
        }
        StringBuilder builder = new StringBuilder(header);
        builder.append('\n');
        SSBOBinding[] bindings = new SSBOBinding[ssbos.size()];
        int i = 0;
        for (var entry : ssbos.int2ObjectEntrySet()) {
            String val = entry.getValue();
            bindings[i] = new SSBOBinding(entry.getIntKey(), i);
            builder.append("layout(binding = (BUFFER_BINDING_INDEX_BASE+")
                    .append(i)
                    .append(")) restrict buffer IrisBufferBinding")
                    .append(i)
                    .append(' ')
                    .append(val)
                    .append(";\n");
            i++;
        }
        IntConsumer bindingFunction = base -> {
            for (SSBOBinding binding : bindings) {
                glBindBufferBase(GL_SHADER_STORAGE_BUFFER, base + binding.bindingOffset, ssboStore.getBufferIndex(binding.irisIndex));
            }
        };
        return new SSBOSet(builder.toString(), bindingFunction, bindings.length);
    }
}
