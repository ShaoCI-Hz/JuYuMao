# 局域猫 · UI 与动画多 AGENT 评审报告

> 三个独立子代理并行评审：UI 视觉层 / 动画与交互层 / UX 与信息架构
> 日期：2026-08-16

## 总体结论

架构质量高：主题全走 MiuixTheme.colorScheme、高频数据已下沉组件订阅、组件复用得当。**无系统性崩溃/数据风险**（P1 级问题集中在个别崩溃点与导航缺陷）。改进空间集中在：**播放页深色前景适配、转场/切歌过渡、迷你播放条、导航压栈、底部留白与空态行动化**。

---

## 一、P1（优先修复：崩溃 / 可用性）

| 问题 | 位置 | 说明 |
|---|---|---|
| 曲库维度列表 key 冲突崩溃 | BrowseScreen.kt:776 | `items(names, key={it})` 同名专辑/艺术家（同名专辑不同艺术家很常见）→ LazyColumn key 重复直接抛异常。改 `key = { "$it#$index" }` |
| 播放页无限压栈 | NavGraph.kt:64-66/132/138 | 连点两首歌堆叠两个 player entry，返回退回"上一首歌播放页"。改 `popUpTo(Player)+launchSingleTop` 替换式进入 |
| 搜索页无返回按钮 | SearchScreen.kt:32 | push 进入后顶栏无返回箭头，只能系统手势 |
| 曲库"歌单功能即将上线"死文案 | BrowseScreen.kt:191-195 | 歌单已上线，文案误导 + 占 120dp 空白；曲库无歌单入口 |
| 无迷你播放条 | FloatingNavBar.kt | 底栏无当前曲目/播放状态，任意页看不到"正在播什么"（与主流播放器最大差距） |

## 二、P2（明显体验缺陷）

**视觉/一致性**
- PlayerSlider.kt:42-44 用 onSurface：浅色主题下进度条黑不可见（播放页背景恒定深色）
- 底部留白混乱：无底栏页（Playlist/Search/SmartPlaylist）留 120~160dp；Browse 与 Scaffold padding 重复 120dp
- 封面圆角 6/8/12/16 分裂（squircle vs RoundedCornerShape）；区块标题配色分裂（primary vs onBackground）
- HomeScreen 推荐卡高 = 屏高/5 硬算；Browse 入口页 Column 无滚动（低分辨率/大字体溢出）
- SmbConnect/Onboarding 手搓状态栏 48dp（挖孔屏偏移）

**状态与反馈**
- 曲库 listFilter/多选 selectedIds 用 remember（非 rememberSaveable）：切 tab 返回后全部丢失
- 播放 tab 无歌时零反馈；首页"本地音乐"卡扫描后无跳转（断头入口）
- NAS 空态无"去连接"按钮；曲库无加载态（直显"暂无歌曲"）
- shuffle/repeat 内存态不持久化：退出重进图标与真实状态脱节
- A-B 循环三按钮常驻挤占播放页空间；歌单详情行不可点播；搜索结果行仅有收藏
- 多选操作栏无"播放"动作；"播放全部"仅维度列表有

**动画**
- FlowingLightEffect 全屏光斑不随播放停止 + PlayerBackground 全屏 blur：播放页生命周期持续满帧渲染（帧率最高风险）
- 频谱重进不重启 bug：`alreadyPlayingThis` 跳过 spectrum.start → 频谱永久不恢复
- 切歌淡入 bug：coverAlpha targetValue 恒 1，artworkUri 变化不重置 → 淡入从未生效
- 水平滑动切歌无实时跟随（无 translationX、无回弹）

## 三、P3（打磨/一致性）

- 动画 token 散落（6 种 spring stiffness、12 种 tween 时长、两套按压曲线）；建议 Animations.kt 集中
- 分隔线 0.5dp+alpha 重复 5 处；行 padding/间距三值混用；图标语义过载（Tune 5 义）
- 双击暂停与歌词行单击竞争；歌词页滚动触发 pager 误切；pager 手动切页与"歌词"按钮状态不同步
- 频谱 30fps GC（数组复用+降采样）；歌词逐字 AnnotatedString 每 200ms 重建
- 共享元素转场缺失（列表封面 → 播放器封面）；PulsingGlow 暂停恢复闪变
- 设置页无"高级"分组、滑杆无重置；播放模式 UI 与控制器状态脱节

---

## 四、推荐落地顺序（三 Agent 共识）

1. **修复类**（低风险高收益）：曲库 key 崩溃、播放页压栈、搜索返回、滑块浅色主题、频谱重进、切歌淡入 bug、死文案删除
2. **播放页质感**：切歌过渡体系（封面 crossfade + 背景色联动 + 信息 slide）、水平滑切实时跟随、动画 token 统一
3. **迷你播放条 + 底栏复合**（P1 体验差距单点）
4. **统一列表操作能力**：全列表"点行播放 + 更多菜单"、播放全部、多选加播放、空态→行动态组件
5. **状态持久化**：shuffle/repeat、曲库 listFilter/多选 rememberSaveable
6. **可选高级**：SharedTransitionLayout 共享元素转场、FlowingLightEffect 播放门控 + 频谱降采样（性能治理）
