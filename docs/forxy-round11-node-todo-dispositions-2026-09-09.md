# 拾壹轮：NodeManager / NodeStore 原作者 TODO 裁决

日期：2026-09-09。代码基线 `6166df430`。本表针对活动 Forge owner 的节点协议，
不把排除编译的原始副本改写成另一条正式路线，也不以删除 TODO 文本代替偿债。
异步队列、生成令牌、几何缓存和正常客户端验收另见拾壹轮执行记录。

## 状态含义与源码基准

- **已实现**：活动 owner 已补协议/实现，有实际 owner 的回归测试。
- **已验证原行为**：原设计已有可用机制，本轮补可执行证据，保留行为。
- **政策保留**：原注释是优化设想或无调用者的扩展，已作明确决策，不伪造实现。
- **仍缺证据**：不能由本文件的 CPU/GPU 辅助测试推导的正常客户端/性能结论。

行号均指原始 `src/main/java/me/cortex/voxy/client/core/rendering/hierachical/`
目录下未修改的对照文件。活动实现为 `src/main/java/me/cortex/voxy/forge/NodeManager.java`
和 `NodeStore.java`。同一问题在原文件出现多次时列出所有锚点，合并裁决而不漏算。

## NodeManager 逐项裁决

| 原始锚点 | 原作者问题 | 裁决 | 活动 owner 与证据 |
| --- | --- | --- | --- |
| 26–61 | 空顶层节点请求会出错；“所有节点都有孩子”的旧假设 | 已验证原行为 | 顶层零 mask 请求明确停留，保持 child watcher；后续非零 mask 会给同一请求加孩子，取消时释放。测试循环覆盖全部 8 个 bit、重复请求、空→非空→空和最后取消。非顶层零 mask 不分配孩子。 |
| 39–42 | CPU 搬移节点后 GPU 旧队列可能仍含旧 ID，考虑 redirect 节点 | 政策保留 | 不增加 redirect 类型或扩大 GPU ABI。GPU→CPU 请求/回收项携带位置而不是节点 ID；CPU 解析当前位置状态。节点搬移同时发布新旧行、map、cleaner 操作。几何/child 事件的旧生命周期由 router/生成链令牌另行拒收，不把几何 payload 与陈旧 GPU ID 绑定。 |
| 230 | 收到未订阅的几何应如何处理 | 已验证原行为 | `processGeometryResult=false` 保留调用者所有权；Async 拒收路径负责缓存/释放。当前生命周期的订阅结果交给唯一正式 geometry owner。跨生命周期拒收在进入 NodeManager 前完成。 |
| 252、1309 | 删除时下载 GPU 几何进入 cache | 政策保留 | 已上传的几何保持释放/按需重生成政策，不实现原版未完成的同步 GPU 读回 stub。尚在 CPU 的 BuiltSection 继续使用现有有界 GeometryCache；“removeGeometryCached”名字不代表已经下载。 |
| 330 | leaf 请求交错 child 增删缺实现 | 已验证原行为 / 已实现 | 原移植已有增删；本轮证明部分结果替换、删除、重加及取消。取消时加强完整 `REQUEST + CHILD + requestId` 归属检查。 |
| 332、415、446、589、697、1438 | 不应“假设是 child request” | 已实现（协议收敛） | 两类请求不混在 NodeStore 字段：single 只由 top-level insert 创建，以 active-map SINGLE 标签存在；完成后才分配真实节点。真实节点上的 requestId 只指 childRequests。验证器核对位置、类型、ID、mask、分配集合及数量。没有凭空增加无调用者的非顶层 single 路径。 |
| 349、465 | 取消孩子时还需验证 request 类型/ID | 已实现 | 取消条目必须与当前请求的完整编码相等；完成 materialization 时同样核对完整旧编码，不仅检查最高两位。 |
| 387 | child-existence 更新是否需要 invalidate | 已实现 | mask / request-flight 都会影响下一次 GPU 行，保留并补齐 invalidation；leaf 更新在请求完成前写入新 mask，与 inner 路径一致。 |
| 437 | inner 必须先更新 mask 再完成/重排 | 已验证原行为 | 原有顺序保留；leaf 路径对齐同一协议。完成前检查父节点 request 归属与 satisfied，leaf request mask 必须等于新父 mask。 |
| 505–507、1558、1587 | 所有实体孩子移除时的 sentinel 是否安全 | 已验证原行为 | `SENTINEL_EMPTY_CHILD_PTR=0xFFFFFE` 与 NULL child pointer 分离。实体孩子暂为空、仍有请求孩子时保留 inner；GPU 不解引用 sentinel。请求完成后恢复连续 child block。 |
| 585 | 重用局部 requestId/request 以提速 | 政策保留 | 不为删 TODO 重排删除→完成顺序；这类局部微优化需要热点证据，不能改变 request release 时机。 |
| 619、1224–1228、1430 | inner→leaf 缺父 mesh，用 EMPTY 暂代；增加 in-flight 状态 | 已实现 | 禁止伪造 EMPTY。父 mesh 为 NULL 且收到零 mask 时，在 CPU 保留“待空收敛”意图和最后有效 child 拓扑，请求真实父结果；真实 mesh/EMPTY 到达后才删除孩子。新非零 mask 撤销意图。标记随节点搬移、释放和 ID 复用一并处理。 |
| 792 | 只删孩子时是否要重新清 cleaner age | 政策保留 | 父节点 ID 未变、几何仍在；其可见时间不因拓扑收缩伪装成新分配。新节点 alloc 与节点搬移 destination 会重置 age，空出的 source 清除。 |
| 832、1500、1545 | 是否再维护 NodeStore 的 node-type 位 | 政策保留 | activeSectionMap 是当前 CPU 类型的唯一来源；不启用第二套可失配的 type 账本。GPU 根据 mesh/child pointer/flags 判断，无需新增 type 位。 |
| 839 | single 完成不应盲认 top-level | 已实现（约束而非扩展） | 只有 `insertTopLevelNode` 创建 single；完成必须找到同位置、同 single 对象的 active-map owner。真实 node slot 分配成功后才释放请求，分配失败仍可正常清理其 mesh。 |
| 870 | 零 mask request 完成后的状态 | 已验证原行为 | 取消释放 request，requestId 归 NULL，request-flight 清除，计数准确、GPU 行 invalidate。顶层初次零 mask 停留是单独的已证明例外。 |
| 895、997 | child 结果为零应该 warning/error？ | 已实现（分类） | 合法 EMPTY、LOD0 与 generation 期间变空均可产生零 child mask；不再输出误导性告警。非法 owner/mask/重复资源仍抛出 invariant 错误，不统一吞掉异常。 |
| 930 | split 后 grandparent 的 AllChildrenAreLeaf | 已实现 | 每次节点类型变化，重新计算**直接父节点**的实体孩子类型，值改变必须 invalidate GPU 行。split 清 false，cleaner collapse / 零 mask collapse 恢复 true；多层树测试证明不能将该布尔值当成“所有后代是叶子”向上复制。 |
| 1073、1239 | missing/request 状态的日志和限速 | 已验证原行为 / 政策保留 | GPU 迟到的位置请求可能已不在 active map，按无操作处理；重复请求不重复分配。非法批大小/负数量不当成迟到事件吞掉。是否增加日志节流不是正确性补丁的前置条件。 |
| 1092 | 临时按 activeRequestCount>100 拒绝请求 | 政策保留 | 不恢复被注释的任意限流。保留既有固定 GPU 请求容量、worker budget 和 request allocator 边界；压力测试不靠扩大容量通过。 |
| 1103 | 无 in-flight request 时 request entry 必须为 NULL | 已验证原行为 / 已实现 | 验证 `(requestId != NULL) == requestInFlight`，仅统计 child request；snapshot 枚举 single/child 分配集合并与真实 allocator count 一致。 |
| 1115–1118、1196 | 迟到 GPU request 不应把已有 mesh 的 inner 错标为 in-flight | 已验证原行为 | 只有新加 BLOCK watch 时才标 geometry-in-flight；已存在 watch 的重复请求仅重发节点行。新的上传失败回归保证 upload 成功前不会提前消费 in-flight 标记。 |
| 1249–1250、1279 | cleaner 将父节点变 leaf；是否只有所有兄弟 leaf 才允许 | 已验证原行为 / 政策保留 | 继续原版覆盖策略：父 mesh 缺失先请求、不删子树；父 mesh 就绪后才整体收拢。没有证据要求新增“兄弟必须全 leaf”策略，也不改原 cleaner 资格规则。 |
| 1259 | 不允许移除 top-level leaf mesh 后是否需重置 age | 已验证原行为 / 政策保留 | NodeManager 拒绝移除顶层 leaf；正式 cleaner 已排除 MAX_LOD。无需为了 GPU 陈旧请求伪造新的 alloc。 |
| 1327–1328 | NULL mesh 的 in-flight 是否应取消 watch | 政策保留（有回归） | 该请求在恢复覆盖，且已无旧 mesh 可节省。重复 cleaner 不取消它，避免请求永远不完成；只在真正删除节点生命周期时取消所有 watch。 |
| 1349 | 大量逐节点微小上传应改 compute-copy | 已验证现有活动路线 | 活动 Async 使用真实 SyncResults 的 16 B scatter records，经 production memcpy/scatter kernel 同批发布 mesh 数据、geometry metadata 与 node rows；未恢复原非正式 `writeChanges` 逐项微上传。 |
| 1485、1492、1628 | 请求/叶节点完整性验证留空 | 已实现 | 补 request 分配集合、唯一 geometry owner、geometry ID 对应的位置、watcher flags、pending-collapse 条件、正常 shutdown 全清空。每一步压力事件均检查实际 owner，不只数源码 ++/--。 |
| 1615 | LOD0 请求特殊分支 | 已验证原行为 | LOD0 不再产生更低孩子请求；固定多级用例与随机序列均到达真实 LOD0。 |

## NodeStore 逐项裁决

| 原始锚点 | 原作者问题 | 裁决 | 证据 |
| --- | --- | --- | --- |
| 75 | batch free | 已实现（协议） / 政策保留（位图批加速） | 全范围先验证再释放，非法 count/越界/已空 slot 不会释放合法前缀。底层逐位 free 保留；无证据要求重写共享位图的释放算法。 |
| 203 | AllChildrenAreLeaf 尚未接到 manager | 已实现 | 活动 manager 的 split/collapse/重排均维护；值变化发布，GPU 行位 29 保留既有语义。 |
| 294 | geometry ID 写 GPU 前保证边界 | 已验证原行为 | setter 区分 NULL=-1、EMPTY=-2、有界有效 ID；精确 16 B GPU round-trip 验证 sentinel 与 flags，不截断越界 ID。 |
| 298 | child pointer 写 GPU 前保证边界 | 已验证原行为 | NULL、EMPTY child sentinel 与有效 pointer 区分，count 只编码1–8；有效性由真实 allocation/map 检查，sentinel 不解引用。 |

## 明确的布局与证据边界

- CPU 仍是每节点 4 个 long。待空收敛位只使用原来保留的第 4 个 long 的 bit0；
  `copyNode` 会复制它，`clear` 会归零。`writeNode` 完全不输出这一 long。
- GPU 仍为 16 B `uvec4`；position 占前8 B，mesh/child各24 bit，request/count/leaf flag 的
  位置不变。Geometry metadata 仍32 B，经两个16 B scatter项上传。没有增加请求/cleaner容量。
- CPU 测试先重现7条失败：父级 flag 未发布、两种 collapse 未恢复父 flag、伪EMPTY丢覆盖、
  batch free 释放合法前缀、single allocation 失败丢请求、mesh upload 失败提前清 in-flight。
- 2026-09-09 已执行标准32×2,000：64,000个外部事件，1,782次split、961次collapse、
  253次node move、733个待空收敛观察状态，最深LOD0。
- 同日已执行100×10,000长测：1,000,000个事件，28,414次split、15,880次collapse、
  3,789次node move、11,390个待空收敛观察状态，最深LOD0；所有seed终止后无节点/请求/
  watcher/几何/原生缓冲残留。数字是事件与观测计数，不把无操作事件称为一百万次拓扑变化。
- 同日 `round10GpuTest` 全部13项通过（原12项+新增拾壹 round-trip）：实际 NodeManager
  结果经 Async publication、production memcpy/scatter 写入 GL；HOC 原始 `addRequest`
  内核产生位置请求，实际 DownloadStream/HOC consumer/Async worker 消费；完整 cleaner
  sorter/transformer 输出经过实际异步下载回到 NodeManager。覆盖延迟、重放、真实分配到
  最后 ID127、释放后 ID复用、被删行 tombstone、祖先 flag 发布及 pending-NULL 不伪 EMPTY。
  几何生成服务在此辅助测试中是外部 producer 记录器，不能据此声称完成渲染/模型视觉验收。
  测试设备为 RTX5070Ti / NVIDIA616.64 / OpenGL4.6；完整套件 JUnit 时间0.963s。
- GPU审计确认：新增 CPU第四long不进入16B scatter；node、geometry metadata分别使用
  scatter location最高位0/1，geometry仍拆为两项；copy→scatter→cleaner的现有屏障与顺序
  保持。测试输入也必须走 UploadStream，不能对无 DYNAMIC_STORAGE_BIT 的正式 GlBuffer
  使用 glNamedBufferSubData；早期 fixture错误已更正，没有为测试修改生产 buffer flags。
- 正常 dev/四整合包限定场景及worker p95 对照已经完成，由[拾壹轮执行记录](forxy-round11-execution-record.md)汇总。
  辅助 GPU 内核测试即使通过也不能替代正常客户端视觉/资源生命周期测试。
