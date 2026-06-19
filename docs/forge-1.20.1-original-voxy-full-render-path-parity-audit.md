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

## Remaining bottom-up parity work

1. Complete `SoftwareModelTextureBakery` runtime parity for solid, leaves,
   cutout, translucent, fluid, tint, and atlas sampling.
2. Complete `ModelFactory` metadata, fluid LUT, dedupe, biome colour, and
   on-demand model request/requeue behavior.
3. Complete `RenderDataFactory` parity, including neighbor-section logic,
   opaque/non-opaque buckets, fluid model lookup, light/biome packing, and
   greedy merge behavior.
4. Port `RenderGenerationService` task queue and result-consumer behavior.
5. Port `BasicAsyncGeometryManager` and `BasicSectionGeometryData`.
6. Port `RenderDistanceTracker` and `HierarchicalOcclusionTraverser`.
7. Port `MDICViewport` and production `cmdgen.comp`.
8. Port `MDICSectionRenderer` and original terrain shader binding order.
9. Port `VoxyRenderSystem` lifecycle only after the lower owners match.

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
complete RenderDataFactory parity
 -> restore model-miss request/requeue
 -> introduce RenderGenerationService-style async generation
 -> feed BasicAsyncGeometryManager-style section upload/swap
```
