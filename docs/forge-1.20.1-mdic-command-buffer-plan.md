# Forge 1.20.1 MDIC Command Buffer Plan

## Current G5 Direct Debug Renderer Chain

The current Forge migration path is intentionally narrow and debug-only:

```text
CPU SectionGeometryManager
 -> upload-only GL geometry heap
 -> direct debug draw list
 -> draw item SSBO
 -> indirect command buffer
 -> glMultiDrawArraysIndirect debug draw
```

The direct debug renderer proves that packed Voxy-like geometry records can be vertex-pulled from the upload-only heap. It still uses a Forge-only debug shader and does not enter the original Voxy renderer pipeline.

## Future MDIC Target

The next alignment target is a command-buffer boundary that can eventually feed an MDIC-style renderer:

```text
CPU SectionGeometryManager
 -> GL geometry heap + metadata buffer
 -> MDIC-compatible section command planning
 -> command buffer / draw command buffer
 -> later renderer-side execution
```

G5.8 does not draw from this command buffer. It does not connect `MDICSectionRenderer`, `VoxyRenderSystem`, shaderpack paths, texture atlas upload, or ModelStore. It only establishes layout, planning, upload, readback audit, and lifecycle rules.

## G5.7 Indirect Debug Command vs MDIC Skeleton Command

G5.7 uses the OpenGL `DrawArraysIndirectCommand` layout:

```text
uint count
uint instanceCount
uint first
uint baseInstance
```

That command is enough for `glMultiDrawArraysIndirect`, but it does not describe Voxy section metadata directly. A later MDIC-aligned command needs to carry or reference section-level data:

```text
section id
geometry ptr
bucket offsets or bucket mask
record count
visibility / LoD flags
section origin
metadata index
command generation
draw mode / bucket mask
```

## G5.8 Skeleton Layout

The current Forge skeleton uses a fixed 12-word layout named `ForgeMdicCommand`. It is a debug alignment layout, not a final original Voxy command ABI:

```text
word0  sectionId
word1  geometryPtr
word2  recordStart
word3  recordCount

word4  sectionOriginX as raw float bits
word5  sectionOriginY as raw float bits
word6  sectionOriginZ as raw float bits
word7  bucketMask

word8  metadataIndex
word9  lodLevel
word10 flags
word11 generation low 32 bits
```

`geometryPtr` is still an item pointer into the upload-only GL geometry heap. The command buffer does not own the heap. `recordStart` is relative to `geometryPtr`, and G5.8 currently plans from the start of each section. `bucketMask` is derived from the decoded 8-bucket metadata offsets.

The geometry records referenced by this command still use `geometryFormat=partial-original-bit-layout`, `finalFormat=false`.

## Relationship To Original Voxy Renderer

This is not `MDICSectionRenderer`.
This is not `VoxyRenderSystem`.
This is not the shaderpack renderer.

The layout is a Forge 1.20.1 debug skeleton that makes the next boundary explicit. G6 can decide how much to reshape this skeleton toward the original renderer structures, including bucket-aware draw planning, command compaction, and real renderer ownership.

The current path still has no texture atlas, ModelStore, shaderpack integration, Embeddium/Oculus/Sodium/Iris integration, or mixins.

## Lifecycle And Ownership

The upload-only GL geometry heap remains owned by `ForgeGpuGeometryUploadManager`.

The MDIC skeleton owns only:

```text
CPU-side ForgeMdicCommand list
GL command buffer copy of that list
audit status
```

The MDIC skeleton references:

```text
geometryPtr
section metadata index
heap generation
dimension id
```

If the heap is cleared, recreated, or the dimension/world changes, the command list and GL command buffer must be considered stale or cleared. All GL upload/readback work is render-thread only. `direct_gl_mdic_clear` clears only the skeleton list/buffer/audit state and does not clear CPU BuiltSection cache, CPU SectionGeometryManager, the upload-only GL heap, or simple renderer buffers.

## Follow-Up Route

```text
G5.9 MDIC command buffer CPU/GL audit + stress
G6.0 minimal MDIC debug draw using MDIC command buffer
G6.1 bucket-aware draw planning
G6.2 renderer lifecycle hardening
G6.x original renderer alignment
```
