package me.cortex.voxy.forge;

import static org.lwjgl.opengl.GL11C.GL_GEQUAL;
import static org.lwjgl.opengl.GL11C.GL_GREATER;
import static org.lwjgl.opengl.GL11C.GL_LEQUAL;
import static org.lwjgl.opengl.GL11C.GL_LESS;

record ForgeOriginalVoxyRenderProperties(boolean isZero2One, boolean isReverseZ, boolean useBlockAtlasUVs) {
    static ForgeOriginalVoxyRenderProperties getRenderProperties() {
        return new ForgeOriginalVoxyRenderProperties(false, false, false);
    }

    String injectDefines(String source) {
        StringBuilder defines = new StringBuilder();
        if (this.isZero2One) {
            defines.append("#define USE_ZERO_ONE_DEPTH\n");
        }
        if (this.isReverseZ) {
            defines.append("#define USE_REVERSE_Z\n");
        }
        int split = source.indexOf('\n');
        if (split < 0) {
            return source + '\n' + defines;
        }
        return source.substring(0, split + 1) + defines + source.substring(split + 1);
    }

    int closerEqualDepthCompare() {
        return this.isReverseZ ? GL_GEQUAL : GL_LEQUAL;
    }

    int closerDepthCompare() {
        return this.isReverseZ ? GL_GREATER : GL_LESS;
    }

    float clearDepth() {
        return this.isReverseZ ? 0.0F : 1.0F;
    }

    float inverseClearDepth() {
        return this.isReverseZ ? 1.0F : 0.0F;
    }
}
