package me.cortex.voxy.forge;

import static org.lwjgl.opengl.GL11C.GL_TEXTURE_1D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_1D;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_BINDING_3D;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_BINDING_RECTANGLE;
import static org.lwjgl.opengl.GL31C.GL_TEXTURE_RECTANGLE;

/** Target-specific texture binding helpers for the Forge Embeddium state boundary. */
final class ForgeOriginalVoxyTextureBindings {
    private static final Binding[] EMPTY_BINDINGS = new Binding[0];

    private ForgeOriginalVoxyTextureBindings() {
    }

    static void bind2D(int unit, int texture) {
        bind(unit, GL_TEXTURE_2D, texture);
    }

    static void bind(int unit, int target, int texture) {
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        try {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(target, texture);
        } finally {
            glActiveTexture(activeTexture);
        }
    }

    static Binding[] captureNon2D(int baseUnit, int[] targets) {
        int bindingCount = 0;
        for (int target : targets) {
            if (target != GL_TEXTURE_2D) {
                bindingCount++;
            }
        }
        if (bindingCount == 0) {
            return EMPTY_BINDINGS;
        }

        Binding[] bindings = new Binding[bindingCount];
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        try {
            int outputIndex = 0;
            for (int i = 0; i < targets.length; i++) {
                int target = targets[i];
                if (target == GL_TEXTURE_2D) {
                    continue;
                }
                glActiveTexture(GL_TEXTURE0 + baseUnit + i);
                bindings[outputIndex++] = new Binding(
                        baseUnit + i,
                        target,
                        glGetInteger(bindingQuery(target)));
            }
        } finally {
            glActiveTexture(activeTexture);
        }
        return bindings;
    }

    static void restore(Binding[] bindings) {
        if (bindings == null || bindings.length == 0) {
            return;
        }
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        try {
            for (Binding binding : bindings) {
                glActiveTexture(GL_TEXTURE0 + binding.unit);
                glBindTexture(binding.target, binding.texture);
            }
        } finally {
            glActiveTexture(activeTexture);
        }
    }

    static int bindingQuery(int target) {
        return switch (target) {
            case GL_TEXTURE_1D -> GL_TEXTURE_BINDING_1D;
            case GL_TEXTURE_2D -> GL_TEXTURE_BINDING_2D;
            case GL_TEXTURE_3D -> GL_TEXTURE_BINDING_3D;
            case GL_TEXTURE_RECTANGLE -> GL_TEXTURE_BINDING_RECTANGLE;
            default -> throw new IllegalArgumentException("Unsupported Oculus texture target: " + target);
        };
    }

    static Binding[] empty() {
        return EMPTY_BINDINGS;
    }

    record Binding(int unit, int target, int texture) {
    }
}
