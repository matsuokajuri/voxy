package me.cortex.voxy.forge;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL46C.*;

/** Actual depth-stage/fullscreen and SSAO owners in the root test's existing GL context. */
final class Round10DepthGpuCases {
    private static final RenderProperties PROPERTIES = new RenderProperties(false, false, false);
    private static final int RED = 180, GREEN = 140, BLUE = 100;

    static void runAll() {
        try {
            verifyProjectionReconstruction();
            verifyActualSsaoModes();
            verifyDepthStencilSetup();
            assertEquals(GL_NO_ERROR, glGetError());
            System.out.println("ROUND10_DEPTH_GPU=d32-to-d24s8-vanilla/LOD-partition; 1:1/scaled/resize; "
                    + "caller-write-mask-independent; SSAO-six-face-flat/NPOT/clear/LOD-vs-vanilla-edge");
        } finally {
            glUseProgram(0);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, 64, 64);
            glDepthMask(true);
            glStencilMask(0xff);
            glColorMask(true, true, true, true);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_STENCIL_TEST);
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_CULL_FACE);
            glDisable(GL_BLEND);
            for (int i = 0; i <= 3; i++) {
                glBindSampler(i, 0);
                glBindTextureUnit(i, 0);
            }
        }
    }

    private static void verifyDepthStencilSetup() {
        checkGl("before constructing DepthStage");
        ForgeOriginalVoxyPipelineDepthStage stage = new ForgeOriginalVoxyPipelineDepthStage(PROPERTIES);
        try {
            checkGl("DepthStage constructor");
            for (int[] sizes : new int[][]{{8, 8, 8, 8}, {9, 7, 9, 7}, {8, 8, 4, 4}, {8, 8, 12, 10}, {3, 5, 1, 1}}) {
                int sw = sizes[0], sh = sizes[1], width = sizes[2], height = sizes[3];
                float[] source = partitionDepth(sw, sh);
                try (DepthInput input = new DepthInput(sw, sh, source)) {
                    checkGl("source DepthInput constructor");
                    prepareRasterState(width, height);
                    checkGl("prepareRasterState");
                    int depth = stage.setupDepthTexture(input.framebuffer, input.framebuffer, sw, sh, width, height);
                    checkGl("DepthStage.setupDepthTexture");
                    assertEquals(GL_DEPTH24_STENCIL8, glGetTextureLevelParameteri(depth, 0, GL_TEXTURE_INTERNAL_FORMAT));
                    checkGl("query target GL_TEXTURE_INTERNAL_FORMAT");
                    assertEquals(GL_TEXTURE, glGetNamedFramebufferAttachmentParameteri(stage.framebufferId(),
                            GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
                    checkGl("query target attachment object type");
                    assertEquals(GL_DEPTH_STENCIL_ATTACHMENT, stage.getDepthAttachmentType());
                    assertEquals(width, glGetTextureLevelParameteri(depth, 0, GL_TEXTURE_WIDTH));
                    checkGl("query target width");
                    assertEquals(height, glGetTextureLevelParameteri(depth, 0, GL_TEXTURE_HEIGHT));
                    checkGl("query target height");
                    assertPartition(depth, source, sw, sh, width, height);
                    assertEquals(GL_NO_ERROR, glGetError(), Arrays.toString(sizes));
                }
            }

            // The depth owner must establish its clear masks, not inherit stale caller masks.
            // Seed a known old frame so the assertion cannot pass because fresh storage was zero.
            int width = 8, height = 8;
            float[] source = partitionDepth(width, height);
            try (DepthInput input = new DepthInput(width, height, source)) {
                prepareRasterState(width, height);
                stage.setupDepthTexture(input.framebuffer, input.framebuffer, width, height, width, height);
                glClearNamedFramebufferfi(stage.framebufferId(), GL_DEPTH_STENCIL, 0, 0.25f, 0x5a);
                glDepthMask(false);
                glStencilMask(0);
                glStencilFunc(GL_NOTEQUAL, 7, 0x35);
                int output = stage.setupDepthTexture(input.framebuffer, input.framebuffer, width, height, width, height);
                assertFalse(glGetBoolean(GL_DEPTH_WRITEMASK), "depth caller mask must be restored");
                assertEquals(0, glGetInteger(GL_STENCIL_WRITEMASK), "stencil caller mask must be restored");
                assertEquals(GL_NOTEQUAL, glGetInteger(GL_STENCIL_FUNC));
                assertEquals(7, glGetInteger(GL_STENCIL_REF));
                assertPartition(output, source, width, height, width, height);
                assertEquals(GL_NO_ERROR, glGetError());
            }
        } finally {
            glDepthMask(true);
            glStencilMask(0xff);
            stage.free();
        }
    }

    private static void assertPartition(int texture, float[] input, int sw, int sh, int width, int height) {
        int[] packed = new int[width * height];
        glGetTextureImage(texture, 0, GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8, packed);
        checkGl("glGetTextureImage GL_DEPTH_STENCIL/GL_UNSIGNED_INT_24_8");
        // The original shader samples UV * (target / source), not a cross-format depth blit.
        // With the target-sized viewport this addresses source texel floor(fragment coordinate).
        // The original depth sampler leaves wrapping at its GL_REPEAT default for extents above 1.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sx = x % sw, sy = y % sh;
                boolean vanilla = input[sy * sw + sx] != PROPERTIES.clearDepth();
                int value = packed[y * width + x];
                assertEquals(vanilla ? 0 : 1, value & 0xff, "stencil partition at " + x + ',' + y);
                assertEquals(vanilla ? 0 : 0x00ff_ffff, value >>> 8, "depth partition at " + x + ',' + y);
            }
        }
    }

    private static float[] partitionDepth(int width, int height) {
        float[] values = new float[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                values[y * width + x] = switch ((x + y * 2) % 4) {
                    case 0 -> 1.0f;
                    case 1 -> 0.05f;
                    case 2 -> 0.5f;
                    default -> 0.95f;
                };
            }
        }
        return values;
    }

    private static void prepareRasterState(int width, int height) {
        glViewport(0, 0, width, height);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(true);
        glStencilMask(0xff);
        glColorMask(true, true, true, true);
    }

    private static void checkGl(String operation) {
        assertEquals(GL_NO_ERROR, glGetError(), operation);
    }

    private static void verifyProjectionReconstruction() {
        String source = PROPERTIES.injectDefines(ShaderLoader.parse("voxy:post/ssao.comp"));
        source = source.replaceFirst("void main\\(\\)", "void originalSsaoMain()") + "\n" + """
                layout(std430,binding=0) buffer ReconstructionResult { vec4 reconstructed[5]; };
                void main() {
                    uint i=gl_GlobalInvocationID.x;
                    if (i>=5u || gl_GlobalInvocationID.y!=0u) return;
                    const float distances[5]=float[5](0.5, 0.5001, 10.0, 512.0, 4096.0);
                    float distance=distances[i];
                    vec4 clip=MVP*vec4(distance*0.1, distance*-0.2, -distance, 1.0);
                    vec3 screen=NDC2SCREEN(clip.xyz/clip.w);
                    if (i==4u) screen=vec3(0.5, 0.5, FAR);
                    reconstructed[i]=vec4(rev3d(invMVP,screen),screen.z);
                }
                """;
        int program = Round10GpuExecutionTest.computeProgram(ForgeOriginalVoxyShaderCompiler.prepareSource(source));
        int output = glCreateBuffers();
        try {
            Matrix4f projection = projection(64, 48, 0.5f, 4096.0f);
            glNamedBufferData(output, 5L * 4L * Float.BYTES, GL_DYNAMIC_READ);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, output);
            glUseProgram(program);
            glUniformMatrix4fv(3, false, projection.get(new float[16]));
            glUniformMatrix4fv(4, false, new Matrix4f(projection).invert().get(new float[16]));
            glDispatchCompute(1, 1, 1);
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_BUFFER_UPDATE_BARRIER_BIT);
            float[] actual = new float[20];
            glGetNamedBufferSubData(output, 0, actual);
            float[] distances = {0.5f, 0.5001f, 10.0f, 512.0f, 4096.0f};
            for (int i = 0; i < distances.length; i++) {
                float distance = distances[i];
                float tolerance = Math.max(0.0001f, distance * 0.001f);
                for (int channel = 0; channel < 4; channel++) assertTrue(Float.isFinite(actual[i * 4 + channel]));
                assertEquals(i == 4 ? 0.0f : distance * 0.1f, actual[i * 4], tolerance);
                assertEquals(i == 4 ? 0.0f : distance * -0.2f, actual[i * 4 + 1], tolerance);
                assertEquals(-distance, actual[i * 4 + 2], tolerance);
                assertTrue(actual[i * 4 + 3] >= -1.0e-6f && actual[i * 4 + 3] <= 1.0f);
            }
            assertEquals(0.0f, actual[3], 1.0e-6f);
            assertEquals(1.0f, actual[19]);
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            glUseProgram(0);
            glDeleteBuffers(output);
            glDeleteProgram(program);
        }
    }

    private static void verifyActualSsaoModes() {
        MDICViewport viewport = new MDICViewport(PROPERTIES, 16);
        EnumMap<SSAO.SSAOMode, int[]> edgeResults = new EnumMap<>(SSAO.SSAOMode.class);
        try {
            for (SSAO.SSAOMode mode : new SSAO.SSAOMode[]{SSAO.SSAOMode.BASIC, SSAO.SSAOMode.BETTER, SSAO.SSAOMode.BEST}) {
                SSAO ssao = SSAO.createSSAO(PROPERTIES, mode);
                try {
                    for (int face = 0; face < 6; face++) {
                        int[] flat = computeSsao(ssao, viewport, 17, 13, face, faceView(face), false, false, 10.0f);
                        assertFlatColour(flat, "flat face=" + face + " mode=" + mode);
                    }
                    for (float clear : new float[]{0.0f, 1.0f}) {
                        int[] output = computeClearSsao(ssao, viewport, 17, 13, clear);
                        for (int value : output) assertEquals(0, value, "clear depth must not produce LOD colour: " + mode);
                    }
                    int[] edge = computeSsao(ssao, viewport, 64, 48, 3, new Matrix4f(), true, false, 9.5f);
                    int[] vanillaEdge = computeSsao(ssao, viewport, 64, 48, 3, new Matrix4f(), true, true, 9.5f);
                    int[] flatSeam = computeSsao(ssao, viewport, 64, 48, 3, new Matrix4f(), true, true, 10.0f);
                    int boundary = 44;
                    int darkened = 0, maximumProjectionDifference = 0;
                    for (int y = 0; y < 48; y++) {
                        for (int x = 0; x < boundary; x++) {
                            int pixel = (y * 64 + x) * 4;
                            if (edge[pixel] < RED - 1) darkened++;
                            assertRgb(flatSeam, pixel, RED, GREEN, BLUE, 1, "continuous flat vanilla/LOD seam " + mode);
                            if (mode == SSAO.SSAOMode.BASIC) {
                                assertRgb(vanillaEdge, pixel, RED, GREEN, BLUE, 1,
                                        "BASIC intentionally has no source-vanilla-depth sampling");
                            } else {
                                for (int channel = 0; channel < 3; channel++) {
                                    int difference = Math.abs(edge[pixel + channel] - vanillaEdge[pixel + channel]);
                                    maximumProjectionDifference = Math.max(maximumProjectionDifference, difference);
                                    assertTrue(difference <= 2, "LOD/source projection mismatch: " + mode + " delta=" + difference);
                                }
                            }
                        }
                    }
                    assertTrue(darkened > 0, "the nearby raised edge must produce measurable occlusion in " + mode);
                    edgeResults.put(mode, edge);
                    System.out.println("ROUND10_SSAO_" + mode + "=flat-six-faces-clean; lod-edge-darkened-pixels="
                            + darkened + "; vanilla-vs-lod-max-channel-delta=" + maximumProjectionDifference);
                } finally {
                    // A deleted but still-current GL program is only destroyed on the next
                    // bind. Do not leave that unusable restore target for the following owner.
                    try {
                        glUseProgram(0);
                        glBindImageTexture(0, 0, 0, false, 0, GL_READ_WRITE, GL_RGBA8);
                        for (int unit = 1; unit <= 3; unit++) {
                            glBindSampler(unit, 0);
                            glBindTextureUnit(unit, 0);
                        }
                        checkGl("SSAO test binding teardown");
                    } finally {
                        ssao.free();
                    }
                    checkGl("SSAO owner free");
                }
            }
            assertFalse(Arrays.equals(edgeResults.get(SSAO.SSAOMode.BASIC), edgeResults.get(SSAO.SSAOMode.BETTER)),
                    "one-sample BASIC and 12-sample BETTER should retain their distinct edge kernels");
            assertFalse(Arrays.equals(edgeResults.get(SSAO.SSAOMode.BETTER), edgeResults.get(SSAO.SSAOMode.BEST)),
                    "12- and 24-sample generated patterns should retain their distinct edge kernels");
        } finally {
            viewport.free();
        }
    }

    private static int[] computeSsao(SSAO ssao, MDICViewport viewport, int width, int height, int face,
                                     Matrix4f modelView, boolean edge, boolean vanillaOccluder, float nearSurface) {
        Matrix4f voxyProjection = projection(width, height, 0.5f, 4096.0f);
        Matrix4f vanillaProjection = projection(width, height, 0.05f, 256.0f);
        configureViewport(viewport, width, height, voxyProjection, vanillaProjection, modelView);
        float[] base = new float[width * height], vanilla = new float[base.length];
        int boundary = width * 11 / 16;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean occluder = edge && x >= boundary;
                base[y * width + x] = occluder && vanillaOccluder ? 0.0f
                        : projectDepth(voxyProjection, occluder ? nearSurface : 10.0f);
                vanilla[y * width + x] = occluder && vanillaOccluder ? projectDepth(vanillaProjection, nearSurface) : 1.0f;
            }
        }
        return executeSsao(ssao, viewport, face, base, vanilla);
    }

    private static int[] computeClearSsao(SSAO ssao, MDICViewport viewport, int width, int height, float clear) {
        Matrix4f projection = projection(width, height, 0.5f, 4096.0f);
        configureViewport(viewport, width, height, projection, projection, new Matrix4f());
        float[] base = new float[width * height], source = new float[base.length];
        Arrays.fill(base, clear); Arrays.fill(source, 1.0f);
        return executeSsao(ssao, viewport, 3, base, source);
    }

    private static int[] executeSsao(SSAO ssao, MDICViewport viewport, int face, float[] base, float[] vanilla) {
        int width = viewport.width, height = viewport.height;
        try (DepthInput source = new DepthInput(width, height, vanilla);
             DepthInput lod = new DepthInput(width, height, base);
             ColourTexture input = new ColourTexture(width, height, face, false);
             ColourTexture output = new ColourTexture(width, height, face, true)) {
            ssao.computeSSAO(viewport, output.id, input.id, lod.texture, source.framebuffer);
            glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_TEXTURE_UPDATE_BARRIER_BIT);
            int[] values = output.read();
            for (int i = 0; i < values.length; i += 4) {
                assertTrue(values[i] <= RED && values[i + 1] <= GREEN && values[i + 2] <= BLUE,
                        "SSAO may only darken the input, never generate a bright/debug/unwritten pixel");
                assertTrue(values[i + 3] == 0 || values[i + 3] == 255);
            }
            assertEquals(GL_NO_ERROR, glGetError());
            return values;
        }
    }

    private static void configureViewport(MDICViewport viewport, int width, int height,
                                          Matrix4f projection, Matrix4f vanillaProjection, Matrix4f modelView) {
        viewport.setScreenSize(width, height).setProjection(projection).setVanillaProjection(vanillaProjection).setModelView(modelView);
        projection.mul(modelView, viewport.MVP);
    }

    private static Matrix4f projection(int width, int height, float near, float far) {
        return new Matrix4f().perspective((float) Math.toRadians(70.0), width / (float) height, near, far);
    }

    private static float projectDepth(Matrix4f projection, float distance) {
        Vector4f clip = new Vector4f(0.0f, 0.0f, -distance, 1.0f).mul(projection);
        return (clip.z / clip.w) * 0.5f + 0.5f;
    }

    private static Matrix4f faceView(int face) {
        return switch (face) {
            case 0 -> new Matrix4f().rotateX((float) (-Math.PI / 2));
            case 1 -> new Matrix4f().rotateX((float) (Math.PI / 2));
            case 2 -> new Matrix4f().rotateY((float) Math.PI);
            case 3 -> new Matrix4f();
            case 4 -> new Matrix4f().rotateY((float) (Math.PI / 2));
            case 5 -> new Matrix4f().rotateY((float) (-Math.PI / 2));
            default -> throw new IllegalArgumentException();
        };
    }

    private static void assertFlatColour(int[] values, String label) {
        for (int pixel = 0; pixel < values.length; pixel += 4) {
            assertRgb(values, pixel, RED, GREEN, BLUE, 1, label);
            assertEquals(255, values[pixel + 3], label);
        }
    }

    private static void assertRgb(int[] values, int pixel, int red, int green, int blue, int tolerance, String label) {
        assertEquals(red, values[pixel], tolerance, label + " red");
        assertEquals(green, values[pixel + 1], tolerance, label + " green");
        assertEquals(blue, values[pixel + 2], tolerance, label + " blue");
    }

    private static final class DepthInput implements AutoCloseable {
        final int texture;
        final int framebuffer;

        DepthInput(int width, int height, float[] depths) {
            this.texture = glCreateTextures(GL_TEXTURE_2D);
            this.framebuffer = glCreateFramebuffers();
            try {
                glTextureStorage2D(this.texture, 1, GL_DEPTH_COMPONENT32F, width, height);
                glTextureParameteri(this.texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTextureParameteri(this.texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTextureParameteri(this.texture, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTextureParameteri(this.texture, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTextureSubImage2D(this.texture, 0, 0, 0, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, depths);
                glNamedFramebufferTexture(this.framebuffer, GL_DEPTH_ATTACHMENT, this.texture, 0);
                glNamedFramebufferDrawBuffer(this.framebuffer, GL_NONE);
                glNamedFramebufferReadBuffer(this.framebuffer, GL_NONE);
                assertEquals(GL_FRAMEBUFFER_COMPLETE, glCheckNamedFramebufferStatus(this.framebuffer, GL_FRAMEBUFFER));
            } catch (RuntimeException | Error failure) {
                this.close();
                throw failure;
            }
        }

        @Override
        public void close() {
            glDeleteFramebuffers(this.framebuffer);
            glDeleteTextures(this.texture);
        }
    }

    private static final class ColourTexture implements AutoCloseable {
        final int id;
        final int pixels;

        ColourTexture(int width, int height, int face, boolean output) {
            this.pixels = width * height;
            this.id = glCreateTextures(GL_TEXTURE_2D);
            ByteBuffer bytes = MemoryUtil.memAlloc(this.pixels * 4);
            try {
                for (int i = 0; i < this.pixels; i++) {
                    bytes.put((byte) (output ? 255 : RED)).put((byte) (output ? 0 : GREEN))
                            .put((byte) (output ? 255 : BLUE)).put((byte) (output ? 127 : (64 | face)));
                }
                bytes.flip();
                glTextureStorage2D(this.id, 1, GL_RGBA8, width, height);
                glTextureParameteri(this.id, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTextureParameteri(this.id, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTextureParameteri(this.id, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTextureParameteri(this.id, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTextureSubImage2D(this.id, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            } catch (RuntimeException | Error failure) {
                glDeleteTextures(this.id);
                throw failure;
            } finally {
                MemoryUtil.memFree(bytes);
            }
        }

        int[] read() {
            ByteBuffer bytes = MemoryUtil.memAlloc(this.pixels * 4);
            try {
                glGetTextureImage(this.id, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
                int[] values = new int[this.pixels * 4];
                for (int i = 0; i < values.length; i++) values[i] = Byte.toUnsignedInt(bytes.get(i));
                return values;
            } finally {
                MemoryUtil.memFree(bytes);
            }
        }

        @Override
        public void close() {
            glDeleteTextures(this.id);
        }
    }

    private Round10DepthGpuCases() {
    }
}
