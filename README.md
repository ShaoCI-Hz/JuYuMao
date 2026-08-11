# 局域猫播放器 (JuYuMao Player)

> **整体重构声明**：本项目由 Cat Player 全面重构而来。自 v4.0.0 起，项目正式更名为 **局域猫 (JuYuMao)**，代码库完全重写，以局域网 SMB 音乐播放为核心。

Android 局域网 SMB 音乐播放器，直连 NAS 串流播放，同时支持本地音乐扫描。

![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-purple)
![Compose](https://img.shields.io/badge/Compose-BOM-2026.06-blueviolet)
![Miuix](https://img.shields.io/badge/Miuix-0.9.3-ff6900)
![API](https://img.shields.io/badge/API-29%2B-brightgreen)
![License](https://img.shields.io/badge/License-MIT-blue)

## 功能

### NAS 连接
- SMB 直连播放，无需挂载系统共享
- 局域网自动扫描（网段 445 端口探测）
- mDNS 自动发现
- 共享名自动枚举（jcifs-ng + 常见共享名搜索）
- 自定义端口、域/工作组支持
- 匿名/访客连接
- 自动重连（启动时自动连接已保存服务器）
- 连接池管理（30 分钟空闲回收）

### 音乐播放
- 本地音乐扫描（MediaStore + 元数据增强提取）
- NAS 音乐流式播放（smbj SMB2/3）
- MP3/AAC/FLAC(含24bit)/WAV/OGG/OPUS（DSD/APE/WavPack 因未集成 FFmpeg 解码器暂不可播）
- 封面/歌词自动提取缓存
- 播放队列、随机、循环模式
- 均衡器
- 定时关闭

### 元数据
- 双层提取：jaudiotagger（完整标签 + 内嵌歌词）→ MediaMetadataRetriever（回退）
- 封面缓存（磁盘 + 内存 LRU）
- 歌词：内嵌 (USLT/LYRICS) + 外挂 .lrc
- 批量元数据缓存（多线程并行，可配置线程数）

### 界面
- MIUI 设计语言（Miuix 0.9.3）主题 + Monet 动态取色
- 深色/浅色/跟随系统三模式
- 首页仪表盘（统计、每日一句、天气）
- 浏览页（全部/本地/NAS 筛选）
- Salt Player 风格播放页（流光背景、封面取色、上下滑歌词）
- 实时搜索
- 通知栏媒体控制（封面/标题/切歌）

### 系统
- MediaSession + 前台服务
- 桌面 Widget
- 缓存管理（NAS 下载、封面、歌词，可自定义清除）

## 技术栈

| 模块 | 技术 |
|------|------|
| 语言 | Kotlin 2.4.0 |
| UI | Jetpack Compose + Material3 + Miuix 0.9.3 |
| 音频 | Media3 ExoPlayer 1.5.1 |
| SMB | smbj 0.13.0（播放）+ jcifs-ng 2.1.9（共享枚举） |
| DI | Hilt 2.60.1 + KSP 2.3.11 |
| 存储 | Room 2.8.4 + DataStore 1.1.1 |
| 图片 | Coil 2.7.0 |
| 元数据 | jaudiotagger 3.0.1 |
| Widget | Glance 1.1.1 |
| 构建 | AGP 9.2.1 + Gradle 9.4.1 |

## 编译

环境要求：
- JDK 17
- Android SDK 37（compileSdk 37）
- Gradle 9.4.1（已包含 wrapper）

```bash
git clone https://github.com/ShaoCI-Hz/JuYuMao.git
cd JuYuMao
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`
调试包名：`com.hezi.juyumao.debug`

前往 [Releases](https://github.com/ShaoCI-Hz/JuYuMao/releases) 下载最新 APK。

## 更新日志

### v4.0.0 (2026-08-01) — 整体重构为局域猫
Cat Player 全面重构，代码库完全重写，更名「局域猫 (JuYuMao)」。

**核心重构**
- 全新代码库：包名 com.hezi.juyumao，Kotlin 2.1.0 + Compose BOM 2024.12
- 播放引擎重写：PlaybackController + PlaybackStateHolder（内部协程轮询进度）
- SMB 模块重做：smbj 流式播放 + jcifs-ng 共享枚举双库方案
- 元数据系统重构：双层提取 + 封面缓存 + 批量多线程缓存

**NAS 连接**
- 局域网网段扫描（445 端口探测）
- 共享名自动枚举
- 自定义端口/域/匿名连接
- 自动重连 + 重试机制
- 连接池（30 分钟空闲回收）

**播放体验**
- Salt Player 风格播放页：流光背景、封面取色、沉浸模式
- 上下滑切换封面/歌词（VerticalPager）
- 进度条实时更新（200ms 轮询）
- 歌词自动跟随进度滚动

**元数据与缓存**
- 内嵌歌词提取（MP3 USLT / FLAC LYRICS）
- 封面自动提取缓存（磁盘 + 内存 LRU）
- 批量元数据缓存（多线程，设置中可调线程数 1-8）
- 缓存管理页（查看/清除封面、歌词、NAS 下载）

**修复与优化**
- 密码加密存储（Android Keystore AES-GCM）
- SMB 播放元数据完整传递（标题/封面到通知栏）
- 进入播放页不再重播同一首歌
- 播放/歌词/封面实时同步

## 许可证

MIT License
