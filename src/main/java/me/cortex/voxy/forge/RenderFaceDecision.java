package me.cortex.voxy.forge;

/**
 * One CPU-side decision contract for a model face and the model touching it.
 *
 * <p>The packed model id alone is not proof that two partial faces cover one another. Same-model
 * culling therefore still requires the block-state self-culling semantic <em>and</em> the exact
 * software-bakery coverage captured by {@link FaceOcclusionMask}.</p>
 */
final class RenderFaceDecision {
    static final long MODEL_ID_MASK = 0xFFFFL << 26;
    static final long LIGHT_MASK = 0xFFL << 55;

    private RenderFaceDecision() {
    }

    static Result evaluate(
            int face,
            long selfQuad,
            long selfMetadata,
            long neighborQuad,
            long neighborMetadata,
            FaceCoverageLookup coverage
    ) {
        if (!ModelQueries.faceExists(selfMetadata, face)) {
            return Result.CULLED_MISSING_FACE;
        }

        int oppositeFace = face ^ 1;
        int selfModelId = modelId(selfQuad);
        int neighborModelId = modelId(neighborQuad);

        if (selfModelId == neighborModelId
                && ModelQueries.cullsSame(selfMetadata)
                && coverage.isFaceCoverageOccludedBy(
                        selfModelId,
                        face,
                        neighborModelId,
                        oppositeFace)) {
            return Result.CULLED_SAME_MODEL_COVERAGE;
        }

        if (ModelQueries.faceCanBeOccluded(selfMetadata, face)) {
            if (ModelQueries.faceUsesOcclusionMask(neighborMetadata, oppositeFace)) {
                if (coverage.isFaceCoverageOccludedBy(
                        selfModelId,
                        face,
                        neighborModelId,
                        oppositeFace)) {
                    return Result.CULLED_NEIGHBOR_COVERAGE;
                }
            } else if (ModelQueries.faceOccludes(neighborMetadata, oppositeFace)) {
                return Result.CULLED_NEIGHBOR_COVERAGE;
            }
        }

        return ModelQueries.faceUsesSelfLighting(selfMetadata, face)
                ? Result.MESH_SELF_LIGHT
                : Result.MESH_NEIGHBOR_LIGHT;
    }

    static int modelId(long quad) {
        return (int) ((quad & MODEL_ID_MASK) >>> 26);
    }

    static long selectedLight(Result result, long selfQuad, long neighborQuad) {
        if (!result.meshes()) {
            throw new IllegalArgumentException("A culled face has no selected light source: " + result);
        }
        return (result.usesSelfLight() ? selfQuad : neighborQuad) & LIGHT_MASK;
    }

    enum Result {
        CULLED_MISSING_FACE(false, false),
        CULLED_SAME_MODEL_COVERAGE(false, false),
        CULLED_NEIGHBOR_COVERAGE(false, false),
        MESH_NEIGHBOR_LIGHT(true, false),
        MESH_SELF_LIGHT(true, true);

        private final boolean meshes;
        private final boolean usesSelfLight;

        Result(boolean meshes, boolean usesSelfLight) {
            this.meshes = meshes;
            this.usesSelfLight = usesSelfLight;
        }

        boolean meshes() {
            return this.meshes;
        }

        boolean usesSelfLight() {
            return this.usesSelfLight;
        }
    }

    @FunctionalInterface
    interface FaceCoverageLookup {
        boolean isFaceCoverageOccludedBy(int modelId, int face, int occluderModelId, int occluderFace);
    }
}
