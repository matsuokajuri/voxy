package me.cortex.voxy.forge;

import java.util.zip.CRC32;

final class ForgeModelAtlasPixelSample {
    static final int TILE_SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    static final int BYTES_PER_PIXEL = 4;
    static final int BYTES_PER_FACE = TILE_SIZE * TILE_SIZE * BYTES_PER_PIXEL;

    private final int modelId;
    private final int blockStateId;
    private final String blockState;
    private final String sourceSprite;
    private final String sourceSpriteAtlas;
    private final Face[] faces;

    ForgeModelAtlasPixelSample(
            int modelId,
            int blockStateId,
            String blockState,
            String sourceSprite,
            String sourceSpriteAtlas,
            Face[] faces
    ) {
        this.modelId = modelId;
        this.blockStateId = blockStateId;
        this.blockState = blockState;
        this.sourceSprite = sourceSprite;
        this.sourceSpriteAtlas = sourceSpriteAtlas;
        this.faces = faces.clone();
    }

    int modelId() {
        return this.modelId;
    }

    int blockStateId() {
        return this.blockStateId;
    }

    String blockState() {
        return this.blockState;
    }

    String sourceSprite() {
        return this.sourceSprite;
    }

    String sourceSpriteAtlas() {
        return this.sourceSpriteAtlas;
    }

    int uploadedFaces() {
        int count = 0;
        for (Face face : this.faces) {
            if (face != null && face.pixels().length == BYTES_PER_FACE) {
                count++;
            }
        }
        return count;
    }

    int uploadedPixels() {
        return this.uploadedFaces() * TILE_SIZE * TILE_SIZE;
    }

    int missingFaces() {
        int count = 0;
        for (Face face : this.faces) {
            if (face == null || face.missingFace()) {
                count++;
            }
        }
        return count;
    }

    Face face(int faceIndex) {
        return faceIndex < 0 || faceIndex >= this.faces.length ? null : this.faces[faceIndex];
    }

    String faceTile(int faceIndex) {
        if (!ForgeModelAtlasLayout.isValidModelId(this.modelId)) {
            return "none";
        }
        return ForgeModelAtlasLayout.faceTile(this.modelId, faceIndex).format();
    }

    String faceChecksum(int faceIndex) {
        Face face = this.face(faceIndex);
        return face == null ? "none" : face.checksum();
    }

    static String checksum(byte[] bytes) {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes, 0, bytes.length);
        return String.format("0x%08X", crc32.getValue());
    }

    record Face(
            int faceIndex,
            String direction,
            String spriteName,
            String spriteAtlas,
            boolean missingFace,
            byte[] pixels,
            String checksum
    ) {
        static Face missing(int faceIndex, String direction) {
            byte[] transparent = new byte[BYTES_PER_FACE];
            return new Face(faceIndex, direction, "none", "none", true, transparent, ForgeModelAtlasPixelSample.checksum(transparent));
        }
    }
}
