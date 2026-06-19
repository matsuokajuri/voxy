# Forge 1.20.1 deprecated prototype routes

This document marks legacy proof, preview, fallback, and sample paths that must
not be extended as the formal renderer route.

## Controlling rule

The Forge migration must now follow original Voxy parity first:

```text
find original Voxy owner / data structure / shader / algorithm
 -> inspect original behavior
 -> port or adapt the mechanism to Forge 1.20.1
 -> document only unavoidable Forge-specific deviations
```

Do not replace an original Voxy mechanism with a convenient Forge/debug/sample
path. If a legacy path remains in code, it is for historical comparison or
temporary regression evidence only.

## Deprecated code routes

These classes are now deprecated as implementation direction:

```text
ForgeModelSampleSet
ForgeModelAtlasSampleSetUploader
ForgeRealModelStoreSample
ForgeFormalShaderInputBridge
ForgeFormalTexturedShaderPreview
ForgeFormalPackedQuadPreview
ForgeFormalTerrainPackedRecordBridge
ForgeFormalCmdgenGpuValidator
ForgeFormalCmdgenRealSectionDryRun
ForgeFormalIsolatedMdicDrawSmokeTest
ForgeFormalTerrainShaderIntegration
ForgeFormalVisibleLodPreview
ForgeVoxyCommands legacy monolithic debug/prototype command surface
ForgeDirectGpuGeometryRenderer
ForgeSimpleGpuMeshRenderer
ForgeDebugMeshRenderer
ForgeGpuGeometryReadbackDebugRenderer
ForgeMdicDebugRenderer
ForgeTexturedDebugQuadRenderer
ForgeTexturedReadbackRenderer
ForgeTexturedMdicDebugRenderer
```

They must not feed new formal readiness, new production ownership, or new K/L
renderer route decisions.

## Deprecated route patterns

The following patterns are deprecated:

```text
sample-set model data as formal source
sample-set atlas upload as formal atlas source
synthetic packed-quad fixture as success condition
offscreen validation shader as terrain shader substitute
visible preview owner as renderer owner
manual QA refresh as lifecycle substitute
temporary model-id rewrite as geometry path
BakedQuad CPU mesh back-conversion as RenderDataFactory substitute
radius/frustum debug candidate snapshots as traversal substitute
validation cmdgen compute shader as production cmdgen.comp
giant all-in-one command files as the home for new parity work
debug pipeline clear commands as lifecycle substitutes
```

## Not deleted yet

These routes are not all physically deleted yet because command registration,
status aggregation, and historical stage audits still reference them. Removing
them must be done in controlled batches that keep the project compiling.

Until removed, they are explicitly deprecated and must remain isolated from the
new formal parity route.

## Command surface status

`ForgeVoxyCommands` is now treated as a legacy monolithic command surface. It
still exists because many historical status, clear, and QA handlers reference
prototype objects that have not yet been safely removed from `ForgeVoxyInstance`.

New original-Voxy parity work must not add more branches to that file. The
retirement direction is:

```text
split current-route commands into focused parity registrars
 -> move legacy debug/prototype commands behind an explicit legacy surface
 -> remove command handlers once their backing prototype objects are deleted
 -> delete the old monolithic command file
```

The command `/voxy parity_route_status` exists only to make this boundary
visible while the old command surface is still compiled.

## Replacement direction

Replacement work must follow the original Voxy chain:

```text
ModelBakerySubsystem / ModelFactory / ModelStore
 -> SoftwareModelTextureBakery / TextureUtils / ModelQueries
 -> RenderGenerationService
 -> RenderDataFactory
 -> BasicAsyncGeometryManager / BasicSectionGeometryData
 -> RenderDistanceTracker / HierarchicalOcclusionTraverser
 -> MDICViewport
 -> cmdgen.comp
 -> MDICSectionRenderer
 -> original terrain shader contract
```

Any future Forge-specific adaptation must be documented in the parity audit
before it becomes part of the route.
