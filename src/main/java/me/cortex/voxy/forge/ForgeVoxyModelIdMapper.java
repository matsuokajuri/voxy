package me.cortex.voxy.forge;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime-only placeholder for original Voxy client model ids.
 *
 * <p>The original ModelFactory assigns ids after baking block models and model
 * textures into ModelStore. G2.2 deliberately stays CPU-only, so this maps the
 * existing Voxy Mapper blockstate id to a stable 16-bit placeholder model id.
 */
public final class ForgeVoxyModelIdMapper {
    public static final ForgeVoxyModelIdMapper INSTANCE = new ForgeVoxyModelIdMapper();
    private static final int AIR_OR_MISSING_MODEL_ID = 0;
    private static final int MAX_CLIENT_MODEL_ID = 0xFFFF;

    private final Map<Integer, Integer> blockStateIdToModelId = new HashMap<>();
    private int nextModelId = 1;

    private ForgeVoxyModelIdMapper() {
    }

    public synchronized ModelIdResult getOrCreateModelId(int blockStateId) {
        if (blockStateId <= 0) {
            return new ModelIdResult(AIR_OR_MISSING_MODEL_ID, true, false);
        }

        Integer existing = this.blockStateIdToModelId.get(blockStateId);
        if (existing != null) {
            return new ModelIdResult(existing, false, false);
        }

        if (this.nextModelId > MAX_CLIENT_MODEL_ID) {
            return new ModelIdResult(AIR_OR_MISSING_MODEL_ID, true, true);
        }

        int modelId = this.nextModelId++;
        this.blockStateIdToModelId.put(blockStateId, modelId);
        return new ModelIdResult(modelId, false, false);
    }

    public synchronized int uniqueModelCount() {
        return this.blockStateIdToModelId.size();
    }

    public record ModelIdResult(int modelId, boolean missing, boolean overflow) {
    }
}
