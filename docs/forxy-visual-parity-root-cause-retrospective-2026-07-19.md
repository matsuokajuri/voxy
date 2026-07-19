# Forxy 视觉一致性故障复盘（2026-07-19）

本文记录 LOD 树叶和跨 vanilla/LOD 边界阴影问题的诊断失误、已经确认的
原版机制，以及后续视觉问题必须遵守的证据门槛。目的不是保存每次试验，
而是防止再次把“发现一处差异”误写成“找到症状根因”。

## 当前结论

| 问题 | 当前状态 | 可声称的结论 |
| --- | --- | --- |
| LOD 树叶发黑、发糊 | 已解决；用户于 2026-07-19 通过 Oculus + Complementary 实机确认 | 根因是 Forge 1.20.1 缺失现代 Minecraft 的 `DARK_CUTOUT` atlas level-zero 预处理 |
| 跨 vanilla/LOD 边界的阴影形状或亮度变化 | 已解决；用户于 2026-07-20 通过 Oculus + Complementary 实机确认 | 根因是 LOD-only 区块缺少可供 Oculus 阴影图使用的 Vanilla 投影几何；正式实现保留三圈仅供阴影遍历的隐藏 Vanilla 网格 |

对应代码检查点是 `c10d2f57`。这笔提交保留了一个确实存在的 Oculus
viewport 生命周期对齐，但提交信息和本文都不把它描述为阴影修复。

## 树叶：原版究竟做了什么

前几轮只比较了 Voxy 的模型分类和 shader，追踪在 Voxy API 边界停住了。
真正决定树叶外观的是 Voxy 之外的 Minecraft 纹理上传前处理：

```text
leaf texture .png.mcmeta
 -> mipmap_strategy = dark_cutout
 -> Minecraft MipmapGenerator
 -> TextureUtil.fillEmptyAreasWithDarkColor(level 0)
 -> GPU block atlas upload
 -> original Voxy downloads the already-processed atlas
 -> Voxy MipGen keeps the dark-cutout mip rule
 -> Voxy forces leaves onto the solid LOD layer
 -> fragment alpha becomes opaque, but gap RGB is dark leaf green rather than black
```

安装的原版 Voxy 0.2.16 字节码检查
`quad.materialInfo().sprite().contents().mipmapStrategy == DARK_CUTOUT`。安装的
Minecraft 26.1.2 叶子 `.mcmeta` 选择 `dark_cutout`，其
`fillEmptyAreasWithDarkColor` 实现遍历非透明像素，选择 `R+G+B` 最小的像素，
把三个 RGB 通道分别取 3/4，再用该 RGB 填充所有 alpha 为零的像素；alpha
仍保持为零。

Forge 1.20.1 没有这层现代 atlas 预处理，所以 Voxy 下载到的透明叶子像素
RGB 是黑色。LOD shader 又按原版把树叶作为实心材质显示，最终黑色 RGB
直接可见。正确适配是在 Forge `MipGen` 的 darkened 路径补齐同一 level-zero
预处理，并继续使用 darkened mip；不是改成普通 `solidify`，也不是关闭
darkened mip。`MipGenDarkCutoutParityTest` 同时锁定 heap 和生产
`MemoryBuffer` 路径。

## 前几轮为什么没有找到树叶根因

1. **原版追踪没有到底。** 看到了 Voxy 的 `DARK_CUTOUT` 判断，却没有继续追
   Minecraft 资源 metadata、`MipmapGenerator`、`TextureUtil` 和 GPU atlas
   上传顺序。项目规则要求检查依赖到底层，这里实际违反了该规则。
2. **用可见结果倒推内部机制。** 根据“黑”“糊”直接切换 solidify/mip 选项，
   把 level-zero 透明像素 RGB 和后续 mip 过滤混成一个变量。关闭 darkened
   虽能改变外观，却必然损失原版清晰度。
3. **把近似映射写成了已完成 parity。** 旧审计只记录 Embeddium sprite
   transparency 和纹理变暗信号，没有证明它产生了原版 atlas 中相同的像素。
   文档结论早于像素证据，妨碍了继续向下追踪。
4. **缺少最小像素夹具。** 在首次运行客户端前，没有测试“透明像素 alpha
   仍为零、RGB 必须等于最暗有效像素的 3/4、mip 仍走 darkened 模式”。

## 阴影：已经确认和已经排除的内容

用户给出的关键约束是：同一片阴影完全位于 vanilla 区块或完全位于 LOD
区块时形状一致，只有跨越两者边界时才改变；移动视角会放大差异。该约束
更指向两个渲染域的坐标重建、深度或 shadow-space 输入不一致，而不是世界
光照数据本身。

本轮已经得到以下结果：

| 候选 | 证据与试验 | 结论 |
| --- | --- | --- |
| `colortex18` history 邻域缺少有效样本 | 2 像素 history 搜索已在运行时确认生效，但视觉无改善 | 排除为主要根因，试验代码已撤回 |
| Forge 缺少原版 `beginLevelRendering` viewport apply | 原版 Voxy 0.2.16 字节码和 Oculus 1.8.0 注入点确认差异；Forge 已恢复捕获/应用生命周期 | 真实 parity 缺口，但用户复测阴影无改善；不能当作症状根因 |
| 树叶 mip/透明 RGB | 树叶已改善，阴影没有同步改善 | 两个问题独立，禁止继续捆绑修复 |

第一次复测还错误地加载了 Distant Horizons。DH 明确禁止和 Voxy 同时运行，
并使 Complementary 的 Voxy shader patch 因 `dhRenderDistance` 未定义而回退。
该次运行不具备阴影验收资格；后续最小 Embeddium + Oculus 运行才是有效
复测。这也说明兼容全家桶不能作为视觉根因的第一复现环境。

## 前几轮为什么没有找到阴影根因

前几轮仍然以“找到一个原版/Forge 差异”为停止条件，而不是以“测到这个差异
正好改变了边界坏像素”为停止条件。viewport hook 的缺失是事实，但事实上的
差异不自动等于因果。我们没有在同一帧、同一片跨界阴影上捕获 vanilla 和
LOD 两侧的 shadow-space 坐标、级联选择、bias 与 shadow depth 样本，因此
此前所有 shader/history/matrix 修改都缺少能闭环到目标像素的证据。

后续证据排除了这些方向：当前帧 `colortex18` 遮罩在移动中稳定；Voxy 与
Oculus 的 current/previous camera 和矩阵逐值一致；history render target 的
物理纹理、翻转状态也始终命中预期目标。真正缺少的输入不是坐标或历史纹理，
而是 Vanilla 阴影图的投影物几何：位于首个 LOD-only 区块中的树已经被 Voxy
摄取和渲染，却没有对应的 Embeddium 网格进入 Oculus shadow pass。

单独把阴影搜索半径加一圈时，运行时得到 `availableOuterColumns=0`、
`builtOuterColumns=0`，视觉无从改善；把该圈真正加载并建模后得到 `28/28`，
用户确认已有明显效果。由于低角度阴影会跨越多个区块，最终隐藏范围扩为三圈，
配置距离 6 时得到 `visible=6, loaded=9, shadowBlocks=144` 和 `132/132`
已建模隐藏列，用户确认效果符合预期。

正式实现不修改保存的渲染距离：普通 Vanilla 渲染和 Voxy 分界仍使用用户值
N；仅在 Voxy 与 Oculus 光影同时启用时，让单人服务端和 Embeddium 后台准备
至 `min(N+3, 32)`，Oculus shadow pass 使用该完整范围，普通地形遍历仍裁到 N。
远程服务器若没有发送 N+3 范围内的区块，客户端不能凭空补出投影物，这一限制
属于网络数据可用性边界。

## 今后必须遵守的视觉诊断门槛

### 一、先冻结最小复现

- 第一轮只加载硬前置和直接相关集成：本问题固定为 Embeddium + Oculus +
  Voxy + 指定 shaderpack。
- 固定世界、位置、视角、时间、天气、渲染距离、shaderpack profile 和分辨率。
- 记录一片明确跨 vanilla/LOD 边界的阴影，以及两侧各一个同帧对照像素。
- 兼容全家桶只能在根因修复后做回归，不能参与根因定位。

### 二、原版依赖必须追到底层

```text
原版 Voxy 调用点
 -> 被调用的 Minecraft/Iris/Sodium API
 -> 资源 metadata 或生成后的 shader
 -> CPU/GPU 上传前后的实际数据
 -> 最终消费该数据的 shader stage
```

不能因为 Forge 没有同名 public API 就停止；应检查实际发布 jar 的字节码、
资源文件和运行时生成物。任何“Forge 等价信号”都必须通过输出数据等价证明，
不能只靠名称或用途相似。

### 三、每轮只允许一个可证伪候选

每个候选必须先填写：

```text
候选差异：
原版证据：
Forge 证据：
目标像素应出现的可测差值：
单变量探针：
结果：confirmed / excluded / blocked
探针撤回：yes / no
```

没有“目标像素应出现什么差值”，就不能改正式代码。运行时生效但视觉不变的
候选立即标记 `excluded`，不得换个名字继续扩展同一路线。

### 四、本次阴影闭环采用的输入捕获顺序

本次按以下顺序捕获并排除候选，之后才允许修改覆盖范围：

1. vanilla 与 LOD 的 view/projection、inverse matrix、camera position 和 TAA
   jitter/frame index；
2. 两侧由 depth 重建出的 world/view position；
3. shadow cascade/region 选择、shadow projection 坐标、bias、采样 depth 和
   最终 visibility；
4. Voxy patched vertex/fragment 两阶段的 defines、attribute layout 和相关
   uniform 实值；
5. 同帧 vanilla 正常像素、LOD 正常像素、跨界坏像素三者的 RenderDoc pixel
   history 或等价 gated probe。

只有某一项在坏像素处出现与原版或同帧对照不一致，才允许围绕该项写修复。
临时 shader 可用颜色编码一次只显示一个输入，但必须在提交前撤回。本次所有
遮罩、矩阵、camera、history texture 和单圈计数探针均在正式实现前撤回；只
保留一次性的正式范围启用日志。

### 五、完成条件

- 静态 source/bytecode parity 只能证明“实现路径已对齐”。
- 单元测试只能证明“确定性输入的输出已锁定”。
- 视觉问题必须由用户在目标场景确认；没有确认就保持“未解决”。
- 文档必须分别记录 `confirmed divergence` 和 `confirmed symptom cause`，禁止
  再把前者写成后者。

## 复盘后的状态

- 树叶：原版依赖链、像素级夹具、生产 buffer 路径和用户实机结果已闭环。
- 阴影：缺失投影物几何、范围适配、运行时列计数和用户实机结果已闭环；
  history、fog、mip、矩阵和 viewport 生命周期不再作为该症状的候选根因。
