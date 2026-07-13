package me.cortex.voxy.forge;

import me.cortex.voxy.common.util.MemoryBuffer;
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
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL45C.glCreateVertexArrays;

final class FullscreenBlit extends TrackedObject {
    private final int vertexArrayId = glCreateVertexArrays();
    private final GlBuffer indexBuffer;
    private final int programId;

    FullscreenBlit(RenderProperties properties, String vertexShaderId, String fragmentShaderId) {
        this(properties, vertexShaderId, fragmentShaderId, new String[0]);
    }

    FullscreenBlit(
            RenderProperties properties,
            String vertexShaderId,
            String fragmentShaderId,
            String... fragmentDefines) {
        this.indexBuffer = new GlBuffer(6L, false);
        try {
            MemoryBuffer quadIndices = generateQuadIndicesByte(1);
            try {
                long ptr = UploadStream.instance().upload(this.indexBuffer.id, 0L, this.indexBuffer.size());
                quadIndices.cpyTo(ptr);
            } finally {
                quadIndices.free();
            }
            UploadStream.instance().commit();
            this.programId = compileProgram(
                    properties.injectDefines(ShaderLoader.parse(vertexShaderId)),
                    injectDefines(properties.injectDefines(ShaderLoader.parse(fragmentShaderId)), fragmentDefines));
        } catch (RuntimeException e) {
            this.indexBuffer.free();
            glDeleteVertexArrays(this.vertexArrayId);
            this.free0();
            throw e;
        }
    }

    void bind() {
        glUseProgram(this.programId);
    }

    void blit() {
        glBindVertexArray(this.vertexArrayId);
        glUseProgram(this.programId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, this.indexBuffer.id);
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_BYTE, 0L);
        glBindVertexArray(0);
    }

    @Override
    public void free() {
        this.free0();
        this.indexBuffer.free();
        glDeleteVertexArrays(this.vertexArrayId);
        glDeleteProgram(this.programId);
    }

    private static MemoryBuffer generateQuadIndicesByte(int quadCount) {
        if ((quadCount * 4) >= 1 << 8) {
            throw new IllegalArgumentException("Quad count too large");
        }
        MemoryBuffer buffer = new MemoryBuffer(quadCount * 6L);
        long ptr = buffer.address;
        for (int i = 0; i < quadCount * 4; i += 4) {
            MemoryUtil.memPutByte(ptr, (byte) (i + 1));
            MemoryUtil.memPutByte(ptr + 1L, (byte) (i + 2));
            MemoryUtil.memPutByte(ptr + 2L, (byte) i);
            MemoryUtil.memPutByte(ptr + 3L, (byte) (i + 1));
            MemoryUtil.memPutByte(ptr + 4L, (byte) (i + 3));
            MemoryUtil.memPutByte(ptr + 5L, (byte) (i + 2));
            ptr += 6L;
        }
        return buffer;
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
