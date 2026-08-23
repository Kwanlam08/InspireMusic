# Inspire Music 交接文档

> 最后更新：2026-08-15
> 注意：本文档基于**实际 git 状态**编写。

---

## 1. 当前 git 状态（最重要，先看这个）

```
git status: 工作区有 39 个文件未提交（29 个改动/删除 + 10 个未跟踪）
git stash list: stash@{0} = "wip-glass-refactor"（冗余备份）
                stash@{1} = "backup-before-glass-refactor"（冗余备份）
```

### 结论（2026-08-15 会话成果，全部在工作区、未提交）
- **依赖升级已生效**：compose-bom 2024.12.01、core-ktx 1.15.0、lifecycle 2.8.7、activity-compose 1.9.3、navigation-compose 2.8.5、media3 1.5.1、coil 2.7.0、coroutines 1.9.0、Room 2.7.0、Haze 1.6.0（版本号 versionCode 80 / versionName 3.10.1，勿降）。
- **Bug 修复已生效**（从旧 stash 合并回来）：Room 迁移闪退、歌词重复加载、播放队列崩溃、ripple 删除、底栏拖拽优化。
- **真玻璃返回按钮方案已完整落地**，并修复了日记页/设置页的返回逻辑（见第 3、4 节）。
- 两个 stash 都是历史备份，已验证冗余，**确认手机上没问题后可以 `git stash drop`**。

### 工作区未提交文件清单
| 文件 | 改动 |
|------|------|
| `app/build.gradle.kts` | versionCode 80 / versionName 3.10.1 + 新依赖（见上） |
| `app/src/main/java/com/inspiremusic/ui/AppNavigation.kt` | 全局返回按钮 + 全局「新建/排序」按钮 + 日记/设置内部返回信号（核心文件） |
| `MusicDiaryScreen.kt` | 日志页返回信号收集；日志页头 Spacer 74.dp |
| `SettingsScreen.kt` | 二级页返回信号收集；二级页头 Spacer 64.dp |
| `PlaylistsScreen.kt` | 删「新建」按钮（已移全局层） |
| `SongsScreen.kt` | 删「排序」按钮（已移全局层）+ 排序信号收集 |
| `NowPlayingScreen.kt` | 删「歌词工作室」入口；歌词左右内边距 0.dp（对齐缩略图） |
| `ui/lyrics/LyricsStudioSheet.kt` | **已删除**（功能入口移除） |
| 其余屏幕文件（Albums/Artists/AlbumDetail/ArtistDetail/PlaylistDetail/LibraryOrganizer） | 返回按钮 → Spacer 让位 |
| `AppDatabase.kt`、`MusicViewModel.kt`、`NowPlayingScreen.kt`、`ItunesMetadataClient.kt`、`LiquidGlassInteractions.kt`、`SharedUI.kt`、`BlurBottomNavigation.kt`、`BackdropLiquidGlass.kt`、`MainActivity.kt`、`AudioRepository.kt`、`AppSettings.kt` | Bug 修复 + ripple 删除（从旧 stash 合并） |
| `AppSettings.kt`（本次新增字段） | `defaultNowPlayingTab`(0-2)、`hideStatusBarInLandscape`(默认 true) + 对应 setter/key（**沉浸式封面已按用户要求整个移除**） |
| `SettingsScreen.kt`（本次新增） | 新增 `SettingsPage.NowPlaying` 子页：默认页签、横屏隐藏状态栏、歌词显示 8 项滑块/开关、恢复默认按钮 |
| `NowPlayingScreen.kt`（本次接入） | currentTab 初始取默认页签；横屏状态栏按开关执行（沉浸式封面代码已全部移除，方块 morph 动画恢复原样） |
| `.github/release-notes.md`、workflow、strings.xml | 版本/文案相关 |

---

## 2. 项目目标与约束

- 项目：**Inspire Music**，包名 `com.inspiremusic`（外层目录名是历史遗留 `AppleMusicClone`）。
- 用户是小白：回复要短，少啰嗦，别说看不懂的话。
- 不动现有动效和功能，只优化视觉（例外：歌词工作室入口按用户要求移除）。
- 改动前必须备份（git stash）。
- 底栏不需要额外点按反馈（已有原生效果）。
- 真液态玻璃 = 与底栏同款 `LayerBackdrop` 共享 backdrop，禁止 Haze 嵌套。

---

## 3. 技术关键决策

### 3.1 为什么某些按钮必须放 NavHost 外面（真玻璃的唯一方案）
- Haze/backdrop 禁止嵌套采样：`IllegalArgumentException: Layout nodes using Modifier.haze and Modifier.hazeChild can not be descendants of each other`；共享 LayerBackdrop 被 NavHost 内节点采样会循环 RenderNode、RenderThread 栈溢出崩溃。
- **屏幕内的按钮永远做不了真玻璃**（只能安全静态材质）：NavHost 内 `LocalBackdropLayer = null`。
- **已移到全局层（真玻璃）的按钮**：全局返回按钮、播放清单「新建」（TopEnd）、歌曲页「排序」（TopEnd）。
- **保持屏幕内安全材质的按钮**：Home 搜索框 Clear/Send、设置页缓存删除按钮、播放清单/歌曲页长按菜单等（不值得移）。
- 旧 `glassy = true`（静态 blur）方案被用户否过（"看起来假"），**不要再用**。

### 3.2 全局按钮清单（app/.../ui/AppNavigation.kt，NavHost 外共享 LayerBackdrop）
```kotlin
// 位置和显示条件
librarySearchAction  // TopEnd：Library 主页=搜索图标；"library/search"=返回图标
currentRoute == "playlists" → TopEnd 新建（+ 直接 createPlaylist+navigate）
currentRoute == SubRoutes.SONGS → TopEnd 排序（点击→ songsSortRequest 信号 → 屏幕内开排序弹层）
showGlobalBackButton  // TopStart：
    Screen.Diary.route    → diarySubPageOpen（日志列表/详情打开时）
    Screen.Settings.route → settingsSubPageOpen（二级页打开时）
    其他子路由（除 4 个主标签页）→ 恒显
```
- 统一规格：`width = 52.dp, height = 44.dp, cornerRadius = 18.dp, useSharedBackdrop = true, ignoreBackdropCompatibility = false`。

### 3.3 日记页 / 设置页内部返回（重要：别改成 popBackStack 直接退）
- 日记：日志列表/日志详情是 **MusicDiaryScreen 内部状态**（showLogPage / selectedLogId / aiAnalysisTarget），不是路由。
  全局返回按钮点击 → `diaryBackRequest.tryEmit(Unit)` → 屏幕内 `LaunchedEffect` 收集：
  `aiAnalysisTarget != null → 关分析页；selectedLogId != null → 回日志列表；showLogPage → 回主页面`。
- 设置：二级页也是内部状态（page / selectedArtworkAlbum）。`settingsBackRequest` 同款逻辑，逐级返回。
- 这些信号用 `MutableSharedFlow<Unit>(extraBufferCapacity = 1)` + `tryEmit`，按钮点击不需要挂起。
- **教训：删除页面内返回按钮后，必须保证全局按钮可见+正确，否则用户被困在内部页面。** 当时日记/设置差点漏掉（主标签页不显示全局按钮），已补 `diarySubPageOpen`/`settingsSubPageOpen` 条件。

### 3.4 backdrop 分层结构（AppNavigation.kt）
- NavHost 内：`LocalBackdropLayer provides null`、关闭 backdrop/haze 渲染。
- NavHost 外（底栏/MiniPlayer/全局按钮层）：`LocalBackdropLayer provides activeOverlayBackdrop`，`OverlayGlassBackend.LAYER_BACKDROP` 时才非空。
- `hazeState` 只给底栏/MiniPlayer 用。

---

## 4. 已完成的修复

- Room 升级闪退 → `fallbackToDestructiveMigration(dropAllTables = true)`（AppDatabase.kt）
- 歌词重复加载 → `lyricsLoadJob` cancel 旧任务；`scanLibraryHealth` 先 `loadSongs()`（MusicViewModel.kt）
- 播放队列崩溃 → key 前缀 + LaunchedEffect 键（NowPlayingScreen.kt）
- ripple 删除（`indication = null`）—— LiquidGlassInteractions.kt / SharedUI.kt
- iTunes 空数据修复（HEAD 已有）
- AI 日记分析详情页返回按钮退错页 → 见 3.3（本次会话修复）
- 设置页二级页无返回按钮 → 见 3.3（本次会话修复）
- 「歌词工作室」入口移除（本次会话，用户嫌按钮突兀+功能不实用）：
  - 删 LownPlaying 更多菜单项、showLyricsStudio 状态、LyricsStudioSheet 调用、删除 `ui/lyrics/LyricsStudioSheet.kt`。
  - MusicViewModel / LyricsStudioStore 的相关函数**保留为死代码**（收藏歌词、歌词显示设置在用，别乱删）。
- 歌词滚动左右对齐：`NowPlayingLyricsWithBlur` 里 `LyricsView(lyricHorizontalPadding = 0.dp)`，让歌词文字与专辑缩略图最左侧对齐。
- 正在播放页设置子页（本次会话新增，用户选定：歌词 8 项 + 默认页签 + 横屏状态栏）：
  - 歌词 8 项全部走 `LyricsDisplaySettings`（此前歌词工作室删了后没有入口，本次接回 UI）；滑块范围：竖/横屏字号 16-32sp、译文 10-20sp、行距 6-20dp、高亮 50-100%、蓝牙延迟 0-3000ms（步进 100ms）。
  - 默认页签仅「下次打开播放页」生效（currentTab 每次进入重新初始化）。
  - **沉浸式封面：用户要求整体删除（三次方案均不满意）**——AppSettings 字段/setter、设置页开关、NowPlayingScreen 背景层全部移除，播放页恢复原始方块封面+morph 动画。
  - 默认页签仅「下次打开播放页」生效（currentTab 每次进入重新初始化）。

---

## 5. 构建与环境

- 本地工具链（已是最新，勿再升）：AGP 8.13.2 / Kotlin 2.2.21 / KSP 2.2.21-2.0.5。
- Room 用 **2.7.0**（2.6.1 与 KSP 2.2 冲突，报 "unexpected jvm signature V"）。
- 构建命令：
  ```
  .\gradlew.bat :app:assembleRelease --no-daemon --console=plain
  ```
- 产物：`app\build\outputs\apk\release\app-release.apk`
- 版本：versionCode **80** / versionName **3.10.1**。
- 签名：`app/signing/inspire-release.jks`（密码 inspiremusic / inspiremusic），**不要把 .jks 提交进公开仓库**。
- 真机安装：
  ```
  & "C:\Users\NG KwanLam\Downloads\Compressed\platform-tools-latest-windows\platform-tools\adb.exe" install -r app\build\outputs\apk\release\app-release.apk
  ```

---

## 6. 下一步（待办）

1. **真机验证本次改动**（APK 3.10.1 已装已启动）：
   - 设置 → 正在播放页设置：滑块/开关/分段选择生效、恢复默认生效；
   - 默认页签改「歌词」后重新打开播放页 → 停在歌词页签；
   - 横屏隐藏状态栏关闭后 → 横屏显示状态栏；
   - 播放页恢复原始封面动画（morph/呼吸缩放）如常；
   - 之前遗留待验：播放清单「+」、歌曲页「排序」、日记/设置全局返回、歌词左对齐。
2. 验证 OK 后：清理冗余 stash（`git stash drop stash@{1}`、`git stash drop stash@{0}`）、删除工作区里的一次性 build 日志（build_*.log）。
3. **评估是否 commit + 发版 3.10.1**（原计划）：release-notes 已更新；注意 .jks 别进仓库、别把不相关图片/临时文件也提交。
4. 未做：`MusicViewModel`/`LyricsStudioStore` 里歌词工作室死代码清理（可选，低优先）。

---

## 7. 关键文件索引

| 文件 | 作用 |
|------|------|
| `app/src/main/java/com/inspiremusic/ui/AppNavigation.kt` | 全局返回/新建/排序按钮 + backdrop 分层（核心） |
| `ui/components/BackdropLiquidGlass.kt` | 玻璃入口：backdrop → haze → 安全材质 三档回退 |
| `ui/components/SharedUI.kt` | FloatingGlassIconButton（glassy 弃用勿用） |
| `ui/components/BlurBottomNavigation.kt` | 底栏，拖拽优化 |
| `ui/components/LiquidGlassInteractions.kt` | glassClickable + segmented rail（ripple 已删） |
| `ui/screens/MusicDiaryScreen.kt` | 内部返回信号收集（diaryBackRequest） |
| `ui/screens/NowPlayingScreen.kt` | 播放页；歌词对齐；队列修复；默认页签/横屏状态栏/沉浸式封面 |
| `settings/AppSettings.kt` | 全局设置（含正在播放页 3 项新字段 + setter） |
| `ui/screens/SettingsScreen.kt` | 内部返回信号收集 + 正在播放页设置子页（SettingsPage.NowPlaying） |
| `ui/screens/SongsScreen.kt` | 排序信号收集（songsSortRequest）；FavoritesScreen 也在此文件 |
| `ui/screens/NowPlayingScreen.kt` | 播放页；歌词对齐；队列修复 |
| `data/LyricsStudioStore.kt` + `viewmodel/MusicViewModel.kt` | 歌词工作室遗留死代码（收藏歌词/显示设置仍在使用；显示设置现已在设置页有 UI 入口） |

---

## 8. 给下一会话的提醒

- **先看第 1 节**：工作区才是当前生效状态；两个 stash 是冗余备份。
- 涉及玻璃/backdrop 的改动，必须先确认编译通过，再上真机。
- **屏幕内按钮不能真玻璃**——若再遇到"某按钮要真玻璃"，方案只有移到 AppNavigation 全局层（+ 信号流沟通），或保持安全材质。
- 改 UI 视觉时不要动 `MusicViewModel`/播放逻辑，避免把修好的 Bug 弄回去。