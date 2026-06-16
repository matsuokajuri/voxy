package me.cortex.voxy.forge;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

final class ForgeTexturedDebugShader implements AutoCloseable {
    private static final String VERTEX_SHADER = """
            #version 330 core

            layout(location = 0) in vec2 aPosition;
            layout(location = 1) in vec2 aUv;

            uniform mat4 uMvp;

            out vec2 vUv;

            void main() {
                vUv = aUv;
                gl_Position = uMvp * vec4(aPosition, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core

            uniform sampler2D uAtlas;
            uniform float uAlpha;

            in vec2 vUv;
            out vec4 fragColor;

            void main() {
                vec4 texel = texture(uAtlas, vUv);
                fragColor = vec4(texel.rgb, texel.a * uAlpha);
            }
            """;

    private int programId;
    private int mvpUniform = -1;
    private int atlasUniform = -1;
    private int alphaUniform = -1;
    private boolean shaderCompiled;
    private boolean programCreated;
    private String lastShaderError = "none";

    boolean ensureReady() {
        if (this.programId != 0) {
            return true;
        }

        int vertex = 0;
        int fragment = 0;
        try {
            vertex = compileShader(GL20C.GL_VERTEX_SHADER, VERTEX_SHADER, "vertex");
            fragment = compileShader(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SHADER, "fragment");
            int program = GL20C.glCreateProgram();
            GL20C.glAttachShader(program, vertex);
            GL20C.glAttachShader(program, fragment);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE) {
                String log = GL20C.glGetProgramInfoLog(program);
                GL20C.glDeleteProgram(program);
                this.lastShaderError = "link: " + sanitize(log);
                this.shaderCompiled = false;
                this.programCreated = false;
                return false;
            }

            this.programId = program;
            this.mvpUniform = GL20C.glGetUniformLocation(program, "uMvp");
            this.atlasUniform = GL20C.glGetUniformLocation(program, "uAtlas");
            this.alphaUniform = GL20C.glGetUniformLocation(program, "uAlpha");
            this.shaderCompiled = true;
            this.programCreated = true;
            this.lastShaderError = "none";
            return true;
        } catch (RuntimeException e) {
            this.lastShaderError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.shaderCompiled = false;
            this.programCreated = false;
            return false;
        } finally {
            if (vertex != 0) {
                GL20C.glDeleteShader(vertex);
            }
            if (fragment != 0) {
                GL20C.glDeleteShader(fragment);
            }
        }
    }

    void bind(Matrix4f mvp, float alpha) {
        GL20C.glUseProgram(this.programId);
        if (this.mvpUniform >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buffer = stack.mallocFloat(16);
                mvp.get(buffer);
                GL20C.glUniformMatrix4fv(this.mvpUniform, false, buffer);
            }
        }
        if (this.atlasUniform >= 0) {
            GL20C.glUniform1i(this.atlasUniform, 0);
        }
        if (this.alphaUniform >= 0) {
            GL20C.glUniform1f(this.alphaUniform, alpha);
        }
    }

    void unbind() {
        GL20C.glUseProgram(0);
    }

    boolean shaderCompiled() {
        return this.shaderCompiled;
    }

    boolean programCreated() {
        return this.programCreated && this.programId != 0;
    }

    String lastShaderError() {
        return this.lastShaderError;
    }

    @Override
    public void close() {
        if (this.programId != 0) {
            GL20C.glDeleteProgram(this.programId);
            this.programId = 0;
        }
        this.mvpUniform = -1;
        this.atlasUniform = -1;
        this.alphaUniform = -1;
        this.shaderCompiled = false;
        this.programCreated = false;
    }

    private static int compileShader(int type, String source, String label) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException(label + ": " + sanitize(log));
        }
        return shader;
    }

    private static String sanitize(String log) {
        if (log == null || log.isBlank()) {
            return "unknown";
        }
        return log.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
