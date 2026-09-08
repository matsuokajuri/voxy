# 节气兼容修复：Ecliptic Seasons 0.12.18.9.1

状态更新：r5 已获四整合包用户验收。本文保留修复过程；新版节气扩展开启场景仍未覆盖，
当前验收范围以[最终快速审计](forxy-compat-r5-four-modpacks-audit-2026-09-08.md)为准。

## 结论与范围

已将节气的旧 Voxy 集成适配到 Forxy 正式渲染链。没有通过关闭节气/Voxy、关闭积雪、
吞掉注入异常或切换备用渲染器来绕过崩溃。没有修改整合包的配置、模组或存档。

本次仅编译和打包、检查源代码及产物结构；按用户要求，**不执行测试、不启动客户端**。
因此本记录不是运行时/视觉验收结论。

2026-09-08 补充：本页新增扩展针对提供 Voxy API 的新版节气。Closing Song 使用的
`0.10-pre10-2` 完全没有该扩展；现按实际 class 能力门控，保留旧版原有行为，
不对它加载新版专用类。详见 `forxy-ecliptic-pre-voxy-api-compat-2026-09-08.md`。

目标证据：

- 用户提供的 `错误报告-2026-09-07_19.31.39.zip`。
- 内含 `crash-2026-09-07_19.31.35-client.txt` 与日志，实例为 Create-Delight-Remake。
- Forge 1.20.1 / 47.4.16，Ecliptic Seasons `0.12.18.9.1`，Forxy `0.2.17-beta-forge`。
- 以该实例实际安装的节气 JAR 字节码为准，不把附件诊断文档中的“关闭兼容”建议当成指令。

## 根因

报错来自节气的 `compat.voxy.MixinVoxelIngestService`：
`eclipticseasons$processJob_in` 要求的注入没有成功，计数为 `(0/1)`。
旧集成期望 `processJob` 直接调用 `WorldConversionFactory.convert`，并使用队列 `pop()`；
Forxy 正式服务则使用 `poll()`，通过 `ingestNow -> convertSection` 调用转换器。
恢复 `pop()` 或移动调用仅为满足旧注入，会破坏已修好的服务生命周期。

这不是单独一个方法改名。旧集成还依赖不存在的 `IngestSection`、原客户端模型类、
原 `commonImpl.WorldImporter` 和 `IGetVoxyRenderSystem`。只跳过出错的一个 mixin，
会在积雪 ID 烘焙、导入或季节刷新时留下另一处故障。

## 一一对应的功能接线

| 节气旧入口 | Forxy 的正式接线 |
| --- | --- |
| `MixinIngestSection`、`MixinIngestSection2`、`MixinVoxelIngestService` | `SectionContext` 随原 ingest job 的 lighting 参数携带来源世界弱引用；支持整区块、单 section、受信任 Chunky 输入 |
| `MixinWorldConversionFactory` | 原转换器完成后、原 mipper 之前调用 `decorate`；使用节气的实际积雪 API/判定规则，仅改变方块 ID，不改变生物群系、光照或非空气计数 |
| `MixinMapping` | `Mapper.baseBlockStateId` 为状态、透明度及新增 mip 属性查询解码；存储与模型映射仍保留独立的积雪 ID |
| `MixinModelBakerySubsystem` | 请求边界按解码后的真实状态检查，队列与去重仍以积雪 ID 区分 |
| `MixinModelFactory` | 正式 `BlockBake` 的 ID 决定本次烘焙的 `snowy` 参数，没有共享的可泄漏积雪标志 |
| `MixinModelTextureBakery` | 从节气 `ExtraModelManager` 获取额外积雪模型及 `cancelTop` 面；追加到现有软件 bakery 消费者，随后走同一次六面栅格化、模型去重和 GPU 上传 |
| `MixinWorldImporter` | 当前 `WorldImporter` 根据真实 `Y + 1` 查找上方 section，提供其方块和光照；原始 region、存档、ZIP、Bobby 的导入仍共用原 owner |
| `MixinClientLevel` | 当前会话的 client tick 读取节气雪况变化，使用会话现有 `ImportManager` / `ServiceManager` 自动重导入 |
| `VoxyEsHandler.onSolarTermChangeEvent` | 保留节气事件处理器，替换方法体，调用 `reloadOriginalVoxyRenderer(false)` 释放并重建正式模型/渲染资源；不销毁 WorldEngine、不清 LOD 数据 |

## 保留的语义

- `VoxyTest`、`VoxyLODAutoReload`、`VoxyReloadWhenSeasonChanged` 均保留节气自身开关与默认值。
  后一个原本不依赖 `VoxyTest`，适配也不增加这一限制。没有强制开启任何选项。
- 积雪编码仍为 `1048575 - baseId`。只解码能对应真实非空气状态的编码，不引入第二个映射表。
  真实状态与积雪 ID 发生编码空间冲突时明确报错，不把错误映射悄悄换成其他方块。
- 不在 mapper 列表中虚构百万个条目；积雪模型仍使用原 20-bit ID 空间，物理属性取自实际状态。
- 已加载区块调用 `EclipticSeasonsApi.isSnowyBlock`；未加载的导入数据使用上方光照、
  发光方块排雪设置、树叶积雪设置及 `MapChecker.shouldSnowAtBiome`。
- 雪模型保持节气的替换/额外模型上下文和随机种子 `42`。材质元数据按当前原版 Voxy
  契约由 Forge quad bridge 读取，不把旧集成的位含义生搬到新的材质布局中。
- 自动导入服从现有会话取消/关闭顺序；手动导入忙时不清掉雪况变化标记。
  单机使用实际世界与维度的 region 路径；远程保留节气命名的本地存档/region 输入能力。

## 不可避免的接口适配

原 Voxy 的模型、转换、mipper、导入与生命周期源码已对照；不为满足旧 mixin 创建
一套假的原包名 renderer/importer，也不复活退役路线。

使用 MixinSquared 的公开 `MixinCanceller` 服务（最低 ABI `0.2.0-beta.6`，内嵌 `0.3.3`），只替换表中 10 个已被完整接替的
旧 Voxy mixin。名单为精确类名，不影响节气其余 mixin。`VoxyEsHandler` 采用整方法替换，
避免留下对不存在的 `IGetVoxyRenderSystem` 的执行路径。服务提供者放在 `forge.compat`
而非受 Mixin 限制的 `forge.mixin` 包内。

转换器的原调色板/位存储循环不变。积雪判定是仅在功能开启时、mip 之前对 4096 个基础
体素的附加处理；关闭功能或未安装节气时不扫描、不复制上方 section。
NBT 上方 section 按真实 Y 查找，不继承旧实现“列表连续且按 Y 排序”的假设。

## 构建与分发

节气只作 `compileOnly` 输入，不内嵌、不成为必装模组。新增编译参数：

```powershell
"-PvoxyEclipticSeasonsDevJar=<EclipticSeasons-1.20.1-forge-0.12.18.9.1-all.jar>"
"-PvoxyMixinSquaredDevJar=<mixinsquared-forge-0.3.3.jar>"
"-PvoxyMixinSquaredApiDevJar=<mixinsquared-forge-0.2.0-beta.6.jar>"
```

也可把同名开发依赖放入已有的 `dev-mods` 目录。编译 API 从 `0.2.0-beta.6` 的嵌套 core
JAR 提取，确保不引用新版独有接口；发布仍内嵌 Forge `0.3.3`，兼容范围 `[0.2.0-beta.6,)`。
2026-09-08 已纠正此前“JarJar 一定保证选中新版 Forge wrapper”的错误假设：不同 Maven
坐标的载荷可能经过第二阶段 Forge modId 选择而加载旧版。详见
`forxy-mixinsquared-dependency-compat-2026-09-08.md`。内嵌增加约 95 KiB 框架及少量适配类，
不是内嵌节气，更没有重新内嵌 SQLite。

执行门禁为 `compileJava jarJar -x test`（包含 `reobfJarJar`），不调用任何测试任务。
静态检查包含可选 mixin 门控、服务资源路径、真实 JarJar 载荷与重混淆输出。
未来实机验收仍需覆盖：原崩溃实例进世界、积雪开关、远处积雪模型、自动重导入和节气刷新；
本次没有执行或宣称这些场景通过。

发布产物：`build/releases/voxy-forge-0.2.17-beta-forge-ecliptic-compat-20260907.jar`，
12,812,116 字节，SHA256：
`4416edfca13340b6fcbfb181075f0f1892b8881e838a8e83a2a0a9d92abbe9fe`。
相对本次修复前的 12,699,252 字节增加 112,864 字节；产物中无节气自身类、无 SQLite 载荷。
