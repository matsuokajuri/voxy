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

## K5 formal cmdgen GPU validation note

K5 adds the first GPU-side validation of command-generation output without
starting a renderer. It compiles a small Forge-owned audit compute program that
uses the same formal binding shape as the original command-generation path:

- draw command buffer at binding 1,
- draw count / parameter buffer at binding 2,
- section metadata at binding 3,
- visibility at binding 4,
- render-list / indirect lookup at binding 5,
- position scratch at binding 6.

The K5 validation program writes one deterministic `DrawCommand`, updates the
opaque draw count, writes one position scratch entry, and reads those outputs
back on the CPU for audit.

Intentional Forge deviation:

- K5 uses isolated validation buffers, not live renderer buffers.
- K5 may use a synthetic validation fixture because formal traversal output is
  not implemented yet.
- K5 does not run production `cmdgen.comp` as the live renderer path.
- K5 does not submit generated commands to
  `glMultiDrawElementsIndirectCountARB`.
- K5 does not call `MDICSectionRenderer` or `VoxyRenderSystem`.

After K5, the project has a command-generation GPU proof, but production
command generation, formal traversal, formal terrain shader integration, and
formal MDIC draw remain blocked.

## K6 formal cmdgen real-section dry-run note

K6 removes the K5 synthetic fixture from the success path. The command-generation
GPU validation program now consumes real Forge section metadata when available:

- current-world chunks are ingested into the temporary WorldEngine skeleton,
- `ForgeCpuMeshBuilder` creates CPU mesh sections,
- `ForgeVoxyBuiltSectionBuilder` creates real Voxy/Forge BuiltSection records,
- `ForgeSectionGeometryManager` owns the CPU-side section metadata snapshot,
- K6 copies that metadata into isolated validation-only SSBOs,
- the audit compute program emits command/count/position-scratch data,
- CPU readback verifies the first command against the accepted section metadata.

Intentional Forge deviation:

- K6 does not run production `cmdgen.comp` as the live renderer path.
- K6 does not use debug MDIC command buffers as formal command buffers.
- K6 does not submit generated commands to
  `glMultiDrawElementsIndirectCountARB`.
- K6 does not call `MDICSectionRenderer` or `VoxyRenderSystem`.
- K6 does not enable the formal draw pipeline.

After K6, the command-generation proof has advanced from "can a validation
program generate any command?" to "can the validation path consume real section
metadata?" The remaining live-renderer blockers are still production cmdgen,
formal traversal, global formal model-id geometry, formal terrain shader
integration, and formal MDIC draw.

## K7 formal isolated MDIC draw smoke test note

K7 performs one formal-path validation draw, but only into an isolated
offscreen framebuffer. It intentionally does not wire the original
`MDICSectionRenderer` or `VoxyRenderSystem`.

Original alignment:

- The validation command is shaped as the original Voxy five-field indexed
  indirect `DrawCommand`.
- The draw count is read from the original opaque draw-count parameter-buffer
  offset.
- Formal model inputs use the original binding intent: model data at binding 3,
  model colour at binding 4, and the block model atlas on texture unit 0.
- The validation call may use `glMultiDrawElementsIndirectCountARB`, but only
  with `glMultiDrawElementsIndirectCountCallScope=K7_validation_offscreen_only`.

Intentional Forge deviation:

- K7 copies K6 command values into K7-owned validation buffers instead of using
  live renderer command buffers.
- K7 uses an isolated validation shader subset, not the production terrain
  shader.
- K7 uses an offscreen framebuffer, not the Minecraft main framebuffer.
- K7 does not mutate the original GL geometry heap.
- K7 does not enable live terrain draw and does not claim the formal draw
  pipeline is ready.

After K7, the project has proven that a real-section-derived formal command can
execute an isolated offscreen validation draw against formal ModelStore
resources. Live renderer integration remains blocked on production cmdgen,
formal traversal, global formal model-id geometry, production terrain shader
semantics, and renderer lifecycle hardening.

## K8 formal model-id section geometry path note

K8 creates a K8-owned formal geometry snapshot path. It scans real
BuiltSection packed records, recovers the source block state through the legacy
model-id mapper, resolves the corresponding formal model id from the I3/I6
model lifecycle, and writes formal-model-id records into an isolated snapshot
and validation buffer.

Intentional Forge deviation:

- K8 does not globally rewrite existing live/debug geometry records.
- K8 does not mutate the original GL geometry heap.
- K8 does not submit K8 geometry to the live renderer.
- K8 may use K7 offscreen evidence as dependency proof, but does not turn K7
  preview resources into the renderer.
- K8 does not call `MDICSectionRenderer` or `VoxyRenderSystem`.

After K8, the project can distinguish:

```text
formalModelIdGeometryPathReady=true
globalFormalModelIdGeometryPathReady=true
globalFormalModelIdGeometryEnabledForLiveRenderer=false
```

The next renderer blockers remain production cmdgen, formal traversal,
production terrain shader integration, live MDIC draw ownership, and full
resource rebuild automation.

## K9 formal terrain shader offscreen integration note

K9 integrates a production-aligned terrain shader adapter into the formal path,
but only for offscreen validation. It consumes the K8 formal model-id geometry
snapshot and K6/K7 real-section command/count evidence, then binds formal
`ModelStore` resources using the original binding intent:

- packed quad-style geometry at binding 1,
- model data at binding 3,
- model colour at binding 4,
- block model atlas on texture unit 0,
- position scratch at binding 5.

Intentional Forge deviation:

- K9 uses a shader adapter/subset instead of claiming the original terrain
  shader files are fully ported.
- K9 owns its framebuffer, shader program, index buffer, command buffer,
  draw-count buffer, and position-scratch validation buffer.
- K9 does not draw into the Minecraft main framebuffer.
- K9 does not call `MDICSectionRenderer` or `VoxyRenderSystem`.
- K9 does not enable live terrain draw or mark the formal draw pipeline ready.

After K9, the formal path can prove `modelId -> BlockModel.faceData -> atlas
sample -> offscreen pixel` with real-section-derived geometry and command
evidence. Remaining blockers are production cmdgen, formal hierarchical
visibility traversal, full terrain shader semantics, live MDIC renderer
ownership, lightmap, biome tint, material/alpha semantics, translucency, and
resource rebuild automation.

## K10 formal visible LoD preview note

K10 introduces an explicit visible preview hook for the formal path. It uses the
same formal input chain proven by K8 and K9, then draws a small world-space
preview into the Minecraft main framebuffer only when enabled by command or
preset.

Original alignment:

- The preview continues to use the original Voxy-style indexed indirect command
  shape copied from K6/K7 validation evidence.
- Formal model inputs keep the original binding intent: packed geometry at
  binding 1, model data at binding 3, model colour at binding 4, position
  scratch at binding 5, and the block model atlas on texture unit 0.
- The visible shader path is still the K9 production-aligned adapter subset,
  not a claim that the original terrain shader is fully ported.

Intentional Forge deviation:

- K10 registers a narrow Forge render-stage hook for preview-only drawing.
- The preview is disabled by default and requires explicit debug opt-in.
- The preview auto-disables in QA after a short visible window.
- K10 does not call `MDICSectionRenderer` or `VoxyRenderSystem`.
- K10 does not use debug MDIC command buffers as formal command buffers.
- K10 does not mutate or replace the original GL geometry heap.
- K10 does not mark the formal draw pipeline, formal renderer, or actual
  renderer draw as ready.

After K10, the project has visible proof that the formal path can put pixels in
the game framebuffer, but the remaining blockers are still production cmdgen,
formal hierarchical traversal, live MDIC renderer integration, full terrain
shader semantics, lightmap, biome tint, material/alpha behavior, translucency,
and resource rebuild automation.

## K10.1 visible preview observe/performance hotfix note

K10.1 is a hotfix to the K10 preview loop. It does not add production MDIC
rendering and does not advance to K11.

The visible preview now has an explicit observe mode that remains disabled by
default and must be enabled with a command. Observe mode increases preview
scale, applies a bright debug tint, and reports camera-relative placement and
world bounds so manual validation can distinguish the preview from ordinary
terrain.

The render hook is kept narrow:

- build/rebuild work stays in build, QA, or enable paths;
- shader compilation and GL allocation are not expected in the per-frame draw;
- framebuffer readback is QA/audit-only, not part of manual observe frames;
- repeated hook registration is detected and reported;
- disabled or stale preview state returns from the hook before drawing.

The same hard boundary remains:

```text
MDICSectionRendererCalled=false
VoxyRenderSystemCalled=false
productionLiveRendererDrawExecuted=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

## K10.2 visible preview observe timeout hotfix note

K10.2 keeps the K10 visible preview as a debug opt-in preview and only changes
the manual observe enable lifecycle. The command handler no longer performs
heavy preview construction, shader compilation, GL allocation, readback, or a
full prerequisite rebuild before returning.

Manual observe enable now follows a render-thread request contract:

- command handling sets `observeEnableRequested=true` and records command-path
  timing/GL/readback/rebuild flags;
- the render hook consumes the request on the render thread;
- observe drawing is enabled only when existing K10 resources are already ready
  and not stale;
- stale or missing resources disable observe mode safely and report
  `lastObserveEnableFailureReason`;
- no command path calls `MDICSectionRenderer`, `VoxyRenderSystem`, or the
  production live renderer.

The K10.2 timeout QA command is:

```text
/voxy qa_k10_visible_preview_observe_timeout
```

It verifies that observe enable returns quickly and does not do command-thread
GL work, readback, or synchronous rebuild. It remains separate from production
MDIC renderer integration.

## K10.3 visible preview observe prepare hotfix note

K10.3 keeps the same non-production boundary as K10.2 but changes the missing
resource behavior. If manual observe enable finds the K10 visible preview owner
missing or stale, the render hook now starts a bounded prepare sequence instead
of failing immediately.

The prepare sequence is explicitly staged:

```text
K6 real-section cmdgen dry-run
 -> K7 isolated offscreen draw evidence
 -> K8 formal model-id geometry snapshot
 -> K9 terrain shader integration
 -> K10 visible preview owner build
 -> observe mode enable
```

This work is render-thread owned and does not happen in the Brigadier command
handler. The command still returns quickly and reports no command-thread GL
work, readback, or synchronous rebuild. Disable, clear, reload, world unload,
dimension switch, and preset clear/off cancel any pending observe prepare.

K10.3 still does not call `MDICSectionRenderer`, does not call
`VoxyRenderSystem`, does not run the production live renderer, and does not
change formal renderer readiness.

If a resource reload is observed during the manual observe prepare sequence,
K10.3 treats it as an instruction to rebuild the preview dependencies rather
than as a reason to drop the explicit user request. Disable, clear, world
unload, dimension switch, preset off, and preset clear still cancel the request.

K10 observe performance follow-up: once manual observe could visibly draw, the
continuous main-framebuffer path was changed to a lightweight direct indexed
draw using the K6 command fields and K8/K9 formal inputs. This avoids calling
the indirect-count validation path every frame while preserving the same
preview-only boundary. The formal command and draw-count buffers remain owned
evidence, not debug buffers and not live renderer inputs.

The manual observe path now caps the visible draw to a small sample count so the
debug proof does not render the whole real-section command payload every frame.
The uncapped K6 command data remains recorded for audit, and the capped observe
sample does not imply formal MDIC live draw readiness.

Original Voxy comparison found one concrete K10 preview drift: original
`quads3.vert` and `SharedIndexBuffer` use four logical vertices per packed quad
with a shared index sequence `1,2,0,1,3,2`. K10's visible preview has been
adjusted to that convention instead of the earlier six-vertex helper so future
MDIC integration does not inherit a preview-only vertex-id assumption.

Manual observe command follow-up: observe enable/disable now return short
toggle-oriented feedback instead of formatting the full K10 and renderer status
on the command path. Expensive or verbose readiness evidence stays behind the
explicit status/audit commands, and disable no longer runs a synchronous K10
audit before stopping preview drawing.

## K11 visible preview lifecycle prewarm note

K11 adds an explicit prewarm lifecycle for the K10 visible preview. It keeps the
preview path below production MDIC integration but stops `observe_enable` from
being the command that implicitly discovers and prepares missing resources.

The K11 command path is:

```text
/voxy formal_visible_lod_preview_prepare
 -> render-thread K6/K7/K8/K9/K10 preparation
 -> visible preview resources prepared
 -> visible preview remains disabled

/voxy formal_visible_lod_preview_observe_enable
 -> lightweight enable only when prepared
```

If the resources are stale or missing, observe enable reports
`preview-not-prepared-run-formal_visible_lod_preview_prepare` and returns
without starting a hidden rebuild. K11 still does not call `MDICSectionRenderer`
or `VoxyRenderSystem`, does not run production `cmdgen.comp`, and does not mark
the formal draw pipeline or formal renderer ready.

## K12/K13 visible preview prepare timing and reuse note

K12/K13 does not add live MDIC rendering. It tightens the K11 prepare workflow so
the formal visible preview path can be tested in larger batches with fewer game
restarts. The prepare owner now records per-step timing for the K6/K7/K8/K9/K10
preparation chain, reports the slowest step, and marks whether a second prepare
request reused already-prepared resources instead of rebuilding them.

The compact QA entry is:

```text
/voxy qa_k12_k13_visible_preview_prepare_reuse
```

Expected behavior after a successful prepare is that a repeated prepare command
returns quickly with `lastPrepareReusedExistingResources=true` and
`unnecessaryRebuildDetected=false`. Visible preview drawing still requires the
explicit observe enable command, and the production MDIC renderer remains
disabled.

## K14/K16 multi-section preview integration note

K14/K16 keeps the visible path below formal MDIC renderer integration but
removes a preview-only single-record weakness. The K8 formal model-id geometry
snapshot can contain multiple accepted real sections; K10 now builds a bounded,
interleaved preview buffer from that snapshot and draws only that capped copy.

This is intentionally not the production geometry ownership path:

```text
K8 formal section snapshot
 -> interleaved K10-owned preview buffer
 -> capped visible preview draw
 -> no MDICSectionRenderer
 -> no VoxyRenderSystem
 -> no live geometry heap mutation
```

The production path still needs formal traversal, production command
generation, formal MDIC command buffer ownership, full terrain shader semantics,
and a default-disabled live renderer integration step before the project can
leave preview-only rendering.

## K17/K18 world-placed preview note

K17/K18 changes the K10 visible preview from a camera-relative proof panel into
a preview-only world-placed packed-quad sample. The sidecar buffer supplies the
section base and LoD scale that the original Voxy shader obtains through section
metadata and position scratch concepts:

```text
K8 formal packed record
 + K8 section position
 -> K10-owned section sidecar buffer
 -> shader decodes packed local position / size / face
 -> world-space visible preview quad
```

The sidecar is intentionally scoped to preview validation. It is not a
production section metadata buffer, not a formal MDIC command resource, and not
owned by the live renderer. Production integration still requires a formal
section metadata owner, operational production cmdgen, and live MDIC draw
ownership.

## K19/K20 section metadata / position scratch preview note

K19/K20 keeps K10 on the preview side of the renderer boundary while reducing
the K17/K18 sidecar drift from original Voxy. The visible preview now uploads a
bounded, K10-owned `SectionMeta`-shaped metadata buffer plus a matching
position-scratch buffer for the selected preview records:

```text
K8 formal packed records
 + K8 section positions
 -> K10 preview SectionMeta buffer
 -> K10 preview positionScratch buffer
 -> visible shader derives section base and LoD scale
```

This matches the original concept where `cmdgen.comp` writes
`positionScratch[drawId] = extractRawPos(meta)` and `quads3.vert` /
`quad_util.glsl` derive world placement from that raw section position. The K10
buffers remain validation resources: they are not `BasicSectionGeometryData`,
not production MDIC command resources, not a live renderer input, and not a
replacement for formal visibility traversal or production command generation.

## K21/K22 preview DrawCommand / bucket alignment note

K21/K22 adds a preview-only bridge between the K19/K20 section metadata path and
future production MDIC draw ownership. The visible preview now constructs a
K10-owned command buffer using the original five-field `DrawCommand` layout:

```text
count
instanceCount
firstIndex
baseVertex
baseInstance
```

The K10 draw remains a direct, preview-scoped draw, but its draw count and
index/base parameters are derived from that command state. The shader also uses
the preview command `baseInstance` to address the preview position scratch
buffer, matching the shape of the original `cmdgen.comp -> positionScratch ->
quads3.vert` handoff.

This is not production indirect rendering. K21/K22 does not run production
`cmdgen.comp`, does not bind the K2 formal command buffers as live renderer
inputs, does not call `MDICSectionRenderer`, and does not call
`glMultiDrawElementsIndirectCountARB` for terrain rendering.

## K23-K30 minimal formal LoD prototype note

K23-K30 keeps the renderer on the safe side of the production MDIC boundary but
raises the visible proof from a command-aligned sample to a bounded formal LoD
patch:

```text
K8 formal model-id section geometry snapshot
 -> K10/K23 bounded contiguous bucket copy
 -> preview SectionMeta + positionScratch
 -> K10-owned DrawCommand
 -> direct visible prototype draw
```

The patch cap is deliberately small (`96` records) and uses one accepted
section/bucket so the current `baseInstance -> positionScratch` contract stays
honest. The draw remains direct and prototype-scoped; K23-K30 does not submit
production indirect draw calls, does not run live production `cmdgen.comp`,
does not call `MDICSectionRenderer`, and does not make the formal renderer
default-enabled. The next production-facing step is still to move from this
prototype-owned bounded copy into formal renderer-owned section geometry,
visibility, and command resources.

## K31-K36 moving formal LoD preview owner note

K31-K36 adds the first moving-patch ownership shell above the K23-K30 bounded
LoD prototype. The path is still preview-only, but it is less hardcoded:

```text
formal terrain renderer owner shell
 -> K8 formal model-id section geometry snapshot
 -> interleaved bounded patch copy
 -> K10-owned SectionMeta / positionScratch / DrawCommand preview resources
 -> explicit visible preview toggle
```

The moving patch records a player chunk anchor, section count, record count,
rebuild count, update threshold, and whether the source was a real candidate
snapshot. This makes the preview closer to a future renderer-managed region
without changing the live renderer contract.

K31-K36 still does not:

```text
run production cmdgen.comp
populate K2 formal command buffers as live renderer input
call MDICSectionRenderer
call VoxyRenderSystem
mutate the original geometry heap
enable default LoD drawing
claim formalDrawPipelineReady
claim formalRendererReady
claim actualRendererDrawEnabled
```

The next MDIC-facing work should convert this preview-owned moving patch into
formal renderer-owned update scheduling and command resource handoff, still
with explicit opt-in and bounded draw scope until production traversal and
shader semantics are ready.

## K37-K42 expanded formal LoD patch preview note

K37-K42 keeps the same MDIC boundary as K31-K36 but expands the preview input
so the visible proof can cover a small region instead of one narrow patch slice:

```text
K8 expanded formal model-id snapshot
 -> interleaved K10 preview copy
 -> preview SectionMeta / positionScratch
 -> K10-owned DrawCommand
 -> direct visible preview draw
```

The expanded path is capped at 768 formal packed records and reports
`recordCount`, `sectionCount`, `chunkCount`, `yBandCount`, `sectionBases`, and
`selectionStrategy`. This gives the next production-facing stage better input
evidence without treating the preview copy as formal MDIC command ownership.

K37-K42 still does not run production `cmdgen.comp`, does not populate the K2
formal command buffers as live renderer inputs, does not submit production
indirect draws, does not call `MDICSectionRenderer`, and does not call
`VoxyRenderSystem`.

## K43-K48 moving update lifecycle note

K43-K48 adds the first explicit update decision layer above the expanded
preview patch:

```text
expanded patch built anchor
 + current player chunk
 -> movingUpdateDeltaChunks
 -> movingUpdateRebuildNeeded
 -> render-thread expanded patch prepare request
```

This is the beginning of renderer-like patch lifecycle behavior, but the
resources are still K10 preview resources. The rebuild-if-needed command marks
stale and requests the existing render-thread prepare path only when the player
has moved beyond the threshold, avoiding command-thread GL work and avoiding
per-frame rebuild attempts.

K43-K48 does not implement production visibility traversal, does not dispatch
production `cmdgen.comp`, does not hand commands to `MDICSectionRenderer`, and
does not enable a live formal renderer.

## K49-K54 auto update throttle note

K49-K54 extends the preview owner with an automatic, throttled update loop:

```text
visible preview enabled
 -> cheap chunk-anchor check every N render-hook invocations
 -> movingUpdateRebuildNeeded
 -> defer while player position is still changing
 -> schedule existing expanded-patch prepare
 -> reuse persistent visible shader/buffer owners
 -> auto-enable preview after data refresh
```

This is intentionally not a production MDIC update loop. It does not own formal
visibility traversal, does not run production command generation, does not call
`MDICSectionRenderer`, and does not route through `VoxyRenderSystem`. Its value
is proving that the current preview can detect movement-driven staleness and
refresh without repeatedly compiling shaders or recreating GL buffer objects.
The original Voxy renderer goes further by generating section meshes
asynchronously and uploading/swapping them incrementally; that production-grade
owner remains future work.
The preview source selector now prefers the current player chunk and sorts
nearby cached BuiltSections by distance before fallback use. This reduces stale
preview anchors without claiming production visibility traversal or production
geometry ownership.
