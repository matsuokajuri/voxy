package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyGeometryRouteRetirementTest {
    private static final Path FORGE_SOURCE = Path.of("src/main/java/me/cortex/voxy/forge");

    @Test
    void retiredGeometryMdicIslandStaysAbsent() throws IOException {
        List<String> retiredPrefixes = List.of(
                "ForgeCpu",
                "ForgeGpuGeometry",
                "ForgeMdicCommand",
                "ForgeMdicVisibility",
                "ForgeSectionGeometry",
                "ForgeVoxyBuiltSection");
        List<String> retiredExactNames = List.of(
                "ForgeVoxyGeometryBuffer.java",
                "ForgeVoxyGeometryCache.java",
                "ForgeVoxyGreedyMesher.java",
                "ForgeVoxyQuadEncoder.java");

        try (Stream<Path> files = Files.list(FORGE_SOURCE)) {
            List<String> offenders = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> retiredPrefixes.stream().anyMatch(name::startsWith)
                            || retiredExactNames.contains(name))
                    .sorted()
                    .toList();
            assertTrue(offenders.isEmpty(), "Retired geometry/MDIC sources returned: " + offenders);
        }
    }

    @Test
    void packageLocalGeometryTypesRemainOnTheFormalVisibleRoute() throws IOException {
        String instance = source("ForgeVoxyInstance.java");
        String modelPipeline = source("ForgeOriginalVoxyModelPipeline.java");
        String renderSystem = source("ForgeOriginalVoxyRenderSystem.java");
        String renderGeneration = source("RenderGenerationService.java");

        assertTrue(instance.contains("new ForgeOriginalVoxyModelPipeline(owner)"));
        assertTrue(modelPipeline.contains("new ForgeOriginalVoxyRenderSystem("));
        assertTrue(renderSystem.contains("this.renderGen = new RenderGenerationService("));
        assertTrue(renderSystem.contains("this.nodeManager = new AsyncNodeManager("));
        assertTrue(renderSystem.contains("this.sectionRenderer = new MDICSectionRenderer();"));
        assertTrue(renderGeneration.contains("RenderDataFactory factory = new RenderDataFactory("));

        assertFalse(instance.contains("ForgeCpu"));
        assertFalse(instance.contains("ForgeMdicCommand"));
        assertFalse(instance.contains("ForgeSectionGeometry"));
    }

    private static String source(String fileName) throws IOException {
        return Files.readString(FORGE_SOURCE.resolve(fileName));
    }
}
