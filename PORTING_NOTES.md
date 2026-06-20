# Voxy Forge 1.20.1 Porting Notes

## Current Route

The Forge port is now on the original Voxy parity-remediation route.

```text
original Voxy source is the baseline
```

Historical cached-LoD previews, debug renderers, synthetic fixtures, sample
model paths, and K-stage visible preview work are retained only as historical
evidence while their references are being retired. They are not the formal
renderer route.

## Required Client Dependencies

The Forge port requires these client-side mods:

```text
Embeddium
Oculus
```

Mapping from original Voxy frontends:

```text
Original Fabric Voxy hard dependency: Sodium
Forge parity frontend: Embeddium

Original Fabric Voxy shaderpack integration: Iris
Forge parity shaderpack frontend: Oculus
```

Oculus declares `provides = ["iris"]` in its Forge metadata, so it is the
correct Forge-side target for original Iris integration work. Embeddium retains
many Sodium package/API names internally, so some source-level references may
still use `sodium` package names while the actual Forge mod prerequisite is
`embeddium`.

Development runs must load Embeddium and Oculus as mods. The local source
folders `embeddium-20.1-forge/` and `Oculus-1.20.1-new/` are reference source
trees only unless the developer explicitly wires or builds them for the
workspace. Do not commit those source folders as part of this repository unless
the project policy changes.

## Active Porting Chain

The Forge implementation must converge on the original Voxy chain:

```text
WorldEngine / WorldSection / Mapper
 -> ModelBakerySubsystem
 -> ModelFactory
 -> SoftwareModelTextureBakery / TextureUtils / ModelQueries
 -> ModelStore
 -> RenderGenerationService
 -> RenderDataFactory
 -> BuiltSection
 -> BasicAsyncGeometryManager
 -> BasicSectionGeometryData
 -> RenderDistanceTracker
 -> HierarchicalOcclusionTraverser
 -> ViewportSelector / Viewport
 -> MDICViewport
 -> cmdgen.comp
 -> MDICSectionRenderer
 -> original terrain shader contract
```

If original Voxy depends on Sodium or Iris behavior at a layer, the Forge port
must first inspect that original dependency and then port/adapt it against
Embeddium or Oculus with the same ownership, data layout, lifecycle, and
performance semantics.

## Current Positive Progress

Current parity work has started at the bottom of the model and render-generation
chain:

- original-style `ModelFactory` id mapping, dedupe, metadata cache, and fluid
  lookup structures are being ported under the Forge source set;
- original-style software model texture data now uses a
  `ColourDepthTextureData`-shaped record rather than the deprecated preview
  `FaceTexture` shape;
- original-style `UploadStream`, mip-chain atlas upload, biome LUT upload,
  `RenderGenerationService`, `RenderDataFactory`, `BuiltSection`,
  `ScanMesher2D`, and `OccupancySet` parity pieces exist in the Forge path;
- deprecated preview/debug command paths are documented and must not be
  promoted into the formal route.

These are partial parity pieces, not formal renderer readiness.

## Blocking Gaps

The renderer is not ready until these original owners are ported or adapted:

```text
BasicAsyncGeometryManager
BasicSectionGeometryData
RenderDistanceTracker
HierarchicalOcclusionTraverser
ViewportSelector / Viewport
MDICViewport
production cmdgen.comp
MDICSectionRenderer
original terrain shader contract
VoxyRenderSystem lifecycle
Embeddium/Sodium frontend integration points
Oculus/Iris shaderpack integration points
```

The following readiness fields must remain false until those owners exist:

```text
formalRendererReady=false
actualRendererDrawEnabled=false
formalDrawPipelineReady=false
earlyUsableLodRendererReady=false
```

## Deprecated Historical Paths

Do not build new implementation on:

```text
simple Forge GPU mesh renderer
debug MDIC renderers
sample-set model/atlas paths
synthetic command-generation fixtures
temporary model-id rewrites
visible preview owners
manual QA commands as lifecycle substitutes
```

See:

```text
docs/forge-1.20.1-deprecated-prototype-routes.md
docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md
docs/forge-1.20.1-stage-roadmap.md
```

## Validation

Default validation for code changes:

```powershell
git status
.\gradlew compileJava
```

Run `.\gradlew runClient` only when the changed subsystem needs Minecraft
runtime state, GL behavior, or visual validation. Because Embeddium and Oculus
are now hard client prerequisites, development runtime validation must provide
both mods.
