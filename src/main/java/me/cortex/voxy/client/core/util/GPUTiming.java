package me.cortex.voxy.client.core.util;

import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.core.vulkan.VoxyVulkanContext;
import me.cortex.voxy.common.Logger;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.OptionalLong;

public final class GPUTiming {
    private static final int MAX_MARKERS = 64;
    private static final double NANOSECONDS_PER_MILLISECOND = 1_000_000.0;

    public static final GPUTiming INSTANCE = new GPUTiming();

    private final Deque<GpuQueryPool> freePools = new ArrayDeque<>();
    private final Deque<PendingFrame> pendingFrames = new ArrayDeque<>();
    private final String[] currentLabels = new String[MAX_MARKERS];

    private GpuQueryPool currentPool;
    private int currentCount;
    private float[] timings = new float[0];
    private String[] labels = new String[0];
    private boolean enabled;
    private boolean closed;

    private GPUTiming() {
    }

    public void marker() {
        this.marker(null);
    }

    public void marker(String label) {
        if (!this.enabled) return;
        RenderSystem.assertOnRenderThread();
        this.ensureOpen();
        if (this.currentCount >= MAX_MARKERS) {
            throw new IllegalStateException("Voxy GPU timing exceeded " + MAX_MARKERS + " markers in one frame");
        }
        if (this.currentPool == null) {
            this.currentPool = this.freePools.pollFirst();
            if (this.currentPool == null) {
                this.currentPool = VoxyVulkanContext.get().hostDevice().createTimestampQueryPool(MAX_MARKERS);
            }
        }

        int slot = this.currentCount++;
        this.currentLabels[slot] = label;
        VoxyVulkanContext.get().hostDevice().createCommandEncoder().writeTimestamp(this.currentPool, slot);
    }

    public boolean setEnabled(boolean enable) {
        if (enable && !VoxyVulkanContext.isInitialized()) {
            Logger.warn("Voxy GPU timing requires the Minecraft Vulkan host; the debug timing request was not enabled");
            this.enabled = false;
            return false;
        }
        this.ensureOpen();
        this.enabled = enable;
        return this.enabled;
    }

    public String getDebug() {
        if (!this.enabled) return "";
        StringBuilder result = new StringBuilder("GpuTime: [");
        for (int i = 0; i < this.timings.length; i++) {
            if (this.labels[i] != null) {
                result.append(this.labels[i]).append(':');
            }
            result.append(String.format("%.2f", this.timings[i]));
            if (i != this.timings.length - 1) result.append(", ");
        }
        return result.append(']').toString();
    }

    public void tick() {
        if (this.closed) return;
        RenderSystem.assertOnRenderThread();
        this.enqueueCurrentFrame();
        while (!this.pendingFrames.isEmpty()) {
            PendingFrame frame = this.pendingFrames.getFirst();
            OptionalLong[] values = frame.pool().getValues(0, frame.labels().length);
            if (Arrays.stream(values).anyMatch(OptionalLong::isEmpty)) return;

            this.consume(frame.labels(), values);
            this.pendingFrames.removeFirst();
            this.freePools.addLast(frame.pool());
        }
    }

    private void enqueueCurrentFrame() {
        if (this.currentCount == 0) return;
        this.pendingFrames.addLast(new PendingFrame(
                this.currentPool,
                Arrays.copyOf(this.currentLabels, this.currentCount)
        ));
        Arrays.fill(this.currentLabels, 0, this.currentCount, null);
        this.currentPool = null;
        this.currentCount = 0;
    }

    private void consume(String[] frameLabels, OptionalLong[] values) {
        int intervalCount = values.length - 1;
        if (intervalCount != this.timings.length) {
            this.timings = new float[intervalCount];
            this.labels = new String[intervalCount];
        } else {
            Arrays.fill(this.labels, null);
        }

        double timestampPeriod = VoxyVulkanContext.get().hostDevice().getDeviceInfo().timestampPeriod();
        if (!(timestampPeriod > 0.0)) {
            throw new IllegalStateException("Minecraft Vulkan device reported an invalid timestamp period: " + timestampPeriod);
        }
        long current = values[0].getAsLong();
        for (int i = 1; i < values.length; i++) {
            long next = values[i].getAsLong();
            long deltaTicks = next - current;
            float milliseconds = (float) (deltaTicks * timestampPeriod / NANOSECONDS_PER_MILLISECOND);
            this.timings[i - 1] = Math.max(this.timings[i - 1] * 0.99f + milliseconds * 0.01f, milliseconds);
            this.labels[i - 1] = frameLabels[i - 1];
            current = next;
        }
    }

    public void free() {
        if (this.closed) return;
        this.closed = true;
        this.enabled = false;
        if (this.currentPool != null) {
            this.currentPool.close();
            this.currentPool = null;
        }
        while (!this.pendingFrames.isEmpty()) this.pendingFrames.removeFirst().pool().close();
        while (!this.freePools.isEmpty()) this.freePools.removeFirst().close();
        Arrays.fill(this.currentLabels, null);
        this.currentCount = 0;
    }

    private void ensureOpen() {
        if (this.closed) throw new IllegalStateException("Voxy GPU timing has already been closed");
    }

    private record PendingFrame(GpuQueryPool pool, String[] labels) {
    }
}
