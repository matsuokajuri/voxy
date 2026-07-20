package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelFactoryTintSourcePlanTest {
    @Test
    void biomeDependencyProbeVisitsEveryTintSourceInOrder() {
        List<String> calls = new ArrayList<>();
        ModelFactory.TintSourcePlan plan = new ModelFactory.TintSourcePlan(List.of(
                (biome, marker) -> {
                    calls.add("constant");
                    return 0x102030;
                },
                (biome, marker) -> {
                    calls.add("biome");
                    marker.run();
                    return 0;
                }));

        assertTrue(ModelFactory.isBiomeDependentColour(plan, null));
        assertEquals(List.of("constant", "biome"), calls);

        calls.clear();
        assertEquals(0x102030, ModelFactory.captureColourConstant(plan, null));
        assertEquals(List.of("constant"), calls);
    }

    @Test
    void colourCaptureReturnsFirstRealSourceAndStops() {
        List<String> calls = new ArrayList<>();
        ModelFactory.TintSourcePlan plan = new ModelFactory.TintSourcePlan(List.of(
                (biome, marker) -> {
                    calls.add("missing");
                    return -1;
                },
                (biome, marker) -> {
                    calls.add("selected");
                    return 0x456789;
                },
                (biome, marker) -> {
                    calls.add("late");
                    return 0xABCDEF;
                }));

        assertEquals(0x456789, ModelFactory.captureColourConstant(plan, null));
        assertEquals(List.of("missing", "selected"), calls);
    }

    @Test
    void containedFluidBiomeDependencyTransitionsTheSharedTintPlan() {
        long biomeColouredFluidMetadata = 1L << (8 * ForgeModelAtlasLayout.FACE_COUNT);
        ModelFactory.TintSourcePlan noSources = new ModelFactory.TintSourcePlan(List.of());
        ModelFactory.TintPlan noTint = new ModelFactory.TintPlan(
                false, false, noSources, -1, -1, null, -1);

        ModelFactory.TintPlan mergedNoTint = ModelFactory.mergeContainedFluidBiomeColourDependency(
                noTint, true, biomeColouredFluidMetadata);
        assertTrue(mergedNoTint.biomeDependent());
        assertFalse(mergedNoTint.hasTint());
        assertEquals(-1, mergedNoTint.recordColourTint());
        assertFalse(ModelFactory.requiresBiomeColourLut(mergedNoTint));
        assertEquals(mergedNoTint, ModelFactory.applyBiomeColourLut(
                mergedNoTint, 9, new int[] {0xFF123456}));

        int constantColour = 0xFF456789;
        ModelFactory.TintPlan constantTint = new ModelFactory.TintPlan(
                true,
                false,
                new ModelFactory.TintSourcePlan(List.of((biome, marker) -> constantColour)),
                constantColour,
                constantColour,
                null,
                -1);
        ModelFactory.TintPlan mergedConstantTint = ModelFactory.mergeContainedFluidBiomeColourDependency(
                constantTint, true, biomeColouredFluidMetadata);
        assertTrue(mergedConstantTint.biomeDependent());
        assertTrue(ModelFactory.requiresBiomeColourLut(mergedConstantTint));
        assertEquals(constantColour, mergedConstantTint.dedupeColour());

        int[] colours = {constantColour, constantColour};
        ModelFactory.TintPlan finalized = ModelFactory.applyBiomeColourLut(
                mergedConstantTint, 17, colours);
        assertEquals(17, finalized.recordColourTint());
        assertEquals(17, finalized.immediateBiomeColourBaseIndex());
        assertEquals(constantColour, finalized.dedupeColour());
        assertArrayEquals(colours, finalized.immediateBiomeColours());

        assertEquals(constantTint, ModelFactory.mergeContainedFluidBiomeColourDependency(
                constantTint, true, 0L));
        assertEquals(noTint, ModelFactory.mergeContainedFluidBiomeColourDependency(
                noTint, false, biomeColouredFluidMetadata));
    }

    @Test
    void processModelResultMergesBeforeSemanticDedupeAndFinalizesOnlyNewModels() throws IOException {
        String source = Files.readString(Path.of("src/main/java/me/cortex/voxy/forge/ModelFactory.java"));
        int methodStart = source.indexOf("private boolean processModelResult(Minecraft minecraft)");
        int methodEnd = source.indexOf("private int resolveClientFluidModelId", methodStart);
        String body = source.substring(methodStart, methodEnd);

        int createTint = body.indexOf("this.createTintPlan(");
        int merge = body.indexOf("tint = mergeContainedFluidBiomeColourDependency(");
        int prepare = body.indexOf("this.prepareRecord(");
        int semanticKey = body.indexOf("ModelSemanticKey.from(");
        int dedupeKey = body.indexOf("new ModelEntry(");
        int lookup = body.indexOf("this.modelTexture2id.get(entry)");
        int duplicateBranch = body.indexOf("if (duplicate != null)");
        int duplicateReturn = body.indexOf("return true;", duplicateBranch);
        int finalize = body.indexOf("tint = this.finalizeTintForNewModel(");
        int buildRecord = body.indexOf("this.buildRecord(");
        assertTrue(createTint >= 0 && createTint < merge);
        assertTrue(merge < prepare && prepare < semanticKey && semanticKey < dedupeKey);
        assertTrue(dedupeKey < lookup && lookup < duplicateBranch);
        assertTrue(duplicateBranch < duplicateReturn && duplicateReturn < finalize);
        assertTrue(finalize < buildRecord);

        int prepareStart = source.indexOf("private PreparedRecord prepareRecord(");
        int prepareEnd = source.indexOf("private RecordBuild buildRecord(", prepareStart);
        String prepareBody = source.substring(prepareStart, prepareEnd);
        assertTrue(prepareBody.contains("tint.biomeDependent(),"));
        assertTrue(prepareBody.contains("flags |= tint.biomeDependent() ? 2 : 0;"));
        assertTrue(prepareBody.contains(
                "words[ForgeModelStoreLayoutSpec.WORD_COLOUR_TINT] = tint.dedupeColour();"));
        assertTrue(prepareBody.contains(
                "words[ForgeModelStoreLayoutSpec.WORD_CUSTOM_ID] = customId;"));
        assertFalse(prepareBody.contains("finalizeTintForNewModel"));

        int buildStart = source.indexOf("private RecordBuild buildRecord(", prepareEnd);
        int buildEnd = source.indexOf("private void recordModelCapacityHighWater", buildStart);
        String build = source.substring(buildStart, buildEnd);
        assertTrue(build.contains(
                "words[ForgeModelStoreLayoutSpec.WORD_COLOUR_TINT] = tint.recordColourTint();"));
        assertFalse(build.contains("mergeContainedFluidBiomeColourDependency("));

        int finalizeStart = source.indexOf("private TintPlan finalizeTintForNewModel(");
        int finalizeEnd = source.indexOf("private ResultUploader addBiome0", finalizeStart);
        String finalizeBody = source.substring(finalizeStart, finalizeEnd);
        assertTrue(finalizeBody.contains("modelsRequiringBiomeColours.add"));
        assertTrue(finalizeBody.contains(
                "return applyBiomeColourLut(tint, biomeIndex, immediateColours);"));
    }
}
