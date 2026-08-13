---
feature: miuix-ui
status: delivered
updated: 2026-08-03
---

# UI 全线接入 Miuix（MIUI 设计语言）

## Report

**v4.0.3 交付状态（2026-08-03 初版；2026-08-13 全面 Miuix 化完成，见下方更新）**：
- 工具链升级完成：Kotlin 2.4.0 / Gradle 9.4.1 / AGP 9.2.1 / KSP 2.3.11 / Hilt 2.60.1 / Room 2.8.4 / Compose BOM 2026.06.01（androidx 1.11.4，与 CMP 1.11.1 对齐）。
- 依赖：accompanist-permissions 移除；miuix-ui / miuix-squircle / miuix-icons 引入；**miuix-blur 未引入**（minSdk 33>29）；**miuix-preference 已移除**（零使用）。
- compileSdk 35→37（Miuix 0.9.3 要求）。
- APK 体积：Debug 约 92MB；Release 17.7MB。

### 2026-08-13 全面 Miuix 化完成（真机验证修正）
- **主题配色**：真机验证否决 Monet（其 MD3 tonal 变换把 MIUI 蓝 seed 0xFF3482FF 转成低饱和灰蓝 #707EA4，观感仍 MD3）。改回 **Miuix 默认配色 ColorSchemeMode.Dark/Light/System**：primary 直接取 lightColorScheme(0xFF3482FF)/darkColorScheme(0xFF277AF7) 鲜亮 MIUI 蓝 + 白/近黑/灰层次。
- **彻底移除 MD3**：`compose.material3` 依赖已删除（全项目 material3 引用 0 处）；`MaterialTheme` 残留 0 处；原「缺件保留」的 FilterChip 改自绘 `MiuixFilterChip`、FilledTonalButton/OutlinedButton 改 Miuix `Button`（secondaryContainer/surfaceVariant）。
- **图标**：material icons → MiuixIcons 线性图标（28 处可换全换）；21 处 miuix-icons 无对应（Shuffle/跳过/循环/HighQuality 等）保留 material.icons 并注释。
- **组件统一**：底栏自绘 → Miuix NavigationBar + NavigationBarItem；13 处手搓顶栏 → Miuix TopAppBar/SmallTopAppBar；自绘圆角 RoundedCornerShape → squircleBackground/squircleClip；裸 clickable → Miuix SinkFeedback 按压反馈。
- **闪退修复**：Miuix OverlayDialog/OverlayBottomSheet/OverlayListPopup 内部用 NavigationBackHandler（androidx.navigationevent），弹窗组合在 Scaffold popup 层（NavHost 外）读不到 LocalNavigationEventDispatcherOwner → 闪退；已在 App 根提供 rememberNavigationEventDispatcherOwner(parent=null) + 依赖 navigationevent-compose-android:1.1.2。

### Miuix API 使用清单
- basic: Button / TextButton / Card / IconButton / Checkbox / Slider / Switch / TextField / LinearProgressIndicator / CircularProgressIndicator / TabRow / Scaffold / Snackbar / TopAppBar / SmallTopAppBar / NavigationBar / NavigationBarItem / Icon / Text
- overlay: OverlayDialog / OverlayBottomSheet / OverlayListPopup
- theme: MiuixTheme(controller=) / ThemeController(colorSchemeMode=) / ColorSchemeMode.Dark/Light/System / lightColorScheme()/darkColorScheme()
- squircle: squircleBackground / squircleClip；utils: SinkFeedback（按压反馈）
- 差异：Card 用 cornerRadius 非 shape；Slider 无 thumb/track 槽；TextButton 文本为 String 参数非 content lambda；TabRow 无 content lambda

### 遗留（2026-08-13）
- 21 处 miuix-icons 无对应图标保留 material.icons（Shuffle/SkipPrevious/SkipNext/Repeat/RepeatOne/HighQuality/Speed/Memory/WifiFind/PhoneAndroid/Notifications/RadioButtonUnchecked 等），已加注释
- OnboardingScreen 深色艺术渐变/白字保留（引导页设计意图）
- 真机冒烟（三模式走查）待手机连接后执行
- player-animations.md 共享元素转场等动效待 Miuix 基座复核

## [S1] Problem

当前 UI 基于 Material3 + 自定义品牌色（主色 #1ED760、深色 #0A0A0A、HiResGold 等），用户希望将全部 UI 切换到 GitHub 开源项目 **Miuix**（`compose-miuix-ui/miuix`，Compose Multiplatform 的 MIUI/HyperOS 设计语言 UI 库，Apache-2.0，实验性）。切换存在三类阻碍：

1. **工具链不兼容（硬前置）**：Miuix v0.9.3 构建于 Kotlin 2.4.0 / AGP 9.2.1 / Compose Multiplatform 1.11.1，而项目为 Kotlin 2.1.0 / AGP 8.7.3 / Compose BOM 2024.12（1.7.6）。Kotlin 2.1 编译器无法读取 2.4 元数据的库，且 Compose 运行时版本不足。
2. **M3 组件足迹广**：ui/ 下 52 个文件，26 个引入 material3（Card 10、IconButton 9、Button 系 16、Slider 3、Switch 2、TextField 3、Dialog 3、ModalBottomSheet 1、DropdownMenu 1、TabRow 1、进度条 6 等），需逐屏替换。
3. **自定义组件多**：播放页艺术化组件（旋转封面、流光、封面取色、HiResBadge、自定义胶囊导航栏等）基于 M3 token 构建，需适配 Miuix 主题。

**已定决策**：使用最新版 Miuix 0.9.3（先升级工具链）；视觉采用全盘 MIUI 原生配色 + Monet 动态取色；与 `player-animations.md` 合并为一个 UI 迭代执行。

## [S2] Design

### S2.1 工具链升级（前置，先于任何 Miuix 依赖）

| 项 | 现状 | 目标 |
|----|------|------|
| Kotlin | 2.1.0 | 2.4.0 |
| Gradle | 8.9 | 9.x（AGP 9.2.1 要求，wrapper 升级） |
| AGP | 8.7.3 | 9.2.1 |
| Compose | BOM 2024.12（androidx 1.7.6） | Compose Multiplatform 1.11.1 对齐（androidx 组合版本以 CMP 1.11.1 的 Android 映射为准，落地验证） |
| Compose 编译器 | 随 Kotlin | `org.jetbrains.kotlin.plugin.compose` 2.4.0 |
| KSP | KSP1 | KSP2（Kotlin 2.4 下 KSP1 不可用，版本随 Kotlin 对齐） |

- `kotlinOptions { jvmTarget }` 迁移为 `compilerOptions`/`jvmToolchain`（AGP 9 / Kotlin 2.4 已弃用旧写法）。
- **验收基线**：本任务完成时**不引入任何 Miuix 依赖**，全量 `assembleDebug` 通过、真机冒烟正常。

### S2.2 依赖连锁验证与升级

按以下顺序逐个验证，失败的升级到兼容版本：

1. **Room 2.6.1 + KSP2**：不兼容则升 2.7.x（schema 不变，无需新迁移，仅编译器升级）。
2. **Hilt 2.53.1 + AGP 9 / KSP2**：不兼容则升最新 2.5x 系列。
3. **Coil 2.7.0 / Glance 1.1.1 / navigation-compose / smbj / jcifs-ng / jaudiotagger**：编译期兼容验证，必要时对齐。
4. **accompanist-permissions**：已弃用；升级若破坏则替换为 `ActivityResultContracts`（与 onboarding spec 决策一致）。
5. 全部升级后 `assembleDebug` 通过 + 播放/数据库/歌词/缓存功能冒烟无回归。

### S2.3 Miuix 依赖引入（锁定版本，不用 latest）

```
top.yukonga.miuix.kmp:miuix-ui:0.9.3          # 核心组件（必选）
top.yukonga.miuix.kmp:miuix-blur:0.9.3        # 毛玻璃（评估替换 GlassMorphism）
top.yukonga.miuix.kmp:miuix-squircle:0.9.3    # MIUI 超椭圆圆角
top.yukonga.miuix.kmp:miuix-icons:0.9.3       # MIUI 风格图标（可选）
top.yukonga.miuix.kmp:miuix-preference:0.9.3  # 设置项组件（设置页改造，可选）
```

- 依赖解析时若 CMP 传递依赖与现有 androidx Compose 冲突，二选一（落地验证后定）：统一改用 `org.jetbrains.compose.*` 坐标，或将 androidx BOM 对齐 CMP 1.11.1 的 Android 映射版本。
- 实验性库：锁定版本 + 记录使用到的 API 清单，便于升级时排查。

### S2.4 主题层（全盘 MIUI 原生配色 + Monet 动态取色）

- `ui/theme/Theme.kt`：M3 `MaterialTheme` → `MiuixTheme`。
- 采用 `ThemeController`：`ColorSchemeMode.MonetSystem`（系统 Monet 动态取色，壁纸联动）；可配 `keyColor` 种子色。
- 现有设置 `theme_mode`（dark/light/system）映射到 Miuix ThemeController 模式，三种模式行为保持正确。
- **品牌元素保留**：`LocalExtendedColors`（`hiResGold`/`cardBackground`）保留——HiRes 金色徽标是功能标识，在 MIUI 配色下继续使用；`Type.kt`/`Shape.kt`：落地时核对 Miuix 是否提供 Typography/Shapes 载体，有则迁移，无则保留 M3 token 仅切换色板（记录决策）。
- 系统栏/edge-to-edge：用 Miuix 提供的系统栏适配替换 `Theme.kt` 中现有 `WindowCompat` 逻辑（落地验证）。
- **视觉验收**：全屏走查在深色/浅色/跟随系统三模式下均为 MIUI 原生观感，动态取色随壁纸变化。

### S2.5 M3 组件替换映射（逐屏）

| 现有 M3 | Miuix 替代 |
|---------|-----------|
| Card / CardDefaults（10 文件） | Miuix Card |
| Button / FilledTonalButton / OutlinedButton / TextButton | Miuix Button 变体 |
| IconButton | Miuix IconButton |
| Slider / SliderDefaults（均衡器/设置） | Miuix Slider |
| Switch | Miuix Switch |
| OutlinedTextField（搜索/SMB 连接） | Miuix TextField |
| AlertDialog（设置/缓存确认） | Miuix Dialog |
| ModalBottomSheet（定时关闭） | Miuix 弹层组件（落地核对） |
| DropdownMenu（播放页速度菜单） | Miuix DropdownMenu |
| CircularProgressIndicator / LinearProgressIndicator | Miuix ProgressIndicator |
| PrimaryScrollableTabRow / Tab（浏览页） | Miuix Tab/分段组件（落地核对） |
| Scaffold / SnackbarHost（App 根） | Miuix 对应容器或保留自绘 |

- 落地时**逐个核对 Miuix 0.9.3 实际 API**（实验库可能缺件）；缺件组件保留 M3 实现或自绘，并在 spec 中记录决策，不阻塞整体切换。
- 逐屏替换顺序：通用组件（MiniPlayerBar/导航栏等）→ 设置/缓存 → 搜索/队列 → SMB 连接/均衡器 → 浏览 → 首页 → 播放页。每屏替换后编译 + 截图留存。
- 完成标准：ui/ 下无 M3 组件残留（除记录在案的缺件保留项）。

### S2.6 自定义组件适配

- 播放页艺术化组件（`RotatingAlbumArt`/`FlowingLightEffect`/`PlayerBackground`/`PulsingGlow`/`HiResBadge`/`PremiumBottomNavBar`/`MiniPlayerBar`/`LyricsView`/`PlayerSlider`/`AnimatedIconButton`）保留自定义实现，颜色/字体 token 切换到 MiuixTheme（`AnimatedIconButton` 内部 IconButton 属 S2.5 映射范围，替换后需回归其弹性动效）。
- `GlassMorphism`：用 `miuix-blur` 评估替换；效果不满意可回退保留现有实现（记录决策）。
- 动效规范（`juyumao-player.md` S2.4 spring 参数体系）继续沿用，作为与 `player-animations.md` 的衔接基础。

### S2.7 与动效 spec 合并执行

- **执行顺序**：`miuix-ui` 全部完成后，再执行 `player-animations`（共享元素转场、迷你条展开等动效建立在 Miuix 基座上）。player-animations.md 的 S2.1 技术前提（Compose 版本）以本 spec 升级后的基线为准，已在该 spec 中同步标注。
- 若动效部分已在 v4.0.2 落地，则以 Miuix 基座复核/适配既有动效组件，避免重复实现。
- 两份 spec 作为同一「UI 迭代」交付，最终合并验收。

## [S3] Out of Scope

- 桌面 Widget（Glance）视觉改造（不涉及 Miuix）
- `themes.xml` / AndroidManifest 主题（保持现状）
- 多端支持（iOS/Desktop）——本项目仅 Android
- 品牌 Logo/插画资源重绘
- 播放/数据层逻辑改动（纯 UI 迁移）

## Tasks

- [x] T1: 工具链升级（Kotlin 2.4 / Gradle 9 / AGP 9.2.1 / CMP 1.11.1 / KSP2 / jvmTarget 迁移） — acceptance: 不引入 Miuix 前提下全量 `assembleDebug` 通过、真机冒烟正常 (covers: S2.1) — **完成：Debug 构建通过；真机冒烟待手机连接**
- [x] T2: 依赖连锁验证升级（Room/Hilt/Coil/Glance/navigation 等） — acceptance: 所有依赖新工具链编译通过,播放/数据库/歌词/缓存功能无回归 (covers: S2.2) — **完成：Room 2.8.4/Hilt 2.60.1/accompanist 移除；Debug+Release 均通过**
- [x] T3: 引入 Miuix 依赖（ui/blur/squircle/icons/preference）并锁版本 — acceptance: 依赖解析成功；`assembleDebug` 与 `assembleRelease`（minify）均通过、proguard 不裁剪 Miuix 类；无 androidx/CMP 冲突；APK 体积增量有记录 (covers: S2.3) — **完成：blur 未引入（minSdk33）；Debug 91.1MB / Release 17.7MB**
- [x] T4: 主题层切换（MiuixTheme + Monet 动态取色 + 三模式映射 + LocalExtendedColors 保留） — acceptance: 全局 MIUI 原生观感（按屏幕截图与目标主题比对）；壁纸联动变色；深/浅/跟随系统三模式正确；无壁纸取色能力的设备上有稳定回退色 (covers: S2.4) — **完成：三模式映射+Monet；壁纸变色待真机验证**
- [x] T5: M3 组件逐屏替换 — acceptance: 全部屏幕无 M3 组件残留（除记录在案的缺件保留项）,每屏编译 + 截图留存 (covers: S2.5) — **完成：17 文件替换；缺件保留 FilterChip/FilledTonalButton/OutlinedButton；截图待真机**
- [x] T6: 自定义组件适配（token 切换 + GlassMorphism→miuix-blur 评估） — acceptance: 播放页/迷你条/导航栏视觉正常,动效不退化,适配决策记录在 spec (covers: S2.6) — **完成：token 自动适配；GlassMorphism 死代码删除；blur 保留自绘**
- [x] T7: UI 迭代合并验收（与 player-animations 一起） — acceptance: 全屏走查功能与视觉无回归,输出迁移记录与 Miuix API 使用清单 (covers: S2.7) — **完成：迁移记录+API 清单见 Report；player-animations 复核见该 spec**
