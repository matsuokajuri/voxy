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

## G5.9 Hardening Notes

G5.9 keeps the same no-draw boundary but makes the command buffer easier to trust before any renderer consumes it.

Current layout version:

```text
layoutVersion=G5_9_MDIC_COMMAND_V1
wordsPerCommand=12
bytesPerCommand=48
stage=G5_9_MDIC_COMMAND_BUFFER_HARDENING
```

The command field table remains:

```text
word0  sectionId
word1  geometryPtr
word2  recordStart
word3  recordCount
word4  sectionOriginX raw float bits
word5  sectionOriginY raw float bits
word6  sectionOriginZ raw float bits
word7  bucketMask
word8  metadataIndex
word9  lodLevel
word10 flags
word11 heap generation low 32 bits
```

Audit rules:

```text
command count matches CPU-side list
buffer bytes match commandCount * bytesPerCommand
CPU encode/decode round-trips through ForgeMdicCommandLayout
readback commands match CPU commands field-for-field
recordCount is non-negative
geometryPtr is 128-item aligned
bucketMask is non-zero for non-empty commands
generation matches the active upload-only GL heap
dimension matches the active client dimension
audited record total equals CPU-side commandRecords
```

The status path now reports diagnostic summaries for future G6 work:

```text
minRecordCount / maxRecordCount / avgRecordCount
nonEmptyBucketCommands / emptyBucketCommands
bucketMaskOr / bucketMaskAnd
minGeometryPtr / maxGeometryPtr
lastPlanCandidateSections / lastPlanAcceptedSections
lastInvalidLayoutCommands / lastInvalidGenerationCommands
lastInvalidDimensionCommands
lastInvalidBucketMaskCommands / lastInvalidGeometryPtrCommands
```

Lifecycle rules:

```text
heap clear
heap generation change
dimension switch
world unload
debug pipeline clear
preset off / preset clear
```

all make the MDIC command list and GL command buffer stale or cleared. `direct_gl_mdic_clear` clears the CPU command list, GL command buffer copy, and audit state without clearing the geometry heap, CPU SectionGeometryManager, BuiltSection cache, direct renderer draw data, or simple renderer buffers.

Stress rules:

```text
plan -> build buffer -> audit
clear -> rebuild -> re-audit
geometry heap clear -> re-enable upload -> rebuild -> audit
source regression through BUILT_SECTION and GL_HEAP_READBACK
return to mdic_skeleton runtime state
```

G5.9 still does not draw from the MDIC command buffer. The reason is deliberate: G6.0 should start from a command buffer whose layout, generation, dimension, readback audit, and clear/rebuild lifecycle are already proven. The G6.0 minimum entry condition is:

```text
lastAuditOk=true
lastCommandBufferMatch=true
lastLayoutMatch=true
lastGenerationMatch=true
lastDimensionMatch=true
lastInvalidCommands=0
stressFailures=0
```
