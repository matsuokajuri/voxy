package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.util.MemoryBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;

import static org.lwjgl.opengl.ARBDirectStateAccess.nglTextureSubImage2D;

final class ForgeOriginalVoxyModelStore {
    static final int MODEL_SIZE = 64;
    static final int MODEL_CAPACITY = 1 << 16;
    static final long MODEL_DATA_BYTES = (long) MODEL_SIZE * MODEL_CAPACITY;
    static final long MODEL_COLOUR_BYTES = (long) Integer.BYTES * MODEL_CAPACITY;

    private static final ArrayList<Integer> MODEL_TEXTURE_CACHE = new ArrayList<>();

    private int modelBufferId;
    private int modelColourBufferId;
    private int texturesId;
    private int blockSamplerId;
    private boolean ready;
    private String lastFailureReason = "none";

    String build(Minecraft minecraft) {
        if (this.ready) {
            return "none";
        }
        if (!RenderSystem.isOnRenderThread()) {
            return this.fail("model-store-build-not-render-thread");
        }
        try {
            this.modelBufferId = createZeroedBuffer(MODEL_DATA_BYTES);
            this.modelColourBufferId = createZeroedBuffer(MODEL_COLOUR_BYTES);
            this.texturesId = getOrCreateModelStoreTextureAtlas();
            this.blockSamplerId = GL33C.glGenSamplers();
            if (this.blockSamplerId == 0) {
                return this.fail("model-store-sampler-create-failed");
            }

            int mipLevel = minecraft == null || minecraft.options == null
                    ? ForgeOriginalVoxyMipGen.LAYERS - 1
                    : Math.min(minecraft.options.mipmapLevels().get(), ForgeOriginalVoxyMipGen.LAYERS - 1);
            GL33C.glSamplerParameteri(this.blockSamplerId, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST_MIPMAP_LINEAR);
            GL33C.glSamplerParameteri(this.blockSamplerId, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL33C.glSamplerParameteri(this.blockSamplerId, GL12C.GL_TEXTURE_MIN_LOD, 0);
            GL33C.glSamplerParameteri(this.blockSamplerId, GL12C.GL_TEXTURE_MAX_LOD, mipLevel);

            int error = GL11C.glGetError();
            if (error != GL11C.GL_NO_ERROR) {
                return this.fail("model-store-build-" + glErrorName(error));
            }
            this.ready = true;
            this.lastFailureReason = "none";
            return "none";
        } catch (RuntimeException e) {
            return this.fail("model-store-build-" + e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }

    boolean canUploadOriginalVoxyModel() {
        return RenderSystem.isOnRenderThread()
                && this.ready
                && this.modelBufferId != 0
                && this.modelColourBufferId != 0
                && this.texturesId != 0
                && this.blockSamplerId != 0;
    }

    boolean originalModelStoreOwnerReady() {
        return this.ready
                && this.modelBufferId != 0
                && this.modelColourBufferId != 0
                && this.texturesId != 0
                && this.blockSamplerId != 0;
    }

    String lastFailureReason() {
        return this.lastFailureReason;
    }

    void bind(int modelBindingIndex, int colourBindingIndex, int textureBindingIndex) {
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, modelBindingIndex, this.modelBufferId);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, colourBindingIndex, this.modelColourBufferId);
        GL45C.glBindTextureUnit(textureBindingIndex, this.texturesId);
        GL33C.glBindSampler(textureBindingIndex, this.blockSamplerId);
    }

    String uploadOriginalVoxyModelRecord(int modelId, MemoryBuffer model) {
        if (!this.canUploadOriginalVoxyModel()) {
            return "original-model-store-not-upload-ready";
        }
        if (!isValidModelId(modelId)) {
            return "invalid-original-model-id-" + modelId;
        }
        if (model == null || model.size != MODEL_SIZE) {
            return "invalid-original-model-record-size";
        }
        model.cpyTo(ForgeOriginalVoxyUploadStream.instance().upload(this.modelBufferId, (long) modelId * MODEL_SIZE, MODEL_SIZE));
        return this.glErrorOrNone("original-model-record-upload-stream");
    }

    String uploadOriginalVoxyModelRecordWord(int modelId, int wordIndex, int value) {
        if (!this.canUploadOriginalVoxyModel()) {
            return "original-model-store-not-upload-ready";
        }
        if (!isValidModelId(modelId)) {
            return "invalid-original-model-id-" + modelId;
        }
        if (wordIndex < 0 || wordIndex >= MODEL_SIZE / Integer.BYTES) {
            return "invalid-original-model-record-word-index-" + wordIndex;
        }
        long ptr = ForgeOriginalVoxyUploadStream.instance().upload(
                this.modelBufferId,
                ((long) modelId * MODEL_SIZE) + ((long) wordIndex * Integer.BYTES),
                Integer.BYTES);
        MemoryUtil.memPutInt(ptr, value);
        return this.glErrorOrNone("original-model-record-word-upload-stream");
    }

    String uploadOriginalVoxyModelColourRange(int baseIndex, MemoryBuffer colours) {
        if (!this.canUploadOriginalVoxyModel()) {
            return "original-model-store-not-upload-ready";
        }
        if (baseIndex < 0 || colours == null || (long) baseIndex * Integer.BYTES + colours.size > MODEL_COLOUR_BYTES) {
            return "invalid-original-model-colour-range";
        }
        colours.cpyTo(ForgeOriginalVoxyUploadStream.instance().upload(this.modelColourBufferId, (long) baseIndex * Integer.BYTES, colours.size));
        return this.glErrorOrNone("original-model-colour-range-upload-stream");
    }

    String uploadOriginalVoxyBiomeUpload(MemoryBuffer biomeColourBuffer, MemoryBuffer modelBiomeIndexPairs) {
        if (!this.canUploadOriginalVoxyModel()) {
            return "original-model-store-not-upload-ready";
        }
        if (biomeColourBuffer == null || modelBiomeIndexPairs == null || modelBiomeIndexPairs.size % Long.BYTES != 0) {
            return "invalid-original-biome-upload";
        }
        biomeColourBuffer.cpyTo(ForgeOriginalVoxyUploadStream.instance().upload(this.modelColourBufferId, 0L, biomeColourBuffer.size));
        long ptr = modelBiomeIndexPairs.address;
        for (long offset = 0; offset < modelBiomeIndexPairs.size; offset += Long.BYTES) {
            long pair = MemoryUtil.memGetLong(ptr);
            ptr += Long.BYTES;
            int modelId = (int) pair;
            int biomeIndex = (int) (pair >>> 32);
            MemoryUtil.memPutInt(
                    ForgeOriginalVoxyUploadStream.instance().upload(
                            this.modelBufferId,
                            ((long) modelId * MODEL_SIZE) + ForgeModelStoreFormalLayout.WORD_COLOUR_TINT * (long) Integer.BYTES,
                            Integer.BYTES),
                    biomeIndex);
        }
        return this.glErrorOrNone("original-biome-upload-stream");
    }

    String uploadOriginalVoxyModelTextureMipChain(int modelId, MemoryBuffer texture) {
        if (!this.canUploadOriginalVoxyModel()) {
            return "original-model-store-not-upload-ready";
        }
        if (!isValidModelId(modelId)) {
            return "invalid-original-model-id-" + modelId;
        }
        if (texture == null || texture.size < ForgeOriginalVoxyMipGen.UPLOADED_MIP_CHAIN_BYTES) {
            return "invalid-original-model-texture-buffer-size";
        }

        int x = (modelId & 0xFF) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X;
        int y = ((modelId >> 8) & 0xFF) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_Y;
        long cAddr = texture.address;
        for (int level = 0; level < ForgeOriginalVoxyMipGen.LAYERS; level++) {
            int width = (ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X) >> level;
            int height = (ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_Y) >> level;
            nglTextureSubImage2D(this.texturesId, level, x >> level, y >> level, width, height, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, cAddr);
            cAddr += (long) width * height * ForgeModelAtlasPixelSample.BYTES_PER_PIXEL;
        }
        return this.glErrorOrNone("original-model-texture-upload");
    }

    String readOriginalVoxyModelRecord(int modelId, byte[] out) {
        if (!this.canUploadOriginalVoxyModel()) {
            return "original-model-store-not-upload-ready";
        }
        if (!isValidModelId(modelId)) {
            return "invalid-original-model-id-" + modelId;
        }
        if (out == null || out.length != MODEL_SIZE) {
            return "invalid-original-model-readback-size";
        }
        MemoryBuffer readback = new MemoryBuffer(MODEL_SIZE);
        try {
            GL45C.nglGetNamedBufferSubData(this.modelBufferId, (long) modelId * MODEL_SIZE, MODEL_SIZE, readback.address);
            copyBytes(readback.address, out);
            return this.glErrorOrNone("original-model-record-readback");
        } finally {
            readback.free();
        }
    }

    String readOriginalVoxyModelColourRange(int baseIndex, int byteCount, byte[] out) {
        if (!this.canUploadOriginalVoxyModel()) {
            return "original-model-store-not-upload-ready";
        }
        if (baseIndex < 0 || byteCount < 0 || out == null || out.length != byteCount
                || (long) baseIndex * Integer.BYTES + byteCount > MODEL_COLOUR_BYTES) {
            return "invalid-original-model-colour-readback-range";
        }
        MemoryBuffer readback = new MemoryBuffer(byteCount);
        try {
            GL45C.nglGetNamedBufferSubData(this.modelColourBufferId, (long) baseIndex * Integer.BYTES, byteCount, readback.address);
            copyBytes(readback.address, out);
            return this.glErrorOrNone("original-model-colour-readback");
        } finally {
            readback.free();
        }
    }

    String readOriginalVoxyModelTextureMipChain(int modelId, byte[] out) {
        if (!this.canUploadOriginalVoxyModel()) {
            return "original-model-store-not-upload-ready";
        }
        if (!isValidModelId(modelId)) {
            return "invalid-original-model-id-" + modelId;
        }
        if (out == null || out.length < ForgeOriginalVoxyMipGen.UPLOADED_MIP_CHAIN_BYTES) {
            return "invalid-original-model-texture-readback-size";
        }
        MemoryBuffer readback = new MemoryBuffer(ForgeOriginalVoxyMipGen.UPLOADED_MIP_CHAIN_BYTES);
        try {
            int x = (modelId & 0xFF) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X;
            int y = ((modelId >> 8) & 0xFF) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_Y;
            long offset = 0L;
            for (int level = 0; level < ForgeOriginalVoxyMipGen.LAYERS; level++) {
                int width = (ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X) >> level;
                int height = (ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_Y) >> level;
                int levelBytes = width * height * ForgeModelAtlasPixelSample.BYTES_PER_PIXEL;
                GL45C.nglGetTextureSubImage(
                        this.texturesId,
                        level,
                        x >> level,
                        y >> level,
                        0,
                        width,
                        height,
                        1,
                        GL11C.GL_RGBA,
                        GL11C.GL_UNSIGNED_BYTE,
                        levelBytes,
                        readback.address + offset);
                offset += levelBytes;
            }
            copyBytes(readback.address, out);
            return this.glErrorOrNone("original-model-texture-readback");
        } finally {
            readback.free();
        }
    }

    void free() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::free);
            return;
        }
        if (this.modelBufferId != 0) {
            GL15C.glDeleteBuffers(this.modelBufferId);
        }
        if (this.modelColourBufferId != 0) {
            GL15C.glDeleteBuffers(this.modelColourBufferId);
        }
        if (this.texturesId != 0) {
            giveBackModelStoreTextureAtlas(this.texturesId);
        }
        if (this.blockSamplerId != 0) {
            GL33C.glDeleteSamplers(this.blockSamplerId);
        }
        this.modelBufferId = 0;
        this.modelColourBufferId = 0;
        this.texturesId = 0;
        this.blockSamplerId = 0;
        this.ready = false;
    }

    private String fail(String reason) {
        this.free();
        this.lastFailureReason = reason == null || reason.isBlank() ? "model-store-failed" : reason.replace(' ', '-');
        return this.lastFailureReason;
    }

    private String glErrorOrNone(String prefix) {
        int error = GL11C.glGetError();
        return error == GL11C.GL_NO_ERROR ? "none" : prefix + "-" + glErrorName(error);
    }

    private static int createZeroedBuffer(long bytes) {
        int buffer = GL45C.glCreateBuffers();
        GL45C.glNamedBufferStorage(buffer, bytes, 0);
        GL45C.nglClearNamedBufferData(buffer, GL30C.GL_R8UI, GL30C.GL_RED_INTEGER, GL11C.GL_UNSIGNED_BYTE, 0L);
        return buffer;
    }

    private static int getOrCreateModelStoreTextureAtlas() {
        int texture;
        if (MODEL_TEXTURE_CACHE.isEmpty()) {
            texture = GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D);
            GL45C.glTextureStorage2D(
                    texture,
                    ForgeOriginalVoxyMipGen.LAYERS,
                    GL11C.GL_RGBA8,
                    ForgeModelAtlasLayout.ATLAS_WIDTH,
                    ForgeModelAtlasLayout.ATLAS_HEIGHT);
            GL45C.glTextureParameteri(texture, GL12C.GL_TEXTURE_MAX_LEVEL, ForgeOriginalVoxyMipGen.LAYERS - 1);
        } else {
            texture = MODEL_TEXTURE_CACHE.remove(MODEL_TEXTURE_CACHE.size() - 1);
        }
        zeroTexture(texture);
        return texture;
    }

    private static void giveBackModelStoreTextureAtlas(int texture) {
        MODEL_TEXTURE_CACHE.add(texture);
    }

    private static void zeroTexture(int texture) {
        for (int level = 0; level < ForgeOriginalVoxyMipGen.LAYERS; level++) {
            GL45C.nglClearTexImage(texture, level, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 0L);
        }
    }

    private static boolean isValidModelId(int modelId) {
        return modelId >= 0 && modelId < MODEL_CAPACITY;
    }

    private static void copyBytes(long source, byte[] out) {
        for (int i = 0; i < out.length; i++) {
            out[i] = MemoryUtil.memGetByte(source + i);
        }
    }

    private static String glErrorName(int error) {
        return switch (error) {
            case GL11C.GL_NO_ERROR -> "GL_NO_ERROR";
            case GL11C.GL_INVALID_ENUM -> "GL_INVALID_ENUM";
            case GL11C.GL_INVALID_VALUE -> "GL_INVALID_VALUE";
            case GL11C.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION";
            case GL11C.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY";
            default -> "GL_ERROR_" + error;
        };
    }
}
