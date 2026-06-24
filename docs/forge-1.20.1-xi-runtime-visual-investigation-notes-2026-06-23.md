# Forge 1.20.1 XI Runtime Visual Investigation Notes

Date: 2026-06-23

Purpose: preserve the current source-reading trail for the remaining runtime
visual bugs so context compaction does not lose the evidence.

## Active Work Protocol After User Escalation

The user correctly pointed out that repeatedly listing "risk points" without
turning them into proven or excluded facts wastes runtime tests and context.

Hard rule for the rest of this investigation:

```text
read one candidate
 -> classify it as confirmed / excluded / blocked
 -> write that classification here
 -> only then read the next candidate
```

No code edits are allowed from this point unless a candidate is marked
`confirmed` with specific source evidence. No broad visual fixes, water fixes,
brightness multipliers, shaderpack-name special cases, or multi-subsystem
changes are allowed while the black solid-LOD cause is still unproven.

Current binary question:

```text
Are generated solid LOD quads already carrying bad light/material data before
the shaderpack receives them?
```

The next useful step must answer that question directly, either from existing
audits or from one narrow audit hook.

Current recovery point:

- Last committed stable point: `2f4ebaa` (`XI stabilize original parity runtime path`).
- After that commit, the user confirmed:
  - water rendering is normal again;
  - new-world creation no longer crashes;
  - solid LOD blocks remain too dark/black;
  - some solid LOD blocks look transparent or missing;
  - new-world LOD blocks can show small white diagonal slashes;
  - the vanilla chunk / LOD boundary can sometimes have non-rendered holes.

## Rules Re-read

Re-read before continuing:

- `AGENTS.md`: original Voxy source is the baseline; use CodeGraph first for
  source-code reading; use RTK wrappers for shell commands; compile between
  risky steps; do not commit local state such as `run/`, logs, `.codegraph/`,
  `.agents/`, `CODEX.md`, or local config.
- `CODEX.md`: keep changes surgical, avoid speculation, state uncertainty, and
  verify work.
- `C:\Users\Matsu\.codex\RTK.md`: prefix shell commands with `rtk`.
- `D:\Projects\voxy\.agents\skills\minecraft-modding\SKILL.md`: this skill is
  1.21/NeoForge-oriented, so only the general modding discipline applies; do
  not copy 1.21 APIs into this Forge 1.20.1 project.

## Source Read This Pass

### `VoxelIngestService`

File:
`src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java`

Important current Forge behavior:

- `ingestChunkWithStats` iterates loaded chunk sections.
- Both the missing-light prepass and the actual ingest pass call
  `shouldIngestLoadedChunkSection`.
- `shouldIngestLoadedChunkSection` currently returns:

```java
return shouldIngestSection(section, cx, cy, cz) && !section.hasOnlyAir();
```

This means fully air chunk sections are skipped before `WorldConversionFactory`
can write air voxels with light data.

The same skip also exists for single-section updates via
`ingestChunkSectionWithStats`.

### `WorldConversionFactory`

File:
`src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java`

Important original-equivalent behavior:

- Conversion writes every voxel in the 16x16x16 section.
- For block id `0`, it does not leave the entry as plain zero when light exists.
  It writes:

```java
data[idx] = Mapper.airWithLight(light);
```

Conclusion: the core converter is designed for air voxels to carry light. The
Forge live-load path currently prevents pure-air sections from reaching that
converter.

### `WorldImporter`

File:
`src/main/java/me/cortex/voxy/commonImpl/importers/WorldImporter.java`

Relevant original/offline import behavior:

- `importSectionNBT` skips only sections whose `block_states` compound is
  absent/empty.
- If a section has block states, it decodes block light and sky light and calls
  `WorldConversionFactory.convert`.
- Its light packing is:

```java
return (byte) (sky | (block << 4));
```

Conclusion: original-side import does not have a `hasOnlyAir()` early skip at
the conversion call site. If the stored section exists with a block palette,
air light can be preserved.

### `WorldUpdater` and `WorldSection`

Files:

- `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java`
- `src/main/java/me/cortex/voxy/common/world/WorldSection.java`

Important behavior:

- `WorldUpdater.insertUpdate` writes the provided `VoxelizedSection` into the
  level-0 world section and parent LOD layers.
- Level-0 non-empty state is based on `lvl0NonAirCount`.
- `WorldSection.updateLvl0State` sets `nonEmptyChildren` to `0` when the
  section has zero non-air blocks.

Conclusion: storing a pure-air section with light data should not make it a
renderable/non-empty section by itself. It can still exist in world storage and
serve as neighbor lighting data.

### Forge `ForgeOriginalVoxyRenderDataFactory`

File:
`src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderDataFactory.java`

Relevant behavior:

- `prepareSectionData` converts world data into mesh-generation data.
- For air voxels, it preserves only light:

```java
sectionData[i * 2] = (block & (0xFFL << 56)) >>> 1;
sectionData[i * 2 + 1] = 0;
```

- For fully opaque blocks, `packPartialQuadData` clears self light:

```java
lightAndBiome &= ~(ForgeOriginalVoxyModelQueries._isFullyOpaque(metadata)
        * (0xFFL << 55));
```

- Opaque inner faces use the adjacent voxel's light:

```java
(selfModel & ~LM) | (nextModel & LM)
```

- Opaque outer faces use the neighboring section face's light:

```java
(A & ~LM) | ((neighborId & (0xFFL << 56)) >>> 1)
```

Conclusion: solid block faces intentionally rely on neighbor light, especially
for fully opaque blocks. If pure-air neighbor sections are skipped, solid LOD
faces can receive zero light.

### Original `RenderDataFactory`

File:
`src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java`

The same core lighting/neighbor pattern exists in the original renderer:

- air voxels preserve light into `sectionData`;
- fully opaque models clear self light;
- faces use neighbor light;
- outer faces read `neighboringFaces`.

Conclusion: the Forge render-data factory is currently close to original in
this specific logic. The higher-risk divergence is upstream data availability:
the Forge live-ingest route skips pure-air sections.

## Current Root-Cause Ranking

### 1. High probability: skipped pure-air light sections

Why this matches:

- Solid LOD blocks are dark/black while water is normal.
- Fully opaque solid faces use neighbor light, not self light.
- Pure-air sections carry sky light in the original data model.
- Forge live ingest currently skips `hasOnlyAir()` sections before conversion.
- Missing neighbor light also explains boundary weirdness between normal chunks
  and LOD sections.

Expected safe fix direction:

- Stop using `!section.hasOnlyAir()` as the loaded-section ingest gate.
- Preserve the existing optimization only for sections that are both pure air
  and have no block/sky light data.
- Let pure-air sections with sky/block light pass through conversion so they
  become `airWithLight` data.

Open risk:

- Need confirm storage/update churn is acceptable. `WorldUpdater` should keep
  pure-air sections non-renderable because `lvl0NonAirCount == 0`, but they may
  still dirty parent data when light changes.

### 2. Medium probability: stale or incomplete neighbor face data

Why this is suspicious:

- `ForgeOriginalVoxyRenderDataFactory` has a persistent `neighboringFaces`
  array.
- The array is filled only for directions requested by `neighborMsk`.
- If any outer-face path reads a direction not refreshed in the current build,
  stale neighbor data can cause wrong culling, holes, or wrong light.
- Original has the same persistent array pattern, so this may be safe only if
  every read is perfectly masked. This still needs a tighter audit.

Expected check:

- Confirm every outer-face read is guarded by a mask that implies that
  direction was populated during the same `generateMesh` call.
- If not, clear `neighboringFaces` before acquiring neighbors or guard the read.

### 3. Medium probability: atlas alpha/discard or texture bleeding

Why this matches:

- User reports small white diagonal slashes on LOD blocks.
- The opaque fragment shader still performs alpha discard when `useDiscard()`
  is set, before forcing visible alpha to 1.
- Incorrect texture coordinates, atlas wrap/filtering, or cutout alpha sampling
  can produce holes/slashes on foliage/grass-like models.

Expected check:

- Compare Forge `ForgeOriginalVoxyModelStore`,
  `ForgeSoftwareModelTextureBakery`, atlas texture parameters, mip generation,
  and original `ModelStore`.
- Confirm atlas wrap/filter/mipmap state matches original.
- Confirm cutout/cross models are not being classified as fully opaque.

Follow-up source check:

- Forge `ForgeOriginalVoxyModelStore` uses the same sampler shape as original
  `ModelStore`: nearest magnification, nearest-mipmap-linear minification, and
  min/max LOD tied to the block atlas mip level.
- Forge `ForgeOriginalVoxyMipGen` keeps the original solidify/mip strategy.
- Forge `ForgeOriginalVoxySoftwareRasterizer` is materially equivalent to
  original `SoftwareRasterizer`.
- Forge `ForgeSoftwareModelTextureBakery` reads the Forge block atlas as RGBA
  bytes and repacks to the ABGR integer format that the original software
  rasterizer expects.
- Leaves are still force-classified as solid in the Forge baker, matching the
  original `state.is(BlockTags.LEAVES)` behavior.

Current conclusion:

- No clear atlas/sampler/rasterizer parity bug has been found yet.
- The white diagonal marks in the user's screenshot look more like rain/weather
  streaks being visible through dark LOD than like atlas bleed.

### 3b. Medium probability: weather/depth interaction for white diagonal marks

Why this is suspicious:

- The marks are small diagonal white streaks, visually similar to rain.
- They appear over/through LOD terrain.
- With shaderpacks, Voxy may be configured not to write LOD depth into vanilla
  depth.

Source check:

- `ForgeOriginalVoxyOculusShaderPatch.emitToVanillaDepth()` returns
  `!excludeLodsFromVanillaDepth`.
- `ForgeOriginalVoxyRenderPipeline.finishOculus` only blits Voxy depth back to
  the source framebuffer when `renderToVanillaDepth` is true and viewport size
  matches.
- Original `IrisVoxyRenderPipeline.finish` follows the same rule.

Current conclusion:

- Do not force a vanilla-depth blit yet. That would violate the shaderpack's
  `excludeLodsFromVanillaDepth` request and diverge from original Iris Voxy.
- If the white marks are confirmed as rain/weather, track it as a shaderpack /
  vanilla-weather depth integration issue, not as model atlas corruption.

### 4. Lower probability for current black-solid issue: shaderpack shadow path

Prior evidence from Complementary shaderpack inspection:

- `voxy_opaque.glsl` uses `parameters.lightMap`.
- With `VOXY_PATCH`, Complementary's shadow sampling blocks are disabled in the
  inspected lighting path.

Conclusion: shaderpack integration can still have issues, but current black
solid LOD most likely starts before shader lighting: the face light values being
fed to the shader are probably too low/zero.

## Not Yet Fixed In This Pass

Initial note was written before new code changes after commit `2f4ebaa`.

## Fix Attempt 1: Preserve Light-Bearing Air Sections

Changed file:
`src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java`

Change made:

- The loaded-chunk ingest path no longer filters sections with
  `!section.hasOnlyAir()` before reading light data.
- A loaded section is now converted when:
  - it contains non-air blocks; or
  - it is pure air but has non-empty block or sky light data.
- Single-section ingest uses the same rule.
- Missing sky light in sky-light dimensions still defers non-air sections
  instead of converting them with zero sky light.
- Pure-air sections without actual block/sky light data do not defer the chunk;
  they are skipped.

Reason:

- Original conversion supports `Mapper.airWithLight(light)`.
- Solid LOD faces, especially fully opaque faces, use adjacent air/neighbor
  light.
- Previous Forge live-ingest behavior never allowed pure-air sections to become
  light-bearing neighbor data.

Extra evidence from latest log before this change:

```text
Voxy auto ingest minecraft:overworld ... converted=956 nonAirSections=956 ...
Voxy auto ingest minecraft:overworld ... converted=86 nonAirSections=86 ...
```

Because converted section counts exactly matched non-air section counts, the old
runtime path was not converting pure-air sections at all.

Validation:

- `rtk test .\gradlew compileJava` passed after the change.
- A first runtime attempt with a broader deferral rule showed this was too
  conservative:

```text
Voxy auto ingest minecraft:overworld: queued=28 attempted=98 converted=0 nonAirSections=0 ... missingSkyLightSections=1238 deferredLightSections=1238
```

- The broad rule was then tightened so pure-air sections missing sky-light data
  do not stall chunk ingest.
- `rtk test .\gradlew compileJava` passed again after tightening.
- Second runtime attempt after tightening showed the intended count shape:

```text
Voxy auto ingest minecraft:overworld: queued=0 attempted=45 converted=498 nonAirSections=437 ...
Voxy auto ingest minecraft:overworld: queued=0 attempted=17 converted=183 nonAirSections=156 ...
Voxy auto ingest minecraft:overworld: queued=0 attempted=39 converted=446 nonAirSections=382 ...
```

Conclusion from logs:

- Runtime ingest no longer stalls at `converted=0`.
- `converted > nonAirSections` confirms some pure-air sections with light data
  now enter the Voxy world data path.
- Visual confirmation is still required for black LOD and boundary holes.

User visual result:

- The user reported no visible improvement:
  - white diagonal marks still exist;
  - LOD blocks are still dark/black.

Conclusion:

- Fix Attempt 1 corrected a real data-flow deviation but did not fix the
  visible black LOD bug.
- The next investigation must distinguish two downstream cases:
  - final generated LOD quads still carry low/zero sky light, which points to
    mesh neighbor-light selection or missing neighbor data;
  - final generated LOD quads carry normal sky light but shaderpack output is
    still black, which points to Oculus/Complementary lightmap or material
    shader integration.

Risk to watch:

- Forge/Oculus appears to report many pure-air sections with no sky light data
  at the light-layer API. The current rule only converts pure-air sections when
  the light layer actually provides non-empty data. If black LOD persists, the
  next question is whether Forge needs a safe equivalent for original
  light-bearing empty sections when the light engine omits those layers.

The next code change should be small and focused:

1. Runtime-check whether converted sections now exceed non-air sections in the
   auto-ingest summary.
2. If still black, add a short-lived or gated audit for final quad light values
   and inspect whether opaque LOD quads now carry sky light.
3. If the black LOD remains after light-bearing air sections are confirmed,
   inspect final quad light readback and shaderpack lightmap input before
   touching atlas code.

## Process Correction: Stop Guess-Fixing

User report after Fix Attempt 1:

- White diagonal marks still exist.
- Solid LOD is still dark/black.
- The user explicitly notes that the heavy black-LOD problem was not visible
  right after the grid/polygon-mode issue was fixed, and appeared during later
  water/runtime repair attempts.

What went wrong in this investigation:

- The visual state immediately after the grid fix was not preserved as a
  labelled regression baseline before later changes continued.
- Later "water" repair work touched a broader set of runtime-render paths than
  water alone: model metadata/baking, shaderpack pipeline data, depth/target
  handling, ingest/lifecycle, and resource reload sequencing.
- That allowed separate symptoms to become mixed:
  - water visibility;
  - solid LOD darkness;
  - white weather/depth streaks;
  - transparent or missing solid faces;
  - chunk/LOD boundary holes.
- Fix Attempt 1 corrected a real original-parity deviation in air-section light
  ingestion, but the unchanged visual result proves it is not sufficient and
  should not be treated as the main fix.

Corrected next step:

1. Do not add broad rendering fixes until the regression point is narrowed.
2. Compare the commit range from the grid-fix state to the later water/runtime
   state, prioritizing files that affect solid block color and transparency.
3. Add or use a narrow runtime audit that answers one binary question:
   do final generated solid LOD quads already carry low/zero light, or do they
   carry valid light and become black only inside the shaderpack path?
4. Only after that binary split should a code fix be made.

## Regression Narrowing Log, 2026-06-23 Late Pass

This section records the source-reading trail immediately after the user
reported that black LOD and white diagonal marks still persist.

### User correction

The user explicitly called out that documentation was not being updated while
code/source reading continued. This section is added to preserve the evidence
before any further investigation.

### Why the current bug count grew

Current working conclusion:

- The polygon/grid artifact was a GL polygon-mode / fill-state problem and is
  already fixed.
- The later water/runtime repair work was not a water-only change. The commit
  `2f4ebaa6` also touched:
  - `ForgeSoftwareModelTextureBakery`
  - `ForgeOriginalVoxyModelPipeline`
  - `ForgeOriginalVoxyOculusRenderPipelineData`
  - `ForgeOriginalVoxyRenderPipeline`
  - `VoxelIngestService`
  - `ForgeVoxyInstance`
  - `ForgeOriginalVoxyClientLevelMixin`
- Because the post-grid visual state was not preserved as a labelled baseline,
  the later black-LOD regression was not bisected cleanly.
- This was a process failure: water, depth, light, material, and shaderpack
  symptoms were investigated together instead of isolating the first bad
  change.

### Relevant existing plan reminder

`docs/forge-1.20.1-black-lod-bugfix-plan-2026-06-22.md` already says:

```text
black LoD is not proven to be a water/fluid bug
```

It also ranks these as primary black-LOD hypotheses:

- shaderpack draw target / G-buffer contract drift;
- patched fragment output contract drift;
- lightmap contract drift;
- material / block-state id contract drift;
- GL state drift around the Embeddium/Oculus hook adapter.

This pass must follow that plan instead of returning to water-specific fixes.

### Static parity checks completed in this late pass

#### Draw buffer selection

Original:

`IrisVoxyRenderPipelineData.getDrawBuffers(...)`

Forge:

`ForgeOriginalVoxyOculusRenderPipelineData.getDrawBuffers(...)`

Finding:

- Both paths iterate the `voxy.json` target id array in order.
- Both resolve each target through Iris/Oculus `RenderTargets.getOrCreate`.
- Both choose alt texture when `getFlippedAfterPrepare()` says the stage wrote
  to alt, otherwise main texture.

Current conclusion:

- No obvious target-id to texture-id order bug was found in this method.

#### Framebuffer attachment and `glDrawBuffers`

Forge file:

`src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java`

Finding:

- `resizeExternal(...)` stores the opaque and translucent external texture id
  arrays from `ForgeOriginalVoxyOculusRenderPipelineData`.
- `attachDrawTargets(...)` attaches target `i` to
  `GL_COLOR_ATTACHMENT0 + i`.
- `setDrawBuffers(...)` calls `glNamedFramebufferDrawBuffers` with the same
  ordered attachment array.

Current conclusion:

- No obvious attachment-order mismatch has been found yet.
- This does not prove the G-buffer contract is correct, because blend state,
  clear state, and shader output layout still need runtime/audit evidence.

#### Shaderpack patch JSON parsing

Original:

`IrisShaderPatch`

Forge:

`ForgeOriginalVoxyOculusShaderPatch`

Finding:

- The core fields align:
  - `opaqueDrawBuffers`
  - `translucentDrawBuffers`
  - `uniforms`
  - `samplers`
  - `opaquePatchData`
  - `translucentPatchData`
  - `ssbos`
  - `blending`
  - `taaOffset`
  - `excludeLodsFromVanillaDepth`
  - `renderScale`
  - `useViewportDims`
  - `skipShaderDepthHackFix`
- `emitToVanillaDepth()` still follows original semantics:
  `!excludeLodsFromVanillaDepth`.

Current conclusion:

- Do not force vanilla depth writes as a black-LOD fix unless a later source
  comparison proves original does so for the active shaderpack. Forcing it now
  would be a non-original workaround.

### Shader fragment data path found

Files:

- `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag`
- `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl`
- `src/main/resources/assets/voxy/shaders/lod/lighting.glsl`
- `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert`

Important current shader contract:

In patched shader mode, `quads.frag` calls:

```glsl
voxy_emitFragment(VoxyFragmentParameters(
    colour,
    tile,
    texPos,
    face,
    modelId,
    getLightmapUv(interData.y),
    tint,
    model.customId));
```

`quad_util.glsl` sets, in `PATCHED_SHADER` mode:

```glsl
attributes.x = lighting;
attributes.y = tintColour;
```

Because `quads3.vert` assigns:

```glsl
interData = quad.attributeData;
```

the intended mapping is:

```text
interData.x = flags / face / model id
interData.y = packed light byte
interData.z = tint colour
interData.w = unused here
```

`lighting.glsl` maps the byte to lightmap UV as:

```glsl
vec2 base = vec2((index >> 4) & 0xFu, index & 0xFu) / 15;
```

This matches the current Java-side packing where the low nibble is sky light
and the high nibble is block light.

Current conclusion:

- The resource shader contract itself still looks internally consistent.
- If final generated solid quads carry a sane nonzero light byte, the black
  output likely happens inside shaderpack material/G-buffer handling.
- If final generated solid quads carry zero/near-zero light, the root remains
  upstream in section light/neighbor-light/mesh generation.

### New high-risk finding: flat attributes depend on provoking-vertex state

File:

`src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert`

Important code:

```glsl
setupQuad(quad, quadData[uint(gl_VertexID) >> 2], pos, (gl_VertexID & 3) == 1);
interData = quad.attributeData;
```

This means only vertex 1 of each quad intentionally generates full flat
attributes.

File:

`src/main/java/me/cortex/voxy/client/core/rendering/util/SharedIndexBuffer.java`

Important index order:

```java
i + 1, i + 2, i + 0,
i + 1, i + 3, i + 2
```

That design makes vertex `i + 1` the first vertex for both triangles.

File:

`src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyMdicSectionRenderer.java`

Observed in translucent path:

```java
glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);
```

Why this matters:

- With `flat` interpolation, the fragment receives attributes from the
  provoking vertex.
- This renderer design depends on `GL_FIRST_VERTEX_CONVENTION` so the fragment
  receives the only vertex that actually computed `interData`.
- If opaque/temporal terrain draw misses this state, or if another hook changes
  it before draw, `interData` can be undefined/stale.

Symptoms this could explain:

- black solid LOD, if `interData.y` light byte is undefined or zero;
- transparent/missing solid faces, if flags/model id are undefined;
- white diagonal/weather-like streaks, if depth/material output is wrong;
- holes at vanilla/LOD transition, if wrong depth or discard state is used.

Next required check before editing:

1. Compare original `MDICSectionRenderer.renderTerrain(...)` against
   `ForgeOriginalVoxyMdicSectionRenderer.renderTerrain(...)`.
2. Confirm opaque and temporal paths also set
   `glProvokingVertex(GL_FIRST_VERTEX_CONVENTION)` immediately before
   `glMultiDrawElementsIndirectCountARB`.
3. Confirm the GL state capture/restore path does not leave a different
   provoking-vertex convention for terrain draw.
4. If missing, the fix is small and original-equivalent: set the provoking
   vertex convention in every terrain draw path exactly where original does.

### Provoking-vertex hypothesis result

Status: excluded as the main current bug.

Original file:

`src/main/java/me/cortex/voxy/client/core/rendering/section/backend/mdic/MDICSectionRenderer.java`

Original `renderTerrain(...)` does:

```java
glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);
glMultiDrawElementsIndirectCountARB(...);
```

Forge file:

`src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyMdicSectionRenderer.java`

Forge `renderTerrain(...)` also does:

```java
glMemoryBarrier(GL_COMMAND_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
glProvokingVertex(GL_FIRST_VERTEX_CONVENTION);
glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
glMultiDrawElementsIndirectCountARB(...);
glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
```

Conclusion:

- The opaque and temporal terrain draw path is not missing the original
  provoking-vertex state.
- The already-fixed grid artifact is still protected here by explicit fill mode.
- Do not spend more time on this hypothesis unless a later runtime GL-state
  audit proves another hook changes provoking-vertex state between this call and
  the draw, which is currently unlikely because the call is immediately before
  draw.

Next binary split:

The next useful evidence is not another visual guess. It must answer:

```text
Are generated solid LOD quads carrying valid light/custom block-state data
before the shaderpack receives them?
```

If yes, black LOD is downstream in shaderpack G-buffer/material output. If no,
black LOD is upstream in world ingest, neighbor-light selection, or model
metadata/quad packing.

## Candidate 1: Existing Final-Quad Lighting Audit

Status: `confirmed` as usable diagnostic evidence, not as a bug fix.

Source checked:

- `ForgeOriginalVoxyRenderDataFactory.AUDIT_LIGHTING`
- `ForgeOriginalVoxyRenderDataFactory.auditQuadLighting(...)`
- `build.gradle` run configuration

Finding:

- `ForgeOriginalVoxyRenderDataFactory` already has a gated final-quad lighting
  audit:

```java
private static final boolean AUDIT_LIGHTING =
        Boolean.getBoolean("voxy.forge.auditLighting");
```

- The audit samples generated quad records after mesh generation and logs:
  - section position and LoD level;
  - quad count;
  - sampled count;
  - sky light min/max/average;
  - block light min/max/average.
- This is exactly the next binary split needed for black LOD:

```text
if generated solid quads have low/zero sky light:
    upstream data / neighbor light / mesh packing is still wrong
else:
    shaderpack G-buffer/material/light output is the remaining primary suspect
```

Build/run configuration finding:

- `build.gradle` currently sets Forge and mixin system properties for run
  configs, but does not expose `voxy.forge.auditLighting` as a Gradle project
  property.
- No source or build-file edit is required for the next test. The audit can be
  enabled for a single run with a temporary JVM environment variable instead of
  committing diagnostic plumbing.

Next action:

Run one client session with:

```text
JAVA_TOOL_OPTIONS=-Dvoxy.forge.auditLighting=true
```

Then read only the bounded log lines containing:

```text
Original Voxy quad lighting audit
Voxy auto ingest
```

Expected decision:

- `skyAvg` near 0 on visible LOD solid terrain: `confirmed` upstream light/mesh
  bug.
- `skyAvg` high/normal while visible LOD remains black: `confirmed` downstream
  shaderpack output/material/G-buffer bug.

### Candidate 1 runtime result

Status: `confirmed` downstream shaderpack/material/G-buffer path is now the
primary bug direction.

Run:

```text
JAVA_TOOL_OPTIONS=-Dvoxy.forge.auditLighting=true
rtk test .\gradlew runClient
```

Relevant log lines:

```text
Original Voxy quad lighting audit: run=1 ... lvl=4 quads=26 ... skyMin=15 skyMax=15 skyAvg=15.00 blockMin=0 blockMax=0 blockAvg=0.00
Original Voxy quad lighting audit: run=2 ... lvl=4 quads=52 ... skyMin=15 skyMax=15 skyAvg=15.00 blockMin=0 blockMax=0 blockAvg=0.00
Original Voxy quad lighting audit: run=3 ... lvl=4 quads=79 ... skyMin=15 skyMax=15 skyAvg=15.00 blockMin=0 blockMax=0 blockAvg=0.00
...
Original Voxy quad lighting audit: run=12 ... lvl=4 quads=23 ... skyMin=15 skyMax=15 skyAvg=15.00 blockMin=0 blockMax=0 blockAvg=0.00
```

Auto-ingest also showed real section conversion continuing:

```text
Voxy auto ingest minecraft:overworld: queued=0 attempted=45 converted=508 nonAirSections=432 ... missingSkyLightSections=0 deferredLightSections=0
Voxy auto ingest minecraft:overworld: queued=0 attempted=14 converted=161 nonAirSections=136 ... missingSkyLightSections=0 deferredLightSections=0
Voxy auto ingest minecraft:overworld: queued=0 attempted=7 converted=80 nonAirSections=68 ... missingSkyLightSections=0 deferredLightSections=0
```

Conclusion:

- Final generated LoD quad records are not globally carrying sky light 0.
- The black solid-LoD symptom is no longer supported by the upstream
  "ingested light missing / final quad light zero" hypothesis.
- The next candidate must be shaderpack output contract:
  - `VoxyFragmentParameters` field order and meaning;
  - `model.customId` / block-state material id correctness;
  - patched fragment writes to G-buffer targets;
  - shaderpack sampler/uniform inputs used by the patch;
  - per-target blend/clear state.

Do not continue editing `VoxelIngestService` for the black solid-LoD symptom
unless future evidence contradicts this runtime audit.

## Candidate 2: Existing Shaderpack Model Id Audit

Status: `confirmed` as usable diagnostic evidence, not yet run in this late
pass.

Source checked:

- `ForgeOriginalVoxyModelFactory.AUDIT_SHADERPACK`
- `ForgeOriginalVoxyModelFactory.auditShaderpackModelInput(...)`

Finding:

- `ForgeOriginalVoxyModelFactory` already has a bounded shaderpack model audit:

```java
private static final boolean AUDIT_SHADERPACK =
        Boolean.getBoolean("voxy.forge.auditShaderpack");
private static final int MAX_SHADERPACK_MODEL_AUDITS = 48;
```

- Each sampled model bake logs:

```text
blockStateId
modelId
customId
layer
isFluid
containsFluid
fluidModelId
state
```

Why this matters:

- Candidate 1 proved final generated LOD quads can carry normal sky light.
- The next likely failure class is shaderpack material/G-buffer handling.
- `customId` is passed to the shaderpack as `VoxyFragmentParameters.customId`.
- If common solid states have `customId=0`, wrong ids, or suspicious layer
  classification, shaderpacks can treat LOD solid terrain as the wrong
  material even though geometry and light bytes are valid.

Next action:

Run a bounded client audit with:

```text
JAVA_TOOL_OPTIONS=-Dvoxy.forge.auditShaderpack=true
```

Then read only lines containing:

```text
Original Voxy model shaderpack audit
```

Expected decision:

- representative solid states have valid nonzero `customId` values and sane
  solid/cutout layer classification: material-id mapping becomes less likely;
- common solid states show `customId=0` or bad layer classification:
  `confirmed` model/material id bug.

### Candidate 2 runtime result

Status: `excluded` for the broad "customId is globally missing/zero" theory.

Run:

```text
JAVA_TOOL_OPTIONS=-Dvoxy.forge.auditShaderpack=true
rtk test .\gradlew runClient
```

Relevant result count:

```text
48 Original Voxy model shaderpack audit lines
```

Representative log samples:

```text
stone:       blockStateId=21 modelId=1 customId=10080 layer=SOLID
granite:     blockStateId=19 modelId=2 customId=10084 layer=SOLID
grass_block: blockStateId=38 modelId=5 customId=10132 layer=CUTOUT
sand:        blockStateId=40 modelId=6 customId=10232 layer=SOLID
dirt:        blockStateId=28 modelId=8 customId=10128 layer=SOLID
leaves:      blockStateId=61 modelId=9 customId=10009 layer=SOLID
water:       blockStateId=39 modelId=24 customId=32000 layer=TRANSLUCENT
```

Conclusion:

- Common solid blocks are not reaching the shaderpack with `customId=0`.
- The broad "all solid material ids are missing" theory is excluded.
- This does not yet prove every material id is semantically correct, but it
  rules out the simple global id-loss bug.
- Together with Candidate 1, this narrows the black solid-LOD problem to one of
  these remaining downstream paths:
  - patched `voxy_emitFragment` output writes to the wrong G-buffer fields;
  - framebuffer/draw-target semantics are subtly different from original Iris;
  - shaderpack sampler/uniform state differs from original;
  - per-target blend/clear state differs from original;
  - solid/cutout layer classification causes discard/holes for some blocks, but
    this does not explain all solid terrain being globally dark by itself.

Additional runtime note:

The same run produced original MDIC readback lines showing:

```text
opaquePatchUsed=true
translucentPatchUsed=true
opaqueFallback=false
translucentFallback=false
opaque draw count became nonzero
glError=0
```

So the active patched shader route is being used and is drawing; the remaining
bug is in what the shaderpack receives/writes, not in "no draw" or fallback.

## Candidate 3: Complementary Voxy Patch Field/Output Shape

Status: `excluded` for the simple "shaderpack patch ignores light/customId or
writes no opaque output" theory.

Source checked from the active local shaderpack zip:

`run/shaderpacks/ComplementaryUnbound_r5.8.1.zip`

Entries:

```text
shaders/program/voxy.json
shaders/world0/voxy.json
shaders/world0/voxy_opaque.glsl
shaders/program/voxy_opaque.glsl
```

Finding:

- `shaders/world0/voxy.json` includes `/program/voxy.json`.
- `shaders/world0/voxy_opaque.glsl` defines `OVERWORLD` and includes
  `/program/voxy_opaque.glsl`.
- `voxy.json` requests opaque draw buffers:

```text
opaqueDrawBuffers = [0, 6]
```

- `program/voxy_opaque.glsl` declares two opaque outputs:

```glsl
layout(location = 0) out vec4 gbufferData0;
layout(location = 1) out vec4 gbufferData6;
```

This matches Voxy's original target indirection model: output location 0 writes
the first requested draw target, and output location 1 writes the second
requested draw target.

- `voxy_emitFragment(...)` uses the parameters that Candidate 1 and Candidate 2
validated upstream:

```glsl
mat = int(parameters.customId);
lmCoord = clamp((parameters.lightMap - 0.03125) * 1.06667,
                vec2(0.0), vec2(0.9333, 1.0));
glColor = parameters.tinting;
vec4 color = parameters.sampledColour * vec4(glColor.rgb, 1.0);
...
gbufferData0 = color;
gbufferData6 = vec4(smoothnessD, materialMask, skyLightFactor, 1.0);
```

Conclusion:

- The shaderpack patch does not ignore `lightMap`.
- The shaderpack patch does not ignore `customId`.
- The shaderpack patch does write opaque output locations matching the requested
  target count.
- Therefore the remaining shaderpack-side bug is subtler than a missing field
  or missing output. The next candidates are:
  - shaderpack uniform values used by `DoLighting(...)` and space conversion;
  - external sampler bindings;
  - blend state application for opaque/translucent stages;
  - FBO target semantics and clear/attachment state compared with original
    Iris.

## Candidate 4: Opaque Blend Setup Mismatch

Status: `excluded`.

Question:

Does original Iris Voxy apply shaderpack blend setup during opaque terrain
rendering while Forge/Oculus only applies it during translucent rendering?

Source comparison:

Original:

`src/main/java/me/cortex/voxy/client/core/IrisVoxyRenderPipeline.java`

```java
public void setupAndBindOpaque(Viewport<?> viewport) {
    this.fb.bind();
    this.doBindings();
}

public void setupAndBindTranslucent(Viewport<?> viewport) {
    this.fbTranslucent.bind();
    this.doBindings();
    if (this.data.getBlender() != null) {
        this.data.getBlender().run();
    }
}
```

Forge:

`src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java`

```java
void setupAndBindOpaque(...) {
    ...
    if (this.oculusPipelineData != null) {
        this.bindOculusShaderpackBindings();
    }
}

void setupAndBindTranslucent(...) {
    ...
    if (this.oculusPipelineData != null) {
        this.bindOculusShaderpackBindings();
        if (this.oculusPipelineData.getBlender() != null) {
            this.oculusPipelineData.getBlender().run();
        }
    }
}
```

Conclusion:

- Forge matches original behavior for this point.
- Do not add opaque blend setup as a black-LOD fix.

## Candidate 5: Existing Log Evidence For Voxy Uniform Failure

Status: `blocked` from logs alone.

Question:

Does the latest runtime log already show missing Voxy shaderpack uniforms,
matrix-diff failures, or `vx*` uniform binding failures?

Log scan pattern:

```text
uniform
Uniform
matrix
Matrix
Oculus.*Voxy
vxModelView
vxProj
could not be found
diff
audit
```

Finding:

- The latest log contains Oculus/Complementary custom-uniform warnings:

```text
Unknown variable: BIOME_PALE_GARDEN
Unknown variable: endFlashIntensity
```

- These are shaderpack/Oculus custom-uniform compatibility warnings and are not
  direct evidence that Voxy's `vxModelView`, `vxProj`, `vxDepthTexOpaque`, or
  other Voxy patch inputs are missing.
- No direct Voxy uniform/matrix failure line was found in this bounded log
  scan.

Conclusion:

- Logs alone do not confirm the uniform path as broken.
- Logs alone also do not prove it is correct.
- Candidate remains open only if source comparison or a narrow audit checks the
  actual values/bindings used by `program/voxy_opaque.glsl`.

## Candidate 6: Generated Header Binding Points

Status: `excluded`.

Question:

Does Forge/Oculus generate different UBO/SSBO/sampler binding points from
original Iris Voxy?

Source comparison:

Original:

`src/main/java/me/cortex/voxy/client/core/IrisVoxyRenderPipeline.java`

```java
private static final int UNIFORM_BINDING_POINT = 7;
...
builder.append("#define BUFFER_BINDING_INDEX_BASE 10\n");
builder.append("#define BASE_SAMPLER_BINDING_INDEX 6\n");
```

Forge:

`src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java`

```java
public static final int UNIFORM_BINDING_POINT = 7;
public static final int BUFFER_BINDING_INDEX_BASE = 10;
public static final int BASE_SAMPLER_BINDING_INDEX = 6;
```

`src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java`

uses these same constants when generating the shader header and when binding
uniforms, SSBOs, and samplers.

Conclusion:

- The simple "Forge uses different Voxy shaderpack binding points" theory is
  excluded.
- Remaining uniform/sampler risk is about values supplied by Oculus/Forge or
  texture ids, not binding point numbers.

## Candidate 7: Sampler Unbind Parity Drift

Status: `confirmed` source parity bug.

Question:

Does Forge bind shaderpack samplers with the same null-sampler behavior as
original Iris Voxy?

Original source:

`src/main/java/me/cortex/voxy/client/iris/IrisVoxyRenderPipelineData.java`

Original binding behavior:

```java
glBindTextureUnit(unit, sampler.texture.getAsInt());
int samplerId = sampler.sampler.getAsInt();
glBindSampler(unit, samplerId == -1 ? 0 : samplerId);
```

Forge source:

`src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java`

Current binding behavior:

```java
glBindTextureUnit(unit, textureId);
int samplerId = sampler.sampler.getAsInt();
if (samplerId != -1) {
    glBindSampler(unit, samplerId);
}
```

Why this matters:

- In original Voxy, `samplerId == -1` explicitly unbinds the sampler object by
  binding sampler `0`.
- In Forge, `samplerId == -1` leaves whatever sampler object was previously
  bound to that texture unit.
- Voxy shaderpack patches sample multiple shaderpack textures and external
  textures during lighting/material evaluation.
- A stale sampler object can change filtering/compare/wrap behavior for the
  shaderpack G-buffer/depth/noise/light inputs without producing a GL error.

Conclusion:

- This is a real original-parity drift in the active shaderpack path.
- The fix is one-line and original-equivalent: always call `glBindSampler(unit,
  samplerId == -1 ? 0 : samplerId)`.
- After the fix, run `compileJava` and then one client visual/log test.

Fix applied:

`src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java`

```java
glBindSampler(unit, samplerId == -1 ? 0 : samplerId);
```

Validation:

```text
rtk test .\gradlew compileJava
BUILD SUCCESSFUL
```

Runtime visual status:

- `rtk test .\gradlew runClient` completed after the sampler-unbind fix.
- Bounded latest-log scan after the run showed no new Voxy sampler/image-binding
  failure line and no new Voxy GL error line in the captured patterns.
- Auto-ingest continued in the overworld, including converted sections and no
  persistent zero-conversion stall.
- Visual result is pending user confirmation because the agent is not using
  ComputerUse for visual inspection.
