# Forge 1.20.1 original Voxy unported-content migration reference, 2026-06-23

This document is a post-bugfix migration reference. It lists the original Voxy
content that is still missing, only partially adapted, or present but not yet
proven equivalent in the Forge 1.20.1 port.

It does not replace the active defect repair plan:

```text
docs/forge-1.20.1-xi-confirmed-defect-repair-plan-2026-06-23.md
```

## Post-XXI authoritative completion rounds, 2026-07-13

The per-subsystem `partial` labels below are a 2026-06-23 snapshot. They must
not be read as current after XII-XXI.6: the formal renderer, model bakery,
generation/geometry chain, HOC/MDIC frame route, terrain/shaderpack pipeline,
water/translucency, resource reuse, persistent storage TYPE surface, and
identifier-routed active-world map have since been ported and runtime-validated.
XXII starts by reconciling every old label against current source.

Work is intentionally grouped into three large Roman rounds so each round can
receive one consolidated manual regression before its single commit.

### Authoritative twelve-section reconciliation at XXII

| Historical section | Current classification | Evidence / remaining owner |
| --- | --- | --- |
| 1. Instance, storage, service root | core parity complete | XXI.1-XXI.6 ported the storage TYPE surface, identity-keyed active-world map, original idle/shutdown order, and level-routed ingest; XXII adds isolated identity/corrupt-entry tests |
| 2. VoxyRenderSystem outer owner | renderer parity complete | XII-XX.7 formal owner and live readiness evidence; XXI.6 lifecycle routing |
| 3. ModelBakerySubsystem | renderer parity complete | original model factory/store/software bakery path, including the XX.4 transparent-model repair |
| 4. Render generation and geometry | renderer parity complete | original service, BuiltSection, async geometry manager/data, upload, and resource reuse chain |
| 5. RenderResourceReuse | renderer parity complete | XX.5 rebuild reuse and XX.7 terminal clear semantics |
| 6. Viewport/HOC/MDIC | renderer parity complete | original traversal, cmdgen, MDIC draws, frame ordering, and current-lifecycle readiness evidence |
| 7. Shaderpack/render pipeline | Oculus adaptation complete | original Iris contract mapped to Oculus and broadly shaderpack-regressed; IterationT is absent upstream and remains a post-parity TODO |
| 8. Terrain shader contract | renderer parity complete | original packed attributes, patched/normal program defines, light/tint/depth, and state restore |
| 9. Water/translucency | renderer parity complete | original bake/metadata/translucent command route; Photon water regression passed |
| 10. Ingest/removal/light | core parity complete in XXII | original `LIGHT_AND_DATA` readiness, dirty-section route, and last-loaded removal snapshot |
| 11. Compatibility integrations | XXIII / platform classification pending | importers, UI and real Forge equivalents remain; Fabric-only integrations must be marked platform-N/A rather than copied |
| 12. Config/readiness/cleanup | core config complete in XXII; whole-mod status pending | original enabled/rendering/ingest/SSAO semantics and defaults are ported; user features and XXIV final parity decision remain |

The detailed bodies below are retained as the 2026-06-23 source inventory and
migration rationale. Their old `partial` wording is superseded by this table and
the updated per-section status blocks.

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
[ ] port ImportManager and client import task/reference lifecycle
[ ] port current-world, named-world, raw-region, ZIP, cancel, and progress UI
[ ] port /voxy reload and applicable original diagnostics/F3 statistics
[ ] expose Forge/Embeddium configuration UI without copying ModMenu literally
[ ] map Distant Horizons only through a real Forge 1.20.1 API
[ ] classify Bobby, Flashback, FREX, Nvidium, ModMenu, and other Fabric-only hooks
[ ] perform one consolidated importer/config/lifecycle/render smoke regression
```

### XXIV final whole-mod audit and release regression

```text
[ ] classify every original source area as ported, Forge-adapted, platform-N/A,
    upstream-incomplete, or genuinely missing
[ ] remove final preview/skeleton/status wording and update all active docs
[ ] verify mixins, dependency bounds, native libraries, clean config, and final JAR
[ ] run the complete no-shader/multi-shader/dimension/reload/relog/multiplayer/
    storage/importer/shutdown regression once
[ ] after user approval, flip wholeOriginalModParity and verify status/build
```

Accepted upstream behavior and platform-N/A integrations are not completion
blockers. IterationT remains a post-parity compatibility TODO because original
Voxy has no adaptation for it.

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

## Status labels used here

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

Several lower-level original-shaped pieces exist in Forge now, especially under
`src/main/java/me/cortex/voxy/forge/**`. However, the port is still not a full
original Voxy renderer migration. The main remaining work is not more isolated
patching; it is converging owners, lifecycles, and data contracts back onto the
original chain.

The most important unported or incomplete areas are:

```text
1. VoxyClientInstance / VoxyInstance storage and service root
2. VoxyRenderSystem outer lifecycle owner
3. ModelBakerySubsystem as the model-baking owner
4. shaderpack/Oculus pipeline data and patch-output contract
5. terrain shader visual contract, including light/material/target bindings
6. water/translucent model and draw route
7. chunk ingest, chunk removal, and light-update parity
8. RenderResourceReuse and resource shutdown parity
9. full viewport/HOC/MDIC frame rhythm parity
10. status/config/docs cleanup so no preview-era route can look formal
```

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

Not yet migrated:

```text
- full `VoxyInstance.activeWorlds` map parity beyond the active/closing Forge
  adapter (the currently required same-world reuse behavior is ported)
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

Remaining migration steps:

```text
1. Validate multiplayer server isolation and corrupted-section deletion.
2. Audit remaining full `VoxyInstance.activeWorlds` semantics beyond the current
   active/closing adapter.
   Dimension isolation, relog, and shutdown passed in XXI.1; default config
   creation/reload passed in XXI.2; LZ4 and fragmentation restart passed in XXI.3;
   LMDB restart passed in XXI.4; Redis restart passed in XXI.5.
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

Not yet migrated:

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

Migration steps:

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
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelFactory.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelStore.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxySoftwareModelTextureBakery.java
```

Forge has original-shaped model factory/store/bakery code, but the original
`ModelBakerySubsystem` owner is not fully migrated as the formal owner. There is
also still historical placeholder model-store status/code that must not be part
of the formal route.

Not yet migrated:

```text
- ModelBakerySubsystem as the owner of ModelStore and ModelFactory
- original processing thread behavior and exception propagation
- requestBlockBake dedupe/locking behavior as the formal block-state bake path
- mapper biome/state callbacks routed into the model subsystem owner
- tick(totalBudget) upload semantics under the render owner
- shutdown of processing thread, factory, and store in original order
- proof that Forge fluid, tint, material, and atlas records match original data
```

Migration steps:

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
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderGenerationService.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderDataFactory.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyBuiltSection.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyBasicAsyncGeometryManager.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyBasicSectionGeometryData.java
```

Forge has a substantial port here, but several outputs are still only
parity-unproven. This area directly affects visual correctness because bad
model metadata, light bits, fluid/translucent flags, geometry buckets, or upload
metadata can make LoD terrain black, missing, overdrawn, or invisible.

Not yet migrated or not yet proven:

```text
- exact BuiltSection output layout for all material and face cases
- light/self-light/emission metadata parity
- fluid and translucent metadata parity
- directional face mask and culling parity for all buckets
- async geometry result merge order and stale-result rejection
- geometry capacity/headroom behavior against original
- status flags that prove uploaded valid sections, not only allocated resources
```

Migration steps:

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

Not yet migrated or not yet proven:

```text
- exact point where reusable resources are returned during render shutdown
- exact point where reusable resources are globally cleared during client
  instance shutdown
- guarantee that stale render-thread cleanup cannot return a buffer still owned
  by a newer renderer
- model atlas reuse ownership through the formal ModelStore owner
```

Migration steps:

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
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyHierarchicalOcclusionTraverser.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyViewportSelector.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyMdicViewport.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyMdicSectionRenderer.java
```

Forge has an original-shaped MDIC path, including command generation and draw
calls. It is still not fully proven equivalent because user-visible behavior has
shown major visual drift under shaders, and because frame rhythm still depends
on the split Forge lifecycle.

Not yet migrated or not yet proven:

```text
- original renderOpaque top-level order as the only formal frame route
- HOC request cadence while moving
- top-level node update cadence
- MDICViewport input buffer and temporal command layout parity
- cmdgen.comp barrier/readback/output layout parity under Forge GL state
- translucent command generation and draw order parity
- shadow-pass and multi-viewport behavior against Oculus/Embeddium hooks
```

Migration steps:

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

The Forge/Oculus bridge is one of the most sensitive remaining areas. It must
carry the same semantic data as original Iris integration, not a string-level
shader replacement that merely compiles.

Not yet migrated or not yet proven:

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

Migration steps:

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
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyMdicSectionRenderer.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelStore.java
```

The shaders are present, but the user-visible black LoD terrain and missing
water symptoms prove that the full visual contract is not yet established.

Not yet migrated or not yet proven:

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

Migration steps:

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
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxySoftwareModelTextureBakery.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderDataFactory.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyMdicSectionRenderer.java
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java
```

Forge currently does not have proven original-equivalent water/translucent
output. User testing has shown water can disappear, so this remains a formal
unported/incomplete area even if parts of the compute and draw route exist.

Not yet migrated or not yet proven:

```text
- Forge fluid model bake output equals original SoftwareModelTextureBakery
- fluidBlockStateId and fluid metadata are carried into model records
- containsFluid/isFluid/isTranslucent bits are produced in the same cases
- buildtranslucents.comp input and output layout is correct
- translucent draw target is the same semantic target as original pipeline
- blend/depth/cull state is restored safely after translucent draw
- shaderpack translucent terrain patch owner is equivalent to original Iris
```

Migration steps:

```text
1. Do not continue water fixes until generic terrain lighting/output is stable.
2. Compare original and Forge fluid bake output for still water, flowing water,
   waterlogged blocks, and non-water translucent blocks.
3. Trace one water section from:
   BlockState/FluidState -> model bake -> model metadata -> BuiltSection ->
   geometry metadata -> buildtranslucents.comp -> translucent draw.
4. Validate the no-shaderpack translucent target before shaderpack patching.
5. Then validate Oculus shaderpack translucent patching against original Iris
   patch data.
6. Add status fields that separately report:
   - fluid bake records
   - translucent commands generated
   - translucent draw target ready
   - translucent draw submitted.
```

Validation:

```text
ocean
river
swamp
waterlogged foliage/blocks if available
no shaderpack first, then shaderpack
```

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
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderDataFactory.java
```

Forge has chunk ingest and light data handling, but known audit findings show
that chunk removal and light-update behavior are not yet fully original
equivalent.

Not yet migrated or not yet proven:

```text
- chunk-remove path can access the same last loaded chunk data original Sodium
  mixins used
- ingest obeys the same config owner as original Voxy
- light DataLayer deferral has an explicit update/retry owner
- dirty section callbacks and mapper callbacks are attached/detached only by
  the formal owners
- world detach cannot leave pending ingest/light work targeting a freed world
```

Migration steps:

```text
1. Compare original Sodium chunk mixins with Embeddium's 1.20.1 chunk storage
   and removal flow.
2. Use Embeddium source from the repository instead of decompiling jars.
3. Replace ordinary "get current chunk if still loaded" removal reads with an
   Embeddium-equivalent last-loaded snapshot if the API exists.
4. If no equivalent exists, document the platform blocker and choose a safe
   conservative behavior.
5. Route ingest enable/disable through the original-equivalent config owner.
6. Add an explicit owner for deferred light data retries and clear it on world
   detach.
7. Compile and audit with world leave/enter and new-world generation.
```

Validation:

```text
new world generation
fast flight across chunk boundaries
leave world while generation is active
re-enter world and verify no stale light/ingest work crashes or corrupts LoD
```

## 11. Compatibility integrations and platform blockers

Status for XXIII:

```text
pending real Forge-equivalent migration or explicit platform-N/A classification
```

Original Voxy has Fabric-side integrations that must not be copied literally
into Forge. The Forge port should migrate behavior only when there is a real
Forge/Oculus/Embeddium equivalent.

Do not port literally:

```text
- Fabric Loader entrypoints
- Fabric API-only client hooks
- Fabric access wideners
- ModMenu integration
- Sodium package-specific hooks where Embeddium has a different owner
- Iris package-specific hooks where Oculus has a different owner
```

Port only through a real Forge equivalent:

```text
- Sodium builder-thread sharing -> Embeddium builder-thread equivalent
- Sodium chunk renderer hook -> Embeddium chunk renderer equivalent
- Iris shader pipeline data -> Oculus shader pipeline equivalent
- Iris shader reload lifecycle -> Oculus reload lifecycle equivalent
- Flashback/replay storage override if a Forge-compatible equivalent is in
  project scope
- FREX-specific behavior only if a real Forge equivalent exists
- Nvidium-specific behavior only if a real Forge equivalent exists
```

Migration steps:

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

## 12. Config, readiness status, and deprecated route cleanup

Status after XXII implementation and completed runtime regression:

```text
core config/cleanup complete; user features and whole-mod parity remain pending
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

The project still has staged flags, historical status names, and old preview or
placeholder concepts that can mislead future work. These should not be treated
as original Voxy migration content.

Not yet migrated or not yet complete:

```text
- Forge runtime config aligned with original Voxy config semantics
- readiness flags tied to real original-equivalent owners
- status output that cannot report formal readiness from placeholder/debug data
- old stage-roadmap wording rewritten or marked superseded
- broad/unbounded Embeddium/Oculus dependency and mixin assumptions documented
```

Migration steps:

```text
1. Keep formal readiness flags false until the original-equivalent owner exists:
   - formalRendererReady=false
   - actualRendererDrawEnabled=false
   - formalDrawPipelineReady=false
   - earlyUsableLodRendererReady=false.
2. Remove staged skeleton switches from runtime config once the old routes no
   longer need them to compile.
3. Update status naming so it reports actual owners and data paths, not phase
   names from historical prototype work.
4. Update deprecated-route inventory every time old code is isolated or removed.
5. Audit docs for old H/I/J/K/L or preview-era wording after each migration
   round.
```

## Recommended post-bugfix migration order

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

The original-chain migration is not complete until all of these are true:

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
```
