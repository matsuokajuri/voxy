package me.cortex.voxy.forge;

/** Sentinel used to unwind a malformed Voxy shaderpack patch without crashing Oculus. */
public final class ForgeOriginalVoxyShaderLoadError extends RuntimeException {
    public ForgeOriginalVoxyShaderLoadError(String message, Throwable cause) {
        super(message, cause);
    }
}
