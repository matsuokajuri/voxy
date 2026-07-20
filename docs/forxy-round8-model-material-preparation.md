# Forxy 捌轮 model and material preparation

Status: prepared on 2026-07-20. This preparation freezes the current contract;
it does not change production rendering behavior.

## Baseline and scope

Original Voxy remains the baseline. The traced route is:

```text
ModelBakerySubsystem -> ModelFactory -> SoftwareModelTextureBakery
 -> TextureUtils / ModelQueries -> ModelStore
 -> RenderDataFactory packed quad -> quad_format.glsl / quad_util.glsl
 -> quads3.vert / quads.frag
```

The active Forge route preserves the same ownership with
`ForgeSoftwareModelTextureBakery`, the Forge `ModelFactory`, `ModelStore`, and
the package-local active `RenderDataFactory`.

## Frozen packed-data inventory

### CPU model metadata (`long`)

Bits 0-47 contain six eight-bit face records, indexed by Minecraft direction.

| Face-bit | Meaning | Producer | Active consumer |
|---|---|---|---|
| 0 | face occludes its neighbour | `ModelFactory.buildVoxyMetadata` | `ModelQueries.faceOccludes`, `RenderDataFactory` |
| 1 | face covers the full block | `ModelFactory.buildVoxyMetadata` | none; currently orphaned |
| 2 | face may be occluded | `ModelFactory.buildVoxyMetadata` | `ModelQueries.faceCanBeOccluded`, `RenderDataFactory` |
| 3 | face uses self rather than neighbour light | `ModelFactory.buildVoxyMetadata` | `ModelQueries.faceUsesSelfLighting`, `RenderDataFactory` |
| 4-7 | reserved | none | none |
| whole byte `0xFF` | face absent | `ModelFactory.buildVoxyMetadata` | `ModelQueries.faceExists` |

Bits 48-63 are global metadata:

| Bits | Meaning | Active consumer |
|---|---|---|
| 48 | biome-colour LUT required | `RenderDataFactory` |
| 49 | translucent | quad typing / translucent meshing |
| 50 | double-sided | quad typing / double-sided draw list |
| 51 | contains a separate fluid model | fluid meshing and model substitution |
| 52 | is a fluid model | fluid masks and meshing |
| 53 | same-model neighbour culling | `RenderDataFactory` |
| 54 | fully opaque | masks and neighbour culling |
| 55-58 | block-light emission, four bits | `RenderDataFactory.applyQuadLight` |
| 59-63 | reserved | none |

### GPU `BlockModel` record (64 bytes / 16 words)

| Words/bits | Meaning | Consumer |
|---|---|---|
| words 0-5, bits 0-3 / 4-7 / 8-11 / 12-15 | face min/max U/V in sixteenth-block units | `extractFaceSizes` |
| words 0-5, bits 16-21 | face indentation in 1/64 units | `extractFaceIndentation` |
| words 0-5, bit 22 | alpha cutout | `makeQuadFlags` / fragment discard |
| words 0-5, bit 23 | merged-quad cutout override | `makeQuadFlags` |
| words 0-5, bits 24-25 | none/partial/full face tint state | vertex attributes and fragment tint |
| words 0-5, bits 26-31 | reserved | none |
| word 6, bit 0 | has tint | none; currently orphaned |
| word 6, bit 1 | `colourTint` is a biome LUT base index | `modelHasBiomeLUT` |
| word 6, bit 2 | translucent | `modelIsTranslucent` |
| word 6, bit 3 | shaded/AO | `modelIsShaded` |
| word 6, bits 4-31 | reserved | none |
| word 7 | constant RGBA tint, biome LUT base index, or `-1` | `quad_util.glsl` |
| word 8 | shaderpack custom block-state id | patched `quads.frag` path |
| words 9-15 | reserved padding | none |

### Packed geometry and atlas identity

- The raw 64-bit quad stores face/size/position in bits 0-25, model id in bits
  26-41, biome id in bits 46-54, and packed light in bits 55-62.
- Generated vertex attribute word X stores model id in bits 16-31.
- The atlas is a 256x256 model grid. Each model owns a 3x2 group of 16x16 face
  tiles; fragment UV reconstruction uses the low and high eight model-id bits.
- `quad_format.glsl` has a historical non-`uint64_t` decoder that reads twenty
  model bits, but the active GL 4.6 vertex path defines `QUAD_DATA_USE_64_BIT`,
  and the CPU packer, atlas, generated attribute, and fragment path remain
  sixteen-bit. That fallback is not evidence that widening is safe.

`Round8ModelDataLayoutContractTest` locks these current CPU/GPU/shader boundaries
before any 捌轮 behavior change.

## Capacity decision

Keep the 16-bit model id for 捌轮. Widening would simultaneously change the raw
quad, generated attributes, atlas addressing, model/colour buffers, fragment
decode, fluid substitution masks, and every model-id comparison in the mesher.
There is no measured capacity failure: the latest captured full-compatibility
run exposed 24,056 external block-state mappings, while the model factory
deduplicates those mappings further.

捌.1 keeps that layout and strengthens deduplication instead. `ModelEntry` now
compares the exact six colour/depth faces plus a render-semantic key containing
the contained-fluid model id, full CPU metadata, GPU flags, shaderpack
`customId`, constant-tint colour, biome-tint block-state identity, software-bake
flags, material layer, and all six encoded face words. The contained-fluid biome
dependency is merged before this key is built, so two biome-dependent models no
longer collapse merely because both use `-1` as their pre-LUT colour.

The factory now warns at 49,152 unique models and every following 4,096-model
boundary, retains fail-fast behavior before model id 65,536 can be allocated,
and logs mapped, unique, and deduplicated counts when its owner is released.

Model records and model ids are render-session resources, not persisted world
storage. A model-layout change requires rebuilding the renderer's generated
geometry and GPU resources, but no RocksDB/LMDB mapping-format migration.

## Existing implementation status

| Area | Current state | 捌轮 action |
|---|---|---|
| constant tint | implemented | freeze with golden CPU/GPU decode fixtures |
| biome tint | implemented, including contained-fluid dependency | add multi-source and non-equivalent-source dedupe fixtures |
| emission | four-bit metadata and mesher light maximum implemented | add state/emissive-rendering fixtures and shader decode proof |
| alpha/material layer | per-pixel alpha is inspected; atlas alpha and face cutout state are preserved | add opaque/cutout/translucent golden fixtures before changing thresholds |
| face occlusion | coarse per-face coverage/depth booleans only | design an oriented mask with a real mesher consumer for 玖轮 |
| custom renderer | deliberately classified as empty; missing model is fatal | keep the explicit classification and test; true block-entity rendering is out of the voxel-model contract until designed end to end |
| double-sided thin models | original missing-opposite-face heuristic only | add explicit vine/glow-lichen fixtures before optimizing to one double-sided representation |
| unused fields | CPU face bit 1, GPU flags bit 0, and record padding have no consumers | do not repurpose until the layout test and all consumers change together |

## Execution order

### 捌.1 — contract freeze and semantic dedupe

- [x] Extend the dedupe key to every render-visible semantic listed above.
- [x] Add equal/different fixtures for constant tint, biome tint identity,
  layer, shaded state, emission, custom id, contained fluid, cull-same, software
  mip flags, encoded face data, and exact baked colour/depth content.
- [x] Add model-count/high-water diagnostics; keep fail-fast behavior at 65,536.

`Round8ModelSemanticDedupTest`, `ModelFactoryTintSourcePlanTest`, and
`Round8ModelDataLayoutContractTest` are the 捌.1 regression gate. The exact-jar
compile and the full 212-test suite passed on 2026-07-20. Interim runtime
regression passed both the Embeddium-only client and the Oculus 1.8.0 client
with Complementary Unbound: no visual issue was reported, shutdown was clean,
and the new summaries recorded 1,047 unique models from 1,474 mappings without
Oculus and 1,008 unique models from 1,399 mappings with Oculus. The broader
material matrix remains part of 捌.5 rather than being inferred from this gate.

### 捌.2 — material and lighting goldens

- [x] Add deterministic post-raster boundary outputs for opaque stone, cutout
  iron bars, translucent glass, water, solid-layer leaves, emissive glowstone,
  and partial constant tint. Existing watertight rasterizer fixtures remain the
  geometry-stage gate immediately before these outputs.
- [x] Verify the 64-byte native upload record, 8,160-byte uploaded mip chain,
  original 8,184-byte allocation including its zero tail, and shader-side face
  decode from the same fixtures.
- [x] Change behavior only for a fixture-proven mismatch from the original
  contract or an explicitly documented Forge 1.20.1 adaptation. No material or
  lighting mismatch was confirmed in 捌.2, so no visual algorithm was changed.

`Round8ModelMaterialGoldenTest` owns the seven material goldens and SHA-256 mip
identities. Production and tests now share the same layer classifier and exact
model-record serializer, eliminating duplicate test-only reconstruction while
preserving the original packed format. `Round8ModelDataLayoutContractTest`
locks the serializer into `ModelBakeUpload`; the full test suite passed on
2026-07-20. Live GPU readback and the broader visual matrix remain 捌.5 gates.

### 捌.3 — oriented occlusion representation

- [x] Define mask resolution and all six face orientations first.
- [x] Carry one representation from bakery output to CPU model metadata and the
  active mesher; do not add dead GPU metadata.
- [x] Prove opposite-face transforms and all section-border directions before
  玖轮 consumes the masks.

The representation is a CPU-only 16x16 coverage mask generated by the same
written-pixel rule as the software bake: four `long` words per face, 24 per
unique model. The model metadata uses face bit 4 only as a tested availability
marker; the mask itself remains out of the 64-byte GPU record and shaders.
`Round8FaceOcclusionMaskTest` reads the real bakery view matrices and proves
that each opposite pair already shares raster coordinates, so touching faces
are compared directly with no rotation or mirror. The formal non-opaque culling
entry now consumes the sidecar conservatively: it hides a face only when the
target mask is nonempty and is a complete subset of the neighbour mask. The
remaining inherited mesher branches are deliberately left for 玖轮's
RenderDataFactory-wide correctness pass.

The full exact-jar suite and both runtime fronts then passed: Embeddium-only and
Oculus 1.8.0 with Complementary Unbound shut down normally, and user inspection
reported no thin-material holes or LOD seam regression.

### 捌.4 — thin and double-sided representation

- [x] Characterize vines, glow lichen, iron bars, panes, and comparable models.
- [x] Replace the global heuristic only when the new representation reduces geometry
  without losing face ownership, tint, cutout, lighting, or shaderpack ids.

The original mechanism was retained after tracing its actual consumer: it does
not duplicate back faces, but routes the model's existing quads once into the
shared double-sided command range instead of one of six camera-directional
ranges. `DoubleSidedClassification` exposes the present-face and
missing-opposite-axis masks, and now rejects the zero-face case. The post-raster
fixtures in `Round8ThinModelRepresentationTest` cover vine, glow lichen,
crossed plants, iron bars, glass panes, empty models, command typing, and cmdgen
layout. Complementary-face merging remains rejected because the current global
model bit cannot preserve two-sided lighting, normals, shaderpack ids, and face
ownership; no fixture-proven lossless replacement exists.

### 捌.5 — qualification

- [x] Run the full automated suite and clean JarJar build.
- [x] Qualify leaves, stained glass, vines, fluids, emissive blocks, biome
  transitions, and custom-renderer-empty behavior in Embeddium-only and Oculus
  clients; repeat the broader compatibility matrix if shared shader or geometry
  layouts change.

The opt-in `voxy.forge.auditRound8ModelGpuUpload` gate verifies the production
upload rather than a reconstructed fixture: after `UploadStream.commit()` it
reads each selected 64-byte model record and the four uploaded mip levels of the
corresponding 3x2 atlas tile back from OpenGL, compares every byte, and only then
frees the staging buffers. The default count is 32 and the gate is disabled in
normal builds.

On 2026-07-20 the 32-record/full-mip audit passed in both the Embeddium-only
client and Oculus 1.8.0 with Complementary Unbound. Both clients exited cleanly,
and repeated user inspection across 捌轮 reported no material, lighting, colour,
missing-face, or vanilla/LOD seam regression. The exact-dependency suite passed
all 227 tests. A clean `jarJar`/`reobfJarJar` build produced the 12,676,537-byte
`voxy-forge-0.2.17-beta-forge-all.jar`; its metadata, six pinned nested
dependencies, model classes, and Windows x64 LWJGL ZSTD/LMDB resources were
confirmed in the finished archive.

## Stop conditions

- Do not widen model ids without a measured 65,536-model failure after semantic
  deduplication.
- Do not consume reserved bits in only one layer of the pipeline.
- Do not turn custom renderers into synthetic cubes or preview geometry.
- Do not start 玖轮 mesher changes until oriented occlusion fixtures pass.
