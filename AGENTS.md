# AGENTS.md

本文件适用于仓库根目录及全部子目录。当前分支的工作只有一个目标：

> 在保持 Minecraft 26.2、Fabric Loader、Fabric API 和 Sodium 版本不变的前提下，把原 Voxy OpenGL 正式渲染链逐项迁移为 Vulkan 等价实现。

## 1. 项目基线

- 工作分支：`26.2-vulkan`。
- 原始基线：原作者 `upstream/dev`，基线提交 `72fb44a1faff2b82a78a24ed0075fbc0fbd99e22`。
- Minecraft：26.2。
- Fabric Loader：0.19.3。
- Fabric API：0.152.2+26.2。
- Sodium：mc26.2-0.9.1-fabric。
- Iris（运行时）：1.11.2+26.2-fabric。
- Java：25。
- 迁移设计：`docs/26.2-vulkan-migration-design.md`。
- 唯一迁移进度账本：`docs/26.2-vulkan-migration-todo.md`。

版本是任务边界的一部分。除非用户明确要求，不得通过升级或更换 Minecraft、Fabric Loader、Fabric API、Sodium 或其他加载器来规避迁移问题。

## 2. 最高规则：迁移，不替代

原 Voxy OpenGL 实现是所有行为、数据结构、shader 契约、生命周期、所有权和性能语义的基线。

```text
读取原 OpenGL owner 及上下游
 -> 追踪数据布局和执行顺序到底层
 -> 建立 Vulkan 等价映射
 -> 保持算法、所有权和链条不变
 -> 验证后再移除对应 OpenGL owner
```

Vulkan 实现可以使用 Vulkan 专属 API 和对象，但只能改变“如何向 GPU 表达”，不能改变“Voxy 要 GPU 做什么”。

如果无法确认原实现的行为，继续读取原源码；不得用推测填补缺口。

## 3. 绝对禁止项

以下规则适用于正式路径、临时实现和所谓过渡方案：

- 不允许 OpenGL fallback。
- 不允许在 Vulkan 不可用或能力不足时静默退回 OpenGL。
- 不允许 CPU cmdgen、CPU culling 或 CPU visibility 替代原 GPU 路径。
- 不允许用固定最大 draw count 替代 indirect-count。
- 不允许重新设计或简化 HOC、NodeCleaner、geometry allocator、cmdgen、MDIC、terrain pipeline 或 shader 算法。
- 不允许以 Sodium 的 CPU-generated indirect command 路线替代 Voxy 的 GPU cmdgen。
- 不允许创建第二套 `VkInstance`、`VkPhysicalDevice`、`VkDevice`、VMA allocator 或独立于 Minecraft 的呈现链。
- 不允许 preview renderer、debug renderer、synthetic fixture、sample bridge、offscreen-only path 或 manual-QA command 成为正式路线。
- 不允许用测试 shader、合成数据、单个像素或一次离屏 dispatch 宣称 renderer ready。
- 不允许为了“先看到画面”而绕开原数据链、ownership、barrier 或生命周期。
- 不允许保留双正式 renderer。OpenGL 只能作为迁移期间的只读行为基线，并在 Vulkan 等价 owner 验证后分批移除。
- 不允许把无法证明等价的自创逻辑提交为正式实现。

设备缺少正式路径需要的 Vulkan feature、limit 或 extension 时，必须明确报告阻断并停止 Voxy Vulkan 初始化。能力不足不是引入 fallback 的理由。

## 4. 允许的 Vulkan 专属适配

下列变化属于 API 语义转换，不属于算法替代，但必须记录原语义和 Vulkan 映射：

| OpenGL 基线 | Vulkan 等价实现 |
| --- | --- |
| `GlBuffer` / buffer target | `VkBuffer`、usage flags、VMA allocation |
| GL texture / texture view | `VkImage` / `VkImageView` |
| GLSL program | shaderc 编译的 SPIR-V module |
| GL binding points | descriptor set、push descriptor、push constant |
| GL framebuffer | dynamic rendering 和显式 attachment |
| `glMemoryBarrier` | `vkCmdPipelineBarrier2` 和精确 stage/access dependency |
| GL fence | timeline semaphore、submission lifetime、延迟销毁 |
| `glDispatchCompute` | `vkCmdDispatch` |
| `glDispatchComputeIndirect` | `vkCmdDispatchIndirect` |
| `glMultiDrawElementsIndirectCountARB` | `vkCmdDrawIndexedIndirectCount` |
| texture upload/readback | staging buffer、copy command、异步回调 |
| GL depth compare enum | Vulkan compare op / backend-neutral depth policy |

可以新增 Vulkan wrapper、descriptor schema、barrier helper、pipeline cache 和生命周期适配器，但这些类只能承载原 OpenGL owner 的等价职责。新增抽象不得引入另一套渲染算法或不同的数据所有权。

OpenGL 的 SSBO、sampler、image 等 binding namespace 相互独立，而 Vulkan 同一 descriptor set 中的 binding 必须唯一。允许重新编号，但必须维护一份明确映射，并用 SPIR-V reflection 校验；shader 读取的数据和含义不得改变。

## 5. 必须保持的正式链

```text
WorldEngine / WorldSection / Mapper
 -> ModelBakerySubsystem
 -> ModelFactory / SoftwareModelTextureBakery / TextureUtils / ModelQueries
 -> ModelStore
 -> RenderGenerationService / RenderDataFactory / BuiltSection
 -> BasicAsyncGeometryManager / BasicSectionGeometryData
 -> RenderDistanceTracker
 -> HierarchicalOcclusionTraverser
 -> ViewportSelector / Viewport / MDICViewport
 -> cmdgen.comp
 -> MDICSectionRenderer
 -> terrain shader / SSAO / composite
```

正式 GPU 顺序必须保持：

```text
geometry upload/scatter
 -> NodeCleaner
 -> hierarchical occlusion traversal
 -> MDIC prep
 -> cull raster
 -> cmdgen compute
 -> prefix sum
 -> translucent command generation
 -> opaque indirect-count draw
 -> temporal indirect-count draw
 -> translucent indirect-count draw
 -> final composite
```

不得改变以下契约：

- model record、section metadata、geometry element、node、visibility、request queue、indirect command 和 draw-count 的布局；
- section/node/model ID 的生成、复用和 owner；
- geometry arena 的地址语义和资源复用；
- HOC 分层迭代、ping-pong scratch queue 和 request readback；
- GPU 生成 draw command 与 draw count；
- opaque、temporal、translucent 的顺序和分类；
- reversed-Z、depth/stencil mask、HiZ、SSAO 和 composite 语义；
- upload、download、resource reload、world unload 和退出时的异步生命周期；
- terrain shader 的输入、输出、纹理、光照、tint 和透明度契约。

## 6. Vulkan 宿主边界

Voxy 必须寄宿在 Minecraft 26.2 已创建的 Vulkan 环境中：

- 复用 Minecraft 的 `VkInstance`、physical/logical device、VMA allocator 和 queues。
- 复用 Minecraft 的 swapchain、render targets、image views、submission 和 frames-in-flight 生命周期。
- attachment 与 dynamic rendering 尽量由 Blaze3D 管理。
- Blaze3D 公共 API 能准确表达原语义时优先使用。
- Blaze3D 缺少 compute、storage resource、indirect dispatch 或 indirect-count 时，使用最小范围的 raw Vulkan bridge 补齐。
- Voxy GPU 对象必须进入 Minecraft 的提交和延迟销毁生命周期，不能在仍被 GPU 使用时释放。
- parity 完成前默认只使用 graphics queue，避免未经证明的跨队列 ownership 和 semaphore 逻辑。
- dedicated compute queue 或 async compute 只能作为 parity 完成后的独立性能轮次，不能改变正式算法或执行依赖。

Minecraft 26.2 的公共 Blaze3D API 不足以表达 Voxy 全部正式链。不得因为公共 API 缺失而删减链条。

`drawIndirectCount` 是硬能力。必须在 logical device 创建前要求并启用；不支持时 fail fast，不得替代。

## 7. 上游源码规则

在修改 Minecraft、Fabric 或 Sodium 接入点之前，读取项目实际锁定版本的源码：

- Minecraft 26.2 Loom sources；
- Fabric Loader 0.19.3；
- Fabric API 0.152.2+26.2；
- Sodium tag `mc26.2-0.9.1`；
- 需要 shaderpack 兼容时再读取 Iris 1.11.2+26.2-fabric 的对应实现。

不要用旧版本 API 记忆替代当前源码。不要仅凭类名猜测 ownership 或生命周期。

Sodium 可作为 Minecraft Vulkan 接入方式的参考，例如 backend detection、render-pass command buffer access 和 push constants；Sodium 的 CPU indirect command 生成不是 Voxy 正式路线的参考实现。

## 8. 读取代码：CodeGraph 优先

如果仓库根目录存在 `.codegraph/`，定位或理解源代码时先使用 CodeGraph：

- `codegraph explore "<symbol names or question>"`：跨 symbol、ownership、data flow 和 call path；
- `codegraph node <symbol-or-file>`：读取一个 symbol 或文件及 callers。

只有在以下情况使用 `rg` 或直接读取：

- `.codegraph/` 不存在；
- 目标未被索引；
- 目标是 docs、config、resources、shader、log 或脚本；
- 需要确认 CodeGraph 的 staleness warning。

如果 CodeGraph 不可用或未索引，明确说明后再回退。

修改一个子系统前必须：

1. 读取原 OpenGL owner；
2. 读取创建它、写入它、消费它和销毁它的上下游；
3. 记录 buffer/image 布局、binding、dispatch/draw 参数和 barrier；
4. 对照 Vulkan 宿主 API 与缺口；
5. 设计逐项等价映射；
6. 实现并验证；
7. 更新迁移文档中的状态和偏差；
8. 仅在引用清零后移除旧 GL owner。

## 9. Shader 迁移规则

- 原 shader 算法、常量、workgroup size、buffer layout 和读写顺序是基线。
- GLSL 转 SPIR-V 时不得顺手重写算法。
- 所有 descriptor binding、push constant range 和 specialization constant 必须显式记录并反射校验。
- vertex/fragment 共享的 define 必须在两个 stage 保持一致。
- compute-to-compute、compute-to-indirect、compute-to-vertex/fragment、depth-to-sampled 和 transfer-to-consumer 依赖必须显式建立。
- 原实现已有 capability-based shader variant 时可以保留同样的选择；不得新增低质量 fallback variant。
- 原 `ivec2` quad 数据路径能够表达相同数据时，优先使用它，避免无必要地把 `shaderInt64` 变成硬要求。
- 临时 shader probe 只能用于诊断，必须在提交前移除。

## 10. Readiness

以下条件全部满足前，不得宣称 Vulkan renderer ready：

- 原正式 CPU 链连接到 Vulkan GPU 链；
- ModelStore、geometry、HOC、NodeCleaner、cmdgen、MDIC 和 terrain pipeline 都由正式 owner 驱动；
- draw commands 和 draw count 由 GPU 生成；
- 实际使用 `vkCmdDrawIndexedIndirectCount`；
- 没有 Voxy OpenGL owner 在正式路径初始化或执行；
- Vulkan validation 没有未解释的 error；
- world reload、resource reload、resize 和退出不产生 use-after-free 或泄漏；
- LOD、光照、水体、透明、昼夜、洞、接缝和遮挡通过实际世界验证；
- section 数量、显存占用、上传/回读和长时间运行通过压力验证。

单元测试、readback、RenderDoc capture、临时 GPU probe 和离屏验证都是诊断证据，但不能单独满足 readiness。

## 11. 实施轮次

使用罗马数字作为主轮次，点号作为轮内步骤：

```text
I    Vulkan Host Contract
II   Resource and Synchronization
III  Shader and Pipeline System
IV   Model and Geometry Storage
V    HOC and NodeCleaner
VI   MDIC Command Generation and Draw
VII  Render Pipeline Integration
VIII Formal Renderer Ownership and Lifecycle
IX   Iris and Shaderpack Parity
X    OpenGL Retirement and Single-Route Audit
XI   Validation, Compatibility and Performance
XII  Final Readiness and Delivery
```

例如：`III.1_DESCRIPTOR_SCHEMA`、`VI.2_ORIGINAL_CMDGEN_OUTPUT_PARITY`。

每轮应形成完整、可验证的 owner 或合同。不要为了制造进度把一个不可运行的步骤拆成大量微小提交。

Round IX 的 Iris/shaderpack 兼容在正常 Vulkan renderer 完整闭环后单独追踪。不得在未读取 Iris 对应源码时猜测其 pipeline contract。

### 11.1 TODO 进度规则

- `docs/26.2-vulkan-migration-todo.md` 是本分支直到迁移彻底完成为止的唯一进度账本。
- 开始一项实现前先定位对应未完成 checkbox；源码追踪发现新的必需 owner、合同或 blocker 时，先把它加入正确依赖位置。
- 一项实现接入正式链并完成该项要求的验证后，必须在同一批改动中把 `[ ]` 改为 `[x]`。不得延后集中补勾。
- 部分完成时必须拆分子项，只勾已实现且已验证的部分；不得删除、合并、改写或批量勾选未完成项来隐藏缺口。
- 仅通过编译、临时 probe、readback、RenderDoc capture、测试像素或离屏验证不得勾选正式 renderer readiness 项。
- 每次阶段性交付都要报告本次勾选的项目及其验证证据。只有全部适用项目已勾选且最终合法性审计无 blocker，才能宣布迁移彻底完成。

## 12. 验证

代码改动后的默认验证：

```powershell
rtk git status
rtk test .\gradlew compileJava
```

涉及 runtime、Vulkan device、shader、resource lifetime、render pass、同步或画面行为时运行：

```powershell
rtk test .\gradlew runClient
```

运行时要求：

- Minecraft 图形 API 选择 `Prefer Vulkan (Experimental)`；
- 记录并确认实际 backend 是 Vulkan，不能只相信配置项，因为 Minecraft 可能回退到 OpenGL；
- 开发阶段尽可能启用 Vulkan validation；
- 把 validation error 当作正确性失败处理，不得仅因画面出现而忽略；
- 优先使用 quick-play；
- agent 不以肉眼猜测用户屏幕结果，涉及视觉正确性时请求用户确认；
- 不使用 ComputerUse，除非用户明确授权；
- 正常关闭 Minecraft，不主动 kill Java，除非用户明确要求。

文档改动至少运行：

```powershell
rtk git diff --check
```

## 13. 诊断纪律

对 runtime 或 visual bug：

```text
读取一个候选 owner
 -> 用日志、validation、readback 或临时 probe 获取证据
 -> 标记 confirmed / excluded / blocked
 -> 记录结果
 -> 再检查下一个候选
```

不要同时堆叠多个未经验证的修复。每个临时 GPU/shader probe 必须在提交前还原。

Vulkan 同步错误、资源 lifetime 错误和 descriptor 错误可能只在特定 GPU/driver 出现；“本机能显示”不是正确性证明。

## 14. Git 与本地产物

- 提交和推送只在用户明确要求时执行。
- 暂存必须使用明确路径；工作树混有用户文件时禁止 `git add -A`。
- 不提交：`run/`、logs、crash reports、saves、local config、build outputs、`.idea/`、`.agents/`、`.codegraph/`、`CODEX.md`、本地开发模组和反编译/参考仓库。
- 不修改或删除不属于当前任务的用户文件。
- 不使用 `git reset --hard`、`git checkout --` 等破坏性命令处理用户改动。
- commit message 应描述一个完成并验证的迁移轮次或明确的文档变更。
- 报告 commit hash、compile 状态、runClient 状态和最终 git status。

## 15. RTK 与 Shell

所有 shell 命令使用 `rtk` 前缀。优先：

```powershell
rtk git status
rtk git diff
rtk test <build-or-test-command>
rtk log <log-file>
rtk rg <pattern> <path>
```

不要直接读取完整 `latest.log` 或 `debug.log`；使用 `rtk log` 或针对性搜索。

## 16. 工作风格

- 直接报告 OpenGL/Vulkan 语义差异和阻断点。
- 平台 API 不足时向底层追踪，不引入替代路线。
- 如果等价迁移需要重写大量代码，按正确方向重写，不以规模为由缩减正式链。
- 做出 Vulkan-specific adaptation 时，在设计文档中说明它对应的 OpenGL owner、保持的合同和不可避免的 API 差异。
- 先完成正确性和 parity，再做 async compute、descriptor 优化、layout 优化等性能工作。
- 不把“编译通过”“客户端启动”“出现像素”描述成完整 renderer 已完成。
