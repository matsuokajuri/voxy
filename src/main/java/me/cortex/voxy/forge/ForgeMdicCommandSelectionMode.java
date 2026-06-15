package me.cortex.voxy.forge;

enum ForgeMdicCommandSelectionMode {
    FIRST_N,
    NEAREST_CAMERA,
    RADIUS,
    FRUSTUM_RADIUS,
    AUTO;

    static ForgeMdicCommandSelectionMode parse(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        for (ForgeMdicCommandSelectionMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return AUTO;
    }
}
