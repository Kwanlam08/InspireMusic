import React, { useState } from 'react';
import { useMusic } from '../context/MusicContext';
import { FolderPlus, Trash2, Sliders, Cpu, SunMoon, Sun, Moon, Laptop, Info, Check } from 'lucide-react';
import { ThemeMode } from '../types';

export const SettingsView: React.FC = () => {
  const { settings, updateSettings, scanFolder, isScanning, setThemeMode } = useMusic();
  const [savedSuccess, setSavedSuccess] = useState(false);

  const [aiEndpoint, setAiEndpoint] = useState(settings.aiApiUrl);
  const [aiKey, setAiKey] = useState(settings.aiApiKey);
  const [aiModel, setAiModel] = useState(settings.aiModel);
  const [lyricSize, setLyricSize] = useState(settings.lyricFontSize);
  const [blurStrength, setBlurStrength] = useState(settings.lyricBlurStrength);
  const [lyricOffset, setLyricOffset] = useState(settings.lyricDelayOffsetMs || 0);

  const handleSaveSettings = () => {
    updateSettings({
      aiApiUrl: aiEndpoint,
      aiApiKey: aiKey,
      aiModel: aiModel,
      lyricFontSize: lyricSize,
      lyricBlurStrength: blurStrength,
      lyricDelayOffsetMs: lyricOffset,
    });
    setSavedSuccess(true);
    setTimeout(() => setSavedSuccess(false), 2000);
  };

  const removeFolder = (folderPath: string) => {
    updateSettings({
      musicFolders: settings.musicFolders.filter((f) => f !== folderPath),
    });
  };

  return (
    <div className="flex-1 h-full overflow-y-auto p-8 pb-32 space-y-8 select-none text-neutral-900 dark:text-white transition-colors">
      {/* Title */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight text-neutral-900 dark:text-white/95">
          设置
        </h1>
        <p className="text-xs text-neutral-500 dark:text-white/40 mt-1">
          外观主题、音乐文件夹、歌词微调与 AI 灵感参数配置
        </p>
      </div>

      <div className="space-y-6 w-full max-w-4xl">
        {/* Appearance Theme Selector (Screen-09.png style) */}
        <div className="p-6 rounded-3xl bg-white/70 dark:bg-white/[0.03] backdrop-blur-2xl border border-black/5 dark:border-white/[0.09] space-y-4 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-amber-500/15 border border-amber-500/25 flex items-center justify-center text-amber-500">
              <SunMoon className="w-4 h-4" />
            </div>
            <div>
              <h3 className="font-bold text-sm">外观主题</h3>
              <p className="text-[11px] text-neutral-500 dark:text-white/40">跟随 Windows 系统主题或手动切换深浅色</p>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3 pt-1">
            {[
              { mode: 'system' as ThemeMode, label: '跟随系统', icon: Laptop },
              { mode: 'dark' as ThemeMode, label: '曜石深色', icon: Moon },
              { mode: 'light' as ThemeMode, label: '珍珠浅色', icon: Sun },
            ].map((item) => {
              const Icon = item.icon;
              const isActive = settings.themeMode === item.mode;

              return (
                <button
                  key={item.mode}
                  onClick={() => setThemeMode(item.mode)}
                  className={`
                    flex flex-col items-center justify-center gap-2 p-3.5 rounded-2xl border transition-all cursor-pointer
                    ${
                      isActive
                        ? 'bg-white dark:bg-white/15 text-neutral-900 dark:text-white border-pink-500 shadow-sm shadow-pink-500/10'
                        : 'bg-black/[0.02] dark:bg-white/[0.04] border-black/5 dark:border-white/10 text-neutral-500 dark:text-white/60 hover:text-black dark:hover:text-white'
                    }
                  `}
                >
                  <Icon className={`w-5 h-5 ${isActive ? 'text-pink-500' : ''}`} />
                  <span className="text-xs font-semibold">{item.label}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Local Music Folders */}
        <div className="p-6 rounded-3xl bg-white/70 dark:bg-white/[0.03] backdrop-blur-2xl border border-black/5 dark:border-white/[0.09] space-y-4 shadow-sm">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-xl bg-pink-500/15 border border-pink-500/25 flex items-center justify-center text-pink-500">
                <FolderPlus className="w-4 h-4" />
              </div>
              <div>
                <h3 className="font-bold text-sm">音乐文件夹</h3>
                <p className="text-[11px] text-neutral-500 dark:text-white/40">已包含的本地音乐扫描路径</p>
              </div>
            </div>

            <button
              onClick={scanFolder}
              disabled={isScanning}
              className="px-3.5 py-1.5 rounded-xl bg-black/5 dark:bg-white/[0.08] hover:bg-black/10 dark:hover:bg-white/[0.15] active:scale-95 border border-black/5 dark:border-white/10 text-xs font-semibold transition-all"
            >
              {isScanning ? '扫描中...' : '+ 添加文件夹'}
            </button>
          </div>

          <div className="space-y-2 pt-2">
            {settings.musicFolders.length === 0 ? (
              <div className="text-xs text-neutral-400 dark:text-white/30 italic py-2">
                尚未添加任何文件夹，点击右上角添加。
              </div>
            ) : (
              settings.musicFolders.map((folder, i) => (
                <div
                  key={i}
                  className="flex items-center justify-between p-3 rounded-xl bg-black/[0.02] dark:bg-white/[0.03] border border-black/5 dark:border-white/5 text-xs"
                >
                  <span className="truncate pr-4">{folder}</span>
                  <button
                    onClick={() => removeFolder(folder)}
                    className="p-1 hover:text-red-500 text-neutral-400 dark:text-white/30 transition-colors"
                    title="移除"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Lyrics Appearance Settings (NowPlaying screen subpage style) */}
        <div className="p-6 rounded-3xl bg-white/70 dark:bg-white/[0.03] backdrop-blur-2xl border border-black/5 dark:border-white/[0.09] space-y-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-purple-500/15 border border-purple-500/25 flex items-center justify-center text-purple-500">
              <Sliders className="w-4 h-4" />
            </div>
            <div>
              <h3 className="font-bold text-sm">动效歌词设置</h3>
              <p className="text-[11px] text-neutral-500 dark:text-white/40">字号、虚化强度与音频延迟补偿</p>
            </div>
          </div>

          <div className="space-y-4 pt-1">
            <div>
              <div className="flex justify-between text-xs mb-1.5 font-medium">
                <span>歌词基础字号 ({lyricSize}px)</span>
              </div>
              <input
                type="range"
                min="18"
                max="42"
                value={lyricSize}
                onChange={(e) => setLyricSize(parseInt(e.target.value))}
                className="w-full h-1 bg-black/15 dark:bg-white/20 rounded-lg appearance-none cursor-pointer accent-pink-500"
              />
            </div>

            <div>
              <div className="flex justify-between text-xs mb-1.5 font-medium">
                <span>非当前行虚化强度 ({blurStrength}px)</span>
              </div>
              <input
                type="range"
                min="0"
                max="16"
                value={blurStrength}
                onChange={(e) => setBlurStrength(parseInt(e.target.value))}
                className="w-full h-1 bg-black/15 dark:bg-white/20 rounded-lg appearance-none cursor-pointer accent-pink-500"
              />
            </div>

            <div>
              <div className="flex justify-between text-xs mb-1.5 font-medium">
                <span>蓝牙 / 音频输出延迟补偿 ({lyricOffset}ms)</span>
                <span className="text-neutral-400 dark:text-white/40">正数延后 · 负数提前</span>
              </div>
              <input
                type="range"
                min="-1000"
                max="3000"
                step="100"
                value={lyricOffset}
                onChange={(e) => setLyricOffset(parseInt(e.target.value))}
                className="w-full h-1 bg-black/15 dark:bg-white/20 rounded-lg appearance-none cursor-pointer accent-pink-500"
              />
            </div>
          </div>
        </div>

        {/* AI Inspiration API Settings */}
        <div className="p-6 rounded-3xl bg-white/70 dark:bg-white/[0.03] backdrop-blur-2xl border border-black/5 dark:border-white/[0.09] space-y-5 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-violet-500/15 border border-violet-500/25 flex items-center justify-center text-violet-500">
              <Cpu className="w-4 h-4" />
            </div>
            <div>
              <h3 className="font-bold text-sm">AI 灵感模型服务</h3>
              <p className="text-[11px] text-neutral-500 dark:text-white/40">自定义配置你的大模型 API 服务</p>
            </div>
          </div>

          <div className="space-y-3.5 pt-1">
            <div>
              <label className="block text-xs font-medium mb-1">API 接口端点</label>
              <input
                type="text"
                value={aiEndpoint}
                onChange={(e) => setAiEndpoint(e.target.value)}
                placeholder="https://api.deepseek.com/v1"
                className="w-full px-3.5 py-2 rounded-xl bg-black/[0.04] dark:bg-white/[0.06] border border-black/5 dark:border-white/10 text-xs focus:outline-none focus:border-pink-500"
              />
            </div>

            <div>
              <label className="block text-xs font-medium mb-1">API Key</label>
              <input
                type="password"
                value={aiKey}
                onChange={(e) => setAiKey(e.target.value)}
                placeholder="sk-..."
                className="w-full px-3.5 py-2 rounded-xl bg-black/[0.04] dark:bg-white/[0.06] border border-black/5 dark:border-white/10 text-xs focus:outline-none focus:border-pink-500"
              />
            </div>

            <div>
              <label className="block text-xs font-medium mb-1">模型名称</label>
              <input
                type="text"
                value={aiModel}
                onChange={(e) => setAiModel(e.target.value)}
                placeholder="deepseek-chat"
                className="w-full px-3.5 py-2 rounded-xl bg-black/[0.04] dark:bg-white/[0.06] border border-black/5 dark:border-white/10 text-xs focus:outline-none focus:border-pink-500"
              />
            </div>

            <div className="pt-2 flex items-center gap-3">
              <button
                onClick={handleSaveSettings}
                className="px-5 py-2 rounded-xl bg-pink-500 text-white text-xs font-semibold hover:bg-pink-600 active:scale-95 transition-all shadow-md cursor-pointer"
              >
                保存配置
              </button>

              {savedSuccess && (
                <span className="flex items-center gap-1 text-xs text-teal-600 dark:text-teal-400 animate-in fade-in">
                  <Check className="w-3.5 h-3.5" />
                  已保存并生效
                </span>
              )}
            </div>
          </div>
        </div>

        {/* About Info */}
        <div className="p-6 rounded-3xl bg-black/[0.02] dark:bg-white/[0.02] border border-black/5 dark:border-white/5 space-y-2 text-xs text-neutral-500 dark:text-white/40">
          <div className="flex items-center gap-2 text-neutral-700 dark:text-white/60 font-medium">
            <Info className="w-3.5 h-3.5" />
            <span>Inspire Music Windows Desktop v1.1.0</span>
          </div>
          <p>
            Liquid Glass 真液态玻璃设计风格 · 纯本地运行 · 尊重隐私 · 音乐与灵感的专属空间
          </p>
        </div>
      </div>
    </div>
  );
};
