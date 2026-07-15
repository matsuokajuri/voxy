package me.cortex.voxy.client.core.vulkan;

final class VulkanRanges {
    private VulkanRanges() {
    }

    static void check(long offset, long length, long capacity, String description) {
        if (offset < 0L || length < 0L || offset > capacity || length > capacity - offset) {
            throw new IllegalArgumentException(description + " range [" + offset + ", " + length
                    + "] exceeds capacity " + capacity);
        }
    }

    static void checkAlignment(long value, long alignment, String description) {
        if (alignment <= 0L || value % alignment != 0L) {
            throw new IllegalArgumentException(description + " must be aligned to " + alignment + " bytes: " + value);
        }
    }
}
