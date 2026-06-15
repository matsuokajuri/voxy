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
G6.1 minimal MDIC debug draw lifecycle hardening + stress
G6.2 optional multi / indirect MDIC debug draw prototype
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

## G6.1 Debug Draw Hardening Notes

G6.0 completed the first visible MDIC command-buffer debug draw:

```text
MDIC command GL buffer
 + upload-only GL geometry heap
 -> minimal MDIC debug shader
 -> loop-per-command glDrawArrays
 -> colored debug geometry
```

G6.1 keeps that renderer deliberately small. It does not add multi-draw, indirect MDIC draw, texture atlas access, ModelStore, shaderpack integration, or the formal `MDICSectionRenderer`. Instead it hardens the debug path around lifecycle and diagnostics.

Current debug draw stage:

```text
stage=G6_1_MDIC_DEBUG_DRAW_HARDENED
debugDrawMode=LOOP_PER_MDIC_COMMAND
formalMdicRenderer=false
voxyRenderSystem=false
shaderpack=false
```

Status now reports command and heap ownership boundaries:

```text
commandListGeneration / commandBufferGeneration / currentHeapGeneration
commandListDimension / commandBufferDimension / currentDimension
commandListStale / commandBufferStale
lastRenderSkippedReason
```

The expected skipped reasons are:

```text
DISABLED
ACTUAL_DRAW_DISABLED
HEAP_MISSING
COMMAND_LIST_MISSING
COMMAND_BUFFER_MISSING
COMMAND_LIST_STALE
COMMAND_BUFFER_STALE
DIMENSION_MISMATCH
SHADER_UNAVAILABLE
WORLD_MISSING
```

Render-state safety remains local to the debug renderer. The draw path saves and restores the GL program, VAO, array buffer, geometry SSBO binding, MDIC command SSBO binding, the direct renderer draw-item SSBO binding, depth test, depth mask, blend, and cull state. Restore failures are counted but do not crash the client.

G6.1 adds lightweight frame timing:

```text
lastFrameRenderMs
maxFrameRenderMs
avgFrameRenderMs
lastFrameOverBudget
overBudgetFrames
mdicDebugDrawFrameBudgetMs
```

The new draw stress command exercises:

```text
mdic_debug preset
plan -> build buffer -> audit
draw enable -> status
draw disable -> draw clear
geometry heap clear -> verify draw stopped
re-enable upload -> rebuild -> audit -> redraw
source regression through BUILT_SECTION and GL_HEAP_READBACK
return to mdic_debug runtime state
```

G6.1 still does not make this path a renderer replacement. G6.2 can prototype a bounded multi / indirect MDIC debug draw only after this lifecycle contract stays stable. G6.x remains the place to align toward formal renderer ownership, bucket-aware planning, material/atlas work, and original renderer compatibility.

## G6.2 Multi / Indirect Debug Draw Notes

G6.2 keeps the same ownership boundary as G6.1, but adds draw-mode variants for the MDIC debug renderer:

```text
LOOP_PER_COMMAND
MULTI_DRAW_ARRAYS
MULTI_DRAW_ARRAYS_INDIRECT
AUTO
```

`LOOP_PER_COMMAND` remains the default and the fallback. `AUTO` prefers:

```text
MULTI_DRAW_ARRAYS_INDIRECT
 -> MULTI_DRAW_ARRAYS
 -> LOOP_PER_COMMAND
```

The multi-draw path still reads the same MDIC command buffer SSBO and the same upload-only geometry heap SSBO. The only difference is command selection:

```text
loop:
  uniform commandIndex
  glDrawArrays per command

multi:
  gl_DrawIDARB selects command
  glMultiDrawArrays once for the bounded command list

indirect:
  gl_DrawIDARB selects command
  derived DrawArraysIndirectCommand buffer drives glMultiDrawArraysIndirect
```

The derived indirect command buffer is intentionally not the MDIC command buffer. It is a debug-only OpenGL standard indirect buffer generated from the audited MDIC command list:

```c
typedef struct {
    uint count;
    uint instanceCount;
    uint first;
    uint baseInstance;
} DrawArraysIndirectCommand;
```

For each MDIC command:

```text
count = recordCount * 6
instanceCount = 1
first = 0
baseInstance = command index
```

This derived buffer exists only to exercise the indirect OpenGL call shape. It does not create the original Voxy MDIC command pipeline, does not connect to `MDICSectionRenderer`, and does not make the debug renderer a formal renderer.

G6.2 status and audit commands expose:

```text
configuredDrawMode / effectiveDrawMode
multiDrawSupported / indirectSupported
drawIdSupported / baseInstanceSupported
derivedIndirectCommandBufferCreated / derivedIndirectCommandBufferBytes / stale
lastFrameApiDrawCalls / lastFrameLogicalCommands / lastFrameVertices
lastIndirectCommandBufferMatch / lastInvalidIndirectCommands
```

The derived indirect audit is command-triggered only. It readbacks the standard indirect command buffer and compares it against the CPU-side MDIC command list. It checks command count, byte size, vertex count, instance count, first, base instance, total vertices, generation, and dimension through the surrounding command list and buffer state.

Lifecycle rules remain strict. Any heap clear, heap generation change, dimension switch, world unload, debug pipeline clear, preset off, preset clear, or `direct_gl_mdic_draw_clear` invalidates or deletes the derived indirect buffer and audit state. It must not clear or pollute:

```text
G5 direct renderer draw list / indirect buffer
GL_HEAP_READBACK debug source
BUILT_SECTION source
CPU_MESH source
CPU BuiltSection cache
CPU SectionGeometryManager
```

G6.2 still does not implement the formal MDIC renderer. The next useful step is a bucket-aware MDIC debug planning pass, then grouping and lifecycle hardening before any G7-era alignment with the original renderer, materials, atlas, shaderpack, or `VoxyRenderSystem`.

## G6.3 Bucket-Aware MDIC Debug Commands

G6.3 changes the Forge MDIC debug command semantics from section-level commands to bucket-level commands:

```text
G6.2:
  one uploaded section -> one MDIC debug command
  recordStart = 0
  recordCount = section itemCount

G6.3:
  one uploaded section -> one command per included non-empty bucket
  recordStart = offsets[bucket]
  recordCount = bucketEnd - bucketStart
  bucketMask = 1 << bucket
```

The existing 12-word `ForgeMdicCommand` layout is retained, but the layout version is now:

```text
layoutVersion=G6_3_MDIC_BUCKET_COMMAND_V1
stage=G6_3_BUCKET_AWARE_MDIC_DEBUG_DRAW
```

Bucket meanings follow the Voxy `RenderDataFactory` output and the original `cmdgen.comp` command generation shape:

```text
bucket 0: translucent
bucket 1: double-sided
bucket 2..7: directional face buckets
```

By default, G6.3 includes:

```text
double-sided bucket 1
directional buckets 2..7
```

and skips:

```text
translucent bucket 0
```

The translucent bucket is skipped because this debug renderer does not implement translucent sorting or the original translucent command generation path. This keeps the command list deterministic and bounded while still proving that the MDIC command buffer can address sub-section geometry ranges.

The planner keeps the existing camera/radius section selection, then expands each accepted section into up to eight bucket commands. Empty buckets are not emitted. Budget limits apply to both records and commands:

```text
mdicCommandMaxSections=16
mdicCommandMaxCommands=128
mdicCommandMaxCommandsPerSection=8
mdicCommandMaxRecords=32768
```

When the record budget cannot fit a whole bucket, G6.3 skips that bucket rather than truncating it. That keeps audit rules strict:

```text
recordStart == decodedMetadata.offsets[bucket]
recordCount == decoded bucket length
recordStart + recordCount <= itemCount
bucketMask is a single bit
empty buckets do not create commands
skipped translucent buckets are counted
```

The draw paths from G6.2 are preserved:

```text
LOOP_PER_COMMAND
MULTI_DRAW_ARRAYS
MULTI_DRAW_ARRAYS_INDIRECT
AUTO
```

The derived OpenGL `DrawArraysIndirectCommand` buffer still contains one standard indirect command per Forge MDIC debug command:

```text
count = bucketCommand.recordCount * 6
instanceCount = 1
first = 0
baseInstance = MDIC command index
```

This makes the indirect draw path bucket-aware without changing it into the original Voxy MDIC renderer. The debug shader still reads the Forge MDIC command buffer SSBO and the upload-only geometry heap SSBO. It now also tints debug color by `bucketMask`, but it still does not sample textures, use ModelStore, apply real material state, or integrate with shaderpacks.

Relationship to original Voxy:

```text
similar:
  bucket-level command grouping from section metadata offsets
  draw commands reference geometryPtr + bucket-local record ranges

not yet similar:
  no GPU hierarchical traversal
  no HiZ occlusion
  no directional camera face-mask filtering
  no translucent sort
  no DrawElementsIndirect / indexed shared quad buffer
  no MDICSectionRenderer / VoxyRenderSystem
```

G6.4 candidates:

```text
directional face-mask filtering based on camera relative section position
frustum / visibility planning alignment
formal command layout closer to DrawElementsIndirect
RenderDataFactory / ModelStore / texture metadata bridge
```

G6.3 remains a debug renderer and command-buffer migration checkpoint. It should not be treated as a formal renderer replacement.

## G6.4 Directional Face-Mask MDIC Bucket Commands

G6.4 keeps the G6.3 bucket-aware command layout, but adds conservative directional filtering for buckets 2..7 during CPU-side debug command planning:

```text
layoutVersion=G6_4_MDIC_BUCKET_FACE_MASK_COMMAND_V1
stage=G6_4_DIRECTIONAL_FACE_MASK_MDIC_DEBUG_DRAW
```

The bucket semantics remain aligned with the current Forge metadata offsets and the original Voxy `cmdgen.comp` shape:

```text
bucket 0: translucent
bucket 1: double-sided
bucket 2: directional_y_minus
bucket 3: directional_y_plus
bucket 4: directional_z_minus
bucket 5: directional_z_plus
bucket 6: directional_x_minus
bucket 7: directional_x_plus
```

Planning still skips translucent bucket 0 by default and keeps double-sided bucket 1 when present. For directional buckets, the planner compares the current camera position against the decoded uploaded section AABB:

```text
cameraY < sectionMinY -> bucket 2
cameraY > sectionMaxY -> bucket 3
cameraZ < sectionMinZ -> bucket 4
cameraZ > sectionMaxZ -> bucket 5
cameraX < sectionMinX -> bucket 6
cameraX > sectionMaxX -> bucket 7
```

When the camera is inside an axis range, the planner conservatively includes both directions for that axis. When the camera is inside the full section AABB, the default behavior is to keep all directional buckets:

```text
mdicCommandDirectionalFaceMask=true
mdicCommandDirectionalFaceMaskFallbackAllWhenInside=true
```

If camera or AABB data is unavailable, G6.4 also falls back to all directional buckets and reports the reason in status:

```text
faceMaskFallbackReason=MISSING_CAMERA / MISSING_AABB / INSIDE_SECTION / EMPTY_MASK / mixed / none
```

The audit path now checks that generated directional bucket commands obey the active face mask unless a conservative fallback-all reason applies. Status and audit expose:

```text
directionalFaceMask
faceMaskCommandsAccepted
faceMaskCommandsRejected
rejectedDirectionalBuckets
insideSectionFallbacks
missingCameraFallbacks
missingAabbFallbacks
bucket2RejectedByFaceMask .. bucket7RejectedByFaceMask
invalidFaceMaskCommands
lastFaceMaskAuditOk
```

This is closer to the original Voxy command generation idea because directional buckets can now be omitted when they face away from the camera. It is still deliberately smaller than the original renderer:

```text
no occlusion culling
no LOD traversal
no frustum hierarchy
no translucent sorting
no texture atlas / ModelStore
no MDICSectionRenderer / VoxyRenderSystem
```

G6.5 candidates:

```text
frustum/radius visibility alignment
bucket command stats hardening
formal DrawElementsIndirect layout design
RenderData / ModelStore bridge
```

G6.4 remains a debug renderer checkpoint. It proves command grouping and conservative face-mask filtering without changing ownership or calling the formal Voxy render path.

## G6.5 Visibility-Aware MDIC Command Planning

G6.5 keeps the G6.4 bucket-aware and directional face-mask command layout, but changes how uploaded sections are selected before bucket commands are generated:

```text
stage=G6_5_VISIBILITY_AWARE_MDIC_DEBUG_DRAW
layoutVersion=G6_4_MDIC_BUCKET_FACE_MASK_COMMAND_V1
```

The layout words are unchanged. The new work is planning policy and diagnostics:

```text
uploaded sections
 -> selectionMode=AUTO
 -> effectiveSelectionMode=FRUSTUM_RADIUS / RADIUS / NEAREST_CAMERA / FIRST_N
 -> bucket-aware commands
 -> directional face-mask filtering
 -> loop / multi / indirect debug draw
```

The default runtime preset values are conservative but larger than G6.4:

```text
mdicCommandSelectionMode=AUTO
mdicCommandRenderDistanceChunks=16
mdicCommandMaxPlanCandidates=512
mdicCommandMaxSections=32
mdicCommandMaxCommands=256
mdicCommandMaxRecords=65536
mdicCommandUseFrustum=true
mdicCommandFrustumFallbackToRadius=true
```

The current Forge command path can reliably capture camera position, chunk, and section at command planning time. It does not yet have a reliable frustum snapshot without adding renderer-specific integrations, mixins, or Sodium/Iris hooks. Therefore the expected default fallback is:

```text
selectionMode=AUTO
frustumAvailable=false
effectiveSelectionMode=RADIUS
selectionFallbackReason=FRUSTUM_UNAVAILABLE
```

If a reliable frustum snapshot is added later, the same planning fields can report:

```text
frustumAvailable=true
effectiveSelectionMode=FRUSTUM_RADIUS
rejectedByFrustum>0 when applicable
```

Status and audit now expose the selection evidence:

```text
cameraPosition
cameraChunk
cameraSection
renderDistanceChunks
candidateSections
acceptedSections
rejectedByRadius
rejectedByFrustum
rejectedByBudget
rejectedByMissingMetadata
nearestAcceptedDistance
farthestAcceptedDistance
maxPlanCandidates
maxSections
maxCommands
maxRecords
lastSelectionAuditOk
invalidSelectionCommands
invalidRadiusCommands
invalidFrustumCommands
```

The audit is intentionally tied to the CPU-side command list built during planning. It verifies that generated commands belong to the accepted selection result and that radius/frustum mode metadata is self-consistent. It does not recalculate command visibility from a later camera position, because command buffers may be audited after the camera moves.

Relationship to original Voxy visibility systems:

```text
similar:
  command planning is now camera/radius aware
  bucket and face-mask filtering remain command-generation concerns
  status explicitly distinguishes desired mode from effective fallback mode

not yet similar:
  no HierarchicalOcclusionTraverser
  no RenderDistanceTracker integration
  no HiZ occlusion
  no full LOD traversal
  no frustum hierarchy or renderer-owned view snapshot
```

G6.6 candidates:

```text
formal DrawElementsIndirect layout design
visibility stats hardening
LOD-level metadata skeleton
RenderData / ModelStore bridge
```

G6.5 remains a debug renderer checkpoint. It improves which commands are generated and how that choice is audited, without making the Forge PoC a formal MDIC renderer.
