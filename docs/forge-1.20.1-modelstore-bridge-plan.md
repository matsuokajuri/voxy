# Forge 1.20.1 ModelStore parity plan

This document supersedes the old sample model-store bridge plan.

The Forge ModelStore path must match original Voxy `ModelStore` ownership and
binding semantics. Sample buffers and sample atlases are deprecated.

## Original file

```text
src/main/java/me/cortex/voxy/client/core/model/ModelStore.java
```

Related consumers:

```text
src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java
src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java
src/main/resources/assets/voxy/shaders/lod/block_model.glsl
src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl
```

## Required parity

Forge must own the same formal resources:

```text
modelData buffer
modelColour buffer
block model atlas texture
sampler
binding points expected by shaders
lifecycle cleanup
reload invalidation
```

## Deprecated prior route

Do not use these as formal ModelStore proof:

```text
ForgeRealModelStoreSample
ForgeModelSampleSet
ForgeModelAtlasSampleSetUploader
ForgeFormalShaderInputBridge with sample-set data
debug small atlas fallback as readiness
```

They are historical evidence only.

## Required behavior

- `ModelFactory` writes real 64-byte model records into `modelData`.
- `ModelFactory` writes model colour entries into `modelColour`.
- atlas uploads come from software-baked formal model faces.
- shader binding indices match original Voxy contracts.
- resource deletion is owned and render-thread safe.
- allocation failure is reported honestly and does not become readiness.

## Validation

Readback validation is acceptable, but it must validate the real formal
ModelStore owner, not a sample buffer.

Checks:

```text
modelData readback equals CPU formal record
modelColour readback equals CPU entry
atlas tile readback equals software-baked pixels
binding indices match bindings.glsl
sample/debug source usage is false
```
