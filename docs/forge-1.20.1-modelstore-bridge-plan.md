# Forge 1.20.1 ModelStore bridge readiness

This document audits the original Voxy model/render-data pipeline and the Forge
1.20.1 PoC placeholder model-id path after G6.8. The goal for G6.9 is a no-draw
readiness bridge: report what is present, what is missing, and why a formal
textured MDIC renderer is not ready yet.

G6.9 does not upload a real texture atlas, does not instantiate the original
`ModelStore`, does not connect `MDICSectionRenderer`, and does not change the
existing G6 debug draw paths.

## Original Voxy model and render-data path

The original path starts with world section data and block states, then resolves
model ids before render data is emitted:

```text
WorldEngine / Mapper
 -> ModelBakerySubsystem
 -> ModelFactory
 -> ModelStore
 -> RenderDataFactory
 -> packed quad records
 -> formal shader vertex pulling
```

`src/main/java/me/cortex/voxy/client/core/model/ModelBakerySubsystem.java`
owns a `ModelStore` and a `ModelFactory`. Its constructor creates a background
thread named `Model factory processor`, calls `factory.processAllThings()` on that
thread, and later calls `factory.processUploads()` from `tick(...)`. Shutdown
stops the thread, frees the factory, and frees the store.

`ModelBakerySubsystem.requestBlockBake(int blockId)` validates the Voxy
`Mapper` block id, deduplicates requests with `seenIds`, enqueues
`ModelFactory.addEntry(blockId)`, and unparks the background processor. This is
the formal route from a Voxy block-state id to a baked model id.

`src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java` owns the
model id mapping and metadata:

- `idMappings` maps Voxy block ids to client model ids.
- `metadataCache` stores the 64-bit meshing metadata read by
  `RenderDataFactory`.
- `fluidStateLUT` maps model ids to associated fluid model ids.
- `modelTexture2id` deduplicates baked model/texture entries.
- `getModelId(int blockId)` returns the baked client model id, or throws
  `IdNotYetComputedException` if the model is not ready.
- `getModelMetadataFromClientId(int clientId)` returns the metadata used by the
  mesher.
- `processTextureBakeResult(...)` assigns or reuses a model id, writes face
  metadata, model flags, tint information, custom id, model texture data, and
  finally installs `idMappings[blockId] = modelId`.

`src/main/java/me/cortex/voxy/client/core/model/ModelStore.java` owns the GPU
storage used by formal shaders:

- `MODEL_SIZE = 64`.
- `modelBuffer = MODEL_SIZE * 65536`, named `ModelData`.
- `modelColourBuffer = 4 * 65536`, named `ModelColour`.
- `textures = RenderResourceReuse.getOrCreateModelStoreTextureAtlas()`.
- `blockSampler` is configured against the Minecraft blocks atlas mip level.
- `bind(3, 4, 0)` binds model data, model colours, and the atlas/sampler.

`src/main/java/me/cortex/voxy/client/core/RenderResourceReuse.java` creates the
atlas used by `ModelStore` as an `RGBA8` texture sized:

```text
width  = ModelFactory.MODEL_TEXTURE_SIZE * 3 * 256
height = ModelFactory.MODEL_TEXTURE_SIZE * 2 * 256
```

With `MODEL_TEXTURE_SIZE = 16`, each model occupies a 3-by-2 tile group for the
six block faces.

`ModelFactory.ModelBakeResultUpload.upload(...)` writes the 64-byte model record
to `modelBuffer`, optional biome colour data to `modelColourBuffer`, and texture
mip levels to the model atlas with `nglTextureSubImage2D(...)`. The atlas position
is derived from the model id:

```text
X = (modelId & 0xFF) * MODEL_TEXTURE_SIZE * 3
Y = ((modelId >> 8) & 0xFF) * MODEL_TEXTURE_SIZE * 2
```

`src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java`
uses `ModelFactory` during geometry generation:

- `prepareSectionData(...)` reads `rawModelIds = modelMan._unsafeRawAccess()`.
- For each non-air block it resolves the model id and calls
  `modelMan.getModelMetadataFromClientId(modelId)`.
- `packPartialQuadData(...)` places `modelId << 26`, light/biome data, and
  quad type bits into the packed quad record.
- `ModelQueries` controls translucent, double-sided, fluid, biome-colour,
  opacity, and face-occlusion decisions from the metadata.
- `Mesher.putQuad(...)` writes one of eight buckets:
  bucket 0 translucent, bucket 1 double-sided, and buckets 2..7 directional.

`src/main/java/me/cortex/voxy/client/core/model/ModelQueries.java` defines the
metadata tests used by both meshing and render data generation. Examples include
`isTranslucent`, `isDoubleSided`, `isBiomeColoured`, `isFullyOpaque`,
`faceExists`, `faceOccludes`, and `lightEmission`.

## Original shader contract

The model shader inputs are defined across these files:

- `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl`
- `src/main/resources/assets/voxy/shaders/lod/block_model.glsl`
- `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag`

`bindings.glsl` declares:

- `sampler2D blockModelAtlas`
- `QuadBuffer`
- `SectionBuffer`
- `ModelBuffer` with `BlockModel modelData[]`
- `ModelColourBuffer` with `uint colourData[]`
- `PositionScratchBuffer`

`block_model.glsl` defines the 64-byte model record shape used by shaders:

```glsl
struct BlockModel {
    uint faceData[6];
    uint flagsA;
    uint colourTint;
    uint customId;
    uint _pad[7];
};
```

The first 24 bytes are six face records. Then `flagsA`, `colourTint`, and
`customId` are consumed by shader logic. The remaining words pad the record to
64 bytes.

`quad_util.glsl` reads `modelData[modelId]`, extracts face dimensions and face
indentation from `faceData`, applies biome colour with `colourData` when
`modelHasBiomeLUT(model)` is true, combines lighting with `getLighting(...)`,
and builds the per-vertex attributes later consumed by the fragment shader.

`quads3.vert` pulls one packed quad from `quadData[gl_VertexID >> 2]`, reads
section position from `positionBuffer[gl_BaseInstance]`, calls `setupQuad(...)`,
and emits UV plus packed attributes.

`quads.frag` samples `blockModelAtlas`, handles alpha discard, tinting, lightmap
UV, optional patched shader output, and emits the textured fragment. In the
patched path it also reads `modelData[modelId]` to pass `model.customId`.

## Current Forge placeholder model-id path

The Forge PoC currently has a placeholder route, not a real model bridge.

`src/main/java/me/cortex/voxy/forge/ForgeVoxyModelIdMapper.java` maps an integer
block-state id to a stable 16-bit placeholder model id. The class comment already
states the important limitation: original Voxy assigns ids after baking block
models and textures into `ModelStore`, while the Forge path deliberately maps the
existing id to a stable placeholder.

`src/main/java/me/cortex/voxy/forge/ForgeVoxyQuadEncoder.java` calls
`ForgeVoxyModelIdMapper.INSTANCE.getOrCreateModelId(blockId)`, packs that
placeholder id into bits 26..41, and records biome/light/debug information. It
does not query model metadata, UVs, render material, texture atlas coordinates,
or biome tint tables.

`src/main/java/me/cortex/voxy/forge/ForgeVoxyBuiltSectionBuilder.java` builds
partial original-layout sections. It preserves the eight bucket offsets and
records counts such as `uniqueModelIds`, `missingModelId`, `modelIdOverflow`,
`missingTexture`, and `missingGreedy`. The produced sections are explicitly
`finalRendererFormat=false`.

`src/main/java/me/cortex/voxy/forge/ForgeMdicDebugShader.java` extracts the
packed model id from the quad record, but only uses it as part of a stable debug
colour seed. It does not bind `modelData`, `modelColourBuffer`, or
`blockModelAtlas`.

`src/main/java/me/cortex/voxy/forge/ForgeMdicCommandPlanner.java` plans
visibility/radius, face-mask, and bucket-aware MDIC debug commands from uploaded
metadata. It consumes geometry ranges and bucket offsets, not real model
metadata.

## Gap summary

| Area | Original Voxy | Current Forge PoC | Gap |
| --- | --- | --- | --- |
| model id source | `ModelFactory.getModelId(blockId)` after bake | `ForgeVoxyModelIdMapper.getOrCreateModelId(blockStateId)` | Placeholder id has no real metadata |
| block id mapping | Voxy `Mapper` block ids | Voxy mapper ids when available, fallback samples may use vanilla registry id | Needs one authoritative bridge |
| model metadata | `metadataCache[modelId]` | none | Meshing/render metadata missing |
| model record | `BlockModel` in 64-byte `ModelStore` record | none | Shader cannot textured-draw |
| model colour | `modelColourBuffer` with biome tint data | none | Biome colour path missing |
| texture atlas | 3x2 face tile atlas per model | none | UV/material lookup missing |
| shader use | `modelData`, `colourData`, `blockModelAtlas`, lightmap | debug colour only | Formal shader inputs missing |
| resource reload | model bakery/store lifecycle | no formal model resources | Reload contract missing |

## No-draw bridge skeleton

G6.9 adds a no-draw readiness skeleton with commands:

```text
/voxy model_bridge_check
/voxy model_bridge_status
/voxy model_bridge_clear
/voxy model_bridge_dump_sample
```

The check reports:

- `placeholderModelIdsPresent`
- `stablePlaceholderModelIds`
- `canMapModelIdToBlockState`
- `realModelStoreReady=false`
- `realModelFactoryReady=false`
- `modelBakeryBridgeReady=false`
- `textureAtlasReady=false`
- `modelDataBufferReady=false`
- `modelColourBufferReady=false`
- `biomeTintReady=false`
- `lightmapReady=false`
- `resourceReloadReady=false`
- `formalShaderInputsReady=false`
- `formalModelBridgeReady=false`

The command may sample one current-world blockstate so the placeholder mapper can
prove stable ids and blockstate reverse mapping. This is still no-draw and no
atlas upload.

## P0 blockers

- A Forge-owned `ModelStore` equivalent does not exist.
- A Forge-safe `ModelFactory` / `ModelBakerySubsystem` bridge does not exist.
- Texture atlas allocation, upload, sampler ownership, and reload behavior are
  undefined.
- Formal shader inputs are missing: `modelData`, `colourData`, atlas, lightmap,
  tint/material metadata, and patched shader contract.
- Placeholder model ids are not enough for real textured terrain.

## Resource reload risks

The original path owns a model processing thread, upload queue, atlas texture,
model buffers, sampler, and shader inputs. Forge integration needs a clear reload
contract before any real atlas upload:

- What invalidates model ids?
- When is the atlas recreated?
- How are pending bake/upload jobs cancelled?
- How are debug geometry and formal model resources separated?
- What happens on world unload, dimension switch, resource reload, and preset
  clear?

Without these answers, a formal textured renderer would be easy to make visible
once and hard to keep correct.

## Why G6.9 remains no-draw

The draw API side is already proven by G6.7. Drawing real textured terrain would
require a correct model/atlas/shader input contract, not another draw-call
prototype. G6.9 therefore adds readiness visibility and documentation only.

## Next candidates

### G6.10 minimal ModelStore skeleton

Add a Forge-side no-texture or dummy-storage skeleton that reports intended
buffer sizes and lifecycle without uploading a real atlas. This should remain
no-draw until resource reload and ownership are clear.

### G6.10 model id audit + blockstate mapping

Make placeholder ids explicitly traceable to Voxy mapper block ids and Minecraft
blockstate strings, then audit how many encoded records can be mapped back.

### G6.10 resource reload lifecycle skeleton

Define no-draw clear/reload ordering for future model buffers, atlas, sampler,
and shader variants.

### G7.0 textured debug quad prototype

Only after model storage and reload ownership are understood, draw a tiny
textured debug sample. This should still not be the formal MDIC renderer.
