# Forge 1.20.1 line-by-line port audit

```text
AUDIT_STATUS=beta-complete-user-approved-2026-07-14
AUDIT_BRANCH=forge-1.20.1-skeleton
AUDIT_HEAD=836208526d5457657a1c919c130be18a72006b3b
AUDIT_WORKTREE=XXVIII-final-repairs-validated
AUDIT_PASS_1=252-of-252-files-reviewed
AUDIT_PASS_2=261-of-261-files-reviewed
AUDIT_PASS_3=270-of-270-files-reviewed
AUDIT_PASS_4=271-of-271-files-reviewed
AUDIT_PASS_5=272-of-272-files-reviewed
AUDIT_PASS_6=272-of-272-files-reviewed
AUDIT_PASS_7=272-of-272-files-reviewed
AUDIT_PASS_8=275-of-275-files-reviewed
AUDIT_PASS_9=275-of-275-files-reviewed
AUDIT_PASS_10=275-of-275-files-reviewed
AUDIT_PASS_11=275-of-275-files-reviewed
AUDIT_PASS_12=275-of-275-files-reviewed
AUDIT_PASS_13=275-of-275-files-reviewed
AUDIT_PASS_14=275-of-275-files-reviewed
AUDIT_ZERO_FINDING_FULL_PASS=proven-pass-14
AUDIT_CLIENT_LAUNCH=formal-embeddium-0.3.31-combined-runtime-passed-user
```

## Objective and completion rule

Audit every line of every applicable Forge port source, test, shader, packaged
resource, and build/configuration file against original Voxy and the actual
Forge 1.20.1 platform contract. During each full pass, findings are recorded
but business-code fixes are frozen. After the pass covers every frozen hash,
all confirmed findings are repaired together; the inventory is re-frozen and
a new full pass starts from the first file. Completion requires one entire
post-fix pass with zero new findings and proof that every ledger row was
reviewed. A successful build or a failed search alone cannot satisfy this
completion rule.

## Scope boundary

Included:

- every Java file selected by `sourceSets.main.java.includes` in `build.gradle`;
- every file under the default test source sets;
- every packaged resource selected by `sourceSets.main.resources.includes`,
  including all GLSL stages and include files;
- `build.gradle`, `settings.gradle`, Gradle properties, access transformer,
  Mixin metadata, Forge metadata, and the reproducible inventory tool;
- the binary icon as a hash/artifact audit row (it has no text lines).

Excluded:

- the uncompiled original `me/cortex/voxy/client/**` reference tree except any
  individual file explicitly selected by the Forge source set; it remains the
  comparison baseline, not the port target;
- generated Gradle wrapper scripts/JAR, downloaded dependencies, build output,
  `run/`, logs, saves, IDE state, `.agents/`, `.codegraph/`, and documentation
  prose other than this audit record;
- user-owned `capsettings.cap`, which must remain untracked and untouched.

## Per-file review protocol

A row may change from `pending` to `reviewed` only after all of its physical
lines have been inspected. Java review starts with CodeGraph and records the
original symbol/file and caller/consumer path; direct reads are used for
unindexed source, tests, resources, and exact line confirmation. Each relevant
line is classified as exact upstream code, required 1.20.1 Forge adaptation,
Forge platform ownership/glue with no upstream line equivalent, test/audit
code, or a confirmed finding. Comments, imports, error paths, cleanup paths,
configuration defaults, concurrency, native ownership, and disabled/optional
branches are all in scope.

For each finding, the live findings table records severity, exact target lines,
original evidence, consumers/callers, why it diverges, and disposition. No
finding is fixed until the current full pass is complete.

## Pass 1 frozen inventory summary

- Files: 252
- Text lines: 36741
- Bytes: 1533469
- audit-tool: 1
- binary-resource: 1
- build-config: 2
- build-logic: 2
- main-java: 179
- main-resource: 10
- shader: 43
- test-java: 14

## Pass history

| Pass | Frozen snapshot | Coverage | New findings | Repair state | Result |
|---|---|---:|---:|---|---|
| 1 | frozen hashes below | 252 / 252 files | 15 | all 15 repaired after full frozen coverage; `compileJava` and `test` passed | repaired; Pass 2 required |
| 2 | repaired-tree B001-B261 hashes below | 261 / 261 files | 22 | all 22 repaired after full frozen coverage; repair cross-review added P2-F018 through P2-F022; forced clean test/JarJar and artifact gates passed | repaired; Pass 3 required |
| 3 | post-Pass-2-repair C001-C270 hashes below | 270 / 270 files | 3 | all 3 repaired after complete review and zero-drift proof; `compileJava` and `test` passed | repaired; Pass 4 required |
| 4 | post-Pass-3-repair D001-D271 hashes below | 271 / 271 files | 5 | all 5 repaired after complete physical review and whole-tree zero-drift proof; repair cross-review added P4-F004 and P4-F005; forced clean test/JarJar and artifact gates passed | repaired; Pass 5 required |
| 5 | post-Pass-4-repair E001-E272 hashes below | 272 / 272 files | 1 | P5-F001 repaired only after complete physical coverage and whole-tree zero-drift proof; focused behavioral test and independent repair review passed | repaired; Pass 6 required |
| 6 | post-Pass-5-repair F001-F272 hashes below | 272 / 272 files | 0 during static review | every frozen row reached EOF and all partition/full-inventory fields re-matched | invalidated as terminal evidence by P7-F001 at first subsequent `runClient` |
| 7 | post-P7-F001-repair G001-G272 hashes below | 272 / 272 files | 4 | all four repaired after full coverage; focused tests, complete 98-test suite, and independent repair reviews passed | repaired; Pass 8 required |
| 8 | post-Pass-7-repair H001-H275 hashes below | 275 / 275 files | 1 | P8-F001 confirmed only after every frozen row reached EOF and the whole inventory re-matched with zero drift; focused repair gate passed | repaired; Pass 9 required |
| 9 | post-P8-F001-repair I001-I275 hashes below | 275 / 275 files | 1 | every frozen row reached EOF and every path/kind/line/byte/SHA field re-matched; P9-F001 was confirmed only after complete coverage | repaired; Pass 10 required |
| 10 | post-P9-F001-repair J001-J275 hashes below | 275 / 275 files | 3 | every frozen row reached EOF and all five inventory fields re-matched; P10-F001 through P10-F003 were confirmed only after complete coverage | repaired; Pass 11 required |
| 11 | post-P10-repair K001-K275 hashes below | 275 / 275 files | 3 | all three stale comments repaired after complete coverage; static diff check passed | repaired; Pass 12 required |
| 12 | post-P11-repair L001-L275 hashes below | 275 / 275 files | 7 | every frozen row reached EOF and all five inventory fields re-matched; P12-F001 through P12-F007 were confirmed only after complete coverage and finding-level original-source cross-checks | repaired; focused compile/tests and static diff check passed; Pass 13 required |
| 13 | post-P12-repair M001-M275 hashes below | 275 / 275 files | 1 | every frozen row reached EOF and every path/kind/line/byte/SHA field re-matched; P13-F001 was confirmed only after complete coverage and an independent lifecycle review | repaired; focused compile/test and static diff check passed; Pass 14 required |
| 14 | post-P13-repair N001-N275 hashes below | 275 / 275 files | 0 | every frozen row reached EOF and every path/kind/line/byte/SHA field re-matched; all four clean-room partitions independently reported zero findings | terminal zero-finding review and forced clean static/artifact gate passed |

## Findings

| ID | Severity | File and lines | Original/platform evidence | Finding | Status |
|---|---|---|---|---|---|
| P1-F001 | P3 | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java:10` | Original `VoxyCommon.isVerificationFlagOn("trackBuffers")` accepts only the exact string `"true"`; the port uses `Boolean.getBoolean`, whose parse is case-insensitive. | `-Dvoxy.trackBuffers=TRUE` enables buffer tracking only in the Forge port. This is diagnostic-only and has no default-runtime impact, but it is a measurable parity deviation. | repaired; exact lowercase property test passed |
| P1-F002 | P3 | `src/main/java/me/cortex/voxy/common/world/WorldSection.java:15` | Original `VoxyCommon.isVerificationFlagOn("verifyWorldSectionExecution")` accepts only the exact string `"true"`; the port uses `Boolean.getBoolean`, whose parse is case-insensitive. | `-Dvoxy.verifyWorldSectionExecution=TRUE` enables verification only in the Forge port. Diagnostic-only, but not exact original flag semantics. | repaired; exact lowercase property test passed |
| P1-F003 | P2 | `src/main/java/me/cortex/voxy/config/ForgeOriginalVoxyCpuLayout.java:9-21` | Original `CpuLayout.getCoreCount()` derives physical cores only through its Windows/Linux layout builders and falls back to logical `availableProcessors()` on unsupported platforms or layout-validation failure. The Forge helper asks OSHI for a physical count on every platform. | Default service-thread counts can differ from original Voxy on macOS/other platforms and on layouts the original deliberately rejects. The port can preserve the original platform/fallback decision instead of broadening it. | repaired; Forge uses included original `CpuLayout`; replacement deleted |
| P1-F004 | P2 | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java:80-82,142-165` and `RenderResourceReuse.java:27-32,67-83` | Original `BasicSectionGeometryData.free()` decommits every committed sparse page before an external geometry buffer is returned to `RenderResourceReuse`; the original cache retains the GL buffer object, not its committed pages. | The Forge cache carries `committedSparseBytes` across owner rebuilds and decommits only at terminal shutdown. This can retain a large sparse VRAM commitment across dimension/shaderpack owner rebuilds and is not the original lifecycle contract. | repaired; decommit -> `glFinish` -> cache handoff ordering test passed |
| P1-F005 | P2 | `src/main/java/me/cortex/voxy/forge/DebugUtils.java:25,34,59` | Original verifier cancellation is tied to `engine.instanceIn`, the owner of the exact engine being scanned. Forge checks the process-wide reusable `ForgeVoxyInstance.INSTANCE`, whose session runtime can be replaced while the verifier still holds an old engine reference. | A verifier/repair worker from an old session can resume against old storage when a new session makes the singleton active. Cancellation must be tied to the captured engine/session generation. | repaired; verifier reads captured engine's session owner |
| P1-F006 | P2 | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java:9` | Original constructor calls `super(null, null, false, false)`, explicitly disabling suppression and stack-trace capture for this control-flow exception. The Forge constructor implicitly uses the normal `RuntimeException` constructor. | Active model/meshing retry paths repeatedly throw this exception while IDs are unavailable; filling stacks adds avoidable worker CPU and allocation overhead that original Voxy intentionally removes. | repaired; no-stack/no-suppression test passed |
| P1-F007 | P2 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java:85-95` | Original `NormalRenderPipeline` owns the final colour/depth blit and SSAO, while original `IrisVoxyRenderPipeline` owns only its shaderpack depth blits, optional depth-hack transform, and Iris UBO path. | The merged Forge owner constructs the normal-only final blit and SSAO even when captured Oculus pipeline data selects the shaderpack path. Those unused resources add GL allocation/compilation work and can make shaderpack pipeline construction fail because a shader that the selected path never uses fails to compile. | repaired; NORMAL/OCULUS exclusive resource mode test passed |
| P1-F008 | P2 | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java:62` and fluid-bake call path | Forge 1.20.1's `LiquidBlockRenderer.setupSprites()` initializes `waterOverlay`; its tessellator still compares the selected face sprite with that field when deciding whether to emit the reverse quad. The original Voxy model pipeline consumes Minecraft's initialized fluid renderer. | Forge directly constructs `new LiquidBlockRenderer()` but never invokes `setupSprites()`, leaving `waterOverlay` null. Water-overlay faces therefore take different quad-emission behavior from the initialized vanilla/original renderer and can bake different fluid models. | repaired; protected vanilla initialization is invoked before texture preparation |
| P1-F009 | P2 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java:445-466,539-578` | Original `ModelFactory` enumerates every `BlockTintSource`, marks a model biome-dependent if any source calls `getBlockTint`, and captures the first source that produces a real colour. Forge 1.20.1 exposes baked-quad tint indices rather than that source list, but all distinct indices remain enumerable. | Forge retains only the first encountered tinted quad index and uses it both to classify biome dependence and to sample every biome upload. Multi-tint-index models can be misclassified or coloured from the wrong tint source. | repaired; ordered all-source classification/capture tests passed |
| P1-F010 | P3 | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java:60-62,109-121` | Original `FullscreenBlit` applies its callback to `Shader.Builder`; builder defines are injected into every shader stage. | Forge's equivalent constructor names the arguments `fragmentDefines` and injects them only into the fragment source. The current `USE_ENV_FOG` and `EMIT_COLOUR` shaders reference them only in the fragment stage, so this is presently latent, but the shared-stage shader-builder contract is not faithfully ported. | repaired; both-stage source test passed |
| P1-F011 | P3 | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java:37-39,50-59,75-89,91-107` | Original `FullscreenBlit` uses one static empty VAO and `SharedIndexBuffer.INSTANCE_BYTE`; each instance owns only its shader. | Forge creates, uploads, and frees a private VAO plus six-byte index buffer for every blit even though the original shared resources are present in the port. This is a small but avoidable ownership/allocation divergence with no platform blocker. | repaired; static VAO/shared index ownership test passed |
| P1-F012 | P2 | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java:34-36` | Original Iris `MixinLevelRenderer` obtains the current `VoxyRenderSystem` first and mutates the OpenGL viewport only when that owner is non-null. | Forge applies the viewport reset whenever an Oculus shaderpack is active outside the shadow pass, even when Voxy is disabled or no live render owner exists. The unconditional mixin therefore mutates external render state beyond Voxy's lifetime and can affect non-Voxy frames. | repaired; viewport guard truth-table test passed |
| P1-F013 | P1 | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java:241-247,524-530,700-704,736-739` | Original `AsyncNodeManager.tick()` rethrows a worker failure on the render thread, original geometry-result processing lets invariant failures terminate the worker, and failure to return a sync result to either cache is fatal. The active Forge render caller does not block faithful exception propagation. | Forge records and suppresses all three fatal conditions. A worker failure can leave the visible owner alive but permanently stale; a geometry invariant can be swallowed after partial `NodeManager` mutation; and result-cache corruption is converted to resource disposal plus logging. `recordFailure` neither restarts nor invalidates the owner. | repaired; exact fatal propagation restored; full test suite passed |
| P1-F014 | P3 | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl:171-173` | Original `isCulledByHiz()` lets a degenerate positive viewport-edge box fall through its empty sample loops and remain culled by the initialized sentinel. The Forge-only guard returns visible instead. | The extra conservative edge-node traversal cannot create false occlusion, but it is a local algorithm change independent of Oculus or reverse-Z and has no documented 1.20.1 platform blocker. Under the original-as-baseline rule it must be removed unless such a blocker is proven. | repaired byte-for-byte to `dev`; full test suite passed |
| P1-F015 | P2 | `src/main/resources/META-INF/accesstransformer.cfg` and `src/main/java/me/cortex/voxy/forge/ModelFactory.java:51,720-740` | Original `voxy.accesswidener` exposes `StairBlock.baseState`, and original `ModelFactory` reads it directly to normalize stair states before model baking. Forge's AT omits the mapped field `f_56859_`. | Forge reflects the literal development name `baseState`. Reflection strings are not reobfuscated, so the production all-JAR cannot resolve the runtime-mapped field, caches null, and silently skips original stair normalization. The AT must expose the field and the direct access must be restored. | repaired; AT exposes `f_56859_` and direct access compiles |
| P2-F001 | P1 | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java:318-367,729-732` | Original `AsyncNodeManager.stop()` rethrows `InterruptedException` before freeing worker-owned queues, native memory, GL programs, geometry state, or cache entries. | Forge records the interruption and continues freeing resources while the worker may still be alive and accessing them. This creates a concurrent native/GL use-after-free window. | repaired; original stop ordering restored and focused test passed |
| P2-F002 | P2 | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java:164-214,243-268,283-296` and `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java:63-71` | Original chunk ingest waits until at least one `SKY` or `BLOCK` storage reports `LIGHT_AND_DATA`; a non-air chunk with neither ready returns false. In a no-skylight dimension, block light is therefore the readiness source. | Forge treats every no-skylight section as sky-ready. A Nether section exposed before block-light storage is ready is ingested with block light zero and is not marked deferred, so the retry queue never revisits it. | repaired; dimension-aware readiness and retry tests passed |
| P2-F003 | P3 | `src/main/java/me/cortex/voxy/forge/BuiltSection.java:8` | Original verification flags accept only the exact lowercase string `"true"`. | `Boolean.getBoolean` also accepts `TRUE`/`True`, repeating the diagnostic-flag semantic drift repaired elsewhere in Pass 1. | repaired; exact lowercase source contract tested |
| P2-F004 | P1 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java:651-656` | Original post-dynamic work directly runs render-distance tracking and `ModelBakerySubsystem.tick`; worker death and tracker invariant failures propagate. | Forge catches every `RuntimeException` and records it as nonfatal, leaving a visible owner alive with a dead model worker or stale tracker state. | repaired; fatal propagation source contract tested |
| P2-F005 | P1 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java:223-240` | `SpriteContents.name()` is a public mapped method and direct invocations are reobfuscated by ForgeGradle. | Reflection asks for literal method name `name`; the production all-JAR retains that string while the runtime method is `m_246162_`. Mipped non-opaque sprites, notably leaves, fail model baking in production. | repaired; all-JAR calls reobfuscated `m_246162_` directly |
| P2-F006 | P1 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java:46-62` | The mapped `LightTexture.lightTexture` field can be exposed by an access transformer and `AbstractTexture.getId()` can be invoked directly. | Reflection strings `lightTexture` and `getId` are not reobfuscated (`f_109870_` / `m_117963_` in production), so packaged builds silently capture texture 0 and can render LOD with invalid lightmap input. | repaired; all-JAR directly accesses `f_109870_` and calls `m_117963_` |
| P2-F007 | P3 | `src/main/java/me/cortex/voxy/forge/GlDebug.java:11` | Original `voxy.glDebug` checks exact lowercase `"true"`. | Forge uses case-insensitive `Boolean.parseBoolean`, a diagnostic-only but measurable flag deviation. | repaired; exact lowercase source contract tested |
| P2-F008 | P1 | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java:91-105` and `src/main/java/me/cortex/voxy/forge/ModelFactory.java:96-109,307-351` | Original `addEntry` and model-bake invariant failures propagate to the model worker; `tick` then propagates worker death. It never continues with request dedupe committed while `idMappings[id]` remains `-1`. | Forge commits `seenIds` before knowing enqueue succeeded, swallows mapper/software-bake/capacity failures, removes in-flight state, and leaves the mapping absent. Future requests are permanently rejected; a dependent fluid model can also keep `hasWorkerWork()` true in a no-park busy loop. | repaired; dedupe commit and failure propagation contracts restored |
| P2-F009 | P1 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java:161-220,354-365,1026-1044` | Original render-thread uploads drain directly and propagate failure. | Forge publishes CPU model mappings before the segmented GPU upload, then frees and drops the payload after 16 reported failures while keeping the mapping valid. This can permanently expose zeroed or partially uploaded model data with no rebake route. | repaired; failed payload remains owned and is requeued until factory teardown |
| P2-F010 | P1 | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java:265-330,366-394` | Original MDIC cmdgen and draw paths propagate readiness/invariant/runtime failures; only the zero-section case is a normal no-op. | Forge records and returns on unavailable targets/buffers/viewports and catches cmdgen runtime failures after prep/cull may have partially rewritten indirect buffers. The frame then consumes stale or partial commands. | repaired; nonzero-section readiness and runtime failures propagate |
| P2-F011 | P2 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java:488-498` | Original biome replacement detects a different existing biome ID and throws, invalidating the owner. | Forge replaces the CPU entry and silently returns without a colour upload, splitting CPU biome identity from the GPU colour table. | repaired; biome identity mismatch is fatal |
| P2-F012 | P2 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java:586-605` | Original tint-source enumeration and colour capture do not retain partial results after a provider/model exception. | Forge catches every runtime failure and permanently bakes with whichever tint indices happened to be enumerated first, allowing incomplete dedupe and wrong biome colours. | repaired; partial tint results cannot survive failure |
| P2-F013 | P3 | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java:75,97,117,172` and `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java:121,283,345-346,400,478` | Original `HiZBuffer`, MDIC, and fullscreen passes bind the process-global empty `GlVertexArray.STATIC_VAO`. | Forge allocates and lifecycle-manages private empty VAOs for HiZ and MDIC even though the port already needs the same shared owner for fullscreen passes. This is an unnecessary object/ownership deviation. | repaired; process-global empty VAO is shared without per-owner deletion |
| P2-F014 | P3 | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java:20-67` | Original validates every shader/import ID through Minecraft's identifier type and loads only packaged classpath assets. | Forge manually splits IDs and falls back to `src/main/resources` on disk. The fallback can make a dev run pass while the packaged JAR is missing a shader, and malformed IDs bypass the original validation contract. | repaired; ResourceLocation validation and classpath-only loading tested |
| P2-F015 | P3 | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java:186-191` | Original samples the initialized texture directly. The Forge caller returns before rasterization when atlas pixels are unavailable. | An unreachable Forge-only transparent fallback masks an invalid sampler invariant and its comment incorrectly claims reload churn can reach it. | repaired; direct initialized-sampler contract restored |
| P2-F016 | P2 | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java:21-31` | Original `VoxyRenderSystem.getViewport()` rejects an Iris shadow pass before delegating to the Vivecraft-aware selector. | Forge checks Vivecraft first. With Vivecraft and Oculus together, a non-vanilla VR pass can win during a shadow pass and render Voxy into the shadow route, unlike original. | repaired; shadow rejection precedes Vivecraft selection |
| P2-F017 | P3 | `src/main/java/me/cortex/voxy/forge/WorldImporter.java:413-457` | Original uses sentinel defaults for missing numeric coordinates/Y, requires a sections list, and accepts only a complete biome codec result. | Forge defaults missing/wrong numeric tags to zero, silently treats a missing/wrong sections list as empty, and accepts partial biome decode results. Malformed imports can be placed at valid coordinate zero or retain partial biome data instead of following original error/default handling. | repaired; sentinel/list/full-codec semantics tested |
| P2-F018 | P3 | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java:314-318` | Original `AsyncNodeManager.stop()` first throws `IllegalStateException` when `running` is already false. Its normal owner lifecycle prevents a second stop, and construction-failure cleanup still begins with `running=true`. | Forge omits the state precondition, so worker-failure shutdown or an accidental second stop can enter cleanup in a state the original rejects; several native/GL resources are not proven idempotent under duplicate release. No Forge lifecycle requirement or documented deviation justifies this difference. | repaired; original running-state precondition tested |
| P2-F019 | P2 | `src/main/java/me/cortex/voxy/forge/ModelStore.java:268-300` and `src/main/resources/META-INF/accesstransformer.cfg` | Original `ModelStore` directly reads the block `TextureAtlas` owner's actual mip level. Forge 1.20.1 maps that field as `TextureAtlas.mipLevel -> f_276072_`, which can be exposed by the access transformer and directly reobfuscated. | Forge reflects `maxMipLevel`, `maxMipmapLevels`, and `f_119402_`; none names the 1.20.1 atlas field (`f_119402_` belongs to `ModelManager.maxMipmapLevels`). Atlas lookup therefore always fails and falls back to the requested model-manager level, which can differ from the atlas's actual prepared mip level. | repaired; AT/direct access tested and all-JAR reads `f_276072_` |
| P2-F020 | P1 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java:154-195,998-1017`, `ModelStore.java:90-177,180-203`, `UploadStream.java:93-169`, and `ForgeOriginalVoxyModelPipeline.java:778-784` | Original `ModelFactory.processUploads()` drains every uploader and commits `UploadStream` before the model store can be freed. | A model uploader can queue record/colour copies, then report a texture GL error before the final commit. Forge requeues the CPU payload and synchronously tears down `ModelStore`; its buffers are deleted while the process-global `UploadStream.uploadList` still contains their raw IDs. A later tick/free copies into deleted or reused buffer IDs. Earlier successful uploaders in the same drain are affected too. | repaired; pending copies commit/clear before owner teardown, with failure-identity test |
| P2-F021 | P3 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java:154-159,271-279` and `ForgeOriginalVoxyModelPipeline.java:761-766` | Original model uploads execute on their render-thread caller and treat broken model-store ownership as fatal; there is no runtime store rebuild path. | Forge silently returns when invoked off the render thread, attempts to rebuild a not-ready store inside upload processing, and the outer upload entry also records-and-returns on the wrong thread. Formal callers already guarantee the render thread, so these are unreachable fallback contracts rather than required Forge adaptations. | repaired; wrong-thread and broken-store states are fatal, rebuild fallback removed |
| P2-F022 | P2 | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java:63-71,89-97` | The Forge-only retry adapter requires its deque and membership set to represent the same pending chunk keys so the eight-per-tick budget remains bounded and deduplicated. | A non-deferred result removes a key only from `queuedChunks`, leaving it in `pendingChunks`. A later deferred event appends the same key again; duplicate retries can consume or starve the per-tick budget. | repaired; set/deque dedupe invariant and unrelated-section success tested |
| P3-F001 | P2 | `src/main/java/me/cortex/voxy/common/Logger.java:54-55` | Original `Logger.error()` checks `VoxyCommon.IS_IN_MINECRAFT && !VoxyCommon.IS_DEDICATED_SERVER` before entering the client HUD path. Forge 1.20.1 marks `Minecraft` client-only. | The port directly executes `Minecraft.getInstance()` before any distribution guard. A common-service diagnostic on a dedicated server can therefore resolve a client-only class and replace the original error path with an invalid-distribution class-load failure. | repaired; common outer class is client-reference-free and HUD entry is distribution-guarded |
| P3-F002 | P3 | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java:383-387,434-439` | Original uses numeric/string fallback getters and requires a real compound for `block_state`. Forge 1.20.1 `getInt`, `getString`, and `getCompound` silently return `0`, `""`, and an empty compound for missing or wrong-type tags. | The 1.20.1 adaptation checks only key presence. Malformed persisted mappings can be coerced into id zero, an empty biome id, or the air/random-resave recovery route instead of preserving original fail-fast/default semantics. | repaired; tag-type-aware numeric/string defaults and required compound semantics tested |
| P3-F003 | P3 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java:199-256` | `setupDepthTexture()` binds `GL_FRAMEBUFFER`, which changes both draw and read bindings. A local GL-state owner must preserve both bindings when it claims to restore its caller's state. | `GlState` captures only the draw FBO and restores with `GL_FRAMEBUFFER`, forcing the read binding to the old draw FBO. The formal outer renderer later restores both and masks the defect today, but the helper's Forge-local isolation contract is incomplete. | repaired; draw/read bindings are captured and restored independently |
| P4-F001 | P2 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java:308-331,397-410,451-467,853-888` and `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java:198-207,578-589` | Original `ModelFactory.java:471-476,520,632-662` merges the contained-fluid model's biome-colour dependency only after duplicate rejection, then uses that same state for CPU metadata, GPU `modelFlags`, colour/base-index selection, and biome-colour-model registration. | Forge initially omitted the inherited dependency entirely; the first repair only fixed CPU metadata and left GPU flags/LUT state divergent. The completed repair merges into the shared `TintPlan` after dedupe and before finalization/build, preserving pre-merge dedupe colour while driving every original consumer. | repaired; no-tint and constant-tint fluid inheritance plus production ordering/consumer contracts tested |
| P4-F002 | P3 | `SectionSerializationStorage.java:33`, `TrackedObject.java:80-82`, `ActiveSectionTracker.java:151`, `Mapper.java:125,132,176,388-402`, `SaveLoadSystem3.java:103`, and `WorldEngine.java:169-172` | These original common-layer sites call `me.cortex.voxy.common.Logger`, whose error route also schedules the client HUD/actionbar notice and whose warn/info routes preserve the caller-prefix and `SHUTUP` controls. Pass 3 made that common logger distribution-safe by isolating client bytecode behind a Forge dist guard. | The Forge adaptations replaced the calls with per-class direct SLF4J loggers. Corruption/load/mapper/leak/shutdown errors silently lost the original HUD notice, while warn/info output bypassed the original diagnostic formatting and suppression contract. | repaired; exact common Logger routing, DFU callback, bytecode linkage, and dedicated-server isolation tests passed |
| P4-F003 | P3 | `ForgeOriginalVoxyModelPipeline.java:922-1218`, `ForgeOriginalVoxyOculusRenderPipelineData.java:822-919`, `ForgeOriginalVoxyRenderPipeline.java:498-527`, `ForgeOriginalVoxyPipelineDepthStage.java:224-281`, `ForgeOriginalVoxyTextureBindings.java`, `Capabilities.java`, `HierarchicalOcclusionTraverser.java`, `MDICSectionRenderer.java`, and `SSAO.java` | The Forge-only Embeddium injection boundary must return every GL state item that the embedded original renderer mutates. Khronos specifies that indexed buffer binds also overwrite the corresponding generic binding, range bindings carry independent start/size state, and `glBindTextureUnit(unit, 0)` resets every texture target on that unit. | The former snapshot lost indexed range/generic buffer state, array/dispatch bindings, image unit 0, unpack state, back-face stencil state, and non-2D texture bindings. The repair captures/restores the complete active mutation set, restores generic bindings after indexed ranges, uses legal core-profile polygon restoration, and applies target-specific zero binding with bounded Oculus 1D/2D/3D/rectangle capture. | repaired; focused GL-boundary/texture-target contracts and full test suite passed |
| P4-F004 | P2 | `ForgeOriginalVoxyOculusRenderPipelineData.java:84-98,982-1023` | Oculus 1.8 exposes four `TextureType` enum identities, but its shipped `TEXTURE_RECTANGLE.getGlType()` bytecode returns `32879` (`GL_TEXTURE_3D`) instead of `34037` (`GL_TEXTURE_RECTANGLE`). Original Voxy's nonzero DSA binding does not need the numeric target; Forge's new target-specific zero/capture/restore adaptation does. | Trusting the broken accessor makes a rectangle sampler capture, clear, and restore the unrelated 3D target while leaving the rectangle binding leaked. The Forge adapter now maps the four enum identities explicitly to the correct Khronos targets and no production sampler path calls `getGlType()`. | repaired; actual Oculus enum values are reflectively exercised and rectangle maps to `GL_TEXTURE_RECTANGLE` |
| P4-F005 | P3 | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java:109-265` | A state-boundary regression test must constrain the active `renderEmbeddiumCutout` capture/arm/finally-restore route and the ModelPipeline -> RenderPipeline -> ImageSet non-2D delegate chain, not only isolated helpers and source fragments. | The first P4-F003 test revision could pass if the capture helper became dead code or the production delegate returned an empty set; it also asserted the broken Oculus `getGlType()` call. The test now locks capture-before-mutation, restore arming and finally placement, every delegate hop, target-specific restore, and the real four-enum mapping. | repaired; seven focused parity tests pass and production delegation is source-locked |
| P5-F001 | P3 | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java:11-24` and `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java:9-16` | Original `TrackedObject` reads only `voxy.ensureTrackedObjectsAreFreed`, defaults it to exact lowercase `"true"`, and delegates to `VoxyCommon.isVerificationFlagOn`; original Voxy has no `voxy.disableTrackedObjectAllocations` property. | Forge retained a legacy disable alias and parsed it through case-insensitive `Boolean.getBoolean`. When the original property is absent, values such as `-Dvoxy.disableTrackedObjectAllocations=TRUE` disable tracking only in the port. The alias is not required by Forge and extends observable behavior beyond the original baseline. | repaired; alias removed, active helper preserves exact default/case semantics, focused test and independent review passed |
| P7-F001 | P1 | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java:35-50` and `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java:8-34` | Original `client/mixin/iris/MixinLevelRenderer` keeps the viewport behavior inside a private injection method. Mixin 0.8.5 rejects non-private static methods during its MAIN applicator phase unless they are valid target members. | Forge extracted the viewport predicate as a package-private static helper solely for direct unit-test access. `runClient` therefore crashed before window initialization with `InvalidMixinException: contains non-private static method shouldResetViewport(ZZZ)Z`, despite compile and unit tests passing. | repaired; helper is `@Unique private static`, the test reflectively verifies private/static plus predicate behavior, focused compile/test passed, and a second `runClient` entered the world and created the Voxy render system |
| P7-F002 | P3 | `src/main/resources/META-INF/mods.toml:6-11` and `build.gradle:69-77` | Original `fabric.mod.json:17` exposes `assets/voxy/icon.png` as the mod icon. Forge 1.20.1 `ModInfo` exposes a logo only when the mod/file metadata contains `logoFile`; packaging the PNG alone does not populate that field. | The Forge JAR deliberately includes the original icon, but `mods.toml` never names it, so Forge's mod-list metadata has an empty logo instead of the original Voxy icon. This is a visible metadata parity omission with no platform blocker. | repaired; `logoFile` maps the already packaged original icon and processed-resource byte parity test passed |
| P7-F003 | P2 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java:275-309,404-430,480-508` | Original `IrisShaderPatch.BlendStateDeserializer` can emit a null entry for an unknown per-buffer blend value, but original `PatchGson.checkValid()` immediately dereferences every entry inside `makePatch()`'s catch boundary, so the malformed `voxy.json` is rejected as `ShaderLoadError` during shaderpack loading. | Forge's parser retains the null entry while its validation explicitly skips null states. The active translucent setup later dereferences `state.off`, converting the original load-time rejection into a deterministic render-time NPE. | repaired; original blending-first/null-dereference load boundary restored and behavioral test passed |
| P7-F004 | P2 | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java:186-214,387-427,499-509,725-729` | Original `AsyncNodeManager.addTopLevel()` and `removeTopLevel()` mutate the top-level delta sets and apply the matching `workCounter` delta while still holding `tlnLock`; the worker cannot drain those sets before their work is counted. | Forge releases `topLevelNodeLock` before applying the counter delta. A worker already active for another item can drain and process the newly inserted top-level delta plus that item, subtract both from a counter that still accounts for only one, and enter the negative-counter one-second sleep before the producer's late increment restores zero. This creates an intermittent LOD update stall with no Forge necessity. | repaired; set mutation, counter delta, and unpark are one producer critical section; strengthened lock-protocol test passed |
| P7-F005 | P3 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java:372-430` | Original `BlendStateDeserializer` maps a JSON array directly to blend constants and indexes the first four values. An array shorter than four throws inside the deserializer's own catch, which returns only mappings accumulated before that entry and skips the current and later entries. | Forge adds a short-array branch that converts the malformed entry into a valid per-buffer `off` state and continues parsing later entries. The same malformed shaderpack can therefore disable a colour attachment's blending only in the port instead of preserving the original partial-map fallback. | repaired; original partial-map behavior restored and behavioral mapping test passed |
| P8-F001 | P2 | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java:158-181,491-508,859-869` | Original `VoxyInstance` owns `WorldEngine` objects by `WorldIdentifier`; `WorldIdentifier.of(Level)` reads the identifier from the level but the session owner never retains a `ClientLevel`. Forge's process-global callback adapter needs an identity guard for late work, but that platform adaptation does not require strong level ownership. | `SessionRuntime.levels` was an identity-backed strong set that only added each current `ClientLevel`. Dimension switches and same-session respawns therefore retained every old level, including its chunk/entity state, until disconnect. | repaired after complete Pass 8 coverage; the guard now stores weak identity references, preserves same-session late-callback recognition and cross-session rejection, and the focused lifecycle test passed |
| P9-F001 | P2 | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java:445-455` and `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` | Original `MixinLevelRenderer#setLevel` compares the old and replacement `ClientLevel` objects at `HEAD` and shuts the old renderer down synchronously before `LevelRenderer` rebuilds level-dependent state. | Forge detected a switch only at client-tick END. A same-dimension replacement could therefore leave the old renderer/world selection live through the new level's Embeddium rebuild, then clear deferred-light retry entries produced by that new level. The dimension-string predecessor also could not recognize equal-key replacement objects. | repaired after complete Pass 9 coverage; the required Mixin now owns the exact `setLevel` HEAD identity boundary, teardown is `retry clear -> dimension stale -> active-world detach`, and END tick contains no fallback/second teardown owner |
| P10-F001 | P3 | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java:232-268` | Original `PrintfInjector.processResult()` runs its `preRun` callback after the empty-result guard and before decoding each non-empty readback; original `PrintfDebugUtil` supplies `printfQueue::clear`. Both original and Forge `DownloadStream.tick()` may retire multiple completed frames in one call. | Forge cleared `CURRENT_QUEUE` only when scheduling the next download. If multiple shader-printf readbacks retired in one tick, Forge merged their lines, while original Voxy clears before each non-empty batch and retains only the final batch for the next overlay snapshot. This affects only opt-in shader printf diagnostics. | repaired; real private callback behavior covers two non-empty batches plus an empty batch, and independent review passed |
| P10-F002 | P3 | `src/main/java/me/cortex/voxy/forge/ModelQueries.java:3-79` | Original `ModelQueries` exposes both boolean and bit-valued accessors for double-sided, translucent, fluid, and biome-coloured model metadata. These accessors are part of the original metadata component even when a particular current consumer uses only the bit-valued form. | Forge omitted `isDoubleSided`, `isTranslucent`, `isFluid`, `isBiomeColoured`, and `_isBiomeColoured`. No current Forge caller was broken, but the port's model-metadata API was an incomplete mirror with no platform blocker. | repaired; all 19 original accessors now match, with one-hot positive/negative bit tests and independent review |
| P10-F003 | P3 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java:273-305` | Original `processTextureBakeResult()` treats a completed bake whose block ID is already mapped as an impossible duplicate and throws `IllegalStateException` after software baking and before fluid resolution, dedupe, or in-flight removal. | Forge added a branch that silently removed the in-flight marker and returned success. The normal producer lock makes the branch unreachable in a healthy run, but invariant corruption was suppressed instead of following original fail-fast ownership semantics. | repaired; duplicate mapping now throws after software-bake failure handling and before fluid resolution, with ordering/source-contract test and independent review |
| P11-F001 | P3 | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java:12-17` | Original `RenderResourceReuse` establishes geometry-buffer reuse/lifetime ownership without claiming that reuse fixes a shaderpack-hole failure. XX.5 ground-truth testing reproduced the holes with reuse active and explicitly excluded the old free/reallocate window as their root cause. | The class comment still states that XX.4 implicated NVIDIA VRAM aliasing and presents reuse as more than an allocation optimisation for that reason. The implementation is correct, but its current architectural rationale contradicts the completed investigation record. | repaired; comment now states only original ownership/lifetime, allocation-churn, and GL-ordering semantics |
| P11-F002 | P3 | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java:527-530` | The active dimension lifecycle hook is `LevelRenderer#setLevel` at `HEAD`; Pass 9 removed the old END-tick fallback so teardown occurs before vanilla replaces the world reference. | The guard comment still says it waits for an `END`-tick lifecycle transition. Runtime behavior is correct, but the stale ownership description can send later maintenance back toward the removed unsafe boundary. | repaired; comment names the active `LevelRenderer#setLevel` `HEAD` transition |
| P11-F003 | P3 | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java:63-66` | Original buffer reuse means the geometry buffer persists across owner rebuilds and is freed only at full instance shutdown. XX.5 ground truth showed that this parity mechanism did not close XX.4 and excluded free/reallocate aliasing as the failure source. | The allocation-site comment repeats the disproven `NVIDIA VRAM aliasing` explanation. The reuse call itself is correct; only its documented reason is stale. | repaired; allocation-site comment now records the actual reuse ownership contract |
| P12-F001 | P3 | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java:47-51,517` and `NodeManager.java:28-41,321,606,633,673,759,777` | Original `AsyncNodeManager` reads the exact-lowercase `voxy.verifyNodeManager` diagnostic flag and, after publishing each worker result, calls the original `NodeManager.verifyIntegrity()` tree/request/watcher/allocation audit. Original `NodeManager` also maintains `activeNodeRequestCount` at every child-request allocation/release so the verifier can cross-check its pool. | Forge omitted the flag, post-publication verifier call, all four verification methods, and the request counter. Default rendering is unaffected because the original flag defaults false, but the original opt-in integrity mechanism is absent with no Forge blocker. | repaired; exact flag/publication order, verifier surface, and 2-allocation/4-release counter contract are source-tested |
| P12-F002 | P2 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java:922-1018,1073-1103`, `ForgeOriginalVoxyRenderPipeline.java:498-527`, and `ForgeOriginalVoxyOculusRenderPipelineData.java:879-940,1093-1128` | Original `VoxyRenderSystem` saves only SSBO slots `0..9` and clears/restores the fixed first 12 texture/sampler units. The Forge boundary must additionally cover the exact slots its Oculus, statistics, UBO, and shader-printf adapters mutate. | Forge instead queries and restores every driver-reported texture/sampler and SSBO slot plus 16 UBO slots on every visible frame, including binding/start/size queries and fresh arrays. Cost scales with hardware limits rather than the active Voxy mutation set. | repaired; fixed 12 texture/sampler units plus exact sparse UBO/SSBO mutation plans replace hardware-maximum scans |
| P12-F003 | P2 | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java:42,44-75` and `ForgeOriginalVoxyModelPipeline.java:408-506,549-558` | Original built-section transitions target the `ChunkBoundRenderer` owned by the same `VoxyRenderSystem`. Forge may rebuild only that owner while retaining the Embeddium `RenderSectionManager`, so its adapter must reseed all already-built sections for every new owner generation. | The mixin remembers only one boolean per Embeddium manager and resets it only if `RenderSectionManager.update()` happens to observe Voxy inactive. Owner-only rebuilds do not guarantee such an update, so a new chunk-bound owner can permanently miss existing built sections until unrelated section transitions occur. | repaired; every successfully published owner generation is reseeded at Embeddium `renderLayer` CUTOUT `HEAD`, including an empty snapshot |
| P12-F004 | P3 | `src/main/java/me/cortex/voxy/forge/GPUTiming.java:28,38,44-45,50,67-69,88-93` | Original `GPUTiming` delegates every `free()` directly to its tracked query owner; normal terminal lifecycle already guarantees one formal free, while `TrackedObject.free0()` exposes duplicate or post-free misuse. | Forge adds a `freed` state that silently ignores duplicate free and every later marker/enable/debug/tick call. This masks invalid lifecycle use rather than preserving original fail-fast ownership, and no Forge lifecycle requires it. | repaired; the outer silent guard is removed, terminal ownership stays single, and a no-GL test proves duplicate free fails through `TrackedObject` |
| P12-F005 | P3 | `LMDBStorageBackend.java:44`, `MDICSectionRenderer.java:447,737`, `mixin/ForgeOriginalVoxyOculusIrisMixin.java:25`, `NodeManager.java:79,151,164,199-218,244,314,479,662,771`, `PrintfDebugUtil.java:264`, `RenderDataFactory.java:1756`, and `ModelFactory.java:137-145,470-480,713-730` | The exact original sites route through `me.cortex.voxy.common.Logger`; errors also retain the HUD/actionbar path, and all levels retain caller-prefix plus `SHUTUP` controls. The already repaired Forge common logger is distribution-safe. Original `ModelFactory` also warns on missing biome lookup and errors on duplicate biome insertion. | These remaining sites use direct `VoxyForge.LOGGER` or omit the original diagnostic entirely, losing original logging side effects and suppression/formatting semantics. | repaired; all 22 original sites now match level, text, concatenation, throwable, and common Logger route; only two documented Forge-only diagnostics remain direct |
| P12-F006 | P2 | `src/main/java/me/cortex/voxy/forge/NodeManager.java:474-490` | Original `NodeManager.updateChildSectionsInner()` calls `processRequest(pos)` when an inner-to-leaf conversion lacks geometry, then immediately requires both the block watcher bit and `isNodeGeometryInFlight(nodeId)` before assigning the temporary empty geometry sentinel. | Forge extracted the conversion into `transformInnerToLeaf()` but omitted that postcondition. If request setup fails to establish either side of the invariant, Forge masks the failure with `EMPTY_GEOMETRY_ID` and continues with a leaf that can remain empty. | repaired; both watcher and geometry-in-flight conditions are checked immediately after request setup and before the temporary empty sentinel |
| P12-F007 | P2 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java:470-480,713-730` | Original `ModelFactory.addBiome0()` rejects a null biome before mutating its biome table; it captures plains once as `DEFAULT_BIOME`, uses strict identifier parsing, and has no registry-first-entry substitute. Original `processAllThings()` dereferences the active level registry, so an invalid no-level lifecycle cannot become a valid null mapping. | Forge could return null with no level and silently treat `oldBiome == biome == null` as a duplicate; it also accepted invalid identifiers through `tryParse`, changed one invariant message, and substituted the first registered biome if plains were absent. These paths hide invalid lifecycle/data states and diverge from original default-biome ownership. | repaired; strict identifier parsing, construct-time plains ownership, exact invariant messages, missing-biome warning/default, duplicate logging, and null fail-fast are restored |
| P13-F001 | P3 | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java:13,54-58`, `ForgeOriginalVoxyClientRuntime.java:61-67`, and `ForgeVoxyInstance.java:765-775` | Original `GlBuffer.free()` begins with `TrackedObject.free0()`, exposing duplicate resource release. Forge's process-terminal adaptation already has two single-owner gates: `terminalCleanupComplete` around client shutdown and `sharedIndexInitialized` around `SharedIndexBuffer.freeAll()`. | `SharedIndexBuffer` adds a third local `freed` flag that silently ignores a repeated release before its tracked `GlBuffer` can report the invalid lifecycle. No normal Forge path requires that masking; it can hide a broken terminal-owner call sequence. | repaired; the wrapper delegates directly to its tracked buffer while both formal terminal gates remain source-tested |

## Pass 1 repair batch validation

- Repair policy: no finding was modified until all 252 frozen Pass 1 rows had
  been reviewed and the snapshot was rechecked at `0` hash/size mismatches.
- Scope: all fifteen findings were repaired together. No client was launched.
- Static gate: `rtk git diff --check` passed.
- Compile gate: `rtk test .\\gradlew compileJava --stacktrace --console=plain`
  completed successfully on 2026-07-13.
- Test gate: `rtk test .\\gradlew test --stacktrace --console=plain` completed
  successfully on 2026-07-13, including the new focused parity tests.
- This result does **not** close the audit. Pass 2 must freeze the repaired tree
  and re-read every in-scope physical line from A001 with no inherited review
  credit from Pass 1.

## Pass 2 frozen ledger

Snapshot frozen after the complete Pass 1 repair batch and its successful
compile/test gates: **261 files / 37,401 text lines / 1,557,822 bytes**.
Every Pass 2 row began at `pending`; Pass 1 coverage was not inherited. All
rows changed to `reviewed` only after their frozen physical lines reached EOF.

### Pass 2 full-review freeze proof

- All B001-B261 physical lines were read to EOF: **261 files / 37,401 text
  lines**.
- Independent partitions covered B001-B071, B072-B107, and B108-B161; the
  root partition covered B162-B261, including every shader/resource/test line
  and visual/hash inspection of the binary icon.
- The inventory generator was rerun after review and compared against every
  frozen B row: **261 inventory paths / 261 ledger paths / 0 hash, line, byte,
  missing-path, or extra-path mismatches**.
- No production source was modified before that zero-mismatch proof.
- Twenty-two confirmed findings are recorded above. Seventeen came from the
  frozen line review; P2-F018 through P2-F022 were added by independent repair cross-review
  before the batch could close. The repair batch began only
  after this proof was captured; no client was launched.

### Reviewed and retained non-defect hardening

`GPUTiming.java` intentionally retains three small safety differences that do
not alter the renderer data contract: locale-stable debug formatting, an
inclusive query-capacity bound, and deletion of allocated-but-not-yet-downloaded
queries during shutdown. The original omits or weakens these checks. Reverting
them would reintroduce a locale-dependent diagnostic, an off-by-one capacity
edge, and a GL query leak, so they are documented rather than classified as
repair findings.

### Pass 2 repair batch validation

- Repair policy: no Pass 2 finding was modified until all 261 frozen B rows had
  been reviewed and the snapshot was rechecked at zero mismatches.
- Scope: all twenty-two findings were repaired together; P2-F018 through
  P2-F022 came from independent repair cross-review before the batch closed.
- Static gate: `rtk git diff --check` passed.
- Forced clean gate: `rtk test .\\gradlew clean test jarJar --rerun-tasks
  --stacktrace --console=plain` completed successfully on 2026-07-14.
- Packaged artifact: `voxy-forge-0.2.17-beta-forge-all.jar` is 12,573,346
  bytes (11.99 MiB), SHA-256
  `0fc19ef1d2207bf183ac8581830b589291f15b9230f63d30805127b4d54c9669`.
- Artifact composition: 451 entries; zero bundled `net/minecraft`, LWJGL,
  Mojang, Oculus/Iris, or Embeddium classes. Required mixin metadata, access
  transformer, Voxy classes, and shader resources are present.
- Reobfuscation proof: the all-JAR directly references `SpriteContents` method
  `m_246162_`, `LightTexture` field `f_109870_`, `DynamicTexture` method
  `m_117963_`, and `TextureAtlas` field `f_276072_`.
- No client was launched. This validation closes Pass 2 repairs but does not
  close the audit; Pass 3 must re-freeze and re-read the entire changed tree
  with no inherited coverage.

| ID | Kind | Lines | Bytes | SHA-256 | Pass 2 | Finding IDs | Path |
|---|---|---:|---:|---|---|---|---|
| B001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| B002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| B003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| B004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| B005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| B006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| B007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| B008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| B009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| B010 | main-java | 78 | 2923 | `b9e20039c7125cc0d7ed28f2c8ab8ac690dce5fea24b18790c2358996d8d9432` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| B011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| B012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| B013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| B014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| B015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| B016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| B017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| B018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| B019 | main-java | 106 | 3612 | `c9a7f5e4d345416342190096e671d93a8856a5752a897d1c494dbeb10e16a0b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| B020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| B021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| B022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| B023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| B024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| B025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| B026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| B027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| B028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| B029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| B030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| B031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| B032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| B033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| B034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| B035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| B036 | main-java | 105 | 3459 | `cfa29bfdaa935bdfc1e0aa78b7c0c4cfc36c9c94b5590165248cb52c80fb6e06` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| B037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| B038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| B039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| B040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| B041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| B042 | main-java | 403 | 18097 | `d0f7b958f5e904984562cf86d228c9c26a02c2b0f75c1f5775a7694c78f86b8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| B043 | main-java | 445 | 18123 | `408ececd277723abb6c30b878e62f6fb4077ff1695db204bdcf4a107a80525bd` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| B044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| B045 | main-java | 127 | 4902 | `e6d60e05d3acca86d85eceff7bccbe3ba8474b6ba09fee195410e00f76725bd8` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| B046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| B047 | main-java | 436 | 20391 | `404d6fdcf9e718f6e33f9be23e2fff0741c454452ed780d076f29020c48f600b` | reviewed | P2-F002 | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| B048 | main-java | 220 | 8735 | `1e7fb40a8d69125f921f15a6e2f830619422fe0d5eeaecf5c421fc373fbb7195` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| B049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| B050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| B051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| B052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| B053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| B054 | main-java | 949 | 40799 | `1cf87a263f4e44abe270d3a8bc581ea25864d1dac41cf0608c6496832fc7a319` | reviewed | P2-F001 | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| B055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| B056 | main-java | 291 | 12893 | `b46592d03b77fb7693bd1c347fb55622f2fc91e645f4bfb30e5aba2f222b8ca7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| B057 | main-java | 70 | 2214 | `3d747e767840cbe9e01cc6127dbe3ace2b4b8da95e702301bd6e746671c6cde0` | reviewed | P2-F003 | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| B058 | main-java | 237 | 10619 | `3557684d5e0c0a7089a76067dc2be4fac040c5561c1a8706cca3f5f33427a6c6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| B059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| B060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| B061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| B062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| B063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| B064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| B065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| B066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| B067 | main-java | 106 | 3869 | `1a5978233f01e410f7a20509e6805ae9a6b36f50e77edef80a27c2342cdc208b` | reviewed | P2-F002 | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| B068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| B069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| B070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| B071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| B072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| B073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| B074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| B075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| B076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| B077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| B078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| B079 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| B080 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| B081 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| B082 | main-java | 1116 | 50882 | `16e1d933ab1b9e95e8249a8682b048d653edefcab212956a3bb2743ceb32573d` | reviewed | P2-F004 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| B083 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| B084 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| B085 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| B086 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| B087 | main-java | 1078 | 41968 | `8089c45ab50c47aef427de351703edc5016e85cb9da4262a52c4cf645c6b005e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| B088 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| B089 | main-java | 511 | 20426 | `1fbb2de734a5d70b49455e9efdc163d3c12bce20bce4aa2f569c4e7fe5de2439` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| B090 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| B091 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| B092 | main-java | 280 | 11982 | `4f444f89330b55388708a7b54fbca6875eead3a3b6e731f6b9d6e8ff6c7c7e95` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| B093 | main-java | 280 | 11295 | `9fc997cebcdc7d271f06f777970c427f77e332e6c947f111fe0b2ed3a3d56039` | reviewed | P2-F005 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| B094 | main-java | 599 | 25487 | `2d29f76c5eab6e0133f5af3c0257c3c630a280af76ca6a8df34bc431c1f3e653` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| B095 | main-java | 65 | 2052 | `62995786ed6935f4c5d2284b326de89a1dbf9645fff51926da702f3351c3a991` | reviewed | P2-F006 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| B096 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| B097 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| B098 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| B099 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| B100 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| B101 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| B102 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| B103 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| B104 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| B105 | main-java | 922 | 38889 | `c78d6e369edf7caf7f1ef03fbee5c0b1e6f97d857128dae94e0b283f9bdb06b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| B106 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| B107 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| B108 | main-java | 140 | 5535 | `4f34057d128d79d18c5b7a4412accfd197ffa807f0375cb0cf91dc6e49c253de` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| B109 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| B110 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| B111 | main-java | 38 | 1080 | `37276fd6cd264744f05e623bdc077dd94d325bc2a1585a52bd4b2f57844b3129` | reviewed | P2-F007 | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| B112 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| B113 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| B114 | main-java | 457 | 21522 | `5d5639b6bd594ad56f5dc8e0dc70102f2eaedede58489885bf1d713c728eeb26` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| B115 | main-java | 240 | 11416 | `80771690fa4b29abe74ede2ad52213731b71cb27150dd165a4a5b2e0f47df3cd` | reviewed | P2-F013 | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| B116 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| B117 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| B118 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| B119 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| B120 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| B121 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| B122 | main-java | 782 | 36296 | `924bd24bdf0d7f69d63f961c854d0b93b4f9f6f01131a37eb79be6e269fa835e` | reviewed | P2-F010, P2-F013 | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| B123 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| B124 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| B125 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| B126 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| B127 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| B128 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| B129 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| B130 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| B131 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| B132 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| B133 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| B134 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| B135 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| B136 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| B137 | main-java | 46 | 2038 | `2a782f2d47330077525adfa6b5b3c554ac93ba1a0462f66a8f9a76a8615c8685` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| B138 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| B139 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| B140 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| B141 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| B142 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| B143 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| B144 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| B145 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| B146 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| B147 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| B148 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| B149 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| B150 | main-java | 131 | 5097 | `b2e7fa51f019649320fe5618861284282f7736140505e2a7a232a587df5db62e` | reviewed | P2-F008 | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| B151 | main-java | 1167 | 47847 | `2abf1ee0b3aef5a0d685495dd809d27d2b32485a8bd2ffa80011490c7f08033e` | reviewed | P2-F008, P2-F009, P2-F011, P2-F012 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| B152 | main-java | 59 | 1619 | `b6d31aa4334f0ee6faff06799d8b2e2a577d573a3e6e6fec7feea5bad3735c92` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| B153 | main-java | 339 | 14769 | `3f24038663332cec68c896885299b753ab890c2045225157f27aadfe014f9f65` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| B154 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| B155 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| B156 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| B157 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| B158 | main-java | 268 | 9928 | `0346194f683fbb04e8c6d5df13b837397188de783d46f03cf15de486d750201b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| B159 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| B160 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| B161 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| B162 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| B163 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| B164 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| B165 | main-java | 74 | 3147 | `5ba059898052b935af088ea64e9589f7e1c6f84029b2d40798a9bf6d1fc43e2b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| B166 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| B167 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| B168 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| B169 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| B170 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| B171 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| B172 | main-java | 83 | 3017 | `bc626e5d754577bfec7f8fdf87ae3c05c124b3c47117d7842489090316345728` | reviewed | P2-F014 | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| B173 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| B174 | main-java | 255 | 9829 | `60dd9950e828b4e18e317b33bbb8945058ca1e0be85900f49b9dd9e25b431f95` | reviewed | P2-F015 | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| B175 | main-java | 254 | 10646 | `a791fa63d39dcdcf081f955e9539f524f21f28f8f314d1dab80b91a2d7a09408` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| B176 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| B177 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| B178 | main-java | 254 | 9369 | `e43924cbfec12f350077e8008638d3cee4013a46e698340ee5dab747c662b882` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| B179 | main-java | 67 | 2300 | `c4860904b4ee63b0b13af351e347d0193afe9c8673493704b25b68c7a0008235` | reviewed | P2-F016 | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| B180 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| B181 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| B182 | main-java | 478 | 20116 | `486a71142d3487a29c8b0e19f4943e66b30fbe06d7723a9f929fe03178ef7c53` | reviewed | P2-F017 | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| B183 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| B184 | binary-resource | - | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| B185 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| B186 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| B187 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| B188 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| B189 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| B190 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| B191 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| B192 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| B193 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| B194 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| B195 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| B196 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| B197 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| B198 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| B199 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| B200 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| B201 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| B202 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| B203 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| B204 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| B205 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| B206 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| B207 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| B208 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| B209 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| B210 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| B211 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| B212 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| B213 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| B214 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| B215 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| B216 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| B217 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| B218 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| B219 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| B220 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| B221 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| B222 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| B223 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| B224 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| B225 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| B226 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| B227 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| B228 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| B229 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| B230 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| B231 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| B232 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| B233 | main-resource | 9 | 717 | `c0babcbd19c839c2c96863e94ceee2adf8b8fcf7db32834e286ae7bf70e8349d` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| B234 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| B235 | main-resource | 42 | 848 | `e265d69e26a96649382e08b51edc9005b510f45eba0eb01c38125e26d5e0b234` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| B236 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| B237 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| B238 | test-java | 17 | 711 | `00280e77ef9dea22f1bbd83bad3d10a66da9f232188e9e4b288d51cdc51882d1` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| B239 | test-java | 74 | 2920 | `95f013534eac8b1e1069eb0a05f8e1c69bd3884899fa7640035056b073e7c0c6` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| B240 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| B241 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| B242 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| B243 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| B244 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| B245 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| B246 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| B247 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| B248 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| B249 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| B250 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| B251 | test-java | 20 | 712 | `01a9beb9480fe229215e0dbd978be0ad303d0d18b75fa5681708164113f30a20` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| B252 | test-java | 58 | 2426 | `b96335219a3454c6c20d32ee740065c0ba3f3169a369156481a2c35e88d80085` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| B253 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| B254 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| B255 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| B256 | test-java | 16 | 813 | `46c31de66e6c1f3cf97f4b106ccbde20290029caa69656d9926dd4042ffe446b` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| B257 | test-java | 54 | 1819 | `30e49a93b22354ee7630d1e535bf4a90ffec1976e65881eb31f6c3bf930c1b1e` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| B258 | test-java | 30 | 943 | `a6b3375f749811b9f4a008f2b295371cc2e55524fee2ea9de0bd20d6ba43d49a` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| B259 | test-java | 56 | 2822 | `5196671a0e887aab45cbb31c6b5ff78fb7c46e892d70c0fdb925a4fefe78d11c` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| B260 | test-java | 64 | 2382 | `11f039e5840f988c270265345a797f5cfb4c5060df51b277c56def8ec7ad069d` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| B261 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |

## Pass 1 frozen ledger

| ID | Kind | Lines | Bytes | SHA-256 | Pass 1 | Finding IDs | Path |
|---|---|---:|---:|---|---|---|---|
| A001 | build-logic | 380 | 14922 | `1490ae3cd13c6c50610a3a37693538ecf6a83fb4451e64c72e7bf65597524a1b` | reviewed | — | `build.gradle` |
| A002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | — | `gradle.properties` |
| A003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | — | `gradle/wrapper/gradle-wrapper.properties` |
| A004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | — | `settings.gradle` |
| A005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | — | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| A006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| A007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| A008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| A009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| A010 | main-java | 78 | 2923 | `b9e20039c7125cc0d7ed28f2c8ab8ac690dce5fea24b18790c2358996d8d9432` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| A011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| A012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| A013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| A014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| A015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| A016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| A017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| A018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | — | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| A019 | main-java | 106 | 3612 | `c9a7f5e4d345416342190096e671d93a8856a5752a897d1c494dbeb10e16a0b3` | reviewed | — | `src/main/java/me/cortex/voxy/common/Logger.java` |
| A020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | — | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| A021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | — | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| A022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | — | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| A023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | — | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| A024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | — | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| A025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | — | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| A026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | — | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| A027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | — | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| A028 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | — | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| A029 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | — | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| A030 | main-java | 121 | 3759 | `8f8b0e66e98b69546d4b48eac586226e97fee04c822fba1cdc33ed17b6d67598` | reviewed | P1-F001 | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| A031 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | — | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| A032 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | — | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| A033 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | — | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| A034 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | — | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| A035 | main-java | 105 | 3459 | `cfa29bfdaa935bdfc1e0aa78b7c0c4cfc36c9c94b5590165248cb52c80fb6e06` | reviewed | — | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| A036 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | — | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| A037 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | — | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| A038 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | — | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| A039 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | — | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| A040 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | — | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| A041 | main-java | 403 | 18097 | `d0f7b958f5e904984562cf86d228c9c26a02c2b0f75c1f5775a7694c78f86b8a` | reviewed | — | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| A042 | main-java | 445 | 18123 | `408ececd277723abb6c30b878e62f6fb4077ff1695db204bdcf4a107a80525bd` | reviewed | — | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| A043 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | — | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| A044 | main-java | 127 | 4902 | `e6d60e05d3acca86d85eceff7bccbe3ba8474b6ba09fee195410e00f76725bd8` | reviewed | — | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| A045 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | — | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| A046 | main-java | 436 | 20391 | `404d6fdcf9e718f6e33f9be23e2fff0741c454452ed780d076f29020c48f600b` | reviewed | — | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| A047 | main-java | 201 | 8089 | `a6b3d3e2991807f560f1c242f75d64d5c86de6d47b36699fa58dc776ee4b2571` | reviewed | — | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| A048 | main-java | 340 | 11844 | `2f2a1ef22b35ae5be9e6ee28ed83959093cc69a0c0b80eb0b6f857cb8d84a878` | reviewed | P1-F002 | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| A049 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | — | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| A050 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | — | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| A051 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | — | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| A052 | main-java | 23 | 663 | `9718ae1ea5abb1bb5c47ea0b2fa5784ba8b8b57006b6ad1909dcca07b9974b58` | reviewed | P1-F003 | `src/main/java/me/cortex/voxy/config/ForgeOriginalVoxyCpuLayout.java` |
| A053 | main-java | 74 | 3951 | `0757c6be669519c894fd9706cb4d751e174bf437982baffba79047c3eb7c9499` | reviewed | — | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| A054 | main-java | 956 | 41162 | `b1eab5566a4c4d536887c5419bc6b46179309da05138976c4753c7b6029c8325` | reviewed | P1-F013 | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| A055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | — | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| A056 | main-java | 269 | 12408 | `058793342dc7b343d885d9bde0579ea4bb741723088b815223cadbbbd5f9fda5` | reviewed | P1-F004 | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| A057 | main-java | 70 | 2214 | `3d747e767840cbe9e01cc6127dbe3ace2b4b8da95e702301bd6e746671c6cde0` | reviewed | — | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| A058 | main-java | 237 | 10619 | `3557684d5e0c0a7089a76067dc2be4fac040c5561c1a8706cca3f5f33427a6c6` | reviewed | — | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| A059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| A060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| A061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| A062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | — | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| A063 | main-java | 142 | 7358 | `48ba6a1a7a351b35cf2b6f1d57b7540aa85191999082be82e2b0a02d89ec9188` | reviewed | P1-F005 | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| A064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | — | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| A065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | — | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| A066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| A067 | main-java | 106 | 3869 | `1a5978233f01e410f7a20509e6805ae9a6b36f50e77edef80a27c2342cdc208b` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| A068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| A069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| A070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| A071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| A072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| A073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| A074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| A075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| A076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| A077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| A078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| A079 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| A080 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| A081 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| A082 | main-java | 1112 | 50752 | `f57f4ee40cc40a335f639641b7e89c1ea0653010eb9d792273cdbb2d708d89b1` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| A083 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| A084 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| A085 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| A086 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| A087 | main-java | 1078 | 41968 | `8089c45ab50c47aef427de351703edc5016e85cb9da4262a52c4cf645c6b005e` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| A088 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| A089 | main-java | 511 | 20426 | `1fbb2de734a5d70b49455e9efdc163d3c12bce20bce4aa2f569c4e7fe5de2439` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| A090 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| A091 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| A092 | main-java | 280 | 11982 | `4f444f89330b55388708a7b54fbca6875eead3a3b6e731f6b9d6e8ff6c7c7e95` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| A093 | main-java | 280 | 11295 | `9fc997cebcdc7d271f06f777970c427f77e332e6c947f111fe0b2ed3a3d56039` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| A094 | main-java | 547 | 23706 | `36c2fc039d6b127107e87d4c46dd765564eb96e0dd56f860efa672314c396f31` | reviewed | P1-F007 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| A095 | main-java | 65 | 2052 | `62995786ed6935f4c5d2284b326de89a1dbf9645fff51926da702f3351c3a991` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| A096 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| A097 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| A098 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| A099 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| A100 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| A101 | main-java | 480 | 21616 | `752bba2c211609d11dcd6a75fc0c8717eb7b31e0e99eab0de6637338c5b2ce51` | reviewed | P1-F008 | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| A102 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| A103 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| A104 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| A105 | main-java | 905 | 38328 | `bcf6c1a2bb5f3ec734798640365b0f29707d1ddf80399de3994fcc5b805eeab2` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| A106 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| A107 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | — | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| A108 | main-java | 158 | 6443 | `d909e7010818f1dba2e1eda6580d619734bef185307b7aa94ef8d4070873a546` | reviewed | P1-F010, P1-F011 | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| A109 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | — | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| A110 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | — | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| A111 | main-java | 38 | 1080 | `37276fd6cd264744f05e623bdc077dd94d325bc2a1585a52bd4b2f57844b3129` | reviewed | — | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| A112 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | — | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| A113 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | — | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| A114 | main-java | 457 | 21522 | `5d5639b6bd594ad56f5dc8e0dc70102f2eaedede58489885bf1d713c728eeb26` | reviewed | — | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| A115 | main-java | 240 | 11416 | `80771690fa4b29abe74ede2ad52213731b71cb27150dd165a4a5b2e0f47df3cd` | reviewed | — | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| A116 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| A117 | main-java | 13 | 316 | `6127f0e2d1df7181cfc8bd0b815593615c8652b0d21bf248cb019d2c5f0a3cd0` | reviewed | P1-F006 | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| A118 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| A119 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | — | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| A120 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | — | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| A121 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | — | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| A122 | main-java | 782 | 36296 | `924bd24bdf0d7f69d63f961c854d0b93b4f9f6f01131a37eb79be6e269fa835e` | reviewed | — | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| A123 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | — | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| A124 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | — | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| A125 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| A126 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| A127 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| A128 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| A129 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| A130 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| A131 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| A132 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| A133 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| A134 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| A135 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| A136 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| A137 | main-java | 39 | 1687 | `1d02529183d3932e96cbc76e092cd8fc25964dd75022f8101676717461305510` | reviewed | P1-F012 | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| A138 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| A139 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| A140 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| A141 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| A142 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| A143 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| A144 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| A145 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| A146 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| A147 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| A148 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| A149 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | — | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| A150 | main-java | 131 | 5097 | `b2e7fa51f019649320fe5618861284282f7736140505e2a7a232a587df5db62e` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| A151 | main-java | 1132 | 46913 | `30b2b3203f01dcaaf53f7f111469931e7290d3a1ec5bb3af01e6373a27ae4e15` | reviewed | P1-F009, P1-F015 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| A152 | main-java | 59 | 1619 | `b6d31aa4334f0ee6faff06799d8b2e2a577d573a3e6e6fec7feea5bad3735c92` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| A153 | main-java | 339 | 14769 | `3f24038663332cec68c896885299b753ab890c2045225157f27aadfe014f9f65` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| A154 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | — | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| A155 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | — | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| A156 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | — | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| A157 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | — | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| A158 | main-java | 268 | 9928 | `0346194f683fbb04e8c6d5df13b837397188de783d46f03cf15de486d750201b` | reviewed | — | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| A159 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| A160 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | — | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| A161 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | — | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| A162 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | — | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| A163 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | — | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| A164 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | — | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| A165 | main-java | 85 | 3761 | `7a6f55f7c8d6d45b0239bbda6dc25d4d7355a42e4fc7692c0b11ecf5bf8a348c` | reviewed | P1-F004 | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| A166 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | — | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| A167 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| A168 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | — | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| A169 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | — | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| A170 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| A171 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | — | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| A172 | main-java | 83 | 3017 | `bc626e5d754577bfec7f8fdf87ae3c05c124b3c47117d7842489090316345728` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| A173 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | — | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| A174 | main-java | 255 | 9829 | `60dd9950e828b4e18e317b33bbb8945058ca1e0be85900f49b9dd9e25b431f95` | reviewed | — | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| A175 | main-java | 254 | 10646 | `a791fa63d39dcdcf081f955e9539f524f21f28f8f314d1dab80b91a2d7a09408` | reviewed | — | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| A176 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | — | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| A177 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | — | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| A178 | main-java | 254 | 9369 | `e43924cbfec12f350077e8008638d3cee4013a46e698340ee5dab747c662b882` | reviewed | — | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| A179 | main-java | 67 | 2300 | `c4860904b4ee63b0b13af351e347d0193afe9c8673493704b25b68c7a0008235` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| A180 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | — | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| A181 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | — | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| A182 | main-java | 478 | 20116 | `486a71142d3487a29c8b0e19f4943e66b30fbe06d7723a9f929fe03178ef7c53` | reviewed | — | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| A183 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | — | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| A184 | binary-resource | binary | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | — | `src/main/resources/assets/voxy/icon.png` |
| A185 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | — | `src/main/resources/assets/voxy/lang/en_us.json` |
| A186 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | — | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| A187 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | — | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| A188 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | — | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| A189 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | — | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| A190 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | — | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| A191 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| A192 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| A193 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| A194 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| A195 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| A196 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| A197 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| A198 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| A199 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| A200 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| A201 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| A202 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| A203 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| A204 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| A205 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| A206 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| A207 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| A208 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| A209 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| A210 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| A211 | shader | 201 | 7319 | `44011ab0bb72c5849ee3d97f6e4b63bf83ca3f8ac7db85d503139ae3259c3e17` | reviewed | P1-F014 | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| A212 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| A213 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| A214 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| A215 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| A216 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| A217 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | — | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| A218 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | — | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| A219 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | — | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| A220 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | — | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| A221 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | — | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| A222 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | — | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| A223 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | — | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| A224 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | — | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| A225 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | — | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| A226 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | — | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| A227 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | — | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| A228 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | — | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| A229 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | — | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| A230 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | — | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| A231 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | — | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| A232 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | — | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| A233 | main-resource | 8 | 646 | `13845bfaea0a7b3042af9f072934be9b8c68400e6540cb72ac6977139f1bad06` | reviewed | P1-F015 | `src/main/resources/META-INF/accesstransformer.cfg` |
| A234 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | — | `src/main/resources/META-INF/MANIFEST.MF` |
| A235 | main-resource | 42 | 848 | `e265d69e26a96649382e08b51edc9005b510f45eba0eb01c38125e26d5e0b234` | reviewed | — | `src/main/resources/META-INF/mods.toml` |
| A236 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | — | `src/main/resources/pack.mcmeta` |
| A237 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | — | `src/main/resources/voxy.forge.mixins.json` |
| A238 | test-java | 17 | 711 | `00280e77ef9dea22f1bbd83bad3d10a66da9f232188e9e4b288d51cdc51882d1` | reviewed | — | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| A239 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | — | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| A240 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | — | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| A241 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | — | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| A242 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | — | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| A243 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | — | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| A244 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | — | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| A245 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | — | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| A246 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | — | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| A247 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | — | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| A248 | test-java | 20 | 712 | `01a9beb9480fe229215e0dbd978be0ad303d0d18b75fa5681708164113f30a20` | reviewed | — | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| A249 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | — | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| A250 | test-java | 30 | 943 | `a6b3375f749811b9f4a008f2b295371cc2e55524fee2ea9de0bd20d6ba43d49a` | reviewed | — | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| A251 | test-java | 64 | 2382 | `11f039e5840f988c270265345a797f5cfb4c5060df51b277c56def8ec7ad069d` | reviewed | — | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| A252 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | — | `tools/generate-line-audit-inventory.ps1` |


## Pass 3 frozen ledger

Snapshot frozen after the complete Pass 2 repair batch, forced clean test/JarJar
validation, and packaged-bytecode inspection: **270 files / 38,118 text lines /
1,591,782 bytes**. No Pass 1 or Pass 2 coverage is inherited. Every C row
begins at `pending` and may move to `reviewed` only after every frozen physical
line in that file has been read to EOF and compared under the protocol above.

Frozen kind counts:

- build logic: 2; build config: 2
- main Java: 180; test Java: 31
- main resources: 10; shaders: 43
- binary resources: 1; audit tools: 1

Contiguous review partitions (balanced by physical lines):

- C001-C073: 9,592 lines
- C074-C111: 9,532 lines
- C112-C162: 9,599 lines
- C163-C270: 9,395 lines

### Pass 3 full-review freeze proof

- All C001-C270 physical lines were read to EOF: **270 files / 38,118 text
  lines / 1,591,782 bytes**.
- Independent partitions covered C001-C073, C074-C111, and C112-C162; the
  root partition covered C163-C270, including every shader/resource/test line,
  the inventory tool, and visual/hash inspection of the binary icon.
- Every partition independently recomputed its frozen paths, byte lengths,
  physical line counts, and SHA-256 values with zero mismatches. The root then
  reran the complete inventory comparison: **270 ledger rows / 270 current
  rows / 0 hash, line, byte, missing-path, or extra-path mismatches**.
- No in-scope source or resource was modified before this zero-drift proof.
- Three confirmed Forge-port findings are recorded as P3-F001 through
  P3-F003. Repair begins only after this proof; no client was launched.
- Shader-object cleanup after a later stage fails and partial model-bakery
  construction cleanup were explicitly compared with original Voxy and
  excluded because the same exceptional-path ownership gap exists upstream and
  the Forge path is not distinctly worse.

### Pass 3 repair batch validation

- Repair policy: no Pass 3 finding was modified until all 270 frozen C rows
  had been reviewed and the full snapshot comparison proved zero mismatches.
- P3-F001 isolates client-only HUD bytecode behind a Forge distribution guard;
  P3-F002 restores original malformed-NBT type/default handling; P3-F003
  restores independent draw/read framebuffer state.
- Static gate: `rtk git diff --check` passed.
- Compile/test gate: `rtk test .\\gradlew compileJava test --stacktrace
  --console=plain` completed successfully on 2026-07-14, including focused
  bytecode, NBT, and GL-state source-contract coverage.
- No client was launched. Pass 3 is repaired, but completion still requires a
  newly frozen full-tree pass with zero new findings.

| ID | Kind | Lines | Bytes | SHA-256 | Pass 3 | Finding IDs | Path |
|---|---|---:|---:|---|---|---|---|
| C001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| C002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| C003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| C004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| C005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| C006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| C007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| C008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| C009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| C010 | main-java | 78 | 2923 | `b9e20039c7125cc0d7ed28f2c8ab8ac690dce5fea24b18790c2358996d8d9432` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| C011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| C012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| C013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| C014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| C015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| C016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| C017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| C018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| C019 | main-java | 106 | 3612 | `c9a7f5e4d345416342190096e671d93a8856a5752a897d1c494dbeb10e16a0b3` | reviewed | P3-F001 | `src/main/java/me/cortex/voxy/common/Logger.java` |
| C020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| C021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| C022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| C023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| C024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| C025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| C026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| C027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| C028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| C029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| C030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| C031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| C032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| C033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| C034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| C035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| C036 | main-java | 105 | 3459 | `cfa29bfdaa935bdfc1e0aa78b7c0c4cfc36c9c94b5590165248cb52c80fb6e06` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| C037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| C038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| C039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| C040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| C041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| C042 | main-java | 403 | 18097 | `d0f7b958f5e904984562cf86d228c9c26a02c2b0f75c1f5775a7694c78f86b8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| C043 | main-java | 445 | 18123 | `408ececd277723abb6c30b878e62f6fb4077ff1695db204bdcf4a107a80525bd` | reviewed | P3-F002 | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| C044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| C045 | main-java | 127 | 4902 | `e6d60e05d3acca86d85eceff7bccbe3ba8474b6ba09fee195410e00f76725bd8` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| C046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| C047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| C048 | main-java | 220 | 8735 | `1e7fb40a8d69125f921f15a6e2f830619422fe0d5eeaecf5c421fc373fbb7195` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| C049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| C050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| C051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| C052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| C053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| C054 | main-java | 946 | 40552 | `7bef679a87a7c54463c0cc071f364934d0eef06794db2bf5518ef86a7ff4b19c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| C055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| C056 | main-java | 291 | 12893 | `b46592d03b77fb7693bd1c347fb55622f2fc91e645f4bfb30e5aba2f222b8ca7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| C057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| C058 | main-java | 237 | 10619 | `3557684d5e0c0a7089a76067dc2be4fac040c5561c1a8706cca3f5f33427a6c6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| C059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| C060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| C061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| C062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| C063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| C064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| C065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| C066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| C067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| C068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| C069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| C070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| C071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| C072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| C073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| C074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| C075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| C076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| C077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| C078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| C079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| C080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| C081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| C082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| C083 | main-java | 1144 | 52062 | `fd76d5cad712973640f99a751a9580cea7685a81148cda77e6c4de55fb73964f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| C084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| C085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| C086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| C087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| C088 | main-java | 1078 | 41968 | `8089c45ab50c47aef427de351703edc5016e85cb9da4262a52c4cf645c6b005e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| C089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| C090 | main-java | 511 | 20426 | `1fbb2de734a5d70b49455e9efdc163d3c12bce20bce4aa2f569c4e7fe5de2439` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| C091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| C092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| C093 | main-java | 280 | 11982 | `4f444f89330b55388708a7b54fbca6875eead3a3b6e731f6b9d6e8ff6c7c7e95` | reviewed | P3-F003 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| C094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| C095 | main-java | 599 | 25487 | `2d29f76c5eab6e0133f5af3c0257c3c630a280af76ca6a8df34bc431c1f3e653` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| C096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| C097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| C098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| C099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| C100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| C101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| C102 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| C103 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| C104 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| C105 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| C106 | main-java | 922 | 38889 | `c78d6e369edf7caf7f1ef03fbee5c0b1e6f97d857128dae94e0b283f9bdb06b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| C107 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| C108 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| C109 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| C110 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| C111 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| C112 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| C113 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| C114 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| C115 | main-java | 457 | 21522 | `5d5639b6bd594ad56f5dc8e0dc70102f2eaedede58489885bf1d713c728eeb26` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| C116 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| C117 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| C118 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| C119 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| C120 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| C121 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| C122 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| C123 | main-java | 800 | 37152 | `d25498501d626b98c4f85ed2af3385f3599f90a34a65df0b0aa08af8e0ac2fbe` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| C124 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| C125 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| C126 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| C127 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| C128 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| C129 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| C130 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| C131 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| C132 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| C133 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| C134 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| C135 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| C136 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| C137 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| C138 | main-java | 46 | 2038 | `2a782f2d47330077525adfa6b5b3c554ac93ba1a0462f66a8f9a76a8615c8685` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| C139 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| C140 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| C141 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| C142 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| C143 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| C144 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| C145 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| C146 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| C147 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| C148 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| C149 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| C150 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| C151 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| C152 | main-java | 1132 | 46904 | `103a1517ec21fc5fef074dbb9c19a1dc83bc242d2c53330585e70a84aa400ccb` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| C153 | main-java | 59 | 1619 | `b6d31aa4334f0ee6faff06799d8b2e2a577d573a3e6e6fec7feea5bad3735c92` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| C154 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| C155 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| C156 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| C157 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| C158 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| C159 | main-java | 268 | 9928 | `0346194f683fbb04e8c6d5df13b837397188de783d46f03cf15de486d750201b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| C160 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| C161 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| C162 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| C163 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| C164 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| C165 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| C166 | main-java | 74 | 3147 | `5ba059898052b935af088ea64e9589f7e1c6f84029b2d40798a9bf6d1fc43e2b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| C167 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| C168 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| C169 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| C170 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| C171 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| C172 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| C173 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| C174 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| C175 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| C176 | main-java | 254 | 10646 | `a791fa63d39dcdcf081f955e9539f524f21f28f8f314d1dab80b91a2d7a09408` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| C177 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| C178 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| C179 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| C180 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| C181 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| C182 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| C183 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| C184 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| C185 | binary-resource | null | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| C186 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| C187 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| C188 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| C189 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| C190 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| C191 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| C192 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| C193 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| C194 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| C195 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| C196 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| C197 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| C198 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| C199 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| C200 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| C201 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| C202 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| C203 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| C204 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| C205 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| C206 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| C207 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| C208 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| C209 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| C210 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| C211 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| C212 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| C213 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| C214 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| C215 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| C216 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| C217 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| C218 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| C219 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| C220 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| C221 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| C222 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| C223 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| C224 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| C225 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| C226 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| C227 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| C228 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| C229 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| C230 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| C231 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| C232 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| C233 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| C234 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| C235 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| C236 | main-resource | 42 | 848 | `e265d69e26a96649382e08b51edc9005b510f45eba0eb01c38125e26d5e0b234` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| C237 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| C238 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| C239 | test-java | 17 | 711 | `00280e77ef9dea22f1bbd83bad3d10a66da9f232188e9e4b288d51cdc51882d1` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| C240 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| C241 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| C242 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| C243 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| C244 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| C245 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| C246 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| C247 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| C248 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| C249 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| C250 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| C251 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| C252 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| C253 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| C254 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| C255 | test-java | 20 | 712 | `01a9beb9480fe229215e0dbd978be0ad303d0d18b75fa5681708164113f30a20` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| C256 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| C257 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| C258 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| C259 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| C260 | test-java | 16 | 813 | `46c31de66e6c1f3cf97f4b106ccbde20290029caa69656d9926dd4042ffe446b` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| C261 | test-java | 54 | 1819 | `30e49a93b22354ee7630d1e535bf4a90ffec1976e65881eb31f6c3bf930c1b1e` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| C262 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| C263 | test-java | 30 | 943 | `a6b3375f749811b9f4a008f2b295371cc2e55524fee2ea9de0bd20d6ba43d49a` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| C264 | test-java | 56 | 2822 | `5196671a0e887aab45cbb31c6b5ff78fb7c46e892d70c0fdb925a4fefe78d11c` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| C265 | test-java | 173 | 9952 | `8b811ce42b101dfb43b471ce4780d19188ca5644163428778983d0e973bac3fe` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| C266 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| C267 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| C268 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| C269 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| C270 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |

## Pass 4 complete frozen-review proof

- Freeze: 271 files / 38,237 physical text lines / 1,596,803 bytes.
- Independent clean-room partitions covered D001-D073 (73 files / 9,619
  lines), D074-D111 (38 / 9,536), and D112-D162 (51 / 9,599); the root
  partition covered D163-D271 (109 / 9,483), including every resource,
  shader, test, the binary icon, and the inventory tool.
- Every partition physically read every frozen file through EOF and then
  recomputed path existence, line count, byte count, and SHA-256. Each
  partition reported zero mismatches.
- The root reran `tools/generate-line-audit-inventory.ps1` after all four
  partitions completed and compared every current row with the frozen D
  inventory: **271 frozen rows / 271 current rows / 38,237 lines / 1,596,803
  bytes / 0 hash, line, byte, kind, missing-path, or extra-path mismatches**.
- No in-scope source, resource, test, build file, or audit tool was modified
  before that whole-tree zero-drift proof. Documentation was updated as
  findings were confirmed because audit docs are deliberately outside the
  frozen executable inventory.
- Five findings are recorded as P4-F001 through P4-F005: contained-fluid tint
  state propagation, restoration of original common diagnostic routing,
  complete GL-state isolation at the Embeddium injection boundary, the broken
  Oculus rectangle target accessor, and the first boundary test's dead-code
  coverage gap. No client was launched.
- Repair starts only after this proof. Because Pass 4 found defects, it cannot
  be the required zero-new-finding terminal pass; the repaired tree must be
  frozen and reread in full as Pass 5.

## Pass 4 repair batch validation

- P4-F001 now follows the complete original `ModelFactory` ordering: the
  dedupe key uses the parent's own tint state, duplicate models return without
  registering a new colour table, and a contained fluid's biome dependency is
  then merged into one shared `TintPlan` before finalization. CPU metadata,
  GPU flags, colour/base-index encoding, and biome LUT registration all consume
  that merged state. A no-tint parent keeps colour `-1` without a LUT; a
  constant-tint parent converts to a biome LUT while retaining its pre-merge
  dedupe colour.
- P4-F002 restores the six original common-layer callers to
  `me.cortex.voxy.common.Logger`. Independent review confirmed exact message,
  level, throwable, DFU callback, and dedicated-server bytecode semantics.
- P4-F003 makes the Forge/Embeddium boundary round-trip the active renderer's
  indexed UBO/SSBO names and ranges, generic buffer names, array/draw/dispatch
  indirect bindings, unpack state, image unit 0, separate front/back stencil
  state, and legal core-profile polygon state. Texture zero-binding is now
  target-specific; all units retain their 2D/sampler state and only actual
  Oculus sampler slots at base unit 6 add bounded 1D/3D/rectangle capture.
- Repair cross-review added P4-F004 after `javap` of the actual Oculus 1.8 JAR
  proved that `TEXTURE_RECTANGLE.getGlType()` incorrectly returns
  `GL_TEXTURE_3D`. The Forge adapter now maps enum identity to the four correct
  targets explicitly, so rectangle capture/clear/restore cannot leak or damage
  the 3D binding.
- The same review added P4-F005 because the first source-contract test did not
  prove that the state helpers were connected to the active render route. It
  now locks capture, restore arming, first mutation ordering, the outer
  `finally`, every ModelPipeline -> RenderPipeline -> ImageSet delegate hop,
  and all four real Oculus enum identities.
- `rtk git diff --check` passed. `rtk test .\\gradlew compileJava test
  --rerun-tasks --stacktrace --console=plain` completed successfully in 20
  seconds with all seven Gradle tasks executed. Focused model-tint, common
  logger, GL-boundary, and texture-target contracts also passed during the
  repair reviews.
- The forced post-repair `rtk test .\\gradlew clean test jarJar --rerun-tasks
  --stacktrace --console=plain` gate exited successfully. The resulting
  `voxy-forge-0.2.17-beta-forge-all.jar` is 12,584,005 bytes, has SHA-256
  `30c58e9dc85da6c56e5de10e0d343d1a9f365c93495ea0e66e1edcf4a9482570`,
  contains 457 ZIP entries, and contains zero bundled Minecraft, Forge,
  LWJGL, Oculus/Iris, Embeddium, or Sodium dependency-class entries.
- No client was launched. Pass 4 is repaired but cannot close XXVII because it
  found defects; Pass 5 must freeze the resulting executable inventory and
  physically reread every file from zero inherited coverage.

## Pass 4 frozen ledger

Snapshot frozen after the complete Pass 3 repair batch, successful
`compileJava test`, forced clean `test jarJar`, and packaged-artifact
inspection: **271 files / 38,237 text lines / 1,596,803 bytes**. No prior-pass
coverage is inherited. Every D row starts at `pending` and may move to
`reviewed` only after all frozen physical lines reach EOF.

Balanced review partitions:

- D001-D073: 73 files / 9,619 lines
- D074-D111: 38 files / 9,536 lines
- D112-D162: 51 files / 9,599 lines
- D163-D271: 109 files / 9,483 lines

| ID | Kind | Lines | Bytes | SHA-256 | Pass 4 | Finding IDs | Path |
|---|---|---:|---:|---|---|---|---|
| D001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| D002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| D003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| D004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| D005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| D006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| D007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| D008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| D009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| D010 | main-java | 78 | 2923 | `b9e20039c7125cc0d7ed28f2c8ab8ac690dce5fea24b18790c2358996d8d9432` | reviewed | P4-F002 | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| D011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| D012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| D013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| D014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| D015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| D016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| D017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| D018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| D019 | main-java | 117 | 3948 | `f81142da3322e9aaa3b03e9d61ef92cf567ddd8a4e269d657b9fd2caef79f5b7` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| D020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| D021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| D022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| D023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| D024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| D025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| D026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| D027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| D028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| D029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| D030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| D031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| D032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| D033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| D034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| D035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| D036 | main-java | 105 | 3459 | `cfa29bfdaa935bdfc1e0aa78b7c0c4cfc36c9c94b5590165248cb52c80fb6e06` | reviewed | P4-F002 | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| D037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| D038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| D039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| D040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| D041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| D042 | main-java | 403 | 18097 | `d0f7b958f5e904984562cf86d228c9c26a02c2b0f75c1f5775a7694c78f86b8a` | reviewed | P4-F002 | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| D043 | main-java | 461 | 18726 | `29a6105d402f76434eb9be3848975eabff542068bc4737c0d722e42682184d11` | reviewed | P4-F002 | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| D044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| D045 | main-java | 127 | 4902 | `e6d60e05d3acca86d85eceff7bccbe3ba8474b6ba09fee195410e00f76725bd8` | reviewed | P4-F002 | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| D046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| D047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| D048 | main-java | 220 | 8735 | `1e7fb40a8d69125f921f15a6e2f830619422fe0d5eeaecf5c421fc373fbb7195` | reviewed | P4-F002 | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| D049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| D050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| D051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| D052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| D053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| D054 | main-java | 946 | 40552 | `7bef679a87a7c54463c0cc071f364934d0eef06794db2bf5518ef86a7ff4b19c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| D055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| D056 | main-java | 291 | 12893 | `b46592d03b77fb7693bd1c347fb55622f2fc91e645f4bfb30e5aba2f222b8ca7` | reviewed | P4-F003 | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| D057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| D058 | main-java | 237 | 10619 | `3557684d5e0c0a7089a76067dc2be4fac040c5561c1a8706cca3f5f33427a6c6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| D059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| D060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| D061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| D062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| D063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| D064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| D065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| D066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| D067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| D068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| D069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| D070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| D071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| D072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| D073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| D074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| D075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| D076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| D077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| D078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| D079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| D080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| D081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| D082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| D083 | main-java | 1144 | 52062 | `fd76d5cad712973640f99a751a9580cea7685a81148cda77e6c4de55fb73964f` | reviewed | P4-F003 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| D084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| D085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| D086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| D087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| D088 | main-java | 1078 | 41968 | `8089c45ab50c47aef427de351703edc5016e85cb9da4262a52c4cf645c6b005e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| D089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| D090 | main-java | 511 | 20426 | `1fbb2de734a5d70b49455e9efdc163d3c12bce20bce4aa2f569c4e7fe5de2439` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| D091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| D092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| D093 | main-java | 284 | 12219 | `d4fc421055ef460275647ed940f9651fe958b2156a217f435ba97fabf70d14dd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| D094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| D095 | main-java | 599 | 25487 | `2d29f76c5eab6e0133f5af3c0257c3c630a280af76ca6a8df34bc431c1f3e653` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| D096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| D097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| D098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| D099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| D100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| D101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| D102 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| D103 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| D104 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| D105 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| D106 | main-java | 922 | 38889 | `c78d6e369edf7caf7f1ef03fbee5c0b1e6f97d857128dae94e0b283f9bdb06b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| D107 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| D108 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| D109 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| D110 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| D111 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| D112 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| D113 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| D114 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| D115 | main-java | 457 | 21522 | `5d5639b6bd594ad56f5dc8e0dc70102f2eaedede58489885bf1d713c728eeb26` | reviewed | P4-F003 | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| D116 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| D117 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| D118 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| D119 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| D120 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| D121 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| D122 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| D123 | main-java | 800 | 37152 | `d25498501d626b98c4f85ed2af3385f3599f90a34a65df0b0aa08af8e0ac2fbe` | reviewed | P4-F003 | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| D124 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| D125 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| D126 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| D127 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| D128 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| D129 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| D130 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| D131 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| D132 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| D133 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| D134 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| D135 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| D136 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| D137 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| D138 | main-java | 46 | 2038 | `2a782f2d47330077525adfa6b5b3c554ac93ba1a0462f66a8f9a76a8615c8685` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| D139 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| D140 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| D141 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| D142 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| D143 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| D144 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| D145 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| D146 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| D147 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| D148 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| D149 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| D150 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| D151 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| D152 | main-java | 1132 | 46904 | `103a1517ec21fc5fef074dbb9c19a1dc83bc242d2c53330585e70a84aa400ccb` | reviewed | P4-F001 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| D153 | main-java | 59 | 1619 | `b6d31aa4334f0ee6faff06799d8b2e2a577d573a3e6e6fec7feea5bad3735c92` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| D154 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| D155 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| D156 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| D157 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| D158 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| D159 | main-java | 268 | 9928 | `0346194f683fbb04e8c6d5df13b837397188de783d46f03cf15de486d750201b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| D160 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| D161 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| D162 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| D163 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| D164 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| D165 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| D166 | main-java | 74 | 3147 | `5ba059898052b935af088ea64e9589f7e1c6f84029b2d40798a9bf6d1fc43e2b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| D167 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| D168 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| D169 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| D170 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| D171 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| D172 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| D173 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| D174 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| D175 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| D176 | main-java | 254 | 10646 | `a791fa63d39dcdcf081f955e9539f524f21f28f8f314d1dab80b91a2d7a09408` | reviewed | P4-F003 | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| D177 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| D178 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| D179 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| D180 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| D181 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| D182 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| D183 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| D184 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| D185 | binary-resource | null | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| D186 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| D187 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| D188 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| D189 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| D190 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| D191 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| D192 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| D193 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| D194 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| D195 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| D196 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| D197 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| D198 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| D199 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| D200 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| D201 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| D202 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| D203 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| D204 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| D205 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| D206 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| D207 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| D208 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| D209 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| D210 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| D211 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| D212 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| D213 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| D214 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| D215 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| D216 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| D217 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| D218 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| D219 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| D220 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| D221 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| D222 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| D223 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| D224 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| D225 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| D226 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| D227 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| D228 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| D229 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| D230 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| D231 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| D232 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| D233 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| D234 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| D235 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| D236 | main-resource | 42 | 848 | `e265d69e26a96649382e08b51edc9005b510f45eba0eb01c38125e26d5e0b234` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| D237 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| D238 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| D239 | test-java | 17 | 711 | `00280e77ef9dea22f1bbd83bad3d10a66da9f232188e9e4b288d51cdc51882d1` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| D240 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| D241 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| D242 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| D243 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| D244 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| D245 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| D246 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| D247 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| D248 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| D249 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| D250 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| D251 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| D252 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| D253 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| D254 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| D255 | test-java | 20 | 712 | `01a9beb9480fe229215e0dbd978be0ad303d0d18b75fa5681708164113f30a20` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| D256 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| D257 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| D258 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| D259 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| D260 | test-java | 16 | 813 | `46c31de66e6c1f3cf97f4b106ccbde20290029caa69656d9926dd4042ffe446b` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| D261 | test-java | 54 | 1819 | `30e49a93b22354ee7630d1e535bf4a90ffec1976e65881eb31f6c3bf930c1b1e` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| D262 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| D263 | test-java | 30 | 943 | `a6b3375f749811b9f4a008f2b295371cc2e55524fee2ea9de0bd20d6ba43d49a` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| D264 | test-java | 56 | 2822 | `5196671a0e887aab45cbb31c6b5ff78fb7c46e892d70c0fdb925a4fefe78d11c` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| D265 | test-java | 173 | 9952 | `8b811ce42b101dfb43b471ce4780d19188ca5644163428778983d0e973bac3fe` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| D266 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| D267 | test-java | 88 | 3845 | `bdc6d91b8d31c377e320369422453125cc64fdc5c4564af14a171ec265677841` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java` |
| D268 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| D269 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| D270 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| D271 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |


## Pass 5 frozen inventory summary

- Files: 272
- Text lines: 38,859
- Bytes: 1,627,437
- audit-tool: 1
- binary-resource: 1
- build-config: 2
- build-logic: 2
- main-java: 181
- main-resource: 10
- shader: 43
- test-java: 32

## Pass 5 frozen ledger

Snapshot frozen only after the complete Pass 4 repair batch, independent repair
cross-review, successful forced clean test/JarJar, and packaged-artifact
inspection: **272 files / 38,859 text lines / 1,627,437 bytes**. No prior-pass
coverage is inherited. Every E row starts at `pending` and may move to
`reviewed` only after all frozen physical lines reach EOF. No production,
test, resource, build, or audit-tool repair may begin until all 272 rows are
reviewed and a final whole-inventory hash/line/byte/kind comparison proves zero
drift.

| ID | Kind | Lines | Bytes | SHA-256 | Status | Finding | Path |
|---|---|---:|---:|---|---|---|---|
| E001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| E002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| E003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| E004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| E005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| E006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| E007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| E008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| E009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| E010 | main-java | 77 | 2860 | `5532ef6577e4e7b564c3eabd31bc1982636a4e0389d3d2a53d11a75e86554cf3` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| E011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| E012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| E013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| E014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| E015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| E016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| E017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| E018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| E019 | main-java | 117 | 3948 | `f81142da3322e9aaa3b03e9d61ef92cf567ddd8a4e269d657b9fd2caef79f5b7` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| E020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| E021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| E022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| E023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| E024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| E025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| E026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| E027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| E028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| E029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| E030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| E031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| E032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| E033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| E034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| E035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| E036 | main-java | 100 | 3199 | `4fa71e2c21e150420c19da83e2e1dc8c9baf83431b46af55d6d53f6cd5a75f20` | reviewed | P5-F001 | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| E037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| E038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| E039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| E040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| E041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| E042 | main-java | 401 | 18014 | `e0ac18566fbafcad7087f9911ab4bf6b626b05fcc0da73e7c24e58b70a6894ac` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| E043 | main-java | 460 | 18654 | `fb9ad4336baf4d7fcd6072b7baafc98f0deefa8afb6912e3422bac48fd811400` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| E044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| E045 | main-java | 126 | 4826 | `2ca07119fd09514d8b6c2c2f05db6b929c45041822afcde05c411b9525ac4aed` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| E046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| E047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| E048 | main-java | 218 | 8557 | `a1673cd263b10ab2978ef7c867fb49dc122fa9295693b719fc48ac44455788bf` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| E049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| E050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| E051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| E052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| E053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| E054 | main-java | 946 | 40552 | `7bef679a87a7c54463c0cc071f364934d0eef06794db2bf5518ef86a7ff4b19c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| E055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| E056 | main-java | 291 | 12893 | `b46592d03b77fb7693bd1c347fb55622f2fc91e645f4bfb30e5aba2f222b8ca7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| E057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| E058 | main-java | 237 | 10641 | `237211e080f5ce86983c26b5e50bb8e6bc62284fab39776c9f82dc34b333e7ef` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| E059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| E060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| E061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| E062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| E063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| E064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| E065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| E066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| E067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| E068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| E069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| E070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| E071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| E072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| E073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| E074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| E075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| E076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| E077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| E078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| E079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| E080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| E081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| E082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| E083 | main-java | 1289 | 59280 | `ed005e1afd3a7c4ecd241bcc110e0047697dfab80f8ebe9cb2cc9ed6438fed93` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| E084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| E085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| E086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| E087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| E088 | main-java | 1130 | 44334 | `f67a1019097ecd5fb39c70faec1f9ca01a962dddd77ab259e71d8d0cdec36be4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| E089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| E090 | main-java | 511 | 20426 | `1fbb2de734a5d70b49455e9efdc163d3c12bce20bce4aa2f569c4e7fe5de2439` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| E091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| E092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| E093 | main-java | 284 | 12241 | `c44391f8c9af59b7dde02b9c00c0b5a650f9cc672251402fc82e87222a3e491c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| E094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| E095 | main-java | 607 | 25910 | `9773f2a06ffc2c857865b1d751fb4c115447831ff953a8b975f10ec4c07e3e70` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| E096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| E097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| E098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| E099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| E100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| E101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| E102 | main-java | 101 | 3503 | `b8431d287244d8b2b9cda2c5c9360222a803a87ef091014a1fdafcf28dd24755` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyTextureBindings.java` |
| E103 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| E104 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| E105 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| E106 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| E107 | main-java | 922 | 38889 | `c78d6e369edf7caf7f1ef03fbee5c0b1e6f97d857128dae94e0b283f9bdb06b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| E108 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| E109 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| E110 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| E111 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| E112 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| E113 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| E114 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| E115 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| E116 | main-java | 457 | 21544 | `fe18ed936de5b24d1ad3403ef3289664204f8c362d3f19fbd9fad8adb6bf5a57` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| E117 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| E118 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| E119 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| E120 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| E121 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| E122 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| E123 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| E124 | main-java | 800 | 37240 | `9a120c449e6f3f0270acb81030a2c7774ad130b8f310a8bf2de1235ebb851343` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| E125 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| E126 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| E127 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| E128 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| E129 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| E130 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| E131 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| E132 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| E133 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| E134 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| E135 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| E136 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| E137 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| E138 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| E139 | main-java | 46 | 2038 | `2a782f2d47330077525adfa6b5b3c554ac93ba1a0462f66a8f9a76a8615c8685` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| E140 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| E141 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| E142 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| E143 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| E144 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| E145 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| E146 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| E147 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| E148 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| E149 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| E150 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| E151 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| E152 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| E153 | main-java | 1182 | 48577 | `6511a20bcbc7b37a5f39dfe2135217d9618987717110b9139b23ef463cc74169` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| E154 | main-java | 59 | 1619 | `b6d31aa4334f0ee6faff06799d8b2e2a577d573a3e6e6fec7feea5bad3735c92` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| E155 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| E156 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| E157 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| E158 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| E159 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| E160 | main-java | 268 | 9928 | `0346194f683fbb04e8c6d5df13b837397188de783d46f03cf15de486d750201b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| E161 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| E162 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| E163 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| E164 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| E165 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| E166 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| E167 | main-java | 74 | 3147 | `5ba059898052b935af088ea64e9589f7e1c6f84029b2d40798a9bf6d1fc43e2b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| E168 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| E169 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| E170 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| E171 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| E172 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| E173 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| E174 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| E175 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| E176 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| E177 | main-java | 254 | 10712 | `29a0ed676cec34a81071fee35d72cc4cc983b88670fb60b640646cfb2165bd0f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| E178 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| E179 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| E180 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| E181 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| E182 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| E183 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| E184 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| E185 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| E186 | binary-resource | - | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| E187 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| E188 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| E189 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| E190 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| E191 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| E192 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| E193 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| E194 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| E195 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| E196 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| E197 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| E198 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| E199 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| E200 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| E201 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| E202 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| E203 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| E204 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| E205 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| E206 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| E207 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| E208 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| E209 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| E210 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| E211 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| E212 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| E213 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| E214 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| E215 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| E216 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| E217 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| E218 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| E219 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| E220 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| E221 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| E222 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| E223 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| E224 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| E225 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| E226 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| E227 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| E228 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| E229 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| E230 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| E231 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| E232 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| E233 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| E234 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| E235 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| E236 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| E237 | main-resource | 42 | 848 | `e265d69e26a96649382e08b51edc9005b510f45eba0eb01c38125e26d5e0b234` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| E238 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| E239 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| E240 | test-java | 17 | 711 | `00280e77ef9dea22f1bbd83bad3d10a66da9f232188e9e4b288d51cdc51882d1` | reviewed | P5-F001 | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| E241 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| E242 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| E243 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| E244 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| E245 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| E246 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| E247 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| E248 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| E249 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| E250 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| E251 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| E252 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| E253 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| E254 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| E255 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| E256 | test-java | 20 | 712 | `01a9beb9480fe229215e0dbd978be0ad303d0d18b75fa5681708164113f30a20` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| E257 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| E258 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| E259 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| E260 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| E261 | test-java | 16 | 813 | `46c31de66e6c1f3cf97f4b106ccbde20290029caa69656d9926dd4042ffe446b` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| E262 | test-java | 139 | 6639 | `d1bb2581285d425cc67d8e3c318a4e9adc41f1a9242e7694744d4575071473d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| E263 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| E264 | test-java | 30 | 943 | `a6b3375f749811b9f4a008f2b295371cc2e55524fee2ea9de0bd20d6ba43d49a` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| E265 | test-java | 56 | 2822 | `5196671a0e887aab45cbb31c6b5ff78fb7c46e892d70c0fdb925a4fefe78d11c` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| E266 | test-java | 173 | 9952 | `8b811ce42b101dfb43b471ce4780d19188ca5644163428778983d0e973bac3fe` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| E267 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| E268 | test-java | 281 | 14988 | `7e944d75a6d9c0696c2d4c7480843aba019dcf8e4b3bd23fd15aa2e7d149c1fd` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java` |
| E269 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| E270 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| E271 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| E272 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |

## Pass 5 coverage and repair proof

- E001-E074: 74 files / 9,704 lines / 384,258 bytes; one finding,
  P5-F001; every row reached EOF and re-matched.
- E075-E112: 38 files / 9,745 lines / 410,534 bytes; zero findings; every
  row reached EOF and re-matched.
- E113-E164: 52 files / 9,714 lines / 408,613 bytes; zero findings; every
  row reached EOF and re-matched.
- E165-E272: 108 files / 9,696 text lines / 424,032 bytes; zero findings;
  every Java, text resource, shader, test, build/audit line and the binary icon
  row was reviewed and re-matched.
- Whole snapshot: 272 frozen rows and 272 current rows; zero path, order, kind,
  line, byte, SHA-256, missing, or extra mismatches before repair.
- P5-F001 was repaired only after all four partitions closed. The legacy
  Forge-only tracked-object disable alias was removed; the active helper now
  reproduces original default-on and exact-lowercase semantics. The focused
  test and an independent repair review passed, and no alias remains outside
  the historical audit record.
- `rtk test .\gradlew test --tests
  me.cortex.voxy.common.util.TrackedObjectFlagTest --rerun-tasks --stacktrace
  --console=plain` passed. No client was launched.

Because Pass 5 found P5-F001, it is not the terminal zero-finding pass. The
post-repair executable inventory must be frozen and independently reread from
zero inherited coverage as Pass 6.


## Pass 6 frozen inventory summary

- Files: 272
- Text lines: 38,854
- Bytes: 1,627,177
- audit-tool: 1
- binary-resource: 1
- build-config: 2
- build-logic: 2
- main-java: 181
- main-resource: 10
- shader: 43
- test-java: 32

## Pass 6 frozen ledger

Snapshot frozen after the complete P5-F001 repair, focused test, independent
repair review, and successful full compile/test gate: **272 files / 38,854
text lines / 1,627,177 bytes**. Pass 6 inherits zero review coverage.
Every F row starts at `pending` and may move to `reviewed` only after all
frozen physical lines reach EOF. Any confirmed finding invalidates Pass 6 as
the terminal pass and triggers another deferred repair plus full new pass.

| ID | Kind | Lines | Bytes | SHA-256 | Status | Finding | Path |
|---|---|---:|---:|---|---|---|---|
| F001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| F002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| F003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| F004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| F005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| F006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| F007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| F008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| F009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| F010 | main-java | 77 | 2860 | `5532ef6577e4e7b564c3eabd31bc1982636a4e0389d3d2a53d11a75e86554cf3` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| F011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| F012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| F013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| F014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| F015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| F016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| F017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| F018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| F019 | main-java | 117 | 3948 | `f81142da3322e9aaa3b03e9d61ef92cf567ddd8a4e269d657b9fd2caef79f5b7` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| F020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| F021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| F022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| F023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| F024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| F025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| F026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| F027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| F028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| F029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| F030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| F031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| F032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| F033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| F034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| F035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| F036 | main-java | 96 | 3045 | `fb73e6b34e24077dfed06ca7d9c8b82ec598f0fa45dc105c34f6d2ca6b85be07` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| F037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| F038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| F039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| F040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| F041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| F042 | main-java | 401 | 18014 | `e0ac18566fbafcad7087f9911ab4bf6b626b05fcc0da73e7c24e58b70a6894ac` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| F043 | main-java | 460 | 18654 | `fb9ad4336baf4d7fcd6072b7baafc98f0deefa8afb6912e3422bac48fd811400` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| F044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| F045 | main-java | 126 | 4826 | `2ca07119fd09514d8b6c2c2f05db6b929c45041822afcde05c411b9525ac4aed` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| F046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| F047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| F048 | main-java | 218 | 8557 | `a1673cd263b10ab2978ef7c867fb49dc122fa9295693b719fc48ac44455788bf` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| F049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| F050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| F051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| F052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| F053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| F054 | main-java | 946 | 40552 | `7bef679a87a7c54463c0cc071f364934d0eef06794db2bf5518ef86a7ff4b19c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| F055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| F056 | main-java | 291 | 12893 | `b46592d03b77fb7693bd1c347fb55622f2fc91e645f4bfb30e5aba2f222b8ca7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| F057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| F058 | main-java | 237 | 10641 | `237211e080f5ce86983c26b5e50bb8e6bc62284fab39776c9f82dc34b333e7ef` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| F059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| F060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| F061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| F062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| F063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| F064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| F065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| F066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| F067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| F068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| F069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| F070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| F071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| F072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| F073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| F074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| F075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| F076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| F077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| F078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| F079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| F080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| F081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| F082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| F083 | main-java | 1289 | 59280 | `ed005e1afd3a7c4ecd241bcc110e0047697dfab80f8ebe9cb2cc9ed6438fed93` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| F084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| F085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| F086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| F087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| F088 | main-java | 1130 | 44334 | `f67a1019097ecd5fb39c70faec1f9ca01a962dddd77ab259e71d8d0cdec36be4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| F089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| F090 | main-java | 511 | 20426 | `1fbb2de734a5d70b49455e9efdc163d3c12bce20bce4aa2f569c4e7fe5de2439` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| F091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| F092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| F093 | main-java | 284 | 12241 | `c44391f8c9af59b7dde02b9c00c0b5a650f9cc672251402fc82e87222a3e491c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| F094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| F095 | main-java | 607 | 25910 | `9773f2a06ffc2c857865b1d751fb4c115447831ff953a8b975f10ec4c07e3e70` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| F096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| F097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| F098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| F099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| F100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| F101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| F102 | main-java | 101 | 3503 | `b8431d287244d8b2b9cda2c5c9360222a803a87ef091014a1fdafcf28dd24755` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyTextureBindings.java` |
| F103 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| F104 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| F105 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| F106 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| F107 | main-java | 922 | 38889 | `c78d6e369edf7caf7f1ef03fbee5c0b1e6f97d857128dae94e0b283f9bdb06b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| F108 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| F109 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| F110 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| F111 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| F112 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| F113 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| F114 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| F115 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| F116 | main-java | 457 | 21544 | `fe18ed936de5b24d1ad3403ef3289664204f8c362d3f19fbd9fad8adb6bf5a57` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| F117 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| F118 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| F119 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| F120 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| F121 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| F122 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| F123 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| F124 | main-java | 800 | 37240 | `9a120c449e6f3f0270acb81030a2c7774ad130b8f310a8bf2de1235ebb851343` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| F125 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| F126 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| F127 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| F128 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| F129 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| F130 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| F131 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| F132 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| F133 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| F134 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| F135 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| F136 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| F137 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| F138 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| F139 | main-java | 46 | 2038 | `2a782f2d47330077525adfa6b5b3c554ac93ba1a0462f66a8f9a76a8615c8685` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| F140 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| F141 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| F142 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| F143 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| F144 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| F145 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| F146 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| F147 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| F148 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| F149 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| F150 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| F151 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| F152 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| F153 | main-java | 1182 | 48577 | `6511a20bcbc7b37a5f39dfe2135217d9618987717110b9139b23ef463cc74169` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| F154 | main-java | 59 | 1619 | `b6d31aa4334f0ee6faff06799d8b2e2a577d573a3e6e6fec7feea5bad3735c92` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| F155 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| F156 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| F157 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| F158 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| F159 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| F160 | main-java | 268 | 9928 | `0346194f683fbb04e8c6d5df13b837397188de783d46f03cf15de486d750201b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| F161 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| F162 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| F163 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| F164 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| F165 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| F166 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| F167 | main-java | 74 | 3147 | `5ba059898052b935af088ea64e9589f7e1c6f84029b2d40798a9bf6d1fc43e2b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| F168 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| F169 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| F170 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| F171 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| F172 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| F173 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| F174 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| F175 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| F176 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| F177 | main-java | 254 | 10712 | `29a0ed676cec34a81071fee35d72cc4cc983b88670fb60b640646cfb2165bd0f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| F178 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| F179 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| F180 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| F181 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| F182 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| F183 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| F184 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| F185 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| F186 | binary-resource | - | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| F187 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| F188 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| F189 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| F190 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| F191 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| F192 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| F193 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| F194 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| F195 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| F196 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| F197 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| F198 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| F199 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| F200 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| F201 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| F202 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| F203 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| F204 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| F205 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| F206 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| F207 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| F208 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| F209 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| F210 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| F211 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| F212 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| F213 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| F214 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| F215 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| F216 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| F217 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| F218 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| F219 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| F220 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| F221 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| F222 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| F223 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| F224 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| F225 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| F226 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| F227 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| F228 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| F229 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| F230 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| F231 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| F232 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| F233 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| F234 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| F235 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| F236 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| F237 | main-resource | 42 | 848 | `e265d69e26a96649382e08b51edc9005b510f45eba0eb01c38125e26d5e0b234` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| F238 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| F239 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| F240 | test-java | 16 | 605 | `524055a0a7bd81d9630daa3b5fb085bed06919b2e4e0acd7d60970185efb7ff2` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| F241 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| F242 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| F243 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| F244 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| F245 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| F246 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| F247 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| F248 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| F249 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| F250 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| F251 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| F252 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| F253 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| F254 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| F255 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| F256 | test-java | 20 | 712 | `01a9beb9480fe229215e0dbd978be0ad303d0d18b75fa5681708164113f30a20` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| F257 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| F258 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| F259 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| F260 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| F261 | test-java | 16 | 813 | `46c31de66e6c1f3cf97f4b106ccbde20290029caa69656d9926dd4042ffe446b` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| F262 | test-java | 139 | 6639 | `d1bb2581285d425cc67d8e3c318a4e9adc41f1a9242e7694744d4575071473d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| F263 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| F264 | test-java | 30 | 943 | `a6b3375f749811b9f4a008f2b295371cc2e55524fee2ea9de0bd20d6ba43d49a` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| F265 | test-java | 56 | 2822 | `5196671a0e887aab45cbb31c6b5ff78fb7c46e892d70c0fdb925a4fefe78d11c` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| F266 | test-java | 173 | 9952 | `8b811ce42b101dfb43b471ce4780d19188ca5644163428778983d0e973bac3fe` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| F267 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| F268 | test-java | 281 | 14988 | `7e944d75a6d9c0696c2d4c7480843aba019dcf8e4b3bd23fd15aa2e7d149c1fd` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java` |
| F269 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| F270 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| F271 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| F272 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |

## Pass 6 terminal zero-finding proof

- F001-F074: 74/74 files, 9,700/9,700 physical text lines, and
  384,104/384,104 bytes reached EOF; the independent partition recheck found
  zero path, kind, line, byte, or SHA-256 mismatches and zero findings.
- F075-F112: 38/38 files, 9,745/9,745 physical text lines, and
  410,534/410,534 bytes reached EOF; the independent partition recheck found
  zero path, kind, line, byte, or SHA-256 mismatches and zero findings.
- F113-F164: 52/52 files, 9,714/9,714 physical text lines, and
  408,613/408,613 bytes reached EOF; the independent partition recheck found
  zero path, kind, line, byte, or SHA-256 mismatches and zero findings.
- F165-F272: 108/108 files, 9,695/9,695 physical text lines, and
  423,926/423,926 bytes reached EOF; production Java was inspected
  CodeGraph-first, every text resource/test/build/audit line was reread, and
  the binary icon was visually inspected. This partition found zero findings.
- Whole snapshot: 272 frozen rows and 272 regenerated rows, representing
  38,854 physical text lines and 1,627,177 bytes, re-matched in identical
  order with zero missing, extra, path, kind, line, byte, or SHA-256
  mismatches. Pass 6 inherits no prior-pass review credit.
- Original/platform evidence was checked for every production subsystem;
  original behaviors and documented, necessary Forge 1.20.1 adaptations were
  not misclassified as port defects. Confirmed new findings in Pass 6: **0**.
- Final static gate: `rtk git diff --check` passed.
- Final build gate: `rtk test .\gradlew clean test jarJar --rerun-tasks
  --stacktrace --console=plain` passed. The generated reports contain 32 test
  suites / 94 tests / 0 failures / 0 errors / 0 skipped tests.
- Packaged artifact: `voxy-forge-0.2.17-beta-forge-all.jar` is 12,583,915
  bytes, SHA-256
  `94cd04bd5ff74f6e29198d9ac505560b2cf8c45c76e7dec177ec88827ec2c4c5`,
  contains 457 ZIP entries, and contains zero bundled Minecraft, Forge,
  LWJGL, Oculus/Iris, Embeddium, or Sodium dependency classes.
- No client was launched during Pass 6. Its static zero-finding result remains
  valid for that frozen review, but the first subsequent `runClient` exposed
  P7-F001 and invalidated Pass 6 as terminal completion evidence.

## Post-Pass-6 runtime invalidation and P7-F001 repair

- The first post-audit `runClient`, anchored at `D:\Projects\voxy`, failed on
  2026-07-14 at 08:44:22 before window initialization. Mixin 0.8.5 rejected
  `ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin` because its extracted
  `shouldResetViewport(ZZZ)Z` helper was package-private static.
- Original `client/mixin/iris/MixinLevelRenderer` keeps the equivalent viewport
  action inside its private injection. The Forge predicate itself was correct;
  extracting it for a direct package-level unit test introduced an invalid
  Mixin member shape.
- P7-F001 changes only that member contract: the production helper is now
  `@Unique private static voxy$shouldResetViewport`, and the test reaches it by
  reflection while explicitly asserting `private` and `static` before checking
  the four predicate cases. An independent review confirmed the fix and found
  no other non-private static methods in the Forge mixin directory.
- `rtk test .\gradlew compileJava test --tests
  me.cortex.voxy.forge.mixin.ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest
  --rerun-tasks --stacktrace --console=plain` passed.
- A second anchored `runClient` passed the real Mixin application phase,
  entered the default world, created the original Voxy persistent WorldEngine,
  reached the Embeddium command-generation hook, and created
  `ForgeOriginalVoxyRenderPipeline` with `MDICSectionRenderer`. The client was
  left running for user validation.
- This is a targeted repaired runtime finding, not a new terminal clean pass.
  The audit remains reopened until the repaired inventory is frozen and a new
  full zero-finding pass completes.

## Pass 7 frozen inventory summary

- Files: 272
- Text lines: 38,876
- Bytes: 1,627,815
- audit-tool: 1
- binary-resource: 1
- build-config: 2
- build-logic: 2
- main-java: 181
- main-resource: 10
- shader: 43
- test-java: 32

## Pass 7 frozen ledger

Snapshot frozen after the complete P7-F001 repair, focused compile/test,
successful real Mixin application through default-world entry, clean client
shutdown, and user visual confirmation: **272 files / 38,876 text lines /
1,627,815 bytes**. Relative to Pass 6, only G139 (the repaired production
mixin) and G261 (its strengthened regression test) changed. Pass 7 inherits
zero review coverage. Every G row starts at `pending` and may move to
`reviewed` only after all frozen physical lines reach EOF. Any confirmed
finding invalidates Pass 7 as the terminal pass and triggers another deferred
repair plus a complete newly frozen pass.

### Pass 7 full-review freeze proof

- Four disjoint clean-room partitions reached EOF: G001-G074 (74 files,
  9,700 lines, 384,104 bytes), G075-G112 (38 files, 9,745 lines, 410,534
  bytes), G113-G164 (52 files, 9,719 lines, 408,723 bytes), and G165-G272
  (108 files, 9,712 lines, 424,454 bytes).
- Each partition independently re-matched path, kind, physical line count,
  byte count, and SHA-256 against its frozen rows with zero drift.
- The root then regenerated the complete inventory: **272 inventory paths /
  272 ledger paths / 38,876 text lines / 1,627,815 bytes / 0 hash, line,
  byte, missing-path, or extra-path mismatches**.
- No production source or packaged resource was modified during the pass.
  P7-F002 through P7-F004 were recorded but left frozen until this proof was
  complete; P7-F005 was added by independent pre-repair cross-review. Pass 7
  is therefore complete but cannot be terminal; the repair batch must be
  followed by a newly frozen full Pass 8.

| ID | Kind | Lines | Bytes | SHA-256 | Status | Finding | Path |
|---|---|---:|---:|---|---|---|---|
| G001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | P7-F002 | `build.gradle` |
| G002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| G003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| G004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| G005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| G006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| G007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| G008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| G009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| G010 | main-java | 77 | 2860 | `5532ef6577e4e7b564c3eabd31bc1982636a4e0389d3d2a53d11a75e86554cf3` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| G011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| G012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| G013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| G014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| G015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| G016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| G017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| G018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| G019 | main-java | 117 | 3948 | `f81142da3322e9aaa3b03e9d61ef92cf567ddd8a4e269d657b9fd2caef79f5b7` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| G020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| G021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| G022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| G023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| G024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| G025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| G026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| G027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| G028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| G029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| G030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| G031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| G032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| G033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| G034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| G035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| G036 | main-java | 96 | 3045 | `fb73e6b34e24077dfed06ca7d9c8b82ec598f0fa45dc105c34f6d2ca6b85be07` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| G037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| G038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| G039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| G040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| G041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| G042 | main-java | 401 | 18014 | `e0ac18566fbafcad7087f9911ab4bf6b626b05fcc0da73e7c24e58b70a6894ac` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| G043 | main-java | 460 | 18654 | `fb9ad4336baf4d7fcd6072b7baafc98f0deefa8afb6912e3422bac48fd811400` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| G044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| G045 | main-java | 126 | 4826 | `2ca07119fd09514d8b6c2c2f05db6b929c45041822afcde05c411b9525ac4aed` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| G046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| G047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| G048 | main-java | 218 | 8557 | `a1673cd263b10ab2978ef7c867fb49dc122fa9295693b719fc48ac44455788bf` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| G049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| G050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| G051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| G052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| G053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| G054 | main-java | 946 | 40552 | `7bef679a87a7c54463c0cc071f364934d0eef06794db2bf5518ef86a7ff4b19c` | reviewed | P7-F004 | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| G055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| G056 | main-java | 291 | 12893 | `b46592d03b77fb7693bd1c347fb55622f2fc91e645f4bfb30e5aba2f222b8ca7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| G057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| G058 | main-java | 237 | 10641 | `237211e080f5ce86983c26b5e50bb8e6bc62284fab39776c9f82dc34b333e7ef` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| G059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| G060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| G061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| G062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| G063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| G064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| G065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| G066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| G067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| G068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| G069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| G070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| G071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| G072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| G073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| G074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| G075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| G076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| G077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| G078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| G079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| G080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| G081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| G082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| G083 | main-java | 1289 | 59280 | `ed005e1afd3a7c4ecd241bcc110e0047697dfab80f8ebe9cb2cc9ed6438fed93` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| G084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| G085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| G086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| G087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| G088 | main-java | 1130 | 44334 | `f67a1019097ecd5fb39c70faec1f9ca01a962dddd77ab259e71d8d0cdec36be4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| G089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| G090 | main-java | 511 | 20426 | `1fbb2de734a5d70b49455e9efdc163d3c12bce20bce4aa2f569c4e7fe5de2439` | reviewed | P7-F003, P7-F005 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| G091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| G092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| G093 | main-java | 284 | 12241 | `c44391f8c9af59b7dde02b9c00c0b5a650f9cc672251402fc82e87222a3e491c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| G094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| G095 | main-java | 607 | 25910 | `9773f2a06ffc2c857865b1d751fb4c115447831ff953a8b975f10ec4c07e3e70` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| G096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| G097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| G098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| G099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| G100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| G101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| G102 | main-java | 101 | 3503 | `b8431d287244d8b2b9cda2c5c9360222a803a87ef091014a1fdafcf28dd24755` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyTextureBindings.java` |
| G103 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| G104 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| G105 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| G106 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| G107 | main-java | 922 | 38889 | `c78d6e369edf7caf7f1ef03fbee5c0b1e6f97d857128dae94e0b283f9bdb06b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| G108 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| G109 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| G110 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| G111 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| G112 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| G113 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| G114 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| G115 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| G116 | main-java | 457 | 21544 | `fe18ed936de5b24d1ad3403ef3289664204f8c362d3f19fbd9fad8adb6bf5a57` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| G117 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| G118 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| G119 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| G120 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| G121 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| G122 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| G123 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| G124 | main-java | 800 | 37240 | `9a120c449e6f3f0270acb81030a2c7774ad130b8f310a8bf2de1235ebb851343` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| G125 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| G126 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| G127 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| G128 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| G129 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| G130 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| G131 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| G132 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| G133 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| G134 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| G135 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| G136 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| G137 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| G138 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| G139 | main-java | 51 | 2148 | `4a10af9961c30f1561d0cc63c8f681f40fb4b46251311210f0ba8ceee3390f1c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| G140 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| G141 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| G142 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| G143 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| G144 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| G145 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| G146 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| G147 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| G148 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| G149 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| G150 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| G151 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| G152 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| G153 | main-java | 1182 | 48577 | `6511a20bcbc7b37a5f39dfe2135217d9618987717110b9139b23ef463cc74169` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| G154 | main-java | 59 | 1619 | `b6d31aa4334f0ee6faff06799d8b2e2a577d573a3e6e6fec7feea5bad3735c92` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| G155 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| G156 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| G157 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| G158 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| G159 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| G160 | main-java | 268 | 9928 | `0346194f683fbb04e8c6d5df13b837397188de783d46f03cf15de486d750201b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| G161 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| G162 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| G163 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| G164 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| G165 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| G166 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| G167 | main-java | 74 | 3147 | `5ba059898052b935af088ea64e9589f7e1c6f84029b2d40798a9bf6d1fc43e2b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| G168 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| G169 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| G170 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| G171 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| G172 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| G173 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| G174 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| G175 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| G176 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| G177 | main-java | 254 | 10712 | `29a0ed676cec34a81071fee35d72cc4cc983b88670fb60b640646cfb2165bd0f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| G178 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| G179 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| G180 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| G181 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| G182 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| G183 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| G184 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| G185 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| G186 | binary-resource | - | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| G187 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| G188 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| G189 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| G190 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| G191 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| G192 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| G193 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| G194 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| G195 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| G196 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| G197 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| G198 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| G199 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| G200 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| G201 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| G202 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| G203 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| G204 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| G205 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| G206 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| G207 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| G208 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| G209 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| G210 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| G211 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| G212 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| G213 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| G214 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| G215 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| G216 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| G217 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| G218 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| G219 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| G220 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| G221 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| G222 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| G223 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| G224 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| G225 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| G226 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| G227 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| G228 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| G229 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| G230 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| G231 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| G232 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| G233 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| G234 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| G235 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| G236 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| G237 | main-resource | 42 | 848 | `e265d69e26a96649382e08b51edc9005b510f45eba0eb01c38125e26d5e0b234` | reviewed | P7-F002 | `src/main/resources/META-INF/mods.toml` |
| G238 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| G239 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| G240 | test-java | 16 | 605 | `524055a0a7bd81d9630daa3b5fb085bed06919b2e4e0acd7d60970185efb7ff2` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| G241 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| G242 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| G243 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| G244 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| G245 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| G246 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| G247 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| G248 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| G249 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| G250 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| G251 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| G252 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| G253 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| G254 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| G255 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| G256 | test-java | 20 | 712 | `01a9beb9480fe229215e0dbd978be0ad303d0d18b75fa5681708164113f30a20` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| G257 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| G258 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| G259 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| G260 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| G261 | test-java | 33 | 1341 | `deafeaa09bdfa5ecd54b0eecde9342710904bf4249ff171a5c75b69635818b42` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| G262 | test-java | 139 | 6639 | `d1bb2581285d425cc67d8e3c318a4e9adc41f1a9242e7694744d4575071473d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| G263 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| G264 | test-java | 30 | 943 | `a6b3375f749811b9f4a008f2b295371cc2e55524fee2ea9de0bd20d6ba43d49a` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| G265 | test-java | 56 | 2822 | `5196671a0e887aab45cbb31c6b5ff78fb7c46e892d70c0fdb925a4fefe78d11c` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| G266 | test-java | 173 | 9952 | `8b811ce42b101dfb43b471ce4780d19188ca5644163428778983d0e973bac3fe` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| G267 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| G268 | test-java | 281 | 14988 | `7e944d75a6d9c0696c2d4c7480843aba019dcf8e4b3bd23fd15aa2e7d149c1fd` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java` |
| G269 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| G270 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| G271 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| G272 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |


## Pass 7 repair batch validation

- Repair policy: P7-F002 through P7-F005 remained unchanged until every frozen
  G row reached EOF and the complete 272-file inventory re-matched with zero
  drift.
- P7-F002 maps the already packaged original icon through Forge's
  `logoFile="assets/voxy/icon.png"` metadata key. The processed-resource test
  verifies the key and byte-identical packaged PNG.
- P7-F003 restores original blending-first validation and removes the null-state
  skip, so an unknown per-buffer value unwinds inside `makePatch()` as
  `ForgeOriginalVoxyShaderLoadError` instead of failing in the render runnable.
- P7-F005 removes the Forge-only short-array fallback, restoring the original
  deserializer catch and partial-map behavior.
- P7-F004 restores the original top-level producer linearization: opposing-set
  removal/pending-set insertion, `workCounter` delta, and worker unpark all
  occur under the same lock used by the worker snapshot/clear. Independent
  review found the production repair clean and prompted stronger assertions for
  both producer set operations and the worker side of the lock protocol.
- `rtk git diff --check` passed. The first focused attempt correctly exposed
  that Oculus is compile-only for main sources; the test was converted to
  reflection without broadening dependencies. `compileTestJava` then passed.
- The four focused behavioral/source-contract tests passed with zero failures.
  The complete suite was force-rerun: **35 suites / 98 tests / 0 failures /
  0 errors / 0 skipped**. `JSON_DUMP.txt` was restored or removed by the
  expected-failure test and did not remain in the workspace.
- Two independent post-repair reviews confirmed the shader/metadata and async
  production fixes. No client was launched. Pass 8 is still required and
  inherits no review credit.

## Pass 8 frozen inventory summary

- Files: 275
- Text lines: 39,143
- Bytes: 1,639,316
- audit-tool: 1
- binary-resource: 1
- build-config: 2
- build-logic: 2
- main-java: 181
- main-resource: 10
- shader: 43
- test-java: 35

## Pass 8 frozen ledger

Snapshot frozen after the complete Pass 7 repair batch, focused gates, full
98-test suite, and independent repair reviews: **275 files / 39,143 text lines /
1,639,316 bytes**. The three production/resource repairs and three new focused
test files are part of this baseline. Every H row begins at `pending`; no Pass 7
review credit is inherited. The four disjoint clean-room partitions are
H001-H075 (75 files, 9,831 lines, 389,961 bytes), H076-H113 (38 files, 9,647 lines, 405,610 bytes), H114-H165 (52 files, 10,018 lines, 420,441 bytes), H166-H275 (110 files, 9,647 lines, 423,304 bytes).

| ID | Kind | Lines | Bytes | SHA-256 | Status | Finding | Path |
|---|---|---:|---:|---|---|---|---|
| H001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| H002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| H003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| H004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| H005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| H006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| H007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| H008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| H009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| H010 | main-java | 77 | 2860 | `5532ef6577e4e7b564c3eabd31bc1982636a4e0389d3d2a53d11a75e86554cf3` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| H011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| H012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| H013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| H014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| H015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| H016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| H017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| H018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| H019 | main-java | 117 | 3948 | `f81142da3322e9aaa3b03e9d61ef92cf567ddd8a4e269d657b9fd2caef79f5b7` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| H020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| H021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| H022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| H023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| H024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| H025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| H026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| H027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| H028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| H029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| H030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| H031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| H032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| H033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| H034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| H035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| H036 | main-java | 96 | 3045 | `fb73e6b34e24077dfed06ca7d9c8b82ec598f0fa45dc105c34f6d2ca6b85be07` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| H037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| H038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| H039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| H040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| H041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| H042 | main-java | 401 | 18014 | `e0ac18566fbafcad7087f9911ab4bf6b626b05fcc0da73e7c24e58b70a6894ac` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| H043 | main-java | 460 | 18654 | `fb9ad4336baf4d7fcd6072b7baafc98f0deefa8afb6912e3422bac48fd811400` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| H044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| H045 | main-java | 126 | 4826 | `2ca07119fd09514d8b6c2c2f05db6b929c45041822afcde05c411b9525ac4aed` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| H046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| H047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| H048 | main-java | 218 | 8557 | `a1673cd263b10ab2978ef7c867fb49dc122fa9295693b719fc48ac44455788bf` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| H049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| H050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| H051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| H052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| H053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| H054 | main-java | 944 | 40553 | `9cd334392987dfcea92bc3bf879827d476e2c525a9c8e2edc45b2ddf4ff5ec31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| H055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| H056 | main-java | 291 | 12893 | `b46592d03b77fb7693bd1c347fb55622f2fc91e645f4bfb30e5aba2f222b8ca7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| H057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| H058 | main-java | 237 | 10641 | `237211e080f5ce86983c26b5e50bb8e6bc62284fab39776c9f82dc34b333e7ef` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| H059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| H060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| H061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| H062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| H063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| H064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| H065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| H066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| H067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| H068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| H069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| H070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| H071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| H072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| H073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| H074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| H075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| H076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| H077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| H078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| H079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| H080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| H081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| H082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| H083 | main-java | 1289 | 59280 | `ed005e1afd3a7c4ecd241bcc110e0047697dfab80f8ebe9cb2cc9ed6438fed93` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| H084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| H085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| H086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| H087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| H088 | main-java | 1130 | 44334 | `f67a1019097ecd5fb39c70faec1f9ca01a962dddd77ab259e71d8d0cdec36be4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| H089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| H090 | main-java | 508 | 20285 | `c4faf0160a5504e658798a9c9c16654d61694079708eb11c334466ce8c2a6a8e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| H091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| H092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| H093 | main-java | 284 | 12241 | `c44391f8c9af59b7dde02b9c00c0b5a650f9cc672251402fc82e87222a3e491c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| H094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| H095 | main-java | 607 | 25910 | `9773f2a06ffc2c857865b1d751fb4c115447831ff953a8b975f10ec4c07e3e70` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| H096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| H097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| H098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| H099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| H100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| H101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| H102 | main-java | 101 | 3503 | `b8431d287244d8b2b9cda2c5c9360222a803a87ef091014a1fdafcf28dd24755` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyTextureBindings.java` |
| H103 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| H104 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| H105 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| H106 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| H107 | main-java | 922 | 38889 | `c78d6e369edf7caf7f1ef03fbee5c0b1e6f97d857128dae94e0b283f9bdb06b6` | reviewed | P8-F001 | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| H108 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| H109 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| H110 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| H111 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| H112 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| H113 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| H114 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| H115 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| H116 | main-java | 457 | 21544 | `fe18ed936de5b24d1ad3403ef3289664204f8c362d3f19fbd9fad8adb6bf5a57` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| H117 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| H118 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| H119 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| H120 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| H121 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| H122 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| H123 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| H124 | main-java | 800 | 37240 | `9a120c449e6f3f0270acb81030a2c7774ad130b8f310a8bf2de1235ebb851343` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| H125 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| H126 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| H127 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| H128 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| H129 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| H130 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| H131 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| H132 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| H133 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| H134 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| H135 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| H136 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| H137 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| H138 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| H139 | main-java | 51 | 2148 | `4a10af9961c30f1561d0cc63c8f681f40fb4b46251311210f0ba8ceee3390f1c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| H140 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| H141 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| H142 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| H143 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| H144 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| H145 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| H146 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| H147 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| H148 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| H149 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| H150 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| H151 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| H152 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| H153 | main-java | 1182 | 48577 | `6511a20bcbc7b37a5f39dfe2135217d9618987717110b9139b23ef463cc74169` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| H154 | main-java | 59 | 1619 | `b6d31aa4334f0ee6faff06799d8b2e2a577d573a3e6e6fec7feea5bad3735c92` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| H155 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| H156 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| H157 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| H158 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| H159 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| H160 | main-java | 268 | 9928 | `0346194f683fbb04e8c6d5df13b837397188de783d46f03cf15de486d750201b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| H161 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| H162 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| H163 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| H164 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| H165 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| H166 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| H167 | main-java | 74 | 3147 | `5ba059898052b935af088ea64e9589f7e1c6f84029b2d40798a9bf6d1fc43e2b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| H168 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| H169 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| H170 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| H171 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| H172 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| H173 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| H174 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| H175 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| H176 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| H177 | main-java | 254 | 10712 | `29a0ed676cec34a81071fee35d72cc4cc983b88670fb60b640646cfb2165bd0f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| H178 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| H179 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| H180 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| H181 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| H182 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| H183 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| H184 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| H185 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| H186 | binary-resource | - | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| H187 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| H188 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| H189 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| H190 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| H191 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| H192 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| H193 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| H194 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| H195 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| H196 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| H197 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| H198 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| H199 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| H200 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| H201 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| H202 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| H203 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| H204 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| H205 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| H206 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| H207 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| H208 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| H209 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| H210 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| H211 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| H212 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| H213 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| H214 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| H215 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| H216 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| H217 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| H218 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| H219 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| H220 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| H221 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| H222 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| H223 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| H224 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| H225 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| H226 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| H227 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| H228 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| H229 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| H230 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| H231 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| H232 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| H233 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| H234 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| H235 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| H236 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| H237 | main-resource | 43 | 880 | `056e6b46e3031dcf272205f357e7dd751bba9edfb13183018d5167c97698eae2` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| H238 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| H239 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| H240 | test-java | 16 | 605 | `524055a0a7bd81d9630daa3b5fb085bed06919b2e4e0acd7d60970185efb7ff2` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| H241 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| H242 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| H243 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| H244 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| H245 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| H246 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| H247 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| H248 | test-java | 114 | 5385 | `be22e735ce9f29ac7aba83ad6ce21b62531f71d5b20ea07d9a10f6684523e9d3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerTopLevelWorkParityTest.java` |
| H249 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| H250 | test-java | 32 | 1210 | `9283f3af89df614ca75b769f24ab5da853d0ab6f76aca1332842370cb973fa45` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeModMetadataParityTest.java` |
| H251 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| H252 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| H253 | test-java | 125 | 5014 | `c2c87e927817979d510e3c3c18a01c28c9c259ab92af0fe840874b34fd5fb906` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatchTest.java` |
| H254 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| H255 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| H256 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| H257 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| H258 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| H259 | test-java | 20 | 712 | `01a9beb9480fe229215e0dbd978be0ad303d0d18b75fa5681708164113f30a20` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| H260 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| H261 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| H262 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| H263 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| H264 | test-java | 33 | 1341 | `deafeaa09bdfa5ecd54b0eecde9342710904bf4249ff171a5c75b69635818b42` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| H265 | test-java | 139 | 6639 | `d1bb2581285d425cc67d8e3c318a4e9adc41f1a9242e7694744d4575071473d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| H266 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| H267 | test-java | 30 | 943 | `a6b3375f749811b9f4a008f2b295371cc2e55524fee2ea9de0bd20d6ba43d49a` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| H268 | test-java | 56 | 2822 | `5196671a0e887aab45cbb31c6b5ff78fb7c46e892d70c0fdb925a4fefe78d11c` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| H269 | test-java | 173 | 9952 | `8b811ce42b101dfb43b471ce4780d19188ca5644163428778983d0e973bac3fe` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| H270 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| H271 | test-java | 281 | 14988 | `7e944d75a6d9c0696c2d4c7480843aba019dcf8e4b3bd23fd15aa2e7d149c1fd` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java` |
| H272 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| H273 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| H274 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| H275 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |


## Pass 8 completion proof

- Four disjoint clean-room partitions re-read every frozen row to EOF with no
  inherited Pass 7 credit: H001-H075 (75 files / 9,831 lines / 389,961 bytes),
  H076-H113 (38 / 9,647 / 405,610), H114-H165 (52 / 10,018 / 420,441), and
  H166-H275 (110 / 9,647 / 423,304).
- Independent partition recomputation and the final whole-tree comparison
  matched all 275 ledger paths / 39,143 physical text lines / 1,639,316 bytes,
  with zero path, kind, line, byte, SHA-256, missing-path, or extra-path drift.
- Pass 8 confirmed one finding after complete coverage: P8-F001. Every other
  reviewed file closed with no finding. No source was edited and no client was
  launched until the complete frozen-tree proof was recorded.

## Pass 8 repair batch validation

- P8-F001 replaces the Forge-only strong identity set of historical
  `ClientLevel` objects with weak identity membership. The production guard
  still rejects a mismatched connection token, remembers the current level,
  accepts a known prior level only within the same session, and rejects an
  unknown identity.
- The focused lifecycle test directly exercises that production policy,
  including cross-connection rejection, current/known-prior/unknown identity
  cases, deterministic cleared-reference pruning, and re-addition. The forced
  focused compile/test gate passed.
- Independent repair review first identified the missing policy-path coverage;
  after the test seam was strengthened, its incremental review returned no
  findings.
- The forced full repair gate passed: 35 suites / 99 tests / zero failures,
  errors, or skips, plus successful `jarJar`. No client was launched. Because
  production and test sources changed, Pass 8 is non-terminal and every Pass 9
  row starts from zero review credit.

## Pass 9 frozen inventory summary

- Files: 275
- Text lines: 39,233
- Bytes: 1,642,875
- audit-tool: 1
- binary-resource: 1
- build-config: 2
- build-logic: 2
- main-java: 181
- main-resource: 10
- shader: 43
- test-java: 35

## Pass 9 frozen ledger

Snapshot frozen after the complete P8-F001 repair, strengthened focused test,
independent zero-finding repair review, and full 99-test/JarJar gate: **275
files / 39,233 text lines / 1,642,875 bytes**. Every I row begins at
`pending`; no Pass 8 review credit is inherited. The four disjoint clean-room
partitions are I001-I075 (75 files, 9,831 lines, 389,961 bytes), I076-I113 (38
files, 9,697 lines, 407,295 bytes), I114-I165 (52 files, 10,018 lines, 420,441
bytes), and I166-I275 (110 files, 9,687 lines, 425,178 bytes).

| ID | Kind | Lines | Bytes | SHA-256 | Status | Finding | Path |
|---|---|---:|---:|---|---|---|---|
| I001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| I002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| I003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| I004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| I005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| I006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| I007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| I008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| I009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| I010 | main-java | 77 | 2860 | `5532ef6577e4e7b564c3eabd31bc1982636a4e0389d3d2a53d11a75e86554cf3` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| I011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| I012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| I013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| I014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| I015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| I016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| I017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| I018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| I019 | main-java | 117 | 3948 | `f81142da3322e9aaa3b03e9d61ef92cf567ddd8a4e269d657b9fd2caef79f5b7` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| I020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| I021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| I022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| I023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| I024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| I025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| I026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| I027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| I028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| I029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| I030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| I031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| I032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| I033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| I034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| I035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| I036 | main-java | 96 | 3045 | `fb73e6b34e24077dfed06ca7d9c8b82ec598f0fa45dc105c34f6d2ca6b85be07` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| I037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| I038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| I039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| I040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| I041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| I042 | main-java | 401 | 18014 | `e0ac18566fbafcad7087f9911ab4bf6b626b05fcc0da73e7c24e58b70a6894ac` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| I043 | main-java | 460 | 18654 | `fb9ad4336baf4d7fcd6072b7baafc98f0deefa8afb6912e3422bac48fd811400` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| I044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| I045 | main-java | 126 | 4826 | `2ca07119fd09514d8b6c2c2f05db6b929c45041822afcde05c411b9525ac4aed` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| I046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| I047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| I048 | main-java | 218 | 8557 | `a1673cd263b10ab2978ef7c867fb49dc122fa9295693b719fc48ac44455788bf` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| I049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| I050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| I051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| I052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| I053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| I054 | main-java | 944 | 40553 | `9cd334392987dfcea92bc3bf879827d476e2c525a9c8e2edc45b2ddf4ff5ec31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| I055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| I056 | main-java | 291 | 12893 | `b46592d03b77fb7693bd1c347fb55622f2fc91e645f4bfb30e5aba2f222b8ca7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| I057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| I058 | main-java | 237 | 10641 | `237211e080f5ce86983c26b5e50bb8e6bc62284fab39776c9f82dc34b333e7ef` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| I059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| I060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| I061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| I062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| I063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| I064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| I065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| I066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| I067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| I068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| I069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| I070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| I071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| I072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| I073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| I074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| I075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| I076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| I077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| I078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| I079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| I080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| I081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| I082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| I083 | main-java | 1289 | 59280 | `ed005e1afd3a7c4ecd241bcc110e0047697dfab80f8ebe9cb2cc9ed6438fed93` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| I084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| I085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| I086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| I087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| I088 | main-java | 1130 | 44334 | `f67a1019097ecd5fb39c70faec1f9ca01a962dddd77ab259e71d8d0cdec36be4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| I089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| I090 | main-java | 508 | 20285 | `c4faf0160a5504e658798a9c9c16654d61694079708eb11c334466ce8c2a6a8e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| I091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| I092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| I093 | main-java | 284 | 12241 | `c44391f8c9af59b7dde02b9c00c0b5a650f9cc672251402fc82e87222a3e491c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| I094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| I095 | main-java | 607 | 25910 | `9773f2a06ffc2c857865b1d751fb4c115447831ff953a8b975f10ec4c07e3e70` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| I096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| I097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| I098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| I099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| I100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| I101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| I102 | main-java | 101 | 3503 | `b8431d287244d8b2b9cda2c5c9360222a803a87ef091014a1fdafcf28dd24755` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyTextureBindings.java` |
| I103 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| I104 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| I105 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| I106 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| I107 | main-java | 972 | 40574 | `b6c31291ed77a37013c3047d2703c41fddc0e3ec6bdcfc3fce24ac1cbc768dbc` | reviewed | P9-F001 | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| I108 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| I109 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| I110 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| I111 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| I112 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| I113 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| I114 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| I115 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| I116 | main-java | 457 | 21544 | `fe18ed936de5b24d1ad3403ef3289664204f8c362d3f19fbd9fad8adb6bf5a57` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| I117 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| I118 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| I119 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| I120 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| I121 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| I122 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| I123 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| I124 | main-java | 800 | 37240 | `9a120c449e6f3f0270acb81030a2c7774ad130b8f310a8bf2de1235ebb851343` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| I125 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| I126 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| I127 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| I128 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| I129 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| I130 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| I131 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| I132 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| I133 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| I134 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| I135 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| I136 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| I137 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| I138 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| I139 | main-java | 51 | 2148 | `4a10af9961c30f1561d0cc63c8f681f40fb4b46251311210f0ba8ceee3390f1c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| I140 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| I141 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| I142 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| I143 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| I144 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| I145 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| I146 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| I147 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| I148 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| I149 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| I150 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| I151 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| I152 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| I153 | main-java | 1182 | 48577 | `6511a20bcbc7b37a5f39dfe2135217d9618987717110b9139b23ef463cc74169` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| I154 | main-java | 59 | 1619 | `b6d31aa4334f0ee6faff06799d8b2e2a577d573a3e6e6fec7feea5bad3735c92` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| I155 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| I156 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| I157 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| I158 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| I159 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| I160 | main-java | 268 | 9928 | `0346194f683fbb04e8c6d5df13b837397188de783d46f03cf15de486d750201b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| I161 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| I162 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| I163 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| I164 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| I165 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| I166 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| I167 | main-java | 74 | 3147 | `5ba059898052b935af088ea64e9589f7e1c6f84029b2d40798a9bf6d1fc43e2b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| I168 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| I169 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| I170 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| I171 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| I172 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| I173 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| I174 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| I175 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| I176 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| I177 | main-java | 254 | 10712 | `29a0ed676cec34a81071fee35d72cc4cc983b88670fb60b640646cfb2165bd0f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| I178 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| I179 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| I180 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| I181 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| I182 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| I183 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| I184 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| I185 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| I186 | binary-resource | - | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| I187 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| I188 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| I189 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| I190 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| I191 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| I192 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| I193 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| I194 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| I195 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| I196 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| I197 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| I198 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| I199 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| I200 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| I201 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| I202 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| I203 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| I204 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| I205 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| I206 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| I207 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| I208 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| I209 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| I210 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| I211 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| I212 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| I213 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| I214 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| I215 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| I216 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| I217 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| I218 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| I219 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| I220 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| I221 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| I222 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| I223 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| I224 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| I225 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| I226 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| I227 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| I228 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| I229 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| I230 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| I231 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| I232 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| I233 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| I234 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| I235 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| I236 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| I237 | main-resource | 43 | 880 | `056e6b46e3031dcf272205f357e7dd751bba9edfb13183018d5167c97698eae2` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| I238 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| I239 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| I240 | test-java | 16 | 605 | `524055a0a7bd81d9630daa3b5fb085bed06919b2e4e0acd7d60970185efb7ff2` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| I241 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| I242 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| I243 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| I244 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| I245 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| I246 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| I247 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| I248 | test-java | 114 | 5385 | `be22e735ce9f29ac7aba83ad6ce21b62531f71d5b20ea07d9a10f6684523e9d3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerTopLevelWorkParityTest.java` |
| I249 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| I250 | test-java | 32 | 1210 | `9283f3af89df614ca75b769f24ab5da853d0ab6f76aca1332842370cb973fa45` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeModMetadataParityTest.java` |
| I251 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| I252 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| I253 | test-java | 125 | 5014 | `c2c87e927817979d510e3c3c18a01c28c9c259ab92af0fe840874b34fd5fb906` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatchTest.java` |
| I254 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| I255 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| I256 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| I257 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| I258 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| I259 | test-java | 60 | 2586 | `f361e8926f6e49b67f1a3999453efe2449b62c749f9ba861c49b6223c8c80d91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| I260 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| I261 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| I262 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| I263 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| I264 | test-java | 33 | 1341 | `deafeaa09bdfa5ecd54b0eecde9342710904bf4249ff171a5c75b69635818b42` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| I265 | test-java | 139 | 6639 | `d1bb2581285d425cc67d8e3c318a4e9adc41f1a9242e7694744d4575071473d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| I266 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| I267 | test-java | 30 | 943 | `a6b3375f749811b9f4a008f2b295371cc2e55524fee2ea9de0bd20d6ba43d49a` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| I268 | test-java | 56 | 2822 | `5196671a0e887aab45cbb31c6b5ff78fb7c46e892d70c0fdb925a4fefe78d11c` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| I269 | test-java | 173 | 9952 | `8b811ce42b101dfb43b471ce4780d19188ca5644163428778983d0e973bac3fe` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| I270 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| I271 | test-java | 281 | 14988 | `7e944d75a6d9c0696c2d4c7480843aba019dcf8e4b3bd23fd15aa2e7d149c1fd` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java` |
| I272 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| I273 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| I274 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| I275 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |

## Pass 9 completion proof

- All four disjoint partitions reached EOF independently: I001-I075, I076-I113,
  I114-I165, and I166-I275. Their totals are exactly 275 files / 39,233 physical
  text lines / 1,642,875 bytes.
- A fresh whole-tree inventory matched every frozen path, kind, line count, byte
  count, and SHA-256 field with zero missing paths, extra paths, or drift.
- Pass 9 confirmed one finding after complete coverage: P9-F001. The first 274
  rows inherited no Pass 8 credit; every row was re-read from the frozen I
  snapshot before any repair was made. No client was launched.

## Pass 9 repair batch validation

- P9-F001 ports original `MixinLevelRenderer#setLevel` at the exact `HEAD`
  boundary. The existing shadow level is compared by object identity and the
  instance callback clears old deferred-light retry state, marks the old renderer
  stale, and detaches the active world selection before vanilla/Embeddium rebuild.
- The delayed END-tick identity/fallback owner and its `activeClientLevel` weak
  reference were removed. END tick now only selects the current world and advances
  the model pipeline, so it cannot erase retry work produced by the replacement
  level.
- The focused lifecycle and Mixin tests validate the processed mixin JSON,
  processed `META-INF/mods.toml` registration, Shadow type, handler signature,
  `setLevel`/`HEAD` metadata, object-identity condition, exact single-owner
  teardown order, early runtime guard, and absence of the END fallback.
- Three independent static repair reviews closed with zero findings after their
  test-contract gaps were repaired. The focused gate passed. The forced full gate
  passed 35 suites / 102 tests / zero failures, errors, or skips, and `jarJar`
  completed successfully.
- One earlier full-gate attempt was excluded before compilation because an
  independent reviewer accidentally overlapped a Gradle process and Windows
  locked ForgeGradle's generated binpatched JAR. No cache was deleted and no Java
  process was killed; the single-owner focused and full reruns passed.
- Because production, test, and processed-resource contracts changed, Pass 9 is
  non-terminal. Every Pass 10 row starts with zero inherited review credit.

## Pass 10 frozen inventory summary

- Files: 275
- Text lines: 39,390
- Bytes: 1,650,791
- audit-tool: 1
- binary-resource: 1
- build-config: 2
- build-logic: 2
- main-java: 181
- main-resource: 10
- shader: 43
- test-java: 35

## Pass 10 frozen ledger

Snapshot frozen after the complete P9-F001 repair, strengthened production-wiring
tests, three independent zero-finding repair reviews, and the full 102-test/JarJar
gate: **275 files / 39,390 text lines / 1,650,791 bytes**. Every J row begins at
`pending`; no Pass 9 review credit is inherited. The four disjoint clean-room
partitions are J001-J075 (75 files, 9,831 lines, 389,961 bytes), J076-J113 (38
files, 9,706 lines, 407,427 bytes), J114-J165 (52 files, 10,035 lines, 421,217
bytes), and J166-J275 (110 files, 9,818 lines, 432,186 bytes).

| ID | Kind | Lines | Bytes | SHA-256 | Status | Finding | Path |
|---|---|---:|---:|---|---|---|---|
| J001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| J002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| J003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| J004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| J005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| J006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| J007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| J008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| J009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| J010 | main-java | 77 | 2860 | `5532ef6577e4e7b564c3eabd31bc1982636a4e0389d3d2a53d11a75e86554cf3` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| J011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| J012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| J013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| J014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| J015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| J016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| J017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| J018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| J019 | main-java | 117 | 3948 | `f81142da3322e9aaa3b03e9d61ef92cf567ddd8a4e269d657b9fd2caef79f5b7` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| J020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| J021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| J022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| J023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| J024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| J025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| J026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| J027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| J028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| J029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| J030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| J031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| J032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| J033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| J034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| J035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| J036 | main-java | 96 | 3045 | `fb73e6b34e24077dfed06ca7d9c8b82ec598f0fa45dc105c34f6d2ca6b85be07` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| J037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| J038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| J039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| J040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| J041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| J042 | main-java | 401 | 18014 | `e0ac18566fbafcad7087f9911ab4bf6b626b05fcc0da73e7c24e58b70a6894ac` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| J043 | main-java | 460 | 18654 | `fb9ad4336baf4d7fcd6072b7baafc98f0deefa8afb6912e3422bac48fd811400` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| J044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| J045 | main-java | 126 | 4826 | `2ca07119fd09514d8b6c2c2f05db6b929c45041822afcde05c411b9525ac4aed` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| J046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| J047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| J048 | main-java | 218 | 8557 | `a1673cd263b10ab2978ef7c867fb49dc122fa9295693b719fc48ac44455788bf` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| J049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| J050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| J051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| J052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| J053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| J054 | main-java | 944 | 40553 | `9cd334392987dfcea92bc3bf879827d476e2c525a9c8e2edc45b2ddf4ff5ec31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| J055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| J056 | main-java | 291 | 12893 | `b46592d03b77fb7693bd1c347fb55622f2fc91e645f4bfb30e5aba2f222b8ca7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| J057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| J058 | main-java | 237 | 10641 | `237211e080f5ce86983c26b5e50bb8e6bc62284fab39776c9f82dc34b333e7ef` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| J059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| J060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| J061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| J062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| J063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| J064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| J065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| J066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| J067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| J068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| J069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| J070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| J071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| J072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| J073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| J074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| J075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| J076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| J077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| J078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| J079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| J080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| J081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| J082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| J083 | main-java | 1289 | 59280 | `ed005e1afd3a7c4ecd241bcc110e0047697dfab80f8ebe9cb2cc9ed6438fed93` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| J084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| J085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| J086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| J087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| J088 | main-java | 1130 | 44334 | `f67a1019097ecd5fb39c70faec1f9ca01a962dddd77ab259e71d8d0cdec36be4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| J089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| J090 | main-java | 508 | 20285 | `c4faf0160a5504e658798a9c9c16654d61694079708eb11c334466ce8c2a6a8e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| J091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| J092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| J093 | main-java | 284 | 12241 | `c44391f8c9af59b7dde02b9c00c0b5a650f9cc672251402fc82e87222a3e491c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| J094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| J095 | main-java | 607 | 25910 | `9773f2a06ffc2c857865b1d751fb4c115447831ff953a8b975f10ec4c07e3e70` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| J096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| J097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| J098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| J099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| J100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| J101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| J102 | main-java | 101 | 3503 | `b8431d287244d8b2b9cda2c5c9360222a803a87ef091014a1fdafcf28dd24755` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyTextureBindings.java` |
| J103 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| J104 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| J105 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| J106 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| J107 | main-java | 981 | 40706 | `e97e7eba4fcb700649ebcd6e2abeaa7535403169256d2db3cba22c05064e3f6f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| J108 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| J109 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| J110 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| J111 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| J112 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| J113 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| J114 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| J115 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| J116 | main-java | 457 | 21544 | `fe18ed936de5b24d1ad3403ef3289664204f8c362d3f19fbd9fad8adb6bf5a57` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| J117 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| J118 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| J119 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| J120 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| J121 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| J122 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| J123 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| J124 | main-java | 800 | 37240 | `9a120c449e6f3f0270acb81030a2c7774ad130b8f310a8bf2de1235ebb851343` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| J125 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| J126 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| J127 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| J128 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| J129 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| J130 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| J131 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| J132 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| J133 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| J134 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| J135 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| J136 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| J137 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| J138 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| J139 | main-java | 68 | 2924 | `b887027ccf7544a5f4c36d3fd6fa31b271780d67d219c4879db6bc69bec3bdd8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| J140 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| J141 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| J142 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| J143 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| J144 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| J145 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| J146 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| J147 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| J148 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| J149 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| J150 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| J151 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| J152 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| J153 | main-java | 1182 | 48577 | `6511a20bcbc7b37a5f39dfe2135217d9618987717110b9139b23ef463cc74169` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| J154 | main-java | 59 | 1619 | `b6d31aa4334f0ee6faff06799d8b2e2a577d573a3e6e6fec7feea5bad3735c92` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| J155 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| J156 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| J157 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| J158 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| J159 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| J160 | main-java | 268 | 9928 | `0346194f683fbb04e8c6d5df13b837397188de783d46f03cf15de486d750201b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| J161 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| J162 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| J163 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| J164 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| J165 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| J166 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| J167 | main-java | 74 | 3147 | `5ba059898052b935af088ea64e9589f7e1c6f84029b2d40798a9bf6d1fc43e2b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| J168 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| J169 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| J170 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| J171 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| J172 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| J173 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| J174 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| J175 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| J176 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| J177 | main-java | 254 | 10712 | `29a0ed676cec34a81071fee35d72cc4cc983b88670fb60b640646cfb2165bd0f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| J178 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| J179 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| J180 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| J181 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| J182 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| J183 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| J184 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| J185 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| J186 | binary-resource | - | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| J187 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| J188 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| J189 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| J190 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| J191 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| J192 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| J193 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| J194 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| J195 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| J196 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| J197 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| J198 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| J199 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| J200 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| J201 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| J202 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| J203 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| J204 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| J205 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| J206 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| J207 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| J208 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| J209 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| J210 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| J211 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| J212 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| J213 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| J214 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| J215 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| J216 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| J217 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| J218 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| J219 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| J220 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| J221 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| J222 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| J223 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| J224 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| J225 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| J226 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| J227 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| J228 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| J229 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| J230 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| J231 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| J232 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| J233 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| J234 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| J235 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| J236 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| J237 | main-resource | 43 | 880 | `056e6b46e3031dcf272205f357e7dd751bba9edfb13183018d5167c97698eae2` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| J238 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| J239 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| J240 | test-java | 16 | 605 | `524055a0a7bd81d9630daa3b5fb085bed06919b2e4e0acd7d60970185efb7ff2` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| J241 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| J242 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| J243 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| J244 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| J245 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| J246 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| J247 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| J248 | test-java | 114 | 5385 | `be22e735ce9f29ac7aba83ad6ce21b62531f71d5b20ea07d9a10f6684523e9d3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerTopLevelWorkParityTest.java` |
| J249 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| J250 | test-java | 32 | 1210 | `9283f3af89df614ca75b769f24ab5da853d0ab6f76aca1332842370cb973fa45` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeModMetadataParityTest.java` |
| J251 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| J252 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| J253 | test-java | 125 | 5014 | `c2c87e927817979d510e3c3c18a01c28c9c259ab92af0fe840874b34fd5fb906` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatchTest.java` |
| J254 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| J255 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| J256 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| J257 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| J258 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| J259 | test-java | 163 | 8152 | `8cee1e04f06bbe624cc2e4a55088bb3c29be1d4ce8cc30e3650cd3e1be957995` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| J260 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| J261 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| J262 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| J263 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| J264 | test-java | 61 | 2783 | `35b79ce1b86b292efc387ea8a8e1de3be2a5abed2d7d76b7870423d757eddfc7` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| J265 | test-java | 139 | 6639 | `d1bb2581285d425cc67d8e3c318a4e9adc41f1a9242e7694744d4575071473d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| J266 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| J267 | test-java | 30 | 943 | `a6b3375f749811b9f4a008f2b295371cc2e55524fee2ea9de0bd20d6ba43d49a` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| J268 | test-java | 56 | 2822 | `5196671a0e887aab45cbb31c6b5ff78fb7c46e892d70c0fdb925a4fefe78d11c` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| J269 | test-java | 173 | 9952 | `8b811ce42b101dfb43b471ce4780d19188ca5644163428778983d0e973bac3fe` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| J270 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| J271 | test-java | 281 | 14988 | `7e944d75a6d9c0696c2d4c7480843aba019dcf8e4b3bd23fd15aa2e7d149c1fd` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java` |
| J272 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| J273 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| J274 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| J275 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |

## Pass 10 completion proof

- Four disjoint clean-room partitions reread every frozen row to EOF with no
  inherited Pass 9 credit: J001-J075 (75 files / 9,831 lines / 389,961 bytes),
  J076-J113 (38 / 9,706 / 407,427), J114-J165 (52 / 10,035 / 421,217), and
  J166-J275 (110 / 9,818 / 432,186).
- Every partition independently recomputed path, kind, physical line count,
  byte count, and SHA-256 with zero drift, zero excluded files, and zero
  unresolved truncation. A fresh whole-tree inventory then matched all **275
  files / 39,390 physical text lines / 1,650,791 bytes** with zero missing,
  extra, reordered, path, kind, line, byte, or hash mismatches.
- Pass 10 confirmed P10-F001 through P10-F003 only after complete frozen-tree
  coverage. J001-J113 and J166-J275 reported zero findings. The J114-J165
  independent review surfaced the three candidates, and the root independently
  rechecked each against original `PrintfInjector`/`PrintfDebugUtil`, original
  `ModelQueries`, original `ModelFactory`, and both DownloadStream retirement
  loops before accepting them.
- No production source was edited and no Gradle task or client was launched
  until this proof was recorded. Because Pass 10 found deviations, it cannot be
  terminal; its repair batch must be followed by a newly frozen full Pass 11.

## Pass 10 repair batch validation

- P10-F001 restores original `PrintfInjector.preRun` ordering by clearing the
  current diagnostic queue only after a readback is known to be non-empty and
  immediately before decoding it. The behavioral test invokes the real private
  callback for two non-empty batches and one empty batch, and restores the
  static maps, queues, native allocation, and system-property state.
- P10-F002 adds the five omitted original `ModelQueries` accessors. All 19
  original boolean/bit-valued metadata queries are now present. One-hot tests
  prove each repaired bit independently and prove every other accessor returns
  false/zero.
- P10-F003 replaces silent duplicate-mapping success with original fatal
  `IllegalStateException` semantics. The check now follows software baking and
  Forge bake-failure handling, then precedes fluid resolution, dedupe, and
  in-flight removal, matching the original merged call boundary.
- The first independent test review found weak all-bits-set coverage and a
  system-property restoration leak. Both were strengthened before the final
  gate. A second production and test review then reported zero findings.
- `rtk git diff --check` passed. The focused three-class gate passed, followed
  by the forced `rtk test .\\gradlew clean test jarJar --rerun-tasks
  --stacktrace --console=plain` gate: **35 suites / 105 tests / 0 failures / 0
  errors / 0 skipped**. No client was launched.
- Because production and tests changed, this repair evidence is non-terminal.
  Pass 11 inherits zero Pass 10 review credit.

## Pass 11 frozen inventory summary

- Files: 275
- Text lines: 39,527
- Bytes: 1,656,868
- audit-tool: 1
- binary-resource: 1
- build-config: 2
- build-logic: 2
- main-java: 181
- main-resource: 10
- shader: 43
- test-java: 35

## Pass 11 frozen ledger

Snapshot frozen after all three Pass 10 repairs, strengthened tests, two
independent zero-finding repair reviews, and the forced 105-test/JarJar gate:
**275 files / 39,527 text lines / 1,656,868 bytes**. Every K row begins at
`pending`; no Pass 10 review credit is inherited. The four disjoint
clean-room partitions are K001-K075 (75 files, 9,831 lines, 389,961 bytes),
K076-K113 (38 files, 9,706 lines, 407,427 bytes), K114-K165 (52 files, 10,056
lines, 421,827 bytes), and K166-K275 (110 files, 9,934 lines, 437,653 bytes).

| ID | Kind | Lines | Bytes | SHA-256 | Status | Finding | Path |
|---|---|---:|---:|---|---|---|---|
| K001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| K002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| K003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| K004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| K005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| K006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| K007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| K008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| K009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| K010 | main-java | 77 | 2860 | `5532ef6577e4e7b564c3eabd31bc1982636a4e0389d3d2a53d11a75e86554cf3` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| K011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| K012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| K013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| K014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| K015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| K016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| K017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| K018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| K019 | main-java | 117 | 3948 | `f81142da3322e9aaa3b03e9d61ef92cf567ddd8a4e269d657b9fd2caef79f5b7` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| K020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| K021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| K022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| K023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| K024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| K025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| K026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| K027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| K028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| K029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| K030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| K031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| K032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| K033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| K034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| K035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| K036 | main-java | 96 | 3045 | `fb73e6b34e24077dfed06ca7d9c8b82ec598f0fa45dc105c34f6d2ca6b85be07` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| K037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| K038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| K039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| K040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| K041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| K042 | main-java | 401 | 18014 | `e0ac18566fbafcad7087f9911ab4bf6b626b05fcc0da73e7c24e58b70a6894ac` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| K043 | main-java | 460 | 18654 | `fb9ad4336baf4d7fcd6072b7baafc98f0deefa8afb6912e3422bac48fd811400` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| K044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| K045 | main-java | 126 | 4826 | `2ca07119fd09514d8b6c2c2f05db6b929c45041822afcde05c411b9525ac4aed` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| K046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| K047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| K048 | main-java | 218 | 8557 | `a1673cd263b10ab2978ef7c867fb49dc122fa9295693b719fc48ac44455788bf` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| K049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| K050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| K051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| K052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| K053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| K054 | main-java | 944 | 40553 | `9cd334392987dfcea92bc3bf879827d476e2c525a9c8e2edc45b2ddf4ff5ec31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| K055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| K056 | main-java | 291 | 12893 | `b46592d03b77fb7693bd1c347fb55622f2fc91e645f4bfb30e5aba2f222b8ca7` | reviewed | P11-F003 | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| K057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| K058 | main-java | 237 | 10641 | `237211e080f5ce86983c26b5e50bb8e6bc62284fab39776c9f82dc34b333e7ef` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| K059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| K060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| K061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| K062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| K063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| K064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| K065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| K066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| K067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| K068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| K069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| K070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| K071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| K072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| K073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| K074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| K075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| K076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| K077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| K078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| K079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| K080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| K081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| K082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| K083 | main-java | 1289 | 59280 | `ed005e1afd3a7c4ecd241bcc110e0047697dfab80f8ebe9cb2cc9ed6438fed93` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| K084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| K085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| K086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| K087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| K088 | main-java | 1130 | 44334 | `f67a1019097ecd5fb39c70faec1f9ca01a962dddd77ab259e71d8d0cdec36be4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| K089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| K090 | main-java | 508 | 20285 | `c4faf0160a5504e658798a9c9c16654d61694079708eb11c334466ce8c2a6a8e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| K091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| K092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| K093 | main-java | 284 | 12241 | `c44391f8c9af59b7dde02b9c00c0b5a650f9cc672251402fc82e87222a3e491c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| K094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| K095 | main-java | 607 | 25910 | `9773f2a06ffc2c857865b1d751fb4c115447831ff953a8b975f10ec4c07e3e70` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| K096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| K097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| K098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| K099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| K100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| K101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| K102 | main-java | 101 | 3503 | `b8431d287244d8b2b9cda2c5c9360222a803a87ef091014a1fdafcf28dd24755` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyTextureBindings.java` |
| K103 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| K104 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| K105 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| K106 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| K107 | main-java | 981 | 40706 | `e97e7eba4fcb700649ebcd6e2abeaa7535403169256d2db3cba22c05064e3f6f` | reviewed | P11-F002 | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| K108 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| K109 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| K110 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| K111 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| K112 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| K113 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| K114 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| K115 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| K116 | main-java | 457 | 21544 | `fe18ed936de5b24d1ad3403ef3289664204f8c362d3f19fbd9fad8adb6bf5a57` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| K117 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| K118 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| K119 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| K120 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| K121 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| K122 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| K123 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| K124 | main-java | 800 | 37240 | `9a120c449e6f3f0270acb81030a2c7774ad130b8f310a8bf2de1235ebb851343` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| K125 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| K126 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| K127 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| K128 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| K129 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| K130 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| K131 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| K132 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| K133 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| K134 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| K135 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| K136 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| K137 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| K138 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| K139 | main-java | 68 | 2924 | `b887027ccf7544a5f4c36d3fd6fa31b271780d67d219c4879db6bc69bec3bdd8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| K140 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| K141 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| K142 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| K143 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| K144 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| K145 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| K146 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| K147 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| K148 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| K149 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| K150 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| K151 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| K152 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| K153 | main-java | 1182 | 48634 | `64ca6b283d683c44defc501bda1d378b7d41709b2767732252ac9222252a5c1e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| K154 | main-java | 79 | 2141 | `25dfbe13df4c008d4290a8a8a18134d4afe82683cd7fe4da373728828f657e48` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| K155 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| K156 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| K157 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| K158 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| K159 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| K160 | main-java | 269 | 9959 | `c5b1342ed3f165ba3c4431213ae1d5015bafe152de10554868976d905b42787c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| K161 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| K162 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| K163 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| K164 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| K165 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| K166 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| K167 | main-java | 74 | 3147 | `5ba059898052b935af088ea64e9589f7e1c6f84029b2d40798a9bf6d1fc43e2b` | reviewed | P11-F001 | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| K168 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| K169 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| K170 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| K171 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| K172 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| K173 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| K174 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| K175 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| K176 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| K177 | main-java | 254 | 10712 | `29a0ed676cec34a81071fee35d72cc4cc983b88670fb60b640646cfb2165bd0f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| K178 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| K179 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| K180 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| K181 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| K182 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| K183 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| K184 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| K185 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| K186 | binary-resource | - | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| K187 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| K188 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| K189 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| K190 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| K191 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| K192 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| K193 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| K194 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| K195 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| K196 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| K197 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| K198 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| K199 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| K200 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| K201 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| K202 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| K203 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| K204 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| K205 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| K206 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| K207 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| K208 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| K209 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| K210 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| K211 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| K212 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| K213 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| K214 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| K215 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| K216 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| K217 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| K218 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| K219 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| K220 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| K221 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| K222 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| K223 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| K224 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| K225 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| K226 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| K227 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| K228 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| K229 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| K230 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| K231 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| K232 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| K233 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| K234 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| K235 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| K236 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| K237 | main-resource | 43 | 880 | `056e6b46e3031dcf272205f357e7dd751bba9edfb13183018d5167c97698eae2` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| K238 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| K239 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| K240 | test-java | 16 | 605 | `524055a0a7bd81d9630daa3b5fb085bed06919b2e4e0acd7d60970185efb7ff2` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| K241 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| K242 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| K243 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| K244 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| K245 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| K246 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| K247 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| K248 | test-java | 114 | 5385 | `be22e735ce9f29ac7aba83ad6ce21b62531f71d5b20ea07d9a10f6684523e9d3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerTopLevelWorkParityTest.java` |
| K249 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| K250 | test-java | 32 | 1210 | `9283f3af89df614ca75b769f24ab5da853d0ab6f76aca1332842370cb973fa45` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeModMetadataParityTest.java` |
| K251 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| K252 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| K253 | test-java | 125 | 5014 | `c2c87e927817979d510e3c3c18a01c28c9c259ab92af0fe840874b34fd5fb906` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatchTest.java` |
| K254 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| K255 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| K256 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| K257 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| K258 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| K259 | test-java | 163 | 8152 | `8cee1e04f06bbe624cc2e4a55088bb3c29be1d4ce8cc30e3650cd3e1be957995` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| K260 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| K261 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| K262 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| K263 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| K264 | test-java | 61 | 2783 | `35b79ce1b86b292efc387ea8a8e1de3be2a5abed2d7d76b7870423d757eddfc7` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| K265 | test-java | 139 | 6639 | `d1bb2581285d425cc67d8e3c318a4e9adc41f1a9242e7694744d4575071473d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| K266 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| K267 | test-java | 94 | 3650 | `2bd8be1565b4fb66766355329f2af17c6191478074b1a15d77e0a128f139b592` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| K268 | test-java | 82 | 4154 | `3a2b0895c1b2f0168c4aea22e267c787e78b9725f94017c594c777e2403ba3d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| K269 | test-java | 199 | 11380 | `30690769dd485e9cacbedb142995570436f77ee2cdf810084d1983faafd5982f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| K270 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| K271 | test-java | 281 | 14988 | `7e944d75a6d9c0696c2d4c7480843aba019dcf8e4b3bd23fd15aa2e7d149c1fd` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java` |
| K272 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| K273 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| K274 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| K275 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |

### Pass 11 full-review freeze proof

- All K001-K275 physical lines were read to EOF: **275 files / 39,527 text
  lines / 1,656,868 bytes**. No Pass 10 review credit was inherited.
- The four disjoint clean-room partitions completed exactly as frozen:
  K001-K075 = **75 files / 9,831 lines / 389,961 bytes**; K076-K113 =
  **38 / 9,706 / 407,427**; K114-K165 = **52 / 10,056 / 421,827**; and
  K166-K275 = **110 / 9,934 / 437,653**.
- The inventory generator was rerun after review and compared positionally
  against every frozen K row: **275 paths**, with **0 path, kind, physical-line,
  byte, or SHA-256 drifts**. Partition totals add exactly to the whole snapshot.
- Production Java used CodeGraph first; original Voxy and the necessary Forge,
  Embeddium, and Oculus contracts were traced before classification. Every
  shader/resource/test line and the inventory tool reached EOF; the binary icon
  was inspected by hash and visually at original resolution.
- Three P3 documentation defects were confirmed: P11-F001 and P11-F003 repeat
  the XX.4 free/reallocate theory that XX.5 ground truth excluded, while
  P11-F002 names the removed END-tick dimension boundary instead of the active
  `LevelRenderer#setLevel` `HEAD` hook.
- No production source was modified and no Gradle task or client was launched
  until this proof was recorded. Because Pass 11 found deviations, it cannot be
  terminal; the comment repairs must be followed by a newly frozen full Pass 12.

### Pass 11 repair batch validation

- P11-F001 and P11-F003 now describe only the original geometry-buffer reuse,
  lifetime, allocation-churn, ownership, and GL-ordering contract; the excluded
  XX.4 NVIDIA-aliasing theory was removed from production comments.
- P11-F002 now names the active `LevelRenderer#setLevel` `HEAD` lifecycle
  boundary rather than the removed END-tick fallback.
- These are comment-only repairs; runtime code, data layout, and ownership were
  unchanged. `rtk git diff --check` passed. No Gradle task or client was run.
- A fresh inventory changed only L056, L107, and L167, exactly matching the
  three repaired comments. Pass 12 inherits zero Pass 11 review credit.

## Pass 12 frozen inventory summary

- Files: 275
- Text lines: 39,522
- Bytes: 1,656,447
- audit-tool: 1
- binary-resource: 1
- build-config: 2
- build-logic: 2
- main-java: 181
- main-resource: 10
- shader: 43
- test-java: 35

## Pass 12 frozen ledger

Snapshot frozen after all three Pass 11 comment repairs and the successful
static diff check: **275 files / 39,522 text lines / 1,656,447 bytes**. Every
L row begins at `pending`; no Pass 11 review credit is inherited. The four
disjoint clean-room partitions are L001-L075 (75 files, 9,830 lines, 389,851
bytes), L076-L113 (38 files, 9,706 lines, 407,436 bytes), L114-L165 (52 files,
10,056 lines, 421,827 bytes), and L166-L275 (110 files, 9,930 lines, 437,333
bytes).

| ID | Kind | Lines | Bytes | SHA-256 | Status | Finding | Path |
|---|---|---:|---:|---|---|---|---|
| L001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| L002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| L003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| L004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| L005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| L006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| L007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| L008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| L009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| L010 | main-java | 77 | 2860 | `5532ef6577e4e7b564c3eabd31bc1982636a4e0389d3d2a53d11a75e86554cf3` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| L011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| L012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| L013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| L014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| L015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| L016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| L017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| L018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| L019 | main-java | 117 | 3948 | `f81142da3322e9aaa3b03e9d61ef92cf567ddd8a4e269d657b9fd2caef79f5b7` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| L020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| L021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| L022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| L023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| L024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| L025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| L026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| L027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| L028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| L029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| L030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| L031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| L032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| L033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| L034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| L035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| L036 | main-java | 96 | 3045 | `fb73e6b34e24077dfed06ca7d9c8b82ec598f0fa45dc105c34f6d2ca6b85be07` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| L037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| L038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| L039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| L040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| L041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| L042 | main-java | 401 | 18014 | `e0ac18566fbafcad7087f9911ab4bf6b626b05fcc0da73e7c24e58b70a6894ac` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| L043 | main-java | 460 | 18654 | `fb9ad4336baf4d7fcd6072b7baafc98f0deefa8afb6912e3422bac48fd811400` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| L044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| L045 | main-java | 126 | 4826 | `2ca07119fd09514d8b6c2c2f05db6b929c45041822afcde05c411b9525ac4aed` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| L046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| L047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| L048 | main-java | 218 | 8557 | `a1673cd263b10ab2978ef7c867fb49dc122fa9295693b719fc48ac44455788bf` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| L049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| L050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| L051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| L052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| L053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| L054 | main-java | 944 | 40553 | `9cd334392987dfcea92bc3bf879827d476e2c525a9c8e2edc45b2ddf4ff5ec31` | reviewed | P12-F001 | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| L055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| L056 | main-java | 290 | 12783 | `6a9a732b05dbe46b8b69685c3f212f4e8b564c71aad95d62b6433480f81f4cde` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| L057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| L058 | main-java | 237 | 10641 | `237211e080f5ce86983c26b5e50bb8e6bc62284fab39776c9f82dc34b333e7ef` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| L059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| L060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| L061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| L062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| L063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| L064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| L065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| L066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| L067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| L068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| L069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| L070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| L071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| L072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| L073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| L074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| L075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| L076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| L077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| L078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| L079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| L080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| L081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| L082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| L083 | main-java | 1289 | 59280 | `ed005e1afd3a7c4ecd241bcc110e0047697dfab80f8ebe9cb2cc9ed6438fed93` | reviewed | P12-F002 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| L084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| L085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| L086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| L087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| L088 | main-java | 1130 | 44334 | `f67a1019097ecd5fb39c70faec1f9ca01a962dddd77ab259e71d8d0cdec36be4` | reviewed | P12-F002 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| L089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| L090 | main-java | 508 | 20285 | `c4faf0160a5504e658798a9c9c16654d61694079708eb11c334466ce8c2a6a8e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| L091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| L092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| L093 | main-java | 284 | 12241 | `c44391f8c9af59b7dde02b9c00c0b5a650f9cc672251402fc82e87222a3e491c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| L094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| L095 | main-java | 607 | 25910 | `9773f2a06ffc2c857865b1d751fb4c115447831ff953a8b975f10ec4c07e3e70` | reviewed | P12-F002 | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| L096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| L097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| L098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| L099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| L100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| L101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| L102 | main-java | 101 | 3503 | `b8431d287244d8b2b9cda2c5c9360222a803a87ef091014a1fdafcf28dd24755` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyTextureBindings.java` |
| L103 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| L104 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| L105 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| L106 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| L107 | main-java | 981 | 40715 | `21ab8bb12d14b5820933e422c2f6814aa444a73cc4b55925ff920c3db73b0f0f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| L108 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| L109 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| L110 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| L111 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| L112 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| L113 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| L114 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| L115 | main-java | 173 | 6097 | `9aa86d8ce97cf8c0eb5e75b448f7cd53c1defd6138232372baad9da02b188b95` | reviewed | P12-F004 | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| L116 | main-java | 457 | 21544 | `fe18ed936de5b24d1ad3403ef3289664204f8c362d3f19fbd9fad8adb6bf5a57` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| L117 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| L118 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| L119 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| L120 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| L121 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| L122 | main-java | 167 | 6274 | `7926d746b1cbf9850b82edf1d0ada782f72b1e2f889c2cedadc7793b43764f64` | reviewed | P12-F005 | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| L123 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| L124 | main-java | 800 | 37240 | `9a120c449e6f3f0270acb81030a2c7774ad130b8f310a8bf2de1235ebb851343` | reviewed | P12-F005 | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| L125 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| L126 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| L127 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| L128 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| L129 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| L130 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| L131 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| L132 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| L133 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| L134 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| L135 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| L136 | main-java | 145 | 6187 | `821bca26314540c36b6327218d860f6208b7af1f94387475676efedb9e6ab0da` | reviewed | P12-F003 | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| L137 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| L138 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| L139 | main-java | 68 | 2924 | `b887027ccf7544a5f4c36d3fd6fa31b271780d67d219c4879db6bc69bec3bdd8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| L140 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| L141 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| L142 | main-java | 29 | 1399 | `a5fcb4d93b2921c21830318c3048b5c97b064509a4add285b24f540005b6556a` | reviewed | P12-F005 | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| L143 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| L144 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| L145 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| L146 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| L147 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| L148 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| L149 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| L150 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| L151 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| L152 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| L153 | main-java | 1182 | 48634 | `64ca6b283d683c44defc501bda1d378b7d41709b2767732252ac9222252a5c1e` | reviewed | P12-F005, P12-F007 | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| L154 | main-java | 79 | 2141 | `25dfbe13df4c008d4290a8a8a18134d4afe82683cd7fe4da373728828f657e48` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| L155 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| L156 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| L157 | main-java | 1128 | 50791 | `6c3289ad87bac3b7f92a45b132fc83e0393d54a6b8f41dd500187462658a0a1e` | reviewed | P12-F001, P12-F005, P12-F006 | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| L158 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| L159 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| L160 | main-java | 269 | 9959 | `c5b1342ed3f165ba3c4431213ae1d5015bafe152de10554868976d905b42787c` | reviewed | P12-F005 | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| L161 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| L162 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| L163 | main-java | 1828 | 80009 | `ced17c304edb36bb3a635d7c084fcf404d345adfd03451dad4323bc9d8ee6e11` | reviewed | P12-F005 | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| L164 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| L165 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| L166 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| L167 | main-java | 70 | 2827 | `cb4e57040b920edf891802fe9463830b03bc99ae3dd9061513879fff917432d0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| L168 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| L169 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| L170 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| L171 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| L172 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| L173 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| L174 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| L175 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| L176 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| L177 | main-java | 254 | 10712 | `29a0ed676cec34a81071fee35d72cc4cc983b88670fb60b640646cfb2165bd0f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| L178 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| L179 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| L180 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| L181 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| L182 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| L183 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| L184 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| L185 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| L186 | binary-resource | - | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| L187 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| L188 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| L189 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| L190 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| L191 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| L192 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| L193 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| L194 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| L195 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| L196 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| L197 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| L198 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| L199 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| L200 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| L201 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| L202 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| L203 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| L204 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| L205 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| L206 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| L207 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| L208 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| L209 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| L210 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| L211 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| L212 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| L213 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| L214 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| L215 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| L216 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| L217 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| L218 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| L219 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| L220 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| L221 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| L222 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| L223 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| L224 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| L225 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| L226 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| L227 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| L228 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| L229 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| L230 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| L231 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| L232 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| L233 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| L234 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| L235 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| L236 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| L237 | main-resource | 43 | 880 | `056e6b46e3031dcf272205f357e7dd751bba9edfb13183018d5167c97698eae2` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| L238 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| L239 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| L240 | test-java | 16 | 605 | `524055a0a7bd81d9630daa3b5fb085bed06919b2e4e0acd7d60970185efb7ff2` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| L241 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| L242 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| L243 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| L244 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| L245 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| L246 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| L247 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| L248 | test-java | 114 | 5385 | `be22e735ce9f29ac7aba83ad6ce21b62531f71d5b20ea07d9a10f6684523e9d3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerTopLevelWorkParityTest.java` |
| L249 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| L250 | test-java | 32 | 1210 | `9283f3af89df614ca75b769f24ab5da853d0ab6f76aca1332842370cb973fa45` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeModMetadataParityTest.java` |
| L251 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| L252 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| L253 | test-java | 125 | 5014 | `c2c87e927817979d510e3c3c18a01c28c9c259ab92af0fe840874b34fd5fb906` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatchTest.java` |
| L254 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| L255 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| L256 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| L257 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| L258 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| L259 | test-java | 163 | 8152 | `8cee1e04f06bbe624cc2e4a55088bb3c29be1d4ce8cc30e3650cd3e1be957995` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| L260 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| L261 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| L262 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| L263 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| L264 | test-java | 61 | 2783 | `35b79ce1b86b292efc387ea8a8e1de3be2a5abed2d7d76b7870423d757eddfc7` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| L265 | test-java | 139 | 6639 | `d1bb2581285d425cc67d8e3c318a4e9adc41f1a9242e7694744d4575071473d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| L266 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| L267 | test-java | 94 | 3650 | `2bd8be1565b4fb66766355329f2af17c6191478074b1a15d77e0a128f139b592` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| L268 | test-java | 82 | 4154 | `3a2b0895c1b2f0168c4aea22e267c787e78b9725f94017c594c777e2403ba3d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| L269 | test-java | 199 | 11380 | `30690769dd485e9cacbedb142995570436f77ee2cdf810084d1983faafd5982f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| L270 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| L271 | test-java | 281 | 14988 | `7e944d75a6d9c0696c2d4c7480843aba019dcf8e4b3bd23fd15aa2e7d149c1fd` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java` |
| L272 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| L273 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| L274 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| L275 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |

### Pass 12 full-review freeze proof

- All L001-L275 physical lines were read to EOF: **275 files / 39,522 text
  lines / 1,656,447 bytes**. No Pass 11 review credit was inherited.
- Disjoint coverage reconciled exactly: L001-L075 = **75 files / 9,830 lines /
  389,851 bytes**; L076-L113 = **38 / 9,706 / 407,436**; L114-L165 =
  **52 / 10,056 / 421,827**; L166-L275 = **110 / 9,930 / 437,333**.
- Every partition re-read comments, imports, error/cleanup paths, optional flags,
  resources, tests, and platform adapters. Each partition independently
  recomputed `path / kind / lines / bytes / SHA-256` drift as
  **0 / 0 / 0 / 0 / 0**.
- The whole frozen inventory was regenerated after all four reports: baseline
  and current both remained **275 files / 39,522 lines / 1,656,447 bytes**, with
  whole-inventory drift **0 / 0 / 0 / 0 / 0**.
- Seven findings were confirmed only after complete coverage and the required
  finding-level original-source cross-checks: P12-F001 restores
  the original opt-in node integrity verifier; P12-F002 bounds the per-frame GL
  state boundary to active mutation slots; P12-F003 binds chunk-bound reseeding
  to renderer-owner generations; P12-F004 removes post-free lifecycle masking;
  P12-F005 restores the remaining original Logger routes; P12-F006 restores the
  inner-to-leaf geometry-request postcondition; and P12-F007 restores null-biome
  fail-fast ownership.
- Because Pass 12 found deviations, it cannot be terminal. All seven must be
  repaired as one batch, validated, frozen again, and followed by a new
  zero-inheritance full Pass 13.

### Pass 12 repair batch validation

- P12-F001 restores the exact `voxy.verifyNodeManager` opt-in verifier,
  post-publication ordering, tree/request/watcher/allocation checks, and the
  child-request accounting mutations used by that verifier.
- P12-F002 replaces driver-maximum GL binding scans with the original fixed
  12-unit texture/sampler boundary plus exact Forge-adapter UBO/SSBO mutation
  plans, including optional Oculus images, statistics, and shader printf.
- P12-F003 binds the Embeddium built-section snapshot to each successfully
  published original render-owner generation at `renderLayer` CUTOUT `HEAD`;
  the generation is recorded even when the snapshot contains zero sections.
- P12-F004 removes the outer GPU-timing freed mask. Terminal shutdown still owns
  exactly one formal free, while a local empty-query test proves a second free
  fails in `TrackedObject.free0()` before any GL call.
- P12-F005 restores all 22 original common-Logger sites with exact levels,
  messages, concatenation, and throwable arguments. The only remaining direct
  logger calls in those files are the two documented Forge-only diagnostics.
- P12-F006 restores the original watcher-bit plus geometry-in-flight
  postcondition immediately after an inner-to-leaf request and before assigning
  the temporary empty geometry sentinel.
- P12-F007 restores strict biome identifier parsing, construct-time plains
  ownership, exact invariant messages, missing/duplicate diagnostics, and null
  fail-fast semantics; the non-original first-registry-entry fallback is gone.
- `rtk test .\\gradlew compileJava` passed. The focused repair gate then
  passed **4 suites / 21 tests / 0 failures / 0 errors**:
  `AsyncNodeManagerTopLevelWorkParityTest`,
  `ForgeVoxyInstanceSessionLifecycleTest`,
  `OriginalParityRepairSourceContractTest`, and
  `Pass3ParityRepairTest`.
- `rtk git diff --check` passed. Relative to the untouched Pass 12 baseline,
  the new inventory has **0 missing / 0 extra / 0 kind drift** and exactly
  **18 expected SHA changes** across production files and their source-contract
  tests. Pass 13 inherits zero Pass 12 review credit.

## Pass 13 frozen inventory summary

- Files: 275
- Text lines: 40,092
- Bytes: 1,686,579
- audit-tool: 1
- binary-resource: 1
- build-config: 2
- build-logic: 2
- main-java: 181
- main-resource: 10
- shader: 43
- test-java: 35

## Pass 13 frozen ledger

Snapshot frozen after the complete P12-F001 through P12-F007 repair batch,
focused 21-test gate, and successful static diff check: **275 files / 40,092
text lines / 1,686,579 bytes**. Every M row begins at `pending`; no Pass 12
review credit is inherited. The four disjoint clean-room partitions are
M001-M075 (**75 files / 9,835 lines / 390,076 bytes**), M076-M113
(**38 / 9,811 / 412,336**), M114-M165 (**52 / 10,286 / 431,853**), and
M166-M275 (**110 / 10,160 / 452,314**).

| ID | Kind | Lines | Bytes | SHA-256 | Status | Finding | Path |
|---|---|---:|---:|---|---|---|---|
| M001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| M002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| M003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| M004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| M005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| M006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| M007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| M008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| M009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| M010 | main-java | 77 | 2860 | `5532ef6577e4e7b564c3eabd31bc1982636a4e0389d3d2a53d11a75e86554cf3` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| M011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| M012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| M013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| M014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| M015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| M016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| M017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| M018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| M019 | main-java | 117 | 3948 | `f81142da3322e9aaa3b03e9d61ef92cf567ddd8a4e269d657b9fd2caef79f5b7` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| M020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| M021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| M022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| M023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| M024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| M025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| M026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| M027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| M028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| M029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| M030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| M031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| M032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| M033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| M034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| M035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| M036 | main-java | 96 | 3045 | `fb73e6b34e24077dfed06ca7d9c8b82ec598f0fa45dc105c34f6d2ca6b85be07` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| M037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| M038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| M039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| M040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| M041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| M042 | main-java | 401 | 18014 | `e0ac18566fbafcad7087f9911ab4bf6b626b05fcc0da73e7c24e58b70a6894ac` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| M043 | main-java | 460 | 18654 | `fb9ad4336baf4d7fcd6072b7baafc98f0deefa8afb6912e3422bac48fd811400` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| M044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| M045 | main-java | 126 | 4826 | `2ca07119fd09514d8b6c2c2f05db6b929c45041822afcde05c411b9525ac4aed` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| M046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| M047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| M048 | main-java | 218 | 8557 | `a1673cd263b10ab2978ef7c867fb49dc122fa9295693b719fc48ac44455788bf` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| M049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| M050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| M051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| M052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| M053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| M054 | main-java | 949 | 40778 | `2c06c745cef5ff1fa79f13276b5552c52cb84e76c91f20fd4b497ce15f24bba1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| M055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| M056 | main-java | 290 | 12783 | `6a9a732b05dbe46b8b69685c3f212f4e8b564c71aad95d62b6433480f81f4cde` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| M057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| M058 | main-java | 237 | 10641 | `237211e080f5ce86983c26b5e50bb8e6bc62284fab39776c9f82dc34b333e7ef` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| M059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| M060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| M061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| M062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| M063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| M064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| M065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| M066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| M067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| M068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| M069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| M070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| M071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| M072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| M073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| M074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| M075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| M076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| M077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| M078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| M079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| M080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| M081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| M082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| M083 | main-java | 1295 | 59530 | `24a2c984de94364066e9a427b7aff269c871558cec0021a96ac966b5e0cef8b4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| M084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| M085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| M086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| M087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| M088 | main-java | 1134 | 44457 | `b5e0d6d0c61ac21b660dd8db1c566e95422837958a5ab69c11ca74502890e1ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| M089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| M090 | main-java | 508 | 20285 | `c4faf0160a5504e658798a9c9c16654d61694079708eb11c334466ce8c2a6a8e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| M091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| M092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| M093 | main-java | 284 | 12241 | `c44391f8c9af59b7dde02b9c00c0b5a650f9cc672251402fc82e87222a3e491c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| M094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| M095 | main-java | 697 | 30207 | `f5dfbb4b8aa256d8624987ee0c20cd9146daf4af558bc088b414b765ae11b7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| M096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| M097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| M098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| M099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| M100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| M101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| M102 | main-java | 101 | 3503 | `b8431d287244d8b2b9cda2c5c9360222a803a87ef091014a1fdafcf28dd24755` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyTextureBindings.java` |
| M103 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| M104 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| M105 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| M106 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| M107 | main-java | 986 | 40945 | `52da6d7678b54030b102a3094b081d329fdae5bca6c94383c7967336a5e12c1d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| M108 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| M109 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| M110 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| M111 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| M112 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| M113 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| M114 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| M115 | main-java | 166 | 5925 | `bcdf4d4e8d48a01f5c6ebcf8b0e24272c2baf22fd89da3963e351d35183c3776` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| M116 | main-java | 457 | 21544 | `fe18ed936de5b24d1ad3403ef3289664204f8c362d3f19fbd9fad8adb6bf5a57` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| M117 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| M118 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| M119 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| M120 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| M121 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| M122 | main-java | 168 | 6305 | `c849ad573e3f825d279316c4264c2620a9e712290f85e61d7014d519ceefc2b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| M123 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| M124 | main-java | 801 | 37244 | `cc863364c24c4b57c8f3b81edc163f0cddad0b5c529a0b569b408e86866383e6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| M125 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| M126 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| M127 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| M128 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| M129 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| M130 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| M131 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| M132 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| M133 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| M134 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| M135 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| M136 | main-java | 151 | 6540 | `b5d33859a0d91975428bcf3dfd4440c607c87c6e5a3e2f2b0bae261d2ad32b5a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| M137 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| M138 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| M139 | main-java | 68 | 2924 | `b887027ccf7544a5f4c36d3fd6fa31b271780d67d219c4879db6bc69bec3bdd8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| M140 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| M141 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| M142 | main-java | 29 | 1314 | `f690857bb98ab8eeab06afb448ebe9f6041cefff90707bcc400fc5a943326e6d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| M143 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| M144 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| M145 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| M146 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| M147 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| M148 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| M149 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| M150 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| M151 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| M152 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| M153 | main-java | 1179 | 48455 | `d91bebcf6ea13a430d93bbb087a6772c1e86c37c142332d6f886436b948d3e94` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| M154 | main-java | 79 | 2141 | `25dfbe13df4c008d4290a8a8a18134d4afe82683cd7fe4da373728828f657e48` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| M155 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| M156 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| M157 | main-java | 1354 | 60725 | `b0b63198dd21b5018437067b47922771b79145aee02198f31cb9b7c5c767912e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| M158 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| M159 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| M160 | main-java | 274 | 10076 | `60754f2b6cf3eb2d9b6603aa1fb79d1834e791bd018b35b1a3045b49ef616a5c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| M161 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| M162 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| M163 | main-java | 1829 | 80032 | `9914b7ea29d4911b112a16a1165f2f88ca31ca2f808e72b70518a837898a6183` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| M164 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| M165 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| M166 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| M167 | main-java | 70 | 2827 | `cb4e57040b920edf891802fe9463830b03bc99ae3dd9061513879fff917432d0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| M168 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| M169 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| M170 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| M171 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| M172 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| M173 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| M174 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| M175 | main-java | 214 | 9181 | `5055b75eb3b4cc83a41a51f1c0788db584158cc2e8419be1ec7da5c70918d434` | reviewed | P13-F001 | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| M176 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| M177 | main-java | 254 | 10712 | `29a0ed676cec34a81071fee35d72cc4cc983b88670fb60b640646cfb2165bd0f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| M178 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| M179 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| M180 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| M181 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| M182 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| M183 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| M184 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| M185 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| M186 | binary-resource | - | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| M187 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| M188 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| M189 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| M190 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| M191 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| M192 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| M193 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| M194 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| M195 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| M196 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| M197 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| M198 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| M199 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| M200 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| M201 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| M202 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| M203 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| M204 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| M205 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| M206 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| M207 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| M208 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| M209 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| M210 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| M211 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| M212 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| M213 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| M214 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| M215 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| M216 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| M217 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| M218 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| M219 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| M220 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| M221 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| M222 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| M223 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| M224 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| M225 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| M226 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| M227 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| M228 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| M229 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| M230 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| M231 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| M232 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| M233 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| M234 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| M235 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| M236 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| M237 | main-resource | 43 | 880 | `056e6b46e3031dcf272205f357e7dd751bba9edfb13183018d5167c97698eae2` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| M238 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| M239 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| M240 | test-java | 16 | 605 | `524055a0a7bd81d9630daa3b5fb085bed06919b2e4e0acd7d60970185efb7ff2` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| M241 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| M242 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| M243 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| M244 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| M245 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| M246 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| M247 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| M248 | test-java | 139 | 6903 | `75c7236fa24619b5d66014a2602a78d6164afb7fb08e9458f33579cbc14bfc36` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerTopLevelWorkParityTest.java` |
| M249 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| M250 | test-java | 32 | 1210 | `9283f3af89df614ca75b769f24ab5da853d0ab6f76aca1332842370cb973fa45` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeModMetadataParityTest.java` |
| M251 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| M252 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| M253 | test-java | 125 | 5014 | `c2c87e927817979d510e3c3c18a01c28c9c259ab92af0fe840874b34fd5fb906` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatchTest.java` |
| M254 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| M255 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| M256 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| M257 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| M258 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| M259 | test-java | 257 | 13581 | `e9fb2957991d7c660433a16f73411fc561cfb5f81b33f25c4a6cd6c785a915d2` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| M260 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| M261 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| M262 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| M263 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| M264 | test-java | 61 | 2783 | `35b79ce1b86b292efc387ea8a8e1de3be2a5abed2d7d76b7870423d757eddfc7` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| M265 | test-java | 139 | 6639 | `d1bb2581285d425cc67d8e3c318a4e9adc41f1a9242e7694744d4575071473d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| M266 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| M267 | test-java | 94 | 3650 | `2bd8be1565b4fb66766355329f2af17c6191478074b1a15d77e0a128f139b592` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| M268 | test-java | 174 | 10846 | `528a76ddef099812d024fe34c82d681d4ab684ceebd86ae44915b28fcd13d0af` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| M269 | test-java | 199 | 11380 | `30690769dd485e9cacbedb142995570436f77ee2cdf810084d1983faafd5982f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| M270 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| M271 | test-java | 300 | 16330 | `1787c155143508099709dbb5344b9a3727eae14ce1ddfc5d108a0bdf991af9bd` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java` |
| M272 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| M273 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| M274 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| M275 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |

### Pass 13 full-review freeze proof

- All M001-M275 physical lines were read to EOF: **275 files / 40,092 text
  lines / 1,686,579 bytes**. No Pass 12 review credit was inherited.
- The four disjoint clean-room partitions completed exactly as frozen:
  M001-M075 = **75 files / 9,835 lines / 390,076 bytes**; M076-M113 =
  **38 / 9,811 / 412,336**; M114-M165 = **52 / 10,286 / 431,853**; and
  M166-M275 = **110 / 10,160 / 452,314**.
- Each partition independently re-matched path, kind, line count, byte count,
  and SHA-256. The separate whole-inventory proof then matched **275 frozen / 275
  current** rows with zero missing, extra, reordered, path, kind, line, byte, or
  SHA drift.
- P13-F001 was the only new finding. It was confirmed after complete coverage
  and by a separate read-only lifecycle review: the two formal terminal gates
  already provide idempotent ownership, while the local shared-index `freed`
  flag bypasses the tracked buffer's duplicate-free failure.
- No production source was edited and no Gradle task or client was launched
  until this proof was recorded. Because Pass 13 found a deviation, it cannot
  be terminal; the repair must be validated and followed by a newly frozen,
  zero-inheritance full Pass 14.

### Pass 13 repair batch validation

- P13-F001 removes only `SharedIndexBuffer`'s redundant local `freed` state and
  conditional. Forge's terminal `freeAll()` adaptation and its
  `sharedIndexInitialized` plus `terminalCleanupComplete` owners are unchanged.
- The source-contract test proves direct delegation to `GlBuffer.free()`, that
  `TrackedObject.free0()` precedes `glDeleteBuffers`, and that initialization,
  runtime shutdown, and instance-terminal ordering each have one formal owner.
  It deliberately avoids loading `SharedIndexBuffer`, whose static instances
  require a live GL context.
- `rtk test .\\gradlew compileJava test --tests
  me.cortex.voxy.forge.ForgeVoxyInstanceSessionLifecycleTest --stacktrace
  --console=plain` passed: **1 suite / 7 tests / 0 failures / 0 errors / 0
  skips**. `rtk git diff --check` also passed. No client was launched.
- Because production and test sources changed, this repair is non-terminal.
  Pass 14 must freeze the new hashes and reread all executable files from zero
  inherited coverage.

## Pass 14 frozen inventory summary

- Files: 275
- Text lines: 40,139
- Bytes: 1,689,515
- audit-tool: 1
- binary-resource: 1
- build-config: 2
- build-logic: 2
- main-java: 181
- main-resource: 10
- shader: 43
- test-java: 35

## Pass 14 frozen ledger

Snapshot frozen after the complete P13-F001 repair, focused seven-test gate,
and successful static diff check: **275 files / 40,139 text lines / 1,689,515
bytes**. Exactly two executable files changed relative to Pass 13:
`SharedIndexBuffer.java` and its source-contract test. Every N row begins at
`pending`; no Pass 13 review credit is inherited. The four disjoint clean-room
partitions are N001-N075 (**75 files / 9,835 lines / 390,076 bytes**),
N076-N113 (**38 / 9,811 / 412,336**), N114-N165 (**52 / 10,286 / 431,853**),
and N166-N275 (**110 / 10,207 / 455,250**).

| ID | Kind | Lines | Bytes | SHA-256 | Status | Finding | Path |
|---|---|---:|---:|---|---|---|---|
| N001 | build-logic | 381 | 14990 | `7b0b2d8901fdb14ef0ea011d23de0cd92444af87091bc3c906b2b868e2a8f4e2` | reviewed | - | `build.gradle` |
| N002 | build-config | 23 | 687 | `4b5a6e78e8b24e75a41422b12856113efae0e186f2bdff0dbe2d443971063685` | reviewed | - | `gradle.properties` |
| N003 | build-config | 7 | 256 | `435698be2abeed278fc23142d7bae8ba170084770fb34c27f1682c8f7044cc4f` | reviewed | - | `gradle/wrapper/gradle-wrapper.properties` |
| N004 | build-logic | 27 | 635 | `8a39a841f26e0730138dde7a8305e38a9bd46f5ee5b0c12adb4138264bb4aead` | reviewed | - | `settings.gradle` |
| N005 | main-java | 33 | 837 | `ca4e61d800608c0125ebec8fbae6c0b4164e5f250bb257bd20a93119bd162124` | reviewed | - | `src/main/java/me/cortex/voxy/client/compat/SemaphoreBlockImpersonator.java` |
| N006 | main-java | 11 | 272 | `07fbb4cb3d0f257f72c117a08fa75c6be7ea24e53806835c27510030289d9162` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/compressors/StorageCompressor.java` |
| N007 | main-java | 120 | 3523 | `efa2f58d27e7962d1f7fff0c8137ae9db18c53fefe55a92a724045c89944bfcb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/ConfigBuildCtx.java` |
| N008 | main-java | 13 | 355 | `b5435ddb3ae78284724affe70c6e078a9fa837b1cfa57b43aecee531969b76e6` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IMappingStorage.java` |
| N009 | main-java | 7 | 200 | `e6bd3e7519f63ce95c9e1df784de2368ea7d747bf25b2e2bf67a559ff9e12350` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/IStoredSectionPositionIterator.java` |
| N010 | main-java | 77 | 2860 | `5532ef6577e4e7b564c3eabd31bc1982636a4e0389d3d2a53d11a75e86554cf3` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionSerializationStorage.java` |
| N011 | main-java | 11 | 446 | `65aac70af9ae6f1b857480c9e81702544c7550c33d78b4e918d933c5ad744ed0` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/section/SectionStorage.java` |
| N012 | main-java | 133 | 4161 | `3bae7970764215c6e45dcb1f10832d5ba7e7b4a4e283442d6acb48df3700fa82` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/inmemory/MemoryStorageBackend.java` |
| N013 | main-java | 26 | 685 | `b46c2c9ec6a77c5105264118c2e1605dcdf2c6ca223cccda13000c2d85a264fa` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/Cursor.java` |
| N014 | main-java | 136 | 4038 | `b218d5cd123855eece05415003c4aa80a321e8f8d56627246547774ffa6a4ee9` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/LMDBInterface.java` |
| N015 | main-java | 7 | 190 | `aec6fe15b288c433eb00dce64563458350627f8859a8d55ff97f72ca982e637b` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionCallback.java` |
| N016 | main-java | 5 | 148 | `00821d8d2e87c72ce49cf8b3609499810d9724c46ec032e3ee2411ff067977bb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrappedCallback.java` |
| N017 | main-java | 77 | 2684 | `5dbce1e841680a5cd1baf9f7443448dcdded881828a5aa6c95cee7b0761f3d8a` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/lmdb/TransactionWrapper.java` |
| N018 | main-java | 35 | 1172 | `851323405c18a4ce9ed2f0b51096267155f8df967761ce70118bdb9cfb08f8fb` | reviewed | - | `src/main/java/me/cortex/voxy/common/config/storage/StorageBackend.java` |
| N019 | main-java | 117 | 3948 | `f81142da3322e9aaa3b03e9d61ef92cf567ddd8a4e269d657b9fd2caef79f5b7` | reviewed | - | `src/main/java/me/cortex/voxy/common/Logger.java` |
| N020 | main-java | 184 | 7139 | `3501b06051e8f52c431b4503c13313fd12b3fb1282dfd4b26c4c5381d623e916` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/MultiThreadPrioritySemaphore.java` |
| N021 | main-java | 160 | 5434 | `0f524bacf654ad31253df0e52aece1778e02907fe2b56da5fe4e14a1b6b89774` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/PerThreadContextExecutor.java` |
| N022 | main-java | 100 | 3029 | `bd5682ce04350577795eae802dedf47b2078ac28f9e332942194ecbbc7a0363e` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/Service.java` |
| N023 | main-java | 184 | 7065 | `76eadac68f96ae766bbfb5d1c1ed1d55a08b28819ae03685ad128a3377298322` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/ServiceManager.java` |
| N024 | main-java | 123 | 4171 | `5e6bd09b313fb1a574f222828440f6f48984a71532619717f04b778703ab47b3` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/UnifiedServiceThreadPool.java` |
| N025 | main-java | 128 | 4351 | `6a938c7d6d4d9b12afd18b9ea93f1b8cf82bc05727c5753d6a8dd293c0f808af` | reviewed | - | `src/main/java/me/cortex/voxy/common/thread/WeakConcurrentCleanableHashMap.java` |
| N026 | main-java | 210 | 9168 | `ef2776146f51bb541f43584d94aef2ead653a49fc2a458a3292726859fade766` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/AllocationArena.java` |
| N027 | main-java | 31 | 766 | `9ab123ce4339429624d58311298bfbfba9661f9b4948c96234a518db684609ef` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ByteBufferBackedInputStream.java` |
| N028 | main-java | 171 | 6454 | `9e0b29ca73bdc70c39466b9ea016d71a5337c2587cc18ae9d06750a49f9b9df1` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/cpu/CpuLayout.java` |
| N029 | main-java | 7 | 166 | `d3954ade0b9a5bd1671914cae96eaa46610b1ef1bf8c332c8ffaaffe83539608` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/GlobalCleaner.java` |
| N030 | main-java | 299 | 9514 | `b143146a0ff445a4c071cb2af06c7d077e25559c6fdf6a69db275ba27555e883` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/HierarchicalBitSet.java` |
| N031 | main-java | 122 | 3795 | `8c28ff58a2263af5720c1c458543591d8eea2697d20e31c3e66b8fbc371586d5` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/MemoryBuffer.java` |
| N032 | main-java | 6 | 91 | `7fe791ad710881dbb99ed4f23a50743501874a5349af83a838a2002d79075de7` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/Pair.java` |
| N033 | main-java | 37 | 1388 | `86bc4a7f4dc76689c9ac3e6a5326edf64bd7752462b45b4dca79db5ade231ce4` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ResizingThreadLocalMemoryBuffer.java` |
| N034 | main-java | 27 | 850 | `d20a323f96aef9075f2e171619bdecb5d3531dc80486c48843f237d45cb8502a` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadLocalMemoryBuffer.java` |
| N035 | main-java | 118 | 4579 | `2dce169df903ab7130cb7a9ec5b37094dcbba7a13251b23d33bd8a44dbe893a0` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/ThreadUtils.java` |
| N036 | main-java | 96 | 3045 | `fb73e6b34e24077dfed06ca7d9c8b82ec598f0fa45dc105c34f6d2ca6b85be07` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/TrackedObject.java` |
| N037 | main-java | 74 | 3443 | `0fe2313ac6f8c55cb9804a2efbdf17ea56236cd2e62d6780507f154c954de576` | reviewed | - | `src/main/java/me/cortex/voxy/common/util/UnsafeUtil.java` |
| N038 | main-java | 5 | 127 | `7a3956927fc2783b65035e7dd2f76271a3e069a1457e7f3501cb65421ae0ece0` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/ILightingSupplier.java` |
| N039 | main-java | 57 | 1649 | `df6837d023bdedc997cd439144fac3af83ab2534a20cea97dc984dd81a86efb8` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/VoxelizedSection.java` |
| N040 | main-java | 219 | 10022 | `c3f961f965e69501ff70d59e229668cbd396c274a58c38cd4154e3b95675cc75` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldConversionFactory.java` |
| N041 | main-java | 77 | 2841 | `b7dc8611031585849b2189411496565d5c621990360a7337739ccf7816761a46` | reviewed | - | `src/main/java/me/cortex/voxy/common/voxelization/WorldVoxilizedSectionMipper.java` |
| N042 | main-java | 401 | 18014 | `e0ac18566fbafcad7087f9911ab4bf6b626b05fcc0da73e7c24e58b70a6894ac` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/ActiveSectionTracker.java` |
| N043 | main-java | 460 | 18654 | `fb9ad4336baf4d7fcd6072b7baafc98f0deefa8afb6912e3422bac48fd811400` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mapper.java` |
| N044 | main-java | 83 | 3836 | `12fc48d7f3968c3d078b408aea5689cb08d398bc7376dc146ae6ceb39c5ffae4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/other/Mipper.java` |
| N045 | main-java | 126 | 4826 | `2ca07119fd09514d8b6c2c2f05db6b929c45041822afcde05c411b9525ac4aed` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/SaveLoadSystem3.java` |
| N046 | main-java | 98 | 3979 | `23ac84019f68fe0764fd6d4feabc4b68fa2e635a7c01a1a796f3db118dd865ce` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/SectionSavingService.java` |
| N047 | main-java | 466 | 21355 | `86fa626e90efec0fd5fc973357cc02d9d9ee67c15498312446a997f4c55f2bd4` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/service/VoxelIngestService.java` |
| N048 | main-java | 218 | 8557 | `a1673cd263b10ab2978ef7c867fb49dc122fa9295693b719fc48ac44455788bf` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldEngine.java` |
| N049 | main-java | 341 | 11880 | `3f03daa74c527a43faad76005f72e1722ac2364bdb983cb0801fa1c5e386aaa6` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldSection.java` |
| N050 | main-java | 153 | 7419 | `e6a3b882fe555d20de39b3f06abaf4312b5c4991e21eaaac74cb058cd48f637e` | reviewed | - | `src/main/java/me/cortex/voxy/common/world/WorldUpdater.java` |
| N051 | main-java | 15 | 452 | `cb25bc9588617d071580a3530d18ccd60ae07faa00c9afb04d867e8e80e1db6c` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/importers/IDataImporter.java` |
| N052 | main-java | 126 | 3867 | `16699c3b1ebacd34e47a2a67872cf3d140c19f3f973c5a65fab9713d4a9e8f42` | reviewed | - | `src/main/java/me/cortex/voxy/commonImpl/ImportManager.java` |
| N053 | main-java | 75 | 3966 | `cc0051ce43bc4b78c384c7e52c4783705ead289bc4f317d3a78304c5dc32cddd` | reviewed | - | `src/main/java/me/cortex/voxy/config/ForgeVoxyConfig.java` |
| N054 | main-java | 949 | 40778 | `2c06c745cef5ff1fa79f13276b5552c52cb84e76c91f20fd4b497ce15f24bba1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/AsyncNodeManager.java` |
| N055 | main-java | 208 | 8806 | `f0b0c6f39587daa70fc17cf6888e20d104b4fd4df9d374293c8ace74f68e85a2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicAsyncGeometryManager.java` |
| N056 | main-java | 290 | 12783 | `6a9a732b05dbe46b8b69685c3f212f4e8b564c71aad95d62b6433480f81f4cde` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BasicSectionGeometryData.java` |
| N057 | main-java | 71 | 2250 | `6a4a4c6649148e4cfade33783f291c4cb3e0f8549695dc1dcb84bb9e75e4b3ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/BuiltSection.java` |
| N058 | main-java | 237 | 10641 | `237211e080f5ce86983c26b5e50bb8e6bc62284fab39776c9f82dc34b333e7ef` | reviewed | - | `src/main/java/me/cortex/voxy/forge/Capabilities.java` |
| N059 | main-java | 473 | 20361 | `e9e01deeeb80b34160734ff582c1a4d4edba6759d10beae3a804a98a2834c287` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ChunkBoundRenderer.java` |
| N060 | main-java | 65 | 2680 | `17e454f2e856e5997ac37dc7f4d8a6af6489a50de5303b91ef397aa01e6a9f3b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ClientImportManager.java` |
| N061 | main-java | 34 | 1064 | `6398493a88fc1c953498f98f0ce4e8af28f9b975b8e1ac026de7e290a4edccba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ColourDepthTextureData.java` |
| N062 | main-java | 68 | 1967 | `d16f383a2e5bb5fb3c2b522ee11bc3089428a2b5ea8d01fdc8507cde86b24d90` | reviewed | - | `src/main/java/me/cortex/voxy/forge/CompressionStorageAdaptor.java` |
| N063 | main-java | 142 | 7336 | `fd97389e60efe8d4dcf18182a96f954e5ac87523f15e57d5728ba3a8b8df5e35` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DebugUtils.java` |
| N064 | main-java | 122 | 4727 | `f79fffb818bb3d1e903798a44b6a234502aedecc5db1e8048142196204ef80fa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DepthFramebuffer.java` |
| N065 | main-java | 227 | 8858 | `9cafc7a205a66ab06fb083d27ae043de9223515db639f5afa2e2bc2731baf289` | reviewed | - | `src/main/java/me/cortex/voxy/forge/DownloadStream.java` |
| N066 | main-java | 51 | 1650 | `ecc5b203b209c0afd1cf89cf57e4fe91c491cded81ab68ffc86d05d2f5a5cc64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ExpandingObjectAllocationList.java` |
| N067 | main-java | 122 | 4497 | `42ca1320fb49ffb2510c405f66502f1f02c9c1623eeb0dd13ed68497840c030f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeIngestRetryQueue.java` |
| N068 | main-java | 46 | 1630 | `c7a1f8f499895e07cb7f86057a06c756370f9d0665802687fca669a8255b3b36` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasLayout.java` |
| N069 | main-java | 18 | 538 | `2554b3963d84b5a46663854c036be0fb6c5bd47b8a3663d28871c6a4a90ab387` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelAtlasPixelFormat.java` |
| N070 | main-java | 14 | 467 | `3b63787f0451854f6355756553ab18afab3d99d38b40892ab40db631e86026e3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeModelStoreLayoutSpec.java` |
| N071 | main-java | 43 | 1466 | `a332ab4a11b2fc74545f237c129c5b6ff0e3b82ee90de25cb13e5d0d2da01d97` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusShadowStateBridge.java` |
| N072 | main-java | 72 | 2929 | `2779112788bdb1a224e393de42817f2cc5e095d860dd00df5dd56038c20fb000` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOculusWorldRenderingSettingsBridge.java` |
| N073 | main-java | 110 | 4130 | `ef55fac366f577fcdfba144e5ad4d647f23bf8e9fd43f01ab7e9686729b6c5bd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyClientRuntime.java` |
| N074 | main-java | 97 | 6683 | `8bd7af953296428b7dca27fa8c04aeca66080a735f2b87b51a864784e4ddea10` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyColorSRGB.java` |
| N075 | main-java | 133 | 5856 | `872e6d96b74968559a157dc0f66eca98ccc3aa273ece2539de6ccb0e838601f5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyConfigSnapshot.java` |
| N076 | main-java | 400 | 16172 | `fc1a24e9084b7c193bcdb158ca9b066ef1591abe6c7133848f7e2811b6747c02` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoder.java` |
| N077 | main-java | 810 | 33057 | `f29f890780a69effe69916b4520a2213803a03d7a35aa1db41463d9d4104ef81` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporter.java` |
| N078 | main-java | 277 | 15087 | `616ad197238873f49e0e3a7ec00dad04b5481d07a5fe610e83969a24ec294d80` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmbeddiumOptions.java` |
| N079 | main-java | 20 | 573 | `597f8595d71782843f7d0442c69c91ab3537d5d47c4ffabd597c47ceebbd6dd2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyEmptyVertexArray.java` |
| N080 | main-java | 57 | 2367 | `93e9b7950bdabad97909a3e90a43a929d41f08084f7db20bffcfb8b4e952a557` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyFogParameters.java` |
| N081 | main-java | 42 | 1321 | `512db69939009d08c0db9c41abe78a5417d796f9a71f33761987a21d25e7b9a1` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyGlResourceStatistics.java` |
| N082 | main-java | 9 | 221 | `f549b005591b2f403b8d9e4db3381b9b12d72861de744c82e2d4eb099ec1860d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelLayer.java` |
| N083 | main-java | 1295 | 59530 | `24a2c984de94364066e9a427b7aff269c871558cec0021a96ac966b5e0cef8b4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyModelPipeline.java` |
| N084 | main-java | 254 | 11708 | `ea121249d18cdf4704a9be718e4fda859defbedff293d542cda01c817b1da1c0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyNormalPipelineTargets.java` |
| N085 | main-java | 5 | 151 | `021e0070854f0bf30070abcd88b22b04e45d9cff4630a08ba4dce89d3463f411` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPatchDataAccess.java` |
| N086 | main-java | 117 | 5080 | `5f1efd1edb7aba52a92c0b1c1c4d5e5dd3f3ba1dad068833ca1c44325f61f9ad` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineBridge.java` |
| N087 | main-java | 5 | 164 | `59a84b9e27f1222e199d13ba9cc861741c1233f1bdd21d8774bdaf6b7448b885` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusPipelineDataAccess.java` |
| N088 | main-java | 1134 | 44457 | `b5e0d6d0c61ac21b660dd8db1c566e95422837958a5ab69c11ca74502890e1ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusRenderPipelineData.java` |
| N089 | main-java | 50 | 1975 | `cc02e64c41e6ff99ff804aa97c262261dbc5bef3a2ebadc5b10bbf54a8d0fb61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusSamplers.java` |
| N090 | main-java | 508 | 20285 | `c4faf0160a5504e658798a9c9c16654d61694079708eb11c334466ce8c2a6a8e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatch.java` |
| N091 | main-java | 85 | 3408 | `801bdfb5561e8087487f1a1adaaef3ce6546c9130d1739aa535d46a1ea040b4f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusVoxyUniforms.java` |
| N092 | main-java | 109 | 5092 | `172af6abf0c5e6dd6bce37b62272591330ef14b3deef0548a6d8ab558187b4e5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorage.java` |
| N093 | main-java | 284 | 12241 | `c44391f8c9af59b7dde02b9c00c0b5a650f9cc672251402fc82e87222a3e491c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyPipelineDepthStage.java` |
| N094 | main-java | 275 | 11105 | `7f722b58e35e34e1213df1b14d006b93259ef2e9c500e306b78d93dab77bd6d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridge.java` |
| N095 | main-java | 697 | 30207 | `f5dfbb4b8aa256d8624987ee0c20cd9146daf4af558bc088b414b765ae11b7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipeline.java` |
| N096 | main-java | 51 | 1594 | `2b1d00529f90978a4d08fe19ceb3c708dccb81fb12b0302f6fc03d14d746cc99` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderStateCapture.java` |
| N097 | main-java | 316 | 15485 | `58c34f411fd620803a8899e7d23e633202a863b1f72f26e3d415658c77bd93a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderSystem.java` |
| N098 | main-java | 90 | 3431 | `f10b4a5845b07022d4f715e1d4d05f2cad08abcbcf24f5f6e7ff564d9138e648` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyServiceThreadPolicy.java` |
| N099 | main-java | 27 | 859 | `5c79d54cda1d827e8c25879b4837f7ff27d39eeafc05ec02e6840ccd4ef166ce` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderCompiler.java` |
| N100 | main-java | 8 | 318 | `1c7dbdf001e4e46575e1ac23c3a8ae2c5276050d0837a91dd376c6407323fdb9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyShaderLoadError.java` |
| N101 | main-java | 436 | 16544 | `1290f7c8007af450e22cff290e9c66843aa190306a8356703907a1929e19863d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfig.java` |
| N102 | main-java | 101 | 3503 | `b8431d287244d8b2b9cda2c5c9360222a803a87ef091014a1fdafcf28dd24755` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeOriginalVoxyTextureBindings.java` |
| N103 | main-java | 487 | 21867 | `e0ffc97182dd9458512d0c91024ee0c8efd2bd5ab1b1b0ddf9b6de1139852be7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeSoftwareModelTextureBakery.java` |
| N104 | main-java | 50 | 1726 | `958d4b325bfdbee39dc9eba11152ce528aca0c11dc92d2ceb7cb43eab41618d5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVivecraftRenderPassBridge.java` |
| N105 | main-java | 36 | 1558 | `780c06126486845e628631e54f22a0490bee4e163f2b5df1b8583fe131b8b0b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyClientModEvents.java` |
| N106 | main-java | 294 | 13818 | `29e6e2b2d267606123e44fea1816310b4aaf507d905a12ec287075400018b03f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyCommands.java` |
| N107 | main-java | 986 | 40945 | `52da6d7678b54030b102a3094b081d329fdae5bca6c94383c7967336a5e12c1d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyInstance.java` |
| N108 | main-java | 23 | 756 | `2ec3dc737f64d3aab773313606808f3489af1e4b00fa9c58a7b8f440a75a5bf4` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ForgeVoxyResourceReloadListener.java` |
| N109 | main-java | 129 | 4415 | `8e1d6d44dd194fc5c05f3883a96d92ac04f8cdb1c141dce30726697e7324d427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FragmentedStorageBackendAdaptor.java` |
| N110 | main-java | 137 | 5439 | `e6da5ac70f801d96b1d6fe58ce9b3e542725e16fc730f9fd567519551ddc3f31` | reviewed | - | `src/main/java/me/cortex/voxy/forge/FullscreenBlit.java` |
| N111 | main-java | 62 | 1802 | `b0e25481db4ef15c11cd24cecbc4d3e67e803d28b09453bbd38d60272c1e1e61` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GeometryCache.java` |
| N112 | main-java | 107 | 3002 | `ac7712657d46ce0806a0b7d1d11c07efc6c07babc5e8c212d63367c42be8fd1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlBuffer.java` |
| N113 | main-java | 38 | 1073 | `ffa07094a7ab0e711d55a66761dbe27c616faadd6a959ac14ee97119639d3c2f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GlDebug.java` |
| N114 | main-java | 365 | 14993 | `8c4bc169f2077e592d8933429744ba2793ea89b02c8595ce0e36c86bdeb055ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUSelectorWindows2.java` |
| N115 | main-java | 166 | 5925 | `bcdf4d4e8d48a01f5c6ebcf8b0e24272c2baf22fd89da3963e351d35183c3776` | reviewed | - | `src/main/java/me/cortex/voxy/forge/GPUTiming.java` |
| N116 | main-java | 457 | 21544 | `fe18ed936de5b24d1ad3403ef3289664204f8c362d3f19fbd9fad8adb6bf5a57` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HierarchicalOcclusionTraverser.java` |
| N117 | main-java | 236 | 11171 | `86dab708015aa67532a967af4303d4b8dde15336cc1fb93e67a5ccf152fac3a5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/HiZBuffer.java` |
| N118 | main-java | 11 | 315 | `8238969a454f6fe0daba41abebd4b3e931d9562bba48d55c630ec03d49571caa` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ICheekyClientChunkCache.java` |
| N119 | main-java | 14 | 357 | `79e4d08137e62329ca48aff63496aad665458db0027a441ecf0fe401f24d393f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IdNotYetComputedException.java` |
| N120 | main-java | 23 | 675 | `a1c0509d08a05a025d01258adaa20c985c05b2650afbd8bb5074743d420ec779` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ISectionWatcher.java` |
| N121 | main-java | 6 | 201 | `aa843dbb5d548a22badd852cecaee8eeeac244ae22fa8ea5523d3fa91867a7ae` | reviewed | - | `src/main/java/me/cortex/voxy/forge/IWorldGetIdentifier.java` |
| N122 | main-java | 168 | 6305 | `c849ad573e3f825d279316c4264c2620a9e712290f85e61d7014d519ceefc2b5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LMDBStorageBackend.java` |
| N123 | main-java | 53 | 2029 | `b75c5e4d5aafd960913f698570738402c1a72b7763297bf9889ee14d93d1e42d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/LZ4Compressor.java` |
| N124 | main-java | 801 | 37244 | `cc863364c24c4b57c8f3b81edc163f0cddad0b5c529a0b569b408e86866383e6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICSectionRenderer.java` |
| N125 | main-java | 166 | 5469 | `e4a8f6f41116baad0e5718736b9a3fff7f6eea874cbe8dc6ac84b9c36b74e10b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MDICViewport.java` |
| N126 | main-java | 275 | 12670 | `3689bd132c4363975304f7e3766d74602f836695dccb66b2cfc92274efdea559` | reviewed | - | `src/main/java/me/cortex/voxy/forge/MipGen.java` |
| N127 | main-java | 26 | 1109 | `509da1db71421243683714089da1ea963adac1ef1d8c8c85f298a73ddb4146e9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyAcediumRenderPipelineMixin.java` |
| N128 | main-java | 55 | 2262 | `8548c5c7b7e51aee2f14c16128c82e599ca69fcaf1157f44e595ddd41247a108` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyBlockableEventLoopMixin.java` |
| N129 | main-java | 52 | 2158 | `10c88b85f04ebe0cdcb4ed53d13400be9033217ecc5716f0cd434f8141ad85d8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyChunkyForgeWorldMixin.java` |
| N130 | main-java | 48 | 1962 | `4d140a7d23e6faf252e978cc0fd01c1c19bea8cd6ef1fcf2eb3a6cf71e54abf5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientChunkCacheMixin.java` |
| N131 | main-java | 94 | 3552 | `72e41635d8e60e870130bc9b6ba1b9803061ff2f8c44e5e360841daf7b34908f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientLevelMixin.java` |
| N132 | main-java | 23 | 1193 | `9b8d2485d551a1903a522c4be11f03d7815439034bc37347f7ec7c0c7112e553` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyClientPacketListenerMixin.java` |
| N133 | main-java | 38 | 1597 | `4155276d6315a9af5603c927268559a0e054843fb88772614a5d0bbbe13a3f41` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkJobQueueMixin.java` |
| N134 | main-java | 12 | 450 | `9c46352d3b576b062b037a01c1187e8c36b882a106b3fee674ef98b8d1372cb5` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumChunkTrackerAccessor.java` |
| N135 | main-java | 44 | 2100 | `891c5a7634b4d7b1f7b8bdd54ad8c379031654c825ffa86289cf1ff3fd990dd0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumDefaultChunkRendererMixin.java` |
| N136 | main-java | 151 | 6540 | `b5d33859a0d91975428bcf3dfd4440c607c87c6e5a3e2f2b0bae261d2ad32b5a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyEmbeddiumRenderSectionManagerMixin.java` |
| N137 | main-java | 79 | 2888 | `6d84cf1508a1a945e18719598c2235b4e5078f759cf00aeecba3b2b9d999df64` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGlDebugMixin.java` |
| N138 | main-java | 34 | 1737 | `38d0b1b58a3d58964ef648cc0391f58495056ee29b31e53662e52e13fc9413ee` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyGpuSelectMixin.java` |
| N139 | main-java | 68 | 2924 | `b887027ccf7544a5f4c36d3fd6fa31b271780d67d219c4879db6bc69bec3bdd8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixin.java` |
| N140 | main-java | 18 | 789 | `940f1645e5c967b32088b5dcf0bc66018df1dbe46f002bf4676759dcb67591ea` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyMinecraftMixin.java` |
| N141 | main-java | 15 | 527 | `4597ea92bd0d255a2e921e2a7ded9382400173314898c5bdfedd3c0aa7d96137` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusCustomUniformsAccessor.java` |
| N142 | main-java | 29 | 1314 | `f690857bb98ab8eeab06afb448ebe9f6041cefff90707bcc400fc5a943326e6d` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisMixin.java` |
| N143 | main-java | 12 | 447 | `59bfa669ebf74c2ec10a8fb0b3c7a996c7bb476c152f0e799a9f528f0bf7aa0c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineAccessor.java` |
| N144 | main-java | 57 | 2433 | `e83336389dc0caed539f25b58d5df494915245435fff54f46ea7086d78b1fdbf` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisRenderingPipelineMixin.java` |
| N145 | main-java | 31 | 1306 | `fbd905bf76ef3fc3d8b1db9404a9c5855432749e70f341b1c3ac6ee07f1125ba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusIrisSamplersMixin.java` |
| N146 | main-java | 28 | 1243 | `bbd72ba62d7e5fc9d18f93e597d8b5b41301536ff6cf895bd2dd4f1f811668e2` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusMatrixUniformsMixin.java` |
| N147 | main-java | 84 | 3625 | `54569e8cd2890b5b8c8c8d286fce5f796a1f6130032b6a4ea73bee2548a26eba` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusProgramSetMixin.java` |
| N148 | main-java | 52 | 2067 | `8feb2d0c491bed98ad47b91bc184e11927a6c8ec29caea3d0090816be56a680b` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusRenderTargetsMixin.java` |
| N149 | main-java | 22 | 992 | `ffd8c5c05b7b8b6bd21327aca1ca53bd526bbae283342eb2dbf86b6606902e24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusShaderPackSourceNamesMixin.java` |
| N150 | main-java | 33 | 1474 | `4741232042263de96ceb53ca939a41f76a4df240c33e3ee96f344a8bdfcc1425` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusStandardMacrosMixin.java` |
| N151 | main-java | 16 | 720 | `b702fcf587569665be1c19fcabc0649f440510a1c6c132d0b95b4ae7da457b88` | reviewed | - | `src/main/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyOculusWorldRenderingSettingsMixin.java` |
| N152 | main-java | 155 | 6200 | `a54fdab1ddade3a50f96d183ddfa70d1daadee78f68581f622ca87a69437d335` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelBakerySubsystem.java` |
| N153 | main-java | 1179 | 48455 | `d91bebcf6ea13a430d93bbb087a6772c1e86c37c142332d6f886436b948d3e94` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelFactory.java` |
| N154 | main-java | 79 | 2141 | `25dfbe13df4c008d4290a8a8a18134d4afe82683cd7fe4da373728828f657e48` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelQueries.java` |
| N155 | main-java | 308 | 13576 | `229a53634c802b2d40d7a9dfbaa4c17e017d4e05a024a77c063fecd746f394a9` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ModelStore.java` |
| N156 | main-java | 243 | 10012 | `01d6bc74d090bea2dfd6cd02b3f25e81818cda1615575949a15fc5f4ee810415` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeCleaner.java` |
| N157 | main-java | 1354 | 60725 | `b0b63198dd21b5018437067b47922771b79145aee02198f31cb9b7c5c767912e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeManager.java` |
| N158 | main-java | 303 | 10077 | `0821f81b8295c8e0e7e7a5e14a28c98c2771082932b16ec5b42134ec5d98da1a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/NodeStore.java` |
| N159 | main-java | 90 | 2849 | `8d75323b969a2c68e3d9e85c0a9c41de398cfbfdaacc9d8bb8e37b7911a3cc04` | reviewed | - | `src/main/java/me/cortex/voxy/forge/OccupancySet.java` |
| N160 | main-java | 274 | 10076 | `60754f2b6cf3eb2d9b6603aa1fb79d1834e791bd018b35b1a3045b49ef616a5c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/PrintfDebugUtil.java` |
| N161 | main-java | 69 | 1916 | `f4adbc9e5b2069372fb13266d60718bc85831f812aad5321f453fc66a5968154` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReadonlyCachingLayer.java` |
| N162 | main-java | 139 | 4512 | `f4f0dc720ebe5d3e38b5d901c607ed3f85eb0efaae8eb7ac0eea8610a1d2faf3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RedisStorageBackend.java` |
| N163 | main-java | 1829 | 80032 | `9914b7ea29d4911b112a16a1165f2f88ca31ca2f808e72b70518a837898a6183` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDataFactory.java` |
| N164 | main-java | 65 | 2184 | `2185fe5fa9f16ca63b5ce16d6338be33f5286bf71550af04cab89e4a6860b014` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderDistanceTracker.java` |
| N165 | main-java | 337 | 12791 | `c6fac7af71c0ccb7b85778948d776531316c81cc449c4728d5c3eb1465ff7070` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderGenerationService.java` |
| N166 | main-java | 50 | 1438 | `78eaccbc88c8d1dc9c1d2caf88e652bf12707e1fbd902f1b4f2102e74cca3552` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderProperties.java` |
| N167 | main-java | 70 | 2827 | `cb4e57040b920edf891802fe9463830b03bc99ae3dd9061513879fff917432d0` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderResourceReuse.java` |
| N168 | main-java | 42 | 1412 | `477b66c7594132171ab5d8038ed41c943c9f07ae2359007b7db5369ba8761427` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RenderStatistics.java` |
| N169 | main-java | 184 | 5320 | `b422c16695e323835cfcff743f4a1476e5e43ee7f5f34d69a0e50a3a196c7ee3` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ReuseVertexConsumer.java` |
| N170 | main-java | 190 | 7020 | `1ef88d276d256c6d73ef8fe98b2f4173860db8534fa7a81e9cf62c9a25f56f9e` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RingTracker.java` |
| N171 | main-java | 219 | 8634 | `554f70c77ceaee268beda2054133bc159734f577c1b03fbc74c2fd4c987dbeab` | reviewed | - | `src/main/java/me/cortex/voxy/forge/RocksDBStorageBackend.java` |
| N172 | main-java | 110 | 3639 | `6be0b68330f4eb54f7edd9eb67f6a73308dde27c98dabfbc0e604bf043d454b7` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ScanMesher2D.java` |
| N173 | main-java | 158 | 5736 | `938fbc7d078b4ba1a2967a4b714075232f1e4fe46e571b173a170a719e216da8` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SectionUpdateRouter.java` |
| N174 | main-java | 68 | 2428 | `56ed4b232ec6dc0307f134abb14647a587a76940169cf87d142a8dbed2cc4d6c` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ShaderLoader.java` |
| N175 | main-java | 210 | 9082 | `5554c81113f60a1c8d9bbe41423343a66abe9c489e2e137813267e9d7fceb216` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SharedIndexBuffer.java` |
| N176 | main-java | 250 | 9511 | `0e1d3a1266e1d7dc06a36a34ac76cc0dc47583d1ce562bbd7a45d19a2ff92981` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SoftwareRasterizer.java` |
| N177 | main-java | 254 | 10712 | `29a0ed676cec34a81071fee35d72cc4cc983b88670fb60b640646cfb2165bd0f` | reviewed | - | `src/main/java/me/cortex/voxy/forge/SSAO.java` |
| N178 | main-java | 228 | 8327 | `2363213cec707f3dced463745998075ab5adc38c1160106ae5d1971b56caaefd` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TextureUtils.java` |
| N179 | main-java | 90 | 2809 | `fc4d0c33f996ac4c14ff042a6d1301112489286b8c903f50b5714d9658c02c24` | reviewed | - | `src/main/java/me/cortex/voxy/forge/TimingStatistics.java` |
| N180 | main-java | 271 | 10099 | `6718cfaf91fd53954a3a8b80abbaf3edb3957ac69327bd1825f076fa3b5df101` | reviewed | - | `src/main/java/me/cortex/voxy/forge/UploadStream.java` |
| N181 | main-java | 67 | 2300 | `36a2be3474a3fe6ff3eefd3ae32fca1bbb526ecb852f6dbe83d3b80c0342e853` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ViewportSelector.java` |
| N182 | main-java | 27 | 1024 | `8da7b1af202c9bf7efc9f844597d0469137eb1e2caff1dfd0b84b908c61da057` | reviewed | - | `src/main/java/me/cortex/voxy/forge/VoxyForge.java` |
| N183 | main-java | 129 | 4589 | `e415f5dc7add30d2fdcc848a46c640ebeb9ce936817d62dd428b15a79b19d7b6` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldIdentifier.java` |
| N184 | main-java | 491 | 20524 | `715e805d6b0d6d195b147b43c957c6f8d430bf250027446eba3c9f7b6ecbe69a` | reviewed | - | `src/main/java/me/cortex/voxy/forge/WorldImporter.java` |
| N185 | main-java | 73 | 2701 | `753aa480c120b763996577a0e4073189fc46592e57b1cbee7084fbad39365973` | reviewed | - | `src/main/java/me/cortex/voxy/forge/ZSTDCompressor.java` |
| N186 | binary-resource | - | 61710 | `8d18c38b1088fecb4ed37e6557eee6c4eeaa9a30cdfbf9adbc515bba0a818370` | reviewed | - | `src/main/resources/assets/voxy/icon.png` |
| N187 | main-resource | 36 | 1911 | `c03d95179170decdab9e66e90f1fd5050422c16633b0f8f43c8067570e9d521f` | reviewed | - | `src/main/resources/assets/voxy/lang/en_us.json` |
| N188 | main-resource | 4 | 69 | `96378a707bba140fe6e2d95f87b3465120986f066b28478360fde15dfc3989e2` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.fsh` |
| N189 | main-resource | 62 | 2076 | `521ea11b6f6d597d0d29bcf6c9b27a460586ebf79117e4cbc5c6aeb2004df6f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/chunkoutline/outline.vsh` |
| N190 | main-resource | 25 | 753 | `cfbc060e5b5fa20000c1202aa0d59b97081e1cd5ca977fff38320426befb5d54` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.fsh` |
| N191 | main-resource | 10 | 253 | `aa83c83d36b32197fe99bf7ddbe1fb1f7b33dbd39d56cbeefc436a0db3d6cd62` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/blit.vsh` |
| N192 | shader | 93 | 4196 | `73ed1046979b74e5ceffd78f426beea42aa778f58b134e5d012f3351592feb58` | reviewed | - | `src/main/resources/assets/voxy/shaders/hiz/hiz.comp` |
| N193 | shader | 44 | 1244 | `37152f4339854a2fbcfc3b917ddca028f452b9a3c2ff63c53137a1bdf195837e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/block_model.glsl` |
| N194 | shader | 14 | 684 | `44e30b1d078f65af5b756501184be5e082a577179e6a521d3195f5d51c002d1d` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/frustum.glsl` |
| N195 | shader | 94 | 2488 | `f0f2a24ae9b53f9fcd846ebe3a7c6a89e4edba6cf64294d658c2e314822bd796` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/bindings.glsl` |
| N196 | shader | 51 | 1634 | `c4eba68e3a67e1afee246bfe8153581dfcd0ae179173415855b84366234e8036` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/buildtranslucents.comp` |
| N197 | shader | 221 | 6906 | `da341df71b716e599c2e828b0908cdfbfa5c64883ed8837ad5c2916a95eb509e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cmdgen.comp` |
| N198 | shader | 16 | 407 | `67bde88bb0613dafcb867415fc5dcdf966b6ff87e74866755759495c4f6810c5` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.frag` |
| N199 | shader | 61 | 1850 | `72f12aad2289f2134db4f632a145d2bd54d5fc15396146d4fd2ef8128a80c707` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/cull/raster.vert` |
| N200 | shader | 24 | 633 | `b5bc80a8eaa774bebe9b010ff758467432394540ef349f8bf21bf5262be1a2f3` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/prep.comp` |
| N201 | shader | 252 | 7291 | `bf54e6e0a6a2cf8d1a281894454a80c1c95b0c15342e8b121f9165025af01e8c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads.frag` |
| N202 | shader | 84 | 1899 | `8b2ccddc35249cc6b2a2d8bd5d6153915a624d5bf2bf15249db05a2ed10afc7e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/quads3.vert` |
| N203 | shader | 7 | 236 | `848616913d003afaf814b14aafc66cd679a2e51c5373d8b9001c8b7de4a38e1b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.frag` |
| N204 | shader | 97 | 2910 | `505feec7e1e9005adb853ad52f54bc4a64616a44fedef48fb6fbf5bea8111e9b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/gl46/test/raw.vert` |
| N205 | shader | 22 | 584 | `11b63cbef198ddd074f20d29506dac4c4288e39a5f00c09f7fc7bc7f1269086e` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/batch_visibility_set.comp` |
| N206 | shader | 35 | 1188 | `de2bdaae738748988f7467e5cb5b9d6f3f3a23c287c0b2da2089a9cdda402359` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/result_transformer.comp` |
| N207 | shader | 180 | 6694 | `05474ab6a099ac1abcd00954af54f75bb728d7312c609b3d0a2ebab3ee2de127` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/cleaner/sort_visibility.comp` |
| N208 | shader | 7 | 142 | `452b66c0d22dcc8b02c114f4ddcd9d617c3cb78707f24b5ac1b8cb50123f1341` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/frag.frag` |
| N209 | shader | 40 | 1157 | `08096970d22f373ebc4fe541317ba10028d1d03fb49d1413027842d273d8342c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/node_outline.vert` |
| N210 | shader | 24 | 488 | `e9a258ade13d6959c593d06dec6a3d3d8cc19c438c55bd9588fb6777bb5ed3b6` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/debug/setup.comp` |
| N211 | shader | 103 | 2698 | `bdbed744c86e0a4da4a4f3b957d608e0ccb449968044014dc3a8f6ba900bc1c1` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/node.glsl` |
| N212 | shader | 57 | 1963 | `8506807ed1e117f8b3aabf31b71e01724357bf7a6d911fa787a13efb7e52655b` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/queue.glsl` |
| N213 | shader | 198 | 7251 | `0d7db9f232e754f49dd54373f245e3aeaddd11cc7b6d54277f12783153b28eea` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/screenspace.glsl` |
| N214 | shader | 192 | 6618 | `f333da914b00d9484c7b28a84a0748b9ce3c1bf25c27bb391282ca6d78756528` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/hierarchical/traversal_dev.comp` |
| N215 | shader | 19 | 479 | `37ad29a1c5076a517afe29501fbce8a665a6c455b84cdf749bf4e0c4b80e6043` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/lighting.glsl` |
| N216 | shader | 18 | 389 | `270f8609da93823f8810c41137d40d1aee93483c2e24e24ba5c5a9d70f0f69a9` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/pos_util.glsl` |
| N217 | shader | 79 | 2035 | `8c0df5edc2ca49b78bbf8b35453274694a1a0b1845c77858ee9c2a4d1d7d471f` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_format.glsl` |
| N218 | shader | 189 | 5786 | `3733548a4cd632adc435fe80c2f40732cdbac530ab69d9bbce874b8bed604227` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/quad_util.glsl` |
| N219 | shader | 41 | 825 | `95a7f1b5221e4dcf7c1a7d909dd968ead041f9cbc974d8e6009b31ddf64db12c` | reviewed | - | `src/main/resources/assets/voxy/shaders/lod/section.glsl` |
| N220 | shader | 12 | 184 | `8476a8946ffc96290ef19fdacbd9bc63d3bb256414ee80c8f8cbd9080de4caf2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_cutout.frag` |
| N221 | shader | 61 | 1763 | `dd67297cd6b68f18f3548832d63d56acf251366683f750debd060aff034fb865` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/blit_texture_depth_cutout.frag` |
| N222 | shader | 9 | 206 | `69adea29c8e11af186cc16efa3eabe03cae4f55723766e58af845402874678f1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth_copy.frag` |
| N223 | shader | 8 | 158 | `22dcd955f3dbecdcbf33c4c62772e09a086a08e180b40b6f822a7527423469d9` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/depth0.frag` |
| N224 | shader | 9 | 233 | `04d1078acd2cc34a62810add8186d17ce5d16cc8959c2bd603425890838f1ed2` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen.vert` |
| N225 | shader | 8 | 204 | `5978fa668c5b6df49d3f17ad8fd949af7bf769afb207c79a4e88e28d4a6c96d1` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/fullscreen2.vert` |
| N226 | shader | 6 | 95 | `bf5e7bdcf0acca77676589fa04a2aa96c697c860b7d799350e25ab58ea62b9ac` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/noop.frag` |
| N227 | shader | 14 | 292 | `7a1497f85c5b71e62cc791524a22b27b54c52eb4546ead1a2f840a2918c4c8af` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/setup_stencil_depth.frag` |
| N228 | shader | 277 | 8287 | `0a75dfacc511ffa5991cdbbb026e24c0423d408c5ed725bfb03154bb8145d5ff` | reviewed | - | `src/main/resources/assets/voxy/shaders/post/ssao.comp` |
| N229 | shader | 67 | 1352 | `a4f63aa0576eef6c70f6dc60355aeb78c62535f19f5c6cb29384c9e982096072` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/depthutils.glsl` |
| N230 | shader | 31 | 1005 | `be0ac2cf2bd4d995a1b92f8aba37eb8dfae6861d4a9276eb3e4947a7aab00dfd` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/memcpy.comp` |
| N231 | shader | 124 | 3602 | `a81fbda33705cde8109859de83cd13715fe069b44b3482f9483002416326c771` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/inital3.comp` |
| N232 | shader | 52 | 1121 | `b4354d8df395926f72c24f7c4fda3fef906193c70782eaec9842af0ecbcb8365` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/prefixsum/simple.comp` |
| N233 | shader | 40 | 1280 | `9d661b6c2a7d48eee40727fc4a35fe212bcd42a9472ba861476b6cf009222952` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/scatter.comp` |
| N234 | shader | 27 | 562 | `0bb1f8d5a037a53075745e6353437cf956613c90599b11c96978789fbe937563` | reviewed | - | `src/main/resources/assets/voxy/shaders/util/set.comp` |
| N235 | main-resource | 11 | 871 | `4e134135859596247d5f3abb57ed7f8d3ee59ae05aee3573f09c7a745a668f7e` | reviewed | - | `src/main/resources/META-INF/accesstransformer.cfg` |
| N236 | main-resource | 3 | 60 | `e7cfad1618bda0faf753447b98fdc1ba7394f1e1d1fe1bbe5f9559fadb6f67d7` | reviewed | - | `src/main/resources/META-INF/MANIFEST.MF` |
| N237 | main-resource | 43 | 880 | `056e6b46e3031dcf272205f357e7dd751bba9edfb13183018d5167c97698eae2` | reviewed | - | `src/main/resources/META-INF/mods.toml` |
| N238 | main-resource | 6 | 92 | `040cb4ca76ba91a8e6b98163a0fc742b5a1653e432d572c244903a4f83b5a1a3` | reviewed | - | `src/main/resources/pack.mcmeta` |
| N239 | main-resource | 37 | 1476 | `aea3a2d89ab5329121893a04654be9382cffd680fb81eb836acf32d1ce016425` | reviewed | - | `src/main/resources/voxy.forge.mixins.json` |
| N240 | test-java | 16 | 605 | `524055a0a7bd81d9630daa3b5fb085bed06919b2e4e0acd7d60970185efb7ff2` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/TrackedObjectFlagTest.java` |
| N241 | test-java | 102 | 3865 | `91cd699d1a72bf15d51bdb17bd2ae90616755968267cde0305f5e9d46472344d` | reviewed | - | `src/test/java/me/cortex/voxy/common/util/VerificationFlagPropertySemanticsTest.java` |
| N242 | test-java | 249 | 10029 | `01f94cca6c0271f62fb502785e3ed3155045c5d259a9914184149d7fdafc3b33` | reviewed | - | `src/test/java/me/cortex/voxy/common/voxelization/WorldConversionFactoryTest.java` |
| N243 | test-java | 39 | 1870 | `b01ba25243f8c32b1987d356c91bfc7d92ccc10e3e429e61ea431bb9fbd8ce15` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/SectionSerializationStorageCorruptionTest.java` |
| N244 | test-java | 37 | 1535 | `fd6ee311afe736dcb0c659c7b51573b578ff6c5437ed61ea9e2bbf014c09c0cb` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceBufferReuseTest.java` |
| N245 | test-java | 54 | 2319 | `e0d82f651fbfd3ef6fe822b966f8fa8de8726f48af37337c44c5a6093dbf0241` | reviewed | - | `src/test/java/me/cortex/voxy/common/world/service/VoxelIngestServiceLightingReadinessTest.java` |
| N246 | test-java | 20 | 668 | `02dddb9d6eae26bc34cfb5208d31ca4f367399895a2fccb55c095c07ecec7801` | reviewed | - | `src/test/java/me/cortex/voxy/config/ForgeVoxyCpuLayoutParityTest.java` |
| N247 | test-java | 45 | 2154 | `b9b163f94c7d358670098d9e4dfab2a0dc0c33bdbb1d7bfda240cd52081c3ecc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerStopParityTest.java` |
| N248 | test-java | 139 | 6903 | `75c7236fa24619b5d66014a2602a78d6164afb7fb08e9458f33579cbc14bfc36` | reviewed | - | `src/test/java/me/cortex/voxy/forge/AsyncNodeManagerTopLevelWorkParityTest.java` |
| N249 | test-java | 36 | 1388 | `95f60be27271dcdeeae782c68811663f04829e69682831b4bf5eb4cf15d9d890` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeIngestRetryQueueTest.java` |
| N250 | test-java | 32 | 1210 | `9283f3af89df614ca75b769f24ab5da853d0ab6f76aca1332842370cb973fa45` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeModMetadataParityTest.java` |
| N251 | test-java | 346 | 14912 | `e013d74b0dd9556dc7d557a009bab4b36f124cd74b1297c4d3457c8a6823bb91` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhDataDecoderTest.java` |
| N252 | test-java | 153 | 6857 | `4a0a42267f6a1e9e26d31b3548496ac2c252e046739b5f12891e9faa85a925a5` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyDhImporterTest.java` |
| N253 | test-java | 125 | 5014 | `c2c87e927817979d510e3c3c18a01c28c9c259ab92af0fe840874b34fd5fb906` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyOculusShaderPatchTest.java` |
| N254 | test-java | 58 | 2580 | `903e996b4bcfddc18a599643725ff15064666a7d4a1cac563d81c704747deb39` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyPersistentStorageIdentityTest.java` |
| N255 | test-java | 45 | 2387 | `3a0111ce61c4e1cc1a9f8a77f5fa1b7e43e51bba761dcbc558d53986a8eb6e74` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyQuadMaterialBridgeTest.java` |
| N256 | test-java | 29 | 1058 | `670cb867174123a1a3006c85fc9f21f0f5fea31fe3db6293843b7a01abd1a880` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyRenderPipelineResourceModeTest.java` |
| N257 | test-java | 101 | 3669 | `5826c89cf2d1a2db17c7db886aac11e57331137672399a44de33cf8501747d03` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeOriginalVoxyStorageConfigTest.java` |
| N258 | test-java | 117 | 5484 | `2833423f42bf75a42fcfcfcb015120a63d2c7e4586f4877706e8ddfaf2db51d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyConfigSemanticsTest.java` |
| N259 | test-java | 308 | 16616 | `0560012101b0163ec1ec2fdf07c16dd2a021250f31e6729e728f316deb9d7c95` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ForgeVoxyInstanceSessionLifecycleTest.java` |
| N260 | test-java | 54 | 2278 | `759260ee713c118b9dc3e19e70278736e03b6c1231985811abc8bdda253e0ee9` | reviewed | - | `src/test/java/me/cortex/voxy/forge/FullscreenBlitParityTest.java` |
| N261 | test-java | 50 | 1643 | `6572bf42fc408fd1f9a4d6429f13e10ec7b6fdd48615336e354458237f4d9eeb` | reviewed | - | `src/test/java/me/cortex/voxy/forge/GeometryBufferReuseLifecycleTest.java` |
| N262 | test-java | 25 | 962 | `24cc509762cb35e291396bd5eb711a9621c0b975c41aa8e3c05debbd6c06f0c3` | reviewed | - | `src/test/java/me/cortex/voxy/forge/IdNotYetComputedExceptionTest.java` |
| N263 | test-java | 98 | 3980 | `c9af6c1e38bc4ab5d816df37ba0dfe32b8e4019ba908539e17b346be9814acd0` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyDiagnosticMixinTest.java` |
| N264 | test-java | 61 | 2783 | `35b79ce1b86b292efc387ea8a8e1de3be2a5abed2d7d76b7870423d757eddfc7` | reviewed | - | `src/test/java/me/cortex/voxy/forge/mixin/ForgeOriginalVoxyLevelRendererRenderStateCaptureMixinTest.java` |
| N265 | test-java | 139 | 6639 | `d1bb2581285d425cc67d8e3c318a4e9adc41f1a9242e7694744d4575071473d1` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelFactoryTintSourcePlanTest.java` |
| N266 | test-java | 154 | 7402 | `f403616861f16696b9ade96bd3a12553b0141221f07e6c4ceab3e23a8ca49116` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ModelUploadFailureLifecycleParityTest.java` |
| N267 | test-java | 94 | 3650 | `2bd8be1565b4fb66766355329f2af17c6191478074b1a15d77e0a128f139b592` | reviewed | - | `src/test/java/me/cortex/voxy/forge/NodeCleanerShaderSourceTest.java` |
| N268 | test-java | 174 | 10846 | `528a76ddef099812d024fe34c82d681d4ab684ceebd86ae44915b28fcd13d0af` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalParityRepairSourceContractTest.java` |
| N269 | test-java | 199 | 11380 | `30690769dd485e9cacbedb142995570436f77ee2cdf810084d1983faafd5982f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/OriginalPipelineFailureParitySourceContractTest.java` |
| N270 | test-java | 97 | 4678 | `1d5c8fb7a1f22e1f307cd0ab752fb0239493dcf3223e1753f0091c7349a4dae4` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass2ParityRepairSourceContractTest.java` |
| N271 | test-java | 300 | 16330 | `1787c155143508099709dbb5344b9a3727eae14ce1ddfc5d108a0bdf991af9bd` | reviewed | - | `src/test/java/me/cortex/voxy/forge/Pass3ParityRepairTest.java` |
| N272 | test-java | 24 | 848 | `6fba137a3ff53f4868148f93fe91361542802d1897e23d8938221bb8c7fc24a6` | reviewed | - | `src/test/java/me/cortex/voxy/forge/ShaderLoaderParityTest.java` |
| N273 | test-java | 84 | 3156 | `66083dc283ed1240ae583a80d73954297bcc282c09ad188ef389fd6807e00b1f` | reviewed | - | `src/test/java/me/cortex/voxy/forge/SoftwareRasterizerTest.java` |
| N274 | test-java | 36 | 1302 | `98037363672b3a0dd31e6469aa40e6e81c9e7036c0608b3834a6328ddcff59cc` | reviewed | - | `src/test/java/me/cortex/voxy/forge/WorldImporterNbtParityTest.java` |
| N275 | audit-tool | 142 | 4948 | `8f9261194d179bb10683781218770d8cc2d4b09990e8a2961e241734ccea5480` | reviewed | - | `tools/generate-line-audit-inventory.ps1` |

### Pass 14 terminal zero-finding proof

- Pass 14 inherited zero Pass 13 review credit. All N001-N275 executable
  inventory rows were physically read to EOF: **275 files / 40,139 text lines /
  1,689,515 bytes**.
- Four disjoint clean-room partitions reconciled exactly to the frozen tree:
  N001-N075 = **75 files / 9,835 lines / 390,076 bytes**; N076-N113 =
  **38 / 9,811 / 412,336**; N114-N165 = **52 / 10,286 / 431,853**; and
  N166-N275 = **110 / 10,207 / 455,250**.
- Every Java file used CodeGraph before the physical read. Original Voxy and
  the necessary Forge 1.20.1, Embeddium, and Oculus ownership/API contracts
  were then compared line by line. Resources, shaders, tests, build logic, and
  the inventory tool also reached EOF; the binary icon was hash-compared and
  inspected at original resolution.
- All four partitions independently reported **0 new findings**. The final
  regenerated inventory matched all **275 frozen / 275 current** rows with
  `path / kind / lines / bytes / SHA-256` drift **0 / 0 / 0 / 0 / 0**, and no
  missing, extra, or reordered path.
- The indexed draw-buffer blending candidate was explicitly traced through
  the original Iris path and the Forge/Oculus adaptation; no Forge-specific
  contract deviation was established. Other apparent edge cases in request
  churn, printf readback, storage, meshing, and model-generation cleanup were
  either byte/flow-equivalent to original Voxy or required platform adapters.
- No source was modified during Pass 14, and no client was launched. This is
  the required complete post-repair zero-finding review. The forced clean
  static/test/JarJar gate is recorded separately after execution.

### Pass 14 forced static and artifact validation

- `rtk test .\\gradlew clean test jarJar --rerun-tasks --stacktrace
  --console=plain` exited successfully. The fresh XML results contain **35 test
  suites / 110 tests / 0 failures / 0 errors / 0 skipped**.
- `rtk git diff --check` passed after the gate.
- A final post-gate inventory regeneration still matched the Pass 14 baseline:
  **275 frozen / 275 current** and `path / kind / lines / bytes / SHA-256`
  drift **0 / 0 / 0 / 0 / 0**.
- The resulting `voxy-forge-0.2.17-beta-forge-all.jar` is **12,589,719 bytes
  (12.01 MiB)**, SHA-256
  `6685922c2a42a705635aa1cabb4fa0bc47e20034c774b045cd02456ffaf0a0c6`,
  and contains **458** ZIP entries with zero duplicate entries.
- The artifact contains the required Forge metadata, Mixin configuration,
  original Voxy shader resources, icon, and Forge entry class. It contains
  **zero** bundled Minecraft, Forge, LWJGL, Oculus/Iris, Embeddium, or Sodium
  dependency classes.
- The only packaged `me/cortex/voxy/client/**` class is the explicitly selected
  original `SemaphoreBlockImpersonator`, whose exact semaphore-block contract
  is consumed by the Forge Embeddium queue Mixin. No other uncompiled original
  client implementation leaked into the artifact.
- The seven expected JarJar runtime libraries are present; the largest is the
  compressed `rocksdbjni-10.2.1.jar`. This accounts for most of the all-JAR
  size while keeping the final outer artifact close to the original Voxy size.
- No client was launched during that forced gate. Pass 14 closes the exhaustive
  static line-audit completion rule; the later user-observed runtime gate is
  recorded separately below.

### Post-Pass 14 consolidated runtime regression

- On 2026-07-14 the client was launched from the required anchored repository
  directory with `Set-Location D:\Projects\voxy; .\gradlew runClient`. It
  entered the quick-play world `新的世界` with Embeddium and Oculus loaded.
- Runtime evidence shows the original persistent WorldEngine storage chain and
  formal `ForgeOriginalVoxyRenderPipeline` / `MDICSectionRenderer` route being
  created successfully. The run exercised shader disable/re-enable and repeated
  external-patch/pipeline rebuilds, overworld/Nether/End transitions,
  disconnect/re-entry, and final client shutdown.
- The user completed the visual regression and reported no issue. Targeted log
  review found no Voxy `ERROR`/`FATAL` or `GL_INVALID` event; every observed
  renderer teardown completed, followed by persistent WorldEngine close,
  network-session shutdown, and Forge original-parity instance shutdown.
- The client closed normally and `runClient` exited 0 with `BUILD SUCCESSFUL in
  5m 42s`. This closes the consolidated runtime gate for the Pass 14 worktree.
  The user's 2026-07-14 finalization request subsequently approved readiness,
  final packaging, commit, and push of this audited worktree.
- The final forced-clean packaging rerun passed the same **35 suites / 110
  tests / 0 failures / 0 errors / 0 skipped** gate. Its all-JAR is **12,589,718
  bytes**, SHA-256
  `50a27befcfb8d9390aac4db77ab76cf25afe9b4a1fa0aa30c554b7654a5b5502`,
  with 458 entries, zero duplicates, seven expected nested libraries, and zero
  bundled Minecraft/Forge/LWJGL/Oculus/Embeddium/Sodium classes.

### Post-XXVII formal-client compatibility invalidation (XXVIII)

- The committed XXVII tree and its 275-file Pass 14 ledger remain the complete
  historical static audit baseline. They are no longer a release qualification
  for the current worktree: the first formal-client run used the declared
  minimum Embeddium 0.3.31 and crashed before world entry.
- The supplied crash report identifies
  `ForgeOriginalVoxyQuadMaterialBridge.<clinit>` and a missing
  `SpriteTransparencyLevelHolder`. That class exists in the development
  Embeddium 0.3.32 beta but not in the formal 0.3.31 JAR, even though
  `gradle.properties` and `mods.toml` accept `[0.3.31,0.4)`.
- The two-file XXVIII delta was reviewed against the exact 0.3.31 JAR, the
  0.3.32 transparency-classification implementation, and Forge 1.20.1
  `SpriteContents`/`NativeImage`. It removes the newer internal-class linkage,
  preserves the same `OPAQUE`/`TRANSPARENT`/`TRANSLUCENT` alpha precedence, and
  weakly caches results for the atlas-content lifetime.
- Regression coverage now exercises every classification branch and rejects
  reintroduction of the post-0.3.31 internal package dependency. A forced clean
  `test jarJar` using the exact formal 0.3.31 JAR passed **35 suites / 112 tests /
  0 failures / 0 errors / 0 skipped**.
- The replacement all-JAR is **12,590,782 bytes**, SHA-256
  `3b9c0dee957a1c9cbcc18171903b5e7bc0b171dbfa871f59110d2dabc06dbe06`, with
  458 entries, zero duplicates, zero bundled frontend/platform classes, and no
  post-0.3.31 transparency-holder constant-pool reference.
- This targeted delta audit does not retroactively rewrite the Pass 14 hashes.
  Current whole-mod acceptance remains reopened until the replacement artifact
  starts and completes visual/lifecycle regression in the user's formal
  0.3.31 instance. No XXVIII commit is authorized before that user confirmation.

### XXVIII.2 audit correction: restored XVI watertight quad coverage

- Formal 0.3.31 startup progressed into the affected modpack, proving the
  XXVIII.1 linkage repair. User screenshots then showed regular, face-local
  diagonal marks on LOD blocks with shaderpacks disabled. The separately seen
  shaderpack fog wall was classified by the user as modpack configuration and
  is excluded from this code change.
- Historical commit `7d1bc34e` records the same symptom, root cause, exact
  software-raster correction, and successful runtime verification. The current
  `SoftwareRasterizer` had lost that correction during XXV reconstruction:
  `rasterQuad` again delegated coverage to two independently tested triangles.
- Pass 14's original-source equality is therefore not sufficient for this
  method. The current original implementation is still the comparison baseline,
  but the XVI four-outer-edge coverage test is a documented, runtime-proven
  correction to a defective software-bakery edge-ownership result, not a
  substitute render route.
- `rasterConvexQuad` now decides inclusion from the quad's four outer edges.
  The original `(0,1,2)` / `(2,3,0)` split remains solely for barycentric
  depth/UV interpolation, so the internal diagonal cannot leave clear baked
  texels. Degenerate triangle-as-quad input remains valid.
- The old test name and 9-of-16 coverage expectation incorrectly enshrined the
  regression. It now requires watertight 16-of-16 coverage for the controlled
  quad. The focused test passes.
- A forced clean `test jarJar` against the exact affected Embeddium 0.3.31 JAR
  passes **35 suites / 112 tests / 0 failures / 0 errors / 0 skipped**. The
  all-JAR is **12,590,782 bytes**, SHA-256
  `3b9c0dee957a1c9cbcc18171903b5e7bc0b171dbfa871f59110d2dabc06dbe06`,
  with 458 entries, zero duplicates, and zero bundled platform/frontend classes.
- The Pass 14 hash ledger remains the committed historical XXVII snapshot; the
  changed rasterizer and test are explicitly superseded by this delta audit.
  User visual confirmation is required before commit or readiness closure.

### XXVIII.3 audit correction: custom renderers are empty, not missing models

- The 2026-07-14 19:59 error bundle proves the current JAR entered the affected
  0.3.31 modpack, created the formal Voxy route, and rendered for about five
  minutes. The fatal owner was then `ModelFactory.processModelResult`, reporting
  block-state 5731 as `baked-model-missing-or-custom`; shutdown completed through
  the normal render-system teardown path.
- Direct read-only decoding of the persistent Mapper's typed RocksDB mapping
  identifies 5731 as
  `butcher:pestleandmortarblock[facing=south,animation=0]`. The Butcher JAR owns
  an animated block-entity renderer for this state, so Forge correctly marks the
  model as custom-rendered.
- Original `SoftwareModelTextureBakery` has an explicit TODO for block-entity
  models but does not fail an empty ordinary-quad bake. The Forge adapter's
  merged null/custom failure classification, combined with XXVII's strict error
  propagation, changed that unsupported original case into a client crash.
- `ForgeSoftwareModelTextureBakery.classifyModel` now separates `MISSING`,
  `CUSTOM_RENDERER_EMPTY`, and `BAKED_QUADS`. Only `MISSING` sets a failure.
  Custom renderers return empty face data with `none`, allowing the unchanged
  `ModelFactory` dedupe/mapping/in-flight completion path to run. This preserves
  the original limitation without inventing replacement geometry.
- A focused behavior test covers all three routes. The forced-clean exact-0.3.31
  `test jarJar` gate passes **36 suites / 113 tests / 0 failures / 0 errors / 0
  skipped**. The all-JAR is **12,591,959 bytes**, SHA-256
  `d53b8c6c5c495020556e63b41fb77f42f9eda7df9fbbf326fece151b462f3a06`,
  with 459 entries, zero duplicates, and zero bundled platform/frontend classes.
- The user confirmed the final JAR no longer showed the diagonal seam and did
  not crash around the custom-renderer state. This closes the combined runtime
  confirmation for XXVIII.2 and XXVIII.3.

### XXVIII.4 audit correction: Chunky FULL result must be ingested by identity

- Installed Chunky 1.3.146 bytecode confirms its Forge adapter returns an
  `allOf` Void future and independently removes the `CHUNKY` ticket on async
  completion. The previous Voxy RETURN injector added another async completion
  and then called `getChunkNow`; it could therefore observe null after ticket
  release/unload and silently omit the generated chunk.
- Original Fabric Voxy wraps the holder generation future and consumes the
  successful chunk object directly. Forge 1.20.1 exposes it as
  `Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>`; the Forge mixin now
  redirects `ChunkHolder.getOrScheduleFuture`, consumes the left value, and
  preserves the same `LevelChunk` contract. No fallback importer, synthetic
  LOD, or renderer-side repair was added.
- Ocean concentration is consistent with fast homogeneous generation widening
  the scheduling window. Source review excludes a water-only empty-section
  filter: water sections are non-air and every non-null section is eligible.
- The external Chunky target prevents automatic remapping of the nested
  Minecraft invocation. Both verified Forge 1.20.1 names are explicit:
  development `getOrScheduleFuture` and production `m_140049_`; `require=0`
  ensures only the environment-appropriate point applies.
- The focused source contract rejects the former `getChunkNow`/`thenRunAsync`
  route and requires the dependency-free standard-Mixin redirect, exact-result
  capture, and both runtime targets. The forced-clean exact-0.3.31 gate passes
  **36 suites / 114 tests / 0 failures / 0 errors / 0 skipped** plus reobfuscated
  JarJar. The 12,592,017-byte artifact has SHA-256
  `d8fa3c7cf01f463597e2e0613433d906592b6a1e2edcf85358750ad1d02e8add`,
  459 entries, zero duplicates, and zero bundled platform/frontend classes.
  The user completed Chunky pregeneration and inspection of never-client-loaded
  ocean LOD without reproducing a hole, reconfirmed the other XXVIII repairs,
  and approved the final commit/push. The project is beta-complete as of
  2026-07-14; IterationT remains the already documented post-migration
  compatibility TODO because original Voxy has no corresponding adaptation.
