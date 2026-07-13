# Forge 1.20.1 Roman round roadmap

> **HISTORICAL ROADMAP.** This file records the early Roman remediation plan and
> is no longer the current status/implementation ledger. Use
> `forge-1.20.1-original-voxy-full-render-path-parity-audit.md`; XXV supersedes
> the partial-owner and `ForgeFrontendCompat` statements below.

This document supersedes the old H/I/J/K/L and K10-preview-era roadmap.

The project route is now strict original Voxy parity. Historical stages remain
in git history, but they are no longer the implementation plan.

## Current rule

```text
all implementation starts from original Voxy source
all logic is ported/adapted from original Voxy logic
all chains must be traced to the bottom layer before coding
```

Preview, sample-set, synthetic, fallback, and QA-only paths are deprecated as
future direction. They may stay in code temporarily only to preserve compilation
until their references are removed.

## Forge frontend prerequisites

The Forge port now treats these client mods as hard prerequisites:

```text
Embeddium
Oculus
```

Embeddium is the Forge replacement for the original Sodium frontend. Oculus is
the Forge replacement for the original Iris/shaderpack integration point. New
parity work that reaches Sodium/Iris-dependent original code must inspect the
original Voxy source and then adapt it against Embeddium/Oculus, preserving the
same ownership and data flow.

Frontend package and mod-id mapping is tracked in:

```text
docs/forge-1.20.1-embeddium-oculus-frontend-mapping.md
```

## Superseded historical plan

The following are historical and not current route guidance:

```text
extended G6.x debug/MDIC proof phases
H no-draw ownership skeleton stages
I safe-set ModelStore/ModelFactory prototype stages
J shader/preview prototype stages
K visible preview and moving-patch stages
K10/K11/K12... preview prepare/update stages
K23-K54 minimal preview renderer batches
```

These stages produced useful evidence, but they also introduced substitute
routes. New work must not continue those routes.

## Active naming convention

New implementation rounds use Roman numerals only:

```text
I, II, III, IV, V, VI, VII, VIII, IX, X
```

A large Roman numeral is one coherent implementation round. Dotted Arabic
suffixes are steps inside that same round:

```text
V.1_ORIGINAL_MDIC_VIEWPORT_CMDGEN_INPUT_PARITY
V.2_ORIGINAL_CMDGEN_COMP_OUTPUT_PARITY
V.3_ORIGINAL_CMDGEN_READBACK_AND_BARRIER_AUDIT
```

Old alphanumeric labels such as `H`, `I5`, `J2`, `K10`, `K23-K54`, and `L1`
are historical labels only. They must not be used for new implementation work.
Prefer one coherent Roman round with several compiled steps over splitting each
dotted step into a separate mini-stage.

## Active roadmap: Roman original Voxy parity remediation

The active roadmap is component parity with the original renderer:

```text
I. Source audit and substitute retirement
II. ModelBakerySubsystem / ModelFactory / SoftwareModelTextureBakery / ModelStore parity
III. RenderGenerationService / RenderDataFactory / BuiltSection / geometry manager parity
IV. RenderDistanceTracker / NodeManager / HierarchicalOcclusionTraverser / Viewport / HiZ / MDICViewport parity
V. cmdgen.comp input/output parity
VI. MDICSectionRenderer parity
VII. Original terrain shader contract parity
VIII. Visible LoD renderer integration behind explicit parity owner
IX. Runtime lifecycle, reload, movement update, and performance parity
X. Compatibility polish and deprecated-route retirement
```

These Roman rounds are parity-remediation work units, not feature demos.

## Current Roman-route status

The Forge route now has a focused original-Voxy model-pipeline owner boundary:

```text
ForgeFrontendCompat
ForgeOriginalVoxyModelPipeline
ForgeOriginalVoxyModelFactory
```

It follows the original `VoxyRenderSystem` ordering at the boundary level:

```text
Embeddium/Oculus frontend prerequisites verified through Forge ModList
 -> no FabricLoader runtime check in the Forge route
WorldEngine / Mapper
 -> existing biome entries queued
 -> mapper biome callback attached
 -> block model requests enter a Forge-port ModelFactory
 -> SoftwareModelTextureBakery writes colour/depth faces
 -> idMappings / metadataCache / fluidStateLUT / modelTexture2id are updated
 -> formal ModelStore receives modelData/modelColour/atlas uploads
```

It now owns a partial original `ModelFactory` port, but it does not yet claim
full renderer or draw-pipeline parity. The model owner now includes the first
original `ModelBakerySubsystem` worker/upload split:

```text
"Model factory processor" worker thread
LockSupport.unpark request wakeups
worker-side processAllThings()
render-thread upload-result queue
packed 3x2 mip-chain atlas upload
biome colour LUT upload
```

The original `me.cortex.voxy.client.core.*` files are authoritative source
references but are not included in the current Forge compile source set, so the
mechanisms must be ported/adapted under `me.cortex.voxy.forge` rather than
directly imported.

Missing before Roman round II model/upload parity can be considered complete:

```text
none at the ModelFactory / ModelStore upload-contract level
```

The `TextureUtils` / `ColorSRGB` conversion path is now aligned to original
Voxy's Sodium dependency through a Forge-local port of Embeddium's fast-srgb8
`ColorSRGB` implementation.

The active software bakery now also uses the original-shaped
`ReuseVertexConsumer` / `SoftwareRasterizer` path: MemoryBuffer-backed
24-byte vertex records, whole-atlas UV sampling, and original depth/stencil
framebuffer packing. Its block material bridge now reads Embeddium's injected
`BakedQuadView` and sprite transparency data instead of guessing from raw
vanilla vertices, so the Forge/Embeddium route reports
`originalSoftwareModelTextureBakeryUsed=true`.

The original persistent-mapped `UploadStream`, `RenderGenerationService`
missing-model request/requeue, `RenderDataFactory`, `BuiltSection`,
`ScanMesher2D`, `OccupancySet`, and `ModelQueries` parity pieces are now present
in the Forge source set. `RenderGenerationService` now reports
`originalServiceManagerParityReady=true`: the Forge parity route includes the
original `ServiceManager` / `Service` / `UnifiedServiceThreadPool` execution
stack, the original service-thread target config, Embeddium builder-thread
subtraction, and the original `SemaphoreBlockImpersonator` builder-thread
sharing mechanism mapped onto Embeddium `ChunkJobQueue`.

The original model upload route now also audits committed uploads by reading
back the original `ModelStore` modelData, optional modelColour range, and
3x2 atlas mip-chain region. The Iris/Oculus custom block-state id hook is
connected through the same `WorldRenderingSettings.INSTANCE.getBlockStateIds()`
source used by original Voxy.

`RenderGenerationService` `BuiltSection` output is now consumed by a
Forge-port `BasicAsyncGeometryManager` owner. This ports the original section id
allocation, geometry heap allocation, 128-record alignment, 32-byte metadata
packing, and heap upload/remove/update event sets. A Forge-port
`BasicSectionGeometryData` now owns the render-thread metadata/geometry buffer
store with the original capacity policy, sparse-buffer behavior, and free
lifecycle. `ForgeOriginalVoxyAsyncNodeGeometrySync` now removes the old direct
generated-section drain path and applies geometry uploads/metadata updates
through original-style `SyncResults`, `UploadStream`, `memcpy.comp`, and
`scatter.comp` into `BasicSectionGeometryData`. The Forge route now has
original-shaped `NodeManager`, `NodeStore`, `SectionUpdateRouter`,
`NodeCleaner`, `GeometryCache`, and `RenderDistanceTracker` ownership.
`ForgeOriginalVoxyHierarchicalOcclusionTraverser`,
`ForgeOriginalVoxyViewportSelector`, `ForgeOriginalVoxyMdicViewport`,
`ForgeOriginalVoxyHiZBuffer`, and `ForgeOriginalVoxyDepthFramebuffer` are now
part of the active route. The Forge route now mirrors original
`AbstractRenderPipeline.initDepthStencil(...)` through
`ForgeOriginalVoxyPipelineDepthStage`, copying the current depth attachment into
a Voxy-owned `DepthFramebuffer(GL_DEPTH24_STENCIL8)` before building HiZ.

The current active round is now implemented and runtime-audited:

```text
V_ORIGINAL_MDIC_COMMAND_GENERATION_CHAIN
 -> V.1_ORIGINAL_MDIC_VIEWPORT_CMDGEN_INPUT_PARITY
 -> V.2_ORIGINAL_CMDGEN_COMP_OUTPUT_PARITY
 -> V.3_ORIGINAL_CMDGEN_READBACK_AND_BARRIER_AUDIT
 -> VI_ORIGINAL_MDIC_SECTION_RENDERER_CHAIN
```

The Forge route now folds the command-generation owner into
`ForgeOriginalVoxyMdicSectionRenderer`, matching the original owner boundary
more closely than the old command-generator-only class. The active chain now
follows original `MDICSectionRenderer.buildDrawCalls(...)` through:

```text
MDICViewport render-list from HOC
 -> production prep.comp
 -> production cull/raster visibility mark
 -> production cmdgen.comp
 -> prefixsum.comp over translucent distance buckets
 -> production buildtranslucents.comp
 -> DrawCommand / draw-count / position-scratch readback audit on request
```

This owner binds real `BasicSectionGeometryData` metadata/geometry buffers and
the real `MDICViewport` draw-count, draw-command, visibility, indirect-lookup,
and position-scratch buffers. It does not use K-era synthetic cmdgen validation
buffers, preview command buffers, debug MDIC command buffers, or sample-set
inputs.

VI also introduces original-shaped `renderOpaque`, `renderTemporal`, and
`renderTranslucent` methods with the original model-store, lightmap, shared
index, indirect draw, and parameter-buffer binding order. These methods are not
called by the active Forge render hook yet. Until the original render pipeline
target and `VoxyRenderSystem` lifecycle are ported, the route still must not be
counted as `formalDrawPipelineReady`, `formalRendererReady`, or
`actualRendererDrawEnabled`.

VII is now started at the terrain-shader contract owner level:

```text
ForgeOriginalVoxyRenderPipeline
 -> ForgeOriginalVoxyMdicSectionRenderer terrain/translucent shader build
 -> original quads3.vert / quads.frag source path
 -> pipeline TAA hook / shader patch hook / patched-or-normal fallback shape
```

This mirrors the original `MDICSectionRenderer` relationship with
`AbstractRenderPipeline` closely enough for source ownership and compile-time
contract work. The next VII pass now also ports the original
`NormalRenderPipeline` target ownership shape: the Voxy-owned depth/stencil
framebuffer receives the opaque colour attachment, a separate SSAO/translucent
framebuffer receives the SSAO colour attachment, and the original final blit
shader source is owned by the pipeline. The Forge owner now also has the
original SSAO compute owner, original `SSAO.AUTO` capability selection, the
`postOpaquePreTranslucent(...)` SSAO handoff, and the original
`transformBlitDepth(...)` final depth blit shape. IX now ports original
environmental fog final-blit semantics by capturing Forge 1.20.1
`RenderSystem` fog state into `ForgeOriginalVoxyFogParameters`, storing it on
`ForgeOriginalVoxyMdicViewport`, compiling the final blit with `USE_ENV_FOG`
when `originalVoxyUseEnvironmentalFog=true`, uploading the original uniforms,
and skipping the transform blit when fog covers all Voxy rendering. The Forge
source set still lacks an Oculus pipeline-data bridge equivalent to original
`IrisVoxyRenderPipelineData`, so Oculus-backed shaderpack patch semantics are
still not ported. VII/VIII/IX progress is therefore not renderer readiness and
must not enable visible or live terrain draw as a formal-ready route.

Runtime readback now proves a non-empty MDICViewport render-list and production
`cmdgen.comp` output. The latest audit produced `renderListSectionCount=146`,
`opaqueDrawCount=545`, original draw-count/cull-command layouts, and
`positionScratchReadbackOk=true`. A zero-section render-list still must not be
counted as `V.2_ORIGINAL_CMDGEN_COMP_OUTPUT_PARITY` or
`V.3_ORIGINAL_CMDGEN_READBACK_AND_BARRIER_AUDIT` success.

## Required source trace

Each parity batch must begin by reading the original Voxy component and its
dependencies. For example:

```text
RenderGenerationService
 -> RenderDataFactory
 -> ModelFactory / ModelQueries
 -> BuiltSection
 -> BasicAsyncGeometryManager
```

Do not port a middle layer without confirming the lower layers it depends on.

## Deprecated routes

See:

```text
docs/forge-1.20.1-deprecated-prototype-routes.md
```

Anything listed there is not allowed to become the formal renderer route.

## Success criteria

A subsystem is considered aligned only when:

- the original Voxy source was inspected;
- the Forge implementation owns the same kind of state;
- the data layout matches or has a documented unavoidable Forge adaptation;
- lifecycle and cleanup match original ownership;
- the implementation does not rely on sample/debug/preview substitutes;
- compile passes;
- runtime validation is performed only when that subsystem needs Minecraft/GL.

## Current next direction

The next work should continue bottom-up parity, not preview hardening:

```text
V.1_ORIGINAL_MDIC_VIEWPORT_CMDGEN_INPUT_PARITY
 -> V.2_ORIGINAL_CMDGEN_COMP_OUTPUT_PARITY
 -> V.3_ORIGINAL_CMDGEN_READBACK_AND_BARRIER_AUDIT
 -> VI_ORIGINAL_MDIC_SECTION_RENDERER_CHAIN
 -> VII_ORIGINAL_TERRAIN_SHADER_CONTRACT_CHAIN
 -> VIII_ORIGINAL_VISIBLE_RENDERER_OWNER_CHAIN
 -> IX_ORIGINAL_RUNTIME_FOG_AND_LIFECYCLE_PARITY
 -> X_ORIGINAL_COMPATIBILITY_AND_DEPRECATED_ROUTE_RETIREMENT
```

Visible preview work must not be treated as progress toward production parity
unless it is being removed or replaced by the original Voxy mechanism.

## VIII implementation

`VIII_ORIGINAL_VISIBLE_RENDERER_OWNER_CHAIN` is implemented as one coherent
Roman round with these internal steps:

```text
VIII.1_ORIGINAL_VOXY_RENDER_SYSTEM_FRAME_ORDER
 -> VIII.2_ORIGINAL_VISIBLE_MDIC_DRAW_SUBMISSION
 -> VIII.3_ORIGINAL_POST_FRAME_DYNAMIC_WORK_AND_STATE_RESTORE
```

The source baseline is original `VoxyRenderSystem.renderOpaque(...)` plus
`AbstractRenderPipeline.runPipeline(...)`, not the historical visible preview
owner. The Forge route uses the existing Embeddium cutout hook only as the
platform entry point, then runs the original-shaped owner sequence:

```text
select/update Viewport
 -> capture current draw framebuffer and viewport
 -> pipeline.preSetup
 -> chunk depth-bound preparation or equivalent depth clear
 -> pipeline.setup
 -> MDICSectionRenderer.renderOpaque
 -> innerPrimaryWork: DownloadStream.tick, AsyncNodeManager.tick, NodeCleaner.tick, HiZ traversal
 -> MDICSectionRenderer.buildDrawCalls
 -> MDICSectionRenderer.renderTemporal
 -> postOpaquePreperation
 -> pipeline.postOpaquePreTranslucent
 -> MDICSectionRenderer.renderTranslucent
 -> pipeline.finish
 -> post-frame UploadStream/model/render-distance work
 -> restore framebuffer, viewport, samplers, buffers, and Embeddium/Minecraft state
```

VIII enables controlled visible LoD draw submission through the original-shaped
`ForgeOriginalVoxyMdicSectionRenderer` and `ForgeOriginalVoxyRenderPipeline`
owners. It does not reintroduce K-era preview draw paths, synthetic geometry,
debug command buffers, or manual-refresh substitutes. The new status evidence
is grouped under `ForgeOriginalVoxyVisibleRendererStats`, including
`originalVoxyRunPipelineOrderUsed`, `originalVisibleMdicDrawSubmissionUsed`,
`originalPipelineFinishCalled`, and `originalVisibleRendererStateRestoreUsed`.

VIII must still report these as false until the full original runtime lifecycle
and compatibility semantics are complete:

```text
formalRendererReady=false
actualRendererDrawEnabled=false
formalDrawPipelineReady=false
earlyUsableLodRendererReady=false
```

Known VIII blockers to carry forward into status:

```text
Oculus/Iris shaderpack pipeline patch semantics are not yet ported
VoxyRenderSystem lifecycle/shutdown/reload ownership is only partially mirrored
runtime movement/update performance parity still needs real-runtime validation
```

## IX implementation

`IX_ORIGINAL_RUNTIME_FOG_AND_LIFECYCLE_PARITY` is implemented as one coherent
Roman round with these internal steps:

```text
IX.1_ORIGINAL_VIEWPORT_FOG_AND_FINAL_BLIT_PARITY
 -> IX.2_ORIGINAL_RENDERER_LIFECYCLE_FLUSH_PARITY
 -> IX.3_ORIGINAL_RUNTIME_STATUS_AUDIT_VISIBILITY
```

The source baseline is original `Viewport`, `NormalRenderPipeline.finish(...)`,
and `VoxyRenderSystem.shutdown()`. The Forge route keeps the Embeddium cutout
hook as a platform entry point only, then maps the missing newer
`Viewport.fogParameters` input through Forge 1.20.1 `RenderSystem` fog state.

IX adds:

```text
originalVoxyUseEnvironmentalFog config
ForgeOriginalVoxyFogParameters
MDICViewport fog parameter storage
NormalRenderPipeline final-blit USE_ENV_FOG define/uniform behavior
fog-covers-all-rendering final-blit skip behavior
DownloadStream.flushWaitClear lifecycle drain
render-state capture clear on stale/reload
status fields for fog and lifecycle flush evidence
```

IX removes environmental fog from the VIII blocker list, but it does not make
the renderer formal-ready. The remaining blockers are Oculus/Iris shaderpack
pipeline-data parity, full `VoxyRenderSystem` lifecycle/reload ownership, and
movement/update performance validation under the original-shaped owner.
