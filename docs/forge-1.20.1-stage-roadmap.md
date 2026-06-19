# Forge 1.20.1 stage roadmap

This document supersedes the old H/I/J/K and K10-preview-era roadmap.

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

## Active roadmap: Voxy parity remediation

The active roadmap is component parity with the original renderer:

```text
P0. Source audit and substitute retirement
P1. ModelBakerySubsystem / ModelFactory / ModelStore parity
P2. SoftwareModelTextureBakery / TextureUtils / ModelQueries parity
P3. RenderGenerationService parity
P4. RenderDataFactory parity
P5. BuiltSection output and BasicAsyncGeometryManager parity
P6. BasicSectionGeometryData and geometry heap ownership parity
P7. RenderDistanceTracker / HierarchicalOcclusionTraverser parity
P8. ViewportSelector / Viewport / MDICViewport parity
P9. cmdgen.comp input/output parity
P10. MDICSectionRenderer parity
P11. Original terrain shader contract parity
P12. Integrated VoxyRenderSystem lifecycle parity
```

The P labels are planning labels for parity remediation, not feature demos.

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
complete RenderDataFactory parity
 -> restore original model-miss request/requeue semantics
 -> introduce RenderGenerationService-style async ownership
 -> feed BasicAsyncGeometryManager-style section upload/swap
```

Visible preview work must not be treated as progress toward production parity
unless it is being removed or replaced by the original Voxy mechanism.
