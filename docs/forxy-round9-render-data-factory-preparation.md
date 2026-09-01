# Forxy 玖轮 RenderDataFactory correctness record

Status: completed on 2026-07-20. The initial preparation pass changed no
production rendering behavior; the implementation and qualification evidence
are recorded below without erasing that baseline.

## Baseline and active owner

Original Voxy remains the baseline. Current CodeGraph construction tracing
confirms that the package-local Forge classes are the single active formal
geometry route:

```text
ForgeOriginalVoxyRenderSystem
 -> AsyncNodeManager
 -> RenderGenerationService
 -> one RenderDataFactory per service worker
 -> BuiltSection
 -> BasicAsyncGeometryManager
 -> BasicSectionGeometryData
 -> MDICSectionRenderer
```

This corrects the old audit-era assumption that the Forge-named
`RenderDataFactory` was an unused duplicate. `RenderGenerationService` creates
it at its worker-context boundary and calls `generateMesh` for live
`WorldSection` jobs.

The current Forge file has 46 lines containing `TODO` or `FIXME`; the original
tree copy has 47. They are not 46 independent defects. Except for package/API
adaptations and 捌轮's exact occlusion-mask consumer, the two files retain the
same algorithms and repeated comments. The missing Forge marker is the
original "maybe enable this" note: 捌轮 enabled that path with an exact 16x16
coverage test instead of the former coarse boolean-only check.

## Formal input and output contract

### Input preparation

- One section contains 32x32x32 packed `Mapper` states.
- `prepareSectionData` resolves each block-state id to a sixteen-bit model id
  and stores two longs per voxel: packed quad inputs and CPU model metadata.
- Three 32x32 bit-plane arrays classify opaque, non-opaque, and fluid/contained-
  fluid occupancy along X.
- `acquireNeighborData` copies exactly one 32x32 touching plane from each
  required neighbour into six direction slots. Missing model ids fail through
  `IdNotYetComputedException`; `RenderGenerationService` requests the models and
  requeues the same section job.

### Geometry output

- Bucket 0 contains translucent quads.
- Bucket 1 contains shared double-sided quads.
- Buckets 2 through 7 contain the six directional faces in model direction
  order.
- Every quad is the original packed eight-byte record. Every bucket allocates
  65,536 slots, while its packed sixteen-bit GPU count can represent at most
  65,535 emitted quads.
- `BuiltSection.offsets` records the eight bucket starts. Its geometry buffer,
  packed AABB, child-existence byte, and optional occupancy data are consumed by
  the active geometry manager and MDIC renderer without a conversion route.

捌轮's model metadata and exact face masks are now upstream inputs to this
contract. 玖轮 must consume them consistently in every branch; it must not add
another face representation.

## Marker classification

The 46 marker-bearing lines collapse into the following correctness clusters.
Many comments repeat across Y/Z and the separately optimized X implementation.

| Cluster | Current owners | Risk |
|---|---|---|
| retry/reset correctness | `generateMesh`, `RenderGenerationService` | a missing-model exception leaves mesher counters, masks, bounds, or neighbour data stale for the retry |
| one face-decision rule | opaque/fluid/non-opaque inner and outer generators | different branches disagree about face existence, `cullsSame`, exact masks, or self occlusion |
| six-direction section ownership | `acquireNeighborData`, every `*OuterGeometry` method | gaps or duplicate quads depend on axis, side, or adjacent rebuild order |
| translucent and thin adjacency | `shouldMeshNonOpaqueBlockFace`, non-opaque outer paths | stained glass, leaves, panes, and vines lose a face or retain hidden internal layers |
| fluid and contained-fluid overlay | four fluid generators and `getFluidClientStateId` | water faces survive behind opaque blocks, disappear at borders, or use the base model contract |
| light source selection | all material families plus `applyQuadLight` | forward/backward or inner/outer faces select different neighbour/self light; emission must remain a floor |
| X incremental scan bookkeeping | three X-inner generators and outer handoff | the `z == 30`/`skip(31)` repair, next-entry reads, or five-bit accumulators emit extra geometry or lose cells |
| bounds and capacity failure | `Mesher.emitQuad`, `generateMesh`, `BuiltSection` | invalid AABB, per-bucket overwrite, or an unrepresentable count reaches the GPU silently |

These are performance proposals, not correctness prerequisites, and stay out of
the repair steps until the differential suite is green:

- cache packed model results in `prepareSectionData`;
- cache directional section faces;
- avoid copying neighbour planes when a proven lifetime-safe view is faster;
- emit directly to the final native buffer or make skip extraction branchless.

`BUILD_OCCUPANCY_SET` is currently false. Occupancy work is not used to justify
mesher readiness and will not be mixed into face-correctness repairs unless the
formal route enables it.

## Preparation baseline

`Round9OpaqueMeshingBaselineTest` calls the actual private production meshing
methods on a fresh formal `RenderDataFactory`; it does not implement a second
mesher. It supplies only already-packed section arrays, then reads the real
eight-bucket native output.

Two ordinary opaque fixtures are frozen before any 玖轮 production change:

| Fixture | Required result | SHA-256 of counters, quads, and bounds |
|---|---|---|
| isolated interior cube | six directional one-pixel quads, no translucent or double-sided output | `8ed3a5f1749194dbdfa643cee554767a45c0d40cb9b3f47e1a581181c7c398eb` |
| interior 16x16x16 cube | exactly six greedily merged 16x16 quads | `d8d086c905911251d26baaed74304141cd67230e76e4021d255363cb8d69f978` |

The focused exact-dependency test passes. These fixtures characterize ordinary
opaque output only; they do not claim border, translucent, fluid, lighting,
randomized, or runtime completion.

## Execution order

### 玖.1 — shared face-decision contract

- Freeze readable packed-quad decode helpers and a table-driven decision oracle
  for face existence, exact coverage, same-model adjacency, and self lighting.
- Extend the direct-owner fixture harness to slab, stair, leaves, stained
  glass, pane, vine, water, waterlogged, and emissive cases.
- First prove which inherited branches differ; do not rewrite all generators on
  suspicion.

### 玖.2 — six-direction border ownership

- Exercise every negative and positive section face with the same world cells
  represented on opposite sides of the boundary.
- Assert one owner for each visible face, zero owners for a fully covered face,
  and identical results regardless of which adjacent section rebuilds first.
- Route opaque, non-opaque, and exact-mask decisions through the same tested
  touching-face convention.

### 玖.3 — translucent and self-occlusion correctness

- Replace id-only `cullsSame` guesses with the shared decision and actual
  touching-face coverage.
- Preserve visible stained-glass boundaries while removing only proven internal
  full-face layers.
- Verify thin and double-sided models keep face ownership, lighting, tint, and
  command type from 捌轮.

### 玖.4 — fluid and contained-fluid correctness

- Test pure water, adjacent equal/different fluids, waterlogged blocks, opaque
  neighbours, and all six section borders.
- Resolve the contained-fluid model before culling and lighting decisions, then
  preserve its translucent command typing.
- Prove no base-block face is substituted for an overlay fluid face.

### 玖.5 — lighting parity

- Lock sky and block light for forward/backward, inner/outer, opaque,
  non-opaque, fluid, and self-lit faces.
- Keep model emission as a block-light floor.
- Differentially compare axis-swapped fixtures so Y/Z and X cannot select
  different light sources for equivalent geometry.

### 玖.6 — scan bookkeeping, limits, and randomized differential tests

- Replace the five-bit skip overflow repair only after 30/31/32-length and
  sparse-row fixtures reproduce its exact intended coverage.
- Decode every emitted quad back to covered cells and compare against a simple
  test-only face oracle over deterministic randomized 32-cubed volumes.
- Fail before writing beyond a bucket; verify offsets, AABB, empty sections,
  repeated reuse after failure, and cancellation/retry cleanup.

### 玖.7 — qualification

- Run the full exact-dependency suite and clean JarJar build.
- Run Embeddium-only and Oculus with Complementary Unbound, checking ordinary
  terrain, leaves, glass, water, emissive blocks, all visible LOD seams, and
  movement-triggered remeshing.
- Repeat the broader compatibility matrix only if shared geometry or shader
  layouts change.

## Implementation result

玖轮 keeps the original optimized three-axis mesher and its eight-bucket packed
output. It does not add a generic mesher, fallback renderer, or conversion path.

- New package-local `RenderFaceDecision` centralizes emission, exact/coarse
  touching-face coverage, and self/neighbor light selection. `ModelFactory`
  implements its face-coverage lookup, so every material family consumes the
  existing 捌轮 masks rather than inventing another representation.
- Opaque, non-opaque, and fluid inner/outer generators use the shared contract
  in all six directions. Exact same-model culling requires proven touching-face
  coverage; partial cutout and thin geometry are not hidden merely because ids
  match.
- Six-border ownership is symmetrical for negative and positive X/Y/Z faces.
  Same fluids suppress their internal face, different fluids emit both material
  faces, and waterlogged/contained fluids retarget to the real fluid model for
  culling, light, and translucent command typing.
- Merged runs and forward/backward faces now carry the selected self or neighbor
  light consistently. Model emission remains the block-light floor.
- The three X-inner generators now track `lastProcessedZ[32]` directly. This
  replaces the lossy five-bit skip accumulator and the special `z == 30` /
  `skip(31)` repair while preserving exact coverage for 30-, 31-, and 32-cell
  runs.
- Capacity is checked before each native write. A 65,536-slot bucket therefore
  accepts at most the representable 65,535 records and fails before the next
  write; aggregate count and descriptive packed-AABB invariants protect the
  `BuiltSection` handoff.
- Retry/reset handling is exercised through success, missing-model failure,
  recovery, and empty-section reuse of one factory. Reusable arrays remain
  worker-owned scratch, neighbor planes remain intentional lifetime-safe
  snapshots, final quads remain native, and model metadata remains owned by the
  `ModelFactory` cache. The old cache/direct-emission comments were adjudicated
  against those owners rather than deleted as presumed defects.

The active Forge `RenderDataFactory` now contains zero TODO, FIXME, or HACK
markers. This count is completion evidence only in combination with the tests
below; marker removal by itself was never a gate.

## Automated fixtures

The completed suite directly invokes the active production factory:

| Test | Contract locked |
|---|---|
| `Round9OpaqueMeshingBaselineTest` | original isolated-cube and merged 16-cube goldens |
| `Round9FaceDecisionContractTest` | exact/coarse coverage, same-model decisions, and light source |
| `Round9SectionBorderOwnershipTest` | one-owner/zero-owner rules on all six borders and material classes |
| `Round9FluidMeshingContractTest` | equal/different/contained fluids, opaque neighbors, model identity, and axis symmetry |
| `Round9MeshingLightingParityTest` | self, neighbor, emission-floor, direction, material, and border light |
| `Round9XScanBookkeepingTest` | 30/31/32 runs, sparse rows, three seeded 32-cubed differential volumes, and decoded six-face coverage |
| `Round9FactoryLimitsAndReuseTest` | pre-write 16-bit count failure, sentinel integrity, offsets, AABB, retry recovery, and empty reuse |

The exact-dependency full run passed 68 suites / 244 tests with zero failures,
errors, or skips. `git diff --check` was clean, and a clean `jarJar` build
produced `build/libs/voxy-forge-0.2.17-beta-forge-all.jar`.

## Runtime qualification

- Embeddium 0.3.31 without Oculus: normal forest, water, leaves, and LOD output;
  no holes, duplicate faces, seams, or flashing; normal world/client shutdown.
- Oculus 1.8.0 plus Complementary Unbound r5.8.1: both external Voxy shader
  patches loaded. Disabling and re-enabling shaders rebuilt the Voxy renderer;
  the post-reload scene remained stable and the client exited normally.
- Full optional matrix: Embeddium 0.3.31, Oculus 1.8.0, Acedium 0.2.7-beta,
  Vivecraft 1.3.15 windowed/non-VR, Bobby Reforged 5.0.1, and Chunky 1.3.146.
  A 6 -> 26 -> 6 render-distance cycle forced bulk load/unload/remesh work.
  Visual output remained stable, the original distance was restored, and the
  client exited with `BUILD SUCCESSFUL`.
- Targeted log scans found no `render-quad-bucket`, `render-quad-count`,
  `invalid-render-aabb`, `IdNotYetComputed`, Voxy error/fatal, or out-of-memory
  event. Known Forge coremod, Embeddium taint, optional Fabric-class, Oculus
  shaderpack metadata, and Realms-auth warnings are outside this round and did
  not interrupt rendering.

Distant Horizons is intentionally absent from the combined client because the
project preserves the explicit Voxy/DH simultaneous-mod rejection policy.

## Stop conditions

- Do not replace the original optimized mesher with a generic production
  fallback or a test oracle.
- Do not delete a repeated TODO merely because another axis appears correct.
- Do not hide a face from model id alone; prove touching-face coverage and the
  material rule.
- Do not combine the deferred caches/direct-emission optimizations with a
  correctness repair.
- Do not claim 玖轮 complete from the opaque baseline or source comparison;
  border, material, randomized, and real-client gates remain mandatory.
