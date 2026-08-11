---
feature: player-animations
status: in-progress
updated: 2026-08-03
---

# 播放器动效升级

## Report

**v4.0.2 交付状态（2026-08-03 多 AGENT 审计核实）**：
- 已落地：PulsingGlow 呼吸动画（CoverLyricsPager 已使用）；MiniPlayerBar 内部动效（封面 crossfade/进度 spring）；频谱可视化（SpectrumBars+SpectrumAnalyzer+设置开关）；AnimatedIconButton 已在 MiniPlayerBar 使用（尚未全局替换）。
- 未落地（遗留，本轮与 miuix-ui 合并执行）：共享元素转场（T1）；迷你条展开/收起（T2）；封面/歌词翻页增强（T5）未核实。

## [S1] Problem

播放页已有一定动效基础（旋转封面、流光背景、封面取色渐变、歌词逐行滚动、沉浸模式切换、底部导航弹性），但缺失音乐 App 最具表现力的两类转场，且存在多个半成品：

1. **无「列表 → 播放器」共享元素转场**：从列表点击封面直接跳转播放页，封面没有「飞入」动画（juyumao-player spec S2.4 规划过 SharedElement，未实现）。
2. **无「迷你条 → 全屏」展开/收起动画**：`MiniPlayerBar` 点击后是生硬的页面跳转；迷你条本身也无内部动效。
3. **半成品组件**：`PulsingGlow.kt` 是静态径向渐变（名不副实，无呼吸动画）；`AnimatedIconButton.kt` 已实现弹性按压但全项目未使用；`MiniPlayerBar` 无动画（仅 Coil crossfade）。
4. **无频谱可视化**：均衡器页与播放页均无实时频谱。
5. **封面/歌词翻页生硬**：`CoverLyricsPager` 使用默认 VerticalPager 翻页，无缩放/透明度跟随；切歌无过渡。

## [S2] Design

动效风格延续 juyumao-player spec S2.4 动画规范（spring 参数体系），不引入外部动画库。

### S2.1 列表 → 播放器共享元素转场

> **执行前提（审计修正）**：本 spec 与 `miuix-ui.md` 合并执行，执行顺序为 miuix-ui 完成后本 spec 再开工。届时 Compose 已升级至 CMP 1.11.1 对应版本，**原「Compose 1.7 / BOM 2024.12」技术前提已失效**，落地时先在新基线复核 `SharedTransitionLayout` 可用性，再决定首选/备选方案。

- 首选方案：`SharedTransitionLayout`（升级后的 Compose 版本，实验 API）——列表项封面与播放页封面共享过渡。
- 备选方案（若 navigation 场景下实验 API 受限）：自定义入场动画——点击时记录封面在列表中的布局位置/尺寸，播放页入场时用同一图片经 `graphicsLayer` 做缩放+位移动画放大到播放页封面位置。
- 验收基准：点击列表封面后，播放页封面从点击处放大入场；返回时对称缩小。
- 前置：列表项封面为独立 Composable（可复用 `RotatingAlbumArt`/列表缩略图），播放页封面容器支持动画初始态。

### S2.2 迷你播放器展开 / 收起

- 点击迷你条 → 全屏播放器呈现「原位放大」动画：封面初始尺寸 = 迷你条封面尺寸，spring 放大至播放页封面尺寸；背景从纯色渐变过渡到流光背景（复用 `PlayerBackground` 取色结果）。
- 实现路线二选一（实现前验证，以收起动画与展开对称为验收）：
  - A. 全屏播放器改为自绘可折叠容器（ModalBottomSheet 式）承载，展开/收起由手势 + 动画驱动，天然支持双向；
  - B. 保留 navigation 路由，入场动画模拟放大，返回动画反向。
- `MiniPlayerBar` 补充：切歌时封面 crossfade 过渡、进度更新动画、按压水波纹。

### S2.3 补全半成品

- `PulsingGlow`：补 `rememberInfiniteTransition` 呼吸动画（scale 1.0→1.15 + alpha 周期约 2s，Linear），颜色跟随封面主色（复用 `PlayerBackground` 的 Palette 取色结果）。
- `AnimatedIconButton`：全局替换播放/暂停/上下首/收藏/队列/关闭等按钮（现有 spring 参数 damping=0.35/stiffness=500 沿用）。
- 其他静态组件（`GlassMorphism` 等）不在本轮改动范围。

### S2.4 频谱可视化

- 数据源：Android `Visualizer`（绑定自身 `audioSessionId`，API 29+ 无需额外权限）→ FFT → Compose `Canvas` 绘制柱状/环形频谱。
- 位置：播放页封面后方或歌词下方（与流光背景分层，不遮挡文字）；均衡器页复用同一组件。
- 开关：设置项「频谱可视化」默认开启可关闭，防止低端机性能问题。
- 性能约束：Canvas 绘制节流（约 30-60fps）、`graphicsLayer` 缓存、仅在播放页启用、不进入列表页。

### S2.5 封面 / 歌词切换增强

- `CoverLyricsPager`：翻页拖拽时目标页随 offset 缩放 + 透明度渐变（映射 VerticalPager offset），松手回弹（spring）。
- 切歌：封面淡出 + 旋转进入过渡；歌词行滚动精度保持现状。

## [S3] Out of Scope

- Lottie / 外部动画库
- 3D 倾斜 / 视差效果
- 粒子系统
- 桌面 Widget 动效（Glance 能力受限）

## Tasks

- [ ] T1: 列表 → 播放器共享元素转场 — acceptance: 点击列表封面，播放页封面从点击位置放大入场；返回动画对称 (covers: S2.1)
- [ ] T2: 迷你播放器展开/收起 + 内部动效 — acceptance: 迷你条点开展开动画流畅，收起可反向；切歌封面过渡可见 (covers: S2.2)
- [x] T3: 补全 PulsingGlow 呼吸动画并全局启用 AnimatedIconButton — acceptance: 光晕随播放呼吸可见；播放页所有主按钮有弹性按压反馈 (covers: S2.3) — 部分落地（PulsingGlow 已呼吸；AnimatedIconButton 仅 MiniPlayerBar 使用，全局替换待 miuix-ui 基座复核）
- [ ] T4: 频谱可视化（播放页 + 均衡器页 + 开关） — acceptance: 播放中显示实时频谱；开关可关闭；播放页 10 分钟运行无显著掉帧 (covers: S2.4)
- [ ] T5: 封面/歌词翻页与切歌过渡增强 — acceptance: 翻页拖拽有缩放/透明度跟随，切歌有封面过渡 (covers: S2.5)
- [ ] T6: 动效性能回归 — acceptance: 低端机（或模拟器节流）上播放页持续运行无卡顿；无未回收动画协程泄漏 (covers: S2.1-S2.5)
