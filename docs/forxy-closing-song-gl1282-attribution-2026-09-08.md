# Closing Song 的两条 GL1282：调用栈、格式与无 Voxy 对照

## 结论

本次两条 `source and destination internal formats are not compatible` 的机制已复现：
**TACZ 枪械模型首次要求启用模板缓冲，改变了主深度附件格式；Mekalus 当时两份深度
副本尚未同步格式，随后进行的两次图像复制分别失败。**

完整移除 Voxy 后，同样出现两条错误，纹理格式变化和复制调用点一致。因此，在本次
Closing Song 配置中，Voxy 不是这两条错误的根因，也不是发生该错误的必要条件。
这不代表所有模组组合的所有 GL1282 都与 Voxy 无关。

本次只调查，没有修改 Voxy 或第三方模组的正式代码，没有屏蔽错误或实施修复。

## 对象与保护措施

- Closing Song 1.6.4，Forge 1.20.1 / 47.3.22，Java 21.0.11。
- 实际光影模组是 `mekalus-mc1.20.1-1.8.0.1.jar`，modId 为 `oculus`。
- 光影包：`ComplementaryUnbound_r5.8.1.zip`，启动时已开启。
- TACZ：`1.1.7-hotfix2`；栈中还包括 Yes Steve Model `2.6.5-forge+mc1.20.1`。
- A 使用已验收的 Voxy r5；B 将该 JAR 移出可加载后缀，完全不加载 Voxy。
- B 仍保留与 A 相同的 MixinSquared 0.3.3，避免把 Voxy 的共享依赖变化混入对照。
- 原日志、options、Oculus/PCL 设置先备份；从原存档复制独立测试世界，未进入原存档。
- A、B 分别从相同原始存档副本启动，相同位置、相同快捷栏、相同窗口 854×480。

## 最小复现动作

用户回忆做过开关光影、用刀、开镜、开枪。自动复现发现不需要完成全部动作：

1. 新进程进入测试副本，光影开启，尚未持有枪械。
2. 初始两次主深度复制的源、目标格式均为 `0x1902`。
3. 按数字 **7**，切到快捷栏中的 AWP：`tacz:modern_kinetic_gun`，
   `GunId=cib:cs_awp`，`AttachmentSCOPE=tacz:scope_contender`。
4. 尚未开镜、开枪即产生两条目标错误。

## 直接触发链（B，无 Voxy）

23:40:59.296，独立探针在第一次 `RenderTarget.enableStencil` 入口捕获：

```text
Mekalus ShadowRenderer.renderShadows / renderEntities
 -> 玩家实体渲染 / Yes Steve Model 事件与持有物品渲染
 -> TACZ GunItemRendererWrapper
 -> BedrockGunModel.render / lambda$render$28:305
 -> RenderHelper.enableItemEntityStencilTest:62
 -> Minecraft RenderTarget.enableStencil
```

这里不是“开镜代码”才第一次执行，而是阴影实体渲染中已经渲染了持枪模型。
YSM 出现在本次实际链中；没有做移除 YSM 的对照，因此不额外认定 YSM 是唯一必要条件
或将它单独定为缺陷所有者。

随即主深度纹理（两轮编号均为 7）从：

- `0x1902`：`GL_DEPTH_COMPONENT`，纯深度；
- 变为 `0x8CAD`：`GL_DEPTH32F_STENCIL8`，深度与模板合并。

枚举值同时与安装包的 `DepthBufferFormat.fromGlEnum` 对照，不靠纹理名字猜测。

## 两条错误分别是什么

| 错误 | 源 → 目标 | 源格式 | 当时目标格式 | 实际调用 |
| --- | --- | --- | --- | --- |
| 第一条 | 7 → 443 | `0x8CAD` | `0x1902` | `RenderTargets.copyPreHandDepth:228` |
| 第二条 | 7 → 442 | `0x8CAD` | `0x1902` | `RenderTargets.copyPreTranslucentDepth:216` |

两者都经 `DepthCopyStrategy$Gl43CopyImage.copy:114` 调用原生
`GL43C.glCopyImageSubData`；尺寸都是 854×480，报错与尺寸不匹配无关。
目标分别是“无手部”和“无透明物”的深度副本。因此原日志的重复两条并不是同一行
被简单重复打印，而是两个独立复制调用各失败一次。

## 有 / 无 Voxy 的实测证据

以下时间为本机 JST（原独立探针日志使用 UTC，已加 9 小时）：

| 对照 | 切枪时刻 | 第一条 GL1282 | 第二条 GL1282 | 随后两目标均变为正确格式 |
| --- | --- | --- | --- | --- |
| A：Voxy r5 存在 | 23:31:09.532 | 23:31:09.715 | 23:31:09.788 | 23:31:09.871～.875 |
| B：Voxy 完全不加载 | 23:40:59.188 | 23:40:59.311 | 23:40:59.379 | 23:40:59.449～.452 |

每轮独立探针和 Minecraft 原日志均记录两条相同格式不兼容错误，之后未继续刷该错误。
两轮错误栈都位于 Mekalus 的同一复制路径；B 没有 Voxy mod 及 renderer。

## 为什么稍后自行恢复

安装包源码显示 `RenderTargets.resizeIfNeeded` 发现深度格式变化时，会 resize
`noHand`、`noTranslucents` 并标记 dirty。两个 `copyPre...Depth` 方法本身则直接使用已缓存
状态和复制策略，没有在这里重新核对主深度附件格式。

本次主附件在渲染过程中升级格式，更新副本的时机晚于这两个复制调用。随后观察到
副本格式也更新为 `0x8CAD`，复制恢复匹配。这说明是短暂的格式同步时序问题，不是
持续缺少 LOD 数据、Voxy 模型烘焙失败或存档损坏。失败复制当刻的视觉影响未做像素级量化。

## 诊断方法与限制

使用独立 Java agent，而不是修改 Voxy JAR，这样 B 不加载 Voxy 时仍能使用同一复制
诊断逻辑。探针在原复制前只查询实际纹理格式和尺寸，不调用 `glGetError`、不改变格式
或复制算法；复制期间暂开同步 debug callback，返回后恢复此前状态，以获取同步调用栈。
B 额外记录了首次 `enableStencil` 入口；该附加钩子只打印栈。
未出现探针内部失败或同步状态恢复失败。

原始证据保存在工作区忽略目录 `run/diagnostics/gl1282-2026-09-08/`：
`baseline/logs__latest.log`、`with-voxy-A.log`、`with-voxy-latest.log`、
`without-voxy-B.log`、`without-voxy-latest.log`，以及实际安装包反编译的
`RenderTargets` / `DepthBufferFormat`。
诊断源与 JAR 仅在这个忽略目录中，不是正式运行路径，不提交。

后续若修复，应围绕“主深度附件变更通知与复制前副本同步”处理；不需要改 Voxy LOD
着色、换一个备用渲染器或屏蔽 GL 错误。本轮未擅自实施该修复。

## 清理记录与最终还原（2026-09-08 23:59）

A 已正常结束。B 于 23:41:37 请求正常退出，日志确认所有维度已保存，但进程 47152
卡在 Xaero World Map 的退出清理：Render thread 位于
`MapProcessor.waitForLoadingToFinish:641`，Server thread 等待
`WorldDataHandler.onServerWorldUnload:106` 的 capabilities 锁。这与已完成的 GL 复现是
独立问题；未强杀进程，已请求用户仅结束此次测试进程的许可。

用户随后手动关闭测试进程；再次检查确认 PID 47152 和 PCL 进程均已退出。
代理没有强杀 Java。

- PCL/Setup.ini、options.txt、oculus.properties 已恢复，逐文件 SHA256 与测试前备份一致。
- Voxy r5 保持原文件名，SHA256 仍为
  `7af4197428fff73e17b412e2229dc263a3f347b1311336e57a7831d1c5626546`。
- 独立诊断 MixinSquared 已移出 mods；整合包内没有 gl1282 诊断模组残留。
- 两份测试存档已移至工作区忽略目录 `run/diagnostics/gl1282-2026-09-08/worlds/`，
  分别为 `with-voxy-A`、`without-voxy-B`；整合包 saves 中诊断副本数量为零。
- 原始存档未用于测试，正式代码/JAR 未修改。本次只留下调查文档，未提交或推送。
