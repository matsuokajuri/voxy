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

## Cleanup status, 2026-06-22

The 2026-06-22 cleanup pass physically removed the listed debug, preview,
sample-set, direct-GL, textured-readback, visible-LoD-preview, K-stage formal
owner, and offscreen-validation route families from active source.

The step-by-step deletion record is:

```text
docs/forge-1.20.1-debug-preview-cleanup-2026-06-22.md
```

Shared data/layout helpers that were still active were renamed away from old
Sample/Formal route names during the same cleanup pass:

```text
ForgeModelAtlasLayout
ForgeModelAtlasPixelFormat
ForgeOriginalUploadedModelSummary
ForgeOriginalVoxyModelStoreLayoutSpec
```

These retained helpers are active original-Voxy model/atlas layout utilities,
not preview owners or debug renderers.

## Command surface status

`ForgeVoxyCommands` is no longer the legacy monolithic command surface. It now
only registers the parity command surface:

```text
ForgeVoxyCommands
 -> ForgeVoxyParityCommands
```

The old debug/prototype/preview/preset/formal-owner command registrars were
deleted. New original-Voxy parity work must stay in focused parity surfaces and
must not recreate the old monolithic handler pattern.

## Follow-up cleanup result, 2026-06-22

The follow-up cleanup removed or isolated the deprecated surfaces that the first
cleanup pass still left compiled:

```text
ForgeVoxyRuntimeOverrides legacy preset/override methods
ForgeVoxyConfig keys for removed debug/preview routes
ForgeGpuGeometryVisualization* GL-heap visualization sample route
ForgeGpuGeometryReadbackMesh* GL-heap readback mesh sample route
ForgeGpuMeshUploadManager / ForgeGpuMeshCache / SimpleGpuMesh* simple-GPU preview route
ForgeMdicCommandManager stress/debug-only helpers
ForgeMdicCommandLayout historical DEBUG_DRAW stage label
ForgeModelBridgeResourceReloadTracker texturedMdicDebugStale flag
ForgeVoxyInstance stale debug-pipeline log wording
ForgeModelBridgeReadiness / ForgeBakedModelBridge diagnostic owners
ForgeModelStoreSkeleton.dumpSample
ForgeVoxyQuadEncoder.packPreviewRecord
ForgeRealModelStoreFaceSample
ForgeModelAtlasPixelSample active-utility name
ForgeFormalUploadedModelSummary active-cache name
ForgeModelStoreFormalLayout active-layout name
ForgeMdicCommandBuffer.bufferIdForDebugRenderer
ForgeGpuGeometryHeap.geometryBufferIdForDirectRenderer
ForgeGpuGeometryUploadManager validateSample/auditSample manual helper route
ForgeGpuGeometryDecodedMetadata active metadata-view name
ForgeMdicCommandPlanner.createFaceMaskPlanForAudit
```

The simple-GPU / GL-heap visualization / readback-mesh / MDIC debug draw route
families no longer have active Forge source files or config/runtime switches.
The retained low-level CPU geometry helpers are current original-Voxy
model/geometry staging code, not the deleted simple-GPU preview renderer.

Remaining naming debt:

```text
Oculus/Iris Samplers files still match naive "sample" filename scans because
sampler is the shaderpack API term. They are active shaderpack bridge code.
```

The 2026-06-23 follow-up cleanup removed the isolated placeholder model-store
family that previously kept old fake model-data/status vocabulary alive:

```text
ForgeModelStoreSkeleton
ForgeModelDataBuffer
ForgeModelStoreRecord
ForgeModelStoreStats
ForgeModelStoreAuditResult
ForgeModelStoreLayout
ForgeModelStoreLayoutAuditor
ForgeModelStoreLayoutAuditResult
```

`ForgeVoxyModelIdMapper` still exists only for legacy CPU-geometry
compatibility/status paths. It is not the active original model-id owner; the
active route uses `ForgeOriginalVoxyModelFactory` and
`ForgeOriginalVoxyModelStore`.

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

The 2026-06-22 lifecycle/light repair retires two more non-original shortcuts
from the active route:

```text
the Forge-only synchronous WorldEngine save callback that saved immediately and
returned false

the Embeddium single-section raw light ingest path that bypassed the
chunk-aware light-layer handling
```

The replacement path is active-route parity work, not a new prototype route:
`WorldEngine.setSaveCallback(...)` now uses the original `SectionSavingService`
ownership shape, and single-section Embeddium updates now reuse
`VoxelIngestService.ingestChunkSectionWithStats(...)`. Active Forge worlds are
also detached before delayed idle free, matching the original `WorldEngine`
idle-cleanup rule more closely than the old immediate skeleton close.

The follow-up LoD darkness repair also removes the Forge-only
`Level.getBrightness(...)` fallback from the active ingest path. A short-lived
attempt to use Embeddium clone-section missing-light defaults is documented as
incorrect for persistent Voxy LoD data. The active route now defers non-air
sections in sky-lit dimensions when the real sky `DataLayer` is absent, and
keeps the chunk eligible for later retry instead of writing guessed lighting.

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
