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
import static org.lwjgl.opengl.GL15C.glBindBuffer;
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
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glMultiDrawArraysIndirect;

final class ForgeMdicDebugShader {
    static final int COMMAND_BINDING_INDEX = 2;
    static final String ERROR_STAGE_NONE = "none";
    static final String ERROR_STAGE_BIND_SHADER = "MDIC_BIND_SHADER";
    static final String ERROR_STAGE_BIND_GEOMETRY_SSBO = "MDIC_BIND_GEOMETRY_SSBO";
    static final String ERROR_STAGE_BIND_COMMAND_SSBO = "MDIC_BIND_COMMAND_SSBO";
    static final String ERROR_STAGE_SET_UNIFORMS = "MDIC_SET_UNIFORMS";
    static final String ERROR_STAGE_DRAW_ARRAYS = "MDIC_DRAW_ARRAYS";
    static final String ERROR_STAGE_MULTI_DRAW_ARRAYS = "MDIC_MULTI_DRAW_ARRAYS";
    static final String ERROR_STAGE_INDIRECT_COMMAND_BUFFER_UPLOAD = "MDIC_INDIRECT_COMMAND_BUFFER_UPLOAD";
    static final String ERROR_STAGE_BIND_INDIRECT_COMMAND_BUFFER = "MDIC_BIND_INDIRECT_COMMAND_BUFFER";
    static final String ERROR_STAGE_MULTI_DRAW_ARRAYS_INDIRECT = "MDIC_MULTI_DRAW_ARRAYS_INDIRECT";

    private static final String DRAW_ID_EXTENSION_PLACEHOLDER = "${DRAW_ID_EXTENSION}";
    private static final String COMMAND_INDEX_UNIFORM_PLACEHOLDER = "${COMMAND_INDEX_UNIFORM}";
    private static final String COMMAND_INDEX_EXPR_PLACEHOLDER = "${COMMAND_INDEX_EXPR}";

    private static final String VERTEX_SHADER_TEMPLATE = """
            #version 430 core
            ${DRAW_ID_EXTENSION}

            layout(std430, binding = 0) readonly buffer GeometryRecords {
                uvec2 records[];
            };

            struct MdicCommand {
                uvec4 a;
                uvec4 b;
                uvec4 c;
            };

            layout(std430, binding = 2) readonly buffer MdicCommands {
                MdicCommand commands[];
            };

            uniform mat4 uModelView;
            uniform mat4 uProjection;
            ${COMMAND_INDEX_UNIFORM}
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

            vec3 bucketTint(uint bucketMask) {
                if ((bucketMask & 1u) != 0u) {
                    return vec3(0.85, 0.65, 1.00);
                } else if ((bucketMask & 2u) != 0u) {
                    return vec3(1.00, 0.85, 0.55);
                } else if ((bucketMask & 4u) != 0u) {
                    return vec3(0.75, 1.00, 0.75);
                } else if ((bucketMask & 8u) != 0u) {
                    return vec3(1.00, 0.65, 0.65);
                } else if ((bucketMask & 16u) != 0u) {
                    return vec3(0.65, 0.85, 1.00);
                } else if ((bucketMask & 32u) != 0u) {
                    return vec3(1.00, 1.00, 0.60);
                } else if ((bucketMask & 64u) != 0u) {
                    return vec3(0.65, 1.00, 0.95);
                } else if ((bucketMask & 128u) != 0u) {
                    return vec3(1.00, 0.70, 0.95);
                }
                return vec3(1.0);
            }

            void main() {
                uint commandIndex = ${COMMAND_INDEX_EXPR};
                MdicCommand command = commands[commandIndex];
                uint sectionId = command.a.x;
                uint geometryPtr = command.a.y;
                uint recordStart = command.a.z;
                uint recordIndex = geometryPtr + recordStart + uint(gl_VertexID / 6);
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

                vec3 sectionOrigin = vec3(
                    uintBitsToFloat(command.b.x),
                    uintBitsToFloat(command.b.y),
                    uintBitsToFloat(command.b.z)
                );
                float s = 1.0;
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

                uint colorSeed = sectionId ^ commandIndex ^ command.b.w ^ command.c.z;
                vec3 debugColor = colorFor(face, modelId, biomeId, lightId, colorSeed) * bucketTint(command.b.w);
                vColor = vec4(min(vec3(1.0), debugColor), uAlpha);
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
    private boolean indirectSupported;
    private boolean multiDrawIndirectSupported;
    private boolean drawIndirectBufferSupported;
    private boolean drawIdSupported;
    private boolean baseInstanceSupported;
    private String unsupportedReason = "none";
    private String multiDrawUnsupportedReason = "unknown";
    private String indirectUnsupportedReason = "unknown";
    private String glVersion = "unknown";
    private String glslVersion = "unknown";
    private String lastShaderError = "none";
    private String lastMultiDrawShaderError = "none";
    private String lastIndirectShaderError = "none";
    private final ProgramHandle loopProgram = new ProgramHandle();
    private final ProgramHandle multiDrawProgram = new ProgramHandle();
    private final ProgramHandle indirectProgram = new ProgramHandle();

    boolean ensureReady() {
        return this.ensureProgram(this.loopProgram, loopVertexShader(), true, "loop");
    }

    boolean ensureMultiDrawReady() {
        this.checkSupport();
        if (!this.multiDrawSupported) {
            this.lastMultiDrawShaderError = this.multiDrawUnsupportedReason;
            return false;
        }
        return this.ensureProgram(this.multiDrawProgram, drawIdVertexShader(), false, "multi-draw");
    }

    boolean ensureIndirectReady() {
        this.checkSupport();
        if (!this.indirectSupported) {
            this.lastIndirectShaderError = this.indirectUnsupportedReason;
            return false;
        }
        return this.ensureProgram(this.indirectProgram, drawIdVertexShader(), false, "indirect");
    }

    DrawCallResult drawCommandWithDiagnostics(
            int geometryBufferId,
            int commandBufferId,
            Matrix4f modelView,
            Matrix4f projection,
            int commandIndex,
            int vertexCount,
            float alpha
    ) {
        if (!this.ensureReady()) {
            return DrawCallResult.empty();
        }
        if (vertexCount <= 0) {
            return DrawCallResult.empty();
        }
        DrawCallResult bindResult = this.bindProgramAndBuffers(this.loopProgram, geometryBufferId, commandBufferId);
        if (!bindResult.ok()) {
            return bindResult;
        }
        DrawCallResult uniformResult = this.setCommonUniforms(this.loopProgram, modelView, projection, alpha);
        if (!uniformResult.ok()) {
            return uniformResult;
        }
        glUniform1i(this.loopProgram.commandIndexLocation, commandIndex);
        int glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_SET_UNIFORMS, 0, 0L);
        }

        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_DRAW_ARRAYS, 1, vertexCount);
        }
        return new DrawCallResult(GL_NO_ERROR, ERROR_STAGE_NONE, 1, vertexCount);
    }

    DrawCallResult drawMultiWithDiagnostics(
            int geometryBufferId,
            int commandBufferId,
            Matrix4f modelView,
            Matrix4f projection,
            ForgeMdicCommandList commandList,
            int maxCommands,
            int maxRecords,
            float alpha
    ) {
        if (!this.ensureMultiDrawReady()) {
            return DrawCallResult.empty();
        }
        if (commandBufferId == 0 || commandList == null || !commandList.isValid()) {
            return DrawCallResult.empty();
        }
        DrawBudget budget = DrawBudget.from(commandList, maxCommands, maxRecords);
        if (budget.logicalCommands <= 0 || budget.vertices <= 0L) {
            return DrawCallResult.empty();
        }

        DrawCallResult bindResult = this.bindProgramAndBuffers(this.multiDrawProgram, geometryBufferId, commandBufferId);
        if (!bindResult.ok()) {
            return bindResult;
        }
        DrawCallResult uniformResult = this.setCommonUniforms(this.multiDrawProgram, modelView, projection, alpha);
        if (!uniformResult.ok()) {
            return uniformResult;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer firsts = stack.mallocInt(budget.logicalCommands);
            IntBuffer counts = stack.mallocInt(budget.logicalCommands);
            long recordsLeft = Math.max(0, maxRecords);
            for (int i = 0; i < budget.logicalCommands; i++) {
                ForgeMdicCommand command = commandList.commands().get(i);
                int records = Math.max(0, command.recordCount());
                int drawnRecords = (int) Math.min(recordsLeft, records);
                firsts.put(0);
                counts.put(drawnRecords * 6);
                recordsLeft -= drawnRecords;
            }
            firsts.flip();
            counts.flip();
            glMultiDrawArrays(GL_TRIANGLES, firsts, counts);
        }

        int glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_MULTI_DRAW_ARRAYS, budget.logicalCommands, budget.vertices);
        }
        return new DrawCallResult(GL_NO_ERROR, ERROR_STAGE_NONE, budget.logicalCommands, budget.vertices);
    }

    DrawCallResult drawIndirectWithDiagnostics(
            int geometryBufferId,
            int commandBufferId,
            int indirectCommandBufferId,
            Matrix4f modelView,
            Matrix4f projection,
            ForgeMdicCommandList commandList,
            int maxCommands,
            int maxRecords,
            float alpha
    ) {
        if (!this.ensureIndirectReady()) {
            return DrawCallResult.empty();
        }
        if (commandBufferId == 0 || indirectCommandBufferId == 0 || commandList == null || !commandList.isValid()) {
            return DrawCallResult.empty();
        }
        DrawBudget budget = DrawBudget.from(commandList, maxCommands, maxRecords);
        if (budget.logicalCommands <= 0 || budget.vertices <= 0L) {
            return DrawCallResult.empty();
        }

        DrawCallResult bindResult = this.bindProgramAndBuffers(this.indirectProgram, geometryBufferId, commandBufferId);
        if (!bindResult.ok()) {
            return bindResult;
        }
        DrawCallResult uniformResult = this.setCommonUniforms(this.indirectProgram, modelView, projection, alpha);
        if (!uniformResult.ok()) {
            return uniformResult;
        }

        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, indirectCommandBufferId);
        int glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_INDIRECT_COMMAND_BUFFER, budget.logicalCommands, budget.vertices);
        }

        glMultiDrawArraysIndirect(GL_TRIANGLES, 0L, budget.logicalCommands, 0);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_MULTI_DRAW_ARRAYS_INDIRECT, budget.logicalCommands, budget.vertices);
        }
        return new DrawCallResult(GL_NO_ERROR, ERROR_STAGE_NONE, budget.logicalCommands, budget.vertices);
    }

    void unbind() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, COMMAND_BINDING_INDEX, 0);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
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
                this.indirectSupported,
                this.multiDrawIndirectSupported,
                this.drawIndirectBufferSupported,
                this.drawIdSupported,
                this.baseInstanceSupported,
                this.loopProgram.compiled,
                this.loopProgram.created,
                this.multiDrawProgram.compiled,
                this.multiDrawProgram.created,
                this.indirectProgram.compiled,
                this.indirectProgram.created,
                this.lastShaderError,
                this.lastMultiDrawShaderError,
                this.lastIndirectShaderError,
                this.unsupportedReason,
                this.multiDrawUnsupportedReason,
                this.indirectUnsupportedReason,
                this.glVersion,
                this.glslVersion,
                true
        );
    }

    private DrawCallResult bindProgramAndBuffers(ProgramHandle program, int geometryBufferId, int commandBufferId) {
        glUseProgram(program.programId);
        glBindVertexArray(program.vaoId);
        int glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_SHADER, 0, 0L);
        }

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, geometryBufferId);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_GEOMETRY_SSBO, 0, 0L);
        }

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, COMMAND_BINDING_INDEX, commandBufferId);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_COMMAND_SSBO, 0, 0L);
        }
        return DrawCallResult.empty();
    }

    private DrawCallResult setCommonUniforms(ProgramHandle program, Matrix4f modelView, Matrix4f projection, float alpha) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrixBuffer = stack.mallocFloat(16);
            modelView.get(matrixBuffer);
            glUniformMatrix4fv(program.modelViewLocation, false, matrixBuffer);
            matrixBuffer.clear();
            projection.get(matrixBuffer);
            glUniformMatrix4fv(program.projectionLocation, false, matrixBuffer);
        }
        glUniform1f(program.alphaLocation, alpha);
        int glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_SET_UNIFORMS, 0, 0L);
        }
        return DrawCallResult.empty();
    }

    private boolean ensureProgram(ProgramHandle program, String vertexShaderSource, boolean hasCommandIndexUniform, String label) {
        if (!RenderSystem.isOnRenderThread()) {
            this.recordShaderError(label, "not-render-thread");
            return false;
        }
        this.checkSupport();
        if (!this.shaderSupported) {
            return false;
        }
        if (program.created) {
            return true;
        }
        try {
            program.vertexShaderId = compile(GL_VERTEX_SHADER, vertexShaderSource);
            program.fragmentShaderId = compile(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            program.programId = glCreateProgram();
            glAttachShader(program.programId, program.vertexShaderId);
            glAttachShader(program.programId, program.fragmentShaderId);
            glLinkProgram(program.programId);
            if (glGetProgrami(program.programId, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException("MDIC debug " + label + " program link failed: " + glGetProgramInfoLog(program.programId));
            }
            program.vaoId = glGenVertexArrays();
            program.modelViewLocation = glGetUniformLocation(program.programId, "uModelView");
            program.projectionLocation = glGetUniformLocation(program.programId, "uProjection");
            program.alphaLocation = glGetUniformLocation(program.programId, "uAlpha");
            program.commandIndexLocation = hasCommandIndexUniform ? glGetUniformLocation(program.programId, "uCommandIndex") : -1;
            program.compiled = true;
            program.created = true;
            this.recordShaderError(label, "none");
            return true;
        } catch (RuntimeException e) {
            this.recordShaderError(label, e.getClass().getSimpleName() + ": " + e.getMessage());
            program.close();
            return false;
        }
    }

    private void recordShaderError(String label, String error) {
        if ("multi-draw".equals(label)) {
            this.lastMultiDrawShaderError = error;
        } else if ("indirect".equals(label)) {
            this.lastIndirectShaderError = error;
        } else {
            this.lastShaderError = error;
        }
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
            this.unsupportedReason = "OpenGL_4.3_required_for_SSBO";
            this.multiDrawUnsupportedReason = this.unsupportedReason;
            this.indirectUnsupportedReason = this.unsupportedReason;
            this.lastShaderError = this.unsupportedReason;
            this.lastMultiDrawShaderError = this.unsupportedReason;
            this.lastIndirectShaderError = this.unsupportedReason;
            return;
        }
        this.shaderSupported = true;
        this.unsupportedReason = "none";
        this.drawIdSupported = capabilities.GL_ARB_shader_draw_parameters;
        this.baseInstanceSupported = capabilities.OpenGL42 || capabilities.GL_ARB_base_instance || capabilities.OpenGL43;
        this.drawIndirectBufferSupported = capabilities.OpenGL40 || capabilities.GL_ARB_draw_indirect || capabilities.OpenGL43;
        this.multiDrawIndirectSupported = capabilities.OpenGL43 || capabilities.GL_ARB_multi_draw_indirect;
        this.multiDrawSupported = this.drawIdSupported;
        this.indirectSupported = this.multiDrawSupported && this.drawIndirectBufferSupported && this.multiDrawIndirectSupported;
        this.multiDrawUnsupportedReason = this.multiDrawSupported ? "none" : "ARB_shader_draw_parameters_required_for_gl_DrawID";
        if (this.indirectSupported) {
            this.indirectUnsupportedReason = "none";
        } else if (!this.multiDrawSupported) {
            this.indirectUnsupportedReason = this.multiDrawUnsupportedReason;
        } else if (!this.drawIndirectBufferSupported) {
            this.indirectUnsupportedReason = "GL_DRAW_INDIRECT_BUFFER_not_supported";
        } else {
            this.indirectUnsupportedReason = "glMultiDrawArraysIndirect_not_supported";
        }
    }

    private static String loopVertexShader() {
        return VERTEX_SHADER_TEMPLATE
                .replace(DRAW_ID_EXTENSION_PLACEHOLDER, "")
                .replace(COMMAND_INDEX_UNIFORM_PLACEHOLDER, "uniform int uCommandIndex;")
                .replace(COMMAND_INDEX_EXPR_PLACEHOLDER, "uint(uCommandIndex)");
    }

    private static String drawIdVertexShader() {
        return VERTEX_SHADER_TEMPLATE
                .replace(DRAW_ID_EXTENSION_PLACEHOLDER, "#extension GL_ARB_shader_draw_parameters : require")
                .replace(COMMAND_INDEX_UNIFORM_PLACEHOLDER, "")
                .replace(COMMAND_INDEX_EXPR_PLACEHOLDER, "uint(gl_DrawIDARB)");
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
        this.loopProgram.close();
        this.multiDrawProgram.close();
        this.indirectProgram.close();
    }

    private static final class ProgramHandle {
        private int programId;
        private int vertexShaderId;
        private int fragmentShaderId;
        private int vaoId;
        private int modelViewLocation = -1;
        private int projectionLocation = -1;
        private int commandIndexLocation = -1;
        private int alphaLocation = -1;
        private boolean compiled;
        private boolean created;

        private void close() {
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
            this.modelViewLocation = -1;
            this.projectionLocation = -1;
            this.commandIndexLocation = -1;
            this.alphaLocation = -1;
            this.compiled = false;
            this.created = false;
        }
    }

    private record DrawBudget(int logicalCommands, long vertices) {
        private static DrawBudget from(ForgeMdicCommandList commandList, int maxCommands, int maxRecords) {
            int commandLimit = Math.min(Math.max(0, maxCommands), commandList.commandCount());
            long recordsLeft = Math.max(0, maxRecords);
            int logicalCommands = 0;
            long vertices = 0L;
            for (int i = 0; i < commandLimit && recordsLeft > 0L; i++) {
                ForgeMdicCommand command = commandList.commands().get(i);
                int records = Math.max(0, command.recordCount());
                int drawnRecords = (int) Math.min(recordsLeft, records);
                logicalCommands++;
                vertices += (long) drawnRecords * 6L;
                recordsLeft -= drawnRecords;
            }
            return new DrawBudget(logicalCommands, vertices);
        }
    }

    record ShaderStatus(
            boolean shaderSupported,
            boolean multiDrawSupported,
            boolean indirectSupported,
            boolean multiDrawIndirectSupported,
            boolean drawIndirectBufferSupported,
            boolean drawIdSupported,
            boolean baseInstanceSupported,
            boolean shaderCompiled,
            boolean programCreated,
            boolean multiDrawShaderCompiled,
            boolean multiDrawProgramCreated,
            boolean indirectShaderCompiled,
            boolean indirectProgramCreated,
            String lastShaderError,
            String lastMultiDrawShaderError,
            String lastIndirectShaderError,
            String unsupportedReason,
            String multiDrawUnsupportedReason,
            String indirectUnsupportedReason,
            String glVersion,
            String glslVersion,
            boolean usesSsbo
    ) {
        boolean ok() {
            return this.shaderSupported && this.shaderCompiled && this.programCreated;
        }

        boolean shaderCompiledFor(ForgeMdicDebugDrawMode mode) {
            return switch (mode) {
                case LOOP_PER_COMMAND -> this.shaderCompiled;
                case MULTI_DRAW_ARRAYS -> this.multiDrawShaderCompiled;
                case MULTI_DRAW_ARRAYS_INDIRECT -> this.indirectShaderCompiled;
                case AUTO -> this.indirectSupported ? this.indirectShaderCompiled
                        : this.multiDrawSupported ? this.multiDrawShaderCompiled
                        : this.shaderCompiled;
            };
        }

        boolean programCreatedFor(ForgeMdicDebugDrawMode mode) {
            return switch (mode) {
                case LOOP_PER_COMMAND -> this.programCreated;
                case MULTI_DRAW_ARRAYS -> this.multiDrawProgramCreated;
                case MULTI_DRAW_ARRAYS_INDIRECT -> this.indirectProgramCreated;
                case AUTO -> this.indirectSupported ? this.indirectProgramCreated
                        : this.multiDrawSupported ? this.multiDrawProgramCreated
                        : this.programCreated;
            };
        }

        boolean readyFor(ForgeMdicDebugDrawMode mode) {
            return this.shaderSupported && this.shaderCompiledFor(mode) && this.programCreatedFor(mode);
        }
    }

    record DrawCallResult(int glError, String stage, int logicalCommands, long vertices) {
        static DrawCallResult empty() {
            return new DrawCallResult(GL_NO_ERROR, ERROR_STAGE_NONE, 0, 0L);
        }

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
