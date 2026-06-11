package me.cortex.voxy.forge;

import java.util.List;

public record ForgeCpuMeshBuildResult(
        ForgeCpuMeshBuildStats stats,
        List<ForgeCpuBuiltSection> sections
) {
}
