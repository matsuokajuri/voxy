package me.cortex.voxy.forge;

import java.util.List;

public record ForgeVoxyBuiltSectionBuildResult(
        ForgeVoxyBuiltSectionStats stats,
        List<ForgeVoxyBuiltSection> sections
) {
}
