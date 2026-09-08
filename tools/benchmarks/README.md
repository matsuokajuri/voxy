# 拾壹轮 CPU 所有者对照工具

本工具只测量活动 Java owner 的 CPU 批操作和资源交接。它不启动 Minecraft，不创建 GL
上下文，不是替代 renderer，也不能作为 dev/整合包视觉验收或帧率/实机 worker 指标。

## 运行

先完成一次项目正常编译。协调 Gradle 槽后，在正常开发依赖参数后追加：

```powershell
rtk test .\gradlew.bat round11BenchmarkClasspath -I tools/benchmarks/round11-classpath.init.gradle <现有开发依赖参数>
```

再使用 Forge 1.20.1 对应的 JDK 17；不要依赖系统默认的 Java 版本：

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/benchmarks/run-round11-cpu-benchmark.ps1 -JavaHome <JDK17目录> -BaselineRef HEAD -Forks 3 -Warmup 64 -Samples 64
```

- 输出必须位于仓库 `build` 内，且目录必须不存在；已有采样不覆盖。
- runner 用 `git archive` 获取基线，冻结候选源码和 build 目录 classpath 副本，分别独立
  javac 编译；不会切换分支、重置文件或覆盖正式 build classes。
- fresh JVM 的 baseline/candidate 顺序交错；同 heap、处理器计数、Java 版本、输入轨迹。
- 更新后的runner每个场景也使用独立JVM，并要求实际fastutil8.5.9运行ABI；编译classpath
  保持项目配置。新增最短warmup时长，防止只用批次数误称已经稳态。
- `-Scenario <名称>` 可单独复跑；`-CompileOnly` 只生成隔离编译结果。
- `-PrintCompilation` **只用于 JIT 诊断**，不要引用该次运行的计时结果。
- 源码 SHA256、完整 JVM 参数、环境、每 fork 日志和 JSON 都保留在输出中。
  不完整的结果行数会使 runner 失败，不会把空报告当作通过。

## 场景和指标

| 场景 | 真正执行的 owner | 一批工作 |
| --- | --- | --- |
| arena_churn | AllocationArena | 4096次分配，间隔释放/重分配，再全部释放 |
| geometry_same_size | BasicAsyncGeometryManager | 512个网格，8轮同尺寸替换，全部删除 |
| geometry_resize | 同上 | 64→2048→512→4096→128 B 重复，全部删除 |
| geometry_publish | 同上 + 原 ComputeMemoryCopy | 同尺寸轨迹，每轮把待上传数据复制进真实同步 packet |
| cache_churn | GeometryCache | 4096次put/take；3072命中、1024miss、993逐出，再失效清空 |
| node_split_remove | NodeManager + NodeStore + GeometryManager | 128个LOD2父节点逐一添加/收mesh/分8个LOD1孩子/删除 |
| sync_copy_replace | AsyncNodeManager.ComputeMemoryCopy | 512个目标位置，16轮增长/缩小，间隔取消copy |
| async_worker_cached_split | **真正的 AsyncNodeManager.workerRun** | 32个父节点，真实路由cache hit、32项GPU请求、256个孩子mesh、父级删除，共353输入事件 |

`p50_ns_per_op` / `p95_ns_per_op` 是各采样批的 ns/操作分位数；汇总表取各 fresh JVM
分位数的中位数。不是逐个普通游戏帧的耗时。

最后一个场景的 ns/event **仅累计四次 workerRun 的时间**，排除输入构造和 CPU 消费者
归还 packet；另外输出每次 workerRun 的 p50/p95。这里没有 GL上传、真实模型生成、
磁盘/网络、线程调度等待或GPU回传延迟。Java分配指标仍覆盖整个批次（包含输入构造），
不能误解释成纯 worker 的分配。

该场景复用已有测试的外部 producer seam：只为不会被调用的 RenderGenerationService
构造依赖提供空实例；真正 Async 构造器/Router/NodeManager/缓存/发布代码全部照常运行。
每个请求mesh预先放入现有有界缓存；不会运行第二套 worker 或伪造 renderer。

每个测量批结束都检查 MemoryBuffer 数量/字节数与预热结束时相等；geometry/node/cache
另检查其真实资源计数。`peak_live_geometry_bytes` 是批次可见的存活预留峰值，不是瞬态
堆分配峰值、显存峰值或整个游戏进程RSS。

## 已知测量限制

固定次数 warmup 不等于保证 C2 完成。2026-09-09 的诊断在 warmup 标记后仍见 C2 编译，
因此当前计时只作参考；分配数量、资源清空和确定性计数才是对应样本的直接证据。
若需要稳态判断，单独增加目标场景 warmup，检查诊断日志后再关闭 PrintCompilation
复跑，并排除游戏/GPU测试等并行负载。不要用短样本差异直接改生产算法。

详见 `docs/forxy-round11-cpu-performance-audit.md`。

最终R2参数在 `run/diagnostics/round11-2026-09-09/final-r2-performance-plan.md`；C2诊断和
实际8.5.9、逐场景独立JVM、长warmup的最终采样现已完成，见上述CPU审计文档开头。
旧snapshot仍只作历史调查，不得混用。
