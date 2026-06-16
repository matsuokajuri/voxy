package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB;
import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL11C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11C.GL_VERSION;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glGetError;
import static org.lwjgl.opengl.GL11C.glGetString;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
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
import static org.lwjgl.opengl.GL20C.glUniform1iv;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.glMultiDrawElementsIndirect;

final class ForgeTexturedMdicDebugShader implements AutoCloseable {
    static final String ERROR_STAGE_NONE = "none";
    static final String ERROR_STAGE_BIND_SHADER = "TEXTURED_MDIC_BIND_SHADER";
    static final String ERROR_STAGE_BIND_GEOMETRY_SSBO = "TEXTURED_MDIC_BIND_GEOMETRY_SSBO";
    static final String ERROR_STAGE_BIND_COMMAND_SSBO = "TEXTURED_MDIC_BIND_COMMAND_SSBO";
    static final String ERROR_STAGE_BIND_MODEL_DATA_SSBO = "TEXTURED_MDIC_BIND_MODEL_DATA_SSBO";
    static final String ERROR_STAGE_BIND_MODEL_COLOUR_SSBO = "TEXTURED_MDIC_BIND_MODEL_COLOUR_SSBO";
    static final String ERROR_STAGE_BIND_MODEL_VALIDITY_SSBO = "TEXTURED_MDIC_BIND_MODEL_VALIDITY_SSBO";
    static final String ERROR_STAGE_BIND_ATLAS_TEXTURE = "TEXTURED_MDIC_BIND_ATLAS_TEXTURE";
    static final String ERROR_STAGE_SET_UNIFORMS = "TEXTURED_MDIC_SET_UNIFORMS";
    static final String ERROR_STAGE_BIND_ELEMENT_ARRAY_BUFFER = "TEXTURED_MDIC_BIND_ELEMENT_ARRAY_BUFFER";
    static final String ERROR_STAGE_BIND_ELEMENTS_INDIRECT_BUFFER = "TEXTURED_MDIC_BIND_ELEMENTS_INDIRECT_BUFFER";
    static final String ERROR_STAGE_BIND_PARAMETER_BUFFER = "TEXTURED_MDIC_BIND_PARAMETER_BUFFER";
    static final String ERROR_STAGE_MULTI_DRAW_ELEMENTS_INDIRECT = "TEXTURED_MDIC_MULTI_DRAW_ELEMENTS_INDIRECT";
    static final String ERROR_STAGE_MULTI_DRAW_ELEMENTS_INDIRECT_COUNT = "TEXTURED_MDIC_MULTI_DRAW_ELEMENTS_INDIRECT_COUNT";

    private static final String VERTEX_SHADER = """
            #version 430 core
            #extension GL_ARB_shader_draw_parameters : require

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

            struct BlockModel {
                uint faceData[6];
                uint flagsA;
                uint colourTint;
                uint customId;
                uint _pad[7];
            };

            layout(std430, binding = 3) readonly buffer ModelBuffer {
                BlockModel modelData[];
            };

            layout(std430, binding = 4) readonly buffer ModelColourBuffer {
                uint colourData[];
            };

            layout(std430, binding = 6) readonly buffer ModelValidityBuffer {
                uint modelValidity[];
            };

            uniform mat4 uModelView;
            uniform mat4 uProjection;
            uniform int uSampleModelId;
            uniform int uSampleModelCount;
            uniform int uSampleModelIds[16];
            uniform int uInputMode;
            uniform int uMaxModelId;
            uniform int uFullAtlas;
            uniform vec2 uAtlasSize;

            out vec2 vUv;
            out flat uint vTintColour;
            out float vVisible;

            bool isSampleModel(uint modelId) {
                if (uInputMode == 1) {
                    return modelId <= uint(uMaxModelId) && modelValidity[modelId] != 0u;
                }
                if (uSampleModelCount <= 0) {
                    return modelId == uint(uSampleModelId);
                }
                int limit = min(uSampleModelCount, 16);
                for (int i = 0; i < limit; i++) {
                    if (modelId == uint(uSampleModelIds[i])) {
                        return true;
                    }
                }
                return false;
            }

            vec4 extractFaceSizes(uint faceData) {
                return (vec4(faceData & 15u, (faceData >> 4u) & 15u, (faceData >> 8u) & 15u, (faceData >> 12u) & 15u) / 16.0)
                    + vec4(0.0, 1.0 / 16.0, 0.0, 1.0 / 16.0);
            }

            vec2 uvFor(uint modelId, uint face, int corner, uint faceData) {
                uint tileX;
                uint tileY;
                if (uFullAtlas != 0) {
                    uint modelTileX = modelId & 255u;
                    uint modelTileY = (modelId >> 8) & 255u;
                    uint baseX = modelTileX * 16u * 3u;
                    uint baseY = modelTileY * 16u * 2u;
                    tileX = baseX + ((face >> 1u) * 16u);
                    tileY = baseY + ((face & 1u) * 16u);
                } else {
                    tileX = (face >> 1u) * 16u;
                    tileY = (face & 1u) * 16u;
                }

                vec4 faceSize = uInputMode == 1 ? extractFaceSizes(faceData) : vec4(0.0, 1.0, 0.0, 1.0);
                vec2 faceUv0 = vec2(faceSize.x, faceSize.z);
                vec2 faceUv1 = vec2(faceSize.y, faceSize.w);
                vec2 uv0 = (vec2(float(tileX), float(tileY)) + faceUv0 * 16.0 + vec2(0.5)) / uAtlasSize;
                vec2 uv1 = (vec2(float(tileX), float(tileY)) + faceUv1 * 16.0 - vec2(0.5)) / uAtlasSize;
                if (corner == 1) {
                    return vec2(uv1.x, uv0.y);
                } else if (corner == 2) {
                    return uv1;
                } else if (corner == 3) {
                    return vec2(uv0.x, uv1.y);
                }
                return uv0;
            }

            void main() {
                uint commandIndex = uint(gl_BaseInstanceARB);
                MdicCommand command = commands[commandIndex];
                uint geometryPtr = command.a.y;
                uint recordStart = command.a.z;
                uint recordIndex = geometryPtr + recordStart + uint(gl_VertexID / 4);
                int corner = gl_VertexID - (gl_VertexID / 4) * 4;
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

                if (!isSampleModel(modelId)) {
                    vVisible = 0.0;
                    vUv = vec2(0.0);
                    vTintColour = 0xFFFFFFFFu;
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    return;
                }
                uint faceData = 0u;
                uint tintColour = 0xFFFFFFFFu;
                if (uInputMode == 1) {
                    BlockModel model = modelData[modelId];
                    faceData = model.faceData[face];
                    if (faceData == 0xFFFFFFFFu) {
                        vVisible = 0.0;
                        vUv = vec2(0.0);
                        vTintColour = 0xFFFFFFFFu;
                        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                        return;
                    }
                    tintColour = colourData[modelId];
                    if (tintColour == 0xFFFFFFFFu) {
                        tintColour = model.colourTint;
                    }
                }

                vec3 sectionOrigin = vec3(
                    uintBitsToFloat(command.b.x),
                    uintBitsToFloat(command.b.y),
                    uintBitsToFloat(command.b.z)
                );
                vec3 p0 = vec3(float(localX), float(localY), float(localZ));
                vec3 p1 = p0;
                vec3 p2 = p0;
                vec3 p3 = p0;
                float len = float(lenCells);
                float wid = float(widthCells);

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
                } else if (corner == 2) {
                    local = p3;
                } else if (corner == 3) {
                    local = p2;
                }

                vVisible = 1.0;
                vUv = uvFor(modelId, face, corner, faceData);
                vTintColour = tintColour;
                gl_Position = uProjection * uModelView * vec4(sectionOrigin + local, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 430 core

            uniform sampler2D uAtlas;
            uniform float uAlpha;

            in vec2 vUv;
            in flat uint vTintColour;
            in float vVisible;
            layout(location = 0) out vec4 fragColor;

            void main() {
                if (vVisible < 0.5) {
                    discard;
                }
                vec4 texel = texture(uAtlas, vUv);
                if (texel.a <= 0.01) {
                    discard;
                }
                if (vTintColour != 0xFFFFFFFFu) {
                    vec3 tint = vec3(
                        float((vTintColour >> 16u) & 255u),
                        float((vTintColour >> 8u) & 255u),
                        float(vTintColour & 255u)
                    ) / 255.0;
                    texel.rgb *= tint;
                }
                fragColor = vec4(texel.rgb, texel.a * uAlpha);
            }
            """;

    private boolean supportChecked;
    private boolean shaderSupported;
    private boolean elementsIndirectSupported;
    private boolean elementsIndirectCountSupported;
    private String unsupportedReason = "unknown";
    private String glVersion = "unknown";
    private String glslVersion = "unknown";
    private int programId;
    private int vaoId;
    private int modelViewUniform = -1;
    private int projectionUniform = -1;
    private int alphaUniform = -1;
    private int atlasUniform = -1;
    private int sampleModelIdUniform = -1;
    private int sampleModelCountUniform = -1;
    private int sampleModelIdsUniform = -1;
    private int inputModeUniform = -1;
    private int maxModelIdUniform = -1;
    private int fullAtlasUniform = -1;
    private int atlasSizeUniform = -1;
    private boolean shaderCompiled;
    private boolean programCreated;
    private String lastShaderError = "none";

    boolean ensureReady() {
        if (this.programId != 0 && this.programCreated) {
            return true;
        }
        if (!RenderSystem.isOnRenderThread()) {
            this.lastShaderError = "not-render-thread";
            return false;
        }
        this.checkSupport();
        if (!this.shaderSupported) {
            this.lastShaderError = this.unsupportedReason;
            return false;
        }

        int vertex = 0;
        int fragment = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, VERTEX_SHADER);
            fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            int program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException("program link failed: " + sanitize(glGetProgramInfoLog(program)));
            }
            this.programId = program;
            this.vaoId = glGenVertexArrays();
            this.modelViewUniform = glGetUniformLocation(program, "uModelView");
            this.projectionUniform = glGetUniformLocation(program, "uProjection");
            this.alphaUniform = glGetUniformLocation(program, "uAlpha");
            this.atlasUniform = glGetUniformLocation(program, "uAtlas");
            this.sampleModelIdUniform = glGetUniformLocation(program, "uSampleModelId");
            this.sampleModelCountUniform = glGetUniformLocation(program, "uSampleModelCount");
            this.sampleModelIdsUniform = glGetUniformLocation(program, "uSampleModelIds");
            if (this.sampleModelIdsUniform < 0) {
                this.sampleModelIdsUniform = glGetUniformLocation(program, "uSampleModelIds[0]");
            }
            this.inputModeUniform = glGetUniformLocation(program, "uInputMode");
            this.maxModelIdUniform = glGetUniformLocation(program, "uMaxModelId");
            this.fullAtlasUniform = glGetUniformLocation(program, "uFullAtlas");
            this.atlasSizeUniform = glGetUniformLocation(program, "uAtlasSize");
            this.shaderCompiled = true;
            this.programCreated = true;
            this.lastShaderError = "none";
            return true;
        } catch (RuntimeException e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.closeOnRenderThread();
            this.lastShaderError = error;
            return false;
        } finally {
            if (vertex != 0) {
                glDeleteShader(vertex);
            }
            if (fragment != 0) {
                glDeleteShader(fragment);
            }
        }
    }

    DrawCallResult drawElementsIndirectWithDiagnostics(
            int geometryBufferId,
            int commandBufferId,
            int sharedIndexBufferId,
            int elementsIndirectCommandBufferId,
            int atlasTextureId,
            boolean fullAtlas,
            int atlasWidth,
            int atlasHeight,
            int sampleModelId,
            int[] sampleModelIds,
            ForgeTexturedMdicDebugInputMode inputMode,
            int modelDataBufferId,
            int modelColourBufferId,
            int modelValidityBufferId,
            int maxModelId,
            Matrix4f modelView,
            Matrix4f projection,
            ForgeMdicCommandList commandList,
            int maxCommands,
            int maxRecords,
            float alpha
    ) {
        if (!this.ensureReady()) {
            return DrawCallResult.empty();
        }
        if (!this.elementsIndirectSupported || atlasTextureId == 0 || commandBufferId == 0 || sharedIndexBufferId == 0 || elementsIndirectCommandBufferId == 0 || commandList == null || !commandList.isValid()) {
            return DrawCallResult.empty();
        }
        DrawBudget budget = DrawBudget.from(commandList, maxCommands, maxRecords);
        if (budget.logicalCommands <= 0 || budget.indices <= 0L) {
            return DrawCallResult.empty();
        }
        DrawCallResult bind = this.bindProgramAndBuffers(geometryBufferId, commandBufferId, atlasTextureId, fullAtlas, atlasWidth, atlasHeight, sampleModelId, sampleModelIds, inputMode, modelDataBufferId, modelColourBufferId, modelValidityBufferId, maxModelId, modelView, projection, alpha);
        if (!bind.ok()) {
            return bind;
        }
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sharedIndexBufferId);
        int glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_ELEMENT_ARRAY_BUFFER, budget.logicalCommands, budget.logicalVertices);
        }
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, elementsIndirectCommandBufferId);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_ELEMENTS_INDIRECT_BUFFER, budget.logicalCommands, budget.logicalVertices);
        }
        glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0L, budget.logicalCommands, 0);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_MULTI_DRAW_ELEMENTS_INDIRECT, budget.logicalCommands, budget.logicalVertices);
        }
        return new DrawCallResult(GL_NO_ERROR, ERROR_STAGE_NONE, budget.logicalCommands, budget.logicalVertices);
    }

    DrawCallResult drawElementsIndirectCountWithDiagnostics(
            int geometryBufferId,
            int commandBufferId,
            int sharedIndexBufferId,
            int elementsIndirectCommandBufferId,
            int drawCountBufferId,
            int drawCountValue,
            int maxDrawCount,
            int atlasTextureId,
            boolean fullAtlas,
            int atlasWidth,
            int atlasHeight,
            int sampleModelId,
            int[] sampleModelIds,
            ForgeTexturedMdicDebugInputMode inputMode,
            int modelDataBufferId,
            int modelColourBufferId,
            int modelValidityBufferId,
            int maxModelId,
            Matrix4f modelView,
            Matrix4f projection,
            ForgeMdicCommandList commandList,
            int maxCommands,
            int maxRecords,
            float alpha
    ) {
        if (!this.ensureReady()) {
            return DrawCallResult.empty();
        }
        if (!this.elementsIndirectCountSupported || atlasTextureId == 0 || commandBufferId == 0 || sharedIndexBufferId == 0 || elementsIndirectCommandBufferId == 0 || drawCountBufferId == 0 || commandList == null || !commandList.isValid()) {
            return DrawCallResult.empty();
        }
        int clampedDrawCount = Math.min(Math.max(0, drawCountValue), Math.max(0, maxDrawCount));
        DrawBudget budget = DrawBudget.from(commandList, Math.min(maxCommands, clampedDrawCount), maxRecords);
        if (budget.logicalCommands <= 0 || budget.indices <= 0L || clampedDrawCount <= 0) {
            return DrawCallResult.empty();
        }
        DrawCallResult bind = this.bindProgramAndBuffers(geometryBufferId, commandBufferId, atlasTextureId, fullAtlas, atlasWidth, atlasHeight, sampleModelId, sampleModelIds, inputMode, modelDataBufferId, modelColourBufferId, modelValidityBufferId, maxModelId, modelView, projection, alpha);
        if (!bind.ok()) {
            return bind;
        }
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sharedIndexBufferId);
        int glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_ELEMENT_ARRAY_BUFFER, budget.logicalCommands, budget.logicalVertices);
        }
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, elementsIndirectCommandBufferId);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_ELEMENTS_INDIRECT_BUFFER, budget.logicalCommands, budget.logicalVertices);
        }
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, drawCountBufferId);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_PARAMETER_BUFFER, budget.logicalCommands, budget.logicalVertices);
        }
        glMultiDrawElementsIndirectCountARB(GL_TRIANGLES, GL_UNSIGNED_INT, 0L, 0L, maxDrawCount, 0);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_MULTI_DRAW_ELEMENTS_INDIRECT_COUNT, budget.logicalCommands, budget.logicalVertices);
        }
        return new DrawCallResult(GL_NO_ERROR, ERROR_STAGE_NONE, budget.logicalCommands, budget.logicalVertices);
    }

    ShaderStatus createStatusSnapshot() {
        if (RenderSystem.isOnRenderThread()) {
            this.checkSupport();
        }
        return new ShaderStatus(
                this.shaderSupported,
                this.elementsIndirectSupported,
                this.elementsIndirectCountSupported,
                this.shaderCompiled,
                this.programCreated && this.programId != 0,
                this.lastShaderError,
                this.unsupportedReason,
                this.glVersion,
                this.glslVersion
        );
    }

    void unbind() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeMdicDebugShader.COMMAND_BINDING_INDEX, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBridge.MODEL_DATA_BINDING_INDEX, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBridge.MODEL_COLOUR_BINDING_INDEX, 0);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBridge.MODEL_VALIDITY_BINDING_INDEX, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, 0);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    @Override
    public void close() {
        if (RenderSystem.isOnRenderThread()) {
            this.closeOnRenderThread();
        } else {
            RenderSystem.recordRenderCall(this::closeOnRenderThread);
        }
    }

    private DrawCallResult bindProgramAndBuffers(
            int geometryBufferId,
            int commandBufferId,
            int atlasTextureId,
            boolean fullAtlas,
            int atlasWidth,
            int atlasHeight,
            int sampleModelId,
            int[] sampleModelIds,
            ForgeTexturedMdicDebugInputMode inputMode,
            int modelDataBufferId,
            int modelColourBufferId,
            int modelValidityBufferId,
            int maxModelId,
            Matrix4f modelView,
            Matrix4f projection,
            float alpha
    ) {
        glUseProgram(this.programId);
        glBindVertexArray(this.vaoId);
        int glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_SHADER, 0, 0L);
        }
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, geometryBufferId);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_GEOMETRY_SSBO, 0, 0L);
        }
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeMdicDebugShader.COMMAND_BINDING_INDEX, commandBufferId);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_COMMAND_SSBO, 0, 0L);
        }
        boolean useFormalInputBridge = inputMode == ForgeTexturedMdicDebugInputMode.FORMAL_INPUT_BRIDGE;
        if (useFormalInputBridge && (modelDataBufferId == 0 || modelColourBufferId == 0 || modelValidityBufferId == 0 || maxModelId < 0)) {
            return DrawCallResult.empty();
        }
        if (useFormalInputBridge) {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBridge.MODEL_DATA_BINDING_INDEX, modelDataBufferId);
            glError = glGetError();
            if (glError != GL_NO_ERROR) {
                return new DrawCallResult(glError, ERROR_STAGE_BIND_MODEL_DATA_SSBO, 0, 0L);
            }
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBridge.MODEL_COLOUR_BINDING_INDEX, modelColourBufferId);
            glError = glGetError();
            if (glError != GL_NO_ERROR) {
                return new DrawCallResult(glError, ERROR_STAGE_BIND_MODEL_COLOUR_SSBO, 0, 0L);
            }
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, ForgeFormalShaderInputBridge.MODEL_VALIDITY_BINDING_INDEX, modelValidityBufferId);
            glError = glGetError();
            if (glError != GL_NO_ERROR) {
                return new DrawCallResult(glError, ERROR_STAGE_BIND_MODEL_VALIDITY_SSBO, 0, 0L);
            }
        }
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlasTextureId);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_BIND_ATLAS_TEXTURE, 0, 0L);
        }
        this.setUniforms(modelView, projection, alpha, sampleModelId, sampleModelIds, inputMode, maxModelId, fullAtlas, atlasWidth, atlasHeight);
        glError = glGetError();
        if (glError != GL_NO_ERROR) {
            return new DrawCallResult(glError, ERROR_STAGE_SET_UNIFORMS, 0, 0L);
        }
        return DrawCallResult.empty();
    }

    private void setUniforms(
            Matrix4f modelView,
            Matrix4f projection,
            float alpha,
            int sampleModelId,
            int[] sampleModelIds,
            ForgeTexturedMdicDebugInputMode inputMode,
            int maxModelId,
            boolean fullAtlas,
            int atlasWidth,
            int atlasHeight
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrixBuffer = stack.mallocFloat(16);
            modelView.get(matrixBuffer);
            glUniformMatrix4fv(this.modelViewUniform, false, matrixBuffer);
            matrixBuffer.clear();
            projection.get(matrixBuffer);
            glUniformMatrix4fv(this.projectionUniform, false, matrixBuffer);
        }
        glUniform1f(this.alphaUniform, alpha);
        glUniform1i(this.atlasUniform, 0);
        glUniform1i(this.sampleModelIdUniform, sampleModelId);
        int[] sanitizedIds = sanitizeSampleModelIds(sampleModelId, sampleModelIds);
        glUniform1i(this.sampleModelCountUniform, sanitizedIds.length);
        if (this.sampleModelIdsUniform >= 0) {
            glUniform1iv(this.sampleModelIdsUniform, sanitizedIds);
        }
        glUniform1i(this.inputModeUniform, inputMode == ForgeTexturedMdicDebugInputMode.FORMAL_INPUT_BRIDGE ? 1 : 0);
        glUniform1i(this.maxModelIdUniform, Math.max(-1, maxModelId));
        glUniform1i(this.fullAtlasUniform, fullAtlas ? 1 : 0);
        glUniform2f(this.atlasSizeUniform, Math.max(1, atlasWidth), Math.max(1, atlasHeight));
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
            this.elementsIndirectSupported = false;
            this.elementsIndirectCountSupported = false;
            this.unsupportedReason = "OpenGL_4.3_required_for_SSBO";
            return;
        }
        if (!capabilities.GL_ARB_shader_draw_parameters) {
            this.shaderSupported = false;
            this.elementsIndirectSupported = false;
            this.elementsIndirectCountSupported = false;
            this.unsupportedReason = "ARB_shader_draw_parameters_required_for_gl_BaseInstance";
            return;
        }
        boolean drawIndirect = capabilities.OpenGL40 || capabilities.GL_ARB_draw_indirect || capabilities.OpenGL43;
        boolean multiDrawIndirect = capabilities.OpenGL43 || capabilities.GL_ARB_multi_draw_indirect;
        boolean baseInstance = capabilities.OpenGL42 || capabilities.GL_ARB_base_instance || capabilities.OpenGL43;
        this.shaderSupported = true;
        this.elementsIndirectSupported = drawIndirect && multiDrawIndirect && baseInstance;
        this.elementsIndirectCountSupported = this.elementsIndirectSupported
                && capabilities.GL_ARB_indirect_parameters
                && capabilities.glMultiDrawElementsIndirectCountARB != 0L;
        if (!this.elementsIndirectSupported) {
            this.unsupportedReason = "draw-elements-indirect-or-baseInstance-not-supported";
        } else if (!this.elementsIndirectCountSupported) {
            this.unsupportedReason = "indirect-count-not-supported";
        } else {
            this.unsupportedReason = "none";
        }
    }

    private void closeOnRenderThread() {
        if (this.programId != 0) {
            glDeleteProgram(this.programId);
        }
        if (this.vaoId != 0) {
            glDeleteVertexArrays(this.vaoId);
        }
        this.programId = 0;
        this.vaoId = 0;
        this.modelViewUniform = -1;
        this.projectionUniform = -1;
        this.alphaUniform = -1;
        this.atlasUniform = -1;
        this.sampleModelIdUniform = -1;
        this.sampleModelCountUniform = -1;
        this.sampleModelIdsUniform = -1;
        this.inputModeUniform = -1;
        this.maxModelIdUniform = -1;
        this.fullAtlasUniform = -1;
        this.atlasSizeUniform = -1;
        this.shaderCompiled = false;
        this.programCreated = false;
        this.lastShaderError = "none";
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("shader compile failed: " + sanitize(log));
        }
        return shader;
    }

    private static String sanitize(String log) {
        if (log == null || log.isBlank()) {
            return "unknown";
        }
        return log.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static int[] sanitizeSampleModelIds(int sampleModelId, int[] sampleModelIds) {
        int[] source = sampleModelIds == null || sampleModelIds.length == 0 ? new int[]{sampleModelId} : sampleModelIds;
        int count = Math.min(16, source.length);
        int[] result = new int[count];
        for (int i = 0; i < count; i++) {
            result[i] = source[i];
        }
        return result;
    }

    static String formatGlError(int error) {
        return error == GL_NO_ERROR ? "none" : "0x" + Integer.toHexString(error).toUpperCase();
    }

    private record DrawBudget(int logicalCommands, long logicalVertices, long indices) {
        static DrawBudget from(ForgeMdicCommandList commandList, int maxCommands, int maxRecords) {
            int commandLimit = Math.min(Math.max(0, maxCommands), commandList.commandCount());
            long recordsLeft = Math.max(0, maxRecords);
            int logicalCommands = 0;
            long logicalVertices = 0L;
            long indices = 0L;
            for (int i = 0; i < commandLimit && recordsLeft > 0L; i++) {
                ForgeMdicCommand command = commandList.commands().get(i);
                int records = Math.max(0, command.recordCount());
                int drawnRecords = (int) Math.min(recordsLeft, records);
                logicalCommands++;
                logicalVertices += (long) drawnRecords * ForgeMdicDebugSharedIndexBuffer.VERTICES_PER_QUAD;
                indices += (long) drawnRecords * ForgeMdicDebugSharedIndexBuffer.INDICES_PER_QUAD;
                recordsLeft -= drawnRecords;
            }
            return new DrawBudget(logicalCommands, logicalVertices, indices);
        }
    }

    record ShaderStatus(
            boolean shaderSupported,
            boolean elementsIndirectSupported,
            boolean elementsIndirectCountSupported,
            boolean shaderCompiled,
            boolean programCreated,
            String lastShaderError,
            String unsupportedReason,
            String glVersion,
            String glslVersion
    ) {
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
}
