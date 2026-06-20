package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

final class ForgeOriginalVoxyModelFactory {
    private static final int MAX_BLOCK_STATE_IDS = 1 << 20;
    private static final int MAX_MODEL_IDS = 1 << 16;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final byte[] EMPTY_FACE_PIXELS = new byte[ForgeModelAtlasPixelSample.BYTES_PER_FACE];
    private static final Field STAIR_BASE_STATE_FIELD = findStairBaseStateField();

    private final ForgeVoxyInstance instance;
    private final Mapper mapper;
    private final ForgeFormalModelStore store;
    private final ForgeSoftwareModelTextureBakery softwareBakery = new ForgeSoftwareModelTextureBakery();
    private final ConcurrentLinkedDeque<BlockBake> bakeQueue = new ConcurrentLinkedDeque<>();
    private final ReentrantLock blockStatesInFlightLock = new ReentrantLock();
    private final IntOpenHashSet blockStatesInFlight = new IntOpenHashSet(6000);
    private final Map<ModelEntry, Integer> modelTexture2id = new HashMap<>();
    private final Map<Integer, ForgeFormalUploadedModelSummary> uploadedByBlockState = new HashMap<>();
    private final long[] metadataCache = new long[MAX_MODEL_IDS];
    private final int[] fluidStateLUT = new int[MAX_MODEL_IDS];
    private final int[] idMappings = new int[MAX_BLOCK_STATE_IDS];

    private int nextModelId = 1;
    private int requestedBlockStateCount;
    private int completedModelCount;
    private int uploadedModelRecordCount;
    private int uploadedModelColourCount;
    private int uploadedAtlasFaceCount;
    private int dedupeHitCount;
    private int dedupeMissCount;
    private int failedBakeCount;
    private int lastRequestedBlockStateId;
    private int lastUploadedBlockStateId;
    private int lastUploadedModelId;
    private int lastDuplicateBlockStateId;
    private int lastDuplicateModelId;
    private String lastFailureReason = "none";

    ForgeOriginalVoxyModelFactory(ForgeVoxyInstance instance, Mapper mapper, ForgeFormalModelStore store) {
        this.instance = instance;
        this.mapper = mapper;
        this.store = store;
        Arrays.fill(this.idMappings, -1);
        Arrays.fill(this.fluidStateLUT, -1);
        this.idMappings[0] = 0;
        this.fluidStateLUT[0] = 0;
    }

    boolean addEntry(int blockId) {
        this.requestedBlockStateCount++;
        this.lastRequestedBlockStateId = blockId;
        if (blockId < 0 || blockId >= this.idMappings.length) {
            this.fail("block-state-id-out-of-range-" + blockId);
            return false;
        }
        if (this.idMappings[blockId] != -1) {
            return false;
        }

        BlockState blockState;
        try {
            blockState = this.mapper.getBlockStateFromBlockId(blockId);
        } catch (RuntimeException e) {
            this.fail("mapper-block-state-missing-" + blockId);
            return false;
        }
        blockState = normalizeBlockState(blockState);

        boolean isFluid = blockState.getBlock() instanceof LiquidBlock;
        if (!isFluid && !blockState.getFluidState().isEmpty()) {
            BlockState fluidState = blockState.getFluidState().createLegacyBlock();
            int fluidStateId = this.mapper.getIdForBlockState(fluidState);
            if (this.idMappings[fluidStateId] == -1) {
                this.addEntry(fluidStateId);
            }
        }

        this.blockStatesInFlightLock.lock();
        try {
            if (!this.blockStatesInFlight.add(blockId)) {
                return false;
            }
            VarHandle.loadLoadFence();
            if (this.idMappings[blockId] != -1) {
                this.blockStatesInFlight.remove(blockId);
                return false;
            }
            this.bakeQueue.add(new BlockBake(blockId, blockState));
            this.lastFailureReason = "none";
            return true;
        } finally {
            this.blockStatesInFlightLock.unlock();
        }
    }

    void addBiome(Mapper.BiomeEntry biomeEntry) {
        // Original ModelFactory queues biome colour updates here. The Forge port keeps
        // the owner hook but full biome LUT uploads are still pending parity work.
    }

    int processUploadsOnRenderThread(Minecraft minecraft, int maxModels) {
        if (!RenderSystem.isOnRenderThread()) {
            this.fail("model-factory-process-not-render-thread");
            return 0;
        }
        if (!this.ensureStoreReady()) {
            return 0;
        }
        int processed = 0;
        while (processed < maxModels && this.processOne(minecraft)) {
            processed++;
        }
        return processed;
    }

    ForgeOriginalVoxyModelFactoryStats createStatusSnapshot() {
        return new ForgeOriginalVoxyModelFactoryStats(
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                this.requestedBlockStateCount,
                this.bakeQueue.size(),
                this.blockStatesInFlight.size(),
                this.completedModelCount,
                this.uploadedModelRecordCount,
                this.uploadedModelColourCount,
                this.uploadedAtlasFaceCount,
                this.dedupeHitCount,
                this.dedupeMissCount,
                this.failedBakeCount,
                this.modelTexture2id.size(),
                this.nextModelId,
                this.lastRequestedBlockStateId,
                this.lastUploadedBlockStateId,
                this.lastUploadedModelId,
                this.lastDuplicateBlockStateId,
                this.lastDuplicateModelId,
                this.lastFailureReason
        );
    }

    boolean hasPendingWork() {
        return !this.bakeQueue.isEmpty();
    }

    boolean hasModelForBlockId(int blockId) {
        return blockId >= 0 && blockId < this.idMappings.length && this.idMappings[blockId] != -1;
    }

    int getModelId(int blockId) {
        if (blockId < 0 || blockId >= this.idMappings.length || this.idMappings[blockId] == -1) {
            throw new IllegalStateException("model-id-not-yet-computed-" + blockId);
        }
        return this.idMappings[blockId];
    }

    long getModelMetadataFromClientId(int clientId) {
        if (clientId < 0 || clientId >= this.metadataCache.length) {
            return 0L;
        }
        return this.metadataCache[clientId];
    }

    int getFluidClientStateId(int clientId) {
        if (clientId < 0 || clientId >= this.fluidStateLUT.length || this.fluidStateLUT[clientId] == -1) {
            throw new IllegalStateException("fluid-model-id-not-yet-computed-" + clientId);
        }
        return this.fluidStateLUT[clientId];
    }

    private boolean ensureStoreReady() {
        if (this.store.canUploadOriginalVoxyModel()) {
            return true;
        }
        this.store.build();
        if (!this.store.canUploadOriginalVoxyModel()) {
            this.fail("formal-model-store-not-upload-ready");
            return false;
        }
        return true;
    }

    private boolean processOne(Minecraft minecraft) {
        BlockBake bake = this.bakeQueue.poll();
        if (bake == null) {
            return false;
        }
        if (this.idMappings[bake.blockId()] != -1) {
            this.removeInFlight(bake.blockId());
            return true;
        }

        ForgeSoftwareModelTextureBakery.BakeResult softwareBake = this.softwareBakery.renderToOutput(minecraft, bake.state(), bake.blockId());
        if (!"none".equals(softwareBake.failureReason())) {
            this.removeInFlight(bake.blockId());
            this.failedBakeCount++;
            this.fail("software-bakery-" + softwareBake.failureReason());
            return true;
        }

        int fluidModelId = this.resolveClientFluidModelId(bake.state());
        if (fluidModelId == Integer.MIN_VALUE) {
            this.bakeQueue.addFirst(bake);
            this.fail("fluid-model-id-not-yet-computed-" + bake.blockId());
            return false;
        }

        int tintingColour = this.captureTintColour(minecraft, bake.state(), softwareBake);
        ModelEntry entry = new ModelEntry(softwareBake.faces(), fluidModelId, tintingColour);
        Integer duplicate = this.modelTexture2id.get(entry);
        if (duplicate != null) {
            this.idMappings[bake.blockId()] = duplicate;
            this.lastDuplicateBlockStateId = bake.blockId();
            this.lastDuplicateModelId = duplicate;
            this.dedupeHitCount++;
            this.completedModelCount++;
            this.uploadedByBlockState.put(bake.blockId(), new ForgeFormalUploadedModelSummary(
                    bake.blockId(),
                    bake.state().toString(),
                    duplicate,
                    entry.signature(),
                    "dedupe-alias",
                    ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES,
                    this.getModelMetadataFromClientId(duplicate),
                    this.fluidStateLUT[duplicate]
            ));
            this.removeInFlight(bake.blockId());
            this.lastFailureReason = "none";
            return true;
        }

        int modelId = this.nextModelId++;
        if (!ForgeModelAtlasLayout.isValidModelId(modelId) || modelId == 0 || modelId >= this.metadataCache.length) {
            this.removeInFlight(bake.blockId());
            this.failedBakeCount++;
            this.fail("formal-model-id-capacity-exhausted");
            return true;
        }

        RecordBuild build = this.buildRecord(minecraft, bake.state(), softwareBake, tintingColour, fluidModelId);
        String uploadError = this.uploadModel(modelId, build);
        if (!"none".equals(uploadError)) {
            this.removeInFlight(bake.blockId());
            this.failedBakeCount++;
            this.fail(uploadError);
            return true;
        }

        this.modelTexture2id.put(entry, modelId);
        this.metadataCache[modelId] = build.voxyMetadata();
        if (bake.state().getBlock() instanceof LiquidBlock) {
            this.fluidStateLUT[modelId] = modelId;
        } else if (fluidModelId != -1) {
            this.fluidStateLUT[modelId] = fluidModelId;
        }
        this.idMappings[bake.blockId()] = modelId;
        this.lastUploadedBlockStateId = bake.blockId();
        this.lastUploadedModelId = modelId;
        this.dedupeMissCount++;
        this.completedModelCount++;
        this.uploadedByBlockState.put(bake.blockId(), new ForgeFormalUploadedModelSummary(
                bake.blockId(),
                bake.state().toString(),
                modelId,
                entry.signature(),
                build.primarySprite(),
                ForgeModelStoreFormalLayout.MODEL_RECORD_BYTES,
                build.voxyMetadata(),
                fluidModelId
        ));
        this.removeInFlight(bake.blockId());
        this.lastFailureReason = "none";
        return true;
    }

    private int resolveClientFluidModelId(BlockState state) {
        if (state.getBlock() instanceof LiquidBlock || state.getFluidState().isEmpty()) {
            return -1;
        }
        int fluidBlockStateId = this.mapper.getIdForBlockState(state.getFluidState().createLegacyBlock());
        if (fluidBlockStateId < 0 || fluidBlockStateId >= this.idMappings.length) {
            return Integer.MIN_VALUE;
        }
        int fluidModelId = this.idMappings[fluidBlockStateId];
        return fluidModelId == -1 ? Integer.MIN_VALUE : fluidModelId;
    }

    private RecordBuild buildRecord(
            Minecraft minecraft,
            BlockState state,
            ForgeSoftwareModelTextureBakery.BakeResult softwareBake,
            int tintingColour,
            int fluidModelId
    ) {
        ForgeCpuMeshLayer layer = softwareBake.layer();
        int[] words = new int[ForgeModelStoreFormalLayout.MODEL_RECORD_WORDS];
        Arrays.fill(words, 0);
        Arrays.fill(words, 0, ForgeModelStoreFormalLayout.FACE_DATA_WORDS, -1);
        FaceUpload[] faces = new FaceUpload[ForgeModelAtlasLayout.FACE_COUNT];
        int tintedFaces = 0;
        int writtenFaces = 0;
        String primarySprite = "none";

        for (Direction direction : DIRECTIONS) {
            int faceIndex = direction.get3DDataValue();
            ForgeSoftwareModelTextureBakery.FaceTexture texture = softwareBake.faces()[faceIndex];
            int writtenPixels = texture == null ? 0 : texture.writtenPixelCount(layer);
            if (writtenPixels == 0) {
                faces[faceIndex] = FaceUpload.empty(faceIndex, direction.getName());
                continue;
            }
            int tintState = texture.tintState(layer);
            if (tintState == 2 || tintState == 3) {
                tintedFaces++;
            }
            byte[] pixels = ForgeSoftwareModelTextureBakery.rgbaBytes(texture);
            int faceData = encodeSoftwareFaceData(texture, layer);
            faces[faceIndex] = new FaceUpload(faceIndex, direction.getName(), pixels, ForgeModelAtlasPixelSample.checksum(pixels), faceData);
            words[faceIndex] = faceData;
            if (faceData >= 0) {
                writtenFaces++;
            }
            if ("none".equals(primarySprite)) {
                primarySprite = "software-bakery-face-" + direction.getName();
            }
        }

        long metadata = buildVoxyMetadata(state, layer, faces, tintedFaces > 0, fluidModelId);
        int flags = 0;
        flags |= tintedFaces > 0 ? 1 : 0;
        flags |= layer == ForgeCpuMeshLayer.TRANSLUCENT ? 4 : 0;
        flags |= (softwareBake.flags() & 1) != 0 ? 8 : 0;
        words[ForgeModelStoreFormalLayout.WORD_FLAGS_A] = flags;
        words[ForgeModelStoreFormalLayout.WORD_COLOUR_TINT] = tintingColour;
        words[ForgeModelStoreFormalLayout.WORD_CUSTOM_ID] = 0;
        return new RecordBuild(words, tintingColour, faces, flags, writtenFaces, primarySprite, metadata);
    }

    private String uploadModel(int modelId, RecordBuild build) {
        String error = this.store.uploadOriginalVoxyModelRecord(modelId, build.words());
        if (!"none".equals(error)) {
            return error;
        }
        this.uploadedModelRecordCount++;
        error = this.store.uploadOriginalVoxyModelColour(modelId, build.modelColour());
        if (!"none".equals(error)) {
            return error;
        }
        this.uploadedModelColourCount++;
        for (int faceIndex = 0; faceIndex < ForgeModelAtlasLayout.FACE_COUNT; faceIndex++) {
            FaceUpload face = build.faces()[faceIndex];
            error = this.store.uploadOriginalVoxyAtlasFace(modelId, faceIndex, face.pixels());
            if (!"none".equals(error)) {
                return error;
            }
            this.uploadedAtlasFaceCount++;
        }
        return "none";
    }

    private void removeInFlight(int blockId) {
        this.blockStatesInFlightLock.lock();
        try {
            this.blockStatesInFlight.remove(blockId);
        } finally {
            this.blockStatesInFlightLock.unlock();
        }
    }

    private int captureTintColour(Minecraft minecraft, BlockState state, ForgeSoftwareModelTextureBakery.BakeResult softwareBake) {
        int tintIndex = -1;
        for (ForgeSoftwareModelTextureBakery.FaceTexture face : softwareBake.faces()) {
            if (face != null && (face.tintState(softwareBake.layer()) == 2 || face.tintState(softwareBake.layer()) == 3)) {
                tintIndex = 0;
                break;
            }
        }
        if (tintIndex < 0 || minecraft.level == null || minecraft.player == null) {
            return -1;
        }
        try {
            BlockColors colors = minecraft.getBlockColors();
            int rgb = colors.getColor(state, minecraft.level, minecraft.player.blockPosition(), tintIndex);
            return rgb == -1 ? -1 : 0xFF000000 | rgb;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private void fail(String reason) {
        this.lastFailureReason = reason == null || reason.isBlank() ? "failed" : reason.replace(' ', '-');
    }

    private static BlockState normalizeBlockState(BlockState state) {
        if (state != null && state.getBlock() instanceof StairBlock stair && STAIR_BASE_STATE_FIELD != null) {
            try {
                Object baseState = STAIR_BASE_STATE_FIELD.get(stair);
                if (baseState instanceof BlockState blockState) {
                    return blockState.getBlock().withPropertiesOf(state);
                }
            } catch (IllegalAccessException ignored) {
                // Keep the original state if the Forge field cannot be read.
            }
        }
        return state;
    }

    private static Field findStairBaseStateField() {
        try {
            Field field = StairBlock.class.getDeclaredField("baseState");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }

    private static int encodeSoftwareFaceData(ForgeSoftwareModelTextureBakery.FaceTexture texture, ForgeCpuMeshLayer layer) {
        int[] bounds = texture.bounds(layer);
        if (bounds[1] < bounds[0] || bounds[3] < bounds[2]) {
            return -1;
        }
        float depth = texture.depth(layer, layer != ForgeCpuMeshLayer.SOLID);
        if (depth < -0.1F) {
            return -1;
        }
        int minU = clampInt(bounds[0], 0, 15);
        int maxU = clampInt(bounds[1], 0, 15);
        int minV = clampInt(bounds[2], 0, 15);
        int maxV = clampInt(bounds[3], 0, 15);
        int depthEncoded = clampInt(Math.round(depth * 64.0F), 0, 62);
        int faceData = minU
                | (maxU << 4)
                | (minV << 8)
                | (maxV << 12)
                | (depthEncoded << 16);
        int area = Math.max(1, (maxU - minU + 1) * (maxV - minV + 1));
        int written = texture.writtenPixelCount(layer);
        boolean faceCoversFullBlock = minU == 0 && maxU == 15 && minV == 0 && maxV == 15;
        boolean needsAlphaDiscard = ((float) written / (float) area) < 0.9F;
        needsAlphaDiscard |= layer != ForgeCpuMeshLayer.SOLID;
        needsAlphaDiscard &= layer != ForgeCpuMeshLayer.TRANSLUCENT;
        faceData |= needsAlphaDiscard ? 1 << 22 : 0;
        faceData |= (!faceCoversFullBlock && layer != ForgeCpuMeshLayer.TRANSLUCENT) ? 1 << 23 : 0;
        int tintState = texture.tintState(layer);
        if (tintState == 2) {
            faceData |= 1 << 24;
        } else if (tintState == 3) {
            faceData |= 2 << 24;
        }
        return faceData;
    }

    private static long buildVoxyMetadata(
            BlockState state,
            ForgeCpuMeshLayer layer,
            FaceUpload[] faces,
            boolean biomeColourDependent,
            int fluidModelId
    ) {
        boolean isFluid = state.getBlock() instanceof LiquidBlock;
        boolean containsFluid = !isFluid && !state.getFluidState().isEmpty() && fluidModelId != -1;
        boolean translucent = layer == ForgeCpuMeshLayer.TRANSLUCENT || isFluid;
        boolean doubleSided = needsDoubleSidedQuads(faces);
        boolean cullsSame = cullsSame(state);
        boolean fullyOpaque = true;
        long metadata = 0L;
        for (int face = ForgeModelAtlasLayout.FACE_COUNT - 1; face >= 0; face--) {
            metadata <<= 8;
            FaceUpload upload = faces[face];
            if (upload == null || upload.faceDataWord() < 0) {
                metadata |= 0xFFL;
                fullyOpaque = false;
                continue;
            }
            int faceData = upload.faceDataWord();
            int minU = faceData & 0xF;
            int maxU = (faceData >>> 4) & 0xF;
            int minV = (faceData >>> 8) & 0xF;
            int maxV = (faceData >>> 12) & 0xF;
            int depth = (faceData >>> 16) & 0x3F;
            int writtenPixels = writtenPixelCount(upload.pixels(), layer);
            int area = Math.max(1, (maxU - minU + 1) * (maxV - minV + 1));
            boolean faceCoversFullBlock = minU == 0 && maxU == 15 && minV == 0 && maxV == 15;
            boolean occludesFace = layer != ForgeCpuMeshLayer.TRANSLUCENT
                    && !isFluid
                    && depth < 7
                    && ((float) writtenPixels / (float) (ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE)) > 0.9F;
            boolean canBeOccluded = depth < 20;
            boolean selfLighting = depth > 1 || translucent;
            long faceMetadata = 0L;
            faceMetadata |= occludesFace ? 1L : 0L;
            faceMetadata |= faceCoversFullBlock ? 2L : 0L;
            faceMetadata |= canBeOccluded ? 4L : 0L;
            faceMetadata |= selfLighting ? 8L : 0L;
            metadata |= faceMetadata;
            fullyOpaque &= occludesFace && area == 256;
        }
        long global = 0L;
        global |= biomeColourDependent ? 1L : 0L;
        global |= translucent ? 2L : 0L;
        global |= doubleSided ? 4L : 0L;
        global |= containsFluid ? 8L : 0L;
        global |= isFluid ? 16L : 0L;
        global |= cullsSame ? 32L : 0L;
        global |= fullyOpaque ? 64L : 0L;
        global |= ((long) clampInt(state.getLightEmission(), 0, 15)) << 7;
        metadata |= global << (8 * ForgeModelAtlasLayout.FACE_COUNT);
        return metadata;
    }

    private static boolean needsDoubleSidedQuads(FaceUpload[] faces) {
        return isMissingFace(faces, 0) && isMissingFace(faces, 1)
                || isMissingFace(faces, 2) && isMissingFace(faces, 3)
                || isMissingFace(faces, 4) && isMissingFace(faces, 5);
    }

    private static boolean isMissingFace(FaceUpload[] faces, int face) {
        return faces == null || face < 0 || face >= faces.length || faces[face] == null || faces[face].faceDataWord() < 0;
    }

    private static boolean cullsSame(BlockState state) {
        boolean allTrue = true;
        boolean allFalse = true;
        for (Direction direction : DIRECTIONS) {
            if (state.skipRendering(state, direction)) {
                allFalse = false;
            } else {
                allTrue = false;
            }
        }
        return allTrue && !allFalse;
    }

    private static int writtenPixelCount(byte[] pixels, ForgeCpuMeshLayer layer) {
        int count = 0;
        for (int i = ForgeModelAtlasPixelSample.BYTES_PER_PIXEL - 1; i < pixels.length; i += ForgeModelAtlasPixelSample.BYTES_PER_PIXEL) {
            int alpha = pixels[i] & 0xFF;
            if (layer == ForgeCpuMeshLayer.TRANSLUCENT ? alpha > 0 : alpha >= 128) {
                count++;
            }
        }
        return count;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record BlockBake(int blockId, BlockState state) {
    }

    private record FaceUpload(int faceIndex, String direction, byte[] pixels, String checksum, int faceDataWord) {
        static FaceUpload empty(int faceIndex, String direction) {
            return new FaceUpload(faceIndex, direction, EMPTY_FACE_PIXELS.clone(), ForgeModelAtlasPixelSample.checksum(EMPTY_FACE_PIXELS), -1);
        }
    }

    private record RecordBuild(
            int[] words,
            int modelColour,
            FaceUpload[] faces,
            int flags,
            int writtenFaceCount,
            String primarySprite,
            long voxyMetadata
    ) {
    }

    private static final class ModelEntry {
        private final ForgeSoftwareModelTextureBakery.FaceTexture[] faces;
        private final int fluidModelId;
        private final int tintingColour;
        private final int hash;

        private ModelEntry(ForgeSoftwareModelTextureBakery.FaceTexture[] faces, int fluidModelId, int tintingColour) {
            this.faces = faces.clone();
            this.fluidModelId = fluidModelId;
            this.tintingColour = tintingColour;
            int value = 31 * fluidModelId + tintingColour;
            for (ForgeSoftwareModelTextureBakery.FaceTexture face : this.faces) {
                value = 31 * value + faceHash(face);
            }
            this.hash = value;
        }

        private String signature() {
            StringBuilder builder = new StringBuilder();
            builder.append(this.fluidModelId).append('|').append(this.tintingColour);
            for (ForgeSoftwareModelTextureBakery.FaceTexture face : this.faces) {
                builder.append('|').append(faceHash(face));
            }
            return builder.toString();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof ModelEntry other)) {
                return false;
            }
            if (this.fluidModelId != other.fluidModelId || this.tintingColour != other.tintingColour || this.faces.length != other.faces.length) {
                return false;
            }
            for (int i = 0; i < this.faces.length; i++) {
                if (!faceEquals(this.faces[i], other.faces[i])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        private static boolean faceEquals(ForgeSoftwareModelTextureBakery.FaceTexture left, ForgeSoftwareModelTextureBakery.FaceTexture right) {
            if (left == right) {
                return true;
            }
            if (left == null || right == null) {
                return false;
            }
            return left.width() == right.width()
                    && left.height() == right.height()
                    && Arrays.equals(left.colour(), right.colour())
                    && Arrays.equals(left.depth(), right.depth());
        }

        private static int faceHash(ForgeSoftwareModelTextureBakery.FaceTexture face) {
            if (face == null) {
                return 0;
            }
            int value = face.width() * 312337173 ^ face.height();
            value = 31 * value + Arrays.hashCode(face.colour());
            value = 31 * value + Arrays.hashCode(face.depth());
            return value;
        }
    }
}
