package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncNodeManagerTopLevelWorkParityTest {
    @Test
    void topLevelDeltaAndWorkCountShareTheOriginalProducerLock() throws IOException {
        String forge = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java"));
        assertFalse(forge.contains("addTopLevelWork("));
        assertForgeMethod(
                forge,
                "void addTopLevel(long sectionPosition)",
                "this.topLevelNodeRemoves.remove(sectionPosition)",
                "this.topLevelNodeAdds.add(sectionPosition)");
        assertForgeMethod(
                forge,
                "void removeTopLevel(long sectionPosition)",
                "this.topLevelNodeAdds.remove(sectionPosition)",
                "this.topLevelNodeRemoves.add(sectionPosition)");
        assertWorkerSnapshotUsesTheSameLock(forge);

        String original = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/client/core/rendering/hierachical/AsyncNodeManager.java"));
        assertOriginalMethod(
                original,
                "public void addTopLevel(long section)",
                "this.tlnAdd.add(section)");
        assertOriginalMethod(
                original,
                "public void removeTopLevel(long section)",
                "this.tlnRem.add(section)");
    }

    @Test
    void nodeManagerVerificationMatchesOriginalPublicationAndRequestAccounting() throws IOException {
        String async = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java"));
        assertTrue(async.contains(
                "System.getProperty(\"voxy.verifyNodeManager\", \"false\").equals(\"true\")"));
        String worker = braceBlock(async, "private void workerRun()");
        int publish = worker.indexOf("this.publishSyncResults();");
        int verify = worker.indexOf("this.nodeManager.verifyIntegrity();");
        assertTrue(publish >= 0 && verify > publish);

        String manager = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/NodeManager.java"));
        assertEquals(2, occurrences(manager, "this.childRequests.put(request)"));
        assertEquals(4, occurrences(manager, "this.childRequests.release(requestId)"));
        assertEquals(2, occurrences(manager, "this.activeNodeRequestCount++"));
        assertEquals(4, occurrences(manager, "this.activeNodeRequestCount--"));
        assertTrue(manager.contains("private int verifyRequest("));
        assertTrue(manager.contains("private void verifyNode("));
        assertTrue(manager.contains("void verifyIntegrity()"));
        assertTrue(manager.contains("void verifyIntegrity(LongSet watchingPositions, IntSet nodes)"));
        assertTrue(manager.contains(
                "this.activeNodeRequestCount != this.childRequests.count()"));
    }

    private static void assertForgeMethod(
            String source,
            String signature,
            String opposingRemoval,
            String pendingAddition) {
        String method = braceBlock(source, signature);
        String producerLock = braceBlock(method, "synchronized (this.topLevelNodeLock)");
        int removalIndex = producerLock.indexOf(opposingRemoval);
        int additionIndex = producerLock.indexOf(pendingAddition);
        int countIndex = producerLock.indexOf("this.workCounter.getAndAdd(state)");
        int unparkIndex = producerLock.indexOf("LockSupport.unpark(this.workerThread)");

        assertTrue(removalIndex >= 0, signature);
        assertTrue(additionIndex > removalIndex, signature);
        assertTrue(countIndex > additionIndex, signature);
        assertTrue(unparkIndex > countIndex, signature);
        assertEquals(1, occurrences(method, "this.workCounter.getAndAdd(state)"), signature);
        assertEquals(1, occurrences(producerLock, "this.workCounter.getAndAdd(state)"), signature);
        assertEquals(1, occurrences(method, "LockSupport.unpark(this.workerThread)"), signature);
        assertEquals(1, occurrences(producerLock, "LockSupport.unpark(this.workerThread)"), signature);
    }

    private static void assertWorkerSnapshotUsesTheSameLock(String source) {
        String worker = braceBlock(source, "private void workerRun()");
        String workerLock = braceBlock(worker, "synchronized (this.topLevelNodeLock)");
        for (String operation : new String[]{
                "new LongOpenHashSet(this.topLevelNodeAdds)",
                "this.topLevelNodeAdds.clear()",
                "new LongOpenHashSet(this.topLevelNodeRemoves)",
                "this.topLevelNodeRemoves.clear()"}) {
            assertTrue(workerLock.contains(operation), operation);
            assertEquals(1, occurrences(worker, operation), operation);
            assertEquals(1, occurrences(workerLock, operation), operation);
        }
    }

    private static void assertOriginalMethod(String source, String signature, String mutation) {
        String method = braceBlock(source, signature);
        int lockIndex = method.indexOf("this.tlnLock.writeLock()");
        int mutationIndex = method.indexOf(mutation);
        int countIndex = method.indexOf("this.workCounter.getAndAdd(state)");
        int unparkIndex = method.indexOf("LockSupport.unpark(this.thread)");
        int unlockIndex = method.indexOf("this.tlnLock.unlockWrite(stamp)");

        assertTrue(lockIndex >= 0, signature);
        assertTrue(mutationIndex > lockIndex, signature);
        assertTrue(countIndex > mutationIndex, signature);
        assertTrue(unparkIndex > countIndex, signature);
        assertTrue(unlockIndex > unparkIndex, signature);
    }

    private static String braceBlock(String source, String anchor) {
        int anchorIndex = source.indexOf(anchor);
        assertTrue(anchorIndex >= 0, "missing source anchor: " + anchor);
        int open = source.indexOf('{', anchorIndex + anchor.length());
        assertTrue(open >= 0, "missing opening brace after: " + anchor);
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') {
                depth++;
            } else if (value == '}' && --depth == 0) {
                return source.substring(open + 1, index);
            }
        }
        throw new AssertionError("missing closing brace after: " + anchor);
    }

    private static int occurrences(String value, String target) {
        return (value.length() - value.replace(target, "").length()) / target.length();
    }
}
