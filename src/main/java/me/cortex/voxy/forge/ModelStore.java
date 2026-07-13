package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.util.TrackedObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.lwjgl.opengl.ARBDirectStateAccess.nglTextureSubImage2D;

final class ModelStore {
    static final int MODEL_SIZE = 64;
    static final int MODEL_CAPACITY = 1 << 16;
    static final long MODEL_DATA_BYTES = (long) MODEL_SIZE * MODEL_CAPACITY;
    static final long MODEL_COLOUR_BYTES = (long) Integer.BYTES * MODEL_CAPACITY;

    private static final ArrayList<TrackedModelTexture> MODEL_TEXTURE_CACHE = new ArrayList<>();

    private GlBuffer modelBuffer;
    private GlBuffer modelColourBuffer;
    private TrackedModelTexture modelTexture;
    private int texturesId;
    private int blockSamplerId;
    private boolean ready;

    String build(Minecraft minecraft) {
        if (this.ready) {
            return "none";
        }
        if (!RenderSystem.isOnRenderThread()) {
            return this.fail("model-store-build-not-render-thread");
        }
        drainLatchedGlErrors("original-model-store-build");
        try {
            this.modelBuffer = new GlBuffer(MODEL_DATA_BYTES).name("ModelData");
            this.modelColourBuffer = new GlBuffer(MODEL_COLOUR_BYTES).name("ModelColour");
            this.modelTexture = getOrCreateModelStoreTextureAtlas();
            this.texturesId = this.modelTexture.id;
            GlDebug.texture("ModelTextures", this.texturesId);
            this.blockSamplerId = GL33C.glGenSamplers();
            if (this.blockSamplerId == 0) {
                return this.fail("model-store-sampler-create-failed");
            }

            int mipLevel = resolveBlockAtlasMipLevel(minecraft);
            GL33C.glSamplerParameteri(this.blockSamplerId, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST_MIPMAP_LINEAR);
            GL33C.glSamplerParameteri(this.blockSamplerId, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL33C.glSamplerParameteri(this.blockSamplerId, GL12C.GL_TEXTURE_MIN_LOD, 0);
            GL33C.glSamplerParameteri(this.blockSamplerId, GL12C.GL_TEXTURE_MAX_LOD, mipLevel);

            int error = GL11C.glGetError();
            if (error != GL11C.GL_NO_ERROR) {
                return this.fail("model-store-build-" + glErrorName(error));
            }
            this.ready = true;
            return "none";
        } catch (RuntimeException e) {
            return this.fail("model-store-build-" + e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }

    boolean canUploadOriginalVoxyModel() {
        return RenderSystem.isOnRenderThread()
                && this.ready
                && this.modelBuffer != null
                && this.modelColourBuffer != null
                && this.texturesId != 0
                && this.blockSamplerId != 0;
    }

    void bind(int modelBindingIndex, int colourBindingIndex, int textureBindingIndex) {
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, modelBindingIndex, this.modelBuffer.id);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, colourBindingIndex, this.modelColourBuffer.id);
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
        model.cpyTo(UploadStream.instance().upload(this.modelBuffer.id, (long) modelId * MODEL_SIZE, MODEL_SIZE));
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
        long ptr = UploadStream.instance().upload(
                this.modelBuffer.id,
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
        colours.cpyTo(UploadStream.instance().upload(this.modelColourBuffer.id, (long) baseIndex * Integer.BYTES, colours.size));
        return this.glErrorOrNone("original-model-colour-range-upload-stream");
    }

    String uploadOriginalVoxyBiomeUpload(MemoryBuffer biomeColourBuffer, MemoryBuffer modelBiomeIndexPairs) {
        if (!this.canUploadOriginalVoxyModel()) {
            return "original-model-store-not-upload-ready";
        }
        if (biomeColourBuffer == null || modelBiomeIndexPairs == null || modelBiomeIndexPairs.size % Long.BYTES != 0) {
            return "invalid-original-biome-upload";
        }
        biomeColourBuffer.cpyTo(UploadStream.instance().upload(this.modelColourBuffer.id, 0L, biomeColourBuffer.size));
        long ptr = modelBiomeIndexPairs.address;
        for (long offset = 0; offset < modelBiomeIndexPairs.size; offset += Long.BYTES) {
            long pair = MemoryUtil.memGetLong(ptr);
            ptr += Long.BYTES;
            int modelId = (int) pair;
            int biomeIndex = (int) (pair >>> 32);
            MemoryUtil.memPutInt(
                    UploadStream.instance().upload(
                            this.modelBuffer.id,
                            ((long) modelId * MODEL_SIZE) + ForgeModelStoreLayoutSpec.WORD_COLOUR_TINT * (long) Integer.BYTES,
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
        if (texture == null || texture.size < MipGen.UPLOADED_MIP_CHAIN_BYTES) {
            return "invalid-original-model-texture-buffer-size";
        }

        int x = (modelId & 0xFF) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X;
        int y = ((modelId >> 8) & 0xFF) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_Y;
        long cAddr = texture.address;
        for (int level = 0; level < MipGen.LAYERS; level++) {
            int width = (ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X) >> level;
            int height = (ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_Y) >> level;
            nglTextureSubImage2D(this.texturesId, level, x >> level, y >> level, width, height, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, cAddr);
            cAddr += (long) width * height * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL;
        }
        return this.glErrorOrNone("original-model-texture-upload");
    }

    void free() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::free);
            return;
        }
        if (this.modelBuffer != null) {
            this.modelBuffer.free();
        }
        if (this.modelColourBuffer != null) {
            this.modelColourBuffer.free();
        }
        if (this.texturesId != 0) {
            giveBackModelStoreTextureAtlas(this.modelTexture);
        }
        if (this.blockSamplerId != 0) {
            GL33C.glDeleteSamplers(this.blockSamplerId);
        }
        this.modelBuffer = null;
        this.modelColourBuffer = null;
        this.texturesId = 0;
        this.modelTexture = null;
        this.blockSamplerId = 0;
        this.ready = false;
    }

    private String fail(String reason) {
        this.free();
        return reason == null || reason.isBlank() ? "model-store-failed" : reason.replace(' ', '-');
    }

    private String glErrorOrNone(String prefix) {
        int error = GL11C.glGetError();
        return error == GL11C.GL_NO_ERROR ? "none" : prefix + "-" + glErrorName(error);
    }

    //glGetError reads the context-wide latched error flags, which include errors raised by
    // earlier non-Voxy GL calls (Oculus pipeline reloads latch errors routinely). Drain them
    // before running upload checks so glErrorOrNone only reflects Voxy's own calls.
    static void drainLatchedGlErrors(String context) {
        int error = GL11C.glGetError();
        int drained = 0;
        while (error != GL11C.GL_NO_ERROR && drained < 16) {
            VoxyForge.LOGGER.warn(
                    "Drained pre-existing GL error {} before {} (latched by earlier non-Voxy GL calls).",
                    glErrorName(error),
                    context);
            drained++;
            error = GL11C.glGetError();
        }
    }

    private static TrackedModelTexture getOrCreateModelStoreTextureAtlas() {
        TrackedModelTexture texture;
        if (MODEL_TEXTURE_CACHE.isEmpty()) {
            texture = new TrackedModelTexture(GL45C.glCreateTextures(GL11C.GL_TEXTURE_2D));
            GL45C.glTextureStorage2D(
                    texture.id,
                    MipGen.LAYERS,
                    GL11C.GL_RGBA8,
                    ForgeModelAtlasLayout.ATLAS_WIDTH,
                    ForgeModelAtlasLayout.ATLAS_HEIGHT);
            ForgeOriginalVoxyGlResourceStatistics.textureAllocated(
                    texture.id,
                    ForgeOriginalVoxyGlResourceStatistics.mipChainBytes(
                            ForgeModelAtlasLayout.ATLAS_WIDTH,
                            ForgeModelAtlasLayout.ATLAS_HEIGHT,
                            MipGen.LAYERS,
                            ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL));
            GL45C.glTextureParameteri(texture.id, GL12C.GL_TEXTURE_MAX_LEVEL, MipGen.LAYERS - 1);
        } else {
            texture = MODEL_TEXTURE_CACHE.remove(MODEL_TEXTURE_CACHE.size() - 1);
        }
        zeroTexture(texture.id);
        return texture;
    }

    private static void giveBackModelStoreTextureAtlas(TrackedModelTexture texture) {
        MODEL_TEXTURE_CACHE.add(texture);
    }

    //Original RenderResourceReuse.clearResources(): cached atlases survive renderer rebuilds but
    //are deleted when the entire client instance shuts down.
    static void clearCachedModelStoreTextureAtlases() {
        while (!MODEL_TEXTURE_CACHE.isEmpty()) {
            MODEL_TEXTURE_CACHE.remove(MODEL_TEXTURE_CACHE.size() - 1).free();
        }
    }

    private static int resolveBlockAtlasMipLevel(Minecraft minecraft) {
        if (minecraft == null || minecraft.getTextureManager() == null) {
            return MipGen.LAYERS - 1;
        }
        AbstractTexture texture = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        Integer atlasMipLevel = readIntField(texture, "maxMipLevel", "maxMipmapLevels", "f_119402_");
        if (atlasMipLevel != null) {
            return clampMipLevel(atlasMipLevel);
        }
        return clampMipLevel(readIntField(minecraft.getModelManager(), "maxMipmapLevels", "f_119402_"));
    }

    private static int clampMipLevel(Integer mipLevel) {
        if (mipLevel == null) {
            return MipGen.LAYERS - 1;
        }
        return Math.max(0, Math.min(mipLevel, MipGen.LAYERS - 1));
    }

    private static Integer readIntField(Object owner, String... names) {
        if (owner == null) {
            return null;
        }
        Class<?> type = owner.getClass();
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.getInt(owner);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static void zeroTexture(int texture) {
        for (int level = 0; level < MipGen.LAYERS; level++) {
            GL45C.nglClearTexImage(texture, level, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 0L);
        }
    }

    private static boolean isValidModelId(int modelId) {
        return modelId >= 0 && modelId < MODEL_CAPACITY;
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

    private static final class TrackedModelTexture extends TrackedObject {
        private final int id;

        private TrackedModelTexture(int id) {
            this.id = id;
        }

        @Override
        public void free() {
            this.free0();
            ForgeOriginalVoxyGlResourceStatistics.textureFreed(this.id);
            GL11C.glDeleteTextures(this.id);
        }
    }

}
