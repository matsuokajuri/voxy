package me.cortex.voxy.forge;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL46C.*;

/**
 * Strict compile/link coverage of the production terrain stages in the caller's GL context.
 * The test-only patch consumer verifies the existing ABI, not any real shaderpack's visuals.
 */
final class Round10TerrainShaderGpuCases {
    private static final String TEST_PATCH = """

            layout(location = 0) out vec4 testPatchColour;
            void voxy_emitFragment(VoxyFragmentParameters parameters) {
                vec4 colour = parameters.sampledColour * parameters.tinting;
                vec2 coordinates = parameters.tile + parameters.uv + parameters.lightMap;
                float ids = float(parameters.face + parameters.modelId + parameters.customId);
                testPatchColour = colour + vec4(coordinates, ids, 0.0) * 0.000001;
            }
            """;

    private Round10TerrainShaderGpuCases() {
    }

    static void runAll() {
        assertEquals(GL_NO_ERROR, glGetError(), "Previous GPU case left an error before terrain compile/link");
        int linkedCases = 0;
        for (boolean zeroToOne : new boolean[]{false, true}) {
            // These are compilation variants only. Do not change the context clip mode or
            // enable reverse-Z in the product while exercising both ordinary depth conventions.
            RenderProperties properties = new RenderProperties(zeroToOne, false, false);
            for (boolean patched : new boolean[]{false, true}) {
                for (boolean translucent : new boolean[]{false, true}) {
                    for (boolean taa : new boolean[]{false, true}) {
                        String name = "terrain patched=" + patched + " translucent=" + translucent
                                + " taa=" + taa + " zeroToOne=" + zeroToOne;
                        String vertex = properties.injectDefines(ShaderLoader.parse("voxy:lod/gl46/quads3.vert"));
                        String fragment = properties.injectDefines(ShaderLoader.parse("voxy:lod/gl46/quads.frag"));
                        // Same scalar inputs as the formal directional-tint defines, without
                        // constructing Minecraft or replacing any terrain function under test.
                        vertex = define(vertex, "NO_SHADE_FACE_TINT 1.0", "UP_FACE_TINT 1.0",
                                "DOWN_FACE_TINT 0.5", "Z_AXIS_FACE_TINT 0.8", "X_AXIS_FACE_TINT 0.6");
                        if (taa) {
                            vertex = define(vertex, "TAA_PATCH 1")
                                    + "\nvec2 taaShift() { return vec2(0.00025, -0.0005); }\n";
                        }
                        if (translucent) {
                            vertex = define(vertex, "TRANSLUCENT 1");
                            fragment = define(fragment, "TRANSLUCENT 1");
                        }
                        if (patched) {
                            vertex = define(vertex, "PATCHED_SHADER 1");
                            fragment = define(fragment, "PATCHED_SHADER 1") + TEST_PATCH;
                        }
                        assertEquals(patched, vertex.contains("#define PATCHED_SHADER 1"), name);
                        assertEquals(patched, fragment.contains("#define PATCHED_SHADER 1"), name);
                        assertEquals(translucent, vertex.contains("#define TRANSLUCENT 1"), name);
                        assertEquals(translucent, fragment.contains("#define TRANSLUCENT 1"), name);
                        int program = strictLink(vertex, fragment, name);
                        try {
                            assertEquals(0, glGetFragDataLocation(program, patched ? "testPatchColour" : "outColour"), name);
                            assertNotEquals(GL_INVALID_INDEX, glGetProgramResourceIndex(program, GL_SHADER_STORAGE_BLOCK, "ModelBuffer"), name);
                            assertTrue(glGetUniformLocation(program, "blockModelAtlas") >= 0, name);
                            assertTrue(glGetUniformLocation(program, "depthTex") >= 0, name);
                            assertEquals(GL_NO_ERROR, glGetError(), name);
                            linkedCases++;
                        } finally {
                            glDeleteProgram(program);
                        }
                    }
                }
            }
        }
        assertEquals(16, linkedCases);
        System.out.println("ROUND10_TERRAIN_LINK_CASES=" + linkedCases
                + "; production vertex/fragment; test-only patch ABI consumer, not shaderpack qualification");
    }

    private static String define(String source, String... definitions) {
        int split = source.indexOf('\n') + 1;
        assertTrue(split > 0, "Shader version line is required");
        StringBuilder result = new StringBuilder(source.length() + definitions.length * 40);
        result.append(source, 0, split);
        for (String definition : definitions) result.append("#define ").append(definition).append('\n');
        return result.append(source, split, source.length()).toString();
    }

    private static int strictLink(String vertexSource, String fragmentSource, String name) {
        int vertex = 0, fragment = 0, program = 0;
        boolean success = false;
        try {
            vertex = strictCompile(GL_VERTEX_SHADER, vertexSource, name + " vertex");
            fragment = strictCompile(GL_FRAGMENT_SHADER, fragmentSource, name + " fragment");
            program = glCreateProgram();
            assertNotEquals(0, program, name);
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), name + " link: " + glGetProgramInfoLog(program));
            success = true;
            return program;
        } finally {
            if (vertex != 0) glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
            if (!success && program != 0) glDeleteProgram(program);
        }
    }

    private static int strictCompile(int type, String source, String name) {
        int shader = glCreateShader(type);
        boolean success = false;
        try {
            assertNotEquals(0, shader, name);
            glShaderSource(shader, ForgeOriginalVoxyShaderCompiler.prepareSource(source));
            glCompileShader(shader);
            assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), name + " compile: " + glGetShaderInfoLog(shader));
            success = true;
            return shader;
        } finally {
            if (!success && shader != 0) glDeleteShader(shader);
        }
    }
}
