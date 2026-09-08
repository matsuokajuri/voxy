# Harium / Lithium 调色板导致 LOD 缺失的修复

状态更新：r5 已获四整合包用户验收，摄入刷错未再出现。本文保留修复过程；当前验收范围
以[最终快速审计](forxy-compat-r5-four-modpacks-audit-2026-09-08.md)为准。

## 当前证据

用户在替换 compat-r2 后可以进入 Create-Delight-Remake 世界，但没有 LOD 且持续刷错。
检查其 `logs/latest.log`（本次读取时 313,319,032 字节、最后修改 2026-09-07 23:43:37）：

- 23:38:49.638 起，区块摄入服务持续抛出：
  `IllegalStateException: Unknown block palette type: me.jellysquid.mods.lithium.common.world.chunk.LithiumHashPalette`。
- 失败位置为 `WorldConversionFactory.setupLocalPalette:68`，经 `convert ->
  VoxelIngestService.convertSection -> ingestNow -> processJob` 抛出。
- 对整份日志的 `had an exception ...` 服务错误归类，只有这一种，共 **96,324** 条。
  报错发生在不同摄入/Embeddium 工作线程，不是只影响某一个方块模型。
- 23:38:51 正式渲染器成功创建。关闭时仅有 1～2 个映射模型，与地形无法完成摄入一致。
- 用户已经正常退出世界/游戏；日志记录最终 runtime shutdown complete。

原有启动期还存在其他整合包警告/错误，但不能把这些统称为本次 LOD 缺失的原因。
本次不修改其他模组的配置或屏蔽它们的日志，也不把“能进世界”当作此前积雪视觉验收。

## 根因与原版对照

实际提供该类的文件是 `Harium-mc1.20.1-1.0.0.jar`：其元数据 `modId=harium`，
声明为 Radium/Lithium 的 Forge 分支，保留 `me.jellysquid.mods.lithium` 类名。
不是要求用户再安装一个 Lithium。

原版 Voxy 提交 `192721a7d` 的 `WorldConversionFactory.setupLithiumLocalPallet`
已经有 LithiumHashPalette 分支：遍历调色板的 `getSize()/valueFor(i)`，建立局部
Mapper ID 表，然后与原版调色板一样直接解码 `SimpleBitStorage` / `ZeroBitStorage`。

Forge 版在迁移时只保留了 Linear / HashMap / SingleValue / Global 调色板，漏掉这一
原作者已经实现的可选分支。这是缺失的移植功能，不能用降级读取或关闭 Harium 来代替。

本次已读实际 Harium 字节码：

- `LithiumHashPalette<T>` 实现 Minecraft `Palette<T>`。
- `getSize()`（SRG `m_62680_`）返回 entries 的有效数量。
- `valueFor(i)`（SRG `m_5795_`）按原 palette ID 读取 entries；越界返回 null。
- `copy()` 保留 entries、size、indexBits 及引用映射；没有重排传给 packed storage 的 ID。

这些正是原版 Voxy 的局部调色板构建契约。

## 修复范围

`setupLocalPalette` 现在显式识别 Forge LithiumHashPalette，并复用与原版一致的
HashMap 调色板转换循环：保留状态缓存、Mapper ID、瞬时空条目的原 sentinel 语义。
按实际类名及父类识别，不依赖 `lithium` mod id，不链接/内嵌 Harium 类，也不成为必装依赖。

后续流程不变：

```text
LithiumHashPalette -> 原局部 palette cache -> 原 packed-storage 解码
 -> 现有节气附加处理（开启时） -> 原 mipper -> WorldUpdater -> 正式渲染链
```

没有 4096 次 `PalettedContainer.get(x,y,z)` 的备用路径，没有将未知调色板假定为空气，
没有抑制异常来伪装成功。其他未知调色板/存储仍按原契约明确报错。
此前节气完整适配、Zeta 注册表同步修复全部保留。

## 构建与验证边界

按用户此前要求：只编译、重混淆打包与静态检查，不运行测试、不启动客户端。
因此本次修复尚未通过整合包运行或视觉验收；旧版测试记录不能证明本次已通过。
不清理或改写用户已有 LOD 缓存、存档、配置及原日志。

`compileJava jarJar -x test`（含 `reobfJarJar`）通过，`git diff --check` 通过。
重混淆字节码已核对：新分支调用实际 Harium 所实现的 `Palette.m_62680_/m_5795_`，
仍解码 `SimpleBitStorage` / `ZeroBitStorage`，没有链接 Harium 专属类型或内嵌其 JAR。

发布文件：`build/releases/voxy-forge-0.2.17-beta-forge-compat-r3-20260907.jar`。
大小 12,813,311 字节，SHA256：
`30565c5893f93ccf32cc113807b66152c889c37cd74544acbce867b513f8e9fd`。
替换旧 Voxy JAR 使用；无需删除缓存或卸载 Harium。未提交、未推送。
