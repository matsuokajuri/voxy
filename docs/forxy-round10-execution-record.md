# Forxy 拾轮执行记录

目标：完成整个拾轮，按完整验证清单审计后提交并推送 `Forxy`。2026-09-05 已完成代码、自动测试、本机可用客户端矩阵及快速审计；提交信息以Git历史和最终交付为准。物理VR与明确暂停的外部诊断不冒充通过。

当前执行授权（2026-09-05 用户更新）：用户要求主动完成所有工作并允许 Computer Use，现恢复正式客户端视觉与兼容矩阵，完成审计后提交并推送。此前暂停状态已被本次授权取代。

基线：`4542fd387`，以及已存在的 SQLite 不内嵌政策/发布包测试/拾轮准备文档。保持原版可见渲染链、20 B draw command、64 B model、8 B quad；不改拾壹轮节点状态机。

## 最终验收更新（2026-09-05）

- 正式容量客户端与 Closing Song 原整合包通过；用户故障坐标及新区域重复 MC 8→31→8，没有持续空洞；树叶、河流、雪地、透明玻璃以及已有跨区阴影未见回归。
- 无Oculus客户端、完整Oculus/Acedium/Vivecraft窗口/Chunky/Bobby组合、Unbound/Reimagined/Photon、resize、资源重载、跨维度、重进和正常退出已覆盖，详见[最终实机记录](forxy-round10-final-runtime-qualification-2026-09-05.md)。
- 实机发现并补充修复了Acedium0.2.7 CPU-mapped buffer释放未持有NV地址的GL1282；真实GPU失败对照、最小可选门控及客户端相同操作回归通过。这与旧“逆转未来”Oculus depth-copy报错不是同一问题。
- 最终 **328项测试**全部通过（312默认、12 GPU、1发布包、1 Bobby精确构建、2真实数据）。JAR **12,699,252 B**，SHA256 `a22a15242b3cfcd42afa83dcaf3f994e6ee9f4bfd3f530c13e0f2a61b4d0911e`，SQLite仍不内嵌。
- Chunky默认跳过已生成区块不等于全部数据摄取；原版也仅接新生成FULL结果。包含既有区块的对照已通过，未更改正式摄取范围或替代renderer。
- BSL10.1.3基础渲染/重载正常但缺少endFlashIntensity，不记作全效果兼容；保留独立兼容待办。真实VR双眼/镜像仍需硬件。旧GL1282继续保留直接栈归因及未完成A/B的限制，不扩展第三方修复。

## 历史进度（2026-09-03至09-04；以下“当前/最新”均为当时状态）

**2026-09-04 更新：用户在 Closing Song 测试上述候选后报告静止不恢复的空洞，正式可见链验收失败。** 已核实 JAR 身份与日志，正在调查；先前自动验证不能当作整轮完成。现象、候选裁决与证据见[空洞回归调查](forxy-round10-closing-song-holes-investigation-2026-09-04.md)。

同日后续：用户重进后开启光影已正常，再关闭光影也正常；新会话零 child-existence 告警为 0。没有生产修复，故保留为“偶发空洞未解决、重进恢复”，不凭本次正常截图关闭问题。故障日志轮转为整合包 `logs/2026-09-04-1.log.gz`。

再次更新：用户已在无光影的新区域，通过 **MC/Embeddium 区块视距 8 -> 31 -> 8** 重现。已对正式边界深度 clear 的调用者 depthMask 依赖建立失败 GPU 回归，并做仅限 MDICViewport 清理/resize 的最小修复；截图的因果归属及整合包复测仍待验证。详见调查记录的新触发条件与候选修复章节。

**最新更新（16:32）：该深度候选实机失败，已撤回。** 新日志确认 Ingest service 因脏区段释放/获取已卸载对象持续失败，正在修复真实生命周期协议。此前“局部 GPU 测试通过”不证明空洞已修复；不再沿深度掩码候选继续试错。

**16:54 修复完成并重新打包：** 脏 free claim 回滚、失败 acquire 不变更引用、二级缓存锁内复活与首引用、异常读锁释放；同时修复伍轮遗留的“新区段已写入仍被 LOAD_MISSING 阻止渲染”问题。当前 311 默认 + 11 GPU + 1 发布包 + 1 Bobby 精确构建 + 2 真实数据 = **326 项通过**。当前 JAR 为 **12,697,798 B**，SHA-256 `99e959d9a303dbb234edf5510d64470829537ada409f26e3f61aba0974ff4967`；没有自动替换整合包文件，实机复测仍待用户确认。

| 阶段 | 已落实 | 尚需证明 |
|---|---|---|
| 拾.1 容量/布局 | `GpuBufferLayout`、消费者与布局测试、正式发布包检查通过；透明排序新增独立 4 KiB prefix 安全尾区 | 整轮客户端回归 |
| 拾.2 HOC | 有界 CAS、成功计数、末轮/source/node 检查、请求预算与异常溢出区分；408 B 请求头遥测；12 B 独立调度快照与真实间接链测试 | 正式容量客户端与重建/退出回归 |
| 拾.3 MDIC/Cleaner | 命令 reservation、prefix snapshot、坏索引防护、max7 command/section draw cap、每视口 12 B 调度快照；Cleaner mask/sentinel/maxId+1/fixed range；GPU 压力通过 | 最终可见链回归 |
| 拾.4 多视口 | prepared 状态增加视口身份/尺寸并随 owner teardown 清空；串行全帧语义保留；A/B/A capture 与 GPU viewport 生命周期测试 | 正式运行态 capture/frame/depth 记录、窗口 Vivecraft 回归 |
| 拾.5 材质 | normal/patched 共用 base-texel tint 函数，移除 helper 退出后的隐式采样；原版量化/merged override/ABI 的证据裁决 | 光影包与既有树叶/阴影/薄模型画面回归 |
| 拾.6 深度 | 实际 GPU 深度约定、HiZ 全 mip/NPOT/resize/reverse-Z、fullscreen depth/stencil、SSAO 三模式通过；清理写掩码顺序已修复；附件审计覆盖纹理/渲染缓冲/层级/采样数 | 真实客户端；旧 GL1282 进一步交互/A-B 归因暂停 |
| 拾.7 验收/发布 | 300 项默认、11 项 GPU、1 项发布包、1 项 Bobby 精确构建、2 项真实数据测试全部通过；已生成候选 JAR | 可用兼容矩阵、整轮完成审计、commit、push |

生产容量未被测试的小容量覆盖。GPU gate 使用测试专属上下文执行正式 GLSL/owner，属于辅助证据；最终仍须正常容量的 Minecraft 可见链。

## 已有验证事实

- 最终统一构建成功（2026-09-03 17:32）：默认 77 suites / 300 tests、GPU 1 suite / 11 tests、发布包 1、Bobby 精确构建 1、真实数据 2；共 315 项测试，失败/错误/跳过均为 0。
- `round10GpuTest` 真实设备为 NVIDIA GeForce RTX 5070 Ti，OpenGL 4.6.0 NVIDIA 616.56。
- 实际 HOC traversal/TAA 源编译与 child CAS、batch8、末轮、source clamp、request 49/50/51 + retry、render/empty/missing、range 外哨兵通过；五轮 GPU indirect 链覆盖 `0 -> 33 -> 0 -> 1 -> 65` 及 `4 -> 8 -> 16 -> 32 -> 64`，循环内没有 CPU 读回。
- 实际 block-model 全部 64 个 indentation 编码、partial tint base-level 取样、depth helper 四约定、HiZ 的各级/NPOT/1x1/resize/反向深度及 viewport 分离与释放已通过。
- Cleaner GPU 用例通过，含 0/1/17/255/256/511/512/513/最大节点范围、external bit/sentinel/重复与无效 ID、固定 2048 B 下载区和边界哨兵。
- MDIC 初次 GPU 压力为“预期 12 条合法 command，实际 0”，最终由独立调度快照消除；原期望未降低，所有临时探针已移除。正式 prep/cmdgen/simple+subgroup prefix/builder/cull 的空→非空、并发边界、7-command 整批、透明同桶和恢复用例通过。
- 正式地形 vertex/fragment 的 normal/patched × opaque/translucent × TAA 开关 × 普通深度两约定，共 16 种编译/链接组合通过。patched 使用测试专属 ABI consumer，绝不冒充实际光影包通过。
- 深度阶段在调用者 stencil mask 为 0 时曾保留旧值 `0x5A`；修复为先设置 depth/stencil 写掩码再执行原 named clear，GPU 验证输出 stencil 为 1 且调用者状态恢复。没有更换深度格式/算法。
- SSAO BASIC/BETTER/BEST 的六面平面、clear、NPOT、边缘及 Vanilla/Voxy 投影测试通过。边缘暗化像素分别为 48/192/192，Vanilla/Voxy 最大色阶差分别为 0/1/0。
- 独立代码审计核查 HOC/MDIC 发布屏障、异步下载内存、边界恢复与 viewport teardown；发现的 prepared owner 强引用残留已修复。`git diff --check` 通过。

## 首个候选发布包与可重复命令（2026-09-03，历史基线）

- 产物：`build/libs/voxy-forge-0.2.17-beta-forge-all.jar`，12,697,572 B（约 12.11 MiB）。
- SHA-256：`7b89daed741e9a2caeda0f9cd1447a7b9d9431a2b57f137340fe2eb8b2183c8d`。
- 正式 JarJar/reobf 已完成；打包政策测试确认 SQLite 仍不内嵌，XZ 与必需 JarJar payload 保持完整。没有替换用户整合包内的 JAR。

```powershell
rtk proxy .\gradlew test round10GpuTest packagedArtifactTest bobbyExactArtifactTest compatRealDataTest `
  "-PvoxyEmbeddiumDevJar=.gradle/local-inputs/embeddium-0.3.31+mc1.20.1.jar" `
  "-PvoxyOculusDevJar=.gradle/local-inputs/oculus-mc1.20.1-1.8.0.jar" `
  "-PvoxyBobbyDevJar=.gradle/local-inputs/bobby-1.20.1_v5.0.1.jar" `
  "-PvoxyBobbyCacheTestPath=run/.bobby/127.0.0.1_25565/-4672863472195697072/minecraft/overworld" `
  "-PvoxyDistantHorizonsDevJar=.gradle/local-inputs/DistantHorizons-3.2.0-b-1.20.1-fabric-forge.jar" `
  "-PvoxyDhDatabaseTestPath=run/saves/新的世界/data/DistantHorizons.sqlite" `
  --console=plain
```

真实数据测试只读本地既有 Bobby cache 与 DH 数据库，不是在游戏中同时启用 DH/Voxy，也不代替完整导入后的视觉验收。

## 已撤回候选：边界深度清理最小修复（2026-09-04）

- 与首个候选相比，生产代码仅增加 `MDICViewport.clearDepthBounding` 的临时 depth-write 开启/恢复，resize 也走此入口，以及每 viewport 一次的实际 false 状态 INFO；不改排队、节点或缓存。
- 新 GPU 用例修复前明确得到旧深度 `0.75`，期望 `0`，修复后通过。最终重跑 **300 项默认 + 12 项 GPU + 1 项发布包**，零失败/错误/跳过；Bobby/DH 的 3 项既有结果仍属于前一轮验证，未计入本次 313 项。
- 该历史 JAR 为 **12,697,990 B**，SHA-256 `578422978598e561466fae1bd29cfd6b8b269711558203beaa898db602a1fc5f`；用户已安装并报告入世界大面积空白，现不再建议使用。
- 其深度掩码补偿没有实际触发 INFO；新日志直接证明数据摄取区段生命周期错误。已回撤候选源码和候选测试，不保留它作为未经验证的正式修复；未清缓存或动存档。

## 必须保留的裁决与限制

- 请求预算耗尽是常规调度节流，不是 GPU 越界故障；真正容量/坏索引事件单独可观测。
- 有限容量超限时的策略是有界发布/当批关闭或原链已有父 mesh，随后再次遍历/竞争。持续容量不足时不承诺无缺面，也不新增替代 renderer。
- MDIC shader 的原 count ABI 前 44 B 保持；诊断使用原 1 KiB 空余区。透明 histogram/list 偏移保持，增加不可变 prefix 尾区以避免变动 cursor 被误当桶边界。
- 相对原版明确增加：每个 MDIC viewport 12 B、每个 HOC owner 12 B 的独立间接调度快照。它们通过 GPU copy 获取原 XYZ，不移动 44 B/80 B ABI，也不扩大正式队列容量。该改动针对本机测得的 writable SSBO/indirect 同缓冲别名问题，不泛化为所有驱动必然失败。
- 原版 MDIC 的共享 uniform/distance scratch 依赖当前串行全帧契约，未盲目全部迁移至 viewport。Oculus prepared capture 的身份/尺寸则已收紧。
- 1/64 face indentation 保留原版六面方向及 `0..62` producer 饱和、`63 -> 1.0` decoder 约定，不虚构额外半像素修复。部分染色仍受原版灰度启发式限制；未挪用 alpha 位或扩展未消费字段。
- SQLite 仍不内嵌。物理 VR 双眼/镜像/头部运动需真实硬件，作为明确硬件待办保留；不把普通窗口等同于 VR 验收。
- “逆转未来”旧 GL1282 已取得直接调用栈（见下文），进一步交互复现/A-B 归因按用户要求暂停；不能用它合理化任意深度格式改动。

## 当时的暂停与恢复条件（现已被09-05验收取代）

1. 代码和全部自动测试已完成。若后续再改生产代码，重新执行上述统一命令。
2. 客户端操作恢复后，再启用 `-PvoxyAuditRound10` 采集真实帧的视口、capture generation、clip-depth、compare、attachment format/size/samples，执行准备文档中的可用兼容矩阵；当前不操作客户端。
3. 保留旧 GL1282 的已有栈证据，不继续复现，也不扩展第三方修复；临时磁盘诊断配置已恢复。
4. 对[准备清单](forxy-round10-gpu-visibility-shader-multiview-preparation.md)每项验收证据逐条核对；补齐正式测试与工作树审查后才提交和推送。

上述是09-03暂停时的记录，不再作为当前停止指示。09-05用户恢复授权后已完成可用矩阵；提交/推送状态以本文件顶部最终记录及最终Git结果为准。

## 已暂停的外部诊断（2026-09-03）

- 为复现旧 GL1282，已通过 PCL 启动原安装的“逆转未来2.3.3”（旧测试 JAR，未替换为拾轮代码）。
- 其 `config/immediatelyfast.json` 中 `debug_only_print_additional_error_information`
  从 `false` 临时改成 `true`；收到暂停指示后已将磁盘配置恢复为 `false`。未修改其他配置。运行中的旧客户端未通过 UI 关闭或强杀；内存中的开关可能持续到其正常退出。
- 用户补充的复现操作：打开背包、开镜、切换维度。
- 该旧包运行只用于归因，不得计入拾轮新代码的客户端通过记录。
- 已复现并归因启动期 GL1280：17:14:24.923 的同步栈落在
  `EffekseerCoreJNI.EffekseerManagerCore_Initialize` -> AAAParticles 2.2.3
  的全局 manager 初始化（本次日志 1801–1816），不是 Voxy 地形 owner。
- 暂停前已捕获 GL1282：17:19:09.616，同步栈为 `GL43C.glCopyImageSubData`
  -> `DepthCopyStrategy$Gl43CopyImage.copy:114` -> `RenderTargets.copyPreTranslucentDepth:216`
  -> `IrisRenderingPipeline.beginTranslucents:1064`，来自已安装 Oculus 1.8.1。
  日志错误为 source/destination internal formats incompatible，发生在切到已有枪械后。
  这确认了报错的直接调用者，不等于排除所有模组间接影响；不在未完成 A/B 证据时宣称完全无关。
- 用户指示后不再继续游戏操作或 GL 复现；该诊断不计入拾轮候选构建验收。
