package me.cortex.voxy.forge;

import java.util.zip.CRC32;

final class ForgeModelAtlasPixelFormat {
    static final int TILE_SIZE = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
    static final int BYTES_PER_PIXEL = 4;
    static final int BYTES_PER_FACE = TILE_SIZE * TILE_SIZE * BYTES_PER_PIXEL;

    private ForgeModelAtlasPixelFormat() {
    }

    static String checksum(byte[] bytes) {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes, 0, bytes.length);
        return String.format("0x%08X", crc32.getValue());
    }
}
