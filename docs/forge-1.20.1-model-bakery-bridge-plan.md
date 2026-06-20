# Forge 1.20.1 model bakery bridge plan

This document supersedes the old one-block, safe-set, and preview-shader model
bridge plan.

The model pipeline must now follow original Voxy `ModelBakerySubsystem`,
`ModelFactory`, `ModelStore`, `SoftwareModelTextureBakery`, `TextureUtils`, and
`ModelQueries` behavior.

## Original files to treat as authoritative

```text
src/main/java/me/cortex/voxy/client/core/model/ModelBakerySubsystem.java
src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java
src/main/java/me/cortex/voxy/client/core/model/ModelStore.java
src/main/java/me/cortex/voxy/client/core/model/TextureUtils.java
src/main/java/me/cortex/voxy/client/core/model/ModelQueries.java
src/main/java/me/cortex/voxy/client/core/model/bakery/SoftwareModelTextureBakery.java
src/main/java/me/cortex/voxy/client/core/model/bakery/SoftwareRasterizer.java
src/main/java/me/cortex/voxy/client/core/model/bakery/ReuseVertexConsumer.java
```

## Superseded route

The following old directions are deprecated:

```text
one-block formal bake/upload as route
small safe-set as route
sample-set modelData/modelColour/atlas bridge
first BakedQuad sprite extraction
rejecting fluid/cutout/translucent/tinted blocks up front
manual QA preset as lifecycle substitute
preview shader validation as model-store proof
```

These may remain in history but must not guide new implementation.

## Required parity chain

Forge must converge on:

```text
blockStateId request
 -> ModelFactory addEntry semantics
 -> fluid state pre-bake ordering
 -> bake queue / in-flight map
 -> SoftwareModelTextureBakery renderToOutput
 -> TextureUtils layer/depth/bounds/tint processing
 -> metadataCache
 -> fluidStateLUT
 -> idMappings
 -> modelTexture2id dedupe
 -> ModelStore modelData/modelColour/atlas upload
 -> upload queue processed on correct GL thread
 -> resource reload invalidation/rebuild
```

## Current Forge direction

Recent parity remediation added:

- Forge-adapted `ForgeSoftwareModelTextureBakery`;
- Forge-local `ForgeModelQueries`;
- formal metadata propagation into direct section geometry;
- deprecation markers for sample-set and preview shader sources.
- `ForgeOriginalVoxyModelPipeline`, a focused Roman-route owner boundary that starts
  from the active `WorldEngine`, queues existing mapper biomes, attaches the
  mapper biome callback, and queues future block bake requests without using
  safe-set or preview sources.
- `ForgeOriginalVoxyModelFactory`, a Forge-port of the original `ModelFactory`
  mapping and upload core. It owns `idMappings`, `metadataCache`,
  `fluidStateLUT`, `modelTexture2id`, fluid pre-bake ordering, in-flight
  tracking, software colour/depth bake, and formal ModelStore uploads.
- The Forge-port owner now mirrors the first original `ModelBakerySubsystem`
  split: a `"Model factory processor"` worker thread drains block/biome bake
  work, `LockSupport.unpark` wakes it on requests, and render ticks consume an
  upload-result queue on the GL thread.
- The model upload result now carries the packed 3x2 mip-chain atlas tile and
  biome colour LUT updates, matching the original `ModelBakeResultUpload` /
  `BiomeUploadResult` responsibilities at the semantic level.

The on-demand missing-model request/requeue behavior from
`RenderGenerationService` is now present in the Forge parity route, backed by a
Forge-local port of original `RenderDataFactory` and `IdNotYetComputedException`
semantics. Original `UploadStream`-equivalent persistent mapped staging now
backs original-route modelData/modelColour uploads.

## Compile-source-set constraint

The original `me.cortex.voxy.client.core.model.*` files remain authoritative
references, but they are not compiled by the current Forge source set. The
Forge implementation must therefore port the original semantics into Forge
classes rather than directly import the original classes.

This constraint does not permit behavioral fallback. The Forge port must still
match:

```text
ModelBakerySubsystem queue/thread behavior
ModelFactory idMappings / metadataCache / fluidStateLUT / modelTexture2id
SoftwareModelTextureBakery colour/depth output
ModelStore modelData/modelColour/atlas upload contract
RenderGenerationService missing-model request/requeue behavior
```

## Current parity status

Implemented in the current Forge-port model route:

```text
dedicated ModelBakerySubsystem processing thread
upload-result queue separated from bake processing
biome colour LUT upload
packed 3x2 mip-chain atlas upload
UploadStream persistent mapped staging for original modelData/modelColour writes
TextureUtils helper logic port
fluid pre-bake ordering
idMappings / metadataCache / fluidStateLUT / modelTexture2id ownership
RenderGenerationService missing-model request/requeue
RenderDataFactory raw WorldSection mesh generation
```

Still incomplete and must not be treated as readiness:

```text
BasicAsyncGeometryManager / BasicSectionGeometryData output ownership
```

The original model route now closes the `ModelFactory -> ModelStore` upload
audit loop. After `UploadStream.commit()`, Forge reads back the original
modelData record, any modelColour range written by the upload, and the 3x2
atlas mip-chain region for each committed model upload. These checks are
reported as original-route status fields and do not use historical formal-store
or preview readback paths.

The original Iris/Oculus custom block-state id hook is also present:
`WorldRenderingSettings.INSTANCE.getBlockStateIds()` is read from Oculus and
provided to `ForgeOriginalVoxyModelFactory`, which writes the custom id word in
the 64-byte model record exactly where original Voxy does. If Oculus reports no
active block-state id map, the custom id remains zero.

`SoftwareModelTextureBakery` now uses the Forge/Embeddium source-equivalent
quad material route: block quads are read through Embeddium's injected
`BakedQuadView`, render layers are mapped through the same solid/cutout/
cutout-mipped/tripwire/translucent categories used by Embeddium's
`DefaultMaterials`, and sprite transparency/dark-cutout behavior follows
Embeddium's `SpriteContentsMixin` signal. This removes the previous raw
vanilla vertex-array and guessed metadata route from the active model pipeline.

## Required behavior

The model lifecycle must support:

- arbitrary block states seen by `RenderDataFactory`;
- missing model request and section generation requeue;
- fluid-state model ordering before containing block model upload;
- dedupe only when original-equivalent signatures prove safety;
- metadata cache lookups from geometry generation;
- fluid LUT lookups from geometry generation;
- biome/model colour behavior equivalent to original Voxy;
- formal `ModelStore` uploads owned by the model subsystem.

## Forbidden shortcuts

Do not use:

```text
sample-set atlas as formal atlas
sample-set model ids as formal ids
placeholder/debug model ids as formal ids
BakedQuad first-sprite pixels as model texture
safe-set coverage as model lifecycle readiness
preview shader readback as model-store readiness
```

## Validation

Validation should compare the Forge outputs against original Voxy semantics:

- `BlockModel` record is 64 bytes and fields match original meaning;
- face data comes from software colour/depth bake;
- metadata bits match `ModelQueries`;
- fluid model id LUT is populated before dependent block models;
- modelData/modelColour/atlas uploads read back correctly;
- no sample/debug model path is used as source.
