# Forge 1.20.1 original Voxy full render-path parity audit

This is the current route-control ledger. It supersedes old K-stage and preview
audit language.

## Current rule

```text
strict bottom-up original Voxy parity
```

Every subsystem must be traced to original Voxy source before implementation.
Deprecated preview, sample, synthetic, fallback, and QA-only paths must not be
extended.

## Forge frontend dependency policy

Original Fabric Voxy depends on Sodium and integrates with Iris. The Forge port
must target the Forge equivalents:

```text
Sodium frontend -> Embeddium hard client prerequisite
Iris shaderpack integration -> Oculus hard client prerequisite
```

The root reference source folders currently present in the workspace are:

```text
embeddium-20.1-forge/
Oculus-1.20.1-new/
```

They are reference sources for parity inspection, not repository artifacts to
commit. Embeddium keeps many Sodium package/API names internally, and Oculus
declares `provides = ["iris"]`, so source-level package names may still contain
`sodium` or `iris` while the Forge metadata and runtime prerequisites are
`embeddium` and `oculus`.

Detailed frontend mapping is tracked in:

```text
docs/forge-1.20.1-embeddium-oculus-frontend-mapping.md
```

The Forge source set now includes a small `ForgeFrontendCompat` status layer
that uses Forge `ModList` to report Embeddium/Oculus presence and versions. It
does not register renderer hooks or shaderpack hooks yet.

## Current verdict

```text
VOXY_PARITY_INCOMPLETE
FORMAL_RENDERER_READY=false
ACTUAL_RENDERER_DRAW_ENABLED=false
```

The project has visible historical proof paths, but those are deprecated. The
renderer is not parity-complete until the original Voxy chain is ported.

## Authoritative original chain

```text
VoxyRenderSystem
 -> WorldEngine / WorldSection / Mapper
 -> ModelBakerySubsystem
 -> ModelFactory
 -> SoftwareModelTextureBakery / SoftwareRasterizer / ReuseVertexConsumer
 -> TextureUtils / ModelQueries
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
 -> quads3.vert / quads.frag / block_model.glsl / quad_util.glsl
```

## Confirmed drift

| Area | Drift | Required correction |
| --- | --- | --- |
| Model texture bake | first-quad/sprite extraction and safe-set rejection replaced original software bake semantics | Port/adapt `SoftwareModelTextureBakery`, `SoftwareRasterizer`, `TextureUtils` |
| Model lifecycle | safe-set upload does not match original on-demand model request/requeue | Port `ModelBakerySubsystem` and `ModelFactory` lifecycle |
| Geometry generation | temporary rewrites / CPU mesh conversion are not original `RenderDataFactory` | Complete `RenderDataFactory` parity from `WorldSection` raw data |
| Geometry ownership | Forge debug/simple heap paths are not original `BasicAsyncGeometryManager` | Port allocation, 128-record alignment, section id reuse, upload/free lifecycle |
| Visibility | radius/frustum/preview snapshots are not original traversal | Port `RenderDistanceTracker` and `HierarchicalOcclusionTraverser` |
| Command generation | validation compute shaders are not production `cmdgen.comp` | Wire original `cmdgen.comp` inputs/outputs |
| Draw owner | visible preview owner is not `MDICSectionRenderer` | Port `MDICViewport` and `MDICSectionRenderer` ownership |
| Shader semantics | adapter/subset shader is not full original terrain shader contract | Port original shader inputs and semantics |

## Corrections already started

These are directionally correct but not complete readiness:

- Forge-adapted `ForgeSoftwareModelTextureBakery` exists.
- Forge-local `ForgeModelQueries` exists.
- formal direct section geometry is moving toward `RenderDataFactory`-style
  `WorldSection` raw-data generation.
- sample/preview/synthetic routes are explicitly deprecated.
- `ForgeOriginalVoxyModelPipeline` now owns the first L0/L1 parity boundary:
  it starts from the active `WorldEngine`, queues existing mapper biomes,
  attaches the mapper biome callback, and queues block bake requests for the
  future `RenderGenerationService` path.
- `ForgeOriginalVoxyModelFactory` now ports the original `ModelFactory`
  mapping core into the Forge source set:

```text
idMappings[1<<20]
metadataCache[1<<16]
fluidStateLUT[1<<16]
modelTexture2id dedupe
fluid pre-bake ordering
stair base-state normalization
SoftwareModelTextureBakery colour/depth bake
original ModelStore modelData/modelColour/atlas upload
```

The Forge route now also ports the first `ModelBakerySubsystem` lifecycle
mechanics:

```text
"Model factory processor" worker thread
LockSupport.unpark on block/biome requests
worker-side processAllThings()
render-thread upload-result queue consumption
3x2 model texture mip-chain generation/upload
biome colour LUT result uploads and model record colourTint rewrites
Forge-local TextureUtils helper port
```

This is still not full renderer readiness. The persistent-mapped
`UploadStream` staging path, `RenderGenerationService` model-miss
request/requeue integration, and `RenderDataFactory` raw-section mesh path now
exist in the Forge parity source set. `BuiltSection` output is now consumed by
a Forge-port `BasicAsyncGeometryManager` owner with original 8-byte geometry
records, 128-record heap allocation, 32-byte metadata packing, and
upload/remove/update event sets. The higher renderer owners after the async
geometry overlay are still incomplete.

The Forge route now also ports the first original render-generation layer:

```text
ForgeOriginalVoxyRenderGenerationService
 -> original-style BuildTask priority/retry state
 -> held-section policy capped at 1000 sections
 -> ForgeOriginalVoxyRenderDataFactory
 -> ForgeOriginalVoxyBuiltSection
 -> missing model IdNotYetComputedException
 -> ModelFactory requestBlockBake
 -> task requeue
```

`ForgeOriginalVoxyRenderDataFactory` is a Forge-local port of original
`RenderDataFactory`, including its raw `WorldSection` scan, opaque/non-opaque
bucket emission, fluid lookup path, neighbor face acquisition, greedy
`ScanMesher2D`, `OccupancySet`, and `BuiltSection` output layout. Java 17 lacks
the original source's `Integer.expand` / `Long.expand` helpers, so equivalent
Forge-local bit-expansion helpers are used to preserve the same bit semantics.

`ForgeOriginalVoxyBasicAsyncGeometryManager` is now a Forge-local port of
original `BasicAsyncGeometryManager` for the active parity route. It preserves:

```text
HierarchicalBitSet section id allocation
AllocationArena geometry heap allocation
8-byte geometry records
128-record allocation alignment
32-byte section metadata layout
heap upload / heap removal / metadata update sets
writeMetadataSplit high/low metadata writes
```

`ForgeOriginalVoxyBasicSectionGeometryData` is now a Forge-local port of
original `BasicSectionGeometryData` for the active parity route. It preserves:

```text
32-byte section metadata buffer allocation
geometry buffer capacity policy from RenderResourceReuse
NVIDIA Windows sparse allocation workaround
ARB sparse buffer commitment growth in ensureAccessable
render-thread-only build/free ownership
driver memory-release wait during free
```

The current bridge records generated sections into the async geometry owner and
drains the pending sync events so the model/render-generation pipeline does not
retain or leak `MemoryBuffer` uploads while `AsyncNodeManager` / `NodeManager`
are not ported yet. The render-thread `BasicSectionGeometryData` owner now
exists and supplies the geometry capacity to the async owner, but the original
node-layer sync into that owner is still missing. This is not draw readiness:
`originalNodeManagerParityReady=false` remains reported.

The model bake data path has been corrected away from the K-era `FaceTexture`
formal-preview shape and back toward the original Voxy model texture contract:

```text
ForgeSoftwareModelTextureBakery
 -> ForgeOriginalVoxyColourDepthTextureData[6]
 -> ForgeOriginalVoxyTextureUtils
 -> ForgeOriginalVoxyMipGen
 -> ForgeOriginalVoxyModelFactory ModelEntry / metadata / model record
 -> ForgeOriginalVoxyModelStore
```

`ForgeOriginalVoxyColourDepthTextureData` is a direct Forge-package port of the
original `ColourDepthTextureData` record, including hash, equality, and clone
semantics. `ForgeOriginalVoxyTextureUtils` and `ForgeOriginalVoxyMipGen` now
operate on that record instead of the historical `FaceTexture` substitute.
`ForgeOriginalVoxyModelFactory` now dedupes, computes metadata, builds
`faceData`, computes tint, and generates the mip-chain from
`ForgeOriginalVoxyColourDepthTextureData[]`.

`ForgeOriginalVoxyTextureUtils.mipColours()` now uses a Forge-local port of
Embeddium/Sodium's fast-srgb8 `ColorSRGB` table implementation, matching the
original Voxy `TextureUtils` RGB conversion path instead of the previous
formula approximation. Forge 1.20.1 does not expose the original source's
`net.minecraft.util.ARGB` helper class, so the alpha conversion is routed
through the same table helper and documented as a version-mapping adaptation.
`textureUtilsByteForByteAuditReady=true` now means this ColorSRGB-backed
`TextureUtils`/mip sample audit passes; it does not imply that the whole
software bakery or ModelStore owner is parity-complete.

The software bake raster layer has also been corrected away from the Forge
object-list substitute. The active Forge bakery now uses:

```text
ForgeOriginalVoxyReuseVertexConsumer
 -> MemoryBuffer-backed 24-byte vertex records
 -> ForgeOriginalVoxySoftwareRasterizer
 -> whole block-atlas UV sampling
 -> original depth/stencil/tint/blend framebuffer packing
 -> original-shaped scratch output buffer
 -> ForgeOriginalVoxyColourDepthTextureData[6] extraction in ModelFactory
```

This ports the original `ReuseVertexConsumer` / `SoftwareRasterizer` structure
and removes the previous per-quad `TextureAtlasSprite.getPixelRGBA()` sampling
path from the active bake. The active Forge `ModelFactory` now owns the bake
scratch buffer like original `ModelFactory.bakeScratchBuffer`; it calls the
original-shaped `renderToOutput(..., outputBuffer)` contract and extracts
`ColourDepthTextureData[6]` from the packed `long` framebuffer output before
dedupe, metadata, mip-chain, and upload.

The historical K/I `BakeResult.faces()` / `FaceTexture` substitute has been
removed from the bakery surface. Historical preview code that still compiles
against the bakery now consumes `ForgeOriginalVoxyColourDepthTextureData`
directly. New parity work must not reintroduce a separate face texture DTO.

`SoftwareModelTextureBakery` is now source-aligned for the Forge 1.20.1 /
Embeddium frontend: the atlas capture uses the original-style DSA
`glGetTextureImage` path, block baking routes Forge render layers through the
same material categories Embeddium uses for chunk meshing, and
`ForgeOriginalVoxyReuseVertexConsumer` no longer decodes raw vanilla vertex
arrays or guesses metadata. Instead, it reads Embeddium's injected
`BakedQuadView` for position, UV, shade, tint, and sprite ownership, then maps
the result onto the original Voxy 24-byte software-raster vertex record.

The original source's `BakedQuad.materialInfo().layer()` is mapped to
Embeddium's `DefaultMaterials.forRenderLayer(...)` source contract:

```text
solid -> no discard
cutout / cutoutMipped / tripwire -> discard
translucent -> translucent consumer + discard
```

The original `MipmapStrategy.DARK_CUTOUT` signal is not present as a public
Forge 1.20.1 field. The Forge route now follows Embeddium's lower texture
pipeline instead: `SpriteContentsMixin` records sprite transparency and keeps
the source texture dark for mipmapped leaf sprites while rewriting other
transparent pixels. The bakery bridge uses that same Embeddium source condition
and sprite transparency signal for the original Voxy dark-cutout bit. This is a
documented version/API mapping, not a free-form texture-name fallback.

The original model upload owner has now been split away from the historical
I/K-era formal store. `ForgeOriginalVoxyModelPipeline` creates a
`ForgeOriginalVoxyModelStore` on the render thread and passes it into
`ForgeOriginalVoxyModelFactory`, instead of passing
`ForgeVoxyInstance.getFormalModelStore()`. The Forge port now mirrors original
`ModelStore` ownership:

```text
modelBuffer: 64 * 65536 bytes
modelColourBuffer: 4 * 65536 bytes
model texture atlas: 16 * 3 * 256 by 16 * 2 * 256 RGBA8
block sampler: nearest / nearest-mipmap-linear with terrain mip bound
```

The upload result shape has also been moved back to original Voxy semantics:

```text
ModelBakeResultUpload.model MemoryBuffer
ModelBakeResultUpload.texture MemoryBuffer
BiomeUploadResult biome colour MemoryBuffer
BiomeUploadResult model/id pair MemoryBuffer
UploadStream persistent mapped staging for buffers
nglTextureSubImage2D for atlas mip-chain upload
```

The Forge owner now also has an original-route upload audit after
`UploadStream.commit()`: the last committed model records are read back from
the original `modelBuffer`, optional `modelColourBuffer` ranges are read back
when the upload wrote biome colours, and the 3x2 mip-chain atlas region is read
back level by level. Status exposes:

```text
originalModelStoreReadbackAuditReady
modelDataReadbackOk
modelColourReadbackOk
atlasMipChainReadbackOk
modelStoreReadbackAuditRuns
modelStoreReadbackAuditFailures
lastAuditedModelId
lastModelStoreReadbackAuditFailureReason
```

The original Iris/Oculus shaderpack material id hook is also connected. Original
Voxy calls `ModelFactory.setCustomBlockStateMapping(WorldRenderingSettings.INSTANCE.getBlockStateIds())`
from the Iris render pipeline. The Forge port reads the same Oculus singleton
through a narrow reflection bridge and writes the original custom id word in the
64-byte model record. A null map remains valid and writes zero, matching the
original behavior when no shaderpack block-state ids are active.

The old `ForgeFormalModelStore.uploadOriginalVoxy*` methods remain only for
historical preview/prototype command compatibility. They are no longer the
owner used by the original Voxy model pipeline.

The old `BakeResult.faces()` and `FaceTexture` view has been removed from the
bakery API. Historical preview/debug classes are deprecated; where they still
compile, they must consume the original-shaped `ColourDepthTextureData` output
instead of forcing the formal path to preserve K-era DTOs.

## Source-set reality

The authoritative original Voxy files under `me.cortex.voxy.client.core.*` are
reference sources in this repository, but the current Forge Gradle source set
only compiles the Forge package, selected platform/config/common files, and
world/storage utilities.

That means the Forge route must port/adapt original mechanisms under
`me.cortex.voxy.forge` instead of directly importing the original client-core
classes. Direct import of `ModelBakerySubsystem` was tested and rejected by
`compileJava` because the package is outside the active source set.

This is not a license to substitute behavior. The Forge implementation must
still match original ownership, data layout, lifecycle, and performance
semantics.

## Remaining bottom-up parity work

1. Keep removing historical `ForgeFormalModelStore` references from preview
   code; the original model pipeline now uses `ForgeOriginalVoxyModelStore`.
2. Port `AsyncNodeManager` / `NodeManager` ownership instead of the current
   direct generated-section bridge.
3. Connect async geometry event sets to the render-thread
   `BasicSectionGeometryData` owner through the original node-layer sync.
4. Port `RenderDistanceTracker` and `HierarchicalOcclusionTraverser`.
5. Port `MDICViewport` and production `cmdgen.comp`.
6. Port `MDICSectionRenderer` and original terrain shader binding order.
7. Port `VoxyRenderSystem` lifecycle only after the lower owners match.

## Current documented Forge deviations

| Area | Reason | Status |
| --- | --- | --- |
| `StairBlock.baseState` access | original source accesses the field directly; Forge 1.20.1 exposes it as private at compile time | Forge port uses a cached reflective field read to preserve original normalization semantics |
| `UploadStream` persistent staging | original upload path depends on `GlPersistentMappedBuffer`, `GlFence`, `GlBuffer`, and `AllocationArena` from the original client-core GL stack | Forge now ports this as `ForgeOriginalVoxyUploadStream`: persistent mapped staging buffer, `AllocationArena`, frame fences, explicit flush/copy/commit. The singleton is lazy-created on the render thread to respect Forge GL-context timing. |
| `TextureUtils` ColorSRGB path | original Voxy imports Sodium `ColorSRGB`; Forge runtime prerequisite is Embeddium, whose reference source keeps the same fast-srgb8 table under a moved package | Forge now ports that fast-srgb8 table locally and `textureUtilsByteForByteAuditReady=true` is reported when the table/mip sample audit passes. The 1.20.1 `ARGB` class name is unavailable, so alpha uses the same table helper as a documented mapping adaptation. |
| `RenderGenerationService` request/requeue | original request/requeue depends on `RenderDataFactory.generateMesh()` throwing `IdNotYetComputedException` from real section generation | Forge now ports BuildTask priority, held-section retention, inner/outer missing-model scans, `requestBlockBake`, and requeue. Direct Fabric `ServiceManager` import is blocked by Fabric `commonImpl` dependencies, so a Forge-local worker carries the same task semantics; `originalServiceManagerParityReady=false` remains reported until the common thread stack is cleanly Forge-adapted. |
| `RenderDataFactory` Java version helpers | original source uses `Integer.expand` / `Long.expand`, unavailable in Java 17 | Forge uses local equivalent bit-expansion helpers with the same mask/value semantics. |
| `BasicAsyncGeometryManager` result consumption before `NodeManager` parity | original Voxy routes render-generation results through `AsyncNodeManager` / `NodeManager`, which owns request state and mesh id replacement | Forge now ports the lower `BasicAsyncGeometryManager` allocation/metadata/event semantics and consumes generated sections into it, but reports `originalNodeManagerParityReady=false` until the original async node layer is ported. |
| `SoftwareModelTextureBakery` model collection and dark-cutout metadata | Forge 1.20.1 lacks the newer original `BlockStateModelPart` and public `BakedQuad.materialInfo()` API, but Embeddium injects the equivalent `BakedQuadView` and sprite transparency data used by its own chunk mesher | fixed for the active Forge/Embeddium route: `originalSoftwareModelTextureBakeryUsed=true`; the adaptation is constrained to Embeddium source-equivalent material and transparency signals |
| `ModelStore` ownership and audit | fixed: the original model pipeline now owns `ForgeOriginalVoxyModelStore` instead of historical `ForgeFormalModelStore`; uploads use original-style `MemoryBuffer` results, persistent `UploadStream`, DSA texture mip uploads, and post-commit readback audit for modelData/modelColour/atlas mip-chain regions | `originalModelStoreUsed=true` is reported when the owner is built; `originalModelStoreReadbackAuditReady=true` is reported after a committed upload readback matches the CPU payload |
| Iris/Oculus custom block-state ids | original Voxy receives `WorldRenderingSettings.INSTANCE.getBlockStateIds()` from the Iris pipeline; Forge cannot compile against Oculus source directly in this source set | Forge reads the same Oculus singleton through `ForgeOculusWorldRenderingSettingsBridge`; null maps write custom id zero, matching original behavior |

## Do not do

Do not use:

```text
sample-set data as formal model source
synthetic fixtures as success conditions
temporary model-id rewrites as geometry path
visible preview owner as renderer owner
debug command buffers as formal command buffers
manual refresh commands as lifecycle substitute
adapter shader as production terrain shader
```

## Current next work

Continue with bottom-up parity:

```text
port AsyncNodeManager / NodeManager ownership around BasicAsyncGeometryManager
 -> sync event sets into BasicSectionGeometryData
 -> replace remaining direct/debug geometry ownership with original Voxy geometry owners
```
