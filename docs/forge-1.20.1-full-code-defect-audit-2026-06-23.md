# Forge 1.20.1 full code defect audit, 2026-06-23

> **HISTORICAL SNAPSHOT.** This is the issue ledger captured on 2026-06-23, not
> the current implementation status or an active plan. Many findings below were
> resolved in later Roman-numeral rounds. Use
> `forge-1.20.1-original-voxy-full-render-path-parity-audit.md` as the canonical
> current status and re-confirm any surviving item against current source before
> acting on it.

This document is retained so the post-cleanup findings and their evidence remain
reviewable.

## Audit scope

Requested scope:

```text
scan current code as thoroughly as possible
find bugs, drift, hidden issues, route contamination, and small details
compare against original Voxy
use project docs and external Forge/Fabric/Voxy/Embeddium/Oculus references
```

Current mode:

```text
audit only
no code fixes yet unless explicitly requested
append every finding here before continuing deeper scans
```

Follow-up repair plan:

```text
docs/forge-1.20.1-xi-confirmed-defect-repair-plan-2026-06-23.md
```

Post-bugfix original-content migration reference:

```text
docs/forge-1.20.1-original-voxy-unported-content-migration-reference-2026-06-23.md
```

## Reference links

User-provided external references:

```text
Forge 1.20.1 docs: https://docs.minecraftforge.net/en/1.20.1/gettingstarted/
Fabric docs: https://docs.fabricmc.net/develop/
Original Voxy issues: https://github.com/MCRcortex/voxy/issues
Embeddium issues: https://github.com/FiniteReality/embeddium/issues
Oculus issues: https://github.com/Asek3/Oculus/issues
```

Local reference sources:

```text
embeddium-20.1-forge/
Oculus-1.20.1-new/
src/main/java/me/cortex/voxy/client/** original Voxy source fragments
src/main/java/me/cortex/voxy/forge/** Forge port
```

Important existing project docs:

```text
docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md
docs/forge-1.20.1-deprecated-prototype-routes.md
docs/forge-1.20.1-deep-runtime-drift-audit-2026-06-21.md
docs/forge-1.20.1-black-lod-bugfix-plan-2026-06-22.md
docs/forge-1.20.1-debug-preview-cleanup-2026-06-22.md
```

## Severity labels

```text
P0 crash, freeze, data corruption, or impossible startup
P1 visible renderer breakage, original-chain blocker, or serious lifecycle drift
P2 correctness drift that can produce visual/performance bugs under common cases
P3 documentation/status/naming debt that can mislead future work
```

Current inventory snapshot:

```text
confirmed findings: 34
P1 findings: 9
P2 findings: 20
P3 findings: 5
excluded direct-cause theories: 1
external risk-signal groups: 2
```

## Confirmed findings

### P1: Active Forge WorldEngine still uses in-memory storage

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java
createActiveWorldSkeleton()
 -> new CountingSectionStorage(new SectionSerializationStorage(new MemoryStorageBackend()), ...)
 -> new WorldEngine(storage, this)
```

Why this is a problem:

Original Voxy's `WorldEngine` route is backed by configured storage and reloads
saved section/id-mapping data. The current Forge active-world creation still
uses `MemoryStorageBackend`, so LoD section data and id mappings are process
local. That can hide save/load bugs, lose LoD data across world re-entry, and
make world lifecycle behavior diverge from the original owner.

Required comparison:

```text
original Voxy client bootstrap/config storage creation
 -> WorldEngine constructor ownership
 -> SectionSavingService / SectionStorage close and reload behavior
 -> Forge 1.20.1 world save path and per-world/dimension storage root
```

Do not fix by simply swapping a path string. The storage owner, flush/close
order, id mapping persistence, and world/dimension keying must match the
original Voxy lifecycle.

### P1: Full outer VoxyRenderSystem lifecycle is still missing

Evidence:

```text
docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md
 -> current verdict keeps formalRendererReady=false
 -> remaining blocker includes full outer VoxyRenderSystem lifecycle ownership

src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java
 -> owns Forge WorldEngine lifecycle, tick lifecycle, close queue, and many managers

src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java
 -> original constructor owns world ref, model service, render gen, geometry data,
    node manager, node cleaner, HOC traversal, pipeline, viewport selector,
    render distance tracker, render pipeline execution, and dynamic post-frame work
```

Why this is a problem:

The active Forge route has many original-shaped pieces, but the outer owner is
still a Forge instance plus Embeddium hook adapter rather than the original
`VoxyRenderSystem` constructor/render/shutdown owner. This makes reload,
dimension switch, world detach, GL state restore, download stream flush,
model-bakery setup, and service-thread ownership easier to desynchronize.

Required comparison:

```text
VoxyRenderSystem constructor
VoxyRenderSystem.renderOpaque()
VoxyRenderSystem.shutdown()
ForgeVoxyInstance lifecycle
ForgeOriginalVoxyModelPipeline lifecycle
Embeddium hook adapter call order
```

### P1: Runtime config still exposes staged skeleton switches on the active path

Evidence:

```text
src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java
 -> ENABLE_WORLD_ENGINE_SKELETON
 -> ENABLE_AUTO_CHUNK_INGEST
 -> ENABLE_AUTO_CPU_MESH_BUILD
 -> ENABLE_AUTO_BUILT_SECTION_BUILD
 -> ENABLE_AUTO_GEOMETRY_MANAGER_CONSUME
 -> ENABLE_GEOMETRY_GPU_UPLOAD
 -> ENABLE_MDIC_COMMAND_SKELETON

src/main/java/me/cortex/voxy/forge/ForgeVoxyRuntimeOverrides.java
 -> still returns the above switches to active managers
```

Why this is a problem:

These switches look like staged route gates rather than original Voxy runtime
configuration. They can leave the formal route in impossible mixed states:
WorldEngine on but ingest off, geometry upload on but MDIC skeleton off, MDIC
planner using radius/frustum fallback knobs instead of the original HOC/MDIC
flow, and so on. Even if defaults are false, the presence of these gates means
runtime behavior can still depend on old phased toggles.

Required comparison:

```text
original VoxyConfig fields that actually affect renderer behavior
ForgeVoxyConfig current switches
all callers of ForgeVoxyRuntimeOverrides
which switches are legacy QA gates vs required user-facing config
```

### P1: Placeholder model-store route survived cleanup and was owned by ForgeVoxyInstance

Original evidence before 2026-06-23 repair:

```text
src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java
 -> private final ForgeModelStoreSkeleton modelStoreSkeleton

src/main/java/me/cortex/voxy/forge/ForgeModelStoreSkeleton.java
 -> builds ForgeModelStoreRecord.placeholder(...)
 -> uploads through ForgeModelDataBuffer

src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayout.java
 -> LAYOUT_VERSION = "PLACEHOLDER_MODEL_RECORD_V1"
 -> FORMAL_LAYOUT_COMPATIBLE = false
 -> FLAG_PLACEHOLDER / FLAG_NO_FACE_DATA / FLAG_NO_ATLAS / FLAG_NO_REAL_MODEL_METADATA

src/main/java/me/cortex/voxy/forge/ForgeModelDataBuffer.java
 -> "upload placeholder model data buffers"
 -> writes record.debugColour() into modelColour buffer
```

Why this is a problem:

The deprecated sample-set classes had been removed, but a semantically
equivalent placeholder model-store path still existed and was still owned by the
top-level Forge instance. It only fed command/status/audit surfaces, but it kept
a non-original `modelData/modelColour` upload path alive in the same source set
as the active original model store. This violated the cleanup goal of isolating
old code from the new route and could mislead future shader/model debugging
because it contained fake colors and explicitly lacked faceData, atlas, and real
metadata.

Required comparison:

```text
all callers of ForgeModelStoreSkeleton / ForgeModelDataBuffer
all status fields exposed by ForgeVoxyParityCommands
whether any active model/MDIC shader path can accidentally bind these buffers
replacement with ForgeOriginalVoxyModelStore-only diagnostics
```

Repair status, 2026-06-23:

```text
Fixed for active source.

CodeGraph confirmed getModelStoreSkeleton() had no callers and the placeholder
model-store class family was an isolated legacy/status route. The repair removed
ForgeVoxyInstance ownership of ForgeModelStoreSkeleton and
ForgeModelStoreLayoutAuditor, removed the reload tracker dependency on
getModelStoreSkeleton().markStale(...), and deleted:

ForgeModelStoreSkeleton
ForgeModelDataBuffer
ForgeModelStoreRecord
ForgeModelStoreStats
ForgeModelStoreAuditResult
ForgeModelStoreLayout
ForgeModelStoreLayoutAuditor
ForgeModelStoreLayoutAuditResult

ForgeVoxyModelIdMapper was not deleted because old CPU geometry/status code
still calls getOrCreateModelId() and uniqueModelCount(); it is now documented
in-code as legacy compatibility and no longer exposes unused reverse/snapshot
helpers.

Validation:
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

### P1: Shaderpack visual contract is not proven even after patched shaders compile

Evidence:

```text
docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md
 -> latest next-work section says black LoD must now compare generic shaderpack
    terrain contract:
    voxy.json draw target ids
    main/alt target texture selection
    framebuffer attachment order
    glDrawBuffers order
    patched VoxyFragmentParameters
    lightmap uv / sampler binding
    custom block-state material id
    shaderpack G-buffer outputs
```

Why this is a problem:

The latest documented status says `opaquePatchedShaderUsed=true` and
`translucentPatchedShaderUsed=true`, but the user still observes black LoD and
missing water. That means "shader compiled" is not enough; the output contract
may still be wrong. The likely risk area is generic Oculus/Iris shaderpack data
flow, not one Complementary-specific brightness hack.

Required comparison:

```text
original IrisVoxyRenderPipelineData
original Iris Voxy draw-target attachment and draw-buffer order
Oculus 1.20.1 RenderTargets / ProgramSet / WorldRenderingPipeline equivalents
ForgeOriginalVoxyOculusRenderPipelineData
ForgeOriginalVoxyRenderPipeline final output and depth/lightmap binding
```

### P1: Oculus shaderpack patch exposure is enabled before visual-output parity is proven

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java
 -> FORMAL_SHADERPACK_PATCH_OUTPUT_READY = true
 -> shouldExposeVoxyShaderpackPatch() returns enabled && true

same method comment:
 -> says Forge still audits zero MDIC output on the normal Oculus path
 -> says exposing only the render-target table is the safe subset until the
    formal draw output owner is ready
```

Why this is a problem:

The code currently exposes full Voxy shaderpack patch data to Oculus whenever
the mod is enabled early-safe, while the local runtime still has unresolved
black-LoD and missing-water symptoms under Complementary. That means shaderpack
programs may be compiled around the assumption that Voxy's terrain patch output
is trustworthy even though this audit has not proven the output contract.
This is especially risky because different shaderpacks can request different
uniforms, samplers, draw buffers, blend states, and depth behavior.

Required comparison:

```text
original Iris ProgramSet patch exposure conditions
original IrisVoxyRenderPipelineData build timing
ForgeOriginalVoxyOculusProgramSetMixin target-table expansion
ForgeOriginalVoxyOculusPipelineBridge.shouldExposeVoxyShaderpackPatch()
runtime readback proving nonzero MDIC draw output and visible shaderpack output
```

### P1: Oculus shaderpack path may collapse multiple draw targets into first-texture status/output helpers

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java
resizeExternal(...)
 -> attaches every opaque target to GL_COLOR_ATTACHMENT0 + i
 -> attaches every translucent target to GL_COLOR_ATTACHMENT0 + i
 -> stores colourTextureId = firstTextureOrZero(data.opaqueDrawTargets)
 -> stores colourSsaoTextureId = firstTextureOrZero(data.translucentDrawTargets)

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java
 -> colourTextureId() and colourSsaoTextureId() expose those first textures
 -> oculusDrawTargetsReady() only checks that external draw targets are in use
```

Why this is a problem:

Original `IrisVoxyRenderPipeline` attaches all shaderpack draw targets and the
shaderpack expects each output location to land in the matching target. The
Forge path does attach multiple framebuffer color attachments, but several
helpers and readiness/status methods still collapse the state to the first
opaque/translucent texture. That may be harmless for diagnostic fields, but it
is dangerous if any later depth, final-output, status, or shader binding logic
assumes the first texture represents the whole shaderpack output.

Required comparison:

```text
IrisVoxyRenderPipeline constructor draw-buffer setup
IrisVoxyRenderPipeline finish/postOpaquePreTranslucent behavior
all ForgeOriginalVoxyRenderPipeline callers of colourTextureId/colourSsaoTextureId
all command/status fields that report shaderpack target readiness
```

### P1: User-observed runtime symptoms remain unresolved

Observed symptoms reported in this thread:

```text
far LoD blocks render very dark, as if light level were near zero
changing time to midnight makes LoD darker, changing back to day brightens a bit
water is missing or not visible in LoD
new world creation has crashed or frozen in some recent test runs
previous grid artifact was fixed
vertical rectangle artifact was likely spyglass-related, not core LoD
```

Why this matters:

These symptoms prove the current active route is still visually incorrect under
real Oculus/shaderpack runtime. Any future code fix must be validated against
these exact symptoms and must not regress the already-fixed grid artifact.

### P1: Forge reads the Oculus WorldRenderingSettings reload flag without consuming it

Evidence:

```text
Oculus-1.20.1-new/.../WorldRenderingSettings.java
 -> isReloadRequired() returns reloadRequired
 -> clearReloadRequired() is the only local consumer that clears the flag

Oculus-1.20.1-new/.../PipelineManager.java
 -> when a new Oculus pipeline is created, it checks isReloadRequired()
 -> calls levelRenderer.allChanged()
 -> then calls WorldRenderingSettings.INSTANCE.clearReloadRequired()

src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java
 -> isReloadRequired() reflectively calls only isReloadRequired()
 -> no bridge exists for clearReloadRequired()

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> clientTick() calls consumeOculusWorldRenderingSettingsReload() before
    start scheduling and upload ticks
 -> consumeOculusWorldRenderingSettingsReload() calls markOculusWorldRenderingSettingsReload()
 -> markOculusWorldRenderingSettingsReload() can mark the active owner stale,
    clear resources, and request a restart
```

Why this is a problem:

Oculus treats `WorldRenderingSettings.reloadRequired` as a stateful reload
request that must be consumed. The Forge bridge currently observes the flag but
does not clear it or version it. `markOculusWorldRenderingSettingsReload()` has
a partial same-event guard while the pipeline is already stale or start is
pending, but once a Forge Voxy owner starts successfully, the still-true Oculus
flag can be seen again on a later client tick and clear/restart the freshly
created pipeline. This can plausibly produce world-entry stalls, repeated
resource churn, or new-world instability around shader/model reloads.

Required comparison:

```text
Oculus PipelineManager reloadRequired consumption
original Iris/Sodium reload timing around WorldRenderingSettings
ForgeOriginalVoxyModelPipeline clientTick/start/clear ordering
whether Forge should clear the Oculus flag, track a generation, or only rebuild
after Oculus has completed its own pipeline reload
```

Repair status, 2026-06-23:

```text
fixed for repeated Forge-owner restart churn
```

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> consumeOculusWorldRenderingSettingsReload() now treats reloadRequired as a
    false-to-true edge and ignores repeated true polls until Oculus clears it
 -> Forge still does not call clearReloadRequired(), preserving Oculus
    PipelineManager as the flag consumer
 -> bounded logs distinguish the first observed reload edge from repeated
    debounced polls
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

Remaining risk:

```text
Runtime runClient evidence is still needed to prove the new debounce removes
the observed new-world crash/freeze. Shaderpack visual output remains a separate
unfixed P1.
```

### P2: RenderDataFactory lighting/TODOs are inherited upstream risks, not yet Forge-only drift

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderDataFactory.java
 -> TODO: LIGHTING
 -> TODO FIX THIS (self lighting)
 -> TODO translucent face-culling / same-model self-occlusion comments

src/main/java/me/cortex/voxy/client/core/rendering/building/RenderDataFactory.java
 -> contains the same corresponding TODOs and logic
```

Why this matters:

These comments are visually relevant because they touch light selection,
self-lighting, translucent/fluid faces, and face culling. However, because the
same logic exists in the original source, they are not by themselves proof of a
Forge-port regression. The audit should use them as high-risk comparison points
while looking for Forge-specific input differences: section light data,
fluid/model metadata, shader lightmap binding, and shaderpack output targets.

Required comparison:

```text
WorldConversionFactory / VoxelIngestService light packing
ForgeOriginalVoxyModelFactory metadata generation
ForgeOriginalVoxyModelStore model record upload
shader lightmap sampling and block-model atlas sampling
```

### P2: Legacy CPU/BuiltSection/cache managers remain registered beside the original pipeline

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java
 -> ForgeCpuMeshBuildManager
 -> ForgeCpuMeshCache
 -> ForgeVoxyBuiltSectionBuildManager
 -> ForgeVoxyGeometryCache
 -> ForgeSectionGeometryManager
 -> ForgeSectionGeometryConsumeManager
 -> ForgeGpuGeometryUploadManager
 -> ForgeMdicCommandManager
```

Why this is a risk:

The cleanup removed old visible renderers, but several staged manager layers
still register and clear from the top-level Forge instance. Some may now be
active original-shaped infrastructure; some may be historical staging. This
needs a caller-by-caller audit so the new route does not silently depend on old
CPU mesh or cache bridges after the renderer cleanup.

Required comparison:

```text
original RenderGenerationService -> BasicAsyncGeometryManager path
Forge manager registration and tick/update paths
which managers feed ForgeOriginalVoxyModelPipeline/MDIC directly
which managers only exist for stale command/status evidence
```

### P2: Post-frame dynamic work is gated differently from original Voxy

Evidence:

```text
original src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java
renderOpaque(...)
 -> pipeline.runPipeline(...)
 -> always enters postDynamic block:
    UploadStream tick
    RenderDistanceTracker.setCenterAndProcess(...)
    modelService.tick(...)

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
renderEmbeddiumCutout(...)
 -> commandGenerationCompleted = true only after sectionRenderer.buildDrawCalls(...)
 -> finally runs runOriginalPostDynamicWorkAfterCommandGeneration(...) only when
    commandGenerationCompleted is true
```

Why this is a problem:

The normal successful frame likely runs post-dynamic work, but the Forge route
can skip it on early return, failed setup, failed traversal readiness, or any
frame that exits before command-generation completion. Original Voxy's dynamic
work happens after the render pipeline call, not specifically after successful
cmdgen. This can delay or stall movement-driven top-level node changes,
geometry requests, model bake queue progress, and render-distance updates in
exactly the area the user wants verified.

Required comparison:

```text
all early returns in ForgeOriginalVoxyModelPipeline.renderEmbeddiumCutout()
original VoxyRenderSystem.renderOpaque() early returns
whether UploadStream tick / RenderDistanceTracker / model factory tick must run
after partial frames, empty geometry frames, or setup failures
```

Repair status, 2026-06-23:

```text
fixed for command-generation gating drift
```

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> renderEmbeddiumCutout() now marks post-dynamic work eligible after
    renderPipeline.setup(...) succeeds instead of waiting for
    sectionRenderer.buildDrawCalls(...)
 -> runOriginalPostDynamicWorkAfterCommandGeneration(...) is still the helper
    name, but its call site now follows the original frame lifecycle more
    closely by running after a valid pipeline setup even if later cmdgen or
    translucent stages do not complete
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

Remaining risk:

```text
Frames that return before a valid pipeline setup still skip post-dynamic work.
That is intentional until the full VoxyRenderSystem-equivalent outer owner is
ported, because those frames do not have a valid original viewport/pipeline
frame to advance from.
```

### P2: Forge chunk-remove ingest may miss the last loaded chunk snapshot

Evidence:

```text
original src/main/java/me/cortex/voxy/client/mixin/sodium/MixinRenderSectionManager.java
 -> onChunkRemoved uses ICheekyClientChunkCache.voxy$cheekyGetChunk(x, z)
 -> comment says removal-time ingest is uncertain but intentionally tries the
    cached chunk before it disappears

src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java
 -> onChunkRemoved calls this.world.getChunkSource().getChunk(x, z, FULL, false)
 -> if that returns null, no final ingest is performed
```

Why this is a risk:

The original Sodium path deliberately uses a cache escape hatch on chunk
removal. The Forge Embeddium path uses the ordinary chunk-source lookup, which
may already return null by the time `onChunkRemoved` fires. If the periodic
nearby scan or add-time ingest missed the chunk, Forge can lose the last chance
to write that chunk into the LoD world before vanilla removes it. This is more
likely to create missing LoD/transition gaps than global black LoD, but it is a
real original-parity deviation.

Required comparison:

```text
Embeddium 1.20.1 RenderSectionManager.onChunkRemoved timing
ClientChunkCache state at that hook point
whether a Forge equivalent of ICheekyClientChunkCache is still available
interaction with ForgeChunkIngestManager's periodic scan and retention radius
```

### P2: Forge Embeddium mixin chunk ingest ignores the staged ingest config gate

Evidence:

```text
original src/main/java/me/cortex/voxy/client/mixin/sodium/MixinRenderSectionManager.java
 -> onChunkAdded/onChunkRemoved check VoxyConfig.CONFIG.ingestEnabled

src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java
 -> onChunkAdded/onChunkRemoved call VoxelIngestService.tryAutoIngestChunk(...)
    without checking ForgeVoxyRuntimeOverrides.enableAutoChunkIngest() or an
    original-equivalent ingest-enabled config

src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java
 -> ENABLE_AUTO_CHUNK_INGEST default is false, but the mixin path can still
    ingest whenever ForgeVoxyInstance has an active WorldEngine
```

Why this is a problem:

The current config says automatic chunk ingest is disabled by default, but the
Embeddium hook path can still ingest chunks on add/remove. That creates two
different meanings for "auto ingest": periodic scan only vs all runtime ingest.
This is configuration drift from original Voxy and makes runtime evidence hard
to interpret because disabling the config does not disable every ingest path.

Required comparison:

```text
original VoxyConfig.CONFIG.ingestEnabled semantics
ForgeVoxyConfig.ENABLED vs ENABLE_AUTO_CHUNK_INGEST intended ownership
all VoxelIngestService.tryAutoIngestChunk callers
whether section-update ingest should be separately gated or always required
```

### P2: Runtime light-data defer has retry coverage but no explicit light-update queue

Evidence:

```text
src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java
 -> ingestChunkWithStats(...) skips a section when skyLight is null in sky-lit
    dimensions and increments deferredLightSections

src/main/java/me/cortex/voxy/forge/ForgeChunkIngestManager.java
 -> if stats.deferred() is true, the chunk is not inserted into ingestedChunks
 -> later radius scans may queue it again
```

Why this remains risky:

The periodic retry means deferred light is not a one-shot dead end. However,
the retry is tied to the nearby scan cadence and loaded-chunk radius, not to a
specific light-ready notification or original-owned ingest service queue. If
Forge/Embeddium produces light data after the add/remove hook but outside the
scan window, the LoD world can remain stale until another scan or section-info
transition happens. This does not prove the black-LoD root cause, but it is a
runtime synchronization risk in the light/ingest chain.

Required comparison:

```text
original MixinClientLevel light update hook
original MixinRenderSectionManager rawIngest timing
Forge Embeddium section-info update timing
whether Forge should listen to light-ready/section dirty events explicitly
```

Repair status, 2026-06-23: partially fixed for live chunk ingest input
sanity.

Evidence:

```text
src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java
 -> loaded chunk ingest now ignores all-air LevelChunkSections before checking
    light readiness, matching original WorldImporter's skip of sections without
    block_states payload
 -> loaded chunk ingest preflights non-air sections for missing sky-light
    DataLayer before any WorldEngine writes
 -> when a non-air section is missing sky light in a sky-lit dimension, the
    chunk returns deferred stats and performs no partial write for that pass
 -> rawIngestWithStats(...) still keeps the lower-level original conversion
    path available for callers that already own complete section data
```

Validation:

```text
2026-06-23: rtk test .\gradlew compileJava -> passed after this repair.
```

Remaining risk:

```text
This does not add an original-equivalent light-ready notification queue. Retry
is still owned by ForgeChunkIngestManager's nearby scan cadence, and runtime
validation is still required to prove that the black-LoD symptom is gone. It
does remove the confirmed Forge-only behavior where empty air sections could
inflate deferred-light counts and where non-air missing-sky sections could be
mixed with partial chunk writes.
```

Additional repair status, 2026-06-23:

```text
original ClientLevel block-state update ingest hook ported to Forge
```

Evidence:

```text
original src/main/java/me/cortex/voxy/client/mixin/minecraft/MixinClientLevel.java
 -> injects at ClientLevel.setBlocksDirty TAIL.
 -> when a block changes to air on a section border, it re-ingests that section
    with copied block/sky DataLayer state.

src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java
 -> adds the same setBlocksDirty TAIL hook for Forge 1.20.1 official mappings.
 -> uses ForgeVoxyInstance.INSTANCE.getCurrentEngineOptional() instead of the
    Fabric VoxyCommon/WorldIdentifier path.
 -> keeps the original trigger shape: ignore unchanged states, ignore non-air
    updates, and only re-ingest border-section changes.

src/main/resources/voxy.forge.mixins.json
 -> registers ForgeOriginalVoxyClientLevelMixin in the client mixin list.
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

Remaining risk:

```text
This closes the original block-state update hook gap, but it is not a complete
light-update queue. Runtime validation is still needed to prove delayed light
arrival, day/night visual response, and black LoD behavior under shaderpacks.
```

### P2: Lightmap texture binding can silently bind texture 0 with no status evidence

Evidence:

```text
original src/main/java/me/cortex/voxy/client/core/rendering/util/LightMapHelper.java
 -> getLightmapTextureId() directly returns the level lightmap texture GL id
 -> MDICSectionRenderer.bindRenderingBuffers(...) binds that id to unit 1

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java
 -> lightTextureId() reflectively reads LightTexture.lightTexture.getId()
 -> if reflection/runtime access fails, it catches RuntimeException and keeps
    the last captured id
 -> if startup capture also failed, lightTextureId remains 0

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyMdicSectionRenderer.java
 -> bindLightmap(1) binds ForgeOriginalVoxyRenderStateCapture.lightTextureId()

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java
 -> shaderpack external sampler "lightmap" uses the same lightTextureId()
```

Why this is a risk:

Oculus 1.20.1 has a `LightTextureAccessor("lightTexture")`, so the field name
itself appears valid. The problem is observability and failure behavior: a
failed read can collapse both normal terrain lightmap sampling and shaderpack
`lightmap` sampling to texture 0 without a readiness flag or status line. The
user's black-LoD symptom is still not proven to be this, but the current code
does not make it easy to distinguish "bad lighting data" from "no lightmap
texture bound."

Repair status, 2026-06-23: partially fixed for shaderpack lightmap/image
binding observability.

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java
 -> ImageSet.ready() returns false when a required external sampler such as
    lightmap resolves to texture id 0
 -> ImageSet.lastFailureReason() records
    oculus-image-binding-texture-zero:lightmap
 -> ImageSet.bind(...) logs the texture-0 condition once instead of silently
    treating it as ready
 -> sampler binding now matches original IrisVoxyRenderPipelineData: sampler
    id -1 is not bound as 0; only explicit sampler ids are bound

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java
 -> oculusImageBindingsReady() now delegates to ImageSet.ready()
 -> oculusPipelineFailureReason() reports the ImageSet failure before the
    bridge-level failure reason
```

Validation:

```text
2026-06-23: rtk test .\gradlew compileJava -> passed after this repair.
```

Remaining risk:

```text
This does not prove that the lightmap id is non-zero in the user's runtime, nor
does it fix the generic black-LoD output by itself. It makes the failure visible
and removes one Forge-only sampler-state deviation from original Iris Voxy.
Normal MDIC bindLightmap(1) still uses ForgeOriginalVoxyRenderStateCapture and
needs runtime/log verification during XI.5.
```

Required comparison:

```text
vanilla/Forge LightTexture GL id access under production mappings
whether captureLightTexture(...) runs before the first Voxy draw
status field for non-zero lightTextureId and latest capture source
runtime audit log when lightTextureId is 0 during terrain draw
```

### P2: Render-state capture mixin mutates GL viewport at LevelRenderer HEAD

Evidence:

```text
src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java
 -> @Inject(method = "renderLevel", at = @At("HEAD"))
 -> captures projection/lightmap/Oculus viewport
 -> when shaderpack is active and not shadow pass, calls:
    glViewport(0, 0, Minecraft.getInstance().getMainRenderTarget().width,
                    Minecraft.getInstance().getMainRenderTarget().height)
```

Why this is a risk:

The capture mixin should ideally observe render state, not modify it. Changing
the viewport at the head of `LevelRenderer.renderLevel` can race Oculus'
own viewport/render-scale setup, shadow-pass handling, and Embeddium frame
state. The main Voxy render path later captures/restores viewport around its
own draw, but this earlier mutation happens outside that guarded block.

Required comparison:

```text
why the HEAD glViewport call was added
Oculus 1.20.1 beginLevelRendering / render-scale viewport setup order
whether this call is still needed after ForgeOriginalVoxyModelPipeline uses the
captured source viewport and renderPipeline.renderScalingFactor()
```

### P2: Oculus shaderpack uniform builder is adapted, not byte-parity proven

Evidence:

```text
original IrisVoxyRenderPipelineData.createUniformSet(...)
 -> CommonUniforms.addDynamicUniforms(...)
 -> customUniforms.assignTo(...)
 -> customUniforms.mapholderToPass(...)
 -> VoxyUniforms are exposed through MixinMatrixUniforms into CommonUniforms

ForgeOriginalVoxyOculusRenderPipelineData.createUniformSet(...)
 -> ForgeOriginalVoxyOculusVoxyUniforms.addUniforms(...)
 -> CameraUniforms.addCameraUniforms(...)
 -> CommonUniforms.addDynamicUniforms(...)
 -> customUniforms.assignTo(...)
 -> customUniforms.mapholderToPass(...)
```

Why this is a risk:

The Forge/Oculus path likely needs this adaptation because Oculus 1.20.1's
uniform APIs differ from original Iris, and `ForgeOriginalVoxyOculusVoxyUniforms`
is mostly an equivalent rewrite of original `VoxyUniforms`. However, the final
uniform list/order/types are not yet proven byte-for-byte equivalent for the
shader patch. A missing or shifted camera/custom uniform can produce
shaderpack-specific dark output without causing shader compilation failure.

Required comparison:

```text
patch.getUniformList() for the active shaderpack
original Iris uniform layout StructLayout ordering and offsets
Forge Oculus uniform layout ordering and offsets
whether CameraUniforms are required by Oculus patch extraction or duplicated
```

### P2: Forge fluid model baking is a platform-specific substitute that still needs output parity proof

Evidence:

```text
original SoftwareModelTextureBakery
 -> owns FluidRenderer from Minecraft model manager's fluid state model set
 -> bakeFluidState(...) passes a synthetic BlockAndTintGetter and layer callback

ForgeSoftwareModelTextureBakery
 -> owns a Forge-side fluidRenderer and renderFluid(...)
 -> manually selects ItemBlockRenderTypes.getRenderLayer(fluidState)
 -> passes a synthetic BlockAndTintGetter into fluidRenderer.tesselate(...)
```

Why this is a risk:

The Forge implementation closely mirrors the original control flow, including
the per-face air-neighbor trick and tint metadata injection. Still, it is a
Forge-specific substitute around a different renderer/API surface. Because the
user currently reports missing LoD water, this path needs a concrete readback
or logged bake audit proving that water block states produce non-empty faces,
translucent metadata, a valid fluid model id, and uploaded atlas pixels.

Confirmed Forge-side defect found during repair:

```text
ForgeSoftwareModelTextureBakery.renderToOutput(...) previously returned early
for state.getRenderShape() == RenderShape.INVISIBLE before checking
state.getBlock() instanceof LiquidBlock.

In vanilla/Forge 1.20.1, pure water and lava are LiquidBlock states with an
INVISIBLE block render shape; they must be rendered through LiquidBlockRenderer.
The old Forge ordering therefore baked pure water/lava as empty output with no
failure. Since texturesFromOutput(...) materializes empty output as six all-zero
faces, ForgeOriginalVoxyModelFactory could dedupe the result against model id 0
(air). This matches runtime audit evidence where water-containing states showed
fluidModelId=0.

Repair applied 2026-06-23:
- Move the LiquidBlock renderFluid(...) path before the non-fluid
  RenderShape.INVISIBLE return.
- Compile validation: rtk test .\gradlew compileJava -> passed.

Remaining proof required:
- Runtime audit must show pure water/lava model ids no longer alias to 0.
- Visual validation must confirm LoD water visibility. This repair does not
  prove the generic black LoD terrain shader output issue is fixed.
```

Required comparison:

```text
water/lava block-state bake summaries under voxy.forge.auditShaderpack
ForgeOriginalVoxyModelFactory fluidModelId and metadata bits
ForgeOriginalVoxyRenderDataFactory containsFluid/isFluid buckets
translucent draw count/readback when water LoD should be visible
```

### P2: Forge quad material bridge approximates original material metadata through Embeddium internals

Evidence:

```text
src/main/java/me/cortex/voxy/client/core/model/bakery/ReuseVertexConsumer.java
 -> original reads quad.materialInfo().layer(), isTinted(), shade(),
    and sprite().contents().mipmapStrategy == MipmapStrategy.DARK_CUTOUT

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java
 -> reflectively reads Embeddium/Sodium BakedQuadView methods
 -> maps discard from vanilla RenderType equality
 -> maps tint from getColorIndex() != -1
 -> maps shade from hasShade()
 -> approximates DARK_CUTOUT by checking Embeddium sprite transparency plus
    sprite path containing "leaves"

src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java
 -> FLAG_DARKENED depends on anyDarkenedTex from that bridge
```

Why this is a problem:

This is not an exact original `materialInfo()` bridge. The dark-cutout flag is
especially approximate: original Voxy uses the sprite mipmap strategy, while
Forge guesses from transparency and a leaves path substring. That can mis-tag
or miss darkened cutout textures, which affects model bake flags and can change
the appearance of leaves/vegetation-heavy LoD. The bridge also depends on
Embeddium internal class names instead of a stable public API.

Required comparison:

```text
Embeddium 1.20.1 BakedQuadView material data equivalents
SpriteTransparencyLevelHolder vs original MipmapStrategy.DARK_CUTOUT semantics
ForgeOriginalVoxyModelFactory flag interpretation
leaf/cutout bake samples under day/night and shaderpack paths
```

### P2: Forge model baking rejects custom or unknown RenderType layers

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java
isKnownRenderLayer(...)
 -> accepts only solid, cutout, cutoutMipped, tripwire, translucent
 -> throws unsupported-render-layer for anything else

src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java
renderBlock(...)
 -> catches the exception and returns 0 for the model bake
```

Why this is a problem:

Original Voxy's Fabric-side path consumes Minecraft's `ChunkSectionLayer`
material info. Forge mods can expose custom `RenderType` values through model
render types. The current bridge can therefore silently drop those models from
LoD by returning an empty bake, instead of documenting an unavoidable platform
blocker or mapping the custom layer into the closest original material bucket.

Required comparison:

```text
Forge model getRenderTypes(...) outputs for vanilla and common modded blocks
Embeddium chunk layer normalization before BakedQuadView emission
original ChunkSectionLayer material buckets
documented unsupported RenderType blocker list
```

### P2: Forge async node/geometry sync is a reimplementation with a result-merge behavior drift

Evidence:

```text
src/main/java/me/cortex/voxy/client/core/rendering/hierachical/AsyncNodeManager.java
 -> if previous SyncResults is null:
      create a fresh result set
      upload new geometry copies
      clear heap removals because there is no pending result to cancel
 -> if previous SyncResults exists:
      merge removals into the existing pending upload set

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyAsyncNodeGeometrySync.java
 -> publishSyncResults() always calls mergeGeometryEvents(sync)
 -> mergeGeometryEvents() always folds heap removals into sync.geometryUpload
    regardless of whether sync was a fresh result or a previous pending result
```

Why this is a problem:

In original Voxy, heap removals cancel uploads that have not yet reached the
render thread. If there is no pending `SyncResults`, a removal does not need to
remove anything from the new upload-copy batch; the metadata invalidation is
handled separately. Forge's current behavior is usually a no-op when the upload
point is absent, but it is not the original merge contract and makes geometry
event ordering harder to reason about during black-LoD or stale-geometry
debugging.

Required comparison:

```text
AsyncNodeManager.run() result publication branch
ForgeOriginalVoxyAsyncNodeGeometrySync.publishSyncResults()
ForgeOriginalVoxyAsyncNodeGeometrySync.mergeGeometryEvents()
ComputeMemoryCopy.remove()
metadata invalidation writes for removed sections
```

Repair status, 2026-06-23:

```text
fixed for result-publication merge branch parity
```

Evidence:

```text
original AsyncNodeManager.run()
 -> prev == null creates a fresh SyncResults, uploads new geometry, then clears
    heap removals because there is no previous pending upload set to cancel.
 -> prev != null merges heap removals into the previous pending upload set
    before appending new uploads.

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyAsyncNodeGeometrySync.java
 -> publishSyncResults() now passes whether a previous pending result existed.
 -> mergeGeometryEvents(...) now removes heap removals from geometryUpload only
    when merging with a previous pending result; for fresh result publication it
    clears heap removals after accepting the current upload batch, matching the
    original branch.
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

Remaining risk:

```text
This fixes the static merge-contract drift. It does not prove runtime geometry
capacity/headroom behavior, HOC traversal cadence, or visible shader output.
```

### P2: Forge render-state restore is incomplete for an Embeddium hook insertion

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
captureOriginalVoxyRenderState()
 -> captures framebuffer, viewport, SSBO bindings, texture/sampler bindings,
    program, VAO, depth/stencil/blend/cull enabled flags, depth mask,
    color mask, active texture

restoreOriginalVoxyRenderState()
 -> restores only that captured subset

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java
 -> applyTerrainDepthStencilState() mutates depth func, stencil op/func/mask
 -> postOpaquePreTranslucentOculus() mutates depth func, stencil func, color mask
 -> finish()/finishOculus()/transformBlitDepth() mutate blend, depth/stencil,
    framebuffer, texture bindings, and color mask
```

Why this is a problem:

Original Voxy owns its render pipeline in its own render stage. Forge injects
that work into an Embeddium cutout hook, so any GL state not restored can leak
into Minecraft, Embeddium, Oculus, or later shaderpack passes. The previously
observed polygon/grid issue was fixed by forcing fill mode around terrain draw,
which is direct evidence that GL-state leakage can reach visible output. The
current restore still does not cover depth func, stencil func/op/mask, blend
function/equation, polygon mode, cull mode, draw buffers, or other mutable GL
state changed by the pipeline.

Required comparison:

```text
original Voxy render stage ownership assumptions
Embeddium 1.20.1 terrain pass GL-state contract
Oculus shaderpack pass GL-state contract
all GL mutations in ForgeOriginalVoxyRenderPipeline and ForgeOriginalVoxyMdicSectionRenderer
restoreOriginalVoxyRenderState() coverage
```

Repair status, 2026-06-23:

```text
One confirmed native crash vector was repaired:
- The latest new-world failure produced hs_err_pid10820.log with
  EXCEPTION_ACCESS_VIOLATION in nvoglv64.dll from the render thread while
  Minecraft's GameRenderer was taking the automatic world screenshot through
  NativeImage.downloadTexture/glGetTexImage.
- The directly preceding Voxy Forge atlas readback path
  ForgeSoftwareModelTextureBakery.setupTexture() changed pixel pack row length,
  pack skips, pack alignment, pixel-pack-buffer binding, and framebuffer binding
  without restoring them.
- The Forge adapter now snapshots and restores those states around
  ARBDirectStateAccess.glGetTextureImage().
- This does not claim the broader GL-state restore item is complete; terrain
  draw state, shaderpack target state, and post-frame state still need the
  existing detailed audit.
```

### P2: Geometry buffer readiness/status does not prove valid uploaded sections

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyBasicSectionGeometryData.java
createStatusSnapshot()
 -> ready is true when metadata and geometry buffer ids are nonzero
 -> sectionCount is updated later from SyncResults.geometrySectionCount

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
renderEmbeddiumCutout()
 -> frameHadGeometry = geometryData.sectionCount() > 0
 -> draw-submission status is derived from sectionCount and target readiness
```

Why this is a problem:

Buffer allocation only proves that the storage objects exist. It does not prove
that geometry uploads, metadata scatter writes, HOC traversal, command
generation, and draw submission all operated on valid LoD sections in that
frame. This does not directly cause black LoD, but it can make status/debug
output claim that the formal path is drawing when the actual section/command
payload is empty, stale, or shader-invisible.

Required comparison:

```text
BasicSectionGeometryData.setSectionCount()
AsyncNodeManager.tick()
MDICSectionRenderer command count/readback semantics
ForgeOriginalVoxyMdicSectionRenderer visible draw counters
status fields exposed through ForgeVoxyParityCommands
```

### P2: Geometry capacity/headroom logic is Forge-adapted and not proven byte-parity

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyBasicSectionGeometryData.java
computeOriginalGeometryCapacityBytes()
 -> adds NVIDIA Linux cap, GPU memory headroom, NVX available-memory limit,
    and a system-property override

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyAsyncNodeGeometrySync.java
hasGeometryCapacityHeadroom()
 -> uses GEOMETRY_UPLOAD_HEADROOM_BYTES and current Forge manager stats
```

Why this is a problem:

Some of this may be necessary defensive behavior on Forge/Windows drivers, but
it is not currently documented as an unavoidable platform adaptation. Capacity
and upload-headroom drift can change when geometry jobs are accepted, deferred,
or starved. In visual terms this can look like missing LoD islands, delayed
updates while moving, or draw counts lagging behind world ingestion.

Required comparison:

```text
original BasicSectionGeometryData geometry capacity calculation
original AsyncNodeManager upload limits/headroom
Forge GPU/driver adaptation rationale
runtime status for geometry OOM or upload starvation
```

### P2: Forge-owned service thread pool and section saving service have no shutdown owner

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java
 -> private final ForgeOriginalVoxyModelPipeline originalVoxyModelPipeline
 -> private final SectionSavingService originalVoxySectionSavingService =
        new SectionSavingService(originalVoxyModelPipeline.getServiceManager())
 -> onClientLogout(...) calls originalVoxyModelPipeline.markWorldUnload()
 -> closeActiveWorld() eventually frees active WorldEngine
 -> no shutdown call for originalVoxySectionSavingService
 -> no shutdown call for originalVoxyModelPipeline's UnifiedServiceThreadPool

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> private final UnifiedServiceThreadPool serviceThreadPool = new UnifiedServiceThreadPool()
 -> markStaleAndClear(...) shuts renderGeneration, factory, geometry, render resources
 -> does not shut serviceThreadPool

src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java
 -> shutdown() exists
 -> CodeGraph callers show only original commonImpl/VoxyInstance.shutdown() calls it

src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java
 -> shutdown() drains and shuts the saving service
 -> CodeGraph callers show original commonImpl/VoxyInstance.shutdown() uses this owner pattern,
    but ForgeVoxyInstance does not call shutdown on its own section saving service
```

Why this is a problem:

Original Voxy's `VoxyInstance.shutdown()` explicitly shuts ingest, saving, and
the unified service thread pool after world teardown. The Forge port keeps a
singleton `ForgeVoxyInstance` and clears the active world on logout/dimension
changes, but the dedicated service pool and original section saving service are
not owned by a matching shutdown path. This can leave background services and
permits alive across logout/new-world cycles, and it makes new-world freezes or
late section-save work harder to reason about.

Required comparison:

```text
Forge client shutdown/disconnect events that can safely drain services
original VoxyInstance.shutdown() ordering
SectionSavingService.shutdown() interaction with active WorldEngine.free()
whether ForgeOriginalVoxyModelPipeline needs a terminal shutdown distinct from
world/reload markStaleAndClear(...)
```

Repair status, 2026-06-23:

```text
Partially fixed.

Forge 1.20.1 provides net.minecraftforge.event.GameShuttingDownEvent, documented
as firing on the physical client while the GL context is still valid. The Forge
port now registers this event in ForgeVoxyInstance and runs an idempotent
shutdown path:

1. disable VoxelIngestService auto-ingest;
2. clear world-scoped managers and reload state;
3. ask ForgeOriginalVoxyModelPipeline to run client-stop cleanup;
4. close the active WorldEngine into the closing queue;
5. if render cleanup completed synchronously on the render thread, drain
   SectionSavingService, immediately free no-longer-used closing worlds for
   shutdown, and shut down the original UnifiedServiceThreadPool.

The implementation deliberately skips terminal service-thread shutdown when
render cleanup was deferred off the render thread, because shutting the service
manager before queued GL/resource cleanup would race original render-generation
service teardown. Full VoxyRenderSystem shutdown-order parity and runtime proof
of the Forge event thread remain open.

Validation:
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

### P2: Queued render-thread start/cleanup tasks have no lifecycle generation guard

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> clientTick() can call runOnRenderThread(this::startOnRenderThread)
 -> runOnRenderThread(...) uses RenderSystem.recordRenderCall(...) when not on
    the render thread
 -> markStaleAndClear(...) sets startRequested=false and startQueuedOnRenderThread=false
 -> markStaleAndClear(...) can also queue cleanup work through runOnRenderThread(...)
 -> startOnRenderThread() does not verify that the start request is still current
    before allocating model store, render generation, geometry data, traversal,
    MDIC renderer, render pipeline, and world ref
```

Why this is a problem:

Original Voxy creates and shuts down `VoxyRenderSystem` from the `LevelRenderer`
owner in a synchronous owner-controlled path. The Forge port splits start and
cleanup across `ForgeVoxyInstance`, client tick, and render-thread queued work.
If a start task was queued before resource reload, logout, dimension switch, or
Oculus settings reload marked the owner stale, there is no generation token or
still-requested check at the start of `startOnRenderThread()`. A stale queued
start can therefore build resources after the route was cancelled, or race with
queued cleanup/start work around world entry. This is a lifecycle correctness
risk for the new-world crash/freeze symptoms even when individual resource free
calls are otherwise valid.

Required comparison:

```text
original MixinLevelRenderer allChanged/setLevel/close renderer creation order
original VoxyRenderSystem constructor/shutdown ownership
ForgeOriginalVoxyModelPipeline requestStart/shouldScheduleStart/startOnRenderThread
ForgeOriginalVoxyModelPipeline markStaleAndClear render-thread cleanup ordering
whether Forge should carry a lifecycle generation through every queued render call
```

Repair status, 2026-06-23:

```text
fixed for queued start/install/cleanup and factory-upload stale-generation
guards
```

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> requestStart(), markStaleAndClear(), and recordFailure() now advance a
    lifecycleGeneration token
 -> scheduled start work captures the token and bails out if the token no
    longer matches before start or before owner install
 -> stale queued cleanup still frees captured old resources, but does not flush
    global DownloadStream state after a newer owner is active
 -> queued model factory uploads check that their captured factory still belongs
    to the live owner
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

Remaining risk:

```text
The broader P1 full VoxyRenderSystem owner gap remains. This fix guards the
current split Forge owner but does not yet replace it with the original
VoxyRenderSystem lifecycle.
```

### P2: Physical-server classloading boundary is not explicitly sealed

Evidence:

```text
src/main/resources/META-INF/mods.toml
 -> Forge and Minecraft dependencies are side="BOTH"
 -> Embeddium and Oculus dependencies are mandatory but side="CLIENT"
 -> no metadata-level statement prevents the Voxy Forge mod class itself from
    being loaded on a dedicated server

src/main/java/me/cortex/voxy/forge/VoxyForge.java
 -> top-level mod class imports RegisterClientReloadListenersEvent and FMLClientSetupEvent
 -> listener registration is guarded by DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)
 -> registerForgeMixinConfig() still runs unconditionally

src/main/java/me/cortex/voxy/forge/**
 -> the Forge source set contains many direct net.minecraft.client, Blaze3D,
    LWJGL, Embeddium, and Oculus references
```

Why this is a problem:

The project is a client renderer and Embeddium/Oculus are hard client
prerequisites, so this is not a gameplay-side bug. But the current package is
not proven safe if the Forge mod is accidentally placed on a dedicated server:
the main mod class and included Forge package contain client-only descriptors
and implementation types, while only some listener registration is dist-guarded.
If the physical-server loader resolves those descriptors or the unconditionally
registered mixin config in an unexpected order, startup can fail before any
client-only guard runs.

Required comparison:

```text
Forge 1.20.1 client-only mod metadata conventions
whether runServer with the built jar succeeds or fails
whether VoxyForge should avoid client event types in method descriptors on the
top-level @Mod class
whether all client-only initialization should live behind a physically client
class loaded only from DistExecutor
```

Partial repair status, 2026-06-23:

```text
top-level client event descriptors removed
```

Evidence:

```text
src/main/java/me/cortex/voxy/forge/VoxyForge.java
 -> no longer imports RegisterClientReloadListenersEvent or FMLClientSetupEvent.
 -> the top-level @Mod constructor now registers a client-only helper through
    DistExecutor, leaving client event method descriptors off the @Mod class.

src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java
 -> owns the client setup and client resource-reload listener methods.
 -> keeps ForgeVoxyInstance client initialization behind the client-only event
    registration path.
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

Remaining risk:

```text
The project is still a client renderer with mandatory client frontends. This
does not prove physical-server load compatibility; metadata-side client-only
policy, unconditional mixin config registration, and runServer behavior still
need a separate decision.
```

### P2: Required internal Embeddium/Oculus mixins have broad unbounded dependency ranges

Evidence:

```text
src/main/resources/voxy.forge.mixins.json
 -> required=true
 -> injectors.defaultRequire=1
 -> targets internal Embeddium/Oculus classes and exact method/field shapes:
    RenderSectionManager, ChunkJobQueue, DefaultChunkRenderer,
    ProgramSet, IrisRenderingPipeline, IrisSamplers, RenderTargets,
    WorldRenderingSettings, StandardMacros, MatrixUniforms, CustomUniforms

gradle.properties
 -> embeddium_version_range=[0.3.31,)
 -> oculus_version_range=[1.8.0,)

src/main/resources/META-INF/mods.toml
 -> Embeddium/Oculus are mandatory client dependencies with those ranges
```

Why this is a problem:

The Forge port relies on exact non-public frontend implementation details, not
stable public APIs. A required mixin with `defaultRequire=1` is appropriate when
the exact tested frontend build is part of the contract, but the dependency
ranges currently accept every later Embeddium/Oculus version. A compatible
version range that is broader than the internal mixin target contract can turn
ordinary frontend updates into hard startup crashes or silent hook drift.

Required comparison:

```text
exact Embeddium/Oculus versions used by the local source folders and dev jars
Forge mods.toml version range semantics for client mandatory dependencies
whether mixin targets should be guarded by plugin-side applicability checks or
the dependency ranges should be narrowed to tested versions
```

### P3: Visible draw-submission status can be true before draw counts are proven non-zero

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
renderEmbeddiumCutout(...)
 -> frameHadGeometry = geometryData.sectionCount() > 0
 -> opaqueSubmitted = frameHadGeometry && renderPipeline.opaqueDrawTargetReady()
 -> temporalSubmitted = frameHadGeometry && renderPipeline.opaqueDrawTargetReady()
 -> translucentSubmitted = frameHadGeometry && renderPipeline.translucentDrawTargetReady()
```

Why this is a problem:

These status booleans prove target readiness plus non-empty geometry data, not
actual non-zero MDIC draw counts. During black-LoD or missing-water debugging,
status can therefore overstate that visible terrain was submitted. The
renderer should report draw-output evidence from the MDIC draw count/readback
path separately from "target existed and section data existed."

Required comparison:

```text
ForgeOriginalVoxyMdicSectionRenderer draw count fields/readback audit
status fields exposed through ForgeVoxyParityCommands
documentation wording for originalVisibleMdicDrawSubmissionUsed
```

Repair status, 2026-06-23:

```text
fixed for status overstatement
```

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyMdicSectionRenderer.java
 -> now exposes readback-backed opaque, temporal opaque, and translucent draw
    count predicates.

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> originalVisibleMdicDrawSubmissionUsed and the per-pass submitted booleans
    now come from those MDIC draw-count readback predicates, not from
    geometryData.sectionCount() plus target readiness.
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

Remaining risk:

```text
This is a conservative status repair only. It does not prove that black LoD or
missing water are fixed; it prevents status output from claiming draw
submission before a non-zero MDIC draw count has been read back.
```

### P3: Startup logs described the port as disabled skeleton

Evidence:

```text
src/main/java/me/cortex/voxy/forge/VoxyForge.java
 -> "Voxy Forge 1.20.1 skeleton loaded. LoD rendering and shader integration are disabled."
 -> "Voxy Forge client skeleton ready."
```

Why this is a problem:

The active route now starts original-shaped model/render/MDIC/Oculus owners, so
these logs are stale. They are not a rendering bug, but they make `latest.log`
evidence harder to interpret during exactly the kind of runtime testing the
user is doing. If the renderer starts and later fails, the first log line still
claims rendering/shader integration are disabled.

Required correction:

```text
replace with current parity-route wording
include enabled/config state and frontend presence only if already safe to read
avoid claiming renderer readiness
```

Repair status, 2026-06-23:

```text
fixed
```

Evidence:

```text
src/main/java/me/cortex/voxy/forge/VoxyForge.java
 -> startup log now says the original-parity route is loaded while formal
    renderer readiness remains gated by runtime validation.
 -> client setup log now says parity adapters are registered rather than
    calling the active path a skeleton.
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

### P3: Documentation still contains old-stage wording that can mislead validation or route selection

Evidence:

```text
docs/forge-1.20.1-runclient-quickplay.md
 -> still frames the workflow as "QA1" and "does not advance H-stage renderer
    functionality"

docs/forge-1.20.1-stage-roadmap.md
docs/forge-1.20.1-k0-original-voxy-renderer-alignment-audit.md
docs/forge-1.20.1-formal-renderer-readiness-audit.md
docs/forge-1.20.1-formal-mdic-renderer-integration-plan.md
docs/forge-1.20.1-direct-gl-heap-renderer-plan.md
 -> first lines now contain superseded/current-route warnings, but their bodies
    still need occasional spot checks before they are used as implementation
    evidence
```

Why this is a problem:

AGENTS.md says old alphanumeric and preview-era notes are historical only. The
most dangerous remaining doc issue is not only missing banners; it is old-stage
wording that can make validation or implementation planning look like it still
belongs to H/K/QA-era work. Every workflow doc should name the current Roman
round/original-parity route when it is still actionable.

Repair status, 2026-06-23:

```text
runClient quick-play document fixed
```

Evidence:

```text
docs/forge-1.20.1-runclient-quickplay.md
 -> rewritten as a runtime-validation helper for the current Roman-round
    original Voxy parity route.
 -> removed QA1/H-stage wording.
 -> records RTK log usage and the rule that manual screenshots/commands are
    runtime evidence, not a formal implementation path.
```

Remaining risk:

```text
Other superseded old-stage documents still have warning banners but may need
spot checks before they are used as implementation evidence.
```

### P3: Forge BuiltSection verification flag does not use the original verification flag owner

Evidence:

```text
original BuiltSection
 -> VERIFY_BUILT_SECTION_OFFSETS = VoxyCommon.isVerificationFlagOn("verifyBuiltSectionOffsets")

ForgeOriginalVoxyBuiltSection
 -> VERIFY_BUILT_SECTION_OFFSETS = Boolean.getBoolean("voxy.verifyBuiltSectionOffsets")
```

Why this is a problem:

This is not a visible rendering bug by itself, but it means a verification flag
that should follow Voxy's central verification-flag system now uses a different
raw JVM property name. Offset verification can therefore be silently disabled
when the original verification flag is enabled, or enabled through a Forge-only
name that project docs do not describe.

Required comparison:

```text
VoxyCommon.isVerificationFlagOn(...) semantics
all ForgeOriginalVoxy* verification flags
RTK/runClient debug property documentation
```

Investigation status, 2026-06-23:

```text
not changed in code
```

Reason:

```text
VoxyCommon.isVerificationFlagOn("verifyBuiltSectionOffsets") and
Boolean.getBoolean("voxy.verifyBuiltSectionOffsets") read the same JVM system
property with the same default-false semantics for the current flag.

Directly importing VoxyCommon from the Forge adapter would also run
VoxyCommon's FabricLoader-backed static initializer in a Forge process, which
is not a safe parity repair without first adding a Forge-safe common
verification owner. This item is therefore a shared-owner cleanup task, not a
current rendering fix.
```

### P3: Forge Embeddium service-thread sharing status conflates requested and available states

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java
 -> select() returns useEmbeddiumBuilderThreads and embeddiumBuilderThreadCountAvailable separately
 -> queryEmbeddiumBuilderThreads() can return available=false with source such as
    embeddium-renderer-not-attached or embeddium-render-section-manager-not-ready

src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> updateDedicatedThreads()
 -> originalEmbeddiumBuilderThreadSharingEnabled = selection.useEmbeddiumBuilderThreads()
 -> originalEmbeddiumBuilderThreadSharingReady = selection.useEmbeddiumBuilderThreads()
 -> createEmbeddiumBuilderSemaphoreBlock() gates only on originalEmbeddiumBuilderThreadSharingEnabled
```

Why this is a problem:

Original Voxy also lets Sodium builder queues share the Voxy semaphore block
when configured, and later updates the dedicated Voxy thread count after Sodium
renderer init. So the semaphore interception itself is not automatically wrong.
The Forge status layer is still misleading: it reports builder-thread sharing
ready when the current policy may have failed to read the Embeddium builder
thread count. That can hide thread-count subtraction failures and make runtime
diagnostics claim parity when the Forge path is only using the requested config,
not a confirmed Embeddium builder-thread count.

Required comparison:

```text
original VoxyClientInstance.updateDedicatedThreads()
original MixinChunkJobQueue semaphore-block insertion
ForgeOriginalVoxyServiceThreadPolicy.Selection fields
ForgeOriginalVoxyModelPipeline status snapshot fields for builder-thread sharing
```

Repair status, 2026-06-23:

```text
fixed for status readiness
```

Evidence:

```text
src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java
 -> originalEmbeddiumBuilderThreadSharingEnabled still reports the user/config
    request.
 -> originalEmbeddiumBuilderThreadSharingReady now requires both the sharing
    request and a confirmed Embeddium builder thread count from
    ForgeOriginalVoxyServiceThreadPolicy.Selection.
```

Validation:

```text
rtk test .\gradlew compileJava -> passed on 2026-06-23
```

## Checked and currently excluded as direct causes

### Light-byte nibble order is not currently a proven Forge-only bug

Evidence:

```text
src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java
 -> getLightingSupplier(...) returns sky | (block << 4)

src/main/java/me/cortex/voxy/commonImpl/importers/DHImporter.java
 -> Mapper.withLight(..., (getBlockLight(entry) << 4) | getSkyLight(entry))

src/main/java/me/cortex/voxy/common/world/other/Mapper.java
 -> getLightId(...) stores/returns the full byte without swapping nibbles
```

Audit note:

The Forge runtime ingest path and original DH import path both pack light as
low nibble sky, high nibble block. The user's black-LoD symptom still points at
the light/shader contract, but this specific "sky/block nibble swapped" theory
is not supported by the current source comparison.

## High-risk areas still to scan

```text
ForgeOriginalVoxyOculus* vs Oculus 1.20.1 source
ForgeOriginalVoxyRenderPipeline vs original IrisVoxyRenderPipeline
ForgeOriginalVoxyMdicSectionRenderer vs original MDICSectionRenderer
ForgeOriginalVoxyRenderDataFactory vs original RenderDataFactory
VoxelIngestService lighting/data-layer flow vs original WorldConversionFactory
ForgeOriginalVoxyModelFactory/Store vs original ModelFactory/ModelStore
thread ownership and shutdown order
GL state capture/restore around Embeddium/Oculus hooks
resource reload and shaderpack reload races
server-side classloading/client-only dependency boundaries
remaining docs and command/status surfaces
```

## Eight-point deep scan progress, 2026-06-23

This section tracks the follow-up request to scan the eight still-open areas in
detail. Keep this updated before moving between large audit areas so progress
survives context compaction.

```text
1. ForgeOriginalVoxyModelFactory / ModelStore field-level parity: first pass done
2. ForgeOriginalVoxyRenderDataFactory input/output parity: first pass done
3. RenderGenerationService queues, threads, shutdown, resource release: first pass done
4. Shaderpack output chain draw targets/samplers/uniforms/framebuffers: first pass done
5. Embeddium hook insertion vs original Sodium hook lifecycle: first pass done
6. reload / world detach / new-world crash path: first pass done
7. client-only classloading, mixin failure, hard dependency boundaries: first pass done
8. old-stage documentation superseded/deprecated sweep: first pass done
```

### Eight-point scan note: ModelFactory / ModelStore

Deep-scan status:

```text
ForgeOriginalVoxyModelFactory.addEntry/processModelResult/buildRecord/buildVoxyMetadata
ForgeOriginalVoxyModelFactory.ModelBakeUpload/BiomeUpload
ForgeOriginalVoxyModelStore build/bind/upload/readback/free
ForgeOriginalVoxyModelStoreLayoutSpec
original ModelFactory.processTextureBakeResult/addBiome0/upload
original ModelStore constructor/buffer/atlas/sampler layout
```

Current result:

The model record layout, face-data packing, metadata bits, `fullyOpaque` bit,
block light emission bits, biome-colour LUT upload, model atlas tile formula,
and model-store buffer sizes are broadly aligned with the original source. The
earlier suspicion that Forge added non-original `fullyOpaque` or block-light
metadata is excluded: original `ModelFactory` writes the same upper metadata
bits.

Remaining risk carried forward:

Forge still owns model baking through `ForgeOriginalVoxyModelPipeline`'s custom
processor thread and upload calls instead of the original outer
`ModelBakerySubsystem` owner. That is not a field-layout mismatch by itself,
but it remains part of the lifecycle/threading parity scan below.

### Eight-point scan note: RenderDataFactory

Deep-scan status:

```text
ForgeOriginalVoxyRenderDataFactory.prepareSectionData
ForgeOriginalVoxyRenderDataFactory.acquireNeighborData
ForgeOriginalVoxyRenderDataFactory.generateMesh
ForgeOriginalVoxyRenderDataFactory fluid/non-opaque mask preparation
ForgeOriginalVoxyRenderDataFactory expandBits/expandBitsLong Java 17 shims
ForgeOriginalVoxyBuiltSection
original RenderDataFactory corresponding sections
original BuiltSection
```

Current result:

`prepareSectionData`, light/biome packing, fully-opaque light clearing, fluid
masking, non-opaque masking, neighbor acquisition, and output section packing
are mechanically aligned with original Voxy after accounting for class-name
changes and Java 17 replacements for Java 21 `Integer.expand` / `Long.expand`.
The helper implementations match the original expand semantics: low input bits
are placed into the set-bit positions of the mask.

New issue found:

The built-section verification flag uses a Forge-only raw system property
instead of the original `VoxyCommon.isVerificationFlagOn(...)` owner. That is
tracked above as a P3 finding.

### Eight-point scan note: RenderGeneration / Threads / Shutdown

Deep-scan status:

```text
ForgeOriginalVoxyRenderGenerationService.enqueueTask/processJob/handleMissingModel/shutdown
original RenderGenerationService.enqueueTask/processJob/shutdown
ServiceManager / Service permit semantics
ForgeOriginalVoxyModelPipeline.startOnRenderThread/markStaleAndClear
ForgeOriginalVoxyModelPipeline.startProcessingThread/stopProcessingThread
UnifiedServiceThreadPool shutdown callers
ForgeVoxyInstance login/logout/dimension switch/world close
SectionSavingService shutdown owner
```

Current result:

The Forge render-generation queue, missing-model request/requeue behavior,
held-section policy, and task-map removal are a close rewrite of original
`RenderGenerationService`. The direct `taskQueue.poll()` after service permit
acquisition is inherited from original service semantics, not a new Forge-only
empty-queue bug.

New issue found:

Forge creates an original-route `SectionSavingService` and a pipeline-owned
`UnifiedServiceThreadPool` without a terminal shutdown owner. That is tracked
above as a P2 finding because it can leave service state alive across
logout/new-world cycles.

### Eight-point scan note: Shaderpack / Oculus output chain

Deep-scan status:

```text
ForgeOriginalVoxyOculusShaderPatch makePatch/collectRequestedRenderTargets/checkValid
original IrisShaderPatch makePatch/checkValid/blending/samplers/SSBO parsing
ForgeOriginalVoxyOculusProgramSetMixin render-target table redirect
ForgeOriginalVoxyOculusStandardMacrosMixin VOXY define exposure
ForgeOriginalVoxyOculusIrisRenderingPipelineMixin pipeline-data capture
ForgeOriginalVoxyOculusRenderPipelineData draw buffers/uniforms/images/SSBOs
ForgeOriginalVoxyRenderPipeline Oculus setup/postOpaque/finish/bind/header path
ForgeOriginalVoxyNormalPipelineTargets external draw-target framebuffer attachment
original IrisVoxyRenderPipeline constructor/setup/postOpaque/finish/bind/header path
local Oculus 1.20.1 ProgramSet / IrisRenderingPipeline / RenderTargets sources
```

Current result:

The Voxy `voxy.json` schema, patch-source loading, blending validation, uniform
layout packing, SSBO binding base, image/sampler binding base, opaque/translucent
draw-target texture selection, framebuffer attachment order, and draw-buffer
order are mechanically close to original Iris Voxy after accounting for Oculus
1.20.1 API differences. The local Oculus `ProgramSet` constructor builds
`PackDirectives` before `locateDirectives()`, so the Forge redirect that merges
Voxy-requested high-index colortex targets into
`PackRenderTargetDirectives.BASELINE_SUPPORTED_RENDER_TARGETS` is a necessary
platform adapter rather than a preview shortcut. The `RenderTargets` sparse-table
expansion also matches the local Oculus failure mode: Oculus sizes its target
array from the supported-target map, then later rejects draw buffers whose index
is outside `getRenderTargetCount()`.

Issues still standing from this area:

```text
P1: full shaderpack patch exposure is enabled even though visual-output parity is not proven.
P1: first-texture status/output helpers can misrepresent multi-target shaderpack output.
P2: Oculus dynamic uniform construction is adapted to Oculus 1.20.1 and not byte-parity proven.
P2: lightmap texture binding can silently bind texture 0 without status evidence.
P2: Forge's split outer pipeline must restore GL state because it does not reuse AbstractRenderPipeline.runPipeline().
```

Excluded in this pass:

```text
JSON patch parsing is not currently a confirmed Forge-only bug.
Unsupported default sampler requests are inherited from original Iris Voxy.
Framebuffer attachment order for shaderpack draw targets is not currently a confirmed bug.
Binding bases 7/10/6 for UBO/SSBO/samplers match original Iris Voxy.
```

### Eight-point scan note: Embeddium hook insertion / Sodium lifecycle parity

Deep-scan status:

```text
ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin constructor/update/add/remove/updateSectionInfo hooks
ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin CUTOUT render hook
ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin semaphore-block redirect/shutdown
local Embeddium RenderSectionManager / RenderSection / DefaultChunkRenderer / ChunkJobQueue sources
original MixinRenderSectionManager / MixinDefaultChunkRenderer / MixinChunkJobQueue / MixinSodiumWorldRenderer
ForgeOriginalVoxyModelPipeline renderEmbeddiumCutout/chunk-bound tracker/thread policy
original VoxyRenderSystem.renderOpaque and AbstractRenderPipeline.runPipeline call order
original VoxyClientInstance.updateDedicatedThreads
```

Current result:

The Forge CUTOUT hook is placed at the same broad point as original Voxy's Sodium
hook: immediately before `ShaderChunkRenderer.end(...)` for the cutout terrain
pass. The Forge path cannot call original `VoxyRenderSystem.renderOpaque()`
because the outer `VoxyRenderSystem` owner is still missing, so it routes through
`ForgeOriginalVoxyModelPipeline.renderEmbeddiumCutout(...)`. That confirms the
existing P1 lifecycle finding rather than creating a separate new draw hook bug.

Issues still standing from this area:

```text
P1: full outer VoxyRenderSystem lifecycle is still missing.
P2: chunk-remove ingest may miss the last loaded chunk snapshot because Forge uses ordinary getChunk(..., false).
P2: Embeddium mixin chunk/section ingest bypasses the staged enableAutoChunkIngest gate.
P2: render-state restore remains Forge-owned because the original AbstractRenderPipeline.runPipeline owner is not reused.
P3: service-thread sharing status reports config-requested sharing as ready even when builder thread count was not confirmed.
```

Excluded in this pass:

```text
Forge RenderSection.setInfo redirect shape matches Embeddium 1.20.1's void setInfo API.
CUTOUT hook placement before ShaderChunkRenderer.end(...) matches the original Sodium hook placement at the same broad lifecycle point.
Semaphore-block interception is not automatically wrong; original Voxy also intercepts Sodium ChunkJobQueue when builder-thread sharing is enabled.
```

### Eight-point scan note: Reload / World Detach / New-world Crash Path

Deep-scan status:

```text
ForgeVoxyInstance onClientLogin/onClientLogout/onClientTick/dimension switch/closeActiveWorld/drainClosingWorlds
ForgeOriginalVoxyModelPipeline clientTick/requestStart/startOnRenderThread/markStaleAndClear/markResourceReload/markWorldUnload/markDimensionSwitch
ForgeModelBridgeResourceReloadTracker handleReload
ForgeOculusWorldRenderingSettingsBridge isReloadRequired/getBlockStateIds
local Oculus WorldRenderingSettings reloadRequired/clearReloadRequired
local Oculus PipelineManager pipeline creation reloadRequired consumption
original MixinLevelRenderer allChanged/setLevel/close renderer creation/shutdown
original VoxyRenderSystem constructor/shutdown
original VoxyInstance world cache, idle cleaner, SectionSavingService and thread-pool shutdown
```

Current result:

The Forge route releases the old `WorldEngine` reference from the render-thread
cleanup path after model/render resources are freed, and the closing-world queue
waits for `WorldEngine.isWorldIdle()` before calling `free()`. That broad
ref-count shape matches the original idea that worlds may stay alive until
renderer and active sections release them.

New issues found:

```text
P1: Forge observes Oculus WorldRenderingSettings.reloadRequired but does not consume or version it.
P2: queued render-thread start/cleanup tasks have no lifecycle generation guard.
```

Existing issues reinforced:

```text
P1: full outer VoxyRenderSystem lifecycle is still missing.
P1: active Forge WorldEngine still uses MemoryStorageBackend instead of original configured persistent storage.
P2: Forge-owned SectionSavingService and UnifiedServiceThreadPool still have no terminal shutdown owner.
```

Excluded in this pass:

```text
Keeping an old WorldEngine alive briefly after logout/dimension switch is not itself a bug; original Voxy also keeps worlds in an active-world cache and frees them only after isWorldIdle().
WorldEngine ref acquisition/release around the Forge renderer owner is not obviously inverted: Forge acquires the world ref only after render resources are successfully created, and releases it during cleanup.
```

### Eight-point scan note: Client-only Classloading / Mixin Failure / Dependency Boundaries

Deep-scan status:

```text
META-INF/mods.toml dependencies and mixin registration
build.gradle sourceSets/resource includes, jar manifest MixinConfigs, dev frontend dependency wiring
voxy.forge.mixins.json required/defaultRequire/client mixin list
common.voxy.mixins.json, client.voxy.mixins.json, fabric.mod.json resource presence
VoxyForge entrypoint and DistExecutor listener registration
ForgeFrontendCompat hard frontend status reporting
Forge mixin target classes for Embeddium, Oculus, and LevelRenderer
direct client/LWJGL/Blaze3D import scan under src/main/java/me/cortex/voxy/forge
```

Current result:

Forge runtime registration points at `voxy.forge.mixins.json`, and the Gradle
resource include list only packages the Forge mixin config plus Forge metadata
and shader resources. The old Fabric/common mixin json files remain in the
source tree but are not included in the Forge source set resources by the
current `build.gradle` filters.

New issues found:

```text
P2: physical-server classloading boundary is not explicitly sealed.
P2: required internal Embeddium/Oculus mixins have broad unbounded dependency ranges.
```

Repair status, 2026-06-23:

```text
Partially fixed.

VoxyForge no longer calls Mixins.addConfiguration("voxy.forge.mixins.json")
manually from the top-level @Mod constructor. The Forge mixin config remains
declared through META-INF/mods.toml and the jar manifest, while the client event
handlers stay behind DistExecutor client registration.

The internal frontend dependency ranges were narrowed from unbounded upper
ranges to:

embeddium_version_range=[0.3.31,0.4)
oculus_version_range=[1.8.0,1.9)

Validation:
rtk test .\gradlew compileJava -> passed on 2026-06-23
rtk test .\gradlew processResources -> passed on 2026-06-23

Still open: explicit metadata-side physical-server policy and runtime testing
against the packaged dependency metadata.
```

Excluded in this pass:

```text
Embeddium and Oculus being mandatory client dependencies is not itself a bug under the current AGENTS.md project identity; they are the intended Forge replacements for Sodium and Iris.
The presence of Fabric/common mixin json files in src/main/resources is not by itself a Forge runtime bug because build.gradle excludes them from packaged Forge resources.
The Forge mixin config using the "client" mixin list is correct for client-only mixins; the unresolved risk is the top-level mod/server boundary and broad internal-target version range.
```

### Eight-point scan note: Old-stage Documentation Superseded / Deprecated Sweep

Deep-scan status:

```text
docs/*.md inventory
top-of-file status wording for every Markdown document
keyword scan for superseded/deprecated/historical/preview/debug/stage/roadmap/formal/fallback/sample/skeleton
```

Current result:

Most route-control or old-stage documents now start with explicit current-route
or superseded/deprecated language. The following files have clear first-page
warnings and are not currently missing banners:

```text
docs/forge-1.20.1-original-voxy-full-render-path-parity-audit.md
docs/forge-1.20.1-stage-roadmap.md
docs/forge-1.20.1-k0-original-voxy-renderer-alignment-audit.md
docs/forge-1.20.1-formal-renderer-readiness-audit.md
docs/forge-1.20.1-formal-mdic-renderer-integration-plan.md
docs/forge-1.20.1-mdic-command-buffer-plan.md
docs/forge-1.20.1-model-bakery-bridge-plan.md
docs/forge-1.20.1-modelstore-bridge-plan.md
docs/forge-1.20.1-direct-gl-heap-renderer-plan.md
docs/forge-1.20.1-deprecated-prototype-routes.md
docs/forge-1.20.1-debug-preview-cleanup-2026-06-22.md
docs/forge-1.20.1-legacy-code-retirement-plan.md
```

Issue still standing:

```text
P3: docs/forge-1.20.1-runclient-quickplay.md still uses QA1/H-stage wording and should be rewritten to the current Roman/original-parity validation cadence.
```

Excluded in this pass:

```text
The black-LoD plan and deep-runtime drift audit are not old preview-route docs; they are recent runtime/audit artifacts. They still need to be read together with this full-code defect audit before repair work.
```

## External issue topics to check

```text
Original Voxy issues: black borders, water/terrain artifacts, movement update bugs,
shaderpack-specific output issues, renderer crashes.

Oculus issues: shader reload, shaderpack compile/link failures, draw target or
sampler binding differences, Complementary-specific behavior.

Embeddium issues: render hook ordering, chunk rebuild/update behavior, Sodium
package compatibility, Oculus interaction.
```

## Omission sweep after the eight-point pass

The eight requested areas have a first-pass source/doc/config sweep recorded
above. A follow-up omission sweep checked:

```text
full Forge package file inventory
danger keywords: TODO/FIXME/preview/debug/sample/skeleton/fallback/deprecated
render-thread queued work: RenderSystem.recordRenderCall
reload flags: isReloadRequired/clearReloadRequired
readiness booleans: FORMAL_*READY, formalRendererReady, actualRendererDrawEnabled,
formalDrawPipelineReady, earlyUsableLodRendererReady
packaged resource filters for Forge vs Fabric/common mixin configs
```

Result:

```text
No additional independent finding was confirmed in this omission sweep.
The readiness search only re-hit the already-recorded P1 shaderpack patch
exposure constant and the non-blocking ModelStore layout mapping readiness bit.
The skeleton/fallback/sample hits are covered by existing findings:
P1 runtime staged skeleton switches,
P1 placeholder model-store route,
P2 legacy CPU/BuiltSection/cache managers,
P2 old MDIC fallback/config gates,
P3 stale startup logs and old-stage doc wording.
```

Limits:

```text
This is not a formal proof that every byte in every dependency is correct.
The unresolved high-risk areas remain the confirmed findings above, especially
the full VoxyRenderSystem lifecycle gap, shaderpack output contract,
persistent storage, and service shutdown ownership. The 2026-06-23 XI.1 repair
added a Forge-side debounce for Oculus reload polling and generation guards for
queued start/cleanup work, but runtime validation is still required.
```

## External issue signals already found

These are not direct proof of Forge-port bugs, but they are relevant risk
signals that should shape the local audit.

### P1 risk signal: Voxy water/transparent LoD rendering has known upstream issue patterns

References:

```text
https://github.com/MCRcortex/voxy/issues/445
 -> water textures can appear above LoD chunks
 -> water sometimes does not render in LoD chunks
 -> transparent-heavy resource packs can cause severe performance drops

https://github.com/MCRcortex/voxy/issues/214
 -> distant water transparency issue with multiple shaders

https://github.com/MCRcortex/voxy/issues/182
 -> LoD water rendering white rather than blue
```

Why this matters locally:

The user currently reports missing water in the Forge/Oculus path. Upstream
Voxy already has water/transparent LoD bug patterns, so the Forge audit must not
treat water as a cosmetic side issue. The transparent bucket, translucent draw
target, depth/stencil transfer, lightmap, face-culling, and shaderpack
G-buffer output path need a dedicated comparison.

Local code areas to inspect:

```text
ForgeOriginalVoxyRenderDataFactory fluid/translucent emission
ForgeOriginalVoxyMdicSectionRenderer translucent draw path
ForgeOriginalVoxyRenderPipeline Oculus translucent target/depth path
ForgeOriginalVoxyOculusRenderPipelineData draw target bindings
ForgeOriginalVoxyModelFactory fluid model bake and tint path
```

### P2 risk signal: Oculus/Embeddium shader stacks have known crash/incompatibility reports

References:

```text
https://github.com/Asek3/Oculus/issues/678
 -> Oculus/Embeddium crash report with Photon shaders on 1.20.1-era stack

https://github.com/FiniteReality/embeddium/issues/192
 -> Embeddium crash once loading into the world with Oculus in a modded stack
```

Why this matters locally:

The user has seen new-world crash/freeze behavior while testing the Forge port.
Those external issues do not identify this repository's bug, but they confirm
that the Embeddium/Oculus boundary is a high-risk area. The audit should treat
shader reload timing, world-entry timing, constructor mixins, and render-target
access as crash-sensitive instead of assuming compile success is enough.

## Validation log for this audit

```text
2026-06-23: AGENTS.md and CODEX.md read.
2026-06-23: CodeGraph available and used before source inspection.
2026-06-23: Initial CodeGraph pass inspected ForgeVoxyInstance,
            ForgeVoxyCommands, ForgeFrontendCompat, ForgeVoxyConfig,
            ForgeVoxyRuntimeOverrides, MemoryStorageBackend, and original
            VoxyRenderSystem excerpts.
2026-06-23: External issue search added Voxy water/transparent LoD issue
            references and Oculus/Embeddium crash-risk references.
2026-06-23: CodeGraph comparison inspected Forge async node/geometry sync
            against original AsyncNodeManager, including SyncResults merge and
            render-thread upload/scatter behavior.
2026-06-23: CodeGraph comparison inspected Forge render-state capture/restore,
            ForgeOriginalVoxyRenderPipeline GL mutations, MDIC terrain draw
            state, and the already-fixed polygon-mode grid symptom.
2026-06-23: CodeGraph comparison inspected Oculus bridge/pipeline data,
            shaderpatch exposure, draw-target expansion, uniform/image/SSBO
            set builders, lightmap binding, and ProgramSet/RenderTargets hooks.
2026-06-23: CodeGraph comparison inspected Forge MDIC shader compile/patch
            fallback, terrain/translucent render entry points, and original
            MDICSectionRenderer shader fallback behavior.
2026-06-23: CodeGraph comparison inspected Forge software model baking,
            quad material metadata bridge, fluid bake route, and original
            ReuseVertexConsumer material-info semantics.
2026-06-23: Repair pass added Forge GameShuttingDownEvent terminal ownership
            for SectionSavingService/UnifiedServiceThreadPool when render
            cleanup completes synchronously; compileJava passed.
2026-06-23: Repair pass removed the isolated placeholder model-store class
            family and detached reload tracking from the deleted skeleton path;
            compileJava passed.
2026-06-23: Repair pass removed unconditional manual mixin config registration
            and narrowed Embeddium/Oculus dependency ranges; compileJava and
            processResources passed.
```
