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

They must not feed new formal readiness, new production ownership, or new
Roman-numeral renderer route decisions.

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

## Current non-deprecated parity entry

The new parity command registrar is:

```text
ForgeVoxyParityCommands
```

It currently exposes `original_voxy_model_pipeline_*` commands for the active
model-pipeline owner. This is not a preview route. The command surface now
reports the Forge-port original `ModelFactory` mapping/upload state and the
first original render-generation parity state. The original worker thread,
upload-result queue, biome LUT upload, packed mip-chain atlas upload,
persistent-mapped `UploadStream` staging, `RenderGenerationService`
missing-model request/requeue, and `RenderDataFactory` raw-section mesh output
are now part of the active parity route, not the deprecated preview route.
The original model upload path also has committed readback proof and the
Oculus custom block-state id hook.

Remaining lower-renderer blockers are full integrated `VoxyRenderSystem`
lifecycle, patched shaderpack terrain compile parity, the shaderpack far-LoD
white/bright visual artifact seen in the 2026-06-21 runtime passes, and
movement/update performance parity. Production
`cmdgen.comp`, `MDICSectionRenderer`, the original terrain shader hook
boundary, render-thread geometry ownership, node ownership, RenderDistanceTracker,
HiZ, HOC traversal, ViewportSelector, MDICViewport, and the Oculus shaderpack
patch bridge now belong to the active Roman-route parity path.

The 2026-06-21 real-pack check with `ComplementaryUnbound_r5.8.1.zip` is
active-route evidence only: the post-repair pass validates Oculus shaderpack
loading, Voxy shaderpack sidecar discovery, world entry, formal owner startup,
Oculus shaderpack pipeline data consumption, and original MDIC draw submission.
It does not flip formal renderer readiness, because the full `VoxyRenderSystem`
owner is still incomplete and the patched opaque/translucent shaderpack terrain
programs currently fall back to the normal shader path.

The follow-up drift audit in
`docs/forge-1.20.1-deep-runtime-drift-audit-2026-06-21.md` confirms that the
visible LoD pixels in that run were submitted by `ForgeSimpleGpuMeshRenderer`,
not by the formal original MDIC renderer. Runtime presets such as `lod`,
`overlay`, and `gl_heap_readback` therefore remain legacy/debug validation
routes even when they produce on-screen LoD-like pixels. The simple GPU
renderer is now suppressed while the formal original model pipeline start is
requested, queued, or active, but that suppression is only route isolation; it
does not make the simple GPU renderer part of formal readiness. In the later
post-repair pass, formal MDIC submission replaced simple-GPU evidence, but the
shaderpack patched shader fallback remains a degraded active-route blocker.

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
 -> Oculus shaderpack patch/data/binding bridge
 -> VoxyRenderSystem lifecycle
```

Any future Forge-specific adaptation must be documented in the parity audit
before it becomes part of the route.
