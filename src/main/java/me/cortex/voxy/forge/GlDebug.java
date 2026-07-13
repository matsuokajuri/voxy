package me.cortex.voxy.forge;

import static org.lwjgl.opengl.GL43C.GL_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL43C.GL_PROGRAM;
import static org.lwjgl.opengl.GL43C.GL_TEXTURE;
import static org.lwjgl.opengl.GL43C.glObjectLabel;

/** Forge-side port of original Voxy's opt-in OpenGL object labels. */
final class GlDebug {
    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("voxy.glDebug", "false"));

    private GlDebug() {
    }

    static int buffer(String name, int id) {
        return label(GL_BUFFER, name, id);
    }

    static int texture(String name, int id) {
        return label(GL_TEXTURE, name, id);
    }

    static int framebuffer(String name, int id) {
        return label(GL_FRAMEBUFFER, name, id);
    }

    static int program(String name, int id) {
        return label(GL_PROGRAM, name, id);
    }

    private static int label(int type, String name, int id) {
        if (ENABLED && id != 0) {
            glObjectLabel(type, id, name);
        }
        return id;
    }
}
