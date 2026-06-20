# Forge 1.20.1 Embeddium/Oculus frontend mapping

This document controls the Sodium/Iris frontend replacement work for the Forge
parity route. It is not a renderer implementation document.

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

If the jars exist, Gradle adds them as deobfuscated `compileOnly` and
`runtimeOnly` frontend dependencies. If they are absent, the current partial
Forge source set still compiles because the active source set does not yet
compile the original client Sodium/Iris integration classes.

## Forge frontend compatibility status

The active parity command surface includes:

```text
/voxy frontend_compat_status
```

This status uses Forge `ModList` and reports:

```text
embeddiumLoaded
oculusLoaded
embeddiumVersion
oculusVersion
frontendPrerequisitesReady
fabricLoaderUsed=false
sodiumRuntimeModIdUsed=false
irisRuntimeModIdUsed=false
javaPackageGlobalRenameAllowed=false
rendererFrontendHookReady=false
shaderpackFrontendHookReady=false
```

This command is a prerequisite check only. It does not load Embeddium/Oculus
classes, register renderer hooks, connect shaderpack integration, or claim
formal renderer readiness.

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
`net.caffeinemc.mods.sodium.api.config.*`, but the checked Embeddium reference
tree does not expose that API. Embeddium uses Forge-side option construction
events and GUI classes instead. Config UI migration must be handled separately
and must not block renderer parity.

Fog classes are not a direct package-preserving match in the checked
Embeddium tree:

```text
net.caffeinemc.mods.sodium.client.util.FogParameters
net.caffeinemc.mods.sodium.client.util.FogStorage
```

Any Forge port of original `Viewport`, `VoxyRenderSystem`, and Iris/Oculus
viewport capture must inspect Embeddium and Oculus render state sources before
adding an adapter.

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

## Implementation order

1. Add local Embeddium/Oculus jars through `dev-mods/` or Gradle properties.
2. Port a small `ForgeFrontendCompat` or equivalent loader helper:
   `embeddiumLoaded`, `oculusLoaded`, and version/reporting fields. Status:
   first command/status layer present.
3. Migrate original Sodium imports by package category, starting with low-level
   color and renderer-front classes, not the config UI.
4. Replace Fabric `FabricLoader` checks with Forge `ModList` checks.
5. Port Embeddium renderer lifecycle hooks against the actual Embeddium class
   signatures.
6. Port Oculus shaderpack integration against the actual Oculus/Iris package
   signatures.
7. Only after the frontend hooks are exact, connect them to the original
   VoxyRenderSystem/MDIC route.

## Current blockers

```text
frontend jars are optional local dev inputs, not repository artifacts
Sodium config API does not map directly to checked Embeddium sources
FogParameters/FogStorage require a dedicated Forge/Embeddium/Oculus audit
no Embeddium/Oculus mixins have been ported yet
no original VoxyRenderSystem frontend hook has been enabled yet
```
