# Forge 1.20.1 black LoD bugfix plan, 2026-06-22

This document is the active, low-budget repair plan for the current black LoD
runtime bug. It intentionally separates the black LoD issue from the secondary
water/fluid issue so the next repair pass does not burn time switching between
unrelated symptoms.

## Goal

Fix the shaderpack-visible black LoD terrain through the original Voxy
shaderpack contract, not through a Complementary-specific workaround.

The current test pack is Complementary because it exposes the failure clearly,
but the repair target is generic:

```text
original Voxy Iris/Oculus pipeline data
 -> original shader patch hook
 -> original draw target / lightmap / material id contract
 -> original MDIC terrain draw
```

Do not add shaderpack-name checks, brightness multipliers, hard-coded material
ids, or per-pack fixes.

## Current state

The latest runtime work moved the failure past several earlier blockers:

```text
formal original-shaped owner starts
deprecated simple GPU visible route is quarantined while the formal route is active
Oculus shaderpack pipeline data is consumed
Voxy shaderpack sidecar sources are discovered
opaque and translucent Voxy terrain shader patches are requested
latest shaderpack audit reports patched programs used and fallback false
MDIC command generation can produce opaque draw commands
custom block-state ids are populated, not globally zero
Oculus projection/model-view/camera matrix audit reported zero diffs
source depth texture becomes populated after first frames
sky-light DataLayer absence no longer falls back to Level.getBrightness()
```

The bug is still unresolved:

```text
LoD terrain remains visually too dark under shaderpack
water / waterlogged fluid LoD remains missing or incorrect
new-world entry recently exited with code 1 and must be rechecked after the black LoD path is stable
```

## Completed progress to preserve

These items are already complete enough that the next pass should not redo them
unless fresh evidence contradicts them:

```text
first-start lifecycle request for ForgeOriginalVoxyModelPipeline
simple/debug visible route suppression during formal route activity
SectionSavingService ownership for WorldEngine save callback
WorldEngine reference ownership and idle-safe free path
chunk-aware section ingest from Embeddium section updates
sky-lit missing-light section deferral instead of synthetic brightness fallback
Oculus shaderpack source-start discovery for voxy.json and sidecar GLSL files
Oculus shaderpack draw target/uniform/sampler/SSBO data bridge shape
patched shader requested/used/fallback status logging
model custom block-state id audit
MDIC readback audit gated behind actual geometry section count
model metadata correction: fluid blocks no longer force the translucent metadata bit unless their layer is translucent
```

## Working assumptions

These assumptions must be verified by source comparison or a bounded audit
before code changes:

```text
black LoD is not caused by the deprecated simple GPU route
black LoD is not caused by every customId being zero
black LoD is not caused by patched shader compile fallback in the latest run
black LoD is not proven to be a water/fluid bug
```

## Primary hypotheses

### P0. Shaderpack draw target / G-buffer contract drift

Original IrisVoxy gets shaderpack draw target ids from `voxy.json`, resolves
them through Iris render targets, and renders Voxy terrain into those targets in
the same order expected by the patched fragment outputs.

Forge/Oculus must be checked for:

```text
target id list from voxy.json
main/alt texture selection from getFlippedAfterPrepare()
framebuffer attachment order
glDrawBuffers order
layout(location = N) output mapping to shaderpack draw buffer N
per-target blend state and clear expectations
translucent depth/stencil transfer target
```

This is the highest-priority path because a wrong material/G-buffer attachment
can make many shaderpacks treat LoD as unlit, fogged, or shadowed even when
geometry and colors are present.

### P0. Patched fragment output contract drift

The generated shaderpack `voxy_emitFragment(...)` must receive the same
parameters and write the same outputs as original Voxy:

```text
sampled colour
tile / uv
face
model id
lightmap uv
tint
custom block-state id
```

The next pass should compare the original Iris patch source and the Forge/Oculus
patch source path. If a generated source dump is needed, it must be bounded and
temporary, not a committed artifact.

### P0. Lightmap contract drift

Current evidence says LoD is not simply stored with all-zero light. Time of day
changes the user's perceived brightness, so some light path exists. Still, the
contract must be checked:

```text
Mapper light byte packing
RenderDataFactory quad light extraction
quads.frag getLightmapUv(...)
Oculus lightmap sampler binding or shaderpack lightmap expectation
neighbor/self light selection for opaque faces
fully opaque metadata clearing of packed light
```

Do not restore synthetic `Level.getBrightness(...)` fallback. Persistent LoD
data must use real section light data or defer insertion.

### P1. Material / block-state id contract drift

The latest audit saw non-zero `customId` values, but that only proves the map is
not absent. The next pass must verify that Forge/Oculus block-state ids match
the shaderpack material map for representative blocks:

```text
grass_block
stone
dirt
leaves
sand
water or waterlogged block only as secondary evidence
```

If ids are wrong for common blocks, the fix belongs in the Oculus
`WorldRenderingSettings` bridge, not in shader code.

### P1. Hook-adapter GL state drift

Until full `VoxyRenderSystem` ownership replaces the Embeddium hook adapter,
state drift can still corrupt shaderpack output. The next pass should only add
bounded audits for states directly used by shaderpack terrain:

```text
draw framebuffer
draw/read buffers
active program
active texture and sampler bindings needed by Voxy terrain
SSBO bindings used by Voxy terrain and shaderpack patch
image bindings used by shaderpack patch
polygon mode
blend state per target where available
```

### P2. Fluid/water LoD bug

Fluid remains a real issue, but it is not the black-LoD primary path. Current
evidence shows waterlogged blocks can resolve `fluidModelId=0`. That should be
tracked separately after the black terrain contract is stable.

## Repair sequence

### Step 1. Freeze documentation and remove diagnostic noise

No `runClient` in this step.

Expected checks:

```text
rtk git status --short
docs updated with current evidence and this plan
no leftover temporary water/fluid bake diagnostics
```

### Step 2. Static parity comparison: original IrisVoxy vs Forge/Oculus

No `runClient` in this step.

Read and compare:

```text
original IrisVoxyRenderPipelineData.getDrawBuffers(...)
ForgeOriginalVoxyOculusRenderPipelineData.getDrawBuffers(...)
original IrisVoxyRenderPipeline framebuffer setup
ForgeOriginalVoxyRenderPipeline framebuffer setup
Oculus RenderTargets.createColorFramebuffer(...) attachment and draw-buffer semantics
original IrisShaderPatch generated opaque/translucent patch source
ForgeOriginalVoxyOculusShaderPatch generated opaque/translucent patch source
```

Output of this step should be a short table:

```text
contract point
original behavior
Forge/Oculus behavior
verdict: match / drift / blocker
```

### Step 3. Add one bounded generic audit if static comparison is inconclusive

The audit must be generic and gated behind an existing property such as
`voxy.forge.auditShaderpack`.

Allowed audit data:

```text
shaderpack target ids from voxy.json
resolved texture ids for each target
framebuffer attachment order
draw buffer order
patched shader used/fallback booleans
representative customId/lightmap values from first emitted draws
```

Forbidden audit data:

```text
large shader dumps committed to the repo
full latest.log reads
per-pack hard-coded conditions
unbounded per-frame spam
```

Compile after this step.

### Step 4. One controlled runtime pass

Run `runClient` only after Steps 2-3 identify exactly what must be observed.
Do not loop runtime tests.

Ask the user only for visual confirmation that cannot be obtained from logs:

```text
does far LoD remain black?
does brightness change when toggling shaderpack or time of day?
does the game exit/crash on new world?
```

Log review should be bounded to the audit lines and crash tail.

### Step 5. Fix the proven P0 contract drift

Only fix the first proven root cause. Do not bundle water fixes.

After the fix:

```text
compileJava
quick diff audit against original behavior
one targeted runClient if the fix touches GL, shaderpack, framebuffer, or runtime state
```

### Step 6. Update docs before commit

If black LoD is fixed:

```text
mark the fixed contract point
record validation evidence
leave readiness false if outer VoxyRenderSystem lifecycle still remains
record water/new-world crash as separate remaining blockers if still present
```

If black LoD is not fixed:

```text
record the exact contract points ruled out
stop before repeated runtime loops
state the next single hypothesis
```

## Stop conditions

Stop and report instead of continuing when any of these happen:

```text
two controlled runtime passes do not change the evidence
the next suspected fix requires broad shaderpack-specific behavior
the evidence points to missing full VoxyRenderSystem ownership rather than a local bridge bug
the game exits before producing the planned audit evidence
```

## Success criteria

The black LoD bug is considered fixed only when all are true:

```text
no Complementary-specific code path was added
patched shaderpack terrain programs are used without fallback
draw target / G-buffer audit matches original IrisVoxy semantics
far LoD terrain is not globally black under the active shaderpack
simple/debug GPU route is not the visible evidence path
compileJava passes
docs record the remaining blockers honestly
```

This does not automatically make the renderer formally ready. Readiness still
depends on the remaining original `VoxyRenderSystem` lifecycle ownership and any
unfixed water/fluid or reload/new-world issues.
