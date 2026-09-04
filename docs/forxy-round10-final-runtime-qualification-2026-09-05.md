# 拾轮最终实机验收（2026-09-05）

用户已恢复 Computer Use 授权，要求主动完成全部工作并提交推送。本记录保存当日实际运行结果，不以此前辅助 GPU 测试代替客户端验收。

基线候选：`99e959d9a303dbb234edf5510d64470829537ada409f26e3f61aba0974ff4967`，12,697,798 B；包括区段释放/复活与首次写入可见性修复，已撤回边界深度写掩码候选。

## 测试矩阵

| 环境/场景 | 结果 | 证据/限制 |
|---|---|---|
| Closing Song 原世界入场、无光影 | 通过 | 05:45 正式renderer创建，原坐标29995,128,29997地形完整；原地8→31→8保持连续，生命周期/容量/GL错误及零child请求告警为0；F2截图05.50.49 |
| Closing Song 新区域、MC视距8→31→8 | 通过 | 41000,160,41000：首次8区块加载→31区块生成→8区块，F3确认实际距离与空队列，缩距后森林/海岸/建筑连续；F2截图05.56.32。零child风暴与生命周期/GL/容量错误均0；11条重复在途请求被现有保护忽略，未持续增长 |
| Closing Song 光影切换及资源重载 | 通过 | Unbound r5.8.1两阶段patch成功；雨/晴场景完整；F3+T后owner释放重建成功；再关闭光影正常。长距传送回原位置的近区加载过渡在数秒内补齐，未形成持续空洞；06:03正常保存退出，日志已归档。重进测试留至开发客户端 |
| Embeddium-only 正式开发客户端 | 通过 | 06:05–06:18，Oculus 不在 runtime；正常 1048576 geometry 容量。主世界森林/湖泊及下界熔岩/玻璃连续。SSAO AUTO→BASIC→BETTER→BEST→AUTO 各次 Apply 触发正式 owner 重建，菜单背景的实际地形恢复，返回游戏正常；三档像素精确对照仍使用此前 GPU gate。主世界→下界→主世界正常，06:18 保存退出与 Gradle 成功结束 |
| Oculus 开关/Unbound/Reimagined/其他兼容光影 | 通过，保留特定包限制 | Unbound/Reimagined/Photon实际两阶段patch与可见地形正常；BSL仅基础渲染通过，uniform限制见下文 |
| Acedium/Vivecraft窗口/Chunky新区域 | 通过本机可用矩阵 | Acedium实际后端启用；其释放GL错误修复后回归为0；Chunky两次1089区块任务。无VR硬件，不虚报双眼验证 |
| resize/F3+T/维度切换/退出重进 | 通过 | 实际owner释放重建、捕获尺寸/HiZ同步、三维度、进程重启与正常退出均有图像/日志证据 |

测试用临时文件及日志保存在忽略目录 `run/diagnostics/round10-final-2026-09-05/`，不提交用户世界、配置或日志。

## 无 Oculus 实际帧记录

- `embeddium-only-qualified.log`：默认 viewport 同 key 在 854×480→3440×1406 后保持身份，capture 连续；新尺寸的 finish 阶段 HiZ 从 512×256 更新为 2048×1024。setup 中仍记录旧 HiZ 是本帧生成前的观察点，finish 已更新，不能误判为消费者沿用旧尺寸。
- clip-depth=37726（负一到一），depth compare=515（LEQUAL），clear=1；source depth texture 格式6402，正式 prepared/opaque/translucent 格式35056（D24S8），samples=0。按原版正常深度约定执行，没有切换 reverse-Z。
- SSAO Apply 和维度切换均出现旧 owner 正常 shutdown、新 viewport frame 从1开始且 capture 继续递增；最终 WorldEngine、网络会话及客户端全部正常关闭。
- F2 证据：`run/screenshots/2026-09-05_06.12.53.png`（主世界）、`2026-09-05_06.17.05.png`（下界）。
- 日志保留已知开发环境启动提示：Forge `stairsblock` field-to-method 转换提示、Embeddium mixin taint 声明、可选 Fabric 接口缺失，以及开发账号 Realms 鉴权提示。没有新 Voxy 生命周期异常、GPU 容量拒绝或 GL 错误；这些启动提示不被隐藏，也不混同于本轮地形缺失。

## 完整兼容组合：逐项证据

- Embeddium 0.3.31、Oculus 1.8.0、Acedium 0.2.7-beta、Vivecraft 1.3.15、Chunky 1.3.146、Bobby Reforged 5.0.1；不同时启用 DH。Vivecraft 仅普通窗口。
- 06:19 Unbound r5.8.1 启动，06:20 窗口 854×480→3440×1406，`prepared=true`，opaque/translucent 为分离的 D24S8 纹理，finish HiZ 更新至2048×1024；同 key/view 身份保持、capture递增。06.24.07 F2 森林/湖泊全景正常。
- 06:24 Reimagined r5.8.1 opaque/translucent patch 均成功，森林/水面完整，F2 `06.25.03`。Oculus 对 `BIOME_PALE_GARDEN`、`endFlashIntensity` 自定义表达式及旧版 stone_slab variant 给出解析警告；未发生 shader 编译失败或普通路径替代，不能说该光影包日志零警告。
- 06:25 BSL 10.1.3 两阶段 patch 成功、实际图像正常，F2 `06.26.19`，F3+T 后 owner 正常释放重建、画面恢复。但 Voxy uniform 收集报告缺少 `endFlashIntensity`（`ForgeOriginalVoxyOculusRenderPipelineData:830–838` 的诊断，未抛出异常）。因此本次只认定 BSL 基础渲染/重载通过，不认定其全部效果已兼容；保留为单独兼容限制，不凭猜测补常量或删日志。另测 Photon 作为第三款完整矩阵候选。
- 06:28 Photon 1.3b 两阶段 patch 成功，F2 `06.28.59`；06:29 下界、06:30 末地及远岛可见，F2 `06.31.09`。Oculus 保留光影菜单/物品属性解析警告，没有新的 Voxy uniform 缺失或编译错误。
- 06:31 关闭光影后 F3 明确显示 `Using nvidium renderer: 0.2.7-beta`，末地近景与 LOD 远岛连续，F2 `06.31.51`。实机显卡为 RTX5070Ti，驱动616.64（此前辅助 GPU gate 的616.56为当时历史值）。

## 新发现：必须先判别，不提前关闭

- 06:33 末地→主世界（Acedium启用、光影关闭）期间有6条 GL1282：`Buffer object is not resident for this context`，均紧接 ChunkBuilder 停止线程，第一条早于 Voxy teardown；与旧整合包 Oculus depth-copy 的 GL1282 文本/路径不同，不能混为一件事。
- 精确检查 Acedium 0.2.7-beta 字节码：`PersistentClientMappedBuffer` 构造只创建/映射 buffer，未调用 NV resident；`delete()` 却无条件调用 `glMakeNamedBufferNonResidentNV`，是可独立验证的直接候选。尚不凭字节码单独声称运行栈归因已完成。
- 06:36 Chunky 方形中心45000,45192、半径256，9秒处理1089区块，正式 `Chunky trusted FULL ingest hook reached` 已记录。视角固定45000,150,45000，初始6区块；新增LOD有一处突兀断面且队列清空后仍在。
- 单变量对照一：只关闭 Acedium，断面仍在，排除其为该断面的唯一原因。对照二：普通区块视距扩大到31后断面被正常雪坡填齐，确认不是自然悬崖；缩回与重载判别继续，不清缓存。

### Chunky 断面的后续裁决

- 31→6 后正式LOD雪坡持续完整、摄取/保存队列回到0，没有新的生命周期或容量错误。此次现象与持续丢失已摄取几何不同。
- `run/config/chunky/config.json` 的 `forceLoadExistingChunks=false`。精确 Chunky1.3.146 的 `GenerationTask.lambda$run$0` 在 `isChunkGenerated=true` 时直接返回 completedFuture，**不调用 `getChunkAtAsync`**。因此总数1089是处理数，不是1089次新生成/摄取，不能用“100%完成”推断全区域都已进入Voxy。
- 原版 `192721a7d` 的 `commonImpl/mixin/chunky/MixinFabricWorld` 与当前Forge适配都只接 `getChunkAtAsync` 的FULL完成结果。初次客户端小视距周围已有服务器生成、尚未客户端摄取的区块，会被默认Chunky跳过；新增远处LOD与近景之间因此可能有未摄取数据。普通区块实际加载后再缩回的恢复与该路径一致。
- 不改变原版摄取边界、不新增自动扫描或清库。需要导入已有区域时使用正式 `/voxy import current`/离线导入；或由用户选择Chunky的 `forceLoadExistingChunks`。后续测试还将用其强制加载既有区域设置核对完整摄取。

### Acedium 释放保护

- 已增加仅由 `acedium` 身份启用的 `ForgeOriginalVoxyAcediumMappedBufferMixin`，只拦截 CPU-mapped owner 的 NV non-resident 调用；实际 resident 才释放地址，其余原有 unmap/delete/所有权不变，不作用于Voxy自己的buffer。
- GPU回归保留未修复调用的 `GL_INVALID_OPERATION` 对照，以及非resident跳过、真实resident正常释放、最终buffer确实删除三项断言。测试/重启后实际切维度结果待补。
- 完整组合本次06:47保存世界、06:48正常退出；日志为 `full-compat-before-residency-fix.log`。没有强杀Java或清除任何世界数据。
- 修复后统一重跑 **312默认 + 12 GPU + 1发布包 + 1 Bobby精确构建 + 2真实数据 = 328项**，失败/错误/跳过均0；GPU驱动616.64。发布包12,699,252 B，SHA256 `a22a15242b3cfcd42afa83dcaf3f994e6ee9f4bfd3f530c13e0f2a61b4d0911e`。
- 06:51 完整组合重进雪坡连续；F3确认Acedium仍启用。主世界→末地、Photon开→关、末地→新主世界55000,150,55000全部执行，至06:59驻留错误/GL_INVALID/预存GL错误清理均0。此前相同释放过程的6条错误已不再出现。
- 06:59 在新的55000区域，以 `forceLoadExistingChunks=true`、中心55000,55192、半径256运行Chunky：1089区块9秒完成，FULL摄取hook触发，原地6区块视距下森林/雪地/冰面/海岸到新增LOD连续，没有此前跳过既有数据的断层。未调大MC视距补数据，支持上述Chunky默认跳过策略的裁决。测试后恢复该临时选项。
- 最终F2 `07.00.46`；返回开发世界原观察点、恢复创造模式。07:03再次F3+T正常，07:05保存退出，07:06正常关闭客户端，Gradle成功。`full-compat-qualified.log`中GL错误、Voxy ERROR、HOC/MDIC容量拒绝、区段生命周期错误均0。
- `voxy-client.toml`、`options.txt`、`oculus.properties`与Chunky配置均恢复到测试前备份，并按文件字节验证一致；不提交这些本地文件。Closing Song内保留已通过原故障回归的99e959版本，新发布包a22a152另含可选Acedium释放保护。

## 最终快速审计

- 再核HOC原始producer/consumer与有界CAS、成功计数/重试标志、408B下载头、间接调度拷贝屏障；未发现新的阻塞问题。
- 再核MDIC三桶、7-command上限、透明prefix不可变尾区、同帧共享scratch、viewport独立调度与完整teardown；没有把诊断变成另一条绘制路径。
- 再核区段live/free claim、二级缓存锁内复活和LOAD_MISSING首次实写后的可见性；失败前置用例与并发保存反序列化结果均保留。
- Acedium适配只改其CPU-mapped删除时的一次非法NV调用，按acedium身份门控；GPU失败对照与真实客户端相同路径均已验证。未禁用Acedium来掩盖问题。
- 实际客户端已提供正常深度约定、附件类型/格式/尺寸/采样与HiZ更新证据。物理VR眼睛/镜像和旧Oculus深度复制跨模组A/B不虚报通过；BSL缺失uniform作为独立兼容债保留。
- 本轮正式生产容量不变；未嵌入SQLite、未增加替代renderer、未清理用户缓存或存档；仅源码、测试与文档进入提交。
