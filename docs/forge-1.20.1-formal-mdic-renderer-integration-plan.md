# Forge 1.20.1 formal MDIC renderer integration plan

This document supersedes the old debug/preview MDIC integration plan.

The formal Forge MDIC path must be a port/adaptation of original Voxy
`MDICViewport`, `MDICSectionRenderer`, and shader contracts. Preview owners,
debug command buffers, synthetic cmdgen validators, and K10 visible preview
resources are deprecated and must not be extended into the renderer.

New MDIC renderer work uses the Roman-round route. The current MDIC command /
section-renderer owner route is:

```text
V_ORIGINAL_MDIC_COMMAND_GENERATION_CHAIN
 -> V.1_ORIGINAL_MDIC_VIEWPORT_CMDGEN_INPUT_PARITY
 -> V.2_ORIGINAL_CMDGEN_COMP_OUTPUT_PARITY
 -> V.3_ORIGINAL_CMDGEN_READBACK_AND_BARRIER_AUDIT
 -> VI_ORIGINAL_MDIC_SECTION_RENDERER_CHAIN
```

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
5. Use the Forge-port `NodeManager` / `NodeStore` / `SectionUpdateRouter` /
   `NodeCleaner` / `GeometryCache` / `RenderDistanceTracker` ownership layer.
6. Port original `Viewport` / `MDICViewport` / HiZ / render-list ownership. Done
   for the active Roman route, including original-style depth/stencil setup
   into a Voxy-owned `DepthFramebuffer(GL_DEPTH24_STENCIL8)` before HiZ.
7. Port `HierarchicalOcclusionTraverser` request-batch production. Done for the
   active Roman route at the owner/traversal level.
8. Roman V: port production `prep.comp`, cull-raster visibility input,
   `cmdgen.comp` dispatch contract, output readback, and barrier audit as one
   coherent multi-step round. Implemented against real viewport, visibility,
   indirect lookup, metadata, and geometry buffers.
9. Roman VI: fold command generation into
   `ForgeOriginalVoxyMdicSectionRenderer`, port original terrain/translucent
   shader program ownership, prefix-sum and `buildtranslucents.comp` command
   build tail, shared index, lightmap binding adapter, and original-shaped
   `renderOpaque` / `renderTemporal` / `renderTranslucent` binding methods.
   The active hook still does not submit MDIC terrain draw calls.
10. Roman VII: bind the original `ModelStore` and terrain shader contract
    exactly as original Voxy expects. The first VII pass is now implemented at
    the owner/source-contract level: `ForgeOriginalVoxyRenderPipeline` mirrors
    the `AbstractRenderPipeline` shader hook boundary, and
    `ForgeOriginalVoxyMdicSectionRenderer` now builds original
    `quads3.vert`/`quads.frag` terrain and translucent programs through the
    original TAA hook, shader patch hook, and patched-or-normal fallback shape.
    The original `NormalRenderPipeline` colour-target owner shape is now also
    present: opaque colour texture, SSAO/translucent colour texture, the
    framebuffer attachment handoff, and the final blit shader owner. SSAO
    compute execution, final transform/blit handoff, environmental fog
    uniforms, and Oculus-backed shaderpack patch semantics are still not
    ported, so the active hook still does not submit MDIC terrain draw calls.
11. Roman VIII: only then enable controlled renderer draw behind the original
    renderer owner.

## Non-negotiable blockers

Do not enable formal MDIC draw while any of these are true:

```text
original render-pipeline final handoff / VoxyRenderSystem lifecycle not yet ported
original terrain shader SSAO/fog/shaderpack-patch semantics incomplete
MDICSectionRenderer draw methods not called by active pipeline
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
