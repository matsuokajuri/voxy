package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelUploadFailureLifecycleParityTest {
    @Test
    void modelFactoryTreatsThreadAndStoreReadinessAsFatalOwnerInvariants() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/ModelFactory.java");
        String processUploads = between(source, "void processUploads()", "boolean hasPendingUploads()");

        String wrongThread = between(
                processUploads,
                "if (!RenderSystem.isOnRenderThread())",
                "if (!this.store.canUploadOriginalVoxyModel())");
        assertTrue(wrongThread.contains("throw new IllegalStateException"));
        assertFalse(wrongThread.contains("return;"));

        int storeInvariant = processUploads.indexOf("if (!this.store.canUploadOriginalVoxyModel())");
        int storeFatal = processUploads.indexOf("throw new IllegalStateException", storeInvariant);
        int firstPoll = processUploads.indexOf("this.uploadResults.poll()");
        assertTrue(storeInvariant >= 0 && storeFatal > storeInvariant && firstPoll > storeFatal);
        assertFalse(processUploads.contains("this.store.build("));
        assertFalse(source.contains("ensureStoreReady"));
    }

    @Test
    void pipelineCommitsOrDiscardsPendingCopiesBeforeOwnerTeardown() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java");
        String processFactoryUploads = between(
                source,
                "private void processFactoryUploads(ForgeOriginalVoxyRenderSystem renderSystem)",
                "private void runOriginalInnerPrimaryWorkBeforeTraversal");

        String wrongThread = between(
                processFactoryUploads,
                "if (!RenderSystem.isOnRenderThread())",
                "synchronized (this)");
        assertTrue(wrongThread.contains("throw new IllegalStateException"));
        assertFalse(wrongThread.contains("recordFailure"));
        assertFalse(wrongThread.contains("return;"));

        int rootCatch = processFactoryUploads.indexOf("catch (RuntimeException e)");
        int preserve = processFactoryUploads.indexOf("throw preserveFactoryTickFailure", rootCatch);
        int pendingCopies = processFactoryUploads.indexOf("commitPendingCopiesBeforeOwnerTeardown()", preserve);
        int ownerTeardown = processFactoryUploads.indexOf("this.markStaleAndClear(", pendingCopies);
        assertTrue(rootCatch >= 0 && preserve > rootCatch && pendingCopies > preserve && ownerTeardown > pendingCopies);

        String preserveHelper = between(
                source,
                "static RuntimeException preserveFactoryTickFailure(",
                "private void runOriginalInnerPrimaryWorkBeforeTraversal");
        assertEquals(2, occurrences(preserveHelper, "runCleanupSuppressingFailure(failure,"));
        assertTrue(preserveHelper.contains("catch (Throwable cleanupFailure)"));
        assertTrue(preserveHelper.contains("failure.addSuppressed(cleanupFailure)"));
        assertTrue(preserveHelper.contains("return failure;"));
    }

    @Test
    void teardownUploadDrainCannotRetainDeletedTargetBufferNames() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/UploadStream.java");
        String cleanup = between(
                source,
                "void commitPendingCopiesBeforeOwnerTeardown()",
                "void tick()");

        int tryBlock = cleanup.indexOf("try {");
        int commit = cleanup.indexOf("this.commit()", tryBlock);
        int finallyBlock = cleanup.indexOf("finally", commit);
        int clearTargets = cleanup.indexOf("this.uploadList.clear()", finallyBlock);
        int detachSpan = cleanup.indexOf("this.caddr = -1L", clearTargets);
        assertTrue(tryBlock >= 0
                && commit > tryBlock
                && finallyBlock > commit
                && clearTargets > finallyBlock
                && detachSpan > clearTargets);
        assertTrue(cleanup.contains("this.offset = 0L"));
    }

    @Test
    void cleanupFailuresAreSuppressedWithoutReplacingTheFactoryFailure() {
        RuntimeException root = new RuntimeException("factory-root");
        RuntimeException commitFailure = new RuntimeException("commit-secondary");
        Error teardownFailure = new AssertionError("teardown-secondary");
        List<String> calls = new ArrayList<>();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            throw ForgeOriginalVoxyModelPipeline.preserveFactoryTickFailure(
                    root,
                    () -> {
                        calls.add("commit");
                        throw commitFailure;
                    },
                    () -> {
                        calls.add("teardown");
                        throw teardownFailure;
                    });
        });

        assertSame(root, thrown);
        assertEquals(List.of("commit", "teardown"), calls);
        assertEquals(2, root.getSuppressed().length);
        assertSame(commitFailure, root.getSuppressed()[0]);
        assertSame(teardownFailure, root.getSuppressed()[1]);
    }

    @Test
    void failedUploaderPayloadRemainsQueueOwnedUntilFactoryFree() throws IOException {
        String source = source("src/main/java/me/cortex/voxy/forge/ModelFactory.java");
        String processUploads = between(source, "void processUploads()", "boolean hasPendingUploads()");
        String runtimeFailure = between(
                processUploads,
                "catch (RuntimeException e)",
                "if (!\"none\".equals(error))");
        String returnedFailure = between(
                processUploads,
                "if (!\"none\".equals(error))",
                "upload.free()");
        String free = between(source, "void free()", "private boolean processModelResult");

        assertTrue(runtimeFailure.contains("this.uploadResults.addFirst(upload)"));
        assertTrue(returnedFailure.contains("this.uploadResults.addFirst(upload)"));
        assertFalse(runtimeFailure.contains("upload.free()"));
        assertFalse(returnedFailure.contains("upload.free()"));
        assertEquals(1, occurrences(free, "upload.free()"));
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
