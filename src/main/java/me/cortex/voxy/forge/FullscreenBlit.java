package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.TrackedObject;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_TRUE;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11C.glDrawElements;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL20C.nglShaderSource;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;

final class FullscreenBlit extends TrackedObject {
    private final int programId;

    FullscreenBlit(RenderProperties properties, String vertexShaderId, String fragmentShaderId) {
        this(properties, vertexShaderId, fragmentShaderId, new String[0]);
    }

    FullscreenBlit(
            RenderProperties properties,
            String vertexShaderId,
            String fragmentShaderId,
            String... defines) {
        try {
            FullscreenBlitShaderSources sources = FullscreenBlitShaderSources.prepare(
                    properties,
                    ShaderLoader.parse(vertexShaderId),
                    ShaderLoader.parse(fragmentShaderId),
                    defines);
            this.programId = compileProgram(sources.vertex(), sources.fragment());
        } catch (RuntimeException e) {
            this.free0();
            throw e;
        }
    }

    void bind() {
        glUseProgram(this.programId);
    }

    void blit() {
        glBindVertexArray(ForgeOriginalVoxyEmptyVertexArray.id());
        glUseProgram(this.programId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE_BYTE.id());
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_BYTE, 0L);
        glBindVertexArray(0);
    }

    @Override
    public void free() {
        this.free0();
        glDeleteProgram(this.programId);
    }

    private static int compileProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Original fullscreen blit shader link failed: " + log);
        }
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        source = ForgeOriginalVoxyShaderCompiler.prepareSource(source);
        long ptr = MemoryUtil.memAddress(MemoryUtil.memUTF8(source, true));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            nglShaderSource(shader, 1, stack.pointers(ptr).address0(), 0L);
        } finally {
            MemoryUtil.nmemFree(ptr);
        }
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Original fullscreen blit shader compile failed: " + log);
        }
        return shader;
    }
}

record FullscreenBlitShaderSources(String vertex, String fragment) {
    static FullscreenBlitShaderSources prepare(
            RenderProperties properties,
            String vertexSource,
            String fragmentSource,
            String... defines) {
        return new FullscreenBlitShaderSources(
                injectDefines(properties.injectDefines(vertexSource), defines),
                injectDefines(properties.injectDefines(fragmentSource), defines));
    }

    private static String injectDefines(String source, String... defines) {
        if (defines == null || defines.length == 0) {
            return source;
        }
        StringBuilder builder = new StringBuilder();
        for (String define : defines) {
            builder.append("#define ").append(define).append('\n');
        }
        int split = source.indexOf('\n');
        if (split < 0) {
            return source + '\n' + builder;
        }
        return source.substring(0, split + 1) + builder + source.substring(split + 1);
    }
}
