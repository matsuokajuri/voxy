# Forge 1.20.1 formal renderer readiness audit

This document supersedes the old preview/debug readiness audit.

Readiness is now measured only against original Voxy parity. Preview pixels,
sample-set uploads, synthetic GPU validation, offscreen smoke tests, and K10
visible preview output do not count as formal renderer readiness.

## Current verdict

```text
FORMAL_RENDERER_READY=false
ACTUAL_RENDERER_DRAW_ENABLED=false
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
- `RenderGenerationService` now uses original `ServiceManager` / `Service` /
  `UnifiedServiceThreadPool` execution, including service-thread config and
  Embeddium builder-thread semaphore sharing.
- substitute preview/sample routes are marked deprecated.

These are progress toward parity, not renderer readiness.

## Blocking gaps

P0 gaps:

```text
HierarchicalOcclusionTraverser parity incomplete
Viewport / HiZ ownership incomplete
MDICViewport parity incomplete
production cmdgen.comp integration incomplete
MDICSectionRenderer parity incomplete
VoxyRenderSystem lifecycle parity incomplete
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
- command generation uses production `cmdgen.comp`;
- draw submission is owned by the formal MDIC renderer;
- deprecated preview/sample/debug paths are absent from the formal route.
