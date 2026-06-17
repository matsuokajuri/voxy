# Forge 1.20.1 ModelBakery bridge plan

I1 audits the original Voxy model lifecycle and defines a Forge migration plan
for the real `ModelFactory` / `ModelBakerySubsystem` bridge. It does not
implement the bridge, create a renderer, bind a formal shader, or replace any
debug renderer.

## I1 purpose

H1 and H2 created a no-draw formal renderer ownership shell. That shell still
reports the real model lifecycle as a P0 blocker:

```text
real ModelFactory / ModelBakery bridge missing
formal ModelStore missing
formal shader contract incomplete
resource reload rebuild path missing
```

The purpose of I1 is to describe the first blocker precisely enough that I2 and
later work can be implemented without treating sample debug data as formal model
data.

## Original Voxy lifecycle

The original model path is asynchronous on the bake side and render-thread
owned on the GL upload side:

```text
Mapper block id
 -> ModelBakerySubsystem.requestBlockBake(blockId)
 -> ModelFactory.addEntry(blockId)
 -> background processAllThings()
 -> bake model / texture result
 -> ModelBakeResultUpload
 -> processUploads()
 -> ModelStore modelData upload
 -> modelColour upload
 -> atlas tile upload
 -> idMappings[blockId] = modelId
 -> RenderDataFactory gets modelId + metadata
 -> packed quad record uses modelId
```

The waiting and invalid paths are part of the contract:

```text
IdNotYetComputedException
missing model id
model upload pending
resource reload invalidation
shutdown/free
```

`ModelBakerySubsystem` owns the lifecycle. It creates a `ModelStore`, owns a
`ModelFactory`, starts a background processor thread, forwards new block ids and
biomes, calls `ModelFactory.processUploads()` from `tick(...)`, and frees both
factory and store in `shutdown()`.

`ModelFactory` owns model id creation and upload staging. It tracks
`idMappings`, `fluidStateLUT`, `metadataCache`, `modelTexture2id`,
`blockStatesInFlight`, bake queues, upload queues, and biome upload queues.
`addEntry(blockId)` schedules a block state for baking, including special
handling for stair base states and block states containing fluid. The background
`processAllThings()` path bakes textures and records, while `processUploads()`
uploads completed results to GL resources owned by `ModelStore`.

`ModelStore` owns the formal shader inputs: the 64-byte-per-model model data
buffer, model colour buffer, Voxy block atlas texture, and sampler. It binds
model data, colour data, and the atlas to the shader.

`RenderDataFactory` consumes real model ids and metadata while building packed
quad records. If a model id has not been computed yet, it throws
`IdNotYetComputedException` instead of silently inventing a placeholder.

Evidence:

- `src/main/java/me/cortex/voxy/client/core/model/ModelBakerySubsystem.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelStore.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelQueries.java`
- `src/main/java/me/cortex/voxy/client/core/RenderResourceReuse.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java`

## Original model data contract

The original `ModelStore.MODEL_SIZE` is 64 bytes. The shader-side
`BlockModel` layout is:

```glsl
struct BlockModel {
    uint faceData[6];
    uint flagsA;
    uint colourTint;
    uint customId;
    uint _pad[7];
};
```

`ModelFactory.processTextureBakeResult(...)` writes the first six words as
`faceData[6]`. The next words carry `flagsA`, `colourTint`, and `customId`.
Unknown or currently unused formal fields remain padding/reserved in the shader
layout.

The confirmed face data fields include:

- low 16 bits: packed face bounds,
- bits 16 through 21: face indentation,
- bit 22: alpha cutout,
- bit 23: alpha cutout override,
- bits 24 through 25: face tint state.

The confirmed `flagsA` fields include:

- bit 0: model has tint sources,
- bit 1: `colourTint` points into the biome/model colour LUT,
- bit 2: translucent,
- bit 3: shaded / ambient occlusion relevant.

The model metadata cache is separate from the 64-byte model record. It stores
per-face occlusion/full-face/self-lighting data and global flags such as biome
colour, translucent, double-sided, contains fluid, is fluid, culls same, fully
opaque, and block light emission.

The atlas is a Voxy-owned RGBA8 texture from `RenderResourceReuse`:

```text
MODEL_TEXTURE_SIZE = 16
facesPerModelX = 3
facesPerModelY = 2
model grid = 256 x 256
atlasWidth = 16 * 3 * 256 = 12288
atlasHeight = 16 * 2 * 256 = 8192
```

The model tile address is:

```text
baseX = (modelId & 0xFF) * MODEL_TEXTURE_SIZE * 3
baseY = ((modelId >> 8) & 0xFF) * MODEL_TEXTURE_SIZE * 2
```

`ModelBakeResultUpload.upload(...)` copies model data, optional biome colour
data, and atlas pixels. Atlas pixels are uploaded as a 3-by-2 model tile with
mips using `glTextureSubImage2D`.

## Original shader contract

The GL 4.6 bindings show the formal inputs:

- `blockModelAtlas` is a `sampler2D`.
- `ModelBuffer` exposes `BlockModel modelData[]`.
- `ModelColourBuffer` exposes `uint colourData[]`.
- The draw and draw-count buffers use formal MDIC command layout.

`quad_util.glsl` extracts the model id from packed quad data, reads
`modelData[modelId]`, reads `faceData[face]`, builds UVs and flags, and applies
`model.colourTint` with `colourData` when the model has a biome LUT.

`quads.frag` computes the model tile UV from the model id and face. It samples
`blockModelAtlas`, applies alpha discard/cutout rules, computes tint, and emits
formal fragment data with `model.customId`.

Evidence:

- `src/main/resources/assets/voxy/shaders/lod/block_model.glsl`
- `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl`
- `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag`

## Current Forge PoC gap

The Forge PoC has useful probes, but it does not have the original lifecycle.

`ForgeVoxyModelIdMapper` creates stable runtime placeholder ids from mapper
block state ids. That is useful for debug records, but it is not equivalent to
`ModelFactory.getModelId(...)` because it does not bake, dedupe, wait for upload,
throw `IdNotYetComputedException`, own `fluidStateLUT`, or update
`metadataCache`.

`ForgeModelSampleSet*` builds multi-block sample records from currently observed
world/sample data. Those records are real-ish debug samples, not a full
`ModelStore`. They prove that Forge can inspect `BakedModel`, `BakedQuad`,
sprites, UVs, tint, and atlas pixels, but they do not provide the block-id to
model-id lifecycle.

`ForgeModelAtlas*` proves Voxy-style atlas ownership, coordinate mapping, and
sample pixel upload/readback. It is command driven and sample-set driven; it is
not the real bake/upload pipeline.

`ForgeFormalShaderInputBridge` proves that a debug shader can bind sample-set
model data, model colour data, validity data, and the Forge-owned atlas. It is a
bridge concept only. It explicitly uses sample-set model data and not a real
`ModelStore`.

`ForgeModelBridgeReloadListener` and related reload tracking provide a usable
resource reload invalidation pattern. They do not rebuild formal model data
after reload.

Evidence:

- `src/main/java/me/cortex/voxy/forge/ForgeVoxyModelIdMapper.java`
- `src/main/java/me/cortex/voxy/forge/ForgeVoxyQuadEncoder.java`
- `src/main/java/me/cortex/voxy/forge/ForgeVoxyBuiltSectionBuilder.java`
- `src/main/java/me/cortex/voxy/forge/ForgeModelStoreFormalLayout.java`
- `src/main/java/me/cortex/voxy/forge/ForgeModelSampleSet.java`
- `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasSkeleton.java`
- `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasSampleSetUploader.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalShaderInputBridge.java`
- `src/main/java/me/cortex/voxy/forge/ForgeModelBridgeReloadListener.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalRendererManager.java`

## Forge replacement API candidates

Forge 1.20.1 should not depend on Fabric, Sodium, Iris, or mixins for this
bridge. The likely replacement surfaces are:

- `Minecraft.getInstance().getBlockRenderer()` / `BlockRenderDispatcher` for
  block models.
- `BakedModel` and `BakedQuad` for model quads.
- `TextureAtlasSprite` / sprite contents / native image access for sampled
  pixels.
- `BlockColors` for tint source evaluation.
- `ItemBlockRenderTypes` or the Forge/Minecraft render layer API for solid,
  cutout, and translucent classification.
- the fluid state model set or fluid renderer path for fluid-specific model
  data, still requiring a dedicated audit before implementation.
- Forge client reload listener registration for resource invalidation and later
  rebuild scheduling.
- render-thread scheduling for all GL uploads and deletes.

The BakedModel and sprite APIs have already been proven by sample audits. The
missing part is not "can Forge read a model"; it is "can Forge own the full
model id, dedupe, metadata, atlas, tint, reload, and upload lifecycle."

## Migration plan

### I2: Formal ModelStore ownership skeleton

Create a formal ModelStore owner with:

- modelData buffer,
- modelColour buffer,
- Voxy-style atlas texture,
- sampler,
- lifecycle/status,
- reload invalidation,
- clear/free hooks.

I2 should still be no-bake and no-draw. It should not populate real model
records or bind a formal shader. Its main goal is to move ownership away from
sample upload helpers and into a formal resource owner.

### I3: Forge ModelFactory skeleton

Create the lifecycle shell for block-state to model-id mapping:

- `idMappings`,
- pending bake queue,
- seen ids / in-flight ids,
- placeholder `metadataCache`,
- placeholder `fluidStateLUT`,
- placeholder `modelTexture2id`,
- status and audit.

I3 should avoid real atlas upload. It should prove the queueing, wait/error, and
reload invalidation shape before background bake and GL upload complexity is
added.

### I4: Real one-block bake/upload prototype

Implement one formal bake/upload path for one solid block:

```text
BlockState
 -> BakedModel / BakedQuad
 -> faceData
 -> model record
 -> atlas tile upload
 -> idMappings
```

The upload should target the formal ModelStore owner from I2, not the sample-set
atlas helper. It should audit model record bytes, atlas tile checksums, and
metadata. It should still avoid calling `MDICSectionRenderer` or replacing the
formal renderer skeleton.

### I5: Multi-block bake/upload and dedupe

Extend I4 to multiple block states and then harden:

- model texture dedupe,
- missing/animated sprite policy,
- fluid state LUT,
- biome tint / modelColour LUT,
- cutout/translucent/material semantics,
- reload rebuild,
- shutdown/free stress.

Only after I5 should formal textured draw work be reconsidered.

## Risk table

| Risk | Severity | Cause | Mitigation | Stage |
| --- | --- | --- | --- | --- |
| model id stability | P0 | Placeholder ids are assigned eagerly and do not represent baked/deduped model ids | Introduce formal `idMappings` and keep placeholder mapper out of formal path | I3 |
| resource reload invalidation | P0 | Model and atlas data become invalid when resource packs reload | Reuse reload listener pattern, then add rebuild and generation checks | I2-I5 |
| background bake thread safety | P0 | Original baking runs off the render thread while GL upload must not | Separate bake queues from upload queues; never call GL from bake thread | I3-I5 |
| GL upload render-thread safety | P0 | Atlas/model buffer uploads and deletes must happen on the render thread | Use explicit upload queue and render-thread scheduler/status | I2-I5 |
| Minecraft BakedModel API differences | P1 | Forge/Minecraft APIs differ from original Fabric/Sodium assumptions | Keep an adapter layer around `BakedModel`, `BakedQuad`, sprite, and render layer reads | I4 |
| fluid model lifecycle | P1 | Fluids use different model/tint paths and can coexist with block states | Keep `fluidStateLUT` placeholder until solid block path is stable, then add fluid-specific audit | I3-I5 |
| biome tint / modelColour LUT | P1 | Original uses biome-dependent colour LUT and per-model colour pointer updates | Add formal colour LUT only after one-block record upload is stable | I5 |
| texture dedupe | P1 | Original dedupes `ModelEntry` before assigning final model id | Start with no dedupe, then introduce `modelTexture2id` equivalent with audit | I5 |
| animated/missing sprites | P1 | Animated or missing sprites can break static 16x16 tile assumptions | Reject or mark unsupported first; add policy before broad bake | I4-I5 |
| alpha/cutout semantics | P1 | Visual correctness depends on render layer, alpha discard, and face flags | Preserve flags in audits before formal shader use | I4-I5 |
| sample bridge accidentally reused as formal path | P0 | Existing sample-set bridge can make status look ready without real lifecycle | Keep sample classes named and reported as debug/sample; formal manager must require real owner flags | I2-I5 |

## Do not do

Do not directly treat `ForgeModelSampleSet` as a real `ModelFactory`.

Do not treat the sample-set formal input bridge as a formal `ModelStore`.

Do not keep extending textured debug renderers as a replacement for the real
bake lifecycle.

Do not connect `MDICSectionRenderer` now.

Do not connect `VoxyRenderSystem` now.

Do not start shaderpack, Iris, Oculus, Embeddium, or mixin work now.

## Recommended next step

The recommended next stage is:

```text
I2: Formal ModelStore ownership skeleton
```

I2 should remain no-draw. It should create the formal owner for model data,
model colour, atlas texture, sampler, lifecycle, and reload invalidation. That
gives I3 and I4 a correct target for model ids and uploads without adding more
behavior to debug renderers.

## I2 status: Formal ModelStore ownership skeleton

I2 creates a formal ModelStore owner as an empty resource shell. It is the
future upload target for the real Forge `ModelFactory` / `ModelBakery` bridge,
but it still does not bake models, upload real model records, upload real atlas
pixels, bind a formal shader, or draw.

The I2 owner is separate from the older sample-set bridge:

- sample-set classes remain debug/validation tools,
- `ForgeFormalShaderInputBridge` still reports sample-set input bridge status,
- the formal ModelStore owner reports ownership of formal resources only,
- `formalModelStoreReady=false` remains true until real model records and real
  atlas pixels are produced by a formal bake lifecycle.

The new owner tracks:

- formal 64-byte model record layout,
- `modelData` buffer ownership,
- `modelColour` buffer ownership,
- Voxy-style 3-by-2 face-tile atlas texture ownership,
- sampler ownership,
- allocation sizes and fallback state,
- lifecycle stale/clear/rebuild requirements,
- audit state.

The expected formal sizes are:

```text
MODEL_SIZE = 64
MODEL_COUNT = 65536
modelDataBytes = 4194304
modelColourBytes = 262144
atlasWidth = 12288
atlasHeight = 8192
atlasFormat = RGBA8
```

The full atlas is large, so I2 keeps a conservative fallback policy. If full
atlas allocation fails, the owner must report the failure or debug-small-atlas
fallback explicitly. A fallback allocation does not make the formal atlas ready.

Lifecycle rules:

- resource reload stales the owner and requires rebuild,
- world unload and dimension switch stale/clear the owner resources,
- debug pipeline clear and preset clear invalidate the owner without touching
  unrelated renderers,
- cleanup is render-thread aware,
- GL geometry heap, MDIC command buffers, existing MDIC debug renderer, textured
  MDIC debug renderer, sample-set resources, simple renderer, and CPU caches are
  not owned by the formal ModelStore.

H1/H2 formal renderer readiness now sees:

```text
formalModelStoreSkeletonReady
formalModelStoreOwnerReady
realModelStoreReady=false
formalRendererReady=false
```

The blocker wording changes from "no formal ModelStore owner exists" to the
more precise blockers:

```text
P0_FORMAL_MODELSTORE_REAL_DATA_MISSING
P0_FORMAL_MODELSTORE_REBUILD_MISSING
```

This is intentional. I2 solves ownership, not real data population.

Recommended next stage:

```text
I3: Forge ModelFactory skeleton
```

I3 should create the block-state-to-model-id lifecycle shell: mappings, pending
queues, in-flight tracking, metadata cache placeholder, fluid LUT placeholder,
and dedupe placeholder. It should still avoid formal draw.

## I3 status: Forge ModelFactory lifecycle skeleton

I3 adds a no-bake, no-upload lifecycle owner for the future Forge-side
`ModelFactory`. It is intentionally separate from the older placeholder
`ForgeVoxyModelIdMapper` and from the sample-set debug model ids.

The I3 skeleton tracks:

- `blockStateId -> formalModelId` skeleton mappings,
- seen block state ids,
- pending bake requests,
- synchronous in-flight skeleton processing,
- completed skeleton mappings,
- placeholder `metadataCache` entries,
- placeholder `fluidStateLUT` entries,
- placeholder `modelTexture2id` entries.

The current process is:

```text
blockStateId
 -> request
 -> seen / pending
 -> process_skeleton
 -> assign formalModelId skeleton
 -> placeholder metadataCache / fluidStateLUT / modelTexture2id
 -> audit
```

It does not:

- call Minecraft or Forge `BakedModel` bake logic,
- upload formal model records,
- upload formal atlas pixels,
- write real formal ModelStore data,
- bind a formal shader,
- draw,
- call `MDICSectionRenderer`,
- call `VoxyRenderSystem`.

ID semantics are explicit:

- `ForgeVoxyModelIdMapper` ids remain legacy placeholder/debug ids.
- sample-set ids remain debug/validation ids.
- I3 formal ids are lifecycle skeleton ids only.

The status must therefore keep:

```text
formalModelIdsAssigned=true/false
formalModelIdsBackedByRealBake=false
usesPlaceholderModelIds=false
usesFormalModelIds=true/false
sampleSetModelIdsUsed=false
formalModelFactoryReady=false
```

Reload and world lifecycle events mark the skeleton stale and clear mappings so
old block-state-to-model-id assignments do not leak across resource packs,
worlds, or dimensions. This is still only invalidation; rebuild with real bake
results is left for later.

Formal renderer readiness can now report:

```text
formalModelFactorySkeletonReady
formalModelFactoryLifecycleReady
realModelFactoryReady=false
realModelBakeryReady=false
formalRendererReady=false
```

The blocker wording changes from a generic missing ModelFactory to two more
precise P0 blockers:

```text
P0_REAL_MODEL_FACTORY_REAL_BAKE_MISSING
P0_REAL_MODEL_FACTORY_UPLOAD_PIPELINE_MISSING
```

Recommended next stage:

```text
I4: Real one-block bake/upload prototype
```

I4 should take exactly one safe solid `BlockState` through the real Forge
`BakedModel` / `BakedQuad` read path, build one formal record, and upload it
through the I2 formal ModelStore owner. It should still avoid broad renderer
integration until the one-block bake/upload path audits cleanly.

## I4 one-block formal bake/upload prototype

I4 implements the first real-bake upload path into the formal owner, but only
for one safe solid block. The chain is:

```text
safe solid BlockState
 -> Forge BakedModel / BakedQuad / TextureAtlasSprite
 -> one 64-byte formal model record
 -> I3 formalModelId mapping
 -> I2 formal ModelStore modelData slot
 -> I2 formal ModelStore modelColour slot
 -> I2 formal Voxy-style atlas six 16x16 face tiles
 -> readback audit
```

This deliberately bypasses the old sample-set atlas helper as an upload target.
The sample-set path remains a debug/validation tool; the I4 target is the I2
formal `ModelStore` owner.

I4 still does not implement:

- multi-block bake,
- texture dedupe,
- fluid support,
- broad biome LUT,
- formal shader binding,
- formal MDIC draw,
- `MDICSectionRenderer`,
- `VoxyRenderSystem`.

Successful I4 status means only that one prototype block can be baked and
uploaded into the formal owner:

```text
oneBlockRealBakeReady=true
oneBlockFormalModelRecordUploaded=true
oneBlockFormalAtlasPixelsUploaded=true
oneBlockFormalUploadAuditOk=true
```

It must still keep:

```text
realModelFactoryReady=false
realModelBakeryReady=false
realModelStoreReady=false
formalRendererReady=false
actualDrawEnabled=false
```

Recommended next stage:

```text
I5: multi-block bake/upload and dedupe
```

I5 should generalize the single-block path only after I4 readback proves that
the formal owner can safely receive one real baked record and atlas tile set.

## I5 multi-block formal bake/upload and dedupe

I5 generalizes the I4 one-block prototype to a small set of safe solid block
states. The chain is:

```text
safe solid BlockStates
 -> Forge BakedModel / BakedQuad / TextureAtlasSprite
 -> I3 formalModelId mappings
 -> one 64-byte formal model record per accepted unique model
 -> I2 formal ModelStore modelData slots
 -> I2 formal ModelStore modelColour slots
 -> I2 formal Voxy-style atlas face tiles
 -> readback audit
```

The upload target remains the I2 formal `ModelStore` owner. I5 does not use
the older sample-set uploader as the formal path.

I5 adds a first conservative dedupe skeleton. It computes a record-and-texture
signature and reports `modelTexture2idSize`, dedupe hits, and dedupe misses.
Because the current I3 audit treats duplicate formal model ids as invalid, I5
does not yet alias multiple block states onto one formal id. That is a future
ModelFactory lifecycle hardening item.

Unsupported candidates are rejected and counted instead of silently accepted.
The initial policy accepts only safe solid, non-fluid, non-tinted blocks with
readable baked quads and sprites. Fluid, cutout, translucent, tinted, missing
sprite, missing model, and no-quad cases remain explicit unsupported categories.

Successful I5 status means:

```text
multiBlockFormalBakeReady=true
multiBlockFormalRecordsUploaded=true
multiBlockFormalAtlasPixelsUploaded=true
multiBlockFormalUploadAuditOk=true
basicModelDedupeReady=true
unsupportedPolicyReady=true
```

It must still keep:

```text
realModelFactoryReady=false
realModelBakeryReady=false
realModelStoreReady=false
formalTexturedShaderReady=false
formalRendererReady=false
actualDrawEnabled=false
```

I5 is still not a renderer stage. It does not bind a formal shader, draw, call
`MDICSectionRenderer`, call `VoxyRenderSystem`, implement fluid rendering,
implement a broad biome LUT, or touch shaderpack integration.

## I6 formal ModelBakery lifecycle rebuild and alias-safe dedupe

I6 hardens the I5 multi-block formal bake/upload path with a lifecycle
coordinator. The coordinator references the I2 formal `ModelStore` owner, the
I3 formal `ModelFactory` lifecycle, and the I5 multi-block upload path. It does
not replace those components and does not introduce renderer behavior.

The I6 chain is:

```text
formal ModelStore owner
 + formal ModelFactory lifecycle
 + I5 multi-block formal bake/upload
 -> formal ModelBakery lifecycle coordinator
 -> safe-set upload
 -> reload invalidation
 -> deterministic safe-set rebuild
 -> generation tracking
 -> alias-safe dedupe audit
```

The rebuild prototype is command-driven. The QA command runs a safe-set upload,
simulates resource reload invalidation, rebuilds the same safe set, and audits
that modelData, modelColour, and atlas readback are still valid after rebuild.

I6 also makes dedupe alias semantics explicit. Multiple block-state requests may
share a formal model id only when an explicit alias record exists. If no natural
duplicate model appears in the safe set, the QA path uses a duplicate request of
the first accepted block state to prove alias handling without merging uncertain
models. Illegal duplicate mappings remain audit failures.

Successful I6 status means:

```text
formalModelBakeryLifecycleSkeletonReady=true
reloadRebuildPrototypeReady=true
aliasSafeDedupeReady=true
multiBlockFormalUploadAuditReady=true
lastRebuildAuditOk=true
illegalDuplicateMappingCount=0
```

It must still keep:

```text
realModelFactoryReady=false
realModelBakeryReady=false
realModelStoreReady=false
formalTexturedShaderReady=false
formalRendererReady=false
actualDrawEnabled=false
```

I6 is still not a renderer stage. It does not bind a formal shader, draw, call
`MDICSectionRenderer`, call `VoxyRenderSystem`, start a full async bake thread,
implement broad fluid/tint/material support, or touch shaderpack integration.

## J1 formal shader input consumption skeleton

J1 introduces a no-draw formal shader input consumer for the real formal
`ModelStore` owner path proven by I2-I6. The chain is:

```text
I6 safe-set lifecycle rebuild
 -> I2 formal ModelStore owner with modelData/modelColour/atlas/sampler
 -> formal model ids and uploaded safe-set records
 -> binding layout validation
 -> GL bind/unbind validation without shader bind
 -> audit/status/readiness aggregation
```

J1 validates the original shader input contract boundaries that are currently
known: `MODEL_BUFFER_BINDING=3`, `MODEL_COLOUR_BUFFER_BINDING=4`,
`BLOCK_MODEL_TEXTURE_BINDING=0`, and a 64-byte `BlockModel` record containing
`faceData[6]`, `flagsA`, `colourTint`, and `customId`.

The J1 consumer intentionally does not use the older sample-set
`ForgeFormalShaderInputBridge` as the formal data source. That bridge remains a
debug validation path from the extended G phase. J1 consumes the I2 formal owner
resources and the I6 lifecycle safe-set state.

Successful J1 status means:

```text
formalShaderInputConsumerReady=true
modelDataBufferReady=true
modelColourBufferReady=true
atlasTextureReady=true
samplerReady=true
bindingLayoutKnown=true
bindingLayoutCompatible=true
safeSetModelIdsAddressable=true
usesFormalModelIds=true
```

It must still keep:

```text
formalShaderInputReady=false
formalShaderInputContractReady=false
formalTexturedShaderReady=false
formalRendererReady=false
actualDrawEnabled=false
```

J1 is still not a renderer stage. It does not bind a formal shader program,
draw terrain, call `MDICSectionRenderer`, call `VoxyRenderSystem`, add
visibility traversal, or touch shaderpack integration.

## J2 formal shader program validation prototype

J2 adds an audit-only shader program validator on top of the J1 formal input
consumer. The chain is:

```text
I6 safe-set lifecycle rebuild
 -> J1 formal shader input resources
 -> audit-only compute shader compile/link
 -> bind formal modelData/modelColour/atlas/sampler
 -> validate a small set of formal model ids on GPU
 -> read back compact validation results
 -> audit/status/readiness aggregation
```

The validation program consumes the I2 formal `ModelStore` owner resources. It
does not use the older sample-set bridge as a formal source. It checks the known
formal binding contract:

```text
MODEL_BUFFER_BINDING = 3
MODEL_COLOUR_BUFFER_BINDING = 4
BLOCK_MODEL_TEXTURE_BINDING = 0
BlockModel record size = 64 bytes
```

The GPU validation reads selected `faceData`, `flagsA`, `colourTint`,
`modelColour`, and one atlas texel for several formal model ids, then writes a
small result buffer for CPU readback. It is deliberately not a terrain draw.

Successful J2 status means:

```text
formalShaderProgramValidatorReady=true
validationShaderCompileOk=true
validationProgramLinkOk=true
gpuValidationOk=true
validationReadbackOk=true
usesFormalModelIds=true
sampleSetModelIdsUsed=false
```

It must still keep:

```text
formalShaderInputContractReady=false
formalTexturedShaderReady=false
formalRendererReady=false
actualDrawEnabled=false
```

J2 is not a renderer stage. It does not draw terrain, call `MDICSectionRenderer`,
call `VoxyRenderSystem`, implement visibility traversal, or touch shaderpack
integration. The next risky boundary is a real formal textured shader prototype,
not more debug renderer expansion.
