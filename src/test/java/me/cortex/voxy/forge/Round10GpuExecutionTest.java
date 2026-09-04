package me.cortex.voxy.forge;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;
import org.joml.Matrix4f;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL46C.*;

/** Executes production GLSL/owners. This auxiliary context never replaces visible-client QA. */
@Tag("round10-gpu")
class Round10GpuExecutionTest {
    private static long window;

    @BeforeAll
    static void openContext() {
        assertTrue(glfwInit(), "Real GLFW/OpenGL context is required for the requested GPU gate");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(64, 64, "Forxy GPU contract tests", 0, 0);
        assertNotEquals(0L, window);
        glfwMakeContextCurrent(window);
        assertTrue(GL.createCapabilities().OpenGL46);
        System.out.println("ROUND10_GPU=" + glGetString(GL_RENDERER) + "; " + glGetString(GL_VERSION));
    }

    @AfterAll
    static void closeContext() {
        try {
            if (window != 0 && UploadStream.isReady()) {
                UploadStream.instance().commitPendingCopiesBeforeOwnerTeardown();
                SharedIndexBuffer.freeAll();
                UploadStream.instance().free();
            }
        } finally {
            GL.setCapabilities(null);
            glfwMakeContextCurrent(0);
            if (window != 0) glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    @Test
    void actualHocQueuesAreBoundedUnderGpuContention() {
        Round10HocGpuCases.runAll();
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void acediumMappedBufferTeardownDoesNotReleaseAnUnownedNvAddress() {
        assertTrue(GL.getCapabilities().GL_NV_shader_buffer_load,
                "This Acedium regression requires its actual NV buffer-address extension");
        int buffer = glCreateBuffers();
        try {
            // Exact storage/map/unmap sequence in Acedium 0.2.7-beta's CPU-mapped owner.
            glNamedBufferStorage(buffer, 64, 578);
            assertNotEquals(0L, nglMapNamedBufferRange(buffer, 0, 64, 114));
            assertTrue(glUnmapNamedBuffer(buffer));
            assertFalse(org.lwjgl.opengl.NVShaderBufferLoad.glIsNamedBufferResidentNV(buffer));
            assertEquals(GL_NO_ERROR, glGetError());
            org.lwjgl.opengl.NVShaderBufferLoad.glMakeNamedBufferNonResidentNV(buffer);
            assertEquals(GL_INVALID_OPERATION, glGetError(), "Unpatched Acedium failure control");

            AcediumBufferResidency.releaseIfResident(buffer);
            assertEquals(GL_NO_ERROR, glGetError());
            // Preserve release semantics if an external owner really did make it resident.
            org.lwjgl.opengl.NVShaderBufferLoad.glMakeNamedBufferResidentNV(buffer, GL_READ_WRITE);
            assertTrue(org.lwjgl.opengl.NVShaderBufferLoad.glIsNamedBufferResidentNV(buffer));
            AcediumBufferResidency.releaseIfResident(buffer);
            assertFalse(org.lwjgl.opengl.NVShaderBufferLoad.glIsNamedBufferResidentNV(buffer));
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            glDeleteBuffers(buffer);
        }
        assertFalse(glIsBuffer(buffer));
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void actualMdicCommandsAndCullingAreBoundedOnGpu() throws Exception {
        Round10MdicGpuCases.runAll();
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void actualCleanerFlagsSentinelsAndDownloadRangesAreSafeOnGpu() throws Exception {
        Round10CleanerGpuCases.runAll();
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void actualDepthStencilAndSsaoOwnersPreserveTheirContracts() throws Exception {
        Round10DepthGpuCases.runAll();
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void attachmentAuditReportsRealStorageWithoutChangingBindings() {
        int framebuffer = glCreateFramebuffers();
        int texture = glCreateTextures(GL_TEXTURE_2D);
        int renderbuffer = glCreateRenderbuffers();
        int oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int oldRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int oldRenderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
        try {
            assertEquals("default-framebuffer", RenderContractAudit.depthAttachmentInfo(0));
            assertTrue(RenderContractAudit.depthAttachmentInfo(framebuffer).endsWith(":depth=none"));
            glTextureStorage2D(texture, 2, GL_DEPTH_COMPONENT32F, 8, 4);
            glNamedFramebufferTexture(framebuffer, GL_DEPTH_ATTACHMENT, texture, 1);
            String textureInfo = RenderContractAudit.depthAttachmentInfo(framebuffer);
            assertTrue(textureInfo.contains(":texture=" + texture + ":level=1:format=" + GL_DEPTH_COMPONENT32F));
            assertTrue(textureInfo.endsWith(":size=4x2:samples=0"));
            glNamedRenderbufferStorageMultisample(renderbuffer, 2, GL_DEPTH24_STENCIL8, 3, 5);
            glNamedFramebufferRenderbuffer(framebuffer, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, renderbuffer);
            String renderbufferInfo = RenderContractAudit.depthAttachmentInfo(framebuffer);
            assertTrue(renderbufferInfo.contains(":renderbuffer=" + renderbuffer + ":format=" + GL_DEPTH24_STENCIL8));
            int actualSamples = glGetNamedRenderbufferParameteri(renderbuffer, GL_RENDERBUFFER_SAMPLES);
            assertTrue(renderbufferInfo.endsWith(":size=3x5:samples=" + actualSamples));
            assertEquals(oldDraw, glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
            assertEquals(oldRead, glGetInteger(GL_READ_FRAMEBUFFER_BINDING));
            assertEquals(oldRenderbuffer, glGetInteger(GL_RENDERBUFFER_BINDING));
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            glDeleteFramebuffers(framebuffer);
            glDeleteRenderbuffers(renderbuffer);
            glDeleteTextures(texture);
        }
    }

    @Test
    void actualDepthHelpersRoundTripAllConventions() {
        for (boolean zeroToOne : new boolean[]{false, true}) {
            for (boolean reverse : new boolean[]{false, true}) {
                RenderProperties properties = new RenderProperties(zeroToOne, reverse, false);
                String source = properties.injectDefines(ShaderLoader.parse("voxy:util/depthutils.glsl")) + "\n" + """
                        layout(local_size_x=1) in;
                        layout(std430,binding=0) buffer Output { float result[]; };
                        void main() {
                            for (uint i=0; i<5; i++) {
                                float screen = float(i)/4.0;
                                result[i] = NDC2SCREEN_DEPTH(SCREEN2NDC_DEPTH(screen));
                            }
                            result[5] = NEAR;
                            result[6] = FAR;
                            result[7] = REDUCTION(0.25, 0.75);
                        }
                        """;
                float[] result = executeFloats(source, 8, 1);
                for (int i = 0; i < 5; i++) assertEquals(i / 4.0f, result[i], 1.0e-6);
                assertEquals(properties.inverseClearDepth(), result[5]);
                assertEquals(properties.clearDepth(), result[6]);
                assertEquals(reverse ? 0.25f : 0.75f, result[7]);
                assertEquals(GL_NO_ERROR, glGetError());
            }
        }
    }

    @Test
    void actualModelShaderDecodesAllIndentationCodesAndBounds() {
        String source = ShaderLoader.parse("voxy:lod/block_model.glsl") + "\n" + """
                layout(local_size_x=1) in;
                layout(std430,binding=0) buffer Output { float result[]; };
                void main() {
                    uint i = gl_GlobalInvocationID.x;
                    result[i] = extractFaceIndentation(i<<16);
                    if (i==0) {
                        vec4 bounds=extractFaceSizes(0xF0F0u);
                        for (uint j=0; j<4; j++) result[64+j]=bounds[j];
                    }
                }
                """;
        float[] result = executeFloats(source, 68, 64);
        for (int i = 0; i < 64; i++) assertEquals((i == 63 ? 64 : i) / 64.0f, result[i]);
        assertArrayEquals(new float[]{0, 1, 0, 1}, java.util.Arrays.copyOfRange(result, 64, 68));
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void actualTerrainStagesLinkAllNormalPatchedAndTaaContracts() {
        Round10TerrainShaderGpuCases.runAll();
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void actualTintFunctionUsesBaseTexelsForNormalAndPatchedContract() {
        int atlas = glCreateTextures(GL_TEXTURE_2D);
        try {
            glTextureStorage2D(atlas, 2, GL_RGBA8, 2, 2);
            glTextureSubImage2D(atlas, 0, 0, 0, 2, 2, GL_RGBA, GL_FLOAT, new float[]{
                    .5f, .5f, .5f, 1, 0, 1, 0, 1, .5f, .515f, .515f, 1, .5f, .53f, .5f, 1});
            glTextureSubImage2D(atlas, 1, 0, 0, 1, 1, GL_RGBA, GL_FLOAT, new float[]{.9f, .9f, .9f, 1});
            glTextureParameteri(atlas, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
            glTextureParameteri(atlas, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glBindTextureUnit(0, atlas);
            String source = ShaderLoader.parse("voxy:lod/model_tint.glsl");
            int split = source.indexOf('\n') + 1;
            source = source.substring(0, split) + "layout(binding=0) uniform sampler2D blockModelAtlas;\n"
                    + source.substring(split) + "\n" + """
                    layout(local_size_x=1) in;
                    layout(std430,binding=0) buffer Output { float result[]; };
                    void main() {
                        uint index=gl_GlobalInvocationID.x;
                        uint pixel=index%4u;
                        vec2 position=(vec2(pixel%2u,pixel/2u)+0.5)/2.0;
                        result[index]=float(shouldApplyModelTint(index/4u,position));
                    }
                    """;
            assertArrayEquals(new float[]{0,0,0,0, 1,0,1,0, 1,1,1,1}, executeFloats(source, 12, 12));
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            glDeleteTextures(atlas);
        }
    }

    @Test
    void actualViewportOwnersKeepIndependentStateAndReleaseEveryView() throws Exception {
        var allocated = new ArrayList<MDICViewport>();
        ViewportSelector selector = new ViewportSelector(() -> {
            MDICViewport viewport = new MDICViewport(new RenderProperties(false, false, false), 8);
            allocated.add(viewport);
            return viewport;
        });
        var select = ViewportSelector.class.getDeclaredMethod("select", Object.class, String.class);
        select.setAccessible(true);
        Object aKey = new Object(), bKey = new Object();
        try {
            MDICViewport a = (MDICViewport) select.invoke(selector, aKey, "A");
            a.setProjection(new Matrix4f().perspective(1.0f, 1.2f, .05f, 4096))
                    .setModelView(new Matrix4f().rotateY(.4f)).setCamera(-.25, 90, 32.5).setScreenSize(9, 7).update();
            a.frameId = 7;
            Matrix4f matrixA = new Matrix4f(a.MVP);
            MDICViewport b = (MDICViewport) select.invoke(selector, bKey, "B");
            b.setProjection(new Matrix4f().perspective(.7f, .8f, .1f, 2048))
                    .setModelView(new Matrix4f().rotateX(.2f)).setCamera(1024, 20, -2048).setScreenSize(3, 5).update();
            b.frameId = 19;
            MDICViewport again = (MDICViewport) select.invoke(selector, aKey, "A");
            assertSame(a, again);
            assertEquals(matrixA, again.MVP);
            assertEquals(7, again.frameId);
            assertEquals(-1, again.section.x);
            assertEquals(31.75f, again.innerTranslation.x);
            assertEquals(9, glGetTextureLevelParameteri(a.depthBoundingTextureId(), 0, GL_TEXTURE_WIDTH));
            assertEquals(3, glGetTextureLevelParameteri(b.depthBoundingTextureId(), 0, GL_TEXTURE_WIDTH));
            assertNotEquals(a.drawCallBuffer.id, b.drawCallBuffer.id);
            assertNotEquals(a.commandDispatchBuffer.id, b.commandDispatchBuffer.id);
            assertNotEquals(a.drawCountCallBuffer.id, a.commandDispatchBuffer.id);
            assertNotEquals(a.visibilityBuffer.id, b.visibilityBuffer.id);
            assertNotEquals(a.depthBoundingTextureId(), b.depthBoundingTextureId());
            assertEquals(GpuBufferLayout.DRAW_BUFFER_BYTES, glGetNamedBufferParameteri64(a.drawCallBuffer.id, GL_BUFFER_SIZE));
            assertEquals(GpuBufferLayout.DISPATCH_BYTES, glGetNamedBufferParameteri64(a.commandDispatchBuffer.id, GL_BUFFER_SIZE));
            assertEquals(3, allocated.size(), "default + stable A/B keys, not a new viewport per frame");
            assertEquals(GL_NO_ERROR, glGetError());
        } finally {
            selector.free();
        }
        for (MDICViewport viewport : allocated) {
            assertFalse(glIsBuffer(viewport.drawCallBuffer.id));
            assertFalse(glIsBuffer(viewport.commandDispatchBuffer.id));
            assertFalse(glIsBuffer(viewport.visibilityBuffer.id));
        }
        assertEquals(GL_NO_ERROR, glGetError());
    }

    @Test
    void actualHizOwnerConservativelyReducesEveryMipAcrossResizeAndReverseZ() {
        for (boolean reverse : new boolean[]{false, true}) {
            HiZBuffer hiz = new HiZBuffer(new RenderProperties(false, reverse, false));
            try {
                for (int[] dimensions : new int[][]{{8, 8}, {9, 7}, {3, 5}, {1, 1}}) {
                    int width = dimensions[0], height = dimensions[1];
                    float[] input = new float[width * height];
                    for (int i = 0; i < input.length; i++) input[i] = (i % 5) / 4.0f;
                    int texture = glCreateTextures(GL_TEXTURE_2D);
                    try {
                        glTextureStorage2D(texture, 1, GL_DEPTH_COMPONENT32F, width, height);
                        glTextureSubImage2D(texture, 0, 0, 0, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, input);
                        hiz.buildMipChain(texture, width, height);
                        int targetWidth = Integer.highestOneBit(width), targetHeight = Integer.highestOneBit(height);
                        assertEquals((targetWidth << 16) | targetHeight, hiz.getPackedLevels());
                        int levels = Math.max(1, Integer.numberOfTrailingZeros(Math.max(targetWidth, targetHeight)));
                        float[] expected = input;
                        int sourceWidth = width, sourceHeight = height;
                        for (int level = 0; level < levels; level++) {
                            expected = reduceGather(expected, sourceWidth, sourceHeight, targetWidth, targetHeight, reverse);
                            float[] actual = new float[targetWidth * targetHeight];
                            glGetTextureImage(hiz.getHizTextureId(), level, GL_DEPTH_COMPONENT, GL_FLOAT, actual);
                            assertArrayEquals(expected, actual, 2.0e-6f,
                                    "HiZ " + width + "x" + height + " level " + level + " reverse=" + reverse);
                            sourceWidth = targetWidth;
                            sourceHeight = targetHeight;
                            targetWidth = Math.max(1, targetWidth / 2);
                            targetHeight = Math.max(1, targetHeight / 2);
                        }
                        assertEquals(GL_NO_ERROR, glGetError());
                    } finally {
                        glDeleteTextures(texture);
                    }
                }
            } finally {
                hiz.free();
            }
        }
    }

    private static float[] reduceGather(float[] input, int width, int height, int outWidth, int outHeight, boolean reverse) {
        float[] output = new float[outWidth * outHeight];
        for (int y = 0; y < outHeight; y++) {
            for (int x = 0; x < outWidth; x++) {
                int firstX = (int) Math.floor((x + 0.5) * width / outWidth - 0.5);
                int firstY = (int) Math.floor((y + 0.5) * height / outHeight - 0.5);
                float value = reverse ? 1 : 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int sx = Math.max(0, Math.min(width - 1, firstX + dx));
                        int sy = Math.max(0, Math.min(height - 1, firstY + dy));
                        value = reverse ? Math.min(value, input[sy * width + sx]) : Math.max(value, input[sy * width + sx]);
                    }
                }
                output[y * outWidth + x] = value;
            }
        }
        return output;
    }

    static int computeProgram(String source) {
        int shader = glCreateShader(GL_COMPUTE_SHADER);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new AssertionError(log);
        }
        int program = glCreateProgram();
        glAttachShader(program, shader);
        glLinkProgram(program);
        glDeleteShader(shader);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new AssertionError(log);
        }
        return program;
    }

    private static float[] executeFloats(String source, int count, int groups) {
        int program = computeProgram(source);
        int output = glCreateBuffers();
        try {
            glNamedBufferData(output, count * 4L, GL_DYNAMIC_READ);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, output);
            glUseProgram(program);
            glDispatchCompute(groups, 1, 1);
            glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
            float[] result = new float[count];
            glGetNamedBufferSubData(output, 0, result);
            return result;
        } finally {
            glUseProgram(0);
            glDeleteBuffers(output);
            glDeleteProgram(program);
        }
    }
}
