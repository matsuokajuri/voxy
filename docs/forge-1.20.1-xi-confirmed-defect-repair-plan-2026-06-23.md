# Forge 1.20.1 XI confirmed-defect repair plan, 2026-06-23

> **Black-solid-LOD defect RESOLVED 2026-06-25 (commit 24c5503c)** —
> `PATCHED_SHADER` was defined only on the terrain fragment shader, not the vertex
> shader. See `forge-1.20.1-xi-runtime-visual-investigation-notes-2026-06-23.md`.
> Any other defects listed here remain as historical record.

This document is the repair plan for the confirmed defects recorded in:

```text
docs/forge-1.20.1-full-code-defect-audit-2026-06-23.md
docs/forge-1.20.1-black-lod-bugfix-plan-2026-06-22.md
docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md
```

Post-bugfix original-content migration reference:

```text
docs/forge-1.20.1-original-voxy-unported-content-migration-reference-2026-06-23.md
```

It is a plan document only. It does not mark any defect fixed.

## Controlling rule

```text
original Voxy source is the baseline
```

Every repair below must follow this workflow:

```text
read original Voxy owner and dependencies
 -> read current Forge/Oculus/Embeddium equivalent
 -> patch only the parity-preserving adaptation
 -> compile
 -> quick audit against this plan and the defect audit
 -> update docs before moving to the next step
```

Do not add Complementary-specific checks, brightness multipliers, hard-coded
material ids, fake bridges, preview renderers, or debug-only success paths.

## Validation cadence

After every completed code step:

```powershell
rtk git status --short
rtk test .\gradlew compileJava
```

After every risky renderer, shaderpack, GL, lifecycle, or resource-release step:

```text
quick source audit against original Voxy
quick status/log audit for new failure loops
update this plan and the full defect audit
```

Final validation for the whole repair round:

```powershell
rtk git status --short
rtk test .\gradlew compileJava
rtk test .\gradlew runClient
```

Runtime validation must use only necessary game checks. If visual confirmation
is needed, ask the user exactly what to look at. Use `rtk log` for log evidence;
do not read full logs directly.

## Repair ordering

The order is deliberate:

```text
XI.1 stop reload/start churn
XI.2 consolidate lifecycle ownership
XI.3 isolate old staged/placeholder routes
XI.4 repair shaderpack output contract
XI.5 repair black LoD terrain output
XI.6 repair water/translucent path
XI.7 repair storage/service shutdown and ingest/update gaps
XI.8 repair secondary model/material/geometry/status issues
XI.9 repair classloading/dependency/docs/status hygiene
XI.10 final detailed audit and runtime validation
```

Do not start water-specific repairs before the generic black LoD terrain
contract is stable.

## XI.1 Reload and queued-lifecycle guard

Target findings:

```text
P1: Forge reads Oculus WorldRenderingSettings reload flag without consuming it
P2: queued render-thread start/cleanup tasks have no lifecycle generation guard
P1/P2 runtime symptom: new-world crash/freeze after shader/resource changes
```

Original sources to inspect first:

```text
src/main/java/me/cortex/voxy/client/mixin/minecraft/MixinLevelRenderer.java
src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java
src/main/java/me/cortex/voxy/commonImpl/VoxyInstance.java
Oculus-1.20.1-new/.../WorldRenderingSettings.java
Oculus-1.20.1-new/.../PipelineManager.java
```

Forge sources to repair:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java
src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java
src/main/java/me/cortex/voxy/forge/ForgeModelBridgeResourceReloadTracker.java
```

Steps:

```text
1. Replace client-tick polling of Oculus isReloadRequired with a safe event or
   generation model. Prefer the existing clearReloadRequired mixin signal so
   Forge does not steal the Oculus reload flag before Oculus consumes it.
2. If polling must remain, add a local observed-generation/debounce owner and
   prove it cannot mark a freshly started owner stale every tick.
3. Add a lifecycle generation token to ForgeOriginalVoxyModelPipeline.
4. Capture the generation when scheduling start and cleanup render-thread work.
5. At the top of startOnRenderThread, bail out if the generation/request is no
   longer current.
6. In queued cleanup, avoid releasing or clearing a newer owner created after
   the cleanup task was scheduled.
7. Add status fields or concise logs that distinguish reload requested,
   reload consumed, start skipped as stale, cleanup skipped as stale, and
   successful owner start.
```

Acceptance criteria:

```text
compileJava passes
no repeated oculus-world-rendering-settings-reload loop in status/logs
world entry does not queue stale starts after logout/dimension switch/reload
no new direct shaderpack-specific workaround
docs updated with exact remaining blockers
```

Repair status, 2026-06-23:

```text
completed
```

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> polls Oculus WorldRenderingSettings.reloadRequired but now debounces it as
    a false-to-true edge instead of restarting on every true poll
 -> does not call clearReloadRequired, so Oculus PipelineManager remains the
    owner that consumes the Oculus flag
 -> attaches lifecycleGeneration tokens to queued start work
 -> skips stale start/install work when logout, dimension switch, reload, or
    failure invalidates the queued generation
 -> suppresses global DownloadStream flushes from stale queued cleanup after a
    newer owner is active, while still freeing the captured old resources
 -> skips stale queued factory-upload work when the captured factory no longer
    belongs to the current owner
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

Remaining blockers after XI.1:

```text
XI.1 does not fix black LoD, missing water, persistent storage,
full VoxyRenderSystem ownership, or shaderpack output parity. It only removes
the reload/start-cleanup churn that could invalidate later runtime validation.
```

## XI.2 Consolidate the outer lifecycle owner

Target findings:

```text
P1: full outer VoxyRenderSystem lifecycle is still missing
P2: render-state restore incomplete for Embeddium hook insertion
P2: post-frame dynamic work is gated differently from original Voxy
P2: service thread pool and SectionSavingService lack terminal owner
```

Original sources to inspect first:

```text
src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java
src/main/java/me/cortex/voxy/client/core/AbstractRenderPipeline.java
src/main/java/me/cortex/voxy/client/core/rendering/RenderDistanceTracker.java
src/main/java/me/cortex/voxy/client/core/model/ModelBakerySubsystem.java
```

Forge sources to repair:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java
src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java
```

Steps:

```text
1. Create or refactor toward a single Forge original-render owner that mirrors
   VoxyRenderSystem constructor/shutdown/render responsibilities.
2. Move ownership of model factory/store, render generation, geometry data,
   async geometry sync, node cleaner, HOC traversal, render pipeline, viewport
   selector, render-distance tracker, and chunk-bound renderer under that owner.
3. Keep Embeddium cutout mixin as a platform entry point only; it should call
   the owner, not own the renderer lifecycle.
4. Match original shutdown order: detach callbacks, stop node/geometry work,
   shutdown model/render services, free traversal/cleaner/geometry/chunk-bound,
   free viewport selector, free pipeline, flush download stream, release world.
5. Run original post-dynamic work according to the original frame lifecycle,
   not only after successful command generation.
6. Keep explicit Forge adapters only where Embeddium/Oculus API differences
   require them, and document each adapter.
```

Acceptance criteria:

```text
compileJava passes
owner lifecycle is visible as one owner in status/logs
old queued cleanup/start logic no longer owns unrelated resources
post-frame UploadStream/RenderDistanceTracker/model uploads run on the same
conditions as original Voxy or have a documented Forge blocker
```

Partial repair status, 2026-06-23:

```text
post-frame dynamic gating fixed
outer lifecycle owner still incomplete
```

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> renderEmbeddiumCutout() no longer gates original post-dynamic work only on
    sectionRenderer.buildDrawCalls(...) completing
 -> post-dynamic work becomes eligible after renderPipeline.setup(...) succeeds,
    which is closer to original VoxyRenderSystem.renderOpaque() running
    UploadStream tick, RenderDistanceTracker processing, and model uploads
    after the pipeline frame rather than after cmdgen success only
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

Remaining blockers after this partial XI.2 repair:

```text
The full Forge owner is still ForgeOriginalVoxyModelPipeline rather than a true
VoxyRenderSystem-equivalent outer owner. Terminal SectionSavingService/thread-pool
ownership now has a Forge GameShuttingDownEvent owner, but complete
VoxyRenderSystem shutdown-order parity remains open until the full outer owner
exists and runClient confirms the event timing on the render thread.
```

## XI.3 Isolate or remove old staged and placeholder routes

Target findings:

```text
P1: runtime config exposes staged skeleton switches
P1: placeholder model-store route survived cleanup
P2: legacy CPU/BuiltSection/cache managers remain registered
P3: startup logs still describe disabled skeleton
```

Sources to inspect first:

```text
src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java
src/main/java/me/cortex/voxy/forge/ForgeVoxyRuntimeOverrides.java
src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java
src/main/java/me/cortex/voxy/forge/ForgeModelStoreSkeleton.java
src/main/java/me/cortex/voxy/forge/ForgeModelDataBuffer.java
src/main/java/me/cortex/voxy/forge/ForgeCpuMeshBuildManager.java
src/main/java/me/cortex/voxy/forge/ForgeVoxyBuiltSectionBuildManager.java
src/main/java/me/cortex/voxy/forge/ForgeSectionGeometryManager.java
```

Steps:

```text
1. Classify every remaining staged manager as active parity infrastructure,
   diagnostic-only, or deprecated.
2. Remove active startup registration for deprecated managers.
3. Replace placeholder model-store status with original model-store diagnostics.
4. Remove or isolate ForgeModelStoreSkeleton and ForgeModelDataBuffer if no
   active command/status caller still needs them.
5. Collapse staged config switches that can create impossible mixed states.
6. Keep only user-facing config that maps to original Voxy behavior or a
   documented Forge platform adapter.
7. Rewrite startup log wording so it reports the current parity route without
   claiming readiness.
```

Acceptance criteria:

```text
compileJava passes
new original route does not depend on placeholder model data or fake colors
config cannot enable old skeleton route pieces as formal evidence
status commands do not report preview/debug/placeholder readiness as renderer readiness
```

Repair status, 2026-06-23:

```text
placeholder model-store route removed from active source:
- ForgeVoxyInstance no longer owns ForgeModelStoreSkeleton or
  ForgeModelStoreLayoutAuditor.
- ForgeModelBridgeResourceReloadTracker no longer marks a placeholder model
  store stale; it now marks only the original model pipeline reload owner.
- Deleted the isolated placeholder model-store class family:
  ForgeModelStoreSkeleton, ForgeModelDataBuffer, ForgeModelStoreRecord,
  ForgeModelStoreStats, ForgeModelStoreAuditResult, ForgeModelStoreLayout,
  ForgeModelStoreLayoutAuditor, and ForgeModelStoreLayoutAuditResult.
- ForgeVoxyModelIdMapper remains only as legacy CPU-geometry compatibility
  support; its unused reverse/snapshot helpers were removed and its comment now
  states that it is not the formal original model-id owner.
- compileJava passed after this cleanup.
```

Remaining blockers after this XI.3 slice:

```text
legacy CPU/BuiltSection/cache managers still exist as compatibility/status
surfaces and need a separate classification/removal pass. Runtime config
switch cleanup is still open.
```

## XI.4 Shaderpack output-contract repair

Target findings:

```text
P1: shaderpack visual contract not proven
P1: shaderpack patch exposure enabled before visual parity
P1: multi-target output can be collapsed to first texture in helpers/status
P2: lightmap texture binding can silently bind texture 0
P2: Oculus uniform builder is adapted, not byte-parity proven
P2: render-state capture mixin mutates GL viewport at LevelRenderer HEAD
```

Original and platform sources to inspect first:

```text
src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java
src/main/java/me/cortex/voxy/client/core/IrisVoxyRenderPipeline.java
src/main/java/me/cortex/voxy/client/core/IrisVoxyRenderPipelineData.java
src/main/java/me/cortex/voxy/client/mixin/iris/MixinProgramSet.java
src/main/java/me/cortex/voxy/client/mixin/iris/MixinIrisRenderingPipeline.java
Oculus-1.20.1-new/.../ProgramSet.java
Oculus-1.20.1-new/.../IrisRenderingPipeline.java
Oculus-1.20.1-new/.../RenderTargets.java
```

Forge sources to repair:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyMdicSectionRenderer.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java
```

Steps:

```text
1. Build a field-by-field table for original IrisVoxyRenderPipelineData vs
   ForgeOriginalVoxyOculusRenderPipelineData.
2. Verify voxy.json target ids, framebuffer attachments, glDrawBuffers order,
   output layout(location) mapping, blend states, TAA source, SSBO base,
   sampler/image base, and UBO layout.
3. Replace first-texture-only readiness/status helpers with explicit opaque and
   translucent target lists.
4. Make lightmap texture binding fail visible when texture id is 0 under an
   active shaderpack instead of silently binding 0 as if ready.
5. Move viewport mutation out of LevelRenderer HEAD or prove it is equivalent
   to original Iris/Oculus setup timing.
6. Gate shaderpack patch exposure on proven pipeline-data readiness, or keep it
   exposed only with status that clearly says visual parity is not proven.
7. Add bounded audit logs/status fields for target ids, texture ids, draw buffer
   order, lightmap id, patched shader used/fallback, and MDIC draw counts.
```

Acceptance criteria:

```text
compileJava passes
shaderpack output status reports every target rather than the first texture
lightmap binding status cannot say ready when texture id is 0
no shaderpack-name-specific branch is added
black LoD debugging can distinguish geometry/no draw vs wrong G-buffer output
```

Partial repair status, 2026-06-23:

```text
XI.4 step 4 is fixed for shaderpack image/sampler observability:
- ForgeOriginalVoxyOculusRenderPipelineData.ImageSet now treats external
  lightmap texture id 0 as not ready instead of reporting image bindings ready.
- ForgeOriginalVoxyRenderPipeline.oculusImageBindingsReady() now reflects that
  ImageSet readiness, and oculusPipelineFailureReason() reports
  oculus-image-binding-texture-zero:lightmap when the lightmap sampler resolves
  to texture 0.
- ImageSet binding now logs the texture-0 lightmap condition once and keeps the
  result visible through status/failure reason.
- Forge sampler binding was restored to the original IrisVoxyRenderPipelineData
  rule: samplerId == -1 means leave sampler binding unchanged; only explicit
  sampler ids, including 0 for the external lightmap sampler, are bound.
- No shaderpack-name-specific branch and no brightness/light hack was added.
```

Validation:

```text
2026-06-23: rtk test .\gradlew compileJava -> passed after the XI.4
            lightmap/sampler status repair.
```

Remaining XI.4 blockers:

```text
This partial repair does not prove shaderpack visual output parity by itself.
Explicit opaque/translucent target-list status, full patch-exposure readiness
gating, and the LevelRenderer HEAD viewport mutation audit remain open before
XI.5 black-LoD visual repair can be considered proven.
```

## XI.5 Generic black-LoD terrain repair

Target findings:

```text
P1: user-observed black LoD remains unresolved
P2: RenderDataFactory lighting is high risk even if inherited upstream
P2: material/custom block-state id bridge is adapted
P2: model baking rejects custom/unknown render layers
```

Steps:

```text
1. After XI.4, compare one representative opaque terrain block through the full
   chain: block state id, model id, face data, model colour, atlas tile, light
   byte, custom material id, shader lightmap uv, and G-buffer output.
2. Use original Voxy field ownership as the expected values. Do not infer by
   looking at final brightness only.
3. Check whether Complementary-visible darkness is caused by wrong output
   target, wrong lightmap sampler/uv, wrong custom id/material, missing normal
   data, or depth/fog state.
4. Patch only the generic contract mismatch found in step 3.
5. Re-run compile and quick source audit after each patch.
6. Run runClient only after the code-level contract has one clear candidate fix.
7. Ask user to verify far LoD terrain at day and night only if log/status
   evidence cannot prove the visual result.
```

Acceptance criteria:

```text
far LoD terrain is not globally black under the active shaderpack
time-of-day changes do not collapse LoD into near-zero light
patched opaque and translucent programs report used=true and fallback=false
MDIC draw counts are non-zero when visible geometry exists
the fixed path is generic shaderpack contract parity, not a Complementary hack
```

Partial repair status, 2026-06-23:

```text
XI.5/XI.7 input-side lighting hygiene is partially fixed:
- VoxelIngestService live chunk ingest now skips all-air LevelChunkSections,
  matching the original WorldImporter behavior of ignoring sections with no
  block_states payload instead of treating empty air layers as light-deferred
  work.
- Before writing a loaded chunk into WorldEngine, Forge now preflights non-air
  sections for missing sky-light DataLayer in sky-lit dimensions. If any non-air
  section is missing sky light, the chunk returns deferred stats and performs no
  partial WorldEngine writes for that pass.
- This avoids mixing freshly written lit sections with stale/empty/deferred
  sections inside one LoD chunk while the Forge light engine is still catching
  up.
- Light packing was rechecked against shader getLightmapUv(): Forge still packs
  sky in the low nibble and block in the high nibble, which matches the shader.
- This is not yet visual proof that black LoD is fixed. The next runClient must
  verify whether deferredLightSections collapse to real non-air misses only and
  whether far LoD brightness changes.
```

Validation:

```text
2026-06-23: rtk test .\gradlew compileJava -> passed after the live chunk
            ingest light/air-section repair.
```

## XI.6 Water and translucent repair

Target findings:

```text
P1: user reports water missing or invisible
P2: Forge fluid model baking substitute needs output parity proof
P2: translucent draw target/depth path remains high risk
```

Steps:

```text
1. Do not start this until XI.5 stops global black LoD.
2. Compare original ModelFactory fluid model handling, RenderDataFactory
   translucent/fluid masks, MDIC translucent draw path, and pipeline
   postOpaquePreTranslucent/finish behavior.
3. Verify Forge waterlogged and pure water blocks produce non-zero fluid model
   ids and correct translucent metadata.
4. Verify translucent draw targets, depth transfer, blend state, and final blit
   match original Iris/Oculus expectations.
5. Patch fluid bake or translucent pipeline only where original-equivalent data
   is missing.
6. Record known upstream Voxy water issue patterns separately from Forge-only
   regressions.
```

Acceptance criteria:

```text
water LoD is visible in the same broad cases original Voxy supports
opaque terrain fix remains stable
translucent target status shows real target ids and draw counts
remaining upstream-only water limitations are documented as upstream parity limits
```

Partial repair status:

```text
2026-06-23: Fixed a confirmed Forge fluid bake ordering bug that did not
            require client visual validation.

Root cause:
- ForgeSoftwareModelTextureBakery.renderToOutput(...) checked
  state.getRenderShape() == RenderShape.INVISIBLE before the LiquidBlock path.
- In Minecraft 1.20.1, pure water/lava LiquidBlock states are rendered by
  LiquidBlockRenderer even though their block render shape is INVISIBLE.
- The premature invisible-shape return baked pure water/lava as six empty
  faces with no failure. ForgeOriginalVoxyModelFactory could then dedupe that
  empty model against model id 0, the air model, which explains logged
  fluidModelId=0 for water-containing block states and missing LoD water.

Change:
- LiquidBlock states now enter renderFluid(...) before the non-fluid invisible
  render-shape return.

Verification so far:
- rtk test .\gradlew compileJava -> passed.
- Static comparison against original SoftwareModelTextureBakery confirms the
  original first classifies LiquidBlock separately and does not reject it via
  RenderShape.INVISIBLE before fluid baking.

Still needs user/client validation:
- Confirm pure water/lava no longer alias to model id 0 in runtime audit logs.
- Confirm LoD water is visible under Complementary and with shaders disabled.
- Confirm this does not by itself solve the generic black LoD terrain output;
  terrain brightness remains a separate XI.5 issue unless runtime proves
  otherwise.
```

## XI.7 Storage, saving, ingest, and update correctness

Target findings:

```text
P1: active Forge WorldEngine still uses MemoryStorageBackend
P2: service thread pool and SectionSavingService have no shutdown owner
P2: chunk-remove ingest may miss last loaded chunk snapshot
P2: mixin ingest ignores staged ingest config gate
P2: light-data defer has retry coverage but no explicit light-update queue
```

Original sources to inspect first:

```text
src/main/java/me/cortex/voxy/client/VoxyClientInstance.java
src/main/java/me/cortex/voxy/commonImpl/VoxyInstance.java
src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java
src/main/java/me/cortex/voxy/client/mixin/sodium/MixinRenderSectionManager.java
```

Steps:

```text
1. Replace MemoryStorageBackend with original-equivalent configured per-world
   and per-dimension storage, using Forge 1.20.1 world path semantics.
2. Preserve original save callback, mapper id mapping persistence, flush, close,
   and idle-world free ordering.
3. Add a terminal owner for SectionSavingService and UnifiedServiceThreadPool.
4. Add or map a Forge equivalent of original cheeky chunk cache for removal-time
   ingest, or document a platform blocker if no safe equivalent exists.
5. Make ingest config semantics match original Voxy ingestEnabled instead of
   having tick-scan and mixin-ingest disagree.
6. Add light-ready retry or notification ownership for deferred sky-light
   sections so missing DataLayer sections do not starve indefinitely.
```

Acceptance criteria:

```text
compileJava passes
logout/new world does not leave service queues/thread pools alive
WorldEngine data persists according to original storage semantics
chunk remove path has original-equivalent last-snapshot behavior or documented blocker
deferred light sections retry deterministically
```

Repair status, 2026-06-23:

```text
XI.7 native new-world crash mitigation is partially fixed:
- The latest hs_err showed EXCEPTION_ACCESS_VIOLATION in nvoglv64.dll while
  Minecraft was taking the automatic new-world screenshot through
  NativeImage.downloadTexture/glGetTexImage.
- ForgeSoftwareModelTextureBakery.setupTexture() was leaving pixel-pack,
  pixel-pack-buffer, and framebuffer state changed after its atlas readback.
- The Forge adapter now saves and restores read framebuffer, draw framebuffer,
  GL_PIXEL_PACK_BUFFER_BINDING, PACK_ROW_LENGTH, PACK_IMAGE_HEIGHT,
  PACK_SKIP_ROWS, PACK_SKIP_PIXELS, and PACK_ALIGNMENT around the atlas
  glGetTextureImage call.
- This preserves the existing original-shaped atlas readback logic while
  preventing the Forge/Oculus/Minecraft screenshot path from inheriting a
  stale row stride or PBO state.
- compileJava passed after this change.

XI.7 original ClientLevel block-state update ingest hook is partially fixed:
- ForgeOriginalVoxyClientLevelMixin ports the original setBlocksDirty TAIL
  behavior for section-border block removals.
- The hook re-ingests the affected section into the active Forge WorldEngine
  with copied block/sky light layers.
- voxy.forge.mixins.json registers the mixin in the client mixin list.
- compileJava passed after this change.

XI.7 terminal service ownership is partially fixed:
- ForgeVoxyInstance now listens for Forge GameShuttingDownEvent, which Forge
  documents as firing once on the physical client while the GL context is still
  valid.
- shutdown() clears auto-ingest, world-scoped managers, and resource reload
  state, asks ForgeOriginalVoxyModelPipeline to run client-stop cleanup, drains
  SectionSavingService, frees no-longer-used closing worlds immediately for
  shutdown, and then shuts the original UnifiedServiceThreadPool.
- The service thread pool is shut down only when render-resource cleanup ran
  synchronously on the render thread. If cleanup is deferred, Forge logs the
  skipped terminal service shutdown instead of racing queued GL/resource
  teardown.
- compileJava passed after this change.
```

Remaining blockers after this XI.7 slice:

```text
persistent storage, save/load identity, chunk-remove last-snapshot parity,
explicit delayed-light notification ownership, full VoxyRenderSystem-equivalent
shutdown ordering, and runtime proof of correct light arrival still remain open.
```

## XI.8 Secondary model, geometry, and status correctness

Target findings:

```text
P2: async node/geometry sync result-merge drift
P2: geometry readiness/status does not prove valid uploaded sections
P2: geometry capacity/headroom logic is adapted and unproven
P3: visible draw submission status can be true before draw counts are proven
P3: BuiltSection verification flag uses Forge-only property
P3: Embeddium service-thread sharing status conflates requested and available
```

Steps:

```text
1. Compare Forge async node/geometry sync against original AsyncNodeManager and
   BasicAsyncGeometryManager for result merge ordering and removal behavior.
2. Make readiness status distinguish allocated buffers, uploaded sections,
   non-zero draw counts, and visible final output.
3. Compare geometry capacity/headroom against original BasicSectionGeometryData
   and document any Forge GL/sparse-buffer adaptation.
4. Change draw-submission status to use actual MDIC draw counts/readback fields.
5. Move BuiltSection verification flag back to original VoxyCommon verification
   flag ownership.
6. Fix service-thread sharing status so ready means builder thread count was
   actually confirmed, not merely requested by config.
```

Acceptance criteria:

```text
compileJava passes
status no longer overstates visible renderer readiness
geometry upload readiness can be tied to actual uploaded section count
verification flags use original owner semantics
```

Partial repair status, 2026-06-23:

```text
async node/geometry result-merge drift fixed
draw-submission status and service-thread sharing status fixed
BuiltSection verification-owner code unchanged after safety audit
```

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyAsyncNodeGeometrySync.java
 -> publishSyncResults() now distinguishes a fresh SyncResults publication from
    merging into a previous pending SyncResults.
 -> heap removals cancel pending uploads only in the previous-result merge
    branch; fresh publications clear removals like original AsyncNodeManager.

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyMdicSectionRenderer.java
 -> added readback-backed predicates for opaque, temporal opaque, and
    translucent draw counts.

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> visible MDIC draw-submission status now uses those readback-backed
    predicates instead of geometry count plus target readiness.
 -> Embeddium builder-thread sharing status now distinguishes requested
    sharing from confirmed builder-thread-count availability.

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyBuiltSection.java
 -> left unchanged: the raw JVM property uses the same
    voxy.verifyBuiltSectionOffsets key as VoxyCommon.isVerificationFlagOn(...)
    for the current default-false flag, while directly importing VoxyCommon
    would trigger its FabricLoader static initializer under Forge.
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

Remaining blockers after this XI.8 slice:

```text
geometry capacity/headroom parity and runtime proof of visible non-black output
still remain open. These repairs do not fix black LoD or missing water by
themselves.
```

## XI.9 Classloading, dependency, log, and document hygiene

Target findings:

```text
P2: physical-server classloading boundary is not explicitly sealed
P2: required internal Embeddium/Oculus mixins have broad unbounded ranges
P3: startup logs still say disabled skeleton
P3: old-stage runClient doc wording can mislead validation
```

Steps:

```text
1. Decide whether this mod is explicitly client-only at metadata level. If yes,
   make the Forge metadata say so clearly.
2. Move client event method descriptors off the top-level @Mod class if needed
   so physical-server classloading cannot resolve client-only event types.
3. Narrow Embeddium/Oculus version ranges to the tested internal API contract,
   or add a mixin applicability/version guard.
4. Rewrite startup logs to current parity-route wording without claiming formal
   readiness.
5. Rewrite docs/forge-1.20.1-runclient-quickplay.md so it no longer uses QA1 or
   H-stage language and follows current Roman/original-parity validation cadence.
```

Acceptance criteria:

```text
compileJava passes
metadata and logs no longer imply the old skeleton route
frontend dependency range matches the internal mixin contract
runClient documentation cannot be mistaken for old-stage QA direction
```

Partial repair status, 2026-06-23:

```text
top-level client event descriptors moved behind client-only helper
startup log wording fixed
runClient quick-play document wording fixed
unconditional manual Mixins.addConfiguration call removed from VoxyForge
Embeddium/Oculus dependency ranges narrowed to the tested 0.3.x / 1.8.x lines
```

Evidence:

```text
src/main/java/me/cortex/voxy/forge/VoxyForge.java
 -> top-level @Mod class no longer declares client event handler method
    descriptors.
 -> no longer manually registers voxy.forge.mixins.json on both physical sides;
    the config remains declared through Forge metadata/jar manifest.
 -> startup log now describes the original-parity route and explicitly keeps
    formal renderer readiness gated by runtime validation.

src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java
 -> owns FMLClientSetupEvent and RegisterClientReloadListenersEvent handlers
    behind the DistExecutor client registration path.
 -> client setup log now says parity adapters are registered instead of calling
    the active path a skeleton.

docs/forge-1.20.1-runclient-quickplay.md
 -> rewritten for the current Roman-round original Voxy parity route.
 -> removed QA1/H-stage wording and documented RTK log usage.

gradle.properties / META-INF/mods.toml expansion
 -> embeddium_version_range=[0.3.31,0.4)
 -> oculus_version_range=[1.8.0,1.9)
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
rtk test .\gradlew processResources -> passed on 2026-06-23
```

Remaining blockers after this XI.9 slice:

```text
metadata-side physical-server policy and remaining old-stage document spot
checks still need separate cleanup. Dependency range narrowing is compile and
processResources verified, but runtime compatibility still needs client testing.
```

## XI.10 Final audit and runtime validation

Steps:

```text
1. Re-read AGENTS.md and CODEX.md.
2. Run a full source audit against every confirmed finding in the defect audit.
3. Verify every fixed finding is marked fixed with evidence in the audit doc.
4. Verify every remaining finding has a blocker or follow-up note.
5. Run compileJava.
6. Run runClient with the active shaderpack test setup.
7. Check logs through rtk log for reload loops, shader compile fallback, GL
   errors, lifecycle churn, missing target ids, lightmap id 0, and service leaks.
8. Ask the user only for visual checks that logs/status cannot prove:
   far LoD brightness, day/night behavior, water visibility, chunk/LoD seam.
9. Do a final git status review.
10. Commit only source and documentation changes that belong to the repair
    round. Do not commit run/, logs, saves, local config, build outputs,
    .codegraph/, .agents/, or CODEX.md.
```

Whole-round acceptance criteria:

```text
compileJava passes
runClient enters a world without freeze/crash
no repeated lifecycle/reload loop
no shaderpack fallback when the patch is expected to be active
far LoD terrain is no longer globally black
water/translucent result is either fixed or clearly documented as remaining
formalRendererReady remains false until full original owner parity is genuinely complete
all docs reflect current state
git commit records the completed repair round
```

## Blocker policy

If a step cannot be safely completed in the repair round, write the blocker in
the defect audit before moving on:

```text
platform blocker
original source ambiguity
Forge/Oculus/Embeddium API mismatch
runtime-only evidence needed from user
repair scope too large for one coherent round
```

Do not fill a blocker with a fallback implementation. A documented blocker is
better than a fake bridge.
