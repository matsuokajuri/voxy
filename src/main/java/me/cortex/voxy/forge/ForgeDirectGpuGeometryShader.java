package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL11C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_VERSION;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL11C.glGetString;
import static org.lwjgl.opengl.GL14C.glMultiDrawArrays;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.GL_SHADING_LANGUAGE_VERSION;
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
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUniform1f;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniform3f;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;

final class ForgeDirectGpuGeometryShader {
    static final String ERROR_STAGE_NONE = "none";
    static final String ERROR_STAGE_BIND_SHADER = "BIND_SHADER";
    static final String ERROR_STAGE_BIND_GEOMETRY_SSBO = "BIND_GEOMETRY_SSBO";
    static final String ERROR_STAGE_BIND_DRAW_ITEM_SSBO = "BIND_DRAW_ITEM_SSBO";
    static final String ERROR_STAGE_DRAW_ITEM_SSBO_UPLOAD = "DRAW_ITEM_SSBO_UPLOAD";
    static final String ERROR_STAGE_SET_UNIFORMS = "SET_UNIFORMS";
    static final String ERROR_STAGE_DRAW_ARRAYS = "DRAW_ARRAYS";
    static final String ERROR_STAGE_MULTI_DRAW_ARRAYS = "MULTI_DRAW_ARRAYS";

    private static final String VERTEX_SHADER = """
            #version 430 core

            layout(std430, binding = 0) readonly buffer GeometryRecords {
                uvec2 records[];
            };

            uniform mat4 uModelView;
            uniform mat4 uProjection;
            uniform int uBaseRecord;
            uniform vec3 uSectionOrigin;
            uniform float uSectionScale;
            uniform float uAlpha;
            uniform int uColorSeed;

            out vec4 vColor;

            vec3 colorFor(uint face, uint modelId, uint biomeId, uint lightId) {
                vec3 faceColor = vec3(0.95, 0.35, 0.75);
                if (face == 1u) {
                    faceColor = vec3(0.25, 0.85, 1.00);
                } else if (face == 2u) {
                    faceColor = vec3(1.00, 0.85, 0.20);
                } else if (face == 3u) {
                    faceColor = vec3(0.45, 1.00, 0.35);
                } else if (face == 4u) {
                    faceColor = vec3(1.00, 0.45, 0.25);
                } else if (face == 5u) {
                    faceColor = vec3(0.55, 0.55, 1.00);
                }
                uint seed = uint(uColorSeed) ^ (modelId * 1103515245u) ^ (biomeId * 1664525u) ^ (lightId * 1013904223u);
                float wobble = 0.72 + float(seed & 31u) / 96.0;
                return min(vec3(1.0), faceColor * wobble);
            }

            void main() {
                int recordIndex = uBaseRecord + gl_VertexID / 6;
                int corner = gl_VertexID - (gl_VertexID / 6) * 6;
                uvec2 packedRecord = records[recordIndex];
                uint lo = packedRecord.x;
                uint hi = packedRecord.y;

                uint face = lo & 7u;
                uint lenCells = ((lo >> 3) & 15u) + 1u;
                uint widthCells = ((lo >> 7) & 15u) + 1u;
                uint localZ = (lo >> 11) & 31u;
                uint localY = (lo >> 16) & 31u;
                uint localX = (lo >> 21) & 31u;
                uint modelId = ((lo >> 26) & 63u) | ((hi & 1023u) << 6);
                uint biomeId = (hi >> 14) & 511u;
                uint lightId = (hi >> 23) & 255u;

                float s = uSectionScale;
                vec3 p0 = vec3(float(localX) * s, float(localY) * s, float(localZ) * s);
                vec3 p1 = p0;
                vec3 p2 = p0;
                vec3 p3 = p0;
                float len = float(lenCells) * s;
                float wid = float(widthCells) * s;

                if (face == 0u || face == 1u) {
                    p1.x += len;
                    p2.z += wid;
                    p3.x += len;
                    p3.z += wid;
                } else if (face == 2u || face == 3u) {
                    p1.x += len;
                    p2.y += wid;
                    p3.x += len;
                    p3.y += wid;
                } else {
                    p1.y += len;
                    p2.z += wid;
                    p3.y += len;
                    p3.z += wid;
                }

                vec3 local = p0;
                if (corner == 1) {
                    local = p1;
                } else if (corner == 2 || corner == 4) {
                    local = p3;
                } else if (corner == 5) {
                    local = p2;
                }

                vColor = vec4(colorFor(face, modelId, biomeId, lightId), uAlpha);
                gl_Position = uProjection * uModelView * vec4(uSectionOrigin + local, 1.0);
            }
            """;

    private static final String MULTI_DRAW_VERTEX_SHADER = """
            #version 430 core
            #extension GL_ARB_shader_draw_parameters : require

            layout(std430, binding = 0) readonly buffer GeometryRecords {
                uvec2 records[];
            };

            struct DrawItem {
                uvec4 a;
                uvec4 b;
            };

            layout(std430, binding = 1) readonly buffer DrawItems {
                DrawItem drawItems[];
            };

            uniform mat4 uModelView;
            uniform mat4 uProjection;
            uniform float uAlpha;

            out vec4 vColor;

            vec3 colorFor(uint face, uint modelId, uint biomeId, uint lightId, uint colorSeed) {
                vec3 faceColor = vec3(0.95, 0.35, 0.75);
                if (face == 1u) {
                    faceColor = vec3(0.25, 0.85, 1.00);
                } else if (face == 2u) {
                    faceColor = vec3(1.00, 0.85, 0.20);
                } else if (face == 3u) {
                    faceColor = vec3(0.45, 1.00, 0.35);
                } else if (face == 4u) {
                    faceColor = vec3(1.00, 0.45, 0.25);
                } else if (face == 5u) {
                    faceColor = vec3(0.55, 0.55, 1.00);
                }
                uint seed = colorSeed ^ (modelId * 1103515245u) ^ (biomeId * 1664525u) ^ (lightId * 1013904223u);
                float wobble = 0.72 + float(seed & 31u) / 96.0;
                return min(vec3(1.0), faceColor * wobble);
            }

            void main() {
                uint drawId = uint(gl_DrawIDARB);
                DrawItem item = drawItems[drawId];
                int recordIndex = int(item.a.x) + gl_VertexID / 6;
                int corner = gl_VertexID - (gl_VertexID / 6) * 6;
                uvec2 packedRecord = records[recordIndex];
                uint lo = packedRecord.x;
                uint hi = packedRecord.y;

                uint face = lo & 7u;
                uint lenCells = ((lo >> 3) & 15u) + 1u;
                uint widthCells = ((lo >> 7) & 15u) + 1u;
                uint localZ = (lo >> 11) & 31u;
                uint localY = (lo >> 16) & 31u;
                uint localX = (lo >> 21) & 31u;
                uint modelId = ((lo >> 26) & 63u) | ((hi & 1023u) << 6);
                uint biomeId = (hi >> 14) & 511u;
                uint lightId = (hi >> 23) & 255u;

                float s = uintBitsToFloat(item.b.w);
                vec3 sectionOrigin = vec3(
                    uintBitsToFloat(item.b.x),
                    uintBitsToFloat(item.b.y),
                    uintBitsToFloat(item.b.z)
                );
                vec3 p0 = vec3(float(localX) * s, float(localY) * s, float(localZ) * s);
                vec3 p1 = p0;
                vec3 p2 = p0;
                vec3 p3 = p0;
                float len = float(lenCells) * s;
                float wid = float(widthCells) * s;

                if (face == 0u || face == 1u) {
                    p1.x += len;
                    p2.z += wid;
                    p3.x += len;
                    p3.z += wid;
                } else if (face == 2u || face == 3u) {
                    p1.x += len;
                    p2.y += wid;
                    p3.x += len;
                    p3.y += wid;
                } else {
                    p1.y += len;
                    p2.z += wid;
                    p3.y += len;
                    p3.z += wid;
                }

                vec3 local = p0;
                if (corner == 1) {
                    local = p1;
                } else if (corner == 2 || corner == 4) {
                    local = p3;
                } else if (corner == 5) {
                    local = p2;
                }

                vColor = vec4(colorFor(face, modelId, biomeId, lightId, item.a.z), uAlpha);
                gl_Position = uProjection * uModelView * vec4(sectionOrigin + local, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 430 core

            in vec4 vColor;
            layout(location = 0) out vec4 fragColor;

            void main() {
                fragColor = vColor;
            }
            """;

    private boolean supportChecked;
    private boolean shaderSupported;
    private boolean multiDrawSupported;
    private boolean drawIdSupported;
    private boolean shaderCompiled;
    private boolean programCreated;
    private boolean multiDrawShaderCompiled;
    private boolean multiDrawProgramCreated;
    private String lastShaderError = "none";
    private String lastMultiDrawShaderError = "none";
    private String unsupportedReason = "none";
    private String multiDrawUnsupportedReason = "none";
    private String glVersion = "unknown";
    private String glslVersion = "unknown";
    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    private int vaoId;
    private int multiProgramId;
    private int multiVertexShaderId;
    private int multiFragmentShaderId;
    private int multiVaoId;
    private int modelViewLocation = -1;
    private int projectionLocation = -1;
    private int baseRecordLocation = -1;
    private int sectionOriginLocation = -1;
    private int sectionScaleLocation = -1;
    private int alphaLocation = -1;
    private int colorSeedLocation = -1;
    private int multiModelViewLocation = -1;
    private int multiProjectionLocation = -1;
    private int multiAlphaLocation = -1;

    boolean ensureReady() {
        if (!RenderSystem.isOnRenderThread()) {
            this.lastShaderError = "not-render-thread";
            return false;
        }
        this.checkSupport();
        if (!this.shaderSupported) {
            return false;
        }
        if (this.programCreated) {
            return true;
        }
        try {
            this.vertexShaderId = compile(GL_VERTEX_SHADER, VERTEX_SHADER);
            this.fragmentShaderId = compile(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            this.programId = glCreateProgram();
            glAttachShader(this.programId, this.vertexShaderId);
            glAttachShader(this.programId, this.fragmentShaderId);
            glLinkProgram(this.programId);
            if (glGetProgrami(this.programId, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException("program link failed: " + glGetProgramInfoLog(this.programId));
            }
            this.vaoId = glGenVertexArrays();
            this.modelViewLocation = glGetUniformLocation(this.programId, "uModelView");
            this.projectionLocation = glGetUniformLocation(this.programId, "uProjection");
            this.baseRecordLocation = glGetUniformLocation(this.programId, "uBaseRecord");
            this.sectionOriginLocation = glGetUniformLocation(this.programId, "uSectionOrigin");
            this.sectionScaleLocation = glGetUniformLocation(this.programId, "uSectionScale");
            this.alphaLocation = glGetUniformLocation(this.programId, "uAlpha");
            this.colorSeedLocation = glGetUniformLocation(this.programId, "uColorSeed");
            this.shaderCompiled = true;
            this.programCreated = true;
            this.lastShaderError = "none";
            return true;
        } catch (RuntimeException e) {
            this.lastShaderError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.closeOnRenderThread();
            return false;
        }
    }

    boolean ensureMultiDrawReady() {
        if (!RenderSystem.isOnRenderThread()) {
            this.lastMultiDrawShaderError = "not-render-thread";
            return false;
        }
        this.checkSupport();
        if (!this.multiDrawSupported) {
            this.lastMultiDrawShaderError = this.multiDrawUnsupportedReason;
            return false;
        }
        if (this.multiDrawProgramCreated) {
            return true;
        }
        try {
            this.multiVertexShaderId = compile(GL_VERTEX_SHADER, MULTI_DRAW_VERTEX_SHADER);
            this.multiFragmentShaderId = compile(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            this.multiProgramId = glCreateProgram();
            glAttachShader(this.multiProgramId, this.multiVertexShaderId);
            glAttachShader(this.multiProgramId, this.multiFragmentShaderId);
            glLinkProgram(this.multiProgramId);
            if (glGetProgrami(this.multiProgramId, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException("multi draw program link failed: " + glGetProgramInfoLog(this.multiProgramId));
            }
            this.multiVaoId = glGenVertexArrays();
            this.multiModelViewLocation = glGetUniformLocation(this.multiProgramId, "uModelView");
            this.multiProjectionLocation = glGetUniformLocation(this.multiProgramId, "uProjection");
            this.multiAlphaLocation = glGetUniformLocation(this.multiProgramId, "uAlpha");
            this.multiDrawShaderCompiled = true;
            this.multiDrawProgramCreated = true;
            this.lastMultiDrawShaderError = "none";
            return true;
        } catch (RuntimeException e) {
            this.lastMultiDrawShaderError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.closeMultiDrawOnRenderThread();
            return false;
        }
    }

    int drawItem(int geometryBufferId, Matrix4f modelView, Matrix4f projection, ForgeDirectGpuGeometryDrawItem item, float alpha) {
        return this.drawItemWithDiagnostics(geometryBufferId, modelView, projection, item, alpha).glError();
    }

    DrawCallResult drawItemWithDiagnostics(int geometryBufferId, Matrix4f modelView, Matrix4f projection, ForgeDirectGpuGeometryDrawItem item, float alpha) {
        if (!this.ensureReady()) {
            return new DrawCallResult(GL_NO_ERROR, ERROR_STAGE_NONE);
        }
        glUseProgram(this.programId);
        glBindVertexArray(this.vaoId);
        int glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_SHADER);
        }

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, geometryBufferId);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_GEOMETRY_SSBO);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrixBuffer = stack.mallocFloat(16);
            modelView.get(matrixBuffer);
            glUniformMatrix4fv(this.modelViewLocation, false, matrixBuffer);
            matrixBuffer.clear();
            projection.get(matrixBuffer);
            glUniformMatrix4fv(this.projectionLocation, false, matrixBuffer);
        }
        glUniform1i(this.baseRecordLocation, item.baseRecord());
        glUniform3f(this.sectionOriginLocation, item.originX(), item.originY(), item.originZ());
        glUniform1f(this.sectionScaleLocation, item.scale());
        glUniform1f(this.alphaLocation, alpha);
        glUniform1i(this.colorSeedLocation, item.sectionId());
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_SET_UNIFORMS);
        }

        glDrawArrays(GL_TRIANGLES, 0, item.vertexCount());
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_DRAW_ARRAYS);
        }
        return new DrawCallResult(GL_NO_ERROR, ERROR_STAGE_NONE);
    }

    DrawCallResult drawMultiWithDiagnostics(
            int geometryBufferId,
            int drawItemBufferId,
            Matrix4f modelView,
            Matrix4f projection,
            ForgeDirectGpuGeometryDrawList drawList,
            float alpha
    ) {
        if (!this.ensureMultiDrawReady()) {
            return new DrawCallResult(GL_NO_ERROR, ERROR_STAGE_NONE);
        }
        if (drawItemBufferId == 0 || drawList == null || !drawList.isValid()) {
            return new DrawCallResult(GL_NO_ERROR, ERROR_STAGE_NONE);
        }

        glUseProgram(this.multiProgramId);
        glBindVertexArray(this.multiVaoId);
        int glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_SHADER);
        }

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, geometryBufferId);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_GEOMETRY_SSBO);
        }

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeDirectGpuGeometryDrawItemBuffer.BINDING_INDEX, drawItemBufferId);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_DRAW_ITEM_SSBO);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrixBuffer = stack.mallocFloat(16);
            modelView.get(matrixBuffer);
            glUniformMatrix4fv(this.multiModelViewLocation, false, matrixBuffer);
            matrixBuffer.clear();
            projection.get(matrixBuffer);
            glUniformMatrix4fv(this.multiProjectionLocation, false, matrixBuffer);
            glUniform1f(this.multiAlphaLocation, alpha);

            glError = glGetError();
            if (glError != GL_NO_ERROR) {
                return new DrawCallResult(glError, ERROR_STAGE_SET_UNIFORMS);
            }

            IntBuffer firsts = stack.mallocInt(drawList.itemCount());
            IntBuffer counts = stack.mallocInt(drawList.itemCount());
            for (ForgeDirectGpuGeometryDrawItem item : drawList.items()) {
                firsts.put(0);
                counts.put(item.vertexCount());
            }
            firsts.flip();
            counts.flip();
            glMultiDrawArrays(GL_TRIANGLES, firsts, counts);
        }

        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_MULTI_DRAW_ARRAYS);
        }
        return new DrawCallResult(GL_NO_ERROR, ERROR_STAGE_NONE);
    }

    void unbind() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeDirectGpuGeometryDrawItemBuffer.BINDING_INDEX, 0);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    void close() {
        if (RenderSystem.isOnRenderThread()) {
            this.closeOnRenderThread();
        } else {
            RenderSystem.recordRenderCall(this::closeOnRenderThread);
        }
    }

    ShaderStatus createStatusSnapshot() {
        if (RenderSystem.isOnRenderThread()) {
            this.checkSupport();
        }
        return new ShaderStatus(
                this.shaderSupported,
                this.multiDrawSupported,
                this.drawIdSupported,
                this.shaderCompiled,
                this.programCreated,
                this.shaderCompiled,
                this.multiDrawShaderCompiled,
                this.multiDrawProgramCreated,
                this.lastShaderError,
                this.lastMultiDrawShaderError,
                this.unsupportedReason,
                this.multiDrawUnsupportedReason,
                this.glVersion,
                this.glslVersion,
                true
        );
    }

    private void checkSupport() {
        if (this.supportChecked) {
            return;
        }
        this.supportChecked = true;
        GLCapabilities capabilities = GL.getCapabilities();
        String version = glGetString(GL_VERSION);
        String shadingVersion = glGetString(GL_SHADING_LANGUAGE_VERSION);
        this.glVersion = version == null ? "unknown" : version.replace(' ', '_');
        this.glslVersion = shadingVersion == null ? "unknown" : shadingVersion.replace(' ', '_');
        if (!capabilities.OpenGL43) {
            this.shaderSupported = false;
            this.multiDrawSupported = false;
            this.drawIdSupported = false;
            this.unsupportedReason = "OpenGL_4.3_required_for_SSBO";
            this.multiDrawUnsupportedReason = this.unsupportedReason;
            this.lastShaderError = this.unsupportedReason;
            this.lastMultiDrawShaderError = this.multiDrawUnsupportedReason;
            return;
        }
        this.shaderSupported = true;
        this.unsupportedReason = "none";
        this.drawIdSupported = capabilities.GL_ARB_shader_draw_parameters;
        this.multiDrawSupported = this.drawIdSupported;
        this.multiDrawUnsupportedReason = this.multiDrawSupported ? "none" : "GL_ARB_shader_draw_parameters_required_for_gl_DrawIDARB";
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("shader compile failed: " + log);
        }
        return shader;
    }

    private void closeOnRenderThread() {
        if (this.programId != 0) {
            glDeleteProgram(this.programId);
            this.programId = 0;
        }
        if (this.vertexShaderId != 0) {
            glDeleteShader(this.vertexShaderId);
            this.vertexShaderId = 0;
        }
        if (this.fragmentShaderId != 0) {
            glDeleteShader(this.fragmentShaderId);
            this.fragmentShaderId = 0;
        }
        if (this.vaoId != 0) {
            glDeleteVertexArrays(this.vaoId);
            this.vaoId = 0;
        }
        this.closeMultiDrawOnRenderThread();
        this.shaderCompiled = false;
        this.programCreated = false;
        this.modelViewLocation = -1;
        this.projectionLocation = -1;
        this.baseRecordLocation = -1;
        this.sectionOriginLocation = -1;
        this.sectionScaleLocation = -1;
        this.alphaLocation = -1;
        this.colorSeedLocation = -1;
    }

    private void closeMultiDrawOnRenderThread() {
        if (this.multiProgramId != 0) {
            glDeleteProgram(this.multiProgramId);
            this.multiProgramId = 0;
        }
        if (this.multiVertexShaderId != 0) {
            glDeleteShader(this.multiVertexShaderId);
            this.multiVertexShaderId = 0;
        }
        if (this.multiFragmentShaderId != 0) {
            glDeleteShader(this.multiFragmentShaderId);
            this.multiFragmentShaderId = 0;
        }
        if (this.multiVaoId != 0) {
            glDeleteVertexArrays(this.multiVaoId);
            this.multiVaoId = 0;
        }
        this.multiDrawShaderCompiled = false;
        this.multiDrawProgramCreated = false;
        this.multiModelViewLocation = -1;
        this.multiProjectionLocation = -1;
        this.multiAlphaLocation = -1;
    }

    record ShaderStatus(
            boolean shaderSupported,
            boolean multiDrawSupported,
            boolean drawIdSupported,
            boolean shaderCompiled,
            boolean programCreated,
            boolean loopShaderCompiled,
            boolean multiDrawShaderCompiled,
            boolean multiDrawProgramCreated,
            String lastShaderError,
            String lastMultiDrawShaderError,
            String unsupportedReason,
            String multiDrawUnsupportedReason,
            String glVersion,
            String glslVersion,
            boolean usesSsbo
    ) {
        boolean ok() {
            return this.shaderSupported && this.shaderCompiled && this.programCreated;
        }

        boolean multiDrawOk() {
            return this.multiDrawSupported && this.multiDrawShaderCompiled && this.multiDrawProgramCreated;
        }
    }

    record DrawCallResult(int glError, String stage) {
        boolean ok() {
            return this.glError == GL_NO_ERROR;
        }

        String formattedError() {
            return formatGlError(this.glError);
        }
    }

    static String formatGlError(int error) {
        return error == GL_NO_ERROR ? "none" : "0x" + Integer.toHexString(error).toUpperCase();
    }
}
