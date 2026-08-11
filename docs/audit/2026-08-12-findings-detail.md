# JuYuMao 审计明细 — MEDIUM / LOW（2026-08-12）

> 与主报告 `2026-08-12-64-agent-audit.md` 配套。行号为审计时的 `main` 分支代码行号。
> 格式：`文件:行号` [M|L] 问题 — 修复要点

## 一、MEDIUM 明细（按模块）

### 1. 入口 / 根 UI / 导航 / 主题
- `ui/AppViewModel.kt:87` [M] autoReconnectSavedServers 不读 `SettingsRepository.smbAutoConnect`，关闭设置仍强制重连 — 先取设置，false 直接返回
- `ui/AppViewModel.kt:93,140` [M] serverList 为空时误报"自动连接失败"打扰新用户 — isEmpty 静默结束
- `ui/AppViewModel.kt:159` [M] togglePlay 绕过 PlaybackController 直接 exoPlayer.play/pause，交叉淡化失效且虚报播放中 — 委托 playbackController.togglePlay()
- `ui/theme/Theme.kt:53` [M] ThemeController 每次重组新建实例未 remember — `remember(colorSchemeMode) { ThemeController(...) }`
- `ui/navigation/NavGraph.kt:53,75` [M] `navigate("smb_connect?guide={guide}")` 字面模板当目标导航（deep-link 语义错误）— 用 `Screen.SmbConnect.createRoute()`
- `ui/navigation/NavGraph.kt:52,64,70,125` [M] Player 路由无 launchSingleTop，连续点歌创建多个并行 ViewModel，加载结果互相覆盖 — 加 launchSingleTop=true
- `ui/navigation/Screen.kt:23-25` [M] route 模板与 createRoute 双写法、默认值三处重复维护 — 统一 createRoute 生成目标
- `ui/components/PremiumBottomNavBar.kt:35-39` [M] edge-to-edge 下底部胶囊条被手势条遮挡 — navigationBarsPadding()

### 2. 数据层
- `data/local/db/dao/SongDao.kt:22-23` [M] getRecentlyPlayed 未过滤 lastPlayedAt>0，未播放歌曲混入 — WHERE lastPlayedAt > 0
- `data/local/db/dao/SongDao.kt:114-115` [M] getTodayPlayCount 实为"今日有播放记录的歌数"非播放次数 — 改名或改按事件表聚合
- `data/local/db/dao/ServerDao.kt:18-19` [M] insert 按主键 upsert，id=0 重复服务器可重复入库 — (ip,port) 唯一索引
- `data/local/db/dao/ServerDao.kt:18-22` [M] insert/update 明文密码写 SQLite — 加密存储
- `data/local/db/entity/ServerEntity.kt:28` [M] effectiveShareName 只按 "/" 分割，smb:// 前缀/反斜杠处理错误 — 先剥前缀再统一切分
- `data/local/db/entity/SongEntity.kt:24` [M] smbServerId 无外键无索引，删服务器后歌曲悬空 — Index + ForeignKey CASCADE
- `data/local/db/entity/SongEntity.kt:40` [M] isFavorite 无索引，收藏查询全表扫描 — 建索引
- `data/local/db/JuYuMaoDatabase.kt:20,44` [M] exportSchema=false 无法跑迁移测试；CREATE UNIQUE INDEX 遇同名非唯一索引静默跳过 — exportSchema=true + DROP 后重建
- `data/local/datastore/SettingsDataStore.kt:29-40` [M] 13 个 Flow 无 .catch，Preferences 损坏时上游流终止打穿 UI — 统一 .catch 回退 emptyPreferences
- `data/local/crypto/CryptoHelper.kt:78-81` [M] 解密失败静默返回密文本身，密钥失效后密码不可恢复且无感知 — 加 enc:v1: 前缀，失败抛受控异常
- `data/local/artwork/ArtworkCache.kt:31,50,46` [M] tmp 名固定并发覆盖；全尺寸解码 OOM；覆写后内存缓存旧图不失效 — UUID 后缀 / inSampleSize / remove 内存 key
- `data/local/cache/CacheManager.kt:146-156` [M] clearDir/dirSize 跟随符号链接，symlink 可递归删外部目录/成环崩溃 — walkFileTree 不跟随链接
- `data/local/cache/CacheManager.kt:97-106,112-116` [M] 直接写目标文件非原子，半截残留；delete 失败静默且记账错 — 临时文件+rename；失败回滚计数
- `data/local/lyrics/LyricsManager.kt:168,34-39,63-77,156-160` [M] 纯文本歌词 timeMs=0 恒高亮末行；LOCAL 分支无 try-catch；SMB 下载 8MB 仅提标签；tempFile 同毫秒覆盖+流不关闭；空 LRC 降级把元数据当歌词 — 详见各修复
- `data/local/metadata/MetadataExtractor.kt:110,66,133,51,41-43,68` [M] embeddedPicture 二次解析；channels 硬编码 0；USLT 用 toString() 取歌词（污染）；TRACKTOTAL 缺失；tag==null 双重回退重复建 retriever；MP3 8MB 截断时长/尾部标签 — 详见各修复
- `data/local/metadata/MetadataBatchProcessor.kt:174,52,63,90` [M] 中途异常仍报 100%；私有 scope 无取消 API；isRunning 检查与设置非原子可双启动；按服务器串行 — 详见各修复
- `data/local/scanner/LocalMusicScanner.kt:88,51,60-64,60,133-134,200-208` [M] IS_MUSIC==1 硬过滤漏真实音乐；DATA 列弃用；IS_PENDING 未过滤；无权限检查行为不一致；封面 key 用 hashCode 碰撞；checkExternalLrc 每首歌 listFiles O(N²) — 详见各修复
- `data/remote/smb/AudioFileFilter.kt:34,40,46,63` [M] lowercase() 用系统 locale，土耳其语 "I"→"ı" 漏扫 AIFF — lowercase(Locale.ROOT)
- `data/repository/MetadataRepository.kt:42-46,118-120,37-51,99-105,186-199` [M] 吞 CancellationException；失败静默返回且 isMetadataCached 只查 albumArtUri 致永久跳过；ensureArtwork 与批量缓存并发写封面；albumArtist/genre/composer 无 ?: 保护会清空旧值；output 流不在 finally 关闭 — 详见各修复
- `data/repository/LyricsRepository.kt:13-50,41,54` [M] 整个类死代码且与 LyricsManager 行为分叉；take(maxOf(5,len)) 截取失效；GBK 乱码 — 删除或薄代理
- `data/remote/smb/JcifsShareEnumerator.kt:62-65,40,53-55` [M] 认证失败/无共享统一吞成 emptyList 无法区分；BaseContext 从不 close；listFiles null 直接 NPE — 区分错误/关闭 context/判空
- `data/remote/smb/SmbFileScanner.kt:59-65,27,59,67-77` [M] 根目录失败返回 success(空) 无法区分；无总超时；listFiles 与播放共用 ioMutex 串行；不跳过 NAS 系统目录(@eaDir 等) — 详见各修复
- `data/remote/smb/ShareDiscovery.kt:56-59,78-85,60-90,72-89` [M] 枚举非空即 return 跳过暴力搜索不合并；无总超时最坏 5 分钟；吞 CancellationException；连接/会话泄漏 — 详见各修复
- `data/remote/smb/SmbClient.kt:44-47,162,128` [M] disconnect 无同步与 connect 竞态；isConnected 只查 TCP 假活；路径拼接未滤 .. — 详见各修复
- `data/remote/discovery/NetworkMonitor.kt:26-28,30-32` [M] onLost 无条件 DISCONNECTED 误报离线；多网共存状态抖动 — 统一按 activeNetwork 判定
- `data/remote/discovery/SmbDiscovery.kt:78-81,26-41` [M] 吞 CancellationException；getLocalIpAddress 可能绑蜂窝/VPN 接口 mDNS 失败 — rethrow / 优先 WiFi 接口

### 3. 播放层
- `player/PlaybackController.kt:128-150,80-81,117-121,244-268,169-179,234-242,63-66` [M] pause 淡出后音量不回恢复；gapless/crossfade 仅 init 读一次；无缝队列不调 updateSettings HiRes 缓冲失效；SMB 失败降级用无 scheme 路径必失败；previous 不传 shuffle；无缝队列全部复用全局 artworkUri；错误提示一律"无法播放该格式" — 详见各修复
- `player/PlaybackQueue.kt:97,35-76,22-28` [M] previous(ONE) 与 next(ONE) 不对称；三次独立快照读非原子可 IndexOutOfBounds；setQueue 不校验 startIndex/空队列不重置 shuffleOrder — 详见各修复
- `player/PlaybackStateHolder.kt:49-68,125-128` [M] bindPlayer 从不移除旧 listener 堆积；release 后 exoPlayer 不置 null，外部调用抛 IllegalStateException — 保存引用/置 null
- `player/MusicPlayerService.kt:126-132,92-94` [M] onTaskRemoved 缺 !playWhenReady 条件；通知被划掉后 stopSelf 但播放继续且无控制入口 — 暂停播放/同步 release
- `player/audio/AudioEffectsManager.kt:60-67,69-80,88-93,121-126` [M] Equalizer 构造失败连带低音/响度全丢；只监听一次 STATE_READY session 变化不重绑；setBandLevel 不重置 currentPreset；Virtualizer 默认 strength 0 无效果 — 详见各修复
- `player/audio/SpectrumAnalyzer.kt:89,96,23-63,37-42,71-77` [M] FFT 频点错位（Nyquist 当 DC 虚部）；8-bit FFT 幅值/2000 使频谱柱近乎恒矮；active 无 volatile；start 无同步可泄漏 Visualizer；单例被播放页/均衡器页互踩 stop — 详见各修复
- `player/audio/LrcParser.kt:59,29-72` [M] 正文含 ] 被截断；负时间戳/≥100 分钟不解析 — substring 到最后一个 ] / 正则放宽

### 4. UI / ViewModel
- `ui/home/HomeScreen.kt:72-75,56-58,149` [M] 统计卡片与 spec 不符且 totalSize 未展示；权限拒绝无反馈；缓存管理卡片 onClick 空 — 详见各修复
- `ui/home/HomeViewModel.kt:68-74,99-125,104-124` [M] NAS 轮询无 try-catch 异常后冻结；跨天不刷新日期/问候/天气；天气直连 wttr.in 绕过 Repository 违反分层 — 详见各修复
- `ui/browse/BrowseScreen.kt:134-137,195-197` [M] 维度切换竞态（旧列表短暂显示/后到覆盖）；无封面 SMB 项滚动并发提取形成重试风暴 — Job 取消 + 失败去重 + 限流
- `ui/browse/BrowseViewModel.kt:48-57,45-57,69-92,95-99` [M] loadDimensionSongs 不取消旧任务；.first() 快照不反映 DB 变化；ensureArtwork 无全局并发上限；toggleFavorite 快照取反丢事件 — 详见各修复
- `ui/cache/CacheScreen.kt:119,71-76` [M] 组合期主线程 file.length() stat；isClearing/isDownloading 从未用、按钮不防重入 — VM 预算大小/禁用按钮
- `ui/cache/CacheViewModel.kt:67-70,115-121,126-142` [M] getById null 时 isDownloading 卡死；删除/清除无 try-catch 虚假成功；isClearing 异常时永久 true — 详见各修复
- `ui/equalizer/EqualizerScreen.kt:92-95,151-153` [M] equalizer null 时 setEnabled 仍置 true 误导；拖动中被外部更新覆盖滑块 — 判空拒绝/拖拽标记
- `ui/equalizer/EqualizerViewModel.kt:31-37,70-73,24-25` [M] 频谱绑定只查一次 player 未就绪即 return；onCleared 无条件 stop 互踩播放页；均衡参数无持久化重启全丢 — 详见各修复
- `ui/lyrics/LyricsView.kt:48,64-71` [M] 切歌后 LazyListState 无 key 停旧位置、currentIndex=-1 不滚回顶部 — LaunchedEffect(lyricsData) scrollToItem(0)
- `ui/onboarding/OnboardingScreen.kt:125,142-149,137-157,58-61` [M] 跳过按钮双 ViewModel 双写；alpha 动画死代码；delayMs 未使用交错渐入失效；永久拒绝无提示 — 详见各修复
- `ui/onboarding/OnboardingViewModel.kt:22-23` [M] onboardingCompleted 从未被收集是死状态，且初始 false 造成冷启动引导页闪烁 — 收敛到 AppViewModel
- `ui/player/PlayerScreen.kt:30-40,111-117,47,45,43-44` [M] 频谱/位置顶层 collect 重组爆炸；倍速 UI 与持久化脱钩；手势翻页与按钮不同步；shuffle/repeat 双状态源 — 详见各修复
- `ui/player/PlayerViewModel.kt:83,123-136,172-179,186-188` [M] 加载失败不清空旧状态；频谱轮询协程不随 loadJob 取消且 enabled 读初值；toggleFavorite 竞态；onCleared stop 互踩均衡器 — 详见各修复
- `ui/player/components/CoverLyricsPager.kt:46-50` [M] coverAlpha target 恒 1f 死代码，切歌封面淡入从未生效 — key(artworkUri) 重启动画
- `ui/player/components/PlayerBackground.kt:39-69` [M] 慢解码任务晚完成覆盖 dominantColor 背景错位 — isActive/最新 uri 校验
- `ui/player/components/PlayerSlider.kt:27-34` [M] seek 后滑块回跳再跳闪烁 — isDragging 保持到 position 收敛
- `ui/player/components/PlayerTopBar.kt:42-46` [M] 超长歌名换行裁切挤压按钮 — maxLines=1 + Ellipsis
- `ui/player/components/SpectrumBars.kt:35-36` [M] 48 柱固定 4dp+3dp 总宽 333dp 超小屏被裁 — 自适应缩放
- `ui/playlist/PlaylistScreen.kt:47` [M] currentPlaylist!! 强解包 NPE 风险 — 局部非空快照
- `ui/playlist/PlaylistViewModel.kt:73,42,58` [M] isSongInPlaylist 预检 TOCTOU 仍可重复插入崩溃；rename 不刷新快照；openPlaylist 两步协程交错出现 B 标题+A 歌曲 — 详见各修复
- `ui/queue/QueueScreen.kt:76,77,81` [M] key={it.id} 重复 id 崩溃；indexOf 全字段匹配高亮错位；playAt 传快照索引播错歌 — 详见各修复
- `ui/search/SearchViewModel.kt:23-26` [M] 搜索流无异常兜底，一次 DB 异常永久失效 — flatMapLatest 内 .catch
- `ui/search/SearchScreen.kt:75` [M] 防抖等待期显示"未找到"误导 — isSearching 状态
- `ui/settings/SettingsScreen.kt:30` [M] hiltViewModel() 取独立 AppViewModel 实例，进入设置页重复执行 20s 重连副作用 — 共享 activity 级 owner
- `ui/settings/SettingsScreen.kt:452-465` [M] AudioOutputInfo 用 getDevices.firstOrNull 非当前路由设备；采样率是硬件能力非实际输出 — activePlaybackConfigurations
- `ui/settings/SettingsViewModel.kt:43-73,20` [M] setter 无异常捕获可崩溃；stateIn initialValue 与 DataStore 默认值重复硬编码致首帧闪烁 — 详见各修复
- `ui/sleep/SleepTimerViewModel.kt:29-33,19-22` [M] 熄屏 Doze 抑制 delay 定时失效；计时仅存内存进程被杀静默消失 — AlarmManager / 持久化时间戳
- `ui/smb/SmbConnectScreen.kt:329-357,340-357,210-215,362-394,32-39` [M] 连接/搜索共享双入口并发；扫描中按钮不禁用；共享列表点击用表单新旧混合状态；错误卡片语义混杂；表单全 remember 旋转丢 — 详见各修复
- `ui/smb/SmbConnectViewModel.kt:230,126-131,68-71,148,256-258,248-254` [M] 吞 CancellationException；connect 不重置 isConnected 旧成功残留；checkExistingConnection 空实现；savedServers 未加载完就 connect 重复入库；无 domain 字段域认证必失败；connectToScanned 硬编码 sharePath="" 必失败 — 详见各修复
- `ui/statistics/StatisticsViewModel.kt:59-60,62,38-67` [M] 周/月统计实为去重歌数非播放次数；weekPlayDurationMs 死字段计全曲时长；refresh 无互斥旧数据覆盖新数据；无 try-catch 转圈 — 详见各修复
- `ui/widget/JuYuMaoWidget.kt:90-105,91-105` [M] 播放按钮纯装饰无 clickable；点击冒泡到父 Box 打开 App 误导 — 详见各修复
- `ui/AppViewModel.kt`（交叉）[M] 中文日志 GBK 乱码 — UTF-8 重存

### 5. 构建 / 发布 / 资源
- `app/build.gradle.kts:119-121` [M] smbj exclude bouncycastle 但依赖图实际是 jcifs-ng 传递的 jdk15on:1.69，与报告 jdk18on 1.75 不符 — 显式 implementation bcprov-jdk18on:1.75
- `app/proguard-rules.pro:1-36` [M] jcifs-ng 无 keep 规则（PropertyConfiguration java.beans 反射 setter），release 共享枚举可能静默失败 — -keep class jcifs.**
- `app/src/main/AndroidManifest.xml:18` [M] allowBackup=true 无排除规则，DB（含 SMB 服务器）与缓存被备份而 Keystore 密钥不随迁 — dataExtractionRules 排除
- `release.ps1:71-72,125-129,101-104` [M] commit/push 失败被 Out-Null 吞掉仍建 Release；APK 上传失败仍打印"发布完成"；临时 py 脚本残留可被 git add -A 提交 — 检查 LASTEXITCODE / 校验 URL / 放 TEMP
- `.git/config:12`（交叉）[M] remote URL 内嵌明文 GitHub PAT 多处暴露 — 移除 token 改用凭据管理
- `README.md:28`（交叉）[M] 宣称支持 DSD/APE/WavPack 与实际（FFmpeg 未引入）不符 — 修正声明
- `ui/widget/JuYuMaoWidget.kt`（交叉）[M] 静态占位与 README/spec 宣称的"封面+标题+控制"严重不符 — 接状态源
- `ui/theme/Theme.kt:71-77`（交叉）[M] miuix-ui.md 称 MaterialTheme.colorScheme 作 token 访问器，但 JuYuMaoTheme 未包 M3 兼容层，多处直用 M3 typography — 核实/显式包装
- `ui/AppViewModel.kt:105-149`（交叉）[M] Log 中文乱码（同 4 组）

## 二、LOW 概要（~350 条，按共性主题归类）

| 主题 | 涉及范围 | 说明 |
|------|----------|------|
| deprecated 图标 | BrowseScreen / PlaylistScreen / StatisticsScreen | `Icons.Default.ArrowBack/KeyboardArrowRight/QueueMusic` → AutoMirrored |
| `!!` 滥用 | SmbClient / CacheViewModel / PlayerScreen / PlaylistScreen | 均位于前文判空后，脆而不崩 |
| remember vs rememberSaveable | BrowseScreen / PlayerScreen / SmbConnectScreen / SettingsScreen / JuYuMaoApp | 旋转/重建丢状态 |
| Locale 问题 | FormatUtils / PlayerSlider / SleepTimerSheet / AudioFileFilter | String.format 未指定 Locale、lowercase 系统 locale |
| 死代码/未使用 | domain 4 模型、LyricsRepository、SmbRepository 多数方法、SongDao 2 方法、AlbumArtPager 旋转分支、RotatingAlbumArt、Type/Shape/Color token、FlowPreview 等 | 见结构性发现 |
| 无障碍 | AnimatedIconButton / PremiumBottomNavBar / ControlRows / PlayerTopBar | 缺 role/semantics、contentDescription 重复、点击目标 <48dp |
| 性能微优化 | 各 UI 文件 remember 缺失、ImageRequest 重建、每帧对象分配、LazyColumn key | 低危 |
| 异常静默吞掉 | 多处 catch(_:Exception){} 无日志 | 缺可观测性 |
| 文档滞后 | spec 勾选滞后（T5/T8/T9/T10 已落地未勾）、交接报告待办未勾 | 低危 |

## 三、审计覆盖清单

- 106 个 Kotlin 源文件全部覆盖（audit-01..61 + 交叉）
- 构建配置 16 文件（audit-62）：app/build.gradle.kts、根 build.gradle.kts、settings.gradle.kts、gradle.properties、libs.versions.toml、gradle-wrapper.properties、proguard-rules.pro、AndroidManifest.xml、res 全部
- 发布脚本（audit-63）：release.ps1 + .git/config
- 全局交叉（audit-64）：契约、死代码、deprecated、日志、Manifest 权限、spec 一致性
