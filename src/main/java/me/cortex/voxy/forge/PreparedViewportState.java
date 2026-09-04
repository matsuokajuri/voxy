package me.cortex.voxy.forge;

/** Identity and dimensions of the exact Oculus viewport prepared from one capture. */
record PreparedViewportState(Object owner, Object viewport, long generation, int width, int height) {
    boolean matches(Object currentOwner, Object currentViewport, long capturedGeneration, int currentWidth, int currentHeight) {
        return owner != null && viewport != null
                && owner == currentOwner && viewport == currentViewport
                && generation == capturedGeneration
                && width > 0 && height > 0 && width == currentWidth && height == currentHeight;
    }
}
