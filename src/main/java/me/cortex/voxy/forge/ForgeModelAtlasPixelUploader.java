package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

final class ForgeModelAtlasPixelUploader {
    static final String STAGE = "G6_15_ATLAS_PIXEL_UPLOAD_AUDIT";
    static final String ATLAS_FORMAT = "RGBA8";

    private static final int DEBUG_SMALL_ATLAS_WIDTH = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X;
    private static final int DEBUG_SMALL_ATLAS_HEIGHT = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_Y;
    private static final Direction[] DIRECTIONS = Direction.values();

    private final ForgeVoxyInstance instance;
    private ForgeModelAtlasPixelSample sample;
    private int textureId;
    private int actualTextureWidth;
    private int actualTextureHeight;
    private boolean fullAtlasTextureCreated;
    private boolean debugSmallAtlasFallback;
    private boolean atlasPixelsUploaded;
    private boolean atlasPixelsStale;
    private boolean lastReloadInvalidatedAtlasPixels;
    private long uploadRuns;
    private long uploadFailures;
    private long auditRuns;
    private long auditFailures;
    private long clearRuns;
    private String lastUploadError = "none";
    private double lastUploadDurationMs;
    private boolean lastUploadOk;
    private ForgeModelAtlasUploadAuditResult lastAudit = ForgeModelAtlasUploadAuditResult.failure("none", 0.0D);

    ForgeModelAtlasPixelUploader(ForgeVoxyInstance instance) {
        this.instance = instance;
    }

    ForgeModelAtlasUploadStats uploadSample() {
        this.uploadRuns++;
        long start = System.nanoTime();
        this.lastUploadOk = false;
        if (!RenderSystem.isOnRenderThread()) {
            this.failUpload("not-render-thread", start);
            return this.createStatusSnapshot();
        }

        try {
            ForgeModelAtlasPixelSample builtSample = this.buildSample();
            if (builtSample == null) {
                this.failUpload("no-solid-sprite-pixel-sample", start);
                return this.createStatusSnapshot();
            }
            if (!this.createTextureForSample()) {
                this.failUpload(this.lastUploadError, start);
                return this.createStatusSnapshot();
            }

            int uploadedFaces = 0;
            for (int faceIndex = 0; faceIndex < ForgeModelAtlasLayout.FACE_COUNT; faceIndex++) {
                ForgeModelAtlasPixelSample.Face face = builtSample.face(faceIndex);
                if (face == null || face.pixels().length != ForgeModelAtlasPixelSample.BYTES_PER_FACE) {
                    throw new IllegalStateException("missing face pixels " + faceIndex);
                }
                this.uploadFace(faceIndex, builtSample.modelId(), face.pixels());
                uploadedFaces++;
            }
            if (uploadedFaces == 0) {
                throw new IllegalStateException("no face pixels uploaded");
            }

            this.sample = builtSample;
            this.atlasPixelsUploaded = true;
            this.atlasPixelsStale = false;
            this.lastReloadInvalidatedAtlasPixels = false;
            this.lastUploadError = "none";
            this.lastUploadOk = true;
            this.lastAudit = ForgeModelAtlasUploadAuditResult.failure("none", 0.0D);
        } catch (RuntimeException e) {
            this.deleteTextureOnRenderThread();
            this.sample = null;
            this.atlasPixelsUploaded = false;
            this.lastUploadError = e.getClass().getSimpleName() + ": " + e.getMessage();
            this.uploadFailures++;
        }
        this.lastUploadDurationMs = elapsedMs(start);
        return this.createStatusSnapshot();
    }

    ForgeModelAtlasUploadAuditResult audit() {
        this.auditRuns++;
        long start = System.nanoTime();
        ForgeModelAtlasUploadAuditResult result;
        if (!RenderSystem.isOnRenderThread()) {
            result = ForgeModelAtlasUploadAuditResult.failure("not-render-thread", elapsedMs(start));
        } else if (this.sample == null) {
            result = ForgeModelAtlasUploadAuditResult.failure("no-atlas-upload-sample", elapsedMs(start));
        } else if (this.textureId == 0 || !this.atlasPixelsUploaded) {
            result = ForgeModelAtlasUploadAuditResult.failure("atlas-texture-missing", elapsedMs(start));
        } else {
            try {
                result = this.auditReadback(start);
            } catch (RuntimeException e) {
                result = ForgeModelAtlasUploadAuditResult.failure(e.getClass().getSimpleName() + ": " + e.getMessage(), elapsedMs(start));
            }
        }

        this.lastAudit = result;
        if (!result.success()) {
            this.auditFailures++;
        }
        return result;
    }

    ForgeModelAtlasUploadStats createStatusSnapshot() {
        int uploadedFaces = this.sample == null ? 0 : this.sample.uploadedFaces();
        int uploadedPixels = this.sample == null ? 0 : this.sample.uploadedPixels();
        int missingFaces = this.sample == null ? 0 : this.sample.missingFaces();
        boolean textureCreated = this.textureId != 0;
        boolean sampleUploaded = this.atlasPixelsUploaded && textureCreated && this.sample != null && this.lastUploadOk;
        return new ForgeModelAtlasUploadStats(
                STAGE,
                this.uploadRuns,
                this.uploadFailures,
                this.auditRuns,
                this.auditFailures,
                this.clearRuns,
                this.lastUploadError,
                this.lastUploadDurationMs,
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
                uploadedFaces,
                uploadedPixels,
                missingFaces,
                sampleUploaded,
                sampleUploaded,
                false,
                false,
                false,
                this.atlasPixelsStale,
                this.lastReloadInvalidatedAtlasPixels,
                this.lastUploadOk,
                this.lastAudit.success(),
                this.lastAudit.error(),
                this.lastAudit.durationMs(),
                this.lastAudit.auditedFaces(),
                this.lastAudit.auditedPixels(),
                this.lastAudit.missingFaces(),
                this.lastAudit.pixelMismatches(),
                this.lastAudit.atlasReadbackOk(),
                this.sample == null ? -1 : this.sample.modelId(),
                this.sample == null ? -1 : this.sample.blockStateId(),
                this.sample == null ? "none" : this.sample.blockState(),
                this.sample == null ? "none" : this.sample.sourceSprite(),
                this.sample == null ? "none" : this.sample.sourceSpriteAtlas(),
                this.faceTileString(0),
                this.faceTileString(1),
                this.faceTileString(2),
                this.faceTileString(3),
                this.faceTileString(4),
                this.faceTileString(5),
                this.faceChecksum(0),
                this.faceChecksum(1),
                this.faceChecksum(2),
                this.faceChecksum(3),
                this.faceChecksum(4),
                this.faceChecksum(5)
        );
    }

    ForgeTexturedDebugQuadSample createTexturedDebugQuadSample(int preferredFaceIndex) {
        if (this.sample == null || this.textureId == 0 || !this.atlasPixelsUploaded || this.atlasPixelsStale) {
            return ForgeTexturedDebugQuadSample.missing("atlas-upload-sample-missing");
        }
        int faceIndex = preferredFaceIndex >= 0 && preferredFaceIndex < ForgeModelAtlasLayout.FACE_COUNT ? preferredFaceIndex : 0;
        ForgeModelAtlasPixelSample.Face face = this.sample.face(faceIndex);
        if (face == null || face.pixels().length != ForgeModelAtlasPixelSample.BYTES_PER_FACE) {
            for (int i = 0; i < ForgeModelAtlasLayout.FACE_COUNT; i++) {
                ForgeModelAtlasPixelSample.Face fallback = this.sample.face(i);
                if (fallback != null && fallback.pixels().length == ForgeModelAtlasPixelSample.BYTES_PER_FACE) {
                    faceIndex = i;
                    face = fallback;
                    break;
                }
            }
        }
        if (face == null || face.pixels().length != ForgeModelAtlasPixelSample.BYTES_PER_FACE) {
            return ForgeTexturedDebugQuadSample.missing("face-pixels-missing");
        }
        ForgeModelAtlasLayout.Tile tile = this.actualUploadTile(faceIndex, this.sample.modelId());
        return new ForgeTexturedDebugQuadSample(
                true,
                this.textureId,
                this.actualTextureWidth,
                this.actualTextureHeight,
                this.sample.modelId(),
                this.sample.blockStateId(),
                this.sample.blockState(),
                face.spriteName(),
                face.spriteAtlas(),
                faceIndex,
                face.direction(),
                tile.x(),
                tile.y(),
                tile.format(),
                face.checksum()
        );
    }

    String dumpSample() {
        ForgeModelAtlasUploadStats status = this.createStatusSnapshot();
        return String.format(
                "Voxy model atlas upload sample: sampleModelId=%d sourceBlockState=\"%s\" sourceSprite=%s sourceSpriteAtlas=%s atlasTextureObjectCreated=%s fullAtlasTextureCreated=%s debugSmallAtlasFallback=%s atlasWidth=%d atlasHeight=%d actualTextureWidth=%d actualTextureHeight=%d atlasFormat=%s atlasPixelsUploaded=%s uploadedFaces=%d uploadedPixels=%d missingFaces=%d sampleAtlasPixelsUploaded=%s realAtlasPixelUploadReady=%s formalTextureAtlasReady=false formalTexturedShaderReady=false formalModelBridgeReady=false face0Tile=%s face0Checksum=%s face1Tile=%s face1Checksum=%s face2Tile=%s face2Checksum=%s face3Tile=%s face3Checksum=%s face4Tile=%s face4Checksum=%s face5Tile=%s face5Checksum=%s draw=false renderer=none",
                status.sampleModelId(),
                status.sourceBlockState(),
                status.sourceSprite(),
                status.sourceSpriteAtlas(),
                status.atlasTextureObjectCreated(),
                status.fullAtlasTextureCreated(),
                status.debugSmallAtlasFallback(),
                status.atlasWidth(),
                status.atlasHeight(),
                status.actualTextureWidth(),
                status.actualTextureHeight(),
                status.atlasFormat(),
                status.atlasPixelsUploaded(),
                status.uploadedFaces(),
                status.uploadedPixels(),
                status.missingFaces(),
                status.sampleAtlasPixelsUploaded(),
                status.realAtlasPixelUploadReady(),
                status.face0Tile(),
                status.face0Checksum(),
                status.face1Tile(),
                status.face1Checksum(),
                status.face2Tile(),
                status.face2Checksum(),
                status.face3Tile(),
                status.face3Checksum(),
                status.face4Tile(),
                status.face4Checksum(),
                status.face5Tile(),
                status.face5Checksum()
        );
    }

    void markStale(String reason) {
        this.sample = null;
        this.deleteTexture();
        this.atlasPixelsUploaded = false;
        this.atlasPixelsStale = true;
        this.lastReloadInvalidatedAtlasPixels = true;
        this.lastUploadOk = false;
        this.lastUploadError = reason == null || reason.isBlank() ? "stale" : reason;
        this.lastAudit = ForgeModelAtlasUploadAuditResult.failure(this.lastUploadError, 0.0D);
    }

    void clear() {
        this.clearRuns++;
        this.sample = null;
        this.deleteTexture();
        this.atlasPixelsUploaded = false;
        this.atlasPixelsStale = false;
        this.lastReloadInvalidatedAtlasPixels = false;
        this.uploadRuns = 0L;
        this.uploadFailures = 0L;
        this.auditRuns = 0L;
        this.auditFailures = 0L;
        this.lastUploadError = "none";
        this.lastUploadDurationMs = 0.0D;
        this.lastUploadOk = false;
        this.lastAudit = ForgeModelAtlasUploadAuditResult.failure("none", 0.0D);
    }

    private ForgeModelAtlasPixelSample buildSample() {
        ForgeRealModelStoreSampleStats realStats = this.instance.getRealModelStoreSample().createStatusSnapshot();
        if (!realStats.realModelRecordSampleReady()) {
            realStats = this.instance.getRealModelStoreSample().build();
        }
        ForgeModelAtlasStats atlasStats = this.instance.getModelAtlasSkeleton().createStatusSnapshot();
        if (!atlasStats.atlasSkeletonReady()) {
            atlasStats = this.instance.getModelAtlasSkeleton().build();
        }
        if (!realStats.realModelRecordSampleReady() || !atlasStats.atlasSkeletonReady()) {
            return null;
        }

        BlockState state = this.blockStateForId(realStats.sourceBlockStateId()).orElse(Blocks.SAND.defaultBlockState());
        if (!isSolidSampleState(state)) {
            state = Blocks.SAND.defaultBlockState();
        }
        int blockStateId = this.blockStateIdForState(state);
        int modelId = realStats.sourceModelId();
        if (!ForgeModelAtlasLayout.isValidModelId(modelId)) {
            modelId = ForgeVoxyModelIdMapper.INSTANCE.getOrCreateModelId(blockStateId).modelId();
        }
        if (!ForgeModelAtlasLayout.isValidModelId(modelId)) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getBlockRenderer() == null) {
            return null;
        }
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        if (model == null || model.isCustomRenderer()) {
            return null;
        }

        ForgeModelAtlasPixelSample.Face[] faces = new ForgeModelAtlasPixelSample.Face[ForgeModelAtlasLayout.FACE_COUNT];
        String sourceSprite = "none";
        String sourceSpriteAtlas = "none";
        for (Direction direction : DIRECTIONS) {
            int faceIndex = direction.get3DDataValue();
            BakedQuad quad = firstQuadForFace(model, state, direction, blockStateId);
            TextureAtlasSprite sprite = quad == null ? null : quad.getSprite();
            if (sprite == null) {
                faces[faceIndex] = ForgeModelAtlasPixelSample.Face.missing(faceIndex, direction.getName());
                continue;
            }

            ForgeModelAtlasPixelSample.Face face = this.buildFace(faceIndex, direction, sprite);
            faces[faceIndex] = face;
            if (!face.missingFace() && "none".equals(sourceSprite)) {
                sourceSprite = face.spriteName();
                sourceSpriteAtlas = face.spriteAtlas();
            }
        }

        return new ForgeModelAtlasPixelSample(
                modelId,
                blockStateId,
                state.toString(),
                sourceSprite,
                sourceSpriteAtlas,
                faces
        );
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

    private boolean createTextureForSample() {
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
            this.lastUploadError = "atlas-texture-create-failed";
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
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
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

    private void uploadFace(int faceIndex, int modelId, byte[] pixels) {
        ForgeModelAtlasLayout.Tile tile = this.actualUploadTile(faceIndex, modelId);
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
                throw new IllegalStateException("glTexSubImage2D face " + faceIndex + " " + glErrorName(error));
            }
        } finally {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, oldTextureBinding);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, unpackBufferBinding);
            pixelStore.restoreUnpack();
            MemoryUtil.memFree(buffer);
        }
    }

    private ForgeModelAtlasUploadAuditResult auditReadback(long start) {
        int pixelMismatches = 0;
        int auditedFaces = 0;
        int auditedPixels = 0;
        int missingFaces = this.sample.missingFaces();
        String firstMismatch = "none";
        for (int faceIndex = 0; faceIndex < ForgeModelAtlasLayout.FACE_COUNT; faceIndex++) {
            ForgeModelAtlasPixelSample.Face face = this.sample.face(faceIndex);
            if (face == null) {
                continue;
            }
            byte[] readback = this.readbackFace(faceIndex);
            int faceMismatches = countPixelMismatches(face.pixels(), readback);
            if (faceMismatches > 0 && "none".equals(firstMismatch)) {
                firstMismatch = mismatchSummary(faceIndex, face.pixels(), readback);
            }
            pixelMismatches += faceMismatches;
            auditedFaces++;
            auditedPixels += ForgeModelAtlasPixelSample.TILE_SIZE * ForgeModelAtlasPixelSample.TILE_SIZE;
        }

        boolean success = auditedFaces > 0 && pixelMismatches == 0;
        return new ForgeModelAtlasUploadAuditResult(
                success,
                success ? "none" : "atlas-pixel-mismatch-" + firstMismatch,
                elapsedMs(start),
                auditedFaces,
                auditedPixels,
                missingFaces,
                pixelMismatches,
                success
        );
    }

    private byte[] readbackFace(int faceIndex) {
        ForgeModelAtlasLayout.Tile tile = this.actualUploadTile(faceIndex);
        ByteBuffer buffer = MemoryUtil.memAlloc(ForgeModelAtlasPixelSample.BYTES_PER_FACE);
        PixelStoreState pixelStore = PixelStoreState.capturePack();
        int packBufferBinding = GL11C.glGetInteger(GL21C.GL_PIXEL_PACK_BUFFER_BINDING);
        int oldReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int oldReadBuffer = GL11C.glGetInteger(GL11C.GL_READ_BUFFER);
        int framebuffer = GL30C.glGenFramebuffers();
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_PACK_BUFFER, 0);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, framebuffer);
            GL30C.glFramebufferTexture2D(
                    GL30C.GL_READ_FRAMEBUFFER,
                    GL30C.GL_COLOR_ATTACHMENT0,
                    GL11C.GL_TEXTURE_2D,
                    this.textureId,
                    0
            );
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            int framebufferStatus = GL30C.glCheckFramebufferStatus(GL30C.GL_READ_FRAMEBUFFER);
            if (framebufferStatus != GL30C.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("atlas read framebuffer incomplete 0x" + Integer.toHexString(framebufferStatus));
            }
            PixelStoreState.applyTightPack();
            clearGlErrors();
            GL11C.glReadPixels(
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
                throw new IllegalStateException("glReadPixels face " + faceIndex + " " + glErrorName(error));
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

    private ForgeModelAtlasLayout.Tile actualUploadTile(int faceIndex) {
        return this.actualUploadTile(faceIndex, this.sample == null ? -1 : this.sample.modelId());
    }

    private ForgeModelAtlasLayout.Tile actualUploadTile(int faceIndex, int modelId) {
        if (this.fullAtlasTextureCreated && ForgeModelAtlasLayout.isValidModelId(modelId)) {
            return ForgeModelAtlasLayout.faceTile(modelId, faceIndex);
        }
        return new ForgeModelAtlasLayout.Tile(
                (faceIndex >> 1) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE,
                (faceIndex & 1) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE
        );
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

    private String faceTileString(int faceIndex) {
        return this.sample == null ? "none" : this.sample.faceTile(faceIndex);
    }

    private String faceChecksum(int faceIndex) {
        return this.sample == null ? "none" : this.sample.faceChecksum(faceIndex);
    }

    private Optional<BlockState> blockStateForId(int blockStateId) {
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isPresent()) {
            try {
                return Optional.of(engine.get().getMapper().getBlockStateFromBlockId(blockStateId));
            } catch (RuntimeException ignored) {
                // Fall through to vanilla registry fallback.
            }
        }
        try {
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(blockStateId);
            return state == null ? Optional.empty() : Optional.of(state);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private int blockStateIdForState(BlockState state) {
        Optional<WorldEngine> engine = this.instance.getCurrentEngineOptional();
        if (engine.isPresent()) {
            try {
                return engine.get().getMapper().getIdForBlockState(state);
            } catch (RuntimeException ignored) {
                // Fall through to vanilla registry fallback.
            }
        }
        try {
            return Block.BLOCK_STATE_REGISTRY.getId(state);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static BakedQuad firstQuadForFace(BakedModel model, BlockState state, Direction direction, int blockStateId) {
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

    private static List<BakedQuad> safeGetQuads(BakedModel model, BlockState state, Direction direction, int blockStateId) {
        try {
            List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(blockStateId * 31L + (direction == null ? 17L : direction.ordinal())));
            return quads == null ? List.of() : quads;
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static boolean isSolidSampleState(BlockState state) {
        return state != null
                && !state.isAir()
                && state.getFluidState().isEmpty()
                && ForgeCpuMeshLayer.fromBlockState(state) == ForgeCpuMeshLayer.SOLID;
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

    private static String mismatchSummary(int faceIndex, byte[] expected, byte[] actual) {
        int pixelCount = Math.min(expected.length, actual.length) / ForgeModelAtlasPixelSample.BYTES_PER_PIXEL;
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            int offset = pixel * ForgeModelAtlasPixelSample.BYTES_PER_PIXEL;
            if (expected[offset] != actual[offset]
                    || expected[offset + 1] != actual[offset + 1]
                    || expected[offset + 2] != actual[offset + 2]
                    || expected[offset + 3] != actual[offset + 3]) {
                return String.format(
                        "face%d-pixel%d-e=%02X%02X%02X%02X-a=%02X%02X%02X%02X-readChecksum=%s",
                        faceIndex,
                        pixel,
                        expected[offset] & 0xFF,
                        expected[offset + 1] & 0xFF,
                        expected[offset + 2] & 0xFF,
                        expected[offset + 3] & 0xFF,
                        actual[offset] & 0xFF,
                        actual[offset + 1] & 0xFF,
                        actual[offset + 2] & 0xFF,
                        actual[offset + 3] & 0xFF,
                        ForgeModelAtlasPixelSample.checksum(actual)
                );
            }
        }
        return "byteLength-e" + expected.length + "-a" + actual.length + "-readChecksum=" + ForgeModelAtlasPixelSample.checksum(actual);
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
