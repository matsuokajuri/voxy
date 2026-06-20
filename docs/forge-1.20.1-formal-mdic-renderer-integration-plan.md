# Forge 1.20.1 formal MDIC renderer integration plan

This document supersedes the old debug/preview MDIC integration plan.

The formal Forge MDIC path must be a port/adaptation of original Voxy
`MDICViewport`, `MDICSectionRenderer`, and shader contracts. Preview owners,
debug command buffers, synthetic cmdgen validators, and K10 visible preview
resources are deprecated and must not be extended into the renderer.

## Forge frontend prerequisites

Formal MDIC work must assume the Forge client is running with:

```text
Embeddium
Oculus
```

Embeddium is the Forge-side Sodium frontend replacement used for chunk renderer
and related frontend parity work. Oculus is the Forge-side Iris replacement
used for shaderpack integration parity. Do not wire MDIC against old Fabric
runtime dependencies.

## Original files to treat as authoritative

```text
src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport.java
src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java
src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicSectionGeometryData.java
src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicAsyncGeometryManager.java
src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl
src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp
src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert
src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag
src/main/resources/assets/voxy/shaders/lod/section.glsl
src/main/resources/assets/voxy/shaders/lod/quad_format.glsl
src/main/resources/assets/voxy/shaders/lod/quad_util.glsl
src/main/resources/assets/voxy/shaders/lod/block_model.glsl
```

## Required ownership parity

Forge must reproduce the original ownership boundary:

```text
BasicSectionGeometryData
 -> geometry and section metadata buffers

MDICViewport
 -> draw command buffer
 -> draw count buffer
 -> visibility buffer
 -> indirect lookup buffer
 -> position scratch buffer

MDICSectionRenderer
 -> shared index buffer
 -> shader pipeline
 -> model store bindings
 -> command generation dispatch
 -> glMultiDrawElementsIndirectCountARB
```

No debug buffer or preview buffer may be promoted into these owners.

## Deprecated prior route

The following prior work is now historical only:

```text
ForgeMdicDebugRenderer
ForgeTexturedMdicDebugRenderer
ForgeFormalCmdgenGpuValidator
ForgeFormalCmdgenRealSectionDryRun
ForgeFormalIsolatedMdicDrawSmokeTest
ForgeFormalTerrainShaderIntegration
ForgeFormalVisibleLodPreview
```

Those paths can be used to understand mistakes and regressions, but not as
implementation scaffolding for formal MDIC.

## Integration order

1. Use the Forge-local original-parity `RenderGenerationService` /
   `RenderDataFactory` / `BuiltSection` output as the section geometry source.
2. Use the Forge-port `BasicAsyncGeometryManager` allocation, alignment,
   remove/reuse, metadata, and upload-event semantics.
3. Use the Forge-port `BasicSectionGeometryData` render-thread metadata and
   geometry buffer owner.
4. Use the Forge-port async geometry sync to apply geometry upload and metadata
   event sets into `BasicSectionGeometryData` through original-style
   `UploadStream`, `memcpy.comp`, and `scatter.comp`.
5. Port full `NodeManager` ownership around generated sections, replacing the
   temporary section-id side map with original active-section/request state.
6. Port `MDICViewport` buffers and lifecycle.
7. Port production `cmdgen.comp` inputs and dispatch contract.
8. Port `MDICSectionRenderer` draw setup and binding order.
9. Bind the original `ModelStore` exactly as original Voxy expects.
10. Only then enable controlled renderer draw.

## Non-negotiable blockers

Do not enable formal MDIC draw while any of these are true:

```text
AsyncNodeManager / NodeManager parity incomplete
RenderDistanceTracker / HierarchicalOcclusionTraverser parity incomplete
MDICViewport parity incomplete
production cmdgen.comp not wired
original shader binding contract incomplete
original common ServiceManager parity incomplete
```

## Validation

Validation should confirm original contract parity, not preview success.

Useful checks:

- buffer sizes and strides match original;
- draw command layout matches `bindings.glsl`;
- section metadata layout matches `section.glsl`;
- command generation consumes real visibility/render-list inputs;
- generated commands are submitted only by the formal MDIC renderer;
- no deprecated preview/debug owner is part of the formal path.
