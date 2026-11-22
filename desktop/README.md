# Inspire Music Windows 桌面版 (Inspire Music Desktop)

遵循与移动端一致的 **Liquid Glass（真液态毛玻璃）** 与 Apple Music 纯粹设计美学，为 Windows 打造的桌面音乐播放器。

---

## 亮点特性

- 🫧 **1:1 Liquid Glass 视觉体验**：自适应硬件加速毛玻璃、高光微边框与层次分明的悬浮面板。
- 🎨 **动态封面流体色彩 (Fluid Mesh Gradient)**：播放时实时从专辑封面提取调色板，背景缓慢呼吸流动，色彩与旋律交相辉映。
- 🎤 **Apple Music 级动态虚化歌词**：支持精准同步滚动、当前句加粗高亮发光、非播放行景深高斯模糊虚化，点击歌词任意跳转播放。
- 🪟 **Windows 沉浸式窗口**：无边框自定义毛玻璃标题栏，原生支持窗口拖拽、最小化、最大化/还原与关闭。
- 📂 **本地音乐文件夹极速扫描**：直接选取本地文件夹（支持 MP3 / FLAC / WAV / M4A / OGG），毫秒级提取 ID3 标签与高清内嵌封面。
- 📝 **音乐日记 (Music Diary)**：自动记录在 Windows 上的听歌足迹、日记/周记统计与常听榜单。
- ✨ **AI 灵感歌单 (Inspire)**：支持配置 DeepSeek / OpenAI 等自定义 API，根据一句话或心情推荐音乐。

---

## 启动与使用指南

进入 `desktop` 目录：

```powershell
cd desktop
```

### 1. 启动桌面应用

```powershell
npm start
```
或者启动开发实时热更新模式：
```powershell
npm run dev
```

### 2. 浏览器直接预览体验 (可选)

```powershell
npm run dev:web
```
浏览器打开 `http://localhost:5173` 即可直接在网页中体验播放器所有动效与界面！
