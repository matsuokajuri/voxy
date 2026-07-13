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

final class ForgeOriginalVoxyModelFactory {
    private static final int MAX_BLOCK_STATE_IDS = 1 << 20;
    private static final int MAX_MODEL_IDS = 1 << 16;
    private static final int MODEL_SIZE = ForgeOriginalVoxyModelStoreLayoutSpec.MODEL_RECORD_BYTES;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final byte[] EMPTY_FACE_PIXELS = new byte[ForgeModelAtlasPixelFormat.BYTES_PER_FACE];
    private static final Field STAIR_BASE_STATE_FIELD = findStairBaseStateField();
    private static final boolean AUDIT_SHADERPACK = Boolean.getBoolean("voxy.forge.auditShaderpack");
    private static final int MAX_SHADERPACK_MODEL_AUDITS = 48;

    private final Mapper mapper;
    private final ForgeOriginalVoxyModelStore store;
    private final ForgeSoftwareModelTextureBakery softwareBakery = new ForgeSoftwareModelTextureBakery();
    private final MemoryBuffer bakeScratchBuffer = new MemoryBuffer(ForgeSoftwareModelTextureBakery.OUTPUT_BUFFER_BYTES);
    private final ConcurrentLinkedDeque<BlockBake> bakeQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Mapper.BiomeEntry> biomeQueue = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<ResultUploader> uploadResults = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<PendingUploadAudit> pendingUploadAudits = new ConcurrentLinkedDeque<>();
    private static final int MAX_UPLOAD_ATTEMPTS = 16;
    private int consecutiveUploadFailureCount;
    private long droppedUploadCount;
    private final ReentrantLock blockStatesInFlightLock = new ReentrantLock();
    private final IntOpenHashSet blockStatesInFlight = new IntOpenHashSet(6000);
    private final Map<ModelEntry, Integer> modelTexture2id = new HashMap<>();
    private final Map<Integer, ForgeOriginalUploadedModelSummary> uploadedByBlockState = new HashMap<>();
    private final List<Biome> biomes = new ArrayList<>();
    private final List<BiomeModel> modelsRequiringBiomeColours = new ArrayList<>();
    private final long[] metadataCache = new long[MAX_MODEL_IDS];
    private final int[] fluidStateLUT = new int[MAX_MODEL_IDS];
    private final int[] idMappings = new int[MAX_BLOCK_STATE_IDS];
    private Object2IntMap<BlockState> customBlockStateIdMapping;

    private int nextModelId;
    private int requestedBlockStateCount;
    private int completedModelCount;
    private int uploadedModelRecordCount;
    private int uploadedModelColourCount;
    private int uploadedAtlasFaceCount;
    private int dedupeHitCount;
    private int dedupeMissCount;
    private int failedBakeCount;
    private int biomeUploadResultCount;
    private int lastRequestedBlockStateId;
    private int lastUploadedBlockStateId;
    private int lastUploadedModelId;
    private int lastDuplicateBlockStateId;
    private int lastDuplicateModelId;
    private int modelStoreReadbackAuditRuns;
    private int modelStoreReadbackAuditFailures;
    private int lastAuditedModelId;
    private int shaderpackModelAuditRuns;
    private boolean customBlockStateIdMappingReady;
    private boolean customBlockStateIdMappingPresent;
    private String customBlockStateIdMappingSource = "none";
    private boolean originalModelStoreReadbackAuditReady;
    private boolean modelDataReadbackOk;
    private boolean modelColourReadbackOk;
    private boolean atlasMipChainReadbackOk;
    private String lastModelStoreReadbackAuditFailureReason = "not-audited";
    private String lastFailureReason = "none";

    ForgeOriginalVoxyModelFactory(Mapper mapper, ForgeOriginalVoxyModelStore store) {
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
        this.customBlockStateIdMappingReady = true;
        this.customBlockStateIdMappingPresent = mapping != null;
        this.customBlockStateIdMappingSource = source == null || source.isBlank() ? "unknown" : source;
        VoxyForge.LOGGER.info(
                "Original Voxy block-state ID mapping source={} present={} size={}",
                this.customBlockStateIdMappingSource,
                this.customBlockStateIdMappingPresent,
                mapping == null ? 0 : mapping.size());
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

    int processUploadsOnRenderThread() {
        if (!RenderSystem.isOnRenderThread()) {
            this.fail("model-factory-upload-not-render-thread");
            return 0;
        }
        if (!this.ensureStoreReady()) {
            return 0;
        }
        int processed = 0;
        ResultUploader upload = this.uploadResults.poll();
        if (upload == null) {
            return 0;
        }
        //Do not let latched errors from earlier non-Voxy GL calls (e.g. an Oculus pipeline
        // reload) fail Voxy's own upload-audit checks below.
        ForgeOriginalVoxyModelStore.drainLatchedGlErrors("original-model-uploads");
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
                    this.droppedUploadCount++;
                    VoxyForge.LOGGER.error(
                            "Original Voxy model upload dropped after {} failed attempts (models referencing it will render empty until rebuild): {}",
                            MAX_UPLOAD_ATTEMPTS,
                            error);
                    this.consecutiveUploadFailureCount = 0;
                }
                this.fail(error);
                break;
            }
            upload.free();
            this.consecutiveUploadFailureCount = 0;
            processed++;
            upload = this.uploadResults.poll();
        }
        if (ForgeOriginalVoxyUploadStream.isReady()) {
            ForgeOriginalVoxyUploadStream.instance().commit();
            this.auditPendingUploads(this.store);
        }
        return processed;
    }

    ForgeOriginalVoxyModelFactoryStats createStatusSnapshot() {
        return new ForgeOriginalVoxyModelFactoryStats(
                true,
                true,
                true,
                this.store.originalModelStoreOwnerReady(),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                ForgeOriginalVoxyTextureUtils.byteForByteAuditReady(),
                true,
                true,
                false,
                this.originalModelStoreReadbackAuditReady,
                this.modelDataReadbackOk,
                this.modelColourReadbackOk,
                this.atlasMipChainReadbackOk,
                this.modelStoreReadbackAuditRuns,
                this.modelStoreReadbackAuditFailures,
                this.lastAuditedModelId,
                this.customBlockStateIdMappingReady,
                this.customBlockStateIdMappingPresent,
                this.customBlockStateIdMappingSource,
                this.lastModelStoreReadbackAuditFailureReason,
                this.requestedBlockStateCount,
                this.biomeQueue.size(),
                this.uploadResults.size(),
                this.biomeUploadResultCount,
                this.bakeQueue.size(),
                this.blockStatesInFlight.size() + this.uploadResults.size() + this.biomeQueue.size(),
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

    String lastFailureReason() {
        return this.lastFailureReason;
    }

    boolean hasModelForBlockId(int blockId) {
        return blockId >= 0 && blockId < this.idMappings.length && this.idMappings[blockId] != -1;
    }

    int[] _unsafeRawAccess() {
        return this.idMappings;
    }

    int getModelId(int blockId) {
        if (blockId < 0 || blockId >= this.idMappings.length || this.idMappings[blockId] == -1) {
            throw new ForgeOriginalVoxyIdNotYetComputedException(blockId, true);
        }
        return this.idMappings[blockId];
    }

    void auditBlockStateModelOnRenderThread(
            int blockX,
            int blockY,
            int blockZ,
            BlockState clientState,
            boolean clientChunkLoaded,
            boolean worldVoxelPresent,
            long worldMapping) {
        if (!RenderSystem.isOnRenderThread()) {
            VoxyForge.LOGGER.warn(
                    "Original Voxy block-model target audit unavailable: not on render thread block=[{},{},{}]",
                    blockX,
                    blockY,
                    blockZ);
            return;
        }
        int ingestedBlockStateId = worldVoxelPresent ? Mapper.getBlockId(worldMapping) : -1;
        int ingestedBiomeId = worldVoxelPresent ? Mapper.getBiomeId(worldMapping) : -1;
        BlockState state = worldVoxelPresent
                ? this.mapper.getBlockStateFromBlockId(ingestedBlockStateId)
                : clientState;
        int blockStateId = worldVoxelPresent
                ? ingestedBlockStateId
                : this.findExistingBlockStateId(state);
        int modelId = blockStateId >= 0 && blockStateId < this.idMappings.length
                ? this.idMappings[blockStateId]
                : -1;
        ForgeOriginalUploadedModelSummary summary = this.uploadedByBlockState.get(blockStateId);
        boolean modelIdZeroForNonAir = modelId == 0 && !state.isAir();
        if (modelId < 0 || modelId >= MAX_MODEL_IDS) {
            VoxyForge.LOGGER.info(
                    "Original Voxy block-model target audit: block=[{},{},{}] clientChunkLoaded={} clientState={} worldVoxelPresent={} worldMapping=0x{} ingestedState={} blockStateId={} biomeId={} modelId={} modelIdZeroForNonAir={} summary={} modelReady=false",
                    blockX,
                    blockY,
                    blockZ,
                    clientChunkLoaded,
                    clientState,
                    worldVoxelPresent,
                    Long.toHexString(worldMapping),
                    state,
                    blockStateId,
                    ingestedBiomeId,
                    modelId,
                    modelIdZeroForNonAir,
                    summary);
            return;
        }

        byte[] modelRecord = new byte[MODEL_SIZE];
        byte[] texture = new byte[(int) ForgeOriginalVoxyMipGen.UPLOADED_MIP_CHAIN_BYTES];
        String modelReadError = this.store.readOriginalVoxyModelRecord(modelId, modelRecord);
        String textureReadError = this.store.readOriginalVoxyModelTextureMipChain(modelId, texture);
        int[] words = new int[MODEL_SIZE / Integer.BYTES];
        for (int word = 0; word < words.length; word++) {
            int offset = word * Integer.BYTES;
            words[word] = Byte.toUnsignedInt(modelRecord[offset])
                    | (Byte.toUnsignedInt(modelRecord[offset + 1]) << 8)
                    | (Byte.toUnsignedInt(modelRecord[offset + 2]) << 16)
                    | (Byte.toUnsignedInt(modelRecord[offset + 3]) << 24);
        }

        int levelWidth = ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE * ForgeModelAtlasLayout.FACES_PER_MODEL_X;
        int[] alphaZero = new int[ForgeModelAtlasLayout.FACE_COUNT];
        int[] alphaCutout = new int[ForgeModelAtlasLayout.FACE_COUNT];
        int[] alphaOpaque = new int[ForgeModelAtlasLayout.FACE_COUNT];
        int[] rgbaZero = new int[ForgeModelAtlasLayout.FACE_COUNT];
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            int baseX = (face >> 1) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
            int baseY = (face & 1) * ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE;
            for (int y = 0; y < ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE; y++) {
                for (int x = 0; x < ForgeModelAtlasLayout.MODEL_TEXTURE_SIZE; x++) {
                    int pixel = ((baseY + y) * levelWidth + baseX + x)
                            * ForgeModelAtlasPixelFormat.BYTES_PER_PIXEL;
                    int red = Byte.toUnsignedInt(texture[pixel]);
                    int green = Byte.toUnsignedInt(texture[pixel + 1]);
                    int blue = Byte.toUnsignedInt(texture[pixel + 2]);
                    int alpha = Byte.toUnsignedInt(texture[pixel + 3]);
                    if ((red | green | blue | alpha) == 0) {
                        rgbaZero[face]++;
                    }
                    if (alpha == 0) {
                        alphaZero[face]++;
                    } else if (alpha <= 25) {
                        alphaCutout[face]++;
                    } else {
                        alphaOpaque[face]++;
                    }
                }
            }
        }

        StringBuilder faceWords = new StringBuilder();
        for (int face = 0; face < ForgeOriginalVoxyModelStoreLayoutSpec.FACE_DATA_WORDS; face++) {
            if (!faceWords.isEmpty()) {
                faceWords.append(',');
            }
            faceWords.append(String.format(
                    java.util.Locale.ROOT,
                    "0x%08x(discard=%d,override=%d)",
                    words[face],
                    (words[face] >>> 22) & 1,
                    (words[face] >>> 23) & 1));
        }
        StringBuilder alphaSummary = new StringBuilder();
        for (int face = 0; face < ForgeModelAtlasLayout.FACE_COUNT; face++) {
            if (!alphaSummary.isEmpty()) {
                alphaSummary.append(';');
            }
            alphaSummary.append(face)
                    .append(":zero=").append(alphaZero[face])
                    .append(",low=").append(alphaCutout[face])
                    .append(",opaque=").append(alphaOpaque[face])
                    .append(",rgbaZero=").append(rgbaZero[face]);
        }
        int flagsA = words[ForgeOriginalVoxyModelStoreLayoutSpec.WORD_FLAGS_A];
        int actualCustomId = words[ForgeOriginalVoxyModelStoreLayoutSpec.WORD_CUSTOM_ID];
        boolean customMapContains = this.customBlockStateIdMapping != null
                && this.customBlockStateIdMapping.containsKey(state);
        int expectedCustomId = this.customBlockStateId(state);
        int colourTint = words[ForgeOriginalVoxyModelStoreLayoutSpec.WORD_COLOUR_TINT];
        String biomeColourRead = "not-biome-lut";
        int biomeColourIndex = -1;
        int biomeColour = 0;
        if ((flagsA & 2) != 0 && ingestedBiomeId >= 0) {
            long colourIndex = Integer.toUnsignedLong(colourTint) + ingestedBiomeId;
            if (colourIndex <= Integer.MAX_VALUE) {
                biomeColourIndex = (int) colourIndex;
                byte[] colourBytes = new byte[Integer.BYTES];
                biomeColourRead = this.store.readOriginalVoxyModelColourRange(
                        biomeColourIndex,
                        Integer.BYTES,
                        colourBytes);
                biomeColour = Byte.toUnsignedInt(colourBytes[0])
                        | (Byte.toUnsignedInt(colourBytes[1]) << 8)
                        | (Byte.toUnsignedInt(colourBytes[2]) << 16)
                        | (Byte.toUnsignedInt(colourBytes[3]) << 24);
            } else {
                biomeColourRead = "biome-colour-index-overflow";
            }
        }
        VoxyForge.LOGGER.info(
                "Original Voxy block-model target audit: block=[{},{},{}] clientChunkLoaded={} clientState={} worldVoxelPresent={} worldMapping=0x{} ingestedState={} blockStateId={} biomeId={} modelId={} modelIdZeroForNonAir={} summaryModelId={} dedupeSignature={} primarySprite={} voxyMetadata={} modelRead={} textureRead={} faceData=[{}] flagsA=0x{} colourTint=0x{} biomeColourIndex={} biomeColour=0x{} biomeColourRead={} customMapContains={} expectedCustomId={} actualCustomId={} customIdMatch={} atlasChecksum={} alpha=[{}]",
                blockX,
                blockY,
                blockZ,
                clientChunkLoaded,
                clientState,
                worldVoxelPresent,
                Long.toHexString(worldMapping),
                state,
                blockStateId,
                ingestedBiomeId,
                modelId,
                modelIdZeroForNonAir,
                summary == null ? -1 : summary.originalModelId(),
                summary == null ? "missing" : summary.dedupeSignature(),
                summary == null ? "missing" : summary.primarySprite(),
                summary == null ? 0L : summary.voxyMetadata(),
                modelReadError,
                textureReadError,
                faceWords,
                Integer.toHexString(flagsA),
                Integer.toHexString(colourTint),
                biomeColourIndex,
                Integer.toHexString(biomeColour),
                biomeColourRead,
                customMapContains,
                expectedCustomId,
                actualCustomId,
                actualCustomId == expectedCustomId,
                ForgeModelAtlasPixelFormat.checksum(texture),
                alphaSummary);
    }

    private int findExistingBlockStateId(BlockState state) {
        for (Mapper.StateEntry entry : this.mapper.getStateEntries()) {
            if (entry.state.equals(state)) {
                return entry.id;
            }
        }
        return -1;
    }

    long getModelMetadataFromClientId(int clientId) {
        if (clientId < 0 || clientId >= this.metadataCache.length) {
            return 0L;
        }
        return this.metadataCache[clientId];
    }

    int getFluidClientStateId(int clientId) {
        if (clientId < 0 || clientId >= this.fluidStateLUT.length || this.fluidStateLUT[clientId] == -1) {
            throw new ForgeOriginalVoxyIdNotYetComputedException(clientId, false);
        }
        return this.fluidStateLUT[clientId];
    }

    void shutdown() {
        this.bakeQueue.clear();
        this.biomeQueue.clear();
        ResultUploader upload = this.uploadResults.poll();
        while (upload != null) {
            upload.free();
            upload = this.uploadResults.poll();
        }
        this.pendingUploadAudits.clear();
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
            this.fail(this.store.lastFailureReason());
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
        ForgeOriginalVoxyColourDepthTextureData[] textures =
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

        TintPlan tint = this.createTintPlan(minecraft, bake.state(), softwareBake);
        ModelEntry entry = new ModelEntry(softwareBake.textures(), fluidModelId, tint.dedupeColour());
        Integer duplicate = this.modelTexture2id.get(entry);
        if (duplicate != null) {
            this.idMappings[bake.blockId()] = duplicate;
            this.lastDuplicateBlockStateId = bake.blockId();
            this.lastDuplicateModelId = duplicate;
            this.dedupeHitCount++;
            this.completedModelCount++;
            this.uploadedByBlockState.put(bake.blockId(), new ForgeOriginalUploadedModelSummary(
                    bake.blockId(),
                    bake.state().toString(),
                    duplicate,
                    entry.signature(),
                    "dedupe-alias",
                    MODEL_SIZE,
                    this.getModelMetadataFromClientId(duplicate),
                    this.fluidStateLUT[duplicate]
            ));
            this.removeInFlight(bake.blockId());
            this.lastFailureReason = "none";
            return true;
        }

        int modelId = this.nextModelId++;
        if (!ForgeModelAtlasLayout.isValidModelId(modelId) || modelId >= this.metadataCache.length) {
            this.removeInFlight(bake.blockId());
            this.failedBakeCount++;
            this.fail("formal-model-id-capacity-exhausted");
            return true;
        }

        tint = this.finalizeTintForNewModel(minecraft, modelId, bake.state(), tint);
        RecordBuild build = this.buildRecord(bake.state(), softwareBake, tint, fluidModelId, modelId);
        this.auditShaderpackModelInput(bake.blockId(), bake.state(), modelId, softwareBake, fluidModelId, build);
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
        this.uploadedByBlockState.put(bake.blockId(), new ForgeOriginalUploadedModelSummary(
                bake.blockId(),
                bake.state().toString(),
                modelId,
                entry.signature(),
                build.primarySprite(),
                MODEL_SIZE,
                build.voxyMetadata(),
                fluidModelId
        ));
        this.uploadResults.add(new ModelBakeUpload(bake.blockId(), modelId, build));
        this.removeInFlight(bake.blockId());
        this.lastFailureReason = "none";
        return true;
    }

    private void auditShaderpackModelInput(
            int blockStateId,
            BlockState state,
            int modelId,
            ForgeSoftwareModelTextureBakery.BakeResult softwareBake,
            int fluidModelId,
            RecordBuild build) {
        if (!AUDIT_SHADERPACK || this.shaderpackModelAuditRuns >= MAX_SHADERPACK_MODEL_AUDITS) {
            return;
        }
        this.shaderpackModelAuditRuns++;
        VoxyForge.LOGGER.info(
                "Original Voxy model shaderpack audit: run={} blockStateId={} modelId={} customId={} layer={} isFluid={} containsFluid={} fluidModelId={} state={}",
                this.shaderpackModelAuditRuns,
                blockStateId,
                modelId,
                build.words()[ForgeOriginalVoxyModelStoreLayoutSpec.WORD_CUSTOM_ID],
                softwareBake.layer(),
                state.getBlock() instanceof LiquidBlock,
                !(state.getBlock() instanceof LiquidBlock) && !state.getFluidState().isEmpty(),
                fluidModelId,
                state);
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
                ? ForgeOriginalVoxyTextureUtils.WRITE_CHECK_STENCIL
                : ForgeOriginalVoxyTextureUtils.WRITE_CHECK_ALPHA;
        int[] words = new int[ForgeOriginalVoxyModelStoreLayoutSpec.MODEL_RECORD_WORDS];
        Arrays.fill(words, 0);
        Arrays.fill(words, 0, ForgeOriginalVoxyModelStoreLayoutSpec.FACE_DATA_WORDS, -1);
        FaceUpload[] faces = new FaceUpload[ForgeModelAtlasLayout.FACE_COUNT];
        int writtenFaces = 0;
        String primarySprite = "none";

        for (Direction direction : DIRECTIONS) {
            int faceIndex = direction.get3DDataValue();
            ForgeOriginalVoxyColourDepthTextureData texture = softwareBake.textures()[faceIndex];
            int writtenPixels = texture == null ? 0 : ForgeOriginalVoxyTextureUtils.getWrittenPixelCount(texture, checkMode);
            if (writtenPixels == 0) {
                faces[faceIndex] = FaceUpload.empty(faceIndex, direction.getName());
                continue;
            }
            byte[] pixels = ForgeSoftwareModelTextureBakery.rgbaBytes(texture);
            float depth = computeSoftwareDepth(texture, layer);
            int[] bounds = ForgeOriginalVoxyTextureUtils.computeBounds(texture, checkMode);
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
        words[ForgeOriginalVoxyModelStoreLayoutSpec.WORD_FLAGS_A] = flags;
        words[ForgeOriginalVoxyModelStoreLayoutSpec.WORD_COLOUR_TINT] = tint.recordColourTint();
        words[ForgeOriginalVoxyModelStoreLayoutSpec.WORD_CUSTOM_ID] = this.customBlockStateId(state);
        MemoryBuffer mipChain = ForgeOriginalVoxyMipGen.putTexturesBuffer((softwareBake.flags() & 2) != 0, softwareBake.textures());
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
            this.fail("biome-id-reused-" + id);
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
        this.biomeUploadResultCount++;
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

    private void fail(String reason) {
        this.lastFailureReason = reason == null || reason.isBlank() ? "failed" : reason.replace(' ', '-');
    }

    private void enqueueUploadAudit(PendingUploadAudit audit) {
        if (audit != null) {
            this.pendingUploadAudits.add(audit);
        }
    }

    private void auditPendingUploads(ForgeOriginalVoxyModelStore store) {
        PendingUploadAudit audit = this.pendingUploadAudits.poll();
        while (audit != null) {
            this.auditUpload(store, audit);
            audit = this.pendingUploadAudits.poll();
        }
    }

    private void auditUpload(ForgeOriginalVoxyModelStore store, PendingUploadAudit audit) {
        this.modelStoreReadbackAuditRuns++;
        this.lastAuditedModelId = audit.modelId();
        byte[] modelReadback = new byte[MODEL_SIZE];
        String error = store.readOriginalVoxyModelRecord(audit.modelId(), modelReadback);
        if (!"none".equals(error)) {
            this.recordUploadAuditFailure(error, false, false, false);
            return;
        }
        boolean modelOk = Arrays.equals(audit.modelRecord(), modelReadback);

        boolean colourOk = true;
        if (audit.modelColourBytes() != null) {
            byte[] colourReadback = new byte[audit.modelColourBytes().length];
            error = store.readOriginalVoxyModelColourRange(audit.modelColourBaseIndex(), colourReadback.length, colourReadback);
            if (!"none".equals(error)) {
                this.recordUploadAuditFailure(error, modelOk, false, false);
                return;
            }
            colourOk = Arrays.equals(audit.modelColourBytes(), colourReadback);
        }

        byte[] textureReadback = new byte[(int) ForgeOriginalVoxyMipGen.UPLOADED_MIP_CHAIN_BYTES];
        error = store.readOriginalVoxyModelTextureMipChain(audit.modelId(), textureReadback);
        if (!"none".equals(error)) {
            this.recordUploadAuditFailure(error, modelOk, colourOk, false);
            return;
        }
        boolean atlasOk = Arrays.equals(audit.mipChainBytes(), textureReadback);
        this.modelDataReadbackOk = modelOk;
        this.modelColourReadbackOk = colourOk;
        this.atlasMipChainReadbackOk = atlasOk;
        this.originalModelStoreReadbackAuditReady = modelOk && colourOk && atlasOk;
        if (this.originalModelStoreReadbackAuditReady) {
            this.lastModelStoreReadbackAuditFailureReason = "none";
        } else {
            this.modelStoreReadbackAuditFailures++;
            this.lastModelStoreReadbackAuditFailureReason =
                    "modelData=" + modelOk + ",modelColour=" + colourOk + ",atlasMipChain=" + atlasOk;
        }
    }

    private void recordUploadAuditFailure(String reason, boolean modelOk, boolean colourOk, boolean atlasOk) {
        this.modelStoreReadbackAuditFailures++;
        this.modelDataReadbackOk = modelOk;
        this.modelColourReadbackOk = colourOk;
        this.atlasMipChainReadbackOk = atlasOk;
        this.originalModelStoreReadbackAuditReady = false;
        this.lastModelStoreReadbackAuditFailureReason =
                reason == null || reason.isBlank() ? "original-model-store-readback-audit-failed" : reason.replace(' ', '-');
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
                ? ForgeOriginalVoxyTextureUtils.WRITE_CHECK_STENCIL
                : ForgeOriginalVoxyTextureUtils.WRITE_CHECK_ALPHA;
        for (ForgeOriginalVoxyColourDepthTextureData face : softwareBake.textures()) {
            if (face != null) {
                int tintState = ForgeOriginalVoxyTextureUtils.computeFaceTint(face, checkMode);
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
            ForgeOriginalVoxyColourDepthTextureData[] textures
    ) {
        ForgeOriginalVoxyModelLayer layer = ForgeOriginalVoxyModelLayer.OTHER;
        if ((flags & 4) != 0) {
            boolean anyTranslucent = false;
            for (ForgeOriginalVoxyColourDepthTextureData face : textures) {
                anyTranslucent |= face != null && ForgeOriginalVoxyTextureUtils.hasTranslucentPixel(face);
                if (anyTranslucent) {
                    break;
                }
            }
            if (anyTranslucent) {
                layer = ForgeOriginalVoxyModelLayer.TRANSLUCENT;
            } else {
                boolean solid = true;
                for (ForgeOriginalVoxyColourDepthTextureData face : textures) {
                    solid &= face == null || ForgeOriginalVoxyTextureUtils.isSolidWhereDrawn(face);
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
            ForgeOriginalVoxyColourDepthTextureData texture,
            ForgeOriginalVoxyModelLayer layer,
            float depth,
            int[] bounds,
            int written,
            boolean hasTint
    ) {
        int checkMode = layer == ForgeOriginalVoxyModelLayer.SOLID
                ? ForgeOriginalVoxyTextureUtils.WRITE_CHECK_STENCIL
                : ForgeOriginalVoxyTextureUtils.WRITE_CHECK_ALPHA;
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
            int tintState = ForgeOriginalVoxyTextureUtils.computeFaceTint(texture, checkMode);
            if (tintState == 2) {
                faceData |= 1 << 24;
            } else if (tintState == 3) {
                faceData |= 2 << 24;
            }
        }
        return faceData;
    }

    private static float computeSoftwareDepth(ForgeOriginalVoxyColourDepthTextureData texture, ForgeOriginalVoxyModelLayer layer) {
        int checkMode = layer == ForgeOriginalVoxyModelLayer.SOLID
                ? ForgeOriginalVoxyTextureUtils.WRITE_CHECK_STENCIL
                : ForgeOriginalVoxyTextureUtils.WRITE_CHECK_ALPHA;
        return ForgeOriginalVoxyTextureUtils.computeDepth(
                texture,
                layer != ForgeOriginalVoxyModelLayer.SOLID ? ForgeOriginalVoxyTextureUtils.DEPTH_MODE_MIN : ForgeOriginalVoxyTextureUtils.DEPTH_MODE_AVG,
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
        String upload(ForgeOriginalVoxyModelStore store, ForgeOriginalVoxyModelFactory factory);

        default void free() {
        }
    }

    private record PendingUploadAudit(
            int modelId,
            byte[] modelRecord,
            int modelColourBaseIndex,
            byte[] modelColourBytes,
            byte[] mipChainBytes
    ) {
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
        public String upload(ForgeOriginalVoxyModelStore store, ForgeOriginalVoxyModelFactory factory) {
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
            //Publish completion counters only after every stage succeeded. A retry after a later
            //stage failure rewrites the same slot and must not be counted twice.
            factory.uploadedModelRecordCount++;
            if (this.biomeUpload != null) {
                factory.uploadedModelColourCount += (int) (this.biomeUpload.size / Integer.BYTES);
            }
            factory.uploadedAtlasFaceCount += ForgeModelAtlasLayout.FACE_COUNT;
            factory.enqueueUploadAudit(new PendingUploadAudit(
                    this.modelId,
                    bytes(this.model, MODEL_SIZE),
                    this.build.immediateBiomeColourBaseIndex(),
                    this.biomeUpload == null ? null : bytes(this.biomeUpload, (int) this.biomeUpload.size),
                    bytes(this.texture, (int) ForgeOriginalVoxyMipGen.UPLOADED_MIP_CHAIN_BYTES)
            ));
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

    private static byte[] bytes(MemoryBuffer buffer, int length) {
        byte[] out = new byte[length];
        for (int i = 0; i < out.length; i++) {
            out[i] = MemoryUtil.memGetByte(buffer.address + i);
        }
        return out;
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
        public String upload(ForgeOriginalVoxyModelStore store, ForgeOriginalVoxyModelFactory factory) {
            String error = store.uploadOriginalVoxyBiomeUpload(this.biomeColourBuffer, this.modelBiomeIndexPairs);
            if (!"none".equals(error)) {
                return error;
            }
            factory.uploadedModelColourCount += this.colourCount;
            return "none";
        }

        @Override
        public void free() {
            this.biomeColourBuffer.free();
            this.modelBiomeIndexPairs.free();
        }
    }

    private static final class ModelEntry {
        private final ForgeOriginalVoxyColourDepthTextureData[] faces;
        private final int fluidModelId;
        private final int tintingColour;
        private final int hash;

        private ModelEntry(ForgeOriginalVoxyColourDepthTextureData[] faces, int fluidModelId, int tintingColour) {
            this.faces = faces.clone();
            this.fluidModelId = fluidModelId;
            this.tintingColour = tintingColour;
            int value = 31 * fluidModelId + tintingColour;
            for (ForgeOriginalVoxyColourDepthTextureData face : this.faces) {
                value = 31 * value + faceHash(face);
            }
            this.hash = value;
        }

        private String signature() {
            StringBuilder builder = new StringBuilder();
            builder.append(this.fluidModelId).append('|').append(this.tintingColour);
            for (ForgeOriginalVoxyColourDepthTextureData face : this.faces) {
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

        private static boolean faceEquals(ForgeOriginalVoxyColourDepthTextureData left, ForgeOriginalVoxyColourDepthTextureData right) {
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

        private static int faceHash(ForgeOriginalVoxyColourDepthTextureData face) {
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
