# Forxy original-Voxy technical-debt repayment plan

Status: active planning ledger
Branch: `Forxy`
Baseline: `648aa054` (`Add Bobby and DH real-data regression gates`)
Round-numbering rule: original-author debt uses Chinese financial numerals
(`壹轮`, `贰轮`, ...), deliberately distinct from the Forge migration's Roman
rounds. Ordering remains easiest and most isolated work first; state-machine and
GPU architecture work last.

## Purpose

The Forge 1.20.1 migration is complete. This document tracks deliberate Forxy
improvements to technical debt inherited from original Voxy. These changes are
not missing Forge ports and must not be presented as migration parity repairs.

The initial scan found 358 `TODO` / `FIXME` / `HACK` / not-implemented marker
lines in 99 original-source or shared-resource files. Repeated notes, excluded
Fabric-only code, unused stubs, and multiple comments describing one mechanism
are consolidated below into eleven executable rounds.

## Change policy

Original Voxy remains the historical behavior baseline even when Forxy
intentionally improves it.

- Identify the original owner and reproduce or characterize the debt before
  changing production code.
- Record every intentional difference as a `Forxy delta`; never rewrite history
  to make the improvement appear to have existed upstream.
- When an original class has a separate active Forge port, change the active
  Forge owner. Do not edit the excluded original copy merely to hide a diff.
- When Forge directly compiles a shared original class or shader, preserve the
  pre-change contract in a focused regression test or an audit note.
- Do not remove a TODO solely to reduce the marker count. Remove or rewrite it
  only after its behavior is implemented, rejected with evidence, or classified
  as an intentional permanent constraint.
- Do not combine unrelated correctness and optimization work in one repair.
- A preview, debug route, synthetic renderer, or fallback cannot replace the
  formal production owner.

## Completion gate for every round

Each round remains incomplete until all applicable items pass:

- [ ] Original owner, callers, data layout, and failure boundary are documented.
- [ ] A failing or characterization test exists before the production change.
- [ ] The active Forxy owner implements the change without creating a parallel
      route.
- [ ] Focused tests cover success, boundary, corruption, cancellation, or
      concurrency behavior as appropriate.
- [ ] `compileJava`, the full `test` suite, `jarJar`, and `git diff --check` pass.
- [ ] Formal JarJar content is checked when dependencies, resources, or native
      code change.
- [ ] A real client test is completed for runtime, GL, rendering, resource,
      persistence, or Minecraft-data changes.
- [ ] Relevant no-Oculus, Oculus, Acedium, Vivecraft, Bobby Reforged, Chunky, or
      Distant Horizons matrices are rerun in proportion to the change.
- [ ] This ledger and the main parity audit record the final Forxy delta and
      evidence.

## Ordered roadmap

| Round | Difficulty | Debt cluster | Primary risk addressed |
| --- | --- | --- | --- |
| 壹轮 | Easy | ZSTD native result validation | corrupt data or native failure interpreted as a buffer length |
| 贰轮 | Easy-medium | Mapper snapshot and lock safety | stuck locks and nondeterministic mapping recovery |
| 叁轮 | Medium | Optional storage position iteration | LMDB, Redis, or read-only cache cannot enumerate persisted sections |
| 肆轮 | Medium | Service cancellation accounting | stale global work permits and unnecessary worker wakeups |
| 伍轮 | Medium-hard | Storage recovery and versioning | deleted corrupt LOD, non-upgradable mappings, incomplete replica behavior |
| 陆轮 | Hard | Cache, allocator, and worker efficiency | import/streaming stalls, allocation churn, and hard capacity assumptions |
| 柒轮 | Hard | Level-aware mipping | lost light, transparency, thin detail, and unstable distant material choice |
| 捌轮 | Hard | Model and material fidelity | tint, emissive, alpha, block-entity, and model-id limitations |
| 玖轮 | Very hard | RenderDataFactory correctness | section seams, translucent culling, self-occlusion, and LOD lighting |
| 拾轮 | Very hard | GPU visibility, shaders, and multi-view ownership | queue overflow, depth errors, shader collisions, and cross-view state |
| 拾壹轮 | Extreme | NodeManager/HOC state machine | holes, stale requests, invalid leaf/inner transitions, and hierarchy corruption |

## 壹轮：native ZSTD result validation

Goal: make the default persistent storage chain reject native failures before an
error code can become a `MemoryBuffer` size.

- [x] Characterize `nZSTD_compressCCtx` and `nZSTD_decompressDCtx` success and
      error return contracts through the pinned LWJGL Zstd API.
- [x] Check compression-context and decompression-context creation results.
- [x] Check parameter-setting, compression, and decompression results with
      `ZSTD_isError` and include `ZSTD_getErrorName` in the thrown exception.
- [x] Reject decompressed sizes larger than the destination buffer and sizes
      inconsistent with serialized-section limits.
- [x] Add round-trip, truncated-frame, random-data, destination-too-small, and
      native-error tests.
- [x] Reopen a real default RocksDB -> ZSTD -> serializer world after restart.

Exit evidence: corrupted compressed data is rejected deterministically, valid
persisted data survives restart, and no native error value reaches `subSize()`.

### 壹轮 implementation record

Original ownership and failure boundary:

- Original `common/config/compressors/ZSTDCompressor` owns one compression and
  decompression context per thread. Both native calls return a ZSTD `size_t`:
  a byte count on success or an encoded error value for which
  `ZSTD_isError(result)` is true. The inherited decompression TODO passed either
  form directly to `MemoryBuffer.subSize()`.
- The active Forge default route does not construct that excluded copy. It uses
  `ForgeOriginalVoxyStorageConfig -> SectionSerializationStorage ->
  CompressionStorageAdaptor -> me.cortex.voxy.forge.ZSTDCompressor ->
  RocksDBStorageBackend`; the Forge ZSTD owner is therefore the only production
  class changed.
- A serialized section has two header longs, 32x32x32 unsigned-short LUT
  indices, and at least one LUT long. Its minimum accepted byte count is 65,560;
  the existing conservative maximum remains
  `SectionSerializationStorage.BIGGEST_SERIALIZED_SECTION_SIZE` (524,296).

Forxy delta:

- Null compression/decompression contexts are rejected before cleaner
  registration. A failed decompression-context parameter setup frees the
  context immediately.
- Parameter setup, compression, and decompression check `ZSTD_isError` before
  any numeric size check, and failures include the pinned LWJGL 3.3.1
  `ZSTD_getErrorName` text.
- Successful native sizes must be positive, fit the destination, and—when
  decompressed—fit the serialized-section interval. Compression rejects input
  outside that same interval.

Automated evidence on 2026-07-17:

- Pre-change characterization: 6 focused tests executed; the legal round trip
  passed and the other 5 failed because native errors reached `subSize()` or
  invalid section sizes were accepted.
- Post-change: 8 ZSTD boundary/native tests and 1 real default-chain restart
  test passed. The restart test generated the default config, wrote a real
  RocksDB database through serializer and ZSTD, closed it, reread the config,
  reopened the database, and compared all 32x32x32 restored values.
- Clean `compileJava test jarJar`: 42 suites / 140 tests, zero failures, errors,
  or skips. `git diff --check` passed. The formal all-JAR contains the Forge ZSTD
  class and all required metadata; 598 entries and 6 nested JarJar libraries
  were found with no required entry missing.

Real-client evidence on 2026-07-17:

- First client process created the default chain and opened
  `run/saves/新的世界/voxy/74d2036cf9ebdb83178e6f984bd8904e/storage`.
  After the user moved through the world to exercise LOD writes, the renderer,
  persistent WorldEngine, network-session runtime, and Forge instance all shut
  down normally with no Voxy, ZSTD, RocksDB, or lock error.
- Before restart the database contained 137 files / 607,033,029 bytes. A second
  client process reread the existing config, opened the identical world id and
  storage path, displayed the persisted LOD normally, and again completed every
  shutdown stage without a storage/native error.

All 壹轮 completion gates are therefore closed.

## 贰轮：Mapper snapshot and lock safety

Goal: make mapping snapshots and failed-entry recovery deterministic without
changing valid mapping IDs.

- [ ] Put block and biome snapshot locks behind `try/finally` or an equivalent
      scoped owner.
- [ ] Test an exception during snapshot validation and prove the next access can
      still acquire the lock.
- [ ] Define snapshot consistency while mappings are added concurrently.
- [ ] Replace random invalid-block substitution with a deterministic missing or
      error-state policy that cannot silently map one corrupt entry to an
      unrelated real block.
- [ ] Preserve air id `0`, stored numeric ids, callback order, and existing valid
      serialized mappings.
- [ ] Add concurrent block/biome insertion and snapshot stress tests.

Exit evidence: valid mapping bytes remain compatible, invalid entries have a
stable outcome, and no exception can strand either mapping lock.

## 叁轮：optional storage position iteration

Goal: make every advertised optional backend implement the section-position
enumeration required by restart and world discovery.

- [ ] Specify `iteratePositions(level, consumer)` ordering, level filtering,
      duplicate handling, and callback exception behavior from the working
      RocksDB contract.
- [ ] Implement LMDB cursor iteration without leaking transactions, cursors, or
      native buffers.
- [ ] Implement bounded Redis iteration without loading an unbounded keyspace in
      one allocation.
- [ ] Implement read-only cache enumeration as a deduplicated union of cache and
      source positions.
- [ ] Replicate ID mappings into the read-only cache without mutating the
      read-only source.
- [ ] Add empty, mixed-level, duplicate, large-set, restart, and close-during-
      iteration tests for each backend.

Exit evidence: LMDB, Redis, and ReadonlyCaching can create, close, reopen, and
enumerate a real multi-level world without `Not yet implemented` exceptions.

## 肆轮：service cancellation accounting

Goal: keep service-local task permits, global job counts, and pooled wake permits
consistent when work is stolen, drained, or cancelled during shutdown.

- [ ] Model the invariants between `Service.tasks`,
      `ServiceManager.totalJobs`, and `MultiThreadPrioritySemaphore` permits.
- [ ] Add deterministic race tests for execute versus run, steal, drain, service
      shutdown, manager shutdown, and thread-count changes.
- [ ] Design an explicit cancellation/retraction mechanism; do not simulate it
      with an unsafe negative semaphore release.
- [ ] Prove cancelled jobs cannot leave workers spinning and cannot consume a
      wake intended for another live service.
- [ ] Preserve Embeddium builder-thread sharing through
      `SemaphoreBlockImpersonator`.
- [ ] Stress repeated renderer/session shutdown and re-enable cycles.

Exit evidence: counters and permits return to zero, all workers terminate, and
no submitted live job is lost or executed after its service cleanup.

## 伍轮：storage recovery and format versioning

Goal: recover or fail explicitly when persistent data is corrupt, stale, or
partially replicated.

- [ ] Add a mapping data-version record and define upgrade, refusal, and backup
      behavior before changing the stored format.
- [ ] Preserve backward reads for the current production database format.
- [ ] Rebuild a corrupt higher-level section from valid children when possible;
      distinguish regenerated air from unavailable data.
- [ ] Make fragmented storage detect divergent/missing replicas and repair them
      only from a verified good copy.
- [ ] Define and implement `ConditionalConfig`, or remove it from the advertised
      configuration registry with an explicit compatibility decision.
- [ ] Add crash-interruption, corrupt-value, partial-replica, old-version, and
      failed-upgrade recovery tests.
- [ ] Verify real RocksDB persistence and the optional backends from 叁轮.

Exit evidence: recovery never invents unrelated block mappings, never destroys
the sole valid replica, and leaves an auditable result after a failed upgrade.

## 陆轮：cache, allocator, and worker efficiency

Goal: remove avoidable CPU allocation, cache contention, and worker stalls before
changing visual algorithms.

- [ ] Benchmark imports, rapid flight, steady camera, and shutdown before each
      optimization.
- [ ] Replace `ActiveSectionTracker`'s loader wait and secondary-cache policy
      only after proving acquire/release and null-on-empty semantics.
- [ ] Make section-array reuse respond to allocation rate or memory pressure.
- [ ] Remove `AllocationArena`'s greater-than-`2^30` failure boundary or enforce
      a checked limit before construction; add alignment support if required by
      real consumers.
- [ ] Reduce `HierarchicalBitSet` slow paths without changing allocation order.
- [ ] Add render-generation caching with explicit dirty invalidation and bounded
      ownership.
- [ ] Evaluate mapped async uploads, `allocFromLargest`, multibind, and buffer
      reuse independently; retain only measured improvements.
- [ ] Add performance thresholds without making timing-sensitive unit tests
      flaky.

Exit evidence: identical visible output and persisted data with measured lower
allocation, contention, or frame-time cost on at least one real workload and no
regression on the others.

## 柒轮：level-aware mipping

Goal: make distant voxel selection preserve meaningful visibility and lighting
instead of choosing primarily by occurrence count.

- [ ] Build deterministic 2x2x2 fixtures for opaque, cutout, translucent, fluid,
      emissive, thin, and mixed-light inputs.
- [ ] Define level-aware weighting for opacity and visual bounding boxes.
- [ ] Define whether light should use maximum, weighted average, or a material-
      aware rule; test skylight and block light separately.
- [ ] Preserve thin but visually dominant structures without making sparse noise
      dominate every higher LOD.
- [ ] Guarantee stable selection independent of input traversal order.
- [ ] Measure CPU cost during Chunky generation, Bobby import, and DH import.
- [ ] Visually qualify day/night, water, forests, emissive blocks, and high-
      contrast silhouettes across several LOD levels.

Exit evidence: fixture rules are explicit and stable, real-world detail/light
improves, and ingest throughput stays within an accepted measured budget.

## 捌轮：model and material fidelity

Goal: replace original model approximations only where a stronger representation
can be carried through baking, storage, mesh generation, and shaders.

- [ ] Inventory every packed model-data bit before changing limits or metadata.
- [ ] Decide whether the 65,535-state limit needs a wider id or deduplication by
      baked model content; update all CPU/GPU layouts as one change.
- [ ] Implement deterministic constant tint, biome tint detection, emissive
      lighting, and per-pixel alpha classification.
- [ ] Add face occlusion masks and prove their orientation at section borders.
- [ ] Support or explicitly classify block-entity/custom-rendered models rather
      than silently treating all of them as empty.
- [ ] Add double-sided representation for vines, glow lichen, and comparable thin
      models without duplicating hidden geometry.
- [ ] Add golden software-raster, mip, metadata, upload-readback, and shader-
      decode fixtures.
- [ ] Visually qualify leaves, stained glass, vines, fluids, emissive blocks,
      custom models, and biome transitions.

Exit evidence: every new metadata field has a tested consumer, existing world
data is migrated or invalidated safely, and representative model classes render
without new holes or colour drift.

## 玖轮：RenderDataFactory correctness

Goal: resolve the 47 inherited TODO/FIXME lines in the active mesher by behavior,
not by comment deletion.

- [ ] Split the debt into independent fixtures: same-model culling, self-
      occlusion, neighbor-face occlusion, section-border faces, opaque masks,
      translucent geometry, self lighting, and incremental-run counts.
- [ ] Capture the current original output for ordinary opaque cubes before any
      algorithm change.
- [ ] Define face ownership at all six section borders so adjacent rebuild order
      cannot create gaps or duplicate quads.
- [ ] Use model occlusion masks from 捌轮 instead of guessing solely from
      ids or average depth.
- [ ] Carry correct light for merged runs, forward/backward faces, self-lit
      models, and cross-section neighbors.
- [ ] Replace overflow and end-of-run hack fixes with checked invariants.
- [ ] Add randomized voxel-volume differential tests and explicit stained-glass,
      water, leaves, slab, stair, and thin-model fixtures.
- [ ] Stress concurrent dirty/remesh events and shaderpack toggles.

Exit evidence: no vanilla/LOD seams, holes, duplicate geometry, translucent
border loss, or lighting regressions in automated fixtures and real visual runs.

## 拾轮：GPU visibility, shaders, and multi-view ownership

Goal: harden GPU bounds and make every viewport own all state that can differ
between eyes, mirrors, cameras, or temporal histories.

- [ ] Add bounded writes and overflow telemetry for traversal, render-list,
      command, translucent, and cleaner queues.
- [ ] Replace hard-coded top-level LOD and unexplained binding numbers with
      validated layout owners.
- [ ] Determine whether MDIC uniform and translucent-distance buffers must move
      into `MDICViewport`; test two interleaved viewports before changing layout.
- [ ] Resolve block-model positional error, merged-alpha behavior, tint encoding,
      and derivative inputs with shader/CPU golden fixtures.
- [ ] Specify reverse-Z ownership end to end before changing depth conversion.
- [ ] Repair SSAO/depth behavior only after ground-truth texture probes isolate
      the failing stage.
- [ ] Recheck no-Oculus, Oculus with several packs, Acedium, windowed Vivecraft,
      and shaderpack switching.
- [ ] Keep physical Vivecraft eye/mirror qualification open until real VR
      hardware is available.

Exit evidence: queue overflow fails closed or degrades explicitly, independent
viewports cannot contaminate one another, and all available visual matrices pass.

## 拾壹轮：NodeManager and HOC state machine

Goal: replace the original author-marked hierarchy uncertainty with an explicit,
verified transition system.

- [ ] Write a state model for request, leaf, inner, empty, geometry-in-flight,
      request-in-flight, top-level, and sentinel combinations.
- [ ] Encode legal transitions and ownership of every node id, request id,
      geometry id, watcher entry, cleaner entry, and active-position entry.
- [ ] Reproduce and fix the author-marked top-level-empty child creation failure.
- [ ] Remove assumptions that every request is a child request; validate type and
      position before mutation.
- [ ] Verify leaf-to-inner and inner-to-leaf transitions, including
      `AllChildrenAreLeaf` propagation to ancestors.
- [ ] Define inner-node child-existence zero behavior instead of warning and
      continuing with ambiguous state.
- [ ] Resolve geometry removal while generation/upload is in flight.
- [ ] Replace recursive deletion that discards recoverable geometry with a
      defined cache/download/free policy.
- [ ] Add model-based transition tests, randomized operation sequences, invariant
      verification after every step, and deterministic concurrency schedules.
- [ ] Stress rapid spectator movement, Chunky generation, Bobby/DH import,
      dimension changes, disconnect/re-entry, shaderpack rebuilds, and shutdown.
- [ ] Treat any `child change not in active map`, negative work count, stale
      request, invalid sentinel, or `inner child existence -> 0` warning as a
      failed gate until classified by a tested transition.

Exit evidence: long randomized and real-world runs preserve all state invariants,
produce no hierarchy warnings, and show no persistent or reproducible LOD holes.

## Deferred and excluded markers

The following do not become work merely because a marker exists:

- IterationT shaderpack compatibility is new post-migration compatibility work,
  not original-author debt.
- Fabric-only UI and Mixin notes are outside the Forxy Forge build unless a real
  Forge owner needs the same responsibility.
- The original `BasicAsyncGeometryManager.downloadAndRemove()` stub currently has
  no caller and the active Forge owner does not expose that interface. Reopen it
  only if a completed design requires geometry download.
- Test-only shaders and diagnostic formatting improvements remain outside the
  production queue until they block a real test or debugging workflow.
- Flashback/FREX platform-N/A results and Distant Horizons' simultaneous-mod
  rejection are platform/upstream constraints, not technical debt to bypass.

## Progress summary

- [x] 壹轮：native ZSTD result validation
- [ ] 贰轮：Mapper snapshot and lock safety
- [ ] 叁轮：optional storage position iteration
- [ ] 肆轮：service cancellation accounting
- [ ] 伍轮：storage recovery and format versioning
- [ ] 陆轮：cache, allocator, and worker efficiency
- [ ] 柒轮：level-aware mipping
- [ ] 捌轮：model and material fidelity
- [ ] 玖轮：RenderDataFactory correctness
- [ ] 拾轮：GPU visibility, shaders, and multi-view ownership
- [ ] 拾壹轮：NodeManager and HOC state machine
