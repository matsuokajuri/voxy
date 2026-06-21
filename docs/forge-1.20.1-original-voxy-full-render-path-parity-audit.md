# Forge 1.20.1 original Voxy full render-path parity audit

This is the current route-control ledger. It supersedes old H/I/J/K/L-stage and
preview audit language. New implementation work uses Roman-major rounds with
dotted Arabic steps inside the same round, for example
`V_ORIGINAL_MDIC_COMMAND_GENERATION_CHAIN` containing
`V.1_ORIGINAL_MDIC_VIEWPORT_CMDGEN_INPUT_PARITY`,
`V.2_ORIGINAL_CMDGEN_COMP_OUTPUT_PARITY`, and
`V.3_ORIGINAL_CMDGEN_READBACK_AND_BARRIER_AUDIT`.

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
that uses Forge `ModList` to report Embeddium/Oculus presence and versions. The
active renderer entry is still the Embeddium cutout hook adapter, and Roman X
now adds the Oculus shaderpack patch/data/binding bridge for the original MDIC
pipeline. The 2026-06-21 post-repair runtime pass now confirms Oculus can load
`ComplementaryUnbound_r5.8.1.zip`, expose Voxy shaderpack sidecar sources to
the Forge bridge, enter a world, start the formal original-shaped owner, consume
Oculus shaderpack pipeline data, and submit original MDIC draws. This is still
not full outer `VoxyRenderSystem` ownership, and shaderpack visual parity is not
ready because the patched opaque/translucent terrain programs currently fall
back to the normal shader path.

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
| Visibility | radius/frustum/preview snapshots are not original traversal | `RenderDistanceTracker` is now Forge-ported; next correction is the original `HierarchicalOcclusionTraverser` with its `Viewport` / HiZ / render-list dependencies |
| Command generation | validation compute shaders are not production `cmdgen.comp` | Fixed for the active Roman route: production `prep.comp`, cull raster, `cmdgen.comp`, prefix sum, and `buildtranslucents.comp` now run from `ForgeOriginalVoxyMdicSectionRenderer` against real `MDICViewport` and `BasicSectionGeometryData` resources. |
| Draw owner | visible preview owner is not `MDICSectionRenderer` | Fixed for the active Roman VIII route: the Embeddium cutout hook now delegates to `ForgeOriginalVoxyModelPipeline.renderEmbeddiumCutout(...)`, which runs the original-shaped `AbstractRenderPipeline.runPipeline(...)` order through `ForgeOriginalVoxyMdicSectionRenderer.renderOpaque(...)`, HOC inner work, production `buildDrawCalls(...)`, `renderTemporal(...)`, `postOpaquePreTranslucent(...)`, `renderTranslucent(...)`, and `finish(...)`. This is still an Embeddium hook adapter, not full `VoxyRenderSystem` lifecycle parity. |
| Shader semantics | adapter/subset shader is not full original terrain shader contract | Partially fixed in Roman VII/VIII/IX/X: `ForgeOriginalVoxyRenderPipeline` now provides the original `AbstractRenderPipeline`-shaped shader hook owner, and `ForgeOriginalVoxyMdicSectionRenderer` builds original `quads3.vert` / `quads.frag` terrain and translucent programs through the original TAA hook, shader patch hook, directional face tint injection, and patched-or-normal fallback shape. The original `NormalRenderPipeline` target owner shape is present for non-shaderpack rendering. Roman X ports the original Iris/Oculus `voxy.json` patch parser, uniform struct layout, sampler/image binding declarations, SSBO binding declarations, shaderpack draw target attachment, TAA hook, blend setup, texture barrier, depth-hack fix, and depth blit path. The 2026-06-21 post-repair `runClient` pass validates real Oculus shaderpack load/world entry, formal owner startup, Oculus shaderpack pipeline data consumption, and original MDIC draw submission. Remaining gaps: full outer `VoxyRenderSystem` ownership and patched shaderpack terrain compile parity; `opaquePatchedShaderUsed=false` and `translucentPatchedShaderUsed=false` because ComplementaryUnbound currently references missing Forge/Oculus patch uniforms and samplers, so renderer readiness remains false. |

## Corrections already started

These are directionally correct but not complete readiness:

- Forge-adapted `ForgeSoftwareModelTextureBakery` exists.
- Forge-local `ForgeModelQueries` exists.
- formal direct section geometry is moving toward `RenderDataFactory`-style
  `WorldSection` raw-data generation.
- Forge-local original-shaped `NodeManager`, `NodeStore`,
  `SectionUpdateRouter`, `GeometryCache`, `NodeCleaner`, and
  `RenderDistanceTracker` are now wired into the parity pipeline.
- sample/preview/synthetic routes are explicitly deprecated.
- `ForgeOriginalVoxyModelPipeline` now owns the first Roman-route model
  pipeline parity boundary:
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

The service execution owner now also follows the original Voxy threading
boundary instead of a Forge-local executor substitute:

```text
ForgeOriginalVoxyModelPipeline
 -> instance-lifetime UnifiedServiceThreadPool
 -> original ServiceManager / Service execution
 -> original serviceThreads config target
 -> Embeddium builder thread subtraction
 -> Embeddium ChunkJobQueue semaphore block sharing
```

Original Voxy performs the builder-thread sharing in
`MixinChunkJobQueue` by replacing Sodium's `ChunkJobQueue` semaphore with
`SemaphoreBlockImpersonator(instance.getThreadPool().groupSemaphore.createBlock())`.
The Forge port mirrors that mechanism against Embeddium's equivalent
`me.jellysquid.mods.sodium.client.render.chunk.compile.executor.ChunkJobQueue`.
This is a frontend package mapping from Sodium to Embeddium, not a new
scheduler.

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

`ForgeOriginalVoxyAsyncNodeGeometrySync` now ports the original
`AsyncNodeManager` geometry result sync shape for the active parity route and
routes generated sections through a Forge-package port of the original
`NodeManager` / `NodeStore` / `SectionUpdateRouter` ownership path:

```text
top-level node request
 -> original-shaped NodeManager active section ownership
 -> SectionUpdateRouter watch / forwardEvent / triggerRemesh
 -> SingleNodeRequest / NodeChildRequest
 -> leaf-to-inner node transition
 -> inner-node child compaction and child pointer remap
 -> RenderGenerationService initial mesh task
 -> RenderGenerationService result consumer
 -> child existence update queue
 -> request batch / remove batch entry points
 -> async geometry result queue
 -> BasicAsyncGeometryManager event sets
 -> NodeStore node update writes
 -> top-level node id callback deltas
 -> cleaner reset/clear operation deltas
 -> SyncResults / ComputeMemoryCopy
 -> UploadStream staging
 -> util/memcpy.comp geometry copy
 -> util/scatter.comp nodeBuffer + metadataBuffer writes
 -> BasicSectionGeometryData
```

This removes the previous direct generated-section bridge that immediately
drained pending event sets and removes the temporary section-id side map that
bypassed `NodeManager` ownership. The scatter path now uses separate node-data
and section-metadata outputs like original Voxy instead of aliasing both
scatter outputs to the metadata buffer.

The remaining missing part is no longer the `NodeManager` ownership state
machine itself, and the render-side cleaner owner is now present. The Forge
route ports original `NodeCleaner` ownership and wires cleaner reset/free
deltas through:

```text
NodeManager cleaner operations
 -> NodeCleaner batch visibility-set compute
 -> visibility buffer
 -> sort_visibility.comp parity shader
 -> result_transformer.comp parity shader
 -> DownloadStream readback
 -> AsyncNodeManager remove batch
 -> NodeManager.removeNodeGeometry()
```

The Forge route now includes a package-local port of original `GeometryCache`,
original `RingTracker`, and original `RenderDistanceTracker`:

```text
Minecraft camera position
 -> RenderDistanceTracker.setCenterAndProcess()
 -> RingTracker top-level section add/remove ring
 -> AsyncNodeManager.addTopLevel/removeTopLevel
 -> NodeManager.insertTopLevelNode/removeTopLevelNode
```

`GeometryCache` is also wired like original Voxy:

```text
SectionUpdateRouter initial render callback
 -> geometryCache.remove(pos)
 -> cached geometry goes directly to submitGeometryResult()
 -> missing cached geometry queues RenderGenerationService.enqueueTask(pos)

world dirty callback
 -> geometryCache.clear(section.key)
 -> SectionUpdateRouter.forwardEvent(...)
 -> neighbor triggerRemesh(...)
```

`originalNodeManagerParityReady=true` is limited to the Forge-local
`NodeManager` / `NodeStore` / `SectionUpdateRouter` / `NodeCleaner` /
`GeometryCache` / `RenderDistanceTracker` ownership layers and does not imply
formal renderer readiness.

Forge mapping differences are limited to source-set and platform names: the
original `RingTracker` lives under excluded `client/core` source, so it is
copied into the Forge package with the same algorithm; Forge official mappings
use `getMinBuildHeight()` / `getMaxBuildHeight()` instead of the original
`getMinSectionY()` / `getMaxSectionY()` calls; the original
`VoxyConfig.CONFIG.sectionRenderDistance` / `subDivisionSize` values are now
represented by Forge config entries `originalVoxySectionRenderDistance` and
`originalVoxySubDivisionSize`. This preserves the original owner semantics
inside the Forge config system instead of hard-coding the default values.

Follow-up review found and corrected two local divergences in this layer:

```text
workCounter negative handling
 -> reverted from a Forge-local clamp-to-zero back to original wait/log behavior

pipeline cleanup order
 -> detach world/mapper callbacks
 -> stop AsyncNodeManager
 -> shutdown render generation / model factory
 -> free NodeCleaner / geometry / store
```

The node producer route is now narrowed to runtime validation of HiZ-backed
execution of the original `HierarchicalOcclusionTraverser`. It is not a CPU
candidate snapshot and must not be replaced by the historical K-stage
`ForgeFormalVisibilityOwner`. Forge now has a Forge-package port of the
original HOC owner, shader import loader, top-level node mapping, request queue
buffer, node buffer ownership, and request-batch download path. Original Voxy's
HOC consumes and owns:

```text
MDICViewport / Viewport
 -> HiZBuffer depth pyramid
 -> indirectLookup render-list buffer
 -> NodeCleaner visibility buffer
 -> AsyncNodeManager node buffer
 -> traversal_dev.comp with queue/node/screenspace/frustum imports
 -> request buffer download
 -> AsyncNodeManager.submitRequestBatch()
```

The current Forge parity route now owns the original `ViewportSelector` shape:
default viewport, optional Vivecraft render-pass viewport, optional
Oculus/Iris shadow viewport, and all `MDICViewport` resources owned by the
selected viewport. The Forge adapters use reflection for the optional
Vivecraft/Oculus sources so the Forge source set does not gain hard compile
dependencies that the original Fabric source did not need in this environment.

The current Forge parity route now owns the original MDIC viewport-side buffers
(`drawCountCallBuffer`, `drawCallBuffer`, `positionScratchBuffer`,
`indirectLookupBuffer`, and `visibilityBuffer`) and includes the original
shader resources in the Forge build path. The status deliberately separates:

```text
originalViewportSelectorReady
originalViewportSelectorDefaultReady
originalViewportSelectorExtraViewportCount
originalViewportSelectorLastSelectedKey
originalHierarchicalOcclusionTraverserOwnerReady
originalMdicViewportOwnerReady
originalHizOwnerReady
originalHizTraversalExecutableReady
```

`originalHizOwnerReady=true` now means the original-shaped `HiZBuffer` and
`DepthFramebuffer` owners exist. `originalHizTraversalExecutableReady=true`
requires a real render frame to build the HiZ mip-chain from the real Minecraft
main framebuffer depth attachment. `originalHierarchicalOcclusionTraverserReady`
requires both the original HOC owner and that selected-viewport HiZ executable
state. A CPU radius/frustum list or debug planner would be a route deviation
and is not accepted as HOC parity.

The Forge 1.20.1 projection adapter uses `GameRenderer.getProjectionMatrix(fov)`
as the available raw Minecraft projection source, then applies the same original
Voxy `extraProjection * adjustedRawProjection` near/far/reverse-Z transform.
Original newer Voxy reads `gameRenderState.levelRenderState.cameraRenderState`
directly; that field is not available in Forge 1.20.1, so this is a documented
version adapter rather than a substitute traversal route.

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

Follow-up parity review corrected three low-level model metadata divergences:

```text
modelId=0
 -> now enters through addEntry(0), matching original ModelFactory startup
 -> ForgeOriginalVoxyModelStore now accepts model id 0 for upload/readback
 -> no hand-reserved Forge-only model-zero shortcut

air / invisible / empty software bake
 -> now returns a successful all-zero output like original SoftwareModelTextureBakery
 -> no longer treats air, invisible, no-render-type, no-quad, or empty-fluid output as a bake failure

block emission metadata
 -> now checks BlockState.emissiveRendering(...) before vanilla light emission
 -> matches original emissive flag semantics instead of getLightEmission-only

face depth metadata
 -> now uses the software-bakery float depth value for original thresholds
 -> occludes depth < 0.1, canBeOccluded depth < 0.3, selfLighting depth > 0.01
 -> no longer reconstructs thresholds from encoded faceData depth
```

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

The Forge upload queue now also mirrors the original `ModelFactory.processUploads()`
pixel unpack boundary before draining model upload results:

```text
GL_UNPACK_ROW_LENGTH=0
GL_UNPACK_SKIP_PIXELS=0
GL_UNPACK_SKIP_ROWS=0
GL_UNPACK_ALIGNMENT=4
```

This matters because Minecraft, Embeddium, and Oculus may leave global pixel
store state configured for their own atlas work. The original Voxy upload path
clears that state before `nglTextureSubImage2D`; the Forge port now does the
same instead of depending on whatever state the previous renderer left behind.

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

Because the readback audit is Forge validation scaffolding rather than an
original renderer path, it saves and restores GL pack state around
`glGetTextureSubImage` and uses a tight pack layout. This keeps the audit from
being affected by unrelated renderer pixel-store state and prevents the audit
from polluting later rendering work.

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
classes. Direct imports of original client-core classes such as
`ModelBakerySubsystem` and `NodeManager` were tested and rejected by
`compileJava` because those packages are outside the active source set and pull
Fabric-side/frontend dependencies when compiled directly.

This is not a license to substitute behavior. The Forge implementation must
still match original ownership, data layout, lifecycle, and performance
semantics.

## Remaining bottom-up parity work

1. Keep removing historical `ForgeFormalModelStore` references from preview
   code; the original model pipeline now uses `ForgeOriginalVoxyModelStore`.
2. Continue runtime validation of original `Viewport` / `MDICViewport` / HiZ /
   render-list ownership, production `cmdgen.comp`, and MDIC draw submission
   after real render frames.
3. Runtime-validate the Roman X Oculus shaderpack patch bridge against real
   shaderpacks that ship `voxy.json`, including uniform/sampler/SSBO bindings,
   draw target attachments, TAA, blend state, and depth transfer.
4. Port full `VoxyRenderSystem` lifecycle around the completed lower owners.
5. Validate movement/update performance under the original-shaped owner and
   continue deleting isolated historical preview/prototype command paths.

## Current documented Forge deviations

| Area | Reason | Status |
| --- | --- | --- |
| `StairBlock.baseState` access | original source accesses the field directly; Forge 1.20.1 exposes it as private at compile time | Forge port uses a cached reflective field read to preserve original normalization semantics |
| `UploadStream` persistent staging | original upload path depends on `GlPersistentMappedBuffer`, `GlFence`, `GlBuffer`, and `AllocationArena` from the original client-core GL stack | Forge now ports this as `ForgeOriginalVoxyUploadStream`: persistent mapped staging buffer, `AllocationArena`, frame fences, explicit flush/copy/commit. The singleton is lazy-created on the render thread to respect Forge GL-context timing. |
| `TextureUtils` ColorSRGB path | original Voxy imports Sodium `ColorSRGB`; Forge runtime prerequisite is Embeddium, whose reference source keeps the same fast-srgb8 table under a moved package | Forge now ports that fast-srgb8 table locally and `textureUtilsByteForByteAuditReady=true` is reported when the table/mip sample audit passes. The 1.20.1 `ARGB` class name is unavailable, so alpha uses the same table helper as a documented mapping adaptation. |
| `RenderGenerationService` request/requeue | original request/requeue depends on `RenderDataFactory.generateMesh()` throwing `IdNotYetComputedException` from real section generation and on `ServiceManager.createService(...)` for worker execution | Fixed for the Forge parity route: `ForgeOriginalVoxyRenderGenerationService` now registers with original `ServiceManager` / `Service` / `UnifiedServiceThreadPool`, creates per-thread `RenderDataFactory` and missed-model sets through the service context supplier, uses `service.execute()` for enqueue/requeue, and drains/shuts down service permits in the original order. `originalServiceManagerParityReady=true` is now reported for this service stack. |
| Service thread count / builder-thread sharing | original `VoxyClientInstance.updateDedicatedThreads()` subtracts Sodium chunk-builder threads, and original `MixinChunkJobQueue` lets Sodium builder workers share Voxy service jobs through `SemaphoreBlockImpersonator` | Fixed for the Forge/Embeddium route: `originalVoxyServiceThreads` mirrors the original `serviceThreads` target default, `originalVoxyUseEmbeddiumBuilderThreads` mirrors the original "Use sodium threads" option, `ForgeOriginalVoxyServiceThreadPolicy` subtracts Embeddium `ChunkBuilder.getTotalThreadCount()`, and `ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin` replaces Embeddium `ChunkJobQueue`'s semaphore with the original `SemaphoreBlockImpersonator` / `groupSemaphore.createBlock()` mechanism. |
| Service thread frontend access | original Voxy compiles against Sodium and uses a Sodium accessor mixin for `SodiumWorldRenderer.renderSectionManager`; Forge must target Embeddium without compiling its internals into this source set | Forge queries Embeddium's equivalent `SodiumWorldRenderer.instanceNullable()`, private `renderSectionManager`, `RenderSectionManager.getBuilder()`, and `ChunkBuilder.getTotalThreadCount()` reflectively. This preserves the original ownership/data source while avoiding a hard compile-time dependency on Embeddium internals inside the Forge source set. |
| `CpuLayout` default thread count | original Voxy's `CpuLayout` uses platform affinity helpers from the original LWJGL/JNA stack; the Forge 1.20.1 classpath lacks the same `org.lwjgl.system.windows.Kernel32` API | Forge uses OSHI physical core count and the same fallback-to-available-processors behavior to preserve the original `serviceThreads = max(coreCount / 1.5, 1)` default. This is a platform adapter, not a scheduler fallback. |
| `common.Logger` client HUD branch | original common logger checks Fabric `VoxyCommon.IS_IN_MINECRAFT` / `IS_DEDICATED_SERVER`; pulling Fabric `commonImpl` into the Forge source set would reintroduce platform code that cannot compile here | Forge keeps the original logger API for the imported thread stack and adapts only the platform guard to `Minecraft.getInstance() != null` before posting client HUD messages. |
| `RenderDataFactory` Java version helpers | original source uses `Integer.expand` / `Long.expand`, unavailable in Java 17 | Forge uses local equivalent bit-expansion helpers with the same mask/value semantics. |
| `AsyncNodeManager` render-side traversal producers | original Voxy receives top-level node adds/removes from `RenderDistanceTracker`, and request batches from `HierarchicalOcclusionTraverser` | Forge now ports the geometry-result queue, `SyncResults`, `ComputeMemoryCopy`, `UploadStream`, `DownloadStream`, `memcpy.comp`, `scatter.comp`, `SectionUpdateRouter`, `SingleNodeRequest`, `NodeChildRequest`, leaf-to-inner transitions, inner-node compaction, top-level node id deltas, cleaner reset/clear deltas, request batch entry points, remove batch entry points, render-side `NodeCleaner`, `GeometryCache`, `RenderDistanceTracker`, and the HOC owner/request-buffer path. `originalNodeManagerParityReady=true` is limited to this ownership layer. `originalHierarchicalOcclusionTraverserOwnerReady=true` does not imply HiZ traversal execution until `originalHizTraversalExecutableReady=true`. |
| `AsyncNodeManager` GeometryCache | original Voxy has a CPU-side `GeometryCache` inside `AsyncNodeManager`; initial render generation first tries `geometryCache.remove(pos)`, and dirty world events clear cached geometry for the changed section | Fixed for the Forge parity route: `ForgeOriginalVoxyGeometryCache` mirrors original cache semantics, initial render callbacks consume cached geometry before queueing render generation, and world dirty callbacks clear stale cached geometry before forwarding router/remesh events. |
| `RenderDistanceTracker` | original Voxy uses `RingTracker` to feed top-level LoD node add/remove events into `AsyncNodeManager` | Fixed for the Forge parity route: `ForgeOriginalVoxyRingTracker` and `ForgeOriginalVoxyRenderDistanceTracker` mirror the original algorithm and feed `AsyncNodeManager.addTopLevel/removeTopLevel`; render distance is now sourced from `originalVoxySectionRenderDistance`, the Forge config equivalent of original `VoxyConfig.CONFIG.sectionRenderDistance`. |
| `HierarchicalOcclusionTraverser` / `ViewportSelector` / `MDICViewport` / HiZ | original Voxy selects a per-pass viewport, copies vanilla depth into a Voxy-owned `DepthFramebuffer(GL_DEPTH24_STENCIL8)` through `setup_stencil_depth.frag`, builds a HiZ depth pyramid, then runs GPU HOC traversal to produce render-list entries and node request batches | Fixed for the Forge owner route: `ForgeOriginalVoxyHierarchicalOcclusionTraverser` ports the original request buffer, node buffer ownership, top-node GPU list, queue metadata, scratch queues, shader import loading, `traversal_dev.comp` compile path, render-list binding contract, mip-nearest HiZ sampler, and request download into `AsyncNodeManager.submitRequestBatch()`. `ForgeOriginalVoxyViewportSelector` now mirrors default / Vivecraft-pass / Oculus-shadow viewport selection. `ForgeOriginalVoxyMdicViewport` owns the original-shaped MDIC buffers plus `ForgeOriginalVoxyHiZBuffer`; `ForgeOriginalVoxyPipelineDepthStage` mirrors original `AbstractRenderPipeline.initDepthStencil(...)` with `ForgeOriginalVoxyDepthFramebuffer` and `setup_stencil_depth.frag` before building the HiZ texture. Runtime audit now shows a non-empty HOC render-list (`renderListCounter=146`) and request batches. Remaining adapter: until `VoxyRenderSystem` owns the render pipeline, the Forge Embeddium hook must restore external GL state after the original depth/stencil setup because there is no immediate `MDICSectionRenderer.renderOpaque(...)` call to consume that stencil state. Historical CPU candidate snapshots/debug planners remain explicitly unacceptable as parity. |
| `MDICViewport` -> `MDICSectionRenderer.buildDrawCalls(...)` | original `MDICSectionRenderer.buildDrawCalls(...)` uploads the MDIC scene uniform, runs `prep.comp`, rasterizes section AABBs into the viewport visibility buffer with color/depth writes disabled, dispatches production `cmdgen.comp`, runs prefix sum for translucent distance buckets, and dispatches `buildtranslucents.comp` | Fixed for the active Roman V/VI/VIII owner route: `ForgeOriginalVoxyMdicSectionRenderer` replaces the command-generator-only owner and now owns original-shaped uniform, distance-count, shared-index, terrain/translucent terrain programs, prep, cull-raster, cmdgen, prefix-sum, translucent-build, and MDIC draw submission resources. It binds real `ForgeOriginalVoxyMdicViewport` buffers and real `ForgeOriginalVoxyBasicSectionGeometryData` metadata/geometry buffers, not K-era synthetic validators or debug command buffers. Runtime readback after the depth/stencil parity fix verifies a non-empty render-list (`renderListSectionCount=146`), production `cmdgen.comp` output (`opaqueDrawCount=545`), original draw-count layout, cull indirect command layout, 20-byte `DrawCommand`, position scratch output, and barrier path. It deliberately does not call `VoxyRenderSystem`; remaining adapter work is outer lifecycle/reload/performance parity, not a preview route. |
| `AbstractRenderPipeline` -> terrain shader owner | original `MDICSectionRenderer` receives a render pipeline, asks it for TAA and shader patches, compiles patched-or-normal opaque and translucent terrain programs, and then relies on the pipeline to bind opaque/translucent draw targets | Partially fixed in Roman VII/VIII/IX/X: `ForgeOriginalVoxyRenderPipeline` owns the original-shaped shader hook boundary and reuses `ForgeOriginalVoxyPipelineDepthStage` for depth/stencil setup. `ForgeOriginalVoxyNormalPipelineTargets` mirrors the original `NormalRenderPipeline` colour target ownership for non-shaderpack rendering and can now switch to external Oculus shaderpack draw targets without owning or deleting them. `ForgeOriginalVoxySSAO` mirrors original SSAO shader compilation, sampler setup, AUTO capability selection, matrix uniforms, image/texture bindings, and dispatch for the normal path. The shaderpack path now mirrors original `IrisVoxyRenderPipeline`: pre-setup custom uniform upload, UBO binding point 7, SSBO base binding 10, sampler base binding 6, opaque/translucent framebuffer binding, shaderpack blend setup, optional depth-hack transform blit, `glTextureBarrier`, translucent depth/stencil blit, and vanilla-depth blit when allowed. The 2026-06-21 post-repair real-pack pass reaches Oculus shaderpack load/world entry, starts the formal owner, consumes shaderpack pipeline data, and submits original MDIC draws. Remaining gap: full outer `VoxyRenderSystem` lifecycle and patched shaderpack terrain compile parity; the patched programs fall back to normal terrain shaders until the missing uniform/sampler namespace is ported. |
| Embeddium render hook entry | original Voxy drives this chain from one `VoxyRenderSystem` owner; Forge must hook Embeddium until that owner is fully ported | The active mixin config uses one `DefaultChunkRenderer` cutout-pass hook. A stale, unregistered `SodiumWorldRenderer.drawChunkLayer` hook source was removed so it cannot be accidentally enabled as a second route. `ForgeOriginalVoxyModelPipeline.renderEmbeddiumCutout(...)` now also guards reentrant entry and only runs post-command-generation dynamic work after command generation actually completes. |
| `SoftwareModelTextureBakery` model collection and dark-cutout metadata | Forge 1.20.1 lacks the newer original `BlockStateModelPart` and public `BakedQuad.materialInfo()` API, but Embeddium injects the equivalent `BakedQuadView` and sprite transparency data used by its own chunk mesher | fixed for the active Forge/Embeddium route: `originalSoftwareModelTextureBakeryUsed=true`; the adaptation is constrained to Embeddium source-equivalent material and transparency signals |
| `ModelStore` ownership and audit | fixed: the original model pipeline now owns `ForgeOriginalVoxyModelStore` instead of historical `ForgeFormalModelStore`; uploads use original-style `MemoryBuffer` results, persistent `UploadStream`, DSA texture mip uploads, block-atlas-derived sampler max LOD, and post-commit readback audit for modelData/modelColour/atlas mip-chain regions | `originalModelStoreUsed=true` is reported when the owner is built; `originalModelStoreReadbackAuditReady=true` is reported after a committed upload readback matches the CPU payload |
| Iris/Oculus custom block-state ids | original Voxy receives `WorldRenderingSettings.INSTANCE.getBlockStateIds()` from the Iris pipeline; Forge cannot compile against Oculus source directly in this source set | Forge reads the same Oculus singleton through `ForgeOculusWorldRenderingSettingsBridge`; null maps write custom id zero, matching original behavior |
| Iris/Oculus shaderpack pipeline data | original Voxy receives `IrisShaderPatch` from `ProgramSet`, stores `IrisVoxyRenderPipelineData` on `IrisRenderingPipeline`, and uses the data to patch/bind MDIC terrain rendering | Fixed for the active Forge/Oculus route in Roman X: `ForgeOriginalVoxyOculusProgramSetMixin`, `ForgeOriginalVoxyOculusIrisRenderingPipelineMixin`, and `ForgeOriginalVoxyOculusRenderPipelineData` mirror the original parser/data owner and bind path. Oculus 1.20.1 passes `GlSampler` directly instead of a sampler supplier, so the Forge bridge adapts only that signature. Forge/Oculus constructor timing requires both bridge mixins to read/store patch data from constructor `TAIL` hooks; an attempted original `INVOKE`-point constructor hook is rejected by Forge/Mixin for this Oculus 1.20.1 target and is documented as a platform hook-shape blocker, not a data-flow shortcut. Oculus 1.20.1 `sourceProvider` only exposes IncludeGraph-discovered starts, so `ForgeOriginalVoxyOculusShaderPackSourceNamesMixin` now adds `voxy.json`, `voxy_opaque.glsl`, `voxy_translucent.glsl`, and `voxy_taa.glsl` to the shaderpack source-start set. The original parser's `JSON_DUMP.txt` side effect is intentionally not ported; parse failures are logged/thrown without creating local artifacts. Runtime validation with `ComplementaryUnbound_r5.8.1.zip` now reports `originalOculusShaderpackPipelineDataReady=true` and `originalOculusShaderpackSource=oculus-shaderpack-voxy-patch`. Remaining blocker: patched opaque/translucent terrain programs are requested but fall back because the current bridge still lacks the full patch uniform/sampler namespace expected by the shaderpack. |
| Oculus `WorldRenderingSettings` reload | original Oculus `PipelineManager.preparePipeline(...)` observes `WorldRenderingSettings.INSTANCE.isReloadRequired()`, calls `levelRenderer.allChanged()`, then clears the flag | Forge Voxy now observes the same reload flag and also receives a mixin callback when Oculus clears it. The Voxy owner responds through the existing `markStaleAndClear(...)` path and requests restart when it was already started or pending start. It does not call `clearReloadRequired()` itself, preserving Oculus ownership of that flag. |

### VI/VII audit corrections

- `MDICSectionRenderer` statistics path: original Voxy conditionally compiles `cmdgen.comp` with `HAS_STATISTICS`, owns a 1024-byte statistics buffer, binds it at binding 8, and downloads visible-section / quad-count data through `DownloadStream`. Forge now mirrors that shape with `ForgeOriginalVoxyRenderStatistics`, `statisticsBuffer`, and `ForgeOriginalVoxyDownloadStream`. The statistics owner defaults disabled, matching original debug-only behavior.
- VI build failure cleanup: if shader/program construction fails during `ForgeOriginalVoxyMdicSectionRenderer.buildOnRenderThread(...)`, already-created GL programs are now freed before recording failure.
- VI/VII GL state cleanup: the Forge hook adapter now restores cull-raster color mask, depth mask, representative-fragment state, and final-blit blend state on exceptional exits. This is limited to adapter-owned state protection while `VoxyRenderSystem` is not yet the outer lifecycle owner.

### VIII implementation audit

- Original source baseline: `VoxyRenderSystem.renderOpaque(...)` delegates the visible renderer to `AbstractRenderPipeline.runPipeline(...)`, which performs `setup`, `renderOpaque`, `innerPrimaryWork`, `buildDrawCalls`, `renderTemporal`, `postOpaquePreperation`, `postOpaquePreTranslucent`, `renderTranslucent`, and `finish` in one frame owner.
- Forge state after VIII: `ForgeOriginalVoxyModelPipeline.renderEmbeddiumCutout(...)` selects/updates the `ForgeOriginalVoxyMdicViewport`, calls `ForgeOriginalVoxyRenderPipeline.preSetup(...)` and `setup(...)`, submits `MDICSectionRenderer.renderOpaque(...)`, builds HiZ from the Voxy-owned depth texture, runs original inner HOC work, dispatches production `buildDrawCalls(...)`, submits temporal and translucent terrain draws, calls `postOpaquePreTranslucent(...)`, calls `finish(...)`, then runs upload/render-distance dynamic work.
- The Embeddium cutout mixin remains only the Forge platform entry point; the implementation route does not use historical K-era preview renderer code.
- VIII status is reported through `ForgeOriginalVoxyVisibleRendererStats` so command output can distinguish owner readiness, original run-pipeline order, MDIC draw submission, pipeline finish, state restore, and preview-route absence.
- Roman IX fixes the environmental fog blocker from this list. Roman X ports
  the Oculus/Iris shaderpack patch bridge. Remaining non-visual parity
  blockers should stay explicit: full `VoxyRenderSystem` lifecycle ownership,
  runtime shaderpack validation, and movement/update performance parity.

### IX implementation audit

- Original source baseline: original `Viewport` owns `FogParameters`; original `NormalRenderPipeline.finish(...)` compiles the final blit with `USE_ENV_FOG` when environmental fog is enabled, uploads fog start/end/color uniforms, and skips `transformBlitDepth(...)` when fog covers the entire Voxy render distance. Original `VoxyRenderSystem.shutdown()` flushes `DownloadStream` around render-resource teardown and detaches callbacks before releasing owners.
- Forge state after IX.1: `ForgeOriginalVoxyFogParameters` captures the Forge 1.20.1 render fog state through `RenderSystem.getShaderFogStart()`, `getShaderFogEnd()`, and `getShaderFogColor()` because the Embeddium 1.20.1 hook does not pass a newer original `FogParameters` object. `ForgeOriginalVoxyMdicViewport` now stores that data before `update()`, and `ForgeOriginalVoxyRenderPipeline.finish(...)` uses the original final-blit fog uniforms and fog-cover skip rule.
- Forge state after IX.2: `ForgeOriginalVoxyDownloadStream.flushWaitClear()` mirrors the original explicit wait/tick/clear drain. `ForgeOriginalVoxyModelPipeline.markStaleAndClear(...)` clears captured render state, detaches world/mapper callbacks, stops the worker, and flushes the download stream before and after render-resource teardown when the stream exists.
- Forge state after IX.3: status output now exposes viewport fog use, final-blit environmental fog enablement/uniform use, last fog start/end, lifecycle download flush use, and render-state capture clear use so later runtime audits can distinguish original parity evidence from stale preview evidence.
- At the IX boundary, readiness did not flip:
  `formalRendererReady=false`, `actualRendererDrawEnabled=false`,
  `formalDrawPipelineReady=false`, and `earlyUsableLodRendererReady=false`
  remained required because the complete original outer owner and shaderpack
  integration were not both ported yet. Roman X updates the shaderpack bridge
  status below; the outer owner blocker remains.

### X implementation audit

- Original source baseline: original `MixinProgramSet` builds `IrisShaderPatch`
  from shaderpack `voxy.json`; original `MixinIrisRenderingPipeline` stores
  the patch and builds `IrisVoxyRenderPipelineData` before setup computes; and
  original `IrisVoxyRenderPipeline` uses that data for draw targets, uniform
  UBO layout, SSBOs, samplers, TAA source, blend setup, depth-hack fix,
  `glTextureBarrier`, translucent depth/stencil transfer, and vanilla-depth
  blit.
- Forge state after X.1: `ForgeOriginalVoxyOculusShaderPatch` ports the
  original `voxy.json` parser and auxiliary `voxy_opaque.glsl`,
  `voxy_translucent.glsl`, and `voxy_taa.glsl` overrides. `ForgeOriginalVoxyOculusRenderPipelineData`
  ports the original uniform struct layout/updater, sampler declaration and
  binding base, SSBO declaration and binding base, draw target lookup from
  Oculus `RenderTargets`, and shaderpack blend setup. `ForgeOriginalVoxyRenderPipeline`
  consumes that data when active instead of using the normal SSAO/final-blit
  path.
- Forge state after X.2: Oculus `WorldRenderingSettings` reload is connected to
  the existing Voxy owner clear/restart path by polling `isReloadRequired()` and
  by observing Oculus `clearReloadRequired()`. The Forge Voxy owner does not
  clear the Oculus flag itself.
- Forge state after X.3: status output now exposes
  `originalOculusShaderpackPipelineActive`,
  `originalOculusShaderpackPipelineDataReady`,
  `originalOculusShaderpackPatchShaderUsed`,
  `originalOculusShaderpackBindingsUsed`,
  `originalOculusShaderpackDrawTargetsUsed`,
  `originalOculusShaderpackUniformsUsed`,
  `originalOculusShaderpackSsboBindingsReady`,
  `originalOculusShaderpackImageBindingsReady`,
  `originalOculusShaderpackBlendStateReady`,
  `originalOculusShaderpackTaaReady`,
  `originalOculusShaderpackSource`, and
  `originalOculusShaderpackFailureReason`.
- Forge runtime validation after X.3 and the follow-up repair pass: the
  2026-06-21 `runClient` pass with `ComplementaryUnbound_r5.8.1.zip` first
  exposed two Forge/Oculus constructor injection timing blockers.
  `ForgeOriginalVoxyOculusProgramSetMixin` and
  `ForgeOriginalVoxyOculusIrisRenderingPipelineMixin` now use constructor
  `TAIL` hooks, and `ForgeVoxyConfig.isEnabledEarlySafe()` avoids reading the
  Forge client config before Oculus has loaded it. A later repair pass also
  added Oculus shaderpack source-start discovery for the Voxy sidecar files.
- The post-repair runtime status now reports formal owner startup and shaderpack
  data consumption:
  `originalVisibleRendererOwnerReady=true`,
  `originalVoxyRunPipelineOrderUsed=true`,
  `originalVisibleMdicDrawSubmissionUsed=true`,
  `originalMdicOpaqueDrawSubmitted=true`,
  `originalMdicTemporalDrawSubmitted=true`,
  `originalMdicTranslucentDrawSubmitted=true`,
  `originalOculusShaderpackPipelineActive=true`,
  `originalOculusShaderpackPipelineDataReady=true`, and
  `originalOculusShaderpackSource=oculus-shaderpack-voxy-patch`.
- The same status proves the remaining shaderpack compile blocker:
  `opaquePatchedShaderRequested=true`,
  `opaquePatchedShaderUsed=false`,
  `opaquePatchedShaderFallbackUsed=true`,
  `translucentPatchedShaderRequested=true`,
  `translucentPatchedShaderUsed=false`, and
  `translucentPatchedShaderFallbackUsed=true`. ComplementaryUnbound currently
  references missing Forge/Oculus patch uniforms and samplers such as
  `vxModelView`, `vxProj`, previous/inverse matrix uniforms, Nether biome
  uniforms, `colortex18`, `colortex19`, `miplevel`, and `vxDepthTexOpaque`.
- Visual validation note: after the formal owner starts, far LoD terrain under
  shaderpack must still be treated as degraded until the patched shader status
  flips to used for both opaque and translucent programs.
- X does not flip readiness. `formalRendererReady=false`,
  `actualRendererDrawEnabled=false`, `formalDrawPipelineReady=false`, and
  `earlyUsableLodRendererReady=false` remain required until full outer
  `VoxyRenderSystem` ownership, patched shaderpack terrain compile parity, and
  movement/update performance parity are complete.

### 2026-06-21 deep runtime drift audit

A focused post-X drift audit is tracked in:

```text
docs/forge-1.20.1-deep-runtime-drift-audit-2026-06-21.md
```

The audit preserves the pre-repair finding that the earlier visible LoD output
came from deprecated `ForgeSimpleGpuMeshRenderer` drawing while the formal
original-shaped pipeline still reported `original-render-pipeline-not-started`.
Follow-up code repair has now addressed the code-level blockers from that audit
and the post-repair run proves the formal route now starts:

```text
first-start lifecycle auto-request for ForgeOriginalVoxyModelPipeline, including the Oculus reload-required first-start interlock
simple GPU visible route suppression while formal original start is requested, queued, or active
separate mixin-ingest vs tick-scanner ingest status
Oculus translucent GL_DEPTH24_STENCIL8 depth texture and final translucent-depth blit
external Oculus target readiness requires real color target texture ids
shaderpack TAA injection in HOC traversal and MDIC cull raster
RenderDistanceTracker original +1 outer ring
Frex-gated primary-work catch-up loop
model tint-index preservation from Forge BakedQuad data where available
shader patch requested/used/fallback status bits
expanded Embeddium hook GL state capture/restore, including driver-reported texture/sampler and SSBO binding ranges
visible-frame skipped count no longer double-counts early hook returns
runClient freeze fix: RenderGenerationService no longer requests missing model bakes while holding taskMapLock
model bake request parity: render-generation workers now use a lightweight requestBlockBakeInternal path instead of building command/status snapshots
ModelFactory hot-loop fix: processing uses queue emptiness checks instead of repeated ConcurrentLinkedDeque.size() polling
Oculus shaderpack sidecar discovery fix: voxy.json, voxy_opaque.glsl, voxy_translucent.glsl, and voxy_taa.glsl are added to Oculus ShaderPackSourceNames
```

The post-repair `runClient` pass entered the world, stayed responsive, printed
status, closed normally, and ended with `BUILD SUCCESSFUL`. It reported:

```text
originalVisibleRendererOwnerReady=true
originalVisibleMdicDrawSubmissionUsed=true
originalOculusShaderpackPipelineDataReady=true
originalOculusShaderpackSource=oculus-shaderpack-voxy-patch
originalVisibleRendererUsesPreviewRoute=false
opaquePatchedShaderRequested=true
opaquePatchedShaderUsed=false
translucentPatchedShaderRequested=true
translucentPatchedShaderUsed=false
```

The remaining blockers are now:

```text
full outer VoxyRenderSystem lifecycle ownership
model-bakery setup inside the complete original constructor boundary
full hook-adapter GL state coverage for image bindings, indirect buffers, polygon mode, and driver extension state
patched shaderpack terrain compile parity: ComplementaryUnbound currently references uniforms/samplers that the Forge/Oculus bridge has not yet declared or bound
```

Do not treat white/bright far-LoD artifacts as final Voxy shaderpack evidence
until both `opaquePatchedShaderUsed=true` and
`translucentPatchedShaderUsed=true`. The simple GPU route is now quarantined
while the formal owner is requested, queued, or active, but the shaderpack patch
fallback still makes visual output degraded.

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
compare original Iris shader patch uniform/sampler namespace against Forge/Oculus 1.20.1
 -> make patched opaque/translucent terrain programs compile without fallback
 -> validate movement/update performance under the original-shaped owner and real shaderpack patch
 -> continue replacing the Embeddium hook adapter with full VoxyRenderSystem lifecycle ownership
```
