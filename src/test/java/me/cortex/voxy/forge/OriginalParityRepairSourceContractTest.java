package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginalParityRepairSourceContractTest {
    @Test
    void verifierCancellationUsesItsWorldSessionOwner() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/DebugUtils.java");
        String instance = source("src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java");
        assertTrue(source.contains("engine.isOwningSessionRunning()"));
        assertTrue(source.contains("world.isOwningSessionRunning()"));
        assertFalse(source.contains("ForgeVoxyInstance.INSTANCE.isRunning()"));
        assertTrue(instance.contains("SessionRuntime implements WorldEngine.LifecycleOwner"));
        assertTrue(instance.contains("return this.running;"));
    }

    @Test
    void fluidRendererInitializesProtectedSpritesBeforeAtlasPreparation() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java");
        int prepare = source.indexOf("void prepareOnRenderThread(Minecraft minecraft)");
        int sprites = source.indexOf("this.fluidRenderer.setupSpritesForBake();", prepare);
        int texture = source.indexOf("this.setupTexture(minecraft);", prepare);
        assertTrue(prepare >= 0 && sprites > prepare && texture > sprites);
        assertTrue(source.contains("extends LiquidBlockRenderer"));
        assertTrue(source.contains("super.setupSprites();"));
    }

    @Test
    void tintPlanEnumeratesDistinctQuadIndicesAndUsesForgeFluidTint() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/ModelFactory.java");
        assertTrue(source.contains("LinkedHashSet<Integer> tintIndices"));
        assertTrue(source.contains("tintIndices.add(quad.getTintIndex())"));
        assertTrue(source.contains("IClientFluidTypeExtensions.of(fluidState)"));
    }

    @Test
    void stairBaseStateUsesMappedAccessTransformerAndDirectRead() throws IOException {
        String accessTransformer = source("src/main/resources/META-INF/accesstransformer.cfg");
        String modelFactory = source("src/main/java/me/cortex/voxy/forge/ModelFactory.java");
        assertTrue(accessTransformer.contains(
                "public net.minecraft.world.level.block.StairBlock f_56859_ # baseState"));
        assertTrue(modelFactory.contains("stair.baseState.getBlock().withPropertiesOf(state)"));
        assertFalse(modelFactory.contains("getDeclaredField(\"baseState\")"));
    }

    @Test
    void modelQueriesRetainsTheCompleteOriginalMetadataAccessorSurface() {
        assertModelQueryFlags(4L << (8 * 6), true, false, false, false);
        assertModelQueryFlags(2L << (8 * 6), false, true, false, false);
        assertModelQueryFlags(16L << (8 * 6), false, false, true, false);
        assertModelQueryFlags(1L << (8 * 6), false, false, false, true);
        assertModelQueryFlags(0L, false, false, false, false);
    }

    @Test
    void originalLoggerRequestAndBiomeFailureContractsArePreserved() throws IOException {
        String lmdb = source("src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java");
        String mdic = source("src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java");
        String iris = source("src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java");
        String node = source("src/main/java/me/cortex/voxy/forge/NodeManager.java");
        String printf = source("src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java");
        String renderData = source("src/main/java/me/cortex/voxy/forge/RenderDataFactory.java");
        String model = source("src/main/java/me/cortex/voxy/forge/ModelFactory.java");

        assertTrue(lmdb.contains("Logger.info(\"Growing DBI env size to: \" + size + \" bytes\");"));
        assertTrue(mdic.contains("Logger.error(\"Frame ID negative, this will cause things to break, wrapping around\");"));
        assertTrue(mdic.contains("Logger.error(\"Failed to compile shader patch, using normal pipeline to prevent errors\", e);"));
        assertTrue(iris.contains("Logger.error(error);"));
        assertTrue(printf.contains("Logger.info(line);"));
        assertTrue(renderData.contains(
                "Logger.warn(\"Large quad count for section \" + WorldEngine.pprintPos(section.key) + \" is \" + this.quadCount);"));
        assertFalse(lmdb.contains("VoxyForge.LOGGER"));
        assertFalse(iris.contains("VoxyForge.LOGGER"));
        assertFalse(printf.contains("VoxyForge.LOGGER"));
        assertFalse(renderData.contains("VoxyForge.LOGGER"));
        assertEquals(1, occurrences(mdic, "VoxyForge.LOGGER"));

        for (String originalCall : new String[]{
                "Logger.error(\"Tried inserting top level pos \" + WorldEngine.pprintPos(pos) + \" but it was in active map, discarding!\");",
                "Logger.warn(\"Recieved geometry update but not watching it, discarding\");",
                "Logger.warn(\"Got child change for pos \" + WorldEngine.pprintPos(pos) + \" but it was not in active map, ignoring!\");",
                "Logger.error(\"Tried processing request for pos: \" + WorldEngine.pprintPos(pos) + \" but its type was a request, ignoring!\");",
                "Logger.error(\"Requests cannot exist for bottom level nodes. at: \" + WorldEngine.pprintPos(pos) + \". Ignoring request\");",
                "Logger.warn(\"Got request for leaf that doesnt have geometry, this should not be possible at pos \" + WorldEngine.pprintPos(pos));",
                "Logger.warn(\"Node: \" + nodeId + \" at pos: \" + WorldEngine.pprintPos(pos) + \" got update request, but geometry was already being watched\");",
                "Logger.warn(\"Tried processing a node that already has a request in flight: \" + nodeId + \" pos: \" + WorldEngine.pprintPos(pos) + \" ignoring\");",
                "Logger.warn(\"Tried removing geometry from top level node which is not allowed, disregarding request\");",
                "Logger.warn(\"Inner node child existence is changing to 0, this is mild bad\");",
                "Logger.error(\"Transforming inner node to leaf node while it has null geometry\");",
                "Logger.error(\"Setting geometry to EMPTY while request is inflight\");",
                "Logger.warn(\"Request result with child existence of 0, for child pos \" + WorldEngine.pprintPos(childPos));",
                "Logger.warn(\"Not creating a leaf request with existence mask of 0 at pos\", WorldEngine.pprintPos(pos));"
        }) {
            assertTrue(node.contains(originalCall), originalCall);
        }
        assertFalse(node.contains("VoxyForge.LOGGER"));
        int transform = node.indexOf("private void transformInnerToLeaf");
        int request = node.indexOf("this.processRequest(pos);", transform);
        int watcherPostcondition = node.indexOf(
                "(this.watcher.get(pos) & UPDATE_TYPE_BLOCK_BIT) == 0",
                request);
        int inflightPostcondition = node.indexOf(
                "!this.nodeData.isNodeGeometryInFlight(nodeId)",
                watcherPostcondition);
        int emptyLog = node.indexOf(
                "Logger.error(\"Setting geometry to EMPTY while request is inflight\");",
                inflightPostcondition);
        int emptyAssignment = node.indexOf(
                "this.nodeData.setNodeGeometry(nodeId, EMPTY_GEOMETRY_ID);",
                emptyLog);
        assertTrue(request >= 0 && watcherPostcondition > request);
        assertTrue(inflightPostcondition > watcherPostcondition);
        assertTrue(emptyLog > inflightPostcondition && emptyAssignment > emptyLog);

        assertEquals(4, occurrences(model, "VoxyForge.LOGGER"));
        assertTrue(model.contains("Original Voxy model capacity high-water:"));
        assertTrue(model.contains("Original Voxy model summary:"));
        assertTrue(model.contains("Forxy Round 8 model GPU readback verified"));
        int addBiome = model.indexOf("private ResultUploader addBiome0");
        int nullBiome = model.indexOf("throw new IllegalStateException(\"Null biome\");", addBiome);
        int storeBiome = model.indexOf("Biome oldBiome = this.biomes.set(id, biome);", addBiome);
        int conflict = model.indexOf(
                "throw new IllegalStateException(\"Biome was put in an id that was not null\");",
                storeBiome);
        int duplicateLog = model.indexOf("Logger.error(\"Biome added was a duplicate: \" + id);", conflict);
        assertTrue(nullBiome > addBiome && storeBiome > nullBiome);
        assertTrue(conflict > storeBiome && duplicateLog > conflict);

        int resolveBiome = model.indexOf("private Biome resolveBiome");
        int normalizeBlockState = model.indexOf("private static BlockState normalizeBlockState", resolveBiome);
        String resolve = model.substring(resolveBiome, normalizeBlockState);
        assertTrue(resolve.contains("ResourceLocation location = new ResourceLocation(biomeId);"));
        assertTrue(resolve.contains("Logger.warn(\"Could not find biome: \" + biomeId + \" using default\");"));
        assertTrue(resolve.contains("return this.DEFAULT_BIOME;"));
        assertFalse(resolve.contains("ResourceLocation.tryParse"));
        assertFalse(resolve.contains("iterator().next()"));
        assertTrue(model.contains("private final Biome DEFAULT_BIOME = Minecraft.getInstance().level.registryAccess()"));
        assertTrue(model.contains("isBiomeDependentColour(tintSources, this.DEFAULT_BIOME)"));
        assertTrue(model.contains("captureColourConstant(tintSources, this.DEFAULT_BIOME)"));
    }

    private static void assertModelQueryFlags(
            long metadata,
            boolean doubleSided,
            boolean translucent,
            boolean fluid,
            boolean biomeColoured) {
        assertEquals(doubleSided, ModelQueries.isDoubleSided(metadata));
        assertEquals(translucent, ModelQueries.isTranslucent(metadata));
        assertEquals(fluid, ModelQueries.isFluid(metadata));
        assertEquals(biomeColoured, ModelQueries.isBiomeColoured(metadata));
        assertEquals(doubleSided ? 1L : 0L, ModelQueries._isDoubleSided(metadata));
        assertEquals(translucent ? 1L : 0L, ModelQueries._isTranslucent(metadata));
        assertEquals(fluid ? 1L : 0L, ModelQueries._isFluid(metadata));
        assertEquals(biomeColoured ? 1L : 0L, ModelQueries._isBiomeColoured(metadata));
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static int occurrences(String source, String target) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(target, index)) >= 0; index += target.length()) {
            count++;
        }
        return count;
    }
}
