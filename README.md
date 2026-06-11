Voxy is an LoD rendering mod for minecraft

## Forge 1.20.1 cached LoD PoC testing

The Forge port keeps the experimental renderer disabled by default. To see cached LoD in this branch, enable the world engine skeleton, auto chunk ingest, auto CPU mesh build, and the simple GPU mesh renderer in the client config. For easier visual checks, lower Minecraft render distance to 4-6 chunks, keep `simpleGpuMeshRenderDistanceChunks` around 64, fly far enough to cache chunks, then look back. If nothing appears, run `/voxy lod_visibility_status` to see whether cached chunks are too near, too far, still vanilla-loaded, or not uploaded yet.
