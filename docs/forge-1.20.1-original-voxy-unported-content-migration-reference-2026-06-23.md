# Forge 1.20.1 original Voxy source-area migration reference, 2026-06-23

This document began as the post-bugfix inventory of missing, partial, and
parity-unproven source areas. It now preserves that historical rationale while
the reconciliation table and per-section status blocks record the completed
Forge 1.20.1 port. The current controlling audit is:

```text
docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md
```

## Post-XXI authoritative completion rounds, reconciled 2026-07-16

The per-subsystem `partial` labels below are a 2026-06-23 snapshot. They must
not be read as current after XII-XXI.6: the formal renderer, model bakery,
generation/geometry chain, HOC/MDIC frame route, terrain/shaderpack pipeline,
water/translucency, resource reuse, persistent storage TYPE surface, and
identifier-routed active-world map have since been ported and runtime-validated.
XXII reconciled every old label against current source. XXV reopened the XXIV
closure for a stricter all-content, retired-code, exact-name, and final-artifact
audit. Its broad visual/lifecycle acceptance passed. Two frontend gaps found by
that acceptance are now implemented (F3 visibility and Voxy pages inside
Embeddium), and the refreshed artifact audit passes. The focused F3/menu
observation and the follow-up Rendering off/on config-Apply lifecycle also
passed. Current XXV acceptance is closed with documented platform adaptations.

Work was grouped into XXII-XXIV, then XXV was added as one exhaustive closure
round so its complete implementation can receive one consolidated manual
regression before a single commit.

### Authoritative twelve-section reconciliation at XXII

| Historical section | Current classification | Evidence / remaining owner |
| --- | --- | --- |
| 1. Instance, storage, service root | core parity complete | XXI.1-XXII ported storage and world ownership; XXVI replaced the process-lifetime service owners with one original-equivalent `SessionRuntime` per network connection |
| 2. VoxyRenderSystem outer owner | renderer parity complete | XII-XX.7 formal owner and live readiness evidence; XXI.6 lifecycle routing |
| 3. ModelBakerySubsystem | renderer parity complete | original model factory/store/software bakery path, including the XX.4 transparent-model repair |
| 4. Render generation and geometry | renderer parity complete | original service, BuiltSection, async geometry manager/data, upload, and resource reuse chain |
| 5. RenderResourceReuse | renderer parity complete | XX.5 rebuild reuse and XX.7 terminal clear semantics |
| 6. Viewport/HOC/MDIC | renderer parity complete | original traversal, cmdgen, MDIC draws, frame ordering, and current-lifecycle readiness evidence |
| 7. Shaderpack/render pipeline | Oculus adaptation complete | original Iris contract mapped to Oculus and broadly shaderpack-regressed; IterationT is absent upstream and remains a post-parity TODO |
| 8. Terrain shader contract | renderer parity complete | original packed attributes, patched/normal program defines, light/tint/depth, and state restore |
| 9. Water/translucency | renderer parity complete | original bake/metadata/translucent command route; Photon water regression passed |
| 10. Ingest/removal/light | core parity complete in XXII | original `LIGHT_AND_DATA` readiness, dirty-section route, and last-loaded removal snapshot |
| 11. Compatibility integrations | implementation complete; F3/menu observation passed | DH/Bobby Reforged import and unload timing, Chunky, Acedium/Nvidium, GPU selection, Embeddium/Oculus, and applicable original debug/timing owners are ported; genuinely Fabric-only hooks remain platform-N/A |
| 12. Config/readiness/cleanup | frontend corrections/artifact/runtime acceptance complete | parity/status DTOs and commands are removed and exact names are converged; F3 follows debug-screen visibility, the nine original settings are hosted by Embeddium, the standalone config screen is retired, and XXVI closed enabled-instance lifecycle parity |

The detailed bodies below began as the 2026-06-23 source inventory and migration
rationale. XXIX.1 reconciles their per-section status with XXVI-XXVIII. Historical
plans are retained only where explicitly labelled historical or superseded;
current-state prose must not contradict the table above.

### XXII core non-renderer parity closure

```text
[x] reconcile all twelve historical inventory sections against XII-XXI.6
[x] port original chunk-remove last-loaded snapshot semantics to Embeddium 1.20.1
[x] unify chunk add/remove, dirty-section, deferred-light retry, and detach ownership
[x] port original enabled/rendering/ingest/SSAO/config semantics and defaults
[x] make a clean Forge config run the formal route without historical switches
[x] remove unused CPU/BuiltSection/GPU/MDIC prototype config and runtime surfaces
[x] rename active ForgeCpuMeshLayer without deleting the live model-bakery enum
[x] correct stale status targets and whole-mod parity evidence
[x] validate server identity isolation and corrupt-section deletion/recovery
[x] compile/build and prepare one fresh/existing-config, movement, dimension,
    reload, logout, shaderpack, storage, and multiplayer regression
```

### XXIII original user features and importers

```text
[x] port ImportManager and client import task/reference lifecycle
[x] port current-world, named-world, raw-region, ZIP, cancel, and progress UI
[x] port /voxy reload and applicable original diagnostics/F3 statistics
[x] expose the original settings through Forge (the initial standalone screen
    was superseded in XXV by official Embeddium option pages)
[x] port the original importer/config/lifecycle surface available in XXIII
[x] classify then-known Fabric-only hooks without creating fake integrations
[x] perform one consolidated importer/config/lifecycle/render smoke regression,
    including the focused no-shader cherry-leaves repair recheck
```

### XXIV final whole-mod audit and release regression

```text
[x] classify every original source area as ported, Forge-adapted, platform-N/A,
    upstream-incomplete, or genuinely missing
[x] remove final preview/skeleton/status wording and update all active docs
[x] verify mixins, dependency bounds, native libraries, clean config, and final JAR
[x] run the complete no-shader/multi-shader/dimension/reload/relog/multiplayer/
    storage/importer/shutdown regression once
[x] after user approval, flip wholeOriginalModParity and verify status/build
```

Accepted upstream behavior and platform-N/A integrations are not completion
blockers. IterationT remains a post-parity compatibility TODO because original
Voxy has no adaptation for it.

### XXV exhaustive original-content and artifact re-audit

```text
[x] inventory every original source area and eligible Forge equivalent again
[x] port DH and Bobby import, Chunky, Acedium, GPU selection, original
    GL/printf/timing/capability owners, and formal F3 data where applicable
[x] remove platform/status/probe shells and converge direct ports to original
    simple owner names
[x] restore production Mixin refmap/reobfuscated JarJar packaging
[x] complete the first broad visual/lifecycle acceptance
[x] gate F3 diagnostics and move all nine settings into Embeddium pages
[x] refresh the final source/resource/JAR audit after the page-ID correction
[x] pass the focused F3-visibility and Embeddium-page runtime observation
[x] pass the focused config-Apply runtime acceptance
```

For the completed portion, the user ran the instructed F3/menu check and closed
normally without reporting an anomaly. `latest.log` records Voxy page
registration at `20:20:20.204`, Embeddium option-GUI construction at
`20:21:07`, no Voxy option-identifier/initializer failure, renderer shutdown at
`20:21:37.485`, and normal `WorldEngine`/instance closure at `20:21:39`. The
The follow-up Apply run supplied the remaining evidence: Rendering off completed
Voxy shutdown plus Oculus recreation at `20:33:39-20:33:40`; Rendering on was
persisted at `20:34:13` and recreated Oculus, `ForgeOriginalVoxyRenderSystem`,
and `MDICSectionRenderer` by `20:34:16`. The user confirmed LOD disappeared and
returned without an anomaly. Clean shutdown followed at `20:34:22-20:34:23`,
`runClient` exited 0 with `BUILD SUCCESSFUL`, and the targeted scan found no
Voxy option/config failure.

The direct DH database importer is an original feature, not an unsupported API
guess: it remains optional, selects the real DH SQLite data, decodes supported
DH formats/compression, and feeds the original import manager/WorldEngine
lifecycle. Bobby Reforged retains `modId="bobby"` and the `.bobby` cache, so
Forge ports both the real cache format/importer and the original split unload
timing. Acedium is the Forge Nvidium fork, declares both `acedium` and
compatibility `nvidium` entries, and retains the
`me.cortex.nvidium.RenderPipeline` owner; only unrelated Fabric-only
Flashback/FREX hooks remain platform-N/A. Fabric ModMenu itself is N/A, but its user-visible
Sodium-options responsibility is mapped: the Forge Mod List entry opens
Embeddium and Voxy adds pages through the official construction event.

### XXIV authoritative final source-area classification

This table supersedes every dated `partial`, `pending`, or suggested-round block
in the historical inventory bodies below.

| Original source area | Final classification | Forge owner / decision |
| --- | --- | --- |
| Instance, world engine, storage, service root | ported + Forge-adapted | original `WorldEngine`, mapper, save/ingest services, storage TYPE registry, identity-keyed active-world map, and idle/shutdown order; Forge supplies login/dimension/game-shutdown event routing |
| `VoxyRenderSystem` outer lifecycle | ported + Forge-adapted | `ForgeOriginalVoxyRenderSystem` owns the original renderer chain; the Embeddium cutout Mixin is only the Forge frontend entry adapter |
| Model bakery/factory/store/software bake | ported + Forge-adapted | original ownership, queue, atlas/model layout, dedupe, biome colour, and upload/readback contracts; the adapter maps Minecraft 1.20.1/Embeddium baked-model APIs |
| Render generation, `BuiltSection`, async geometry | ported | original generation service/thread policy, render-data contract, allocation, metadata, sparse upload/removal, and node geometry synchronization |
| `RenderResourceReuse` and GL lifetime | ported | geometry/model resources survive owner rebuilds and are deleted at terminal instance shutdown in original order |
| Render distance, viewport, HiZ, HOC, MDIC | ported + Forge-adapted | original tracker/traversal/cmdgen/draw owners and frame rhythm; Forge restores external GL state because entry occurs inside Embeddium |
| Iris shaderpack/render pipeline | Forge-adapted | original Iris patch/data/target/uniform/sampler/image/TAA contract is mapped to the real Oculus 1.20.1 equivalents |
| Terrain shaders and visual contract | ported + Forge-adapted | original resources, packed attributes, stage defines, tint/light/depth semantics, normal/patched paths, SSAO and final blit; frontend binding signatures are Oculus/Forge-specific |
| Water and translucent route | ported | original fluid bake/metadata, translucent command construction, blend/depth targets, and draws; Photon regression passed |
| Chunk ingest, removal, and light | ported + Forge-adapted | original readiness/ingest/dirty/remove contracts use Embeddium chunk events plus the Minecraft client chunk-cache snapshot required on Forge 1.20.1 |
| Importers, reload, diagnostics, F3, config UI | ported + Forge-adapted; focused runtime acceptance passed | original import manager/world importer/progress lifecycle and reload behavior; F3 visibility follows the debug screen; Forge opens Embeddium and contributes the original-shaped General/Rendering pages through its official event; Rendering off/on saved-option effects passed runtime qualification |
| Optional compatibility integrations | ported where a real format/Forge owner exists; otherwise platform-N/A | DH, Bobby Reforged (`bobby`), Chunky, Acedium (`acedium`, with compatibility `nvidium` entry), GPU selection, Embeddium and Oculus are ported; Flashback/FREX remain N/A; Fabric ModMenu is N/A but its option-host behavior is mapped to Forge/Embeddium |
| Known upstream limitations | upstream-incomplete | original HOC/request bookkeeping retains its audited author `FIXTHIS` behavior; IterationT has no original Voxy sidecar/adaptation and remains post-parity compatibility work |
| Genuinely missing original source area | none found in XXV inventory | every area has an active owner, a documented Forge adaptation, or an explicit platform/upstream classification; focused frontend runtime qualification passed |

Use this document after the confirmed visual/runtime bugs are fixed, especially
after black LoD terrain, missing water/translucency, reload churn, and new-world
startup crashes are no longer active blockers.

## Controlling baseline

```text
original Voxy source is the baseline
```

The target formal chain remains:

```text
WorldEngine / WorldSection / Mapper
 -> ModelBakerySubsystem
 -> ModelFactory
 -> SoftwareModelTextureBakery / TextureUtils / ModelQueries
 -> ModelStore
 -> RenderGenerationService
 -> RenderDataFactory
 -> BuiltSection
 -> BasicAsyncGeometryManager
 -> BasicSectionGeometryData
 -> RenderDistanceTracker
 -> HierarchicalOcclusionTraverser
 -> ViewportSelector / Viewport
 -> MDICViewport
 -> cmdgen.comp
 -> MDICSectionRenderer
 -> original terrain shader contract
```

## Historical status labels used in retained inventory blocks

```text
missing
    Original Voxy has a formal owner or behavior and Forge has no equivalent
    formal owner yet.

partial
    Forge has code in this area, but ownership, data flow, lifecycle, or output
    still differs from original Voxy.

parity-unproven
    Forge has an intended equivalent, but it has not been proven against the
    original source and real runtime output.

platform-blocked
    The original mechanism depends on Fabric/Sodium/Iris-only APIs and needs a
    documented Forge/Oculus/Embeddium equivalent or a documented blocker.
```

## Current high-level state

The original owner chain, storage/lifecycle root, config, importers, and user
features have active Forge owners. XXVI closed per-session instance ownership,
XXVII completed the line-by-line source audit, and XXVIII completed the formal
Embeddium 0.3.31 and Chunky compatibility repairs. The project is beta-complete.
IterationT stays a post-migration TODO because the original project has no
adaptation for it.

## 1. Instance, world storage, and service root

Status after XXII implementation and completed runtime regression:

```text
core parity complete: original storage/config TYPE inventory, identity-keyed
active-world ownership, idle/shutdown lifecycle, identity isolation, and
corrupt-section deletion/recovery are ported and automatically validated
```

Original Voxy source areas:

```text
src/main/java/me/cortex/voxy/client/VoxyClientInstance.java
src/main/java/me/cortex/voxy/commonImpl/VoxyInstance.java
src/main/java/me/cortex/voxy/common/StorageConfigUtil.java
src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java
src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java
```

Current Forge state:

```text
src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java
```

The active Forge world route now uses original Voxy's default persistent
`SectionSerializationStorage -> ZSTD(level 1) -> RocksDBStorageBackend` chain,
with original world identity/path hashing, Mapper id persistence, idle-world
reclaim, saving/ingest services, and shutdown ordering. Two separate client
processes loaded the same database; the XXI.1 restart run loaded 448 mappings
and recorded 5,676 stored-section hits. XXI.2 ports original `StorageConfigUtil`
creation/reload semantics, original `ConfigBuildCtx`, and the exact polymorphic
TYPE JSON for Serializer, CompressionAdaptor, ZSTD, and RocksDB. Its reload run
loaded 1,384 mappings and recorded 5,989 stored-section hits. XXI.3 adds LZ4,
BasicPathConfig, explicit/automatic fragmentation, and exact registrations for
the upstream-incomplete ConditionalConfig/ReadonlyCachingLayer paths. XXI.4
ports original LMDB transactions/backend/native packaging and validates restart
recovery with 386 mappings and 3,037 stored-section hits. XXI.5 ports Redis with
Jedis 5.1.0, validates 382 mapping reloads and 2,814 section hits against an
isolated local service, and closes the emitted storage TYPE inventory.

XXVI lifecycle closure:

```text
- one SessionRuntime owns the unified service pool, ingest/saving services,
  imports, active-world map, cleaner, storage session, and renderer per network
  connection
- logout or enabled=false tears down that complete owner before a later session
  or enable creates a replacement
- the process-scoped Forge event shell contains no service pool, WorldEngine, or
  render owner
```

XXI.1-XXI.5 completed:

```text
- read original instance/storage ownership through shutdown
- original single-player, multiplayer, Realms, and unknown fallback base paths
- original world identifier and SHA-256 directory mapping
- original default Serializer/ZSTD/RocksDB chain
- Mapper id persistence and stored-section reads
- same-world closing-owner reuse
- saving/ingest service and terminal flush/close ordering
- no active formal-route MemoryStorageBackend construction
- dimension-isolated RocksDB paths and idle close/reopen
- rapid logout/login reuse without a RocksDB LOCK conflict
- StorageConfigUtil-compatible config.json creation, validation, fallback, and
  reload
- original ConfigBuildCtx token/path construction
- exact Serializer/CompressionAdaptor/ZSTD/RocksDB polymorphic TYPE names and
  JSON shape
- storage disabled gate before active-world creation
- LZ4 compressor binary format and lz4-java 1.8.0 packaging
- BasicPathConfig, FragmentationAdaptor, and AutoFragmentationAdaptor
- fragment hash routing, Mapper-id replication/recovery, child flush/close
- exact ConditionalConfig and ReadonlyCachingLayer upstream-incomplete behavior
- LZMA2 excluded by ground truth: its entire original implementation is commented
  out and does not produce a config TYPE
- original LMDBInterface/Cursor/transaction wrapper classes
- LMDB world_sections/id_mapping databases, resize locking, flush, and close
- LWJGL LMDB 3.3.1 plus official Windows/Linux x64 native packaging
- isolated LMDB restart recovery; original iteratePositions remains upstream
  unimplemented
- Redis hash/key/value format, prefix substitution, Jedis pool lifecycle, and
  original upstream-unimplemented iteratePositions behavior
- Jedis 5.1.0 and commons-pool2 2.12.0 packaging
- isolated Redis two-JVM recovery and direct server-side hash-count verification
```

Completed follow-up validation:

```text
- multiplayer identity isolation and corrupted-section deletion/recovery passed
- dimension isolation, relog, and shutdown passed
- default, LZ4, fragmented, LMDB, and Redis persistence/restart paths passed
- XXVI disabled/re-enabled and reconnect ownership tests passed
```

XXI.1-XXI.5 validation:

```text
rtk test .\gradlew compileJava
new world -> generate LoD -> leave world -> re-enter same world
confirmed storage files/id mappings are reused rather than regenerated from RAM
first config run created the exact production TYPE JSON
second JVM reported storageConfigSource=loaded and reused the same database
isolated LZ4 explicit-fragment run wrote four RocksDB databases
AutoFragmentationAdaptor restart loaded 384 mappings and hit 4,925 sections
original ZSTD config restored byte-for-byte after the isolated test
isolated LMDB run wrote data.mdb/lock.mdb and 386 mappings
second LMDB JVM loaded all 386 mappings and hit 3,037 stored sections
original ZSTD config restored byte-for-byte after the LMDB test
isolated Redis first run created 4,432 section fields and 382 mappings
second Redis JVM loaded all 382 mappings and hit 2,814 stored sections
temporary Redis shut down normally; original config restored byte-for-byte
```

## 2. Full VoxyRenderSystem outer lifecycle owner

Status after XX.7 / XXI.6 reconciliation:

```text
renderer parity complete
```

Original Voxy source area:

```text
src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java
```

Current Forge state:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
```

Forge has many original-shaped resources, but they are still owned by a Forge
pipeline object called from the Embeddium hook. Original Voxy has a single
`VoxyRenderSystem` owner that acquires the world reference, constructs the model
service, render generation service, geometry data, async node manager, node
cleaner, traversal, render pipeline, section renderer, viewport selector, render
distance tracker, and chunk-bound renderer, then frees them in a specific order.

Historical pre-completion gaps (superseded):

```text
- a Forge-side owner equivalent to VoxyRenderSystem
- constructor rollback semantics when resource creation fails
- world acquireRef at owner creation and releaseRef at final shutdown
- exact callback ownership for dirty sections, biome ids, and block-state ids
- original renderOpaque frame order as the top-level render entry
- original post-frame/dynamic-work rhythm
- original shutdown order including DownloadStream flush boundaries
- setRenderDistance/getViewport/addDebugInfo ownership under the render owner
```

Historical migration steps (completed):

```text
1. Re-read original VoxyRenderSystem constructor, renderOpaque, getViewport,
   setRenderDistance, addDebugInfo, frexStillHasWork, and shutdown.
2. Introduce a ForgeOriginalVoxyRenderSystem owner, or rename/refactor the
   current pipeline only if the resulting ownership matches the original.
3. Move resource construction under that owner in original order:
   - ModelBakerySubsystem equivalent
   - RenderGenerationService
   - BasicSectionGeometryData
   - async node manager / sync bridge
   - NodeCleaner
   - HierarchicalOcclusionTraverser
   - RenderPipelineFactory-equivalent pipeline creation
   - pipeline.setupExtraModelBakeryData(...)
   - traversal late-stage compile
   - MDICSectionRenderer
   - ViewportSelector
   - RenderDistanceTracker
   - ChunkBoundRenderer.
4. Make the Embeddium hook an adapter only. It should locate the current render
   owner and call its render method; it should not own renderer resources.
5. Add lifecycle generation guards for queued render-thread start and cleanup
   tasks so stale tasks cannot mutate a newer owner.
6. Port shutdown order:
   - flush DownloadStream
   - detach WorldEngine and Mapper callbacks
   - stop async node work
   - shut down model and render-generation services
   - free traversal, node cleaner, geometry data, chunk-bound renderer,
     viewport selector
   - return reusable buffers
   - free render pipeline
   - flush DownloadStream again
   - release world.
7. Remove or quarantine any lifecycle logic that still treats the Embeddium hook
   adapter as the owner.
8. Compile, then compare the new lifecycle against original VoxyRenderSystem
   before running the client.
```

Validation:

```text
rtk test .\gradlew compileJava
world enter -> move -> reload resources -> leave world -> enter another world
confirm no stale queued start/free task mutates the wrong owner
```

## 3. ModelBakerySubsystem owner

Status after XX.4 reconciliation:

```text
renderer parity complete
```

Original Voxy source areas:

```text
src/main/java/me/cortex/voxy/client/core/model/ModelBakerySubsystem.java
src/main/java/me/cortex/voxy/client/core/model/ModelFactory.java
src/main/java/me/cortex/voxy/client/core/model/ModelStore.java
src/main/java/me/cortex/voxy/client/core/model/bakery/SoftwareModelTextureBakery.java
src/main/java/me/cortex/voxy/client/core/model/TextureUtils.java
src/main/java/me/cortex/voxy/client/core/model/ModelQueries.java
```

Current Forge state:

```text
src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java
src/main/java/me/cortex/voxy/forge/ModelFactory.java
src/main/java/me/cortex/voxy/forge/ModelStore.java
src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java
```

The original-shaped `ModelBakerySubsystem` owns the Forge model factory, store,
software bakery, processing thread, upload lifecycle, and custom model
classification. The placeholder model-store family is absent from active source.

Historical pre-completion gaps (superseded):

```text
- ModelBakerySubsystem as the owner of ModelStore and ModelFactory
- original processing thread behavior and exception propagation
- requestBlockBake dedupe/locking behavior as the formal block-state bake path
- mapper biome/state callbacks routed into the model subsystem owner
- tick(totalBudget) upload semantics under the render owner
- shutdown of processing thread, factory, and store in original order
- proof that Forge fluid, tint, material, and atlas records match original data
```

Historical migration steps (completed):

```text
1. Compare original ModelBakerySubsystem against the current Forge model service
   field by field and method by method.
2. Either port ModelBakerySubsystem directly or make the Forge wrapper preserve
   the same public owner contract:
   - getStore()
   - requestBlockBake(int)
   - addBiome(...)
   - tick(long)
   - areQueuesEmpty()
   - getProcessingCount()
   - shutdown().
3. Route Mapper block-state and biome callbacks through this owner only.
4. Remove placeholder model records from the formal route; if old command/status
   code still compiles through them, mark it historical and isolate it.
5. Re-audit Forge model records against original ModelStore.MODEL_SIZE,
   ModelQueries flags, fluid-state LUT, biome color buffer, atlas texture, and
   sampler state.
6. Add runtime status that distinguishes:
   - owner ready
   - model store allocated
   - real model records uploaded
   - placeholder/historical route unavailable.
7. Compile and audit every call site that previously reached the split Forge
   model service directly.
```

Validation:

```text
rtk test .\gradlew compileJava
resource reload -> model rebake -> move into new terrain
confirm no placeholder model ids are used in formal draw calls
```

## 4. Render generation, built sections, and geometry upload chain

Status after XX.7 reconciliation:

```text
renderer parity complete
```

Original Voxy source areas:

```text
src/main/java/me/cortex/voxy/client/core/rendering/building/RenderGenerationService.java
src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java
src/main/java/me/cortex/voxy/client/core/rendering/building/BuiltSection.java
src/main/java/me/cortex/voxy/client/core/rendering/section/BasicAsyncGeometryManager.java
src/main/java/me/cortex/voxy/client/core/rendering/section/BasicSectionGeometryData.java
```

Current Forge state:

```text
src/main/java/me/cortex/voxy/forge/RenderGenerationService.java
src/main/java/me/cortex/voxy/forge/RenderDataFactory.java
src/main/java/me/cortex/voxy/forge/BuiltSection.java
src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java
src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java
```

ForgeOriginalVoxyRenderSystem constructs this complete package-local chain. The
generic class names are current formal ports, not the historical geometry/MDIC
prototype island. This area directly affects visual correctness because bad
model metadata, light bits, fluid/translucent flags, geometry buckets, or upload
metadata can make LoD terrain black, missing, overdrawn, or invisible.

Historical pre-completion gaps (superseded):

```text
- exact BuiltSection output layout for all material and face cases
- light/self-light/emission metadata parity
- fluid and translucent metadata parity
- directional face mask and culling parity for all buckets
- async geometry result merge order and stale-result rejection
- geometry capacity/headroom behavior against original
- status flags that prove uploaded valid sections, not only allocated resources
```

Historical migration steps (completed):

```text
1. Compare original RenderDataFactory and Forge RenderDataFactory for every
   output word written to BuiltSection.
2. For each metadata bit, record the original producer and current Forge
   producer:
   - face exists
   - face occlusion
   - self lighting
   - double sided
   - translucent
   - contains fluid
   - is fluid
   - biome colored
   - fully opaque
   - light emission.
3. Compare original BasicAsyncGeometryManager result acceptance, upload order,
   and stale section rejection with the Forge manager.
4. Reconcile geometry capacity/headroom logic with original behavior before
   changing constants.
5. Ensure status reports only call geometry formal-ready when at least one real
   uploaded section has valid metadata and commands can target it.
6. Compile after each field-layout or upload-order change.
```

Validation:

```text
rtk test .\gradlew compileJava
runtime movement test across new terrain
status check for top-level node, HOC requests, cmdgen dispatch, draw submission,
and post-frame work
```

## 5. RenderResourceReuse and GL resource lifetime

Status after XX.5 / XX.7 reconciliation:

```text
renderer parity complete
```

Original Voxy source area:

```text
src/main/java/me/cortex/voxy/client/core/RenderResourceReuse.java
```

Current Forge state:

```text
src/main/java/me/cortex/voxy/client/core/RenderResourceReuse.java
src/main/java/me/cortex/voxy/forge/** resource owners
```

Original Voxy reuses large GPU resources such as model-store texture atlases and
geometry buffers across renderer lifetimes. Forge has resource reuse available,
but resource ownership and clearing are still tied to the split Forge lifecycle.

Historical pre-completion gaps (superseded):

```text
- exact point where reusable resources are returned during render shutdown
- exact point where reusable resources are globally cleared during client
  instance shutdown
- guarantee that stale render-thread cleanup cannot return a buffer still owned
  by a newer renderer
- model atlas reuse ownership through the formal ModelStore owner
```

Historical migration steps (completed):

```text
1. Re-read original RenderResourceReuse and VoxyRenderSystem.shutdown together.
2. Make geometry buffer return happen only from the formal render owner during
   that owner's shutdown.
3. Make model atlas return happen only from the formal ModelStore shutdown.
4. Make global RenderResourceReuse.clearResources() happen only at instance
   shutdown after all render systems are freed.
5. Add lifecycle-generation assertions or guards around queued cleanup tasks.
6. Compile and audit reload/world-detach sequences.
```

Validation:

```text
resource reload loop
world leave/enter loop
no double-free, no stale buffer reuse, no leaked GlBuffer/GlTexture count growth
```

## 6. Viewport, HOC, MDIC, and frame rhythm

Status after XX.7 reconciliation:

```text
renderer parity complete
```

Original Voxy source areas:

```text
src/main/java/me/cortex/voxy/client/core/rendering/hierachical/HierarchicalOcclusionTraverser.java
src/main/java/me/cortex/voxy/client/core/rendering/viewport/ViewportSelector.java
src/main/java/me/cortex/voxy/client/core/rendering/viewport/MDICViewport.java
src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp
src/main/java/me/cortex/voxy/client/core/rendering/section/backend/MDICSectionRenderer.java
```

Current Forge state:

```text
src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java
src/main/java/me/cortex/voxy/forge/ViewportSelector.java
src/main/java/me/cortex/voxy/forge/MDICViewport.java
src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java
```

Forge has the original-shaped HOC/viewport/cmdgen/MDIC path, including command
generation, draw calls, frame rhythm, and render-thread shutdown. XXIV-XXVIII
runtime evidence closed the earlier shader and lifecycle uncertainty.

Historical pre-completion gaps (superseded):

```text
- original renderOpaque top-level order as the only formal frame route
- HOC request cadence while moving
- top-level node update cadence
- MDICViewport input buffer and temporal command layout parity
- cmdgen.comp barrier/readback/output layout parity under Forge GL state
- translucent command generation and draw order parity
- shadow-pass and multi-viewport behavior against Oculus/Embeddium hooks
```

Historical migration steps (completed):

```text
1. Compare original VoxyRenderSystem.renderOpaque frame order against the Forge
   frame method that Embeddium calls.
2. Move the frame order under the formal render owner before changing more GL
   state.
3. For each frame stage, record the owner and input:
   - viewport selection
   - render distance tracking
   - traversal node requests
   - geometry upload/sync merge
   - cmdgen prep/cull/buildtranslucents
   - opaque draw
   - temporal draw
   - post-opaque preparation
   - translucent draw.
4. Compare MDICViewport buffer sizes, bindings, and temporal offsets against
   original constants.
5. Keep readback/barrier audits, but do not let them become a formal success
   substitute for real visual parity.
6. Validate with movement in a real world only after compile and source audit.
```

Validation:

```text
rtk test .\gradlew compileJava
runClient movement test:
  - top-level node changes while moving
  - HOC request count changes
  - cmdgen dispatch count changes
  - draw counts change with viewpoint
  - no stale frame after world detach/reload
```

## 7. Shaderpack and render pipeline bridge

Status after XX.7 reconciliation:

```text
Oculus adaptation complete and runtime-proven for the original Iris contract
```

Original Voxy source areas:

```text
src/main/java/me/cortex/voxy/client/core/RenderPipelineFactory.java
src/main/java/me/cortex/voxy/client/core/AbstractRenderPipeline.java
src/main/java/me/cortex/voxy/client/core/IrisVoxyRenderPipeline.java
src/main/java/me/cortex/voxy/client/iris/IrisVoxyRenderPipelineData.java
src/main/java/me/cortex/voxy/client/mixin/iris/** shader patch hooks
```

Current Forge state:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java
src/main/java/me/cortex/voxy/forge/mixin/** Oculus mixins
```

The Forge/Oculus bridge remains a sensitive compatibility boundary, but the
original Iris semantic data contract is mapped and runtime-proven. It is not a
string-level shader replacement that merely compiles.

Historical pre-completion gaps (superseded):

```text
- exact original Iris pipeline data fields mapped to Oculus equivalents
- owner of terrain and translucent shader patch data
- block-state id data flow into shaderpack patching
- normal/translucent target mapping for every active shaderpack target
- lightmap, sampler, image, SSBO, and uniform binding parity
- TAA/reverse-Z/depth target behavior
- multi-target helper behavior; current status can collapse to first texture
- runtime reload consumption/versioning for Oculus reloadRequired
- proof that patched output is visually correct across shaderpacks, not only
  Complementary or one local test pack
```

Historical migration steps (completed):

```text
1. Re-read original IrisVoxyRenderPipeline, IrisVoxyRenderPipelineData, and all
   original Iris shader patch mixins.
2. Re-read Oculus 1.20.1 source in the repository for the corresponding
   ProgramSet, WorldRenderingSettings, pipeline target, uniform, and framebuffer
   owners.
3. Build a field-by-field mapping table:
   - original Iris field
   - Oculus equivalent
   - Forge bridge field
   - exact source owner
   - missing/blocker status.
4. Disable or gate any patch exposure whose required data has no proven Oculus
   equivalent.
5. Route shader patch data through the formal render pipeline owner only.
6. Fix reload handling so a shaderpack reload is consumed once, versions the
   owner, and cannot trigger repeated start/free churn.
7. Validate target bindings before draw:
   - opaque terrain target
   - translucent target
   - depth target
   - lightmap texture
   - block atlas/model texture
   - shaderpack images/samplers.
8. Test at least:
   - no shaderpack
   - Complementary
   - one additional Oculus-compatible shaderpack.
9. Document every true platform blocker instead of inventing a fake bridge.
```

Validation:

```text
rtk test .\gradlew compileJava
runClient no shaderpack
runClient shaderpack A
runClient shaderpack B
compare ordinary chunks and LoD chunks for lighting, fog, water, and target
transitions
```

## 8. Terrain shader visual contract

Status after XX.4 / XX.7 reconciliation:

```text
renderer parity complete
```

Original Voxy source areas:

```text
src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert
src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag
src/main/java/me/cortex/voxy/client/core/rendering/section/backend/MDICSectionRenderer.java
src/main/java/me/cortex/voxy/client/core/model/ModelStore.java
src/main/java/me/cortex/voxy/client/core/model/ModelQueries.java
```

Current Forge state:

```text
src/main/resources/assets/voxy/shaders/lod/gl46/**
src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java
src/main/java/me/cortex/voxy/forge/ModelStore.java
```

The complete normal/patched terrain contract is connected. The historical black
LOD, missing-water, and stage-define regressions were repaired and subsequently
passed the broad shaderpack and final no-shader regression gates.

Historical pre-completion gaps (superseded):

```text
- model-store buffer and texture bindings exactly match shader expectations
- lightmap binding cannot silently become texture 0
- biome color and tint data match original
- material flags match original for leaves, cutout, translucent, and fluid cases
- self-light/emission metadata affects output the same way as original
- shaderpack patched and unpatched paths both bind equivalent data
- GL state entering terrain draw matches original expectations after Embeddium
  and Oculus have run
```

Historical migration steps (completed):

```text
1. Make a binding table for every resource read by quads3.vert and quads.frag.
2. For each binding, record:
   - original owner
   - Forge owner
   - lifecycle owner
   - draw-time validation condition.
3. Remove silent fallback to invalid texture ids for required visual inputs.
4. Compare ModelQueries bits from RenderDataFactory output through shader reads.
5. Validate the unpatched/no-shaderpack path before changing shaderpack output.
6. Validate the patched shaderpack path after unpatched terrain is stable.
7. Keep visual fixes data-driven; do not add brightness multipliers or
   shaderpack-specific hacks.
```

Validation:

```text
daytime and midnight visual comparison
ordinary chunk/LoD boundary comparison
foliage, stone, sand, snow, cave shadow, emissive block, and biome-colored grass
```

## 9. Water and translucent route

Status after Photon regression reconciliation:

```text
renderer parity complete
```

Original Voxy source areas:

```text
src/main/java/me/cortex/voxy/client/core/model/bakery/SoftwareModelTextureBakery.java
src/main/java/me/cortex/voxy/client/core/model/ModelQueries.java
src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp
src/main/java/me/cortex/voxy/client/core/rendering/section/backend/MDICSectionRenderer.java
```

Current Forge state:

```text
src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java
src/main/java/me/cortex/voxy/forge/RenderDataFactory.java
src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java
```

The original fluid bake metadata, translucent command construction, draw targets,
blend/depth state, and Oculus shader patch contract are connected. Photon water
and the later no-shader seam/custom-renderer regressions passed. The older
water-disappearance investigation and its proposed status fields are historical;
they do not reopen this completed source area.

## 10. Chunk ingest, chunk removal, and light updates

Status after XXI.6 / XXII implementation and completed runtime regression:

```text
core parity complete
```

Original Voxy source areas:

```text
src/main/java/me/cortex/voxy/client/mixin/sodium/**
src/main/java/me/cortex/voxy/common/world/WorldEngine.java
src/main/java/me/cortex/voxy/common/world/WorldSection.java
src/main/java/me/cortex/voxy/common/world/other/Mapper.java
```

Current Forge state:

```text
src/main/java/me/cortex/voxy/forge/mixin/** Embeddium and Minecraft mixins
src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java
src/main/java/me/cortex/voxy/forge/RenderDataFactory.java
```

XXII ported the original last-loaded removal snapshot, ingest config ownership,
deferred-light retry owner, and callback attach/detach lifecycle. XXVIII.4 then
removed the Chunky post-completion coordinate relookup race by consuming the
exact FULL result future, and the final generated-ocean regression passed without
reproducing an LOD hole. These are completed owners, not remaining migration
steps.

## 11. Compatibility integrations and platform blockers

Current XXV status after the broad runtime regression and frontend correction:

```text
complete: required Embeddium/Oculus equivalents are active; Fabric-only optional
integrations are explicitly platform-N/A and do not report false readiness;
focused F3 visibility and Embeddium option-page observation passed; config Apply
also passed through the Rendering off/on lifecycle
```

Original Voxy has Fabric-side integrations that must not be copied literally
into Forge. The Forge port should migrate behavior only when there is a real
Forge/Oculus/Embeddium equivalent.

Do not port literally:

```text
- Fabric Loader entrypoints
- Fabric API-only client hooks
- Fabric access wideners
- Fabric ModMenu loader/entrypoint integration (its option-host behavior still
  maps to Forge Mod List + Embeddium)
- Sodium package-specific hooks where Embeddium has a different owner
- Iris package-specific hooks where Oculus has a different owner
```

Port only through a real Forge equivalent:

```text
- Sodium builder-thread sharing -> Embeddium builder-thread equivalent
- Sodium chunk renderer hook -> Embeddium chunk renderer equivalent
- Sodium option-page construction -> Embeddium `OptionGUIConstructionEvent`
- Iris shader pipeline data -> Oculus shader pipeline equivalent
- Iris shader reload lifecycle -> Oculus reload lifecycle equivalent
- Flashback/replay storage override if a Forge-compatible equivalent is in
  project scope
- FREX-specific behavior only if a real Forge equivalent exists
- Nvidium-specific behavior only if a real Forge equivalent exists
```

Historical migration steps (completed):

```text
1. For each integration, first classify it:
   - required for formal Forge renderer
   - optional compatibility
   - Fabric-only and not portable
   - blocked by missing Forge equivalent.
2. Required integrations must have source-to-source mapping against Embeddium or
   Oculus repository code.
3. Optional integrations must not block formal renderer readiness.
4. Fabric-only integrations should be documented as not applicable, not replaced
   with fake status or no-op success.
```

## 12. Config, readiness status, deprecated-route cleanup, and user features

Current XXV status:

```text
config, reload, diagnostics, importer, progress UI, deprecated-route cleanup,
the post-correction artifact refresh, and broad visual/lifecycle acceptance are
complete; focused F3/Embeddium page observation and Rendering off/on config-
Apply runtime acceptance passed
```

Original Voxy source areas:

```text
src/main/java/me/cortex/voxy/client/config/VoxyConfig.java
src/main/java/me/cortex/voxy/client/VoxyClient.java
```

Current Forge state:

```text
src/main/java/me/cortex/voxy/forge/** config/status classes
docs/forge-1.20.1-deprecated-prototype-routes.md
```

Completed migration result:

```text
- Forge runtime config carries all nine current original settings: enabled,
  service threads, Embeddium/Sodium builder-thread sharing, ingest, rendering,
  subdivision size, render distance, environmental fog, and SSAO
- clean defaults start the formal route without historical switches
- renderer readiness derives only from current original-equivalent owners
- deprecated renderer/config/status routes are absent from active source
- original importer/reload diagnostics are active; F3 diagnostics are gated by
  Minecraft's debug-screen state
- Embeddium's official option-construction event owns General/Rendering pages,
  one shared repeated-Apply storage baseline, dynamic enabled predicates, and
  the original render-distance minimum; the standalone Forge screen is absent
- renderer-only settings retain the active WorldEngine; enabled/rendering
  request Oculus shader reload
- enabled changes replace the complete per-network-session runtime: renderer,
  imports, ingest/saving services, unified service pool, active-world map,
  storage, and cleaner; only the stateless Forge event shell remains process-owned
- Embeddium/Oculus version bounds and Mixin ownership are explicitly audited
```

Current maintenance rules:

```text
1. Do not reintroduce readiness DTOs, preview routes, or prototype config keys.
2. Treat the package-local Forge RenderGenerationService/RenderDataFactory/
   BuiltSection/MDICSectionRenderer chain as the active formal port.
3. Keep the retired geometry/MDIC family absent through source-contract tests.
4. Keep IterationT isolated as optional post-migration compatibility work.
```

## Historical recommended migration order (superseded)

The following was the 2026-06-23 planning snapshot. The authoritative completed
rounds are XXI-XXIV at the top of this document.

The active XI repair plan should finish first. After that, use coherent Roman
rounds rather than tiny ad hoc commits.

Suggested order:

```text
XII.1 Forge VoxyClientInstance/VoxyInstance storage and service root
XII.2 full ForgeOriginalVoxyRenderSystem outer lifecycle owner
XII.3 ModelBakerySubsystem owner convergence
XII.4 resource reuse and shutdown parity

XIII.1 RenderGenerationService/BuiltSection/geometry output parity
XIII.2 chunk ingest and light-update owner parity
XIII.3 viewport/HOC/MDIC frame-rhythm parity

XIV.1 no-shaderpack terrain visual contract
XIV.2 Oculus shaderpack pipeline bridge parity
XIV.3 water/translucent route parity

XV.1 compatibility/platform-blocker documentation
XV.2 stale config/status/debug route removal
XV.3 final original-chain audit and runtime validation
```

## Per-step validation rule

Each migration step must follow:

```text
read original Voxy owner and dependencies
 -> read Forge/Oculus/Embeddium equivalent source
 -> patch only parity-preserving adaptation
 -> rtk test .\gradlew compileJava
 -> quick source audit
 -> update docs
```

Run the client only when runtime behavior, Minecraft resource state, GL behavior,
or visual output must be validated.

## Final readiness checklist

The renderer/core items below remain satisfied through XXIV and the broad XXV
acceptance, and the post-frontend-correction artifact passes. The focused
F3/Embeddium UI observation and config-Apply lifecycle also passed. Current
whole-mod acceptance is closed with documented platform adaptations:

```text
- active worlds use configured persistent storage, not MemoryStorageBackend
- instance services are owned and shut down like original VoxyInstance
- one formal render owner owns the full renderer lifecycle
- ModelBakerySubsystem-equivalent owner owns model store/factory/bake queue
- render generation output matches original metadata and geometry contracts
- HOC/viewport/MDIC frame rhythm matches original behavior while moving
- unpatched terrain lighting/material output matches ordinary chunks
- Oculus shaderpack bridge maps original Iris data to real Oculus equivalents
- water/translucent sections bake, generate commands, and draw correctly
- reload/world detach cannot mutate stale owners
- old preview/debug/placeholder paths cannot report formal readiness
- every unavoidable Forge-specific deviation is documented as a blocker
- F3 diagnostics are absent while the debug screen is closed
- Embeddium hosts both Voxy option pages
- Rendering off/on Apply persists the latest saved baseline and rebuilds only
  the required renderer/frontend owners while retaining the active WorldEngine
```
