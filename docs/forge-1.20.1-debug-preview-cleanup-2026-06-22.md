# Forge 1.20.1 debug / preview cleanup log, 2026-06-22

> **HISTORICAL CLEANUP LOG.** This file records the 2026-06-22 cleanup sequence;
> it is no longer the active route/status document. Use
> `forge-1.20.1-original-voxy-full-render-path-parity-audit.md` and
> `forge-1.20.1-deprecated-prototype-routes.md` for current ownership and
> remaining retirement work.

This document records the controlled retirement of historical debug, preview,
proof, and fallback routes from the Forge port.

The goal is not to hide evidence. The goal is to leave the active codebase
centered on the original Voxy parity route, while removing old routes in
controlled batches that keep the project compiling.

## Cleanup rule

Use this deletion standard for every batch:

```text
CodeGraph first
 -> identify every caller / field / registration point
 -> delete only routes that are not needed by the active original parity path
 -> keep temporarily only when command/status compatibility still requires them
 -> document the reason before or with the code change
 -> compile after each code-removal batch
```

Do not delete:

```text
original Voxy parity owners
Oculus / Embeddium source-equivalent bridge code
diagnostic status needed to distinguish active parity from legacy routes
user-provided files such as AGENTS.md or CODEX.md
```

Do delete or retire:

```text
visible preview renderers
sample-set bridges
synthetic GPU validators
manual QA-only draw routes
debug-only renderers no longer needed by status compatibility
legacy runtime presets that enable non-original visible LoD routes
```

## Canonical reading order

This file was updated as the cleanup ran, so some later sections were appended
after earlier audit blocks. The intended cleanup order is:

```text
Step 0  Baseline status
Step 1  Initial CodeGraph scan
Step 2  Legacy command registrar removal plan
Step 3  Historical geometry/model command registrar removal plan
Step 4  Monolithic legacy command handler removal
Step 5  Instance-only debug renderer removal plan
Step 6  Direct GL debug renderer family removal plan
Step 7  K10 visible LoD preview owner removal plan
Step 8  Sample / prototype / offscreen-validation owner removal and rescan
Step 9  Legacy reload-status cleanup plan
Step 10 GL-heap/simple-GPU route removal plan
Step 11 Runtime/config isolation
Step 12 Old model-bridge diagnostic isolation
Step 13 Sample-name utility split
Step 14 Status-surface route isolation
Step 15 Active layout class rename
Step 16 Hidden method-level old route cleanup
Final audit
```

## Current active-route boundary

The active route remains:

```text
WorldEngine / Mapper
 -> ForgeOriginalVoxyModelFactory / ModelStore
 -> ForgeOriginalVoxyRenderGenerationService
 -> ForgeOriginalVoxyRenderDataFactory
 -> ForgeOriginalVoxyBasicAsyncGeometryManager
 -> RenderDistanceTracker / HOC / MDICViewport
 -> ForgeOriginalVoxyMdicSectionRenderer
 -> Oculus shaderpack patch/data bridge
```

The current bugfix plan for black LoD is tracked separately:

```text
docs/forge-1.20.1-black-lod-bugfix-plan-2026-06-22.md
```

This cleanup pass must not add new black-LoD fixes.

## Step 0. Baseline status

Status before cleanup:

```text
AGENTS.md is modified by the user and must not be reverted or committed by cleanup.
.agents/ and .codegraph/ are local/untracked and must not be committed.
There are existing uncommitted Roman X repair changes in Java and docs.
```

Existing deprecated inventory:

```text
docs/forge-1.20.1-deprecated-prototype-routes.md
```

## Step 1. Initial CodeGraph scan

CodeGraph scan target:

```text
ForgeSimpleGpuMeshRenderer
ForgeDebugMeshRenderer
ForgeGpuGeometryReadbackDebugRenderer
ForgeMdicDebugRenderer
ForgeTexturedDebugQuadRenderer
ForgeTexturedReadbackRenderer
ForgeTexturedMdicDebugRenderer
ForgeDirectGpuGeometryRenderer
ForgeVoxyInstance
ForgeVoxyCommands
```

Result:

```text
ForgeVoxyInstance still constructs and registers multiple deprecated renderers.
ForgeVoxyCommands still references direct GL, simple GPU, MDIC debug, and other legacy command handlers.
These renderers are not safe to delete as a first action because command/status compatibility still references them.
```

Decision:

```text
Do not delete this group yet.
First isolate or delete smaller route pieces with fewer callers.
For this group, the cleanup path is:
  remove command handlers / presets
  remove ForgeVoxyInstance fields and register calls
  delete backing renderer classes
  compile
```

## Candidate batches

### Batch A. Sample-set / formal preview bridge classes

Target class family:

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
```

Required next check:

```text
CodeGraph callers for each class.
```

### Batch B. Visible legacy renderers and presets

Target class family:

```text
ForgeSimpleGpuMeshRenderer
ForgeDirectGpuGeometryRenderer
ForgeDebugMeshRenderer
ForgeGpuGeometryReadbackDebugRenderer
ForgeMdicDebugRenderer
ForgeTexturedDebugQuadRenderer
ForgeTexturedReadbackRenderer
ForgeTexturedMdicDebugRenderer
```

Required next check:

```text
Remove or quarantine ForgeVoxyCommands branches and ForgeVoxyInstance registrations first.
```

### Batch C. Legacy config/runtime override switches

Target area:

```text
ForgeVoxyRuntimeOverrides
legacy preset methods
old direct/simple/readback flags that only enable deprecated routes
```

Required next check:

```text
Confirm active original parity route does not read these switches.
```

## Compile log

No cleanup deletion batch has been compiled yet in this document.

## Remaining temporary compatibility

The following are currently marked temporary compatibility, not active-route
implementation:

```text
ForgeVoxyCommands legacy monolithic command surface
ForgeVoxyInstance deprecated preview/debug fields
old runtime presets that reference simple/direct/debug renderers
```

They should not be extended. Future cleanup should remove references before
deleting their backing classes.

## Step 2. Legacy command registrar removal plan

CodeGraph scan target:

```text
ForgeVoxyLegacyDebugCommands
ForgeVoxyLegacyPreviewCommands
ForgeVoxyLegacyKPreviewCommands
ForgeVoxyPresetCommands
ForgeVoxyCommands.register
```

Result:

```text
ForgeVoxyLegacyDebugCommands registers direct GL, MDIC debug, GL heap
readback, visualization, stress, and audit commands.

ForgeVoxyLegacyPreviewCommands registers J-stage textured preview,
packed-quad preview, terrain-record bridge, textured debug quad,
textured readback, and textured MDIC debug commands.

ForgeVoxyLegacyKPreviewCommands registers K-preview and QA commands,
including K10 visible LoD preview, minimal LoD renderer preview,
moving/expanded/update preview, and auto-update preview commands.

ForgeVoxyPresetCommands registers preset shortcuts that still enable
deprecated proof, preview, or debug routes.
```

Decision:

```text
Remove these four registrars from ForgeVoxyCommands.register.
Delete the four registrar source files after the root registration no longer
references them.

This batch only removes active command entry points. Backing renderer and
preview classes remain compiled until their remaining ForgeVoxyInstance and
method references are removed in later batches.
```

Code change:

```text
Removed legacy debug, legacy preview, legacy K-preview, and legacy preset
registrar calls from ForgeVoxyCommands.register.

Deleted:
src/main/java/me/cortex/voxy/forge/ForgeVoxyLegacyDebugCommands.java
src/main/java/me/cortex/voxy/forge/ForgeVoxyLegacyPreviewCommands.java
src/main/java/me/cortex/voxy/forge/ForgeVoxyLegacyKPreviewCommands.java
src/main/java/me/cortex/voxy/forge/ForgeVoxyPresetCommands.java
```

Verification:

```text
rtk test .\gradlew compileJava
BUILD SUCCESSFUL in 11s

CodeGraph post-change check:
ForgeVoxyCommands.register now registers only geometry pipeline, model
pipeline, parity, formal owner, and formal renderer command groups.
```

## Step 3. Historical geometry/model command registrar removal plan

CodeGraph scan target:

```text
ForgeVoxyGeometryPipelineCommands
ForgeVoxyModelPipelineCommands
ForgeVoxyCommands.register
```

Result:

```text
ForgeVoxyGeometryPipelineCommands still registers manual ingest/build commands,
CPU mesh commands, GL-heap-readback GPU mesh source selection, lod overlay debug,
and debug pipeline commands.

ForgeVoxyModelPipelineCommands still registers model bridge sample dumps,
real sample/model sample set commands, atlas sample upload commands, and I/J
stage QA/skeleton commands.

The newer original-Voxy status and request surface is already in
ForgeVoxyParityCommands.
```

Decision:

```text
Remove these two registrars from ForgeVoxyCommands.register.
Delete the two registrar files.

This removes manual QA/prototype command entry points while leaving remaining
static handlers and backing owners compiled until their references can be
deleted in smaller batches.
```

Code change:

```text
Removed geometry/model command registrar calls from ForgeVoxyCommands.register.

Deleted:
src/main/java/me/cortex/voxy/forge/ForgeVoxyGeometryPipelineCommands.java
src/main/java/me/cortex/voxy/forge/ForgeVoxyModelPipelineCommands.java
```

Verification:

```text
rtk test .\gradlew compileJava
BUILD SUCCESSFUL in 12s

CodeGraph post-change check:
ForgeVoxyCommands.register now registers only parity, formal owner, and formal
renderer command groups.
```

## Step 4. Monolithic legacy command handler removal

CodeGraph scan target:

```text
ForgeVoxyCommands
ForgeVoxyFormalRendererCommands
ForgeVoxyFormalOwnerCommands
ForgeFormalVisibleLodPreview
```

Result:

```text
After the registrar removals, the only registered handlers still needed from
ForgeVoxyCommands were formal_renderer_check/status/enable/disable/clear and
formal_renderer_dump_blockers.

ForgeVoxyFormalOwnerCommands and ForgeFormalVisibleLodPreview still referenced
a small set of formatting helpers.
```

Decision:

```text
Rewrite ForgeVoxyCommands as a small command root and formal renderer status
bridge.

Do not restore old debug/preview/sample handler implementations.
Keep only minimal formatting helpers needed by the still-registered formal
owner/status surface and the still-compiled visible-preview logger.
```

Code change:

```text
Reduced ForgeVoxyCommands from 10968 lines to 164 lines.
Removed legacy command handlers, legacy imports, sample-set handlers, preview
handlers, debug renderer handlers, runtime preset handlers, and legacy status
formatters from the command class.
```

Verification:

```text
First compile after rewrite failed because ForgeVoxyFormalOwnerCommands and
ForgeFormalVisibleLodPreview still referenced formatter helper methods that had
been removed with the monolithic command body.

Added minimal compatibility formatters only:
formatFormalRendererStatusForLog
formatFormalTerrainRendererOwnerStatus / Audit
formatFormalMdicViewportOwnerStatus / Audit
formatFormalCommandGenerationOwnerStatus / Audit
formatFormalVisibilityOwnerStatus / Audit

rtk test .\gradlew compileJava
BUILD SUCCESSFUL in 10s

CodeGraph post-change check:
ForgeVoxyCommands now has 164 lines and 21 symbols.
```

## Step 5. Instance-only debug renderer removal plan

CodeGraph caller scan target:

```text
ForgeDebugMeshRenderer
ForgeGpuGeometryReadbackDebugRenderer
ForgeSimpleGpuMeshRenderer
ForgeMdicDebugRenderer
ForgeTexturedDebugQuadRenderer
ForgeTexturedReadbackRenderer
ForgeTexturedMdicDebugRenderer
```

Result:

```text
Each target is now referenced only by ForgeVoxyInstance field/getter wiring.
Their command entry points were removed in earlier steps, and they are not part
of the original-Voxy parity renderer path.
```

Decision:

```text
Remove their ForgeVoxyInstance fields, event registrations, getters, dimension
switch clears, and logout clears.

Delete the seven renderer source files in the same batch.
```

Code change:

```text
Removed the seven renderer fields, getters, render-event registrations,
dimension-switch clears, and logout clears from ForgeVoxyInstance.

Deleted:
src/main/java/me/cortex/voxy/forge/ForgeDebugMeshRenderer.java
src/main/java/me/cortex/voxy/forge/ForgeGpuGeometryReadbackDebugRenderer.java
src/main/java/me/cortex/voxy/forge/ForgeSimpleGpuMeshRenderer.java
src/main/java/me/cortex/voxy/forge/ForgeMdicDebugRenderer.java
src/main/java/me/cortex/voxy/forge/ForgeTexturedDebugQuadRenderer.java
src/main/java/me/cortex/voxy/forge/ForgeTexturedReadbackRenderer.java
src/main/java/me/cortex/voxy/forge/ForgeTexturedMdicDebugRenderer.java
```

Follow-up fixes from compile:

```text
ForgeGpuGeometryUploadManager no longer clears the removed readback debug
renderer stats.

ForgeModelBridgeResourceReloadTracker no longer marks the removed textured
debug/readback/MDIC debug renderers stale on resource reload.

ForgeFormalRendererManager no longer reads ForgeMdicDebugRenderer as a formal
draw-count readiness input. mdicDrawCountReady remains false until a real
formal draw-count owner is connected.
```

Verification:

```text
First compile after deletion failed on the three tail-reference areas above.

rtk test .\gradlew compileJava
BUILD SUCCESSFUL in 11s

CodeGraph post-change caller checks:
ForgeDebugMeshRenderer, ForgeMdicDebugRenderer,
ForgeTexturedMdicDebugRenderer, and ForgeGpuGeometryReadbackDebugRenderer are
no longer indexed symbols.
```

## Step 6. Direct GL debug renderer family removal plan

CodeGraph scan target:

```text
ForgeDirectGpuGeometryRenderer
ForgeDirectGpuGeometryRenderState
ForgeDirectGpuGeometryDrawPlanner
ForgeDirectGpuGeometryDrawList
ForgeDirectGpuGeometryShader
ForgeDirectGpuGeometryRendererConfig
```

Result:

```text
The direct GL renderer is a historical visible debug renderer with its own
render-event hook, draw list, shader, draw-item buffer, indirect command
buffer, state record, config, and audit result family.

After command cleanup, its active external wiring is ForgeVoxyInstance field,
getter, register, clear, and dimension-change auto-plan logic.
```

Decision:

```text
Remove the ForgeVoxyInstance wiring and delete the ForgeDirectGpuGeometry*
renderer family.

Do not remove the shared Forge GPU geometry heap/upload manager in this batch;
that path is still used by original geometry upload work.
```

Code change:

```text
Removed direct GL renderer field/getter/register/clear/auto-plan wiring from
ForgeVoxyInstance.

Deleted the ForgeDirectGpuGeometry* renderer family:
shader, render state, state guard, renderer, renderer stats/config, draw mode,
draw item/list/buffer, indirect command buffer, indirect audit result, and draw
planner.
```

Follow-up fixes from compile:

```text
Deleted remaining ForgeMdicDebug* buffer/shader/audit/status/config/guard files
that belonged to the removed MDIC debug renderer.

Deleted remaining ForgeTexturedDebug*, ForgeTexturedReadback*, and
ForgeTexturedMdicDebug* helper files that belonged to removed textured preview
renderers.

Removed ForgeModelAtlasPixelUploader.createTexturedDebugQuadSample, which only
served the deleted textured debug quad renderer.
```

Verification:

```text
First compile after direct-family deletion failed on
ForgeMdicDebugRenderStateGuard referencing the deleted direct draw-item buffer.

Second compile failed on ForgeModelAtlasPixelUploader referencing the deleted
ForgeTexturedDebugQuadSample.

rtk test .\gradlew compileJava
BUILD SUCCESSFUL in 10s
```

## Step 8. Sample / prototype / offscreen-validation owner removal plan

CodeGraph and inventory target:

```text
ForgeRealModelStoreSample
ForgeModelSampleSet
ForgeModelAtlasSampleSetUploader
ForgeFormalShaderInputBridge
ForgeFormalTexturedShaderPreview
ForgeFormalPackedQuadPreview
ForgeFormalTerrainPackedRecordBridge
ForgeFormalCmdgenGpuValidator
ForgeFormalCmdgenRealSectionDryRun
ForgeFormalIsolatedMdicDrawSmokeTest
ForgeFormalTerrainShaderIntegration
ForgeFormalRendererManager
ForgeModelBridgeResourceReloadTracker
ForgeVoxyInstance
```

Result:

```text
These owners are sample/prototype/offscreen validation routes. Their command
entry points are already removed. Remaining references are lifecycle/status
aggregation through ForgeVoxyInstance, ForgeModelBridgeResourceReloadTracker,
and ForgeFormalRendererManager.
```

Decision:

```text
Remove the owners from lifecycle wiring and delete their source families.

In ForgeFormalRendererManager, set the removed sample/prototype/offscreen
readiness fields to false instead of treating old validation routes as formal
renderer readiness.
```

Code change:

```text
Removed the sample/prototype/offscreen owners from ForgeVoxyInstance lifecycle.
Removed the corresponding resource-reload stale marks.
Removed formal owner/renderer command registration so /voxy only exposes the
parity command surface.

Deleted the sample/prototype/offscreen source families, including:
ForgeRealModelStoreSample*
ForgeModelSampleSet*
ForgeModelAtlasSampleSet*
ForgeFormalShaderInputBridge / ForgeFormalShaderInputStats
ForgeFormalTexturedShaderPreview*
ForgeFormalPackedQuadPreview*
ForgeFormalTerrainPackedRecordBridge*
ForgeFormalCmdgen*
ForgeFormalIsolatedMdicDrawSmokeTest*
ForgeFormalTerrainShaderIntegration*

Deleted the remaining K-stage formal skeleton/status surface, including:
ForgeVoxyFormalOwnerCommands
ForgeVoxyFormalRendererCommands
ForgeFormalRenderer*
ForgeFormalTerrainRenderer*
ForgeFormalMdicViewport*
ForgeFormalCommandGeneration*
ForgeFormalVisibility*
ForgeFormalModelStore*
ForgeFormalModelFactory*
ForgeFormalModelBakeryLifecycle*
ForgeFormalShaderInputConsumer*
ForgeFormalShaderProgram*
ForgeOneBlockFormalBakeUpload*
ForgeMultiBlockFormalBakeUpload*
ForgeFormalModelIdSectionGeometry*
```

Follow-up fixes from compile:

```text
ForgeModelAtlasLayout, ForgeModelAtlasPixelSample, and
ForgeFormalUploadedModelSummary were restored because they are shared by the
current ForgeOriginalVoxy* model-store/model-factory path. They are not active
preview owners.
```

Verification:

```text
First compile after this deletion failed on remaining atlas/formal shared-type
references.

rtk test .\gradlew compileJava
BUILD SUCCESSFUL in 9s

Filename inventory check:
No remaining source file matched the retired Debug/Preview/SampleSet/direct
renderer/K-stage formal owner patterns used by this cleanup pass.
```

## Final cleanup audit

Current command surface:

```text
ForgeVoxyCommands
 -> ForgeVoxyParityCommands
```

The root command no longer registers legacy debug, preview, geometry/model
prototype, preset, formal owner, or formal renderer registrars.

Current instance lifecycle:

```text
ForgeVoxyInstance no longer constructs or registers deleted debug/preview
renderers, visible LoD preview owner, K-stage formal owners, sample-set owners,
or offscreen validation owners.
```

Shared types intentionally retained:

```text
ForgeModelAtlasLayout
ForgeModelAtlasPixelSample
ForgeFormalUploadedModelSummary
ForgeModelStoreFormalLayout
```

Reason:

```text
These are currently referenced by ForgeOriginalVoxyModelFactory,
ForgeOriginalVoxyModelStore, ForgeOriginalVoxyMipGen,
ForgeSoftwareModelTextureBakery, ForgeModelStoreSkeleton, or
ForgeModelStoreLayoutAuditor. They are data/layout helpers, not active
debug/preview render owners.
```

Final verification:

```text
rtk git diff --check
OK

rtk test .\gradlew compileJava
BUILD SUCCESSFUL in 6s
```

## Step 7. K10 visible LoD preview owner removal plan

CodeGraph scan target:

```text
ForgeFormalVisibleLodPreview
ForgeFormalVisibleLodPreviewStats
ForgeFormalVisibleLodPreviewAuditResult
ForgeVoxyInstance
ForgeFormalRendererManager
ForgeModelBridgeResourceReloadTracker
```

Result:

```text
ForgeFormalVisibleLodPreview is the historical K10-K54 visible preview route.
It owns a render-event hook and preview shader/buffer/draw lifecycle. It is not
the original Voxy MDICSectionRenderer / VoxyRenderSystem route.
```

Decision:

```text
Remove the preview owner from ForgeVoxyInstance lifecycle and resource reload.
Delete the preview owner, stats, and audit-result files.

In ForgeFormalRendererManager, keep the visible-preview readiness fields false
and keep visiblePreviewDefaultDisabled true so this removed preview cannot
count as formal renderer readiness.
```

Code change:

```text
Removed ForgeFormalVisibleLodPreview field/getter/register and lifecycle marks
from ForgeVoxyInstance.

Removed resource-reload stale marking for ForgeFormalVisibleLodPreview.

Updated ForgeFormalRendererManager so visible-preview readiness/draw/input
fields are false and visiblePreviewDefaultDisabled is true.

Deleted:
src/main/java/me/cortex/voxy/forge/ForgeFormalVisibleLodPreview.java
src/main/java/me/cortex/voxy/forge/ForgeFormalVisibleLodPreviewStats.java
src/main/java/me/cortex/voxy/forge/ForgeFormalVisibleLodPreviewAuditResult.java
```

Verification:

```text
rtk test .\gradlew compileJava
BUILD SUCCESSFUL in 10s
```

## Step 8. Post-cleanup rescan

Scan commands:

```text
codegraph explore "old debug preview sample formal renderer routes in ForgeVoxyCommands ForgeVoxyInstance command registration render hooks remaining references"
codegraph explore "ForgeVoxyRuntimeOverrides methods callers old preset debug formal preview flags current usage"
rg filename/content sweeps for debug, preview, sample, legacy, preset, formal, fixture, smoke, synthetic, GL_HEAP_READBACK, directGpu, mdicDebug, simpleGpu
```

Confirmed removed:

```text
No source files remain for the previously deleted visible renderer families:
ForgeDebugMeshRenderer
ForgeGpuGeometryReadbackDebugRenderer
ForgeMdicDebugRenderer / ForgeMdicDebug*
ForgeTexturedDebug* / ForgeTexturedReadback* / ForgeTexturedMdicDebug*
ForgeDirectGpuGeometry*
ForgeFormalVisibleLodPreview*
ForgeFormalTexturedShaderPreview*
ForgeFormalPackedQuadPreview*
ForgeFormalTerrainPackedRecordBridge*
ForgeFormalCmdgen*
ForgeFormalIsolatedMdicDrawSmokeTest*
ForgeFormalTerrainShaderIntegration*
ForgeVoxyLegacy*Commands
ForgeVoxyPresetCommands
ForgeVoxyFormalOwnerCommands
ForgeVoxyFormalRendererCommands
```

Remaining old-code findings:

```text
ForgeVoxyRuntimeOverrides is still a legacy runtime preset/override surface.
It keeps old applyFormal*Preview/apply*Skeleton/applyMdicDebug/applyOverlay/
applyLod/applyGlHeapReadback/applyDirectGlDebug methods and direct setter
methods for removed debug/prototype routes.

ForgeVoxyConfig still exposes removed or deprecated runtime keys for:
geometry GPU visualization/readback debug routes
direct GL geometry debug renderer
MDIC debug draw
debug mesh renderer
simple GPU renderer/source controls

ForgeGpuGeometryVisualization* and ForgeGpuGeometryReadbackMesh* are still
compiled GL-heap debug visualization/readback sample routes. Their command
entry points are gone, but ForgeVoxyInstance and ForgeGpuGeometryUploadManager
still own/clear them.

ForgeGpuMeshUploadManager, ForgeGpuMeshCache, ForgeGpuMeshBuffer, and
SimpleGpuMesh* still represent the old simple-GPU preview upload route. The
route has no formal command entry now, but ForgeVoxyInstance still registers the
upload manager. Some low-level helpers are entangled with current CPU geometry
data types, so this needs a careful split rather than blind deletion.

ForgeMdicCommandManager still contains stress/debug-only helpers
stressOnce/processForDebugCommand and debug-draw accessors. It also still uses
runtime presets during stress.

ForgeMdicCommandLayout still carries a historical DEBUG_DRAW stage label.

ForgeModelBridgeResourceReloadTracker / Stats still carry
texturedMdicDebugStale fields after the textured debug renderer was removed.

ForgeVoxyInstance still has a stale "debug pipeline state" log message on
dimension switch.
```

Cleanup decision:

```text
Do not claim the cleanup is complete yet. The first pass removed the largest
visible renderer and command families, but the runtime/config/debug-validation
surface still needs at least one more controlled removal batch.

Next safe batch should remove the no-command GL-heap visualization/readback
sample route and stale resource-reload/debug log fields first. The simple-GPU
route and RuntimeOverrides/ForgeVoxyConfig require a more careful split because
some helper names and data containers are still shared by current geometry and
original-Voxy model code.
```

## Step 9. Legacy reload-status cleanup plan

CodeGraph scan target:

```text
ForgeModelBridgeResourceReloadTracker
ForgeModelBridgeResourceReloadStats
ForgeVoxyInstance dimension-switch log
```

Finding:

```text
Resource reload status still tracked removed historical owners:
sampleSetStale
atlasSkeletonStale
atlasPixelsStale
formalShaderInputBridgeStale
texturedDebugQuadStale
texturedReadbackStale
texturedMdicDebugStale

It also kept stale generic flags such as realModelStoreStale,
textureAtlasStale, formalShaderInputsStale, bakedModelSamplesStale, and
spriteSamplesStale even though the active cleanup now only calls:
modelBridgeReadiness.clear()
modelStoreSkeleton.markStale(...)
bakedModelBridge.markStale(...)
originalVoxyModelPipeline.markResourceReload()

ForgeVoxyInstance still logged "debug pipeline state" on dimension switch.
```

Decision:

```text
Shrink the reload tracker/status record to current active owners only.
Rename the dimension-switch log wording to parity pipeline state.
```

Code change:

```text
Removed stale resource-reload status fields for removed sample/debug/formal
preview owners.

ForgeModelBridgeResourceReloadStats now reports only current reload lifecycle
state and the active owners that are actually touched:
modelBridgeReadiness
modelStoreSkeleton
bakedModelBridge
originalVoxyModelPipeline

Changed the dimension-switch log from "debug pipeline state" to
"parity pipeline state".
```

Verification:

```text
rg old reload/debug keywords
OK

rtk test .\gradlew compileJava
BUILD SUCCESSFUL in 9s
```

## Step 10. GL-heap/simple-GPU route removal plan

CodeGraph scan target:

```text
ForgeGpuMeshUploadManager
ForgeGpuMeshCache
ForgeGpuMeshBuffer
ForgeBuiltSectionSimpleMeshBuilder
ForgeGpuGeometryVisualization*
ForgeGpuGeometryReadbackMesh*
ForgeVoxyInstance
ForgeGpuGeometryUploadManager
ForgeVoxyGeometryCache
ForgeMdicCommandManager stress helpers
```

Finding:

```text
The old simple-GPU preview route still owns ForgeGpuMeshUploadManager,
ForgeGpuMeshCache, ForgeGpuMeshBuffer, SimpleGpuMeshSource, and
SimpleGpuMeshLoadedChunkSkipMode.

The old GL-heap readback/visualization route still owns
ForgeGpuGeometryVisualization* and ForgeGpuGeometryReadbackMesh*.

No formal command entry points remain for these routes, but ForgeVoxyInstance
still constructs/registers their managers and ForgeGpuGeometryUploadManager
still clears them.

ForgeVoxyGeometryCache.createKeySnapshot still uses
ForgeGpuMeshBuffer.keyFromBuiltSection only for the old simple-GPU route.
```

Decision:

```text
Delete the old route owners and support files.
Remove their Instance fields, registration, dimension-switch cleanup, and
logout cleanup.
Remove ForgeVoxyGeometryCache.createKeySnapshot because its only caller is the
old simple-GPU route.
Remove MDIC stress-only code that depended on old runtime presets and
simple-GPU source switching.
Keep shared CPU geometry classes such as ForgeCpuMeshBuffer,
ForgeCpuBuiltSection, ForgeCpuMeshLayer, ForgeCpuMeshBuilder, and
ForgeCpuMeshCache because current original-Voxy model/geometry code still uses
them.
```

Code change:

```text
Removed ForgeVoxyInstance ownership/registration/lifecycle cleanup for:
ForgeGpuMeshUploadManager
ForgeGpuMeshCache
ForgeGpuGeometryVisualizationCache
ForgeGpuGeometryReadbackMeshCache
ForgeGpuGeometryReadbackMeshRefreshManager

Deleted old simple-GPU/GL-heap-readback support files:
ForgeGpuMeshUploadManager
ForgeGpuMeshCache
ForgeGpuMeshBuffer
ForgeGpuMeshLoadedChunkFilter
ForgeBuiltSectionSimpleMeshBuilder
ForgeGpuGeometryVisualization*
ForgeGpuGeometryReadbackMesh*

Removed ForgeVoxyGeometryCache.createKeySnapshot because it only served the
deleted simple-GPU route.

Removed MDIC stress helpers that switched old runtime presets and simple-GPU
sources.

Renamed the remaining MDIC heap accessor and command flag away from debug-only
names:
getHeapForMdicCommandGeneration
FLAG_SECTION_COMMAND

Removed orphan ForgeGpuGeometryStressStats.
```

## Step 11. Runtime/config isolation

CodeGraph scan target:

```text
ForgeVoxyRuntimeOverrides
ForgeVoxyConfig
ForgeCpuMeshBuildManager
actual references to ForgeVoxyRuntimeOverrides.*
actual references to ForgeVoxyConfig.*
```

Finding:

```text
RuntimeOverrides still carried old presets and switches for:
formal preview skeletons
overlay/lod/simple GPU mesh
GL heap visualization/readback
direct GPU geometry debug draw
MDIC debug draw
debug mesh alpha/source/status

ForgeVoxyConfig still exposed config keys for the same deleted routes.

ForgeCpuMeshBuildManager still used DEBUG_MESH_RENDER_DISTANCE_CHUNKS only as
a retention-radius reference for CPU mesh build records, keeping a debug mesh
config key alive after the renderer was deleted.
```

Decision:

```text
Convert ForgeVoxyRuntimeOverrides into a thin current-config facade used only by
the still-referenced current path:
world owner
auto chunk ingest
auto CPU mesh build
auto BuiltSection build
auto geometry-manager consume
geometry GPU heap upload
MDIC command generation config

Rewrite ForgeVoxyConfig to expose only keys still read by active code.

Change CPU mesh build retention to use the active auto mesh radius instead of a
debug mesh renderer radius.
```

Code change:

```text
Removed RuntimeOverrides presets, status snapshot, and all deleted-route
switches.

Removed config keys for simple GPU mesh, debug mesh renderer, GL heap
visualization/readback mesh, direct GPU geometry debug draw, and MDIC debug draw.

Deleted orphan SimpleGpuMeshSource and SimpleGpuMeshLoadedChunkSkipMode enums.
```

Verification:

```text
Targeted old-route source scan for simple GPU, debug mesh renderer, GL heap
visualization/readback mesh, direct GPU geometry, MDIC debug draw, old stress
helpers, and old runtime preset setters returned no source hits.

compileJava passed.
```

## 2026-06-23 pre-commit integrity audit

CodeGraph audit target:

```text
ForgeVoxyInstance
ForgeOriginalVoxyModelPipeline
ForgeOriginalVoxyModelFactory
ForgeOriginalVoxyModelStore
ForgeOriginalVoxyRenderGenerationService
ForgeOriginalVoxyRenderDataFactory
ForgeSectionGeometryManager
ForgeGpuGeometryUploadManager
ForgeMdicCommandPlanner
ForgeOriginalVoxyMdicSectionRenderer
ForgeOriginalVoxyOculus* bridge/mixins
ForgeVoxyCommands / ForgeVoxyParityCommands
ForgeModelBridgeResourceReloadTracker
```

Result:

```text
The active route still resolves through the original-Voxy-shaped owners.

Command registration only wires the parity command surface.

Resource reload invalidates the current model-store skeleton and original model
pipeline reload marker; it no longer calls deleted model-bridge/baked-model
diagnostic owners.

Oculus shaderpack patch data, pipeline data, render target, uniform, image, SSBO,
blend, and sampler bridge code remains connected through the ForgeOriginalVoxy
mixins/accessors.
```

Machine scans:

```text
Deleted prototype class/method scan:
no hits

Deleted config/runtime switch scan:
no hits

Forge/config suspicious filename scan:
ForgeOriginalVoxyOculusSamplers
ForgeOriginalVoxyOculusIrisSamplersMixin

Reason: these are Oculus/Iris shaderpack sampler integration files, not the
removed model sample-set route.
```

Validation:

```text
gradlew classes: passed
```

## Step 16. Hidden method-level old route cleanup

CodeGraph scan target:

```text
ForgeMdicCommandBuffer.bufferIdForDebugRenderer
ForgeGpuGeometryHeap.geometryBufferIdForDirectRenderer
ForgeGpuGeometryUploadManager.validateSample
ForgeGpuGeometryUploadManager.auditSample
ForgeGpuGeometryDecodedMetadata
ForgeMdicCommandPlanner.createFaceMaskPlanForAudit
```

Finding:

```text
The debug/direct renderer buffer-id helpers had no callers.

The GPU geometry validateSample/auditSample path had no callers, and its result
types/auditor were only tied to that manual readback helper route.

ForgeGpuGeometryDecodedMetadata was not purely old code: its metadata decode
view is still used by active MDIC command planning. The old name and readback
validation wording made it look like a deleted audit helper.

createFaceMaskPlanForAudit had no callers.
```

Decision:

```text
Delete uncalled debug/direct/manual-audit helpers.

Rename the active metadata decoder to current-path terminology and keep only
the metadata validation needed by MDIC planning.
```

Code change:

```text
Removed:
ForgeMdicCommandBuffer.bufferIdForDebugRenderer
ForgeGpuGeometryHeap.geometryBufferIdForDirectRenderer
ForgeGpuGeometryUploadManager.validateSample
ForgeGpuGeometryUploadManager.auditSample
ForgeGpuGeometryUploadManager.createAuditStatusSnapshot
ForgeGpuGeometryUploadManager.clearAuditStats
ForgeMdicCommandPlanner.createFaceMaskPlanForAudit

Deleted:
ForgeGpuGeometryAuditResult
ForgeGpuGeometryAuditStats
ForgeGpuGeometryAuditor
ForgeGpuGeometryValidationResult

Replaced:
ForgeGpuGeometryDecodedMetadata
-> ForgeGpuGeometryMetadataView
```

Verification:

```text
Targeted source scan for the deleted helper/type names returned no hits.

compileJava passed.
```

## Final audit

Source scans:

```text
Deleted prototype type/method scan:
ForgeGpuMesh*
SimpleGpuMesh*
ForgeBuiltSectionSimple*
ForgeDirectGpu*
ForgeDebugMesh*
ForgeMdicDebug*
ForgeTextured*
ForgeFormal*Preview
ForgeFormal*Smoke
ForgeFormal*DryRun
ForgeModelSampleSet
ForgeModelAtlasSampleSet
ForgeRealModelStoreSample
ForgeBakedModelBridge
ForgeModelBridgeReadiness
ForgeModelAtlasPixelSample
ForgeFormalUploadedModelSummary
ForgeModelStoreFormalLayout
packPreviewRecord
dumpSample

Result: no active source/resource hits.
```

```text
Deleted config/runtime-switch scan:
ENABLE_SIMPLE_GPU_MESH
SIMPLE_GPU_MESH
ENABLE_DEBUG_MESH_RENDERER
DEBUG_MESH_
ENABLE_GEOMETRY_GPU_VISUALIZATION
GEOMETRY_GPU_VISUALIZATION
GEOMETRY_GPU_READBACK_MESH
ENABLE_DIRECT_GPU_GEOMETRY
DIRECT_GPU_GEOMETRY
ENABLE_MDIC_DEBUG_DRAW
MDIC_DEBUG_DRAW
apply*Preset
setGeometryGpuUpload
getHeapForDebugReadback
FLAG_DEBUG_SKELETON
processForDebugCommand
bufferIdForDebugRenderer
geometryBufferIdForDirectRenderer
validateSample
auditSample
createAuditStatusSnapshot
ForgeGpuGeometryAuditResult
ForgeGpuGeometryValidationResult
ForgeGpuGeometryDecodedMetadata

Result: no active source/resource hits.
```

```text
Forge/config suspicious filename scan result:
ForgeOriginalVoxyOculusSamplers
ForgeOriginalVoxyOculusIrisSamplersMixin

These are Oculus/Iris shaderpack sampler integration files, not deprecated
sample-set/prototype routes.
```

Validation:

```text
compileJava passed.
```

## Step 15. Active layout class rename

Scan target:

```text
Forge/config source filenames containing:
debug, preview, legacy, sample, fixture, smoke, formal, readback,
visualization, simple, direct, stress, prototype, old
```

Finding:

```text
After deleting old prototype renderer files, the only Forge/config filename with
historical route wording was ForgeModelStoreFormalLayout.

The class is not old code: it is still used by the active original Voxy
model-store/model-factory path to describe the block-model record layout.
However, the name came from the old formal-route vocabulary and made the active
code look like it depended on that historical path.
```

Decision:

```text
Rename the active layout spec rather than deleting it.

Leave the wider ForgeModelStoreStats status field naming as remaining naming
debt because changing that interface is broader and should be done separately
from prototype-route deletion.
```

Code change:

```text
Renamed:
ForgeModelStoreFormalLayout
-> ForgeOriginalVoxyModelStoreLayoutSpec

Updated active references in:
ForgeModelStoreLayoutAuditResult
ForgeModelStoreLayoutAuditor
ForgeModelStoreSkeleton
ForgeModelStoreStats
ForgeOriginalVoxyModelStore
ForgeOriginalVoxyModelFactory
```

Verification:

```text
Targeted source scan for ForgeModelStoreFormalLayout returned no hits.

Forge/config filename scan now only reports Oculus/Iris Samplers files; those
are shaderpack sampler integration files, not deprecated sample-set/prototype
routes.

compileJava passed.
```

## Step 14. Status-surface route isolation

Scan target:

```text
ForgeOriginalVoxyModelPipelineStats
ForgeOriginalVoxyVisibleRendererStats
ForgeVoxyParityCommands
ForgeVoxyCommands
old route words in Forge/config source
```

Finding:

```text
No implementation path still used the old preview/sample-set renderer routes,
but status records and command output still exposed fields named
previewRouteUsed, sampleSetRouteUsed, and
originalVisibleRendererUsesPreviewRoute.

The top-level route status also listed old route families individually.
```

Decision:

```text
Keep negative audit visibility, but make it an isolation assertion instead of
continuing to center old route names in the active command/status surface.
```

Code change:

```text
Renamed preview/sample-set status fields to:
deprecatedPreviewRouteAbsent
deprecatedModelSampleRouteAbsent
deprecatedVisiblePreviewRouteAbsent

Collapsed the top-level route status old-route list into:
deprecatedPrototypeRoutesAbsent=true
```

Verification:

```text
Targeted scan for old Forge route terms now only reports
BlockState.getFluidState().createLegacyBlock(), which is a Minecraft/Forge API
name and not a deprecated Voxy prototype route.

compileJava passed.
```

## Step 13. Sample-name utility split

CodeGraph scan target:

```text
ForgeModelStoreSkeleton.dumpSample
ForgeVoxyQuadEncoder.packPreviewRecord
ForgeModelAtlasPixelSample
ForgeRealModelStoreFaceSample
ForgeFormalUploadedModelSummary
ForgeModelStoreFormalLayout
```

Finding:

```text
ForgeModelStoreSkeleton.dumpSample and ForgeVoxyQuadEncoder.packPreviewRecord
had no callers and existed only for removed command/debug-preview surfaces.

ForgeRealModelStoreFaceSample had no callers.

ForgeFormalUploadedModelSummary was still used by the active original Voxy
model factory as an uploaded-model cache summary.

ForgeModelAtlasPixelSample had no object callers, but its constants/checksum
were still used by the active original Voxy model factory, mip generator,
model store, and software texture bakery.

ForgeModelStoreFormalLayout is still used by the active original Voxy model
store/model factory path, so deleting it would break the current data layout.
```

Decision:

```text
Delete only truly unused old helper methods/files.

Rename active utility/data holders away from old Sample/Formal route names
instead of deleting them:
ForgeModelAtlasPixelFormat
ForgeOriginalUploadedModelSummary

Keep ForgeModelStoreFormalLayout for now because it is active layout metadata,
but leave it as remaining naming debt rather than treating it as a deleted
preview route.
```

Code change:

```text
Removed unused ForgeModelStoreSkeleton.dumpSample.
Removed unused ForgeVoxyQuadEncoder.packPreviewRecord.
Deleted unused ForgeRealModelStoreFaceSample.
Replaced ForgeModelAtlasPixelSample with ForgeModelAtlasPixelFormat for active
pixel constants/checksum.
Replaced ForgeFormalUploadedModelSummary with ForgeOriginalUploadedModelSummary
for the active original Voxy uploaded-model cache.
```

Verification:

```text
Targeted source scan for the old type/method names returned no source hits.

compileJava passed.
```

## Step 12. Old model-bridge diagnostic isolation

CodeGraph scan target:

```text
ForgeVoxyInstance
ForgeModelBridgeResourceReloadTracker
ForgeModelBridgeResourceReloadStats
ForgeModelBridgeReadiness
ForgeBakedModelBridge
```

Finding:

```text
The old ModelBridgeReadiness and BakedModelBridge diagnostic owners no longer
had formal command callers or renderer integration, but ForgeVoxyInstance still
constructed them and cleared them on dimension switch/logout.

ForgeModelBridgeResourceReloadTracker still called the old readiness bridge and
baked-model bridge on resource reload, so the current reload owner still had
cross-dependencies on old diagnostic code.
```

Decision:

```text
Remove the old diagnostic bridge owners entirely.

Keep resource reload connected only to the current model-store skeleton and the
original Voxy model pipeline reload marker until the outer lifecycle is replaced
by original Voxy-equivalent ownership.
```

Code change:

```text
Removed ForgeModelBridgeReadiness and ForgeBakedModelBridge ownership from
ForgeVoxyInstance.

Removed old diagnostic bridge invalidation fields from
ForgeModelBridgeResourceReloadStats and ForgeModelBridgeResourceReloadTracker.

Deleted old diagnostic files:
ForgeModelBridgeReadiness
ForgeModelBridgeReadinessStats
ForgeModelBridgeAuditResult
ForgeBakedModelBridge
ForgeBakedModelBridgeStats
ForgeBakedModelBridgeAuditResult
ForgeBakedModelSample
ForgeBakedQuadSample
```

Verification:

```text
Targeted source scan for the deleted bridge type names/getters/stale flags
returned no source hits.

compileJava passed.
```
