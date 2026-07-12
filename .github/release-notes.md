# 灵感音乐 Inspire Music

本次更新集中处理资料库和播放稳定性，让日常打开、切歌与播放列表操作更可靠。

## 更新内容

- 优化本地资料库扫描：批量读取与保存元数据，避免逐首数据库查询和后台任务堆积。
- 修复旧播放列表出现重复标识时可能闪退的问题，并自动清理重复数据。
- 统一专辑归组规则，减少合作艺人歌曲被拆成多张重复专辑的情况。
- 移除启动后的重复全量扫描，缩短资料库等待时间。
- 改善正在播放页切歌：上一张封面会保留到新封面加载完成，减少黑色闪烁。
- 限制超大自定义封面的显示解码尺寸，缓解切歌卡顿，同时保留原始高清文件。
- 应用回到前台时重新同步 Media3 播放状态，提升迷你播放器恢复可靠性。

## 安装

下载下方的 `InspireMusic-release.apk` 并安装。若设备提示签名冲突，说明现有安装来自不同签名的旧构建，需要卸载旧版一次后再安装。

源码、问题反馈与后续更新均在 [Kwanlam08/InspireMusic](https://github.com/Kwanlam08/InspireMusic)。

---

This release improves library loading, playlist stability, album grouping, artwork transitions, and playback-state restoration. Download `InspireMusic-release.apk` below.
