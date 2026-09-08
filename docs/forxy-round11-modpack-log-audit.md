# Forxy 拾壹轮：四整合包日志分类审计

日期：2026-09-09。候选为拾壹轮 R2；构建、门禁与实机操作的完整状态见
[拾壹轮执行记录](forxy-round11-execution-record.md)。

最终状态：**四整合包本轮既定 LOD、节点、资源重载和正常退出场景均通过；九份 stop
资源快照全部归零。** 这不是“整包没有 ERROR”的声明，既有交互问题与 Create 的因果边界如下保留。

本记录区分“本轮新增的 Voxy 异常”“既有的 Voxy/平台交互问题”“其他模组或资源问题”。
不能因为日志级别是 ERROR 就认定本轮失败，也不能因为此前已出现就把它写成“与 Voxy 无关”。
这里只读检查日志，没有为消除日志而关闭功能、改配置或调整其他模组。

## 一、当前进度与证据范围

| 整合包 | 当前状态 | 本次检查的日志 |
| --- | --- | --- |
| Closing Song 1.6.4 | 实机测试结束；本篇日志审计完成 | `run/diagnostics/round11-2026-09-09/closing-song-qualified.log`、`closing-song-debug.log` |
| Create Delight Remake | 本轮既定 Voxy 场景及正常退出已完成；包内 Flywheel 新观察保留调查边界 | `run/diagnostics/round11-2026-09-09/create-delight-qualified.log`、`create-delight-debug.log` |
| 逆转未来 2.3.3 | 本轮既定 Voxy 场景及正常退出已完成；日志审计完成 | `run/diagnostics/round11-2026-09-09/reverse-future-qualified.log`、`reverse-future-debug.log` |
| 涟漪之篇·如涟漪之所见 | 本轮既定 Voxy 场景及正常退出已完成；日志审计完成 | `run/diagnostics/round11-2026-09-09/ripples-qualified.log`、`ripples-debug.log` |

基线快照在 `run/diagnostics/round11-2026-09-09/baseline/<pack>/logs/`。
诊断目录、原始日志、测试存档与截图均为忽略的本地产物，不提交到仓库。

## 二、重要的基线区别

### Closing Song：快照日志来自“无 Voxy”对照

Closing Song 基线 `latest.log` 的启动时间为 09-08 23:38，属于此前 GL1282 调查的
`VOXY_GL1282_AUDIT` 存档运行，不是与本轮候选同条件的带 r5 日志：

- 第 2766 行明确记录 `voxy (version 0.2.17-beta-forge -> MISSING)`。
- 第 2808 行记录 `Missing data pack mod:voxy`。
- 没有 Voxy renderer / WorldEngine 初始化记录。

因此，此基线可用于证明 JEI、资源、标签等问题在没有 Voxy 时也存在；但不能用它证明
本轮新增的 Voxy 初始化提示、Embeddium taint 提示或可选库警告是代码回归。
也不能将“本轮日志与基线不同”直接等同于“拾壹轮引入问题”。

此前两条 GL1282 归因另见
[Closing Song 专项调查](forxy-closing-song-gl1282-attribution-2026-09-08.md)。

### Create Delight：基线包含此前 Voxy 运行

Create Delight 基线为 09-08 22:41 起的日志，包含此前带 Voxy 的测试。
当前对照使用新的 `ForxyRound11` 测试副本，基线使用 `新的世界`；路径中的存档名不同
不代表同一类错误发生变化。启动耗时、lambda 地址、集合打印顺序及加载线程编号也需要归一化。

## 三、Closing Song 已完成的日志审计

### Voxy 本轮结果

- 未匹配到 Voxy ERROR、节点/请求所有权异常、负工作计数、队列映射不一致或 shutdown 阶段失败。
- 两份 `Forxy round11 async audit: phase=stop` 快照的全部资源字段均归零：节点、位置、
  single/child 请求、geometry、geometryBytes、work、四种输入队列、SyncResults、待上传、
  待节点写入，以及缓存条目/字节。
- 最终 stop 快照时间为 **02:39:15.130**；02:39:15.142 记录正常 renderer shutdown，
  随后记录 WorldEngine 和 network-session 关闭；02:39:40.844 记录 Minecraft `Stopping!`。
- 本轮归档日志未记录 GL1282；这只是本次日志结果，不表示所有光影/枪械操作组合都已排除 GL 错误。

### 已有或非本轮渲染故障的日志

| 类别 | 证据与分类 |
| --- | --- |
| JEI API 不匹配 | 当前 `latest.log:4534` 的 `IRecipeRegistration.addIngredientInfo(ItemLike, Component[])` `NoSuchMethodError`，在无 Voxy 基线 `latest.log:4434` 已出现。不能归因于拾壹轮生成器或节点修改。 |
| 模型与动画资源 | Mowzie's Mobs、Iron Furnaces、SlashBlade 模型失败，以及 GeckoLib `melee_3` 解析失败，均可在无 Voxy 基线中找到相同资源名称。 |
| 资源包、属性与标签 | FancyMenu `luomuqu` 缺少 images 目录、AttributeFix 未知属性、缺失 tag 等多数正文与基线相同。未更改这些模组/资源。 |
| Embeddium taint | 表示其他模组正在修改 Embeddium 内部类，不等于发生渲染异常。本轮含 `voxy` 的提示与基线没有 Voxy 的区别一致。 |
| 可选 DH 导入库 | 当前 `latest.log:4114` 提示 SQLite JDBC 或 XZ 不可用，故 DH 导入不可用。这是既有外置库政策的能力提示，不是普通 LOD 渲染失败；不能把此包的普通视觉通过当成 DH 导入通过。 |
| 音频/网络/计时 | OpenAL 删除 stream buffers 的错误、空音效、Xaero 更新检查超时、加载耗时等须按各自模块和操作区分；不是本轮节点协议异常的证据。 |
| StairBlock CoreMod ERROR | **有 Voxy 关联的既有平台交互，不能笼统归类为纯第三方噪声。** 详见第五节。 |

初次归一化比较中，本轮 2,027 条 WARN/ERROR 有 2,005 条正文可直接匹配无 Voxy 基线；
剩余 18 种唯一正文包括计时、Mixin 方法后缀、模组存在性提示及音频状态差异。
此统计只是定位工具，不替代逐项归因，也不代表“不匹配条目都由 Voxy 产生”。

## 四、Create Delight 完成结果

主任务实机记录覆盖：重进测试副本、传送至 `(41000, 160, 41000)` 新区域、区块渲染距离
`8→31→8`、F3+T 资源重载。完成后山地、村庄和稀树草原画面连续完整；对应截图为
`run/diagnostics/round11-2026-09-09/create-delight-range-reload.png`。

最终归档日志未匹配到 Voxy ERROR、节点/队列所有权异常、renderer shutdown 阶段失败或 GL1282。
三份 stop 快照分别位于：

| 日志行 | 时间 | 资源结果 |
| --- | --- | --- |
| 10400 | 02:43:51.366 | 节点、请求、几何、work、四队列、SyncResults、缓存等全部为 0 |
| 18493 | 02:56:27.546 | 同上，全部为 0 |
| 20436 | 02:57:22.914 | 同上，全部为 0 |

02:57:22.567 记录所有维度保存完成；02:57:22.922 记录 renderer 正常关闭，随后 WorldEngine 和
network-session 正常关闭；02:57:57.072 记录实例关闭及 Minecraft `Stopping!`。
本包既定 Voxy 场景与回收门禁通过，不等于其所有模组和资源都没有问题；下面单列最终新增观察。

### VillagerTrades LVT 注入错误：明确为既有

本次归档日志第 1335 行，时间 **09-09 02:41:16.188**：

```text
fabric-object-builder-v1.mixins.json:
TradeOffersTypeAwareBuyForOneEmeraldFactoryMixin -> failOnNullItem
VillagerTrades$EmeraldsForVillagerTypeItem ... incompatible changes at opcode 34
```

对照证据：

- 基线 `latest.log:1336`，**09-08 22:42:05.480**：同一方法、同一 callback、同一 opcode 34，正文相同。
- 基线 `debug.log:20738`：同一 Injection error。
- 基线 `debug.log:20736` 另有该 Mixin 的 JAVA_16 / class version 61 提示。

结论：这是此前就存在的 Fabric 兼容层注入问题，不是本轮 `RenderGenerationService`
重载或 token 修改新增的注入错误。记录此结论不等于证明该第三方注入本身没有功能影响。

### 其他日志分类

| 类别 | 对照结果 |
| --- | --- |
| Accessories Interface Mixin | `ContainerMixin` non-public method 错误在基线 `latest.log:649` 已有。 |
| Amendments / ModernFix | double cake 模型生成错误、`registerReloadListener` 时机警告均已有同类同正文基线。 |
| PonderJS / KubeJS | `PonderScene.getWorld()` 为 null 的错误在基线 `latest.log:2402–2404` 已有。烹饪配方缺 experience 的日志也已存在；其中的 `Falling back to vanilla` 是 KubeJS 的行为描述，不是 Forxy 新增 fallback。 |
| Jupiter / GuideME / tags | Cloth 配置源扫描失败、指南 navigation parent 缺失及若干 tag 引用缺失，已有基线；集合打印顺序可能不同。 |
| 计时与更新检查 | deferred task 时长、ModernFix 加载耗时、lambda 地址及网络超时不能按逐字差异直接判回归。 |
| SimpleBackups + Voxy LOCK | 与 Voxy 活动数据库文件有关，但相同错误已有基线；详见下一小节。 |
| StairBlock CoreMod ERROR | 带 r5 的 Create 基线 `latest.log:538/712` 已有；详见第五节。 |

首次检查点快照中，3,075 条 WARN/ERROR 有 3,050 条正文直接匹配基线，其余 25 种唯一正文
主要是动态值、顺序差异、加载卡 tick 和存档路径。
完成重进、新区域和重载后，最终日志为 **7,791 条 WARN/ERROR，其中 5,846 条正文直接匹配基线，
510 种唯一正文未直接匹配**。其中 458 种来自 Minecraft `ModelBakery`，不能把数量增加直接
归因于 Voxy 状态机，也不能继续沿用启动检查点的统计代表最终日志。

### 最终新增观察与边界

- **一条 Voxy WARN**：第 18016 行、02:54:12.774，NodeManager 对已经有在途请求的节点
  `3811 / 1@[634, 3, 641]` 忽略重复请求。这是该接口允许的重复 GPU 请求处理，
  不属于负计数、所有权损坏、静默关闭功能或新增 fallback；随后完整画面及最终回收均通过。
- **模型资源警告**：新区域/资源重载触及更多模型。例如第 20350–20351 行显示
  `Squareful v3.15forMC1.20.1.zip` 的 `scaffolding.json` 包含未知 blockstate property `1`。
  部分日志由 Voxy 按需模型烘焙线程触发 Minecraft 读取资源时输出，但报出的具体问题是资源定义。
  不把所有 458 种唯一 ModelBakery 警告都归为单一原因；本轮没有修整合包资源。
- **四条 Flywheel NPE，已定位 null 参数、仍保留因果边界**：第 17834、17852、17916、17934 行，分别位于
  02:51:23 和 02:52:26。调用链为 `ConcurrentHashMap.computeIfAbsent → RendererReloadCache.get
  → Models.partial → Create PackageVisual`，所示调用栈没有 Voxy 帧。本轮快照基线没有匹配到
  这类 ERROR，因此不能标作“已证明旧有”或“已彻底排除与 Voxy 的直接/间接关系”。
  本地实际依赖字节码已定位为缺失的 `PartialModel` 被传作 null key，详见下一小节。
  没有记录具体箱子 item，也没有进行有/无 Voxy 或 r5/r2 的同场景单变量对照，
  因此不把“调用栈没有 Voxy”写成“已完整排除 Voxy 改变时序的间接作用”。
- 新区域还出现实体/物品数据、template pool 和 worldgen block-entity 提示；与同存档同轨迹
  不一致的旧基线不构成完整因果对照。它们不改变本包已完成的 Voxy 场景与回收记录。

### Flywheel NPE：确切 null 参数链

检查对象为本地实际 `create-1.20.1-6.0.8.jar`，以及运行栈所用的 Flywheel **1.0.4**。
后者由 `vanillin-forge-1.20.1-1.1.3.jar` 的 `META-INF/jars/flywheel-forge-1.20.1-1.0.4.jar`
提供；不因为 Create 自身另内嵌 1.0.5 就拿不同版本代替实际加载版本作结论。
只读取原包并生成忽略目录中的诊断副本供 `javap` 检查，没有修改整合包。

已确认的调用链：

1. `Create PackageVisual.java:25–28` 读取 `entity.box`。空栈或非 `PackageItem` 会先被替换为
   `CARDBOARD_BLOCK`，随后用该物品的注册 ID 查询 `AllPartialModels.PACKAGES`，获得局部变量 `model`。
2. 第 30 行直接调用 `Models.partial(model)`，没有检查查表结果是否为 null。
3. Flywheel `Models.java:52` 将 `partial` 原样传给静态 `PARTIAL` cache 的 `get`。
4. `RendererReloadCache.java:26` 只执行 `map.computeIfAbsent(key, factory)`。
   `map` 在构造时建立为 `ConcurrentHashMap`；此 `PARTIAL` cache 的 final `factory` 来自
   `Models` 静态初始化创建的非空 lambda。
5. 此次 Create 实机使用 **Eclipse Adoptium JDK 21.0.11+10**，不是 dev 环境的 JDK 17：
   归档 `create-delight-qualified.log:2` 明确报告 java version 21.0.11。已使用本地匹配的
   `C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot/lib/src.zip` 复核，
   `ConcurrentHashMap.java:1692` 为 `if (key == null || mappingFunction == null)`，
   第 1693 行抛出 NPE；同一 JDK 的 `javap` 也确认先检查两个参数，再进入哈希/缓存查找。
   结合上述传参和非空 factory，触发的是缺失 `PartialModel` 作为 **null key**。
   这不是“渲染缓存被清空后对象变成 null”：Flywheel 的 reload 操作仅 `map.clear()`，
   不会将 final factory 或 map 置空。

`AllPartialModels.PACKAGES` 的内建条目按 `PackageStyles.STYLES` 建立；若最终 item ID 没有
对应条目，或走到没有包裹模型映射的替代物品分支，上述无检查调用就会把 null 送入 cache。
**日志没有记录具体 `box` item，尚不能判定本次究竟是哪一种实体数据或注册缺口。**

时间线也排除了一个过早的猜测：传送发生于 02:50:48，四条 NPE 在 02:51:23 / 02:52:26；
本次调大视距记录在 02:52:51，F3+T 资源重载在 02:56:16。因此不能把这四条错误说成是
“随后那次 F3+T 清 cache 引起”。

本轮 Forxy Java delta 未修改 Create 包裹、`AllPartialModels` 或 Flywheel cache；已编译的
390 个 Voxy class 也未发现对这些 Flywheel/Create 包模型类的直接引用。依赖 debug 日志中
`EntityStorage` 的额外 Mixin 来自 Colorwheel，并非本轮 Forxy 新增的 Mixin。
这些证据排除了“本轮直接改写该 cache/包模型注册数据”的路径；**不代替同场景 A/B，
不能完整排除加载/渲染时序变化使已有缺口更易被触发。** 本轮没有扩围修复外部模组。

### SimpleBackups 无法复制 RocksDB LOCK

- 当前 `latest.log:10292`，02:42:24.450：无法备份
  `saves/ForxyRound11/voxy/9e58fb0f65fa8c5da5ca74dadae2466d/storage/LOCK`。
- 基线 `latest.log:10304`，09-08 22:43:21.008：同样无法备份
  `saves/新的世界/voxy/9e58fb0f65fa8c5da5ca74dadae2466d/storage/LOCK`。

这是备份模组与正在使用的 Voxy RocksDB 锁文件之间的既有交互，不能称为完全与 Voxy 无关，
也没有证据表明是拾壹轮新增的节点或渲染故障。本轮未修改备份流程；未借此保证运行中备份
的数据一致性或完整性，这与 LOD 渲染验收是不同事项。

## 五、StairBlock AT 与 Forge CoreMod：单独保留关系

本轮 Closing Song `latest.log:439` 的错误为：

```text
Error occurred applying transform of coremod coremods/field_to_method.js function stairsblock
java.lang.IllegalStateException: Field f_56859_ is not private and an instance field
```

当前及本轮之前的 Git HEAD 均含相同的访问变换声明：

```text
# src/main/resources/META-INF/accesstransformer.cfg:9
public net.minecraft.world.level.block.StairBlock f_56859_ # baseState
```

Forxy 将该字段公开，而 Forge 这项转换要求目标字段仍是 private：两者存在明确的私有性约束
冲突关系。不能把这条 ERROR 简写成“第三方纯噪声”或“已证明与 Voxy 无关”。

同时，带此前 Voxy 的 Create Delight 基线 `latest.log:538/712` 已有相同错误，且该 AT 本轮没有
修改；所以它不是拾壹轮新增。Closing Song 的基线未出现此错误，与那次没有安装 Voxy 的事实一致。
本轮没有单独移除这条 AT 做因果对照，因此不宣称已证明它是所有组合中的唯一触发因素。
该既有交互只记录，不在本轮日志审计中扩围改动。

## 六、逆转未来完成结果

主任务已完成视距切换、资源重载及对应视觉场景，截图为
`run/diagnostics/round11-2026-09-09/reverse-future-range-reload.png`。
最终日志没有 Voxy ERROR、节点/队列所有权异常或 renderer shutdown 阶段失败。

- 第 8894 行，**03:11:41.349**：第一次 stop，全部节点/请求/几何/work/队列/SyncResults/缓存资源为 0。
- 第 9083 行，**03:12:27.200**：第二次 stop，同样全部为 0。
- 03:12:27 记录 WorldEngine/network-session 关闭及存档保存流程；03:13:00.387 记录 Voxy
  实例关闭和 Minecraft `Stopping!`，随后 AAAParticles 也记录正常关闭。

归一化比较：2,427 条 WARN/ERROR 中 2,410 条正文直接匹配带 r5 基线；15 种未直接匹配正文
主要是 10 个不同坐标的无效悬挂实体、动态计时、未知乘客同步、空结构池和 JAR 文件名变化。
这并不表示所有运行场景相同，尤其新区域涉及不同实体与地形数据。

需单独记录的关系：

| 日志 | 分类 |
| --- | --- |
| 一条 GL1280 / `GL_INVALID_ENUM` | 当前第 1805 行、03:01:15.650；旧基线同为第 1805 行、09-08 22:46:58.029，正文相同。当前发生在 Voxy renderer 首次创建（03:01:53.044）之前，非拾壹轮新增节点/生成错误。未进一步定位具体 GL 调用，不能套用 Closing Song 的 GL1282 因果结论。 |
| ModernFix `PathResourcePack` 提示 | 基线第 1203 行也指出 Voxy r5 JAR 的资源包根路径调用方式；当前主要差别是候选 JAR 文件名。这是涉及 Voxy 的既有资源加载提示，不写成完全无关的第三方噪声。 |
| DH SQLite/XZ 不可用 | 当前第 6993 行，基线第 6839 行已有相同警告，沿用外置可选库政策；本包普通渲染通过不证明 DH 导入可用。 |

因此，本包的既定 Voxy 场景与正常回收门禁通过；**不是“整包日志没有 GL 错误或其他模组提示”**。

## 七、涟漪之篇完成结果

本轮既定视距、LOD 与资源重载场景已完成，对应截图为
`run/diagnostics/round11-2026-09-09/ripples-range-reload.png`。
归档日志没有 Voxy ERROR、节点/队列所有权异常或 renderer shutdown 阶段失败，未记录 GL1282/GL1280。

- 第 12130 行，**03:26:50.856**：第一次 stop，全部节点/请求/几何/work/队列/SyncResults/缓存资源为 0。
- 第 13568 行，**03:29:02.582**：第二次 stop，同样全部为 0。
- 03:29:02.955–.956 记录所有维度保存完成；03:29:21.705 记录 Voxy 实例关闭，
  03:29:21.709 记录 Minecraft `Stopping!`。主任务同时确认测试 Java 进程正常退出。

最终归一化比较：5,623 条 WARN/ERROR 中 4,672 条正文直接匹配基线；488 种唯一正文未直接匹配。
其中 458 种来自 `ModelBakery`，主要是在新区域/资源重载过程中进一步触及的模型资源问题，
例如 Refined Storage `cover` / `hollow_cover` 的 item JSON 不存在，以及 Crabber's Delight
`seashells` 某些 state variant 未定义模型。不把每一种未匹配资源提示都认定为拾壹轮新增缺陷。

| 本轮观察 | 分类与边界 |
| --- | --- |
| 7 条 Voxy NodeManager WARN | 第 8091–8098 行附近、03:19:04，均为对已有在途请求的节点忽略重复 GPU 请求。属于本轮定义的正常重复事件处理，不是所有权损坏或停止渲染。后续画面与两次完整回收通过。 |
| DH SQLite/XZ 不可用 | 当前第 6488 行，旧基线第 6307 行已有相同外置可选库能力提示；普通 LOD 场景通过不等于 DH 导入可用。 |
| ModernFix Voxy 资源包路径提示 | 带 r5 的基线第 1365 行已有；当前候选 JAR 文件名不同，不是拾壹轮新增。保留涉及 Voxy 的既有资源加载关系，不归为完全无关。 |
| ModelBakery / ModernFix 资源提示 | 报告具体缺失 JSON/variant；本轮不修整合包资源，也不宣称所有模型变体已完整验证。 |
| 新区域实体/区块数据 | 包括不同坐标的无效悬挂实体、DUMMY block entity 对应位置已为水、SculkShrieker 数据缺 key、未知乘客及空/旧数据提示。无本轮节点协议异常证据；不同区域不能当成同场景因果对照。 |
| 计时与模组检测 | ModernFix/JEI 耗时、服务器短暂落后和 Embeddium 检测到 Oculus/SodiumDynamicLights 改内部类，按对应组件与具体行为分类，不把日志级别等同于 Voxy 故障。 |

## 八、最终完成状态与保留边界

- [x] Create Delight 后续完整场景、最终退出与归档日志审计。
- [x] Create Delight 四条 Flywheel NPE 的源码/字节码 null 参数定位；具体 box 与同场景 A/B 未记录，作为因果边界保留。
- [x] 逆转未来本轮候选日志及实机验收。
- [x] 涟漪之篇本轮候选日志及实机验收。
- [x] 四包完成后更新本篇的最终状态；保留上述基线区别和能力边界，不用“全部 ERROR 无关”代替分类。

| 整合包 | 正常 stop 资源全零快照 | 本轮 Voxy ERROR / 节点所有权异常 |
| --- | --- | --- |
| Closing Song | 2 | 未发现 |
| Create Delight | 3 | 未发现 |
| 逆转未来 | 2 | 未发现 |
| 涟漪之篇 | 2 | 未发现 |

最终结论：**四包的既定 Voxy LOD、节点、资源重载、退出和回收场景均已完成并通过，
未发现 Voxy ERROR 或本轮节点协议故障。** 不抹去以下边界：

- Create 四条 Flywheel NPE 已定位为包裹 `PartialModel` 缺失形成 null key，但没有具体 box item
  或同场景 r5/r2 A/B，未完整排除间接时序触发；没有扩围修复外部模组。
- 逆转未来仍记录基线已有 GL1280；不能将 Closing Song 的 GL1282 调查结果套用于另一错误。
- StairBlock AT、ModernFix Voxy 资源包提示、SimpleBackups 活动数据库 LOCK 是有 Voxy 关联的既有交互，
  不是纯第三方噪声，也不是本轮新增问题。
- 外置可选库缺失时 DH 导入能力受限；四包普通渲染通过不替代专门导入/所有模组组合/真实 VR 验收。
