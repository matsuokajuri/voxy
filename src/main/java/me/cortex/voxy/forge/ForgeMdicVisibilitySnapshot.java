package me.cortex.voxy.forge;

record ForgeMdicVisibilitySnapshot(
        boolean cameraAvailable,
        double cameraX,
        double cameraY,
        double cameraZ,
        int cameraChunkX,
        int cameraChunkZ,
        int cameraSectionX,
        int cameraSectionY,
        int cameraSectionZ,
        boolean frustumAvailable,
        String frustumUnavailableReason,
        long capturedAtMillis
) {
    static ForgeMdicVisibilitySnapshot missingCamera(String reason) {
        return new ForgeMdicVisibilitySnapshot(false, 0.0D, 0.0D, 0.0D, 0, 0, 0, 0, 0, false, reason == null ? "CAMERA_UNAVAILABLE" : reason, System.currentTimeMillis());
    }

    double ageMs() {
        return Math.max(0L, System.currentTimeMillis() - this.capturedAtMillis);
    }

    String cameraPositionString() {
        return this.cameraAvailable
                ? String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", this.cameraX, this.cameraY, this.cameraZ)
                : "none";
    }

    String cameraChunkString() {
        return this.cameraAvailable ? this.cameraChunkX + "," + this.cameraChunkZ : "none";
    }

    String cameraSectionString() {
        return this.cameraAvailable ? this.cameraSectionX + "," + this.cameraSectionY + "," + this.cameraSectionZ : "none";
    }
}
