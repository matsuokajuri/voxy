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

## Active roadmap: L-stage Voxy parity remediation

The active roadmap is component parity with the original renderer:

```text
L0. Source audit and substitute retirement
L1. Mapper / WorldEngine / original model-pipeline owner boundary
L2. ModelBakerySubsystem / ModelFactory / ModelStore parity
L3. SoftwareModelTextureBakery / TextureUtils / ModelQueries parity
L4. RenderGenerationService parity
L5. RenderDataFactory parity
L6. BuiltSection output and BasicAsyncGeometryManager parity
L7. BasicSectionGeometryData and geometry heap ownership parity
L8. RenderDistanceTracker / HierarchicalOcclusionTraverser parity
L9. ViewportSelector / Viewport / MDICViewport parity
L10. cmdgen.comp input/output parity
L11. MDICSectionRenderer parity
L12. Original terrain shader contract parity
L13. Integrated VoxyRenderSystem lifecycle parity
```

The L labels are parity-remediation stages, not feature demos.

## Current L0/L1 status

The Forge route now has a focused original-Voxy model-pipeline owner boundary:

```text
ForgeOriginalVoxyModelPipeline
```

It follows the original `VoxyRenderSystem` ordering at the boundary level:

```text
WorldEngine / Mapper
 -> existing biome entries queued
 -> mapper biome callback attached
 -> block model requests are queued for the future RenderGenerationService path
```

It does not yet claim `ModelFactory` or `SoftwareModelTextureBakery` parity.
The original `me.cortex.voxy.client.core.*` files are authoritative source
references but are not included in the current Forge compile source set, so the
mechanisms must be ported/adapted under `me.cortex.voxy.forge` rather than
directly imported.

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
complete Forge-port ModelBakerySubsystem / ModelFactory parity
 -> restore original model-miss request/requeue semantics
 -> port SoftwareModelTextureBakery output into the formal ModelStore owner
 -> introduce RenderGenerationService-style async ownership
 -> replace direct unit-quad section geometry with RenderDataFactory parity
```

Visible preview work must not be treated as progress toward production parity
unless it is being removed or replaced by the original Voxy mechanism.
