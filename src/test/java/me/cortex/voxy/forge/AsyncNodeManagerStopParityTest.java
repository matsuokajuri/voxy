package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncNodeManagerStopParityTest {
    @Test
    void completedStopFailsButWorkerFailureDoesNotPreventOwnedResourceCleanup() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java"));
        int stop = source.indexOf("void stop()");
        int stoppedCheck = source.indexOf("if (this.stopped)", stop);
        int stoppedFailure = source.indexOf("throw new IllegalStateException(", stoppedCheck);
        int runningFalse = source.indexOf("this.running = false;", stop);
        int workerUnpark = source.indexOf("LockSupport.unpark(this.workerThread);", stop);
        int cleanup = source.indexOf("BuiltSection section = this.geometryUpdateQueue.poll()", stop);

        assertTrue(stop >= 0);
        assertTrue(stoppedCheck > stop && stoppedFailure > stoppedCheck);
        assertTrue(stoppedFailure < runningFalse);
        assertTrue(stoppedFailure < workerUnpark);
        assertTrue(stoppedFailure < cleanup);
    }

    @Test
    void interruptedJoinThrowsBeforeAnyStopCleanup() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java"));
        int stop = source.indexOf("void stop()");
        int interruptedCatch = source.indexOf("catch (InterruptedException e)", stop);
        int cleanup = source.indexOf("BuiltSection section = this.geometryUpdateQueue.poll()", interruptedCatch);

        assertTrue(stop >= 0 && interruptedCatch > stop && cleanup > interruptedCatch);
        String interruptionPath = source.substring(interruptedCatch, cleanup);
        assertTrue(interruptionPath.contains("throw new RuntimeException(e);"));
        assertTrue(interruptionPath.contains("Thread.currentThread().interrupt()"));
    }
}
