# Forge 1.20.1 formal renderer readiness audit

This G6.22 audit checks whether the Forge 1.20.1 PoC is ready to start a
formal renderer integration skeleton after G6.21. It is intentionally not a
renderer implementation document. The current textured paths are debug probes,
not a formal Voxy renderer.

Stage numbering note: G6.x labels are now treated as legacy labels for the
extended G phase. New work after G6.22 should use the H/I/J/K stage taxonomy,
starting with H1 formal renderer no-draw skeleton.

## Summary conclusion

The project has not gone off the rails, but it has reached the point where the
debug renderer should stop growing. G6.15 through G6.21 proved valuable pieces:
sample atlas upload, textured debug drawing, multi-block sample data, a
sample-set shader input bridge, and resource reload invalidation. Those pieces
are useful evidence for the formal renderer, but most of them should remain
isolated as debug and validation tools.

The next step can be `H1 formal renderer no-draw skeleton`, with a strict
scope:

- Allowed: create an ownership shell such as `ForgeFormalRendererManager`, add
  lifecycle/status/preset plumbing, and reference existing GL heap, metadata,
  model bridge readiness, and reload status.
- Forbidden: draw, bind a formal shader, call `MDICSectionRenderer`, call
  `VoxyRenderSystem`, replace existing debug renderers, or add shaderpack
  integration.

Direct formal textured rendering is not ready. The largest blockers are the real
`ModelFactory` / `ModelBakerySubsystem` bridge, formal `ModelStore` ownership,
formal shader contract, visibility/LOD traversal, and renderer ownership.

## Original Voxy ownership audit

The original formal renderer is not a single draw class. It is a coordinated
system.

- `VoxyRenderSystem` is the entry and lifecycle owner. It wires `WorldEngine`,
  `ModelBakerySubsystem`, `RenderGenerationService`,
  `BasicSectionGeometryData`, `BasicAsyncGeometryManager`,
  `HierarchicalOcclusionTraverser`, `RenderDistanceTracker`, viewport selection,
  and a section renderer backend.
- `BasicAsyncGeometryManager` owns section id allocation, geometry pointer
  allocation, metadata writes, heap uploads, and frees.
- `BasicSectionGeometryData` owns the 32-byte section metadata buffer and the GL
  geometry buffer used by formal section renderers.
- `MDICViewport` owns per-viewport command buffers: draw command buffer, draw
  count buffer, visibility buffer, indirect lookup buffer, and position scratch
  buffer.
- `MDICSectionRenderer` consumes the pipeline, `ModelStore`, geometry metadata,
  viewport buffers, visibility data, shared index buffer, and shader pipeline.
  Its terrain draw uses `glMultiDrawElementsIndirectCountARB`, but the draw call
  is only the last step after visibility, command generation, model input
  binding, and shader setup.
- `ModelBakerySubsystem` owns model bake lifetime. It owns `ModelFactory` and
  `ModelStore`, processes bake/upload work, reacts to biome/model data, and
  releases resources on shutdown.
- `ModelStore` owns the formal model data buffer, model colour buffer, atlas
  texture, and sampler. It binds them as shader inputs.
- `ModelFactory` owns the block-state/fluid-state to model-id lifecycle,
  deduplication, metadata cache, model record upload, biome colour data, and
  atlas tile upload.
- `RenderDataFactory` consumes real model ids and metadata while generating
  packed quad records and bucket offsets.
- `cmdgen.comp` consumes metadata, visibility buffers, indirect lookup, and
  position scratch data to generate formal draw commands.

Evidence files:

- `src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicAsyncGeometryManager.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicSectionGeometryData.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelStore.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelBakerySubsystem.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java`
- `src/main/resources/assets/voxy/shaders/lod/cmdgen.comp`

## Shader input contract audit

The formal shader input contract is wider than the current debug shader path.

- `bindings.glsl` defines model buffer and model colour buffer bindings, draw
  command layout, and draw count buffer layout.
- `block_model.glsl` defines a 64-byte `BlockModel` with `faceData[6]`,
  `flagsA`, `colourTint`, `customId`, and padding.
- `quad_util.glsl` extracts the model id from packed quad data, loads
  `modelData[modelId]`, reads `faceData[face]`, handles tint/alpha/shading
  flags, and builds quad output.
- `quads3.vert` binds quad buffer, model buffer, model colour buffer, and
  position scratch data, then vertex-pulls geometry.
- `quads.frag` samples the block model atlas from a 3-by-2 face-tile model grid
  and emits formal fragment outputs.

The Forge sample-set formal input bridge proves that a debug shader can bind and
read sample-set `modelData`, `modelColour`, a validity buffer, and a Forge-owned
atlas. It does not prove the full formal shader contract. The missing pieces
include full model id lifecycle, exact material semantics, lightmap, biome LUT,
alpha/cutout behavior, translucent handling, and shaderpack integration.

Evidence files:

- `src/main/resources/assets/voxy/shaders/lod/bindings.glsl`
- `src/main/resources/assets/voxy/shaders/lod/block_model.glsl`
- `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl`
- `src/main/resources/assets/voxy/shaders/lod/quads3.vert`
- `src/main/resources/assets/voxy/shaders/lod/quads.frag`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalShaderInputBridge.java`
- `src/main/java/me/cortex/voxy/forge/ForgeTexturedMdicDebugRenderer.java`
- `src/main/java/me/cortex/voxy/forge/ForgeTexturedMdicDebugShader.java`

## Forge PoC ownership audit

The Forge PoC is currently centered around `ForgeVoxyInstance`. That singleton
owns many independent debug and readiness systems:

- ingest/build/cache systems
- GL geometry upload and readback systems
- simple/direct/MDIC debug renderers
- model bridge readiness tools
- placeholder and sample-set model data
- atlas skeleton and upload probes
- textured debug renderers
- resource reload tracker/listener

This has been practical for the PoC, but it is not the final formal renderer
ownership shape. A formal renderer should introduce a dedicated owner with clear
boundaries instead of continuing to add draw features to `ForgeVoxyInstance` or
`ForgeVoxyCommands`.

Evidence files:

- `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java`
- `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java`
- `src/main/java/me/cortex/voxy/forge/ForgeGpuGeometryHeap.java`
- `src/main/java/me/cortex/voxy/forge/ForgeSectionGeometryManager.java`
- `src/main/java/me/cortex/voxy/forge/ForgeMdicCommandManager.java`
- `src/main/java/me/cortex/voxy/forge/ForgeMdicDebugRenderer.java`
- `src/main/java/me/cortex/voxy/forge/ForgeTexturedMdicDebugRenderer.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalShaderInputBridge.java`
- `src/main/java/me/cortex/voxy/forge/ForgeModelBridgeResourceReloadTracker.java`
- `src/main/java/me/cortex/voxy/forge/ForgeModelBridgeReloadListener.java`
- `src/main/java/me/cortex/voxy/forge/VoxyForge.java`

## Readiness matrix

| Area | Current status | Evidence | Formal-ready? | Blocker | Next action |
| --- | --- | --- | --- | --- | --- |
| WorldEngine lifecycle | Partial skeleton | `ForgeVoxyInstance`, `VoxyRenderSystem` | No | Forge owns an empty/skeleton world engine, not the original render lifecycle | Create no-draw formal manager that references but does not replace the skeleton |
| Chunk ingest | Partial PoC | `ForgeChunkIngestManager`, `RenderDataFactory` | No | Ingest is debug/preset driven and not tied to formal traversal/render scheduling | Keep as input source, define formal ownership later |
| BuiltSection generation | Partial | `ForgeVoxyBuiltSectionBuilder`, `ForgeVoxyQuadEncoder`, `RenderDataFactory` | Partial | Packed data exists, but model id source is still Forge placeholder/sample path | Preserve record learnings, replace model id lifecycle |
| Geometry record layout | Mostly aligned | `ForgeVoxyQuadEncoder`, `quad_format.glsl`, `quad_util.glsl` | Partial | Debug decoders work, but formal shader semantics are incomplete | Freeze layout audit before formal shader work |
| Metadata layout | Mostly aligned | `ForgeGpuGeometryHeap`, `ForgeSectionGeometryManager`, `BasicSectionGeometryData` | Partial | Forge metadata is validated, but ownership is not formal | Move ownership under formal manager later |
| GL geometry heap | Strong PoC infrastructure | `ForgeGpuGeometryHeap`, `BasicSectionGeometryData` | Partial | Upload/audit is solid, but lifecycle owner is debug-side | Reuse as infrastructure behind formal owner |
| Section id / geometry ptr allocation | Partial | `ForgeSectionGeometryManager`, `BasicAsyncGeometryManager` | No | Forge allocator is separate from original async geometry manager semantics | Decide whether to adapt or replace in formal skeleton |
| MDIC command layout | Strong debug proof | `ForgeMdicCommand*`, `cmdgen.comp` | Partial | CPU/debug generation is not formal compute `cmdgen.comp` ownership | Keep audit tools; separate formal DrawCommand ownership |
| Draw count buffer | Strong API proof | `ForgeMdicDebugShader`, `MDICSectionRenderer` | Partial | Buffer/API works, but viewport ownership is missing | Keep proof; formal manager must own or reference viewport buffers |
| Visibility / LOD traversal | Debug only | `ForgeMdicCommandPlanner`, `HierarchicalOcclusionTraverser`, `RenderDistanceTracker` | No | Radius/frustum fallback is not hierarchical occlusion or LOD traversal | Do not call formal renderer until traversal owner is defined |
| ModelStore record layout | Audited/sample only | `ForgeModelStoreFormalLayout`, `ModelStore`, `block_model.glsl` | No | Sample records are not real `ModelStore` lifecycle | Build real ModelFactory/ModelStore bridge before formal draw |
| ModelFactory / model id lifecycle | Placeholder/sample | `ForgeVoxyModelIdMapper`, `ModelFactory`, `ModelBakerySubsystem` | No | No real id mapping, bake queue, dedupe, fluid LUT, metadata cache | Plan and implement real bridge or compatible facade |
| Atlas ownership | Sample infrastructure | `ForgeModelAtlas*`, `ModelStore`, `RenderResourceReuse` | Partial | Forge-owned atlas exists for samples, not full model lifecycle | Keep atlas ownership concept, replace command-driven population |
| Atlas upload | Sample-set proof | `ForgeModelAtlasSampleSetUploader`, `ModelFactory` | No | Multi-block sample upload is not real bake/upload pipeline | Extend only after real ModelFactory path exists |
| modelColour / biome tint | Sample only | `ForgeModelSampleSetBuffer`, `ModelFactory`, `quad_util.glsl` | No | No real biome LUT, tint invalidation, or colour update lifecycle | Treat as blocker for textured formal shader |
| Formal shader input bindings | Sample bridge | `ForgeFormalShaderInputBridge`, `bindings.glsl` | Partial | Bridge is sample-set only and explicitly not formal shader ready | Reuse binding concept, not the sample data source |
| Resource reload | Good invalidation pattern | `ForgeModelBridgeResourceReloadTracker`, `ForgeModelBridgeReloadListener`, `VoxyForge` | Partial | Listener stales sample resources, but formal resource ownership is absent | Reuse pattern in formal manager |
| Dimension switch / world unload | Good debug hygiene | `ForgeVoxyInstance` | Partial | Broad clear works for debug state, not a formal renderer lifecycle contract | Formal manager should expose explicit unload/dimension hooks |
| Renderer ownership | Missing formal owner | `ForgeVoxyInstance`, `VoxyRenderSystem` | No | Too much lives in singleton/debug command space | H1 should create no-draw formal ownership shell |
| VoxyRenderSystem | Not ported | `VoxyRenderSystem` | No | Original depends on pipeline, traversal, model bakery, Iris/Sodium hooks | Do not instantiate directly; design Forge equivalent shell |
| MDICSectionRenderer | Not integrated | `MDICSectionRenderer`, `MDICViewport` | No | Requires pipeline, ModelStore, viewport buffers, visibility, shader patches | Do not call yet |
| Shaderpack / Iris / Oculus / Embeddium | Not started | `VoxyRenderSystem`, shader pipeline hooks | No | Original has Sodium/Iris/FREX assumptions; Forge path avoids these | Keep out of H1 |
| Commands / presets | Extensive PoC tools | `ForgeVoxyCommands` | No | Useful for validation, too large and debug-heavy for formal UX | Keep tools, later split/debug-gate them |

## Debug and formal boundary

### Can migrate into formal renderer infrastructure

- GL geometry heap and metadata validation concepts.
- Section metadata and bucket offset layout.
- DrawElementsIndirect and indirect count API proof.
- Command buffer audit concepts.
- Resource reload invalidation pattern.
- Formal shader input binding concept for model data, colour data, and atlas.
- Atlas layout and coordinate mapping.
- Lifecycle lessons from dimension switch, world unload, and reload stale paths.

Reason: these pieces match original Voxy mechanisms or prove low-level GL/state
behavior that a formal renderer needs.

### Should remain debug or validation tools

- `ForgeMdicDebugRenderer`.
- `ForgeTexturedMdicDebugRenderer`.
- `ForgeTexturedReadbackRenderer`.
- `ForgeTexturedDebugQuadRenderer`.
- GL heap readback visualization.
- Atlas sample upload/readback audits.
- Model sample set audits.
- Most `/voxy` status/stress commands created for G4 through G6.

Reason: these prove behavior and make regressions visible, but they bypass or
simplify formal ownership, traversal, model bake, shader, and material paths.

### Must be replaced or redone

- Placeholder model id lifecycle.
- Sample-set ModelStore records.
- Command-driven sample atlas population.
- Shader-side model id filter and validity-table filtering.
- CPU sample selection for textured debug.
- Textured debug shader logic.
- Renderer ownership through the general `ForgeVoxyInstance` singleton.

Reason: these are scaffolds that helped prove pieces, but they are not the real
`ModelFactory` / `ModelBakerySubsystem` / `ModelStore` / formal shader pipeline.

## Where direct MDICSectionRenderer integration would fail

Calling the original `MDICSectionRenderer` now would be premature because it
expects all of these inputs to already exist:

- a formal `AbstractRenderPipeline` and shader patching path,
- a real `ModelStore` with full model buffer, colour buffer, atlas, and sampler,
- `BasicSectionGeometryData`-style metadata/geometry ownership,
- `MDICViewport` buffers and viewport lifecycle,
- visibility buffers, indirect lookup, position scratch, and draw count buffers,
- render distance and hierarchical occlusion traversal,
- formal resource reload and shutdown ordering,
- shaderpack/Sodium/Iris/FREX integration decisions.

The Forge PoC has debug equivalents for some of these, but not the formal
ownership graph. Bridging them ad hoc would risk creating a second renderer that
resembles Voxy at the draw-call level while missing the system contracts around
it.

## Current visible LoD capability

Current visible capability is real but narrow:

- Multi-block textured MDIC debug geometry exists.
- It can show textured geometry through the GL heap, command list, indirect
  count draw call, sample-set model data, and Forge-owned atlas.
- It is not a usable LoD renderer.

Why it is not usable LoD:

- The model set is sampled, not generated by real `ModelFactory` lifecycle.
- The atlas is populated by commands, not by formal bake/upload ownership.
- The shader is a debug shader, not the formal shader contract.
- Visibility is debug/radius/frustum-fallback, not hierarchical LOD traversal.
- Material, lightmap, biome tint, translucency, fluid, resource reload rebuild,
  and shaderpack integration are incomplete.

## Distance to usable LoD

### A. Current technical demo

Definition: a preset can show Voxy/MDIC-driven LoD-like debug or sample textured
geometry.

Status: yes, as debug. The current PoC can display multi-block textured MDIC
debug geometry. It should be described as debug geometry, not a usable renderer.

### B. Early textured LoD demo

Definition: distant multi-block geometry uses real Minecraft textures through a
preset, lifecycle is mostly stable, but the implementation can still be
debug/formal-hybrid.

Estimated remaining work: about 3 to 5 focused stages.

Needed modules:

- no-draw formal renderer manager and ownership boundary,
- real ModelFactory/ModelBakery bridge plan or minimal compatible facade,
- formal shader input hardening beyond sample sets,
- resource reload rebuild path, not only stale invalidation,
- multi-model textured MDIC hardening without debug-only filters.

Main risk: continuing to add debug draw features instead of replacing sample data
with real model bake lifecycle.

### C. Early usable LoD renderer

Definition: usable as an early Forge Voxy renderer with formal lifecycle, formal
shader inputs, basic model bake, stable dimension switch/reload, and clear
renderer ownership.

Estimated remaining work: about 7 to 12 focused stages after this audit.

Needed modules:

- formal renderer ownership shell,
- real model id lifecycle,
- real ModelStore/atlas upload path,
- formal shader contract implementation,
- LOD/visibility traversal owner,
- resource reload rebuild and cleanup,
- clear presets/configuration,
- regression/stress coverage for world unload, dimension switch, reload, and
  large model sets.

Largest blocker: real `ModelFactory` / `ModelBakerySubsystem` bridge and formal
`ModelStore` ownership. Without that, textured output remains sample-driven.

## Recommended H1 scope

Recommended next stage: `H1 formal renderer no-draw skeleton`.

Allowed:

- `ForgeFormalRendererManager` or equivalent ownership shell.
- Lifecycle/status/preset entries.
- References to GL heap, metadata readiness, MDIC command readiness, model bridge
  readiness, atlas readiness, and reload readiness.
- Clear reporting that `formalRendererReady=false`.
- No-draw validation that all required components are present or missing.

Forbidden:

- formal draw,
- binding formal shaders,
- invoking `MDICSectionRenderer`,
- invoking `VoxyRenderSystem`,
- replacing existing debug renderers,
- shaderpack/Iris/Oculus/Embeddium work,
- turning sample-set data into a claimed formal ModelStore.

Why no-draw: the project now needs an ownership boundary before more rendering.
Without that boundary, the debug renderer will keep expanding and make the final
renderer harder to reason about.

## H1 formal renderer no-draw skeleton

H1 adds a formal renderer ownership shell without adding any rendering path. The
shell is represented by `ForgeFormalRendererManager` and related status/readiness
records. Its purpose is to give future formal renderer work a place to attach
lifecycle, readiness, blockers, and preset plumbing without continuing to expand
the debug renderers.

H1 explicitly does not:

- draw,
- bind a formal shader,
- call `MDICSectionRenderer`,
- call `VoxyRenderSystem`,
- replace the existing MDIC debug renderer,
- replace the textured MDIC debug renderer,
- promote sample-set data to a real `ModelStore`.

The H1 manager aggregates readiness from existing systems such as the GL geometry
heap, metadata, section geometry manager, MDIC command buffer, draw count proof,
model bridge status, sample-set shader input bridge, atlas sample-set upload,
resource reload listener, current dimension, and WorldEngine skeleton. It reports
these as prerequisites and blockers only. A ready skeleton can therefore report
`formalRendererSkeletonReady=true` while still keeping:

```text
formalRendererReady=false
actualDrawEnabled=false
noDraw=true
```

The manager also records lifecycle events that should disable or stale the future
formal renderer owner:

- world unload,
- dimension switch,
- resource reload,
- debug pipeline clear,
- preset off,
- preset clear.

Debug renderer isolation is part of the H1 contract. Enabling or clearing the H1
formal renderer skeleton must not start or clear the existing MDIC debug renderer,
the textured MDIC debug renderer, the GL heap, the simple renderer, or sample
model/atlas data.

H2 candidates:

- harden lifecycle/status reporting around the no-draw manager,
- split command formatting if `ForgeVoxyCommands` keeps growing,
- add prerequisite reports for formal ownership without drawing,
- define the exact H/I boundary for real `ModelFactory` / `ModelBakery` work.

## Blocker list

P0 blockers before formal draw:

- Real `ModelFactory` / `ModelBakerySubsystem` bridge or compatible Forge owner.
- Formal `ModelStore` ownership and rebuild lifecycle.
- Formal shader input contract beyond sample-set buffers.
- Renderer ownership boundary and lifecycle.
- Resource reload rebuild path, not only invalidation.

P1 blockers before early usable LoD:

- Visibility/LOD traversal ownership.
- Formal command buffer ownership separation.
- Model colour, biome tint, lightmap, material, alpha/cutout semantics.
- Dimension/world unload stress under formal ownership.

P2 blockers that can stay later:

- Shaderpack/Iris/Oculus/Embeddium integration.
- Advanced occlusion performance.
- Translucent sorting quality.
- Full renderer performance tuning.

## Final G6.22 answer

The route is still coherent. The Forge PoC has proven real low-level pieces and
made the first visible textured MDIC debug path work. The visual progress was
limited for many stages because most stages changed internal ownership, buffers,
command layout, audits, atlas upload, or shader inputs, not the final visual
style. That work was still useful, but it is now time to stop adding debug draw
modes and establish the formal renderer ownership shell.

The next stage should be no-draw. If H1 starts drawing, it risks blending
debug scaffolding with formal renderer responsibilities too early.
