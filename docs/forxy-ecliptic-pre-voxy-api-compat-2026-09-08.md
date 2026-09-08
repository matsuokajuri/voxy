# Closing Song 旧版节气接口门控修复

状态更新：r5 已获四整合包用户验收。本文保留修复过程；当前验收范围和剩余边界以
[最终快速审计](forxy-compat-r5-four-modpacks-audit-2026-09-08.md)为准。

## 两个实例的证据分开记录

用户反馈：“逆转未来整合包测试通过，closing song 测试不通过”。

### 逆转未来 2.3.3：r4 用户测试通过

附带路径 `versions/逆转未来2.3.3/logs/latest.log` 对应 2026-09-08 21:53～21:57 的运行，
文件 1,455,540 字节，最后修改 21:57:36，加载的是 `compat-r4-20260908.jar`。
正式 renderer 创建成功，三次模型汇总分别为 619/5138/1216 个状态映射，
21:57:35 runtime 正常关闭，21:57:36 Voxy instance 正常结束。
本轮没有摄入服务 `had an exception` 错误。

这与用户验收一致，但不代表整合包所有日志都无错误：启动时还有其他模组的资源、网络、
接口告警，以及 Voxy 创建前已有的 GL_INVALID_ENUM。记录而不扩大本轮修复范围。
此次用户验收针对 r4，不能自动转移成 r5 的实机验收。

### Closing Song 1.6.4：r4 崩溃

错误 ZIP `错误报告-2026-09-08_21.59.48.zip` 内含 Closing Song 配置与
`crash-2026-09-08_21.59.43-client.txt`，并不是“逆转未来”的日志。

- Forge 1.20.1 / 47.3.22。
- 节气 `EclipticSeasons-1.20.1-forge-0.10-pre10-2-all.jar`。
- Voxy `compat-r4-20260908.jar`。
- `EclipticSeasonsIntegration.enabled:41` 访问不存在的 `CompatModule.CommonConfig.voxyTest`
  引发 `NoSuchFieldError`；调用来自会话 client tick。

责任在本次新适配的门控：只检查 `eclipticseasons` 已安装，却假定它一定提供新版接口。

## 实际旧 JAR 的检查结果

对安装的 `0.10-pre10-2` 逐项检查，而不是仅猜测版本号：

- `CompatModule.CommonConfig` 没有 `voxyTest`、`voxyLODAutoReload`、`voxyReloadWhenSeasonChanged`。
- `ClientCon` 没有新版的 `getAgent()`。
- 没有 `compat.voxy.VoxyTool`、`VoxyEsHandler`、`VoxyClientTool`。
- mixin 清单没有任何 Voxy 条目；整个 JAR 的 class 常量池没有 Voxy 引用。

因此它不是把一个选项改名了，而是整个版本尚未实现 Voxy 扩展。不能给缺失字段强塞
一个默认值，或只捕获 NoSuchFieldError 然后继续调用同样不存在的后续接口。
旧版原本没有这些新增的 LOD 积雪/自动重导入功能；本轮不虚构它们已获支持。

## 修复

新增共享 `ForgeEclipticSeasonsCapabilities`：从 Forge 已发现的实际模组文件读取 class
元数据，不 `Class.forName`，不初始化节气配置或客户端类。

1. **未安装节气**：不链接可选集成。
2. **旧版完整缺少 Voxy 扩展**：保留旧版原有季节/渲染钩子和正常 Voxy 正式流程。
   不加载新版专用适配器；不关闭节气、不卸载模组、不修改用户配置。
3. **有 Voxy 扩展**：读取实际适配器字节码，核对它引用的全部节气字段与方法的
   owner、描述符和 static 属性（含继承的接口/方法），再接入新版原有完整功能。
   如果发现部分接口缺失/不匹配，明确报出具体成员，不将其静默当作旧版。

运行时入口、节气事件 handler mixin、旧 mixin 的取消服务使用同一个能力判断，
避免“旧补丁已取消，新 owner 却没有接上”。只做一次元数据读取，结果缓存；不增加
每帧 class 扫描，也不改变 Voxy 的 mapper、packed conversion、模型或渲染 owner。

这是可选功能的接口门控，不是备用渲染器或关闭已有功能的 fallback。针对
`0.12.18.9.1` 的积雪、季节刷新和导入实现不变；Zeta、Harium、MixinSquared 修复均保留。

## 验证边界

按用户此前要求，只有编译、打包和静态字节码/归档检查，没有运行测试任务或启动客户端。
本次静态检查覆盖实际旧版 JAR 完全缺少该扩展、新版 JAR 具备扩展的差异。
最终产物需保留完整 refmap 和取消补丁服务。新版本实机效果尚未验证；未提交、未推送。

最终 `compileJava jarJar -x test`（含 `reobfJarJar`）和 `git diff --check` 通过。
归档包含 7,422 字节 refmap、新能力门控类和原取消补丁服务。
发布：`build/releases/voxy-forge-0.2.17-beta-forge-compat-r5-20260908.jar`，
12,818,746 字节，SHA256：
`7af4197428fff73e17b412e2229dc263a3f347b1311336e57a7831d1c5626546`。
