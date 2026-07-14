package me.cortex.voxy.forge;

import static org.lwjgl.opengl.GL30C.glGenVertexArrays;

/**
 * Client-process-global empty vertex array matching original Voxy's shared static VAO owner.
 *
 * <p>The Minecraft client owns one OpenGL context for the lifetime of the process, so this
 * object deliberately has no renderer- or viewport-scoped deletion path.</p>
 */
final class ForgeOriginalVoxyEmptyVertexArray {
    private static final int ID = glGenVertexArrays();

    private ForgeOriginalVoxyEmptyVertexArray() {
    }

    static int id() {
        return ID;
    }
}
