---
feature: advanced-player-features
status: in-progress
updated: 2026-08-03
---

# 播放器进阶功能

## Report

**v4.0.2 交付状态（2026-08-03 多 AGENT 审计核实）**：
- 已落地：收藏持久化（PlayerScreen→SongDao.updateFavorite）；歌单（PlaylistDao/playlist_songs 表/PlaylistScreen，数据库已至 v3）；无缝播放与缓冲设置生效；倍速菜单；音效增强（BassBoost/Virtualizer/LoudnessEnhancer）。
- 未落地（遗留）：交叉淡化（T5）；专辑/艺术家/流派浏览（T8）、播放统计埋点与报告（T9/T10）未核实完成（BrowseScreen 存在未提交 WIP，疑似进行中）。

## [S1] Problem

播放器基础功能齐全（播放控制、队列、均衡器、定时关闭、歌词、搜索、离线缓存），但存在以下缺口：

1. **两个现成的坑**：收藏按钮在 `PlayerScreen.kt` 里只是局部 UI 状态，从不写入数据库（`SongDao.updateFavorite` 已存在但未被调用）；歌单表 `PlaylistEntity` 是死代码（无 DAO、无 UI、无歌曲关联表）。
2. **设置项空转**：「无缝播放」与「缓冲大小」设置只存 DataStore（`SettingsDataStore.kt:18-19,28-29`），从未作用于 ExoPlayer。
3. **体验类进阶缺失**：无倍速（`PlaybackState.playbackSpeed` 字段存在但未生效）、无淡入淡出/交叉淡化、音效仅均衡器一项。
4. **浏览与数据维度缺失**：浏览页无专辑/艺术家/流派维度（juyumao-player spec S2.5 规划过未实现）；无播放统计（`playCount`/`lastPlayedAt` 列已存在但无人写入）。

## [S2] Design

### S2.1 收藏持久化

- 播放页心形按钮改为读写数据库：`SongDao.updateFavorite(id, isFavorite)`；当前歌曲的收藏态由 PlayerViewModel 按歌曲 id 查询驱动（`SongDao.getById` 或队列歌曲实体携带）。
- 浏览/搜索/队列列表项的收藏入口统一为同一逻辑。
- 新增「我喜欢」入口（浏览页 Tab 或首页快捷入口），数据源 `SongDao.getFavorites()`（已存在）。
- `SongEntity.isFavorite` 列已存在，**无需迁移**。

### S2.2 歌单落地

- **数据层**：新增关联表 `playlist_songs(playlistId, songId)`（复合主键，外键指向 `playlists`/`songs`）；数据库 v2→v3 迁移（现有 `MIGRATION_1_2` 之后追加，禁止 destructive fallback，`JuYuMaoDatabase.kt:52` 已有注释约束）。
- 新增 `PlaylistDao`：歌单 CRUD、歌单内歌曲增删、按歌单查歌曲（含歌曲实体联查）、歌单歌曲计数。
- **UI 层**：
  - 歌单列表页（浏览页新 Tab 或首页入口）：封面拼贴/首曲封面 + 名称 + 歌曲数；
  - 歌单详情页：歌曲列表（复用现有列表组件），支持整单播放（`PlaybackController.loadPlaylist`）、单曲播放、移除歌曲；
  - 添加歌曲弹层：从播放页/详情页/搜索页/队列调用，选择目标歌单；
  - 新建/重命名/删除歌单。
- 歌单播放走现有 `loadPlaylist` 接口，不新增播放机制。

### S2.3 无缝播放 / 淡入淡出 / 交叉淡化 / 倍速

- **无缝播放**：激活 `GAPLESS_PLAYBACK` 设置（默认关）。开启时 `PlaybackController` 改为整队列加载（`exoPlayer.setMediaSources(list)` + `prepare()` 一次），曲目间由 ExoPlayer 自动衔接；同容器格式（FLAC/MP3）天然无缝，避免当前逐首 `setMediaSource` 的重建停顿。实现时验证 Media3 1.5.1 下 FLAC 专辑曲目间无间隙。
- **淡入淡出 / 交叉淡化**：基于 Media3 1.5+ 的 AudioFade 能力（播放/暂停淡入淡出、切歌交叉淡化）。**实现前先核实 `androidx.media3:media3-common` 1.5.1 提供的 fade API 签名与能力**，若 API 不支持交叉淡化则退回「仅淡入淡出 + 无缝」方案。新设置项 `crossfade_duration`（默认 0 即关闭）。
- **倍速**：播放页速度菜单（0.5x/0.75x/1.0x/1.25x/1.5x/2.0x），调用 `ExoPlayer.setPlaybackSpeed`；持久化到 DataStore 新 key `playback_speed`；`PlaybackState.playbackSpeed` 字段已存在，直接生效。倍速下歌词仍按 position 同步（现有轮询机制天然兼容）。
- 新 DataStore key：`playback_speed`、`crossfade_duration`。

### S2.4 音效增强

- `AudioEffectsManager` 在现有 `Equalizer` 基础上扩展：`BassBoost`（低音）、`Virtualizer`（虚拟环绕）、`LoudnessEnhancer`（响度，API 29+ 原生支持）。
- UI：均衡器页增加「音效」分组或独立音效设置区：低音强度滑块、虚拟环绕开关、响度增强开关。
- 沿用现有 `attachToPlayer(audioSessionId)` 绑定模式；效果器创建失败（设备不支持，API 返回 null）时隐藏/禁用对应项，不崩溃。

### S2.5 专辑 / 艺术家 / 流派浏览

- `SongDao` 新增分组查询：专辑（album + albumArtist，封面取组内任一歌曲）、艺术家（排除「未知艺术家」）、流派（排除 null）。
- 浏览页在现有 Tab 结构上补齐三个维度：
  - 专辑网格页（2 列，复用列表组件）→ 专辑详情页（曲目列表，整专播放）；
  - 艺术家列表 → 艺术家详情页（该艺术家歌曲/专辑）；
  - 流派列表 → 流派详情页。
- 数据源为本地 + SMB 已入库歌曲（`songs` 表），不新增扫描逻辑。

### S2.6 播放统计 / 听歌报告

- **埋点**：`PlaybackController` 在曲目开始播放（onIsPlayingChanged 或曲目切换）时递增 `playCount`、更新 `lastPlayedAt`；新增 DAO 方法 `incrementPlayCount(id)`（列已存在，无需迁移）。
- **报告页**：设置或首页入口进入，周/月维度展示：总播放次数、总时长（Σ duration × playCount）、TOP10 歌曲、TOP10 艺术家。
- 首页统计卡片中的「播放次数」改用真实 `playCount` 汇总。

## [S3] Out of Scope

- 歌单云同步/跨设备
- 智能歌单 / 自动推荐 / 私人 FM
- 播客 / 有声书
- 歌词逐字卡拉OK增强（归动效方向）
- 在线曲库 / 音乐商店

## Tasks

- [x] T1: 收藏持久化（播放页 + 列表 + 我喜欢入口） — acceptance: 收藏状态跨重启保留，列表/播放页/我喜欢三处状态一致 (covers: S2.1) — 已落地
- [x] T2: 歌单关联表 + PlaylistDao + 数据库迁移 v3 — acceptance: 升级安装不丢数据；歌单/歌曲关联增删查可用 (covers: S2.2) — 已落地（version=3, MIGRATION_2_3）
- [x] T3: 歌单 UI（列表页/详情页/添加弹层/新建重命名删除） — acceptance: 可新建歌单、加歌、整单播放、移除歌曲 (covers: S2.2; depends: T2) — 已落地（PlaylistScreen）
- [x] T4: 无缝播放生效 — acceptance: 开启后同格式连续曲目切换无间隙；关闭时行为不变 (covers: S2.3) — 已落地（PlaybackController 整队列加载）
- [ ] T5: 淡入淡出与交叉淡化 — acceptance: 播放/暂停/切歌有平滑音量过渡；`crossfade_duration=0` 时关闭；API 不支持时退化为淡入淡出 (covers: S2.3)
- [x] T6: 倍速控制 — acceptance: 速度菜单生效且重启后保留，倍速下音高不变、歌词同步 (covers: S2.3) — 已落地（播放页速度菜单）
- [x] T7: 音效增强（低音/虚拟环绕/响度） — acceptance: 三个效果器可调可开关；不支持设备上对应项隐藏 (covers: S2.4) — 已落地（AudioEffectsManager）
- [ ] T8: 专辑/艺术家/流派浏览与详情页 — acceptance: 三维度可浏览，可整专/整艺术家播放 (covers: S2.5)
- [ ] T9: 播放统计埋点 — acceptance: 播放超过阈值（如 ≥10s）后 `playCount` 递增、`lastPlayedAt` 更新，防误触计次 (covers: S2.6)
- [ ] T10: 听歌报告页 — acceptance: 周/月报告的总次数/时长/TOP 与埋点数据一致；首页播放次数卡片显示真实值 (covers: S2.6; depends: T9)
