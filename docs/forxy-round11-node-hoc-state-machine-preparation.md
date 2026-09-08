# Forxy 拾壹轮准备：节点层级、请求与回收状态机

状态：**拾壹轮已完成**（2026-09-09）；正式结果与限制见[执行记录](forxy-round11-execution-record.md)。
下文作为实施前的历史计划保留；第五节未勾选项及文末“准备交付”是当时快照，不是当前剩余TODO。
当前验收：418项自动门禁、dev两路径、四包限定场景、CPU/实机A/B及环境恢复均完成。
实机A/B仅一对照，worker/publish p95有额外成本；未直接测mesh重建总数/瞬时全游戏资源峰值，不能宣称帧率无回退。
此外，实施已明确AllChildrenAreLeaf只描述直接孩子：每次转换更新受影响的直接父级，
不是把一个布尔值直接传播到所有祖先。下文原拟议措辞以此及执行记录为准。
分支 `Forxy`，代码基线 `6166df430`；本轮沿用汉字大写轮次，不使用 Forge 移植时期的 XI。
本文把总计划的最后一轮拆成可验证步骤，不以准备文档代替偿债完成。

## 一、目标与范围

用易懂的话说：让管理 LOD 层级的“调度账本”在请求、细分、合并、删除和迟到结果到达时
始终一致。正常静止画面原则上不变；预期收益是快速移动、缩放视距、换维度及重建时
更可靠，减少状态错误造成的空洞、卡住的细分、重复重建和资源泄漏，而不是换一种光影。

只处理原作者留下的 NodeManager / HOC 生命周期技术债：

- 节点状态、父子关系、拓扑变化及有效覆盖。
- 单节点请求、子节点请求、geometry-in-flight 与 request-in-flight。
- watcher、cleaner、节点/请求/几何 ID、在途缓冲的所有权。
- CPU worker 到渲染线程/GPU 的发布，以及删除与 ID 复用的时序。

不并入：Vanilla/LOD 过渡动画、新的光影包适配、TACZ/Mekalus 修复、纹理/深度格式重写、
SQLite 内嵌、所有存储/缓存算法重写，以及为了减少 TODO 而实现无调用者的 stub。

## 二、进入本轮的真实基线

- 拾轮已完成；当前兼容提交的 r5 已获四整合包用户验收。产物 SHA256：
  `7af4197428fff73e17b412e2229dc263a3f347b1311336e57a7831d1c5626546`。
  详见[四包审计](forxy-compat-r5-four-modpacks-audit-2026-09-08.md)。
- 09-08 Closing Song 的两条 GL1282 已通过有/无 Voxy 对照确认是 TACZ 启用 stencil 后
  Mekalus 深度副本格式同步的短暂问题，不作为本轮节点缺陷。
  详见[专项归因](forxy-closing-song-gl1282-attribution-2026-09-08.md)；该文档在准备阶段尚未提交，
  后续用户授权后随拾壹轮一并归档。其他版本/组合的 GL 错误不能直接套用此结论。
- 四包验收不覆盖新版节气三个扩展开关开启后的全部场景，也不覆盖真实 VR 硬件。
- 总账的柒轮仍有真实 Chunky/Bobby/DH mipping 成本测量及专门视觉门禁未勾选。
  不把后续泛化冒烟当成这些条目的专门证据，也不在准备拾壹轮时顺手勾掉。
- 09-04 的脏 section 生命周期、LOAD_MISSING 写后可见性修复已经落地；不能再次把
  “所有历史空洞”归因到 NodeManager，更不能撤销这些修复来重做本轮。

## 三、原版与活动 owner 已定位

原版目录拼写实际为 `client/core/rendering/hierachical/`。这些原始文件是对照，
真正改动应在活动 Forge owner，不编辑排除编译的原版副本来假装完成迁移。

| 职责 | 原版对照 | 当前活动实现 / 重点入口 |
| --- | --- | --- |
| 节点状态 | `hierachical/NodeManager.java` | `forge/NodeManager.java`：processRequest、processChildChange、processGeometryResult、finishRequest |
| 节点存储/编码 | `hierachical/NodeStore.java` | `forge/NodeStore.java`：allocate/free/copyNode/writeNode |
| 异步事件与发布 | `hierachical/AsyncNodeManager.java` | `forge/AsyncNodeManager.java`：workerRun、publishSyncResults、mergeGeometryEvents、tick、stop |
| 几何所有权 | `section/geometry/BasicAsyncGeometryManager.java` | `forge/BasicAsyncGeometryManager.java`：uploadReplaceSection、removeSection、pending sync events |
| 订阅与重建 | 原 `ISectionWatcher` / `SectionUpdateRouter` | `forge/ISectionWatcher.java`、`SectionUpdateRouter.java`、`RenderGenerationService.java` |
| 缓存/迟到结果 | 原几何缓存/请求路径 | `forge/GeometryCache.java`、`BuiltSection.cacheEpoch` |
| GPU 请求与回收 | 原 hierarchical shader / HOC / cleaner | `forge/HierarchicalOcclusionTraverser.java`、`NodeCleaner.java`、`GpuBufferLayout.java`；`lod/hierarchical/node.glsl`、`traversal_dev.comp`、cleaner shaders |

已读到的关键事实（行号为本次基线）：

- 原 `NodeManager:34–61` 自述“所有节点都有孩子”等假设后来已有例外；空顶层节点的
  零子请求依赖之后的 child update 才继续。这是必须特征化的历史设计，不是已复现的新故障。
- Forge `NodeManager:315–395` 已处理 child removal 并在 mask 为零时调用
  `transformInnerToLeaf`；不能把它列成“完全没有实现”。
- `transformInnerToLeaf:478–504` 在缺少父几何时可能先设置 EMPTY 并保留生成请求。
  必须验证可见覆盖与收敛，不把“暂缺几何”未经证明地当作真实空白。
- `finishRequest:635–657` 已有零 mask 收尾；`makeLeafChildRequest:780–807` 仍允许顶层
  零 mask 挂起。需要验证两条路径如何衔接，不能只增加一个提前 return。
- 递归删除和 `removeRequest:514–617` 已区分部分 single/child 分支并验证若干映射；
  要补的是完整协议及交错覆盖，不是按旧描述重新实现已有分支。
- `removeGeometryCached:907` 实际直接 `removeSection`。异步拒收的 BuiltSection 则走
  `AsyncNodeManager:557–560 -> GeometryCache.put(section, cacheEpoch)`，两者不是同一条缓存路径。
- `workerRun:417–547` 当前顺序：顶层 remove → add → child update → 有预算的 geometry
  → GPU request batch → cleaner removal → workCounter 扣减 → publish → 可选完整性检查。
  这是特征化基线；准备阶段不改变队列顺序。

## 四、先建立的状态与资源账本

不能把状态压成一个枚举后遗漏其他维度：

| 维度 | 必须区分 |
| --- | --- |
| 位置状态 | 不在 active map；single request；child request；leaf；inner |
| 几何状态 | NULL（尚无/已移除）；EMPTY（真实空结果）；有效 ID；生成/上传在途 |
| 请求状态 | 无请求；已分配且在途；部分结果；满足；取消；迟到结果 |
| 层级 | 顶层/非顶层；LOD0/可继续细分；孩子存在 mask；已实体化孩子；待请求孩子 |
| 发布状态 | CPU 当前状态；尚未发布变更；待渲染线程消费；GPU 可见快照 |

协议冻结点：

- CPU NodeStore 每节点 4 个 long；GPU `writeNode` 输出 **16 B**，不得混为一谈。
- CPU map 的 leaf/inner/request 标签占高两位，single/child 请求标签使用 bit29，ID 使用低24位。
- 几何语义 `-1=NULL`、`-2=EMPTY`；GPU 对应 `0xFFFFFF` / `0xFFFFFE`。
  child ptr 的 `-1` 与 `SENTINEL_EMPTY_CHILD_PTR=0xFFFFFE` 要单独解释，不能解引用 sentinel。
- request 字段19位，`0x7FFFF` 为 NULL。`activeNodeRequestCount` 对应 **childRequests.count()**，
  不是 singleRequests 和 childRequests 的总和。
- child count 在 NodeStore 编码为 `count-1`，有效1～8；零孩子必须使用约定状态，不能编码 `count=0`。
- 保留拾轮的 HOC 请求50条、每条8 B、头8 B（共408 B）；遍历/渲染队列各200,000；
  cleaner 固定256项。不能靠扩大缓冲或放宽边界掩盖状态问题。

每次操作后的不变量至少包括：active map ↔ 分配节点/请求的一致性；父位置和 child index；
mask 与实体孩子/请求孩子的分割；ancestor 的 AllChildrenAreLeaf；每个资源唯一所有者；
计数精确；node move 同步更新 map、cleaner 与发布记录；删除后迟到结果不能污染新一代位置。

## 五、实施前清单快照（历史，不代表当前待办）

### 拾壹.1 协议、状态快照与测试支架

- [ ] 对原版和 Forge owner 建立完整转换表，标注历史行为、已有 Forxy delta 和待裁决项。
- [ ] 用活动 `NodeManager`、`NodeStore`、CPU 侧 `BasicAsyncGeometryManager` 建立测试，
  仅 watcher/cleaner 外部回调用记录器；不建第二个正式状态机或测试 renderer。
- [ ] 扩展可重复的完整性快照：资源集合、mask、计数、待发布项，避免只数源码里的 ++/--。
- [ ] 把外部事件丢弃/过期/重复/非法分类；每一类有预期结果，不统一吞异常。

### 拾壹.2 请求与空顶层节点

- [ ] 单请求结果先后顺序：child 信息先到、geometry 先到、EMPTY 结果、重复结果、取消。
- [ ] 顶层空 → 非空 → 空，多次请求、8个 child bit（特别0x80）及零 mask 完成/挂起。
- [ ] 先复现非法状态或不收敛序列，再修改；如果某路径已正确，仅补证明与删除过时描述。

### 拾壹.3 细分、合并和祖先标记

- [ ] leaf → inner、inner → leaf；孩子连续块扩缩/搬移/删除，保留剩余位置与几何对应关系。
- [ ] AllChildrenAreLeaf 向多级祖先传播与清除，不能只更新当前父节点。
- [ ] mask归零且父 geometry 缺失/在途时，证明覆盖不会提前消失；EMPTY 不能充当未完成结果。

### 拾壹.4 在途几何、删除与位置/ID 复用

- [ ] cleaner 删除、child remove、top-level remove 与生成/上传结果到达的交错。
- [ ] 删除后同位置再添加、请求 ID/节点 ID 复用，验证旧结果、旧 child 通知不能误入新状态。
- [ ] 成功接受、缓存、过期拒收、异常清理分别保证 BuiltSection/MemoryBuffer/WorldSection
  exactly-once 交接或释放；核对现有 cacheEpoch 的边界，不把缓存 epoch 直接当节点生命周期。
- [ ] 不预设必须增加 generation。若证据要求新增标记，先评估 CPU 内协议，单独列出全部
  producer/consumer；未经布局审计不扩展 GPU 请求或持久化格式。

### 拾壹.5 异步发布与计数

- [ ] 快照、合并、上传、scatter、top-level delta、cleaner ID move/free 形成一致可见版本。
- [ ] 生产者与 worker 快照、geometry budget 留尾、publish 等待、stop/drain 的确定性交错。
- [ ] 每批只计一次工作；未消费事件不得无故扣除。负计数“等一秒可能恢复”不能作验收依据。
- [ ] CPU 单 owner 语义不改为任意并发写 NodeManager；GPU 旧快照不能读取已被错误复用的资源。

### 拾壹.6 回收/缓存政策裁决

- [ ] 区分尚在 CPU 的几何、待上传几何和只在 GPU 的几何，列清缓存、重生成、释放的成本及所有者。
- [ ] 优先复用现有有界 GeometryCache，不因函数叫 `removeGeometryCached` 就宣称已缓存。
- [ ] 原版 `downloadAndRemove` 没有工作实现且当前 Forge 无此入口；只有证明必须 GPU 读回时
  才另列完整异步下载设计。否则书面保留明确的释放/重生成政策，不实现空 stub 凑进度。

### 拾壹.7 压力与正式客户端验收

- [ ] 默认固定种子建议至少32×2,000步，长测至少100×10,000步；每步核验不变量，保存seed、
  事件序列和首个差异，并能缩减成最小重放。数字是计划目标，不是已执行结果。
- [ ] 与实际 HOC/cleaner shader 请求回传衔接，覆盖延迟消费、重放、边界ID和释放后复用。
- [ ] 同场景比较基线/候选的 worker p95、重建量、缓存命中、峰值/稳态资源；固定轨迹与容量，
  不凭“没有卡顿感”判断性能。稳定清空后节点、请求、watcher、未消费缓冲全部按协议回收。
- [ ] 正常容量客户端覆盖快速移动/万格传送、8→31→8、空/实地形边界、Chunky、Bobby/DH导入、
  换维度、重建、退出重进；Embeddium-only 与 Oculus 光影都测，关联 Acedium/Vivecraft 窗口。
- [ ] 候选再做四整合包回归；旧版 r5 通过不能替代新状态机验收。真实VR仍单列硬件限制。

## 六、已有测试与新增覆盖边界

沿用 `AsyncNodeManagerTopLevelWorkParityTest`、`AsyncNodeManagerStopParityTest`、
`GeometryCacheTest`、`GeometryBufferReuseLifecycleTest`、`Round10HocQueueSafetyTest`、
`Round10NodeCleanerBoundsTest`、`Round10GpuExecutionTest`。其中部分是源码契约检查，
不是节点随机状态转换证明。原 `TestNodeManager` 是参考材料，不把其独立模拟当正式路线。

新增测试拟按状态转换、请求生命周期、资源所有权、异步发布、模型驱动长序列分组；
实际命名和是否独立长测 task 在拾壹.1 决定，本次不创建空测试/空 task。

执行阶段基础门禁：`compileJava test jarJar` + `git diff --check`，GPU变更保留
`round10GpuTest`，发布包保留 `packagedArtifactTest`；按改动复跑 Bobby 精确构建及真实数据门禁。
编译需保留当前 Embeddium/Oculus 开发输入，以及节气 `0.12.18.9.1`、MixinSquared
内嵌 `0.3.3` / 最低 API `0.2.0-beta.6` 输入；SQLite 继续外置。命令参数沿用兼容修复记录。

## 七、停止条件与完成定义

仅有 TODO、告警文字或字段共享猜测时，不改运行语义；先建立事件序列/资源真值。
发生未分类的 missing active-map、inner child-existence zero、负计数、陈旧请求、重复释放、
sentinel 解引用或可持续复现的空洞时，该门禁失败，不能通过静默返回或关闭验证器收尾。

每阶段记录“已确认 / 已排除 / 缺证据”；所有生产差异标为 Forxy delta。GPU实验只作辅助证据。
最终须完成可用矩阵、正常回收、无新的层级错误、无持续空洞，并把缓存政策和验证限制写回总账。
正常流程退出客户端，不强杀 Java；外部模组阻塞或未获许可的测试保留为未验证，不冒充通过。

**当时准备交付（历史）**：范围、原版/Forge定位、状态维度、布局约束、七阶段顺序与验收条件已列明。
未改 Java/GLSL/配置、未编译测试或启动客户端、未提交推送；等待下一步开始实现。
