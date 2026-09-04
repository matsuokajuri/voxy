# Forxy 拾轮准备：GPU 可见性、着色器与多视口状态

状态：2026-09-05 代码、328项自动验证和本机可用客户端矩阵完成。本文保留准备时容量基线，验收范围见[最终实机记录](forxy-round10-final-runtime-qualification-2026-09-05.md)；物理VR、旧外部GL深度复制A/B和特定光影功能限制仍明确保留。

执行授权更新：09-05用户恢复Computer Use后已执行正式客户端矩阵。没有用离屏测试代替可用实机；旧整合包GL深度复制进一步归因仍单独暂停，与本次已修复的Acedium驻留错误分开。

## 一、基线与目标

- 当前分支：`Forxy`；提交基线：`4542fd387`（XXXIX 兼容补丁合并）。
- 保留当前工作树中 SQLite 不内嵌政策、发布包政策测试和兼容审计记录，不回退其他轮次成果。
- 最近验证基线：70 个默认测试套件 / 253 项测试，以及 1 项发布包政策测试通过。它们不是拾轮 GPU 执行测试。
- 两个整合包的用户视觉冒烟通过，正式 MDIC 渲染和正常退出有日志证据；没有据此宣称新区域 Chunky、全部维度、全部光影切换或真实 VR 已通过。
- SQLite 缺失只使可选 DH 数据导入不可用，不再作为普通 LOD 验收阻塞项。
- 准备时“逆转未来”的 GL 1282 缺调用栈；之后取得 Oculus depth-copy 直接栈，但跨模组 A/B 归因按用户要求暂停，不能借此直接改 Voxy 深度格式。

用易懂的话说：拾轮先保证“GPU 不会把任务写到缓冲区外，也不会按错误数量继续读取”，再验证不同相机是否共享了不该共享的状态，最后逐项核对透明材质、染色、深度与遮挡计算。

正式链保持不变：

```text
ForgeOriginalVoxyModelPipeline（渲染线程、禁止重入）
 -> ViewportSelector -> MDICViewport
 -> depth setup -> opaque -> HiZ
 -> HierarchicalOcclusionTraverser -> render-list
 -> prep -> raster cull -> cmdgen -> prefix sum -> translucent build
 -> temporal -> NORMAL SSAO / Oculus 对应阶段 -> translucent -> finish
```

原版 Java 基线来自 `src/main/java/me/cortex/voxy/client/core/` 下的对应 owner，Forge 活动实现位于 `src/main/java/me/cortex/voxy/forge/`。正式 GLSL 资源位于 `src/main/resources/assets/voxy/shaders/`；`bin/` 副本不是另一个需要修改的 shader owner。

## 二、已经核实的容量与布局

以下是准备时的分配基线，不是扩大后的容量。字节单位为 B。实施新增的 12 B 调度快照及 4 KiB prefix 尾区见执行记录，原 ABI 前缀不移动。

| 对象 | 当前容量 / 分配 | 关键消费者 |
|---|---|---|
| HOC 请求队列 | 50 条 `uvec2`，加 8 B 头，共 408 B | GPU 请求标志、CPU 下载、AsyncNodeManager |
| HOC top-node / scratch A / scratch B | 各 200,000 个 uint，各 800,000 B | 后续轮次 traversal |
| HOC 轮次 metadata | 5 × 16 = 80 B；当前最高 LOD 为 4 | indirect dispatch、source 有效长度 |
| HOC render-list | 200,000 个 uint + 4 B count，共 800,004 B | prep、raster cull、cmdgen |
| MDIC draw commands | 400,000 opaque + 100,000 translucent + 100,000 temporal；stride 20 B，共 12,000,000 B | indirect draw |
| MDIC 桶起点 | opaque 0；translucent 8,000,000 B；temporal 10,000,000 B | cmdgen / translucent builder / draw |
| MDIC draw-count | 分配 1,024 B；当前有效 ABI 44 B | dispatch XYZ 0–11；三种 count 12/16/20；cull command 24–43 |
| position scratch | 400,000 × 8 = 3,200,000 B | terrain vertex 的 baseInstance |
| translucent distance/list | 1,024 × 4 + 100,000 × 4 = 404,096 B | histogram、prefix sum、translucent builder |
| node metadata / cleaner visibility | 2²¹ × 16 / 2²¹ × 4 | traversal、cleaner |
| MDIC visibility | 2²⁰ × 4 = 4,194,304 B | raster cull、cmdgen |
| Cleaner 固定输出 | 256 × 4 B ID + 256 × 8 B position，共 3,072 B | transformer 后仅下载 2,048 B position 区 |

容量源码入口：`HierarchicalOcclusionTraverser.java:69–97`、`MDICViewport.java:13–64`、`MDICSectionRenderer.java:98–117`、`NodeCleaner.java:33–112,188–201`。

必须区分 section 数量、draw command 数量和 quad 数量。一个 section 最多产生 7 条 opaque/temporal command，不能用 section 上限直接证明 command 桶安全。Binding 复用按“program + UBO/SSBO/texture namespace”判断，不要求所有数字全局唯一。

## 三、债务分类：缺口、已有保护与待验证假设

### 源码可见的保护缺口（尚不等于已观察到实机越界）

- `hierarchical/queue.glsl:25–51`：child reservation / sink 写入缺少正式容量检查；末轮检查仅在 `DEBUG` 中。source 读取只信任 metadata 中的 count。
- `hierarchical/traversal_dev.comp:79–84`：render-list 写入有容量检查，但原子计数仍可能超过已写入的有效数量。`gl46/prep.comp:11,20` 和后续 cmdgen 会继续信任该计数。
- `gl46/cmdgen.comp:97,101,113–119`：opaque、temporal、translucent reservation 缺少各自桶上限。绘制 API 的 `maxDrawCount` 不能保护先前的 GPU 越界写入。
- `hierarchical/cleaner/sort_visibility.comp:136` 写入最高位 external 标志，`:85` 却存在直接把带标志值作为 visibility 索引的入口；`:89–90` 的局部插入循环也没有像全局版本那样处理中止 sentinel。先构造压力/重复 ID 夹具，再修复，不把它直接归因于某个已有视觉问题。

### 已有保护或已经实现的机制

- 请求队列 GPU 写入限 50、CPU 下载也有限长处理，不是完全无保护；剩余问题是溢出可观测性、计数语义与 requested 标志是否只对应成功提交。
- Cleaner 输出是固定 256 项，不是无限 append queue。拾轮查索引、sentinel、round-up dispatch 和下载区间，不凭空增加输出溢出问题。
- Cleaner 顶层 LOD 写死为 4，目前与 `WorldEngine.MAX_LOD_LAYER` 相符；这是归属/一致性债，不是当前数值已错。
- `quad_util.glsl:24` 的 TAA TODO 不能单独当成缺功能：`quads3.vert` 的 main 已给 `taaOffset` 赋 `taaShift()`。
- 非 mip 路径目前只剩注释，不新建或恢复该路径；清理注释排在契约测试之后。
- 双面薄模型已有捌轮分类与 command bucket；不能看到 vertex 旧 TODO 就再次复制背面。

### 需要先证明的设计问题

- 原版 MDIC renderer 明确留有将 uniform/distance buffer 移到 viewport 的 TODO；Forge 当前也共享这两个 buffer。但当前完整视图帧在同一渲染线程连续执行，不能由“共享字段”直接推出实际串帧。
- 当前每视口已经独占 draw、lookup、visibility、矩阵、frameId、HiZ 和 chunk-bound depth。Vivecraft pass key 是稳定的枚举实例，不因 weak-map TODO 就更换容器。
- Forge `RenderProperties.java:9–13` 的深度约定来自目标平台适配；不能直接套用较新原版的设备查询逻辑并启用 reverse-Z。

## 四、执行顺序

下列清单保留完整验收范围；实现与自动测试已有进展，正式完成勾选以执行记录的证据为准。按连贯子阶段验证，不要求每个小步骤单独提交。

### 拾.1 — 冻结容量、布局与边界基线

- [x] 将上表变为可执行的 Java 分配 / shader define / GLSL layout / draw 参数一致性测试。
- [x] 冻结 command stride、三个桶区间、count ABI、position/list 关系、请求头和 cleaner range。
- [x] 记录原版对应值，给现有符号优先补消费者测试；只给仍无明确 owner 的常量收敛归属。

门槛：空数据和正常小场景结果不变，所有资源大小与最后合法地址可解释。保留 20 B command、64 B model、8 B quad 布局；不为 TODO 中的性能建议改成 32 B command。

### 拾.2 — HOC 请求、遍历与 render-list 全链路有界

- [x] 分清 attempted count、成功 reservation、实际发布 count 与 overflow。
- [x] 同时保护末轮、批量 child reservation、sink 写入、source 读取和 indirect dispatch。
- [x] render-list 的消费者只能看到已成功写入的项；请求未入队时不得留下永久 requested 标志。
- [x] 确定超限策略及下一帧恢复条件，不静默遗失工作，也不增加另一条替代渲染链。

夹具：0 / 容量−1 / 容量 / 容量+1，大量线程争最后空间，一次最多 8 child 跨界，queueIdx 4/5，请求 49/50/51。

门槛：buffer 哨兵未变化、有效 count 不超限、未写入项不被消费；溢出可观测且恢复行为明确。诊断不得给正常每帧路径加入同步 GPU 读回或 `glFinish`。

### 拾.3 — 命令桶、透明排序与 cleaner 索引安全

- [x] opaque / temporal 按批量 command reservation 检查边界；translucent 的 list、histogram、prefix sum、builder 使用一致的成功数量。
- [x] 验证所有透明项落同一 distance bucket、全透明场景和各桶跨界，不能只限制最后的 draw count。
- [x] 为 cleaner external-bit、`uint(-1)`、重复 ID、空/不足 256 个候选及工作组 round-up 建立测试。
- [x] 将顶层 LOD 的来源与现有 WorldEngine 契约一致化，不改 cleaner 的删除资格规则。

门槛：桶间/末尾哨兵完整；prefix sum 合计等于有效透明项；cleaner 只下载规定 position 区，所有解引用均为合法去标志索引。`0/1/511/512/513/最大节点数` 均覆盖。

### 拾.4 — 以真实生命周期裁决多视口共享状态

自动覆盖已完成：capture A/B/A、GPU viewport 身份/矩阵/尺寸/独立缓冲/唯一释放、prepared cache 身份与 teardown 清理、正式串行调用顺序契约。实机补齐普通窗口/default key的resize、光影/维度/owner重建；真实不同眼睛或镜像pass仍受VR硬件限制。

- [x] 自动A/B/A身份/矩阵/尺寸回归与正式串行完整帧调用契约通过；实机同key尺寸变化、重建和不同维度的完整帧连续记录通过。不同VR眼睛完整帧A/B/A不冒充已实测。
- [x] 记录实际viewport key、capture generation、prepared状态、身份/尺寸；结合源码确认uniform上传者与distance buffer最后消费者仍在同一串行帧。
- [x] 验证default与安装Vivecraft的普通窗口、同key重用、resize、renderer rebuild和正常释放；真实VR pass留在硬件待办。
- [x] 保留原版串行全帧共享buffer，不因非当前调用契约的交错探针擅自迁移；收紧prepared身份、尺寸与owner teardown。

门槛：A 的 command、visibility、透明排序、HiZ 和尺寸不会沿用 B。强制插入“A opaque → B 完整帧 → A translucent”只是非当前契约的压力探针，其失败不能单独证明现实窗口路径有 bug。

关键依据：Forge `MDICSectionRenderer.java:115–118,219–339,554–615`；原版对应 renderer `:90–96`；`ForgeOriginalVoxyModelPipeline.java:586–594,687–759`；`ViewportSelector.java:22–38,62–75`。

### 拾.5 — shader 数学、透明覆盖与染色契约

- [x] 逐面核对 `ModelFactory.encodeSoftwareFaceData` → `block_model.glsl:11–18` 的 1/64 indentation 和 1/16 bounds 编解码，覆盖 0/1/62/63 编码及六面、不同 LOD、负坐标和合并尺寸。
- [x] 核对 `quad_util.glsl` 的 merged cutout override 与 `quads.frag:17,165–179` 的透明裁剪，区分合法透明孔洞与丢面。
- [x] 用带彩色细节的部分染色模型核对 normal/patched 的取样和颜色空间，不直接把 alpha 高位挪作 tint 标志。
- [x] 为 `VoxyFragmentParameters` 的 derivative 需求找到真实 consumer，验证 helper invocation / `textureGrad`；没有消费者不添加新字段。
- [x] 清理已停用的 non-mip/TAA 等旧注释前，先证明正式路径已覆盖其原意。

门槛：沿用捌轮模型/上传和玖轮面所有权夹具，新增 CPU/shader 数学对照与真实画面确认。`PATCHED_SHADER`、`TRANSLUCENT` 必须在 vertex/fragment 两阶段一致；光影 patch 编译失败后出现普通画面不算光影验收通过。不得再次破坏已验收的树叶填色、清晰度或跨 Vanilla/LOD 阴影。

### 拾.6 — 深度、HiZ、SSAO 分阶段取证

- [x] 记录实际 clip-depth mode、compare、clear value、attachment 类型/格式/samples/尺寸及 shader defines。
- [x] 测试 near / mid / far / clear 的投影、深度与反投影，检查 NaN、Infinity、反向比较及 stencil 分区。
- [x] 验证每级 HiZ 是当前深度约定下的保守 reduction，含 resize、非二次幂尺寸和遮挡边缘。
- [x] NORMAL 的 BASIC/BETTER/BEST各次真实Apply重建与Oculus路径分开验收；三模式像素及BETTER vanilla/Voxy投影对照由GPU回归覆盖，不将菜单背景观察说成逐像素视觉比较。
- [ ] 对现有 GL 1282 的跨模组关联完成归因：已有直接 Oculus depth-copy 栈，磁盘临时设置已恢复；进一步复现/A-B 按用户要求暂停。

自动覆盖已完成：真实 depth/stencil owner、SSAO BASIC/BETTER/BEST 与 Vanilla/Voxy 投影、纹理/renderbuffer 附件信息的无状态改动查询；这些不替代 Oculus 实际光影包和真实帧采集。

门槛：不凭旧 TODO 切换 reverse-Z，不用跨格式 depth blit 替换原版 fullscreen depth copy。原版 `AbstractRenderPipeline.java:136–159` 的格式约束，以及 Forge `ForgeOriginalVoxyPipelineDepthStage.java:70–136` 的对应机制必须保留。

### 拾.7 — 正式容量与兼容矩阵验收

- [x] 完整默认测试、发布包政策测试、正式 reobfuscated JarJar。
- [x] Embeddium-only；Oculus 无光影/有光影；Unbound、Reimagined、Photon实际patch与地形通过；BSL的额外uniform限制单独保留。
- [x] Acedium 与 Vivecraft 普通窗口，shader on/off、换包、F3+T、resize、切维度、退出重进和正常退出；新发现的Acedium释放错误已修复回归。
- [x] 跨万格传送、新区31区块再缩距、河流/冰面/玻璃与两次Chunky1089区块任务；Bobby/DH真实只读导入回归再次通过，不把它表述为本轮重新完成了游戏内完整离线导入。SQLite运行库不内嵌，缺少时DH游戏内入口不启用。
- [x] 保留真实 VR 双眼、镜像、头部移动的硬件验证待办；普通窗口不能替代（实际硬件测试仍未完成）。

Bobby精确构建及Bobby/DH真实只读回归通过；新区Chunky、跨万格传送与缩距画面通过。完整游戏内离线导入沿用此前端到端证据，本轮未重新执行；无VR硬件与旧GL归因的限制保留。

验收必须使用正常容量的正式可见链。CPU 模拟、小容量 GPU 压力、临时纹理探针都是辅助证据，不能成为正式路径或替代运行态验收。出现新的溢出、GL、stale request、负计数或 LOD 孔洞时先分类，不仅凭用户没看到异常就关闭条目。

## 五、明确不并入本轮

- 拾壹轮：NodeManager 的 leaf/inner/empty/request 状态机、在途 geometry 删除、递归回收、watcher/ID 所有权、`AllChildrenAreLeaf` 传播及 cleaner 删除资格语义。
- SQLite 重新内嵌、自动下载驱动、数据库格式迁移。
- Vanilla/LOD 切换动画、IterationT 新适配、为通过测试新增替代 renderer。
- 未经测量的 command merging、32 B stride、共享缓存等性能提案。它们可记录，不能用删除 TODO 冒充完成。

## 六、验证命令与停止条件

默认/GPU/发布包命令（已通过；精确构建与真实数据完整命令见执行记录）：

```powershell
rtk proxy .\gradlew test round10GpuTest packagedArtifactTest `
  "-PvoxyEmbeddiumDevJar=.gradle/local-inputs/embeddium-0.3.31+mc1.20.1.jar" `
  "-PvoxyOculusDevJar=.gradle/local-inputs/oculus-mc1.20.1-1.8.0.jar" `
  --console=plain
```

`packagedArtifactTest` 依赖正式打包与重混淆。无 Oculus 客户端仍保留编译期 Oculus JAR，只加 `-PvoxyOculusDevRuntime=false`，不改变可选前置政策。

09-05视觉测试已保存场景/设置基线；高风险变更编译并执行相关测试。探针仍须有范围、默认关闭/临时恢复与明确观察目标。客户端均正常退出，不强杀Java。

若只有 shared-field/TODO 推断、没有合法调用序列或资源真值，不进入行为改写；若 GPU 格式/布局要变，必须先列全 producer 和 consumer；若问题属于拾壹轮，不借拾轮容量修复扩大到整个状态机。

代码、自动验证与本机可用正式可见链验收已完成，最终审计及限制见执行记录。勾选“保留硬件待办”不代表真实VR已经验收。
