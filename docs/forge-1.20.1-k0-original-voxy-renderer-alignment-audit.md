# K0 Original Voxy Renderer Alignment Audit

Stage:

```text
K0_ORIGINAL_VOXY_RENDERER_ALIGNMENT_AUDIT
```

This audit is documentation-only. It does not implement a renderer, does not draw, and does not call `MDICSectionRenderer` or `VoxyRenderSystem`.

## Summary Verdict

```text
K0_VERDICT_READY_FOR_K1_NO_DRAW_FORMAL_RENDERER_OWNER
```

The current Forge path is still aligned with Voxy's original architecture as a staged port, not merely a visually similar rewrite, but only for the next no-draw ownership step. J5 closed the biggest preview-path gap by proving that real terrain/BuiltSection-derived packed records can be mapped to formal model ids in an isolated temporary buffer without mutating the geometry heap.

K1 can safely create a formal terrain renderer owner boundary that references the existing formal model, shader, geometry, and lifecycle readiness surfaces. K1 must remain no-draw or preview-only. It must not enable live terrain rendering, call `MDICSectionRenderer`, call `VoxyRenderSystem`, or treat the J-stage preview systems as the formal renderer.

The project is not ready for live LoD terrain draw. The remaining blockers are formal command/viewport ownership, global formal model-id geometry production, visibility/LOD traversal, shader semantic completion, and formal resource rebuild behavior.

## Original Voxy Mechanism Map

### Renderer Lifecycle Owner

Original owner:

```text
VoxyRenderSystem
 -> WorldEngine
 -> ModelBakerySubsystem
 -> RenderGenerationService
 -> BasicSectionGeometryData
 -> AsyncNodeManager
 -> HierarchicalOcclusionTraverser
 -> RenderDistanceTracker
 -> ViewportSelector
 -> AbstractSectionRenderer / MDICSectionRenderer
 -> AbstractRenderPipeline
```

`VoxyRenderSystem` constructs the model subsystem, render generation service, geometry data owner, traversal, render distance tracker, renderer backend, viewports, and render pipeline. Its shutdown path frees the model subsystem, render generation, traversal, node cleaner, geometry data, viewport selector, and pipeline in a single renderer-level ownership boundary.

The original renderer's draw entry points are `setupViewport`, `renderOpaque`, and `renderTranslucent`. They are downstream of traversal, viewport setup, section renderer ownership, and model store binding.

### Model Lifecycle

Original model flow:

```text
Mapper block id
 -> ModelBakerySubsystem.requestBlockBake(blockId)
 -> ModelFactory.addEntry(blockId)
 -> background processAllThings()
 -> bake model / texture result
 -> ModelBakeResultUpload
 -> processUploads()
 -> ModelStore modelData upload
 -> modelColour upload
 -> atlas tile upload
 -> idMappings[blockId] = modelId
 -> RenderDataFactory gets modelId + metadata
 -> packed quad record uses modelId
```

`ModelBakerySubsystem` owns `ModelStore` and `ModelFactory`, starts a background processing thread, and performs render-thread upload processing during `tick()`.

`ModelFactory` owns:

- `idMappings`
- `metadataCache`
- `fluidStateLUT`
- `modelTexture2id`
- bake queues and upload queues
- biome colour model tracking
- custom block-state id mapping

It dedupes by `ModelEntry`, assigns model ids, records fluid aliases, creates the 64-byte `BlockModel` record, and enqueues `ModelBakeResultUpload`.

`ModelStore` owns:

- `MODEL_SIZE = 64`
- `modelBuffer = 64 * 65536`
- `modelColourBuffer = 4 * 65536`
- Voxy block atlas texture
- sampler

It binds model data, model colour data, atlas texture, and sampler together as the formal shader input set.

### Packed Quad Generation

`RenderDataFactory` produces packed quad records after model ids are available. It reads raw model id mappings and model metadata, packs a 16-bit model id into the quad record, and builds bucketed section geometry.

Important original behavior:

- missing model ids are not silently accepted
- `IdNotYetComputedException` is used to defer section generation
- model metadata influences culling, material bucket selection, and face behavior
- bucket offsets are part of the built section contract
- packed records are generated with final model ids before renderer consumption

### Geometry and Metadata Ownership

`BasicSectionGeometryData` owns the section metadata buffer and geometry buffer. `BasicAsyncGeometryManager` owns section id allocation, geometry pointer allocation, metadata upload, removal, invalidation, and free/reuse behavior.

Key original geometry details:

- geometry records are 8 bytes
- geometry allocations are aligned to 128 records
- metadata entries encode section position, AABB, geometry pointer, bucket offsets, and bucket counts
- section id allocation and geometry pointer allocation are managed together
- removal invalidates metadata and frees geometry ranges

### MDIC Viewport and Command Ownership

`MDICViewport` owns per-viewport command and visibility resources:

- draw count call buffer
- draw call buffer
- position scratch buffer
- indirect lookup / render list buffer
- visibility buffer

`MDICSectionRenderer` owns the MDIC render implementation. Its formal bind shape includes:

- scene uniform buffer
- geometry buffer
- section metadata buffer
- `ModelStore.bind(3, 4, 0)`
- position scratch buffer
- lightmap
- depth texture
- shared index buffer
- indirect draw buffer
- parameter/draw-count buffer

The original draw path uses `glMultiDrawElementsIndirectCountARB` after compute-driven command generation and traversal output preparation.

### Visibility and LOD Traversal

`HierarchicalOcclusionTraverser` owns traversal buffers and fills the viewport render list/visibility state. `RenderDistanceTracker` owns movement-sensitive render distance state. Original MDIC rendering expects traversal output, visibility buffers, and render lists to be valid before command generation and draw.

### Shader Contract

Original shader contract:

- binding 0 uniform buffer: scene data
- binding 1 geometry buffer
- binding 2 section metadata buffer
- binding 3 model buffer
- binding 4 model colour buffer
- binding 5 position scratch buffer
- texture binding 0 block model atlas
- texture binding 1 lightmap
- texture binding 2 depth bounds texture

`BlockModel` layout:

```text
uint faceData[6];
uint flagsA;
uint colourTint;
uint customId;
uint _pad[7];
```

This is 16 uints / 64 bytes.

The shader extracts model id from packed quad bits, reads `modelData[modelId]`, derives face data and tint data, samples the atlas tile derived from model id and face, and applies alpha/cutout/tint/light/depth logic.

## Current Forge Equivalent Map

### Renderer Lifecycle Owner

Current equivalent:

```text
ForgeFormalRendererManager
```

This is intentionally no-draw. It aggregates readiness, lifecycle state, blockers, and debug/formal isolation. It does not own a live formal terrain renderer yet and does not call the original renderer classes.

Aligned parts:

- explicit owner boundary exists
- lifecycle/status/blocker reporting exists
- no-draw separation is enforced
- formal readiness is not inferred from debug renderer success

Missing before live renderer:

- formal viewport owner
- formal command buffer owner
- formal traversal owner
- formal renderer pipeline owner
- cleanup order covering all formal renderer resources

K1 can safely add a no-draw formal terrain renderer owner shell that references these readiness surfaces. K1 must not draw.

### Model Lifecycle

Current equivalents:

```text
ForgeFormalModelStore
ForgeFormalModelFactory
ForgeFormalModelBakeryLifecycle
ForgeOneBlockFormalBakeUpload
ForgeMultiBlockFormalBakeUpload
```

Alignment:

- formal model data, model colour, atlas, and sampler ownership exists
- one-block and multi-block real Forge bake/upload prototypes target the formal ModelStore owner
- alias-safe dedupe semantics are explicit
- safe-set rebuild/generation tracking exists
- sample-set bridge is no longer treated as formal data source

Intentional deviations:

- current lifecycle is command-driven and QA-oriented
- full async background bake thread is not implemented
- broad fluid lifecycle is not implemented
- full biome/model colour LUT is not implemented
- automatic rebuild on real reload is not yet complete

Drift risk:

- continuing to extend QA upload paths without turning them into a formal service could create a parallel ModelFactory instead of a Voxy-aligned lifecycle

### Packed Quad Generation

Current equivalents:

```text
ForgeVoxyBuiltSectionBuilder
ForgeVoxyQuadEncoder
ForgeFormalTerrainPackedRecordBridge
```

Alignment:

- packed quad model-id bit position is understood and replaceable in isolated buffers
- J5 proves real terrain/BuiltSection-derived records can recover block state source and map to formal model ids
- original geometry heap is not mutated during preview

Missing before live renderer:

- global BuiltSection generation does not yet emit formal model ids by default
- placeholder/debug model ids are still possible in existing terrain geometry
- missing model wait/retry semantics are not yet equivalent to `IdNotYetComputedException`
- metadata-driven bucket semantics are not fully formalized

K-stage live draw must not consume placeholder model-id geometry as if it were formal geometry.

### Geometry and Metadata Ownership

Current equivalents:

```text
ForgeGpuGeometryHeap
ForgeSectionGeometryManager
ForgeVoxyBuiltSectionBuilder
```

Alignment:

- GL geometry heap exists
- section geometry manager exists
- metadata and bucket/readback validation exist
- geometry upload/readback proof exists

Gaps:

- exact formal ownership boundary for geometry heap vs formal renderer is not yet separated
- allocation/free/reuse lifecycle still needs formal stress coverage
- command-generation ownership is not yet tied to formal section metadata ownership
- original `BasicAsyncGeometryManager` invalidation and reuse behavior is only partially mirrored

### MDIC Viewport and Command Ownership

Current equivalents:

```text
ForgeMdicCommand*
ForgeMdicDebugRenderer
ForgeTexturedMdicDebugRenderer
```

Alignment:

- indirect draw and count-buffer API proof exists
- command layout experimentation exists
- debug MDIC paths prove GL call feasibility

Intentional deviation:

- current command paths are debug/proof systems, not formal `MDICViewport` or `MDICSectionRenderer`
- CPU-written debug command buffers are not original Voxy command-generation ownership

Gaps before formal draw:

- formal viewport owner missing
- formal draw count buffer owner missing
- formal visibility buffer owner missing
- formal indirect lookup/render list owner missing
- formal position scratch owner missing
- formal compute command-generation path missing
- formal command buffer lifetime and clear order missing

Directly wiring `MDICSectionRenderer.renderTerrain(...)` would be unsafe until every required buffer, owner, and producer is accounted for.

### Visibility and LOD Traversal

Current equivalents:

```text
radius / frustum / debug selection
J-stage preview selection
```

Alignment:

- enough selection exists for preview and QA

Missing:

- hierarchical occlusion traversal
- viewport render list ownership
- visibility buffer production
- render distance queue integration
- LOD node traversal
- traversal-driven command generation

K-stage live terrain draw is not acceptable until traversal and visibility ownership are explicitly represented, even if no occlusion optimization is initially enabled.

### Shader Contract

Current equivalents:

```text
ForgeFormalShaderInputConsumer
ForgeFormalShaderProgramValidator
ForgeFormalTexturedShaderPreview
ForgeFormalPackedQuadPreview
ForgeFormalTerrainPackedRecordBridge
```

Aligned:

- modelData binding and 64-byte record layout are validated
- modelColour binding is validated
- atlas and sampler ownership are validated
- shader program validation exists
- textured preview consumes formal resources
- packed-quad preview consumes formal model ids
- real terrain-derived packed records can be bridged into formal preview buffers

Still missing or incomplete:

- formal terrain shader integration
- full `cmdgen.comp` semantics
- section metadata / position scratch formal path
- lightmap
- biome tint and full modelColour LUT behavior
- material flags
- alpha/cutout semantics
- translucency and sorting
- shaderpack integration
- formal live renderer state restore and binding discipline

### Debug vs Formal Boundary

Current debug/validation-only components:

- `ForgeMdicDebugRenderer`
- `ForgeTexturedMdicDebugRenderer`
- `ForgeTexturedReadbackRenderer`
- `ForgeTexturedDebugQuadRenderer`
- sample-set model data and atlas uploader
- debug command planners
- CPU-written debug draw count paths
- J2 shader program validator
- J3 textured shader preview
- J4 packed quad preview
- J5 terrain record bridge preview

These components remain valuable validation tools, but they must not be treated as formal renderer readiness.

## Alignment Table

| Area | Original Voxy mechanism | Current Forge equivalent | Alignment | Remaining gap |
| --- | --- | --- | --- | --- |
| Renderer lifecycle | `VoxyRenderSystem` owns model, geometry, traversal, renderer, pipeline | `ForgeFormalRendererManager` no-draw owner | Partial and intentional | Formal terrain renderer owner and cleanup order |
| Model lifecycle | `ModelBakerySubsystem` + `ModelFactory` + `ModelStore` | I2-I6 formal store/factory/lifecycle | Good for staged port | Real async service, automatic rebuild, fluid/tint breadth |
| Packed quads | `RenderDataFactory` emits final model ids | `ForgeVoxyBuiltSectionBuilder`, J5 bridge | Good preview proof | Global formal model id emission |
| Geometry metadata | `BasicAsyncGeometryManager` and `BasicSectionGeometryData` | `ForgeGpuGeometryHeap`, `ForgeSectionGeometryManager` | Partial | Formal ownership and free/reuse parity |
| MDIC viewport | `MDICViewport` owns command/visibility buffers | Debug command resources | Proof only | Formal viewport resources missing |
| Command generation | compute-driven `cmdgen.comp` path | CPU/debug command planners | API proof only | Formal compute command path missing |
| Visibility/LOD | hierarchical traversal and render distance tracker | debug/radius/preview selection | Not formal | Traversal/render list owner missing |
| Shader contract | formal terrain shaders consume model/metadata/cmd buffers | J1-J5 validation/preview | Strong preview alignment | Live terrain shader semantics incomplete |
| Resource reload | model service shutdown/rebuild lifecycle | reload invalidation and command-driven rebuild | Partial | automatic formal rebuild path incomplete |
| Shaderpack integration | original has patched shader path hooks | none formal | Not aligned yet | later-stage integration |

## Intentional Forge Deviations

These deviations are acceptable at K0 because they reduce risk before live terrain rendering:

- Forge uses no-draw formal owners before enabling live terrain draw.
- J-stage preview buffers are isolated and do not mutate the real geometry heap.
- Formal model upload is safe-set and command-driven before becoming automatic.
- Formal renderer readiness remains false even when preview shaders succeed.
- Debug renderers remain separate from formal renderer ownership.
- Synthetic fallback was acceptable in J4, but J5 correctly required real terrain-derived records.

## Unacceptable Drift Risks

These would move the project away from a Voxy port and toward an unrelated Forge renderer:

- using debug MDIC command planners as the formal command-generation path
- treating sample-set resources as formal ModelStore resources
- treating preview shader success as formal terrain shader readiness
- consuming placeholder/debug model ids in formal terrain draw
- mutating the existing geometry heap in-place for preview experiments
- bypassing model lifecycle wait/rebuild semantics
- directly calling `MDICSectionRenderer.renderTerrain(...)` without original viewport/traversal/command ownership
- enabling visible LoD terrain draw before formal command and visibility ownership exist

## Remaining Blockers Before K1

K1 can start only as a no-draw or preview-only owner. Before K1 can become live terrain rendering, these blockers remain:

### P0 Before Live Formal Draw

- formal terrain renderer owner resource graph is missing
- formal viewport resource owner is missing
- formal command buffer and draw count ownership are missing
- formal visibility/render-list ownership is missing
- global formal model-id geometry production is incomplete
- formal MDIC command-generation path is incomplete
- formal terrain shader integration is incomplete

### P1 Before Early Usable LoD

- visibility and LOD traversal are missing
- lightmap path is missing
- biome tint/modelColour semantics are incomplete
- material, alpha, and cutout semantics are incomplete
- translucent handling and sorting are missing
- dimension switch/world unload stress under formal owner is incomplete
- automatic resource reload rebuild is incomplete

### P2 Later

- shaderpack integration
- Embeddium/Oculus/Iris integration
- advanced occlusion performance
- translucent quality
- renderer performance tuning

## Recommended K1 Scope

Recommended next stage:

```text
K1: Formal terrain renderer owner no-draw skeleton
```

K1 should do:

- create a formal terrain renderer owner boundary
- reference formal ModelStore, ModelFactory lifecycle, shader validation, geometry heap, and J5 bridge readiness
- define formal viewport/command/visibility resource ownership placeholders
- define cleanup order and lifecycle hooks
- report readiness/blockers
- optionally run preview-only audits through existing J-stage systems
- keep `formalRendererReady=false`
- keep `actualRendererDrawEnabled=false`

K1 must not:

- draw live terrain
- call `MDICSectionRenderer`
- call `VoxyRenderSystem`
- enable formal MDIC commands as renderer
- enable visibility traversal
- globally rewrite geometry ids
- replace debug renderers
- claim formal textured shader readiness
- claim formal renderer readiness

## Explicit Do-Not-Do List

- Do not wire `MDICSectionRenderer.renderTerrain(...)` directly.
- Do not instantiate `VoxyRenderSystem` as a shortcut.
- Do not turn `ForgeMdicDebugRenderer` into the formal renderer.
- Do not turn `ForgeTexturedMdicDebugRenderer` into the formal renderer.
- Do not use sample-set resources as formal resources.
- Do not consume placeholder model ids in formal terrain draw.
- Do not mutate the real geometry heap for preview rewrites.
- Do not skip formal viewport/command/visibility owners.
- Do not mark `formalTexturedShaderReady=true`.
- Do not mark `formalRendererReady=true`.

## Files Inspected

Original Voxy files:

- `src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelBakerySubsystem.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelStore.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelQueries.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicAsyncGeometryManager.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicSectionGeometryData.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicSectionGeometryManager.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/hierachical/HierarchicalOcclusionTraverser.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/RenderDistanceTracker.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/ViewportSelector.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/Viewport.java`
- `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl`
- `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp`
- `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl`
- `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl`
- `src/main/resources/assets/voxy/shaders/lod/section.glsl`
- `src/main/resources/assets/voxy/shaders/lod/block_model.glsl`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag`

Current Forge files and docs:

- `AGENTS.md`
- `docs/forge-1.20.1-stage-roadmap.md`
- `docs/forge-1.20.1-formal-renderer-readiness-audit.md`
- `docs/forge-1.20.1-model-bakery-bridge-plan.md`
- `docs/forge-1.20.1-formal-mdic-renderer-integration-plan.md`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalRendererManager.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelStore.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelStoreAuditResult.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelStoreLifecycleState.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelStoreReadiness.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelStoreStats.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelFactory.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelFactoryAuditResult.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelFactoryLifecycleState.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelFactoryReadiness.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelFactoryStats.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelBakeryLifecycle.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelBakeryLifecycleAuditResult.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelBakeryLifecycleStats.java`
- `src/main/java/me/cortex/voxy/forge/ForgeOneBlockFormalBakeUpload.java`
- `src/main/java/me/cortex/voxy/forge/ForgeOneBlockFormalBakeUploadAuditResult.java`
- `src/main/java/me/cortex/voxy/forge/ForgeOneBlockFormalBakeUploadStats.java`
- `src/main/java/me/cortex/voxy/forge/ForgeMultiBlockFormalBakeUpload.java`
- `src/main/java/me/cortex/voxy/forge/ForgeMultiBlockFormalBakeUploadAuditResult.java`
- `src/main/java/me/cortex/voxy/forge/ForgeMultiBlockFormalBakeUploadStats.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalShaderInputConsumer.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalShaderInputAuditResult.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalShaderInputStats.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalShaderProgramValidator.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalShaderProgramAuditResult.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalShaderProgramStats.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalTexturedShaderPreview.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalTexturedShaderPreviewAuditResult.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalTexturedShaderPreviewStats.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalPackedQuadPreview.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalPackedQuadPreviewAuditResult.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalPackedQuadPreviewStats.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalTerrainPackedRecordBridge.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalTerrainPackedRecordBridgeAuditResult.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalTerrainPackedRecordBridgeStats.java`
- `src/main/java/me/cortex/voxy/forge/ForgeGpuGeometryHeap.java`
- `src/main/java/me/cortex/voxy/forge/ForgeSectionGeometryManager.java`
- `src/main/java/me/cortex/voxy/forge/ForgeMdicCommandPlanner.java`
- `src/main/java/me/cortex/voxy/forge/ForgeMdicCommandAuditResult.java`
- `src/main/java/me/cortex/voxy/forge/ForgeMdicCommandBucketStats.java`
- `src/main/java/me/cortex/voxy/forge/ForgeVoxyBuiltSectionBuilder.java`
- `src/main/java/me/cortex/voxy/forge/ForgeVoxyQuadEncoder.java`
- `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java`
- `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java`

