package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deprecated sample atlas route: kept only as historical G6 audit evidence.
 * New atlas uploads must go through the formal ModelStore/ModelFactory path.
 */
@Deprecated(forRemoval = false)
final class ForgeModelAtlasSampleSetUploader {
    static final String STAGE = "G6_19_MULTI_BLOCK_ATLAS_UPLOAD_AUDIT";
    static final String ATLAS_FORMAT = "RGBA8";

    private static final int DEBUG_SMALL_MAX_MODELS = ForgeModelSampleSet.MAX_REQUESTED_SAMPLES;
    private static final int DEBUG_SMALL_ATLAS_WIDTH = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X * DEBUG_SMALL_MAX_MODELS;
    private static final int DEBUG_SMALL_ATLAS_HEIGHT = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_Y;
    private static final Direction[] DIRECTIONS = Direction.values();

    private final ForgeVoxyInstance instance;
    private List<ForgeModelAtlasPixelSample> samples = List.of();
    private int textureId;
    private int actualTextureWidth;
    private int actualTextureHeight;
    private boolean fullAtlasTextureCreated;
    private boolean debugSmallAtlasFallback;
    private boolean atlasPixelsUploaded;
    private boolean atlasSampleSetStale;
    private boolean lastReloadInvalidatedAtlasSampleSet;
    private long uploadRuns;
    private long uploadFailures;
    private long auditRuns;
    private long auditFailures;
    private long clearRuns;
    private String lastUploadError = "none";
    private double lastUploadDurationMs;
    private boolean lastUploadOk;
    private ForgeModelAtlasSampleSetUploadAuditResult lastAudit = ForgeModelAtlasSampleSetUploadAuditResult.failure("none", 0.0D);

    ForgeModelAtlasSampleSetUploader(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeModelAtlasSampleSetUploadStats uploadSampleSet() {
        this.uploadRuns++;
        long start = System.nanoTime();
        this.lastUploadOk = false;
        if (!RenderSystem.isOnRenderThread()) {
            this.failUpload("not-render-thread", start);
            return this.createStatusSnapshot();
        }

        try {
            List<ForgeModelAtlasPixelSample> builtSamples = this.buildSamples();
            if (builtSamples.isEmpty()) {
                this.failUpload("no-model-sample-set-pixel-samples", start);
                return this.createStatusSnapshot();
            }
            if (!this.createTextureForSamples()) {
                this.failUpload(this.lastUploadError, start);
                return this.createStatusSnapshot();
            }

            int uploadedFaces = 0;
            for (int sampleIndex = 0; sampleIndex < builtSamples.size(); sampleIndex++) {
                ForgeModelAtlasPixelSample sample = builtSamples.get(sampleIndex);
                for (int faceIndex = 0; faceIndex < ForgeModelAtlasLayout.FACE_COUNT; faceIndex++) {
                    ForgeModelAtlasPixelSample.Face face = sample.face(faceIndex);
                    if (face == null || face.pixels().length != ForgeModelAtlasPixelSample.BYTES_PER_FACE) {
                        continue;
                    }
                    this.uploadFace(sampleIndex, faceIndex, sample.modelId(), face.pixels());
                    uploadedFaces++;
                }
            }
            if (uploadedFaces == 0) {
                throw new IllegalStateException("no sample set face pixels uploaded");
            }

            this.samples = List.copyOf(builtSamples);
            this.atlasPixelsUploaded = true;
            this.atlasSampleSetStale = false;
            this.lastReloadInvalidatedAtlasSampleSet = false;
            this.lastUploadError = "none";
            this.lastUploadOk = true;
            this.lastAudit = ForgeModelAtlasSampleSetUploadAuditResult.failure("none", 0.0D);
        } catch (RuntimeException e) {
            this.deleteTextureOnRenderThread();
            this.samples = List.of();
            this.atlasPixelsUploaded = false;
            this.lastUploadError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.uploadFailures++;
        }
        this.lastUploadDurationMs = elapsedMs(start);
        return this.createStatusSnapshot();
    }

    ForgeModelAtlasSampleSetUploadAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeModelAtlasSampleSetUploadAuditResult result;
        if (!RenderSystem.isOnRenderThread()) {
            result = ForgeModelAtlasSampleSetUploadAuditResult.failure("not-render-thread", elapsedMs(start));
        } else if (this.samples.isEmpty()) {
            result = ForgeModelAtlasSampleSetUploadAuditResult.failure("no-atlas-sample-set", elapsedMs(start));
        } else if (this.textureId == 0 || !this.atlasPixelsUploaded) {
            result = ForgeModelAtlasSampleSetUploadAuditResult.failure("atlas-texture-missing", elapsedMs(start));
        } else {
            try {
                result = this.auditReadback(start);
            } catch (RuntimeException e) {
                result = ForgeModelAtlasSampleSetUploadAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
            }
        }
        this.lastAudit = result;
        if (!result.success()) {
            this.auditFailures++;
        }
        return result;
    }

    ForgeModelAtlasSampleSetUploadStats createStatusSnapshot() {
        int uploadedFaces = 0;
        int uploadedPixels = 0;
        int missingFaces = 0;
        int mismatches = this.lastAudit.pixelMismatches();
        for (ForgeModelAtlasPixelSample sample : this.samples) {
            uploadedFaces += sample.uploadedFaces();
            uploadedPixels += sample.uploadedPixels();
            missingFaces += sample.missingFaces();
        }
        boolean textureCreated = this.textureId != 0;
        boolean ready = textureCreated && this.atlasPixelsUploaded && !this.samples.isEmpty() && this.lastUploadOk && !this.atlasSampleSetStale;
        ForgeModelAtlasPixelSample first = this.samples.isEmpty() ? null : this.samples.get(0);
        return new ForgeModelAtlasSampleSetUploadStats(
                STAGE,
                this.uploadRuns,
                this.uploadFailures,
                this.auditRuns,
                this.auditFailures,
                this.clearRuns,
                this.lastUploadError,
                this.lastUploadDurationMs,
                ready,
                textureCreated,
                this.textureId,
                this.fullAtlasTextureCreated,
                this.debugSmallAtlasFallback,
                ForgeModelAtlasLayout.ATLAS_WIDTH,
                ForgeModelAtlasLayout.ATLAS_HEIGHT,
                this.actualTextureWidth,
                this.actualTextureHeight,
                ATLAS_FORMAT,
                this.atlasPixelsUploaded,
                this.samples.size(),
                uploadedFaces,
                uploadedPixels,
                missingFaces,
                mismatches,
                ready,
                ready,
                false,
                false,
                false,
                this.atlasSampleSetStale,
                this.lastReloadInvalidatedAtlasSampleSet,
                this.lastUploadOk,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs(),
                this.lastAudit.auditedModels(),
                this.lastAudit.auditedFaces(),
                this.lastAudit.auditedPixels(),
                this.lastAudit.missingFaces(),
                this.lastAudit.pixelMismatches(),
                this.lastAudit.atlasReadbackOk(),
                this.modelIdList(),
                this.blockStateList(),
                this.spriteList(),
                first == null ? -1 : first.modelId(),
                first == null ? "none" : first.blockState(),
                first == null ? "none" : first.sourceSprite(),
                first == null ? "none" : this.faceTileString(0, 0),
                first == null ? "none" : first.faceChecksum(0)
        );
    }

    int[] modelIds() {
        int[] ids = new int[this.samples.size()];
        for (int i = 0; i < this.samples.size(); i++) {
            ids[i] = this.samples.get(i).modelId();
        }
        return ids;
    }

    String dumpSampleSet() {
        ForgeModelAtlasSampleSetUploadStats status = this.createStatusSnapshot();
        StringBuilder builder = new StringBuilder("Voxy model atlas sample set upload: ");
        builder.append("sampleSetAtlasUploadReady=").append(status.sampleSetAtlasUploadReady())
                .append(" uploadedModelCount=").append(status.uploadedModelCount())
                .append(" uploadedFaceCount=").append(status.uploadedFaceCount())
                .append(" uploadedPixels=").append(status.uploadedPixels())
                .append(" missingFaces=").append(status.missingFaces())
                .append(" pixelMismatches=").append(status.pixelMismatches())
                .append(" atlasTextureObjectCreated=").append(status.atlasTextureObjectCreated())
                .append(" fullAtlasTextureCreated=").append(status.fullAtlasTextureCreated())
                .append(" debugSmallAtlasFallback=").append(status.debugSmallAtlasFallback())
                .append(" formalTextureAtlasReady=false formalTexturedShaderReady=false formalModelBridgeReady=false");
        int limit = Math.min(4, this.samples.size());
        for (int i = 0; i < limit; i++) {
            ForgeModelAtlasPixelSample sample = this.samples.get(i);
            builder.append(" sample").append(i)
                    .append("{modelId=").append(sample.modelId())
                    .append(",blockState=\"").append(sample.blockState())
                    .append("\",sprite=").append(sample.sourceSprite())
                    .append(",face0Tile=").append(this.faceTileString(i, 0))
                    .append(",face0Checksum=").append(sample.faceChecksum(0))
                    .append("}");
        }
        return builder.toString();
    }

    void markStale(String reason) {
        this.samples = List.of();
        this.deleteTexture();
        this.atlasPixelsUploaded = false;
        this.atlasSampleSetStale = true;
        this.lastReloadInvalidatedAtlasSampleSet = true;
        this.lastUploadOk = false;
        this.lastUploadError = reason == null || reason.isBlank() ? "stale" : reason;
        this.lastAudit = ForgeModelAtlasSampleSetUploadAuditResult.failure(this.lastUploadError, 0.0D);
    }

    void clear() {
        this.clearRuns++;
        this.samples = List.of();
        this.deleteTexture();
        this.atlasPixelsUploaded = false;
        this.atlasSampleSetStale = false;
        this.lastReloadInvalidatedAtlasSampleSet = false;
        this.uploadRuns = 0L;
        this.uploadFailures = 0L;
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.lastUploadError = "none";
        this.lastUploadDurationMs = 0.0D;
        this.lastUploadOk = false;
        this.lastAudit = ForgeModelAtlasSampleSetUploadAuditResult.failure("none", 0.0D);
    }

    private List<ForgeModelAtlasPixelSample> buildSamples() {
        ForgeModelSampleSetStats sampleSetStatus = this.instance.getModelSampleSet().createStatusSnapshot();
        if (!sampleSetStatus.sampleSetReady()) {
            sampleSetStatus = this.instance.getModelSampleSet().build();
        }
        ForgeModelAtlasStats atlasStats = this.instance.getModelAtlasSkeleton().createStatusSnapshot();
        if (!atlasStats.atlasSkeletonReady()) {
            atlasStats = this.instance.getModelAtlasSkeleton().build();
        }
        if (!sampleSetStatus.sampleSetReady() || !atlasStats.atlasSkeletonReady()) {
            return List.of();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getBlockRenderer() == null) {
            return List.of();
        }
        List<ForgeModelAtlasPixelSample> result = new ArrayList<>();
        for (ForgeRealModelStoreSampleRecord record : this.instance.getModelSampleSet().records()) {
            BlockState state = this.blockStateForRecord(record);
            var model = minecraft.getBlockRenderer().getBlockModel(state);
            if (model == null || model.isCustomRenderer()) {
                continue;
            }
            ForgeModelAtlasPixelSample.Face[] faces = new ForgeModelAtlasPixelSample.Face[ForgeModelAtlasLayout.FACE_COUNT];
            String sourceSprite = "none";
            String sourceAtlas = "none";
            for (Direction direction : DIRECTIONS) {
                int faceIndex = direction.get3DDataValue();
                BakedQuad quad = firstQuadForFace(model, state, direction, record.blockStateId());
                TextureAtlasSprite sprite = quad == null ? null : quad.getSprite();
                if (sprite == null) {
                    faces[faceIndex] = ForgeModelAtlasPixelSample.Face.missing(faceIndex, direction.getName());
                    continue;
                }
                ForgeModelAtlasPixelSample.Face face = this.buildFace(faceIndex, direction, sprite);
                faces[faceIndex] = face;
                if (!face.missingFace() && "none".equals(sourceSprite)) {
                    sourceSprite = face.spriteName();
                    sourceAtlas = face.spriteAtlas();
                }
            }
            result.add(new ForgeModelAtlasPixelSample(
                    record.modelId(),
                    record.blockStateId(),
                    record.blockState(),
                    sourceSprite,
                    sourceAtlas,
                    faces
            ));
        }
        return result;
    }

    private ForgeModelAtlasPixelSample.Face buildFace(int faceIndex, Direction direction, TextureAtlasSprite sprite) {
        byte[] pixels = new byte[ForgeModelAtlasPixelSample.BYTES_PER_FACE];
        String spriteName;
        String spriteAtlas;
        int spriteWidth;
        int spriteHeight;
        try {
            spriteName = sprite.contents().name().toString();
            spriteAtlas = sprite.atlasLocation().toString();
            spriteWidth = Math.max(1, sprite.contents().width());
            spriteHeight = Math.max(1, sprite.contents().height());
        } catch (RuntimeException e) {
            return ForgeModelAtlasPixelSample.Face.missing(faceIndex, direction.getName());
        }

        for (int y = 0; y < ForgeModelAtlasPixelSample.TILE_SIZE; y++) {
            int sourceY = Math.min(spriteHeight - 1, (y * spriteHeight) / ForgeModelAtlasPixelSample.TILE_SIZE);
            for (int x = 0; x < ForgeModelAtlasPixelSample.TILE_SIZE; x++) {
                int sourceX = Math.min(spriteWidth - 1, (x * spriteWidth) / ForgeModelAtlasPixelSample.TILE_SIZE);
                int pixel = sprite.getPixelRGBA(0, sourceX, sourceY);
                int offset = ((y * ForgeModelAtlasPixelSample.TILE_SIZE) + x) * ForgeModelAtlasPixelSample.BYTES_PER_PIXEL;
                pixels[offset] = (byte) (pixel & 0xFF);
                pixels[offset + 1] = (byte) ((pixel >> 8) & 0xFF);
                pixels[offset + 2] = (byte) ((pixel >> 16) & 0xFF);
                pixels[offset + 3] = (byte) ((pixel >> 24) & 0xFF);
            }
        }
        return new ForgeModelAtlasPixelSample.Face(
                faceIndex,
                direction.getName(),
                spriteName,
                spriteAtlas,
                false,
                pixels,
                ForgeModelAtlasPixelSample.checksum(pixels)
        );
    }

    private boolean createTextureForSamples() {
        this.deleteTextureOnRenderThread();
        this.fullAtlasTextureCreated = false;
        this.debugSmallAtlasFallback = false;
        this.actualTextureWidth = 0;
        this.actualTextureHeight = 0;

        clearGlErrors();
        int maxTextureSize = GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
        if (maxTextureSize >= ForgeModelAtlasLayout.ATLAS_WIDTH && maxTextureSize >= ForgeModelAtlasLayout.ATLAS_HEIGHT) {
            if (this.tryCreateTexture(ForgeModelAtlasLayout.ATLAS_WIDTH, ForgeModelAtlasLayout.ATLAS_HEIGHT, true)) {
                return true;
            }
        }

        this.debugSmallAtlasFallback = true;
        if (this.tryCreateTexture(DEBUG_SMALL_ATLAS_WIDTH, DEBUG_SMALL_ATLAS_HEIGHT, false)) {
            return true;
        }

        if ("none".equals(this.lastUploadError)) {
            this.lastUploadError = "atlas-sample-set-texture-create-failed";
        }
        return false;
    }

    private boolean tryCreateTexture(int width, int height, boolean fullAtlas) {
        clearGlErrors();
        int oldTextureBinding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int unpackBufferBinding = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        PixelStoreState pixelStore = PixelStoreState.captureUnpack();
        int nextTextureId = GL11C.glGenTextures();
        if (nextTextureId == 0) {
            this.lastUploadError = "glGenTextures-returned-zero";
            return false;
        }
        int error;
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            PixelStoreState.applyTightUnpack();
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, nextTextureId);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL12C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
            GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA8, width, height, 0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            error = GL11C.glGetError();
        } finally {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTextureBinding);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, unpackBufferBinding);
            pixelStore.restoreUnpack();
        }
        if (error != GL11C.GL_NO_ERROR) {
            GL11C.glDeleteTextures(nextTextureId);
            this.lastUploadError = "glTexImage2D-" + glErrorName(error);
            return false;
        }

        this.textureId = nextTextureId;
        this.actualTextureWidth = width;
        this.actualTextureHeight = height;
        this.fullAtlasTextureCreated = fullAtlas;
        this.debugSmallAtlasFallback = !fullAtlas;
        return true;
    }

    private void uploadFace(int sampleIndex, int faceIndex, int modelId, byte[] pixels) {
        ForgeModelAtlasLayout.Tile tile = this.actualUploadTile(sampleIndex, faceIndex, modelId);
        ByteBuffer buffer = MemoryUtil.memAlloc(ForgeModelAtlasPixelSample.BYTES_PER_FACE);
        PixelStoreState pixelStore = PixelStoreState.captureUnpack();
        int unpackBufferBinding = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int oldTextureBinding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, this.textureId);
            PixelStoreState.applyTightUnpack();
            clearGlErrors();
            buffer.put(pixels);
            buffer.flip();
            GL11C.glTexSubImage2D(
                    GL11C.GL_TEXTURE_2D,
                    0,
                    tile.x(),
                    tile.y(),
                    ForgeModelAtlasPixelSample.TILE_SIZE,
                    ForgeModelAtlasPixelSample.TILE_SIZE,
                    GL11C.GL_RGBA,
                    GL11C.GL_UNSIGNED_BYTE,
                    buffer
            );
            int error = GL11C.glGetError();
            if (error != GL11C.GL_NO_ERROR) {
                throw new IllegalStateException("glTexSubImage2D sample " + sampleIndex + " face " + faceIndex + " " + glErrorName(error));
            }
        } finally {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTextureBinding);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, unpackBufferBinding);
            pixelStore.restoreUnpack();
            MemoryUtil.memFree(buffer);
        }
    }

    private ForgeModelAtlasSampleSetUploadAuditResult auditReadback(long start) {
        int pixelMismatches = 0;
        int auditedModels = 0;
        int auditedFaces = 0;
        int auditedPixels = 0;
        int missingFaces = 0;
        String firstMismatch = "none";
        for (int sampleIndex = 0; sampleIndex < this.samples.size(); sampleIndex++) {
            ForgeModelAtlasPixelSample sample = this.samples.get(sampleIndex);
            auditedModels++;
            missingFaces += sample.missingFaces();
            for (int faceIndex = 0; faceIndex < ForgeModelAtlasLayout.FACE_COUNT; faceIndex++) {
                ForgeModelAtlasPixelSample.Face face = sample.face(faceIndex);
                if (face == null) {
                    continue;
                }
                byte[] readback = this.readbackFace(sampleIndex, faceIndex, sample.modelId());
                int faceMismatches = countPixelMismatches(face.pixels(), readback);
                if (faceMismatches > 0 && "none".equals(firstMismatch)) {
                    firstMismatch = "sample" + sampleIndex + "-model" + sample.modelId() + "-face" + faceIndex + "-readChecksum=" + ForgeModelAtlasPixelSample.checksum(readback);
                }
                pixelMismatches += faceMismatches;
                auditedFaces++;
                auditedPixels += ForgeModelAtlasPixelSample.TILE_SIZE * ForgeModelAtlasPixelSample.TILE_SIZE;
            }
        }
        boolean success = auditedModels > 0 && auditedFaces > 0 && pixelMismatches == 0;
        return new ForgeModelAtlasSampleSetUploadAuditResult(
                success,
                success ? "none" : "atlas-sample-set-pixel-mismatch-" + firstMismatch,
                elapsedMs(start),
                auditedModels,
                auditedFaces,
                auditedPixels,
                missingFaces,
                pixelMismatches,
                success
        );
    }

    private byte[] readbackFace(int sampleIndex, int faceIndex, int modelId) {
        ForgeModelAtlasLayout.Tile tile = this.actualUploadTile(sampleIndex, faceIndex, modelId);
        ByteBuffer buffer = MemoryUtil.memAlloc(ForgeModelAtlasPixelSample.BYTES_PER_FACE);
        PixelStoreState pixelStore = PixelStoreState.capturePack();
        int packBufferBinding = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int oldReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int oldReadBuffer = GL11C.glGetInteger(GL11C.GL_READ_BUFFER);
        int framebuffer = GL30C.glGenFramebuffers();
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer);
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, this.textureId, 0);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            int framebufferStatus = GL30C.glCheckFramebufferStatus(GL30C.GL_READ_FRAMEBUFFER);
            if (framebufferStatus != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("atlas sample set read framebuffer incomplete 0x" + Integer.toHexString(framebufferStatus));
            }
            PixelStoreState.applyTightPack();
            clearGlErrors();
            GL11C.glReadPixels(tile.x(), tile.y(), ForgeModelAtlasPixelSample.TILE_SIZE, ForgeModelAtlasPixelSample.TILE_SIZE, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, buffer);
            int error = GL11C.glGetError();
            if (error != GL11C.GL_NO_ERROR) {
                throw new IllegalStateException("glReadPixels sample " + sampleIndex + " face " + faceIndex + " " + glErrorName(error));
            }
            byte[] result = new byte[ForgeModelAtlasPixelSample.BYTES_PER_FACE];
            buffer.position(0);
            buffer.get(result);
            return result;
        } finally {
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldReadFramebuffer);
            GL11C.glReadBuffer(oldReadBuffer);
            if (framebuffer != 0) {
                GL30C.glDeleteFramebuffers(framebuffer);
            }
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, packBufferBinding);
            pixelStore.restorePack();
            MemoryUtil.memFree(buffer);
        }
    }

    private ForgeModelAtlasLayout.Tile actualUploadTile(int sampleIndex, int faceIndex, int modelId) {
        if (this.fullAtlasTextureCreated && ForgeModelAtlasLayout.isValidModelId(modelId)) {
            return ForgeModelAtlasLayout.faceTile(modelId, faceIndex);
        }
        return new ForgeModelAtlasLayout.Tile(
                sampleIndex * ForgeModelAtlasLayout.FACES_PER_MODEL_X * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE
                        + (faceIndex >> 1) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE,
                (faceIndex & 1) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE
        );
    }

    private String faceTileString(int sampleIndex, int faceIndex) {
        if (sampleIndex < 0 || sampleIndex >= this.samples.size()) {
            return "none";
        }
        return this.actualUploadTile(sampleIndex, faceIndex, this.samples.get(sampleIndex).modelId()).format();
    }

    private BlockState blockStateForRecord(ForgeRealModelStoreSampleRecord record) {
        try {
            Optional<BlockState> state = this.instance.getCurrentEngineOptional()
                    .map(engine -> engine.getMapper().getBlockStateFromBlockId(record.blockStateId()));
            if (state.isPresent()) {
                return state.get();
            }
        } catch (RuntimeException ignored) {
            // Fall through to vanilla registry.
        }
        BlockState state = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.byId(record.blockStateId());
        return state == null ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : state;
    }

    private static BakedQuad firstQuadForFace(net.minecraft.client.resources.model.BakedModel model, BlockState state, Direction direction, int blockStateId) {
        List<BakedQuad> directional = safeGetQuads(model, state, direction, blockStateId);
        if (!directional.isEmpty()) {
            return directional.get(0);
        }
        for (BakedQuad quad : safeGetQuads(model, state, null, blockStateId)) {
            if (quad.getDirection() == direction) {
                return quad;
            }
        }
        return null;
    }

    private static List<BakedQuad> safeGetQuads(net.minecraft.client.resources.model.BakedModel model, BlockState state, Direction direction, int blockStateId) {
        try {
            List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(blockStateId * 31L + (direction == null ? 17L : direction.ordinal())));
            return quads == null ? List.of() : quads;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private void failUpload(String error, long start) {
        this.lastUploadError = error == null || error.isBlank() ? "upload-failed" : error;
        this.lastUploadDurationMs = elapsedMs(start);
        this.lastUploadOk = false;
        this.uploadFailures++;
    }

    private void deleteTexture() {
        int oldTextureId = this.textureId;
        this.textureId = 0;
        this.actualTextureWidth = 0;
        this.actualTextureHeight = 0;
        this.fullAtlasTextureCreated = false;
        this.debugSmallAtlasFallback = false;
        if (oldTextureId == 0) {
            return;
        }
        if (RenderSystem.isOnRenderThread()) {
            GL11C.glDeleteTextures(oldTextureId);
        } else {
            RenderSystem.recordRenderCall(() -> GL11C.glDeleteTextures(oldTextureId));
        }
    }

    private void deleteTextureOnRenderThread() {
        if (this.textureId != 0) {
            GL11C.glDeleteTextures(this.textureId);
        }
        this.textureId = 0;
        this.actualTextureWidth = 0;
        this.actualTextureHeight = 0;
        this.fullAtlasTextureCreated = false;
        this.debugSmallAtlasFallback = false;
    }

    private String modelIdList() {
        if (this.samples.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (ForgeModelAtlasPixelSample sample : this.samples) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(sample.modelId());
        }
        return builder.toString();
    }

    private String blockStateList() {
        List<String> values = new ArrayList<>();
        for (ForgeModelAtlasPixelSample sample : this.samples) {
            values.add(sample.blockState());
        }
        return compactList("none", values);
    }

    private String spriteList() {
        List<String> values = new ArrayList<>();
        for (ForgeModelAtlasPixelSample sample : this.samples) {
            values.add(sample.sourceSprite());
        }
        return compactList("none", values);
    }

    private static String compactList(String empty, List<String> values) {
        if (values.isEmpty()) {
            return empty;
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(8, values.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append('|');
            }
            builder.append(values.get(i).replace(' ', '_'));
        }
        if (values.size() > limit) {
            builder.append("|...");
        }
        return builder.toString();
    }

    private static int countPixelMismatches(byte[] expected, byte[] actual) {
        int mismatches = 0;
        int pixels = Math.min(expected.length, actual.length) / ForgeModelAtlasPixelSample.BYTES_PER_PIXEL;
        for (int pixel = 0; pixel < pixels; pixel++) {
            int offset = pixel * ForgeModelAtlasPixelSample.BYTES_PER_PIXEL;
            if (expected[offset] != actual[offset]
                    || expected[offset + 1] != actual[offset + 1]
                    || expected[offset + 2] != actual[offset + 2]
                    || expected[offset + 3] != actual[offset + 3]) {
                mismatches++;
            }
        }
        int missingBytes = Math.abs(expected.length - actual.length);
        return mismatches + ((missingBytes + ForgeModelAtlasPixelSample.BYTES_PER_PIXEL - 1) / ForgeModelAtlasPixelSample.BYTES_PER_PIXEL);
    }

    private static void clearGlErrors() {
        while (GL11C.glGetError() != GL11C.GL_NO_ERROR) {
            // Drain stale GL errors before a scoped texture operation.
        }
    }

    private static String glErrorName(int error) {
        return switch (error) {
            case GL11C.GL_NO_ERROR -> "none";
            case GL11C.GL_INVALID_ENUM -> "GL_INVALID_ENUM";
            case GL11C.GL_INVALID_VALUE -> "GL_INVALID_VALUE";
            case GL11C.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION";
            case GL30C.GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION";
            case GL11C.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY";
            default -> "0x" + Integer.toHexString(error);
        };
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0D;
    }

    private record PixelStoreState(int alignment, int rowLength, int imageHeight, int skipRows, int skipPixels, int skipImages) {
        static PixelStoreState captureUnpack() {
            return new PixelStoreState(
                    GL11C.glGetInteger(GL11C.GL_UNPACK_ALIGNMENT),
                    GL11C.glGetInteger(GL11C.GL_UNPACK_ROW_LENGTH),
                    GL11C.glGetInteger(GL12C.GL_UNPACK_IMAGE_HEIGHT),
                    GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_ROWS),
                    GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_PIXELS),
                    GL11C.glGetInteger(GL12C.GL_UNPACK_SKIP_IMAGES)
            );
        }

        static PixelStoreState capturePack() {
            return new PixelStoreState(
                    GL11C.glGetInteger(GL11C.GL_PACK_ALIGNMENT),
                    GL11C.glGetInteger(GL11C.GL_PACK_ROW_LENGTH),
                    GL11C.glGetInteger(GL12C.GL_PACK_IMAGE_HEIGHT),
                    GL11C.glGetInteger(GL11C.GL_PACK_SKIP_ROWS),
                    GL11C.glGetInteger(GL11C.GL_PACK_SKIP_PIXELS),
                    GL11C.glGetInteger(GL12C.GL_PACK_SKIP_IMAGES)
            );
        }

        static void applyTightUnpack() {
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 1);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
            GL11C.glPixelStorei(GL12C.GL_UNPACK_IMAGE_HEIGHT, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
            GL11C.glPixelStorei(GL12C.GL_UNPACK_SKIP_IMAGES, 0);
        }

        static void applyTightPack() {
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, 1);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, 0);
            GL11C.glPixelStorei(GL12C.GL_PACK_IMAGE_HEIGHT, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, 0);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, 0);
            GL11C.glPixelStorei(GL12C.GL_PACK_SKIP_IMAGES, 0);
        }

        void restoreUnpack() {
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, this.alignment);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, this.rowLength);
            GL11C.glPixelStorei(GL12C.GL_UNPACK_IMAGE_HEIGHT, this.imageHeight);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, this.skipRows);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, this.skipPixels);
            GL11C.glPixelStorei(GL12C.GL_UNPACK_SKIP_IMAGES, this.skipImages);
        }

        void restorePack() {
            GL11C.glPixelStorei(GL11C.GL_PACK_ALIGNMENT, this.alignment);
            GL11C.glPixelStorei(GL11C.GL_PACK_ROW_LENGTH, this.rowLength);
            GL11C.glPixelStorei(GL12C.GL_PACK_IMAGE_HEIGHT, this.imageHeight);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_ROWS, this.skipRows);
            GL11C.glPixelStorei(GL11C.GL_PACK_SKIP_PIXELS, this.skipPixels);
            GL11C.glPixelStorei(GL12C.GL_PACK_SKIP_IMAGES, this.skipImages);
        }
    }
}
