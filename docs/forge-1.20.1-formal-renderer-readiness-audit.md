# Forge 1.20.1 formal renderer readiness audit

This document measures readiness only against the original Voxy owner and draw
chain. Preview pixels, synthetic validation, manual readback commands, status
DTOs, and removed prototype routes never count.

## Current verdict (XXV re-audit, 2026-07-13)

```text
ORIGINAL_RENDERER_CHAIN_IMPLEMENTED=true
ONLY_VISIBLE_ROUTE_IS_ORIGINAL_EQUIVALENT=true
XXV_STATIC_SOURCE_AUDIT=passed
XXV_FRONTEND_PARITY_CORRECTIONS=implemented
XXV_FINAL_JAR_AUDIT=passed-after-option-id-correction
XXV_BROAD_VISUAL_ACCEPTANCE=passed
XXV_F3_VISIBILITY_RUNTIME_ACCEPTANCE=passed
XXV_EMBEDDIUM_PAGE_RUNTIME_ACCEPTANCE=passed
XXV_CONFIG_APPLY_RUNTIME_ACCEPTANCE=passed
XXV_TARGETED_FRONTEND_RUNTIME_ACCEPTANCE=passed
WHOLE_ORIGINAL_MOD_PARITY=passed-with-documented-platform-adaptations
```

XXIV's renderer and whole-mod regression passed and was explicitly approved by
the user. XXV reopened the release decision for a deeper all-source,
optional-integration, retired-code, naming, Mixin/refmap, dependency/native, and
final-artifact audit. Therefore the historical XXIV approval remains evidence,
while the completed XXV gates are the current completion claim.

The broad XXV user visual/lifecycle run also passed without a renderer anomaly.
It nevertheless exposed two narrower frontend omissions: Voxy diagnostics were
visible while F3 was closed, and the nine Voxy settings lived in a standalone
Forge screen rather than pages inside Embeddium. Those migrations are now
corrected. The refreshed post-correction artifact passes qualification, and the
user's focused F3/menu observation completed without a reported anomaly. The
follow-up Rendering off/on Apply lifecycle also passed, closing the current
qualification gate.

The former `formalRendererReady`, `actualRendererDrawEnabled`,
`formalDrawPipelineReady`, and `wholeOriginalModParity` values were telemetry
published by the removed parity/status command surface. They did not enable or
disable rendering. XXV removes those fields rather than preserving a second,
status-only ownership model. Current acceptance is based on the real owners,
build/JAR evidence, logs, and the final user-observed runtime gate.

## Required Forge frontends

Embeddium replaces Sodium and Oculus replaces Iris. Both are hard client
prerequisites. They are platform frontends, not substitute Voxy renderers.

## Required original owners and current route

| Original owner/contract | Active Forge owner/adaptation |
| --- | --- |
| `ModelBakerySubsystem` / `ModelFactory` / `ModelStore` | Directly ported owners with Forge 1.20.1 baked-model/material adapters |
| `SoftwareModelTextureBakery` / `SoftwareRasterizer` / `TextureUtils` / `ModelQueries` | Original software bake/raster/model semantics; Forge supplies atlas/model API access |
| `RenderGenerationService` / `RenderDataFactory` / `BuiltSection` | Directly ported generation and packed geometry chain |
| `BasicAsyncGeometryManager` / `BasicSectionGeometryData` | Directly ported allocation, metadata, sparse upload/removal, and resource reuse |
| `AsyncNodeManager` / `NodeManager` / `NodeCleaner` | Original request/tree/cleaner ownership; HOC alone owns the GPU node buffer |
| `RenderDistanceTracker` / `ViewportSelector` / `MDICViewport` | Directly ported tracking and per-view ownership |
| HiZ / `HierarchicalOcclusionTraverser` | Original depth hierarchy, traversal, render-list, and request-batch path |
| `MDICSectionRenderer` / production `cmdgen.comp` | Original prep, cull, command generation, translucent tail, and indirect draws |
| terrain pipeline / Iris patch contract | `ForgeOriginalVoxyRenderPipeline` plus real Oculus patch/data/uniform/target adapters |
| `VoxyRenderSystem` lifecycle | `ForgeOriginalVoxyRenderSystem`; `ForgeOriginalVoxyModelPipeline` is only the Forge event/render-entry lifecycle adapter |

## Active visible frame route

```text
Embeddium DefaultChunkRenderer CUTOUT hook
 -> ForgeOriginalVoxyModelPipeline.renderEmbeddiumCutout
 -> ForgeOriginalVoxyRenderSystem
 -> preSetup
 -> ChunkBoundRenderer depth
 -> setup
 -> MDICSectionRenderer.renderOpaque
 -> source-depth HiZ + HierarchicalOcclusionTraverser
 -> MDICSectionRenderer.buildDrawCalls
 -> renderTemporal
 -> postOpaquePreperation
 -> postOpaquePreTranslucent
 -> renderTranslucent
 -> finish
 -> post-frame upload/render-distance work
```

The Embeddium mixin owns no Voxy render resources. It passes the live matrices
and camera at the Forge draw boundary. Oculus shadow rendering selects the
original shadow-skip behavior; normal/shaderpack targets are owned by the active
pipeline generation.

## Evidence that remains valid

The user-completed XXIV regression covered no shaderpack, multiple shaderpacks
and switching, overworld/nether transitions, standalone F3+T, logout/login,
multiplayer identity isolation, persistent storage recovery, importers, Photon
water, and normal shutdown. It found no active renderer defect. IterationT is a
post-migration compatibility TODO because original Voxy has no adaptation or
sidecar for it.

XXV has additionally established:

- original DH and Bobby import routes, eligible Chunky/Acedium/GPU-selection
  compatibility, formal F3 data, and original GL/GPU debug/timing owners;
- removal of the parity/status registrar and write-only readiness/statistics
  shells without changing render control flow;
- exact simple names for direct ports and explicit Forge names only for platform
  adapters or split lifecycle owners;
- production Mixin annotation processing, generated SRG refmap, and reobfuscated
  JarJar task wiring;
- 45/47 original shader files byte-identical, with only the documented
  Embeddium `outline.vsh` and reverse-AABB `screenspace.glsl` adaptations.
- F3 visibility now follows `Minecraft.options.renderDebug`, and the original
  nine-setting General/Rendering config surface is contributed to Embeddium by
  official `OptionGUIConstructionEvent` pages with shared repeated-Apply
  storage; the standalone Forge config screen is removed.
- config effects now preserve the active `WorldEngine` for renderer-only
  changes, reload Oculus for enabled/rendering changes like original
  `IrisUtil.reload()`, and restore the original render-distance minimum.
- strict enabled-lifecycle parity remains a documented non-blocker: Forge
  immediately tears down the renderer, cancels imports, clears/reselects the
  active world, and reloads Oculus, but keeps its event shell/service pool as
  long-lived singletons and lets the released `WorldEngine` reach normal idle
  cleanup. Original `VoxyCommon.shutdownInstance/createInstance` replaces all
  instance owners immediately. This stable Forge event-shell adaptation is not
  evidence against renderer readiness and remains a documented TODO outside the
  completed focused frontend acceptance gate.

## Current command surface

```text
/voxy reload
/voxy import world|bobby|distant_horizons|raw|zip|current|cancel
/voxy debug verifyTLNChildMask [attemptRepair]
```

`verifyTLNChildMask` is the original developer `DebugUtils` behavior. The old
`parity_route_status`, `original_voxy_*_status`, and XX.4 audit commands are
historical evidence only and are no longer registered.

## Final qualification gate

Readiness may be re-approved only after all of the following are true:

1. Retired-code/name audit has no actionable residue.
2. `compileJava`, the full test/build, and `git diff --check` pass.
3. The final reobfuscated JarJar contains every required class, Mixin/refmap
   mapping, asset, pinned dependency and native; deleted legacy/status classes
   and forbidden Fabric descriptors are absent.
4. The client starts from `D:\Projects\voxy`; the user confirms F3 off/on/off
   visibility, both Voxy pages inside Embeddium, repeated Apply, and
   representative live/renderer-reload settings; shutdown is normal in
   `latest.log`.

Items 1-3 are satisfied by the post-correction production build and artifact
audit: `clean test build jarJar` passed; 7 suites / 20 tests completed with zero
failures, errors, or skipped tests; and `git diff --check` passed. The
97,319,501-byte reobfuscated JarJar has SHA-256
`1cdfda276575b53a665c534bc1f00044c6ba8a05644b1d77c5b5ab5acc788485`.
It contains `ForgeOriginalVoxyConfigSnapshot`,
`ForgeOriginalVoxyEmbeddiumOptions`, language resources, and all registered
Mixins, while the retired `ForgeOriginalVoxyConfigScreen` is absent. A dedicated
`Class.forName` initialization test guards against Embeddium page/option
identifier type collisions.

The initial final-gate launch also caught and closed one Minecraft-version
adaptation before acceptance: original Voxy's GPU-selection Mixin targeted an
`Options.save()` call that is absent from the Forge 1.20.1 constructor. The
required injection now uses the sole pre-window `RenderSystem.initBackendSystem`
boundary. Because Forge 1.20.1's Mixin 0.8.5 rejects callback injection inside
a constructor, the final implementation strictly redirects that one static
call, runs the original body first, and returns the original backend result.
This preserves the original hoisted timing without weakening `require` or
vanilla remapping. The rebuilt artifact and bytecode audit prove the exact
redirect signature/order are present and the obsolete injector/target are
absent.

The subsequent login gate also corrected original Voxy's newer
`ClientChunkCache.drop(ChunkPos)` handler to Forge 1.20.1's real
`drop(int,int)` descriptor. The handler remains a required HEAD injection and
uses the same direct storage-ring lookup, coordinate validation, and Bobby-only
pre-unload ingestion. Final bytecode and the all-vanilla-Mixin signature audit
confirm this adapter and every remaining vanilla target match 1.20.1.

The renderer-construction gate then exposed one source-processing omission in
Forge `NodeCleaner`: its sorter had not used original
`PrintfDebugUtil.PRINTF_processor`, leaving the active `node.glsl` debug
`printf` in expanded GLSL. The corrected chain now expands imports, applies the
original processor, injects cleaner/environment defines, and compiles, in the
same order as original `Shader.Builder`. A focused test proves the normal
configuration contains no active shader `printf`; the rebuilt production
artifact was used for the completed final run.

The earlier broad machine-verifiable client gate passed and remains valid
evidence, but it predates the frontend corrections and therefore does not by
itself satisfy the updated item 4. That anchored run successfully exercised the
normal route, repeated Complementary shaderpack rebuilds, overworld/nether/end
round trips, F3+T resource reload, high-speed streaming, and normal shutdown.
Every rebuild created
`ForgeOriginalVoxyRenderPipeline` with `MDICSectionRenderer`; the post-exit log
scan found no Voxy failure/error, NodeCleaner compile error, uncaught exception,
OOM, or crash, and all runtime owners shut down cleanly.

The user subsequently accepted the broad visual/lifecycle result and completed
the corrected artifact's focused F3/menu observation without reporting an
anomaly. The log records Voxy page registration at `20:20:20.204`, Embeddium
option-GUI construction at `20:21:07`, no Voxy option-identifier/initializer
failure, renderer shutdown completion at `20:21:37.485`, and normal persistent
`WorldEngine` plus instance closure at `20:21:39`. The only page warning came
from Oculus's shader page missing its own ID, not from either Voxy page.

The required config-Apply follow-up also passed. Applying `Rendering` off
completed Voxy flush/shutdown at `20:33:39.764-20:33:39.769`; Oculus destroyed
and recreated its pipeline at `20:33:39.770-20:33:40.214`, while Voxy correctly
remained absent. Applying `Rendering` on persisted `enableRendering=true` at
`20:34:13`, reloaded Oculus at `20:34:13.953-20:34:14.363`, and recreated
`ForgeOriginalVoxyRenderSystem` with `MDICSectionRenderer` at
`20:34:15.802-20:34:16.579`. The user confirmed LOD disappeared and returned as
instructed, with no visual anomaly.

The run then shut down the renderer, server, persistent `WorldEngine`, and Voxy
instance cleanly at `20:34:22-20:34:23`; `runClient` exited 0 with
`BUILD SUCCESSFUL`. The targeted scan found no Voxy error/warning,
option-identifier/initializer error, or config failure. All focused frontend
runtime fields and current whole-mod acceptance therefore pass. The documented
long-lived Forge event-shell adaptation for enabled changes remains a separate
non-blocking strict-lifecycle TODO, and IterationT remains post-migration
compatibility work because original Voxy has no adaptation for it.
