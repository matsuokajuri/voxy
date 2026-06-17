# Forge 1.20.1 stage roadmap

This document corrects the project stage taxonomy after the extended G6.x
sequence. It does not rename old commits, tags, commands, or code constants. It
only defines how future work should be described.

## Why this correction exists

The project already had an A-F stage taxonomy before the current conversation
became long enough to continue from summarized context. The newer conversation
resumed around the later renderer/debug pipeline work, roughly G4 onward. Since
then, the G6.x label grew too large and started covering several distinct work
directions:

- MDIC debug draw and indirect draw API proof.
- Formal renderer readiness audits.
- ModelStore, BakedModel, atlas, and texture bridge probes.
- Textured debug renderers.
- Formal shader input bridge for sample data.
- Real Forge resource reload listener.

That makes the project look like it is stuck in G6, even though the work has
moved across multiple different readiness areas. From this point forward, G6.x
is a legacy label for the extended G phase, not the name for new work.

## Historical A-F phases

A-F already existed as the early project phase system. This document does not
redefine or rewrite those labels. Based on repository history and the carried
context, they cover the early Forge port and renderer migration groundwork,
including:

- Forge 1.20.1 skeleton.
- Forge-compatible Voxy core abstractions.
- serialization, mapper, and world engine skeleton work.
- chunk ingest and controlled lifecycle.
- CPU mesh validation and cache.
- vanilla/simple debug renderers.
- CPU BuiltSection records and source workflow.
- CPU section geometry manager.
- early GL geometry/debug pipeline foundations.

If a future audit recovers the exact original A1/A2/A3 naming from the old
conversation, it should add that detail without changing the meaning of this
roadmap.

## Extended G phase

The current conversation continued around G4 and then stretched G6.x far beyond
one small subphase. The corrected interpretation is:

- G4-G5: renderer, GL heap, direct GL, MDIC command, and debug draw proof phase.
- G6.0-G6.7: MDIC debug draw hardening, DrawElementsIndirect, and draw count
  buffer proof.
- G6.8-G6.22: formal readiness, ModelStore/BakedModel/atlas bridge, textured
  debug visibility, formal input bridge, real reload listener, and final formal
  renderer readiness audit.

G6.x labels remain useful when reading commit history, tags, status fields, and
old documents. They should not be used for new stage names after G6.22.

## Old-to-new mapping

| Old label / old phase | New stage location | Nature | Visible? | Formal-ready? |
| --- | --- | --- | --- | --- |
| A-F | Existing historical phases; not renamed | Forge skeleton, core port, ingest, CPU mesh, BuiltSection, early geometry/debug groundwork | Some stages visible through debug/simple renderers | No; foundational history |
| G4-G5 | G: renderer/GL/MDIC debug proof phase | GL heap, direct GL, MDIC command skeleton, debug draw proof | Yes, debug geometry | No; debug/PoC |
| G6.0-G6.7 | G: MDIC debug draw and draw API proof | MDIC debug renderer, multi/indirect/elements-indirect/count-buffer proof | Yes, same style debug geometry | No; draw API proof only |
| G6.8 | G: formal MDIC renderer readiness audit | Readiness and blocker audit | No | No; audit only |
| G6.9-G6.13 | G: ModelStore / BakedModel / real-ish record bridge | model metadata, placeholder/no-draw validation, one-block record sample | Mostly no; status/audit only | No; sample/placeholder |
| G6.14-G6.15 | G: Voxy-style atlas ownership / pixel upload audit | atlas skeleton and sample pixel upload/readback | No direct terrain draw | No; sample atlas proof |
| G6.16-G6.18 | G: textured debug visibility path | tiny textured quad, readback textured geometry, one-model textured MDIC debug | Yes, textured debug probes | No; debug renderer |
| G6.19-G6.20 | G: multi-block textured debug + formal shader input bridge | sample-set modelData/modelColour/atlas bridge and multi-model textured debug | Yes, textured debug geometry | No; sample-set bridge |
| G6.21 | G: real resource reload lifecycle integration | Forge resource reload listener and stale/cleanup tracking | No direct visual change | Partial lifecycle readiness |
| G6.22 | G: formal renderer readiness audit | Boundary audit and H1 entry decision | No | No; audit says only no-draw skeleton is safe |
| Next | H1: formal renderer no-draw skeleton | formal ownership shell, lifecycle/status, no draw | No | Entry step toward formal ownership |

## Current project position

The project is not stuck in G6. It has completed the historical A-F groundwork
and an extended G phase with substantial debug, GL, MDIC, atlas, model bridge,
textured debug, shader-input, and reload-readiness work.

The current position is the boundary between extended G and H:

```text
extended G complete enough for ownership audit
 -> H1 formal renderer no-draw skeleton
```

Stage correction does not mean starting over. The debug renderer, atlas upload,
MDIC command, indirect-count, sample-set, and formal-input-bridge work remain
valuable validation results. The correction means future work should stop
expanding debug renderers and start defining formal renderer ownership.

## New stage taxonomy

Future work should use these labels:

- H: formal renderer ownership and no-draw skeleton.
- I: real `ModelFactory` / `ModelBakerySubsystem` / formal `ModelStore` bridge.
- J: formal textured renderer prototype.
- K: early usable LoD renderer hardening.

Recommended near-term breakdown:

- H1: formal renderer no-draw skeleton.
- H2: formal renderer lifecycle and status hardening.
- H3: formal renderer prerequisite wiring, still no draw.
- I1: real ModelFactory / ModelBakery bridge plan.
- I2: formal ModelStore ownership skeleton.
- I3: real model id lifecycle prototype.
- J1: formal textured shader prototype.
- J2: formal MDIC textured draw prototype.
- K1: early usable LoD renderer hardening.

## Naming rules from now on

Do not use these labels for new work:

```text
G6.23
G6.24
G6.25
```

They may be mentioned only when referring to legacy plans or old documents. New
work after G6.22 should start at:

```text
H1: formal renderer no-draw skeleton
```

The H1 boundary is strict:

- no formal draw,
- no formal shader binding,
- no `MDICSectionRenderer`,
- no `VoxyRenderSystem`,
- no shaderpack integration,
- no replacement of existing debug renderers.

H1 exists to create a formal owner and status surface before any new rendering
work is added.

## H1 completion note

H1 implements the formal renderer ownership shell as a no-draw manager. It adds
status, readiness aggregation, lifecycle stale tracking, blocker reporting, and a
`formal_renderer_skeleton` preset. It does not draw, bind a formal shader, call
`MDICSectionRenderer`, call `VoxyRenderSystem`, or replace any existing debug
renderer.

QA1 is a test-harness reliability task for `runClient` quick-play world entry.
It does not advance H-stage renderer functionality.

## H2 status note

H2 hardens the H1 no-draw formal renderer manager. It improves lifecycle states,
readiness generations, recheck/stale reporting, structured blockers, and
readiness aggregation layers. It still does not draw, bind a formal shader, call
`MDICSectionRenderer`, call `VoxyRenderSystem`, or modify existing debug renderer
draw behavior.

I1 is the real `ModelFactory` / `ModelBakerySubsystem` bridge plan. It is a
docs-first audit of the original `ModelBakerySubsystem`, `ModelFactory`,
`ModelStore`, and `RenderDataFactory` lifecycle, and it maps the Forge migration
into I2-I5 without implementing Java or renderer draw behavior.

The intended next work moves into the I-stage model lifecycle boundary:

- I2 for a formal `ModelStore` ownership skeleton, still no draw.
- I3 for a Forge `ModelFactory` lifecycle skeleton.

## I2 status note

I2 creates the formal `ModelStore` ownership skeleton. It introduces an owner
for the future formal modelData buffer, modelColour buffer, Voxy-style atlas
texture, and sampler. It is still no-bake and no-draw: it does not implement
real `ModelFactory`, real `ModelBakerySubsystem`, formal shader binding, formal
MDIC draw, `MDICSectionRenderer`, or `VoxyRenderSystem`.

The intended next work is:

- I3 for a Forge `ModelFactory` lifecycle skeleton.
- I4 for a one-block real bake/upload prototype after the lifecycle shell exists.

## I3 status note

I3 creates the Forge formal `ModelFactory` lifecycle skeleton. It tracks
block-state requests, pending/in-flight/completed skeleton states, formal model
id skeleton assignment, placeholder metadata cache entries, placeholder fluid
state LUT entries, and placeholder texture-dedupe entries.

I3 is still no-bake, no-upload, and no-draw. It does not call real Forge
`BakedModel` baking, does not upload real model records or atlas pixels, does
not bind a formal shader, does not draw, and does not call `MDICSectionRenderer`
or `VoxyRenderSystem`.

The intended next work is:

- I4 for a one-block real bake/upload prototype through the formal ModelStore
  owner.
- I5 for multi-block bake/upload, dedupe, tint, fluids, and reload rebuild.

## I4 status note

I4 is the real one-block bake/upload prototype. It takes one safe solid block
through the Forge `BakedModel` / `BakedQuad` / `TextureAtlasSprite` path, uses
an I3 formal model id, and uploads one model record, one model colour entry, and
six face tiles into the I2 formal `ModelStore` owner.

I4 is still not a renderer stage. It does not draw, bind a formal shader, call
`MDICSectionRenderer`, call `VoxyRenderSystem`, or replace any debug renderer.
The intended next work is I5 for multi-block bake/upload and dedupe.

## I5 status note

I5 generalizes the I4 one-block prototype into a small multi-block formal
bake/upload path. It uploads several safe solid block models into the I2 formal
`ModelStore` owner using I3 formal model ids, adds a conservative
record/texture-signature dedupe skeleton, reports unsupported candidates, and
audits modelData, modelColour, and atlas readback.

I5 is still not a renderer stage. It does not draw, bind a formal shader, call
`MDICSectionRenderer`, call `VoxyRenderSystem`, or claim full real
`ModelFactory`, `ModelBakerySubsystem`, `ModelStore`, or formal renderer
readiness.

## I6 status note

I6 adds the formal `ModelBakery` lifecycle rebuild and alias-safe dedupe
prototype. It coordinates the I2 formal `ModelStore`, I3 formal `ModelFactory`
lifecycle, and I5 multi-block formal upload path. It proves that the safe set can
be invalidated by reload simulation and rebuilt deterministically, with explicit
dedupe alias records and generation counters.

I6 is still not a renderer stage. It does not draw, bind a formal shader, call
`MDICSectionRenderer`, call `VoxyRenderSystem`, start full async baking, or claim
full real `ModelFactory`, `ModelBakerySubsystem`, `ModelStore`, or formal
renderer readiness.

The intended next work remains in the I/J boundary:

- broader real model lifecycle coverage before any formal draw claim,
- formal shader input consumption when the model pipeline is sufficiently real,
- formal renderer integration only after the model lifecycle boundary is stable.

## J1 status note

J1 introduces the formal shader input consumption skeleton. It validates the I2
formal `ModelStore` owner resources produced through the I6 lifecycle safe-set
path: modelData, modelColour, atlas texture, sampler, formal model ids, and the
known binding layout.

J1 is no-draw. It does not bind a formal shader program, draw terrain, call
`MDICSectionRenderer`, call `VoxyRenderSystem`, or claim full formal renderer
readiness. It is the bridge between the I-stage formal model lifecycle work and
future J-stage formal shader work.

## J2 status note

J2 adds the formal shader program validation prototype. It compiles and links an
audit-only validation program, binds the I2 formal `ModelStore` owner resources
validated by J1, and reads back compact GPU validation results for formal model
ids produced by the I6 lifecycle safe-set path.

J2 is still not a renderer stage. It does not draw terrain, call
`MDICSectionRenderer`, call `VoxyRenderSystem`, integrate shaderpacks, or claim
`formalTexturedShaderReady` / `formalRendererReady`. It proves shader-side
resource consumption, not visible LoD rendering.

## J3 status note

J3 adds the formal textured shader preview prototype. It uses the I2 formal
`ModelStore` owner and I6 safe-set formal model ids, compiles a preview
vertex/fragment program, renders selected formal model previews into a small
offscreen framebuffer, and audits the readback checksums.

J3 is a controlled preview stage, not a terrain renderer stage. It may perform an
isolated offscreen preview draw, but it does not draw LoD terrain, call
`MDICSectionRenderer`, call `VoxyRenderSystem`, use formal MDIC command buffers,
or claim `formalTexturedShaderReady` / `formalRendererReady`.

## J4 status note

J4 adds the formal packed-quad shader geometry preview. It takes the formal
model lifecycle and shader preview work from I6-J3, maps a small packed-quad
style batch to I3/I6 formal model ids, rewrites only a temporary isolated
preview buffer, renders that buffer into an offscreen framebuffer, and audits
the readback checksums.

J4 is still not a terrain renderer stage. It does not render live LoD terrain,
does not call `MDICSectionRenderer`, does not call `VoxyRenderSystem`, does not
use formal MDIC command buffers as a renderer, and does not claim
`formalTexturedShaderReady` / `formalRendererReady`. If no real terrain packed
records can be safely mapped, the synthetic fallback is reported as a fallback
rather than as real terrain record preview.

## J5 status note

J5 adds the real terrain packed-record formal model-id bridge. It uses the
current-world ingest -> CPU mesh -> BuiltSection path to produce real packed
records, maps their recovered block-state source to I3/I6 formal model ids, and
rewrites only a temporary preview buffer before using the J4 offscreen shader
preview path.

J5 is still not a formal terrain renderer stage. It does not render live LoD
terrain, mutate the original GL geometry heap, globally switch geometry encoding
to formal model ids, call `MDICSectionRenderer`, call `VoxyRenderSystem`, or
claim `formalTexturedShaderReady` / `formalRendererReady`. Unlike J4, synthetic
fallback is not acceptable as J5 success; the QA path must report
`realTerrainRecordsUsed=true` and `syntheticFallbackUsed=false`.

## K0 status note

K0 audits the original Voxy renderer architecture against the current Forge
path before crossing the terrain renderer boundary. Its verdict is:

```text
K0_VERDICT_READY_FOR_K1_NO_DRAW_FORMAL_RENDERER_OWNER
```

That verdict only permits a no-draw formal terrain renderer owner. It does not
permit live LoD terrain draw, direct `MDICSectionRenderer` wiring, direct
`VoxyRenderSystem` wiring, shaderpack integration, or treating J-stage previews
as formal renderer readiness.

## K1 status note

K1 introduces the formal terrain renderer owner no-draw skeleton. It defines the
owner boundary that will eventually hold formal viewport, command-buffer,
visibility, and draw-pipeline resources, while reporting those resources as
missing blockers today.

K1 references, but does not own, the formal ModelStore, formal ModelFactory /
ModelBakery lifecycle, J-stage shader validation/preview resources, J5 terrain
record bridge, GL geometry heap, and section geometry manager. It keeps debug
renderers and J-stage preview systems separated.

K1 remains no-draw:

```text
formalTerrainRendererReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

Next work should harden formal viewport/command/visibility ownership before any
live terrain draw is considered.

## K2 status note

K2 adds the formal MDIC viewport / command / visibility ownership skeleton. It
creates no-draw logical owners for the resources that original Voxy keeps under
`MDICViewport`:

- draw command buffer,
- draw count / parameter buffer,
- visibility buffer,
- render-list / indirect lookup buffer,
- position scratch buffer.

K2 does not allocate live formal GL draw resources yet. It records original
capacity and ownership semantics, keeps allocation deferred, and reports the
deferred reason explicitly. It also keeps command generation, visibility
traversal, formal terrain shader integration, and live draw disabled:

```text
formalCommandGenerationOwnerReady=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

K2 must not be treated as `MDICSectionRenderer` integration. It only creates the
formal control-room ownership boundary needed before command generation and draw
can be considered.

## K3 status note

K3 adds the formal command generation ownership skeleton. It defines who will
produce formal MDIC draw commands and records the no-draw contract that mirrors
original Voxy `cmdgen.comp` and `bindings.glsl`:

- `DrawCommand` is five 32-bit fields and remains indexed-indirect compatible.
- The draw-count / parameter buffer layout is known as dispatch fields, draw
  counters, and the cull indirect command record.
- Command generation inputs are explicit: visibility, render-list / indirect
  lookup, section metadata, position scratch, geometry buffer, and formal
  ModelStore inputs.
- Command generation outputs are explicit: draw command records, draw counters,
  and position scratch entries.

K3 is still logical-only and no-draw. It does not compile or dispatch the
formal command-generation compute shader, does not run `cmdgen.comp`, does not
call `glMultiDrawElementsIndirectCountARB`, and does not call
`MDICSectionRenderer` or `VoxyRenderSystem`.

Expected K3 status:

```text
formalCommandGenerationOwnerReady=true
formalCommandGenerationContractReady=true
formalDrawCommandLayoutReady=true
formalDrawCountLayoutReady=true
commandGenerationGpuProgramReady=false
cmdgenComputeShaderRun=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

The next safe work is formal visibility traversal ownership and/or a no-draw
command-generation GPU-program skeleton. Live terrain draw remains out of scope.

## K4 status note

K4 adds the formal visibility / render-list ownership skeleton. It aligns the
Forge formal renderer path with the original Voxy visibility order:

```text
RenderDistanceTracker / HierarchicalOcclusionTraverser
 -> viewport render list / indirect lookup
 -> visibility buffer
 -> cmdgen.comp command generation input
```

The new owner is still logical-only and no-draw. It defines:

- formal visibility owner,
- formal render-list / indirect lookup owner,
- formal visibility input contract,
- formal render-list output contract,
- provisional CPU candidate snapshot status.

K4 deliberately does not implement hierarchical occlusion traversal, does not
run `cmdgen.comp`, does not call `glMultiDrawElementsIndirectCountARB`, and does
not treat debug radius/frustum command planners as formal traversal.

Expected K4 status:

```text
formalVisibilityOwnerReady=true
formalRenderListOwnerReady=true
formalIndirectLookupOwnerReady=true
formalVisibilityContractReady=true
formalRenderListContractReady=true
formalVisibilityTraversalImplemented=false
formalHierarchicalOcclusionReady=false
formalRenderDistanceTrackerReady=false
debugPlannerUsedAsFormal=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

The next safe work is an operational command-generation GPU-program skeleton or
formal traversal hardening. Live terrain draw remains out of scope.

## K5 status note

K5 adds a no-draw formal command-generation GPU validation path. It does not
make production `cmdgen.comp` operational. Instead, it creates tiny isolated
validation buffers, dispatches an audit-only compute program, reads back a
deterministic `DrawCommand`, draw count, and position scratch entry, then audits
that no draw happened.

The validation buffers are explicitly:

```text
validationOnly=true
liveRendererBuffer=false
debugBuffer=false
```

K5 may use a synthetic validation fixture because K4 owns only the
visibility/render-list contract, not real traversal output. That fixture is
reported as:

```text
validationInputSource=syntheticValidationFixture
syntheticValidationFixtureUsed=true
```

Expected K5 status:

```text
cmdgenValidationProgramReady=true
cmdgenValidationProgramCompileOk=true
cmdgenValidationProgramLinkOk=true
cmdgenValidationDispatchRun=true
cmdgenValidationReadbackOk=true
drawCommandReadbackOk=true
drawCountReadbackOk=true
productionCmdgenReady=false
glMultiDrawElementsIndirectCountCalled=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

The next safe work is either production command-generation ownership hardening
or formal traversal implementation. Live terrain draw remains blocked.

## K6 status note

K6 replaces the K5 synthetic validation fixture with a real-section dry-run
input path when real section metadata is available. It keeps the K5 no-draw GPU
validation shape, but the inputs now come from the Forge CPU section geometry
manager:

```text
current world chunk
 -> VoxelIngestService
 -> ForgeCpuMeshBuilder
 -> ForgeVoxyBuiltSectionBuilder
 -> ForgeSectionGeometryManager metadata snapshot
 -> isolated cmdgen validation buffers
 -> GPU dry-run command generation
 -> CPU readback audit
```

K6 success requires:

```text
validationInputSource=realSectionCandidateSnapshot
syntheticValidationFixtureUsed=false
realSectionMetadataUsed=true
acceptedSectionCount>=1
generatedCommandCount>=1
generatedDrawCount>=1
```

K6 still does not run production `cmdgen.comp` as the renderer path, does not
call `glMultiDrawElementsIndirectCountARB`, does not submit generated commands
to draw, and does not call `MDICSectionRenderer` or `VoxyRenderSystem`.

Expected K6 status:

```text
realSectionDryRunReady=true
realSectionInputSnapshotReady=true
realSectionMetadataUsed=true
realSectionCandidateSnapshotUsed=true
cmdgenDryRunDispatchRun=true
cmdgenDryRunReadbackOk=true
cmdgenDryRunAuditOk=true
firstCommandMatchesAcceptedSection=true
firstCommandUsesRealSectionMetadata=true
productionCmdgenReady=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

The next safe work is production command generation hardening or formal
visibility traversal implementation. Live terrain draw remains blocked.

## K7 status note

K7 adds the first formal-path draw smoke test, but keeps it isolated to an
offscreen validation framebuffer. It reuses the K6 real-section command dry-run
as the draw input:

```text
real section metadata / candidate snapshot
 -> K6 isolated cmdgen dry-run command
 -> K7-owned indirect command buffer
 -> K7-owned draw count / parameter buffer
 -> K7-owned offscreen framebuffer
 -> validation shader subset over formal ModelStore resources
 -> pixel readback audit
```

K7 success requires:

```text
drawInputSource=realSectionCmdgenDryRun
realSectionCommandUsed=true
syntheticDrawFixtureUsed=false
offscreenValidationDrawExecuted=true
offscreenReadbackOk=true
offscreenNonZeroPixelCount>0
```

K7 is not live terrain rendering. It does not draw into the Minecraft main
framebuffer, does not call `MDICSectionRenderer` or `VoxyRenderSystem`, does not
mutate the original geometry heap, and does not mark the formal renderer ready:

```text
validationOnly=true
offscreenOnly=true
visibleTerrainDrawExecuted=false
liveRendererDrawExecuted=false
formalDrawPipelineReady=false
formalRendererReady=false
actualRendererDrawEnabled=false
```

The next safe work is still production command generation, formal traversal,
global formal model-id geometry, and production terrain shader integration
before any visible LoD renderer can be considered.

## K8 status note

K8 adds an opt-in formal model-id section geometry path without live draw. It
removes the previous reliance on one-off temporary preview rewrites by creating
a K8-owned isolated formal geometry snapshot:

```text
real section / BuiltSection packed records
 -> recover blockStateId from the legacy model-id source
 -> look up the I3/I6 formal model id
 -> rewrite model-id bits only in the K8 snapshot
 -> upload/read back a K8 validation buffer
 -> audit formal model-id decode
```

K8 success means:

```text
formalModelIdGeometryPathReady=true
globalFormalModelIdGeometryPathReady=true
globalFormalModelIdGeometryEnabledForLiveRenderer=false
formalGeometrySnapshotCreated=true
formalGeometrySnapshotRecordCount>=1
usesFormalModelIds=true
usesPlaceholderModelIds=false
sampleSetModelIdsUsed=false
originalGeometryHeapMutated=false
```

K8 is still not live terrain rendering. The formal geometry snapshot is isolated
and validation-only; the live renderer does not consume it, `MDICSectionRenderer`
and `VoxyRenderSystem` are not called, and renderer readiness remains false.
