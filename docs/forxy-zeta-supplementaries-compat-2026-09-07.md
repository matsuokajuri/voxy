# Supplementaries / Quark / Zeta 加载期兼容修复

状态更新：r5 已获四整合包用户验收，原加载期崩溃未再出现。本文保留修复过程；当前验收
范围以[最终快速审计](forxy-compat-r5-four-modpacks-audit-2026-09-08.md)为准。

## 本次报告

用户提供 `错误报告-2026-09-07_21.38.41.zip` 与加载失败截图。
报告内为 `crash-2026-09-07_21.38.18-fml.txt`，实例为 Create-Delight-Remake：

- Forge 1.20.1 / 47.4.16。
- Supplementaries `1.20-3.1.42`。
- Zeta `1.0-31`。
- 当前 Voxy 文件为上一版 `voxy-forge-0.2.17-beta-forge-ecliptic-compat-20260907.jar`。

实际失败链为：

```text
SupplementariesForge 构造
 -> Supplementaries.commonInit / ModRegistry.init
 -> CompatHandler.initOptionalRegistries / QuarkCompat.init
 -> ZetaEventBus.subscribe / ForgeZetaEventBus.subscribeMethod:87
 -> convertedHandlers.put
 -> Object2ObjectOpenHashMap.rehash:1275
 -> ArrayIndexOutOfBoundsException: Index 1024 out of bounds for length 513
```

这次不是节气的 `MixinVoxelIngestService` 注入失败，也不是渲染方块数组越界。
日志确认上一版的 MixinCanceller 已注册，并已接替节气的 ClientLevel mixin；
但此次在模组构造阶段就停止，**不能据此声称其后 ingest 或积雪功能已通过实机验证**。
仅凭这份报告也不能断言 Voxy 引入了该竞态，或卸掉 Voxy 就能根治它。

## 对照安装包源码得到的定位

检查实际安装的 `Zeta-1.0-31.jar` 的字节码与反编译源码：

- `ForgeZetaEventBus.convertedHandlers` 是 `Map<Key, Object>` 字段，实际实例为未同步的
  `Object2ObjectOpenHashMap`。
- 构造器只初始化字段；不存在依赖此字段的提前注册调用。
- `subscribeMethod` 先调用原 `remapAndRegister`，再直接向表中 `put`。
- `unsubscribeMethod` 先从同一表 `remove`，随后才在 Forge bus 锁内取消注册。
- 此表在该类中的使用只有这些 put/remove，没有绕过包装器的别名或迭代。
- 原类虽对部分 wrapper 注册和取消注册操作加锁，但没有保护这张表。

因此在 Forge 并行构造、Quark 模块和 Supplementaries 联动同时注册处理器时，表的扩容
可以发生竞争；本次 rehash 的索引与 backing array 长度不一致与该缺陷吻合。
无需修改 fastutil 库版本、渲染状态 ID 或任何积雪编码。

## 最小修复

新增仅在安装 `zeta` 时应用的 `ForgeOriginalVoxyZetaEventBusMixin`，在构造返回、对象
发布之前，将**原有同一张表**包装为 `Collections.synchronizedMap`。

- 保留原 Key 的 equals/hashCode、所有 listener 对象以及 Map 的 null 语义。
- put/remove 使用同一互斥锁，不再同时修改 fastutil 的数组、容量和 size。
- 不重写 subscribe/unsubscribe 方法，不移除任何处理器，不吞异常或重新尝试失败注册。
- 不关闭 Supplementaries、Quark、Zeta、节气或其联动；不改变原事件映射、优先级、
  generic listener、Forge 注册/取消注册、事件分发路径。
- 锁只保护处理器记录表；没有把整个模组加载或游戏事件分发改成单线程。
- Zeta 不作为编译或运行时必装依赖；无 Zeta 时 mixin 门控关闭，使用字符串目标及
  JDK Map 类型，不提前链接 Zeta 类。

这属于第三方 Forge 加载期的线程安全适配，原版 Voxy 没有对应 Zeta owner；不改动
原版 Voxy 的正式渲染、转换、存储和生命周期机制。上一版节气完整适配全部保留。
本补丁针对报告确认的这一个记录表，不宣称修好了 Zeta 所有可能的并发问题。

## 验证边界

按用户此前要求，不执行测试、不启动游戏。执行编译、重混淆打包和产物静态检查。
运行期与整合包启动验收仍未执行；旧的测试通过记录不能作为本补丁的验收证据。
整合包原文件、配置、存档均不修改，代码未提交/推送。

`compileJava jarJar -x test`（含 `reobfJarJar`）通过，`git diff --check` 通过。
产物静态检查确认可选 mixin 已打包，上一版节气服务仍保留，没有内嵌 Zeta 类。

合并修复产物：`build/releases/voxy-forge-0.2.17-beta-forge-compat-r2-20260907.jar`。
大小 12,813,068 字节，SHA256：
`df0be6f92100edbc61cb65d9d88363d7146b8a102572b78f572eee209f5328cf`。
使用时替换旧的 Voxy JAR，不要将两份同时放在 mods 中。
