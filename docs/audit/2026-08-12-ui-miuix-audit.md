# JuYuMao UI 层 Miuix 化审计报告（2026-08-12）

> 触发：用户真机报告「点击主题设置闪退」+ 要求审计「当前代码与主题设置能否实现 Miuix 效果，而非徒有其表」。
> 方法：7 个并行只读子代理按 UI 区域分片审计（主题系统/设置页/播放器/首页浏览搜索/歌单队列统计/公共组件导航/其余页面+全局统计），
> 主代理对 Miuix 0.9.3 关键源码（Scaffold/MiuixPopupUtils/OverlayDialog/DialogContentLayout/RadioButton/ThemeController）逐文件拉取 v0.9.3 tag 源码交叉验证。

---

## 一、总体结论

**主题框架是真 Miuix（骨架真实），但页面内容是「半截皮肤」——『徒有其表』成立，且为系统性。**

- **真实的部分**：根主题注入 `MiuixTheme(controller=ThemeController(...))`（Theme.kt:74）、三模式→`ColorSchemeMode.MonetDark/MonetLight/MonetSystem` 映射正确（Theme.kt:47-51）、根 `Scaffold`/`Snackbar` 为 Miuix 来源、多数业务页用了 Miuix `Card/TextField/IconButton/Switch/Slider/OverlayDialog`、依赖锁定 0.9.3、miuix-blur 按 minSdk 29<33 决策不引入。
- **虚假的部分（核心问题）**：全 App **没有任何 `MaterialTheme(...)` 包裹**，但 **21 个文件仍 import material3**、**138 处 `MaterialTheme.colorScheme` + 99 处 `MaterialTheme.typography`**。Miuix 只提供自己的 `LocalMiuixColorScheme`，**不会向 material3 的 `LocalColorScheme` 注入任何值**——因此这些 token 读到的是 material3 顶层静态默认色（深色 `#D0BCFF`/`#E6E1E5` 淡紫系，浅色 `#6750A4` 紫罗兰），**与 Miuix Monet 壁纸取色完全脱节**：换壁纸这些组件不变色，深色模式下还会出现 M3 淡紫文字/卡片与 Miuix 近黑背景并存的割裂观感。
- **硬编码颜色**：引导页/播放页系列大量 `Color.White/Black/Color(0xFF1A1A2E)` 等写死，完全不随主题。
- **spec 文档纠偏**：`docs/compose/spec/miuix-ui.md` 声称「17 文件替换」「无 M3 残留（除记录缺件）」**不准确**：实际 21 文件仍 import material3，且未记录 `material3.*` 通配（PremiumBottomNavBar、ControlRows）、99 处 `MaterialTheme.typography`、OnboardingScreen 整体未接入 MiuixTheme、以及各页硬编码颜色。

## 二、全局统计（任务 7）

| 指标 | 数值 |
|---|---|
| import material3 的 UI 文件数 | 21 |
| `MaterialTheme.colorScheme` 出现次数 | 138 |
| `MaterialTheme.typography` 出现次数 | 99 |
| 使用 `MiuixTheme.colorScheme/textStyles` 的页面 | Settings/Cache/Equalizer/SmbConnect 等（token 层良好） |
| 三页（Playlist/Queue/Statistics）`MiuixTheme.colorScheme` 出现次数 | **0** |
| `MaterialTheme(...)` 包裹调用 | **0**（全部是访问器） |

## 三、分区域结论（7 个子代理）

| 区域 | Miuix 化估计 | 关键问题 |
|---|---|---|
| 主题系统 + App 骨架（T1） | 框架 90%+ | 根注入正确；`ExtendedColors.cardBackground` 无消费方（死代码）；Color.kt 存 Spotify 绿调色板与主题声明矛盾 |
| 设置页（T2） | 良好 | OverlayDialog 宿主机制**已验证正常**（见下节，原 NPE 判断推翻）；RadioButton 双触发（低）、showThemeDialog 未 rememberSaveable（低） |
| 播放器页（T3） | 播放页系列硬编码重灾区 | ControlRows `material3.*` 通配 + 12 处 Color.White/Black；PlayerBackground `Color(0xFF1A1A2E)`；AlbumArtPager/BottomFunctionBar/RotatingAlbumArt/LyricsView 混 M3 |
| 首页/浏览/搜索（T4） | 50-60% | 三页文字样式与颜色**全部** M3；FilterChip/FilledTonalButton 为记录的缺件保留；搜索页用 Miuix TextField 而非 SearchBar；无 Miuix PressFeedback/overscroll |
| 歌单/队列/统计（T5） | 组件壳 90% / token 0% | PlaylistScreen 三处 OverlayDialog 可用（宿主正常）；L218 FilledTonalButton M3；StatisticsScreen Card 底色 M3 紫 10% |
| 公共组件 + 导航（T6） | 公共层约 15% | **PremiumBottomNavBar 完全自绘 + 全 M3**（最常驻全局组件游离 Miuix 外）；AnimatedIconButton/RotatingAlbumArt/PulsingGlow 零 Miuix 引用；MiniPlayerBar「Miuix 壳 + M3 内脏」；RotatingAlbumArt LaunchedEffect 每帧重启 |
| SMB/均衡器/缓存/引导 + 统计（T7） | SmbConnect 90%；Onboarding 0% | **OnboardingScreen 重灾区**：无 MiuixTheme、14 处 M3 typography、硬编码深色渐变+白字+金色按钮、M3 与 Miuix ButtonDefaults 混用；SmbConnect 的 FilledTonalButton/OutlinedButton 为缺件保留（可接受） |

**『徒有其表』最严重 5 处**：
1. OnboardingScreen.kt —— 纯 M3 外观套 Miuix 骨架
2. ControlRows.kt —— `material3.*` 通配 + 12 处硬编码白/黑
3. PremiumBottomNavBar.kt —— 全局底栏完全自绘 + 全 M3 token
4. Home/Browse/Playlist/Queue/Search/Statistics/SleepTimerSheet 等 12 文件 —— 138 处 M3 colorScheme + 99 处 typography
5. PlayerBackground.kt + 播放页 M3 原子组件 —— 硬编码深紫背景

## 四、「主题设置闪退」专项分析（主代理源码验证）

### 4.1 验证链条（Miuix 0.9.3 tag 源码）

| 文件（v0.9.3） | 关键事实 |
|---|---|
| `basic/Scaffold.kt` | **默认内嵌 `MiuixPopupHost()`**（`popupHost: @Composable () -> Unit = { MiuixPopupHost() }`），并提供全部四个弹窗 CompositionLocal：`LocalPopupStates/LocalDialogStates/LocalRootDialogStates/LocalRootPopupStates` |
| `utils/MiuixPopupUtils.kt` | `DialogLayout` 取列表用 **`LocalRootDialogStates.current ?: LocalDialogStates.current`**（Elvis 回退，**不会 NPE**）；`LocalRootDialogStates` 默认 null 仅在无 Scaffold 时出现 |
| `overlay/OverlayDialog.kt` | `OverlayDialog → DialogContentLayout(popupHost=...) → DialogLayout`，无平台 Popup/Dialog，纯叠层渲染 |
| `layout/DialogContentLayout.kt` | 纯 Compose 布局（Box/Column/graphicsLayer/squircleSurface），无崩溃点 |
| `basic/RadioButton.kt` | 纯 Path 绘制 + selectable/pressable，无崩溃点 |
| `theme/ThemeController.kt` | Monet 模式 `keyColor?.let{...} ?: platformDynamicColors(dark)`，低版本设备回退默认色，无崩溃点 |

### 4.2 结论

- **项目根为 Miuix Scaffold（JuYuMaoApp.kt:8 通配 import、:96 Scaffold 调用）→ OverlayDialog/OverlayBottomSheet/OverlayListPopup 均有宿主，弹窗机制正常，原「LocalRootDialogStates=null → NPE」推断不成立。**
- **静态分析未发现「主题模式」点击路径上的任何必崩点**（弹窗链路、RadioButton、Monet 切换、SettingsScreen 本体、P1+P2 改动均验证过）。
- **剩余可能**：① 设备/ROM 特有崩溃（需 logcat）；② 与主题无关的并发/生命周期问题（如 hiltViewModel 获取、DataStore 读写竞态）；③ 用户操作细节差异（如点的是其它入口）。
- **下一步必须真机 logcat**：`adb logcat -v time | grep -E "FATAL|AndroidRuntime|hezi"`，或复现后 `adb bugreport`。修复前不臆测改动。

## 五、修复优先级清单

### P0（闪退定位，阻塞真机使用）
- 需 logcat 确认「主题设置」闪退真实堆栈；备选防御：`SettingsScreen` 主题选择改用 `WindowDialog`（Miuix 0.9.3 新增的独立窗口弹窗，不依赖 Scaffold 宿主）可绕过一切宿主类问题。

### P1（徒有其表核心：token 层统一，机械替换，低风险高收益）
1. 全局 `MaterialTheme.colorScheme.* → MiuixTheme.colorScheme.*`（138 处，注意 `onSurfaceVariant`/`surfaceVariant` 等字段名差异）
2. 全局 `MaterialTheme.typography.* → MiuixTheme.textStyles.*`（99 处，M3 headline/body/label 体系 → MIUI title/body/footnote 体系，需逐处语义映射）
3. 清理 `material3.*` 通配 import（PremiumBottomNavBar、ControlRows）

### P2（硬编码颜色与组件整改）
4. OnboardingScreen 接入 MiuixTheme（渐变/文字色随深色模式）
5. ControlRows/PlayerBackground 硬编码白/黑/深紫 → Miuix token
6. PremiumBottomNavBar 改 Miuix `NavigationBar`
7. MiniPlayerBar/播放页 M3 颜色 → Miuix token
8. 搜索页升级 Miuix `SearchBar`
9. RotatingAlbumArt LaunchedEffect 每帧重启修复

### P3（清理与文档）
10. Color.kt 清理 Spotify 绿死代码、ExtendedColors.cardBackground 无消费方
11. `docs/compose/spec/miuix-ui.md` 纠偏（21 文件 import material3、99 处 typography、Onboarding 未接入等）
12. RadioButton 双触发收敛、showThemeDialog 改 rememberSaveable

## 六、审计证据

- 7 个子代理报告：T1 sa_20260812_094554_000000000_c969e7fa9083、T2 ..._a67f52ba71ce、T3 ..._aa0e0e7b257f、T4 ..._b9cc2eb0d3bd、T5 ..._e89bd26abbf9、T6 ..._e1715d39bf09、T7 ..._9389b7add80b
- Miuix v0.9.3 tag 源码：Scaffold.kt / MiuixPopupUtils.kt / OverlayDialog.kt / DialogContentLayout.kt / RadioButton.kt / ThemeController.kt（GitHub compose-miuix-ui/miuix）
- 本项目代码：app/src/main/java/com/hezi/juyumao/ui/**（106 Kotlin 文件）
