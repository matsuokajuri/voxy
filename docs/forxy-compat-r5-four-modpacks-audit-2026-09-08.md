# Forxy compat-r5：四整合包验收与提交前快速审计

## 结论

用户确认四个整合包测试均通过。四份日志分别确认正式 Voxy renderer 创建、实际模型
生成、会话和实例正常退出；此前的节气字段错误、MixinSquared 版本阻断、Zeta 扩容
崩溃、Lithium 调色板摄入刷错均未再出现。

快速审计没有发现当前兼容修复范围内阻止提交的问题。本轮不改动已经验收的运行代码，
只整理最终验收状态和限制。此前 r1～r5 修复文档属于过程记录，最终状态以本页为准。

这不是“所有模组日志零错误”，也不等于所有可选功能组合已经逐项测试，见下文边界。

## 同一产物身份

四个实例的 `mods` 中均只有同一份：
`voxy-forge-0.2.17-beta-forge-compat-r5-20260908.jar`。

- 大小：12,818,746 字节。
- SHA256：`7af4197428fff73e17b412e2229dc263a3f347b1311336e57a7831d1c5626546`。
- 校验对象为四个整合包内实际安装的文件，不只是 `build/releases` 中的副本。

## 四份日志分别核对

日志时间均为 2026-09-08、本机 Asia/Tokyo 时区；以下实例名对应各自提供的路径。
“状态映射峰值”取日志中的各次 owner 关闭汇总，不是整个会话所有模型的累加值。

| 实例 | 日志大小 | 正式 renderer 创建次数 | 状态映射峰值 | runtime 正常关闭 | instance 正常关闭 |
| --- | ---: | ---: | ---: | --- | --- |
| 逆转未来2.3.3 | 1,454,117 | 6 | 5,748 | 22:49:39.358 | 22:49:40.466 |
| Create-Delight-Remake | 1,790,213 | 3 | 2,189 | 22:44:50.389 | 22:44:51.977 |
| Closing Song1.6.4 | 673,117 | 2 | 1,802 | 22:41:06.411 | 22:41:11.481 |
| 涟漪之篇·如涟漪之所见 | 1,565,852 | 3 | 1,721 | 22:56:39.659 | 22:56:42.469 |

另外，Create Delight 日志确认 `Ecliptic Seasons Voxy API matched`，Closing Song
确认旧版节气 `predates its Voxy extension API`，验证了这两个不同的门控分支。
四份日志均无摄入服务 `had an exception`、未知调色板异常或本次节气成员链接错误。

## 代码与分发快速审计

- **可选依赖与能力门控**：旧版/新版/未安装节气的行为分开；运行入口、handler mixin、
  旧 mixin 取消使用同一判断。不提前初始化节气类，不先取消旧桥接再静默放弃新桥接。
- **数据和模型链**：来源世界随原 job 上下文传递；原 packed conversion、mipper、
  WorldUpdater、模型队列与 GPU 上传 owner 保持不变。积雪 ID 保留独立映射，状态、
  opacity、mip 属性查询使用真实状态；每次烘焙显式传 snowy，不泄漏到后续模型。
- **导入与生命周期**：NBT 上方 section 按实际 Y 查找；自动导入共享会话原 ImportManager
  和 ServiceManager；renderer-only 季节刷新不销毁 WorldEngine 或清空存储。
- **Harium**：明确适配实际 LithiumHashPalette ABI，仍使用原局部调色板与位存储解码，
  没有按方块 get 的备用转换器，也不吞掉未知其他实现的错误。
- **Zeta**：只在构造发布前同步原处理器记录表，保留注册/取消注册、Key 和值语义，
  不关闭模块或事件，不替换事件总线。
- **构建**：MixinSquared 编译最低 API 为 0.2.0-beta.6，内嵌 0.3.3，外层 JarJar 和
  mods.toml 共用范围 `[0.2.0-beta.6,)`；refmap 生成和缺失检查保持生效。
- **产物**：本轮 `compileJava jarJar -x test`（含 `reobfJarJar`）通过。重建 JAR 与
  四包验收的 r5 逐项解压内容比较，除构建时间所在 manifest 外 **0 项差异**。
  refmap 为 7,422 字节，取消服务存在，没有内嵌节气/Zeta/Lithium 类或 SQLite。

本轮未运行自动化测试，也未再次启动客户端。运行/视觉验收来源为用户的四包测试及
对应日志，不借用早期 328 个测试或其他历史场景来扩张本轮证据。

## 明确保留的边界

1. **节气扩展开启场景未覆盖**：Create Delight 当前配置中 `VoxyTest`、
   `VoxyLODAutoReload`、`VoxyReloadWhenSeasonChanged` 全为 false。它验证了新版 ABI
   门控及正常共存，但不能据此宣布积雪 LOD、自动重导入、季节触发刷新已实机通过。
   没有为通过测试而改动这些开关。
2. **Closing Song 的 GL1282**：22:38:22.782 有两条“source and destination internal
   formats are not compatible”。与此前记录的格式复制告警文本相符，但没有调用栈，
   本轮不重新归因或宣称已修复，也不将正常视觉验收说成 GL 零错误。
3. **逆转未来的 GL1280**：22:46:58.029 出现在 Voxy renderer 创建之前，保留为独立
   现象，不据此推断本批兼容补丁是其根因。
4. 各整合包仍有自身资源/JEI/其他模组 API 告警；未移除、修改或屏蔽这些模组。

## 提交范围

只提交本批运行代码、Mixin/依赖声明、构建检查和兼容文档。四个整合包的配置、模组、
日志、存档没有被修改；所有 `run/`、`build/`、`.gradle/`、JAR、截图、原始日志和
个人工具/索引文件不纳入提交。保留当前 `Forxy` 分支，不修改远端历史。
