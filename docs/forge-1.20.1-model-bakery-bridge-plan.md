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
