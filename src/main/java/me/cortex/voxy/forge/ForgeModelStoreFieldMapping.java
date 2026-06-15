package me.cortex.voxy.forge;

record ForgeModelStoreFieldMapping(
        String name,
        int wordOffset,
        int wordCount,
        boolean known,
        String source,
        String semantics
) {
}
