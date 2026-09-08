# 逆转未来启动回归：MixinSquared 依赖范围修复

状态更新：r5 已获四整合包用户验收，依赖加载阻断未再出现。本文保留修复过程；当前验收
范围以[最终快速审计](forxy-compat-r5-four-modpacks-audit-2026-09-08.md)为准。

## 根因与责任

用户提供 `错误报告-2026-09-08_1.09.43.zip`。实际为“逆转未来 2.3.3”，
Forge 1.20.1 / 47.4.18，Voxy `compat-r3-20260907.jar`。
`crash-2026-09-08_01.09.16-fml.txt` 的唯一加载失败项是：

```text
Mod voxy requires mixinsquared 0.3.3 or above
Currently, mixinsquared is 0.2.0-beta.6
```

这是此次适配引入的过严依赖约束，不是用户安装错误，也不是运行期渲染失败。
此前把“内嵌版本”直接当作“最低必要接口版本”，并假定 JarJar 能保证 Forge 选中新版，
这两个判断都不成立。不得通过关闭节气适配或删除依赖校验来掩盖。

## 为什么内嵌了新版仍加载旧版

检查实际整合包顶层 JAR 的 JarJar 元数据：

| 所属模组 | MixinSquared Maven group | 载荷版本 |
| --- | --- | --- |
| AllTheLeaks | `com.github.bawnorton.mixinsquared` | `0.3.6-beta.1` |
| Voxy r3 | `com.github.bawnorton.mixinsquared` | `0.3.3` |
| Moonlight | `com.github.bawnorton.mixinsquared` | `0.1.1` |
| Ares HUD / EpicFight Nightfall / epicfight-extra | `com.bawnorton.mixinsquared` | `0.2.0-beta.6` |

两个 Maven group 是不同依赖身份，不能仅凭相同 artifact 名认定已合并。
日志明确记录第二阶段 `UniqueModListBuilder` 最终为 `mixinsquared` modId 选择
`mixinsquared-forge-0.2.0-beta.6.jar`；同时 core 选为 `MixinSquared-0.3.6-beta.1.jar`。
Forge wrapper 的 mod 版本并不代表这次所有 MixinSquared 类的实际版本。

## 接口核对与修复

从该整合包的真实嵌套 JAR 读取 `0.2.0-beta.6`，并与此前内嵌 `0.3.3` 及包内 core
`0.3.6-beta.1` 的字节码对照：

- `MixinCanceller.shouldCancel(List<String>, String): boolean` 的二进制签名一致。
- `MixinCancellerRegistrar.register(MixinCanceller)` 及 cancel 回调入口均已存在。
- `0.2.0-beta.6` 的 Forge plugin 在 `onLoad` 中初始化 bootstrap 并调用
  `MixinCancellerLoader.load()`；loader 使用相同的 JDK ServiceLoader 服务接口。
- Voxy 只实现这个公开接口，通过同一个 `META-INF/services` 资源注册；没有调用
  新版专属方法。因此不是需要替代实现或退化路径，而是本来就不需要强制 0.3.3。

修复：

1. 最低接口版本设为已核对的 `0.2.0-beta.6`。
2. 用同一个 Gradle 变量生成外层 JarJar 范围及 `mods.toml` 范围，防止声明再次不一致。
3. 编译改为实际最低版的 core API，内嵌载荷仍保持 `0.3.3`；不会改整合包其他库。
4. 节气全部功能、MixinCanceller 服务、Zeta 同步修复和 Harium 调色板支持保持不变。
   没有关闭功能、去掉必要依赖检查、吞掉异常或改动原版 Voxy 渲染链。

新增最低 API 开发输入：
`-PvoxyMixinSquaredApiDevJar=<mixinsquared-forge-0.2.0-beta.6.jar>`。
也可和内嵌的 0.3.3 输入一起放在已有 `dev-mods` 目录。

## 验证边界

按用户此前要求，仅编译、打包、检查字节码和归档元数据；不运行测试、不启动客户端。
不修改“逆转未来”的模组、配置或存档。此次依赖门槛已按证据修正，但不能把静态
核对说成整合包已实机通过。未提交或推送。

## 同时拦截的增量构建问题

切换到最低版 API 后，第一次增量构建没有重新执行完整注解处理，产物漏掉
`voxy.forge.refmap.json`。静态归档差异检查发现这一点，未将该产物交付用户。
这不是本次用户报告的根因，但会让新 JAR 继续出现 Mixin 加载问题，因此一并修复：

- 生产 `compileJava` 禁用增量编译，使每次实际编译都重新生成完整 Mixin 元数据。
- 将 refmap 声明为编译输出，缺失时不能视为 up-to-date。
- `jarJar` 输出时检查声明的 refmap 文件存在且非空；不满足条件就终止构建。
- 本次记录到缺失产物被打包检查拒绝，随后 `compileJava jarJar --rerun-tasks -x test`
  完整重编译通过，重混淆产物带有 7,422 字节 refmap（11 个映射类）。

没有运行任何测试任务或启动客户端；上述内容属于构建与归档检查。

最终完整编译、重混淆打包及 `git diff --check` 通过。交付产物：
`build/releases/voxy-forge-0.2.17-beta-forge-compat-r4-20260908.jar`，
12,813,323 字节，SHA256：
`a46ccfef85e823a07a940a7acfa4a36047b207e57c4092c45c4565357bd64e76`。
最终归档确认 refmap、取消补丁服务及节气/Zeta/Harium 实现均在；用此文件替换 r3，
不需要用户手动升级或删除整合包内的 MixinSquared。
