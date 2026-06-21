# Forge 1.20.1 deep runtime drift audit, 2026-06-21

This document records a focused post-X audit after runtime testing showed that
the visible LoD result still differs substantially from original Voxy.

The audit compares the original Voxy render path against the current Forge
path and separates three categories:

```text
actual visible pixels during the latest run
formal original-shaped renderer owners that exist but are not active
formal owners that are active only after the missing lifecycle start is fixed
```

## Verdict

```text
VOXY_PARITY_INCOMPLETE
FORMAL_RENDERER_READY=false
ACTUAL_RENDERER_DRAW_ENABLED=false
FORMAL_DRAW_PIPELINE_READY=false
EARLY_USABLE_LOD_RENDERER_READY=false
```

The pre-repair visual result must not be judged as original Voxy output. That
run submitted visible LoD through the deprecated `ForgeSimpleGpuMeshRenderer`,
while the formal original-shaped model/render/MDIC pipeline reported that it
had not started.

The post-repair runtime pass is materially better: the formal owner now starts,
the original MDIC path submits opaque, temporal, and translucent draws, Oculus
shaderpack pipeline data is consumed, and the simple GPU route is no longer the
visible evidence path while the formal owner is active. Readiness still remains
false because the outer `VoxyRenderSystem` owner is incomplete and the
shaderpack-patched terrain programs are requested but fall back to normal Voxy
terrain shaders.

## Runtime evidence

### Pre-repair pass

The final 2026-06-21 `runClient` pass with
`ComplementaryUnbound_r5.8.1.zip` proved that the Forge/Oculus constructor hook
timing blocker was fixed and that the game can enter a world with the
shaderpack loaded.

The same pass also reported:

```text
formalRendererReady=false
actualRendererDrawEnabled=false
formalDrawPipelineReady=false
originalOculusShaderpackPipelineActive=false
originalOculusShaderpackPipelineDataReady=false
originalOculusShaderpackSource=original-render-pipeline-not-started
lastFailureReason=model-factory-not-started
```

Visible draw logs came from:

```text
Voxy simple GPU mesh render: source=CPU_MESH ... stage=AFTER_TRANSLUCENT_BLOCKS
```

That route is explicitly deprecated and is not the formal original Voxy render
path.

### Post-repair pass

The later 2026-06-21 `runClient` pass after the render-generation lock fix,
model-factory hot-loop fix, formal first-start fix, and Oculus shaderpack source
discovery fix entered the world, remained responsive, printed the parity status
commands, closed normally, and ended with `BUILD SUCCESSFUL`.

The run before the user's manual shader toggle reported:

```text
originalVisibleRendererOwnerReady=true
originalVoxyRunPipelineOrderUsed=true
originalVisibleMdicDrawSubmissionUsed=true
originalMdicOpaqueDrawSubmitted=true
originalMdicTemporalDrawSubmitted=true
originalMdicTranslucentDrawSubmitted=true
originalPipelineFinishCalled=true
originalVisibleFrameFailureCount=0
originalVisibleFrameDrawSubmissionCount=17860
originalVisibleRendererUsesPreviewRoute=false
originalOculusShaderpackPipelineActive=true
originalOculusShaderpackPipelineDataReady=true
originalOculusShaderpackPatchShaderUsed=true
originalOculusShaderpackBindingsUsed=true
originalOculusShaderpackDrawTargetsUsed=true
originalOculusShaderpackUniformsUsed=true
originalOculusShaderpackSsboBindingsReady=true
originalOculusShaderpackImageBindingsReady=true
originalOculusShaderpackBlendStateReady=true
originalOculusShaderpackTaaReady=true
originalOculusShaderpackSource=oculus-shaderpack-voxy-patch
originalOculusShaderpackFailureReason=none
```

MDIC status also showed production `prep.comp`, cull raster, and `cmdgen.comp`
dispatches, plus the new shader fallback flags:

```text
productionCmdgenCompUsed=true
productionPrepCompUsed=true
productionCullRasterUsed=true
prepDispatchCount=19918
cullRasterCount=19918
cmdgenDispatchCount=19918
opaquePatchedShaderRequested=true
opaquePatchedShaderUsed=false
opaquePatchedShaderFallbackUsed=true
translucentPatchedShaderRequested=true
translucentPatchedShaderUsed=false
translucentPatchedShaderFallbackUsed=true
```

This means the remaining shaderpack problem moved from "Voxy patch files are not
seen by Oculus" to "Voxy patch files are seen, but the patched terrain programs
do not compile against the current Forge/Oculus uniform and sampler namespace".
The compile errors named missing `vxModelView`, `vxProj`, previous/inverse
matrix uniforms, Nether biome uniforms, `colortex18`, `colortex19`,
`miplevel`, and `vxDepthTexOpaque`, so visual parity is still degraded even
though the renderer stays responsive and falls back safely.

The user then manually disabled shaders in the Oculus UI. Log entries after:

```text
Shaders are disabled because enableShaders is set to false in iris.properties
Shaders are disabled
```

are environment/user action evidence, not a Voxy renderer failure.

## Confirmed issues from the pre-repair runtime pass

The `Required correction` cells below preserve the action list from the audit
moment. The follow-up status section supersedes the rows that have since been
fixed in code.

| Severity | Area | Finding | Evidence | Required correction |
| --- | --- | --- | --- | --- |
| P0 | Formal pipeline lifecycle | `ForgeOriginalVoxyModelPipeline` has no normal first-start trigger. `clientTick()` only schedules start after `requestStart()` sets `startRequested=true`; normal world tick only creates the active `WorldEngine`. Oculus reload marks `RELOAD_PENDING` when the owner was never started, but does not request first start. | `ForgeOriginalVoxyModelPipeline.clientTick()`, `requestStart(...)`, `shouldScheduleStart()`, `markOculusWorldRenderingSettingsReload()`; latest status had `startRequests=0`. | Add an original-equivalent first-start lifecycle owner after the active world and Oculus pipeline data are ready. This must not be a command-only or debug preset start. |
| P0 | Actual visible pixels | The current visible LoD path is `ForgeSimpleGpuMeshRenderer`, not the original MDIC renderer. It uses vanilla `GameRenderer::getPositionColorShader`, `DefaultVertexFormat.POSITION_COLOR`, skips translucent buffers, and does not consume `ModelStore`, atlas textures, original terrain shader, or shaderpack patches. | `ForgeSimpleGpuMeshRenderer`, `ForgeGpuMeshBuffer`, `ForgeGpuMeshUploadManager`; latest log reported simple GPU mesh draws. | Do not use `lod`, `overlay`, `gl_heap_readback`, or simple GPU draw output as formal visual evidence. Disable or isolate this route during formal runtime validation. |
| P0 | Data source / ownership | `ForgeVoxyInstance.createActiveWorldSkeleton()` creates an empty in-memory `WorldEngine`. Formal rendering can only see data after ingest. The tick scanner is small and validation-shaped by default, while Embeddium mixin ingest writes into the same active engine through a separate path. | `ForgeVoxyInstance.createActiveWorldSkeleton()`, `ForgeChunkIngestManager`, `ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin`, `VoxelIngestService`. | Define the formal original-equivalent WorldEngine data owner and ingest cadence. Status output must distinguish empty world, mixin ingest, and tick-scanner ingest. |
| P1 | Oculus/Iris translucent depth | Original `IrisVoxyRenderPipeline` owns a separate translucent `DepthFramebuffer`. Forge currently attaches the same `depthStage.depthTextureId()` to the translucent framebuffer and then blits depth/stencil from the opaque framebuffer to that target. The source and destination depth attachment can be the same texture. `finishOculus()` also blits from the opaque depth texture rather than translucent depth. | Original `IrisVoxyRenderPipeline.fbTranslucent`; Forge `ForgeOriginalVoxyNormalPipelineTargets.resizeExternal(...)`, `ForgeOriginalVoxyRenderPipeline.postOpaquePreTranslucentOculus(...)`, `finishOculus(...)`. | Add an original-equivalent translucent depth framebuffer/texture for Oculus shaderpack draws and use it for post-opaque transfer and final vanilla depth blit. |
| P1 | Shaderpack TAA injection | Original `HierarchicalOcclusionTraverser.lateStageCompile(...)` injects pipeline TAA into traversal, and original `MDICSectionRenderer` injects TAA into the cull raster shader when the pipeline has TAA. At audit time Forge had `ForgeOriginalVoxyRenderPipeline.taaFunction(...)`, but `ForgeOriginalVoxyHierarchicalOcclusionTraverser.buildOnRenderThread(...)` and the Forge cull raster build path did not consume it. | Original `HierarchicalOcclusionTraverser.lateStageCompile(...)`, original `MDICSectionRenderer` cull shader build; Forge `ForgeOriginalVoxyHierarchicalOcclusionTraverser.buildOnRenderThread(...)`, `ForgeOriginalVoxyMdicSectionRenderer.buildOnRenderThread(...)`. | Fixed in follow-up: the render pipeline/TAA hook is now threaded into Forge HOC and cull raster compilation before shaderpack visual validation. |
| P1 | Render distance coverage | Original `VoxyRenderSystem.setRenderDistance(...)` passes `ceil(renderDistance + 1)` to `RenderDistanceTracker` to cover the outer ring. Forge formal startup passes `ceil(originalVoxySectionRenderDistance)` without the extra ring. | Original `VoxyRenderSystem.setRenderDistance(...)`; Forge `ForgeOriginalVoxyModelPipeline.startOnRenderThread(...)`. | Match the original `+1` outer-ring policy. |
| P1 | Frame catch-up cadence | Original `AbstractRenderPipeline.innerPrimaryWork(...)` loops node tick, cleaner tick, and traversal while `frexStillHasWork` remains true. Forge `renderEmbeddiumCutout(...)` runs one geometry-sync tick, one cleaner tick, one traversal, then command generation. Movement can therefore lag behind original update cadence. | Original `AbstractRenderPipeline.innerPrimaryWork(...)`; Forge `ForgeOriginalVoxyModelPipeline.runOriginalInnerPrimaryWorkBeforeTraversal(...)` and `renderEmbeddiumCutout(...)`. | Restore an original-equivalent catch-up loop or document a platform blocker if Embeddium hook timing prevents it. |
| P1 | Runtime preset route confusion | Runtime presets named `lod`, `overlay`, and `gl_heap_readback` enable `ForgeSimpleGpuMeshRenderer`. Their names can make debug/proof output look like formal LoD output. | `ForgeVoxyRuntimeOverrides.applyLodPreset()`, `applyOverlayPreset()`, `applyGlHeapReadbackPreset()`. | Rename, quarantine, or mark these as legacy/debug in command output. Formal validation should use the original model pipeline status, not these presets. |
| P1 | Status reporting risk | Some historical formal owners still compute readiness from source-file marker checks or hard-coded preview facts. Example: `ForgeFormalVisibilityOwner` reports CPU candidate snapshot readiness while the class itself is deprecated for the current route. | `ForgeFormalVisibilityOwner.fileContains(...)`, `isCpuCandidateSnapshotReady()`, deprecated prototype inventory. | Keep historical status separate from current original-shaped owner status. Do not aggregate legacy status into readiness. |
| P2 | Model tint index | `ForgeOriginalVoxyModelFactory.firstTintIndex(...)` returns `0` for any tinted face instead of preserving the real quad tint index. Models using non-zero tint indices can bake wrong colors. | `ForgeOriginalVoxyModelFactory.firstTintIndex(...)`, `createTintPlan(...)`. | Preserve the actual tint index from Forge/Embeddium baked quad data where available, or document an exact Forge blocker. |
| P2 | Shader patch fallback visibility | `ForgeOriginalVoxyMdicSectionRenderer.compilePatchedOrNormal(...)` logs a patch compile failure and falls back to the normal shader. This mirrors the original broad shape but can hide shaderpack patch failures during visual testing. | `ForgeOriginalVoxyMdicSectionRenderer.compilePatchedOrNormal(...)`. | Expose a status flag for patched shader actually compiled/used. Treat fallback as degraded, not successful shaderpack parity. |
| P2 | GL state restoration | The Embeddium hook adapter is still a narrower boundary than original `VoxyRenderSystem`. Follow-up code now captures/restores framebuffer, viewport, program, VAO, texture 2D/sampler bindings, SSBO bindings, depth/stencil/blend/cull enables, depth mask, color mask, and active texture. Remaining uncovered state includes image bindings, indirect buffers, polygon mode, full per-target blend equations/functions, draw/read buffer selectors, and driver extension state. | `ForgeOriginalVoxyModelPipeline.renderEmbeddiumCutout(...)`, `restoreOriginalVoxyRenderState(...)`. | Either move ownership into a full `VoxyRenderSystem` lifecycle boundary or expand adapter state capture until formal runtime validation is stable. |
| P2 | Model bakery GL setup timing | Original `VoxyRenderSystem` constructs model baking resources inside its owner setup and performs explicit GL finish/state cleanup around construction. Forge calls `ForgeSoftwareModelTextureBakery.prepareOnRenderThread(...)` from scheduled startup without the full original constructor boundary. | Original `VoxyRenderSystem` constructor; Forge `ForgeOriginalVoxyModelPipeline.startOnRenderThread(...)`, `ForgeSoftwareModelTextureBakery.setupTexture(...)`. | Start the formal owner in an original-equivalent render-system lifecycle point or explicitly isolate/restore the GL state touched by texture setup. |

## Follow-up repair status

The implementation pass after this audit fixed the code-level items that could
be repaired safely inside the current Embeddium hook adapter:

```text
ForgeOriginalVoxyModelPipeline now auto-requests first start after a live client world and Oculus block-state id bridge are ready.
Persistent Oculus reload-required state no longer prevents that first-start request from reaching the render-thread startup queue.
ForgeSimpleGpuMeshRenderer now skips when the formal original model pipeline start is requested, queued, or active.
ForgeChunkIngestManager now reports Embeddium mixin chunk/section ingest attempts and updates separately from tick-scanner ingest.
Oculus translucent rendering now owns an independent GL_DEPTH24_STENCIL8 depth texture attached to the translucent framebuffer, and final vanilla-depth blit reads the translucent depth result.
External Oculus framebuffer readiness now requires real opaque/translucent color target texture ids instead of treating an empty external target set as ready.
HOC traversal and MDIC cull raster shader compilation now consume the shaderpack TAA hook.
RenderDistanceTracker startup now applies the original ceil(renderDistance + 1) outer-ring policy.
The inner primary work path now uses an original-shaped Frex catch-up loop when Frex is active and real work remains.
MDIC shader status now reports patched shader requested/used/fallback for opaque and translucent programs.
Model tint planning now reads the actual BakedQuad tint index where Forge exposes it.
The Embeddium hook adapter now captures/restores common GL state instead of resetting it to fixed defaults, including driver-reported texture/sampler and SSBO binding ranges.
Original visible-frame skipped counts no longer double-count early returns from the Embeddium hook adapter.
The first post-repair runClient pass reached the world with ComplementaryUnbound_r5.8.1.zip, then froze. Thread dump showed ForgeOriginalVoxyRenderGenerationService requesting missing model bakes while still holding taskMapLock.
ForgeOriginalVoxyRenderGenerationService now releases taskMapLock before forwarding replacement-task missing-model bake requests.
ForgeOriginalVoxyRenderGenerationService now calls a lightweight ForgeOriginalVoxyModelPipeline.requestBlockBakeInternal path, matching original ModelBakerySubsystem's queue request instead of building full status snapshots from render-generation workers.
ForgeOriginalVoxyModelFactory processing now checks for remaining work with queue emptiness tests instead of repeatedly calling ConcurrentLinkedDeque.size() from the hot processing loop.
Oculus shaderpack sidecar source discovery now includes voxy.json, voxy_opaque.glsl, voxy_translucent.glsl, and voxy_taa.glsl in Oculus ShaderPackSourceNames so the original-shaped sourceProvider can expose Voxy patch files.
```

Remaining blockers after the repair are narrower:

```text
full outer VoxyRenderSystem lifecycle ownership is still not ported
model-bakery startup still lacks the complete original constructor boundary
GL image bindings / indirect buffers / polygon mode are still not fully captured by the hook adapter
shaderpack-patched terrain programs are requested but fall back because the current Forge/Oculus bridge does not yet provide every original patch uniform/sampler declaration required by ComplementaryUnbound
```

## Why the white/bright far LoD artifact is still not shaderpack parity evidence

The first artifact report was captured while the formal original render
pipeline had not started, so those pixels came through a deprecated simple mesh
renderer that:

```text
uses POSITION_COLOR
does not bind the Voxy model atlas
does not bind the original terrain shader
does not consume Oculus shaderpatch output
skips translucent simple buffers
is registered after translucent blocks
```

That path can produce color/brightness artifacts on its own. It should not be
used to judge final Voxy LoD texture, shaderpack, translucency, or depth
correctness.

After the repair, the formal MDIC path does run, but shaderpack-patched opaque
and translucent terrain programs still fall back to the normal Voxy terrain
shader. Any remaining bright/white far-LoD artifact under the shaderpack must be
treated as evidence for the patched-terrain compile/binding blocker, not as
proof that shaderpack parity is complete.

## Additional follow-up checks from the second scan

- Embeddium mixin ingest and tick-scanner ingest are both active data paths.
  Status now reports direct mixin chunk/section attempts and updates separately
  from tick-scanner queued/ingested chunks.
- `ForgeChunkIngestManager` default scan radius and rate are validation-sized:
  radius 2, one chunk per tick, and 20 tick cooldown. That is not enough to
  judge movement parity if the formal renderer depends on it.
- `ForgeOriginalVoxyMdicSectionRenderer` now reports whether shaderpack patched
  programs or fallback normal programs are the actual linked programs.
- `ForgeOriginalVoxyHierarchicalOcclusionTraverser` and
  `ForgeOriginalVoxyMdicSectionRenderer` now inject the shaderpack TAA hook.
- Deprecated preview/debug owners remain registered in `ForgeVoxyInstance`.
  Even when disabled by config, their commands and status output can confuse
  runtime reports.

## Repair order

1. Continue from the patched-terrain shader blocker by comparing the original
   Iris patch uniform/sampler namespace against Forge/Oculus 1.20.1.
2. Keep the simple/debug routes quarantined; do not use them to judge visual
   parity now that the formal owner starts and submits MDIC draws.
3. Port the full outer `VoxyRenderSystem` lifecycle when the shaderpack compile
   blocker is understood.

## Current testing rule

For the next runtime pass, success requires all of the following before judging
visual parity:

```text
startRequests > 0
startRuns > 0
originalVisibleRendererOwnerReady=true
originalVoxyRunPipelineOrderUsed=true
originalVisibleMdicDrawSubmissionUsed=true
originalOculusShaderpackPipelineDataReady=true when shaderpack is active
opaquePatchedShaderUsed=true and translucentPatchedShaderUsed=true when judging shaderpack visual parity
no simple GPU mesh draw submissions used as the visible result
```

Until the patched shader status is true, screenshots and white/bright far-LoD
artifacts are evidence about the remaining shaderpack bridge or data-ingest
timing, not final original Voxy shaderpack parity.
