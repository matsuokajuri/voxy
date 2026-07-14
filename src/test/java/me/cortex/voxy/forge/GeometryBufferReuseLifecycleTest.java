package me.cortex.voxy.forge;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeometryBufferReuseLifecycleTest {
    @Test
    void sparsePagesAreDecommittedAndFinishedBeforeCacheHandoff() {
        List<String> operations = new ArrayList<>();

        GeometryBufferReuseLifecycle.release(
                true,
                65_536L,
                () -> operations.add("decommit"),
                () -> operations.add("finish"),
                () -> operations.add("cache"));

        assertEquals(List.of("decommit", "finish", "cache"), operations);
    }

    @Test
    void emptyOrNonSparseBuffersStillFinishBeforeCacheHandoff() {
        for (boolean sparse : new boolean[]{false, true}) {
            List<String> operations = new ArrayList<>();

            GeometryBufferReuseLifecycle.release(
                    sparse,
                    0L,
                    () -> operations.add("decommit"),
                    () -> operations.add("finish"),
                    () -> operations.add("cache"));

            assertEquals(List.of("finish", "cache"), operations);
        }
    }

    @Test
    void cachedGeometryDescriptorCannotCarrySparseCommitment() {
        assertEquals(
                List.of("bufferId", "capacityBytes", "sparse", "nvidiaWindowsSparseWorkaroundUsed"),
                Arrays.stream(RenderResourceReuse.ReusedGeometryBuffer.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList());
    }
}
