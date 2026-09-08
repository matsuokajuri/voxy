# 拾壹轮 CPU 性能与资源对照

日期：2026-09-09。**最终R2的CPU活动owner对照已完成，资源计数正常；同尺寸替换更快，
反复改变几何大小仍有额外CPU和Java分配成本。不能笼统宣称“所有性能无回退”，也不能把
CPU隔离计时冒充真实客户端帧率或调度性能。** [实机A/B](forxy-round11-client-ab-performance-audit.md)
及[dev/整合包验收](forxy-round11-execution-record.md)也已完成，独立记录其成本与验证边界。

## 最终R2：实际8.5.9、充分预热、逐场景独立JVM

最终证据目录：`build/reports/round11-r2-cpu-qualified-20260909/`。

- 基线固定 `6166df430d0804c29ee280f58dc2904520c0236c`，候选包含后述fastutil ABI修复。
- 候选GeometryManager SHA256仍为
  `ea8ec9cb1ca0cf33b4fabca79ca0ea51868ac30a171bff8233e23e5c80abf772`；完整源码哈希见environment.json。
- JDK17.0.19+10、同机器/同heap/同处理器计数；编译classpath沿用项目，测量运行期强制
  实际fastutil8.5.9，SHA256 `9578bf2a1700cf20d21746a2ee89e57ba1abbd37fa9feda68ff5e9a28473a7f9`。
- 8个场景×2版本×3fork，共48个独立JVM；版本顺序交错，每场景不再共享前一场景的类型profile。
  至少2048批并且至少2秒warmup，随后128批测量。实际warmup批数保留在每条结果中。
- 先对worker、node、resize三项分别做C2诊断；关键方法明确在warmup_done前完成tier4。
  例如worker两版C2收尾约2.07/2.12秒、warmup结束2.96/2.75秒；resize C2约0.66秒，
  warmup结束5.76/7.94秒。诊断日志单独保存，**正式采样关闭PrintCompilation**。

各fork分位数取中位数。单位ns/操作；worker行是实际workerRun累计时间/输入事件：

| 场景 | HEAD p50 | R2 p50 | p50变化 | p95 HEAD→R2 | Java分配 B/操作 HEAD→R2 |
| --- | ---: | ---: | ---: | ---: | ---: |
| arena分配/回收控制 | 306.738 | 315.926 | +3.0% | 367.887→399.683 | 1573.215→1573.215 |
| 同尺寸mesh替换 | 329.121 | 82.793 | −74.8% | 494.863→117.383 | 1840.803→350.208 |
| 反复改变mesh大小 | 448.418 | 695.801 | +55.2% | 580.488→907.207 | 1875.970→3230.328 |
| 改大小并复制进CPU发布包 | 582.598 | 874.219 | +50.1% | 774.063→1052.285 | 2405.416→3764.508 |
| 有界缓存命中/逐出 | 46.200 | 43.750 | −5.3% | 62.772→71.378 | 89.456→93.335 |
| 节点添加/细分/删除 | 142.904 | 152.474 | +6.7% | 211.458→207.943 | 518.026→524.026 |
| 原ComputeMemoryCopy替换 | 327.062 | 340.354 | +4.1% | 402.029→422.266 | 1786.573→1786.573 |
| 实际workerRun，无GL缓存命中 | 529.462 | 569.405 | +7.5% | 916.997→859.207 | 2047.524→2054.164 |

worker每批353个输入事件、4次真实workerRun，累计p50约0.187→0.201 ms/批。
混合四阶段的逐次workerRun p50为32.1→58.1 µs、p95为114.2→116.6 µs；阶段耗时不同，
因此同时报告每批累计/事件指标，不用一个混合中位数冒充游戏帧耗时。Java分配仍覆盖整个
批次（包含外部输入构造），而worker行的计时只累计workerRun本身。

资源与同场景条件：

- 全部48份结果每批native buffer数量和字节差为0，整个scenario owner关闭后也归零。
- 同尺寸、resize/发布、node、worker的存活geometry峰值分别保持524,288、2,097,152、
  9,216、294,912 B，两版一致；不是整个游戏显存/RSS或短暂中间预留峰值。
- cache场景每批3072命中/1024 miss/993逐出；worker每批288命中/0 miss/0逐出、353事件，
  工作计数和几何owner清空。计数不一致或资源残留会直接使测试失败。
- size-churn额外成本真实保留，没有为了压数字删除回滚、handoff前预留或改固定容量。
  该场景的差异不能忽略，但也不能直接放大成整个Minecraft帧率回退。

本次运行于游戏/PCL完全退出后；采样结束确认Java进程为0后才交还实机AB窗口。
3fork并不是高置信统计研究，OS/GC尾部波动仍保留在p95；这里完成的是同轨迹CPU owner
比较，并未包含GL、真实模型生成、磁盘/网络或生产者并发。真正客户端的同插桩AB另立证据。

## 早期诊断方法与来源（被上述最终R2对照取代）

沿用原有 `Round6PerformanceHarnessTest` 的显式 CPU owner 测量思路，增加隔离基线、
多 fork、预热、线程分配和真实资源计数。工具在 `tools/benchmarks/`，不进入生产 sourceSet。

- 基线为 git `6166df430` 的实际 Forge 活动实现，而不是另写的旧算法模拟。
- 候选/基线分别从冻结源码独立 javac 编译，同一 main、输入、机器和 JVM 参数运行。
- JDK 17.0.19+10，`-Xms512m -Xmx512m -XX:ActiveProcessorCount=2`；不启动游戏或 GL。
- 此前一次有效采样：3对 fresh JVM、顺序交错、每场景64批warmup、64批测量；不是本轮最终对照。
- 直接原始结果：`build/reports/round11-cpu-final-clean-20260909/`，含环境/源码哈希/
  编译日志/逐fork日志/results.json/summary.json。该目录不是要提交的项目文件。
- 候选 `BasicAsyncGeometryManager.java` SHA256：
  `e73eabf1a87b6dded130e27b0659e5c1a35d0dbe032f1c39bf69f74fc2cf5f1e`。
  其他候选源码以该目录的 `environment.json` 为准；之后改动不能冒充这份快照的结果。

## 早期参考结果（非最终R2）

以下为各fork p50 的中位数，单位 ns/操作（worker场景为 ns/输入事件），不是游戏帧耗时：

| 场景 | HEAD | 候选 | 候选/HEAD | Java分配 B/操作：HEAD→候选 |
| --- | ---: | ---: | ---: | ---: |
| arena分配/回收控制 | 288.9 | 282.2 | 0.98 | 1573→1573 |
| 同尺寸mesh替换 | 273.6 | 83.9 | 0.31 | 1841→357 |
| 反复改变mesh大小 | 388.3 | 579.4 | 1.49 | 1876→3230 |
| 改大小并复制进CPU发布包 | 523.4 | 717.4 | 1.37 | 2405→3765 |
| 有界缓存命中/逐出 | 49.9 | 53.5 | 1.07 | 89→93 |
| 节点添加/细分/删除 | 206.4 | 225.4 | 1.09 | 542→548 |
| 原ComputeMemoryCopy替换 | 262.2 | 282.9 | 1.08 | 1787→1787 |
| 实际workerRun，无GL、缓存命中输入 | 1075.9 | 1781.9 | 1.66 | 2048→2054 |

实际worker场景运行32个父节点及256个子mesh，共353个输入事件；真实四次 workerRun
累计约0.38→0.63 ms/批。逐次 workerRun 的fork中位 p50 为0.100→0.125 ms，p95为
0.171→0.299 ms。这仍是 CPU 隔离调用，不含GL上传/模型生成/磁盘/网络/真实排队延迟。
这些 ns/event 仅计 workerRun，分配统计则包含输入构造，二者测量边界不得混淆。

## 已确认资源事实与热路径处理

- 所有有效采样每批结束的 native buffer 数量差和字节差均为0。
- 同尺寸mesh的存活预留峰值均524,288 B；大小变化/CPU发布均2,097,152 B；
  节点场景均9,216 B；实际worker场景均294,912 B。这里不是整个游戏的显存/RSS峰值。
- 缓存两版每批均3072命中、1024 miss、993逐出，随后缓存计数和字节归零。
- 首批对照确认新事务替换有多余树查询分配：每次通过 arena.getSize 查旧预留，随后
  shrink 再查一次，甚至同尺寸也调用。已改用旧metadata中 itemCount 的原128元素对齐值，
  同尺寸不调用shrink；完整性检查要求实际预留恰好等于该对齐值。
- 保留失败回滚、旧覆盖、三个容器容量预留和BuiltSection严格交接保护。没有为获得漂亮数字
  删除这些边界。反复增长时先尝试原地扩大、再预留新位置，确实增加arena工作；同尺寸则
  不再重复释放/分配ID和heap，Java分配明显降低。
- 几何/缓存/arena所有权专项21项以及同指针缩小发布测试已通过；最终生产小优化已交入
  root统一门禁。它们是正确性证据，不是帧率证明。

## 早期JIT问题与验收边界

第一批短warmup的节点差异约2倍，64批warmup后变为约9%，不能把前者直接归因于新算法。
单独的 PrintCompilation 诊断进一步捕获：候选 NodeManager.processGeometryResult 在
node warmup_done 之后才进入C2；workerRun在该诊断中主要仍为tier2/3。因此即便64批warmup，
也没有证明所有目标方法已经稳定完成最终编译。有效结果关闭了 PrintCompilation，但没有
消除上述稳态证据缺口。arena控制的p95本身也有显著fork波动。

`round11-cpu-final-20260909` 是带编译日志的**诊断**运行，其标准输出出现交错，不能作为
计时结果；最终有效目录名称带 `final-clean`。runner现会校验完整结果行数，缺失即失败。
最早的Java21/零child warning场景也只用于工具冒烟，不进入上表。

dev开始后停止额外CPU采样，避免与实际客户端争抢CPU。当前可据此确认有界资源交接和
分配变化，并量化参考成本；不能勾选“同实机轨迹baseline/candidate worker p95已证明无回退”。
默认关闭的 `auditRound11` 应由正式dev/整合包日志提供当前worker与回收观察；如果没有对
r5使用同样instrument及同轨迹，仍不能把它写成真实客户端基线对照。

## 回收与极端容量政策

未被节点接受的CPU BuiltSection由原有有界 GeometryCache 持有。接受后vertex buffer交给
pending upload，occupancy释放；复制进同步packet后原vertex buffer释放。GPU-only删除
直接释放范围，后续通过正式生成服务重建，不宣称已经缓存，也不添加GPU读回stub。

当replacement不能原地向右扩大时，先预留新范围再释放旧范围。因此在极端碎片化下，若
只有“左邻空洞+旧范围”合并后才装得下新mesh，候选可能拒收并保留旧几何，而旧的先删除
策略可能装得下。这里明确承认额外headroom代价，不宣称相同成功概率；正常worker保留
既有50,000,000 B上传余量。本轮没有再扩展compaction或更改GPU布局来掩盖该边界。

## 实机暴露的 fastutil ABI 错误与后续冻结

早期表格 CPU 微基准采用 Gradle 解析的 fastutil 8.5.12，**不是 Minecraft 1.20.1 实际加载的
8.5.9**。随后 dev 入世界确证：本轮新增的 map/set `ensureCapacity(int)` 在8.5.12为public，
在8.5.9为private，引发 `IllegalAccessError`。这是本轮适配遗漏，不能以微基准通过掩盖。

已对两版实际JAR执行 `javap -p/-c` 核对：map/set的 `n`、`f`、`rehash(int)` 均为稳定
protected；ObjectArrayList.ensureCapacity两版均public。生产修复采用两个微小内部subclass，
用相同 `HashCommon.arraySize(expected, f)` + protected rehash 实现预留，保留三项
handoff前容量保证。没有反射fallback、升级整合包fastutil或打包重复版本。

新增默认测试 `Round11GeometryFastutilRuntimeAbiTest`，由test-only detached配置取得精确
8.5.9（不加入主/测试runtimeClasspath或JarJar），在隔离classloader中强制加载，执行2000个
真实上传跨越map/set扩容阈值、replacement、删除及native归零。修复后23项定向测试全过，
其中22项几何/cache/arena/精确ABI，1项同址缩小同步发布。

最新几何owner冻结SHA256：
`ea8ec9cb1ca0cf33b4fabca79ca0ea51868ac30a171bff8233e23e5c80abf772`。
早期表格仍归属于之前 `e73eab…` 快照；本文开头的最终R2表格已用最新SHA和实际8.5.9重新采样。
