package me.cortex.voxy.client.core.model;

import me.cortex.voxy.client.core.RenderResourceReuse;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBuffer;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanBufferUsage;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanImage;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanImageView;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanSampler;
import me.cortex.voxy.client.core.vulkan.VulkanImageStates;
import me.cortex.voxy.client.core.vulkan.VulkanImageTransfer;
import me.cortex.voxy.client.core.vulkan.VulkanUploadStream;
import me.cortex.voxy.client.core.vulkan.shader.VulkanPushDescriptors;
import me.cortex.voxy.common.util.MemoryBuffer;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;

public class ModelStore {
    public static final int MODEL_SIZE = 64;
    private static final int MAX_MODELS = 1 << 16;
    // ModelFactory's CPU scratch allocation includes the terminal 3x2 RGBA mip, but the
    // original GL owner deliberately generated and uploaded only LAYERS (16..2). Preserve
    // that exact GPU texture contract instead of exposing the unwritten 1x1-per-face tail.
    private static final int UNUSED_CPU_TEXTURE_TAIL_BYTES = 3 * 2 * Integer.BYTES;
    private final VoxyVulkanBuffer modelBuffer;
    private final VoxyVulkanBuffer modelColourBuffer;
    private final VoxyVulkanImage textures;
    private final VoxyVulkanImageView textureView;
    private final VoxyVulkanSampler blockSampler;
    private final VulkanUploadStream uploads = new VulkanUploadStream();

    public ModelStore() {
        int bufferRoles = VoxyVulkanBufferUsage.STORAGE_COMPUTE
                | VoxyVulkanBufferUsage.STORAGE_GRAPHICS
                | VoxyVulkanBufferUsage.TRANSFER_SOURCE
                | VoxyVulkanBufferUsage.TRANSFER_DESTINATION;
        this.modelBuffer = VoxyVulkanBuffer.create((long) MODEL_SIZE * MAX_MODELS,
                VoxyVulkanBufferUsage.of(bufferRoles), "ModelData");
        this.modelColourBuffer = VoxyVulkanBuffer.create(4L * MAX_MODELS,
                VoxyVulkanBufferUsage.of(bufferRoles), "ModelColour");
        this.textures = RenderResourceReuse.getOrCreateModelStoreTextureAtlas();
        this.textureView = this.textures.createView(0, this.textures.getMipLevels());

        //Limit the mips of the texture to match that of the terrain atlas
        int mipLvl = ((TextureAtlas) Minecraft.getInstance().getTextureManager()
                .getTexture(Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png")))
                .maxMipLevel;

        this.blockSampler = new VoxyVulkanSampler(
                AddressMode.REPEAT, AddressMode.REPEAT,
                FilterMode.NEAREST, FilterMode.NEAREST,
                1, OptionalDouble.of(mipLvl)
        );
    }


    public void free() {
        this.uploads.commit();
        this.modelBuffer.close();
        this.modelColourBuffer.close();
        this.textureView.close();
        this.blockSampler.close();
        RenderResourceReuse.giveBackModelStoreTextureAtlas(this.textures);
    }

    void uploadModel(int modelId, MemoryBuffer model) {
        if (model.size != MODEL_SIZE) throw new IllegalArgumentException("Model record must be exactly 64 bytes");
        model.cpyTo(this.uploads.upload(this.modelBuffer, Math.multiplyExact((long) modelId, MODEL_SIZE), MODEL_SIZE).address());
    }

    void uploadColours(long offset, MemoryBuffer colours) {
        modelColourRange(offset, colours.size);
        colours.cpyTo(this.uploads.upload(this.modelColourBuffer, offset, Math.toIntExact(colours.size)).address());
    }

    void uploadModelWord(long offset, int value) {
        this.uploads.upload(this.modelBuffer, offset, Integer.BYTES).data().putInt(value);
    }

    void uploadTexture(int modelId, MemoryBuffer texture) {
        int x = (modelId & 0xFF) * ModelFactory.MODEL_TEXTURE_SIZE * 3;
        int y = ((modelId >> 8) & 0xFF) * ModelFactory.MODEL_TEXTURE_SIZE * 2;
        ByteBuffer source = texture.asByteBuffer();
        int byteOffset = 0;
        for (int level = 0; level < ModelFactory.LAYERS; level++) {
            int width = (ModelFactory.MODEL_TEXTURE_SIZE * 3) >> level;
            int height = (ModelFactory.MODEL_TEXTURE_SIZE * 2) >> level;
            int byteCount = Math.multiplyExact(Math.multiplyExact(width, height), 4);
            ByteBuffer mip = source.duplicate();
            mip.position(byteOffset).limit(byteOffset + byteCount);
            VulkanImageTransfer.upload(
                    this.textures, level, 0, x >> level, y >> level, width, height,
                    mip.slice(), VulkanImageStates.SHADER_SAMPLED
            );
            byteOffset = Math.addExact(byteOffset, byteCount);
        }
        if ((long) byteOffset + UNUSED_CPU_TEXTURE_TAIL_BYTES != texture.size) {
            throw new IllegalStateException("Model texture mip payload size changed: consumed=" + byteOffset
                    + ", original-unused-tail=" + UNUSED_CPU_TEXTURE_TAIL_BYTES
                    + ", payload=" + texture.size);
        }
    }

    void commitUploads() {
        this.uploads.commit();
    }

    public VulkanPushDescriptors bind(
            VulkanPushDescriptors descriptors,
            int modelBindingIndex,
            int colourBindingIndex,
            int textureBindingIndex
    ) {
        return descriptors
                .storageBuffer(modelBindingIndex, this.modelBuffer, 0L, this.modelBuffer.size())
                .storageBuffer(colourBindingIndex, this.modelColourBuffer, 0L, this.modelColourBuffer.size())
                .sampledImage(textureBindingIndex, this.textureView, this.blockSampler,
                        VulkanImageStates.SHADER_SAMPLED.layout());
    }

    /** Old renderer is retained only as a source baseline and must never execute on this branch. */
    public void rejectOpenGlBinding() {
        throw new UnsupportedOperationException("The OpenGL ModelStore binding path is retired; no fallback is available");
    }

    private static void modelColourRange(long offset, long size) {
        if (offset < 0 || size < 0 || offset > 4L * MAX_MODELS - size) {
            throw new IllegalArgumentException("Model colour upload range is invalid");
        }
    }

}
