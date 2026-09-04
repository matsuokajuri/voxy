# 拾轮 Closing Song 空洞回归调查（2026-09-04）

**最终状态（2026-09-05）：区段生命周期与首次写入可见性修复已通过Closing Song实机回归；原故障坐标及新区域均重复8→31→8、光影开关/资源重载，没有持续空洞，原生命周期错误与零child风暴未重现。** 详见[最终实机记录](forxy-round10-final-runtime-qualification-2026-09-05.md)。下面保留失败候选和逐步排除的历史证据。

**整合包验收版本为99e959…；最终发布版本另补Acedium可选释放保护，为a22a152…，328项测试通过。** 无缓存清理、无存档修补；早先578422…深度写掩码试修已撤回，不能回收为本次空洞修复证据。

## 已确认的输入与现象

- 首次测试 Closing Song 1.6.4，截图有大片地形空洞；原地等待不会补齐。后续重进已恢复，再关闭光影也正常，详见下方复测记录。
- 实际安装 `voxy-forge-0.2.17-beta-forge-all.jar` 为 12,697,572 B，SHA-256
  `7b89daed741e9a2caeda0f9cd1447a7b9d9431a2b57f137340fe2eb8b2183c8d`，与拾轮候选一致。
- 日志为该整合包 `logs/latest.log`，会话 2026-09-04 15:45–15:50；Oculus 声明版本 1.8.0.1，光影为 ComplementaryUnbound_r5.8.1.zip，opaque/translucent patch 均有应用记录。
- 未发现本次会话的 `OpenGL debug message`、shader patch 编译失败、`Original HOC bounded queue overflow` 或 `Original Voxy MDIC capacity/input rejection`。这只能排除已报告的事件，不能证明 GPU 画面正确。
- 日志有 114,700 条 `Not creating a leaf request with existence mask of 0`，首次在 15:48:25.679（原日志 4886 行），持续至 15:50:44.369。15:50:40–43 每秒 480 条，恰好 48 个位置各重复 40 次。
- 没有发现 `Got request for leaf` 的 NULL geometry 告警或 bottom-level request 告警；会话最后完整保存世界并正常关闭 Voxy/游戏。
- 继续遵守用户暂停 Computer Use 与旧 GL 错误复现的要求；本次不操作游戏，不删除缓存，不替换整合包 JAR。

## 候选一：零 child-existence 请求循环

分类：**循环已确认；直接导致已有 mesh 丢失的解释已排除；请求饥饿仍待证。**

原版 `client/core/rendering/hierachical/NodeManager.makeLeafChildRequest` 与 Forge 对应方法均在非顶层节点 `childExistence == 0` 时拒绝，清除 request-in-flight 并 invalidate。
`NodeStore.writeNode` 不把 child-existence 上传给 GPU；CPU 更新清除 GPU requested 后，符合细分条件的节点会再次请求。这条循环早于拾轮。

拒绝路径没有删除 mesh。`traversal_dev.comp` 在叶节点请求后独立执行已有 self-mesh 发布，不以请求成功或 requested 标记决定是否绘制。因此不能把大量告警直接解释成“已有地形因请求失败而不画”。

Java 请求预算公式未改变：`ceil(50 * max(0, (4000 - taskCount) / 4000)^2)`。拾轮改变的是软限 reservation 从非原子预检变为严格 CAS；旧版竞争者可能越过软限，但仍最多 50。日志每批 48 条不能单独证明软限就是 48，更不能证明有效节点因预算而永久饥饿。

不得仅隐藏告警、清 requested 或强行恢复旧的无界计数，然后宣称空洞已修复。若要认定请求饥饿，需要有可见有效节点长时间未获请求的直接证据。

## 候选二：拾轮 depth/stencil 清理顺序

分类：**未找到本次改动造成永久空洞的直接证据；仍需要运行态分区真值。**

拾轮把写掩码移到 clear 前，清理的仍是 Voxy 私有 D24S8，不是 Oculus 原目标；清成 FAR/stencil=1 后，同一 fullscreen shader 只将有原版深度的位置标成 stencil=0，opaque 路径继续测试 stencil==1。格式、原版深度来源和分区规则没有改变。

发现一项既有前置状态的测试覆盖缺口：setup/FullscreenBlit 不主动禁用 scissor，而当前 GPU 用例预先禁用了 scissor。若实际进入时带裁剪框，可能局部清理/写入；但本日志没有对应状态证据，不能据此断言这就是用户空洞，更不能将无证据修复计入本次结果。

## 重进后的复测（同日 16:03–16:06）

- 用户提供两张新截图：重进后开启光影时，原先大片空洞已经消失；随后关闭光影也正常。截图坐标约为 `19991, 128, 19987`，与故障现场邻近。
- 新 `latest.log` 记录 16:03:52 使用 ComplementaryUnbound_r5.8.1，16:04:57 进入单人世界，16:05:52 关闭光影；切换时原渲染 owner 正常 shutdown。
- 读取到 16:06:01 的日志快照：零 child-existence 重复请求告警为 **0**，没有 GL debug / HOC bounded overflow / MDIC capacity rejection / shader patch 编译失败事件。
- 先前故障会话已轮转到同目录 `2026-09-04-1.log.gz`；仍可保留为对照，不能把新的干净 `latest.log` 当作故障日志不存在。
- 本次没有改生产代码、替换 JAR 或清理缓存。恢复发生在关闭光影之前，因此本次不是“故障持续时开/关光影”的有效 A/B，不能认定光影导致空洞，也不能认定已修复。
- 同一地点重进恢复、请求风暴同时消失，是首次加载/本次会话运行状态方向的线索；持久化数据与 GPU/CPU 层级状态都可能随重进发生变化，尚不能进一步唯一归因。

## 新触发条件：无光影、新区域、区块视距 8 -> 31 -> 8

- 用户明确调整的是 Minecraft/Embeddium 区块渲染距离，不是 Voxy LOD 距离。约 `29995, 128, 29997` 的新区域在无光影时重现空洞，故开启光影不是此问题的必要条件。
- 同一会话在 16:09:52.440 再次开始零 child-existence 风暴，读取至 16:11:53.995 时已有 155,950 条。Embeddium 在 16:09:59.121、16:09:59.914、16:10:41.839 记录 worker stop；没有相应 Voxy owner shutdown。
- 不能把“没有 Voxy shutdown”直接解释成重建遗漏：精确 Embeddium 0.3.31 的 renderer reload 会构造新 RenderSectionManager，Forge 构造 TAIL 清除 chunk-bound map/add/remove 三者；新空 section 不加入遮罩，正常 remove 在渲染前处理。正常重建路径遗留旧 CPU ledger 的简单解释已排除。

### 已证实并最小修复：边界深度清理依赖调用者写掩码

原版 `DepthFramebuffer.clear` 使用 named framebuffer clear；Forge `ChunkBoundRenderer.renderInner` 同样先 clear，之后才开启 depth writes。
精确 CUTOUT 路径为 `cutoutMipped`，其默认 COLOR_DEPTH_WRITE(true,true) 的 setup 对 true 分支不发出 setter；从该 setup 到 Voxy BEFORE-end 注入点也没有强制设置 depth mask。
因此外部阶段留下的 false 可以到达边界清理。正常天空阶段通常恢复 true，但不能依赖所有整合包/所有帧都满足此前置条件。

新增真实 GPU 用例 `chunkBoundaryDepthClearMustNotKeepThePreviousLargerView`，使用正式 MDICViewport/DepthFramebuffer：先写 0.75，再在 depthMask=false 下要求清为 0。修复前明确失败，实际仍是 **0.75**，不是 0；不是渲染截图或字符串模拟。
本次修复仅使 `MDICViewport.clearDepthBounding` 暂时打开 depth writes，执行原清理，随后恢复调用者掩码；resize 初始化也经过同一入口。没有切换深度格式、替换算法、重建 WorldEngine、清缓存或改 HOC 队列。
每个 viewport 至多记录一次实际遇到 false 的 INFO，便于后续用户日志确认该条件是否发生。

本次 GPU 设备仍为 RTX 5070 Ti，驱动字符串为 NVIDIA 616.64（先前自动验证为 616.56）；不将驱动变化本身作为故障归因。
**已证明并修复这个状态依赖，不等于已经证明它是用户截图的唯一根因。** 需要候选 JAR 按新区域 8 -> 31 -> 8 复测。

修复后 300 项默认、12 项 GPU、1 项发布包检查全部通过（313 项，零失败/错误/跳过）；独立审计建议的种子深度读回及 true-mask 保持断言已补强并再次全量通过。最终新候选 12,697,990 B，SHA-256 `578422978598e561466fae1bd29cfd6b8b269711558203beaa898db602a1fc5f`。不将先前 Bobby/DH 的结果重复记为本次重跑。

### 另一个独立的源码风险（本次未修改）

活动 geometry result 不使用缓存的 epoch 拒绝机制；同位置任务可并行完成，旧 empty 结果晚于新非空 mesh 到达时可以覆盖新 mesh。L0 被记成 EMPTY_MESH 后不再请求，所以能形成等待不恢复的时序风险。
这不等于 childExistence 被覆盖：请求阶段已有 existence-set 保护，已完成节点更新 mesh 不读取该字段。此风险与零 child 告警不可混为一谈；本次不批量改写 NodeManager/在途 geometry 状态机来掩盖边界清理候选的验证结果。

## 新会话的直接生命周期故障（16:32:58）

- 用户确认候选 `578422978598e561466fae1bd29cfd6b8b269711558203beaa898db602a1fc5f` 一进世界便出现大面积空白；已核实整合包实际 JAR 哈希匹配。
- 16:32:56.706 正式 Voxy renderer 创建成功，16:32:56.842 CUTOUT hook 到达；16:32:58.052 多个 Ingest service worker 随即失败。
- 第一条堆栈：`WorldSection.trySetFreed:181` -> `ActiveSectionTracker.tryUnload:321` -> `WorldSection.release` -> `WorldUpdater.insertUpdate:75` -> `VoxelIngestService.ingestNow/processJob`。错误为区段仍 dirty 却已经释放。
- 同时另一线程在 `ActiveSectionTracker.tryUnload:286` 报 `Section was dirty but is also unloaded`，随后大量线程获取同一个 `3@[116,0,117]` 对象失败。此时没有零 child 请求风暴，也没有新补偿 INFO，不能把本次故障归因于“补偿分支触发”。
- 当前读取路径首先将 `atomicState` CAS 从 live/zero-ref 的 1 改为 0，然后检查 dirty/inSaveQueue 并抛出；抛出前已改变生命周期、却还未从 active map 移走对象。后续裸 acquire 又先加引用再检测 unloaded，进一步破坏该对象。
- `tryUnload` 的最终 dirty 检查与 CAS 之间允许合法引用的 acquire/update/release 窗口，故前置重复检查不能代替 claim 后验证。该 CAS 后抛出缺陷在原版 `192721a7d` 同样存在。
- 另一个已核实的陆轮回归：secondary-cache 复活在移除缓存项后提前释放 slice 锁，再执行 primeForReuse/首个 acquire；原版及陆轮之前仍持 slice 锁保护此过程。必须恢复这段保护，不能靠关闭并发/Embeddium 线程共享掩盖。
- 故障原始日志另存于忽略目录 `run/diagnostics/round10-holes/closing-song-2026-09-04-1633-section-lifecycle.log`。未删除缓存或修改存档。

### 生命周期修复与已执行回归

- `WorldSection.trySetFreed` 在独占 free claim 后重读 dirty/queue 标志；若仍脏或在保存队列，强 CAS 回滚为 live/zero-ref 并返回失败。`acquire(count)` 先检查 live 再 CAS，失败不会增加引用。
- tracker 在最后 claim 被取消后解锁走原保存/重试链；缓存热命中异常也通过 finally 释放读锁；旧 unload 回调不能移除别的 holder。
- secondary-cache remove、primeForReuse 与 loader 首引用恢复到同一 slice 锁内，保留陆轮分片 LRU，不通过取消多线程绕过竞态。
- 4 项确定性测试在修复前 **3 失败 / 1 控制通过**，修复后全部通过。3 项 tracker 测试也通过，其中并发测试使用 8 writer × 2000 次、16 个位置、4 saver，并对最终值做独立反序列化核对。
- 回查早先 `2026-09-04-1.log.gz`、`-2.log.gz`，这组生命周期错误计数均为 0。因此它直接解释当前启动失败，但不能仅凭它宣称更早“8 -> 31 -> 8”空洞也已解决。

### 本次排查约束复盘

先检查整个 Voxy 错误链，而非只匹配 GL/HOC/MDIC 告警；大量重复 WARN 需要聚合，不能让它掩盖真正 ERROR。一个底层 API 条件用例转绿，不等于用户画面因果已建立；未在故障运行态出现的条件，不继续推出新的渲染候选。

## 已复现并修复：新区段写入后仍被当成不存在

- 伍轮 `4dd691a2bc` 为存储错误隔离添加 `storageLoadStatus`，并使 cached `acquireIfExists` 拒绝非 OK/RECOVERED；但该状态只在 loader 设置，真实 WorldUpdater 写入后仍为 LOAD_MISSING。原版 cached-hit 没有这条永久拒绝。
- 用与新区域对应坐标构造的真实 VoxelizedSection，经正式 WorldUpdater 写入五层：方块内容、non-air 计数、child-existence 都正确，却在 dirty callback 与随后 cached `acquireIfExists` 中全部返回 null。RenderGenerationService 对 null 返回 `BuiltSection.empty`，这条链直接能制造“已有内存数据但不生成 LOD”；从磁盘重新加载为 LOAD_OK 则可恢复。
- 修复前 4 项测试中 **2 失败 / 2 保护项通过**。现在只将发生 BLOCK/CHILD 实际变化的 LOAD_MISSING 区段提升为 LOAD_OK，并且在通知渲染回调之前完成；未改未写入的 MISSING、UNAVAILABLE、CORRUPT、RECOVERED 策略。
- DONT_SAVE 仅控制持久化，不阻止真实内存变化可见；实际损坏文件的部分内存修改仍不能自动作为恢复结果，也不覆盖损坏原始数据。
- 修复后四项全部通过，包括保存完成后 secondary 同对象复用、五层独立反序列化内容验证及损坏保护。未添加替代 renderer、缓存扫描或自动清库。

## 最终自动验证与修复版（2026-09-04 16:54）

- 80 个默认套件 / 311 项测试；11 项 GPU；发布包 1；Bobby 精确构建 1；Bobby/DH 真实数据 2。总计 **326 项，零失败/错误/跳过**。
- JarJar/reobf 成功，SQLite 仍不内嵌。产物 `build/libs/voxy-forge-0.2.17-beta-forge-all.jar`，**12,697,798 B**，SHA-256 `99e959d9a303dbb234edf5510d64470829537ada409f26e3f61aba0974ff4967`。
- 与上一失败候选相比：撤回 depth-mask 试修；修复 WorldSection/ActiveSectionTracker/WorldEngine 的上述状态协议；没有动用户的存档、缓存、模组或配置。
- 尚未提交/推送。仍需退出游戏替换 JAR，确认入世界正常，并在无光影新区域重复区块视距 8 -> 31 -> 8。自动回归证明源码缺陷已修，整合包像素仍不提前算通过。

## 后续最小判别（旧路线已停止）

不继续发放深度写掩码候选。生命周期与首次写入可见性已修复、测试并重新打包；使用上面的当前修复版做入世界和无光影新区域 8 -> 31 -> 8 复测，不清缓存，也不以自动测试代替最终实机验收。
当前日志没有逐帧 HOC render-list / MDIC command / depth-stencil 内容，尚不足以给出空洞的唯一根因；不据此进行推测性生产修复。

上文“偶发空洞未解决”是09-04修复前的历史结论；09-05基于正式整合包原触发条件的多次复测更新为通过，不是仅凭单次重进恢复关闭。测试中新发现的Chunky默认跳过既有数据断层和Acedium释放错误另有独立裁决，未混同为原问题。
