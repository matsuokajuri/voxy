package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.model.data.ModelData;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

final class ModelFactory implements RenderFaceDecision.FaceCoverageLookup {
    private static final boolean AUDIT_MODEL_GPU_UPLOAD =
            Boolean.getBoolean("voxy.forge.auditRound8ModelGpuUpload");
    private static final int AUDIT_MODEL_GPU_UPLOAD_LIMIT = Math.max(
            1,
            Integer.getInteger("voxy.forge.auditRound8ModelGpuUploadCount", 32));
    private static final int MAX_BLOCK_STATE_IDS = 1 << 20;
    private static final int MAX_MODEL_IDS = 1 << 16;
    static final int MODEL_CAPACITY_WARNING_START = MAX_MODEL_IDS * 3 / 4;
    static final int MODEL_CAPACITY_WARNING_STEP = 1 << 12;
    private static final int MODEL_SIZE = ForgeModelStoreLayoutSpec.MODEL_RECORD_BYTES;
    private static final Direction[] DIRECTIONS = Direction.values();
    private final Biome DEFAULT_BIOME = Minecraft.getInstance().level.registryAccess()
            .registryOrThrow(Registries.BIOME)
            .get(Biomes.PLAINS);
    private static final byte[] EMPTY_FACE_PIXELS = new byte[ForgeModelAtlasPixelFormat.BYTES_PER_FACE];

    private final Mapper mapper;
    private final ModelStore store;
    private final ForgeSoftwareModelTextureBakery softwareBakery = new ForgeSoftwareModelTextureBakery();
    private final MemoryBuffer bakeScratchBuffer = new MemoryBuffer(ForgeSoftwareModelTextureBakery.OUTPUT_BUFFER_BYTES);
    private final ConcurrentLinkedDeque<BlockBake> bakeQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Mapper.BiomeEntry> biomeQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<ResultUploader> uploadResults = new ConcurrentLinkedDeque<>();
    private final ReentrantLock blockStatesInFlightLock = new ReentrantLock();
    private final IntOpenHashSet blockStatesInFlight = new IntOpenHashSet(6000);
    private final Map<ModelEntry, Integer> modelTexture2id = new HashMap<>();
    private final List<Biome> biomes = new ArrayList<>();
    private final List<BiomeModel> modelsRequiringBiomeColours = new ArrayList<>();
    private final long[] metadataCache = new long[MAX_MODEL_IDS];
    private final long[][] faceOcclusionMasks = new long[MAX_MODEL_IDS][];
    private final int[] fluidStateLUT = new int[MAX_MODEL_IDS];
    private final int[] idMappings = new int[MAX_BLOCK_STATE_IDS];
    private Object2IntMap<BlockState> customBlockStateIdMapping;

    private int nextModelId;
    private int nextModelCapacityWarning = MODEL_CAPACITY_WARNING_START;
    private int mappedBlockStateCount;
    private int deduplicatedBlockStateCount;
    private int auditedGpuUploadCount;

    ModelFactory(Mapper mapper, ModelStore store) {
        this.mapper = mapper;
        this.store = store;
        Arrays.fill(this.idMappings, -1);
        Arrays.fill(this.fluidStateLUT, -1);
        this.addEntry(0);
    }

    void prepareOnRenderThread(Minecraft minecraft) {
        this.softwareBakery.prepareOnRenderThread(minecraft);
    }

    void setCustomBlockStateMapping(@Nullable Object2IntMap<BlockState> mapping, String source) {
        this.customBlockStateIdMapping = mapping;
        String mappingSource = source == null || source.isBlank() ? "unknown" : source;
        VoxyForge.LOGGER.info(
                "Original Voxy block-state ID mapping source={} present={} size={}",
                mappingSource,
                mapping != null,
                mapping == null ? 0 : mapping.size());
    }

    boolean addEntry(int blockId) {
        if (blockId < 0 || blockId >= this.idMappings.length) {
            throw new IllegalArgumentException("Original Voxy block-state id is outside the model mapping capacity: " + blockId);
        }
        if (this.idMappings[blockId] != -1) {
            return false;
        }

        BlockState blockState = this.mapper.getBlockStateFromBlockId(blockId);
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
            return true;
        } finally {
            this.blockStatesInFlightLock.unlock();
        }
    }

    void addBiome(Mapper.BiomeEntry biomeEntry) {
        if (biomeEntry != null) {
            this.biomeQueue.add(biomeEntry);
        }
    }

    boolean processAllThings() {
        Mapper.BiomeEntry biomeEntry = this.biomeQueue.poll();
        while (biomeEntry != null) {
            Biome biome = resolveBiome(Minecraft.getInstance(), biomeEntry.biome);
            ResultUploader upload = this.addBiome0(biomeEntry.id, biome);
            if (upload != null) {
                this.uploadResults.add(upload);
            }
            biomeEntry = this.biomeQueue.poll();
        }

        while (this.processModelResult(Minecraft.getInstance())) {
            // Drain like original ModelFactory.processAllThings().
        }
        return this.hasWorkerWork();
    }

    void processUploads() {
        if (!RenderSystem.isOnRenderThread()) {
            throw new IllegalStateException("Original Voxy model uploads must run on the render thread");
        }
        if (!this.store.canUploadOriginalVoxyModel()) {
            throw new IllegalStateException("Original Voxy model store readiness invariant failed before model upload");
        }
        //Do not let latched errors from earlier non-Voxy GL calls (e.g. an Oculus pipeline
        // reload) fail Voxy's own upload checks below.
        ModelStore.drainLatchedGlErrors("original-model-uploads");
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        ResultUploader upload = this.uploadResults.poll();
        if (upload == null) {
            return;
        }
        //Original ModelFactory.processUploads() drains every queued ResultUploader. A polled
        //uploader owns native model/atlas payloads and must be uploaded, requeued, or freed.
        while (upload != null) {
            String error;
            try {
                error = upload.upload(this.store, this);
            } catch (RuntimeException e) {
                //Pipeline failure handling drains pending GPU copies before tearing this owner
                //down. Keep the native payload queue-owned until factory.free() releases it.
                this.uploadResults.addFirst(upload);
                throw e;
            }
            if (!"none".equals(error)) {
                //The CPU model mapping is already published. Keep the payload queue-owned so the
                // formal owner teardown can free it, and fail the frame instead of continuing with
                // a permanently partial GPU model.
                this.uploadResults.addFirst(upload);
                throw new IllegalStateException("Original Voxy model upload failed: " + error);
            }
            if (AUDIT_MODEL_GPU_UPLOAD
                    && upload instanceof ModelBakeUpload modelUpload
                    && this.auditedGpuUploadCount < AUDIT_MODEL_GPU_UPLOAD_LIMIT) {
                UploadStream.instance().commit();
                error = modelUpload.verifyGpu(this.store);
                if (!"none".equals(error)) {
                    this.uploadResults.addFirst(upload);
                    throw new IllegalStateException("Original Voxy model GPU readback failed: " + error);
                }
                this.auditedGpuUploadCount++;
                if (this.auditedGpuUploadCount == AUDIT_MODEL_GPU_UPLOAD_LIMIT) {
                    VoxyForge.LOGGER.info(
                            "Forxy Round 8 model GPU readback verified {} exact model records and mip chains.",
                            this.auditedGpuUploadCount);
                }
            }
            upload.free();
            upload = this.uploadResults.poll();
        }
        if (UploadStream.isReady()) {
            UploadStream.instance().commit();
        }
    }

    boolean hasPendingUploads() {
        return !this.uploadResults.isEmpty();
    }

    int getInflightCount() {
        return this.blockStatesInFlight.size() + this.uploadResults.size() + this.biomeQueue.size() + this.bakeQueue.size();
    }

    boolean areQueuesEmpty() {
        return !this.hasInflightWork();
    }

    private boolean hasWorkerWork() {
        this.blockStatesInFlightLock.lock();
        try {
            if (!this.blockStatesInFlight.isEmpty()) {
                return true;
            }
        } finally {
            this.blockStatesInFlightLock.unlock();
        }
        return !this.biomeQueue.isEmpty() || !this.bakeQueue.isEmpty();
    }

    private boolean hasInflightWork() {
        return this.hasWorkerWork() || !this.uploadResults.isEmpty();
    }

    int getBakedCount() {
        return this.modelTexture2id.size();
    }

    boolean hasModelForBlockId(int blockId) {
        return blockId >= 0 && blockId < this.idMappings.length && this.idMappings[blockId] != -1;
    }

    int[] _unsafeRawAccess() {
        return this.idMappings;
    }

    int getModelId(int blockId) {
        if (blockId < 0 || blockId >= this.idMappings.length || this.idMappings[blockId] == -1) {
            throw new IdNotYetComputedException(blockId, true);
        }
        return this.idMappings[blockId];
    }

    long getModelMetadataFromClientId(int clientId) {
        if (clientId < 0 || clientId >= this.metadataCache.length) {
            return 0L;
        }
        return this.metadataCache[clientId];
    }

    @Override
    public boolean isFaceCoverageOccludedBy(int modelId, int face, int occluderModelId, int occluderFace) {
        if (modelId < 0 || modelId >= this.faceOcclusionMasks.length
                || occluderModelId < 0 || occluderModelId >= this.faceOcclusionMasks.length) {
            return false;
        }
        return FaceOcclusionMask.covers(
                this.faceOcclusionMasks[occluderModelId],
                occluderFace,
                this.faceOcclusionMasks[modelId],
                face);
    }

    int getFluidClientStateId(int clientId) {
        if (clientId < 0 || clientId >= this.fluidStateLUT.length || this.fluidStateLUT[clientId] == -1) {
            throw new IdNotYetComputedException(clientId, false);
        }
        return this.fluidStateLUT[clientId];
    }

    void free() {
        VoxyForge.LOGGER.info(
                "Original Voxy model summary: mappedBlockStates={}, uniqueModels={}, deduplicatedMappings={}, capacity={}/{}",
                this.mappedBlockStateCount,
                this.nextModelId,
                this.deduplicatedBlockStateCount,
                this.nextModelId,
                MAX_MODEL_IDS);
        this.bakeQueue.clear();
        this.biomeQueue.clear();
        ResultUploader upload = this.uploadResults.poll();
        while (upload != null) {
            upload.free();
            upload = this.uploadResults.poll();
        }
        this.blockStatesInFlight.clear();
        this.softwareBakery.free();
        this.bakeScratchBuffer.free();
    }

    private boolean processModelResult(Minecraft minecraft) {
        BlockBake bake = this.bakeQueue.poll();
        if (bake == null) {
            return false;
        }

        int softwareFlags = this.softwareBakery.renderToOutput(minecraft, bake.state(), this.bakeScratchBuffer.address);
        ColourDepthTextureData[] textures =
                ForgeSoftwareModelTextureBakery.texturesFromOutput(this.bakeScratchBuffer.address);
        ForgeOriginalVoxyModelLayer layer = ForgeSoftwareModelTextureBakery.chooseLayer(
                bake.state(),
                softwareFlags,
                textures);
        ForgeSoftwareModelTextureBakery.BakeResult softwareBake =
                new ForgeSoftwareModelTextureBakery.BakeResult(
                        textures,
                        layer,
                        softwareFlags,
                        this.softwareBakery.lastFailureReason()
                );
        if (!"none".equals(softwareBake.failureReason())) {
            throw new IllegalStateException(
                    "Original Voxy software model bake failed for block-state "
                            + bake.blockId()
                            + ": "
                            + softwareBake.failureReason());
        }
        if (this.idMappings[bake.blockId()] != -1) {
            throw new IllegalStateException(
                    "Block id already added: " + bake.blockId() + " for state: " + bake.state());
        }

        int fluidModelId = this.resolveClientFluidModelId(bake.state());
        if (fluidModelId == Integer.MIN_VALUE) {
            this.bakeQueue.addFirst(bake);
            return false;
        }

        TintPlan tint = this.createTintPlan(minecraft, bake.state(), softwareBake);
        boolean containsFluid = !(bake.state().getBlock() instanceof LiquidBlock)
                && !bake.state().getFluidState().isEmpty()
                && fluidModelId != -1;
        tint = mergeContainedFluidBiomeColourDependency(
                tint,
                containsFluid,
                containsFluid ? this.metadataCache[fluidModelId] : 0L);
        PreparedRecord prepared = this.prepareRecord(bake.state(), softwareBake, tint, fluidModelId);
        ModelSemanticKey semanticKey = ModelSemanticKey.from(
                prepared,
                softwareBake,
                tint,
                fluidModelId,
                bake.state());
        ModelEntry entry = new ModelEntry(softwareBake.textures(), semanticKey);
        Integer duplicate = this.modelTexture2id.get(entry);
        if (duplicate != null) {
            this.idMappings[bake.blockId()] = duplicate;
            this.mappedBlockStateCount++;
            this.deduplicatedBlockStateCount++;
            this.removeInFlight(bake.blockId());
            return true;
        }

        int modelId = this.nextModelId;
        if (!ForgeModelAtlasLayout.isValidModelId(modelId) || modelId >= this.metadataCache.length) {
            throw new IllegalStateException("Original Voxy model capacity exhausted at model id " + modelId);
        }
        this.nextModelId++;
        this.recordModelCapacityHighWater();

        tint = this.finalizeTintForNewModel(modelId, tint);
        RecordBuild build = this.buildRecord(prepared, softwareBake, tint);
        this.modelTexture2id.put(entry, modelId);
        this.metadataCache[modelId] = build.voxyMetadata();
        this.faceOcclusionMasks[modelId] = FaceOcclusionMask.copy(build.faceOcclusionMasks());
        if (bake.state().getBlock() instanceof LiquidBlock) {
            this.fluidStateLUT[modelId] = modelId;
        } else if (fluidModelId != -1) {
            this.fluidStateLUT[modelId] = fluidModelId;
        }
        this.idMappings[bake.blockId()] = modelId;
        this.mappedBlockStateCount++;
        this.uploadResults.add(new ModelBakeUpload(bake.blockId(), modelId, build));
        this.removeInFlight(bake.blockId());
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

    private PreparedRecord prepareRecord(
            BlockState state,
            ForgeSoftwareModelTextureBakery.BakeResult softwareBake,
            TintPlan tint,
            int fluidModelId
    ) {
        return prepareRecord(
                state,
                softwareBake,
                tint,
                fluidModelId,
                this.customBlockStateId(state));
    }

    static PreparedRecord prepareRecord(
            BlockState state,
            ForgeSoftwareModelTextureBakery.BakeResult softwareBake,
            TintPlan tint,
            int fluidModelId,
            int customId
    ) {
        ForgeOriginalVoxyModelLayer layer = softwareBake.layer();
        int checkMode = layer == ForgeOriginalVoxyModelLayer.SOLID
                ? TextureUtils.WRITE_CHECK_STENCIL
                : TextureUtils.WRITE_CHECK_ALPHA;
        int[] words = new int[ForgeModelStoreLayoutSpec.MODEL_RECORD_WORDS];
        Arrays.fill(words, 0);
        Arrays.fill(words, 0, ForgeModelStoreLayoutSpec.FACE_DATA_WORDS, -1);
        FaceUpload[] faces = new FaceUpload[ForgeModelAtlasLayout.FACE_COUNT];
        long[] faceOcclusionMasks = FaceOcclusionMask.fromTextures(softwareBake.textures(), checkMode);
        int writtenFaces = 0;
        String primarySprite = "none";

        for (Direction direction : DIRECTIONS) {
            int faceIndex = direction.get3DDataValue();
            ColourDepthTextureData texture = softwareBake.textures()[faceIndex];
            int writtenPixels = texture == null ? 0 : TextureUtils.getWrittenPixelCount(texture, checkMode);
            if (writtenPixels == 0) {
                faces[faceIndex] = FaceUpload.empty(faceIndex, direction.getName());
                continue;
            }
            byte[] pixels = ForgeSoftwareModelTextureBakery.rgbaBytes(texture);
            float depth = computeSoftwareDepth(texture, layer);
            int[] bounds = TextureUtils.computeBounds(texture, checkMode);
            int faceData = encodeSoftwareFaceData(texture, layer, depth, bounds, writtenPixels, tint.hasTint());
            faces[faceIndex] = new FaceUpload(faceIndex, direction.getName(), pixels, ForgeModelAtlasPixelFormat.checksum(pixels), faceData, writtenPixels, depth, coversFullBlock(bounds));
            words[faceIndex] = faceData;
            if (faceData >= 0) {
                writtenFaces++;
            }
            if ("none".equals(primarySprite)) {
                primarySprite = "software-bakery-face-" + direction.getName();
            }
        }

        long metadata = buildVoxyMetadata(
                state,
                layer,
                faces,
                tint.hasTint(),
                tint.biomeDependent(),
                fluidModelId,
                faceOcclusionMasks);
        int flags = 0;
        flags |= tint.hasTint() ? 1 : 0;
        flags |= tint.biomeDependent() ? 2 : 0;
        flags |= layer == ForgeOriginalVoxyModelLayer.TRANSLUCENT ? 4 : 0;
        flags |= (softwareBake.flags() & 1) != 0 ? 8 : 0;
        words[ForgeModelStoreLayoutSpec.WORD_FLAGS_A] = flags;
        words[ForgeModelStoreLayoutSpec.WORD_COLOUR_TINT] = tint.dedupeColour();
        words[ForgeModelStoreLayoutSpec.WORD_CUSTOM_ID] = customId;
        return new PreparedRecord(
                words,
                faces,
                flags,
                writtenFaces,
                primarySprite,
                metadata,
                faceOcclusionMasks);
    }

    private RecordBuild buildRecord(
            PreparedRecord prepared,
            ForgeSoftwareModelTextureBakery.BakeResult softwareBake,
            TintPlan tint
    ) {
        int[] words = Arrays.copyOf(prepared.words(), prepared.words().length);
        words[ForgeModelStoreLayoutSpec.WORD_COLOUR_TINT] = tint.recordColourTint();
        MemoryBuffer mipChain = MipGen.putTexturesBuffer((softwareBake.flags() & 2) != 0, softwareBake.textures());
        return new RecordBuild(
                words,
                tint.recordColourTint(),
                prepared.faces(),
                prepared.flags(),
                prepared.writtenFaceCount(),
                prepared.primarySprite(),
                prepared.voxyMetadata(),
                prepared.faceOcclusionMasks(),
                mipChain,
                tint.immediateBiomeColours(),
                tint.immediateBiomeColourBaseIndex()
        );
    }

    static MemoryBuffer serializeModelRecord(int[] words) {
        if (words == null || words.length != ForgeModelStoreLayoutSpec.MODEL_RECORD_WORDS) {
            throw new IllegalArgumentException("Original Voxy model record must contain exactly 16 words");
        }
        MemoryBuffer model = new MemoryBuffer(MODEL_SIZE).zero();
        for (int index = 0; index < words.length; index++) {
            MemoryUtil.memPutInt(model.address + (long) index * Integer.BYTES, words[index]);
        }
        return model;
    }

    private void recordModelCapacityHighWater() {
        if (this.nextModelId < this.nextModelCapacityWarning) {
            return;
        }
        VoxyForge.LOGGER.warn(
                "Original Voxy model capacity high-water: uniqueModels={}, capacity={}, used={}%, nextWarning={}",
                this.nextModelId,
                MAX_MODEL_IDS,
                (this.nextModelId * 100L) / MAX_MODEL_IDS,
                Math.min(MAX_MODEL_IDS, this.nextModelCapacityWarning + MODEL_CAPACITY_WARNING_STEP));
        this.nextModelCapacityWarning += MODEL_CAPACITY_WARNING_STEP;
    }

    private TintPlan createTintPlan(Minecraft minecraft, BlockState state, ForgeSoftwareModelTextureBakery.BakeResult softwareBake) {
        TintSourcePlan tintSources = collectTintSources(minecraft, state, softwareBake);
        if (tintSources.isEmpty()) {
            return new TintPlan(false, false, tintSources, -1, -1, null, -1);
        }
        boolean biomeDependent = isBiomeDependentColour(tintSources, this.DEFAULT_BIOME);
        int capturedColour = biomeDependent ? -1 : captureColourConstant(tintSources, this.DEFAULT_BIOME);
        // A baked quad tint index only selects a possible BlockColors entry. It
        // does not prove that one exists: vanilla cherry leaves inherit the
        // tinted leaves model but deliberately have no BlockColor registration.
        // Original Voxy queries BlockColors.getTintSources(), which treats that
        // case as untinted. Forge 1.20.1 exposes no equivalent source list, so
        // preserve the same contract through BlockColors' -1 no-tint result.
        if (!biomeDependent && capturedColour == -1) {
            return new TintPlan(false, false, tintSources, -1, -1, null, -1);
        }
        int constant = biomeDependent ? -1 : capturedColour | 0xFF000000;
        if (!biomeDependent) {
            return new TintPlan(true, false, tintSources, constant, constant, null, -1);
        }
        return new TintPlan(true, true, tintSources, -1, -1, null, -1);
    }

    private TintPlan finalizeTintForNewModel(int modelId, TintPlan tint) {
        if (!requiresBiomeColourLut(tint)) {
            return tint;
        }
        int biomeIndex = this.modelsRequiringBiomeColours.size() * this.biomes.size();
        this.modelsRequiringBiomeColours.add(new BiomeModel(modelId, tint.tintSources()));
        int[] immediateColours = null;
        if (!this.biomes.isEmpty()) {
            immediateColours = new int[this.biomes.size()];
            for (int biomeId = 0; biomeId < this.biomes.size(); biomeId++) {
                Biome biome = this.biomes.get(biomeId);
                if (biome != null) {
                    immediateColours[biomeId] = captureColourConstant(tint.tintSources(), biome) | 0xFF000000;
                }
            }
        }
        return applyBiomeColourLut(tint, biomeIndex, immediateColours);
    }

    private ResultUploader addBiome0(int id, Biome biome) {
        if (biome == null) {
            throw new IllegalStateException("Null biome");
        }
        for (int i = this.biomes.size(); i <= id; i++) {
            this.biomes.add(null);
        }
        Biome oldBiome = this.biomes.set(id, biome);
        if (oldBiome != null && oldBiome != biome) {
            throw new IllegalStateException("Biome was put in an id that was not null");
        }
        if (oldBiome == biome) {
            Logger.error("Biome added was a duplicate: " + id);
            return null;
        }
        if (this.modelsRequiringBiomeColours.isEmpty()) {
            return null;
        }

        int[] colours = new int[this.biomes.size() * this.modelsRequiringBiomeColours.size()];
        int[] modelIds = new int[this.modelsRequiringBiomeColours.size()];
        int[] biomeIndexes = new int[this.modelsRequiringBiomeColours.size()];
        for (int modelIndex = 0; modelIndex < this.modelsRequiringBiomeColours.size(); modelIndex++) {
            BiomeModel model = this.modelsRequiringBiomeColours.get(modelIndex);
            int biomeIndex = modelIndex * this.biomes.size();
            modelIds[modelIndex] = model.modelId();
            biomeIndexes[modelIndex] = biomeIndex;
            for (int biomeId = 0; biomeId < this.biomes.size(); biomeId++) {
                Biome targetBiome = this.biomes.get(biomeId);
                if (targetBiome == null) {
                    continue;
                }
                colours[biomeIndex + biomeId] = captureColourConstant(model.tintSources(), targetBiome) | 0xFF000000;
            }
        }
        return new BiomeUpload(colours, modelIds, biomeIndexes);
    }

    private void removeInFlight(int blockId) {
        this.blockStatesInFlightLock.lock();
        try {
            this.blockStatesInFlight.remove(blockId);
        } finally {
            this.blockStatesInFlightLock.unlock();
        }
    }

    private int customBlockStateId(BlockState state) {
        if (this.customBlockStateIdMapping == null || !this.customBlockStateIdMapping.containsKey(state)) {
            return 0;
        }
        return this.customBlockStateIdMapping.getInt(state);
    }

    private static TintSourcePlan collectTintSources(
            Minecraft minecraft,
            BlockState state,
            ForgeSoftwareModelTextureBakery.BakeResult softwareBake) {
        if (state.getBlock() instanceof LiquidBlock) {
            FluidState fluidState = state.getFluidState();
            IClientFluidTypeExtensions extension = IClientFluidTypeExtensions.of(fluidState);
            return new TintSourcePlan(List.of((biome, biomeDependencyMarker) -> extension.getTintColor(
                    fluidState,
                    tintGetter(state, biome, biomeDependencyMarker),
                    BlockPos.ZERO)));
        }

        LinkedHashSet<Integer> tintIndices = collectBakedQuadTintIndices(minecraft, state);
        if (tintIndices.isEmpty() && softwareBakeHasTint(softwareBake)) {
            tintIndices.add(0);
        }
        if (tintIndices.isEmpty()) {
            return TintSourcePlan.EMPTY;
        }

        BlockColors blockColors = minecraft.getBlockColors();
        List<TintSourceDescriptor> sources = new ArrayList<>(tintIndices.size());
        for (int tintIndex : tintIndices) {
            sources.add((biome, biomeDependencyMarker) -> blockColors.getColor(
                    state,
                    tintGetter(state, biome, biomeDependencyMarker),
                    BlockPos.ZERO,
                    tintIndex));
        }
        return new TintSourcePlan(sources);
    }

    private static boolean softwareBakeHasTint(ForgeSoftwareModelTextureBakery.BakeResult softwareBake) {
        int checkMode = softwareBake.layer() == ForgeOriginalVoxyModelLayer.SOLID
                ? TextureUtils.WRITE_CHECK_STENCIL
                : TextureUtils.WRITE_CHECK_ALPHA;
        for (ColourDepthTextureData face : softwareBake.textures()) {
            if (face != null) {
                int tintState = TextureUtils.computeFaceTint(face, checkMode);
                if (tintState == 2 || tintState == 3) {
                    return true;
                }
            }
        }
        return false;
    }

    private static LinkedHashSet<Integer> collectBakedQuadTintIndices(Minecraft minecraft, BlockState state) {
        LinkedHashSet<Integer> tintIndices = new LinkedHashSet<>();
        BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
        if (model == null || model.isCustomRenderer()) {
            return tintIndices;
        }
        Iterable<RenderType> renderTypes = model.getRenderTypes(state, RandomSource.create(42L), ModelData.EMPTY);
        for (RenderType renderType : renderTypes) {
            for (Direction direction : directionsWithNull()) {
                for (BakedQuad quad : getQuads(model, state, direction, renderType)) {
                    if (quad.isTinted()) {
                        tintIndices.add(quad.getTintIndex());
                    }
                }
            }
        }
        return tintIndices;
    }

    private static List<BakedQuad> getQuads(BakedModel model, BlockState state, Direction direction, RenderType renderType) {
        List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(42L), ModelData.EMPTY, renderType);
        return quads == null ? List.of() : quads;
    }

    private static Direction[] directionsWithNull() {
        return new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};
    }

    static boolean isBiomeDependentColour(TintSourcePlan tintSources, Biome biome) {
        boolean[] biomeDependent = new boolean[1];
        Runnable biomeDependencyMarker = () -> biomeDependent[0] = true;
        for (TintSourceDescriptor source : tintSources.sources()) {
            if (source != null) {
                source.colour(biome, biomeDependencyMarker);
            }
        }
        return biomeDependent[0];
    }

    static int captureColourConstant(TintSourcePlan tintSources, Biome biome) {
        for (TintSourceDescriptor source : tintSources.sources()) {
            if (source != null) {
                int colour = source.colour(biome, null);
                if (colour != -1) {
                    return colour;
                }
            }
        }
        return -1;
    }

    private static BlockAndTintGetter tintGetter(
            BlockState state,
            Biome biome,
            @Nullable Runnable biomeDependencyMarker) {
        return new BlockAndTintGetter() {
            @Override
            public float getShade(Direction direction, boolean shade) {
                return 1.0F;
            }

            @Override
            public LevelLightEngine getLightEngine() {
                Minecraft minecraft = Minecraft.getInstance();
                return minecraft.level == null ? null : minecraft.level.getLightEngine();
            }

            @Override
            public int getBlockTint(BlockPos pos, ColorResolver resolver) {
                if (biomeDependencyMarker != null) {
                    biomeDependencyMarker.run();
                    return 0;
                }
                return resolver.getColor(biome, 0, 0);
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return state;
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return state.getFluidState();
            }

            @Override
            public int getHeight() {
                return 1;
            }

            @Override
            public int getMinBuildHeight() {
                return 0;
            }

            @Override
            public int getBrightness(LightLayer type, BlockPos pos) {
                return 0;
            }
        };
    }

    private Biome resolveBiome(Minecraft minecraft, String biomeId) {
        ResourceLocation location = new ResourceLocation(biomeId);
        Biome biome = minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).get(location);
        if (biome == null) {
            Logger.warn("Could not find biome: " + biomeId + " using default");
            return this.DEFAULT_BIOME;
        }
        return biome;
    }

    private static BlockState normalizeBlockState(BlockState state) {
        if (state != null && state.getBlock() instanceof StairBlock stair) {
            return stair.baseState.getBlock().withPropertiesOf(state);
        }
        return state;
    }

    private static int encodeSoftwareFaceData(
            ColourDepthTextureData texture,
            ForgeOriginalVoxyModelLayer layer,
            float depth,
            int[] bounds,
            int written,
            boolean hasTint
    ) {
        int checkMode = layer == ForgeOriginalVoxyModelLayer.SOLID
                ? TextureUtils.WRITE_CHECK_STENCIL
                : TextureUtils.WRITE_CHECK_ALPHA;
        if (bounds[1] < bounds[0] || bounds[3] < bounds[2]) {
            return -1;
        }
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
        boolean faceCoversFullBlock = minU == 0 && maxU == 15 && minV == 0 && maxV == 15;
        boolean needsAlphaDiscard = ((float) written / (float) area) < 0.9F;
        needsAlphaDiscard |= layer != ForgeOriginalVoxyModelLayer.SOLID;
        needsAlphaDiscard &= layer != ForgeOriginalVoxyModelLayer.TRANSLUCENT;
        faceData |= needsAlphaDiscard ? 1 << 22 : 0;
        faceData |= (!faceCoversFullBlock && layer != ForgeOriginalVoxyModelLayer.TRANSLUCENT) ? 1 << 23 : 0;
        if (hasTint) {
            int tintState = TextureUtils.computeFaceTint(texture, checkMode);
            if (tintState == 2) {
                faceData |= 1 << 24;
            } else if (tintState == 3) {
                faceData |= 2 << 24;
            }
        }
        return faceData;
    }

    private static float computeSoftwareDepth(ColourDepthTextureData texture, ForgeOriginalVoxyModelLayer layer) {
        int checkMode = layer == ForgeOriginalVoxyModelLayer.SOLID
                ? TextureUtils.WRITE_CHECK_STENCIL
                : TextureUtils.WRITE_CHECK_ALPHA;
        return TextureUtils.computeDepth(
                texture,
                layer != ForgeOriginalVoxyModelLayer.SOLID ? TextureUtils.DEPTH_MODE_MIN : TextureUtils.DEPTH_MODE_AVG,
                checkMode
        );
    }

    private static boolean coversFullBlock(int[] bounds) {
        return bounds[0] == 0 && bounds[2] == 0
                && bounds[1] == ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE - 1
                && bounds[3] == ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE - 1;
    }

    private static long buildVoxyMetadata(
            BlockState state,
            ForgeOriginalVoxyModelLayer layer,
            FaceUpload[] faces,
            boolean hasTint,
            boolean biomeColourDependent,
            int fluidModelId,
            long[] faceOcclusionMasks
    ) {
        boolean isFluid = state.getBlock() instanceof LiquidBlock;
        boolean containsFluid = !isFluid && !state.getFluidState().isEmpty() && fluidModelId != -1;
        boolean translucent = layer == ForgeOriginalVoxyModelLayer.TRANSLUCENT;
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
            float depth = upload.depth();
            boolean faceCoversFullBlock = upload.faceCoversFullBlock();
            boolean usesOcclusionMask = layer != ForgeOriginalVoxyModelLayer.TRANSLUCENT
                    && depth < 0.1F
                    && upload.writtenPixels() > 0;
            boolean occludesFace = usesOcclusionMask
                    && FaceOcclusionMask.isFullFace(faceOcclusionMasks, face);
            boolean canBeOccluded = depth < 0.3F;
            boolean selfLighting = depth > 0.01F || translucent;
            long faceMetadata = 0L;
            faceMetadata |= occludesFace ? 1L : 0L;
            faceMetadata |= faceCoversFullBlock ? 2L : 0L;
            faceMetadata |= canBeOccluded ? 4L : 0L;
            faceMetadata |= selfLighting ? 8L : 0L;
            faceMetadata |= usesOcclusionMask ? 16L : 0L;
            metadata |= faceMetadata;
            fullyOpaque &= occludesFace;
        }
        long global = 0L;
        global |= biomeColourDependent ? 1L : 0L;
        global |= translucent ? 2L : 0L;
        global |= doubleSided ? 4L : 0L;
        global |= containsFluid ? 8L : 0L;
        global |= isFluid ? 16L : 0L;
        global |= cullsSame ? 32L : 0L;
        global |= fullyOpaque ? 64L : 0L;
        global |= ((long) getBlockLightEmission(state)) << 7;
        metadata |= global << (8 * ForgeModelAtlasLayout.FACE_COUNT);
        return metadata;
    }

    static TintPlan mergeContainedFluidBiomeColourDependency(
            TintPlan tint,
            boolean containsFluid,
            long fluidModelMetadata) {
        boolean biomeColourDependent = tint.biomeDependent()
                || containsFluid && ModelQueries._notIsBiomeColoured(fluidModelMetadata) == 0L;
        if (biomeColourDependent == tint.biomeDependent()) {
            return tint;
        }
        return new TintPlan(
                tint.hasTint(),
                biomeColourDependent,
                tint.tintSources(),
                tint.dedupeColour(),
                tint.recordColourTint(),
                tint.immediateBiomeColours(),
                tint.immediateBiomeColourBaseIndex());
    }

    static boolean requiresBiomeColourLut(TintPlan tint) {
        return tint.biomeDependent() && tint.hasTint();
    }

    static TintPlan applyBiomeColourLut(TintPlan tint, int biomeIndex, int[] immediateColours) {
        if (!requiresBiomeColourLut(tint)) {
            return tint;
        }
        return new TintPlan(
                true,
                true,
                tint.tintSources(),
                tint.dedupeColour(),
                biomeIndex,
                immediateColours,
                biomeIndex);
    }

    private static int getBlockLightEmission(BlockState state) {
        boolean isEmissive = state.emissiveRendering(new BlockGetter() {
            @Override
            public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return state;
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return state.getFluidState();
            }

            @Override
            public int getHeight() {
                return 0;
            }

            @Override
            public int getMinBuildHeight() {
                return 0;
            }
        }, BlockPos.ZERO);
        if (isEmissive) {
            return 15;
        }
        return clampInt(state.getLightEmission(), 0, 15);
    }

    private static boolean needsDoubleSidedQuads(FaceUpload[] faces) {
        return classifyDoubleSidedFaces(faces).required();
    }

    static DoubleSidedClassification classifyDoubleSidedFaces(FaceUpload[] faces) {
        int presentFaceMask = 0;
        if (faces != null) {
            for (int face = 0; face < Math.min(faces.length, ForgeModelAtlasLayout.FACE_COUNT); face++) {
                if (!isMissingFace(faces, face)) {
                    presentFaceMask |= 1 << face;
                }
            }
        }
        int missingOppositeAxisMask = 0;
        for (int axis = 0; axis < 3; axis++) {
            int negativeFace = axis << 1;
            int pairMask = 0b11 << negativeFace;
            if ((presentFaceMask & pairMask) == 0) {
                missingOppositeAxisMask |= 1 << axis;
            }
        }
        return new DoubleSidedClassification(
                presentFaceMask != 0 && missingOppositeAxisMask != 0,
                presentFaceMask,
                missingOppositeAxisMask);
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

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record BlockBake(int blockId, BlockState state) {
    }

    @FunctionalInterface
    interface TintSourceDescriptor {
        int colour(Biome biome, @Nullable Runnable biomeDependencyMarker);
    }

    record TintSourcePlan(List<TintSourceDescriptor> sources) {
        private static final TintSourcePlan EMPTY = new TintSourcePlan(List.of());

        TintSourcePlan {
            sources = List.copyOf(sources);
        }

        boolean isEmpty() {
            return this.sources.isEmpty();
        }
    }

    record TintPlan(
            boolean hasTint,
            boolean biomeDependent,
            TintSourcePlan tintSources,
            int dedupeColour,
            int recordColourTint,
            int[] immediateBiomeColours,
            int immediateBiomeColourBaseIndex
    ) {
    }

    private record BiomeModel(int modelId, TintSourcePlan tintSources) {
    }

    record FaceUpload(int faceIndex, String direction, byte[] pixels, String checksum, int faceDataWord, int writtenPixels, float depth, boolean faceCoversFullBlock) {
        static FaceUpload empty(int faceIndex, String direction) {
            return new FaceUpload(faceIndex, direction, EMPTY_FACE_PIXELS.clone(), ForgeModelAtlasPixelFormat.checksum(EMPTY_FACE_PIXELS), -1, 0, -1.0F, false);
        }
    }

    record DoubleSidedClassification(
            boolean required,
            int presentFaceMask,
            int missingOppositeAxisMask
    ) {
    }

    record PreparedRecord(
            int[] words,
            FaceUpload[] faces,
            int flags,
            int writtenFaceCount,
            String primarySprite,
            long voxyMetadata,
            long[] faceOcclusionMasks
    ) {
    }

    record FaceDataKey(int down, int up, int north, int south, int west, int east) {
        static FaceDataKey from(int[] words) {
            if (words == null || words.length < ForgeModelStoreLayoutSpec.FACE_DATA_WORDS) {
                throw new IllegalArgumentException("Model record does not contain all six face-data words");
            }
            return new FaceDataKey(words[0], words[1], words[2], words[3], words[4], words[5]);
        }
    }

    record ModelSemanticKey(
            int fluidModelId,
            long voxyMetadata,
            int flags,
            int customId,
            int tintColour,
            @Nullable BlockState biomeTintState,
            int softwareFlags,
            ForgeOriginalVoxyModelLayer layer,
            FaceDataKey faceData
    ) {
        static ModelSemanticKey from(
                PreparedRecord prepared,
                ForgeSoftwareModelTextureBakery.BakeResult softwareBake,
                TintPlan tint,
                int fluidModelId,
                BlockState state
        ) {
            BlockState biomeTintState = tint.biomeDependent() ? state : null;
            return new ModelSemanticKey(
                    fluidModelId,
                    prepared.voxyMetadata(),
                    prepared.flags(),
                    prepared.words()[ForgeModelStoreLayoutSpec.WORD_CUSTOM_ID],
                    tint.dedupeColour(),
                    biomeTintState,
                    softwareBake.flags(),
                    softwareBake.layer(),
                    FaceDataKey.from(prepared.words()));
        }
    }

    private record RecordBuild(
            int[] words,
            int modelColour,
            FaceUpload[] faces,
            int flags,
            int writtenFaceCount,
            String primarySprite,
            long voxyMetadata,
            long[] faceOcclusionMasks,
            MemoryBuffer mipChain,
            int[] immediateBiomeColours,
            int immediateBiomeColourBaseIndex
    ) {
    }

    private interface ResultUploader {
        String upload(ModelStore store, ModelFactory factory);

        default void free() {
        }
    }

    private static final class ModelBakeUpload implements ResultUploader {
        private final int blockStateId;
        private final int modelId;
        private final RecordBuild build;
        private final MemoryBuffer model;
        private final MemoryBuffer texture;
        private final MemoryBuffer biomeUpload;

        private ModelBakeUpload(int blockStateId, int modelId, RecordBuild build) {
            this.blockStateId = blockStateId;
            this.modelId = modelId;
            this.build = build;
            this.model = serializeModelRecord(build.words());
            this.texture = build.mipChain();
            if (build.immediateBiomeColours() == null) {
                this.biomeUpload = null;
            } else {
                this.biomeUpload = new MemoryBuffer((long) build.immediateBiomeColours().length * Integer.BYTES);
                long ptr = this.biomeUpload.address;
                for (int colour : build.immediateBiomeColours()) {
                    MemoryUtil.memPutInt(ptr, colour);
                    ptr += Integer.BYTES;
                }
            }
        }

        @Override
        public String upload(ModelStore store, ModelFactory factory) {
            String error = store.uploadOriginalVoxyModelRecord(this.modelId, this.model);
            if (!"none".equals(error)) {
                return error;
            }
            if (this.biomeUpload != null) {
                error = store.uploadOriginalVoxyModelColourRange(
                        this.build.immediateBiomeColourBaseIndex(),
                        this.biomeUpload
                );
                if (!"none".equals(error)) {
                    return error;
                }
            }
            error = store.uploadOriginalVoxyModelTextureMipChain(this.modelId, this.texture);
            if (!"none".equals(error)) {
                return error;
            }
            return "none";
        }

        private String verifyGpu(ModelStore store) {
            return store.verifyOriginalVoxyModelUpload(this.modelId, this.model, this.texture);
        }

        @Override
        public void free() {
            this.model.free();
            this.texture.free();
            if (this.biomeUpload != null) {
                this.biomeUpload.free();
            }
        }
    }

    private static final class BiomeUpload implements ResultUploader {
        private final MemoryBuffer biomeColourBuffer;
        private final MemoryBuffer modelBiomeIndexPairs;
        private final int colourCount;

        private BiomeUpload(int[] colours, int[] modelIds, int[] biomeIndexes) {
            this.biomeColourBuffer = new MemoryBuffer((long) colours.length * Integer.BYTES);
            long colourPtr = this.biomeColourBuffer.address;
            for (int colour : colours) {
                MemoryUtil.memPutInt(colourPtr, colour);
                colourPtr += Integer.BYTES;
            }
            this.modelBiomeIndexPairs = new MemoryBuffer((long) modelIds.length * Long.BYTES);
            long pairPtr = this.modelBiomeIndexPairs.address;
            for (int i = 0; i < modelIds.length; i++) {
                MemoryUtil.memPutLong(pairPtr, Integer.toUnsignedLong(modelIds[i]) | (Integer.toUnsignedLong(biomeIndexes[i]) << 32));
                pairPtr += Long.BYTES;
            }
            this.colourCount = colours.length;
        }

        @Override
        public String upload(ModelStore store, ModelFactory factory) {
            String error = store.uploadOriginalVoxyBiomeUpload(this.biomeColourBuffer, this.modelBiomeIndexPairs);
            if (!"none".equals(error)) {
                return error;
            }
            return "none";
        }

        @Override
        public void free() {
            this.biomeColourBuffer.free();
            this.modelBiomeIndexPairs.free();
        }
    }

    static final class ModelEntry {
        private final ColourDepthTextureData[] faces;
        private final ModelSemanticKey semantics;
        private final int hash;

        ModelEntry(ColourDepthTextureData[] faces, ModelSemanticKey semantics) {
            this.faces = faces.clone();
            this.semantics = semantics;
            int value = semantics.hashCode();
            for (ColourDepthTextureData face : this.faces) {
                value = 31 * value + faceHash(face);
            }
            this.hash = value;
        }

        private String signature() {
            StringBuilder builder = new StringBuilder();
            builder.append(this.semantics);
            for (ColourDepthTextureData face : this.faces) {
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
            if (!this.semantics.equals(other.semantics) || this.faces.length != other.faces.length) {
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

        private static boolean faceEquals(ColourDepthTextureData left, ColourDepthTextureData right) {
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

        private static int faceHash(ColourDepthTextureData face) {
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
