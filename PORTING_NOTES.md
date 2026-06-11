# Voxy Forge 1.20.1 Porting Notes

## Forge 1.20.1 LoD PoC Current Status

The `forge-1.20.1-skeleton` branch currently has a Forge 1.20.1 cached LoD proof of concept.

Working today:

- Starts as a Forge 1.20.1 client mod on Java 17.
- Enters a client world without Fabric, Sodium, Iris, Embeddium, or Oculus dependencies.
- Creates and closes a minimal in-memory `WorldEngine` skeleton.
- Automatically ingests already-loaded client chunks when enabled.
- Converts chunks into Voxy sections.
- Builds CPU-side mesh data and stores it in a CPU mesh cache.
- Uploads simple mesh data into vanilla `VertexBuffer` objects.
- Renders cached LoD color blocks outside the vanilla render-distance neighborhood.

This renderer is a simple Forge GPU PoC. It is not the original Voxy renderer, and it does not use the original MDIC, SSBO, shader, or custom GL pipeline.

## How To Reproduce Cached LoD

1. Start the Forge client with `runClient`.
2. Enter a singleplayer world.
3. Run:

   ```text
   /voxy preset lod
   ```

4. Set Minecraft render distance to 4-6 chunks.
5. Fly through an area long enough for chunks to be ingested, meshed, cached, and uploaded.
6. Turn around and look toward chunks that have left the vanilla render-distance neighborhood.
7. Check diagnostics:

   ```text
   /voxy gpu_mesh_status
   /voxy lod_visibility_status
   ```

If no LoD is visible, the status commands should show whether cached mesh is too near, too far, still filtered by the vanilla render-distance skip mode, in another dimension, not uploaded, or already evicted.

For a direct visibility check, use:

```text
/voxy preset overlay
```

Overlay mode intentionally disables the loaded-chunk skip and uses bright debug colors. It is only a visibility/debug mode, not the intended cached LoD mode.

## Common Commands

- `/voxy preset overlay` - Runtime-only overlay debug preset. Does not write the TOML config.
- `/voxy preset lod` - Runtime-only cached LoD preset. Does not write the TOML config.
- `/voxy preset off` - Runtime-only shutdown for engine, auto ingest, auto CPU mesh build, and renderers.
- `/voxy preset status` - Shows effective config values and whether each value came from config or runtime override.
- `/voxy ingest_current_chunk` - Manually ingest the current chunk.
- `/voxy build_current_chunk_cpu_mesh` - Manually build CPU mesh for the current chunk.
- `/voxy mesh_cache_status` - Shows CPU mesh cache and debug pipeline status.
- `/voxy gpu_mesh_status` - Shows GPU upload/render/cache status and LoD filter diagnostics.
- `/voxy lod_visibility_status` - Shows whether cached chunks are inside the current LoD visibility window.
- `/voxy gpu_mesh_clear` - Clears simple GPU buffers while keeping the CPU mesh cache.
- `/voxy debug_pipeline_clear` - Clears ingest records, mesh build records, CPU mesh cache, and GPU mesh cache.

## Current Limitations

- The renderer is a simple Forge GPU PoC, not the original Voxy MDIC/SSBO renderer.
- There is no texture atlas integration yet.
- There is no shaderpack support.
- There is no Embeddium or Oculus integration.
- Mesh output is currently simplified color-block rendering.
- Translucent mesh is skipped.
- Ambient occlusion is not restored.
- Transparent sorting is not restored.
- Real lightmap behavior is not restored.
- Biome tint is only partially represented through current model-aware CPU mesh work.
- Block entity and special model rendering are not restored.
- Cross-chunk precise culling is not complete.
- The original Voxy renderer is still not connected.
- Long-duration stability testing still needs to be expanded.
- Multiplayer testing has not been done.

## Current Technical Stages

- Stage A: Forge 1.20.1 skeleton, Java 17, empty mod startup.
- Stage B: Core section, storage, serialization, memory storage, and mapper migration.
- Stage C: WorldEngine skeleton, chunk conversion, controlled manual and automatic ingest.
- Stage D: CPU mesh validation, model-aware mesh statistics, CPU mesh cache.
- Stage E: Debug renderer, simple GPU renderer, runtime presets, and cached LoD visibility.

## Next Recommended Steps

1. F3: Improve LoD visual correctness, including color baseline, fog, depth behavior, and near/far blending.
2. F4: Add texture, light, and biome tint support.
3. F5: Add block update dirty rebuild and cache invalidation.
4. G: Investigate Embeddium compatibility.
5. H: Investigate Oculus shader compatibility.
6. Run longer flight tests and GPU memory pressure tests.
