import React, { useEffect, useState } from 'react';
import { Minus, Square, Copy, X, PictureInPicture2, Quote, Maximize2, Minimize2 } from 'lucide-react';
import { useMusic } from '../../context/MusicContext';
import logoImg from '../../assets/logo.png';

export const TitleBar: React.FC = () => {
  const [isMaximized, setIsMaximized] = useState(false);
  const [isFullScreen, setIsFullScreen] = useState(false);
  const { isDesktopLyricsOpen, toggleDesktopLyrics, toggleMiniMode } = useMusic();

  useEffect(() => {
    if (window.electronAPI) {
      window.electronAPI.isMaximized().then(setIsMaximized);
      const unbindMax = window.electronAPI.onMaximizedChange((val) => {
        setIsMaximized(val);
      });
      const unbindFs = window.electronAPI.onFullScreenChange?.((fs) => {
        setIsFullScreen(fs);
      });
      return () => {
        unbindMax();
        unbindFs?.();
      };
    }
  }, []);

  const handleMinimize = () => {
    window.electronAPI?.minimize();
  };

  const handleMaximize = async () => {
    if (window.electronAPI) {
      const state = await window.electronAPI.maximize();
      setIsMaximized(state);
    }
  };

  const handleToggleFullScreen = async () => {
    if (window.electronAPI?.setFullScreen) {
      const next = await window.electronAPI.setFullScreen();
      setIsFullScreen(next);
    }
  };

  const handleClose = () => {
    window.electronAPI?.close();
  };

  return (
    <div className="h-10 w-full flex items-center justify-between px-3 select-none z-50 bg-white/70 dark:bg-black/20 backdrop-blur-md border-b border-black/[0.06] dark:border-white/[0.06] text-neutral-800 dark:text-white/80 shrink-0 transition-colors">
      {/* Draggable Title / Logo Area */}
      <div
        className="flex items-center gap-2.5 h-full flex-1 cursor-default"
        style={{ WebkitAppRegion: 'drag' } as React.CSSProperties}
      >
        <img
          src={logoImg}
          alt="Inspire Music"
          className="w-5 h-5 rounded-md object-contain shadow-sm"
        />
        <span className="text-xs font-semibold tracking-wider text-neutral-800 dark:text-white/90">
          灵感音乐 <span className="text-neutral-400 dark:text-white/40 font-light">Inspire Music</span>
        </span>
      </div>

      {/* Feature & Window Controls */}
      <div
        className="flex items-center h-full -mr-3"
        style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}
      >
        {/* Desktop Floating Lyrics Button */}
        <button
          onClick={toggleDesktopLyrics}
          className={`h-10 w-10 flex items-center justify-center hover:bg-black/5 dark:hover:bg-white/10 transition-colors ${
            isDesktopLyricsOpen ? 'text-pink-500 font-bold' : 'text-neutral-600 dark:text-white/60'
          }`}
          title={isDesktopLyricsOpen ? '关闭桌面悬浮歌词' : '开启桌面悬浮歌词'}
        >
          <Quote className="w-3.5 h-3.5" />
        </button>

        {/* Mini Mode Button */}
        <button
          onClick={toggleMiniMode}
          className="h-10 w-10 flex items-center justify-center hover:bg-black/5 dark:hover:bg-white/10 text-neutral-600 dark:text-white/60 hover:text-black dark:hover:text-white transition-colors"
          title="切换迷你模式"
        >
          <PictureInPicture2 className="w-3.5 h-3.5" />
        </button>

        {/* Fullscreen Button */}
        <button
          onClick={handleToggleFullScreen}
          className="h-10 w-10 flex items-center justify-center hover:bg-black/5 dark:hover:bg-white/10 text-neutral-600 dark:text-white/60 hover:text-black dark:hover:text-white transition-colors"
          title={isFullScreen ? '退出全屏 (F11)' : '全屏 (F11)'}
        >
          {isFullScreen ? (
            <Minimize2 className="w-3.5 h-3.5" />
          ) : (
            <Maximize2 className="w-3.5 h-3.5" />
          )}
        </button>

        <div className="w-[1px] h-3.5 bg-black/10 dark:bg-white/10 mx-1" />

        {/* Windows Standard Controls */}
        <button
          onClick={handleMinimize}
          className="h-10 w-12 flex items-center justify-center hover:bg-black/5 dark:hover:bg-white/10 active:bg-black/10 dark:active:bg-white/15 text-neutral-700 dark:text-white/70 hover:text-black dark:hover:text-white transition-colors"
          title="最小化"
        >
          <Minus className="w-3.5 h-3.5" />
        </button>
        <button
          onClick={handleMaximize}
          className="h-10 w-12 flex items-center justify-center hover:bg-black/5 dark:hover:bg-white/10 active:bg-black/10 dark:active:bg-white/15 text-neutral-700 dark:text-white/70 hover:text-black dark:hover:text-white transition-colors"
          title={isMaximized ? '还原' : '最大化'}
        >
          {isMaximized ? (
            <Copy className="w-3.5 h-3.5 transform rotate-90 scale-90" />
          ) : (
            <Square className="w-3 h-3" />
          )}
        </button>
        <button
          onClick={handleClose}
          className="h-10 w-12 flex items-center justify-center hover:bg-red-500/80 active:bg-red-600 text-white/70 hover:text-white transition-colors"
          title="关闭"
        >
          <X className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
};
