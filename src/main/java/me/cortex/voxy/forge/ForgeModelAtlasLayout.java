package me.cortex.voxy.forge;

final class ForgeModelAtlasLayout {
    static final String LAYOUT_VERSION = "G6_14_VOXY_MODEL_ATLAS_SKELETON_V1";
    static final int MODEL_TEXTURE_SIZE = 16;
    static final int FACES_PER_MODEL_X = 3;
    static final int FACES_PER_MODEL_Y = 2;
    static final int MODEL_GRID_WIDTH = 256;
    static final int MODEL_GRID_HEIGHT = 256;
    static final int MODEL_ID_LIMIT = MODEL_GRID_WIDTH * MODEL_GRID_HEIGHT;
    static final int FACE_COUNT = 6;
    static final int ATLAS_WIDTH = MODEL_TEXTURE_SIZE * FACES_PER_MODEL_X * MODEL_GRID_WIDTH;
    static final int ATLAS_HEIGHT = MODEL_TEXTURE_SIZE * FACES_PER_MODEL_Y * MODEL_GRID_HEIGHT;
    static final boolean FACE_TILE_ORDER_KNOWN = true;

    private ForgeModelAtlasLayout() {
    }

    static boolean isValidModelId(int modelId) {
        return modelId >= 0 && modelId < MODEL_ID_LIMIT;
    }

    static Tile modelBaseTile(int modelId) {
        int modelTileX = modelId & 0xFF;
        int modelTileY = (modelId >> 8) & 0xFF;
        return new Tile(
                modelTileX * MODEL_TEXTURE_SIZE * FACES_PER_MODEL_X,
                modelTileY * MODEL_TEXTURE_SIZE * FACES_PER_MODEL_Y
        );
    }

    static Tile faceTile(int modelId, int faceIndex) {
        Tile base = modelBaseTile(modelId);
        return new Tile(
                base.x() + ((faceIndex >> 1) * MODEL_TEXTURE_SIZE),
                base.y() + ((faceIndex & 1) * MODEL_TEXTURE_SIZE)
        );
    }

    static boolean isValidFaceTile(Tile tile) {
        return tile.x() >= 0
                && tile.y() >= 0
                && tile.x() + MODEL_TEXTURE_SIZE <= ATLAS_WIDTH
                && tile.y() + MODEL_TEXTURE_SIZE <= ATLAS_HEIGHT;
    }

    record Tile(int x, int y) {
        String format() {
            return this.x + "," + this.y;
        }
    }
}
