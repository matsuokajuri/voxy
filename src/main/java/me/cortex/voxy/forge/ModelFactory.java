package me.cortex.voxy.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
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
import net.minecraftforge.client.model.data.ModelData;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

final class ModelFactory {
    private static final int MAX_BLOCK_STATE_IDS = 1 << 20;
    private static final int MAX_MODEL_IDS = 1 << 16;
    private static final int MODEL_SIZE = ForgeModelStoreLayoutSpec.MODEL_RECORD_BYTES;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final byte[] EMPTY_FACE_PIXELS = new byte[ForgeModelAtlasPixelFormat.BYTES_PER_FACE];
    private static final Field STAIR_BASE_STATE_FIELD = findStairBaseStateField();

    private final Mapper mapper;
    private final ModelStore store;
    private final ForgeSoftwareModelTextureBakery softwareBakery = new ForgeSoftwareModelTextureBakery();
    private final MemoryBuffer bakeScratchBuffer = new MemoryBuffer(ForgeSoftwareModelTextureBakery.OUTPUT_BUFFER_BYTES);
    private final ConcurrentLinkedDeque<BlockBake> bakeQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Mapper.BiomeEntry> biomeQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<ResultUploader> uploadResults = new ConcurrentLinkedDeque<>();
    private static final int MAX_UPLOAD_ATTEMPTS = 16;
    private int consecutiveUploadFailureCount;
    private final ReentrantLock blockStatesInFlightLock = new ReentrantLock();
    private final IntOpenHashSet blockStatesInFlight = new IntOpenHashSet(6000);
    private final Map<ModelEntry, Integer> modelTexture2id = new HashMap<>();
    private final List<Biome> biomes = new ArrayList<>();
    private final List<BiomeModel> modelsRequiringBiomeColours = new ArrayList<>();
    private final long[] metadataCache = new long[MAX_MODEL_IDS];
    private final int[] fluidStateLUT = new int[MAX_MODEL_IDS];
    private final int[] idMappings = new int[MAX_BLOCK_STATE_IDS];
    private Object2IntMap<BlockState> customBlockStateIdMapping;

    private int nextModelId;

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
            return false;
        }
        if (this.idMappings[blockId] != -1) {
            return false;
        }

        BlockState blockState;
        try {
            blockState = this.mapper.getBlockStateFromBlockId(blockId);
        } catch (RuntimeException e) {
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
            return;
        }
        if (!this.ensureStoreReady()) {
            return;
        }
        ResultUploader upload = this.uploadResults.poll();
        if (upload == null) {
            return;
        }
        //Do not let latched errors from earlier non-Voxy GL calls (e.g. an Oculus pipeline
        // reload) fail Voxy's own upload checks below.
        ModelStore.drainLatchedGlErrors("original-model-uploads");
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
        GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
        //Original ModelFactory.processUploads() drains every queued ResultUploader. A polled
        //uploader owns native model/atlas payloads and must be uploaded, requeued, or freed.
        while (upload != null) {
            String error;
            try {
                error = upload.upload(this.store, this);
            } catch (RuntimeException e) {
                //Pipeline failure handling tears this owner down on the next client tick. Keep
                //the native payload queue-owned until that teardown drains and frees it.
                this.uploadResults.addFirst(upload);
                throw e;
            }
            if (!"none".equals(error)) {
                //Dropping the upload would leave the model's GPU data zeroed forever (invisible
                // or black faces for every blockstate that maps to it), so retry next tick and
                // only give up after repeated failures.
                this.consecutiveUploadFailureCount++;
                if (this.consecutiveUploadFailureCount < MAX_UPLOAD_ATTEMPTS) {
                    this.uploadResults.addFirst(upload);
                    VoxyForge.LOGGER.warn(
                            "Original Voxy model upload failed (attempt {}/{}), retrying next tick: {}",
                            this.consecutiveUploadFailureCount,
                            MAX_UPLOAD_ATTEMPTS,
                            error);
                } else {
                    upload.free();
                    VoxyForge.LOGGER.error(
                            "Original Voxy model upload dropped after {} failed attempts (models referencing it will render empty until rebuild): {}",
                            MAX_UPLOAD_ATTEMPTS,
                            error);
                    this.consecutiveUploadFailureCount = 0;
                }
                break;
            }
            upload.free();
            this.consecutiveUploadFailureCount = 0;
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

    int getFluidClientStateId(int clientId) {
        if (clientId < 0 || clientId >= this.fluidStateLUT.length || this.fluidStateLUT[clientId] == -1) {
            throw new IdNotYetComputedException(clientId, false);
        }
        return this.fluidStateLUT[clientId];
    }

    void free() {
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

    private boolean ensureStoreReady() {
        if (this.store.canUploadOriginalVoxyModel()) {
            return true;
        }
        this.store.build(Minecraft.getInstance());
        if (!this.store.canUploadOriginalVoxyModel()) {
            return false;
        }
        return true;
    }

    private boolean processModelResult(Minecraft minecraft) {
        BlockBake bake = this.bakeQueue.poll();
        if (bake == null) {
            return false;
        }
        if (this.idMappings[bake.blockId()] != -1) {
            this.removeInFlight(bake.blockId());
            return true;
        }

        int softwareFlags = this.softwareBakery.renderToOutput(minecraft, bake.state(), this.bakeScratchBuffer.address);
        ColourDepthTextureData[] textures =
                ForgeSoftwareModelTextureBakery.texturesFromOutput(this.bakeScratchBuffer.address);
        ForgeOriginalVoxyModelLayer layer = chooseLayer(bake.state(), softwareFlags, textures);
        ForgeSoftwareModelTextureBakery.BakeResult softwareBake =
                new ForgeSoftwareModelTextureBakery.BakeResult(
                        textures,
                        layer,
                        softwareFlags,
                        this.softwareBakery.lastFailureReason()
                );
        if (!"none".equals(softwareBake.failureReason())) {
            this.removeInFlight(bake.blockId());
            return true;
        }

        int fluidModelId = this.resolveClientFluidModelId(bake.state());
        if (fluidModelId == Integer.MIN_VALUE) {
            this.bakeQueue.addFirst(bake);
            return false;
        }

        TintPlan tint = this.createTintPlan(minecraft, bake.state(), softwareBake);
        ModelEntry entry = new ModelEntry(softwareBake.textures(), fluidModelId, tint.dedupeColour());
        Integer duplicate = this.modelTexture2id.get(entry);
        if (duplicate != null) {
            this.idMappings[bake.blockId()] = duplicate;
            this.removeInFlight(bake.blockId());
            return true;
        }

        int modelId = this.nextModelId++;
        if (!ForgeModelAtlasLayout.isValidModelId(modelId) || modelId >= this.metadataCache.length) {
            this.removeInFlight(bake.blockId());
            return true;
        }

        tint = this.finalizeTintForNewModel(minecraft, modelId, bake.state(), tint);
        RecordBuild build = this.buildRecord(bake.state(), softwareBake, tint, fluidModelId, modelId);
        this.modelTexture2id.put(entry, modelId);
        this.metadataCache[modelId] = build.voxyMetadata();
        if (bake.state().getBlock() instanceof LiquidBlock) {
            this.fluidStateLUT[modelId] = modelId;
        } else if (fluidModelId != -1) {
            this.fluidStateLUT[modelId] = fluidModelId;
        }
        this.idMappings[bake.blockId()] = modelId;
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

    private RecordBuild buildRecord(
            BlockState state,
            ForgeSoftwareModelTextureBakery.BakeResult softwareBake,
            TintPlan tint,
            int fluidModelId,
            int modelId
    ) {
        ForgeOriginalVoxyModelLayer layer = softwareBake.layer();
        int checkMode = layer == ForgeOriginalVoxyModelLayer.SOLID
                ? TextureUtils.WRITE_CHECK_STENCIL
                : TextureUtils.WRITE_CHECK_ALPHA;
        int[] words = new int[ForgeModelStoreLayoutSpec.MODEL_RECORD_WORDS];
        Arrays.fill(words, 0);
        Arrays.fill(words, 0, ForgeModelStoreLayoutSpec.FACE_DATA_WORDS, -1);
        FaceUpload[] faces = new FaceUpload[ForgeModelAtlasLayout.FACE_COUNT];
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

        long metadata = buildVoxyMetadata(state, layer, faces, tint.hasTint(), tint.biomeDependent(), fluidModelId);
        int flags = 0;
        flags |= tint.hasTint() ? 1 : 0;
        flags |= tint.biomeDependent() ? 2 : 0;
        flags |= layer == ForgeOriginalVoxyModelLayer.TRANSLUCENT ? 4 : 0;
        flags |= (softwareBake.flags() & 1) != 0 ? 8 : 0;
        words[ForgeModelStoreLayoutSpec.WORD_FLAGS_A] = flags;
        words[ForgeModelStoreLayoutSpec.WORD_COLOUR_TINT] = tint.recordColourTint();
        words[ForgeModelStoreLayoutSpec.WORD_CUSTOM_ID] = this.customBlockStateId(state);
        MemoryBuffer mipChain = MipGen.putTexturesBuffer((softwareBake.flags() & 2) != 0, softwareBake.textures());
        return new RecordBuild(
                words,
                tint.recordColourTint(),
                faces,
                flags,
                writtenFaces,
                primarySprite,
                metadata,
                mipChain,
                tint.immediateBiomeColours(),
                tint.immediateBiomeColourBaseIndex()
        );
    }

    private TintPlan createTintPlan(Minecraft minecraft, BlockState state, ForgeSoftwareModelTextureBakery.BakeResult softwareBake) {
        int tintIndex = firstTintIndex(minecraft, state, softwareBake);
        if (tintIndex < 0) {
            return new TintPlan(false, false, -1, -1, -1, null, -1);
        }
        Biome defaultBiome = defaultBiome(minecraft);
        boolean biomeDependent = isBiomeDependentColour(minecraft, state, tintIndex);
        int capturedColour = biomeDependent ? -1 : captureColourConstant(minecraft, state, tintIndex, defaultBiome);
        // A baked quad tint index only selects a possible BlockColors entry. It
        // does not prove that one exists: vanilla cherry leaves inherit the
        // tinted leaves model but deliberately have no BlockColor registration.
        // Original Voxy queries BlockColors.getTintSources(), which treats that
        // case as untinted. Forge 1.20.1 exposes no equivalent source list, so
        // preserve the same contract through BlockColors' -1 no-tint result.
        if (!biomeDependent && capturedColour == -1) {
            return new TintPlan(false, false, -1, -1, -1, null, -1);
        }
        int constant = biomeDependent ? -1 : capturedColour | 0xFF000000;
        if (!biomeDependent) {
            return new TintPlan(true, false, tintIndex, constant, constant, null, -1);
        }
        return new TintPlan(true, true, tintIndex, -1, -1, null, -1);
    }

    private TintPlan finalizeTintForNewModel(Minecraft minecraft, int modelId, BlockState state, TintPlan tint) {
        if (!tint.biomeDependent() || !tint.hasTint()) {
            return tint;
        }
        int biomeIndex = this.modelsRequiringBiomeColours.size() * this.biomes.size();
        this.modelsRequiringBiomeColours.add(new BiomeModel(modelId, state, tint.tintIndex()));
        int[] immediateColours = null;
        if (!this.biomes.isEmpty()) {
            immediateColours = new int[this.biomes.size()];
            for (int biomeId = 0; biomeId < this.biomes.size(); biomeId++) {
                Biome biome = this.biomes.get(biomeId);
                if (biome != null) {
                    immediateColours[biomeId] = captureColourConstant(minecraft, state, tint.tintIndex(), biome) | 0xFF000000;
                }
            }
        }
        return new TintPlan(true, true, tint.tintIndex(), -1, biomeIndex, immediateColours, biomeIndex);
    }

    private ResultUploader addBiome0(int id, Biome biome) {
        for (int i = this.biomes.size(); i <= id; i++) {
            this.biomes.add(null);
        }
        Biome oldBiome = this.biomes.set(id, biome);
        if (oldBiome == biome) {
            return null;
        }
        if (oldBiome != null) {
            return null;
        }
        if (this.modelsRequiringBiomeColours.isEmpty()) {
            return null;
        }

        int[] colours = new int[this.biomes.size() * this.modelsRequiringBiomeColours.size()];
        int[] modelIds = new int[this.modelsRequiringBiomeColours.size()];
        int[] biomeIndexes = new int[this.modelsRequiringBiomeColours.size()];
        Minecraft minecraft = Minecraft.getInstance();
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
                colours[biomeIndex + biomeId] = captureColourConstant(minecraft, model.state(), model.tintIndex(), targetBiome) | 0xFF000000;
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

    private static int firstTintIndex(Minecraft minecraft, BlockState state, ForgeSoftwareModelTextureBakery.BakeResult softwareBake) {
        int bakedQuadTintIndex = firstBakedQuadTintIndex(minecraft, state);
        if (bakedQuadTintIndex >= 0) {
            return bakedQuadTintIndex;
        }
        int checkMode = softwareBake.layer() == ForgeOriginalVoxyModelLayer.SOLID
                ? TextureUtils.WRITE_CHECK_STENCIL
                : TextureUtils.WRITE_CHECK_ALPHA;
        for (ColourDepthTextureData face : softwareBake.textures()) {
            if (face != null) {
                int tintState = TextureUtils.computeFaceTint(face, checkMode);
                if (tintState == 2 || tintState == 3) {
                    return 0;
                }
            }
        }
        return -1;
    }

    private static int firstBakedQuadTintIndex(Minecraft minecraft, BlockState state) {
        try {
            BakedModel model = minecraft.getBlockRenderer().getBlockModel(state);
            if (model == null || model.isCustomRenderer()) {
                return -1;
            }
            Iterable<RenderType> renderTypes = model.getRenderTypes(state, RandomSource.create(42L), ModelData.EMPTY);
            for (RenderType renderType : renderTypes) {
                for (Direction direction : directionsWithNull()) {
                    for (BakedQuad quad : getQuads(model, state, direction, renderType)) {
                        if (quad.isTinted()) {
                            return quad.getTintIndex();
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return -1;
        }
        return -1;
    }

    private static List<BakedQuad> getQuads(BakedModel model, BlockState state, Direction direction, RenderType renderType) {
        List<BakedQuad> quads = model.getQuads(state, direction, RandomSource.create(42L), ModelData.EMPTY, renderType);
        return quads == null ? List.of() : quads;
    }

    private static Direction[] directionsWithNull() {
        return new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};
    }

    private static ForgeOriginalVoxyModelLayer chooseLayer(
            BlockState state,
            int flags,
            ColourDepthTextureData[] textures
    ) {
        ForgeOriginalVoxyModelLayer layer = ForgeOriginalVoxyModelLayer.OTHER;
        if ((flags & 4) != 0) {
            boolean anyTranslucent = false;
            for (ColourDepthTextureData face : textures) {
                anyTranslucent |= face != null && TextureUtils.hasTranslucentPixel(face);
                if (anyTranslucent) {
                    break;
                }
            }
            if (anyTranslucent) {
                layer = ForgeOriginalVoxyModelLayer.TRANSLUCENT;
            } else {
                boolean solid = true;
                for (ColourDepthTextureData face : textures) {
                    solid &= face == null || TextureUtils.isSolidWhereDrawn(face);
                    if (!solid) {
                        break;
                    }
                }
                layer = solid ? ForgeOriginalVoxyModelLayer.SOLID : ForgeOriginalVoxyModelLayer.CUTOUT;
            }
        }
        if (layer == ForgeOriginalVoxyModelLayer.OTHER && (flags & 8) != 0) {
            layer = ForgeOriginalVoxyModelLayer.CUTOUT;
        }
        if (state.is(net.minecraft.tags.BlockTags.LEAVES)) {
            layer = ForgeOriginalVoxyModelLayer.SOLID;
        }
        return layer == ForgeOriginalVoxyModelLayer.OTHER ? ForgeOriginalVoxyModelLayer.SOLID : layer;
    }

    private static boolean isBiomeDependentColour(Minecraft minecraft, BlockState state, int tintIndex) {
        boolean[] biomeDependent = new boolean[1];
        BlockAndTintGetter getter = tintGetter(state, defaultBiome(minecraft), biomeDependent);
        try {
            minecraft.getBlockColors().getColor(state, getter, BlockPos.ZERO, tintIndex);
            return biomeDependent[0];
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static int captureColourConstant(Minecraft minecraft, BlockState state, int tintIndex, Biome biome) {
        try {
            BlockColors colors = minecraft.getBlockColors();
            int rgb = colors.getColor(state, tintGetter(state, biome, null), BlockPos.ZERO, tintIndex);
            return rgb == -1 ? -1 : rgb;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static BlockAndTintGetter tintGetter(BlockState state, Biome biome, @Nullable boolean[] biomeDependent) {
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
                if (biomeDependent != null) {
                    biomeDependent[0] = true;
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

    private static Biome resolveBiome(Minecraft minecraft, String biomeId) {
        if (minecraft.level == null || biomeId == null) {
            return defaultBiome(minecraft);
        }
        ResourceLocation location = ResourceLocation.tryParse(biomeId);
        if (location == null) {
            return defaultBiome(minecraft);
        }
        Biome biome = minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).get(location);
        return biome == null ? defaultBiome(minecraft) : biome;
    }

    private static Biome defaultBiome(Minecraft minecraft) {
        if (minecraft.level == null) {
            return null;
        }
        Biome biome = minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).get(Biomes.PLAINS);
        return biome == null ? minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).iterator().next() : biome;
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
            int fluidModelId
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
            boolean occludesFace = layer != ForgeOriginalVoxyModelLayer.TRANSLUCENT
                    && depth < 0.1F
                    && ((float) upload.writtenPixels() / (float) (ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE)) > 0.9F;
            boolean canBeOccluded = depth < 0.3F;
            boolean selfLighting = depth > 0.01F || translucent;
            long faceMetadata = 0L;
            faceMetadata |= occludesFace ? 1L : 0L;
            faceMetadata |= faceCoversFullBlock ? 2L : 0L;
            faceMetadata |= canBeOccluded ? 4L : 0L;
            faceMetadata |= selfLighting ? 8L : 0L;
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

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record BlockBake(int blockId, BlockState state) {
    }

    private record TintPlan(
            boolean hasTint,
            boolean biomeDependent,
            int tintIndex,
            int dedupeColour,
            int recordColourTint,
            int[] immediateBiomeColours,
            int immediateBiomeColourBaseIndex
    ) {
    }

    private record BiomeModel(int modelId, BlockState state, int tintIndex) {
    }

    private record FaceUpload(int faceIndex, String direction, byte[] pixels, String checksum, int faceDataWord, int writtenPixels, float depth, boolean faceCoversFullBlock) {
        static FaceUpload empty(int faceIndex, String direction) {
            return new FaceUpload(faceIndex, direction, EMPTY_FACE_PIXELS.clone(), ForgeModelAtlasPixelFormat.checksum(EMPTY_FACE_PIXELS), -1, 0, -1.0F, false);
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
            this.model = new MemoryBuffer(MODEL_SIZE).zero();
            for (int i = 0; i < build.words().length; i++) {
                MemoryUtil.memPutInt(this.model.address + (long) i * Integer.BYTES, build.words()[i]);
            }
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

    private static final class ModelEntry {
        private final ColourDepthTextureData[] faces;
        private final int fluidModelId;
        private final int tintingColour;
        private final int hash;

        private ModelEntry(ColourDepthTextureData[] faces, int fluidModelId, int tintingColour) {
            this.faces = faces.clone();
            this.fluidModelId = fluidModelId;
            this.tintingColour = tintingColour;
            int value = 31 * fluidModelId + tintingColour;
            for (ColourDepthTextureData face : this.faces) {
                value = 31 * value + faceHash(face);
            }
            this.hash = value;
        }

        private String signature() {
            StringBuilder builder = new StringBuilder();
            builder.append(this.fluidModelId).append('|').append(this.tintingColour);
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
