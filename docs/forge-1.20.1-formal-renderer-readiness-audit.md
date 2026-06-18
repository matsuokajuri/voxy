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

## H2 formal renderer lifecycle/status hardening

H2 keeps the H1 boundary intact and does not add draw behavior. The work hardens
the formal renderer shell so it behaves like a future renderer owner even while
`formalRendererReady=false`.

The formal manager lifecycle is now reported with a small explicit state set:

```text
DISABLED
ENABLED_NO_DRAW
STALE
CLEARED
```

Status also reports the latest readiness check, stale reason, and generation
counters:

```text
lastCheckAt
lastCheckReason
lastStaleReason
requiresRecheck
readinessGeneration
lifecycleGeneration
```

This makes it clear whether the no-draw manager was merely enabled, whether a
resource reload or dimension/world lifecycle event invalidated it, and whether a
new `/voxy formal_renderer_check` is needed before trusting the readiness
snapshot.

Readiness aggregation is split into layers:

- `infrastructureReady`: GL heap, metadata, section manager, world/dimension,
  and reload listener are present.
- `debugProofReady`: debug MDIC command and draw-count proof artifacts exist.
- `sampleBridgeReady`: sample-set formal input bridge and sample atlas upload are
  ready.
- `formalPrerequisitesReady`: real formal prerequisites are ready.

`formalPrerequisitesReady` remains `false` because real `ModelFactory`,
`ModelStore`, formal shader, and formal traversal are still missing. This
separation prevents sample bridge success from being confused with a formal
renderer.

Blockers now carry structured fields:

```text
id
severity
title
reason
nextAction
formalDrawBlocking
```

The P0 blockers still block formal draw. H2 intentionally does not reduce the
blocker list to make the status look better.

The `formal_renderer_skeleton` preset remains no-draw. It only enables the formal
manager, runs a readiness check, and prints blocker/readiness status. It must not
enable the existing MDIC debug renderer, textured MDIC debug renderer, sample-set
build, atlas upload, or any draw path.

H3 / I1 candidates:

- H3: prerequisite wiring reports for formal ownership, still no draw.
- I1: real `ModelFactory` / `ModelBakerySubsystem` bridge plan.
- I2: formal `ModelStore` ownership skeleton.

## I1 model bakery bridge note

I1 is tracked in `docs/forge-1.20.1-model-bakery-bridge-plan.md`. It addresses
the P0 `real ModelFactory / ModelBakery bridge missing` blocker as a design
audit only. It does not implement Java, draw, bind a formal shader, call
`MDICSectionRenderer`, or treat sample-set model data as a formal `ModelStore`.

## I2 formal ModelStore ownership note

I2 adds a formal ModelStore ownership skeleton. It owns the empty formal
`modelData` buffer, `modelColour` buffer, Voxy-style atlas texture, and sampler
as lifecycle-managed resources. It remains no-bake and no-draw.

The formal renderer manager can now report:

```text
formalModelStoreSkeletonReady
formalModelStoreOwnerReady
realModelStoreReady=false
formalRendererReady=false
```

This changes the blocker shape but not the formal readiness conclusion. The old
"formal ModelStore missing" blocker becomes more precise:

```text
P0_FORMAL_MODELSTORE_REAL_DATA_MISSING
P0_FORMAL_MODELSTORE_REBUILD_MISSING
```

The owner exists, but no real `ModelFactory` has populated model records, no
formal bake lifecycle exists, and resource reload still cannot rebuild real
model/atlas data.

## I3 formal ModelFactory lifecycle note

I3 adds a formal `ModelFactory` lifecycle skeleton. It can request a
`blockStateId`, track seen/pending/in-flight/completed skeleton state, assign a
formal model id placeholder, and populate placeholder `metadataCache`,
`fluidStateLUT`, and `modelTexture2id` structures.

It remains no-bake, no-upload, and no-draw. It does not call Forge `BakedModel`
logic, does not write real formal ModelStore records, does not upload atlas
pixels, and does not bind a formal shader.

The formal renderer manager can now report:

```text
formalModelFactorySkeletonReady
formalModelFactoryLifecycleReady
realModelFactoryReady=false
realModelBakeryReady=false
formalRendererReady=false
```

The blocker list is refined. The old generic ModelFactory blocker becomes:

```text
P0_REAL_MODEL_FACTORY_REAL_BAKE_MISSING
P0_REAL_MODEL_FACTORY_UPLOAD_PIPELINE_MISSING
```

This means the lifecycle shell exists, but formal draw is still blocked until
real bake results can populate the formal ModelStore and atlas.

## Blocker list

P0 blockers before formal draw:

- Real `ModelFactory` bake path and upload pipeline.
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

## I4 readiness note

I4 adds a one-block real bake/upload prototype into the I2 formal `ModelStore`
owner. It can take one safe solid Forge `BakedModel` sample, build one 64-byte
formal model record, upload one model colour entry, upload six face tiles into
the formal atlas, and audit the readback.

This improves the model pipeline evidence, but it does not make the formal
renderer ready. The formal renderer manager may report one-block prototype
readiness, while still reporting:

```text
realModelFactoryReady=false
realModelBakeryReady=false
realModelStoreReady=false
formalRendererReady=false
actualDrawEnabled=false
```

The remaining P0 blockers are still the general real bake lifecycle, full
formal ModelStore population, resource reload rebuild, and formal shader.

## I5 readiness note

I5 adds a small multi-block formal bake/upload prototype. It takes several safe
solid Forge block states through `BakedModel`, `BakedQuad`, and
`TextureAtlasSprite`, assigns I3 formal model ids, uploads formal modelData,
modelColour, and atlas face tiles into the I2 formal `ModelStore` owner, and
audits readback.

This is stronger evidence than I4 because multiple formal ids and multiple
atlas regions are exercised. It still is not a formal renderer. It does not
bind a formal shader, draw, call `MDICSectionRenderer`, or call
`VoxyRenderSystem`.

The formal renderer manager may report:

```text
multiBlockBakePrototypeReady=true
multiBlockFormalUploadReady=true
multiBlockFormalUploadAuditReady=true
```

while still reporting:

```text
realModelFactoryReady=false
realModelBakeryReady=false
realModelStoreReady=false
formalTexturedShaderReady=false
formalRendererReady=false
actualDrawEnabled=false
```

The remaining blocker is still the full real `ModelFactory` /
`ModelBakerySubsystem` lifecycle, including rebuild after resource reload,
broader block coverage, fluid/tint/material handling, and formal shader
consumption.

## I6 readiness note

I6 adds a formal ModelBakery lifecycle coordinator for the already proven I5
safe-set bake/upload path. It tracks lifecycle, resource, and upload
generations, proves command-driven rebuild after reload invalidation, and audits
alias-safe dedupe mapping semantics.

The formal renderer manager can now report:

```text
formalModelBakeryLifecycleSkeletonReady=true
reloadRebuildPrototypeReady=true
aliasSafeDedupeReady=true
```

These flags mean the lifecycle prototype can invalidate and rebuild the safe set
deterministically. They do not mean the full real `ModelFactory`,
`ModelBakerySubsystem`, or `ModelStore` is ready.

The readiness boundary remains:

```text
realModelFactoryReady=false
realModelBakeryReady=false
realModelStoreReady=false
formalTexturedShaderReady=false
formalRendererReady=false
actualDrawEnabled=false
```

Remaining formal renderer blockers include broad model coverage, full resource
reload rebuild ownership, fluid/tint/material/light semantics, formal shader
consumption, and formal MDIC renderer integration.

## J1 readiness note

J1 adds a formal shader input consumption skeleton. It validates that the I6
safe-set records uploaded into the I2 formal `ModelStore` owner are addressable
through formal model ids, and that the formal owner exposes usable
modelData/modelColour buffers, atlas texture, and sampler handles for the known
shader binding layout.

The J1 path performs a no-draw bind/unbind validation of the formal resources.
It does not bind a formal shader program, does not draw terrain, does not call
`MDICSectionRenderer`, and does not call `VoxyRenderSystem`.

The formal renderer manager can now report:

```text
formalShaderInputConsumerReady=true
formalShaderInputBindingLayoutKnown=true
formalShaderInputBindingLayoutCompatible=true
```

Those flags mean the formal input resources and binding layout have been
validated against the formal owner. They do not mean the final formal shader or
renderer is ready. The readiness boundary remains:

```text
formalShaderInputContractReady=false
formalTexturedShaderReady=false
formalRendererReady=false
actualDrawEnabled=false
```

Remaining blockers include the actual formal shader draw program, full shader
contract semantics for light/tint/material/alpha, formal MDIC renderer
integration, and broader model lifecycle coverage.

## J2 readiness note

J2 adds an audit-only formal shader program validator. It compiles and links a
small validation program, binds the I2 formal `ModelStore` owner resources
validated by J1, runs a compact GPU readback validation for several formal model
ids, and confirms that the formal shader side can read selected modelData,
modelColour, and atlas values.

The J2 path is still not a terrain renderer. The validation program is not
`MDICSectionRenderer`, does not call `VoxyRenderSystem`, does not start a visible
draw, and does not claim the final formal shader contract is complete.

The formal renderer manager can now report:

```text
formalShaderProgramValidatorReady=true
formalShaderProgramValidationReady=true
validationShaderCompileOk=true
validationProgramLinkOk=true
gpuValidationOk=true
```

Those flags mean an audit-only program can consume the current formal resources.
They do not mean the formal textured shader or renderer is ready. The readiness
boundary remains:

```text
formalShaderInputContractReady=false
formalTexturedShaderReady=false
formalRendererReady=false
actualDrawEnabled=false
```

Remaining blockers include the actual terrain shader program, full formal shader
semantics for light/tint/material/alpha, formal MDIC renderer integration,
visibility traversal, and broader model lifecycle coverage.

## J3 readiness note

J3 adds a formal textured shader preview prototype. It compiles and links a
small vertex/fragment preview program, binds the I2 formal `ModelStore` owner
resources validated by J1/J2, renders selected I6 safe-set formal model ids into
a small offscreen framebuffer, and reads back preview checksums.

The J3 preview is deliberately isolated. It is `previewDrawOnly=true`; it does
not render terrain, does not call `MDICSectionRenderer`, does not call
`VoxyRenderSystem`, does not use formal MDIC command buffers, and does not start
the formal renderer draw path.

The formal renderer manager can now report:

```text
formalTexturedShaderPrototypeReady=true
formalTexturedShaderPreviewReady=true
terrainDrawStarted=false
formalRendererDrawStarted=false
actualRendererDrawEnabled=false
```

Those flags mean the current formal shader resources can produce audited
textured preview pixels offscreen. They do not mean the formal textured shader
or renderer is ready. The readiness boundary remains:

```text
formalTexturedShaderReady=false
formalRendererReady=false
actualDrawEnabled=false
```

Remaining blockers include formal MDIC renderer integration, terrain shader
semantics, lightmap, biome tint, material/alpha behavior, visibility traversal,
and shaderpack integration.

## J4 readiness note

J4 adds a formal packed-quad shader geometry preview. It reuses the I2 formal
`ModelStore` owner, the I6 safe-set formal model ids, and the J3 formal
textured preview resources, then renders a small packed-quad-style batch into an
offscreen framebuffer for readback audit.

The J4 preview deliberately keeps the formal terrain renderer boundary intact.
It may scan existing CPU/BuiltSection packed records, but only accepts a record
when it can map the legacy source model id back to a block-state id and then to
an I3/I6 formal model id. The model id rewrite happens only in a temporary
preview buffer; the original geometry heap remains untouched. If no real terrain
record can be mapped safely, the preview may use a synthetic packed-quad
fallback and reports that explicitly.

The formal renderer manager can now report:

```text
formalPackedQuadPreviewReady=true
packedQuadShaderPreviewReady=true
packedQuadModelIdBridgeReady=true
realTerrainRecordsUsed=true/false
syntheticFallbackUsed=true/false
terrainDrawStarted=false
formalRendererDrawStarted=false
actualRendererDrawEnabled=false
```

Those flags mean the current formal shader preview can consume packed-quad-style
geometry with formal model ids in an isolated offscreen audit. They do not mean
the formal textured shader or terrain renderer is ready. The readiness boundary
remains:

```text
formalTexturedShaderReady=false
formalRendererReady=false
actualDrawEnabled=false
```

Remaining blockers include the real formal geometry model-id pipeline, formal
MDIC renderer integration, formal command-buffer ownership, visibility/LOD
traversal, lightmap, biome tint, material/alpha semantics, translucent handling,
and shaderpack integration.

## J5 readiness note

J5 adds the real terrain packed-record formal model-id bridge. It keeps the J4
offscreen preview boundary, but the QA path must now produce and consume real
current-world BuiltSection packed records instead of accepting the synthetic
fallback. The source path is:

```text
current loaded chunk
 -> Voxy ingest
 -> CPU mesh
 -> BuiltSection packed records
 -> blockStateId recovery
 -> I3/I6 formal model id lookup
 -> temporary formal packed-quad buffer
 -> J4 offscreen preview/readback
```

The formal renderer manager can now report:

```text
formalTerrainPackedRecordBridgeReady=true
realTerrainPackedRecordBridgeReady=true
realTerrainRecordsUsed=true
syntheticFallbackUsed=false
temporaryFormalQuadBufferCreated=true
originalGeometryUntouched=true
originalGeometryHeapUntouched=true
terrainDrawStarted=false
formalRendererDrawStarted=false
actualRendererDrawEnabled=false
```

Those flags mean the isolated preview can consume real terrain/BuiltSection
records after safely rewriting only temporary record copies to formal model ids.
They do not mean the global geometry model-id pipeline, formal MDIC command
ownership, formal textured terrain shader, or formal renderer is ready. The
readiness boundary remains:

```text
formalTexturedShaderReady=false
formalRendererReady=false
actualDrawEnabled=false
```

Remaining blockers include making the formal model-id geometry path global,
formal MDIC renderer integration, command-buffer ownership, visibility/LOD
traversal, lightmap, biome tint, material/alpha semantics, translucent handling,
and shaderpack integration.

## K1 readiness note

K1 adds the formal terrain renderer owner no-draw skeleton. This is the first
K-stage owner boundary after the K0 original Voxy alignment audit, but it is
still not a live terrain renderer. It checks that the K0 audit document exists
and carries the `K0_VERDICT_READY_FOR_K1_NO_DRAW_FORMAL_RENDERER_OWNER`
verdict, then reports a formal terrain renderer owner lifecycle and blocker
set.

The K1 owner owns only:

- formal terrain renderer lifecycle/status,
- readiness aggregation for the terrain renderer boundary,
- blocker reporting,
- placeholder status for formal viewport, command-buffer, visibility, and draw
  pipeline ownership.

The K1 owner references, but does not own, the formal ModelStore, formal
ModelFactory / ModelBakery lifecycle, J1-J5 shader/preview/terrain-record
validation resources, GL geometry heap, and section geometry manager.

The formal renderer manager can now report:

```text
formalTerrainRendererOwnerReady=true
formalTerrainRendererLifecycleReady=true
k0AlignmentAuditReady=true
k0VerdictReadyForK1=true
originalVoxyAlignmentPreserved=true
formalViewportOwnerReady=false
formalCommandBufferOwnerReady=false
formalVisibilityOwnerReady=false
formalDrawPipelineReady=false
globalFormalModelIdGeometryReady=false
formalTerrainShaderReady=false
previewSystemsSeparated=true
sampleSetUsedAsFormalSource=false
formalRendererReady=false
actualDrawEnabled=false
```

Those flags mean the owner boundary exists and remains aligned with the K0
audit. They do not mean formal terrain draw is ready. Remaining P0 blockers
include formal viewport ownership, formal command buffer ownership, formal
visibility ownership, global formal model-id geometry, formal terrain shader
integration, and formal MDIC renderer integration.

## K2 readiness note

K2 adds formal MDIC-side ownership skeletons under the K1 terrain renderer owner.
The ownership shape is aligned with original Voxy `MDICViewport` and
`MDICSectionRenderer` expectations:

```text
formalViewportOwner
 -> formalDrawCommandBufferOwner
 -> formalDrawCountBufferOwner
 -> formalVisibilityBufferOwner
 -> formalRenderListOrIndirectLookupOwner
 -> formalPositionScratchOwner
```

These owners are logical-only in K2. Their capacities mirror the original Voxy
shape, but allocation is deliberately deferred:

```text
drawCommandBufferAllocated=false
drawCountBufferAllocated=false
visibilityBufferAllocated=false
renderListOrIndirectLookupAllocated=false
positionScratchAllocated=false
allocationDeferredReason=K2-no-draw-logical-owner
```

K2 can now report:

```text
formalViewportOwnerReady=true
formalCommandBufferOwnerReady=true
formalDrawCommandBufferOwnerReady=true
formalDrawCountBufferOwnerReady=true
formalVisibilityOwnerReady=true
formalRenderListOwnerReady=true
formalIndirectLookupOwnerReady=true
formalPositionScratchOwnerReady=true
debugMdicCommandBuffersUsedAsFormal=false
formalCommandGenerationOwnerReady=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

## K10.3 observe prepare hotfix note

K10.3 fixes the gap left by K10.2: a manual observe command could return
safely but still produce no visible change if the K10 visible preview owner was
not already built. The command path remains lightweight and command-thread
safe, but missing/stale preview resources now trigger a bounded render-thread
prepare sequence instead of immediate safe failure.

The prepare sequence is still preview-only evidence. It may build or refresh
K6 real-section dry-run, K7 isolated offscreen draw evidence, K8 formal
model-id geometry, K9 terrain shader integration, and finally the K10 visible
preview owner over multiple render frames. It does not run per-frame rebuilds,
does not read back during manual observe frames, and does not promote the
preview to production renderer readiness.

New readiness/audit evidence includes:

```text
observePrepareRequested
observePrepareInProgress
observePrepareCompleted
observePrepareFailedSafely
observeAutoEnabledAfterPrepare
observePrepareFrameBudgetExceeded
observePrepareStep
observePrepareFrameCount
lastObservePrepareStepName
lastObservePrepareFailureReason
```

Expected successful manual observe evidence is:

```text
observeEnableCommandReturnedQuickly=true
observeEnableDidGlWorkOnCommandThread=false
observeEnableDidReadbackOnCommandThread=false
observeEnableDidSynchronousRebuild=false
observePrepareCompleted=true
observeAutoEnabledAfterPrepare=true
visiblePreviewDrawExecuted=true
visiblePreviewLightweightDrawPath=true
visiblePreviewDirectDrawUsed=true
visiblePreviewIndirectCountDrawUsed=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

Resource reload during observe prepare is treated as a rebuild signal rather
than a command cancellation. The K10-owned preview resources are cleaned up, the
component remains stale until rebuilt, and the pending prepare sequence
continues on the render thread.

Follow-up performance note: manual observe eventually produced a visible large
debug-tinted preview, but sustained FPS was still too low. Continuous observe
draw now avoids the indirect-count validation call and uses a direct indexed draw
from the K6 command fields against K8/K9 formal inputs. The command and draw
count buffers remain recorded as formal evidence; they are not used as debug
buffers and are not submitted to the production renderer.

The visible observe draw is deliberately capped to a small index count for
human observation. This does not change the K6 command evidence; status keeps
both values separate via `visiblePreviewCommandIndexCount` and
`visiblePreviewDrawIndexCount`, with `visiblePreviewDrawCountCapped=true` when
the observe path is showing only the reduced sample.

After comparing against original Voxy, the K10 visible preview no longer uses a
custom six-vertex quad convention. The shader/index path now mirrors the Voxy
four-vertex quad convention (`gl_VertexID >> 2`) with the shared-index pattern
`1,2,0,1,3,2`. This keeps the preview closer to the original MDIC shader
contract while remaining preview-only.

The blocker boundary shifts from missing owner shells to missing producers and
draw integration: formal command generation, formal visibility traversal, global
formal model-id geometry, formal terrain shader integration, and formal MDIC
draw remain absent.

K10 observe command hotfix: manual observe enable/disable no longer run or print
the full readiness/status dump as part of the toggle path. Enable still only
requests render-thread preparation, and disable only stops preview drawing; full
status and audit remain explicit commands. This keeps the manual preview switch
from becoming a hidden readiness audit or log/chat stress path.

## K3 readiness note

K3 adds the formal command-generation ownership skeleton above the K2 resource
owners. This is still a no-draw contract layer, not operational command
generation.

The K3 owner audits the original Voxy command-generation contract:

```text
cmdgen.comp
 -> visibility buffer
 -> indirect section lookup / render list
 -> section metadata
 -> position scratch
 -> DrawCommand buffer
 -> draw count / parameter buffer
```

The known no-draw layout is:

```text
drawCommandStructKnown=true
drawCommandStrideBytes=20
drawCommandFieldCount=5
drawCommandIndexedIndirectCompatible=true
drawCountBufferLayoutKnown=true
drawCountBufferStrideBytes=4
drawCountBufferLayoutBytes=44
```

K3 can now report:

```text
formalCommandGenerationOwnerReady=true
formalCommandGenerationContractReady=true
formalDrawCommandLayoutReady=true
formalDrawCountLayoutReady=true
debugMdicCommandBuffersUsedAsFormal=false
cmdgenComputeShaderRun=false
glMultiDrawElementsIndirectCountCalled=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

This shifts the blocker boundary again: the owner and layout contract exist, but
the formal GPU command-generation program, formal visibility traversal, global
formal model-id geometry, formal terrain shader integration, and formal MDIC
draw remain absent.

## K4 readiness note

K4 adds the formal visibility / render-list ownership skeleton. It inspects the
original Voxy visibility path and records the contract that the Forge formal
renderer must eventually implement:

```text
RenderDistanceTracker
 -> HierarchicalOcclusionTraverser
 -> Viewport.getRenderList()
 -> MDICViewport.indirectLookupBuffer
 -> MDICViewport.visibilityBuffer
 -> cmdgen.comp sectionCount / indirectLookup / visibilityData inputs
```

The Forge K4 owner keeps that ownership separate from debug radius/frustum
planners. A conservative CPU candidate snapshot may be reported from
`ForgeSectionGeometryManager`, but it is explicitly provisional and is not a
formal traversal implementation.

K4 can now report:

```text
formalVisibilityOwnerReady=true
formalRenderListOwnerReady=true
formalIndirectLookupOwnerReady=true
formalVisibilityContractReady=true
formalRenderListContractReady=true
cpuCandidateSnapshotReady=true
debugPlannerUsedAsFormal=false
cmdgenComputeShaderRun=false
glMultiDrawElementsIndirectCountCalled=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

The remaining formal-renderer blockers are now more precise: formal visibility
traversal implementation, formal command-generation GPU execution, global
formal model-id geometry, formal terrain shader integration, and formal MDIC
draw remain missing.

## K5 readiness note

K5 adds an audit-only GPU validation program for command generation. This proves
that the Forge formal path can bind formal-style command-generation inputs and
produce a `DrawCommand`, draw count, and position scratch output for readback.

K5 intentionally does not run production `cmdgen.comp` as the renderer path and
does not submit generated commands to any draw call. Its buffers are isolated
from debug and live renderer resources:

```text
validationOnly=true
liveRendererBuffer=false
debugBuffer=false
debugMdicCommandBuffersUsedAsFormal=false
```

K5 can now report:

```text
cmdgenValidationProgramReady=true
cmdgenValidationProgramCompileOk=true
cmdgenValidationProgramLinkOk=true
cmdgenValidationDispatchRun=true
cmdgenValidationReadbackOk=true
cmdgenValidationAuditOk=true
drawCommandReadbackOk=true
drawCountReadbackOk=true
productionCmdgenReady=false
glMultiDrawElementsIndirectCountCalled=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

The blocker changes from "no GPU command-generation proof exists" to
"production cmdgen is not operational." Formal visibility traversal, global
formal model-id geometry, terrain shader integration, and formal MDIC draw are
still absent.

## K6 readiness note

K6 adds a no-draw real-section command-generation dry-run. It keeps the K5
audit-only GPU validation pattern, but replaces the synthetic fixture with a
real section candidate snapshot from the Forge CPU section geometry manager.

The K6 path can seed that manager without drawing by building current-world
section data through the existing Forge ingest / CPU mesh / BuiltSection path:

```text
VoxelIngestService
 -> ForgeCpuMeshBuilder
 -> ForgeVoxyBuiltSectionBuilder
 -> ForgeSectionGeometryManager.consumeSection
 -> RealMetadataScan
```

The dry-run shader consumes the same formal binding shape as K5 and original
`cmdgen.comp`:

```text
binding 1: DrawCommand output
binding 2: draw count / parameter output
binding 3: section metadata
binding 4: visibility data
binding 5: indirect section lookup
binding 6: position scratch
```

K6 can now report:

```text
formalCmdgenRealSectionDryRunReady=true
realSectionInputSnapshotReady=true
realSectionMetadataUsed=true
realSectionCandidateSnapshotUsed=true
cmdgenRealSectionDryRunAuditOk=true
```

This is still not production command generation. K6 deliberately keeps:

```text
productionCmdgenReady=false
originalCmdgenFullyOperational=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

Remaining blockers are unchanged in kind: formal hierarchical traversal,
production cmdgen, global formal model-id geometry, formal terrain shader
integration, and formal MDIC draw are still missing.

## K7 readiness note

K7 adds a controlled offscreen-only draw smoke test for the formal path. It is
the first K-stage validation allowed to submit a draw call, but the call is
restricted to K7-owned resources:

- K6 real-section dry-run `DrawCommand` values are copied into a K7-owned
  indirect command buffer.
- The draw count / parameter buffer is K7-owned and uses the original Voxy
  opaque draw-count offset shape.
- The framebuffer, validation shader, index buffer, indirect buffer, and
  parameter buffer are all isolated validation resources.
- Formal model inputs are read from the I2 formal `ModelStore` owner populated
  by the I/J model lifecycle path.

K7 can now report:

```text
isolatedMdicDrawSmokeTestReady=true
offscreenValidationDrawReady=true
offscreenValidationDrawExecuted=true
offscreenReadbackOk=true
realSectionCommandUsed=true
syntheticDrawFixtureUsed=false
```

This does not change renderer readiness:

```text
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
formalTerrainShaderReady=false
```

The remaining blockers are production cmdgen, formal traversal, global formal
model-id geometry, production terrain shader integration, live MDIC renderer
integration, lightmap, biome tint, and material/alpha semantics.

## K8 readiness note

K8 adds the formal model-id section geometry path as an opt-in validation path.
It consumes real section / BuiltSection packed records, recovers block-state
source through the existing mapper, looks up formal model ids from the I3/I6
model lifecycle, and writes those formal ids into a K8-owned isolated geometry
snapshot.

K8 can now report:

```text
formalModelIdGeometryPathReady=true
globalFormalModelIdGeometryPathReady=true
formalGeometrySnapshotCreated=true
formalGeometryReadbackOk=true
formalPackedRecordsAuditOk=true
```

The important boundary is unchanged:

```text
globalFormalModelIdGeometryEnabledForLiveRenderer=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

K8 does not mutate the original GL geometry heap, does not use sample-set data
as a formal source, does not call `MDICSectionRenderer` or `VoxyRenderSystem`,
and does not enable visible terrain rendering. The old blocker "global formal
model-id geometry missing" is now more precise: the opt-in path exists, but it is
not enabled for the live renderer.

## K9 readiness note

K9 adds a formal terrain shader offscreen integration path. It uses the K8
formal model-id geometry snapshot, K6/K7 real-section command/count evidence,
and the I2 formal `ModelStore` owner populated by the I/J lifecycle path.

K9 can now report:

```text
formalTerrainShaderIntegrationReady=true
terrainShaderProgramCompileOk=true
terrainShaderProgramLinkOk=true
k8FormalGeometryUsed=true
k6RealSectionCommandUsed=true
modelDataBindingOk=true
modelColourBindingOk=true
atlasTextureBindingOk=true
formalModelIdDecodeOk=true
faceDataLookupOk=true
atlasSampleOk=true
offscreenTerrainShaderDrawExecuted=true
offscreenReadbackOk=true
```

The integration is deliberately partial:

```text
productionTerrainShaderReady=false
formalTerrainShaderSemanticCompleteness=partial
lightmapReady=false
biomeTintFullReady=false
materialAlphaFullReady=false
translucencyReady=false
shaderpackReady=false
```

The boundary remains strict: K9 draws only into a K9-owned offscreen framebuffer,
does not use sample-set data as a formal source, does not mutate the original
geometry heap, does not call `MDICSectionRenderer` or `VoxyRenderSystem`, and
does not make the formal renderer ready.

## K10 readiness note

K10 adds a visible formal LoD preview toggle. This is the first formal-path
main-framebuffer draw, but it is deliberately reported as preview-only and
debug opt-in:

```text
visiblePreviewDefaultEnabled=false
debugOptInOnly=true
visibleTerrainPreviewOnly=true
productionLiveRendererDrawExecuted=false
```

K10 consumes the already-audited formal inputs instead of debug/sample sources:

```text
drawInputSource=K8FormalGeometryAndK9Shader
k8FormalGeometryUsed=true
k9TerrainShaderIntegrationUsed=true
k6RealSectionCommandUsed=true
syntheticDrawFixtureUsed=false
sampleSetUsedAsFormalSource=false
```

The formal renderer readiness boundary remains unchanged:

```text
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
productionTerrainShaderReady=false
```

The K10 readiness fields are evidence that the preview hook and formal inputs
can produce a visible debug preview. They must not be treated as production
LoD renderer readiness until production cmdgen, traversal, shader semantics,
and live MDIC renderer ownership are complete.

## K10.1 observe/performance hotfix note

K10.1 is a hotfix on the K10 visible preview, not a new renderer stage. It
preserves the successful main-framebuffer preview while adding explicit observe
mode and frame-cost diagnostics.

New status evidence includes:

```text
observeModeEnabled
observeModeDebugTintUsed
observeModeScale
observeModeCameraRelative
previewWorldBounds
previewCameraDistance
renderFrameCount
previewBuildCount
previewRebuildCount
perFrameRebuildDetected
perFrameReadbackDetected
perFrameShaderCompileDetected
perFrameGlAllocationDetected
perFrameLogSpamDetected
duplicateHookRegistrationDetected
lastFrameDrawTimeNanos
averageFrameDrawTimeNanos
maxFrameDrawTimeNanos
renderHookEarlyReturnWhenDisabled
renderHookEarlyReturnWhenStale
```

The hotfix keeps readback/audit work out of the manual observe render loop.
Manual observe mode draws only after explicit opt-in and returns early when
disabled or stale. QA may still request readback explicitly to verify pixels.

Readiness remains unchanged:

```text
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
productionLiveRendererDrawExecuted=false
```

## K10.2 observe timeout hotfix note

K10.2 fixes the manual observe enable path after a timeout was observed while
running:

```text
/voxy formal_visible_lod_preview_observe_enable
```

The log evidence showed the old command path could return only after the K10
preview owner had performed build work, including shader compile and GL
allocation. K10.2 moves manual observe enable to a lightweight request model:
the command records intent and returns, while the render hook handles the
request later. If resources are missing or stale, the hook disables observe mode
and reports a safe failure instead of rebuilding in a loop or blocking the
command.

New readiness/audit evidence includes:

```text
observeEnableRequested
observeEnableHandledOnRenderThread
observeEnableCommandReturnedQuickly
observeEnableCommandDurationMillis
observeEnableDidGlWorkOnCommandThread
observeEnableDidReadbackOnCommandThread
observeEnableDidSynchronousRebuild
observeEnableFailedSafely
observeEnableTimeoutReproduced
lastObserveEnableFailureReason
lastObserveEnableExceptionClass
lastObserveEnableExceptionMessage
```

The expected safe command-path values are:

```text
observeEnableCommandReturnedQuickly=true
observeEnableDidGlWorkOnCommandThread=false
observeEnableDidReadbackOnCommandThread=false
observeEnableDidSynchronousRebuild=false
formalRendererReady=false
actualRendererDrawEnabled=false
```
