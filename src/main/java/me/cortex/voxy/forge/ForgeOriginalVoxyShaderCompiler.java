package me.cortex.voxy.forge;

import org.lwjgl.system.Platform;

/** Applies the unconditional environment defines from the original Shader.Builder. */
final class ForgeOriginalVoxyShaderCompiler {
    private ForgeOriginalVoxyShaderCompiler() {
    }

    static String prepareSource(String source) {
        StringBuilder defines = new StringBuilder();
        if (Capabilities.INSTANCE.isIntel) {
            defines.append("#define IS_INTEL\n");
        }
        if (Platform.get() == Platform.WINDOWS) {
            defines.append("#define IS_WINDOWS\n");
        }
        if (defines.isEmpty()) {
            return source;
        }
        int split = source.indexOf('\n');
        if (split < 0) {
            return source + '\n' + defines;
        }
        return source.substring(0, split + 1) + defines + source.substring(split + 1);
    }
}
