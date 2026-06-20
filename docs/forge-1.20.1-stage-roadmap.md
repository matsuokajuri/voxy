# Forge 1.20.1 Roman stage roadmap

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

New implementation stages use Roman numerals only:

```text
I, II, III, IV, V, VI, VII, VIII, IX, X
```

Substages use dotted Roman-major labels:

```text
V.1_ORIGINAL_MDIC_VIEWPORT_CMDGEN_INPUT_PARITY
V.2_ORIGINAL_CMDGEN_COMP_OUTPUT_PARITY
```

Old alphanumeric labels such as `H`, `I5`, `J2`, `K10`, `K23-K54`, and `L1`
are historical labels only. They must not be used for new implementation work.

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

These Roman labels are parity-remediation stages, not feature demos.

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

Missing before Roman stage II model/upload parity can be considered complete:

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
part of the active route and have runtime proof for real HiZ traversal against
the Minecraft depth attachment.

The next active stage is:

```text
V.1_ORIGINAL_MDIC_VIEWPORT_CMDGEN_INPUT_PARITY
```

It must close the contract from `MDICViewport` / HOC render-list output into
the original production `cmdgen.comp` inputs. It must not reuse K-era synthetic
cmdgen validation buffers or preview command buffers as formal inputs.

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
 -> VI_ORIGINAL_MDIC_SECTION_RENDERER_CHAIN
 -> VII_ORIGINAL_TERRAIN_SHADER_CONTRACT_CHAIN
```

Visible preview work must not be treated as progress toward production parity
unless it is being removed or replaced by the original Voxy mechanism.
