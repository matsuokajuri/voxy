package me.cortex.voxy.forge;

import static org.lwjgl.opengl.NVShaderBufferLoad.glIsNamedBufferResidentNV;
import static org.lwjgl.opengl.NVShaderBufferLoad.glMakeNamedBufferNonResidentNV;

/** Optional Acedium teardown adapter; never used by Voxy's own buffer owners. */
public final class AcediumBufferResidency {
    private AcediumBufferResidency() {
    }

    public static void releaseIfResident(int buffer) {
        // Acedium 0.2.7's CPU-mapped buffer is never made NV-resident by its constructor,
        // yet delete() unconditionally makes it non-resident. Keep its unmap/delete owner
        // intact and release an NV address only if one was actually acquired.
        if (glIsNamedBufferResidentNV(buffer)) {
            glMakeNamedBufferNonResidentNV(buffer);
        }
    }
}
