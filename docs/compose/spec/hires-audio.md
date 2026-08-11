---
feature: hires-audio
status: in-progress
updated: 2026-08-03
---

# Hi-Res 高音质支持

## Report

**v4.0.2 交付状态（2026-08-03 多 AGENT 审计核实）**：
- 已落地：HiRes 统一判定已抽到 `data/local/metadata/HiRes.kt`（sampleRate>48000 ‖ bitsPerSample>=24 ‖ dsf/dff，FLAC 不再误判）；播放页金色 Hi-Res 徽标与音频规格行（AudioSpecRow）已实现；缓冲设置已生效（DynamicLoadControl）。
- 未落地（遗留）：FFmpeg 扩展（T3，方案见 S2.3 修正）；`SmbFileScanner` 未填充 sampleRate/bitsPerSample（SMB 歌曲 HiRes 判定仅按扩展名）。

## [S1] Problem

用户音乐库包含大量高音质文件（FLAC 24bit/192kHz、DSD、APE、WavPack 等），但当前播放链路存在四个缺口：

1. **HiRes 判定过宽**：`LocalMusicScanner.isHiRes()` 把所有 FLAC 都标记为 HiRes（`LocalMusicScanner.kt:199-203`，判定为 `sampleRate > 44100 || 扩展名属于 HIRES_EXTENSIONS`，而 FLAC 就在该集合内），导致 HiRes 标记失真。
2. **宣称的格式实际无法播放**：README 与 juyumao-player spec 宣称支持 DSD/APE/WavPack，但 `app/build.gradle.kts:89-93` 未引入 `media3-exoplayer-ffmpeg`，这些格式解码必然失败。
3. **无 HiRes 输出处理**：`PlayerModule.kt` 使用 ExoPlayer 默认配置，无渲染器定制；HiRes 大文件（24/192 FLAC 约 40MB/3 分钟）走 SMB 串流时使用默认缓冲，易卡顿；用户无法获知实际输出格式。
4. **无 HiRes 标识**：设计稿定义了金色 HiRes 标签（`Theme.kt:55` HiResGold #FFD700），但列表与播放页均无展示，`SongEntity` 已存有 `sampleRate`/`bitsPerSample`/`bitrate` 却无处可见。

## [S2] Design

### S2.1 HiRes 判定统一修正

- 新增共享判定函数（建议 `data/local/metadata/HiRes.kt`），采用 JAS 近似定义：

  | 条件 | 判定 |
  |------|------|
  | 采样率 > 48kHz | HiRes |
  | 位深 ≥ 24bit | HiRes |
  | 扩展名 ∈ {dsf, dff} | HiRes（DSD） |
  | 其余（含 44.1/48kHz 16bit FLAC） | 非 HiRes |

- 本地扫描（`LocalMusicScanner`）与 SMB 扫描（`SmbFileScanner`）统一调用该函数，消除两套逻辑。
- `SongEntity` 已有 `isHiRes`/`sampleRate`/`bitsPerSample` 列，**无需数据库迁移**；修正判定后通过重扫或启动时批量刷新存量歌曲的 `isHiRes` 标记。
- 实现前需核实 `SmbFileScanner` 是否已填充 `sampleRate`/`bitsPerSample`（若未填充则补上，否则 SMB 歌曲的 HiRes 判定只能退化到按扩展名）。

### S2.2 HiRes 标识与规格展示

- **列表项**：`isHiRes == true` 的歌曲在标题旁显示金色「Hi-Res」徽标；DSD（dsf/dff）显示「DSD」。
- **播放页**：封面下方信息区展示采样率/位深/码率，例如「192kHz/24bit · FLAC」，HiRes 歌曲同时显示金色徽标。
- 数据全部来自 `SongEntity`（`sampleRate`/`bitsPerSample`/`bitrate`/`mimeType`），不新增字段。
- 复用 `Theme.kt` 已定义的 `HiResGold` 颜色，不引入新主题资源。

### S2.3 FFmpeg 扩展（全格式解码）

> **事实修正（审计核实）**：`androidx.media3:media3-exoplayer-ffmpeg` **从未发布到 Google Maven**（dl.google.com 该路径返回 404，与 `gradle/libs.versions.toml:56` 注释一致，juyumao-player.md S2.7 亦写明需编译 FFmpeg so 库）。原"直接引入 Maven 依赖"方案不成立，改为以下路径：

- **主路径（推荐）**：自编译 FFmpeg 扩展——从 media3 仓库 1.5.1 分支 `libraries/decoder_ffmpeg` 按官方脚本构建 AAR，经 `mavenLocal()` 或本地仓库引入；或将该解码器源码以 Gradle 子模块纳入工程。落地前先验证构建脚本可用性（NDK 版本、各 ABI 产物）。
- **备选路径**：若自编译不可行，放弃 DSD/APE/WavPack 播放，改为明确标注「格式不支持」（沿用 S2.5 兜底），并同步修正 README 的格式支持声明。
- `PlayerModule.kt` 中 ExoPlayer 改用 `DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON_DEMAND)`：
  - FLAC/WAV/MP3/AAC/OGG/OPUS 继续走 Media3 原生解码器（FLAC 保持 24bit 输出，不受 FFmpeg 影响）；
  - DSD/APE/WavPack/WMA/AIFF 由 FFmpeg 解码补齐。
- **已知限制（记录进实现，不阻塞）**：FFmpeg 的 DSD→PCM 转换输出位深可能为 16bit；DSD 原生 DoP 直通不在本轮范围。
- proguard-rules.pro 补充 media3 ffmpeg 相关 keep 规则；release 构建验证（minify 开启）。
- 评估并记录 APK 体积增量（FFmpeg 多 ABI so 库）。

### S2.4 HiRes 输出与缓冲适配

- **输出路径**：不强制独占输出/采样率锁定（避免部分设备无声）；依赖 ExoPlayer 默认 AudioTrack 输出在设备支持时保留原生采样率与位深，不做主动降采样。实现时验证 24/192 FLAC 的输出采样率/位深（见 T4 验证手段）。
- **缓冲适配**：激活已存在的 `AUDIO_BUFFER_SIZE` 设置（`SettingsDataStore.kt:18`，默认 256KB，当前从未传给 ExoPlayer）；对 `isHiRes` 歌曲使用更大的预缓冲（通过 `LoadControl` 或 `DefaultLoadControl.Builder` 分档配置），缓解大文件 SMB 串流卡顿。设置文案改为「音频缓冲大小」，HiRes 档位在设置页说明。
- **输出信息**：设置页「音频」区新增当前输出设备与格式展示（经 `AudioManager`/`AudioTrack` 查询），让用户确认 HiRes 是否生效。

### S2.5 解码失败兜底

- 沿用 juyumao-player spec S2.8 既有错误处理：FFmpeg 也解不了的格式 → 提示「格式不支持」+ 自动跳过，不崩溃（`PlaybackController.playCurrent()` 播放异常路径已具备跳过行为，回归验证即可）。

## [S3] Out of Scope

- DSD DoP 直通 / USB DAC 独占输出
- 升频（up-sampling）与重采样质量调优
- 在线流媒体 HiRes（仅本地/NAS 源）
- 外接声卡/耳机硬件调校
- 蓝牙 LE Audio / LDAC 相关（使用系统默认）

## Tasks

- [x] T1: 统一 HiRes 判定函数并接入本地/SMB 扫描 — acceptance: 44.1kHz/16bit FLAC 不再标记 HiRes；24bit/96kHz 与 DSF/DFF 正确标记；存量歌曲标记可刷新 (covers: S2.1) — 已落地（HiRes.kt）；SmbFileScanner 补全见 Report 遗留
- [x] T2: HiRes 徽标与音频规格展示（列表项 + 播放页） — acceptance: 列表与播放页按判定显示金色 Hi-Res/DSD 徽标与「采样率/位深/码率」文本 (covers: S2.2) — 已落地（AudioSpecRow）
- [ ] T3: FFmpeg 扩展自编译并配置 RenderersFactory — acceptance: 自编译 AAR 可构建入工程（NDK/ABI 产物验证）；DSD/APE/WavPack 可播放；FLAC 仍走原生解码（日志确认 codec 非 ffmpeg）；release+minify 构建通过、proguard 不裁剪；APK 体积增量有记录 (covers: S2.3)
- [ ] T4: 缓冲设置生效 + HiRes 分档缓冲 + 输出信息展示 — acceptance: 通过 logcat 或调试 UI 显示 bufferMs/LoadControl 参数，断言修改缓冲设置后行为变化；HiRes 歌曲缓冲参数大于普通歌曲；设置页展示当前输出设备/格式（含设备不支持 HiRes 输出时回退 48kHz 说明） (covers: S2.4)
- [ ] T5: 解码失败兜底回归 — acceptance: 无法解码的文件提示并跳过，不崩溃 (covers: S2.5)
