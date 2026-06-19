# Forge 1.20.1 original Voxy full render-path parity audit

Stage: `K55_ORIGINAL_VOXY_RENDER_PATH_PARITY_REMEDIATION`

This is the route-control ledger for comparing the current Forge formal LoD
path against the original Voxy renderer. The purpose is to find and remove
architectural drift, not to justify the preview path.

## Summary verdict

```text
K55_VERDICT_MAJOR_PARITY_WORK_REMAINS_DIRECT_GEOMETRY_FIRST_FIX_APPLIED
```

The Forge path can visibly render real formal LoD records, but several core
mechanisms are still not the original Voxy mechanisms:

- section mesh generation is still too synchronous in parts of the Forge path;
- the visible path is still preview-owned instead of `MDICSectionRenderer` owned;
- formal visibility is not hierarchical occlusion traversal;
- production `cmdgen.comp` is not the live command generator;
- the model lifecycle is safe-set oriented, not fully on-demand.

K55 applies the first correction: K8 now prefers direct formal packed-record
generation from `WorldSection` raw data instead of starting from Forge
`BakedQuad` mesh conversion and temporary model-id rewrite.

## Files inspected

Original Voxy:

- `src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/building/RenderGenerationService.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelBakerySubsystem.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java`
- `src/main/java/me/cortex/voxy/client/core/model/ModelStore.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/RenderDistanceTracker.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/ViewportSelector.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICViewport.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicAsyncGeometryManager.java`
- `src/main/java/me/cortex/voxy/client/core/rendering/section/geometry/BasicSectionGeometryData.java`
- `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl`
- `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert`
- `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl`
- `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl`
- `src/main/resources/assets/voxy/shaders/lod/block_model.glsl`
- `src/main/resources/assets/voxy/shaders/lod/section.glsl`

Current Forge:

- `src/main/java/me/cortex/voxy/forge/ForgeFormalModelIdSectionGeometryPath.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalDirectSectionGeometryBuilder.java`
- `src/main/java/me/cortex/voxy/forge/ForgeVoxyGeometryBuffer.java`
- `src/main/java/me/cortex/voxy/forge/ForgeCpuMeshBuilder.java`
- `src/main/java/me/cortex/voxy/forge/ForgeCpuMeshBuildManager.java`
- `src/main/java/me/cortex/voxy/forge/ForgeVoxyBuiltSectionBuilder.java`
- `src/main/java/me/cortex/voxy/forge/ForgeVoxyBuiltSectionBuildManager.java`
- `src/main/java/me/cortex/voxy/forge/ForgeVoxyQuadEncoder.java`
- `src/main/java/me/cortex/voxy/forge/ForgeSectionGeometryManager.java`
- `src/main/java/me/cortex/voxy/forge/ForgeGpuGeometryHeap.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalCmdgenRealSectionDryRun.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalTerrainShaderIntegration.java`
- `src/main/java/me/cortex/voxy/forge/ForgeFormalVisibleLodPreview.java`

## Original mechanism map

Original Voxy production chain:

```text
WorldEngine / WorldSection
 -> RenderDistanceTracker top-level node queue
 -> AsyncNodeManager / HierarchicalOcclusionTraverser
 -> RenderGenerationService worker queue
 -> RenderDataFactory direct packed quad generation
 -> BuiltSection
 -> BasicAsyncGeometryManager section id + 128-record aligned geometry heap
 -> BasicSectionGeometryData GL buffers
 -> MDICViewport draw/visibility/position-scratch buffers
 -> MDICSectionRenderer buildDrawCalls()
 -> cmdgen.comp writes DrawCommand + draw count
 -> quads3.vert/quads.frag read quadData, positionScratch, modelData, modelColour, atlas
 -> glMultiDrawElementsIndirectCountARB
```

The key performance point is that section mesh generation is not a per-frame or
per-command synchronous Forge mesh operation. It works from Voxy world sections,
model metadata, and packed records, then uploads/replaces sections through a
geometry manager.

## Confirmed drift items

| Area | Original Voxy | Forge path before K55 | Drift | Severity | Required direction |
| --- | --- | --- | --- | --- | --- |
| Section generation ownership | `RenderGenerationService` worker queue | client tick/render prepare paths can build synchronously | Synchronous hitches | P0 | Formal async generation service |
| Geometry generation | `RenderDataFactory` emits packed records from raw section data | `BakedQuad` CPU mesh could be converted back to packed records | Wrong hot path | P0 | Direct formal packed-record generation |
| Model lifecycle | Missing model id requests bake and requeues task | safe-set upload covers selected blocks | Limited coverage | P0 | On-demand formal bake/upload lifecycle |
| Visibility | render-distance tracker + hierarchical occlusion | chunk-radius snapshots and preview patches | Not original traversal | P0 | Formal tracker/traverser ownership |
| Command generation | production `cmdgen.comp` over render list/visibility buffers | audit compute and preview-owned commands | Not live cmdgen | P0 | Production cmdgen after inputs align |
| Draw submission | `MDICSectionRenderer` indirect-count draw | K10 preview hook direct draw | Preview only | P0 | Formal MDIC owner later |
| Shader semantics | original terrain shaders with light/tint/material paths | adapter/subset | Partial semantics | P1 | Full shader contract |
| Geometry heap | `BasicAsyncGeometryManager` overlay + aligned heap upload/remove | Forge manager partially mirrors it | Partial production ownership | P1 | Promote only after async generation/traversal |

## K55 fix applied

K55 adds `ForgeFormalDirectSectionGeometryBuilder` and makes K8 try it before
legacy built-section bridge sources.

New preferred K8 chain:

```text
loaded chunk ingest
 -> WorldEngine / WorldSection raw data
 -> blockStateId lookup
 -> formal model id lookup from I6 uploaded formal models
 -> direct Voxy packed quad record
 -> greedy merge
 -> isolated formal geometry snapshot / validation buffer
```

This removes the previous first-choice dependence on:

```text
BakedQuad CPU mesh -> legacy placeholder model id -> temporary formal id rewrite
```

The direct path remains an isolated K8 snapshot. It does not mutate the live
geometry heap, does not submit renderer commands, and does not change renderer
readiness.

## Remaining blockers before claiming Voxy parity

1. Formal async render generation service:
   priority queue, in-flight task map, model-miss requeue, bounded section
   holding, result consumer, and shutdown semantics equivalent to
   `RenderGenerationService`.

2. Full formal direct `RenderDataFactory` parity:
   fluid LUT, non-opaque face rules, neighbor aux faces across section
   boundaries, model metadata occlusion queries, light selection,
   material/alpha/cutout behavior, and broad biome tint behavior.

3. On-demand formal model lifecycle:
   generation must request missing formal models and requeue sections, rather
   than accepting only the prebuilt safe set.

4. Formal visibility/traversal:
   chunk-radius snapshots must be replaced by original-style top-level
   render-distance tracking and hierarchical occlusion traversal.

5. Production command generation:
   validation compute shaders must be replaced by production `cmdgen.comp` once
   render list, visibility buffer, metadata buffer, and geometry buffer are
   formally owned.

6. Formal MDIC draw owner:
   K10 preview must remain separate until viewport buffers, command buffers,
   model/geometry/shader binding, and indirect-count draw submission are all
   owned by the formal renderer.

## Do-not-do list

- Do not treat K10 visible preview success as Voxy parity.
- Do not hide route drift behind manual refresh commands.
- Do not keep `BakedQuad -> packed record` as the long-term formal geometry path.
- Do not promote debug MDIC buffers to formal command buffers.
- Do not use sample-set or placeholder model ids as formal ids.
- Do not call `MDICSectionRenderer` or `VoxyRenderSystem` until all formal
  owners and inputs match the original contract.
- Do not mark `formalRendererReady`, `formalDrawPipelineReady`, or
  `actualRendererDrawEnabled` true from preview evidence.

## Next implementation batch

The next coherent parity batch should implement a Forge formal render
generation service:

```text
WorldSection task queue
 -> direct formal RenderDataFactory path
 -> model-miss request/requeue semantics
 -> BuiltSection result consumer
 -> incremental geometry-manager upload/swap
```

That is the point where the visible preview can stop doing heavy prepare work
and start consuming continuously produced formal section geometry, which is the
shape of the original Voxy renderer.
