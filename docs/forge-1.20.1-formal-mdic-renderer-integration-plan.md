# Forge 1.20.1 formal MDIC renderer integration readiness

This document audits the Forge 1.20.1 PoC after G6.7 against the original Voxy
formal renderer. It is intentionally a readiness document, not a renderer
implementation plan that should be executed in one jump.

G6.7 reached a debug renderer shape that can build bucket-aware, visibility-aware
commands, derive indexed indirect commands, upload a CPU-written draw count
buffer, and issue `glMultiDrawElementsIndirectCountARB`. That is close to the
draw-call form used by the original renderer, but it is still missing several
formal renderer inputs and ownership contracts.

## Current Forge PoC readiness

The current Forge path already proves these pieces:

- Upload-only GL geometry heap exists and is audited.
- Metadata buffer exists and has readback validation.
- Packed quad record layout matches the current debug decode path.
- Section metadata carries bucket offsets that can be used for bucket commands.
- MDIC command skeleton exists with CPU command list, GL command buffer, audit,
  clear, and stress commands.
- Bucket-aware command planning splits a section into non-empty bucket commands.
- Directional face-mask filtering rejects directional buckets based on camera
  relative position.
- Visibility/radius command selection exists, with explicit fallback when
  frustum data is unavailable.
- DrawArrays debug paths still exist: loop, multi draw, and indirect.
- DrawElementsIndirect-compatible debug path exists.
- Draw count buffer path exists for `glMultiDrawElementsIndirectCountARB`.
- Debug shader can draw colored geometry from the geometry heap and command
  buffer.
- Lifecycle clear, heap clear, dimension switch, source regression, and stress
  have been exercised for the debug path.

These items make the PoC useful for validating command contents, buffer lifetime,
and draw-call state hygiene. They do not make it a formal renderer.

## Missing formal renderer conditions

The formal renderer still needs the following before it can replace or align with
the original Voxy renderer:

- `ModelStore` equivalent for model metadata, colour data, texture storage, and
  sampler ownership.
- `ModelFactory` / `ModelBakerySubsystem` bridge for block state and fluid state
  to model ids and model metadata.
- Texture atlas allocation and upload.
- `modelData` buffer matching the shader's expected model record layout.
- `modelColour` buffer and biome tint path.
- Real material and UV lookup.
- Lightmap and biome colour integration.
- Formal shader pipeline using the real model/texture inputs.
- Resource reload handling for models, textures, shaders, and dependent buffers.
- Formal renderer ownership and shutdown contract.
- Forge/Minecraft render-stage contract for a formal far renderer.
- Viewport, camera, projection, frustum, and framebuffer ownership.
- HiZ or other occlusion traversal.
- Full LOD node traversal and render-distance tracking.
- Translucent handling and sorting.
- Formal command generation ownership, including which stage owns command count
  generation and visibility results.

## Original Voxy formal renderer dependencies

The original renderer is rooted in
`src/main/java/me/cortex/voxy/client/mixin/minecraft/MixinLevelRenderer.java`.
That mixin creates and shuts down `VoxyRenderSystem` from vanilla level renderer
events such as level changes, renderer rebuild, and close. The Forge PoC must not
copy this directly because G6 work explicitly avoids mixins and Sodium/Iris
integration.

`src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java` owns the formal
renderer lifecycle. Its constructor wires `WorldEngine`, `ModelBakerySubsystem`,
`RenderGenerationService`, `BasicSectionGeometryData`, `BasicAsyncGeometryManager`,
`HierarchicalOcclusionTraverser`, `RenderDistanceTracker`, `ViewportSelector`,
and the section renderer backend. It also owns shutdown ordering for geometry,
model, traversal, pipeline, viewport, and world references.

`src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java`
is the formal MDIC backend. It binds the geometry buffer, section metadata buffer,
model buffers, model colour buffer, texture atlas, lightmap, scratch buffers,
element index buffer, draw command buffer, and draw count buffer. Its terrain path
uses `glMultiDrawElementsIndirectCountARB`.

`src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport.java`
owns per-viewport command resources: draw command buffer, draw count buffer,
position scratch buffer, indirect lookup buffer, and visibility buffer.

`src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicSectionGeometryData.java`
and
`src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicAsyncGeometryManager.java`
own section metadata and geometry allocation. The original metadata is compact:
position words, packed AABB, geometry pointer plus bucket offsets, offset deltas,
and child existence. Geometry allocations are aligned to 128 records.

`src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java`
builds the eight geometry buckets used by command generation:

- bucket 0: translucent
- bucket 1: double-sided
- buckets 2..7: directional faces

It depends on model metadata from `ModelFactory` and produces packed quad records
that are later vertex-pulled by shaders.

`src/main/java/me/cortex/voxy/client/core/model/ModelStore.java`,
`src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java`, and
`src/main/java/me/cortex/voxy/client/core/model/ModelBakerySubsystem.java` form the
model and texture side of the renderer. They provide model ids, model metadata,
texture atlas uploads, biome colour data, and render-layer/material metadata.

`src/main/java/me/cortex/voxy/client/core/rendering/hierachical/HierarchicalOcclusionTraverser.java`
and
`src/main/java/me/cortex/voxy/client/core/rendering/RenderDistanceTracker.java`
provide formal visibility, render-distance, traversal, and queueing behavior. The
Forge PoC currently has radius/fallback selection, not this traversal system.

The shader files that define the formal GPU contract include:

- `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl`
- `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp`
- `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl`
- `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl`
- `src/main/resources/assets/voxy/shaders/lod/section.glsl`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag`

`bindings.glsl` defines the indexed `DrawCommand` layout, draw count buffer,
section metadata buffer, model buffers, visibility buffer, and indirect lookup
buffer bindings. `cmdgen.comp` generates formal draw commands from visibility and
section metadata. `quad_util.glsl`, `quads3.vert`, and `quads.frag` consume model,
colour, atlas, light, and material inputs that the debug shader does not yet have.

## Forge PoC versus original renderer

| Original Voxy component | Current Forge PoC counterpart | Status | Gap | Next step |
| --- | --- | --- | --- | --- |
| Geometry heap | Upload-only Forge GL geometry heap | Debug-ready | Ownership is upload-manager only, not formal renderer-owned | Keep ownership separate; document bridge contract |
| Metadata buffer | Forge upload metadata buffer | Debug-ready | Original compact metadata layout is only partially mirrored | Audit exact word layout before formal shader use |
| Section id allocator | Forge uploaded section tracking | Partial | Original uses `HierarchicalBitSet` section ids and node hierarchy | Add section-id ownership audit before formal renderer |
| Geometry ptr allocator | Forge heap allocation pointer | Mostly aligned | Need confirm 128-record alignment and lifetime across rebuilds | Keep G4/G5 readback audits as guardrail |
| Bucket offsets | Forge metadata offsets and bucket commands | Aligned for debug | Translucent and directional semantics are debug-consumed only | Preserve bucket commands; defer translucent sorting |
| DrawCommand | Debug derived indexed indirect command buffer | Draw-call compatible | Formal `DrawCommand` is generated from GPU visibility/cmdgen | Separate formal DrawCommand layout from debug command layout |
| Draw count buffer | CPU-written debug draw count buffer | Debug-ready | Original count buffer is part of GPU command generation pipeline | Keep CPU count for debug; design GPU count later |
| Shared index buffer | Forge MDIC debug shared index buffer | Debug-ready | Original uses formal shared index buffer and shader vertex pulling | Confirm index type and vertex-id assumptions |
| Shader vertex pulling | Forge debug shader reads quad records | Partial | Missing model, atlas, UV, colour, material, light paths | Build ModelStore/atlas bridge before formal draw |
| ModelStore | None in Forge formal path | Missing | No real model metadata, colour buffer, atlas, sampler | P0 blocker: add audit/skeleton bridge |
| Texture atlas | None | Missing | No texture allocation/upload or resource reload | P0 blocker with ModelStore |
| Visibility traversal | Radius/fallback selection | Partial | No HiZ traversal, render queues, visibility buffer, top-node tracking | Add formal traversal skeleton only after ownership is clear |
| LOD selection | Not implemented beyond selected uploaded sections | Missing | No node hierarchy traversal or LOD selection | P1/P2 depending on formal renderer scope |
| Renderer lifecycle | Debug command clear and heap lifecycle | Partial | No formal ownership for resource reload, viewport, framebuffer, model resources | Define no-draw formal bridge lifecycle |
| Resource reload | Debug shader/resource state only | Missing | Model/texture/shader reload not wired | P0 before formal textured renderer |

## Formal renderer blockers

### P0 blockers

- Real `ModelStore` and texture atlas are missing. The formal shaders cannot draw
  real terrain without model metadata, model colour data, texture storage, and
  sampler binding.
- `ModelFactory` / `ModelBakerySubsystem` bridge is missing. The current Forge
  PoC has placeholder/debug model ids, not the original model bake pipeline.
- Formal shader inputs are incomplete. The debug shader only needs command and
  geometry buffers; the original shader requires model buffers, atlas, lightmap,
  biome/tint/material metadata, and position scratch behavior.
- Renderer ownership is undefined. A formal Forge renderer needs clear ownership
  for lifecycle, world unload, dimension switch, resource reload, viewport,
  framebuffer, and command generation.
- Resource reload lifecycle is undefined. Model, texture, shader, atlas, and
  GPU-buffer invalidation must be specified before a formal renderer can be safe.

### P1 blockers

- Visibility is still a debug planner with radius/frustum fallback. It is not the
  original `HierarchicalOcclusionTraverser` plus visibility buffer path.
- LOD traversal is not implemented. Current commands are planned from uploaded
  sections, not from formal node traversal.
- Translucent handling is intentionally skipped or simplified. Original renderer
  has separate translucent command generation and draw ranges.
- Formal DrawCommand ownership is not separated enough from the debug MDIC command
  layout. The PoC currently derives OpenGL commands from debug CPU commands.

### P2 blockers

- Embeddium/Oculus/Iris/shaderpack integration is intentionally absent.
- Advanced occlusion and HiZ performance tuning can wait until the renderer has
  formal inputs and lifecycle.
- Performance tuning and batching heuristics should wait until the real
  ModelStore and formal shader path exist.
- Formal renderer metrics can be expanded after the bridge skeleton exists.

## Readiness matrix

| Check | Ready | Notes |
| --- | --- | --- |
| geometryHeapReady | true | Upload-only GL heap is validated and audited. |
| metadataBufferReady | true | Metadata buffer exists, but original compact layout still needs exact alignment audit. |
| mdicCommandBufferReady | true | Debug MDIC command buffer exists and is audited. |
| drawCountBufferReady | true | CPU-written debug count buffer exists. |
| modelStoreReady | false | No Forge formal `ModelStore` bridge yet. |
| textureAtlasReady | false | No formal atlas upload/reload path. |
| formalShaderReady | false | Debug shader only; original formal shader inputs are missing. |
| visibilityTraversalReady | false | Debug radius/frustum fallback only, no original traverser. |
| resourceReloadReady | false | Formal model/texture/shader reload contract missing. |
| rendererOwnershipReady | false | No formal Forge renderer ownership contract. |
| formalRendererReady | false | P0 blockers remain. |

## Candidate next steps

### 1. G6.9 RenderDataFactory / ModelStore bridge audit + minimal skeleton

Why: the largest formal-renderer blocker is not the draw call anymore; it is the
missing real model/texture/material input path. Without this bridge, a formal
shader cannot move beyond colored debug geometry.

Likely classes: add Forge-side audit/skeleton classes around model id ownership
and inspect compatibility with `RenderDataFactory`, `ModelStore`, `ModelFactory`,
and `ModelBakerySubsystem`. Do not upload a real atlas yet unless the audit shows
a narrow safe path.

Risk: model baking and resource reload have wide lifecycle impact. Keep the first
step no-draw or audit-only.

Verification: compile plus command/status checks showing
`modelStoreReady=false` until a real bridge exists; no debug renderer regression.

Formal renderer proximity: high. This addresses a P0 input blocker.

### 2. G6.9 formal DrawCommand layout separation

Why: the G6.7 debug path uses DrawElementsIndirect-compatible commands, but they
are derived from Forge debug MDIC commands. Formal integration should make the
OpenGL `DrawCommand` and draw count buffer a clearly separate layer, matching
`bindings.glsl` and `cmdgen.comp`.

Likely classes: split or document `ForgeMdicDebugElementsIndirectCommandBuffer`,
`ForgeMdicDebugDrawCountBuffer`, and the MDIC command layout so formal command
buffers are not conflated with debug command buffers.

Risk: low to medium. The main danger is disrupting the debug draw paths.

Verification: existing loop/multi/indirect/elements/count debug audits remain
green; new formal layout status remains no-draw.

Formal renderer proximity: high. It reduces conceptual mismatch with the original
MDIC backend.

### 3. G6.9 formal renderer bridge skeleton / no draw

Why: ownership and lifecycle are P0 blockers. A no-draw bridge can define what a
Forge formal renderer would own and which existing debug resources remain separate.

Likely classes: `ForgeFormalMdicRendererReadiness`,
`ForgeFormalMdicRendererBridge`, or similar no-op status classes. It must not add
a new formal render hook or issue formal draws.

Risk: medium. Even a no-op bridge can confuse users if status implies readiness.
Every status must keep `formalRendererReady=false`.

Verification: compile and command smoke only; ensure no actual formal draw path is
called.

Formal renderer proximity: medium to high. It clarifies lifecycle before
integration.

### 4. G6.9 visibility / LOD skeleton

Why: original Voxy relies on `HierarchicalOcclusionTraverser` and
`RenderDistanceTracker`, while Forge currently uses radius/frustum fallback.

Likely classes: Forge visibility status/snapshot/planner skeletons that separate
debug selection from future formal traversal.

Risk: medium. Implementing traversal too early can distract from the missing
model/texture P0 inputs.

Verification: planning audits prove fallback behavior; no HiZ or full traversal
yet.

Formal renderer proximity: medium. It is important, but less blocking than
ModelStore and formal shader inputs.

### 5. Resource reload lifecycle contract

Why: formal model/texture/shader resources must survive reloads, world unloads,
and dimension changes safely.

Likely classes: no-draw lifecycle audit/status around resource reload hooks and
current debug clear paths.

Risk: medium. Forge resource reload integration must be careful and narrow.

Verification: no-draw status plus existing debug lifecycle tests.

Formal renderer proximity: high for safety, but best after deciding the ModelStore
bridge shape.

## Final recommendation

The next most useful step is a `RenderDataFactory` / `ModelStore` bridge audit and
minimal no-draw skeleton. The draw-call side has enough proof from G6.7; the
formal renderer cannot advance safely without real model metadata, atlas
ownership, and shader input contracts.

The second priority should be formal `DrawCommand` layout separation so the debug
MDIC command list, OpenGL indexed indirect command buffer, and future formal
command-generation buffers have separate names, ownership, and audit status.

Do not directly wire `MDICSectionRenderer` or `VoxyRenderSystem` yet. Their
dependencies include mixins, renderer pipeline ownership, model bakery services,
resource reload behavior, visibility traversal, and shaderpack/Sodium/Iris
assumptions that are not present in this Forge PoC.

Useful original code to reference now:

- `RenderDataFactory` for bucket and quad-record production.
- `ModelStore`, `ModelFactory`, and `ModelBakerySubsystem` for the model/atlas
  bridge design.
- `bindings.glsl`, `cmdgen.comp`, `quad_format.glsl`, and `quad_util.glsl` for
  formal buffer and shader contracts.
- `MDICSectionRenderer` and `MDICViewport` for command/draw-count buffer shape and
  binding order.
- `VoxyRenderSystem` for lifecycle ordering, not for direct copying.

Original code that should not be copied directly yet:

- `MixinLevelRenderer`, because this Forge branch is intentionally no-mixin.
- `MDICSectionRenderer.renderTerrain(...)`, because the formal model/texture and
  visibility dependencies are not ready.
- `HierarchicalOcclusionTraverser`, because HiZ/full traversal is outside the
  current scope.
- Iris/Sodium/shaderpack integration paths, because they are explicitly excluded.

## K1 formal terrain renderer owner note

K1 adds a no-draw formal terrain renderer owner. It is a boundary and lifecycle
holder, not the formal MDIC renderer. It records which future resources belong
under the formal terrain renderer:

- formal viewport owner,
- formal command buffer and draw-count owner,
- formal visibility/render-list owner,
- formal draw pipeline owner,
- global formal model-id geometry path,
- formal terrain shader integration.

All of those remain missing in K1 and are reported as blockers. K1 may reference
the formal ModelStore, model lifecycle, shader validation, J5 terrain record
bridge, GL geometry heap, and section geometry manager readiness, but it must
not use the debug MDIC command buffers or J-stage preview buffers as the formal
renderer.

Do not wire `MDICSectionRenderer.renderTerrain(...)` after K1. The next safe
step is to define formal viewport/command/visibility ownership and audit their
cleanup order while keeping live terrain draw disabled.

## K2 formal MDIC viewport ownership note

K2 defines the formal ownership shell for the original Voxy MDIC viewport-side
resources without using the debug MDIC command buffers as formal resources.

Original alignment:

- `MDICViewport.drawCallBuffer` maps to the formal draw command buffer owner.
- `MDICViewport.drawCountCallBuffer` maps to the formal draw count / parameter
  buffer owner.
- `MDICViewport.visibilityBuffer` maps to the formal visibility owner.
- `MDICViewport.indirectLookupBuffer` maps to the formal render-list / indirect
  lookup owner.
- `MDICViewport.positionScratchBuffer` maps to the formal position scratch owner.

Intentional Forge deviation:

- K2 is logical-only and no-draw.
- K2 does not allocate live formal GL buffers yet.
- K2 does not run `cmdgen.comp`.
- K2 does not call `glMultiDrawElementsIndirectCountARB`.
- K2 does not call `MDICSectionRenderer` or `VoxyRenderSystem`.

The next safe work is command-generation ownership and visibility traversal
design. Live draw remains out of scope until formal command resources are
actually populated by a formal producer and consumed by a formal shader path.

## K3 formal command generation ownership note

K3 defines the formal producer contract for the K2 command resources. It aligns
with original Voxy `cmdgen.comp` without running the compute shader:

- `DRAW_BUFFER_BINDING = 1` maps to the K2 formal draw command buffer owner.
- `DRAW_COUNT_BUFFER_BINDING = 2` maps to the K2 formal draw count / parameter
  buffer owner.
- `SECTION_METADATA_BUFFER_BINDING = 3` maps to formal section metadata input.
- `VISIBILITY_BUFFER_BINDING = 4` maps to the K2 formal visibility owner.
- `INDIRECT_SECTION_LOOKUP_BINDING = 5` maps to the K2 render-list / indirect
  lookup owner.
- `POSITION_SCRATCH_BINDING = 6` maps to the K2 position scratch owner.

The formal `DrawCommand` layout is recorded as five 32-bit fields:

```text
count
instanceCount
firstIndex
baseVertex
baseInstance
```

Intentional Forge deviation:

- K3 is logical-only and no-draw.
- K3 does not allocate a command-generation GPU program.
- K3 does not dispatch `cmdgen.comp`.
- K3 does not bind the formal renderer draw pipeline.
- K3 does not call `glMultiDrawElementsIndirectCountARB`.
- K3 does not call `MDICSectionRenderer` or `VoxyRenderSystem`.

After K3, the missing piece is no longer "who owns command generation"; it is
operational command generation fed by formal visibility traversal and global
formal model-id geometry. Live terrain draw remains blocked.

## K4 formal visibility/render-list ownership note

K4 defines the formal owner for the inputs that original Voxy traversal provides
to `MDICViewport` and `cmdgen.comp`:

- formal visibility buffer contract,
- formal render-list / indirect lookup contract,
- formal section candidate source status,
- provisional CPU candidate snapshot status,
- explicit separation from debug command planners.

Original alignment:

- `HierarchicalOcclusionTraverser` writes section candidates through
  `Viewport.getRenderList()`.
- `MDICViewport.getRenderList()` returns the indirect lookup buffer.
- `cmdgen.comp` consumes `sectionCount`, `indirectLookup[]`, and
  `visibilityData[sectionId]`.

Intentional Forge deviation:

- K4 does not port hierarchical occlusion traversal yet.
- K4 may report a CPU candidate snapshot, but marks it provisional.
- K4 does not run `cmdgen.comp`.
- K4 does not populate live formal draw command buffers.
- K4 does not call `glMultiDrawElementsIndirectCountARB`,
  `MDICSectionRenderer`, or `VoxyRenderSystem`.

After K4, ownership and contracts exist for visibility/render-list data, but
operational traversal and GPU command generation are still missing. Live terrain
draw remains blocked.
