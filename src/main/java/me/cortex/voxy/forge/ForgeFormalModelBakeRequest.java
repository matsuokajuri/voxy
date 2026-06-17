package me.cortex.voxy.forge;

record ForgeFormalModelBakeRequest(
        int blockStateId,
        String blockStateIdSource,
        String blockState,
        String reason
) {
}
