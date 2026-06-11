Voxy is an LoD rendering mod for minecraft

## Forge 1.20.1 cached LoD PoC

This branch contains an experimental Forge 1.20.1 cached LoD proof of concept. The pipeline can ingest loaded chunks, build CPU mesh data, upload simple vanilla GPU buffers, and render cached LoD color blocks outside the vanilla render-distance neighborhood.

The PoC is disabled by default and is not the final Voxy renderer. For the current renderer-migration baseline, use `/voxy preset lod_built_section` in a client world, lower Minecraft render distance to 4-6 chunks, fly through an area, then look back at chunks that have left vanilla view distance. Use `/voxy gpu_mesh_status` and `/voxy lod_visibility_status` for diagnostics.

There are two simple GPU mesh sources:

- `BUILT_SECTION` is the recommended renderer migration source. It decodes the CPU-only Voxy BuiltSection/GeometryCache path.
- `CPU_MESH` is the older Forge PoC source and remains available as a fallback with `/voxy preset lod`.

See `PORTING_NOTES.md` for the current status, test commands, limitations, and next migration steps.
