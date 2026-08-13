# UI 排版规范（Typography & Spacing）

> 本规范是「局域猫」全应用排版系统的唯一权威来源。所有 Screen/组件必须遵循，
> 禁止在 Screen 内硬编码 `fontSize`、随意 `copy(fontWeight = ...)`、或使用非节奏间距值。

## 1. 排版来源

全部文字样式统一取自 `MiuixTheme.textStyles`（Miuix 0.9.3 内置 14 档，为 MIUI/HyperOS 官方字号阶梯）。
**不得**再引用 `MaterialTheme.typography`（已彻底移除）、不得硬编码 `.sp`。

## 2. 语义 → 档位映射（唯一映射表）

MIUI/HyperOS 的排版哲学：**默认 Normal 字重，靠字号与颜色建立层级，不靠加粗**。
全应用唯一允许 `Bold` 的档位是 `subtitle`（及 Onboarding 营销大标题、播放页歌名 hero 两处特例）。

| 语义 | Miuix 档位 | 字号 | 字重 | 用途 |
|---|---|---|---|---|
| `hero`（页面主标题） | `title2` | 24sp | Normal | 播放页歌名、Onboarding 主标题 |
| `statValue`（大数值） | `title3` | 20sp | Normal | 统计卡片数值、倒计时读数 |
| `sectionHeader`（区块标题） | `headline1` | 17sp | Normal | 页面内的分组标题（"最近播放""快捷操作""自动发现"等） |
| `itemTitle`（列表/卡片主文字） | `body1` | 16sp | Normal | 歌曲名、歌单名、设置项标题、设备名 |
| `body`（正文） | `body2` | 14sp | Normal | 说明性正文、空状态提示 |
| `subtitle`（强调副标题） | `subtitle` | 14sp | **Bold** | 需要强调的短标题、段落强调 |
| `caption`（辅助说明） | `footnote1` | 13sp | Normal | 歌手名、副标题、描述、摘要 |
| `caption2`（次要注记） | `footnote2` | 11sp | Normal | 时间戳、时长、徽标、超小注记 |
| `button` | `button` | 17sp | Normal | 按钮文字（组件默认，通常无需显式指定） |

### 关键纠正（本次重构核心）

- **区块标题一律 `headline1`（17sp）**，不得再用 `title4`（18sp，那是"页面级小标题"）。
  历史误用：Home/Cache/Equalizer/Smb 的 "本地音乐""NAS 连接""快捷操作""最近播放""缓存占用""自动发现""手动连接" 等。
- **歌曲名/歌单名/设备名 一律 `body1`（16sp）**，不得用 `footnote1`（Home 的 RecentSongCard 曾误用 13sp）。
- **大数值一律 `title3`（20sp）**，不得 `title4.copy(fontWeight = Bold)` 或 `title1` 混用。
- **删除所有 `.copy(fontWeight = FontWeight.Bold)`**，除非是上表允许的 Bold 档位。

## 3. 字重规则

- 默认 Normal。层级靠字号（17/16/14/13/11）与颜色（onSurface / onSurfaceSecondary）区分。
- 唯一 Bold 档位：`subtitle`。
- 特例：Onboarding 营销大标题、播放页歌名可用 `title2.copy(fontWeight = Bold)`（品牌感）。
- 播放列表"播放中"高亮、次要徽标等，用 `onSurface` 主色（或 primary）区分，不加粗。

## 4. 间距节奏（4dp 基准）

统一取自 `ui/theme/Dimens.kt`。禁止使用 6/10/14/20/28 等非节奏值做间距。

| token | 值 | 用途 |
|---|---|---|
| `Dimens.xs` | 4dp | 图标与文字微间距 |
| `Dimens.sm` | 8dp | 紧密元素间距 |
| `Dimens.md` | 12dp | 卡片间/小组间距 |
| `Dimens.lg` | 16dp | 页面边距、卡片内边距、区块间距 |
| `Dimens.xl` | 24dp | 大分组间距 |
| `Dimens.xxl` | 32dp | 页面顶部留白 |

- 页面左右统一 `Dimens.lg`（16dp）。
- 卡片内边距统一 `Dimens.lg`（16dp）。
- 大分组间距 `Dimens.xl`（24dp），小组间距 `Dimens.md`（12dp）。

## 5. 图标/封面尺寸（非间距，允许的规格值）

图标：导航 24dp、卡片功能图标 28dp、列表图标 40dp、封面缩略 80dp、封面大图按屏宽。
这些是设计规格，不强制走 Dimens，但不得随意缩放。

## 6. 例外（保留的硬编码，均有正当理由）

- `LyricsView` 歌词字号 18f 动态缩放（16/22/18 三态 + 行高 1.5）——歌词滚动效果需要。
- `SleepTimerSheet` 48sp 倒计时大数字——计时器读数，属 `statValue` 之上的特大档。
- `JuYuMaoWidget`（Glance AppWidget）24/14/12/18sp——Glance 独立排版体系，与主应用 Compose 隔离。
- `OnboardingScreen` 深色艺术渐变页——营销页，允许更大字号与部分 Bold。
