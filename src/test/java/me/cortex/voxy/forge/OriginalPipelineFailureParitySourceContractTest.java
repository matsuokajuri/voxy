package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OriginalPipelineFailureParitySourceContractTest {
    @Test
    void seenIdIsCommittedAfterEveryNonExceptionalFactoryResult() throws IOException {
        String request = between(
                source("src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java"),
                "void requestBlockBake(int blockId)",
                "void addBiome(Mapper.BiomeEntry biomeEntry)");

        int contains = request.indexOf("this.seenIds.contains(blockId)");
        int enqueue = request.indexOf("enqueued = this.factory.addEntry(blockId)");
        int commit = request.indexOf("this.seenIds.add(blockId)");
        int unpark = request.indexOf("LockSupport.unpark(this.processingThread)");
        assertTrue(contains >= 0 && enqueue > contains && commit > enqueue && unpark > commit);
        int failureCatch = request.indexOf("catch (RuntimeException e)", enqueue);
        int failureReturn = request.indexOf("return;", failureCatch);
        assertTrue(failureCatch > enqueue && failureReturn > failureCatch && failureReturn < commit);
        assertTrue(request.indexOf("if (enqueued)", commit) > commit);
        assertEquals(1, occurrences(request, "this.seenIds.add(blockId)"));
        assertFalse(request.contains("if (!this.seenIds.add(blockId))"));
    }

    @Test
    void mapperBakeCapacityAndUploadFailuresCannotBecomePermanentMissingModels() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/ModelFactory.java");
        String addEntry = between(source, "boolean addEntry(int blockId)", "void addBiome(Mapper.BiomeEntry biomeEntry)");
        String processUploads = between(source, "void processUploads()", "boolean hasPendingUploads()");
        String processModel = between(
                source,
                "private boolean processModelResult(Minecraft minecraft)",
                "private int resolveClientFluidModelId(BlockState state)");

        assertTrue(addEntry.contains("throw new IllegalArgumentException"));
        assertTrue(addEntry.contains("this.mapper.getBlockStateFromBlockId(blockId)"));
        assertFalse(addEntry.contains("catch (RuntimeException"));

        assertTrue(processModel.contains("Original Voxy software model bake failed for block-state"));
        assertTrue(processModel.contains("Original Voxy model capacity exhausted at model id"));
        String softwareFailure = between(
                processModel,
                "if (!\"none\".equals(softwareBake.failureReason()))",
                "int fluidModelId");
        String capacityFailure = between(
                processModel,
                "if (!ForgeModelAtlasLayout.isValidModelId(modelId)",
                "this.nextModelId++");
        assertTrue(softwareFailure.contains("throw new IllegalStateException"));
        assertFalse(softwareFailure.contains("removeInFlight"));
        assertTrue(capacityFailure.contains("throw new IllegalStateException"));
        assertFalse(capacityFailure.contains("removeInFlight"));

        int uploadFailure = processUploads.indexOf("if (!\"none\".equals(error))");
        int requeue = processUploads.indexOf("this.uploadResults.addFirst(upload)", uploadFailure);
        int fatal = processUploads.indexOf("throw new IllegalStateException(\"Original Voxy model upload failed:", requeue);
        assertTrue(uploadFailure >= 0 && requeue > uploadFailure && fatal > requeue);
        String returnedErrorBranch = processUploads.substring(uploadFailure, processUploads.indexOf("upload.free();", fatal));
        assertFalse(returnedErrorBranch.contains("upload.free();"));
        String runtimeErrorBranch = between(
                processUploads,
                "catch (RuntimeException e)",
                "if (!\"none\".equals(error))");
        assertTrue(runtimeErrorBranch.contains("this.uploadResults.addFirst(upload)"));
        assertTrue(runtimeErrorBranch.contains("throw e;"));
        assertFalse(runtimeErrorBranch.contains("upload.free();"));
        assertFalse(source.contains("MAX_UPLOAD_ATTEMPTS"));
        assertFalse(source.contains("consecutiveUploadFailureCount"));
        assertFalse(source.contains("models referencing it will render empty until rebuild"));
    }

    @Test
    void duplicateCompletedModelMappingRemainsAnOriginalFatalInvariant() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/ModelFactory.java");
        String processModel = between(
                source,
                "private boolean processModelResult(Minecraft minecraft)",
                "private int resolveClientFluidModelId(BlockState state)");
        int softwareBake = processModel.indexOf("this.softwareBakery.renderToOutput(");
        int softwareFailure = processModel.indexOf("if (!\"none\".equals(softwareBake.failureReason()))");
        int duplicateCheck = processModel.indexOf("if (this.idMappings[bake.blockId()] != -1)");
        int fluidResolution = processModel.indexOf("int fluidModelId");
        assertTrue(softwareBake >= 0
                && softwareFailure > softwareBake
                && duplicateCheck > softwareFailure
                && fluidResolution > duplicateCheck);
        String duplicateMapping = between(
                processModel,
                "if (this.idMappings[bake.blockId()] != -1)",
                "int fluidModelId");

        assertTrue(duplicateMapping.contains("throw new IllegalStateException"));
        assertTrue(duplicateMapping.contains("Block id already added:"));
        assertFalse(duplicateMapping.contains("removeInFlight"));
        assertFalse(duplicateMapping.contains("return true"));
    }

    @Test
    void factoryFailuresAreObservedAndTornDownOnlyByTheOwnerTick() throws IOException {
        String subsystem = source("src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java");
        String request = between(
                subsystem,
                "void requestBlockBake(int blockId)",
                "void addBiome(Mapper.BiomeEntry biomeEntry)");
        String pipeline = source("src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java");
        String processFactoryUploads = between(
                pipeline,
                "private void processFactoryUploads(ForgeOriginalVoxyRenderSystem renderSystem)",
                "private void runOriginalInnerPrimaryWorkBeforeTraversal");

        assertTrue(request.contains("catch (RuntimeException e)"));
        assertTrue(request.contains("this.recordFactoryFailure(e)"));
        assertFalse(request.contains("throw e;"));
        assertTrue(subsystem.contains(
                "return this.processingThreadException != null || this.factory.hasPendingUploads();"));
        assertTrue(subsystem.contains("throw new RuntimeException(this.processingThreadException)"));

        int tick = processFactoryUploads.indexOf("renderSystem.modelService().tick()");
        int preserve = processFactoryUploads.indexOf("throw preserveFactoryTickFailure(", tick);
        int uploadDrain = processFactoryUploads.indexOf("commitPendingCopiesBeforeOwnerTeardown()", preserve);
        int teardown = processFactoryUploads.indexOf("this.markStaleAndClear(", uploadDrain);
        assertTrue(tick >= 0 && preserve > tick && uploadDrain > preserve && teardown > uploadDrain);
    }

    @Test
    void biomeReplacementAndTintEnumerationAreFailFast() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/ModelFactory.java");
        String biome = between(source, "private ResultUploader addBiome0(int id, Biome biome)", "private void removeInFlight(int blockId)");
        String tint = between(
                source,
                "private static LinkedHashSet<Integer> collectBakedQuadTintIndices",
                "private static List<BakedQuad> getQuads");

        assertTrue(biome.contains("oldBiome != null && oldBiome != biome"));
        assertTrue(biome.contains("throw new IllegalStateException"));
        assertFalse(biome.contains("if (oldBiome != null) {\n            return null;"));
        assertTrue(tint.contains("tintIndices.add(quad.getTintIndex())"));
        assertFalse(tint.contains("catch (RuntimeException"));
    }

    @Test
    void mdicKeepsOnlyTheOriginalZeroSectionNoOp() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java");
        String build = between(source, "void buildDrawCalls(", "void addDebug(List<String> lines)");
        int zeroSection = build.indexOf("geometryData.getSectionCount() == 0");
        int rendererReady = build.indexOf("if (!this.ready())");

        assertTrue(zeroSection >= 0 && rendererReady > zeroSection);
        assertTrue(build.contains("throw this.failure(\"original-mdic-cmdgen-not-ready\")"));
        assertTrue(build.contains("throw this.failure(\"original-mdic-cmdgen-viewport-not-ready\")"));
        assertFalse(build.contains("catch (RuntimeException"));
        assertFalse(source.contains("this.recordFailure(\"original-mdic-section-renderer-not-ready\");\n            return;"));
        assertFalse(source.contains("this.recordFailure(\"original-mdic-cmdgen-not-ready\");\n            return;"));
    }

    @Test
    void fullscreenHizAndMdicShareOneProcessGlobalEmptyVertexArray() throws IOException {
        String owner = source("src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java");
        String fullscreen = source("src/main/java/me/cortex/voxy/forge/FullscreenBlit.java");
        String hiz = source("src/main/java/me/cortex/voxy/forge/HiZBuffer.java");
        String mdic = source("src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java");

        assertTrue(owner.contains("private static final int ID = glGenVertexArrays()"));
        assertFalse(owner.contains("glCreateVertexArrays"));
        assertFalse(owner.contains("glDeleteVertexArrays"));
        assertFalse(mdic.contains("ForgeOriginalVoxyEmptyVertexArray.ready()"));
        for (String consumer : new String[]{fullscreen, hiz, mdic}) {
            assertTrue(consumer.contains("ForgeOriginalVoxyEmptyVertexArray.id()"));
            assertFalse(consumer.contains("vertexArrayId"));
            assertFalse(consumer.contains("EMPTY_VAO"));
            assertFalse(consumer.contains("glDeleteVertexArrays"));
        }
    }

    private static String source(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertTrue(startIndex >= 0, "missing source anchor: " + start);
        assertTrue(endIndex > startIndex, "missing source anchor: " + end);
        return source.substring(startIndex, endIndex);
    }

    private static int occurrences(String value, String target) {
        return (value.length() - value.replace(target, "").length()) / target.length();
    }
}
