# Forge 1.20.1 original Voxy full render-path parity audit

This is the current route-control ledger. It supersedes old K-stage and preview
audit language.

## Current rule

```text
strict bottom-up original Voxy parity
```

Every subsystem must be traced to original Voxy source before implementation.
Deprecated preview, sample, synthetic, fallback, and QA-only paths must not be
extended.

## Current verdict

```text
VOXY_PARITY_INCOMPLETE
FORMAL_RENDERER_READY=false
ACTUAL_RENDERER_DRAW_ENABLED=false
```

The project has visible historical proof paths, but those are deprecated. The
renderer is not parity-complete until the original Voxy chain is ported.

## Authoritative original chain

```text
VoxyRenderSystem
 -> WorldEngine / WorldSection / Mapper
 -> ModelBakerySubsystem
 -> ModelFactory
 -> SoftwareModelTextureBakery / SoftwareRasterizer / ReuseVertexConsumer
 -> TextureUtils / ModelQueries
 -> ModelStore
 -> RenderGenerationService
 -> RenderDataFactory
 -> BuiltSection
 -> BasicAsyncGeometryManager
 -> BasicSectionGeometryData
 -> RenderDistanceTracker
 -> HierarchicalOcclusionTraverser
 -> ViewportSelector / Viewport
 -> MDICViewport
 -> cmdgen.comp
 -> MDICSectionRenderer
 -> quads3.vert / quads.frag / block_model.glsl / quad_util.glsl
```

## Confirmed drift

| Area | Drift | Required correction |
| --- | --- | --- |
| Model texture bake | first-quad/sprite extraction and safe-set rejection replaced original software bake semantics | Port/adapt `SoftwareModelTextureBakery`, `SoftwareRasterizer`, `TextureUtils` |
| Model lifecycle | safe-set upload does not match original on-demand model request/requeue | Port `ModelBakerySubsystem` and `ModelFactory` lifecycle |
| Geometry generation | temporary rewrites / CPU mesh conversion are not original `RenderDataFactory` | Complete `RenderDataFactory` parity from `WorldSection` raw data |
| Geometry ownership | Forge debug/simple heap paths are not original `BasicAsyncGeometryManager` | Port allocation, 128-record alignment, section id reuse, upload/free lifecycle |
| Visibility | radius/frustum/preview snapshots are not original traversal | Port `RenderDistanceTracker` and `HierarchicalOcclusionTraverser` |
| Command generation | validation compute shaders are not production `cmdgen.comp` | Wire original `cmdgen.comp` inputs/outputs |
| Draw owner | visible preview owner is not `MDICSectionRenderer` | Port `MDICViewport` and `MDICSectionRenderer` ownership |
| Shader semantics | adapter/subset shader is not full original terrain shader contract | Port original shader inputs and semantics |

## Corrections already started

These are directionally correct but not complete readiness:

- Forge-adapted `ForgeSoftwareModelTextureBakery` exists.
- Forge-local `ForgeModelQueries` exists.
- formal direct section geometry is moving toward `RenderDataFactory`-style
  `WorldSection` raw-data generation.
- sample/preview/synthetic routes are explicitly deprecated.
- `ForgeOriginalVoxyModelPipeline` now owns the first L0/L1 parity boundary:
  it starts from the active `WorldEngine`, queues existing mapper biomes,
  attaches the mapper biome callback, and queues block bake requests for the
  future `RenderGenerationService` path.
- `ForgeOriginalVoxyModelFactory` now ports the original `ModelFactory`
  mapping core into the Forge source set:

```text
idMappings[1<<20]
metadataCache[1<<16]
fluidStateLUT[1<<16]
modelTexture2id dedupe
fluid pre-bake ordering
stair base-state normalization
SoftwareModelTextureBakery colour/depth bake
formal ModelStore modelData/modelColour/atlas upload
```

This is not full `ModelBakerySubsystem` parity yet. The original background
processor thread, upload queue separation, biome LUT uploads, and mip-chain
atlas upload still need to be ported.

## Source-set reality

The authoritative original Voxy files under `me.cortex.voxy.client.core.*` are
reference sources in this repository, but the current Forge Gradle source set
only compiles the Forge package, selected platform/config/common files, and
world/storage utilities.

That means the Forge route must port/adapt original mechanisms under
`me.cortex.voxy.forge` instead of directly importing the original client-core
classes. Direct import of `ModelBakerySubsystem` was tested and rejected by
`compileJava` because the package is outside the active source set.

This is not a license to substitute behavior. The Forge implementation must
still match original ownership, data layout, lifecycle, and performance
semantics.

## Remaining bottom-up parity work

1. Complete Forge-port `ModelBakerySubsystem` parity, including the original
   processing thread, `LockSupport.unpark` lifecycle, upload-result queue, and
   render-thread `processUploads()` behavior.
2. Harden the Forge-port `ModelFactory` against original semantics: readback
   audit, exact `TextureUtils` parity, biome colour LUT uploads, custom
   block-state id mapping, mip-chain atlas upload, and documented Forge-only
   stair base-state reflection.
3. Complete `SoftwareModelTextureBakery` runtime parity for solid, leaves,
   cutout, translucent, fluid, tint, and atlas sampling.
4. Complete `ModelStore` upload parity against the formal owner, including
   modelData/modelColour/atlas layout and upload thread rules.
5. Complete `RenderDataFactory` parity, including neighbor-section logic,
   opaque/non-opaque buckets, fluid model lookup, light/biome packing, and
   greedy merge behavior.
6. Port `RenderGenerationService` task queue and result-consumer behavior.
7. Port `BasicAsyncGeometryManager` and `BasicSectionGeometryData`.
8. Port `RenderDistanceTracker` and `HierarchicalOcclusionTraverser`.
9. Port `MDICViewport` and production `cmdgen.comp`.
10. Port `MDICSectionRenderer` and original terrain shader binding order.
11. Port `VoxyRenderSystem` lifecycle only after the lower owners match.

## Current documented Forge deviations

| Area | Reason | Status |
| --- | --- | --- |
| `StairBlock.baseState` access | original source accesses the field directly; Forge 1.20.1 exposes it as private at compile time | Forge port uses a cached reflective field read to preserve original normalization semantics |
| `ModelBakerySubsystem` worker | original uses a dedicated `"Model factory processor"` thread; current Forge port processes a bounded number of model uploads on the render thread | temporary incomplete parity, not renderer readiness |
| Atlas mip upload | original `ModelBakeResultUpload` uploads the packed 3x2 model tile for every mip level | formal Forge store currently uploads base face tiles only |
| Biome/model colour LUT | original uploads per-biome colour data for biome-dependent tinted models | owner hook exists, upload parity pending |

## Do not do

Do not use:

```text
sample-set data as formal model source
synthetic fixtures as success conditions
temporary model-id rewrites as geometry path
visible preview owner as renderer owner
debug command buffers as formal command buffers
manual refresh commands as lifecycle substitute
adapter shader as production terrain shader
```

## Current next work

Continue with bottom-up parity:

```text
complete Forge-port ModelBakerySubsystem / ModelFactory parity
 -> finish SoftwareModelTextureBakery and TextureUtils parity
 -> restore model-miss request/requeue
 -> introduce RenderGenerationService-style async generation
 -> replace direct unit-quad geometry with RenderDataFactory parity
```
