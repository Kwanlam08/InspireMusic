# 灵感音乐 Inspire Music 3.8.0

3.8.0 让本地音乐资料库从“能浏览”升级为可以安全整理、自动更新并可靠恢复播放现场的音乐空间。

## 音乐资料整理中心

- 扫描合作艺人或命名差异造成的重复专辑，并在确认前展示受影响歌曲与合并结果。
- 检查缺失封面、曲号、年份、流派和专辑艺术家。
- 支持逐曲修正歌曲、艺人、专辑、专辑艺术家、曲号、碟号、年份和流派。
- 所有修改仅保存在 App 内覆盖层，不会重写原音乐文件。
- 每次保存和批量合并都会生成撤销记录；Room v5→v6 使用显式非破坏性迁移。

## 本地智能播放列表

- 新增最近添加、很久没听、夜间常听、播放最多、收藏但少听五个内置列表。
- 规则完全在本机运行，并随资料库、收藏和真实播放记录自动更新，不依赖 AI API。

## 播放与稳定性

- 保持 Media3 原生无缝衔接，支持 3、6、12 秒末段淡化。
- 读取音频内嵌 `REPLAYGAIN_TRACK_GAIN` 标签并应用安全音量余量。
- 恢复上次队列顺序、当前索引、随机/循环状态和毫秒级播放位置。
- 加入 2 秒起播与 30 秒最大预缓冲，延续封面交叉替换，减少卡顿和切歌黑闪。
- 新增最多 512 KB 的滚动诊断日志，可从设置导出；不包含音乐文件和 API Key。

## 发布

Release 与 APK 现在直接发布到 [Kwanlam08/InspireMusic](https://github.com/Kwanlam08/InspireMusic)。下载下方 `InspireMusic-release.apk` 安装即可。

---

Version 3.8.0 adds a non-destructive Library Organizer with undo, five offline smart playlists, gapless playback with adjustable fades, ReplayGain tag handling, exact queue/position restoration, and exportable diagnostics.
