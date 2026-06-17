# AGENTS.md

This repository is a Forge 1.20.1 port / migration of Voxy-style LoD rendering.

The primary human goal is a clean final result, not a quick dirty demo. However, implementation cadence should use larger coherent batches and fewer ComputerUse-heavy validation loops.

## 1. Project identity

* Project: Voxy Forge 1.20.1 renderer migration.
* Main branch in current work: `forge-1.20.1-skeleton`.
* Main environment: Windows, PowerShell, Gradle, Java, Forge 1.20.1.
* Current direction: clean formal renderer path.
* Do not use new `G6.x` stage names.
* Old `G6.x` labels are legacy labels for the extended G phase only.

## 2. Stage taxonomy

Historical A-F phases already existed before the current summarized conversation.

Current stage taxonomy:

```text
H: formal renderer ownership and no-draw skeleton
I: real ModelFactory / ModelBakerySubsystem / formal ModelStore bridge
J: formal textured renderer prototype
K: early usable LoD renderer hardening
```

Completed or current high-level status:

```text
H1: formal renderer no-draw skeleton
H2: formal renderer lifecycle/status hardening
I1: real ModelFactory / ModelBakery bridge plan
I2: formal ModelStore ownership skeleton
I3: Forge ModelFactory lifecycle skeleton
Next: I4 real one-block bake/upload prototype
```

Do not create new stages named:

```text
G6.23
G6.24
G6.25
```

Use the current H/I/J/K taxonomy.

## 3. Strategic direction

The user chose the clean route, not a quick dirty playable demo.

That means:

* Prefer formal ownership boundaries.
* Prefer correct lifecycle and reload behavior.
* Do not silently turn debug prototypes into formal renderer components.
* Do not call something `formal ready` just because a debug or sample path works.

However, avoid overly tiny stages. Each stage should complete one coherent feature chain where safe.

Good batching:

```text
one-block bake
+ one-block upload
+ readback audit
+ status integration
+ QA preset
+ docs
+ one runClient smoke test
```

Bad batching:

```text
one-block bake
+ multi-block dedupe
+ fluids
+ biome LUT
+ formal shader
+ renderer draw
+ shaderpack integration
```

One stage may contain several small implementation steps, but it should not cross multiple major risk boundaries.

## 4. Execution cadence

From now on:

1. Prefer larger implementation batches.
2. Add one compact QA command or preset for each stage.
3. Use `compileJava` before `runClient`.
4. Use `runClient` only once after implementation is complete, unless fixing a compile/runtime failure requires a rerun.
5. During ComputerUse validation, minimize game-window typing.
6. Prefer backend/console command input if available.
7. If no backend command input exists, use the in-game chat only for compact QA commands.
8. Prefer latest.log, status output, and audit output as evidence.
9. Use screenshots only for:

   * world entry,
   * safe coordinate confirmation,
   * visual renderer stages.
10. Do not claim success from command dispatch alone. Confirm through status, logs, chat feedback, or screenshot.
11. Minecraft must be closed normally. Do not kill Java.

## 5. ComputerUse and runClient rules

When a stage requires runClient:

1. Start with:

```powershell
.\gradlew runClient
```

2. Quick-play should directly enter the local test world:

```text
run/saves/新的世界
```

3. If quick-play fails:

   * screenshot the title/error screen,
   * inspect `run/logs/latest.log`,
   * manually enter the test world,
   * report that quick-play failed,
   * do not call fallback a quick-play pass.

4. After entering the world, confirm spectator/in-world state.

5. Do not repeat `/gamemode spectator` unless explicitly needed.

6. Teleport to the safe coordinate:

```text
/tp @s 128 140 128
```

7. Confirm teleport through screenshot, latest.log, or chat feedback.

8. Prefer a QA preset over many manual commands.

9. Close Minecraft normally from the UI/menu.

10. Never kill Java unless explicitly instructed by the user.

## 6. Command input preference

Before using game chat, check whether a backend command input is available:

```text
console
standard input
RCON
debug command bridge
test harness command queue
```

If available, use it for `/voxy ...` and `/tp ...`.

If unavailable, report:

```text
backendCommandInputAvailable=false
fallbackToGameChat=true
```

Then use in-game chat with minimal commands.

## 7. Build and validation commands

Common commands:

```powershell
git status
.\gradlew compileJava
.\gradlew runClient
```

Do not run `runClient` repeatedly for every tiny check.

For no-draw stages, one smoke test is enough if:

* compile passes,
* quick-play enters world,
* QA preset/status passes,
* latest.log has no new crash,
* Minecraft closes normally,
* git status is clean except intended files.

## 8. Git rules

Before starting a stage:

```powershell
git status
```

If clean and the previous stage passed, create the requested tag.

Do not tag if the worktree is dirty.

Do not commit:

```text
run/
logs
crash reports
saves
local config
build outputs
.idea/
.vscode local machine state
```

Commit messages should be short and stage-specific, for example:

```text
Add formal ModelStore ownership skeleton
Add formal ModelFactory lifecycle skeleton
Add one-block formal bake upload prototype
```

At the end of each stage, report:

```text
completed commit hash
tag status
build status
runClient status if used
quick-play status
whether ComputerUse was used
whether Java/Minecraft was killed
git status
what remains undone
```

## 9. Formal vs debug boundary

Debug systems are allowed as validation tools, but must not be promoted to formal readiness.

Debug/sample systems include:

```text
ForgeMdicDebugRenderer
ForgeTexturedMdicDebugRenderer
ForgeTexturedReadbackRenderer
ForgeTexturedDebugQuadRenderer
ForgeModelSampleSet*
ForgeModelAtlasSampleSetUploader
ForgeFormalShaderInputBridge when using sample-set data
GL heap readback visualization
sample atlas upload/readback audit
```

These may be useful for regression checks, but they are not formal renderer ownership.

Formal path must use:

```text
ForgeFormalRendererManager
ForgeFormalModelStore
ForgeFormalModelFactory
future real bake/upload pipeline
future formal shader
future formal MDIC renderer integration
```

Do not claim:

```text
formalRendererReady=true
realModelFactoryReady=true
realModelStoreReady=true
formalTexturedShaderReady=true
```

unless the real formal pipeline actually exists.

## 10. Current formal renderer status semantics

The following may be true after H/I skeleton work:

```text
formalRendererSkeletonReady=true
formalModelStoreSkeletonReady=true
formalModelStoreOwnerReady=true
formalModelFactorySkeletonReady=true
formalModelFactoryLifecycleReady=true
```

But these must remain false until the real implementation exists:

```text
formalRendererReady=false
realModelFactoryReady=false
realModelBakeryReady=false
realModelStoreReady=false
formalTexturedShaderReady=false
actualDrawEnabled=false
```

One-block success in I4 may allow:

```text
oneBlockRealBakeReady=true
oneBlockFormalModelRecordUploaded=true
oneBlockFormalAtlasPixelsUploaded=true
oneBlockFormalUploadAuditOk=true
```

But it still does not imply:

```text
realModelFactoryReady=true
realModelStoreReady=true
formalRendererReady=true
```

## 11. No-draw rules

Many H/I stages are no-draw by design.

No-draw means:

* do not bind formal shaders,
* do not call `MDICSectionRenderer`,
* do not call `VoxyRenderSystem`,
* do not enable formal terrain drawing,
* do not replace existing debug renderers,
* do not introduce shaderpack integration,
* do not start Embeddium / Oculus / Iris work,
* do not enable mixins.

A stage may allocate buffers or textures and upload audit data if explicitly allowed, but it must not render unless the stage is explicitly a draw stage.

## 12. Model pipeline direction

The intended formal model pipeline is:

```text
blockStateId
 -> formal ModelFactory lifecycle
 -> real bake/upload
 -> formal ModelStore modelData/modelColour/atlas
 -> RenderDataFactory / packed quad records with real modelId
 -> formal shader input
 -> formal renderer
```

Current I-stage path:

```text
I2: formal ModelStore owner
I3: formal ModelFactory lifecycle skeleton
I4: one-block real bake/upload prototype
I5: multi-block bake/upload, dedupe, fluids, tint, reload rebuild
```

I4 must target the I2 formal ModelStore owner. It must not use the sample-set atlas helper as a formal upload path.

## 13. ID semantics

Keep these IDs separate:

```text
ForgeVoxyModelIdMapper placeholder ids
sample-set debug model ids
formal ModelFactory model ids
```

Rules:

* Do not use placeholder ids as real formal model ids.
* Do not use sample-set ids as real formal model ids.
* If compatibility mapping is needed, status must say so explicitly.
* Formal status should report:

```text
usesPlaceholderModelIds=false
sampleSetModelIdsUsed=false
usesFormalModelIds=true
formalModelIdsBackedByRealBake=<true only for real-baked entries>
```

## 14. Formal ModelStore rules

The formal ModelStore owner owns:

```text
modelData buffer
modelColour buffer
Voxy-style atlas texture
sampler
lifecycle/status/audit
reload invalidation
clear/free hooks
```

Expected layout:

```text
MODEL_SIZE = 64
MODEL_COUNT = 65536
modelDataBytes = 4194304
modelColourBytes = 262144
atlasWidth = 12288
atlasHeight = 8192
atlasFormat = RGBA8
```

The full atlas is large. Allocation failure must be handled gracefully:

```text
fullAtlasTextureCreated=false
debugSmallAtlasFallback=true/false
allocationFailed=true
lastAllocationError=<reason>
```

A fallback must not be reported as formal atlas readiness.

## 15. Formal ModelFactory rules

The formal ModelFactory skeleton owns:

```text
blockStateId -> formalModelId mapping
seen ids
pending queue
in-flight ids
completed skeleton ids
metadataCache placeholder
fluidStateLUT placeholder
modelTexture2id placeholder
status/audit/clear
```

Until real bake exists:

```text
formalModelFactoryReady=false
formalModelIdsBackedByRealBake=false
realModelRecordsUploaded=false
realAtlasPixelsUploaded=false
```

## 16. I4 one-block bake/upload scope

I4 may do exactly one safe solid block.

Allowed:

```text
read Forge BakedModel
read BakedQuad
read TextureAtlasSprite
build one 64-byte formal record
upload one modelData record
upload one modelColour entry
upload six 16x16 face tiles to the formal atlas
readback/audit those writes
```

Forbidden in I4:

```text
multi-block bake
dedupe
fluid support
broad biome LUT
formal shader bind
formal draw
MDICSectionRenderer
VoxyRenderSystem
shaderpack
Embeddium/Oculus/Iris
mixin
```

Preferred fallback safe blocks:

```text
minecraft:sand
minecraft:stone
minecraft:dirt
```

If no safe block is found, fail honestly. Do not fabricate success.

## 17. Resource reload and lifecycle

Resource reload, world unload, dimension switch, debug pipeline clear, preset off, and preset clear must stale or clear only the resources owned by the current component.

Do not accidentally clear unrelated systems:

```text
GL geometry heap
MDIC command buffers
existing MDIC debug renderer
textured MDIC debug renderer
simple renderer
sample-set debug resources
CPU section geometry manager
```

unless the broad command explicitly owns those clears.

All GL cleanup must be render-thread safe.

## 18. Documentation rules

Update docs when a stage changes the route or semantics.

Important docs:

```text
docs/forge-1.20.1-stage-roadmap.md
docs/forge-1.20.1-formal-renderer-readiness-audit.md
docs/forge-1.20.1-model-bakery-bridge-plan.md
docs/forge-1.20.1-runclient-quickplay.md
```

Do not rewrite old history unless necessary. Add compact stage notes instead.

## 19. Preferred stage report format

Use this structure:

```text
完成，已提交：<commit>

tag:
- 是否已创建 <tag>:

本轮阶段:
- 阶段名:
- 是否进入 <stage>:
- 是否 no-bake:
- 是否 no-upload:
- 是否 no-draw:
- 是否修改 renderer draw 逻辑:

本轮新增/修改：
- ...

设计文档更新：
- ...

验证结果：
- compileJava:
- runClient:
- quick-play 是否直接进世界:
- 是否使用后台/console 输入命令:
- 如果没有，是否 fallback 到游戏聊天:
- ComputerUse:
- 操作纪律:
  - 初始 spectator 状态是否截图确认:
  - 安全坐标是否截图+日志反馈确认:
  - 是否想当然继续:
  - 是否正常关闭 Minecraft:
  - 是否 kill Java:

核心状态：
- ...

audit:
- ...

formal renderer integration:
- ...

lifecycle:
- ...

debug isolation:
- ...

crash report:
- git status:

仍未做：
- ...
```

## 20. User preference

The user is tracking this project closely and is sensitive to wasted token usage.

Prioritize:

* larger coherent batches,
* fewer ComputerUse loops,
* compact QA presets,
* honest status,
* no fake readiness,
* no vague optimism,
* clear difference between debug proof and formal readiness.

The user wants the clean route, but not endless tiny skeleton stages.

Be direct. If a stage is not going to produce visible changes, say so clearly.
