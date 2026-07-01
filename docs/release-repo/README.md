# 灵感音乐 / Inspire Music

> 一个专注本地音乐、歌词、歌单和灵感推荐的 Android 音乐播放器。  
> An Android music player for local music, lyrics, playlists, and inspiration-driven listening.

## 下载

请前往 [Releases](../../releases) 下载最新 APK。

如果安装时提示“软件包与已有软件包存在冲突”，通常是因为旧版本和新版本签名不同。请先卸载旧版，再安装最新版。之后使用新版固定签名的 APK，就可以正常覆盖更新。

## 应用亮点

- 本地音乐资料库：歌曲、专辑、艺人、播放列表、我的喜爱。
- 类 Liquid Glass 视觉：底栏、迷你播放器、按钮、弹窗和正在播放页面统一为玻璃风格。
- 正在播放体验：专辑封面、歌词、播放队列、睡眠定时、进度和音量控制。
- 歌词支持：本地歌词、内嵌歌词、在线歌词搜索和歌词缓存管理。
- 音乐日记：按日、周、月回顾听歌记录，查看常听歌曲、艺人和流派。
- 数据备份：播放列表、听歌记录、最近播放均可备份和导入。
- 局域网互传：在两台设备的灵感音乐内直接传输备份。
- 存储空间：查看本地音乐占用的设备空间。
- 可选 AI 推荐：根据输入的心情、场景或关键词生成灵感歌单。

## 截图

截图稍后补充。建议展示这些界面：

1. 灵感页：AI 输入框、推荐结果或灵感卡片。
2. 资料库页：播放列表、专辑、艺人、歌曲入口。
3. 正在播放页：专辑封面、进度条、播放控制。
4. 歌词页：动态歌词或静态歌词展示。
5. 音乐日记页：日记 / 周记 / 月记概览。
6. 设置页：数据备份、音乐存储空间、歌词缓存。
7. LocalSend 互传页：接收和扫描附近设备。

## 安装

1. 在 [Releases](../../releases) 下载最新的 `InspireMusic-release.apk`。
2. 在 Android 设备上打开 APK。
3. 如果系统提示，请允许“安装未知来源应用”。
4. 首次启动后，授予音乐 / 媒体访问权限。

## AI 与隐私

灵感音乐的核心播放、资料库、歌词缓存、音乐日记和备份功能都在本机运行。

AI 功能是可选功能。为了安全，正式版本不应该内置个人 API Key。若使用 AI 推荐，请在设置中配置你自己的 API 服务信息，并自行遵守对应服务商的使用条款。

在线歌词、元数据或 AI 功能可能会向对应服务发送请求；如果你关闭这些在线功能，核心播放器仍可作为本地音乐播放器使用。

## 版本说明

每个发布版本都会在 Releases 页面提供 APK。版本号遵循 `3.0.x` 的小版本递增方式；没有重大重构时，只递增最后一位小版本号。

---

# Inspire Music

Inspire Music is an Android music player focused on local libraries, lyrics, playlists, and inspiration-driven listening.

## Download

Download the latest APK from the [Releases](../../releases) page.

If Android says the package conflicts with an existing package, the installed app was likely signed with an older certificate. Uninstall the old build once, then install the latest APK. Future builds using the stable signing key should update normally.

## Highlights

- Local music library: songs, albums, artists, playlists, and favorites.
- Liquid Glass-inspired visual style across the bottom bar, mini player, buttons, sheets, and now playing screen.
- Rich now playing screen with artwork, lyrics, queue, sleep timer, progress, and volume controls.
- Lyrics support: local lyrics, embedded lyrics, online search, and cache management.
- Music Diary: daily, weekly, and monthly listening summaries with top songs, artists, and genres.
- Data backup: export and import playlists, listening history, and recently played data.
- Local transfer: send backups between two Inspire Music devices on the same network.
- Storage overview: see how much device storage your local music uses.
- Optional AI recommendations based on moods, scenes, or keywords.

## Screenshots

Screenshots will be added later. Recommended screenshots:

1. Inspire page with the AI input and recommendation cards.
2. Library page with playlists, albums, artists, and songs.
3. Now playing page with artwork and playback controls.
4. Lyrics page with synced or static lyrics.
5. Music Diary page with day / week / month summaries.
6. Settings page with data backup, music storage, and lyrics cache.
7. Local transfer page showing receive and nearby devices.

## Installation

1. Download `InspireMusic-release.apk` from [Releases](../../releases).
2. Open the APK on your Android device.
3. Allow installation from this source if Android asks.
4. Grant music / media permission after launching the app.

## AI And Privacy

The core player, library, lyrics cache, diary, and backup features run locally on your device.

AI features are optional. Release builds should not include a personal API key. If you use AI recommendations, configure your own API provider in settings and follow that provider's terms.

Online lyrics, metadata, or AI features may send requests to their configured services. If you disable online features, Inspire Music still works as a local music player.

## Versioning

APK builds are published on the Releases page. Versions follow the `3.0.x` patch version style; small updates increment the final patch number.
