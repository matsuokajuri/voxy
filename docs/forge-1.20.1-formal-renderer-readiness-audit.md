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

- Forge-adapted `SoftwareModelTextureBakery` exists.
- Forge-local original-parity `ModelQueries` exists.
- Forge-local original-parity `UploadStream` persistent mapped staging now
  backs the active original model upload route.
- Forge-local original-parity `RenderGenerationService` and
  `RenderDataFactory` now generate `BuiltSection` output from raw
  `WorldSection` data and request/requeue missing models.
- substitute preview/sample routes are marked deprecated.

These are progress toward parity, not renderer readiness.

## Blocking gaps

P0 gaps:

```text
BasicAsyncGeometryManager parity incomplete
BasicSectionGeometryData parity incomplete
RenderDistanceTracker parity incomplete
HierarchicalOcclusionTraverser parity incomplete
MDICViewport parity incomplete
production cmdgen.comp integration incomplete
MDICSectionRenderer parity incomplete
VoxyRenderSystem lifecycle parity incomplete
original common ServiceManager parity incomplete
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
