package me.cortex.voxy.forge;

import java.util.Arrays;

record ForgeModelStoreRecord(
        int modelId,
        int blockStateId,
        int flags,
        int debugColour
) {
    static ForgeModelStoreRecord placeholder(ForgeVoxyModelIdMapper.ModelIdMapping mapping) {
        return new ForgeModelStoreRecord(
                mapping.modelId(),
                mapping.blockStateId(),
                ForgeModelStoreLayout.PLACEHOLDER_FLAGS,
                debugColour(mapping.modelId(), mapping.blockStateId())
        );
    }

    int[] toWords() {
        int[] words = new int[ForgeModelStoreLayout.WORDS];
        words[ForgeModelStoreLayout.WORD_MODEL_ID] = this.modelId;
        words[ForgeModelStoreLayout.WORD_BLOCK_STATE_ID] = this.blockStateId;
        words[ForgeModelStoreLayout.WORD_FLAGS] = this.flags;
        words[ForgeModelStoreLayout.WORD_DEBUG_COLOUR] = this.debugColour;
        words[ForgeModelStoreLayout.WORD_LAYOUT_MARKER] = ForgeModelStoreLayout.LAYOUT_MARKER;
        return words;
    }

    boolean matchesWords(int[] words, int wordOffset) {
        if (words == null || wordOffset < 0 || wordOffset + ForgeModelStoreLayout.WORDS > words.length) {
            return false;
        }
        return Arrays.equals(this.toWords(), Arrays.copyOfRange(words, wordOffset, wordOffset + ForgeModelStoreLayout.WORDS));
    }

    private static int debugColour(int modelId, int blockStateId) {
        int seed = modelId * 0x45D9F3B ^ blockStateId * 0x119DE1F3;
        seed ^= seed >>> 16;
        int r = 64 + (seed & 0x7F);
        int g = 64 + ((seed >>> 8) & 0x7F);
        int b = 64 + ((seed >>> 16) & 0x7F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
