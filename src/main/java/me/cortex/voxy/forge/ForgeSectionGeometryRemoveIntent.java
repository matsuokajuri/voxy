package me.cortex.voxy.forge;

public record ForgeSectionGeometryRemoveIntent(
        int sectionId,
        String dimension,
        long position,
        int geometryPtr,
        int freedItems,
        long freedBytes
) {
}
