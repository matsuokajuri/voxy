# Forge 1.20.1 Direct GL Heap Renderer Plan

This note captures the G5 renderer boundary before any direct draw code is added. The current branch already proves the data path up to an upload-only GL heap and several debug readback paths. G5.0 keeps that line intact and adds only design plus no-op skeleton interfaces.

## A. Current Data Path

```text
World chunk ingest
 -> CPU-only BuiltSection / ForgeVoxyGeometryCache
 -> CPU SectionGeometryManager
 -> upload/remove/metadata intents
 -> upload-only GL geometry heap
 -> validate / audit / stress
 -> GL_HEAP_READBACK simple debug source
```

The current visible LoD/debug path still renders through the independent simple Forge renderer. The upload-only heap is validated by readback and can feed a debug mesh source, but it is not used for direct drawing.

## B. G5 Direct GL Renderer Goal

The later G5 renderer line is intended to be:

```text
CPU SectionGeometryManager
 -> upload-only GL heap
 -> direct GL renderer reads geometry + metadata GPU buffers
 -> draw debug colored quads
 -> later evolve toward MDIC / original renderer alignment
```

G5.0 does not draw. It only introduces the ownership boundary, status reporting, and a no-op planning step that confirms candidate sections can be selected from the uploaded metadata.

## C. Current GL Buffer Contents

Geometry buffer:

- One 8-byte packed quad record per geometry item.
- The item unit is 8 bytes.
- Geometry pointers are item offsets, not byte offsets.
- Current allocation aligns geometry pointers to 128 items.

Metadata buffer:

- One 32-byte metadata entry per section id.
- Each entry is 8 int words.

Metadata word layout:

| Word | Meaning |
| --- | --- |
| 0 | section position high 32 bits |
| 1 | section position low 32 bits |
| 2 | packed AABB |
| 3 | geometry pointer in 8-byte items |
| 4 | offsets delta 0 and 1 |
| 5 | offsets delta 2 and 3 |
| 6 | offsets delta 4 and 5 |
| 7 | offsets delta 6 and 7 |

Packed quad record layout:

| Bits | Meaning |
| --- | --- |
| 0..2 | face |
| 3..6 | length - 1 |
| 7..10 | width - 1 |
| 11..15 | local z |
| 16..20 | local y |
| 21..25 | local x |
| 26..41 | CPU-only placeholder client model id |
| 42..45 | unused gap |
| 46..54 | mapper biome id |
| 55..62 | light id |
| 63 | unused |

Offsets use the original Voxy bucket order:

| Index | Bucket |
| --- | --- |
| 0 | translucent |
| 1 | double_sided |
| 2 | directional_y_minus |
| 3 | directional_y_plus |
| 4 | directional_z_minus |
| 5 | directional_z_plus |
| 6 | directional_x_minus |
| 7 | directional_x_plus |

The current geometry format is `partial-original-bit-layout`; `finalFormat=false`. Model ids are CPU-only placeholders and no texture atlas or ModelStore is connected.

## D. Minimal Direct Draw Route Options

1. Shader vertex pulling from geometry and metadata buffers

   A small debug shader would derive quad vertices from packed records and section metadata on the GPU. This keeps geometry resident in the uploaded heap and is the shortest path to proving direct heap drawing. It does require introducing a renderer-owned shader and bindings in a later stage.

2. CPU-built draw command list with geometry staying on GPU

   The CPU could prepare a small visible section list while the shader still pulls geometry from GPU buffers. This is useful before MDIC because it limits the section set without building full indirect commands.

3. Full MDIC path

   This matches the original Voxy direction more closely, but it pulls in indirect command buffers, culling, and shader/pipeline complexity. It should stay behind the initial direct debug shader.

Recommended first visible step for G5.1:

```text
small debug shader + vertex pulling + limited section set
```

G5.0 intentionally stops before this step.

## E. Renderer Ownership

- `ForgeGpuGeometryUploadManager` and `ForgeGpuGeometryHeap` own GL heap creation, upload, validation, clear, and release.
- The future direct renderer must not own the geometry or metadata buffers.
- The direct renderer should hold only a snapshot descriptor or safe reference to the heap owner.
- World unload, dimension switch, upload clear, and debug pipeline clear invalidate renderer planning state.
- All GL work must stay on the render thread.
- No non-render-thread path may create, bind, read, or draw GL objects.

## F. Relationship To Existing Debug Sources

- `CPU_MESH` remains the legacy Forge PoC/fallback simple GPU source.
- `BUILT_SECTION` remains the recommended renderer migration simple GPU source.
- `GL_HEAP_READBACK` remains the debug source that reads back heap data and feeds the simple VertexBuffer renderer.
- The direct GL renderer has a separate runtime flag and command set.
- It is not added to `SimpleGpuMeshSource`.
- A later preset can use `direct_gl_debug`, but in G5.0 that preset is skeleton-only and emits no draw.

## G. Follow-Up Route

```text
G5.1 minimal direct GL debug shader skeleton + one-section draw
G5.2 multi-section draw list + frustum/radius limit
G5.3 buffer lifecycle + generation safety
G5.4 replace readback debug source in selected path
G6.x MDIC / original renderer alignment
```

G5.1 should still avoid MDIC and original VoxyRenderSystem. It should prove one controlled direct draw path first, then grow section count and lifecycle safety.
