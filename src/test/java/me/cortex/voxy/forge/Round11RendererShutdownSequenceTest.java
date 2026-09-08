package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Round11RendererShutdownSequenceTest {
    @Test
    void nodeAndGlFailuresStillReachAllLaterOwnersAndRemainReported() {
        List<String> attempted = new ArrayList<>(), reported = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        RendererShutdownSequence cleanup = new RendererShutdownSequence((stage, failure) -> {
            reported.add(stage);
            errors.add(failure);
        });
        IllegalStateException nodeFailure = new IllegalStateException("node invariant");
        AssertionError geometryFailure = new AssertionError("geometry cleanup");
        for (String stage : List.of("nodes", "models", "generation", "geometry", "pipeline", "world")) {
            boolean success = cleanup.attempt(stage, () -> {
                attempted.add(stage);
                if (stage.equals("nodes")) throw nodeFailure;
                if (stage.equals("geometry")) throw geometryFailure;
            });
            assertEquals(!stage.equals("nodes") && !stage.equals("geometry"), success);
        }
        assertEquals(List.of("nodes", "models", "generation", "geometry", "pipeline", "world"), attempted);
        assertEquals(List.of("nodes", "geometry"), reported);
        assertEquals(List.of(nodeFailure, geometryFailure), errors);
        IllegalStateException aggregate = assertThrows(IllegalStateException.class, cleanup::throwIfFailed);
        assertSame(nodeFailure, aggregate.getCause());
        assertArrayEquals(new Throwable[]{geometryFailure}, aggregate.getSuppressed());
    }

    @Test
    void reporterFailureDoesNotSkipWorldReferenceReleaseAndIsNotLost() {
        IllegalStateException reportingFailure = new IllegalStateException("reporting failed");
        RendererShutdownSequence cleanup = new RendererShutdownSequence((stage, failure) -> { throw reportingFailure; });
        cleanup.attempt("nodes", () -> { throw new IllegalStateException("nodes"); });
        boolean[] released = {false};
        assertTrue(cleanup.attempt("world", () -> released[0] = true));
        assertTrue(released[0]);
        IllegalStateException aggregate = assertThrows(IllegalStateException.class, cleanup::throwIfFailed);
        assertArrayEquals(new Throwable[]{reportingFailure}, aggregate.getSuppressed());
    }

    @Test
    void productionOwnerWiresIndependentActionsInOriginalOrderAndThrowsOnlyAfterWorldRelease() throws Exception {
        String source = Files.readString(Path.of("src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java"));
        String shutdown = source.substring(source.indexOf("boolean shutdown(boolean flushDownloadStream)"),
                source.indexOf("private void freeOnConstructionFailure"));
        int previous = -1;
        for (String stage : List.of("initial-download-flush", "world-dirty-callback", "biome-callback", "state-callback",
                "node-manager", "model-service", "render-generation", "traversal", "node-cleaner", "geometry-data",
                "chunk-bounds", "viewports", "section-renderer", "pipeline", "final-download-flush", "world-reference")) {
            int current = shutdown.indexOf("cleanup.attempt(\"" + stage + "\"");
            assertTrue(current > previous, stage);
            previous = current;
        }
        assertTrue(shutdown.indexOf("cleanup.throwIfFailed()") > previous);
        RendererShutdownSequence successful = new RendererShutdownSequence((stage, failure) -> fail("unexpected failure"));
        successful.attempt("world", () -> {});
        assertDoesNotThrow(successful::throwIfFailed);
    }
}
