# Forge 1.20.1 Embeddium/Oculus frontend mapping

This document controls the Sodium/Iris frontend replacement work for the Forge
parity route. It is not a renderer implementation document.

The class/package mapping remains useful. The implementation order and blocker
lists are historical snapshots; XXV current status is in the full render-path
parity audit. In particular, `ForgeFrontendCompat` and its status fields were
removed after dependency enforcement moved entirely to loader/packaging
metadata and the real frontend owners.

## Rule

```text
runtime mod ids change
original Voxy logic does not change
Java packages change only when Embeddium/Oculus actually moved the class
```

Do not do a global text replacement from `sodium` to `embeddium` or from
`iris` to `oculus`. The local reference sources show that Embeddium and Oculus
intentionally preserve many upstream package names.

## Local reference sources

The current workspace contains these untracked reference source trees:

```text
embeddium-20.1-forge/
Oculus-1.20.1-new/
```

They are for inspection only. They must not be committed into this repository.

## Runtime dependency mapping

| Original Voxy frontend | Forge frontend | Forge mod id | Notes |
| --- | --- | --- | --- |
| Sodium | Embeddium | `embeddium` | hard client prerequisite |
| Iris | Oculus | `oculus` | hard client prerequisite; Oculus metadata declares `provides = ["iris"]` |
| Fabric Loader / Fabric API | Forge/FML | `forge` | platform replacement, not an extra runtime mod |

Forge runtime checks must use `ModList` and these ids:

```text
embeddium
oculus
```

Do not use Fabric `FabricLoader` in the Forge route.

## Development jar wiring

The root Gradle build now accepts local frontend jars without committing them.

Preferred local layout:

```text
dev-mods/embeddium-*.jar
dev-mods/oculus-*.jar
```

Alternative explicit paths:

```powershell
.\gradlew compileJava -PvoxyEmbeddiumDevJar=C:\path\embeddium.jar -PvoxyOculusDevJar=C:\path\oculus.jar
```

Gradle adds the resolved jars as deobfuscated `compileOnly` and `runtimeOnly`
frontend dependencies. The current active source set compiles the real
Embeddium/Oculus adapters, so these are no longer optional placeholder inputs;
they mirror the hard client prerequisites declared in Forge metadata.

## Active Forge frontend ownership

The historical `/voxy frontend_compat_status` command and its status DTO were
removed in XXV. Dependency presence is enforced by Forge metadata/packaging;
renderer and option readiness derive from the real frontend owners:

```text
Embeddium DefaultChunkRenderer hook -> original Voxy render owner
Embeddium OptionGUIConstructionEvent -> Voxy General/Rendering pages
Forge ConfigScreenFactory -> Embeddium SodiumOptionsGUI entry
Oculus pipeline Mixins/bridges -> original Iris patch/target/reload contract
```

None of these platform adapters is a second Voxy renderer or a readiness proxy.

## Sodium to Embeddium package reality

Embeddium's Forge mod id is `embeddium`, but much of its renderer API remains
under Sodium package names. The current reference tree confirms these examples:

| Original Voxy import family | Embeddium class location | Mapping status |
| --- | --- | --- |
| `net.caffeinemc.mods.sodium.api.util.ColorMixer` | same package | keep import |
| `net.caffeinemc.mods.sodium.api.util.ColorARGB` | same package | keep import |
| `net.caffeinemc.mods.sodium.api.util.ColorABGR` | same package | keep import |
| `net.caffeinemc.mods.sodium.client.util.color.ColorSRGB` | `me.jellysquid.mods.sodium.client.util.color.ColorSRGB` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer` | `me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer` | package migrated |
| `net.caffeinemc.mods.sodium.client.gl.device.CommandList` | `me.jellysquid.mods.sodium.client.gl.device.CommandList` | package migrated |
| `net.caffeinemc.mods.sodium.client.gl.device.RenderDevice` | `me.jellysquid.mods.sodium.client.gl.device.RenderDevice` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager` | `me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer` | `me.jellysquid.mods.sodium.client.render.chunk.DefaultChunkRenderer` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer` | `me.jellysquid.mods.sodium.client.render.chunk.ShaderChunkRenderer` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices` | `me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform` | `me.jellysquid.mods.sodium.client.render.viewport.CameraTransform` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.viewport.Viewport` | `me.jellysquid.mods.sodium.client.render.viewport.Viewport` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass` | `me.jellysquid.mods.sodium.client.render.chunk.terrain.TerrainRenderPass` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses` | `me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable` | `me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker` | `me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTracker` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder` | `me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.RenderSection` | `me.jellysquid.mods.sodium.client.render.chunk.RenderSection` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo` | `me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionInfo` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder` | `me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder` | package migrated |
| `net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager` | `me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegionManager` | package migrated |

The original Voxy config integration imports
`net.caffeinemc.mods.sodium.api.config.*`. Embeddium 0.3.32 does not expose that
same builder package, but it does expose the equivalent host boundary:
`OptionGUIConstructionEvent`, `OptionPage`, `OptionGroup`, `OptionImpl`, and
`OptionStorage`. The Forge port now uses those official APIs; a standalone Voxy
screen is no longer the formal route.

Fog classes are not a direct package-preserving match in the checked
Embeddium tree:

```text
net.caffeinemc.mods.sodium.client.util.FogParameters
net.caffeinemc.mods.sodium.client.util.FogStorage
```

Any Forge port of original `Viewport`, `VoxyRenderSystem`, and Iris/Oculus
viewport capture must inspect Embeddium and Oculus render state sources before
adding an adapter.

## Sodium options menu to Embeddium option pages

Original `VoxyConfigMenu` contributes two pages to Sodium. The Forge-equivalent
construction boundary is Embeddium 0.3.32's official event bus, not a custom
screen or Mixin:

```text
ForgeOriginalVoxyEmbeddiumOptions.register()
 -> OptionGUIConstructionEvent.BUS.addListener(...)
 -> OptionPage voxy:general_page
 -> OptionPage voxy:rendering_page
 -> six OptionGroup owners
 -> nine OptionImpl values backed by one shared OptionStorage
```

Page IDs deliberately use the `_page` suffix. Embeddium interns
`OptionIdentifier` globally by namespace/path and rejects the same path with a
different value type; keeping the page at `voxy:rendering` would collide with
original Voxy's Boolean `voxy:rendering` option. The option IDs retain original
identity, including the upstream spellings:

| Page | Setting | Embeddium option ID | Control / original range |
| --- | --- | --- | --- |
| General | enabled | `voxy:enabled` | tick box |
| General | service threads | `voxy:thread_count` | slider 1..platform maximum |
| General | use Embeddium/Sodium builder threads | `voxy:use_sodium_threads` | tick box |
| General | ingest | `voxy:ingest_enabled` | tick box |
| Rendering | rendering | `voxy:rendering` | tick box |
| Rendering | subdivision size | `voxy:subdivsize` | nonlinear input 0..100 -> 28..256 |
| Rendering | render distance | `voxy:render_distance` | input 10..1024, display `value * 2`, store `value / 16` sections |
| Rendering | environmental fog | `voxy:eviromental_fog` | tick box; upstream identifier typo preserved |
| Rendering | SSAO mode | `voxy:ssao_mode` | AUTO / BASIC / BETTER / BEST |

All dependent controls read the current modified values, so changing enabled or
rendering immediately updates predicates before Apply. Fog and SSAO are disabled
while an Oculus shaderpack owns those effects. Both pages share one
`OptionStorage`; after every save it replaces its baseline with the just-applied
snapshot, preserving Embeddium's repeated-Apply lifecycle without replaying old
deltas.

Apply effects match original ownership:

| Change | Effect |
| --- | --- |
| enabled | run the Forge runtime-reload adapter: renderer teardown, import cancellation, active-world deselection/reselection, and Oculus reload |
| service threads | update original service-thread policy |
| use Embeddium threads | update thread policy, rebuild the Voxy render owner, and request vanilla `LevelRenderer` rebuild |
| ingest / subdivision | save only; no forced owner rebuild |
| rendering | rebuild only the render owner, retain `WorldEngine`, request Oculus reload |
| render distance | update the live `RenderDistanceTracker` unless a rebuild supersedes it |
| environmental fog / SSAO | rebuild the Voxy render owner and request vanilla `LevelRenderer` rebuild |

Renderer-only rebuilds use `markConfigurationReload()` and preserve the active
in-memory/persistent world owner. The Oculus reload adapter follows original
`IrisUtil.reload()`: it calls `Iris.reload()` only when a pack is active or
shaders are enabled in config. The standalone
`ForgeOriginalVoxyConfigScreen` is retired.

Enabled is the one strict lifecycle deviation: original
`VoxyCommon.shutdownInstance/createInstance` immediately replaces all instance
owners, while Forge keeps its event shell and service pool as long-lived
singletons and lets the released `WorldEngine` reach normal idle cleanup. This
stable Forge event-shell adaptation remains a non-blocking parity TODO and does
not change the completed focused frontend acceptance matrix.

Original ModMenu itself remains Fabric-only. Its user-visible behavior is
mapped: Forge's Mod List config factory opens Embeddium's `SodiumOptionsGUI`,
which posts the same construction event and displays the Voxy pages. Embeddium
does not expose a public initial-page selector, so the factory cannot forcibly
preselect a Voxy page; this does not change the available settings or their
Apply semantics.

## Iris to Oculus package reality

Oculus's Forge mod id is `oculus`, but its shader API remains under
`net.irisshaders.iris.*`. The current reference tree confirms these examples:

| Original Voxy import family | Oculus class location | Mapping status |
| --- | --- | --- |
| `net.irisshaders.iris.Iris` | same package | keep import |
| `net.irisshaders.iris.api.v0.IrisApi` | same package | keep import |
| `net.irisshaders.iris.gl.IrisRenderSystem` | same package | keep import |
| `net.irisshaders.iris.shadows.ShadowRenderer` | same package | keep import |
| `net.irisshaders.iris.pipeline.IrisRenderingPipeline` | same package | keep import |
| `net.irisshaders.iris.pipeline.WorldRenderingPipeline` | same package | keep import |
| `net.irisshaders.iris.shaderpack.ShaderPack` | same package | keep import |
| `net.irisshaders.iris.shaderpack.programs.ProgramSet` | same package | keep import |
| `net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings` | same package | keep import |
| `net.irisshaders.iris.shaderpack.include.ShaderPackSourceNames` | same package | keep import |
| `net.irisshaders.iris.gl.sampler.SamplerHolder` | same package | keep import |
| `net.irisshaders.iris.gl.sampler.GlSampler` | same package | keep import |
| `net.irisshaders.iris.gl.image.ImageHolder` | same package | keep import |
| `net.irisshaders.iris.gl.uniform.*` | same package family | keep imports after signature audit |
| `net.irisshaders.iris.uniforms.CommonUniforms` | same package | keep import |
| `net.irisshaders.iris.uniforms.custom.CustomUniforms` | same package | keep import |
| `net.irisshaders.iris.targets.RenderTargets` | same package | keep import |
| `net.irisshaders.iris.targets.RenderTarget` | same package | keep import |
| `net.irisshaders.iris.gl.state.FogMode` | same package | keep import |
| `net.irisshaders.iris.gl.state.ValueUpdateNotifier` | same package | keep import |
| `net.irisshaders.iris.gl.texture.InternalTextureFormat` | same package | keep import |
| `net.irisshaders.iris.gl.texture.TextureType` | same package | keep import |

The package names staying as `iris` does not mean the Forge mod dependency is
Iris. Runtime dependency and loader checks must still target `oculus`.

## Mixin target policy

Original Voxy mixin packages are named after the upstream frontend:

```text
me.cortex.voxy.client.mixin.sodium.*
me.cortex.voxy.client.mixin.iris.*
```

For Forge parity, the mixin package names may remain historical, but their
targets and plugin gates must be checked against Embeddium/Oculus classes:

```text
Sodium mixin target -> Embeddium class under me.jellysquid.mods.sodium...
Iris mixin target -> Oculus class under net.irisshaders.iris...
```

Do not apply these mixins without a Forge-side compatibility gate.

The first Forge/Embeddium frontend mixin has now been ported for the original
service-thread sharing path:

```text
original MixinChunkJobQueue
 -> Embeddium ChunkJobQueue target
 -> SemaphoreBlockImpersonator
 -> UnifiedServiceThreadPool.groupSemaphore block
```

The gate is the hard `embeddium` client dependency declared in `mods.toml`.
This mixin does not register a renderer hook and does not instantiate
`VoxyRenderSystem`; it only mirrors the original Sodium builder-thread sharing
mechanism against Embeddium's equivalent `ChunkJobQueue`.

## Historical implementation order (completed/superseded)

1. Add local Embeddium/Oculus jars through `dev-mods/` or Gradle properties.
2. Port a small `ForgeFrontendCompat` or equivalent loader helper:
   `embeddiumLoaded`, `oculusLoaded`, and version/reporting fields. Status:
   historical command/status layer was later removed; Forge metadata and real
   owners now enforce the dependency contract.
3. Migrate original Sodium imports by package category, starting with low-level
   color and renderer-front classes, not the config UI.
4. Replace Fabric `FabricLoader` checks with Forge `ModList` checks.
5. Port Embeddium renderer lifecycle hooks against the actual Embeddium class
   signatures.
6. Port Oculus shaderpack integration against the actual Oculus/Iris package
   signatures.
7. Only after the frontend hooks are exact, connect them to the original
   VoxyRenderSystem/MDIC route.

## ColorSRGB parity note

Original Voxy's `TextureUtils` depends on Sodium `ColorSRGB`. The Forge port
now uses a local `ForgeOriginalVoxyColorSRGB` port copied from the checked
Embeddium fast-srgb8 implementation so the active source set still compiles
without committing local frontend jars. This is not a substitute algorithm; it
is the Embeddium/Sodium table path used to preserve original Voxy mip color
semantics.

## Historical blockers at the mapping checkpoint

| Historical blocker | Current resolution |
| --- | --- |
| frontend jars were optional local dev inputs | Embeddium/Oculus are hard Forge client prerequisites and active compile/runtime inputs; reference source trees remain uncommitted |
| original Sodium config API had no direct package match | resolved through official Embeddium `OptionGUIConstructionEvent` plus `OptionPage`/`OptionGroup`/`OptionImpl`/`OptionStorage` |
| FogParameters/FogStorage needed a dedicated audit | viewport/fog input is mapped at the actual Embeddium/Oculus render boundary |
| only ChunkJobQueue sharing was ported | active Embeddium cutout rendering, option pages, and service-thread sharing all use real frontend owners |
| no original VoxyRenderSystem frontend hook | the Embeddium adapter delegates to the single `ForgeOriginalVoxyRenderSystem` chain |

The first broad XXV visual/lifecycle acceptance passed. After the option-page
and F3 corrections, `clean test build jarJar` passes with 7 suites / 20 tests,
including a `Class.forName` initialization regression for page/option identifier
type collisions. The final JarJar contains the new option/snapshot owners and no
standalone config screen.

The focused F3/menu observation has now passed: the user completed the instructed
run and closed normally without reporting an F3-visibility or Voxy-page anomaly.
`latest.log` records Voxy page registration at `20:20:20.204`, construction of
the Embeddium options GUI at `20:21:07`, no Voxy `OptionIdentifier`,
`IllegalArgumentException`, or initializer failure, renderer shutdown completion
at `20:21:37.485`, and normal `WorldEngine`/instance closure at `20:21:39`. The
only construction-time missing-ID warning was emitted for Oculus's shader page.

Apply/reload behavior passed in the follow-up. Applying `Rendering` off completed
Voxy flush/shutdown at `20:33:39.764-20:33:39.769`; Oculus destroyed and
recreated its pipeline at `20:33:39.770-20:33:40.214`, while Voxy correctly
remained absent. Applying `Rendering` on persisted `enableRendering=true` at
`20:34:13`, reloaded Oculus at `20:34:13.953-20:34:14.363`, and recreated the
Voxy render system with `MDICSectionRenderer` at
`20:34:15.802-20:34:16.579`. The user confirmed LOD disappeared and returned
without a visual anomaly.

Clean renderer/server/`WorldEngine`/instance shutdown followed at
`20:34:22-20:34:23`; `runClient` exited 0 with `BUILD SUCCESSFUL`, and the
targeted scan found no Voxy error/warning, option-identifier/initializer error,
or config failure. The focused frontend gate therefore passes. This result does
not close or remove the separate enabled-config strict lifecycle TODO;
IterationT likewise remains post-migration compatibility work because original
Voxy has no pack-specific adaptation for it.
