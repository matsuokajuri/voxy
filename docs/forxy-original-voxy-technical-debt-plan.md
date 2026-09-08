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

- [x] Put block and biome snapshot locks behind `try/finally` or an equivalent
      scoped owner.
- [x] Test an exception during snapshot validation and prove the next access can
      still acquire the lock.
- [x] Define snapshot consistency while mappings are added concurrently.
- [x] Replace random invalid-block substitution with a deterministic missing or
      error-state policy that cannot silently map one corrupt entry to an
      unrelated real block.
- [x] Preserve air id `0`, stored numeric ids, callback order, and existing valid
      serialized mappings.
- [x] Add concurrent block/biome insertion and snapshot stress tests.

Exit evidence: valid mapping bytes remain compatible, invalid entries have a
stable outcome, and no exception can strand either mapping lock.

### 贰轮 implementation record

- The shared active `Mapper` remains the sole id owner. Block and biome
  registration, callback replacement, and dense snapshot capture now use their
  existing `ReentrantLock` owners with `try/finally`; there is no Forge-only
  mapping route.
- A snapshot is a validated dense id prefix captured while the corresponding
  registration lock is held. New atomic callback-and-snapshot methods divide
  concurrent entries at one lock boundary: every entry is delivered either in
  the returned initial snapshot or by the installed callback, never through a
  gap between two separate calls. Both the original and Forge render-system
  constructors use this contract.
- A stored block mapping that decodes to air at a nonzero id is retained in the
  id array as an in-memory air/missing placeholder. It is deliberately absent
  from the reverse block-state map, so canonical air remains id `0`; the corrupt
  entry's original storage bytes are not overwritten and no unrelated real
  block can be selected.
- `MapperSnapshotSafetyTest` first reproduced both stranded snapshot locks and
  the inherited random invalid-state path. It now covers block/biome validation
  exceptions, repeated corrupt-entry loading with byte preservation, concurrent
  registration plus dense snapshots, exact-once biome bootstrap delivery, and
  storage-before-callback ordering. The focused 6-test class and the clean full
  146-test suite pass with zero failures.
- `compileJava test jarJar` passed with 146 tests and the formal all-JAR was
  produced; `git diff --check` passed apart from Git's existing line-ending
  notices.
- Two post-change client processes opened the same quick-play world and existing
  `Serializer -> ZSTD(level 1) -> RocksDB` configuration. Both constructed the
  formal `ForgeOriginalVoxyRenderPipeline` / `MDICSectionRenderer`, displayed LOD
  normally by user confirmation, and shut down the renderer, network-session
  storage owner, and Forge instance cleanly. The second process reread the same
  persisted world without a forced mapping resave, missing-state decode, Mapper
  lock failure, ZSTD failure, or RocksDB failure.

All 贰轮 completion gates are therefore closed.

## 叁轮：optional storage position iteration

Goal: make every advertised optional backend implement the section-position
enumeration required by restart and world discovery.

- [x] Specify `iteratePositions(level, consumer)` ordering, level filtering,
      duplicate handling, and callback exception behavior from the working
      RocksDB contract.
- [x] Implement LMDB cursor iteration without leaking transactions, cursors, or
      native buffers.
- [x] Implement bounded Redis iteration without loading an unbounded keyspace in
      one allocation.
- [x] Implement read-only cache enumeration as a deduplicated union of cache and
      source positions.
- [x] Replicate ID mappings into the read-only cache without mutating the
      read-only source.
- [x] Add empty, mixed-level, duplicate, large-set, restart, and close-during-
      iteration tests for each backend.

Exit evidence: LMDB, Redis, and ReadonlyCaching can create, close, reopen, and
enumerate a real multi-level world without `Not yet implemented` exceptions.

### 叁轮 implementation record

`iteratePositions(level, consumer)` now has one explicit cross-backend
contract: `level == -1` enumerates every stored position, any other value keeps
only that exact encoded level, backend order is deliberately unspecified, the
callback is synchronous, and callback exceptions propagate to the caller.
LMDB performs cursor iteration inside a read-only transaction, Redis uses
bounded binary `HSCAN` pages with duplicate suppression, and
ReadonlyCachingLayer returns a deduplicated cache-first union.

ReadonlyCachingLayer also merges source/cache ID mappings under one mapping
lock, rejects conflicting bytes for the same ID, copies missing source entries
only into the cache, and preserves cache-only mappings. Its inherited `flush()`
bug now flushes rather than closes both children. LMDB, Redis, and the cache
layer use lifecycle read/write locks so close waits for active operations;
attempted close from an operation callback is rejected instead of deadlocking.

`OptionalStorageIterationTest` supplies 13 default tests plus one tagged real-
Redis integration test. The coverage includes empty and mixed-level stores,
duplicates, callback failure, 1,024-key RocksDB/LMDB restart sets, a 4,096 /
6,144-entry cache/source union, a 2,048-entry multi-page Redis scan, mapping
replication/conflict handling, flush behavior, close-during-iteration, and
reentrant-close rejection. The clean default build passed all 159 tests plus
`jarJar`; Redis 7.0.15 passed
the dedicated 2,048-key create/enumerate/restart test and exited through
`SHUTDOWN NOSAVE`.

The runtime gate used two client processes against the same existing source
world and isolated `forxy-round3-readonly-cache`. Both built the formal
`ForgeOriginalVoxyRenderPipeline` / `MDICSectionRenderer`; the second process
reopened the populated cache, visuals passed user confirmation, and every
renderer/session/storage owner shut down normally. No mapping conflict,
closed-storage error, or Voxy fatal error was logged. The original world config
was restored byte-for-byte with SHA-256
`437c1c6b67283dba6bdf52898c502b15e8de008f7dfe9d44053213965c706c9f`.
All 叁轮 completion gates are therefore closed.

## 肆轮：service cancellation accounting

Goal: keep service-local task permits, global job counts, and pooled wake permits
consistent when work is stolen, drained, or cancelled during shutdown.

- [x] Model the invariants between `Service.tasks`,
      `ServiceManager.totalJobs`, and `MultiThreadPrioritySemaphore` permits.
- [x] Add deterministic race tests for execute versus run, steal, drain, service
      shutdown, manager shutdown, and thread-count changes.
- [x] Design an explicit cancellation/retraction mechanism; do not simulate it
      with an unsafe negative semaphore release.
- [x] Prove cancelled jobs cannot leave workers spinning and cannot consume a
      wake intended for another live service.
- [x] Preserve Embeddium builder-thread sharing through
      `SemaphoreBlockImpersonator`.
- [x] Stress repeated renderer/session shutdown and re-enable cycles.

Exit evidence: counters and permits return to zero, all workers terminate, and
no submitted live job is lost or executed after its service cleanup.

### 肆轮 implementation record

The accounting invariant is now explicit. Every accepted `Service` submission
creates exactly one local task permit, one `ServiceManager` global job count,
one global pooled token, and one pooled wake signal per semaphore block. A claim
consumes the local permit and pooled token/signal; a claimed job, including one
that throws, completes the global count exactly once. Steal, drain, and service
shutdown cancel queued work under the service lifecycle lock and retract the
matching pooled signals. Claimed work remains visible through `runningJobs`, so
executor cleanup waits until it has actually left the service.

`ServiceManager` now has explicit completion and cancellation paths,
wait/notification shutdown, and a recoverable rejection when shutdown is
requested while services remain live. `MultiThreadPrioritySemaphore` retracts
only positive acquired permits, reconciles each block's pooled signal floor,
and therefore does not use a negative semaphore release. The unified pool emits
the exact number of worker-exit permits and rejects thread-count changes after
shutdown. `PerThreadContextExecutor` now releases its running-job count even
when context creation or execution throws. The resulting exception-path test
also exposed and fixed inherited lock leaks in
`WeakConcurrentCleanableHashMap` construction, cleanup, and clear paths.

Embeddium 0.3.31's actual `ChunkJobQueue` bytecode was checked against the
adapter contract: it uses `release(int)`, `acquire()`, `tryAcquire()`, and
`availablePermits()`, all of which remain covered by
`SemaphoreBlockImpersonator`. Nine deterministic cancellation-accounting tests
cover submission/shutdown linearization, steal/drain retraction, borrowed-block
execution, limited borrowed work, context-factory failure, premature manager
shutdown, and repeated `4 -> 1 -> 0 -> 3 -> 0` worker resizing across three
cycles. A clean build passed all 168 tests and `jarJar`.

The Embeddium + Oculus client then created the formal original-Voxy renderer and
passed user visual confirmation. Both renderer-owner shutdowns completed, then
the network session, Forge instance, and Minecraft stopped normally; no job-
accounting, service-manager, executor, or fatal error was logged. All 肆轮 exit
gates are therefore closed.

## 伍轮：storage recovery and format versioning

Goal: recover or fail explicitly when persistent data is corrupt, stale, or
partially replicated.

- [x] Add a mapping data-version record and define upgrade, refusal, and backup
      behavior before changing the stored format.
- [x] Preserve backward reads for the current production database format.
- [x] Rebuild a corrupt higher-level section from valid children when possible;
      distinguish regenerated air from unavailable data.
- [x] Make fragmented storage detect divergent/missing replicas and repair them
      only from a verified good copy.
- [x] Define and implement `ConditionalConfig`, or remove it from the advertised
      configuration registry with an explicit compatibility decision.
- [x] Add crash-interruption, corrupt-value, partial-replica, old-version, and
      failed-upgrade recovery tests.
- [x] Verify real RocksDB persistence and the optional backends from 叁轮.

Exit evidence: recovery never invents unrelated block mappings, never destroys
the sole valid replica, and leaves an auditable result after a failed upgrade.

### 伍轮 implementation record

Mapping storage now reserves keys `0` and `1` for a CRC-protected manifest and
full mapping backup. A database without either key remains the production legacy
format: it is read as schema/data version zero, backed up and flushed before an
`UPGRADING` manifest is written, then committed against the running Minecraft
data version without changing any serialized mapping entry or assigned ID.
Future schema/data versions are refused without writes. Interrupted upgrades
restore the verified backup before retrying; a failed upgrade restores the
original mapping bytes and leaves a `FAILED` manifest with an auditable reason.
Structural validation rejects unknown mapping namespaces, empty payloads, and
non-contiguous block or biome ID sequences instead of inventing replacements.

Corrupt persisted sections are retained as evidence. A corrupt level-above-zero
section is rebuilt only when all eight immediate children are valid, using the
original `Mipper.mip` child order, then saved and flushed. Level-zero corruption
or an incomplete child set is tagged `LOAD_UNAVAILABLE`; repeated
`acquireIfExists` calls remain null, while an ordinary acquire may expose only
explicitly tagged temporary air. `ActiveSectionTracker` preserves that status
through both caches.

Both fragmented-storage adaptors now use one reconciliation policy. Empty or
partial compatible replicas may be filled from a structurally verified
superset, byte divergence requires a strict majority, and ties or unverified
extra keys fail before any write. No path deletes a sole copy. The original
`ConditionalConfig` remains unimplemented upstream, so Forge no longer
advertises it and explicitly refuses an existing occurrence while preserving
the user's JSON unchanged.

Eleven new default-suite cases cover legacy upgrade, future-version refusal,
interrupted and failed upgrade rollback, partial and divergent replicas,
higher-LOD reconstruction/unavailability, conditional-config refusal, and real
RocksDB/LMDB reopen behavior. The clean default suite passes all 179 tests plus
`jarJar`; a tagged test also passed against a SHA-256-verified Redis 7.2.14
process on an isolated local port.

The existing 902 MB RocksDB world was copied byte-for-byte before first upgrade.
Embeddium-only then upgraded and rendered it, and Embeddium + Oculus reopened the
same upgraded database. Both paths passed user visual confirmation and closed
the renderer, persistent `WorldEngine`, network session, Forge instance, and
Minecraft normally, with no mapping-version, RocksDB, or Voxy fatal error. All
伍轮 exit gates are therefore closed.

## 陆轮：cache, allocator, and worker efficiency

Goal: remove avoidable CPU allocation, cache contention, and worker stalls before
changing visual algorithms.

- [x] Benchmark imports, rapid flight, steady camera, and shutdown before each
      optimization.
- [x] Replace `ActiveSectionTracker`'s loader wait and secondary-cache policy
      only after proving acquire/release and null-on-empty semantics.
- [x] Make section-array reuse respond to allocation rate or memory pressure.
- [x] Remove `AllocationArena`'s greater-than-`2^30` failure boundary or enforce
      a checked limit before construction; add alignment support if required by
      real consumers.
- [x] Reduce `HierarchicalBitSet` slow paths without changing allocation order.
- [x] Add render-generation caching with explicit dirty invalidation and bounded
      ownership.
- [x] Evaluate mapped async uploads, `allocFromLargest`, multibind, and buffer
      reuse independently; retain only measured improvements.
- [x] Add performance thresholds without making timing-sensitive unit tests
      flaky.

Exit evidence: identical visible output and persisted data with measured lower
allocation, contention, or frame-time cost on at least one real workload and no
regression on the others.

### 陆轮 implementation record

`ActiveSectionTracker` no longer burns CPU in `onSpinWait`/`yield` while another
thread loads a section. A `CompletableFuture` holder reserves every pending
acquire before publication, preserving the original reference count and
`nullOnEmpty` status contract. The secondary cache is split across the existing
64 tracker slices, with per-slice LRU limits whose sum is the original global
budget. Deterministic concurrency tests cover one-load publication, waiter
references, unavailable-section reuse, and the 64-entry global ceiling. The
tagged contention harness reduced eight waiting threads from approximately
906,250,000 CPU ns to zero while keeping one physical load.

The fixed roughly 100 MiB `WorldSection` array reserve is now an adaptive pool.
It grows only after measured allocation bursts, shrinks after quiet periods, and
checks memory pressure every 64 releases; its hard target remains between 32 and
400 arrays. Real Embeddium and Oculus runs reused 96,031 and 131,785 arrays while
allocating 2,134 and 2,109 respectively, then both returned to a 32-array target
at shutdown. This provides a non-timing allocation threshold without pinning the
old maximum during ordinary play.

`AllocationArena` now rejects non-positive or unrepresentable requests and
enforces the packed 30-bit maximum (`2^30-1`) before construction, preventing
silent packed-size corruption. Its active consumers already supply their own
required alignment, so an unused internal alignment mode was not added.
`HierarchicalBitSet` now finds and updates consecutive runs by words, supports an
exact 64-bit run and exact-limit fill, and rolls the high-water mark back by word
rather than by bit. Randomized model tests preserve lowest-ID allocation order,
coalescing, overlap safety, and exact boundary behavior.

The CPU geometry cache has explicit per-position striped epochs, dirty and
neighbor invalidation, byte and 2,048-entry limits, and unambiguous ownership.
Runtime evidence rejected the first admission policy: cloning every generated
mesh produced zero hits, 40,544 evictions, and 98,185,184 retained bytes. The
retained policy takes ownership only of a late result that the node manager
would otherwise discard; ordinary generated geometry is never cloned, and an
empty cache misses without taking its lock. Both final client runs reported zero
retained bytes and zero evictions. This keeps the safe late-result case without
claiming a hit-rate improvement that the tested workload did not produce.

The remaining candidates were audited independently. The active Forge owner
already uploads geometry and metadata through persistently mapped staging plus
compute copies, reuses two synchronization result objects, and returns the large
geometry buffer through `RenderResourceReuse`. `allocFromLargest` has no real
consumer, three SSBO bindings do not justify a multibind conversion, and the
original `downloadAndRemove` GPU-to-CPU cache hook is still unimplemented; adding
a synchronous GPU readback would introduce an unmeasured stall. None of those
speculative paths was added.

The default suite, the tagged performance harness, and the real Bobby Reforged
region/Distant Horizons SQLite decoder gate pass. Embeddium-only rapid-flight,
return-flight, steady-camera, and normal-shutdown runs passed user visual
confirmation. Oculus 1.8.0 with Complementary Unbound also applied both external
Voxy shader patches, passed the same visual workload, and shut down normally.
There was no Voxy fatal error, persisted-data mutation, LOD flash, hole, seam, or
shader regression reported. All 陆轮 exit gates are therefore closed.

## 柒轮：level-aware mipping

Goal: make distant voxel selection preserve meaningful visibility and lighting
instead of choosing primarily by occurrence count.

- [x] Build deterministic 2x2x2 fixtures for opaque, cutout, translucent, fluid,
      emissive, thin, and mixed-light inputs.
- [x] Define level-aware weighting for opacity and visual bounding boxes.
- [x] Define whether light should use maximum, weighted average, or a material-
      aware rule; test skylight and block light separately.
- [x] Preserve thin but visually dominant structures without making sparse noise
      dominate every higher LOD.
- [x] Guarantee stable selection independent of input traversal order.
- [ ] Measure CPU cost during Chunky generation, Bobby import, and DH import.
- [ ] Visually qualify day/night, water, forests, emissive blocks, and high-
      contrast silhouettes across several LOD levels.

Exit evidence: fixture rules are explicit and stable, real-world detail/light
improves, and ingest throughput stays within an accepted measured budget.

Automated implementation status (2026-07-19):

- `Mipper.mip` now receives the target level from both the four voxelized-
  section passes and higher-LOD child recovery. Block occurrence count owns the
  representative; cached opacity, outline-shape volume, fluid state, and light
  emission resolve equal support. Block id and biome id provide deterministic
  final tie breaks, so input traversal order cannot change the result.
- Sparse non-air detail requires one source through levels 1-2, two at level 3,
  three at level 4, and four above it. Very thin shapes require one additional
  source from level 4 onward. Emissive material is preserved through level 4,
  then requires increasing support instead of expanding forever.
- Block light uses the maximum of the eight inputs. Non-air output uses maximum
  non-air skylight; air output uses the ceiling average skylight. The selected
  block's lowest present biome id is the stable representative.
- The deterministic suite covers opaque stone, cutout leaves, translucent
  glass, water, glowstone, iron bars, mixed block/skylight, 256 traversal
  permutations, and the level 1-4 section chain. The non-gating worst-case mixed
  material benchmark measured 86.2 ns per mip, about 50.4 us for all 585 mips
  in one section, within the explicit 120 ns/op and 12x comparison budget.
- Runtime import timing is gated by `-PvoxyAuditRound7Mipping` and reports the
  average whole-section mipping time every 1,024 sections. Chunky/Bobby/DH and
  visual qualification remain open until the client passes below are recorded.

## 捌轮：model and material fidelity

Goal: replace original model approximations only where a stronger representation
can be carried through baking, storage, mesh generation, and shaders.

- [x] Inventory every packed model-data bit before changing limits or metadata.
      The producer/consumer ledger and contract test are recorded in
      `forxy-round8-model-material-preparation.md`.
- [x] Decide whether the 65,535-state limit needs a wider id or deduplication by
      baked model content. Keep the sixteen-bit layout; strengthen semantic
      baked-model deduplication and add high-water evidence before reconsidering
      any end-to-end width change.
- [x] Include every render-visible semantic in the dedupe key: render layer,
      shaded state, biome tint identity, emission, custom id, contained fluid,
      cull-same behavior, and encoded face metadata.
- [x] Implement deterministic constant tint, biome tint detection, emissive
      lighting, and per-pixel alpha classification.
- [x] Add face occlusion masks and prove their orientation at section borders.
- [x] Support or explicitly classify block-entity/custom-rendered models rather
      than silently treating all of them as empty.
- [x] Add double-sided representation for vines, glow lichen, and comparable thin
      models without duplicating hidden geometry.
- [x] Add golden software-raster, mip, metadata, upload-readback, and shader-
      decode fixtures.
- [x] Visually qualify leaves, stained glass, vines, fluids, emissive blocks,
      custom models, and biome transitions.

### 捌轮 preparation record (2026-07-20)

- The original and active Forge routes were traced through bakery, record,
  mesher, atlas, vertex, fragment, and Oculus custom-id consumers.
- `Round8ModelDataLayoutContractTest` freezes the current metadata, 64-byte GPU
  record, sixteen-bit atlas identity, and active shader decode before behavior
  changes.
- The existing constant/biome tint, emission, alpha/material classification,
  and intentional custom-renderer-empty handling are partial foundations, not
  unverified completion claims.
- The round is split into 捌.1 semantic dedupe, 捌.2 material/light goldens,
  捌.3 oriented occlusion, 捌.4 thin/double-sided representation, and 捌.5 runtime
  qualification. Detailed gates live in the preparation document.

### 捌.1 implementation record (2026-07-20)

- `ModelEntry` now retains exact baked colour/depth equality while its semantic
  key also covers contained fluid, CPU metadata including emission and
  cull-same, GPU/shading flags, `customId`, constant or biome tint identity,
  material layer, software mip flags, and all six encoded face records.
- Contained-fluid biome dependence is resolved before duplicate lookup; biome
  LUT allocation still occurs only after a genuinely new model is accepted.
- Capacity remains sixteen-bit and fail-fast. High-water diagnostics begin at
  49,152 unique models, repeat every 4,096 models, and teardown reports mapped,
  unique, and deduplicated totals.
- The layout, semantic-equality, pipeline-order, exact-jar compile, and full
  212-test gates pass. Embeddium-only and Oculus 1.8.0 plus Complementary
  Unbound runtime passes also completed with clean shutdown and no reported
  visual regression; the model summaries proved live deduplication in both
  paths. The broader material qualification remains in 捌.5 after the remaining
  behavior work.

### 捌.2 implementation record (2026-07-20)

- Seven deterministic material fixtures now cover opaque stone, cutout iron
  bars, translucent glass, water, solid-layer leaves, glowstone emission, and
  partial constant tint at the formal bakery-to-record boundary.
- Each fixture freezes material layer, six face words, GPU flags, tint identity,
  CPU metadata, emission, mip SHA-256, the array/native mip equivalence, and the
  active shader bit decode.
- The exact production serializer is shared with `ModelBakeUpload`: it writes
  sixteen words into 64 bytes. The original texture allocation remains 8,184
  bytes while only 8,160 bytes are uploaded; the unused final 24 bytes stay
  zero and are now explicitly tested.
- The duplicate Forge-side layer classifier was removed in favor of the bakery
  classifier already used by the original-adapted route. Goldens confirmed no
  material or lighting mismatch, so 捌.2 made no visual algorithm substitution.
- CPU/native serialization is covered here; 捌.5 subsequently completed live
  GPU readback and the dual-route visual matrix before closing the broader
  upload-readback checklist.

### 捌.3 implementation record (2026-07-20)

- Every unique model now owns an exact six-face, 16x16 CPU coverage sidecar:
  four `long` words per face and 24 words per model. It is derived directly
  from the original `TextureUtils.generateMask` written-pixel contract and does
  not widen the persistent world format, sixteen-bit model id, 64-byte GPU
  record, or shader interface.
- One formerly unused CPU face-metadata bit now states that the corresponding
  non-translucent, boundary-depth face has a usable exact mask. Absent faces
  still require the existing `faceExists` guard, so their `0xFF` sentinel cannot
  be mistaken for a mask-bearing face.
- The active non-opaque mesher culling entry now compares the current face mask
  with the touching neighbour face and removes the quad only when every current
  pixel is covered. Missing or unpublished sidecars fail open and retain the
  face. Coarse `faceOccludes` is retained only for metadata without the new
  exact-mask marker.
- Coarse face occlusion and `fullyOpaque` now require all 256 coverage pixels,
  replacing the inherited greater-than-90-percent approximation. Partial
  cutout faces stay eligible as exact-mask occluders without being promoted to
  opaque cubes.
- `Round8FaceOcclusionMaskTest` proves from the actual original-adapted bakery
  matrices that DOWN/UP, NORTH/SOUTH, and WEST/EAST share raster coordinates;
  it then locks direct subset, no-mirror, empty-mask, metadata, formal-consumer,
  and all-six-section-border direction contracts. Full adoption by the
  remaining inherited mesher branches remains the explicit 玖轮 task.
- The exact-jar full test suite passed, followed by clean Embeddium-only and
  Oculus 1.8.0 plus Complementary Unbound client exits. User visual inspection
  found no missing faces or LOD seam regression in either route. The teardown
  summaries reported 1476/1047/429 and 1488/1053/435 mapped/unique/deduplicated
  model counts respectively.

### 捌.4 implementation record (2026-07-20)

- Original-source tracing confirmed that the existing double-sided mechanism is
  already a real geometry representation, not merely a heuristic flag: opaque
  directional faces occupy six camera-facing command buckets, while a thin
  model's existing quads move once into the shared double-sided bucket. The
  renderer does not synthesize or duplicate a hidden back face.
- `DoubleSidedClassification` now makes the original missing-opposite-axis rule
  explicit as a present-face mask plus a missing-axis mask. A fully empty model
  no longer becomes spuriously double-sided; this covers air and the intentional
  custom-renderer-empty route without changing any visible face.
- Deterministic post-raster fixtures cover a one-face vine, two-face corner glow
  lichen, four-face crossed plant, six-direction iron bars, six-direction glass
  pane, and the empty model. They lock face counts, metadata, translucent
  precedence, mesher buffer typing, and the active cmdgen double-sided range.
- The later original TODO proposing complementary-face merging was deliberately
  not implemented: one retained face cannot yet prove equivalence for
  side-specific lighting, normal direction, shaderpack material ids, and face
  ownership. Keeping the already single-copy command representation is the
  only fixture-proven lossless choice; any future merge needs a wider per-face
  contract rather than another global guess.
- Forge already classifies custom-rendered/block-entity models explicitly as
  intentional empty voxel models, while a missing baked model remains fatal;
  `ForgeSoftwareModelTextureBakeryTest` locks that boundary. No synthetic cube
  or preview geometry was introduced.

### 捌.5 qualification record (2026-07-20)

- A gated `voxy.forge.auditRound8ModelGpuUpload` diagnostic now commits the
  production upload stream and reads the live GPU resources back before the
  staging buffers are freed. It compares each selected 64-byte model record and
  all four mip levels of its 3x2 atlas tile byte for byte. The diagnostic is off
  by default and therefore adds no release-path readback or stall.
- The exact audit passed for 32 unique model records and complete mip chains in
  both the Embeddium-only client and Oculus 1.8.0 with Complementary Unbound.
  Both clients shut down normally. Their final summaries reported
  1257/913/344 and 1521/1061/460 mapped/unique/deduplicated model counts.
- User inspection across the round's repeated Embeddium-only and Oculus runs
  reported no regression in leaves, cutout/thin models, translucent materials,
  fluids, lighting/emission, biome colour, missing faces, or vanilla/LOD seams.
  Intentional custom-renderer-empty behavior is additionally locked by the
  bakery boundary test rather than replaced by synthetic geometry.
- The full exact-dependency suite passed all 227 tests. A clean `jarJar` build
  then executed `reobfJarJar` and produced the 12,676,537-byte
  `voxy-forge-0.2.17-beta-forge-all.jar`; its metadata and payload contain the
  six pinned storage/compression dependencies plus the expected Windows x64
  LWJGL ZSTD and LMDB native resources.

Exit evidence: every new metadata field has a tested consumer, existing world
data is migrated or invalidated safely, and representative model classes render
without new holes or colour drift.

## 玖轮：RenderDataFactory correctness

Goal: resolve the original 47-marker RenderDataFactory debt by behavior, not by
comment deletion. The Forge baseline retained 46 marker-bearing lines after
捌轮's exact-mask consumer closed one original note; 玖轮 resolves their
behavioral clusters and leaves no TODO/FIXME/HACK marker in the active factory.

- [x] Split the debt into independent fixtures: same-model culling, self-
      occlusion, neighbor-face occlusion, section-border faces, opaque masks,
      translucent geometry, self lighting, and incremental-run counts.
- [x] Capture the current original output for ordinary opaque cubes before any
      algorithm change.
- [x] Define face ownership at all six section borders so adjacent rebuild order
      cannot create gaps or duplicate quads.
- [x] Use model occlusion masks from 捌轮 instead of guessing solely from
      ids or average depth.
- [x] Carry correct light for merged runs, forward/backward faces, self-lit
      models, and cross-section neighbors.
- [x] Replace overflow and end-of-run hack fixes with checked invariants.
- [x] Add randomized voxel-volume differential tests and explicit stained-glass,
      water, leaves, slab, stair, and thin-model fixtures.
- [x] Stress concurrent dirty/remesh events and shaderpack toggles.

### 玖轮 preparation record (2026-07-20)

- Current CodeGraph tracing confirms that the package-local Forge
  `RenderDataFactory` is the active per-worker formal owner constructed by
  `RenderGenerationService`, not the retired legacy geometry island described
  by an older audit.
- The Forge owner has 46 TODO/FIXME-bearing lines versus 47 in the original tree
  copy. They collapse into retry/reset, shared face decision, six-direction
  ownership, translucent/self-occlusion, fluid overlay, lighting, X skip
  bookkeeping, and capacity/bounds clusters; repeated axis comments are not
  counted as separate debts.
- `Round9OpaqueMeshingBaselineTest` directly exercises the formal owner's native
  buckets. An isolated interior cube and an interior 16-cubed volume both emit
  exactly six directional quads; the latter greedily merges to one 16x16 quad
  per face. Exact snapshot hashes are recorded in the preparation document.
- No production behavior changed during preparation. At that checkpoint the
  border, material, light, randomized, retry, and real-client gates remained
  open under 玖.1 through 玖.7; the completion record below supersedes that
  checkpoint status.

### 玖轮 implementation and qualification record (2026-07-20)

- `RenderFaceDecision` is now the single allocation-free decision contract for
  opaque, non-opaque, fluid, inner, outer, and all-six-direction paths. It uses
  捌轮's exact face masks for same-model and exact-neighbour coverage, retains
  the original coarse opaque-neighbour rule where applicable, and selects self
  or neighbour light through the same result.
- Every section border now follows one touching-face convention. Same-fluid
  boundaries are hidden, different fluids keep both material faces, and
  contained fluids resolve and emit the actual fluid model instead of the base
  block model.
- X scanning uses one `lastProcessedZ[32]` value per row. The inherited five-bit
  skip counters and `z == 30`/`skip(31)` end repair are gone; exact 30/31/32
  runs, sparse boundary rows, and three seeded 32-cubed volumes decode back to
  the expected cells and all six faces.
- A bucket has 65,536 allocated slots but its packed GPU count can represent at
  most 65,535 quads. The producer now rejects the 65,536th quad before its
  native write, verifies aggregate counts and AABBs, and recovers correctly
  across success, missing-model retry, failure, empty, and reuse sequences.
- The exact dependency suite passed 68 suites / 244 tests with no failures,
  errors, or skips. A clean `jarJar` produced
  `build/libs/voxy-forge-0.2.17-beta-forge-all.jar`.
- Automatic clients passed for Embeddium-only and Oculus 1.8.0 with
  Complementary Unbound. Oculus was disabled and re-enabled in-session to force
  renderer teardown/recreation. The broader Embeddium + Oculus + Acedium +
  Vivecraft + Bobby Reforged + Chunky combination then passed a 6 -> 26 -> 6
  render-distance cycle, forcing bulk chunk load, unload, and remesh work before
  a normal save and shutdown. Distant Horizons remains excluded by the explicit
  simultaneous-mod rejection policy.

Exit evidence: no vanilla/LOD seams, holes, duplicate geometry, translucent
border loss, or lighting regressions in automated fixtures and real visual runs.

## 拾轮：GPU visibility, shaders, and multi-view ownership

Goal: harden GPU bounds and make every viewport own all state that can differ
between eyes, mirrors, cameras, or temporal histories.

- [x] Add bounded writes and overflow telemetry for traversal, render-list,
      command, translucent, and cleaner queues.
- [x] Replace hard-coded top-level LOD and unexplained binding numbers with
      validated layout owners.
- [x] Determine whether MDIC uniform and translucent-distance buffers must move
      into `MDICViewport`; test two interleaved viewports before changing layout.
- [x] Resolve block-model positional error, merged-alpha behavior, tint encoding,
      and derivative inputs with shader/CPU golden fixtures.
- [x] Specify reverse-Z ownership end to end before changing depth conversion.
- [x] Repair SSAO/depth behavior only after ground-truth texture probes isolate
      the failing stage.
- [x] Recheck no-Oculus, Oculus with several packs, Acedium, windowed Vivecraft,
      and shaderpack switching.
- [x] Keep physical Vivecraft eye/mirror qualification open until real VR
      hardware is available.

Exit evidence: queue overflow fails closed or degrades explicitly, independent
viewports cannot contaminate one another, and all available visual matrices pass.

### 拾轮 preparation record (2026-09-03)

Preparation and implementation are complete; available formal-client qualification
completed on 2026-09-05. The preparation capacities,
original/Forge owner comparison, guards, and fixture requirements are recorded in
[the 拾轮 preparation document](forxy-round10-gpu-visibility-shader-multiview-preparation.md).
Current changes, verification results, and remaining gates are in
[the execution record](forxy-round10-execution-record.md).

Planned sequence: 拾.1 layout/capacity baselines; 拾.2 HOC/request/render-list
bounds; 拾.3 draw/translucent/cleaner index safety; 拾.4 evidence-led multi-view
ownership; 拾.5 shader maths/materials; 拾.6 depth/HiZ/SSAO; 拾.7 formal runtime
qualification. Shared MDIC buffers are an inherited design question, not proof
of current cross-view corruption: the active Forge path executes whole frames
serially and rejects reentrancy. Cleaner tagged-index risks need focused tests,
while its fixed 256-item output must not be described as an append queue.

SQLite stays optional and external by user policy. The old GL 1282 now has a
direct Oculus depth-copy stack, but no complete cross-mod A/B attribution.
The user paused that external investigation on 2026-09-03. Computer Use was
re-authorized on 2026-09-05 and the available runtime matrix is now complete;
the old depth-copy A/B remains separately deferred. NodeManager transition/deletion
semantics stay in 拾壹轮.

### 拾轮 completion record (2026-09-05)

328 tests passed, including 12 actual GPU cases, release packaging, exact Bobby
artifact and read-only Bobby/DH data. Normal-capacity Embeddium-only and full
optional-mod clients passed visible terrain, shader switching, resize, dimension
changes, resource reload and re-entry. Closing Song's original and new-area
8→31→8 reproduction remained complete. The discovered Acedium non-resident
CPU-buffer release is guarded only for that optional owner; its GPU failure
control and real-client teardown regression passed. Chunky's default skip of
existing chunks is documented separately from missing ingested geometry.

See [final runtime evidence and limits](forxy-round10-final-runtime-qualification-2026-09-05.md).
Physical VR remains untested. BSL10.1.3 has a separate endFlashIntensity uniform
limitation; Unbound, Reimagined and Photon provide the actual three-pack terrain
matrix. This is not a claim that every shaderpack feature or every mod combination
is supported. SQLite remains external and optional.

## 拾壹轮：NodeManager and HOC state machine

Execution status (2026-09-09): **complete for the scoped round: implementation,
418 automated tests, dev and four-modpack scenarios, performance comparison and environment restoration** from
`6166df430`. See the current [execution record](forxy-round11-execution-record.md).
The Chinese [拾壹轮 preparation](forxy-round11-node-hoc-state-machine-preparation.md)
records the active/original owners, packed-state contract, existing partial
transitions, seven execution stages and evidence gates. Its original preparation
snapshot is not completion evidence; the execution record supplies the completed gates and their limits.

The r5 four-modpack baseline is accepted only for its tested configurations.
Closing Song's two format-copy GL1282 messages now have a Voxy-present/absent
attribution in [the investigation](forxy-closing-song-gl1282-attribution-2026-09-08.md);
they do not justify a node-state or depth-format rewrite. This does not generalize
the result to the older, separately recorded Reverse Future/Oculus combination.

Goal: replace the original author-marked hierarchy uncertainty with an explicit,
verified transition system.

- [x] Write a state model for request, leaf, inner, empty, geometry-in-flight,
      request-in-flight, top-level, and sentinel combinations.
- [x] Encode legal transitions and ownership of every node id, request id,
      geometry id, watcher entry, cleaner entry, and active-position entry.
- [x] Characterize the author-marked top-level-empty case: the existing zero-mask
      wait and later child update already work; preserve them with all-bit and
      cancellation regressions instead of inventing a new failure.
- [x] Remove assumptions that every request is a child request; validate type and
      position before mutation.
- [x] Verify leaf-to-inner and inner-to-leaf transitions, recomputing each affected
      direct parent's `AllChildrenAreLeaf`; it does not mean all descendants are leaves.
- [x] Define inner-node child-existence zero behavior instead of warning and
      continuing with ambiguous state.
- [x] Resolve geometry removal while generation/upload is in flight.
- [x] Define CPU-cache versus GPU-free/rebuild ownership around recursive deletion;
      retain the existing bounded CPU cache and do not add an unused GPU-download stub.
- [x] Add model-based transition tests, randomized operation sequences, invariant
      verification after every step, and deterministic concurrency schedules.
- [x] Stress rapid spectator movement, Chunky generation, Bobby/DH import,
      dimension changes, disconnect/re-entry, shaderpack rebuilds, and shutdown.
- [x] Treat any `child change not in active map`, negative work count, stale
      request, invalid sentinel, or `inner child existence -> 0` warning as a
      failed gate until classified by a tested transition.

Exit evidence: long randomized and real-world runs preserve all state invariants,
produce no unclassified hierarchy warnings, and show no persistent or reproducible LOD holes.

Original TODOs are individually classified in the [disposition table](forxy-round11-node-todo-dispositions-2026-09-09.md).
Permitted duplicate/in-flight GPU requests are explicitly classified, not treated
as corruption or silently suppressed to make the log empty. See the [four-pack
log audit](forxy-round11-modpack-log-audit.md) for nine zero-resource stop snapshots
and external/known-message boundaries. Final R2 CPU comparison uses actual fastutil
8.5.9 and warmed isolated owners; it is not a client-frame-rate benchmark. The
[real-client A/B](forxy-round11-client-ab-performance-audit.md) uses matching
instrumentation and initial state: worker batch p95 0.7280->0.9029 ms, publication
p95 0.3794->0.4848 ms. These observed increases are retained, not called performance
parity; a single asynchronous trajectory pair does not quantify FPS regression.
Same-size replacement improved in the isolated CPU test, while size churn costs
more. Mesh rebuild totals and instantaneous whole-game resource peaks were not
directly instrumented; observed event/cache/resource ledgers are not substitutes.

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
- Animated Vanilla/LOD boundary transitions are a Forxy-specific optional
  enhancement because original Voxy explicitly cancels Sodium chunk fading.
  Their separate, non-gating TODO and safety contract live in
  `forxy-deferred-enhancement-todos.md`.

## Progress summary

Compatibility integration (2026-09-03): Forge rounds XXXVII and XXXVIII were
merged into Forxy without replacing its existing debt-repayment changes. The
combined default build/test gate passes 70 suites / 253 tests and reobfuscated
JarJar. Two user modpack visual smokes passed; the optional-library release
policy test also passed. SQLite is deliberately not bundled, while the GL
attribution and targeted runtime matrix remain open. See the
[XXXIX merge and quick-audit record](forxy-xxxix-compatibility-merge-audit-2026-09-03.md).
This integration does not complete or renumber any roadmap round below.
拾轮 code, automated validation and available visible-client matrix are complete.
See the execution/final-runtime records for hardware and external diagnostic limits.

- [x] 壹轮：native ZSTD result validation
- [x] 贰轮：Mapper snapshot and lock safety
- [x] 叁轮：optional storage position iteration
- [x] 肆轮：service cancellation accounting
- [x] 伍轮：storage recovery and format versioning
- [x] 陆轮：cache, allocator, and worker efficiency
- [ ] 柒轮：level-aware mipping
- [x] 捌轮：model and material fidelity
- [x] 玖轮：RenderDataFactory correctness
- [x] 拾轮：GPU visibility, shaders, and multi-view ownership
- [x] 拾壹轮：NodeManager and HOC state machine

拾壹轮 is complete (2026-09-09) within the documented scenarios and measurement limits;
the tested R2 artifact and restored original environments are recorded, without an automatic Git commit/push.
柒轮's dedicated runtime-cost/visual gates remain open as recorded in its section,
not automatically completed by later general modpack smoke tests.
