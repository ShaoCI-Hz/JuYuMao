---
feature: onboarding
status: delivered
updated: 2026-08-03
---

# 首次使用引导

## Report

**v4.0.2 交付状态（2026-08-03 多 AGENT 审计核实）**：引导页、根路由切换、集中权限申请、NAS 连接引导均已实现（OnboardingScreen 存在，JuYuMaoApp 按 `onboarding_completed` 路由，MainActivity 已移除启动即弹权限）。
**遗留修复项（下轮处理）**：老用户升级安装后 `onboarding_completed` 默认 false，会被强制进入引导——需区分「首次安装 vs 升级」（见 T1 验收补充）。

## [S1] Problem

当前首次启动体验存在三个问题：

1. **无引导页**：App 直接进入功能页（`NavGraph.kt` 根路由即 Home），新用户对「局域网 NAS 直连、Hi-Res 高音质」等核心价值无认知，也缺少品牌感。
2. **权限申请粗暴**：`POST_NOTIFICATIONS` 在 `MainActivity.kt:26-38` 启动即弹（无理由说明，TargetSdk 35 下体验差）；`READ_MEDIA_AUDIO` 需点击「扫描」按钮才申请（`HomeScreen.kt:46-53`），首启流程割裂。
3. **无 NAS 引导链路**：核心功能是连接 NAS 串流，但新用户没有任何引导走向 SMB 连接页。

## [S2] Design

### S2.1 触发与状态

- DataStore 新增 key `onboarding_completed`（默认 false）。
- 首次启动：根路由根据状态选择 OnboardingScreen 或 Home；完成引导后置 true。
- 设置页新增「重新查看引导」入口（重置标记后再次展示）。
- **移除 `MainActivity` 启动即弹的通知权限请求**（`requestNotificationPermission` 删除），权限并入引导流程。

### S2.2 页面结构（3-4 页 HorizontalPager，简约美观）

| 页 | 内容 |
|----|------|
| P1 品牌 | Logo（旋转猫头/音符元素）+「局域猫」+ 一句 slogan；深色背景 + 毛玻璃 + 金色（HiResGold）点缀 |
| P2 核心能力 | 三张卡片：NAS 直连串流、Hi-Res 全格式高音质、沉浸歌词与均衡器 |
| P3 权限说明 | 逐条说明用途：通知（播放控制/进度显示）、本地音频（扫描本地音乐）；仅说明不弹窗 |
| P4 收尾 | 权限申请按钮 +「开始使用」主按钮 +「连接 NAS」次级按钮 |

- 视觉与主题系统一致（深色/浅色随主题，毛玻璃风格沿用 `GlassMorphism`）。
- 动效：每页元素 staggered 渐入（alpha + slide），页码指示器，spring 参数沿用 juyumao-player spec S2.4 规范。

### S2.3 权限集中申请

- 在 P4 集中申请，用已声明的 `accompanist-permissions`（`libs.versions.toml` 已声明未使用）或 `ActivityResultContracts`：
  - `POST_NOTIFICATIONS`（Android 13+ / API 33+）；
  - `READ_MEDIA_AUDIO`（Android 13+）/ `READ_EXTERNAL_STORAGE`（Android 12- / API ≤ 32，Manifest 已声明 maxSdk 32）。
- 已授权或系统不可用的权限自动跳过；每项显示「已授权 / 未授权」状态。
- **拒绝不阻塞**：用户拒绝后仍可「开始使用」，进入首页后由现有按需申请逻辑兜底。

### S2.4 NAS 连接引导

- P4「连接 NAS」：跳转 `SmbConnectScreen`，首次进入时给出轻量高亮提示（如顶部引导文案）。
- 「开始使用」直接进首页；不强制连接 NAS。

### S2.5 既有权限申请改造

- `HomeScreen` 扫描按钮：未授权时点击仍触发音频权限申请（保留现有行为，作为引导拒绝后的兜底）。
- 引导完成后拒绝通知权限：不重复弹窗，播放通知按系统规则处理（用户可去系统设置开启）。

## [S3] Out of Scope

- 视频 / 多语言引导（第一版仅中文）
- 引导页内嵌产品教程动画（可后续归动效方向）
- 注册 / 账号体系
- 引导页数据埋点分析

## Tasks

- [x] T1: 引导状态存储（DataStore `onboarding_completed`）+ 根路由切换 + 设置页重看入口 — acceptance: 全新安装首启进引导；完成后重启直接进首页；重看入口可再次展示 (covers: S2.1) — 已落地；**遗留修复**：既有用户升级后不触发引导（首次安装 vs 升级需区分，如按版本升级写入标记）
- [x] T2: 引导页 UI（P1-P4 + 渐入动效 + 页码指示器） — acceptance: 4 页可滑动浏览，元素渐入动画，视觉与现有主题一致 (covers: S2.2) — 已落地
- [x] T3: 权限集中申请 — acceptance: 引导末尾按系统版本申请通知/音频权限；已授权项自动跳过；拒绝后仍可进入首页 (covers: S2.3) — 已落地
- [x] T4: NAS 连接引导 — acceptance: 引导末尾「连接 NAS」进入 SMB 连接页并显示引导提示 (covers: S2.4) — 已落地
- [x] T5: 移除启动即弹权限 + 扫描按钮兜底回归 — acceptance: 启动不再直接弹通知权限；扫描按钮未授权时仍可申请 (covers: S2.5) — 已落地
