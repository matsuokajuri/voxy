# Forxy 拾壹轮执行记录

状态：**拾壹轮已完成**（2026-09-09）：源码、418项自动门禁、dev两路径、四包限定场景、性能对照及原环境恢复均已完成。性能与外部日志限制见文末，不等于全项目所有待办清零。
2026-09-09 用户授权整轮实现，并自主完成 dev 与四个整合包验证。
基线 `6166df430`；已有准备文档和 GL1282 调查文档保留。实施与验收阶段未自动发布 Git；后续用户明确授权提交并推送。

## 基线与首批证据

- 首次 `compileJava test jarJar` 执行312项默认测试；311通过，1条既有源码断言失败。
  `Pass2ParityRepairSourceContractTest:91` 将节气新增的上方 block-state 解析误包含进 biome
  解析检查。收紧为 biome 赋值自身边界，保留原 `.result()` / 非 partial 的语义要求。
- 最初建立活动 NodeManager、NodeStore、BasicAsyncGeometryManager、GeometryCache、
  AsyncNodeManager、SectionUpdateRouter 的失败/特征化测试，再改生产行为。
- 四包 r5 通过仅作为基线；本轮R2的独立实测与还原结果已记录在下文，不复用旧验收冒充新候选。

## 七阶段进度

- [x] 拾壹.1 协议、状态快照和测试支架。
- [x] 拾壹.2 请求与空顶层节点。
- [x] 拾壹.3 细分、合并、祖先标记。
- [x] 拾壹.4 在途结果、删除与位置/ID复用。
- [x] 拾壹.5 异步发布与计数。
- [x] 拾壹.6 有界缓存与 GPU-only 回收政策。
- [x] 拾壹.7 压力、性能、GPU、dev及四包验收与还原。

## 已复现并修复的生产差异（Forxy delta）

所有测试直接调用活动 Forge owner；仅外部 watcher/cleaner 或模型生成时序使用记录器/可控回调。
CPU 断言与专属 GPU 上下文均不冒充正式可见链验收。

| 状态/事件 | 旧行为与风险 | 本轮处理 |
| --- | --- | --- |
| leaf 细分为 inner | 包含它的父节点仍可能标记 AllChildrenAreLeaf | 同步失效直接父级标记；合并时重新计算直接孩子类型 |
| inner 的孩子 mask 归零、父 mesh 尚缺 | 先移除孩子，再把 NULL 当作 EMPTY，丢失已完成覆盖 | 保留最后完整孩子拓扑，在 CPU 保留待合并意图，父结果到达后再合并 |
| 顶层 single request 完成但节点容量耗尽 | 先释放 request ID，active map 留下悬空请求 | 成功分配节点后才转移 request 所有权 |
| 缺失父几何的上传失败 | 成功接纳前就清除 geometry-in-flight | 成功上传后才清除；失败仍可精确清理旧 owner |
| 取消订阅后同位置重建、旧 dirty 结果迟到 | 位置相同不代表请求相同；cacheEpoch 不等同于 watcher 生命周期 | CPU 独立 watch token；geometry dirty 更新版本，child token 对应订阅生命周期 |
| queued task 被较新 dirty 替代 | 需要同时保证队列 permit、map 位置与保留区段引用 | 锁内合并版本、预留计数先于发布，保留引用的转移结果在锁内锁定 |
| worker/producer/stop 交错 | 发布后才增加工作数；异常/故障后的清理可遗漏 | 接受与 stop 共锁、先计数再发布，finally 扣消费数并释放引用，故障也执行完整关闭 |
| mesh 替换失败/辅助 occupancy | 新分配失败可能丢掉旧可见覆盖或泄漏缓冲 | 事务式预留、稳定 ID、原地缩放，成功才移交；所有 BuiltSection 只能交接/释放一次 |
| 缓存关闭后迟到或重复移交 | 可能缓存已释放对象，或释放缓存当前仍持有对象 | terminal closed 拒收并释放迟到对象；重复所有权违反明确报错且不误释放 |
| arena 缩小/最大空闲块查询 | 缩小未实现；无符号最大空闲查询迭代器可落空 | 复用原有 free 合并，修正迭代器定位与无符号解码 |

`AllChildrenAreLeaf` 表示**直接孩子都是 leaf**，不是“所有后代都是 leaf”；对直接父级的重算会在每次层级转换时逐层维持，
不能把单次变化盲目覆盖所有祖先。CPU NodeStore 仍为四个 long；待合并意图使用原预留第四个 long。
GPU 节点16 B、请求408 B、cleaner 256项、geometry/quad布局及正式容量不扩展。

### 结果与内存的唯一 owner

`RenderGenerationService -> BuiltSection -> AsyncNodeManager` 接受回调时移交所有权。
接纳结果由 `BasicAsyncGeometryManager` 消费，vertex 缓冲进入未发布/已发布 SyncResults，随后上传并释放；occupancy在移交时释放。
拒收/离开可见范围的 CPU 结果仍可进入现有有界 GeometryCache，cacheEpoch失效或超预算则释放。
GPU-only 几何删除后释放并按原请求链重新生成，**不增加 GPU 读回、downloadAndRemove stub 或替代 renderer**。
不能原地扩展时坚持先预留新范围再移除旧范围；极端碎片可能拒绝需“先破坏旧覆盖”才有空间的替换，不通过增大容量掩盖它。

### 自动验证进度（尚非最终门禁）

- 先建立失败：联合45项中24个预期失败；其余是对照。额外复现 single request 容量失败与上传失败提前清除 in-flight。
- 21项 geometry/cache/arena 已通过；Async 同指针2048 B→64 B缩小与实际发布合并通过。
- 14项节点状态、3项存储、1项标准压力通过。标准32×2000事件：1782细分、961合并、253搬移、733次延迟合并状态，最深到LOD0。
- 独立长测100×10000事件通过：28414细分、15880合并、3789搬移、11390次延迟合并状态，最深LOD0；逐事件核对精确资源集合，最终清空。
- 默认门禁旧日志断言要求保留 NULL→EMPTY 和“零 child 就告警”，与已证明修复相冲突；收窄为不恢复虚假EMPTY行为，正常诊断仍保留。
- 此阶段尚待补 generator/router 的实际队列、保留引用、迟到结果与关闭竞态；下述最终门禁已覆盖并重跑，不再是当前待办。

后续统一门禁已在正式客户端前执行417项全部通过，含399默认与下述辅助门禁；
generator/token/RuntimeAudit/RendererShutdownSequence 已纳入，而不是仍待测试。
原始对照异常分支还确认外层 `ForgeOriginalVoxyRenderSystem.shutdown` 一个大try会使早期错误跳过后续清理；
现按原顺序逐项尝试，逐项记原异常，world引用释放后再抛cause/suppressed完整汇总。不能因节点校验失败而漏掉服务/GL关闭。

### 可选实机审计的含义

`-Dvoxy.forge.auditRound11=true` 默认关闭；开启时使用固定2048样本环，约5秒及stop记录
活动worker/publish的p50、p95和累计max，同时输出资源/队列账本。排除park/sleep、发布等待、verify和日志时间；
这些是worker批次CPU耗时，**不是帧时间**。`-Dvoxy.verifyNodeManager=true`为另一个完整性检查开关，
其诊断开销不能作为默认用户性能。没有用该审计替换正常渲染路径。

## 实机保护与恢复

dev及四包配置、options、PCL设置、原JAR与测试前日志已保存到忽略目录
`run/diagnostics/round11-2026-09-09/baseline/`。每个原世界均复制为 `saves/ForxyRound11`，元数据哈希与来源核对一致；
测试只进入副本。临时打开 `voxy.verifyNodeManager` 与本轮审计，测试完归档副本、恢复配置和原已验收JAR。
原存档不清库、不重建、不覆盖；最后再核对原level.dat哈希。

## 正式客户端发现：fastutil 运行时 ABI（01:10，未通过候选）

统一门禁417项通过（399默认、1长测、13GPU、1发布包、1Bobby精确、2真实数据）后，
首次无Oculus dev客户端实际入世界失败。候选12839135 B，SHA256
`f019b1b5b83a8da739df8b42d9048ba9b7a3fa11d785d44afff95028ae0979f1`，**尚未装入四包，不作为交付包**。

- 已确认：Minecraft实际模块 `it.unimi.dsi.fastutil@8.5.9` 的 `Int2ObjectOpenHashMap.ensureCapacity(int)`
  是private；编译/辅助测试类路径版本可调用该方法。`BasicAsyncGeometryManager`预留容量触发 `IllegalAccessError`。
- 不据此切换fastutil、嵌入冲突版本或移除容量预留事务；修正平台ABI并增加精确运行库检查。
- 失败后正式stop审计：node/position/single request/child request/geometry/geometry bytes/work/
  所有输入队列/SyncResults/待上传/待写入/cache entry与bytes均为0。没有强杀Java，客户端正常记录崩溃并退出。
- 日志和崩溃记录保存为 `run/diagnostics/round11-2026-09-09/dev-fastutil-abi-failure.log`
  与 `dev-fastutil-abi-crash.txt`。辅助门禁不覆盖真实模组类加载版本，后续必须重新跑dev和四包。

### R2 ABI修正与无Oculus正式dev验收

R2为12840678 B，SHA256 `16d83b6349720e3eac6000234254ffa2c8251f7c0105235da1a535a5d8ac309a`。
map/set内部极小subclass通过两版稳定的protected `n/f/rehash`执行同一 `HashCommon.arraySize`预留；
不反射、不换fastutil、不加入重复运行库。精确8.5.9隔离测试覆盖2000次正式owner上传、rehash、替换、删除与native归零。
01:19统一门禁重新全部通过；发布包仍不内嵌SQLite。

- 01:20–01:44，Embeddium0.3.31，**没有Oculus runtime**，正式geometry容量4095MiB。
- 原世界副本809.5,110,335.5森林/湖泊正常；传送65000.5,160,65000.5的新海域，实际Minecraft视距8→31→8，
  31加载完成后海洋/海岸连续，缩回8仍完整，F2 `run/screenshots/2026-09-09_01.37.06.png`。
- 大范围新区域生成确有等待过程，不能把尚未交付的区域记作LOD丢失。日志事件18317→48191、geometry223→1932，
  最终work与全部输入/待上传队列清空；只读线程快照无BLOCKED/死锁，各服务正常等待工作。
- 主世界→下界0.5,80,0.5→主世界正常；下界玻璃平台、诡异森林、熔岩及远景连续；F3+T资源重载后恢复，
  F2 `run/screenshots/2026-09-09_01.40.24.png`。维度/重载/最终退出各次stop资源账本全部归零。
- 01:43保存所有维度，01:44从标题正常退出，runClient成功。日志为
  `run/diagnostics/round11-2026-09-09/dev-embeddium-only-qualified.log`，debug日志同目录保存。
- 本次无新Voxy异常、GL错误或GPU overflow；已有Forge开发环境stairsblock转换、Embeddium taint、缺失可选Fabric接口、
  Realms开发账号提示保留。01:40两条Window ERROR是粘贴时剪贴板被占用（65544），重聚焦后命令成功，不是GL1282。
- Computer Use在独占全屏时截图保留旧帧，回窗口模式后图像立即更新；两份线程快照证明渲染持续执行。
  后续视觉测试使用最大化窗口模式，不能把那段旧截图当成游戏卡死证据。测试帧率临时设120，最后恢复。

### 完整dev组合（01:46–02:16）

Embeddium0.3.31、Oculus1.8.0、Acedium0.2.7-beta、Vivecraft1.3.15普通窗口、Chunky1.3.146、Bobby5.0.1。
不同时加载DH mod；SQLite作为开发运行库提供，发布包不内嵌。

- Unbound r5.8.1开启的主世界副本重新入场正常；8→31→8后森林/湖泊/LOD连续，F2 `2026-09-09_01.52.44.png`。
- 关闭Oculus光影后F3明确显示`Using nvidium renderer: 0.2.7-beta`，同时`Shaders are disabled`；
  Acedium没有被禁用来规避测试。Vivecraft主菜单明确VR关，不冒充硬件VR验收。
- 传送75000.5,160,75000.5，Chunky方形中心75000,75000、半径256，1089区块7秒完成。
  `forceLoadExistingChunks=true`为临时测试设置；正式trusted FULL hook触发，新增LOD连续，F2 `2026-09-09_02.05.02.png`。
- 正式`/voxy import bobby`读取既有真实缓存，2988个列出条目导入完成。原WorldImporter仍对Bobby的非region文件
  `last_access`输出`Unknown file`，不是节点/解码失败；未通过删缓存或修改日志级别隐藏此项。
- 正式DH导入读取副本数据库：1800行接受、skipped=0，28800区块，failedRows=0、unscheduledRows=0、
  cancelled=false、fatal=false，9秒完成。此项证明本轮正式导入生命周期，不将跨来源输入冒充同世界几何逐像素比对。
- Acedium路径切到末地正常；末地重新开启Unbound、F3+T重载后近岛/远岛连续，F2 `2026-09-09_02.13.33.png`。
- 各次owner teardown及最终退出资源账本归零；02:14所有维度保存，02:16从标题正常退出，runClient成功。
  证据`dev-full-compat-qualified.log`与`dev-full-compat-debug.log`保存在本轮忽略的diagnostics目录。
- 无新层级异常、GPU overflow或NV residency错误。保留已知光影表达式`BIOME_PALE_GARDEN`/`endFlashIntensity`
  与旧block-state解析警告、开发环境提示；另一次65544剪贴板占用导致粘贴未进入，核对后重试成功。
  不将这些消息包装成“所有日志零ERROR”，也不扩展为本轮外部光影修复。

### 四包：Closing Song 1.6.4（02:19–02:39）

- R2正式JAR、Forge47.3.22与原321模组组合；JVM确认完整性与本轮审计开启，quick-play只进`ForxyRound11`副本。
- 原始点正常，切旁观后传送41000,160,41000的新森林/河口，实际MC视距8→31→8。
  31加载后森林/海岸连续；缩回8仍完整，未出现历史持续空洞。
- F3+T资源重载完成后同位置正常；截图`closing-song-range-reload.png`，原F2名`2026-09-09_02.38.56.png`。
- 02:39正常保存所有维度、返回标题并关闭游戏；stop账本所有节点/请求/几何/缓冲/队列/cache资源归零。
  证据`closing-song-qualified.log`及`closing-song-debug.log`保存在本轮diagnostics目录。
- 实际审计中有497次过期结果拒收，最终仍收敛；没有用重复结果污染新owner。

### R2最终自动门禁复核

最终XML精确合计418项：默认400、长序列1、GPU13、发布包1、Bobby精确1、真实数据2；
全部failures/errors/skipped为0。前文417是首次ABI修正前的历史门禁数，不是最终候选结果。
独立源码快审未发现额外shutdown/token/queue所有权问题，`git diff --check`通过。

### 四包：Create Delight Remake v0.4.8.16（02:41–02:57）

- 原Forge47.4.16、Embeddium0.3.31、Oculus1.8.0与Unbound r5.8.1 + EuphoriaPatches1.9.3组合，R2与两项诊断开关确认生效。
- 初次入世界正常；副本原未开命令，正常保存到标题后，离线只修改副本NBT的allowCommands一个Byte。
  Reverse副本同样预先处理；Ripples原为true未写。session.lock独占、原level.dat哈希及修改字节数均有验证，未开LAN。
- 再入副本后旁观、白天、传送41000,160,41000，实际MC视距8→31→8；新生成草原、村庄、山体持续加载到完整，
  缩回8后LOD接管正常。F3明确D:31、客户端5041区块；未将早期有限生成范围误判成空洞。
- F3+T资源重载完成后地形仍完整；F2 `2026-09-09_02.56.58.png`，归档`create-delight-range-reload.png`。
- 三份stop（首次离开、资源重载、最终离开）均全部资源归零；主阶段55782事件、3404过期拒收后仍正常收敛。
  02:57保存所有维度并从标题Quit正常退出，证据`create-delight-qualified.log`与`create-delight-debug.log`。
- 旧VillagerTrades Fabric注入、Voxy AT/Forge stairsblock转换交互、SimpleBackups读取活动RocksDB的LOCK失败等
  必须区别于新增节点异常，详见[整合包日志审计](forxy-round11-modpack-log-audit.md)。本轮没有扩大范围或屏蔽它们。

### 四包：逆转未来2.3.3（03:01–03:12）

- 原Forge47.4.18、Embeddium0.3.31、Oculus与ComplementaryReimagined r5.8.1组合；R2、两项审计及测试副本确认。
- 原位置0,256,0正常，旁观/白天后传送41000,160,41000的新河谷；实际MC视距8→31→8。
  河床、树林、岛屿/塔楼连续；31加载并缩回8后未见持续空洞。
- F3+T后完整恢复；F2 `2026-09-09_03.12.02.png`，归档`reverse-future-range-reload.png`。
- 两份stop（资源重载/最终离开）全部资源归零，主阶段54095事件、2712过期拒收正常收敛；
  03:12:27所有维度正常保存，从标题退出，Java53520消失。证据`reverse-future-qualified.log`及debug日志。
- 包原有tag与JEI等提示由日志专项区分，不把它们混作本轮节点错误；本次没有执行枪械GL1282专项复現。

### 四包：涟漪之篇·如涟漪之所见（03:15–03:29）

- 原295模组、Forge47.4.16、Oculus及ComplementaryReimagined r5.8.1；R2、两项诊断开关、quick-play副本核对。
- 原位置47,140,-152的山地/建筑正常；旁观传送41000,160,41000，樱花林、河流、岛屿正常加载。
  实际MC视距8→31→8后连续，无持续空洞；傍晚及重载后白天画面均核对。
- F3+T第一次被输入法拦截，不记作已重载；切换英文后真正触发资源重载并由日志和界面确认。
  F2 `2026-09-09_03.28.29.png`归档为`ripples-range-reload.png`。
- 两份stop全部资源归零，主阶段49459事件、5242次过期拒收正常收敛；03:29:02所有维度保存，
  03:29:21标题Quit正常结束，Java43460退出。证据`ripples-qualified.log`与`ripples-debug.log`。
- 至此本轮dev两路径、四包限定场景均完成，未发现新增层级/几何所有权错误或持续空洞。
  这不是四包所有玩法、物品、硬件VR或所有shaderpack的无限范围验收；外部与既有日志边界见专项审计。

### 原环境恢复完成（03:34）

- Minecraft均通过保存/标题Quit关闭，PCL正常退出后再恢复，未强杀Java。
- 先只读预检，修正PowerShell5对ConvertFrom-Json数组重复包装的问题；首次失败未带`-Apply`，没有移动数据。
  随后的五环境预检通过，`-Apply`正常完成。
- dev及四包options/config、四包PCL Setup.ini按固定基线指纹恢复；四个R5原JAR SHA均恢复为
  `7af4197428fff73e17b412e2229dc263a3f347b1311336e57a7831d1c5626546`。
- 五份原`新的世界/level.dat` SHA与测试前manifest一致，原世界从未写入；测试副本按精确路径同卷移动至
  `run/diagnostics/round11-2026-09-09/worlds/<id>`，不删除、不覆盖原世界。
- 被替换的测试配置与候选JAR全部归档，执行记录为
  `run/diagnostics/round11-2026-09-09/restoration/20260908-183255-2673/execution.json`，状态completed。
  baseline、配置指纹、候选hash、原存档hash、reparse/绝对路径边界均在执行前后校验。
- 后续性能A/B使用另一个完全隔离的工作与存档副本，不再修改这五份已恢复环境。

## 最终性能对照与完成边界

### 活动CPU owner：R2、真实fastutil8.5.9

48个隔离JVM（8场景×2版本×3fork）、充分预热且关键路径C2就绪，结果见
[CPU性能审计](forxy-round11-cpu-performance-audit.md)。同尺寸mesh替换p50降低74.8%，
反复改变mesh大小及随发布复制的p50分别增加55.2%与50.1%；实际workerRun隔离场景每输入事件
累计p50增加7.5%。48份结果每批及owner关闭后的native缓冲差均为0。
这些有意保留的回滚/预留/所有权保护有成本，不能包装成“所有路径无回退”，也不能把局部操作百分比直接换算为FPS。

### 同插桩正常客户端A/B（03:59–04:50）

基线`6166df430`仅增加同一计时/只读资源观测，候选使用冻结R2生产代码。两边从相同899文件世界副本、
相同初始NBT/options及依赖开始，固定1920×1080、120fps上限、Embeddium-only、默认几何容量。
经视距16→8预热和实际F3+T后，走同一A→B→C→A轨迹及8→16→8；未在该对照中改生产算法。

- 最后owner的worker活动p95：基线0.7280 ms，R2 0.9029 ms（+24.0%，绝对增加0.1749 ms）。
- publish CPU p95：0.3794→0.4848 ms（+27.8%，绝对增加0.1054 ms）；不包含等待，不是渲染线程帧时间。
- 输入事件26115→26077，worker批次487→438；异步批次划分不同，不是严格等量微基准，事件数也不是mesh重建次数。
- R2两次stop全部账本归零；最终22个过期结果被拒收。基线物理geometry bytes/工作/队列/缓存也归零，
  但旧stop在已退役对象上留下逻辑节点/订阅账本；没有GC后heap留存证据，不能仅凭此宣称旧版跨会话内存泄漏。
- 两边调16后刚解除暂停都出现短暂重建缺口，等待30秒均补齐；回8与最终F2图像连续，没有持续空洞。
  两客户端从标题正常退出，Gradle exit0；候选04:50:36保存所有维度，04:50:53退出。
- 一组A/B不是统计FPS证明。轨迹相同但UI等待时间不同：基线F3+T曾被日文IME拦截，候选首次即成功；
  候选最终静止多等数分钟但未新增审计事件。环内批次均未达2048，不通过平均多个p95伪造整体分位数。

完整条件、资源采样和限制见[实机A/B审计](forxy-round11-client-ab-performance-audit.md)。
证据位于`run/diagnostics/round11-client-ab-2026-09-09/`，仅使用隔离副本，不影响已恢复的dev/四包。
5秒资源快照只能说明观测峰值/稳态，不是瞬时峰值或整个游戏显存；累计成功mesh构建量本轮未直接插桩。

### 交付与未扩张的范围

- 交付`build/libs/voxy-forge-0.2.17-beta-forge-all.jar`，12,840,678 B，SHA256
  `16d83b6349720e3eac6000234254ffa2c8251f7c0105235da1a535a5d8ac309a`；与dev/四包验证的R2一致。
- 无fallback、无第二条renderer、无GPU布局/容量扩张；Oculus仍可选，SQLite仍外置。
- 四包九份stop资源账本全零，没有新增Voxy层级/几何所有权错误；这不等于整个整合包日志零ERROR。
  Create的4条Flywheel/PackageVisual空PartialModel键异常已追到实际JDK调用点，无Voxy直接栈；具体物品与
  同场景旧/新版本A/B缺失，间接时序影响尚不能完全排除。旧StairBlock AT交互、RocksDB活动LOCK备份失败和
  逆转未来启动期GL1280等保留原样，详见[日志审计](forxy-round11-modpack-log-audit.md)，不冒称已修复。
- 使用computer-use技能自主完成正式可见客户端操作与截图核验；硬件VR不在此次可用矩阵内。
- 柒轮专门mipping成本/视觉门禁、Vanilla/LOD过渡动画及外部兼容事项仍按各自总账处理，不被本轮自动勾选。
- 实施与验收结束时尚未提交或推送。原有准备文档与GL1282调查内容保留，所有日志/副本/构建输出留在忽略目录。
- 最终复核：六个测试结果目录重新汇总418项、0失败/错误/跳过；交付JAR与冻结R2 SHA一致；
  Java/javaw进程为0，`git diff --check`通过；当前仍为`Forxy`、HEAD `6166df430`，改动留在工作区。

### 后续提交授权与提交前复核

用户明确要求“提交并推送”后，447个候选源码/构建文件与实机冻结快照逐一SHA核对一致，
`compileJava`重新编译成功，远端`origin/Forxy`与提交前基线无分叉。
本轮源码、测试、基准工具及审计文档随提交归档；包含被本轮引用的此前GL1282调查文档。
原始日志、独立探针、存档、配置、构建JAR及其他本地输出均不进入提交；不自动启动新一轮客户端测试。
