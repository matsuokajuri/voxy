package me.cortex.voxy.forge;

record ForgeFormalRendererBlocker(
        String priority,
        String id,
        String description
) {
    String compact() {
        return this.priority + ":" + this.id;
    }
}
