package me.cortex.voxy.forge;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import me.cortex.voxy.common.util.TrackedObject;

import java.lang.reflect.Array;
import java.util.Arrays;

import static org.lwjgl.opengl.ARBTimerQuery.GL_TIMESTAMP;
import static org.lwjgl.opengl.ARBTimerQuery.glQueryCounter;
import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15C.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL15C.GL_TRUE;
import static org.lwjgl.opengl.GL15C.glDeleteQueries;
import static org.lwjgl.opengl.GL15C.glGenQueries;
import static org.lwjgl.opengl.GL15C.glGetQueryObjecti;
import static org.lwjgl.opengl.GL33C.glGetQueryObjecti64;

/** Direct Forge-side port of original Voxy's timestamp-query debug owner. */
final class GPUTiming {
    static final GPUTiming INSTANCE = new GPUTiming();

    private final GlTimestampQuerySet<String> timingSet = new GlTimestampQuerySet<>(String.class);
    private float[] timings = new float[0];
    private String[] labels = new String[0];
    private boolean enabled;

    private GPUTiming() {
    }

    void marker() {
        this.marker(null);
    }

    void marker(String label) {
        if (this.enabled) {
            this.timingSet.capture(label);
        }
    }

    void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
        }
    }

    String getDebug() {
        if (!this.enabled) {
            return "";
        }
        StringBuilder output = new StringBuilder("GpuTime: [");
        for (int i = 0; i < this.timings.length; i++) {
            if (this.labels[i] != null) {
                output.append(this.labels[i]).append(':');
            }
            output.append(String.format(java.util.Locale.ROOT, "%.2f", this.timings[i]));
            if (i != this.timings.length - 1) {
                output.append(", ");
            }
        }
        return output.append(']').toString();
    }

    void tick() {
        this.timingSet.download((metadata, data) -> {
            long current = data[0];
            if (data.length - 1 != this.timings.length) {
                this.timings = new float[data.length - 1];
                this.labels = new String[metadata.length - 1];
            }
            Arrays.fill(this.labels, null);
            for (int i = 1; i < metadata.length; i++) {
                long next = data[i];
                float time = (float) ((double) (next - current) / 1_000_000D);
                this.timings[i - 1] = Math.max(this.timings[i - 1] * 0.99F + time * 0.01F, time);
                this.labels[i - 1] = metadata[i - 1];
                current = next;
            }
        });
        this.timingSet.tick();
    }

    void free() {
        this.timingSet.free();
    }

    @FunctionalInterface
    private interface TimingDataConsumer<T> {
        void accept(T metadata, long[] timings);
    }

    private static final class GlTimestampQuerySet<T> extends TrackedObject {
        private final IntArrayFIFOQueue pool = new IntArrayFIFOQueue();
        private final ObjectArrayFIFOQueue<InflightRequest<T>> inflight = new ObjectArrayFIFOQueue<>();
        private final int[] queries = new int[64];
        private final T[] metadata;
        private int index;

        @SuppressWarnings("unchecked")
        private GlTimestampQuerySet(Class<T> metadataClass) {
            this.metadata = (T[]) Array.newInstance(metadataClass, 64);
        }

        void capture(T metadata) {
            if (this.index >= this.metadata.length) {
                throw new IllegalStateException("Too many Voxy GPU timing markers");
            }
            int slot = this.index++;
            this.metadata[slot] = metadata;
            int query = this.getQuery();
            glQueryCounter(query, GL_TIMESTAMP);
            this.queries[slot] = query;
        }

        void download(TimingDataConsumer<T[]> consumer) {
            if (this.index != 0) {
                int[] capturedQueries = Arrays.copyOf(this.queries, this.index);
                T[] capturedMetadata = Arrays.copyOf(this.metadata, this.index);
                Arrays.fill(this.metadata, null);
                this.index = 0;
                this.inflight.enqueue(new InflightRequest<>(capturedQueries, capturedMetadata, consumer));
            }
        }

        void tick() {
            while (!this.inflight.isEmpty() && this.inflight.first().callbackIfReady(this.pool)) {
                this.inflight.dequeue();
            }
        }

        private int getQuery() {
            return this.pool.isEmpty() ? glGenQueries() : this.pool.dequeueInt();
        }

        @Override
        public void free() {
            this.free0();
            while (!this.pool.isEmpty()) {
                glDeleteQueries(this.pool.dequeueInt());
            }
            while (!this.inflight.isEmpty()) {
                glDeleteQueries(this.inflight.dequeue().queries);
            }
            if (this.index != 0) {
                glDeleteQueries(Arrays.copyOf(this.queries, this.index));
                this.index = 0;
            }
        }
    }

    private record InflightRequest<T>(int[] queries, T[] metadata, TimingDataConsumer<T[]> callback) {
        private boolean callbackIfReady(IntArrayFIFOQueue queryPool) {
            if (glGetQueryObjecti(this.queries[this.queries.length - 1], GL_QUERY_RESULT_AVAILABLE) != GL_TRUE) {
                return false;
            }
            long[] results = new long[this.queries.length];
            for (int i = 0; i < this.queries.length; i++) {
                results[i] = glGetQueryObjecti64(this.queries[i], GL_QUERY_RESULT);
                queryPool.enqueue(this.queries[i]);
            }
            this.callback.accept(this.metadata, results);
            return true;
        }
    }
}
