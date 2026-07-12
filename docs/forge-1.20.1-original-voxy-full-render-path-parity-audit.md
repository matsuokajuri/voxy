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

1. Continue classifying/removing legacy CPU/BuiltSection/cache compatibility
   surfaces that are not part of the original Voxy owner chain. The isolated
   placeholder model-store family has been removed; the original model pipeline
   uses `ForgeOriginalVoxyModelStore`.
2. Continue runtime validation of original `Viewport` / `MDICViewport` / HiZ /
   render-list ownership, production `cmdgen.comp`, and MDIC draw submission
   after real render frames.
3. Runtime-validate the Roman X Oculus shaderpack patch bridge against real
   shaderpacks that ship `voxy.json`, including uniform/sampler/SSBO bindings,
   draw target attachments, TAA, blend state, and depth transfer.
4. Port full `VoxyRenderSystem` lifecycle around the completed lower owners.
   Done in Roman XX (2026-07-03): `ForgeOriginalVoxyRenderSystem` is the single
   outer owner; see the XX section at the end of this ledger.
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

### 2026-06-22 runtime repair addendum

The follow-up run after the post-X drift audit exposed one original lifecycle
owner mismatch and one ingest data-flow mismatch:

```text
ForgeVoxyInstance.createActiveWorldSkeleton() used a synchronous save callback
that saved the section and returned false instead of transferring ownership to
the original SectionSavingService queue.

ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin rewrote single sections by
passing raw DataLayer values directly into rawIngest, bypassing the chunk-aware
light-layer handling added for chunk ingest.
```

Both are fixed in the active Roman X route:

```text
SectionSavingService is compiled from the original source and wired as the
WorldEngine save callback through the Forge model-pipeline ServiceManager.
Forge WorldEngine lifetime now acquires a reference when the formal model
pipeline attaches and releases it during render-thread cleanup.
Forge active-world close now detaches the current WorldEngine and frees it only
after `WorldEngine.isWorldIdle()`, so render-thread cleanup and save-queue
section refs cannot race `WorldEngine.free()`.
SectionStorage write accounting now wraps the real storage delegate instead of
being coupled to the old synchronous callback.
VoxelIngestService.ingestChunkSectionWithStats(...) now mirrors the chunk ingest
path, including live-engine validation, section-index validation, DataLayer
copying, missing-light accounting, and shared light-layer handling.
Embeddium section-update ingest now calls the same chunk-aware section path
instead of writing raw section light directly.
```

A later visual check showed all LoD sections rendering as if their light level
was zero. A focused comparison traced the Forge divergence to
`VoxelIngestService.createLightingSupplier(...)`: the Forge port used
`Level.getBrightness(...)` when a chunk light `DataLayer` was absent. That is
not original Voxy data flow. An attempted Embeddium-clone-style default
sky-light substitution also made the visual output worse because Voxy stores
lighting into persistent LoD section data, not into an ephemeral chunk-render
clone. The active fix removes both substitutes: in sky-lit dimensions, non-air
sections with a missing sky `DataLayer` are now deferred and not inserted into
`WorldEngine` until real light data is available.

Validation after these fixes:

```text
compileJava: passed
runClient: entered world, exited with code 0, BUILD SUCCESSFUL
dirty/free failure: not reproduced
Oculus matrix audit: projection/model-view/camera diffs all zero
depth-source audit: external depth source populated after the first frame
auto ingest: storage writes now come from the original save service path
```

The high missing block/sky DataLayer counts seen during chunk ingest remain
important runtime evidence. They are now reported with
`deferredLightSections`, and chunks containing deferred sections are not marked
as fully ingested by `ForgeChunkIngestManager`, so later scans can retry them.
If black or white far-LoD artifacts still persist after this fix, the next
comparison point is no longer the old raw section-light path or the
`Level.getBrightness(...)` fallback; it is the remaining shaderpack
patched-terrain compile/binding blocker, full outer `VoxyRenderSystem`
lifecycle ownership, lightmap texture binding state, deprecated CPU/simple
route contamination, or a newly observed platform-specific state gap.

This addendum still does not flip readiness:

```text
formalRendererReady=false
actualRendererDrawEnabled=false
formalDrawPipelineReady=false
earlyUsableLodRendererReady=false
```

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

The active runtime bug is now tracked separately in:

```text
docs/forge-1.20.1-black-lod-bugfix-plan-2026-06-22.md
```

The latest black-LoD investigation changes the next-work focus from
"make shaderpack patches compile" to "verify the complete shaderpack terrain
contract." The most recent shaderpack audit evidence showed:

```text
opaquePatchedShaderRequested=true
opaquePatchedShaderUsed=true
opaquePatchedShaderFallbackUsed=false
translucentPatchedShaderRequested=true
translucentPatchedShaderUsed=true
translucentPatchedShaderFallbackUsed=false
custom block-state ids populated for common blocks
MDIC command generation can produce opaque draw commands
```

This means black LoD must not be treated as a Complementary-specific brightness
problem or as a compile-fallback problem without new evidence. The next pass
must compare original IrisVoxy against Forge/Oculus for the generic shaderpack
contract:

```text
voxy.json draw target ids
 -> main/alt target texture selection
 -> framebuffer attachment order
 -> glDrawBuffers order
 -> patched VoxyFragmentParameters
 -> lightmap uv / sampler binding
 -> custom block-state material id
 -> shaderpack G-buffer outputs
```

Continue with bottom-up parity:

```text
compare original Iris shaderpack draw-target and patch-output contract against Forge/Oculus 1.20.1
 -> fix the first proven draw-target / G-buffer / lightmap / material-id drift
 -> validate movement/update performance under the original-shaped owner and real shaderpack patch
 -> continue replacing the Embeddium hook adapter with full VoxyRenderSystem lifecycle ownership
```

## 2026-06-23 post-cleanup integrity audit

After the deprecated debug/preview/prototype cleanup, the active Forge route was
re-scanned against the original Voxy owner chain with CodeGraph.

Audited active chain:

```text
ForgeVoxyInstance
 -> ForgeOriginalVoxyModelPipeline
 -> ForgeOriginalVoxyModelFactory / ForgeOriginalVoxyModelStore
 -> ForgeOriginalVoxyRenderGenerationService
 -> ForgeOriginalVoxyRenderDataFactory
 -> ForgeOriginalVoxyBasicAsyncGeometryManager
 -> ForgeOriginalVoxyBasicSectionGeometryData
 -> ForgeOriginalVoxyRenderDistanceTracker
 -> ForgeOriginalVoxyHierarchicalOcclusionTraverser
 -> ForgeOriginalVoxyMdicViewport
 -> ForgeOriginalVoxyMdicSectionRenderer
 -> ForgeOriginalVoxyOculus* shaderpack bridge
```

Comparison result:

```text
The cleanup did not reintroduce a preview/simple/debug renderer into the active
path.

The active model, render-generation, geometry, MDIC, HOC/viewport, and Oculus
shaderpack bridge owners still map to the original Voxy owner chain.

The remaining `Sampler` filenames are Oculus/Iris shaderpack sampler API
integration, not the removed sample-set route.

The remaining readback/audit fields in the original MDIC/model-store path are
diagnostic parity checks for active original-shaped buffers, not deleted renderer
owners.

The 2026-06-23 repair removed the isolated placeholder model-store family
(`ForgeModelStoreSkeleton`, `ForgeModelDataBuffer`, placeholder record/status
types, and placeholder layout/audit helpers) and detached resource reload from
that deleted skeleton path. `ForgeVoxyModelIdMapper` remains only for legacy
CPU-geometry compatibility/status code and is explicitly not the formal
original model-id owner.

The 2026-06-23 lifecycle repair added a Forge `GameShuttingDownEvent` terminal
owner that can drain `SectionSavingService` and shut down the original
`UnifiedServiceThreadPool` when render cleanup completes synchronously on the
render thread. This reduces the service-leak gap but does not replace the
remaining full `VoxyRenderSystem` outer-owner requirement.
```

Validation:

```text
deleted prototype class/method scan: no hits
deleted config/runtime switch scan: no hits
Forge/config suspicious filename scan: only Oculus/Iris Samplers files
gradlew classes: passed
rtk test .\gradlew compileJava: passed after terminal-shutdown and placeholder
cleanup repairs
rtk test .\gradlew processResources: passed after dependency range narrowing
```

No readiness flags are changed by this audit:

```text
formalRendererReady=false
actualRendererDrawEnabled=false
formalDrawPipelineReady=false
earlyUsableLodRendererReady=false
```

## 2026-07-02 ingest-threading parity repair

Investigating the remaining fast-movement issues (vanilla/LOD boundary hitch
that scales with MC render distance) exposed an ingest ownership divergence
that earlier audits missed:

```text
Original Voxy VoxelIngestService is a ServiceManager-backed async service
("Ingest service", weight 5000). Callers (Sodium RenderSectionManager mixin,
chunk add/remove hooks, block-update hooks) only capture light DataLayer
copies on the game thread and enqueue IngestSection records; voxel conversion,
mipping, and WorldUpdater.insertUpdate run on ingest worker threads.

The Forge port had rewritten VoxelIngestService as fully synchronous statics:
every chunk add/remove event and every ForgeChunkIngestManager catch-up tick
ran convert + mip + insertUpdate on the client thread. Chunk load/unload event
volume scales with render distance, which made the main-thread ingest cost
scale with render distance during movement - matching the observed hitch.
```

The active fix restores the original ownership:

```text
VoxelIngestService again owns a ServiceManager service ("Ingest service",
weight 5000, same UnifiedServiceThreadPool as SectionSavingService) with a
ConcurrentLinkedDeque work queue.
rawIngestWithStats(...) is now the enqueue point: game-thread callers still do
live-engine checks, DataLayer copies, uniform-sky-light resolution, and
missing/deferred-light accounting (all light-engine access stays on the game
thread), then enqueue; the worker runs convert + mip + insertUpdate.
Queued sections whose WorldEngine died before execution are dropped by an
isLive() check on the worker.
ForgeVoxyInstance constructs the service beside SectionSavingService, routes
the static entry points to it via setActiveService, and shuts it down in the
same terminal path as the saving service. Without an active service the old
synchronous path remains as fallback.
ForgeChunkIngestManager per-tick budget now bounds capture cost only; its
summary log reports enqueuedSections and the ingest worker backlog instead of
worker-side voxel counts, and avgMs is now capture-only (avgCaptureMs).
```

Validation:

```text
compileJava: passed
runClient: pending user visual confirmation (boundary hitch during fast
movement at high render distance)
```

No readiness flags are changed by this repair.

## 2026-07-02 vanilla/LOD boundary mask contract repair (XIII)

The vanilla/LOD boundary flicker (full ring while moving) plus 2-3 persistent
stationary holes were diagnosed with two gated audits
(`-Dvoxy.forge.auditChunkBound`; enable in dev runs with
`.\gradlew runClient -PvoxyAuditChunkBound`):

```text
ForgeOriginalVoxyModelPipeline once-per-second handoff log: chunk-bound mask
adds/removes/clears applied, tracked mask size, MDIC opaque/temporal/
translucent draw counts, geometry queue depth.
ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin once-per-second
mask-coverage log: built-but-not-drawn sections classified into the
render-distance ring sliver vs in-circle frustum/occlusion culls.
```

Ground truth: every stationary hole sample sat at nearest-corner distance
96.0-97.3 blocks against a 96-block draw radius, stable for tens of seconds.
That is exactly the band admitted by the mask cull's 1-block box expansion.

Root cause (present in original Voxy as well; the shader is byte-identical to
the dev-branch baseline): the chunk-bound mask culls its AABBs with a cylinder
over the section box EXPANDED by 1 block (`outline.vsh` `icorner-1..icorner+17`),
while Embeddium's `OcclusionCuller.isWithinRenderDistance` draws sections using
the UNEXPANDED box. Sections inside the mask cylinder but outside the draw
cylinder are masked-but-never-drawn: holes pinned to the render-distance
circle, churning as a flickering ring while the player moves.

Documented deviation from original (defect fix, keeping the mask contract
"mask = exactly where Embeddium draws"):

```text
outline.vsh shouldRender now uses the unexpanded section box, matching
Embeddium's distance cull exactly.
ForgeOriginalVoxyChunkBoundRenderer now derives the mask radius from
Embeddium's RenderSectionManager.getSearchDistance() semantics (fog-occlusion
opaque-fog clamp when no shaderpack is active) instead of always using the
full option render distance.
```

Validation:

```text
compileJava: passed
runClient with shaderpack: user confirmed stationary boundary holes gone and
the moving boundary ring flicker resolved
no outline shader compile/link failures; chunk-bound render active all session
```

No readiness flags are changed by this repair.

## 2026-07-02 patched shaderpack terrain compile parity: blocker retired

The 2026-06-22 blocker "patched shaderpack terrain compile parity:
ComplementaryUnbound currently references uniforms/samplers that the
Forge/Oculus bridge has not yet declared or bound" is stale. A
`-Dvoxy.forge.auditShaderpack` run (exposed via
`.\gradlew runClient -PvoxyAuditShaderpack`) on 2026-07-02 with
ComplementaryUnbound r5.8.1 active reports:

```text
opaquePatch requested=true used=true fallback=false
translucentPatch requested=true used=true fallback=false
MDIC readback audit: opaquePatchUsed=true translucentPatchUsed=true glError=0
```

The XI-era repairs (shaderpack sampler unbind, PATCHED_SHADER define applied to
both shader stages, Oculus sidecar source discovery) had already resolved the
compile fallback; the audit list was never re-validated afterwards. The
remaining readiness blockers are therefore:

```text
full outer VoxyRenderSystem lifecycle ownership (model-bakery setup inside the
complete original constructor boundary)
full hook-adapter GL state coverage for image bindings, indirect buffers,
polygon mode, and driver extension state
```

No readiness flags are changed by this evidence update.

## 2026-07-03 hook-adapter GL state coverage completed (XVIII)

The remaining "full hook-adapter GL state coverage" blocker is addressed.
OriginalVoxyRenderState now additionally captures and restores:

```text
depth func; blend src/dst RGB+alpha; stencil func/ref/value-mask/write-mask
and fail/zfail/zpass ops; polygon mode; provoking vertex (Voxy sets
GL_FIRST_VERTEX_CONVENTION and previously never restored the vanilla
GL_LAST_VERTEX_CONVENTION); front face; indexed GL_UNIFORM_BUFFER bindings
(first min(16, MAX) points; Voxy binds points 0..7); GL_DRAW_INDIRECT_BUFFER
and GL_PARAMETER_BUFFER_ARB bindings; NV_representative_fragment_test enable
state (guarded on driver capability).
```

Image bindings were listed in the original blocker text but the Voxy frame
path performs no glBindImageTexture calls (verified by source inventory), so
no coverage is required there.

The remaining readiness blocker is now only:

```text
full outer VoxyRenderSystem lifecycle ownership (model-bakery setup inside
the complete original constructor boundary)
```

No readiness flags are changed by this repair.

## 2026-07-03 full outer VoxyRenderSystem lifecycle ownership (XX)

The last readiness blocker from the 2026-07-02 list is addressed. The Forge
route now has a single outer lifecycle owner, `ForgeOriginalVoxyRenderSystem`,
ported from original `VoxyRenderSystem`; `ForgeOriginalVoxyModelPipeline`
remains only the platform adapter (start/stop event policy, Embeddium hook
entry, stats), matching the original `MixinLevelRenderer` +
`VoxyClientInstance` split.

- The constructor is the complete original construction boundary:
  `world.acquireRef()` FIRST, `System.gc()`, the vanilla render-distance < 3
  chat warning, SSBO binding capture, double `glFinish()`, then model bakery
  -> render generation -> geometry data/manager -> async node manager -> node
  cleaner -> traversal -> world dirty callback -> existing biome queue ->
  mapper biome callback -> node manager start -> render pipeline -> custom
  block-state mapping (the original `setupExtraModelBakeryData` position) ->
  traversal late-stage compile -> MDIC section renderer -> viewport selector
  -> render distance tracker -> chunk-bound renderer, a single
  `catch (RuntimeException)` -> `releaseRef` -> rethrow, then SSBO binding
  restore and texture-unit/sampler clears.
- Model-bakery setup sits inside that boundary:
  `ForgeOriginalVoxyModelBakerySubsystem` ports original
  `ModelBakerySubsystem` (store + factory + "Model factory processor" worker
  thread started in the constructor, seen-id request dedupe with the original
  out-of-range error, `requestBlockBake`, `addBiome`, `tick`, shutdown join
  order worker -> factory -> store).
- `ForgeOriginalVoxyRenderGenerationService` now receives the bakery
  subsystem and requests missing models through it, matching original
  `RenderGenerationService -> ModelBakerySubsystem` ownership instead of
  routing model-miss requests through the Forge pipeline adapter.
- `shutdown(boolean)` mirrors original `VoxyRenderSystem.shutdown()` order:
  download-stream flush, callback detach, node manager stop, model bakery
  shutdown, render generation shutdown, traversal/cleaner/geometry frees,
  chunk-bound free, viewport selector free, pipeline (with the section
  renderer it owns in original) freed last, second flush, world `releaseRef`.
- Reload mirroring: `markStaleAndClear` delegates to that shutdown on the
  render thread; a restart with a still-live owner shuts the previous owner
  down before constructing the new one (original
  `MixinLevelRenderer.allChanged` order); construction failure with an active
  shaderpack disables Oculus shaders through `IrisApi` (original
  `IrisUtil.disableIrisShaders()` recovery) so the Oculus-triggered reload
  retries on the normal path.

Documented Forge adaptations (not substitutes):

- Store/factory GL setup takes the Minecraft instance and reports error
  strings; the render system rethrows them inside the original exception
  boundary.
- Original leaks partially constructed GL owners when the constructor throws;
  the Forge port keeps the VI-round failure cleanup and frees them in
  shutdown order before releasing the world ref.
- The Forge AsyncNodeManager split (geometry manager + geometry sync)
  requires explicitly attaching the render-generation result consumer
  immediately after node-manager construction, before the world dirty
  callback can route the first build tasks to service workers.
- The global download-stream flush is skipped when a newer lifecycle
  generation already owns the stream.
- Model-bakery worker death records FAILED_SAFE and tears down on the next
  client tick instead of crashing the client (original rethrows out of
  `tick()`); without a shaderpack, construction failure records FAILED_SAFE
  instead of the original client crash.
- There is no Forge `RenderResourceReuse` geometry-buffer cache; the geometry
  buffer is owned per lifecycle.

Validation:

```text
gradlew compileJava: passed
runtime regression pending next play session: world entry, LOD render with
shaderpack on/off, dimension switch, resource reload (F3+T), Oculus
shaderpack toggle, logout/login, client quit
```

No readiness flags are changed by this port until the runtime regression
pass confirms the outer owner in-game.

### XX.1 shaderpack-toggle rebuild regression: silent model-upload loss

The first XX runtime regression pass surfaced per-face LOD holes and black
quads after in-game shaderpack toggles, worsening with each toggle. Ground
truth (baseline vs post-toggle `/voxy original_voxy_model_pipeline_status`,
HOC readback audits, and an in-game re-ingest probe) excluded mesh coverage
(post-rebuild completed meshes and live geometry matched baseline), world
data loss (re-ingest still meshed), and the node tree (remesh worked but the
holes persisted).

Root cause: `ForgeOriginalVoxyModelStore.glErrorOrNone()` validated uploads
with `glGetError()`, which reads the context-wide latched error flags. An
Oculus pipeline reload latches GL errors routinely, so model uploads staged
during the rebuild window read someone else's error, were reported as
failed, and `processUploadsOnRenderThread` freed and silently dropped them —
leaving those model ids zeroed on the GPU forever (baseline lost 2 uploads,
each toggle lost more: `nextModelId` vs `uploadedModelRecordCount` drift).
Every quad referencing a dropped model rendered invisible or black,
clustered by block type, unfixable by remesh.

Fix: drain latched GL errors before the model-store build, before the
upload-audit loop, and at the top of the render-system construction
boundary (all logged); upload failures now retry next tick (front of the
queue, capped at 16 attempts) and only then drop with a loud error log.
This is a Forge audit-layer repair — original Voxy performs these uploads
without glGetError audits at all, so no original behavior is displaced.

2026-07-12 correction: the GL-error-triggered loss above was real, but the
`nextModelId`/`uploadedModelRecordCount` drift used to quantify it was
confounded by a second, older Forge-only loss path. XX.6 below identifies the
independent budget-boundary queue bug; the earlier byte-for-byte readback
proved correctness only for uploaders that actually entered `upload()`, not
coverage of every allocated unique model id.

Also applied in XX.1: the world dirty/biome callbacks and node-manager start
moved to the end of the construction boundary (still inside it) because
Forge ingest services keep running through an owner rebuild, unlike the
original world-join timing.

### XX.2 dimension-switch regression: section-tracker lock leak freezes chunk building

The dimension-switch regression pass (overworld -> nether -> overworld via
/execute) hung the "Joining world" screen ~30s, left vanilla and LOD chunk
building dead (only a few chunks built), and a following F3+T froze the
client permanently in Embeddium `ChunkBuilder: Stopping worker threads`.

Root cause chain, from the force-killed session log:

1. During the return switch, an in-flight ingest job against the closing
   nether `WorldEngine` hit the original `WorldSection.trySetFreed()`
   invariant (`Section freed while marked as dirty`): a racing dirty
   mark/save-queue transition slipped between `ActiveSectionTracker.tryUnload`'s
   save guards and the final free (`markDirty` and the save-queue flags do
   not take the slice lock).
2. The invariant throw happened INSIDE the slice `StampedLock` write region
   of `tryUnload`, which had no try/finally — the write lock leaked forever.
3. Every thread touching that cache slice parked permanently: Voxy service
   jobs, and Embeddium builder threads running shared Voxy jobs inline in
   `MultiThreadPrioritySemaphore.Block.acquire` — chunk building died
   (Joining-world timeout, ~3 chunks), and `ChunkBuilder.stopWorkers` on the
   next F3+T joined wedged builder threads forever (the freeze). The service
   executor swallows job exceptions, so the log showed only the single
   ServiceManager error line.

Fix (defect repair of original common code, not a substitute path):
`tryUnload`'s slice-lock region and LRU-lock region are finally-backed so no
throw can leak them, and a locked re-check retries (into the save path)
when the section turned dirty between the guards and the free, instead of
tripping the trySetFreed invariant (which also poisoned the cache entry
because its CAS commits before the check throws).

No readiness flags are changed by this repair; the dimension-switch
regression item stays pending until the rerun passes.

### XX.3 shaderpack-enable regression: model-bakery worker spin deadlocks shutdown

Enabling BSL froze the client inside `ForgeOriginalVoxyRenderSystem.shutdown()`
(log ends after "Shutting down rendering"). BSL's enable produced a second
reload edge ~2s after the first rebuild, tearing down a system whose model
bakery still had pending render-thread uploads. The XX port mirrored the
original ModelBakerySubsystem worker loop verbatim
(`while (factory.processAllThings());`), but the Forge factory's
`processAllThings()` returns `hasInflightWork()`, which includes the upload
queue that ONLY the render thread drains: the worker spins without parking,
`modelService.shutdown()` joins it from the render thread, and the uploads
can never drain — deadlock. Every earlier rebuild had happened against
quiescent queues, which is why toggles and dimension switches passed.

Fix: the worker drain loop observes `isRunning` again (the pre-XX guard,
documented as a Forge semantic deviation from the original loop). No
readiness flags are changed.

Open regression item from the same pass: Photon (photon_v1.3b) renders no
LOD water (translucent MDIC output not visible under that pack); pack loads
its own external Voxy patches and the rebuild is clean in logs, so this is
a shaderpack-path visual parity item needing an in-game
`-PvoxyAuditShaderpack` investigation, not a lifecycle defect.

### XX.4 open investigation: shaderpack-switch LOD holes and missing water (NOT an XX regression)

Status: OPEN. The 2026-07-04 investigation (audit runs, RenderDoc captures,
and a version bisect) reframed this defect:

- It PREDATES XX: on pre-XX code (590bff49) BSL pack switches alternate
  broken/fine (odd switches broken, even fine; Complementary always fine);
  the XX-era code makes every switch broken. Pack switching was never
  regression-tested before XX, so the defect is a longstanding gap, not a
  lifecycle-port regression.
- Confirmed mechanism level: in broken lifecycles the MDIC command
  generation emits almost no opaque commands (RenderDoc: 20-26 vs 212-270
  healthy on the same pack) while the HOC render list stays full (~1900
  sections); the temporal pass partially compensates each frame; the stable
  losers are the visible holes; translucent commands collapse the same way
  (missing water). The failure is decided per lifecycle at rebuild time and
  persists (a remesh does not fix it).
- The failure pattern depends on the GEOMETRY BUFFER allocation mode:
  sparse-4GB (NVIDIA workaround) alternates per switch; plain-512MB
  (renderdocCompat) fails sporadically (once in ~5 switches) on pre-XX and
  always on current code. This implicates GL memory allocation circumstances
  (VRAM aliasing between the dying and new lifecycle's buffers is the prime
  suspect; the in-tree NVIDIA free-wait workaround only covers the geometry
  buffer, not metadata/node/HiZ objects).
- Exhaustively EXCLUDED with ground truth: model bakes/uploads/custom ids
  (per-model audits + GPU readback audits pass), geometry accounting
  (padding explained the apparent gap), render-list collapse, Oculus
  pipeline-data staleness (per-frame generation check never fired), FBO
  draw-target staleness/flip (attachment audit clean), stencil mask
  (uniform 0x01 both states), depth input source (real values), shadow-pass
  leakage (probe never fired), sparse commitment growth (1GB headroom probe
  no change), patched programs (force-normal probe no change), vanilla
  depth feedback (skip probe no change), frame pass ordering (skeletons
  identical healthy vs broken).
- Diagnostic infrastructure added during the investigation (kept in tree):
  Oculus pipeline generation check with owner restart, draw-target audit,
  shadow-leak probe, source-depth frame audit wiring, sparse commitment
  growth log, and gradle flags voxyForceNormalTerrainShaders /
  voxyAuditSourceDepth / voxySkipVanillaDepthFeedback / voxyRenderdocCompat.

- 2026-07-05 RenderDoc Pixel History finding (locked): hole pixels are
  NEVER touched by any of the frame's section draws — only the sky clear
  and vanilla sky geometry write them; the holes match the sky color
  because they ARE sky. Simultaneously the broken frame's MDIC draws MORE
  sections than the healthy frame (2888 vs 1489) with the user confirming
  per-subdraw scrubbing shows the holes simply never being painted. A
  command list that is both inflated (duplicates) and incomplete (losses)
  points at node→mesh pointer corruption established at rebuild time —
  consistent with every persistence property observed (remesh-proof,
  render-list inflation, hasChildren tree anomalies).

Current probe: `/voxy original_voxy_node_consistency_audit` — downloads the
GPU node buffer's active range and byte-compares every node against the CPU
tree's own serialization (`ForgeOriginalVoxyNodeStore.writeNode` is the
single source of truth for the 16-byte GPU layout), and independently
tallies mesh-pointer duplication inside the CPU tree. The audit defers to a
worker-quiescent render tick (≤240-tick timeout, quiescence reported).
Interpretation: gpuMismatch>0 → scatter/upload-layer corruption;
gpuMismatch=0 with duplicatedMeshIds>0 → node-manager logic race. RenderDoc
golden captures (same code, same pack, broken vs fine) remain available via
the pre-XX checkout recipe recorded in the project memory.

2026-07-11 current frontier (supersedes the earlier node-pointer and
allocation working inferences above):

- The failure still reproduces with XX.5 geometry-buffer reuse active. The
  final broken BSL rebuild reused sparse buffer 271 with its existing
  134348800-byte commitment. Reuse is required original parity, but the
  free/reallocate window is excluded as the root cause of the holes.
- Two worker-quiescent node audits in the same broken lifecycle were stable
  across 42 seconds: 6420 nodes, 4610 unique meshes, zero real GPU/CPU
  mismatches, zero duplicate/out-of-range mesh ids, zero metadata-position
  mismatches, and zero orphaned request flags. The render-generation and
  async-result queues were empty and their submitted/processed counters
  remained balanced.
- The HOC audit was also structurally clean: 645 render-list entries with no
  duplicate, stale, or out-of-range entry. It nevertheless reported 294
  visible `enqueueSelfEmpty` nodes and no newly queued requests. The later
  coordinate-targeted audit excludes those aggregate empty-node counts as the
  cause of the confirmed hole below.
- The recorded ground-hole coordinate `block=[209,63,23]` maps to
  `4@[0,0,0] -> 3@[0,0,0] -> 2@[1,0,0] -> 1@[3,0,0] -> 0@[6,1,0]`. The
  coordinate-targeted node audit found every level present, non-empty, and
  request-free; the L0 entry was a leaf with `geometry=238`. This excludes the
  lost-request/`hasRequested` theory for this visually confirmed hole.
- The coordinate-targeted HOC audit then found exactly one selected level for
  the same point: its L0 node (`mesh=274` in that rebuilt lifecycle) was
  `selectedThisFrame=true` and present at render-list index 117. The HOC and
  render-list stages are therefore excluded for this confirmed hole; the next
  boundary is the target mesh's generated draw commands and packed quads.
- The old audit mislabeled `NodeChildRequest.getMsk()` as `outstanding`; it is
  the required-child mask. Actual outstanding work is
  `required & ~results`. A zero-required-mask top-level request with zero
  child existence is an intentional original-Voxy sentinel, not a stuck
  completion. The audit now reports required/results/outstanding/existence
  masks separately and classifies these sentinels explicitly.
- `/voxy original_voxy_node_consistency_audit <blockX> <blockY> <blockZ>` now
  adds a short L4-to-L0 target-chain record after the normal quiescent audit.
  Each level queries `activeSectionMap` directly, so it can distinguish
  absent, leaf, inner, single-request, and child-request entries and report the
  exact target-child bit, watcher flags, mesh/children state, request id, and
  true outstanding mask. This is read-only instrumentation; request and render
  behavior are unchanged.

2026-07-12 target-model/quad frontier:

- The user isolated a new content correlation: in one broken BSL lifecycle the
  holes affected grass blocks while red concrete placed at the same locations
  rendered normally; other lifecycles can instead affect a leaf type or another
  block type. This reopens semantic model-id/model-record/atlas correlation,
  not the already-passed byte-for-byte upload-at-commit audit.
- BSL 10.1.3 maps `grass_block` and `red_concrete` to the same shaderpack block
  id (`block.25099`), and both BSL and Complementary's opaque Voxy sidecars use
  tint RGB rather than tint alpha for fragment rejection. A shaderpack
  `customId` or biome-tint-alpha difference alone therefore does not explain
  the observed grass-versus-concrete split.
- Opaque `quads.frag` only alpha-discards when the model face's discard bit is
  active (or its merged-quad override applies) and the exact L0 atlas alpha is
  at most 0.1. The decisive checks are consequently: whether a target UP-face
  quad exists, which packed `modelId` it carries, and whether that model's GPU
  face record and atlas tile agree with the factory mapping.
- `/voxy original_voxy_block_model_audit <blockX> <blockY> <blockZ>` is a
  read-only coordinate probe. It resolves the state/biome from Voxy's ingested
  L0 `WorldSection` (falling back to a read-only lookup of the `ClientLevel`
  state in the Mapper's existing-entry snapshot), then reports
  block-state/model ids, non-air-to-model-zero, dedupe summary,
  expected versus GPU `customId`, face discard/override bits, current biome
  colour entry, full atlas checksum, and per-face alpha histograms.
- The targeted MDIC audit now also reconstructs split metadata positions in
  their actual high-word/low-word order and reads only the matched section's
  bounded geometry range. It deep-scans at most the first matching render-list
  entry per LOD level and separately counts duplicates, bounding the diagnostic
  even when the list itself is corrupt. It reports all packed quads covering the
  target cell, including buffer, face, extent, `modelId`, biome id, and raw
  64-bit value.
  This distinguishes "no surface was generated" from "surface generated with
  the wrong model" and "correct surface later discarded" in one capture.
- The first target-HOC probe black-screened because the diagnostic itself put a
  full node-buffer snapshot on LWJGL `MemoryStack`, producing
  `OutOfMemoryError: Out of stack space` before the MDIC target result ran. It
  is not evidence about XX.4. Full snapshots now use bounded native-heap
  checked allocations with conditional `finally` frees; the target geometry
  probe reads at most one matched section per LOD level.

Validation for the diagnostic refinements: `gradlew compileJava` passed after
both the stack-allocation fix and the target model/quad additions. The broken
capture then produced the XX.6 ground truth below; no readiness flag changes.

### XX.5 RenderResourceReuse port: geometry buffer reused across owner rebuilds

The XX.4 investigation's allocation-mode-dependent failure pattern led back
to a dropped original component: original `RenderResourceReuse` caches the
geometry buffer across renderer recreations (`getOrCreateGeometryBuffer` /
`giveBackGeometryBuffer`) and only frees it at full instance shutdown
(`VoxyClientInstance.shutdown -> clearResources`). The Forge port had
documented "no geometry-buffer cache; owned per lifecycle" as a harmless
deviation — it was not: per-rebuild free-then-reallocate of the
driver-heavy geometry allocation (4GB sparse on the NVIDIA workaround
path) opens exactly the VRAM-aliasing window implicated by XX.4, a window
the original never opens by design. Reuse also keeps GL command ordering
on a single buffer object across the rebuild.

`ForgeOriginalVoxyRenderResourceReuse` now ports the geometry-buffer half
(the model-atlas half was already ported as the model store's static
texture cache): `ForgeOriginalVoxyBasicSectionGeometryData` borrows the
buffer on build (carrying over sparse page commitment so ensureAccessable
does not re-commit) and gives it back on free — no decommit, no delete, no
NVIDIA free-wait during rebuilds. `ForgeVoxyInstance.shutdown()` calls
`clearResources()` mirroring the original instance shutdown.

Validation result (2026-07-11): buffer reuse is active across repeated
Complementary/BSL owner rebuilds, but BSL ground holes still reproduced. XX.5
therefore closes the RenderResourceReuse parity deviation without closing
XX.4.

### XX.6 model upload queue conservation: zero GPU slots caused block-type holes

The 2026-07-12 coordinate capture closes XX.4's remaining render-path gap for
the confirmed grass hole at `block=[209,63,23]`:

- HOC selected exactly the L0 node (`mesh=711`, render-list index 529).
- MDIC produced five effective opaque commands plus one effective translucent
  command for the section. The bounded geometry readback found two quads
  covering the target cell, including its UP face; both carried `modelId=24`.
- Voxy's ingested voxel was `grass_block[snowy=false]`, block-state id 146,
  and the current factory mapping was also model id 24. Its summary was a real
  unique bake (`primarySprite=software-bakery-face-down`), not a dedupe alias.
- The current GPU model-24 record was zero (`actualCustomId=0` versus expected
  10132), and every face of its atlas tile had 256/256 zero-alpha, zero-RGBA
  pixels. Thus geometry selection, command generation, and packed model-id
  selection were correct; the referenced GPU model slot had never been
  populated.

The factory snapshot supplies exact queue-conservation evidence:

```text
nextModelId=262
dedupeMissCount=262
uploadedModelRecordCount=250
uploadedAtlasFaceCount=1500 (= 250 * 6)
modelStoreReadbackAuditRuns=250
modelStoreReadbackAuditFailures=0
queuedUploadResultCount=0
queuesEmpty=true
```

Twelve unique model ids were published to `idMappings`, metadata, summaries,
and therefore geometry, but only 250 of 262 `ModelBakeUpload` objects reached
the GPU. The existing readback audit is enqueued at the end of a successful
`ModelBakeUpload.upload()`, so the twelve missing uploaders bypassed it
entirely; all 250 audited uploads could be byte-correct while twelve GPU slots
remained at their expected new-store zero initialization.

Root cause: the Forge port added an undocumented
`MAX_MODEL_UPLOADS_PER_TICK=2` to original `ModelFactory.processUploads()`.
The bounded loop polled its next `ResultUploader` at the end of each successful
iteration, then checked the budget at the top. After processing item two it
removed item three from the concurrent deque, exited because the budget was
exhausted, and neither uploaded, requeued, nor freed item three. Each saturated
client-tick batch therefore lost one native model/atlas payload without an
error or dropped-upload counter. Worker/render timing changes the batch
boundaries on every owner rebuild, explaining why the permanently missing
type can be grass, one leaf type, another block, or nothing conspicuous.

Original parity: original `ModelBakerySubsystem.tick(long totalBudget)` ignores
the budget and calls `ModelFactory.processUploads()`, whose do/while loop drains
the complete upload deque. XX.6 removes the Forge-only two-upload cap and its
capped API, restoring that drain-all owner contract. Forge's context-GL-error
isolation, post-commit readback, bounded retry, and loud terminal failure remain
as audit/platform adaptations; every polled uploader is now uploaded and freed,
requeued on a retryable failure, or explicitly freed with a loud terminal
failure.

Commit review also hardened that ownership boundary without changing successful
upload behavior: an unexpected runtime exception requeues the polled native
payload for lifecycle teardown, completion counters publish only after every
record/colour/atlas stage succeeds, and the retry limit now means exactly 16
failed attempts. The worker loop again uses original `processAllThings()`'s
worker-only predicate rather than busy-spinning on the render-thread upload
queue; global queue-empty status still includes those pending uploads.

Static model-atlas reuse and zeroing are excluded as causes: original and Forge
both clear the cached atlas on checkout for a new store. The zero initialization
only became persistent because the corresponding upload object was lost.
Geometry-buffer reuse is independent.

Validation: `gradlew compileJava` passed. The user then confirmed no holes on a
fresh Complementary lifecycle and across four repeated
Complementary -> BSL -> Complementary rebuild cycles. The final quiescent
snapshot satisfied the complete upload-conservation invariant:

```text
nextModelId=255
dedupeMissCount=255
uploadedModelRecordCount=255
modelStoreReadbackAuditRuns=255
uploadedAtlasFaceCount=1530 (= 255 * 6)
queuedUploadResultCount=0
queuesEmpty=true
```

The same grass target's model 24 now had `expectedCustomId=10132`,
`actualCustomId=10132`, six non-zero atlas faces (256/256 opaque texels each),
and successful record/texture readbacks. The client then completed normal
render-system, world, and instance shutdown with no upload failure, drop, or
diagnostic exception. This resolves XX.4's shaderpack-switch/new-lifecycle
block-type holes. The wider dimension/F3+T/logout regression checklist remains
separate, so readiness flags stay unchanged.

Separate non-causal cleanup note: Forge's full client shutdown clears the
reused geometry cache but does not yet explicitly delete the static cached
model-atlas texture as original `RenderResourceReuse.clearResources()` does.
That terminal GL-resource parity gap cannot cause reload-time model-24 zeroing
and is not mixed into XX.6.
