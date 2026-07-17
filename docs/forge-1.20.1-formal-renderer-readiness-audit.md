# Forge 1.20.1 formal renderer readiness audit

This document measures readiness only against the original Voxy owner and draw
chain. Preview pixels, synthetic validation, manual readback commands, status
DTOs, and removed prototype routes never count.

## Current verdict (XXVIII formal-client and Chunky ingest repair, 2026-07-14)

```text
ORIGINAL_RENDERER_CHAIN_IMPLEMENTED=true
ONLY_VISIBLE_ROUTE_IS_ORIGINAL_EQUIVALENT=true
XXVII_EXHAUSTIVE_LINE_AUDIT=14-passes-final-pass-zero-findings
XXVII_STATIC_AND_ARTIFACT_GATE=35-suites-110-tests-jarJar-passed
XXVII_RUNTIME_REGRESSION=passed-user-2026-07-14
XXVII_RELEASE_READINESS=approved-by-user-finalization-request-2026-07-14
XXVIII_FORMAL_CLIENT_REGRESSION=confirmed-embeddium-0.3.31-linkage-crash
XXVIII_IMPLEMENTATION=implemented-minimum-frontend-watertight-bake-and-custom-renderer-empty-map-repairs
XXVIII_MINIMUM_FRONTEND_GATE=36-suites-114-tests-jarJar-passed-against-exact-0.3.31
XXVIII_FORMAL_RUNTIME_REGRESSION=passed-user-2026-07-14
XXVIII_CHUNKY_PREGGEN_REGRESSION=passed-user-2026-07-14
XXVIII_RELEASE_READINESS=beta-complete-approved-by-user-2026-07-14
WHOLE_ORIGINAL_MOD_PARITY=beta-complete-user-approved-2026-07-14
```

XXIV and XXV remain valid historical acceptance evidence. XXVI then reopened
the implementation for deeper session, ingest, storage, optional-integration,
GL-state, lifecycle, and packaging parity work. XXVII audited the resulting
tree line by line through fourteen independently frozen full passes. Pass 14
reread all 275 executable files (40,139 physical text lines / 1,689,515 bytes)
from zero inherited coverage and found no new issue. The forced clean gate
passed 35 suites / 110 tests plus JarJar, and the consolidated user-observed
runtime regression covered repeated shader rebuilds, overworld/Nether/End
owners, reconnect, and normal shutdown without a reported anomaly.

The first packaged formal-client run then invalidated that release decision.
The formal Embeddium 0.3.31 instance is inside the declared supported range but
lacks a 0.3.32-beta internal transparency-holder class used by the Forge
material adapter. XXVIII removes that hard linkage and mirrors the exact
three-level alpha classifier locally. The complete project now compiles, tests,
and JarJars against the exact formal 0.3.31 JAR. The replacement artifact then
passed the user's formal-client runtime, visual, custom-renderer, and Chunky
pregeneration gates; the Forge port is accepted as beta-complete.

The former `formalRendererReady`, `actualRendererDrawEnabled`,
`formalDrawPipelineReady`, and `wholeOriginalModParity` values were telemetry
published by the removed parity/status command surface. They did not enable or
disable rendering. XXV removed those fields rather than preserving a second,
status-only ownership model. Current acceptance is based on the real owners,
build/JAR evidence, logs, and the final user-observed runtime gate.

## Forge frontends

Embeddium replaces Sodium and remains the hard client prerequisite. Oculus
replaces Iris but is optional, matching original Voxy: an Embeddium-only client
uses the normal render pipeline, while an installed Oculus activates the
shaderpack compatibility path. They are platform frontends, not substitute Voxy
renderers.

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

- original DH and Bobby Reforged import/unload routes, eligible Chunky/Acedium/GPU-selection
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
- XXVI replaced the pre-XXVI process-lifetime service owners with one
  `SessionRuntime` per network connection. Disabling Voxy now destroys the render
  owner, imports, ingest/saving services, unified service pool, active-world map,
  and storage owners before a later enable creates a fresh instance. The earlier
  event-shell strict-lifecycle TODO is historical and closed; only the lightweight
  Forge event listener shell remains process-scoped.

## Current command surface

```text
/voxy reload
/voxy import world|bobby|distant_horizons|raw|zip|current|cancel
/voxy debug verifyTLNChildMask [attemptRepair]
```

`verifyTLNChildMask` is the original developer `DebugUtils` behavior. The old
`parity_route_status`, `original_voxy_*_status`, and XX.4 audit commands are
historical evidence only and are no longer registered.

## Current XXVIII qualification evidence

The final XXVIII source gate uses the exact Embeddium 0.3.31 JAR from the
formal instance and passes **36 suites / 114 tests / 0 failures / 0 errors / 0
skipped** plus reobfuscated JarJar. Artifact inspection confirms the rewritten
material bridge no longer references
`SpriteTransparencyLevelHolder` or its post-0.3.31 implementation package.
The replacement all-JAR is **12,592,017 bytes (12.01 MiB)**, SHA-256
`d8fa3c7cf01f463597e2e0613433d906592b6a1e2edcf85358750ad1d02e8add`, with
459 entries, zero duplicates, and zero bundled frontend/platform classes.

The 0.3.31 compatibility repair has now passed formal startup and world entry in
the user's affected modpack. That run exposed a repeated no-shader diagonal mark
in LOD-baked block faces. XXVIII.2 restores XVI's already accepted watertight
four-edge quad rasterization: triangle splitting remains only for UV/depth
interpolation, not coverage. The regression test now requires continuous 16/16
quad coverage instead of preserving the reverted 9/16 result. The full gate
above includes this repair.

The next formal run proved that active rendering continued for roughly five
minutes, then exposed a separate custom block-entity model classification crash
for `butcher:pestleandmortarblock[facing=south,animation=0]`. Original Voxy does
not yet render block entities into LOD, but it accepts an empty ordinary-quad
bake. XXVIII.3 restores that boundary: custom renderers publish/dedupe a valid
empty model mapping, while a genuinely missing baked model remains fatal. The
new three-route regression test is included in the current gate.

XXVIII.4 additionally ports original Voxy's exact-result Chunky ingest ownership
to Forge 1.20.1, removing the post-ticket coordinate relookup race which omitted
random fast-generated ocean chunks. The user completed the combined formal
runtime gate with the final JAR: the no-shader diagonal remained absent, the
custom-renderer state no longer crashed, Chunky-pregenerated ocean LOD contained
no holes, and no other tested regression was reported. The user explicitly
approved marking the project beta-complete on 2026-07-14.

## Historical XXVII qualification evidence

The exhaustive audit and final forced-clean packaging rerun produced this
accepted artifact:

```text
35 test suites / 110 tests / 0 failures / 0 errors / 0 skipped
voxy-forge-0.2.17-beta-forge-all.jar
size=12,589,718 bytes (12.01 MiB)
SHA-256=50a27befcfb8d9390aac4db77ab76cf25afe9b4a1fa0aa30c554b7654a5b5502
entries=458
duplicate entries=0
bundled Minecraft/Forge/LWJGL/Oculus/Embeddium/Sodium classes=0
```

The 2026-07-14 anchored development client run exited 0 after the user completed the final
visual regression without finding an issue. Runtime logs prove the original
persistent WorldEngine chain, `ForgeOriginalVoxyRenderPipeline`,
`MDICSectionRenderer`, repeated shader lifecycle, all three vanilla dimensions,
disconnect/re-entry, and complete shutdown ownership. The user's finalization
request approved the XXVII packaging, commit, and push. The later formal 0.3.31
crash supersedes that release decision; the evidence remains historical and
does not close XXVIII. The explicit post-migration IterationT compatibility TODO
is unchanged.

## Historical XXV qualification gate

The remainder of this section records the superseded XXV gate and its older
97 MB pre-packaging-cleanup artifact. It is retained as investigation history;
the XXVII evidence above is authoritative.

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
uses the same direct storage-ring lookup, coordinate validation, and Bobby
Reforged-only pre-unload ingestion. Final bytecode and the all-vanilla-Mixin
signature audit
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
runtime fields and current whole-mod acceptance therefore pass. XXVI later closed
the enabled-config strict-lifecycle difference with per-network-session ownership.
IterationT remains post-migration compatibility work because original Voxy has no
adaptation for it.
