# Forge 1.20.1 formal renderer readiness audit

This document supersedes the old preview/debug readiness audit.

Readiness is now measured only against original Voxy parity. New implementation
work uses Roman-major rounds with dotted Arabic steps inside each round.
Preview pixels,
sample-set uploads, synthetic GPU validation, offscreen smoke tests, and old
K-era visible preview output do not count as formal renderer readiness.

## Current verdict

```text
FORMAL_RENDERER_READY=false
ACTUAL_RENDERER_DRAW_ENABLED=false
FORMAL_DRAW_PIPELINE_READY=false
EARLY_USABLE_LOD_RENDERER_READY=false
```

Reason:

```text
the original Voxy ownership chain is not fully ported yet
```

## Required Forge frontends

The Forge renderer parity route requires:

```text
Embeddium
Oculus
```

Embeddium replaces the original Sodium frontend on Forge. Oculus replaces the
original Iris/shaderpack frontend on Forge. Their presence does not make the
renderer ready; it only establishes the correct hard prerequisites for porting
the original Voxy ownership chain.

## Required original owners

The formal renderer cannot be ready until Forge has parity for:

```text
ModelBakerySubsystem
ModelFactory
ModelStore
SoftwareModelTextureBakery
TextureUtils
ModelQueries
RenderGenerationService
RenderDataFactory
BasicAsyncGeometryManager
BasicSectionGeometryData
RenderDistanceTracker
HierarchicalOcclusionTraverser
ViewportSelector
Viewport
MDICViewport
cmdgen.comp
MDICSectionRenderer
terrain shader contract
VoxyRenderSystem lifecycle
```

## Deprecated evidence

These are historical evidence only and must not be counted as readiness:

```text
sample-set ModelStore records
sample atlas upload/readback
debug MDIC renderer output
textured debug renderer output
formal shader input bridge using sample-set data
J-stage offscreen previews
K-stage visible preview owner
synthetic command generation validation
K10/K23-K54 preview movement/update results
```

## Current positive progress

Some bottom-path corrections are now aligned in direction:

- Forge-adapted `SoftwareModelTextureBakery` now uses the original-shaped
  output buffer, `ReuseVertexConsumer`, `SoftwareRasterizer`, Embeddium
  `BakedQuadView` material signals, and Embeddium sprite transparency metadata
  for the active Forge/Embeddium route.
- Forge-local original-parity `ModelQueries` exists.
- Forge-local original-parity `UploadStream` persistent mapped staging now
  backs the active original model upload route.
- The active original model upload route now performs post-commit readback
  audit for modelData, optional modelColour, and the atlas mip-chain region, and
  receives Oculus `WorldRenderingSettings` custom block-state ids through the
  original Voxy hook shape.
- Forge-local original-parity `RenderGenerationService` and
  `RenderDataFactory` now generate `BuiltSection` output from raw
  `WorldSection` data and request/requeue missing models.
- Forge-local original-parity `BasicAsyncGeometryManager` now consumes
  generated `BuiltSection` output into original-style section id allocation,
  geometry heap allocation, 128-record alignment, metadata packing, and
  upload/remove/update event sets.
- Forge-local original-parity `BasicSectionGeometryData` now owns the
  render-thread metadata/geometry buffers with the original capacity policy,
  sparse-buffer workaround, sparse commitment growth, and free lifecycle.
- Forge-local original-parity async geometry sync now applies
  `BasicAsyncGeometryManager` event sets to `BasicSectionGeometryData` through
  original-style `SyncResults`, persistent `UploadStream`, `memcpy.comp`, and
  `scatter.comp` instead of immediately draining them.
- Forge-local original-parity `NodeManager`, `NodeStore`,
  `SectionUpdateRouter`, `NodeCleaner`, `GeometryCache`, and
  `RenderDistanceTracker` are now wired into the active parity pipeline.
- Forge-local original-parity `ViewportSelector`, `MDICViewport`, HiZ owner,
  original depth/stencil setup, and `HierarchicalOcclusionTraverser` owner now
  have runtime proof for real HiZ traversal from a Voxy-owned depth framebuffer.
- Roman V/VI production command-generation readback now passes without debug
  command buffers for the original-shaped `MDICSectionRenderer` owner path:
  HOC produced a non-empty render-list, `cmdgen.comp` produced opaque draw
  commands, position-scratch readback matched the original layout, and the
  owner now includes the original prefix-sum / `buildtranslucents.comp`
  translucent command-build tail.
- Roman VII has started the original terrain-shader contract chain:
  `ForgeOriginalVoxyRenderPipeline` owns the `AbstractRenderPipeline`-shaped
  shader hook boundary, and `ForgeOriginalVoxyMdicSectionRenderer` now builds
  original `quads3.vert` / `quads.frag` terrain and translucent programs
  through the original TAA hook, shader patch hook, and patched-or-normal
  fallback shape. The Forge pipeline now also owns the original-shaped
  `NormalRenderPipeline` opaque colour target, SSAO/translucent colour target,
  framebuffer attachment handoff, SSAO compute owner, original `SSAO.AUTO`
  capability selection, `postOpaquePreTranslucent(...)`, and final
  `transformBlitDepth(...)` handoff.
- Roman VIII now submits visible MDIC terrain draw through the original-shaped
  owner sequence from the Embeddium cutout hook: `preSetup`, `setup`,
  `renderOpaque`, HOC inner work, production `buildDrawCalls`,
  `renderTemporal`, `postOpaquePreTranslucent`, `renderTranslucent`, `finish`,
  post-frame dynamic work, and original-style GL state restore. This is progress
  toward original frame order parity, not full renderer readiness.
- Roman IX now ports original environmental fog final-blit semantics and
  lifecycle drain visibility: Forge captures the render fog start/end/color
  state into `ForgeOriginalVoxyFogParameters`, stores it on
  `ForgeOriginalVoxyMdicViewport`, compiles the final blit with `USE_ENV_FOG`
  when enabled, uploads the original fog uniforms, skips the final transform
  blit when fog covers all Voxy rendering, clears captured render state on
  stale/reload, and flushes `ForgeOriginalVoxyDownloadStream` around render
  resource teardown when present.
- `RenderGenerationService` now uses original `ServiceManager` / `Service` /
  `UnifiedServiceThreadPool` execution, including service-thread config and
  Embeddium builder-thread semaphore sharing.
- substitute preview/sample routes are marked deprecated.

These are progress toward parity, not renderer readiness.

## Blocking gaps

P0 gaps:

```text
VoxyRenderSystem lifecycle / shutdown / reload ownership incomplete
original terrain shader Oculus/Iris shaderpack patch semantics incomplete
movement and runtime update performance parity still needs real-runtime validation
```

P1 gaps:

```text
full lightmap semantics
full biome tint semantics
full material/alpha semantics
translucency behavior
shaderpack integration policy
resource reload automation parity
```

## Readiness rule

A future status field may become true only if it is backed by the original
Voxy-equivalent owner, not a deprecated substitute.

Example:

```text
formalVisibilityTraversalReady=true
```

requires Forge parity with original `RenderDistanceTracker` and
`HierarchicalOcclusionTraverser`. A radius/frustum/debug snapshot does not
qualify.

## Validation rule

Validation must prove parity against original inputs and outputs:

- model metadata matches original meaning;
- section records are produced by `RenderDataFactory`-style logic;
- geometry allocation/free/reuse follows original manager semantics;
- visibility and render lists come from original traversal semantics;
- command generation uses production `prep.comp`, cull raster visibility, and
  `cmdgen.comp`;
- draw submission is owned by the formal MDIC renderer;
- deprecated preview/sample/debug paths are absent from the formal route.
