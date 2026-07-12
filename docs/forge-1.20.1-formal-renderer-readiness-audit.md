# Forge 1.20.1 formal renderer readiness audit

This document measures renderer readiness only against the original Voxy render
owner chain. Preview pixels, sample uploads, synthetic validation, debug draws,
and the removed K-era routes never count.

## Current verdict (XX.7 audit, 2026-07-12)

```text
TECHNICAL_RENDERER_READINESS_PREREQUISITES=true
READINESS_DECISION=approved
FORMAL_RENDERER_READY=live-owner-derived
ACTUAL_RENDERER_DRAW_ENABLED=live-MDIC-draw-derived
FORMAL_DRAW_PIPELINE_READY=live-production-path-derived
EARLY_USABLE_LOD_RENDERER_READY=retired     # historical document flag; no runtime field
WHOLE_ORIGINAL_MOD_PARITY=false             # persistent storage and other content remain
```

The original-equivalent renderer owners now exist, are connected, submit the
visible LOD pixels, and passed the required runtime regression. After explicit
user approval, the three live readiness fields are wired to current-lifecycle
owner/production-command/draw evidence. They are true in a qualified active
world after the current owner submits its own MDIC frame, and correctly remain
false while no owner exists, during a rebuild, or before the first real draw.
This verdict is not a claim that every non-renderer Voxy feature has been ported.

## Required Forge frontends

The Forge route requires Embeddium in place of Sodium and Oculus in place of
Iris. Both are hard runtime prerequisites; neither is a substitute renderer.

## Required original owners and current evidence

| Original owner/contract | Active Forge parity owner | Evidence |
| --- | --- | --- |
| `ModelBakerySubsystem` / `ModelFactory` / `ModelStore` | `ForgeOriginalVoxyModelBakerySubsystem` / `ForgeOriginalVoxyModelFactory` / `ForgeOriginalVoxyModelStore` | Original worker + render-thread drain, software bake, model/colour/atlas upload, dedupe, custom shaderpack id, and GPU readback are active. XX.6 restored original drain-all queue conservation. |
| `RenderGenerationService` / `RenderDataFactory` | `ForgeOriginalVoxyRenderGenerationService` / `ForgeOriginalVoxyRenderDataFactory` | Real `WorldSection` input produces original `BuiltSection` geometry through original service scheduling and model-miss requeue. |
| `BasicAsyncGeometryManager` / `BasicSectionGeometryData` | Forge original-parity geometry manager/data owners | Original section-id/heap/metadata layout, sparse commitment, upload/removal/scatter, and `RenderResourceReuse` geometry ownership are active. |
| `NodeManager` / `NodeCleaner` / `RenderDistanceTracker` | Forge original-parity node, cleaner, and tracker owners | CPU/GPU node audit, metadata cross-check, request flow, top-level ring tracking, and cleaner visibility are active and consistent. |
| `ViewportSelector` / `MDICViewport` / HiZ / HOC | Forge original-parity viewport, depth, HiZ, and traverser owners | Real source depth builds the Voxy HiZ; production HOC produces the render-list and request batches. |
| `MDICSectionRenderer` / production `cmdgen.comp` | `ForgeOriginalVoxyMdicSectionRenderer` | Production prep, raster cull, cmdgen, translucent prefix/build tail, 20-byte commands, and indirect draw submission are active. |
| terrain shader / Iris patch contract | `ForgeOriginalVoxyRenderPipeline` plus Oculus patch owners | Original normal and patched opaque/translucent programs, TAA, UBO/SSBO/sampler/image bindings, blend, depth transfer, SSAO, and final blit are active. |
| `VoxyRenderSystem` lifecycle | `ForgeOriginalVoxyRenderSystem` | One outer owner constructs and frees the entire renderer in original order. `ForgeOriginalVoxyModelPipeline` is now only the Forge event/hook/status adapter. |

## Active visible frame route

There is one visible entry:

```text
Embeddium DefaultChunkRenderer CUTOUT adapter
 -> ForgeOriginalVoxyModelPipeline.renderEmbeddiumCutout
 -> ForgeOriginalVoxyRenderSystem owners
 -> preSetup
 -> chunk-bound depth
 -> setup
 -> MDIC renderOpaque
 -> source-depth HiZ + HOC inner work
 -> production buildDrawCalls
 -> renderTemporal
 -> postOpaquePreperation
 -> postOpaquePreTranslucent
 -> renderTranslucent
 -> finish
 -> post-frame upload/render-distance work
```

This matches original `VoxyRenderSystem.renderOpaque()` and
`AbstractRenderPipeline.runPipeline()` ordering. The Embeddium mixin is only the
Forge platform entry adapter and owns no renderer resources.

## Deprecated-route isolation

The historical preview/sample/direct-GL/simple-GPU/legacy geometry-MDIC owners
have been physically removed from active source. The only retained
`ForgeCpuMeshLayer` is an active model-bakery layer enum, not a CPU preview
renderer. Current command readbacks inspect the real original-parity buffers and
do not submit substitute pixels.

## Runtime qualification

The user completed the post-XX.6 matrix without a renderer regression:

```text
shaderpack switching
dimension switching
standalone F3+T reload
logout/login
client exit
Complementary / BSL and multiple additional shaderpacks
Photon LOD water
```

The final log records repeated Oculus owner-generation restarts, no Voxy model
upload/drop/FAILED_SAFE failures, normal original-parity instance shutdown, and
Minecraft `Stopping!`. IterationT 3.2.0 is a post-parity pack-specific TODO: the
original Voxy source has no IterationT adaptation and the tested pack has no
Voxy sidecars.

## Remaining work classification

Not a renderer-readiness blocker:

```text
persistent WorldEngine storage and id-mapping backend (whole-mod parity)
IterationT pack-specific compatibility (post-parity, absent upstream)
optional compatibility/content items from the unported-content inventory
unused MDIC config-key cleanup and ForgeCpuMeshLayer naming cleanup
```

Terminal render-resource parity found and closed by this audit:

```text
original RenderResourceReuse clears cached model atlases and geometry buffers
Forge previously cleared only cached geometry buffers
XX.7 working tree now also deletes cached model atlases at full instance shutdown
compileJava and full build passed
final runClient exited 0 after repeated owner rebuilds and normal instance shutdown
latest.log contains no Voxy warning/error, upload/drop, FAILED_SAFE, OOM, or GL-invalid event
```

The first post-wiring runtime status check correctly reported
`formalDrawPipelineReady=true`, but kept the two model-level readiness fields
false. The detailed status isolated the sole false prerequisite to the
historical `BasicAsyncGeometryManager` status slot named
`originalNodeManagerParityReady`; the live `AsyncNodeManager` owner in the same
snapshot reported `originalAsyncNodeManagerFullParityReady=true`, with 8,207 HOC
traversals, 8,118 visible MDIC draw frames, zero visible-frame failures, and all
model/generation/geometry/viewport/pipeline owners ready. Original Voxy ownership
is `AsyncNodeManager -> NodeManager -> BasicAsyncGeometryManager`, so the geometry
manager cannot authoritatively report NodeManager readiness. XX.7 removes that
stale proxy from the formal predicate and publishes the live AsyncNodeManager
parity value in the aggregate status field. A final runtime status check remains
required after this correction. That final check passed: `/voxy
parity_route_status` reported `formalRendererReady=true`,
`actualRendererDrawEnabled=true`, and `formalDrawPipelineReady=true` in the
active world; the client then completed normal render-system, instance, and
Minecraft shutdown with `runClient` exit code 0.

## Readiness wiring

1. `formalRendererReady` derives from the live original model, generation,
   geometry, node, traversal, viewport, MDIC, pipeline, and outer-owner chain.
2. `actualRendererDrawEnabled` requires the current owner generation to complete
   original frame order, submit production MDIC terrain, call `finish`, and
   restore external state. Owner creation resets this evidence so a previous
   Oculus/reload generation cannot leak readiness forward.
3. `formalDrawPipelineReady` requires the production prep/cull/cmdgen dispatches,
   the real MDIC renderer, a successful indirect terrain submission, and a ready
   render pipeline. It does not depend on a manual readback command.
4. `/voxy parity_route_status` reports these live values and separately reports
   `wholeOriginalModParity=false` with persistent storage as the next target.
5. `earlyUsableLodRendererReady` was not recreated; it remains an obsolete
   preview-era document flag.

Whole-mod parity remains separately false until persistent storage and the
remaining accepted migration inventory are completed. The post-wiring
`runClient` validation confirmed all three live renderer fields before the XX.7
commit.
