package me.cortex.voxy.forge;

import java.util.List;

final class ForgeOriginalVoxyModelStoreLayoutSpec {
    static final String LAYOUT_VERSION = "ORIGINAL_VOXY_BLOCK_MODEL_V1";
    static final int MODEL_RECORD_BYTES = 64;
    static final int MODEL_RECORD_WORDS = 16;
    static final int FACE_DATA_WORDS = 6;
    static final int WORD_FLAGS_A = 6;
    static final int WORD_COLOUR_TINT = 7;
    static final int WORD_CUSTOM_ID = 8;
    static final int WORD_PADDING_START = 9;
    static final int PADDING_WORDS = 7;
    static final boolean FORMAL_LAYOUT_KNOWN = true;
    static final boolean FACE_DATA_LAYOUT_KNOWN = true;
    static final boolean FLAGS_LAYOUT_KNOWN = true;
    static final boolean COLOUR_TINT_LAYOUT_KNOWN = true;
    static final boolean CUSTOM_ID_LAYOUT_KNOWN = true;
    static final boolean ATLAS_UV_LAYOUT_KNOWN = true;
    static final boolean MATERIAL_LAYOUT_KNOWN = false;
    static final boolean FIELD_MAPPING_READY = false;
    static final boolean FACE_DATA_MAPPING_READY = true;
    static final boolean ATLAS_UV_MAPPING_READY = false;
    static final boolean MATERIAL_MAPPING_READY = false;

    private static final List<ForgeModelStoreFieldMapping> FIELDS = List.of(
            new ForgeModelStoreFieldMapping(
                    "faceData[0..5]",
                    0,
                    6,
                    true,
                    "block_model.glsl BlockModel.faceData[6]; ModelFactory processTextureBakeResult writes faceModelData at 4*face",
                    "Per-face packed bounds/depth/discard/tint data. Bits 0..3 minU, 4..7 maxU, 8..11 minV, 12..15 maxV, 16..21 face indentation encoded in 1/64 units, bit22 alpha cutout, bit23 alpha cutout override, bits24..25 tint state, bits26..31 currently unused/unknown."
            ),
            new ForgeModelStoreFieldMapping(
                    "flagsA",
                    WORD_FLAGS_A,
                    1,
                    true,
                    "block_model.glsl modelHasBiomeLUT/modelIsTranslucent/modelIsShaded; ModelFactory modelFlags",
                    "Bit0 has tint sources, bit1 colourTint indexes modelColourBuffer biome LUT, bit2 translucent model, bit3 shaded/AO model. Bits4..31 currently unused/unknown."
            ),
            new ForgeModelStoreFieldMapping(
                    "colourTint",
                    WORD_COLOUR_TINT,
                    1,
                    true,
                    "quad_util.glsl makeRemainingAttributes; ModelFactory writes -1, constant ARGB, or biome colour base index",
                    "If no tint sources, -1. If non-biome tint, packed ARGB constant. If biome-dependent, base index into modelColourBuffer; shader adds extractBiomeId(quad)."
            ),
            new ForgeModelStoreFieldMapping(
                    "customId",
                    WORD_CUSTOM_ID,
                    1,
                    true,
                    "quads.frag PATCHED_SHADER path; ModelFactory customBlockStateIdMapping",
                    "Optional custom blockstate/model id passed to shaderpack patched fragment path. Defaults to 0 when no custom mapping exists."
            ),
            new ForgeModelStoreFieldMapping(
                    "_pad[0..6]",
                    WORD_PADDING_START,
                    PADDING_WORDS,
                    true,
                    "block_model.glsl BlockModel._pad[7]",
                    "Reserved padding to keep the BlockModel record at 64 bytes. No current shader-visible formal semantics."
            ),
            new ForgeModelStoreFieldMapping(
                    "atlas tile",
                    -1,
                    0,
                    true,
                    "quads.frag getBaseUV; ModelFactory ModelBakeResultUpload atlas upload",
                    "Not stored as modelData words. The modelId selects a 256x256 atlas tile grid: x=(modelId&0xFF)*MODEL_TEXTURE_SIZE*3, y=((modelId>>8)&0xFF)*MODEL_TEXTURE_SIZE*2. Face selects one of 3x2 tiles."
            ),
            new ForgeModelStoreFieldMapping(
                    "material",
                    -1,
                    0,
                    false,
                    "no formal material record found in current ModelStore layout",
                    "ChunkSectionLayer/translucent/shaded flags are represented, but a formal material/UV metadata record beyond atlas tile and faceData is not present in this layout."
            )
    );

    private ForgeOriginalVoxyModelStoreLayoutSpec() {
    }

    static List<ForgeModelStoreFieldMapping> fields() {
        return FIELDS;
    }

    static int knownFieldCount() {
        int count = 0;
        for (ForgeModelStoreFieldMapping field : FIELDS) {
            if (field.known()) {
                count++;
            }
        }
        return count;
    }

    static int unknownFieldCount() {
        return FIELDS.size() - knownFieldCount();
    }

    static String fieldSummary() {
        StringBuilder builder = new StringBuilder();
        for (ForgeModelStoreFieldMapping field : FIELDS) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(field.name())
                    .append("@")
                    .append(field.wordOffset())
                    .append("x")
                    .append(field.wordCount())
                    .append("=")
                    .append(field.known() ? "known" : "unknown");
        }
        return builder.toString();
    }
}
