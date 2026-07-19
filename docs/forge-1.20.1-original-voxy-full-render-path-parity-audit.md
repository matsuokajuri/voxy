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
Iris shaderpack integration -> Oculus optional compatibility frontend
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

The active renderer entry is the Embeddium cutout hook adapter. It delegates to
the single Forge `VoxyRenderSystem` owner. Without Oculus that owner selects the
normal render pipeline; when optional Oculus is installed it may select the
patch/data/binding adaptation of the original Iris MDIC pipeline.
`ForgeFrontendCompat` and its version/status DTO were removed in XXV: dependency
presence and bounds are a loader/packaging contract, not renderer ownership or
readiness state. Embeddium remains the hard client prerequisite.

## Current verdict

```text
RENDERER_PARITY_IMPLEMENTED
XXV_STATIC_SOURCE_AUDIT=passed
XXV_FRONTEND_PARITY_CORRECTIONS=implemented
XXV_FINAL_JAR_AUDIT=passed-after-option-id-correction
XXV_BROAD_VISUAL_ACCEPTANCE=passed
XXV_F3_VISIBILITY_RUNTIME_ACCEPTANCE=passed
XXV_EMBEDDIUM_PAGE_RUNTIME_ACCEPTANCE=passed
XXV_CONFIG_APPLY_RUNTIME_ACCEPTANCE=passed
XXV_TARGETED_FRONTEND_RUNTIME_ACCEPTANCE=passed
XXVI_POST_XXV_GLOBAL_REVIEW=passed-static
XXVI_IMPLEMENTATION=implemented
XXVI_STATIC_SOURCE_AUDIT=passed
XXVI_FULL_BUILD_AND_ARTIFACT_AUDIT=passed
XXVI_CLAUDE_SUPPLEMENTAL_REVIEW=partial-no-findings
XXVI_RUNTIME_REGRESSION=passed-via-xxvii-consolidated-user-regression
XXVII_EXHAUSTIVE_LINE_AUDIT_PASS_1=252-of-252-reviewed
XXVII_PASS_1_FINDINGS=15-repaired-static-gates-passed
XXVII_EXHAUSTIVE_LINE_AUDIT_PASS_2=261-of-261-reviewed-22-repaired
XXVII_EXHAUSTIVE_LINE_AUDIT_PASS_3=270-of-270-reviewed-3-repaired
XXVII_EXHAUSTIVE_LINE_AUDIT_PASS_4_TO_7=complete-findings-repaired
XXVII_EXHAUSTIVE_LINE_AUDIT_PASS_8=275-of-275-reviewed-1-repaired
XXVII_EXHAUSTIVE_LINE_AUDIT_PASS_9=275-of-275-reviewed-1-repaired
XXVII_EXHAUSTIVE_LINE_AUDIT_PASS_10=275-of-275-reviewed-3-repaired
XXVII_EXHAUSTIVE_LINE_AUDIT_PASS_11=275-of-275-reviewed-3-comments-repaired
XXVII_EXHAUSTIVE_LINE_AUDIT_PASS_12=275-of-275-reviewed-7-repaired
XXVII_EXHAUSTIVE_LINE_AUDIT_PASS_13=275-of-275-reviewed-1-repaired
XXVII_EXHAUSTIVE_LINE_AUDIT_PASS_14=275-of-275-reviewed-0-findings
XXVII_ZERO_NEW_FINDING_PASS=passed-pass-14
XXVII_STATIC_COMPLETION=passed
XXVII_FORCED_CLEAN_GATE=35-suites-110-tests-jarJar-passed
XXVII_RUNTIME_REGRESSION=passed-user-2026-07-14
XXVII_RELEASE_READINESS=approved-by-user-finalization-request-2026-07-14
XXVII_FINAL_PACKAGE=12,589,718-bytes-sha256-50a27befcfb8d9390aac4db77ab76cf25afe9b4a1fa0aa30c554b7654a5b5502
XXVIII_FORMAL_CLIENT_REGRESSION=confirmed-embeddium-0.3.31-internal-api-linkage-failure
XXVIII_IMPLEMENTATION=implemented-minimum-frontend-watertight-bake-and-custom-renderer-empty-map-repairs
XXVIII_MINIMUM_FRONTEND_GATE=36-suites-114-tests-jarJar-passed-against-formal-embeddium-0.3.31
XXVIII_FORMAL_RUNTIME_REGRESSION=passed-user-2026-07-14
XXVIII_CHUNKY_PREGGEN_REGRESSION=passed-user-2026-07-14
XXVIII_RELEASE_READINESS=beta-complete-approved-by-user-2026-07-14
WHOLE_ORIGINAL_MOD_PARITY=beta-complete-user-approved-2026-07-14
```

The original-equivalent renderer chain remains the only visible route and its
XXIV regression was explicitly user-approved. XXV deliberately reopened the
completion claim after a byte-level source, API, optional-integration, packaging,
and retired-code audit found additional original content and non-production
status shells. The old `formal*Ready`, `wholeOriginalModParity`, and parity status
command fields were write/read-only telemetry; they were not control-flow gates
and have been removed. The first broad XXV visual/lifecycle acceptance passed,
but it exposed two frontend-parity omissions after that run: Voxy diagnostics
were not gated by the F3 screen, and the original Sodium-hosted Voxy option
pages had been replaced by a standalone Forge screen instead of being embedded
in Embeddium. Both corrections are now implemented. A final review then found
and fixed Embeddium's global `OptionIdentifier` type collision by separating the
page IDs from the Boolean option IDs. The post-fix production build/JAR audit
passes. The user subsequently completed the focused F3/menu observation without
reporting an anomaly, and the log proves the Voxy pages were registered and the
Embeddium options GUI was constructed. A follow-up then exercised Rendering off
and on through Apply: the user confirmed LOD disappeared and returned, the
expected renderer/Oculus lifecycle completed, configuration persisted, and the
client exited normally. That historical XXV acceptance gate closed, but the
post-commit global review reopened whole-mod parity for XXVI after finding
unported session ownership, ingest-performance, storage-type, optional-library,
and diagnostic behavior that the visual/frontend gate could not exercise.

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

## Historical confirmed drift (resolved by XII-XXIII)

This table records the initial gaps that drove the migration. Its current-status
column and the later Roman-round sections are authoritative; the gaps are not
active substitute routes.

| Area | Drift | Required correction |
| --- | --- | --- |
| Model texture bake | first-quad/sprite extraction and safe-set rejection replaced original software bake semantics | Port/adapt `SoftwareModelTextureBakery`, `SoftwareRasterizer`, `TextureUtils` |
| Model lifecycle | safe-set upload does not match original on-demand model request/requeue | Port `ModelBakerySubsystem` and `ModelFactory` lifecycle |
| Geometry generation | temporary rewrites / CPU mesh conversion are not original `RenderDataFactory` | Complete `RenderDataFactory` parity from `WorldSection` raw data |
| Geometry ownership | Forge debug/simple heap paths are not original `BasicAsyncGeometryManager` | Port allocation, 128-record alignment, section id reuse, upload/free lifecycle |
| Visibility | radius/frustum/preview snapshots are not original traversal | `RenderDistanceTracker` is now Forge-ported; next correction is the original `HierarchicalOcclusionTraverser` with its `Viewport` / HiZ / render-list dependencies |
| Command generation | validation compute shaders are not production `cmdgen.comp` | Fixed for the active Roman route: production `prep.comp`, cull raster, `cmdgen.comp`, prefix sum, and `buildtranslucents.comp` now run from `ForgeOriginalVoxyMdicSectionRenderer` against real `MDICViewport` and `BasicSectionGeometryData` resources. |
| Draw owner | visible preview owner is not `MDICSectionRenderer` | Fixed: the Embeddium cutout hook is only a platform entry adapter and delegates to the single `ForgeOriginalVoxyRenderSystem` owner through `ForgeOriginalVoxyModelPipeline.renderEmbeddiumCutout(...)`. The active frame follows original `AbstractRenderPipeline.runPipeline(...)` order and the real `ForgeOriginalVoxyMdicSectionRenderer` submits opaque, temporal, and translucent indirect draws. Full regression passed after XX.6. |
| Shader semantics | adapter/subset shader is not full original terrain shader contract | Fixed for renderer parity: `ForgeOriginalVoxyRenderPipeline` and `ForgeOriginalVoxyMdicSectionRenderer` own the original normal/patched terrain contract, TAA, shaderpack targets/bindings/blend/depth transfer, SSAO, and final blit. The earlier Complementary patched-program fallback blocker was retired on 2026-07-02; the expanded post-XX.6 shaderpack regression passed except IterationT, which has no upstream Voxy adaptation and is classified as a post-parity compatibility TODO. |

## Historical correction trail

These were the first directionally correct pieces before the later Roman rounds
completed ownership and readiness:

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

The current Forge parity route now owns the original outer selection order:
an active Oculus/Iris shadow pass returns no Voxy viewport before optional
Vivecraft render-pass selection, otherwise the selector chooses the Vivecraft
or default viewport. All `MDICViewport` resources remain owned by the selected
viewport. The optional Vivecraft adapter uses a validated reflection contract so
that integration does not become a hard dependency: an absent API remains the
normal vanilla state, while a present but incompatible API now emits one stable
warning instead of silently selecting the vanilla viewport. Optional Oculus
uses the same installed-integration policy: absent state selects the normal
pipeline, while its matching state owners use direct typed access only after the
availability gate.

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
Forge 1.20.1 field. A 2026-07-19 audit of the installed original Voxy 0.2.16 and
Minecraft 26.1.2 artifacts established the complete dependency chain instead of
inferring it from sprite transparency. Leaf `.png.mcmeta` selects
`dark_cutout`; Minecraft's `MipmapGenerator` calls
`TextureUtil.fillEmptyAreasWithDarkColor` on atlas level zero before upload;
original Voxy then downloads that already-processed atlas. The preprocessing
chooses the darkest non-transparent texel, multiplies each RGB channel by 3/4,
and writes that RGB into every alpha-zero texel while preserving alpha zero.

Forge 1.20.1 never performs that modern atlas preprocessing. The active Forge
`MipGen` therefore ports it per 16x16 model face when the existing software-
bakery dark-cutout bit is set, and retains `TextureUtils.mipColours(true, ...)`
for the mip chain. Non-dark-cutout textures retain the original solidify route.
`MipGenDarkCutoutParityTest` covers both heap and production `MemoryBuffer`
paths. The user confirmed on 2026-07-19 that LOD leaves now match the original
crisp dark-green-gap appearance; the earlier transparency heuristic was not a
complete account of the original behavior.

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
through the direct typed `ForgeOculusWorldRenderingSettingsBridge` and writes the original custom id word in the
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

## Remaining parity work after XXIII

1. XXIV performs the final source-area/JAR/documentation audit and one complete
   user regression before any whole-mod parity decision.
2. IterationT remains a post-parity compatibility TODO: it is absent from
   original Voxy and is not a migration blocker.

Storage isolation/corrupt-entry checks, original config semantics, deprecated
route cleanup, model-layer rename, importers, reload/F3 diagnostics, and the
Forge config entry point are implemented and runtime-regressed by XXII-XXIII.

## Current documented Forge deviations

| Area | Reason | Status |
| --- | --- | --- |
| `StairBlock.baseState` access | original source accesses the field directly; Forge 1.20.1 exposes it as private at compile time | Forge port uses a cached reflective field read to preserve original normalization semantics |
| `UploadStream` persistent staging | original upload path depends on `GlPersistentMappedBuffer`, `GlFence`, `GlBuffer`, and `AllocationArena` from the original client-core GL stack | Forge now ports this as `ForgeOriginalVoxyUploadStream`: persistent mapped staging buffer, `AllocationArena`, frame fences, explicit flush/copy/commit. The singleton is lazy-created on the render thread to respect Forge GL-context timing. |
| `TextureUtils` ColorSRGB path | original Voxy imports Sodium `ColorSRGB`; Forge runtime prerequisite is Embeddium, whose reference source keeps the same fast-srgb8 table under a moved package | Forge now ports that fast-srgb8 table locally and `textureUtilsByteForByteAuditReady=true` is reported when the table/mip sample audit passes. The 1.20.1 `ARGB` class name is unavailable, so alpha uses the same table helper as a documented mapping adaptation. |
| `RenderGenerationService` request/requeue | original request/requeue depends on `RenderDataFactory.generateMesh()` throwing `IdNotYetComputedException` from real section generation and on `ServiceManager.createService(...)` for worker execution | Fixed for the Forge parity route: `ForgeOriginalVoxyRenderGenerationService` now registers with original `ServiceManager` / `Service` / `UnifiedServiceThreadPool`, creates per-thread `RenderDataFactory` and missed-model sets through the service context supplier, uses `service.execute()` for enqueue/requeue, and drains/shuts down service permits in the original order. `originalServiceManagerParityReady=true` is now reported for this service stack. |
| Service thread count / builder-thread sharing | original `VoxyClientInstance.updateDedicatedThreads()` subtracts Sodium chunk-builder threads, and original `MixinChunkJobQueue` lets Sodium builder workers share Voxy service jobs through `SemaphoreBlockImpersonator` | Fixed for the Forge/Embeddium route: `originalVoxyServiceThreads` mirrors the original `serviceThreads` target default, `originalVoxyUseEmbeddiumBuilderThreads` mirrors the original "Use sodium threads" option, `ForgeOriginalVoxyServiceThreadPolicy` subtracts Embeddium `ChunkBuilder.getTotalThreadCount()`, and `ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin` replaces Embeddium `ChunkJobQueue`'s semaphore with the original `SemaphoreBlockImpersonator` / `groupSemaphore.createBlock()` mechanism. |
| Service thread frontend access | original Voxy compiles against Sodium and uses a Sodium accessor mixin for `SodiumWorldRenderer.renderSectionManager`; Forge must target Embeddium's matching owner | Forge now ports that mechanism directly through `ForgeOriginalVoxyEmbeddiumWorldRendererAccessor`, then calls typed `RenderSectionManager.getBuilder()` / `ChunkBuilder.getTotalThreadCount()`. Embeddium is already the mandatory frontend, so ABI drift fails at the required Mixin/direct-call contract instead of being swallowed by reflection and silently changing the thread policy. |
| Sodium-hosted config pages | original Voxy adds General/Rendering pages to Sodium and ModMenu opens that frontend; Fabric ModMenu and the original Sodium config API are not Forge 1.20.1 owners | Voxy adds the same nine settings to Embeddium through official `OptionGUIConstructionEvent` pages. The Forge Mod List factory opens Embeddium's screen. Embeddium exposes no public initial-page selector, so it cannot force-select Voxy's page, but the page host, Apply lifecycle, enabled predicates, and setting effects are preserved. |
| F3 diagnostic extension | original diagnostics exist only as debug-screen entries; Forge 1.20.1 exposes `CustomizeGuiOverlayEvent.DebugText` instead of the newer entry-list API | the event adapter returns whenever `Minecraft.options.renderDebug` is false, then appends the same owner diagnostics only while F3 is visible |
| enabled-config instance lifecycle | original applies enabled changes through `VoxyCommon.shutdownInstance/createInstance`, immediately replacing all instance owners | Historical XXV state kept the service pool process-owned. XXVI.1 superseded that adapter with one `SessionRuntime` per network connection; disabled/re-enable now replaces the renderer, imports, services, world map, storage, and cleaner as one owner. |
| `CpuLayout` default thread count | original Voxy's `CpuLayout` uses platform affinity helpers from the original LWJGL/JNA stack; the Forge 1.20.1 classpath lacks the same `org.lwjgl.system.windows.Kernel32` API | Forge uses OSHI physical core count and the same fallback-to-available-processors behavior to preserve the original `serviceThreads = max(coreCount / 1.5, 1)` default. This is a platform adapter, not a scheduler fallback. |
| `common.Logger` client HUD branch | original common logger checks Fabric `VoxyCommon.IS_IN_MINECRAFT` / `IS_DEDICATED_SERVER`; pulling Fabric `commonImpl` into the Forge source set would reintroduce platform code that cannot compile here | Forge keeps the original logger API for the imported thread stack and adapts only the platform guard to `Minecraft.getInstance() != null` before posting client HUD messages. |
| `RenderDataFactory` Java version helpers | original source uses `Integer.expand` / `Long.expand`, unavailable in Java 17 | Forge uses local equivalent bit-expansion helpers with the same mask/value semantics. |
| `AsyncNodeManager` render-side traversal producers | original Voxy receives top-level node adds/removes from `RenderDistanceTracker`, and request batches from `HierarchicalOcclusionTraverser` | Forge now ports the geometry-result queue, `SyncResults`, `ComputeMemoryCopy`, `UploadStream`, `DownloadStream`, `memcpy.comp`, `scatter.comp`, `SectionUpdateRouter`, `SingleNodeRequest`, `NodeChildRequest`, leaf-to-inner transitions, inner-node compaction, top-level node id deltas, cleaner reset/clear deltas, request batch entry points, remove batch entry points, render-side `NodeCleaner`, `GeometryCache`, `RenderDistanceTracker`, and the HOC owner/request-buffer path. `originalNodeManagerParityReady=true` is limited to this ownership layer. `originalHierarchicalOcclusionTraverserOwnerReady=true` does not imply HiZ traversal execution until `originalHizTraversalExecutableReady=true`. |
| `AsyncNodeManager` GeometryCache | original Voxy has a CPU-side `GeometryCache` inside `AsyncNodeManager`; initial render generation first tries `geometryCache.remove(pos)`, and dirty world events clear cached geometry for the changed section | Fixed for the Forge parity route: `ForgeOriginalVoxyGeometryCache` mirrors original cache semantics, initial render callbacks consume cached geometry before queueing render generation, and world dirty callbacks clear stale cached geometry before forwarding router/remesh events. |
| `RenderDistanceTracker` | original Voxy uses `RingTracker` to feed top-level LoD node add/remove events into `AsyncNodeManager` | Fixed for the Forge parity route: `ForgeOriginalVoxyRingTracker` and `ForgeOriginalVoxyRenderDistanceTracker` mirror the original algorithm and feed `AsyncNodeManager.addTopLevel/removeTopLevel`; render distance is now sourced from `originalVoxySectionRenderDistance`, the Forge config equivalent of original `VoxyConfig.CONFIG.sectionRenderDistance`. |
| `HierarchicalOcclusionTraverser` / `ViewportSelector` / `MDICViewport` / HiZ | original Voxy rejects Iris shadow rendering before selecting a default or optional Vivecraft viewport, copies vanilla depth into a Voxy-owned `DepthFramebuffer(GL_DEPTH24_STENCIL8)` through `setup_stencil_depth.frag`, builds a HiZ depth pyramid, then runs GPU HOC traversal to produce render-list entries and node request batches | Fixed: the Forge owners port the original buffers, traversal, selector, depth/HiZ, request download, and shadow-pass rejection before optional-pass selection. The active outer `ForgeOriginalVoxyRenderSystem` now runs `MDICSectionRenderer.renderOpaque(...)` immediately in the original frame order; the Embeddium adapter restores external GL state only because it enters from another renderer's pass. Historical CPU planners remain excluded. |
| `MDICViewport` -> `MDICSectionRenderer.buildDrawCalls(...)` | original `MDICSectionRenderer.buildDrawCalls(...)` uploads the MDIC scene uniform, runs `prep.comp`, rasterizes section AABBs into the viewport visibility buffer with color/depth writes disabled, dispatches production `cmdgen.comp`, runs prefix sum for translucent distance buckets, and dispatches `buildtranslucents.comp` | Fixed: `ForgeOriginalVoxyMdicSectionRenderer` owns the production programs, original buffers/layouts, command generation, and visible opaque/temporal/translucent indirect submissions. Targeted readbacks and the post-XX.6 visual regression confirm the real path rather than debug command buffers. |
| `AbstractRenderPipeline` -> terrain shader owner | original `MDICSectionRenderer` receives a render pipeline, asks it for TAA and shader patches, compiles patched-or-normal opaque and translucent terrain programs, and then relies on the pipeline to bind opaque/translucent draw targets | Fixed for renderer parity: the Forge normal and Oculus paths own the original shader hooks, targets, SSAO, TAA, patch bindings, blend, depth transfers, and final blit. Roman XVIII/XX completed adapter state coverage and outer lifecycle ownership; the 2026-07-02 audit retired the patched-program fallback blocker. |
| Embeddium render hook entry | original Voxy drives this chain from one `VoxyRenderSystem` owner; Forge must hook Embeddium until that owner is fully ported | The active mixin config uses one `DefaultChunkRenderer` cutout-pass hook. A stale, unregistered `SodiumWorldRenderer.drawChunkLayer` hook source was removed so it cannot be accidentally enabled as a second route. `ForgeOriginalVoxyModelPipeline.renderEmbeddiumCutout(...)` now also guards reentrant entry and only runs post-command-generation dynamic work after command generation actually completes. |
| `SoftwareModelTextureBakery` model collection and dark-cutout metadata | Forge 1.20.1 lacks the newer original `BlockStateModelPart` and public `BakedQuad.materialInfo()` API, but Embeddium injects the equivalent `BakedQuadView` and sprite transparency data used by its own chunk mesher | fixed for the active Forge/Embeddium route: `originalSoftwareModelTextureBakeryUsed=true`; the adaptation is constrained to Embeddium source-equivalent material and transparency signals |
| `ModelStore` ownership and audit | fixed: the original model pipeline now owns `ForgeOriginalVoxyModelStore` instead of historical `ForgeFormalModelStore`; uploads use original-style `MemoryBuffer` results, persistent `UploadStream`, DSA texture mip uploads, block-atlas-derived sampler max LOD, and post-commit readback audit for modelData/modelColour/atlas mip-chain regions | `originalModelStoreUsed=true` is reported when the owner is built; `originalModelStoreReadbackAuditReady=true` is reported after a committed upload readback matches the CPU payload |
| Iris/Oculus custom block-state ids | original Voxy receives `WorldRenderingSettings.INSTANCE.getBlockStateIds()` from the Iris pipeline | Without optional Oculus the bridge supplies the original normal-path null mapping; when Oculus is installed it uses the same direct typed singleton and null maps write custom id zero, while installed-frontend ABI drift is not converted into a reflective missing result |
| Iris/Oculus shaderpack pipeline data | original Voxy receives `IrisShaderPatch` from `ProgramSet`, stores `IrisVoxyRenderPipelineData` on `IrisRenderingPipeline`, and uses the data to patch/bind MDIC terrain rendering | Fixed for the active Forge/Oculus route: the program-set/pipeline mixins, source sidecars, patch parser, uniforms, samplers, SSBOs, images, targets, blend, TAA, and depth transfers mirror the original contract with documented Oculus 1.20.1 signature/timing adaptations. Patched opaque/translucent programs are used for compatible packs; Complementary, BSL, Photon, and multiple additional packs passed the final regression. IterationT has no upstream sidecar/adaptation and is post-parity work. |
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

## Historical next-work trail (superseded)

The following investigation plan is preserved only as the evidence trail that
led to the completed renderer. The current work is the XXIV closure section at
the end of this document.

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
truth (baseline vs post-toggle `/voxy original_voxy_model_pipeline_status`
(historical command, removed in XXV),
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
boundary (all logged). XXVII's exhaustive failure-path audit then removed the
remaining capped-drop behavior: a reported upload failure keeps the native
payload queue-owned and propagates a fatal owner error, so CPU mappings can
never remain valid after the corresponding GPU payload is discarded. This is
the nearest explicit-error equivalent of original Voxy's direct upload path.

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

Historical XX.4 probe (removed in XXV):
`/voxy original_voxy_node_consistency_audit` downloaded the
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
- The removed `/voxy original_voxy_node_consistency_audit <blockX> <blockY>
  <blockZ>` probe then
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
block-type holes.

Full regression follow-up (2026-07-12): the user completed shaderpack switching,
dimension switching, standalone F3+T reload, logout/login, and client-exit tests
without finding a regression. Photon LOD water is visible again, and multiple
additional shaderpacks render correctly. Readiness flags remain unchanged until
the user makes the separate readiness decision required by this port.

Post-parity compatibility TODO — IterationT 3.2.0 is the only shaderpack in this
expanded test set reported to render incorrectly. This is not an identified
Forge port omission: neither the original source snapshot in this tree, local
`dev`, `origin/dev`, nor current upstream MCRcortex/voxy `dev` at `72fb44a1`
contains an IterationT/Iteration T special case. Original Voxy exposes the
generic pack-side `voxy.json`, `voxy_opaque.glsl`, `voxy_translucent.glsl`, and
`voxy_taa.glsl` contract; the tested `iterationT 3.2.0.zip` contains none of
those sidecars. After the original Voxy route is completely ported, investigate
IterationT as a new, non-upstream shaderpack compatibility task. Do not invent a
pack-specific substitute inside the parity phase, and do not treat this TODO as
a readiness blocker for original-behavior parity.

### XX.7 renderer-readiness closure audit and terminal atlas cleanup

The readiness audit re-read the current source rather than inheriting the
preview-era status documents. The active visible route has one entry
(`DefaultChunkRenderer` CUTOUT adapter) and one original-equivalent owner chain:
`ForgeOriginalVoxyRenderSystem` owns the model, generation, geometry, node,
tracker, viewport/HiZ/HOC, MDIC, terrain-pipeline, and lifecycle resources;
`ForgeOriginalVoxyModelPipeline` supplies only Forge event/hook/status policy.
The visible frame order matches original `VoxyRenderSystem.renderOpaque()` and
`AbstractRenderPipeline.runPipeline()`, and the real MDIC renderer submits the
pixels. The old preview/simple-GPU/legacy geometry-MDIC owners are absent from
active source; retained audits only read real active buffers.

The user approved the separate renderer-readiness decision. The three live
fields now derive from current owner and draw evidence rather than unconditional
constants: owner construction resets lifecycle draw evidence; formal readiness
requires the complete live owner chain; actual draw readiness requires the
current generation's MDIC submission, finish, and state restore; formal draw
pipeline readiness requires production prep/cull/cmdgen and a successful terrain
indirect submission. `earlyUsableLodRendererReady` remains retired.

One exact terminal resource deviation was found and ported before that decision.
Original `RenderResourceReuse.clearResources()` deletes both cached model
atlases and cached geometry buffers after every render owner is shut down. Forge
already returned atlases to `ForgeOriginalVoxyModelStore`'s static cache across
owner rebuilds, but terminal `ForgeOriginalVoxyRenderResourceReuse.clearResources()`
deleted only geometry buffers. XX.7 now deletes cached atlas texture ids first,
then frees/decommits cached geometry buffers in the original shutdown order.
This cannot affect steady-state pixels and is not the XX.4 root cause; it closes
the known full-instance render-resource lifetime gap.

Renderer readiness is deliberately separated from whole-mod parity. At the XX.7
boundary the active `WorldEngine` still used `MemoryStorageBackend`; XXI.1 below
ports the original default persistent chain, while dynamic storage configuration
and optional backends remain. IterationT, optional integration inventory, unused
config-key cleanup, and the active `ForgeCpuMeshLayer` rename are also later
work, not substitutes or renderer-readiness blockers.

Validation passed: `gradlew compileJava`, full `gradlew build`, and the final
`runClient` smoke test all completed successfully. The client rebuilt the formal
owner four times, reused the same geometry buffer, then completed render-system,
WorldEngine, instance, and Minecraft shutdown normally. `latest.log` contains no
Voxy warning/error, model upload/drop, `FAILED_SAFE`, OOM, or GL-invalid event.
The user subsequently approved that decision and the three live fields were
wired as described above. A post-wiring `runClient` pass was required to confirm
all three reported true in the active world before XX.7 was committed.

The first post-wiring status run exposed one audit-wiring error rather than a
renderer failure: `formalDrawPipelineReady=true`, all live draw and owner
evidence was true, but the aggregate model predicate consumed
`BasicAsyncGeometryManager`'s historical hardcoded-false
`originalNodeManagerParityReady` slot. The same snapshot's authoritative
`AsyncNodeManager` owner reported `originalAsyncNodeManagerFullParityReady=true`;
it had completed 8,207 HOC traversals and 8,118 visible MDIC draw frames with no
visible-frame failure. This matches original ownership, where `AsyncNodeManager`
owns `NodeManager` and passes the geometry manager into it, not the reverse.
XX.7 therefore removes the stale geometry proxy from the formal predicate and
maps the aggregate `originalNodeManagerParityReady` field from the live async
node owner. Final runtime confirmation of all three readiness fields was still
required before commit.

Final XX.7 runtime confirmation passed on 2026-07-12. In the active world,
`/voxy parity_route_status` reported `formalRendererReady=true`,
`actualRendererDrawEnabled=true`, and `formalDrawPipelineReady=true`, while
retaining `earlyUsableLodRendererReady=retired` and
`wholeOriginalModParity=false`. The client then shut down the render system,
WorldEngine, Forge instance, and Minecraft normally; `runClient` exited 0. The
original in-flight-request warnings remain the already audited upstream
NodeManager behavior and did not coincide with a renderer failure.

### XXI.1 original default persistent storage and restart recovery

The formal active-world route no longer constructs `MemoryStorageBackend`.
XXI.1 traces and ports original Voxy's default chain without changing its stored
section or mapping formats:

```text
WorldIdentifier(dimension key, biome seed, dimension-type key)
 -> <base>/<first 32 hex chars of SHA-256(seed + dimension key)>/storage
 -> SectionSerializationStorage
 -> CompressionStorageAdaptor
 -> ZSTDCompressor(level 1)
 -> RocksDBStorageBackend
    column families: default, world_sections, id_mappings
```

The Forge mapping adapter captures the original identifier inputs from the
1.20.1 `ClientLevel` constructor. Single-player uses
`<world root>/voxy`; multiplayer uses `.voxy/saves/<server>` and Realms uses
`.voxy/saves/realms`, matching original `VoxyClientInstance.getBasePath()`.
Because the Forge port retains one process-lifetime instance rather than
original `VoxyInstance.activeWorlds`, closing worlds are keyed by both normalized
base path and original world identifier and may be reclaimed on a rapid relog.
This preserves original idle-world reuse and avoids opening the same RocksDB path
twice while its previous owner is still live.

The storage implementation was ported into the Forge namespace because the
reference config classes use FabricLoader discovery and the current reference
RocksDB file contains unreachable `Long.expand` code unavailable on the required
Java 17 runtime. The active methods, byte order, column-family options, WAL
flush, ZSTD contexts, serialization, mapping keys, and close order remain the
original mechanisms. ForgeGradle packages original RocksDB JNI 10.2.1 and LWJGL
ZSTD 3.3.1 through Jar-in-Jar. Forge Jar-in-Jar identifies classifier siblings
by the same group/name, so the official Windows/Linux native resources are
preserved at their original LWJGL resource paths in the outer mod jar instead of
creating conflicting metadata entries.

Runtime ground truth across two separate client processes:

```text
first process:
  same-world storage path = .../74d2036cf9ebdb83178e6f984bd8904e/storage
  sectionWrites=26409
  mappingWrites=448
  normal RocksDB/WorldEngine/client shutdown, runClient exit 0

second process:
  identical world identifier and storage path
  mappingEntriesLoaded=448
  mappingWrites=0
  sectionLoadHits=5676
  sectionLoadMisses=4014
  sectionWrites=501
  normal RocksDB/WorldEngine/client shutdown, runClient exit 0

third process lifecycle regression:
  overworld path = .../74d2036cf9ebdb83178e6f984bd8904e/storage
  nether path    = .../ecbd3f9d84cf72a2a82c7569cd0da279/storage
  each dimension opened and closed its independent RocksDB owner
  rapid logout/login reused the still-live overworld owner
  final status openCount=3 reuseCount=1 closingWorldCount=0
  no RocksDB LOCK, duplicate-open, native, renderer, or shutdown failure
  runClient exit 0
```

This proves section data and Mapper ids survive a real JVM restart and are read
from disk rather than regenerated from an in-process cache. The read-only
`/voxy original_voxy_storage_status` command reports this active backend and its
load/write counters. Whole-mod parity deliberately remains false: the default
production backend is now persistent, while original dynamic configuration and
the optional storage/compressor/adaptor inventory are handled separately below.

#### Forxy 壹轮 delta: native ZSTD result validation

Forxy intentionally hardens the inherited ZSTD failure boundary without
changing the compressor, stored bytes, backend ownership, or default
`Serializer -> ZSTD(level 1) -> RocksDB` route. Original
`common/config/compressors/ZSTDCompressor` passes the `size_t` result of
`nZSTD_decompressDCtx` directly to `MemoryBuffer.subSize()` despite its own
`TODO:FIXME: DONT ASSUME IT DOESNT FAIL`; the active Forge namespace port had
preserved that behavior.

The active Forge ZSTD owner now rejects null native contexts, checks the
experimental decompression parameter result, and applies `ZSTD_isError` plus
`ZSTD_getErrorName` to parameter setup, compression, and decompression before a
result is treated as a byte count. Successful results must fit their destination
and decompressed data must remain between the characterized serialized-section
minimum (65,560 bytes) and the existing conservative maximum (524,296 bytes).
A parameter-setup failure also frees its not-yet-registered context.

The pre-change focused suite produced the intended red baseline: the valid
round trip passed while truncated-frame, random-data, destination-too-small,
undersized-section, and oversized-input expectations failed. The post-change
suite adds eight native/boundary cases plus a real default-config restart test
that writes RocksDB through ZSTD and the section serializer, closes all owners,
rereads `config.json`, reopens RocksDB, and compares all section values. A clean
2026-07-17 `compileJava test jarJar` run passed 42 suites / 140 tests with no
failure, error, or skip; `git diff --check` and formal JarJar content checks also
passed.

The final runtime gate used two post-change client processes. The first opened
`run/saves/新的世界/voxy/74d2036cf9ebdb83178e6f984bd8904e/storage`,
exercised real LOD writes, and closed every storage/session owner normally. The
database then contained 137 files / 607,033,029 bytes. The second process reread
the existing config, opened the identical world identifier and RocksDB path,
displayed the persisted LOD normally, and again shut down cleanly. Neither log
contains a Voxy, ZSTD, RocksDB, native-size, or lock failure. This closes Forxy
壹轮 without changing the established Forge migration parity claim.

#### Forxy 贰轮 delta: Mapper snapshot and lock safety

Forxy keeps the shared original `Mapper` as the single mapping owner and hardens
its inherited concurrency and corrupt-entry behavior. Block and biome
registration, callback replacement, and dense snapshot validation now release
their existing locks through `try/finally`. A snapshot is a validated id-indexed
prefix captured under the corresponding registration lock.

The old render-system bootstrap performed `getBiomeEntries()` and
`setBiomeCallback()` as separate operations, leaving a concurrent-registration
gap. `setBiomeCallbackAndGetSnapshot()` now linearizes callback installation and
snapshot capture. Both the retained original client owner and the active Forge
`ForgeOriginalVoxyRenderSystem` use that API, so each biome is delivered either
through the initial snapshot or the callback exactly once.

The inherited corrupt-block path decoded an invalid stored entry to air, then
randomly assigned its numeric id to an unrelated real block and force-resaved
the invented mapping. Forxy instead retains that nonzero id as an in-memory air
missing-state placeholder, excludes it from reverse state lookup, and preserves
the original stored bytes. Canonical air remains id `0`; valid mapping encoding,
storage-before-callback order, and existing numeric ids are unchanged.

`MapperSnapshotSafetyTest` covers the two former stranded-lock failures,
repeated stable corrupt-entry loading with byte preservation, concurrent block
and biome insertion against dense snapshots, exact-once callback/snapshot
partitioning, and persistence-before-callback ordering. Its 6 tests and the
clean 146-test suite pass; `compileJava test jarJar` produced the formal all-JAR
and `git diff --check` passed apart from existing line-ending notices.

The runtime gate used two post-change client processes against the same existing
quick-play world and `Serializer -> ZSTD(level 1) -> RocksDB` configuration.
Both built the formal `ForgeOriginalVoxyRenderPipeline` /
`MDICSectionRenderer`, displayed LOD normally by user confirmation, and closed
the renderer, network-session storage owner, and Forge instance normally. The
second process reopened the persisted world without a forced mapping resave,
missing-state decode, Mapper lock failure, ZSTD failure, or RocksDB failure.
This closes Forxy 贰轮 without changing the established Forge migration parity
claim.

#### Forxy 叁轮 delta: optional storage position iteration

Forxy closes the inherited optional-backend enumeration gaps while preserving
the established storage formats and ownership. `iteratePositions(level,
consumer)` now treats `-1` as all levels and any other value as an exact encoded
level filter; ordering remains backend-defined, callbacks are synchronous, and
callback exceptions propagate. LMDB walks an `MDB_NEXT` cursor in a read-only
transaction, Redis uses bounded binary `HSCAN` pages with duplicate suppression,
and ReadonlyCachingLayer returns a deduplicated cache-first union.

ReadonlyCachingLayer now merges ID mappings without mutating its read-only
source: conflicting bytes for an existing ID fail explicitly, missing source
mappings are copied into the cache, and cache-only mappings remain intact. Its
inherited `flush()`-closes-children defect is corrected. Lifecycle read/write
locks on LMDB, Redis, and ReadonlyCachingLayer prevent close racing an active
operation; close requested from inside an operation callback is rejected rather
than attempting an unsafe lock upgrade.

`OptionalStorageIterationTest` contains 13 default tests plus one tagged real-
Redis test. It covers empty/mixed-level stores, duplicates, callback failure,
large sets, restart, mapping replication/conflict, flush semantics, and
close-during-iteration including reentrant-close rejection. A clean build passed
all 159 default tests and `jarJar`.
Redis 7.0.15 then passed the dedicated 2,048-key multi-level create, enumerate,
close, reopen, and cleanup test before `SHUTDOWN NOSAVE`.

Two client processes validated a real ReadonlyCachingLayer chain against the
same existing source world and isolated `forxy-round3-readonly-cache`. The
second process reopened the populated cache; both created the formal
`ForgeOriginalVoxyRenderPipeline` / `MDICSectionRenderer`, visuals passed user
confirmation, and the renderer, network-session storage owner, and Forge
instance shut down normally. No mapping conflict, closed-storage error, or Voxy
fatal error appeared. The default world config was restored byte-for-byte with
SHA-256
`437c1c6b67283dba6bdf52898c502b15e8de008f7dfe9d44053213965c706c9f`.
This closes Forxy 叁轮 without changing the established Forge migration parity
claim.

#### Forxy 肆轮 delta: service cancellation accounting

Forxy closes the inherited cancellation-accounting gap without replacing the
original shared service-pool route. Every accepted service submission now owns
one local task permit, one global job count, one global pooled token, and one
pooled wake signal per semaphore block. Claim, completion, steal, drain, and
shutdown each consume or retract that accounting exactly once under the service
lifecycle lock; claimed jobs remain counted until executor cleanup may safely
run.

`ServiceManager` has distinct completion and cancellation paths plus blocking
shutdown notification. `MultiThreadPrioritySemaphore` tracks pooled signals per
block and retracts them through positive `tryAcquire` operations only, avoiding
negative semaphore release and stale borrowed-worker wakes. The unified pool
uses exact worker-exit permits, while context creation/execution failures can no
longer strand running-job counts. Tests for that exception route also exposed
and fixed inherited `WeakConcurrentCleanableHashMap` lock leaks.

The Embeddium 0.3.31 `ChunkJobQueue` bytecode was verified to use the exact
`release(int)`, `acquire()`, `tryAcquire()`, and `availablePermits()` semaphore
surface already implemented by `SemaphoreBlockImpersonator`, preserving builder
thread sharing. Nine deterministic accounting/race tests pass, including three
full worker-resize/shutdown cycles, and a clean build passes all 168 tests plus
`jarJar`.

An Embeddium + Oculus client run created the formal
`ForgeOriginalVoxyRenderPipeline` / `MDICSectionRenderer` and passed user visual
confirmation. Both renderer-owner shutdowns completed normally, followed by
clean network-session, Forge-instance, and Minecraft shutdown, with no job-
accounting, service-manager, executor, or fatal error. This closes Forxy 肆轮
without changing the established Forge migration parity claim.

#### Forxy 伍轮 delta: storage recovery and format versioning

Forxy now versions the original mapping bytes around the existing mapping key
space rather than replacing their serialization. Reserved manifest/backup keys
carry CRC-protected schema and Minecraft data-version state. Legacy databases
remain readable and are backed up before upgrade; future versions are refused
without mutation; interrupted or failed upgrades restore the verified original
mapping snapshot and leave an auditable state. Mapping validation rejects
unknown namespaces, empty records, and non-contiguous IDs instead of synthesizing
unrelated block or biome mappings.

Section corruption is no longer silently converted to saved air. A corrupt
higher-level section is reconstructed and persisted only when all eight direct
children are valid, using the active `Mipper.mip` contract. Otherwise it is
retained and reported as unavailable through `WorldSection` and both
`ActiveSectionTracker` cache tiers. Fragmented mapping storage similarly repairs
only from a verified superset or strict byte majority, refuses ties before
writing, and never destroys a sole valid replica. Forge also stops advertising
the upstream-unimplemented `ConditionalConfig`; encountering one is an explicit,
non-rewriting compatibility refusal.

The clean suite passes 179 tests plus `jarJar`, including legacy/future/failed
mapping upgrades, corrupt higher-LOD recovery, replica divergence, real RocksDB
and LMDB reopen, and conditional-config preservation. A tagged integration test
also passed against an isolated Redis 7.2.14 process. A copied 902 MB production
RocksDB world upgraded on Embeddium-only and reopened on Embeddium + Oculus; both
client paths passed user visual confirmation and clean renderer/storage/session
shutdown without mapping-version, RocksDB, or Voxy fatal errors. This closes
Forxy 伍轮 without changing the established Forge renderer-parity claim.

#### Forxy 陆轮 delta: cache, allocator, and worker efficiency

Forxy removes the original `ActiveSectionTracker` busy loader wait in favor of a
completion holder that reserves pending references before publication. The
secondary cache is sharded over the existing tracker slices while preserving its
global capacity and `nullOnEmpty` load-status semantics. An eight-waiter harness
reduced measured waiting-thread CPU from about 906 ms to zero with the same
single physical section load. `WorldSection` array reuse is now allocation-rate
and memory-pressure adaptive rather than a fixed roughly 100 MiB reserve; the
qualified Embeddium and Oculus workloads reused 96,031 and 131,785 arrays while
allocating 2,134 and 2,109, and both contracted to 32 cached arrays on shutdown.

`AllocationArena` explicitly enforces its packed 30-bit maximum before an
allocation can be represented incorrectly. `HierarchicalBitSet` performs
consecutive allocation and high-water rollback by words while preserving the
lowest-ID allocation order. Boundary and randomized model tests cover the exact
arena maximum, coalescing/no-overlap, exact 64-bit bit-set runs, exact-limit fill,
and 50,000 mixed bit operations.

The render-generation cache experiment also has runtime ground truth. Blindly
cloning every completed mesh produced zero hits, 40,544 evictions, and
98,185,184 retained bytes, so that policy was removed. The retained bounded
owner accepts only late results the node manager would otherwise discard, uses
per-position striped dirty epochs, and has an unlocked empty-cache miss path.
Final Embeddium and Oculus runs retained zero bytes and performed no eviction;
no cache hit-rate improvement is claimed. The original unimplemented GPU
`downloadAndRemove` route was not invented because it would require unmeasured
readback synchronization.

Mapped staging/compute uploads, the two-object synchronization buffer reuse, and
the reusable geometry buffer were already active on the formal Forge owner.
`allocFromLargest` has no consumer and the current three-SSBO dispatches do not
justify multibind, so no speculative alternatives were retained. The default
suite, tagged performance harness, real Bobby/DH data gate, two Embeddium-only
client passes, and Oculus 1.8.0 + Complementary Unbound client pass all completed
without a Voxy fatal error or reported visual/persistence regression. This
closes Forxy 陆轮 without changing the established renderer-parity claim.

#### Forxy 柒轮 delta: level-aware deterministic mipping (runtime qualification pending)

The inherited original `Mipper` selected the highest-opacity non-air corner and
returned that corner's light, so equal materials depended on input order, one
sparse voxel could expand indefinitely, and block/sky light used unrelated
rules for air and solid output. Forxy retains the original 2x2x2 ownership and
packed Mapper ids but passes the actual target level from
`WorldVoxilizedSectionMipper` and higher-LOD recovery.

Selection is allocation-free and deterministic. Occurrence count is primary;
cached block-state opacity, outline-shape volume, fluid presence, and emission
resolve equal support, followed by stable numeric block/biome tie breaks.
Levels 1-2 preserve a single detail sample, then support thresholds rise to
2/3/4; thin shapes tighten from level 4 and isolated emissive material survives
through level 4 without propagating forever. Block light uses the input maximum,
while skylight uses the non-air maximum for solid output and ceiling average for
air output. Fixtures cover opaque, cutout, translucent, fluid, emissive, thin,
mixed-light, traversal permutations, and the four in-section levels.

The non-gating mixed-material benchmark measured 86.2 ns per mip and 50.4 us for
all 585 mips in one section, within the declared 120 ns/op and 12x comparison
budget. A gated `-PvoxyAuditRound7Mipping` runtime counter is connected for the
Chunky/Bobby/DH import passes. Renderer parity is unchanged; visual and real
import timing qualification remain pending before 柒轮 can be closed.

### XXI.2 original storage config JSON and production TYPE registry

XXI.2 ports the original configuration mechanism around the XXI.1 production
backend rather than adding a Forge-only toggle. Because original
`Serialization.init()` discovers config classes through FabricLoader, the Forge
adapter uses an explicit registry for the exact production types currently
compiled into the Forge source set:

```text
SectionStorageConfig: Serializer
StorageConfig:        CompressionAdaptor, RocksDB
CompressorConfig:     ZSTD
```

The adapter preserves original polymorphic JSON semantics: `TYPE` is emitted as
the first field of each configured object, deserialization dispatches through
the matching abstract config family, and the default remains
`Serializer -> CompressionAdaptor(ZSTD level 1, RocksDB)`. It also compiles and
uses original `ConfigBuildCtx`; `{base_save_path}`, `{world_identifier}`, and
`{player_uuid}` are populated exactly where original `VoxyClientInstance` does,
then `{base_save_path}/{world_identifier}/storage/` is pushed before building
the configured section storage.

`<base>/config.json` now follows original `StorageConfigUtil` lifecycle:

```text
create base directory
 -> load existing JSON when present
 -> require version == 1 and non-null sectionStorageConfig
 -> fall back to the original default on null/invalid/load failure
 -> pretty-print the effective config back to config.json
 -> honor disabled before creating an active WorldEngine
```

Runtime ground truth across two separate client processes using the XXI.1
database:

```text
first process:
  storageConfigSource=default-created
  config JSON TYPE chain = Serializer / CompressionAdaptor / ZSTD / RocksDB
  mappingEntriesLoaded=1384
  sectionLoadHits=8550
  normal storage/render/client shutdown, runClient exit 0

second process:
  storageConfigSource=loaded
  same config path, world identifier, and RocksDB storage path
  mappingEntriesLoaded=1384
  sectionLoadHits=5989
  normal storage/render/client shutdown, runClient exit 0
```

The `/voxy original_voxy_storage_status` command now exposes config readiness,
path, load source, and disabled state independently of storage counters. This
closes the original default-chain config creation/reload gap without claiming
the unported optional TYPE inventory. `wholeOriginalModParity` therefore remains
false; renderer readiness is unchanged.

### XXI.3 original local optional storage/compressor TYPE surface

XXI.3 audits every remaining original config class before choosing the local
runtime scope. The following active, locally testable TYPE names and mechanisms
are now registered:

```text
CompressorConfig: LZ4
StorageConfig:    BasicPathConfig
                  FragmentationAdaptor
                  AutoFragmentationAdaptor
```

The LZ4 port preserves original `LZ4Factory.nativeInstance()` selection, the
four-byte native-endian uncompressed-size prefix, fast compression/decompression,
and original thread-local scratch sizing. ForgeGradle embeds original
`org.lz4:lz4-java:1.8.0` through Jar-in-Jar.

The fragmentation port preserves original double `RandomSupport.mixStafford13`
segment selection, power-of-two backend count check, per-segment section
operations, Mapper-id replication to every fragment, majority recovery if
fragment mappings disagree, child iteration, flush, and close order.
`BasicPathConfig` and `AutoFragmentationAdaptor` push/pop the same
`ConfigBuildCtx` path components as original.

The audit also closed three misleading inventory entries without inventing
working substitutes:

```text
LZMA2:
  the entire original LZMACompressor implementation is inside a block comment;
  no class or TYPE is emitted by the original build, so Forge does not register it

ConditionalConfig:
  original Serialization registers it, but build() unconditionally throws
  NotImplementedException; Forge registers and preserves that exact behavior

ReadonlyCachingLayer:
  at the historical XXI.3 checkpoint Forge preserved the original unimplemented
  iteratePositions() and flush()-closes-children behavior; Forxy 叁轮 later adds
  deduplicated enumeration, mapping replication, correct flush, and lifecycle
  safety without making it the default production backend
```

Runtime validation used a backed-up config and an isolated
`storage/xxi3/fragment_0..3` path, leaving the default ZSTD database untouched:

```text
first JVM, explicit FragmentationAdaptor with four BasicPathConfig children:
  backendChain=Serializer->LZ4->Fragmentation(count=4)
  sectionWrites=30498
  mappingWrites=384
  all four RocksDB fragments created (about 43-52 MiB each)
  no LZ4/native/RocksDB/mapping-consistency/shutdown failure

second JVM, AutoFragmentationAdaptor targeting the same four paths:
  backendChain=Serializer->LZ4->AutoFragmentation(basePath=xxi3/fragment,count=4)->RocksDB
  mappingEntriesLoaded=384
  sectionLoadHits=4925
  sectionLoadMisses=3603
  sectionWrites=354
  no LZ4/native/RocksDB/mapping-consistency/shutdown failure
```

This proves explicit and automatic fragmentation use the same original hash/path
contract and that LZ4 data survives a real JVM restart. After validation the
pre-test `config.json` was restored and verified byte-for-byte by SHA-256. The
isolated test database remains under ignored `run/` only. Whole-mod parity stays
false for LMDB, Redis, and the remaining non-storage migration inventory;
renderer readiness is unchanged.

### XXI.4 original LMDB backend and restart recovery

XXI.4 ports original `LMDBStorageBackend` without replacing its transaction or
database model. The platform-neutral original support classes are compiled
directly from the reference tree:

```text
LMDBInterface
Cursor
TransactionCallback
TransactionWrappedCallback
TransactionWrapper
```

The Forge namespace backend preserves the original mechanics:

```text
environment flags = 0
max named databases = 2
initial map size = 1 << 25 bytes
named databases = world_sections, id_mapping
section/id keys use the original native ByteBuffer integer encoding
MDB_MAP_FULL code -30792 triggers the original access-count/resize-lock loop
map growth step = 1 << 25 bytes
flush = forced mdb_env_sync
close = section DB, mapping DB, environment
```

At the historical XXI.4 checkpoint `iteratePositions()` remained the original
unconditional `Not yet implemented` exception. Forxy 叁轮 later closes that
technical debt with read-only cursor iteration, exact level filtering, callback
exception propagation, and lifecycle-safe close; the stored LMDB format and
validated load/save route are unchanged.

ForgeGradle packages `org.lwjgl:lwjgl-lmdb:3.3.1` through Jar-in-Jar and embeds
the official Windows/Linux x64 native resources at their original LWJGL paths,
matching the classifier handling already used for ZSTD.

Runtime validation used `BasicPathConfig("xxi4-lmdb")` so the optional backend
could not touch the default RocksDB database:

```text
first JVM:
  backendChain=Serializer->ZSTD(level=1)->BasicPath(xxi4-lmdb)->LMDB
  data.mdb created at 33,554,432 bytes; lock.mdb created at 8,192 bytes
  sectionWrites=20786
  mappingWrites=386
  normal LMDB/WorldEngine/render/client shutdown, runClient exit 0

second JVM:
  identical backend chain and LMDB path
  mappingEntriesLoaded=386
  sectionLoadHits=3037
  sectionLoadMisses=2900
  sectionWrites=1012
  no LMDB error code, native load, duplicate mapping, or shutdown failure
  runClient exit 0
```

The pre-test default ZSTD/RocksDB `config.json` was restored and verified
byte-for-byte by SHA-256. LMDB test data remains only under ignored `run/`.
Whole-mod parity remains false for Redis and the remaining non-storage migration
inventory; renderer readiness is unchanged.

### XXI.5 original Redis backend and storage TYPE inventory closure

XXI.5 ports original `RedisStorageBackend` and registers the exact `Redis` TYPE.
The Forge namespace implementation preserves the original contract:

```text
JedisPool(host, port)
hash key = substituted prefix + world_sections
hash key = substituted prefix + id_mappings
section fields = big-endian 8-byte keys
mapping fields = big-endian 4-byte keys
section/mapping values = exact byte arrays copied to/from native buffers
optional per-resource AUTH path retained by the backend constructors
flush = no-op
close = JedisPool.close
```

The original JSON Config exposes only `host`, `port`, and `prefix`; it does not
expose the backend constructor's optional user/password fields, and Forge keeps
that exact surface. At the historical XXI.5 checkpoint `iteratePositions()`
still matched the upstream `Not yet implemented` behavior. Forxy 叁轮 later
adds bounded binary `HSCAN`, exact level filtering, duplicate suppression, and
lifecycle-safe close without changing the Redis hash/key format.

ForgeGradle packages the same original dependencies:

```text
redis.clients:jedis:5.1.0
org.apache.commons:commons-pool2:2.12.0
```

Runtime validation used Redis 7.0.15 unpacked into ignored `run/redis-test`
without installing packages into WSL. The temporary service listened only on
`127.0.0.1:16379`; Windows connectivity and Redis `PING` were verified before
Minecraft started. The config prefix
`xxi5:{world_identifier}:` also exercised original `ConfigBuildCtx` token
substitution.

```text
first JVM:
  backendChain=Serializer->ZSTD(level=1)->Redis(127.0.0.1:16379, isolated prefix)
  sectionWrites=24183
  mappingWrites=382
  Redis HLEN after exit: world_sections=4432, id_mappings=382
  no Jedis connection/pool/class-loading or shutdown failure

second JVM, same Redis service and prefix:
  mappingEntriesLoaded=382
  sectionLoadHits=2814
  sectionLoadMisses=2852
  sectionWrites=1238
  final Redis HLEN: world_sections=4432, id_mappings=384
  normal WorldEngine/render/client shutdown, runClient exit 0
```

The default ZSTD/RocksDB config was restored byte-for-byte by SHA-256, and the
temporary Redis process exited through `SHUTDOWN NOSAVE`. Together XXI.1-XXI.5
now cover every storage/compressor/config TYPE actually emitted by the original
build. `LZMA2` is not emitted because its entire source implementation is
commented out. `ConditionalConfig` retains its documented upstream-incomplete
behavior; the historical ReadonlyCachingLayer gaps are closed by Forxy 叁轮 as
recorded above. Whole-mod parity remains false for the remaining non-storage
inventory and targeted regressions; renderer readiness is unchanged.

### XXI.6 original active-world ownership and identifier-routed ingest

XXI.6 audits the Forge singleton's world lifecycle against original
`VoxyInstance.activeWorlds`. Original ground truth is:

```text
owner = HashMap<WorldIdentifier, WorldEngine> guarded by StampedLock
getNullable(identifier) = weak-cache/map lookup, instance validation, markActive
getOrCreate(identifier) = identity-specific reuse or creation under write lock
cleanIdle() = rechecked removal/free after WorldEngine's exact 10-second idle test
shutdown() = stop ingest/save, wait in 10 ms steps for every used world, free all
MixinClientLevel#setBlocksDirty = derive WorldIdentifier from that ClientLevel
```

The pre-XXI.6 Forge adapter instead held one `activeWorld` pointer and an
identity-tagged closing deque. That preserved delayed idle close and rapid
logout/login reuse, but it was not equivalent to the original map. Five concrete
divergences were confirmed before editing:

```text
1. VoxelIngestService auto target ignored its LevelChunk and returned activeWorld.
2. ForgeChunkIngestManager held a ClientLevel/LevelChunk but returned activeWorld.
3. ClientLevel#setBlocksDirty and Embeddium section rebuild held their owner level
   but returned activeWorld.
4. END-tick called ensure before comparing dimensions; ensure accepted any live
   pointer, so a new-dimension chunk could be submitted to the old WorldEngine.
5. terminal shutdown skipped worlds with live refs instead of waiting/freeing all.
```

This was a real ownership race, not a theoretical container preference: during a
dimension transition Forge can keep callbacks from two `ClientLevel` instances
alive concurrently. A global pointer makes their late/early events
indistinguishable, while the original `WorldIdentifier` route cannot cross-write.

XXI.6 replaces the deque/pointer owner with an identity-keyed live-world map. The
Forge renderer still selects one identity because its model-pipeline adapter is a
single lifecycle owner, but all inputs that possess a concrete level now call
`getEngineForLevel(level)`. `identityForLevel` uses the exact identifier captured
from that `ClientLevel` plus the connection/save base path. The route reuses or
creates that identity's owner and marks it active, matching original
`tryIngestChunk`/`rawIngest` through `VoxyInstance.getOrCreate`; it never falls
back to or prematurely selects the renderer world.

The lifecycle adaptation now:

```text
END tick: detect dimension change -> clear/markDimensionSwitch -> detach selection
          -> select/create by current identifier -> model pipeline tick
early render callback with a different identity: do not retarget the single
          pipeline before markDimensionSwitch has run
idle cleanup: recheck WorldEngine.isWorldIdle(), free and remove from the map
shutdown: stop ingest/save, wait in 10 ms steps until every world is unused,
          free every owner, clear the map, then stop original service threads
```

Storage counters moved into each map entry. Consequently, an old dimension still
finishing saves cannot pollute the current dimension's storage status. The
existing `closingWorldCount` command field is retained for compatibility and now
reports live map entries other than the selected renderer world.

Initial static validation:

```text
gradlew compileJava: BUILD SUCCESSFUL
git diff --check: clean
```

Runtime validation exercised the full owner lifecycle with Complementary Unbound
active:

```text
overworld owner created at .../74d2036cf9ebdb83178e6f984bd8904e/storage
overworld -> nether: old render owner shut down before the nether owner opened
nether owner created at .../ecbd3f9d84cf72a2a82c7569cd0da279/storage
overworld owner passed the exact 10-second idle threshold and was freed
nether -> overworld: nether render owner shut down before overworld opened
overworld owner recreated; nether owner then passed the idle threshold and freed
logout -> rapid login: live overworld owner reused, not opened a second time
final shutdown: remaining overworld owner freed; instance shutdown complete
runClient: BUILD SUCCESSFUL, exit 0
```

Targeted log screening found no Voxy error/exception, dead-world access,
negative reference count, skipped world free, missing active engine, duplicate
storage open, or shutdown stall. The shaderpack emitted its pre-existing Oculus
unknown-uniform/block-map warnings, which are outside active-world ownership and
did not interrupt any transition.

The follow-up visual pass found one to three transient 16x16 black rectangles at
the vanilla/LOD boundary on fresh entry or after returning from the Nether.
Approaching one rectangle made it disappear after the vanilla boundary chunk
finished building. Two F3 screenshots from the same camera position were fitted
against the 16-block grid, yielding target cell `x=112..127, z=-112..-97`.
The live target audits at `block=[120,47,-104]` established:

```text
client chunk loaded = true
client block = void_air
WorldEngine block = gravel (mapping 0x48000000)
modelId = 4, correct gravel bake/customId/atlas alpha
HOC selected exactly one L0 node; tree/GPU/metadata consistency clean
MDIC emitted the section and found one covering UP quad, modelId 4
patched opaque/translucent programs used; glError=0
```

At `block=[120,40,-104]`, the client likewise held `void_air` while WorldEngine
held `dirt`. Thus the visual rectangle was real stale opaque LOD terrain, not a
random transparent texture, zero light, missing geometry, wrong model upload,
HOC selection, or cmdgen failure. The gated lighting probe showed its small
initial translucent sample at sky light 14-15, but that sample did not cover
the later exact black water cells and therefore did not globally exclude a
lighting race.
Moving close and back hid the rectangle because the vanilla boundary section
then covered it; a second audit proved WorldEngine still contained gravel.

The stale data survived correct chunk re-ingest because Forge's XI-era
`shouldIngestLoadedChunkSection` skipped an all-air section with no explicit
light layer. Original `VoxelIngestService.enqueueIngest` does the opposite: it
queues every section and its worker calls `WorldUpdater.insertUpdate(...,
vs.zero())` for all-air/no-light input, explicitly clearing old data. XXI.6
restores that contract by allowing all non-null loaded sections through; the
existing `convertSection` zero fast path performs the original clearing write.

The documented `-PvoxyAuditLighting` probe was also found missing from the
ForgeGradle run-property bridge. XXI.6 wires it to
`voxy.forge.auditLighting=true`; this changes diagnostics only.

The first post-fix fresh-entry regression confirmed that the restored zero
clears are active (`enqueuedSections=4056`, `storageWrites=1131`, versus the
smaller pre-fix section set), but black 16x16 rectangles still appeared. A
second fixed-camera screenshot pair resolved the first grid fit: the left-hand
two-cell rectangle was `block=[104,62,-88]` plus `[104,62,-72]`, not the earlier
approximation at `z=-136`. Live audits while both cells were still black found:

```text
black -88: client/world water modelId=21, worldMapping=0x0000000020000000
           covering translucent UP quad raw=0x00000000541e07f9
black -72: client/world water modelId=21, worldMapping=0x0000000020000000
           covering translucent UP quad raw=0x00000000541e87f9
normal -56: same water/model/biome, worldMapping=0x0e00000020000000
            covering translucent UP quad raw=0x07800000541e07f9
all three: L0 selected, valid render-list/MDIC command, covering 16x16 quad
node audit: gpuRealMismatch=0, no duplicate/stale/out-of-range mesh or request
```

The high mapping/quad bits are the voxel light payload. The black cells had sky
light 0 while the adjacent visually normal water had sky light 14. This is the
exact visual root cause: correct water geometry and texture were drawn, but
Forge captured two chunk light layers before their sky data was populated.

Original `VoxelIngestService.enqueueIngest` gates chunk ingestion with
`getDebugSectionType(...)=LIGHT_AND_DATA`. The Forge adaptation had replaced
that readiness contract with `DataLayer != null`; Forge can expose a non-null
but still-empty sky `DataLayer` while client chunk/light packets are being
applied, so those chunks passed, baked sky 0, and were permanently entered in
`ingestedChunks`. The Forge `ClientLevel#setBlocksDirty` adapter also called
`rawIngest` directly and could bypass the newer deferred-light checks.

XXI.6 restores the original storage-state signal for every non-air section in a
sky-lit dimension: ingestion defers until SKY reports `LIGHT_AND_DATA`, whether
the temporary layer object is null or merely empty. The border-removal adapter
now calls the same `ingestChunkSection` route as the Embeddium callback, so it
cannot overwrite a section through a zero-light bypass. All-air zero clears and
implicit uniform sky handling remain intact. Static validation after this fix:

```text
gradlew compileJava: BUILD SUCCESSFUL
git diff --check: clean
```

Post-fix runtime regression passed. A fresh client entry at the same fixed
camera produced no black rectangles, and two complete overworld/nether return
cycles remained visually clean at the former cells and the vanilla/LOD
boundary. The earlier XXI.6 lifecycle pass had already exercised same-client
logout/login reuse; that ownership code was unchanged by the lighting fix. The
final client exit cleanly shut down the render pipeline and freed both remaining
overworld/nether owners. Log screening found no Voxy exception, dead-world
access, reference-count failure, duplicate storage open, or shutdown stall.
Oculus retained only its pre-existing shaderpack unknown-variable/block-map
warnings.

Final review found no remaining actionable defect in the XXI.6 diff. The
identity-routed map follows original `VoxyInstance` ownership and shutdown
semantics; every callback that owns a `ClientLevel` now routes by that level;
the lighting fix uses the original storage readiness signal and does not add a
water/model/shader special case. Final `compileJava` passed and
`git diff --check` was clean. XXI.6 is runtime-validated and ready to commit
when requested.

Whole-mod parity and every renderer readiness flag remain unchanged.

## XXII-XXIV whole-mod completion plan

After XXI.6, visible renderer parity and its storage/world lifecycle are no
longer the open work. The remaining work is grouped into three large rounds,
each followed by one consolidated user regression and one commit:

```text
XXII  core non-renderer parity closure
      - reconcile the stale unported inventory
      - original/Embeddium chunk-remove snapshot and ingest/light ownership
      - original config semantics and clean-install defaults
      - retire prototype config/runtime surfaces and rename ForgeCpuMeshLayer
      - multiplayer identity and corrupt-storage recovery validation

XXIII original user features
      - ImportManager plus world/raw/ZIP/current/cancel flows and progress UI
      - reload/applicable debug/F3/config UI entry points
      - real Forge-equivalent optional integrations or explicit platform-N/A

XXIV  final whole-mod audit and release regression
      - source-area classification, documentation/status cleanup, JAR audit
      - one complete visual/lifecycle/storage/importer/multiplayer regression
      - user-approved wholeOriginalModParity flip and final build/status check
```

XXII deliberately batches all remaining core-lifecycle/config/storage cleanup
before asking for another manual run. Internal compile/build checkpoints remain
mandatory, but historical preview paths and probes cannot count as completion.

## XXII core non-renderer parity closure

Status: implementation, automated validation, and the consolidated user runtime
regression are complete without an observed issue. XXII is ready for review and
commit when explicitly requested; `wholeOriginalModParity` remains false because
XXIII/XXIV work is still outstanding.

### Original source comparison and inventory reconciliation

XXII re-read the original `VoxyConfig`, `VoxyClientInstance`,
`MixinClientChunkCache`, `MixinRenderSectionManager`, `ICheekyClientChunkCache`,
`SectionSerializationStorage`, `SaveLoadSystem3`, and the consumers of each
Forge adapter before changing code. The twelve-section unported-content
inventory now has an authoritative current table: renderer sections 2-9 are
complete, core instance/storage section 1 is complete, ingest section 10 and
core config portion of section 12 close in this round, while user features and
optional integrations remain explicitly assigned to XXIII/XXIV.

### Last-loaded chunk snapshot and ingest ownership

Original Voxy does not use the normal range-checked chunk lookup during
`RenderSectionManager.onChunkRemoved`. It reads the current
`ClientChunkCache.Storage` slot directly, then verifies the returned chunk's X/Z
coordinates before ingesting the final snapshot. Forge previously called
ordinary `getChunk(..., false)`, which can return null at exactly this removal
boundary.

The Forge 1.20.1 port now exposes the package-private inner `Storage` class and
its original `getIndex`/`getChunk` methods with an Access Transformer. The
transformer entries use the 1.20.1 SRG member names (`m_104481_` and
`m_104479_`), while the Mojmap development source calls the corresponding
methods directly. `onChunkRemoved` uses that exact slot lookup and coordinate
verification. Chunk add, chunk remove, Embeddium section-info
updates, the ClientLevel dirty-section repair, the tick rediscovery adapter, and
the global auto-ingest target all honor the same `enabled && ingestEnabled`
contract. The XXI.6 `LIGHT_AND_DATA` deferred-light retry and level-routed
active-world ownership remain unchanged.

### Original config semantics and clean-install defaults

The Forge config now carries the original behavioral fields and defaults:

```text
enabled=true
enableRendering=true
ingestEnabled=true
service/render-distance/subdivision/environmental-fog settings retained
ssaoMode=AUTO with BASIC/BETTER/BEST and invalid/null -> AUTO parsing
```

`enableRendering=false` tears down only the live render owner and blocks Oculus
patch exposure/draw/start while leaving the WorldEngine available for ingest,
matching the original separation between rendering and ingest. The two
historical defaults `enableWorldEngineSkeleton=false` and
`enableAutoChunkIngest=false` no longer gate the formal route, so a clean config
starts the production owner and ingest path without hand edits. The three
radius/budget/cooldown values remain as documented Forge/Embeddium rediscovery
adapter controls because 1.20.1 lifecycle ordering can expose chunks before the
original-shaped callbacks observe them.

### Prototype surface cleanup and status truthfulness

The unused CPU mesh, BuiltSection, geometry-GPU, and MDIC prototype config keys
and the now-empty `ForgeVoxyRuntimeOverrides` facade are removed. The active
model-bakery enum was not deleted; it is renamed from `ForgeCpuMeshLayer` to
`ForgeOriginalVoxyModelLayer`, with mechanical call-site changes only.

At that historical XXII checkpoint, `/voxy parity_route_status` reported
`wholeOriginalModParity=false`, advances `newWorkTarget` to
`original-user-features-and-importers`, and no longer claims that every legacy
route is physically absent. It instead states that the retained legacy adapter
does not drive the visible renderer. Live renderer readiness predicates are
unchanged.

### Isolated storage regression coverage

JUnit coverage was added outside the shipped runtime route:

1. identical worlds under different server base paths, and different
   dimension/seed identities under one server base, resolve to distinct storage
   paths;
2. a valid serialized section stored under a mismatched key causes
   `SectionSerializationStorage` to return `-1`, delete the bad entry, and fill
   the target section with `Mapper.AIR`, exactly matching original recovery;
3. clean config defaults enable the formal route/render/ingest fields and SSAO
   parsing matches the original fallback contract.

This does not mutate any real `run/saves` or `.voxy` database.

### First runtime attempt: Mixin-package class-load failure and correction

The first anchored XXII `runClient` reached integrated-server player login, then
failed with `IllegalClassLoadError`: the ordinary
then-named `ForgeOriginalVoxyClientChunkCacheAccess` bridge had been placed under the
configured `me.cortex.voxy.forge.mixin.*` package. Mixin owns that package and
rejects direct loading of non-Mixin helper types from transformed production
classes. The failure therefore occurred before the new removal lookup could be
exercised; it was not a storage, shaderpack, world-data, or rendering failure.

The correction keeps the bridge in `me.cortex.voxy.forge`, outside the Mixin
package, and removes the intermediate Storage invoker Mixin entirely. The Access
Transformer now opens the same two members that original Voxy exposes through
its access widener, so the merged `ClientChunkCache` method has no runtime
reference to a second Mixin type. The failed attempt and correction remain part
of uncommitted XXII; a fresh anchored runtime gate was still required. XXV later
aligned the final bridge contract with original
`ICheekyClientChunkCache.voxy$cheekyGetChunk(...)`.

### Automated validation

```text
gradlew test: BUILD SUCCESSFUL, 4 tests
gradlew build: BUILD SUCCESSFUL, 11 actionable tasks
git diff --check: clean
final JAR contains META-INF/accesstransformer.cfg,
  ForgeOriginalVoxyClientChunkCache{Access,Mixin}, and
  ForgeOriginalVoxyModelLayer; removed runtime/model-layer class names absent
```

### Consolidated user regression gate

The completed gate covered existing-config schema upgrade, fresh/default route
evidence, movement across the vanilla/LOD boundary, block removal/rediscovery,
Overworld/Nether return, F3+T, logout/login, no-shader plus
BSL/Complementary/Photon (including water), storage reuse, and a multiplayer
server identity. The observed runs shut down normally without a reported Mixin,
Access Transformer, dead-world, storage, GL, Voxy, or visual failure.

User regression result: all listed client/integrated-server checks passed
without an observed issue. For the multiplayer check, a local-only official
Minecraft 1.20.1 server was created under
ignored `run/local-server-1.20.1`, bound to `127.0.0.1:25565`, and configured for
the development client's offline `Dev` identity. The first infrastructure
attempt through Gradle `runServer` was rejected before world startup because the
shared development runtime includes the client-only Oculus jar, which loads a
client `Screen` on `DEDICATED_SERVER`; this is not a Voxy client multiplayer or
storage failure. The isolated vanilla server reached `Done`, its port probe
passed, the client joined twice and completed the same regression flow without
an observed issue, and both client and server then shut down normally.

The client log independently confirms the remote identity contract: it selected
`run/.voxy/saves/127.0.0.1_25565`, created distinct Overworld and Nether
identity-hash directories for server seed `-4672863472195697072`, reused the
same Overworld engine/path after reconnect, closed idle dimension engines, and
completed final instance shutdown. The server log records both join/leave pairs,
then `stop` saved all three dimensions; a post-stop port probe returned false.

### Final XXII review

The post-regression review re-compared the changed config, lifecycle, SSAO,
chunk-removal, and model-layer paths against original source and inspected every
runtime diff plus the new tests and Access Transformer entries. No actionable
runtime defect was found. The review confirmed that the model-layer rename is
mechanical, removed prototype keys have no live source references, the removal
snapshot uses the original slot lookup plus coordinate check, and all enabled /
rendering / ingest gates preserve the original separation of ownership.

One documentation issue was corrected: the June 22/23 debug-cleanup and full
defect-audit files still described themselves as active/live even though their
in-memory-storage and staged-switch findings are historical. They now point to
this audit as canonical current status, and the XI investigation header now
acknowledges the later regression closure. No code change was required by the
review.

## XXIII original user features

Status: implementation and the consolidated user runtime regression are
complete. The original importer, user-command, F3, reload, and configuration
owners have been mapped, compiled, packaged, and exercised in game. The sole
regression found during that pass, no-shader black cherry leaves, was repaired
and passed its focused visual recheck. No whole-mod parity flag is flipped by
this work; that remains the user-approved XXIV decision.

### Import manager, world importer, and client progress owner

XXIII first re-read original `ImportManager`, `IDataImporter`, `WorldImporter`,
`ClientImportManager`, `VoxyCommands`, and the import/shutdown portions of
`VoxyInstance`. The platform-free original `ImportManager` and `IDataImporter`
now compile directly. Forge owns API adapters only where 1.20.1 differs:

```text
ForgeOriginalVoxyWorldImporter
 -> original MCA sector table and RegionFileVersion decompression
 -> original weighted ServiceManager service (weight 3)
 -> original 10,000 queued-chunk pressure bound
 -> 1.20.1 PalettedContainer/NBT decoding adapter
 -> WorldConversionFactory / WorldVoxilizedSectionMipper / WorldUpdater

ForgeOriginalVoxyClientImportManager
 -> original 50 ms progress throttle
 -> LerpingBossEvent progress/name updates
 -> completion chat message and chunks-per-second result
```

`/voxy import current`, `world`, `raw`, `zip`, and `cancel` now follow the
original path selection and single-import-per-WorldEngine ownership. Importers
acquire a WorldEngine reference while running. Instance shutdown cancels every
active importer before ingest/saving service and shared service-pool shutdown,
matching original `VoxyInstance.shutdown()` ordering. The Forge Access
Transformer opens `BossHealthOverlay.events`, the 1.20.1 equivalent of the
field original `ClientImportManager` updates directly.

### Reload, F3, and Forge configuration entry point

`/voxy reload` now clears the active chunk/model reload adapters, tears down the
formal render owner through the existing world-unload lifecycle, reselects the
current persistent WorldEngine, reapplies the original service-thread policy,
and asks `LevelRenderer` to rebuild. This is the Forge singleton-lifecycle
adapter for original Voxy's instance recreation; it does not construct a second
renderer or substitute a preview path.

Forge's `CustomizeGuiOverlayEvent.DebugText` supplies the 1.20.1 F3 extension
point. It shows the original instance diagnostics (`MemoryBuffer` count and
size, ingest/saving queue counts, active-world section counts) plus formal
renderer/draw/lifecycle state. The newer original `DebugScreenEntryList` API is
not available in Minecraft 1.20.1. The initial XXIII adapter omitted the
original visibility boundary and therefore also rendered this text while F3 was
closed. XXV corrects that migration drift by gating the event handler on
`Minecraft.options.renderDebug` before collecting any Voxy diagnostics.

XXIII initially exposed the nine original controls through a standalone Forge
Mod List screen. That preserved most setting values but did not preserve the
original frontend ownership: original Voxy contributes pages to Sodium's video
options. The XXV correction retires that standalone screen. Voxy now registers
two pages (`voxy:general_page` and `voxy:rendering_page`) through Embeddium's official
`OptionGUIConstructionEvent`, while the Forge Mod List factory opens the same
Embeddium options host. The pages carry all nine current original settings:
enabled, service threads, Embeddium/Sodium builder-thread sharing, ingest,
rendering, subdivision size, LoD render distance, environmental fog, and SSAO
mode. One shared `OptionStorage` owns both pages and updates its baseline after
every save, so Embeddium's repeated Apply lifecycle cannot replay stale changes.

Config effects are also aligned to original ownership. Service-thread changes
resize the original shared service pool; distance updates the live
`RenderDistanceTracker` when no rebuild supersedes it; rendering, builder-thread,
fog, and SSAO changes rebuild only the renderer and retain the active
`WorldEngine`. At the historical XXV checkpoint, enabled changes still used a
process-owned service pool. XXVI.1 superseded that adapter with per-network-
session ownership and original-equivalent full replacement. The LoD-distance slider/config
minimum is restored to original input 10
(`10/16` sections internally), rather than the former Forge minimum of one
section. Original language/icon resources remain packaged for the frontend.

### Optional integration classification

The original optional integrations were compared to the active Forge 1.20.1
dependency and Mixin surfaces:

| Integration | Original behavior | Forge 1.20.1 classification |
| --- | --- | --- |
| Bobby | imports `.bobby` region cache and changes Sodium unload-ingest timing | Bobby Reforged is the real Forge 1.20.1 owner and retains `modId="bobby"`; XXV ports both the cache importer and the original split unload hooks through Minecraft/Embeddium |
| Distant Horizons | optional direct SQLite/XZ/ZSTD importer | XXV ports the optional direct DH database importer and supported decoders/compressors; XXXVI qualifies the two-stage workflow against real DH 3.2.0-b data because that DH release explicitly rejects a simultaneous Voxy installation |
| Flashback | records/replays Voxy storage paths through Flashback metadata and Fabric mixins | Fabric-only Flashback classes are absent; platform-N/A |
| FREX flawless frames | repeats GPU traversal until queued work drains during a Fabric entrypoint callback | Fabric rendering entrypoint is absent; explicit platform-N/A false state replaces the stale reflection to excluded Fabric source |
| Nvidium / Acedium | injects Voxy draw after Nvidium replaces Sodium's terrain pipeline | Acedium is the Forge fork of Nvidium, retains the `me.cortex.nvidium.RenderPipeline` class, and publishes both `acedium` plus compatibility `nvidium` mod entries; the guarded Voxy Mixin selects the exact Acedium owner |
| Chunky | ingests chunks generated by the integrated server | XXV ports the real Forge callback with active-client identity/dimension guards |
| GPU selection | prefers the selected high-performance adapter and worker priority | XXV ports the original Windows selector/priority behavior through a safe optional mixin |
| ModMenu | opens Voxy's Sodium config page | Fabric ModMenu is platform-N/A; Forge `ConfigScreenHandler.ConfigScreenFactory` opens Embeddium, whose official construction event receives the two Voxy option pages. Embeddium has no public initial-page selector, so the Voxy page is user-selectable rather than forcibly preselected. |

Embeddium and Oculus integrations remain the real Forge equivalents already
ported and regression-tested by earlier rounds. Optional platform-N/A findings
do not block the formal renderer or XXIII completion.

### No-shader cherry leaves tint parity repair

The consolidated regression found one block-specific failure: with shaderpacks
disabled, LOD cherry leaves rendered pure black, while the shaderpack path and
all other tested XXIII behavior remained correct. The shader toggle log showed
the expected Oculus pipeline transition and Voxy render-owner rebuild without a
lifecycle or GL failure, so this was traced through the original and Forge model
tint contracts rather than treated as a reload issue.

Minecraft 1.20.1 `cherry_leaves.json` inherits `leaves.json`, whose faces carry
`tintindex: 0`, but `BlockColors.createDefault()` deliberately registers no
`BlockColor` for `CHERRY_LEAVES`. Original Voxy asks
`BlockColors.getTintSources(state)` and therefore classifies this model as
untinted. The Forge adapter instead treated the baked-quad tint index alone as
proof that a tint source existed. Its later `BlockColors.getColor(...)` lookup
correctly returned the canonical `-1` no-tint value, but the model remained
marked tinted. In the unpatched shader, `quad_util.glsl` converts `-1` to the
zero `conditionalTinting` sentinel and `quads.frag` multiplies tinted faces by
that zero, producing black. The patched shaderpack contract passes the raw
`-1`, explaining why the defect was specific to shaderpacks being disabled.

`ForgeOriginalVoxyModelFactory.createTintPlan()` now accepts a baked-quad tint
index only when the 1.20.1 `BlockColors` lookup supplies a real constant colour
or invokes the biome resolver. A non-biome `-1` result is classified as
untinted, matching original Voxy's empty tint-source behavior. This also keeps
white/no-op tint callbacks visually equivalent while avoiding a Forge-only
model metadata state. The repair changes neither the atlas texture nor the
lighting payload.

Validation checkpoint: `gradlew compileJava` passed, and the focused runtime
recheck confirmed that LOD cherry leaves retain their normal pink texture with
shaderpacks disabled. Re-enabling the shaderpack and normal client shutdown also
completed without a Voxy lifecycle or resource-cleanup failure.

### Automated checkpoint before runtime regression

```text
compileJava: passed after importer, reload/F3, and config-screen integration
gradlew test build: BUILD SUCCESSFUL, 11 actionable tasks
git diff --check: clean
final JAR: importer/config classes, ImportManager/IDataImporter, Access Transformer,
  icon, and language resources present
wholeOriginalModParity: false
XXIII consolidated runtime gate: passed, including no-shader cherry leaves repair
```

### Final XXIII review

The post-regression review re-read every changed importer, command, client UI,
reload, F3, optional-integration, and shutdown path against the original source.
`ImportManager` and `IDataImporter` remain the original implementations;
`ForgeOriginalVoxyWorldImporter` changes only the Minecraft 1.20.1 codec,
registry, and ZIP-construction APIs while retaining the original worker,
weighted service, pressure bound, completion, and WorldEngine reference rules.
The command syntax/path selection, boss-bar progress owner, service-rate limiter,
and instance shutdown order likewise match their original owners.

The review also checked the Forge-specific entry points: client-only command and
config registration, the Access Transformer field, live render-distance/thread
updates, renderer reload triggers, and 1.20.1 F3 event. The packaged language and
icon resources are reachable through the restricted source set. The FREX change
removes a stale reflection into excluded Fabric source and records the genuine
Forge platform-N/A result instead of inventing an integration. No actionable
code defect remained. One status defect was corrected before commit:
At that historical XXIII checkpoint, `parity_route_status.newWorkTarget`
pointed to the XXIV final whole-mod audit
and release regression rather than the completed XXIII user-feature round.

## XXIV final whole-mod audit and release regression

### Final original-source area classification

The twelve-area inventory was reconciled once more against the original source,
the compiled Forge source set, and the completed XXI-XXIII owners. Every area is
now classified as directly ported, Forge-adapted through a real platform owner,
platform-N/A, or upstream-incomplete. No genuinely missing original source area
was found. The authoritative matrix is recorded in
`forge-1.20.1-original-voxy-unported-content-migration-reference-2026-06-23.md`.

The two upstream-incomplete findings do not create a Forge substitute route:
the original HOC/request bookkeeping retains its audited author `FIXTHIS`
behavior, and IterationT has no upstream Voxy sidecar/adaptation. The latter
remains a post-parity compatibility TODO.

### Final deprecated-route and status cleanup

CodeGraph reported no external caller for `ForgeVoxyModelIdMapper`; it supplied
no ID to ingest, the original model factory/store, geometry, or visible MDIC
rendering. XXIV therefore physically removes it. The same pass removes the
zero-reference `shouldSuppressDeprecatedVisibleRoutes()` helper, the constant
`deprecated*RouteAbsent` snapshot fields, their command output, and the unused
atlas `SKELETON`/known-layout/format labels. None of these values participated in
a readiness predicate or renderer data contract.

At that historical XXIV checkpoint, `/voxy parity_route_status` reported
`deprecatedPrototypeRoutesAbsent=true`. The startup log no longer says renderer
readiness is pending after its XX.7 approval. After the passed final regression
and explicit user approval, it now truthfully reports whole-mod parity complete.
Active docs distinguish current status from the retained historical investigation
trail.

### Mixin, dependency, clean-config, and final JAR audit

Automated and artifact inspection passed:

```text
gradlew compileJava: passed
gradlew test build: passed (11 tasks)
clean-config defaults test: enabled/rendering/ingest true; SSAO AUTO
registered Forge mixins: 17
missing registered mixin classes in all JAR: 0
Embeddium target classes checked: 4, missing: 0
Oculus target classes checked: 9, missing: 0
development frontends: Embeddium 0.3.32 and Oculus 1.8.0
declared bounds: Minecraft [1.20.1,1.20.2), Forge [47.3.0,48),
  Embeddium [0.3.31,0.4), Oculus [1.8.0,1.9)
Jar-in-Jar metadata: zstd/lmdb 3.3.1, RocksDB JNI 10.2.1,
  LZ4 1.8.0, Jedis 5.1.0, commons-pool2 2.12.0
required Windows/Linux zstd+lmdb native libraries: 4, missing: 0
required manifest/AT/mixin/icon/lang/shader/importer/renderer entries: 11,
  missing: 0
forbidden Fabric descriptors/access widener/common-client mixin configs and
  deleted legacy mapper: 5, unexpectedly present: 0
final all JAR: voxy-forge-0.2.17-beta-forge-poc-all.jar, 75,583,616 bytes
```

The mixin set resolves to three Minecraft owners, four Embeddium owners, and
nine distinct Oculus owners; the two Oculus pipeline mixins intentionally share
`IrisRenderingPipeline`. Compilation verifies hook signatures, and the passed
final runtime regression supplies the load-time qualification for the exact
frontend versions above.

### Consolidated final user regression gate

`wholeOriginalModParity` is true after explicit user approval. The XXIV runtime
gate is complete:

```text
[x] no shaderpack
[x] multiple shaderpacks and shader toggle
[x] overworld <-> nether dimension switch
[x] standalone F3+T reload
[x] logout/login
[x] multiplayer join and identity isolation
[x] persistent-storage restart recovery
[x] importer start/progress/cancel or completion
[x] normal client and local-server shutdown
[x] /voxy parity_route_status live renderer fields true and
    deprecatedPrototypeRoutesAbsent=true
```

The user completed the consolidated client regression without a visual or
functional defect. The log proves repeated shaderpack and owner rebuilds,
overworld/nether switching, resource reload, logout/relogin, reuse of the
persistent singleplayer engine, all three live renderer fields true, and
`deprecatedPrototypeRoutesAbsent=true`. The client shut down the renderer,
persistent world, Voxy instance, and Minecraft normally; `runClient` exited 0.

The multiplayer follow-up connected the Forge client to the isolated vanilla
1.20.1 server at `127.0.0.1:25565`, created the server-specific persistent
identity path, switched overworld/nether and back while reusing the overworld
engine, completed F3+T, and exited normally. The server recorded the `Dev` join,
command, disconnect, then accepted `stop`, saved all three dimensions, and exited
0. Port 25565 no longer has a listener. The development `runServer` task itself
is not a valid dedicated-server fixture because it puts the client-only Oculus
development JAR on the server runtime and Oculus attempts to load `Screen`; the
isolated server avoids that external frontend defect and tests the actual Voxy
client-to-server contract.

Oculus emitted pack-authored custom-uniform/block-map warnings during shaderpack
reloads, including a missing BSL `endFlashIntensity`. The Voxy owner reported the
missing input, rebuilt successfully, and the user observed no visual defect;
there was no Voxy lifecycle, ingest, storage, Mixin, GL, or shutdown failure.

The user explicitly approved whole-mod parity after reviewing the completed
regression. At that historical checkpoint, `/voxy parity_route_status` reported
`wholeOriginalModParity=true` and advances the target to the non-blocking
`post-parity-iterationt-compatibility-todo`; IterationT remains outside the
original Voxy migration completion gate because no upstream adaptation exists.

The post-approval verification passed: CodeGraph re-read the live status method
with the promoted value and post-parity target, `gradlew test build` completed
all 11 tasks successfully, and `git diff --check` remained clean. This closes
XXIV and the original Voxy migration scope.

## XXV exhaustive original-content, retired-code, and artifact re-audit

### Why XXIV was reopened

The user requested a stronger closure than the XXIV area-level audit: inspect
all original Voxy content and eligible Forge equivalents, remove every retired
route and misleading API name, then audit the compiled project and final JAR
before starting the client once for final acceptance. That request supersedes
the XXIV completion sentence above without invalidating its historical runtime
evidence. XXV is one round and remains uncommitted until its requested
review/commit step; the final client validation is now complete.

```text
[x] authoritative original-source / Forge-source inventory
[x] port missing original features and eligible Forge integrations
[x] finish retired-code and exact-name convergence
[x] complete the first broad visual/lifecycle acceptance
[x] correct the newly exposed F3 and Embeddium-option frontend parity gaps
[x] refresh whole-source/resource/final-JAR audit and exact artifact metadata
[x] pass the focused F3-visibility and Embeddium-page runtime observation
[x] pass the focused config-Apply runtime acceptance
```

### Newly completed original features and compatible integrations

The original optional-content inventory was re-read rather than inheriting the
old “Fabric-only” classification. XXV adds only real formats or real Forge
owners; no preview or substitute route was introduced.

| Original area | XXV Forge result |
| --- | --- |
| Distant Horizons import | Direct, optional DH SQLite import was ported with the original database-selection/task lifecycle and Forge-side decoders for supported DH data/compression versions. XXXVI validates real DH 3.2.0-b output and the supported two-stage producer/importer workflow; DH itself must not be installed in the Voxy import client. |
| Bobby import | `.bobby` region-cache import is exposed through the original import manager/world importer lifecycle. |
| Chunky | The real Forge Chunky server-generation callback is bridged only for the integrated server dimension matching the active client identity. Dedicated/multiplayer server chunks cannot leak into the client engine. |
| Acedium | A real optional rendering-pipeline mixin preserves the original Nvidium compatibility intent when the Forge fork's own `acedium` mod entry is installed. |
| GPU selection | Original GPU priority/selection behavior is ported through a Windows mixin and capability owner rather than a diagnostic command. |
| GL debug / printf / timing | Original `GlDebug`, shader printf, `GPUTiming`, `TimingStatistics`, capability and memory statistics owners are present and wired to the active renderer/F3 route. |
| Embeddium / Oculus | Embeddium is the hard Forge frontend; Oculus is the optional Iris-equivalent integration. Both are active adapters when present, not substitutes or status probes. |
| Flashback / FREX hooks | Platform-N/A where no equivalent Forge owner exists. Acedium is the Forge Nvidium owner and is selected through its own `acedium` entry; its second `nvidium` entry is compatibility metadata. |
| ModMenu / Sodium config host | Fabric ModMenu itself is platform-N/A, but its user-visible responsibility is not: the Forge Mod List entry opens Embeddium's video options and Voxy contributes original-shaped pages through Embeddium's official `OptionGUIConstructionEvent`. |

Current supported command surface after removing the parity/status shell:

```text
/voxy reload
/voxy import world|bobby|distant_horizons|raw|zip|current|cancel
/voxy debug verifyTLNChildMask [attemptRepair]
```

### Ownership and lifecycle corrections

- `HierarchicalOcclusionTraverser` is the sole owner of the GPU node buffer;
  `AsyncNodeManager` owns the geometry manager and geometry-result consumer.
- The formal original F3 chain is restored: instance/world/service/model/render
  owners append their own debug data; Forge only supplies the event adapter and
  now exits before timing/GPU/owner collection whenever
  `Minecraft.options.renderDebug` is false, matching original debug-screen
  visibility.
- Chunk ingest retry is event-driven and keeps the same section-ingest contract;
  the retired polling/counter shell is gone.
- The then-no-op Oculus viewport capture injected at `beginLevelRendering` was
  removed during this historical cleanup. A 2026-07-19 artifact audit later
  proved that original Voxy's corresponding hook is not semantically optional:
  `LevelRenderer.renderLevel` captures the vanilla projection, model-view and
  camera, and `IrisRenderingPipeline.beginLevelRendering` applies them before
  Iris activates texture unit zero. Forge now restores that lifecycle and keeps
  the later Embeddium cutout hook as a fallback. Runtime comparison nevertheless
  showed no reduction in the reported cross-vanilla/LOD shadow discontinuity,
  so this is a retained parity correction, not a claimed root-cause fix.
- `CountingSectionStorage` and its five write-only counters were removed;
  `WorldEngine` now owns the real persistent `SectionStorage` directly.
- Original `RenderResourceReuse`, model store, generation, geometry, node,
  traversal, MDIC, pipeline, importer and storage owners now use their original
  simple names where the class is a direct port. Forge/Oculus/Minecraft adapters
  retain an explicit Forge prefix.

The planned direct-port name inventory is complete (62/62). Fifty-six tracked
files are Git renames; six owners were introduced during XXV and therefore have
no tracked preimage:

```text
ForgeOriginalVoxyAsyncNodeGeometrySync -> AsyncNodeManager
ForgeOriginalVoxyBasicAsyncGeometryManager -> BasicAsyncGeometryManager
ForgeOriginalVoxyBasicSectionGeometryData -> BasicSectionGeometryData
ForgeOriginalVoxyBuiltSection -> BuiltSection
ForgeOriginalVoxyGlCapabilities -> Capabilities
ForgeOriginalVoxyChunkBoundRenderer -> ChunkBoundRenderer
ForgeOriginalVoxyClientImportManager -> ClientImportManager
ForgeOriginalVoxyColourDepthTextureData -> ColourDepthTextureData
ForgeOriginalVoxyCompressionStorageAdaptor -> CompressionStorageAdaptor
ForgeOriginalVoxyDepthFramebuffer -> DepthFramebuffer
ForgeOriginalVoxyDownloadStream -> DownloadStream
ForgeOriginalVoxyObjectAllocationList -> ExpandingObjectAllocationList
ForgeOriginalVoxyFragmentedStorageBackendAdaptor -> FragmentedStorageBackendAdaptor
ForgeOriginalVoxyFullscreenBlit -> FullscreenBlit
ForgeOriginalVoxyGeometryCache -> GeometryCache
ForgeOriginalVoxyGlBuffer -> GlBuffer
ForgeOriginalVoxyHiZBuffer -> HiZBuffer
ForgeOriginalVoxyHierarchicalOcclusionTraverser -> HierarchicalOcclusionTraverser
ForgeOriginalVoxySectionWatcher -> ISectionWatcher
ForgeOriginalVoxyIdNotYetComputedException -> IdNotYetComputedException
ForgeOriginalVoxyLmdbStorageBackend -> LMDBStorageBackend
ForgeOriginalVoxyLz4Compressor -> LZ4Compressor
ForgeOriginalVoxyMdicSectionRenderer -> MDICSectionRenderer
ForgeOriginalVoxyMdicViewport -> MDICViewport
ForgeOriginalVoxyMipGen -> MipGen
ForgeOriginalVoxyModelBakerySubsystem -> ModelBakerySubsystem
ForgeOriginalVoxyModelFactory -> ModelFactory
ForgeOriginalVoxyModelQueries -> ModelQueries
ForgeOriginalVoxyModelStore -> ModelStore
ForgeOriginalVoxyNodeCleaner -> NodeCleaner
ForgeOriginalVoxyNodeManager -> NodeManager
ForgeOriginalVoxyNodeStore -> NodeStore
ForgeOriginalVoxyOccupancySet -> OccupancySet
ForgeOriginalVoxyReadonlyCachingLayer -> ReadonlyCachingLayer
ForgeOriginalVoxyRedisStorageBackend -> RedisStorageBackend
ForgeOriginalVoxyRenderDataFactory -> RenderDataFactory
ForgeOriginalVoxyRenderDistanceTracker -> RenderDistanceTracker
ForgeOriginalVoxyRenderGenerationService -> RenderGenerationService
ForgeOriginalVoxyRenderProperties -> RenderProperties
ForgeOriginalVoxyRenderResourceReuse -> RenderResourceReuse
ForgeOriginalVoxyRenderStatistics -> RenderStatistics
ForgeOriginalVoxyReuseVertexConsumer -> ReuseVertexConsumer
ForgeOriginalVoxyRingTracker -> RingTracker
ForgeOriginalVoxyRocksDBStorageBackend -> RocksDBStorageBackend
ForgeOriginalVoxySSAO -> SSAO
ForgeOriginalVoxyScanMesher2D -> ScanMesher2D
ForgeOriginalVoxySectionUpdateRouter -> SectionUpdateRouter
ForgeOriginalVoxyShaderSource -> ShaderLoader
ForgeOriginalVoxySharedIndexBuffer -> SharedIndexBuffer
ForgeOriginalVoxySoftwareRasterizer -> SoftwareRasterizer
ForgeOriginalVoxyTextureUtils -> TextureUtils
ForgeOriginalVoxyUploadStream -> UploadStream
ForgeOriginalVoxyViewportSelector -> ViewportSelector
ForgeOriginalVoxyWorldIdentifier -> WorldIdentifier
ForgeOriginalVoxyWorldImporter -> WorldImporter
ForgeOriginalVoxyZstdCompressor -> ZSTDCompressor
ForgeOriginalVoxyDebugUtils -> DebugUtils
ForgeOriginalVoxyGlDebug -> GlDebug
ForgeOriginalVoxyGpuSelectorWindows2 -> GPUSelectorWindows2
ForgeOriginalVoxyGpuTiming -> GPUTiming
ForgeOriginalVoxyPrintfDebug -> PrintfDebugUtil
ForgeOriginalVoxyTimingStatistics -> TimingStatistics
```

Two runtime access contracts were separately aligned to the original API names:
`IWorldGetIdentifier.voxy$getIdentifier()` and
`ICheekyClientChunkCache.voxy$cheekyGetChunk(...)`. The Forge-only packed-offset
helper is explicitly `ForgeModelStoreLayoutSpec`; calling it simply
`ModelStoreLayoutSpec` would falsely imply an independent original owner.

`ForgeOriginalVoxyRenderSystem` and `ForgeOriginalVoxyModelPipeline` intentionally
retain their names. Forge splits original `VoxyRenderSystem` construction/resource
ownership from the Embeddium event/frame adapter; merging or renaming only one
half would conceal a real platform ownership deviation. The remaining
`ForgeOriginalVoxy*` names are likewise Oculus, Embeddium, Minecraft, DH,
optional-mod, GL-target, or lifecycle adapters rather than missed direct ports.

### Retired proof/status surface removed

XXV deletes the parity command registrar, frontend status DTO, model/reload
status trackers, nine renderer/model/generation statistics DTOs, uploaded-model
summary, reflection-field mapping record, and their snapshot methods. It also
removes write-only lifecycle/stage counters, cached failure strings that were
only returned in the same stack frame, zero-call readiness helpers, preset/debug
clear aliases, storage counters, and other zero-call accessors. Runtime guards,
actual failure returns/logging, queue counts used by scheduling, formal F3
statistics, GL/GPU debug owners, importer progress, and live lifecycle methods
remain.

The removed `activeNodeRequestCount` deserves explicit classification. In the
original it is consumed only by the optional `verifyNodeManager` developer
integrity suite. Forge had copied the increments but not the flag, verifier, or
worker call, leaving a zero-read fragment. XXV removes that fragment. If the
developer verifier is ever ported, its flag, complete verification methods,
worker gate, and counter must be ported together.

### Shader and resource parity evidence

The resource audit compares all original shader resources with the compiled
Forge set. Forty-six of forty-seven shader files are byte-identical. The sole
intentional adaptation is `outline.vsh`, whose frontend symbols map to
Embeddium 1.20.1. The earlier extra reverse-AABB guard in
`lod/hierarchical/screenspace.glsl` was not tied to any Forge/Oculus contract;
the full line audit classified it as an unproven local algorithm change and
restored that shader byte-for-byte to original.

The earlier one-stage `PATCHED_SHADER` defect remains fixed: the define is
applied to both vertex and fragment stages. The final JAR audit must prove all
registered mixins, refmap mappings, access transformer, shaders, metadata,
language/icon assets, optional adapters, pinned JarJar payloads, and required
native libraries are present, while deleted status/legacy classes and Fabric
descriptors are absent.

### Production packaging correction

XXV enables the Mixin annotation processor/Gradle integration for the production
artifact, generates the SRG refmap, adds it to the mixin configuration, and
wires it through the reobfuscated JarJar task. The generated refmap contains
four mapped Mixin classes: three non-identity vanilla SRG mappings
(`renderLevel`, `setBlocksDirty`, and `ClientChunkCache.drop`) plus the GPU
selector's exact `Minecraft(GameConfig)` constructor identity mapping.
Embeddium/Oculus targets intentionally use `remap = false` against their shipped
names.

Pinned JarJar versions are resolved by ForgeGradle through a detached strict
configuration; a normal `dependencies --configuration jarJar` report shows the
un-pinned range candidate and is not the packaged payload. The final archive,
not that report, is the acceptance authority.

### Final-gate startup correction: Minecraft 1.20.1 GPU-selection anchor

The first XXV final client launch stopped during Mixin application before the
game window opened. `ForgeOriginalVoxyGpuSelectMixin` had ported original
`MixinGPUSelect`'s `Options.save()` invocation anchor literally, but the mapped
Forge 1.20.1 `Minecraft(GameConfig)` constructor has no such invocation. The
runtime therefore correctly failed its required 1/1 injection check; the
development-only missing-refmap warning was incidental and copying the refmap
would not create a nonexistent target.

Original commit `970b1cc7` describes this injection as “move and hoist gpu
selection injection”: the GPU selector and Java/Win32 thread-priority changes
must execute before backend/window creation. Forge 1.20.1's semantic equivalent
is the constructor's sole
`RenderSystem.initBackendSystem()Lnet/minecraft/util/TimeSource$NanoTimeSource;`
invocation, immediately before `VirtualScreen.newWindow(...)`.

The next startup established a second platform constraint: Forge 1.20.1 ships
Mixin 0.8.5, whose callback injector rejects an `@Inject(INVOKE)` inside a
constructor even after the superclass initialization. Mixin 0.8.5's standard
method-call redirector explicitly permits this target. The final adaptation is
therefore a required `@Redirect` of that one static invocation: its private,
non-static, no-argument handler returns the exact
`TimeSource.NanoTimeSource` type, executes the original GPU/priority body, then
calls and returns `RenderSystem.initBackendSystem()` once. The redirect applies
only to `Minecraft(GameConfig)`; the handler is outside that target and cannot
recursively redirect itself. Default vanilla remapping and the mixin config's
`defaultRequire = 1` remain intact. A TAIL fallback, `remap = false`, or
`require = 0` would violate the original timing/validation contract.

After the correction, the production refmap contains the exact constructor
identity entry. Final `javap` output proves `@Redirect` is the only injector on
the handler, its descriptor is
`()Lnet/minecraft/util/TimeSource$NanoTimeSource;`, and its bytecode order is
optional GPU selection, Java priority, Win32 priority, original backend call,
then `areturn`. `@Inject`, `CallbackInfo`, the former `Options.save()` descriptor,
and `m_92169_` are absent from the GPU Mixin/final project classes as applicable.
No Gradle resource-copy workaround was added: MixinGradle continues to own the
production refmap, while Forge userdev's missing-refmap message remains an
expected development warning.

### Final-gate startup correction: Bobby unload signature

The next client run passed GPU selection, created the window, started the
integrated server, and reached player login. Loading `ClientChunkCache` then
exposed another source-version signature difference: original Voxy's current
target has `drop(ChunkPos)`, while Forge 1.20.1 has `drop(int x, int z)`. The
literal original handler descriptor was therefore invalid for the real 1.20.1
method.

The final Forge adapter keeps the original mechanism and timing: required
`@Inject(method = "drop", at = @At("HEAD"))`, direct access to the current
storage ring through `ICheekyClientChunkCache`, actual chunk-position validation,
and optional Bobby ingestion before the slot is removed. Only the handler input
is translated to `(int x, int z, CallbackInfo)`, and those coordinates feed the
same `voxy$cheekyGetChunk(x, z)` contract. It does not substitute normal
`getChunk`, the Forge unload event, or a post-unload lookup.

Final `javap` proves descriptor `(IILorg/spongepowered/asm/mixin/injection/
callback/CallbackInfo;)V`, `drop`/`HEAD`, and the expected guard -> storage
lookup -> non-null ingest order. The old `(ChunkPos, CallbackInfo)` handler is
absent. A complete audit of all four vanilla-target Mixin owners also confirms
that the GPU redirect, `ClientLevel` constructor and `setBlocksDirty`, and
`LevelRenderer.renderLevel` selectors/handlers exactly match Forge 1.20.1; no
other vanilla signature adaptation remains.

### Final-gate renderer construction correction: NodeCleaner printf processing

The following client run passed every Mixin, created the window, started the
integrated server, logged the player in, created the persistent `WorldEngine`,
and reached original render-system construction. `NodeCleaner` then failed to
compile `sort_visibility.comp`: expanded GLSL line 135 still contained the
`debugDumpNode` `printf("Node %d, %d@...")` from `node.glsl`, so the NVIDIA
compiler correctly rejected the undefined function, string literal, and `@`
token.

This was one exact Forge port omission, not a driver extension requirement.
Original `NodeCleaner.sorter` is created through
`Shader.makeAuto(PrintfDebugUtil.PRINTF_processor)`. Original `Shader.Builder`
recursively expands imports, runs that processor while adding the source, then
injects ordinary and environment defines before compilation. The Forge HOC
compiler already preserved this contract for `traversal_dev.comp`, but the
Forge `NodeCleaner` sorter had gone directly from `ShaderLoader.parse(...)` to
defines and compilation.

The corrected Forge chain is now:

```text
sort_visibility.comp
 -> ShaderLoader.parse (expand node.glsl / pos_util.glsl)
 -> PrintfDebugUtil.processShader
 -> NodeCleaner defines
 -> IS_WINDOWS / conditional IS_INTEL defines
 -> compute-shader compilation
```

With shader printf debugging disabled, the processor performs the same original
`printf` -> `//printf` replacement. With it enabled, the already-ported binding
20 SSBO/atomic-write transformation remains in use. Only the sorter receives
the processor, exactly like original Voxy; `result_transformer.comp` and
`batch_visibility_set.comp` do not. A focused source-generation test proves the
expanded sorter retains its expected defines and `debugDumpNode` body while no
active `printf(` remains in the normal configuration. The packaged but unused
`debug/node_outline.vert` import remains unchanged: original `DebugRenderer` is
itself unreferenced and also does not attach the printf processor, so extending
that dormant upstream path would not be a Forge parity correction.

### Accepted non-blockers and final gate

- The audited upstream HOC/request author `FIXTHIS` behavior remains original
  behavior, not a Forge substitute.
- IterationT compatibility remains a post-migration TODO because original Voxy
  contains no pack-specific adaptation or sidecar.
- Very fast spectator streaming holes and the original geometry throttling are
  accepted upstream behavior.
- Historical manual audit commands and RenderDoc probes remain evidence in old
  sections only; they are not current commands or readiness mechanisms.

The production acceptance build now passes:

```text
command: gradlew clean test build jarJar --console=plain
result: BUILD SUCCESSFUL
tests: 7 suites; 20 tests; 0 failures; 0 errors; 0 skipped
git diff --check: passed
```

The final reobfuscated JarJar acceptance artifact is
`build/libs/voxy-forge-0.2.17-beta-forge-all.jar`, 97,319,501 bytes, SHA-256
`1cdfda276575b53a665c534bc1f00044c6ba8a05644b1d77c5b5ab5acc788485`.
Its archive audit established all of the following:

- all 9 JarJar payload filenames, metadata versions, and actual nested
  dependencies match their strict pins;
- all 4 packaged Windows/Linux x64 LWJGL native SHA-1 sidecars match their
  binaries;
- all 21 Mixin registrations and classes agree, the generated refmap contains
  the 3 required non-identity vanilla SRG mappings and exact GPU constructor
  identity mapping, and `javap` confirms the corrected required redirect,
  handler bytecode order, and final reobfuscated Minecraft method references;
- all 47 shaders match the current source byte-for-byte and all 42 Voxy shader
  imports resolve;
- all 105 Forge top-level source classes have exactly one final top-level class,
  with no stale clean-build extras;
- `ForgeOriginalVoxyConfigSnapshot`, `ForgeOriginalVoxyEmbeddiumOptions`, the
  Voxy language resources, and registered Mixins are present, while the retired
  standalone `ForgeOriginalVoxyConfigScreen` is absent;
- the twentieth test initializes the Embeddium options class through
  `Class.forName`, so a page/option `OptionIdentifier` type collision fails the
  automated gate rather than first appearing in the options screen;
- the new core interfaces/owners are present while the explicit legacy,
  readiness/status, platform-shell, retired-command, Fabric-descriptor, access
  widener, and XX.4 probe inventories are absent;
- the manifest, Forge dependency metadata, pack metadata, access transformer,
  nested services/native entries, and user-owned `capsettings.cap` exclusion
  are correct.

The only build diagnostics left are three Forge 1.20.1 API deprecation/removal
warnings plus forward-looking Gradle/Eclipse-integration notices; none blocks
Forge 1.20.1 execution. Static source, test, build, and artifact acceptance are
therefore closed for the frontend corrections. The focused F3/menu runtime
observation and config-Apply lifecycle acceptance are also closed.

### Final client technical gate and broad visual acceptance: passed

The anchored `D:\Projects\voxy` `runClient` acceptance run completed normally
after the NodeCleaner correction. Its log proves all of the following against
the current XXV classes:

- all required Mixins applied, the integrated server admitted the player, and
  the persistent original storage chain opened;
- the initial no-shader route created `ForgeOriginalVoxyRenderPipeline` with
  `MDICSectionRenderer`, reached the Embeddium cutout command-generation hook,
  and grew sparse geometry commitment from zero;
- enabling and repeatedly rebuilding Complementary applied both Voxy shader
  patches, loaded the 24,056-entry Oculus block-state mapping, reused the same
  4 GiB sparse geometry buffer, and recreated the same MDIC renderer each time;
- overworld -> nether -> overworld and overworld -> end -> overworld transitions
  all rebuilt the Oculus/Voxy pipelines successfully;
- an F3+T resource reload rebuilt resources and the Voxy renderer successfully;
- normal exit flushed downloads, shut down every active render pipeline and
  worker owner, closed the persistent `WorldEngine`, saved all dimensions, and
  ended the Gradle run successfully.

The post-exit targeted scan found no `Original Voxy ... failure`, Voxy `ERROR`,
NodeCleaner compile error, uncaught-thread exception, OOM, or crash-report line.
The repeated `already has a request in flight` warnings occurred only during the
intentional high-speed spectator streaming exercise and remain the previously
audited original `FIXTHIS` behavior, not a new Forge deviation. Oculus warnings
about shaderpack variables from newer Minecraft versions were pack/frontend
diagnostics and did not prevent any Voxy pipeline recreation.

The user then completed the broad visual acceptance and reported no renderer,
shaderpack, water, dimension, reload, or lifecycle anomaly. That broad result is
accepted evidence. It also exposed two narrower frontend-parity omissions: the
Voxy diagnostic window remained visible with F3 closed, and Voxy settings were
not presented as pages inside Embeddium as original Voxy presents them inside
Sodium.

### XXV frontend parity follow-up: targeted runtime acceptance passed

The follow-up re-read original `VoxyConfigMenu`, its configuration effect flags,
original debug-screen entry ownership, and Embeddium 0.3.32's actual option GUI
construction path before changing the Forge route.

The implementation now has these exact boundaries:

```text
ForgeVoxyInstance.onDebugText
 -> return while Minecraft.options.renderDebug == false
 -> append original owner diagnostics only while F3 is visible

OptionGUIConstructionEvent.BUS
 -> voxy:general_page
 -> voxy:rendering_page
 -> one shared OptionStorage and baseline across both pages
 -> repeated Apply saves only the delta from the most recent baseline

Forge Mod List config factory
 -> Embeddium SodiumOptionsGUI entry
 -> same event-built Voxy pages; no standalone Voxy screen
```

All nine current original settings are present: enabled, service threads, use
Embeddium/Sodium builder threads, ingest, rendering, subdivision size, render
distance, environmental fog, and SSAO mode. Dynamic enabled predicates follow
the master enabled/rendering values; fog and SSAO are unavailable while a
shaderpack owns those effects. The original render-distance input minimum 10 is
restored (`10/16` internal sections), with original nonlinear subdivision and
distance display/storage transforms.

Apply behavior now preserves original ownership instead of restarting the
entire in-memory world for renderer settings: service-thread count updates the
thread policy, render distance updates live when no rebuild supersedes it,
rendering rebuilds only the Voxy render owner, and builder-thread/fog/SSAO
changes rebuild that owner plus vanilla `LevelRenderer`; all retain the active
`WorldEngine`. This paragraph records the historical XXV frontend implementation:
its enabled path still kept the service pool process-owned. XXVI.1 replaced that
owner with a complete per-network-session runtime, so the strict-lifecycle TODO
described here is closed. The old standalone
`ForgeOriginalVoxyConfigScreen` is deleted.

The refreshed production build/JAR evidence above includes these corrections.
The user completed the instructed focused F3/menu run and closed the client
normally without reporting an F3-visibility or Voxy-page anomaly. Runtime
evidence agrees with that observation: at `20:20:20.204` Voxy registered its
option pages with Embeddium; opening the options GUI at `20:21:07` reached
Embeddium's page construction, with only Oculus's own shader page reporting a
missing ID; and no `OptionIdentifier`, `IllegalArgumentException`,
`ExceptionInInitializerError`, Voxy error, or Voxy warning was emitted. The
renderer completed shutdown at `20:21:37.485`, followed by normal persistent
`WorldEngine` and Voxy-instance closure at `20:21:39`.

The required config-Apply follow-up then passed. Applying `Rendering` off while
the client remained active flushed and shut down the Voxy renderer at
`20:33:39.764-20:33:39.769`; Oculus destroyed its pipeline at `20:33:39.770`
and recreated it at `20:33:40.214`, while Voxy correctly stayed absent. Applying
`Rendering` on persisted `enableRendering=true` at `20:34:13`, destroyed and
recreated the Oculus pipeline at `20:34:13.953-20:34:14.363`, then recreated
`ForgeOriginalVoxyRenderSystem` and `MDICSectionRenderer` at
`20:34:15.802-20:34:16.579`. The user confirmed the expected LOD disappearance
and recovery without a visual anomaly.

The client then completed clean renderer/server/`WorldEngine`/instance shutdown
at `20:34:22-20:34:23`; `runClient` exited 0 with `BUILD SUCCESSFUL`. The
targeted scan found no Voxy error/warning, option-identifier/initializer error,
or config failure. `XXV_CONFIG_APPLY_RUNTIME_ACCEPTANCE`, the combined focused
frontend gate, and current whole-mod acceptance therefore pass. XXVI.1 later
closed the enabled-config strict-lifecycle difference; IterationT remains the
documented post-migration compatibility TODO.

## XXVI post-XXV whole-mod parity closure

XXVI re-opened the result after the user requested a whole-repository review and
asked why the Forge artifact had grown to more than 90 MB while original Voxy's
artifact is only about 12 MB. This round did not extend a preview route. It
re-read the current `dev` implementations and the Forge 1.20.1/Embeddium/Oculus
owners, then corrected process/session lifetime, ingest hot-path, storage,
optional-library, diagnostics, custom-material, and packaging drift in one
coherent regression unit.

### XXVI.1 session ownership and disabled-state isolation

Original `ClientSessionEvents` constructs one `VoxyClientInstance` while the
join packet is being handled, before the client level is installed, and destroys
that instance only after Minecraft has detached the level. Original
`VoxyInstance` owns the unified service pool, saving and ingest services, import
manager, active-world map, and active-world cleaner for exactly that session.
The pre-XXVI Forge event shell instead retained some of these owners for the
whole process. Applying `enabled=false` could therefore leave Voxy workers and
Embeddium semaphore participation alive even though rendering was disabled.

XXVI keeps only the Forge event registration shell process-wide. Its
`SessionRuntime` now owns the original resource set per network connection:

```text
ClientPacketListener.handleLogin
 -> after PacketUtils.ensureRunningOnSameThread
 -> beginOriginalVoxyClientSession(the target ClientPacketListener)
 -> read this connection's storage config
 -> create unified pool / saving / ingest / imports / world cleaner
 -> Embeddium later creates its chunk job queue against that pool

Minecraft.clearLevel(Screen) TAIL
 -> level and Embeddium queue are already detached
 -> stop world cleaner
 -> cancel imports
 -> render-thread VoxyRenderSystem shutdown and GL release
 -> stop ingest / saving / unified pool
 -> wait for references and free every WorldEngine/storage
```

The explicit `ClientPacketListener` is required ground truth at the early join
boundary: in Minecraft 1.20.1 `Minecraft#getConnection()` still returns null
until the player owns its connection. The later Forge login event remains only
the post-level world-selection fallback. Reconnect creates a new owner and old
levels are rejected by connection and identity. A disabled Voxy creates no
session runtime, worker, ingest callback, semaphore block, F3 diagnostics, or
LOD owner. Re-enabling through Apply performs original-equivalent full instance
replacement; the XXV text above describing a process-singleton service pool and
an accepted strict-lifecycle TODO is therefore historical and superseded.

Render resources are still released only on the render thread. When shutdown is
requested elsewhere, the continuation runs after the recorded render call and
only then tears down the CPU services/worlds. `RenderResourceReuse` is cleared
at full session end, as in `VoxyClientInstance.shutdown`. The world cleaner is
stopped once, before imports and services, matching `VoxyInstance.shutdown`.

### XXVI.2 storage configuration parity

The storage owner now captures base path, player UUID, and parsed configuration
per session rather than caching configuration by path for the process. A logout
and reconnect therefore rereads `config.json`, including changes made while the
client was disconnected. The original registered `Memory` backend type is now
accepted in addition to the already ported storage graph.

One proposed safety behavior was explicitly rejected during review. Preserving
an invalid/unknown storage JSON and silently disabling that session appeared
safer, but it was not a port: original `StorageConfigUtil` resets any null,
invalid-version, missing-storage, unknown-type, or parse-failed configuration to
the default, then unconditionally writes the canonical JSON before continuing.
XXVI restores that exact reset-and-rewrite behavior. Directory creation and the
final write remain fail-fast exceptions, also matching original. Valid configs
are canonical-rewritten on every session construction rather than returned
without a write.

The only version adaptation in the path is Realms classification: current
original uses `ServerData.isRealm()`, which does not exist in 1.20.1, so Forge
uses the equivalent 1.20.1 owner `Minecraft.isConnectedToRealms()`. Singleplayer
continues to use `<world>/voxy`; multiplayer uses `.voxy/saves/<server>`.

### XXVI.3 ingest hot-path parity

Forge had drifted from original's direct palette decode to the public
`PalettedContainer#get` route and allocated new voxel backing storage during
ingest. XXVI exposes only the required 1.20.1 `PalettedContainer.data`,
`Data.palette`, and `Data.storage` fields through the Forge access transformer,
then ports original's packed `SimpleBitStorage`/`ZeroBitStorage` loop, local and
global palette handling, per-thread biome/palette scratch storage, and weak
per-`Mapper` block-state ID cache. Java 17's small bit-selection helper is the
only substitute for original's newer-Java `Integer.compress` call; its output is
covered exhaustively for the 4x4x4 biome index domain.

`VoxelIngestService` again reuses one 4,681-long `VoxelizedSection` backing array
per ingest thread. This is safe for the same reason as original: conversion,
mipping, and `WorldUpdater.insertUpdate` consume the section synchronously
before the worker accepts its next job. The existing Forge lighting corrections
(LIGHT_AND_DATA readiness, implicit uniform sky light, dead-world drop, and
retry queue) are preserved; they remain 1.20.1 ingestion adaptations and are not
replaced by the performance port.

Original's optional `LithiumHashPalette` branch is intentionally not copied:
Lithium is Fabric-only here, Embeddium does not replace the vanilla palette
classes, and Forge 1.20.1 constructs `LinearPalette`, `HashMapPalette`,
`SingleValuePalette`, or `GlobalPalette`. An unknown coremod palette fails with
its class name instead of silently producing incorrect mapping IDs; this is the
same explicit-adapter boundary as original's Lithium special case.

### XXVI.4 Forge custom material-layer adaptation

Current original receives a per-quad `ChunkSectionLayer`; Forge 1.20.1 exposes
`RenderType` and permits mods to return custom instances. The former Forge path
threw on any non-vanilla type, causing an entire otherwise valid LOD model bake
to become empty. XXVI preserves exact solid/cutout/translucent handling for the
five vanilla chunk layers and classifies only custom layers from Embeddium's own
per-sprite transparency analysis. This is a platform representation adapter,
not a fallback renderer: the same original opaque/translucent consumers,
metadata, software rasterizer, model store, and visible renderer remain in use.

Fluid custom layers use Forge's actual fluid sprites and fluid-type tint alpha;
sprite transparency is considered before a final descriptive-name hint.
`forceSolid` follows original `ReuseVertexConsumer`: it clears discard metadata
for leaves but never reroutes a translucent quad into the opaque consumer.
Unknown custom-layer classifications warn once so future mod-specific evidence
can add a direct mapping without silently changing the model.

### XXVI.5 diagnostics and optional-library behavior

The original tracked-object verification property is restored with its original
default-on, case-sensitive semantics:

```text
voxy.ensureTrackedObjectsAreFreed=true by default
voxy.trackObjectAllocationStacks=false by default
```

The legacy Forge-only `voxy.disableTrackedObjectAllocations` alias has been
removed; tracked-object allocation now depends only on the original property,
including its exact lowercase `"true"` parsing. The 1.20.1
`GlDebug#printDebugLog` adapter now adds an origin
stack to Voxy-caused GL diagnostics and suppresses the expected capability
shader-compile probes. The `BlockableEventLoop#doRunTask` adapter rethrows a
failure whose stack identifies `ClientPacketListener#handleLogin`. This is not
broader than original: newer original first wraps every failed
`ClientboundLoginPacket` in `LoadException`, then unwraps it at the same task
boundary. In 1.20.1 the handler frame is the available equivalent packet-type
evidence.

The Distant Horizons decoder now uses the already packaged LWJGL Zstd binding
instead of adding a second zstd-jni native stack. Streaming decompression,
truncation/error checks, XZ array-cache reset, format/compression gates, and the
SQL single-reconnect path are covered by focused tests. The DH command is
registered only when optional SQLite JDBC and Voxy's privately relocated XZ
classes are present; normal Voxy use does not require or package SQLite. This
matches original's
runtime-library treatment rather than turning DH into a hard Voxy dependency.

### XXVI.6 artifact-size root cause and correction

The 90+ MB artifact was dependency payload, not 80 MB of Forge renderer code.
The three largest mistakenly nested inputs alone were:

```text
rocksdbjni-10.2.1.jar   72,769,957 bytes (all upstream architectures)
sqlite-jdbc-3.49.1.0    14,317,659 bytes
zstd-jni-1.5.7-6         7,404,108 bytes
```

That is 94,491,724 bytes before Voxy classes, shaders, LWJGL modules, XZ, LZ4,
Jedis, and pool2. Original `dev/build.gradle` instead repacks RocksDB, keeps only
Win64 and Linux x64 by default, uses LWJGL Zstd, packages XZ, and treats SQLite
as a development/runtime library. XXVI ports that mechanism to Forge JarJar:

- `makeExcludedRocksDB` contains exactly `librocksdbjni-win64.dll` and
  `librocksdbjni-linux64.so`; its entries are stored so the outer mod JAR can
  compress the selected native payload once;
- zstd-jni and sqlite-jdbc are absent from JarJar metadata and the artifact;
- XZ 1.10 bytecode remains packaged under the private
  `me.cortex.voxy.dependency.xz` namespace because DH compression modes require
  it; neither the original `org.tukaani.xz` package nor a nested XZ JarJar module
  remains, avoiding a Java module split-package conflict with DH 3.2.0-b;
- LWJGL Zstd/LMDB use the Minecraft 1.20.1-compatible 3.3.1 API/native set;
- `-PincludeOtherArchs=true` remains the explicit wider-architecture build
  switch, matching original policy.

The final XXVI artifact is:

```text
build/libs/voxy-forge-0.2.17-beta-forge-all.jar
size=12,560,316 bytes
JarJar dependencies=7
rocksdb natives=Win64 + Linux x64 only
sqlite-jdbc=absent
zstd-jni=absent
xz-1.10=present
```

This is the same size class as original Voxy's 12 MB artifact while retaining
the Forge/Oculus/Embeddium adaptation and the original storage backends.

### XXVI.7 review, exclusions, and validation state

The main review and an independent read-only review rechecked all nullable model
pipeline callers, session/reload ordering, explicit early connection ownership,
Embeddium queue/semaphore lifetime, storage reset semantics, custom block/fluid
materials, TrackedObject flags, diagnostic mixin selectors, XZ, DH gating, and
JarJar contents. The invalid-config preservation route and the unavailable
1.20.1 `ServerData.isRealm()` call were found during this review and removed.
No remaining P0-P3 source finding was identified before the runtime gate.

A separate bounded Claude Code read-only pass likewise reported no verifiable
P0-P3 finding in the files it covered. Its scan was stopped to conserve the
user's limited Claude Pro allowance before it reached the lifecycle core and
all new mixins, so it is recorded only as supplemental partial evidence and is
not counted as the independent review above or as a full-review pass.

The forced clean validation was:

```text
.\gradlew clean test jarJar --rerun-tasks --console=plain
BUILD SUCCESSFUL
14 test suites / 37 tests / 0 skipped / 0 failures / 0 errors
git diff --check=passed
Mixin refmap selectors=resolved for handleLogin, PacketUtils,
  clearLevel, GlDebug.printDebugLog, and BlockableEventLoop.doRunTask
```

The first anchored client launch then exposed a Mixin 0.8.5 application rule
that javac and the unit suite cannot exercise: `@Unique` static methods copied
into a target must be private. The package-visible classification helpers in
the new BlockableEventLoop and GlDebug mixins were made private, while their
unit coverage now invokes them reflectively. A second forced clean
`test jarJar` run passed all 37 tests, and the next anchored client launch
entered the quick-play world with all four new mixins applied and an original
Voxy network-session runtime created. This closes the launch blocker without
changing the diagnostic behavior; the consolidated user-observed runtime
regression remains pending.

The following are not reopened implementation findings:

- IterationT still has no original Voxy sidecar or special case and remains the
  already documented post-migration compatibility TODO.
- `ConditionalConfig` still throws upstream's own not-implemented exception;
  inventing a backend would violate the baseline.
- Fabric Lithium and Nvidium class-level hooks are not loaded directly on Forge;
  their applicable responsibilities are owned by Embeddium and Acedium instead.
- `capsettings.cap` is user-owned, remains untracked, and is excluded from every
  artifact/commit gate.

Static source, unit, packaging, and artifact gates pass. Whole-original-mod
parity remains reopened only because the four new required 1.20.1 mixins and the
session/disabled/custom-material paths require one anchored user-observed client
regression. No XXVI commit is permitted until that gate passes.

## XXVII exhaustive line-by-line port audit

The user's final confidence question is not treated as a request for another
sample review. XXVII freezes every physical text line that can affect the Forge
artifact or its validation route and records each file in the dedicated ledger:

```text
docs/forge-1.20.1-line-by-line-port-audit.md
Pass 1 frozen scope: 252 files / 36,741 lines / 1,533,469 bytes
Pass 1 frozen-snapshot drift check: 0 mismatches
Pass 1 coverage: 252 / 252 files read to EOF
Pass 1 findings: 15
```

The scope includes every main/test Java file admitted by `sourceSets`, every
packaged shader/resource, build/settings/properties/wrapper logic, and the audit
inventory generator itself. The uncompiled original client tree is comparison
ground truth rather than a Forge artifact input. Generated output, dependency
sources, logs, saves, documentation, `.codegraph`, `.agents`, and the user's
untracked `capsettings.cap` are explicitly excluded from line-coverage totals.

Pass 1 found measurable deviations in verification-flag parsing, CPU topology,
sparse-buffer reuse, verifier session ownership, control-flow exception cost,
normal/Oculus resource ownership, liquid renderer initialization, multi-source
tint evaluation, fullscreen shader defines/resource sharing, viewport mutation,
fatal async error propagation, one extra HIZ algorithm guard, and stair-field
access after reobfuscation. None was repaired until the full frozen pass and
hash check were complete.

The complete repair batch now restores the original mechanisms or their narrow
Forge 1.20.1 representation adapters. Integrated validation completed without
launching the client, as explicitly required for this audit goal:

```text
rtk git diff --check
passed

rtk test .\\gradlew compileJava --stacktrace --console=plain
BUILD SUCCESSFUL

rtk test .\\gradlew test --stacktrace --console=plain
BUILD SUCCESSFUL
```

This is not yet a completion claim. The repaired worktree must be frozen again,
all ledger rows must restart from pending at A001, and another complete read to
EOF must produce zero new findings. Any new finding forces another deferred
repair batch and another full pass from A001. No historical review credit,
compile success, unit test, or visual acceptance substitutes for that terminal
zero-new-finding pass.

### XXVII.2 Pass 2 complete review and repair batch

Pass 2 independently re-froze **261 files / 37,401 physical text lines /
1,557,822 bytes**. Every B001-B261 row was read to EOF, and the post-review
inventory comparison proved 261 inventory paths, 261 ledger paths, and zero
hash/line/byte/missing/extra mismatches before any repair began.

The complete pass and its independent repair cross-review identified 22 new
findings (P2-F001 through P2-F022). They covered async-stop ordering, no-sky
light readiness and retry bookkeeping, exact diagnostic flags, fatal model and
MDIC failure propagation, reobfuscation-safe sprite/lightmap/atlas access,
model-request and upload ownership, biome/tint invariants, global empty-VAO
ownership, packaged shader loading, viewport selection, malformed NBT import,
and pending UploadStream target lifetime. All 22 were repaired only after the
full frozen pass had completed.

The forced post-repair gate passed without launching the client:

```text
rtk git diff --check
passed

rtk test .\\gradlew clean test jarJar --rerun-tasks --stacktrace --console=plain
BUILD SUCCESSFUL

voxy-forge-0.2.17-beta-forge-all.jar
size=12,573,346 bytes (11.99 MiB)
SHA-256=0fc19ef1d2207bf183ac8581830b589291f15b9230f63d30805127b4d54c9669
entries=451
bundled Minecraft/LWJGL/Oculus/Embeddium classes=0
```

Production bytecode directly references the reobfuscated targets
`SpriteContents.m_246162_`, `LightTexture.f_109870_`,
`DynamicTexture.m_117963_`, and `TextureAtlas.f_276072_`; the prior literal
development-name reflection hazards are absent from those paths. Pass 2 is
therefore repaired, but XXVII remains open until a new full-tree pass over the
post-repair hashes yields zero new findings.

### XXVII.3 Pass 3 complete review and repair batch

Pass 3 independently froze **270 files / 38,118 physical text lines /
1,591,782 bytes** after the complete Pass 2 repair and artifact gate. Every
C001-C270 row was read to EOF with no inherited coverage. The three independent
partitions and the root partition each recomputed their assigned hashes, line
counts, and byte lengths; the final whole-inventory comparison proved 270
ledger paths, 270 current paths, and zero hash/line/byte/missing/extra
mismatches before repairs began.

Three new findings were confirmed and repaired as one deferred batch:

- the common logger could resolve the client-only `Minecraft` class on a
  dedicated server; client HUD bytecode is now isolated behind the Forge
  distribution guard;
- the 1.20.1 NBT adapter accepted wrong-type mapper tags through vanilla's
  defaulting getters; tag-type-aware numeric/string fallbacks and the required
  compound invariant now match original behavior;
- the pipeline depth helper captured only the draw framebuffer while its
  `GL_FRAMEBUFFER` bind changed both draw and read owners; both bindings are
  now captured and restored independently.

`rtk git diff --check` and `rtk test .\\gradlew compileJava test --stacktrace
--console=plain` passed, including focused bytecode, malformed-NBT, and
framebuffer-state tests. No client was launched. Pass 3 is repaired but is not
the terminal proof: Pass 4 must re-freeze and physically reread the complete
post-repair tree, and any new finding will force another deferred repair batch
and full pass.

### XXVII.4 Pass 4 complete review and repair batch

Pass 4 independently froze **271 files / 38,237 physical text lines /
1,596,803 bytes** after the Pass 3 repair. D001-D271 were physically reread to
EOF across three independent partitions plus the root resource/shader/test
partition. Every partition recomputed its assigned path, kind, line count,
byte count, and SHA-256. The final whole-tree comparison proved 271 frozen
rows, 271 current rows, and zero hash/line/byte/kind/missing/extra mismatches
before any production repair began.

Five new findings were frozen and repaired as one deferred batch:

- original `ModelFactory` merges a contained fluid model's biome-colour
  dependency after dedupe and feeds one state into CPU metadata, GPU flags,
  colour/base-index selection, and biome-colour registration. Forge now merges
  that dependency into its shared tint plan at the same lifecycle point;
- six common-layer adaptations bypass original `Logger` through direct SLF4J
  loggers; all six now route through the distribution-safe original common
  logger with exact message/level/throwable behavior;
- the Forge-only Embeddium injection state owner now round-trips indexed buffer
  names/ranges and generic bindings, array/draw/dispatch-indirect targets,
  image unit 0, unpack state, separate stencil-face state, and legal polygon
  state. Target-specific zero texture binding prevents a 2D clear from
  destroying Oculus 1D/3D/rectangle bindings, with non-2D queries limited to
  actual shaderpack sampler slots rather than every target on every unit;
- repair cross-review of the actual Oculus 1.8 bytecode found that
  `TEXTURE_RECTANGLE.getGlType()` incorrectly reports `GL_TEXTURE_3D`. The
  Forge adapter now maps all four enum identities to their correct GL targets;
- the initial GL-boundary test constrained isolated state helpers but not the
  complete active call chain. It now locks capture, restore arming, first
  mutation ordering, the outer `finally`, every non-2D delegate hop, and the
  four actual Oculus enum identities.

The detailed per-line evidence and row-to-finding mapping are in
`docs/forge-1.20.1-line-by-line-port-audit.md` as P4-F001 through P4-F005.
Independent repair review found and corrected the initially incomplete
P4-F001 CPU-only fix and the Oculus rectangle-target defect before the batch
gate. `rtk git diff --check` and `rtk test .\\gradlew clean test jarJar
--rerun-tasks --stacktrace --console=plain` passed. The resulting all-JAR is
12,584,005 bytes, SHA-256
`30c58e9dc85da6c56e5de10e0d343d1a9f365c93495ea0e66e1edcf4a9482570`,
contains 457 entries, and contains zero bundled Minecraft/Forge/LWJGL/Oculus/
Embeddium/Sodium dependency classes. No client was launched. Pass 4 cannot be
the terminal pass because it found defects; Pass 5 must freeze and reread the
entire repaired tree from zero inherited coverage.

### XXVII.5 Pass 5 complete review and repair batch

Pass 5 independently froze **272 files / 38,859 physical text lines /
1,627,437 bytes** after the complete Pass 4 repair and gate. E001-E272 were
physically reread to EOF across four disjoint partitions. Each partition
recomputed its assigned path, kind, line count, byte count, and SHA-256; a
separate whole-inventory comparison then proved 272 frozen rows, 272 current
rows, and zero missing, extra, reordered, kind, line, byte, or hash mismatches
before production repair began.

Pass 5 found one new P3 parity deviation. Forge retained the historical
`voxy.disableTrackedObjectAllocations` alias when the original
`voxy.ensureTrackedObjectsAreFreed` property was absent. Besides being absent
from original Voxy, the alias used `Boolean.getBoolean` and therefore accepted
case variants that original verification flags reject. The alias is now
removed. The active helper consumes only the original property, defaults it to
enabled, and recognizes only exact lowercase `"true"`; the focused behavioral
test covers the absent, `true`, `false`, and `TRUE` cases and passes.

The detailed evidence is P5-F001 in
`docs/forge-1.20.1-line-by-line-port-audit.md`. Pass 5 cannot be the terminal
pass because it found a defect. The repaired executable inventory must be
frozen again and reread from zero inherited coverage as Pass 6. No client was
launched.

### XXVII.6 Pass 6 terminal zero-finding line audit

Pass 6 froze the complete post-P5-F001 executable inventory at **272 files /
38,854 physical text lines / 1,627,177 bytes** and inherited zero review
coverage. Four disjoint partitions physically reread all 272 files to EOF,
using CodeGraph first for production Java and comparing each production path
against original Voxy plus the necessary Forge 1.20.1, Embeddium, and Oculus
contracts. The partition totals were F001-F074: 74 files / 9,700 lines /
384,104 bytes; F075-F112: 38 / 9,745 / 410,534; F113-F164: 52 / 9,714 /
408,613; and F165-F272: 108 / 9,695 / 423,926. Every partition reported zero
new findings.

Each partition independently re-matched path, kind, line count, byte count,
and SHA-256. A separate whole-inventory comparison then proved 272 frozen rows,
272 regenerated rows, and zero missing, extra, reordered, path, kind, line,
byte, or hash mismatches. The detailed F001-F272 evidence and completion state
are recorded in `docs/forge-1.20.1-line-by-line-port-audit.md`.

`rtk git diff --check` passed. The forced final `rtk test .\gradlew clean test
jarJar --rerun-tasks --stacktrace --console=plain` gate passed with 32 test
suites / 94 tests / 0 failures / 0 errors / 0 skipped tests. The resulting
`voxy-forge-0.2.17-beta-forge-all.jar` is 12,583,915 bytes, has SHA-256
`94cd04bd5ff74f6e29198d9ac505560b2cf8c45c76e7dec177ec88827ec2c4c5`,
contains 457 ZIP entries, and contains zero bundled Minecraft, Forge, LWJGL,
Oculus/Iris, Embeddium, or Sodium dependency classes. No client was launched.
Pass 6 therefore satisfied the static completion rule at the time, but the
first subsequent client launch exposed P7-F001 and invalidated it as terminal
readiness evidence.

### XXVII.7 Post-audit Mixin runtime invalidation

The first client launch after Pass 6 failed before window initialization with
Mixin 0.8.5 rejecting
`ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.shouldResetViewport(ZZZ)Z`
as a non-private static method. Original Voxy's `MixinLevelRenderer` keeps the
equivalent viewport action inside its private injection. Forge had extracted
the predicate only so its unit test could call it directly, but package-level
visibility made it an illegal member for Mixin's MAIN applicator phase. This
is P7-F001 / P1 in the detailed line-audit ledger.

The repair preserves the predicate behavior while restoring a valid Mixin
contract: it is now `@Unique private static voxy$shouldResetViewport`, and the
test uses reflection to assert `private` and `static` before checking all four
behavior cases. The focused compile/test gate passed, and an independent
review confirmed both the repair and that no other Forge mixin static method
has the same visibility defect.

A second client launch, explicitly anchored at `D:\Projects\voxy`, passed the
real Mixin application phase, entered the default world, opened the original
persistent WorldEngine, reached the Embeddium command-generation hook, and
created `ForgeOriginalVoxyRenderPipeline` with `MDICSectionRenderer`. The
client was left running for user validation. Pass 6's static evidence remains
historically valid, but XXVII is reopened: a newly frozen complete zero-finding
pass is required before the line-audit status can return to `complete`.

### XXVII.8 Pass 7 complete review and repair batch

Pass 7 froze the post-P7-F001 tree at 272 files / 38,876 physical text lines /
1,627,815 bytes. Four disjoint clean-room partitions re-read every row to EOF,
and both the partition checks and a final whole-inventory comparison reported
zero path, kind, line, byte, SHA-256, missing-path, or extra-path drift. Pass 7
confirmed four additional parity defects only after preserving that frozen
coverage:

- P7-F002 / P3: the original icon PNG was packaged, but Forge metadata omitted
  `logoFile`, so Forge's mod-list owner could not expose it;
- P7-F003 / P2: the Oculus patch validator skipped a null per-buffer blend
  state and deferred the original load-time rejection into a translucent-render
  NPE;
- P7-F004 / P2: top-level node set mutation and its `workCounter` delta were
  separated by unlocking, allowing the worker to drain uncounted work and enter
  the negative-counter one-second sleep;
- P7-F005 / P3: a short JSON blend array became a valid per-buffer off state in
  Forge instead of following the original deserializer catch/partial-map path.

The repair batch maps `assets/voxy/icon.png` through Forge's exact `logoFile`
key, restores original blending-first/null and short-array behavior, and moves
the top-level set mutation, counter delta, and unpark back into the producer
critical section shared with the worker snapshot. Focused tests cover processed
metadata/icon bytes, the real Oculus load-error boundary, short-array partial
mapping, and both producer and worker sides of the top-level lock protocol.
`git diff --check`, focused gates, and the forced complete suite passed: 35
suites / 98 tests / zero failures, errors, or skips. Two independent repair
reviews found the production changes clean; one strengthened the async protocol
test before the final focused rerun. No client was launched.

Pass 8 is frozen at 275 files / 39,143 physical text lines / 1,639,316 bytes.
Every H001-H275 row starts pending and inherits no Pass 7 review credit. A full
zero-finding Pass 8 remains mandatory before this audit may close.

### XXVII.9 Passes 8 through 14 and terminal static closure

Passes 8 through 13 each re-froze and physically reread all 275 executable
inventory files with zero inherited review credit. Every pass independently
reconciled `path / kind / lines / bytes / SHA-256` before its repair batch:

- Pass 8 found and repaired one strong-retention error in the Forge-only
  historical `ClientLevel` identity guard; weak identity membership now keeps
  late same-session recognition without retaining old dimensions.
- Pass 9 restored original `LevelRenderer#setLevel` `HEAD` identity teardown
  instead of the delayed END-tick fallback.
- Pass 10 repaired three original-contract omissions in shader-printf batch
  replacement, the complete `ModelQueries` accessor surface, and duplicate
  completed-model fail-fast behavior.
- Pass 11 corrected three stale comments that contradicted the completed XX.4
  investigation and the active dimension-switch owner. No runtime code changed.
- Pass 12 repaired seven issues: the opt-in NodeManager integrity verifier,
  bounded active GL-state capture, renderer-generation chunk-bound reseeding,
  GPU-timing duplicate-free visibility, all remaining original Logger routes,
  the inner-to-leaf request postcondition, and ModelFactory biome fail-fast/
  default ownership.
- Pass 13 removed the final redundant local lifecycle mask in
  `SharedIndexBuffer`; the two actual terminal owners remain unchanged and
  source-tested.

Because Pass 13 changed production and test code, Pass 14 started again from
zero at the repaired hashes. Four disjoint clean-room partitions reread all
**275 files / 40,139 physical text lines / 1,689,515 bytes** to EOF:
N001-N075 = 75 files / 9,835 lines / 390,076 bytes; N076-N113 = 38 / 9,811 /
412,336; N114-N165 = 52 / 10,286 / 431,853; and N166-N275 = 110 / 10,207 /
455,250. Every Java file used CodeGraph before its physical read and was
compared against original Voxy plus the required Forge 1.20.1, Embeddium, and
Oculus contracts. Each partition reported zero findings. The final whole-tree
comparison matched 275 frozen and 275 current rows with zero path, kind, line,
byte, SHA-256, missing, extra, or order drift.

The forced post-audit gate passed without launching the client:

```text
rtk test .\gradlew clean test jarJar --rerun-tasks --stacktrace --console=plain
exit=0
35 test suites / 110 tests / 0 failures / 0 errors / 0 skipped
git diff --check=passed

voxy-forge-0.2.17-beta-forge-all.jar
size=12,589,719 bytes (12.01 MiB)
SHA-256=6685922c2a42a705635aa1cabb4fa0bc47e20034c774b045cd02456ffaf0a0c6
entries=458
duplicate entries=0
bundled Minecraft/Forge/LWJGL/Oculus/Embeddium/Sodium classes=0
```

The only packaged `me/cortex/voxy/client/**` class is the explicitly selected
original `SemaphoreBlockImpersonator` used by the Forge Embeddium chunk-job
queue adaptation; no other uncompiled original client implementation is in the
artifact. Seven expected JarJar runtime libraries remain nested, with the
compressed RocksDB JNI payload accounting for most of the all-JAR size.

Pass 14 therefore satisfies XXVII's exhaustive static completion rule. The
consolidated post-repair runtime regression then passed on 2026-07-14. The
anchored `runClient` entered `新的世界` with Embeddium and Oculus, opened the
original persistent storage chain, and repeatedly created the formal
`ForgeOriginalVoxyRenderPipeline` / `MDICSectionRenderer` route. The log proves
shader disable/re-enable and repeated Oculus/Voxy pipeline rebuilds, transitions
through overworld, Nether, and End storage owners, disconnect/re-entry, and the
complete renderer, WorldEngine, network-session, and Forge-instance shutdown
chain. The user reported no visual or lifecycle anomaly, and Gradle exited 0
with `BUILD SUCCESSFUL` after the client closed normally.

The final clean packaging rerun also exited 0: **35 suites / 110 tests / 0
failures / 0 errors / 0 skipped**. The resulting
`voxy-forge-0.2.17-beta-forge-all.jar` is **12,589,718 bytes (12.01 MiB)**,
SHA-256
`50a27befcfb8d9390aac4db77ab76cf25afe9b4a1fa0aa30c554b7654a5b5502`,
with **458** ZIP entries, zero duplicates, seven expected nested JarJar
libraries, and zero bundled Minecraft/Forge/LWJGL/Oculus/Embeddium/Sodium
classes. Required Forge metadata, access transformer, refmap, Mixin config,
icon, entrypoint, formal render owners, and all 47 shader resources are present;
the Fabric descriptor and retired config screen are absent.

The static and runtime evidence gates are therefore complete. This remains an
evidence boundary, not a mathematical promise that no future runtime defect can
exist. The user's 2026-07-14 finalization request approves the release/readiness
decision and authorizes the audited worktree to be packaged, committed, and
pushed. The removed status-only readiness fields have no replacement value to
flip; acceptance is based on the real owners and the evidence above.
`capsettings.cap` remains user-owned, untracked, and excluded.

## XXVIII formal Embeddium 0.3.31 compatibility repair

The packaged XXVII artifact passed the development client but failed during the
first formal-client run. The user-supplied 2026-07-14 error bundle proves an
`ExceptionInInitializerError` in
`ForgeOriginalVoxyQuadMaterialBridge.<clinit>`, caused by
`ClassNotFoundException` for
`org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevelHolder`.
The formal instance was Forge 47.4.20, Oculus 1.8.0, and Embeddium
`0.3.31+mc1.20.1`, which is inside the declared `[0.3.31,0.4)` dependency range.
Java 21 and the newer Forge patch level were not the failure source.

The development classpath had silently selected Embeddium
`0.3.32-beta.9999+mc1.20.1`. Its internal
`SpriteTransparencyLevelHolder` API does not exist in the formal 0.3.31 JAR.
This was therefore a real platform-adapter compatibility defect missed by the
XXVII source and development-runtime gates; the previous whole-mod acceptance
and XXVII final artifact are no longer the current release evidence.

The repair keeps the declared minimum at 0.3.31 and removes the hard linkage to
the post-0.3.31 internal class. The Forge material adapter now mirrors
Embeddium 0.3.32's exact three-level pixel-alpha contract locally:

- any alpha in `1..254` classifies the sprite as `TRANSLUCENT`;
- otherwise any alpha `0` classifies it as `TRANSPARENT`;
- otherwise the sprite is `OPAQUE`.

The source image is the Forge 1.20.1 `SpriteContents#getOriginalImage()` owner,
and results are cached in a synchronized weak-key map so the cache does not
extend the atlas contents' lifetime. This is a bounded Embeddium-version adapter,
not an alternate model or renderer route; all callers continue through the
same original Voxy model-material and software-bake chain.

The focused classifier/source-contract tests passed. A forced clean gate then
compiled and packaged the complete project while explicitly substituting the
exact Embeddium 0.3.31 JAR from the formal instance:

```text
rtk powershell ... .\gradlew clean test jarJar
  -PvoxyEmbeddiumDevJar=<formal Embeddium 0.3.31 JAR>
  --rerun-tasks --stacktrace --console=plain
exit=0
36 test suites / 113 tests / 0 failures / 0 errors / 0 skipped
voxy-forge-0.2.17-beta-forge-all.jar
size=12,591,959 bytes (12.01 MiB)
SHA-256=d53b8c6c5c495020556e63b41fb77f42f9eda7df9fbbf326fece151b462f3a06
entries=459 / duplicate entries=0
bundled Minecraft/Forge/LWJGL/Oculus/Embeddium/Sodium classes=0
post-0.3.31 transparency-holder reference=absent
watertight software-raster quad coverage=present
custom-renderer empty-model mapping=present
```

The minimum-frontend repair has now passed formal startup far enough to enter a
large 0.3.31-based modpack and render the original LOD route. That run exposed a
second current-worktree regression: with shaderpacks disabled, stable
face-local diagonal marks appeared on baked LOD terrain. The shaderpack fog wall
reported in the same run was independently identified by the user as modpack
configuration and is explicitly outside this repair.

### XXVIII.2 watertight software-bakery quad coverage

The no-shader screenshots match the diagonal model-texture seam previously
repaired and accepted in XVI (`7d1bc34e`). XVI established that independently
testing the `(0,1,2)` and `(2,3,0)` triangles makes their shared diagonal a
coverage boundary; transformed float values can leave a clear texel rejected
by both triangles, which is then magnified over the LOD face. It repaired this
by testing the four outer quad edges once and retaining the triangle split only
as the barycentric interpolation basis.

The XXV source reconstruction later restored the original Voxy
`rasterTriangle(false/true)` implementation line-for-line. XXVII's exhaustive
audit retained it, and `SoftwareRasterizerTest` incorrectly locked the resulting
partial edge-ownership coverage as parity. This was a real audit regression:
the original source remains the baseline, but an already runtime-proven,
documented Forge software-bakery correction must not be removed merely to
reproduce a known raster coverage defect.

XXVIII.2 restores XVI's exact four-edge convex-quad coverage algorithm in the
current `SoftwareRasterizer`. Degenerate triangle-as-quad encodings remain
supported because a zero-length outer edge imposes no negative-edge constraint.
The two triangles still select the same vertices and UV/depth interpolation;
only their internal diagonal ceases to decide pixel ownership. The replacement
test requires all 16 pixel centers of the controlled 4x4 quad to be covered,
where the reverted implementation covered only 9 and left deterministic edge
gaps.

The focused rasterizer test and the forced clean 0.3.31 gate above pass. Visual
confirmation in the affected modpack remains mandatory: verify the repeated
diagonal marks are absent with shaderpacks disabled and check representative
cutout/translucent blocks. XXVIII remains uncommitted and not releasable until
the user reports that result.

### XXVIII.3 custom block-entity model crash parity repair

The user's `错误报告-2026-07-14_19.59.12.zip` records a successful 0.3.31 world
entry and roughly five minutes of active original-route rendering before the
model-factory worker failed. The first fatal exception was:

```text
Original Voxy software model bake failed for block-state 5731:
baked-model-missing-or-custom
```

The persistent WorldEngine Mapper RocksDB entry for typed block-state key
`(1 << 30) | 5731` decodes to
`butcher:pestleandmortarblock[facing=south,animation=0]`. The supplying
`butcher-2.7.5-forge-1.20.1.jar` contains a tile renderer, animated geometry,
and no ordinary baked-quad representation for that state. Forge therefore
reports its `BakedModel` as `isCustomRenderer()`.

Original `SoftwareModelTextureBakery` explicitly leaves block-entity model
support as a TODO. It does not turn the absence of ordinary quads into an
exception: the bake completes with empty face data and `ModelFactory` publishes
or deduplicates the empty model mapping. The Forge adapter instead combined
`model == null` and `model.isCustomRenderer()` into one failure reason, while
XXVII's strict failure propagation converted that adapter-only classification
into a client-fatal exception. This is a confirmed port deviation, not a
failure caused by XXVIII.2's quad coverage algorithm.

XXVIII.3 now distinguishes three model routes. A genuinely missing `BakedModel`
remains `baked-model-missing` and therefore fatal. A custom renderer produces
the original-equivalent intentional empty bake with failure reason `none`, so
the normal `ModelFactory` dedupe path publishes a valid block-state mapping and
removes the in-flight request. Ordinary models continue through the existing
quad/material/raster path unchanged. No synthetic geometry or substitute model
is introduced; the animated block entity is simply absent from distant LOD,
matching the original unsupported-feature boundary.

The focused three-route classification test passes. The current forced-clean
gate against the exact affected Embeddium 0.3.31 JAR passes **36 suites / 113
tests / 0 failures / 0 errors / 0 skipped** plus reobfuscated JarJar. The current
artifact statistics are recorded in the gate above. Runtime confirmation must
cover both the no-shader diagonal scene and continued travel/loading around the
Butcher pestle-and-mortar state without a model-factory crash. The user later
confirmed the combined final JAR passed both checks without a reported issue.

### XXVIII.4 Chunky generated-chunk ownership parity repair

The user reported persistent, chunk-aligned LOD holes after a Chunky
pregeneration run, concentrated in large oceans at roughly one or two missing
chunks per ten. The affected chunks had never entered the vanilla client
radius. Loading one as a vanilla chunk and moving away caused its LOD to appear
permanently, proving that the first generated-chunk ingest had been omitted.

The exact installed Chunky 1.3.146 bytecode shows that Forge
`ForgeWorld.getChunkAtAsync` adds a `CHUNKY` ticket, requests the holder's FULL
future, converts it to `CompletableFuture<Void>` with `allOf`, and registers an
asynchronous completion callback which removes the ticket. The existing Forge
Voxy mixin injected at the returned Void future, scheduled a second asynchronous
server callback, and called `getChunkNow(x,z)`. Ticket removal and Voxy's lookup
were sibling completion tasks. Once the generated chunk was no longer resident,
`getChunkNow` returned null and the omission was silent. Fast homogeneous ocean
generation makes this scheduling/unload window easier to hit; there is no
water-only empty-section filter because water sections are non-air and the
ingest loop accepts every non-null section.

Original Voxy's Fabric `MixinFabricWorld` does not perform a coordinate lookup.
It wraps the exact FULL result future and consumes the successful chunk directly,
type-checks it as `LevelChunk`, and passes that object to `VoxelIngestService`.
Forge 1.20.1 represents that result as
`Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>`. XXVIII.4 ports the same
ownership contract by redirecting `ChunkHolder.getOrScheduleFuture` through a
`thenApply` that returns the unchanged `Either`. The post-completion `getChunkNow`,
server reschedule, coordinate snapshot, and ticket-removal race are removed.

Because the enclosing target is a third-party class declared `remap=false`, the
Minecraft invocation target receives no annotation-processor refmap entry. The
mixin therefore declares both the Forge 1.20.1 development Mojmap name
`getOrScheduleFuture` and its verified production runtime name `m_140049_`;
both are optional injection points and exactly one matches in each environment.
A source-contract regression test requires the standard-Mixin redirect,
exact-result capture, and both names,
while rejecting `getChunkNow`, `thenRunAsync`, and the former RETURN injector.
The forced-clean exact-Embeddium-0.3.31 gate passes **36 suites / 114 tests / 0
failures / 0 errors / 0 skipped** plus reobfuscated JarJar. The all-JAR is
**12,592,017 bytes**, SHA-256
`d8fa3c7cf01f463597e2e0613433d906592b6a1e2edcf85358750ad1d02e8add`,
with 459 entries, zero duplicates, and zero bundled platform/frontend classes.
The user completed the final Chunky pregeneration and remote-ocean visual gate
without reproducing a hole, and also reconfirmed the no-shader seam and custom
renderer repairs. On 2026-07-14 the user approved the XXVIII commit/push and
marked the project beta-complete.

## XXIX documentation reconciliation and legacy-route closure

### XXIX.1 current owner verification, 2026-07-16

A fresh CodeGraph construction/caller trace corrected a misleading cleanup
assumption. The package-local Forge `RenderGenerationService`,
`RenderDataFactory`, `BuiltSection`, `AsyncNodeManager`,
`BasicSectionGeometryData`, and `MDICSectionRenderer` are not a second legacy
route. They are constructed by `ForgeOriginalVoxyRenderSystem` and carry the
single visible original-parity geometry/MDIC chain:

```text
Embeddium/Acedium render hook
 -> ForgeVoxyInstance.renderOriginalVoxyAfterTerrain
 -> ForgeOriginalVoxyModelPipeline
 -> ForgeOriginalVoxyRenderSystem
 -> RenderGenerationService -> RenderDataFactory -> BuiltSection
 -> AsyncNodeManager -> BasicSectionGeometryData
 -> HierarchicalOcclusionTraverser -> MDICViewport -> MDICSectionRenderer
```

The actual historical `ForgeCpu*`, `ForgeGpuGeometry*`,
`ForgeMdicCommand*`, `ForgeMdicVisibility*`, `ForgeSectionGeometry*`, and
`ForgeVoxyBuiltSection*` island was already removed in XIX, with its remaining
runtime/config names removed in XXII. Exact source scans confirm those families
remain absent and that `ForgeVoxyInstance` owns only the current
`ForgeOriginalVoxyModelPipeline`. `LegacyGeometryRouteRetirementTest` now locks
both facts: the retired island cannot silently return, and the active generic-
named Forge geometry types cannot be mistaken for deletion candidates.

This round also reconciles the active readiness, frontend, and source-area docs
with XXVI-XXVIII. All pre-XXVI claims that enabled changes leave a process-owned
service pool are explicitly historical and superseded. The old water,
ingest/removal/light, active-world, and prototype-retirement step lists no longer
appear as current work. IterationT remains the only explicit post-migration
compatibility TODO.

Validation against the local exact Forge frontends passed:

```text
.\gradlew compileJava test
  -PvoxyEmbeddiumDevJar=.gradle/local-inputs/embeddium-0.3.31+mc1.20.1.jar
  -PvoxyOculusDevJar=Oculus-1.20.1-new/build/libs/oculus-mc1.20.1-1.8.0.jar
BUILD SUCCESSFUL
37 test suites / 116 tests / 0 failures / 0 errors / 0 skipped
git diff --check=passed
```

## XXX Forge-local compatibility debt hardening

### XXX.1 installed Oculus contracts return to original typed ownership, 2026-07-17

The original Voxy route reads
`WorldRenderingSettings.INSTANCE.getBlockStateIds()` and
`ShadowRenderer.ACTIVE` directly. The Forge adapter had replaced those installed-
frontend contracts with reflection that could convert an Oculus ABI mismatch
into an ordinary null/false result. That was a Forge-local compatibility risk,
not a platform requirement.

`ForgeOculusWorldRenderingSettingsBridge` and
`ForgeOculusShadowStateBridge` now use the same direct typed Oculus owners as
original Voxy. The existing structured world-settings failure result remains for
runtime/linkage reporting, while shadow state no longer has a reflective
false-on-error path. Once Oculus is installed, compile/link failure therefore
identifies an incompatible frontend at its real boundary.

The audit also compared `ForgeOriginalVoxyOculusRenderPipelineData` against the
original `IrisVoxyRenderPipelineData` and the exact local Oculus 1.8.0
`DynamicLocationalUniformHolder` / `SamplerHolder` bytecode. The Forge adapter
already covers every explicit Oculus 1.8.0 uniform method used by the holder and
is broader than the original adapter. The generic `addDynamicUniform` failure
and unsupported default-sampler branch also exist in original Voxy; they are
therefore inherited upstream gaps, not Forge-local compatibility debt, and were
not hidden or replaced by speculative fallback behavior.

### XXX.2 optional installed integrations fail visibly on ABI drift

Optional absence remains supported, but an installed integration can no longer
silently lose its hook:

- Chunky's Mojmap/SRG `getOrScheduleFuture` redirects now share a Mixin group
  with `min = 1`. When `ForgeWorld` exists, at least one runtime mapping must
  match; the exact successful FULL future still feeds the original ingest
  callback.
- Acedium's optional `RenderPipeline.renderFrame` return injection now uses
  `require = 1`. `@Pseudo` still permits Acedium to be absent, while a present
  but incompatible target fails at Mixin application instead of disabling the
  post-terrain Voxy draw invisibly.
- Vivecraft remains reflection-based because it is not a hard Forge dependency.
  The bridge verifies that `instance()` is static and returns the API type and
  that `getCurrentRenderPass()` returns an enum containing `VANILLA`. An absent
  Vivecraft mod remains the normal default route; an installed incompatible API
  skips Voxy for that pass instead of sharing the vanilla viewport.

`ForgeCompatibilityDebtHardeningTest` locks the direct Oculus ownership, both
optional Mixin minimum-match contracts, and the Vivecraft API-shape validator.
This round changes adapter failure semantics only; it does not create an
alternate renderer, alter readiness, or change the formal original-Voxy render
chain. IterationT remains the separate explicit shaderpack compatibility TODO.

Validation against the exact local Forge frontends passed:

```text
.\gradlew compileJava test
  -PvoxyEmbeddiumDevJar=.gradle/local-inputs/embeddium-0.3.31+mc1.20.1.jar
  -PvoxyOculusDevJar=Oculus-1.20.1-new/build/libs/oculus-mc1.20.1-1.8.0.jar
BUILD SUCCESSFUL
38 test suites / 119 tests / 0 failures / 0 errors / 0 skipped
git diff --check=passed
```

## XXXI original optional Iris/Oculus dependency policy restored

### XXXI.1 dependency and class-loading boundary, 2026-07-17

Original Voxy requires Sodium but does not list Iris in `fabric.mod.json`.
`IrisUtil.IRIS_INSTALLED` guards Iris calls, `RenderPipelineFactory` attempts the
Iris pipeline only when that integration is installed and usable, and otherwise
constructs `NormalRenderPipeline`. The Forge metadata had incorrectly made both
Embeddium and Oculus hard prerequisites.

The Forge policy now matches the original:

```text
Embeddium: mandatory=true
Oculus: mandatory=false
```

`ForgeOculusAvailability` is the Oculus-free runtime boundary. Shaderpack,
shadow, reload, pipeline-generation, block-state-id, and reload-required calls
test that boundary before entering methods that directly link Oculus classes.
When Oculus is absent, pipeline capture reports `oculus-not-installed`, custom
block-state mapping is null, reload is a no-op, shadow/shaderpack state is false,
and `ForgeOriginalVoxyRenderPipeline` owns its existing normal resources. When
Oculus is installed, the XXX direct typed contracts and strict ABI failure
semantics remain in force.

`ForgeVoxyMixinPlugin` applies every non-Oculus mixin normally but excludes the
`ForgeOriginalVoxyOculus*` mixins when the loading mod list has no `oculus`
entry. This prevents optional target classes and handler descriptors from being
loaded on the Embeddium-only client while preserving strict injection contracts
once Oculus is installed.

Gradle continues to require the Oculus jar as a `compileOnly` adapter input. A
supplied jar is included in development runtime by default;
`-PvoxyOculusDevRuntime=false` removes it specifically for the absent-frontend
qualification. Release metadata does not bundle or require Oculus.

### XXXI.2 two-matrix runtime qualification

Both frontend matrices entered the same existing quick-play world and built the
formal original-Voxy renderer:

```text
Embeddium only
  Oculus mod state: MISSING
  block-state mapping source=oculus-not-installed present=false size=0
  ForgeOriginalVoxyRenderSystem created
  normal pipeline resources selected
  normal client/window close -> complete Voxy, WorldEngine, worker, and server shutdown

Embeddium + Oculus 1.8.0 + Complementary Unbound
  external opaque/translucent Voxy shader patches applied
  Voxy Oculus mixins present in transformed pipeline classes
  block-state mapping source=oculus-world-rendering-settings present=true size=24056
  ForgeOriginalVoxyRenderSystem created
  Oculus pipeline resources selected
  normal client/window close -> complete Voxy, WorldEngine, worker, and server shutdown
```

The installed-Oculus compile/test matrix passed `39` suites and `122` tests with
zero failures, errors, or skips. The absent-Oculus matrix intentionally does not
run the three parser/bytecode tests whose subject is Oculus itself; its loader,
Mixin, normal-pipeline, world-entry, and shutdown contracts were validated by
the real client run. `ForgeModMetadataParityTest`,
`ForgeCompatibilityDebtHardeningTest`, and `ForgeVoxyMixinPluginTest` lock the
metadata, availability ordering, direct installed-frontend access, and Mixin
selection rules.

This restores dependency parity without weakening installed Oculus
compatibility and does not change renderer readiness. IterationT remains the
separate explicit shaderpack compatibility TODO.

## XXXII no-Oculus Nether render-distance fog parity

### XXXII.1 classify the 1.20.1 foggy-dimension branch as distance fog, 2026-07-17

The Embeddium-only runtime matrix exposed a Forge-local mismatch: Overworld and
End LOD were visible, but Nether `DimensionSpecialEffects#isFoggyAt` fog ended
at the vanilla chunk boundary and hid otherwise live formal LOD. The same run
created the Nether WorldEngine and the
`ForgeOriginalVoxyRenderPipeline`/`MDICSectionRenderer`, so renderer ownership
and ingest were not the cause.

Original `MixinFogRenderer` always sets `FogData.renderDistanceStart/End` to
infinity while Voxy rendering is active. Forge 1.20.1 exposes only one fog pair:
ordinary terrain distance fog ends at the full render distance, while the
foggy-dimension branch ends at `min(renderDistance, 192) * 0.5`. The earlier
near-render-distance heuristic recognized only the first formula.

`ForgeOriginalVoxyFogParameters` now maps both clear-air formulas to original
render-distance fog. Its source-aware classifier keeps fluid fog environmental
and gives blindness/darkness priority over the dimension branch. The
`ViewportEvent.RenderFog` adapter and final-blit capture share that classifier,
which disables the Nether boundary fog without weakening water, lava,
powder-snow, blindness, or darkness fog. Shaderpack-active Oculus continues to
own its fog path and bypasses this adapter.

Validation passed:

```text
.\gradlew cleanTest compileJava test
  -PvoxyEmbeddiumDevJar=.gradle/local-inputs/embeddium-0.3.31+mc1.20.1.jar
  -PvoxyOculusDevJar=Oculus-1.20.1-new/build/libs/oculus-mc1.20.1-1.8.0.jar
BUILD SUCCESSFUL
40 test suites / 125 tests / 0 failures / 0 errors / 0 skipped
```

The user completed the no-Oculus Nether visual gate on 2026-07-17: the vanilla
chunk-boundary fog no longer hid formal LOD, while Overworld and End behavior
remained correct. This closes XXXII without changing renderer readiness or
IterationT.

## XXXIII optional-integration identity and ABI closure

### XXXIII.1 optional Mixin selection follows real Forge mod identities, 2026-07-17

The Forge Mixin plugin now rejects the Chunky target unless `modId="chunky"` is
present and rejects the Acedium target unless `modId="acedium"` is present.
Acedium 0.2.x declares both `acedium` and compatibility `nvidium` mod entries;
its own entry is the exact owner gate, while the target class remains
`me.cortex.nvidium.RenderPipeline` because the fork preserves Nvidium's package.
Absent optional integrations therefore no longer produce virtual-target
`ClassNotFoundException` warnings, while installed targets retain the existing
minimum-match injection contracts so ABI drift cannot become a silent no-op.

### XXXIII.2 Embeddium builder-thread ownership matches original Sodium access

Original `VoxyClientInstance.updateDedicatedThreads()` reads
`SodiumWorldRenderer.renderSectionManager` through an accessor Mixin, then
subtracts the active builder's `getTotalThreadCount()`. Forge previously
reflected the same owner and converted any reflection failure into a normal
dedicated-thread result. `ForgeOriginalVoxyEmbeddiumWorldRendererAccessor` now
ports the original access shape directly against mandatory Embeddium 0.3.31;
the policy uses typed `SodiumWorldRenderer`, `RenderSectionManager`, and
`ChunkBuilder` calls. A not-yet-created renderer/manager still selects the
original full dedicated-thread count, but actual ABI drift fails visibly.

### XXXIII.3 Bobby means Bobby Reforged on Forge

Bobby Reforged 1.20.1 retains original Bobby's `bobby` mod id, `.bobby` cache,
and extended client-chunk ownership. The two existing Forge hooks were therefore
functional rather than dead: `ClientChunkCache.drop` ingests before Bobby-owned
unload, while the normal Embeddium removal hook is suppressed to avoid duplicate
timing. Their constants and documentation now name Bobby Reforged explicitly;
the cache importer remains shared with the original format.

### XXXIII.4 validation

Validated against the formal local frontends on 2026-07-17:

```powershell
.\gradlew compileJava `
  -PvoxyEmbeddiumDevJar=.gradle/local-inputs/embeddium-0.3.31+mc1.20.1.jar `
  -PvoxyOculusDevJar=Oculus-1.20.1-new/build/libs/oculus-mc1.20.1-1.8.0.jar
.\gradlew cleanTest test `
  -PvoxyEmbeddiumDevJar=.gradle/local-inputs/embeddium-0.3.31+mc1.20.1.jar `
  -PvoxyOculusDevJar=Oculus-1.20.1-new/build/libs/oculus-mc1.20.1-1.8.0.jar
```

Both commands passed; the test gate reports 40 suites / 129 tests / zero
failures. No client launch was required for this source-selection and ABI batch.

## XXXIV Acedium and Vivecraft runtime-owner compatibility

### XXXIV.1 Acedium 0.2.7 exact render boundary, 2026-07-17

The official `v0.2.7-1.20.1` artifact was checked directly. Its metadata
declares both `modId="acedium"` and a compatibility `modId="nvidium"`, while
its production owner remains `me.cortex.nvidium.RenderPipeline`. The guarded
Mixin therefore selects `acedium` and targets the exact bytecode descriptor:

```text
renderFrame(
  me.jellysquid.mods.sodium.client.render.viewport.Viewport,
  me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices,
  double, double, double
) -> void
```

Acedium invokes that method only from its replacement SOLID terrain pass. Voxy
injects at its return, as original Voxy does for Nvidium, and now forwards the
three exact camera coordinates supplied by Acedium instead of discarding them
and reconstructing position from `Viewport.getTransform()`. The normal
Embeddium hook remains inactive on this route because Acedium cancels
`RenderSectionManager.renderLayer` before `DefaultChunkRenderer.render`.

The Acedium callback also owns a second view input that cannot be reconstructed
from `ChunkRenderMatrices`: its Embeddium `Viewport` wraps the
`SimpleFrustum -> FrustumIntersection` produced from Minecraft's live
`Frustum`. Acedium itself uses that object for region visibility. The Forge
adapter now extracts the same `FrustumIntersection` through typed Mixin
accessors, forwards it with the exact camera coordinates, and copies its six
planes into the selected Voxy `MDICViewport` after the normal MVP update. MVP,
screen-space bounds, HiZ, temporal visibility, and command generation remain on
the original Voxy path; only the Acedium-owned frustum source is adapted.

### XXXIV.2 Vivecraft 1.20.1 per-pass isolation and fail-closed ABI handling

The official `1.20.1-1.3.15` rendering API confirms static
`VRRenderingAPI.instance()`, enum-returning `getCurrentRenderPass()`, and the
`LEFT`, `RIGHT`, `CENTER`, `THIRD`, `GUI`, `SCOPER`, `SCOPEL`, `CAMERA`,
`MIRROR`, and `VANILLA` pass identities. Forge retains original Voxy's one
dedicated `MDICViewport` per non-vanilla enum value, so stereo eyes and mirror,
scope, or camera views do not share HiZ, depth-bounding, frame-id, or render-list
state with each other or with the default viewport.

The adapter now checks the actual `vivecraft` mod entry before resolving the
optional API, validates that the return type is an enum containing `VANILLA`,
and treats an installed-but-incompatible API as an expected Voxy draw skip. It
logs once and does not fall back to the default viewport, because sharing that
state across VR passes can produce cross-eye culling or stale depth results.

### XXXIV.3 exact-artifact validation

The optional development frontend path now accepts `voxyAcediumDevJar` and
`voxyVivecraftDevJar` in addition to the existing Embeddium/Oculus properties.
The official artifacts used for this gate were:

```text
acedium-0.2.7-beta.jar
  SHA-256 03cbd3abd91e23c46303d5326613dd8d96ffeb08cc0cb4eea50e74e52b516433
vivecraft-1.20.1-1.3.15-forge.jar
  SHA-256 28c886302752eda6c057517e335005df03e51a1c0f0da04a647718a0f2584b56
```

`javap` confirmed the Acedium descriptor and Vivecraft API/pass constants above.
With Embeddium 0.3.31, Oculus 1.8.0, Acedium 0.2.7, and Vivecraft 1.3.15 all on
the deobfuscated compile classpath, `compileJava` passed. The same exact-artifact
classpath passed `cleanTest test`: 40 suites / 131 tests / zero failures.
Client visual qualification remains separate because Vivecraft needs a real VR
render cycle and Acedium needs its NVIDIA renderer active.

### XXXIV.4 Acedium moving-camera frustum qualification, 2026-07-17

The first Acedium-only client run rendered LOD but repeatedly hid and restored
sections while the camera rotated; holding the camera still allowed the view to
stabilize. Runtime logs showed one Voxy render-system creation, excluding an
owner restart or repeated initialization loop. A controlled shader comparison
then isolated the fault:

```text
normal visibility chain                       flicker
HiZ occlusion disabled, frustum retained      flicker
HiZ and frustum visibility disabled           stable
```

After forwarding Acedium's exact Embeddium/Minecraft frustum as described in
XXXIV.1, the normal shader was restored with both
`outsideFrustum() || isCulledByHiz()` active. The same Acedium-only client,
world, and rotating-camera test was stable. This qualifies the compatibility
adapter without weakening or bypassing Voxy's formal visibility chain.

A final ordinary-window combination run loaded Acedium 0.2.7 and Vivecraft
1.20.1-1.3.15 together without Oculus. Vivecraft remained on its `VANILLA`
pass, sustained camera rotation no longer produced LOD flicker, and the user
reported no other visible problem before a normal client shutdown. Physical VR
eye/mirror qualification remains unavailable without a headset.

The complete Embeddium + Oculus + Acedium + Vivecraft development runtime was
then exercised with Oculus shaderpacks enabled. Complementary Unbound loaded,
the user switched to BSL during the same session, the Voxy render owner rebuilt
against the new Oculus pipeline, and the user reported no visible regression.
The client shut down normally and Gradle completed successfully. Vivecraft's
subsequent OpenVR initialization attempt reported missing SteamVR/OpenVR paths,
which is expected on this machine and does not qualify physical VR passes.

## XXXV Bobby Reforged 1.20.1-5.0.1 exact-artifact build gate

The official CurseForge file `4650227` was added as an optional development
input through `voxyBobbyDevJar`. The downloaded `bobby-1.20.1_v5.0.1.jar` is
86,682 bytes with SHA-256
`4eb8296c24fa88cfc27145dc006b8acdea75d5f5175dd8b49dc5ef9709e66ee4`.
The artifact remains local and is not packaged into Voxy.

The exact JAR confirms the active Forge assumptions rather than only its public
project description. It declares `modId="bobby"`, is client-side, and registers
required `ClientChunkManagerMixin` and Sodium chunk-manager mixins with
`defaultRequire=1`. Its refmap maps Bobby's `unload` injection to
`ClientChunkCache.m_104455_(II)V`; Forge Voxy's Mojmap-equivalent `drop` HEAD
hook therefore captures the final real chunk before Bobby substitutes or
persists it, while the Embeddium removal hook remains suppressed to avoid a
duplicate ingest. No runtime adapter change was required.

The published file has one upstream packaging quirk: although CurseForge and
the filename identify release `5.0.1`, its internal `mods.toml` still declares
`version="5.0.0"`. The dedicated `bobbyExactArtifactTest` pins both the official
file hash and this real metadata, plus the mixin/refmap entries and Voxy unload
split, so a replacement artifact or incompatible contract cannot silently pass
as the qualified build.

## XXXVI Bobby Reforged and Distant Horizons real-data regression

### XXXVI.1 Bobby Reforged production-path cache qualification

The exact Bobby Reforged `1.20.1_v5.0.1` artifact from XXXV was exercised
against a real Minecraft 1.20.1 server rather than a synthetic region fixture.
Bobby produced a local ignored cache at:

```text
run/.bobby/127.0.0.1_25565/-4672863472195697072/minecraft/overworld
```

The `compatRealDataTest` gate verifies the exact Bobby artifact hash, scans
multiple real `.mca` files, and sends every allocated chunk through
`WorldImporter.createInputStream`, the production Anvil decompressor. It checks
region coordinates, full chunk status, section lists, and non-air block-state
palettes. The end-to-end `/voxy import bobby` run then imported 3,047 real
chunks and completed normally. Bobby's `last_access` metadata file produces the
same harmless "Unknown file" diagnostic as original `WorldImporter`; it is not
an import failure and does not justify a Forge-only parser divergence.

### XXXVI.2 Distant Horizons 3.2.0-b real database qualification

The optional DH gate now pins the official Forge/Fabric artifact:

```text
DistantHorizons-3.2.0-b-1.20.1-fabric-forge.jar
  SHA-1 5667440fdca4d4543c345c9ba6fda2dda64928ca
```

An unmodified DH client generated
`run/saves/新的世界/data/DistantHorizons.sqlite`. The real database uses V2
data with compression mode 4, mapping tables, and adjacent-column blobs. The
real-data gate validates the SQLite schema and detail-zero population, then
decodes 32 database rows—including four adjacent blobs—through
`ForgeOriginalVoxyDhDataDecoder` and verifies every referenced mapping ID.
This supplements the focused synthetic corruption/edge-case tests; it does not
replace them.

### XXXVI.3 two-stage compatibility policy

DH 3.2.0-b explicitly displays an unsupported-mod error when `voxy` and
`distanthorizons` are installed in the same client. Consequently, the qualified
contract is an offline, two-stage import workflow:

```text
launch Distant Horizons without Voxy -> generate/update DistantHorizons.sqlite
remove Distant Horizons              -> launch Voxy and import that database
```

The Forge adapter does not claim simultaneous-runtime compatibility and does
not bypass DH's policy. `voxyDistantHorizonsDevJar` exists to pin and inspect
the producer artifact for regression testing; it is not a normal Voxy runtime
prerequisite.

### XXXVI.4 private XZ ownership

DH 3.2.0-b embeds `org.tukaani.xz`, while the prior Voxy artifact exposed XZ
1.10 as another Java module. Loading both caused a split-package module
resolution failure before Minecraft startup. Voxy now relocates its required XZ
classes to `me.cortex.voxy.dependency.xz`; the decoder and its tests use that
private namespace. Formal `jarJar` inspection found 132 relocated XZ entries,
zero original-package entries, and zero nested XZ modules. This retains the
original import algorithm without competing with the producer mod's package.

### XXXVI.5 end-to-end import, persistence, and visual evidence

With official Embeddium 0.3.31, Oculus 1.8.0, and Bobby Reforged 5.0.1 loaded,
Voxy imported the real Bobby cache and the DH-produced database in one client
session. The DH scan accepted all 1,120 detail-zero rows and scheduled 17,920
chunks, with zero failed or unscheduled rows. Normal shutdown persisted the
formal WorldEngine storage as 135 files totaling 698,740,077 bytes.

Restarting the same world reopened the same WorldIdentifier and storage path
without rerunning either command. Geometry commitment grew from approximately
67 MB to 473 MB as the persisted data was rebuilt into the visible formal
renderer. The user confirmed the LOD result visually, reported no problem, and
closed the client normally. This qualifies Bobby cache ingestion, DH database
decoding/import, WorldEngine persistence, and restart visibility as one real
data lifecycle; it does not qualify unsupported simultaneous DH runtime use.

## Forxy 2026-07-19 visual root-cause audit

The leaf and shader-shadow reports are deliberately split. The leaf defect is
closed by exact modern `DARK_CUTOUT` level-zero preprocessing in Forge `MipGen`,
its heap/native parity fixture, and user visual acceptance. The cross-
vanilla/LOD shadow discontinuity remains open after the original Oculus
viewport-capture lifecycle was restored and shown not to change the symptom.

The failed hypotheses, evidence boundary, and mandatory next diagnostic gate
are recorded in
`docs/forxy-visual-parity-root-cause-retrospective-2026-07-19.md`. No current
readiness or parity statement may describe the shadow issue as fixed until a
same-frame seam-pixel probe identifies the divergent shadow-space input and the
user accepts the resulting visual regression.
