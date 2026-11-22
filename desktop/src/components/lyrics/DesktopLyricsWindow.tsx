import React, { useState, useEffect } from 'react';
import { Play, Pause, SkipBack, SkipForward, X, Music } from 'lucide-react';
import { LrcLine } from '../../types';

function parseLrc(lrcText: string): LrcLine[] {
  if (!lrcText) return [];
  const lines = lrcText.split('\n');
  const result: LrcLine[] = [];
  const timeRegex = /\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\]/g;

  for (const rawLine of lines) {
    const trimmed = rawLine.trim();
    if (!trimmed) continue;
    let match;
    let hasTime = false;
    while ((match = timeRegex.exec(trimmed)) !== null) {
      hasTime = true;
      const mins = parseInt(match[1], 10);
      const secs = parseInt(match[2], 10);
      const ms = match[3] ? parseInt(match[3].padEnd(3, '0').slice(0, 3), 10) : 0;
      const time = mins * 60 + secs + ms / 1000;
      const text = trimmed.replace(timeRegex, '').trim();
      if (text) {
        result.push({ time, text });
      }
    }
  }
  return result.sort((a, b) => a.time - b.time);
}

export const DesktopLyricsWindow: React.FC = () => {
  const [songInfo, setSongInfo] = useState<{
    title: string;
    artist: string;
    lyrics: string;
    currentTime: number;
    duration: number;
    isPlaying: boolean;
  }>({
    title: 'Inspire Music',
    artist: '正在播放',
    lyrics: '',
    currentTime: 0,
    duration: 0,
    isPlaying: false,
  });

  const [parsedLyrics, setParsedLyrics] = useState<LrcLine[]>([]);

  useEffect(() => {
    if (!window.electronAPI) return;
    const unsub = window.electronAPI.onLyricsStateUpdate?.((data) => {
      setSongInfo((prev) => {
        if (prev.lyrics !== data.lyrics) {
          setParsedLyrics(parseLrc(data.lyrics || ''));
        }
        return data;
      });
    });
    return () => unsub?.();
  }, []);

  // Find active line index
  let currentIndex = -1;
  for (let i = 0; i < parsedLyrics.length; i++) {
    if (parsedLyrics[i].time <= songInfo.currentTime) {
      currentIndex = i;
    } else {
      break;
    }
  }

  const currentLine = currentIndex >= 0 ? parsedLyrics[currentIndex]?.text : songInfo.title;
  const nextLine =
    currentIndex + 1 < parsedLyrics.length
      ? parsedLyrics[currentIndex + 1]?.text
      : songInfo.artist || 'Inspire Music';

  const handleControl = (action: string) => {
    window.electronAPI?.sendLyricsControl?.(action);
  };

  const handleClose = () => {
    window.electronAPI?.closeDesktopLyrics?.();
  };

  return (
    <div className="w-screen h-screen bg-transparent select-none overflow-hidden flex items-center justify-center p-2 font-sans">
      <div
        className="relative group w-full h-full max-h-[85px] flex items-center justify-between px-6 rounded-3xl bg-[#0e0e14]/90 backdrop-blur-3xl border border-white/20 shadow-[0_15px_40px_rgba(0,0,0,0.85)] cursor-move transition-all"
        style={{ WebkitAppRegion: 'drag' } as React.CSSProperties}
      >
        {/* Left: Icon or tiny visualizer */}
        <div
          className="flex items-center gap-3 shrink-0"
          style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}
        >
          <div className="w-9 h-9 rounded-2xl bg-gradient-to-tr from-pink-500 to-purple-600 flex items-center justify-center text-white shadow-md">
            <Music className="w-4 h-4" />
          </div>
        </div>

        {/* Center: Lyrics (Dual line display) */}
        <div className="flex-1 mx-4 text-center min-w-0 pointer-events-none">
          <div className="text-base font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-pink-400 via-rose-300 to-white truncate drop-shadow-sm transition-all duration-200">
            {currentLine || songInfo.title}
          </div>
          <div className="text-xs font-medium text-white/50 truncate mt-0.5">
            {nextLine}
          </div>
        </div>

        {/* Right: Hover Controls & Close */}
        <div
          className="flex items-center gap-1.5 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity"
          style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}
        >
          <button
            onClick={() => handleControl('prev')}
            className="p-2 rounded-full text-white/70 hover:text-white hover:bg-white/10 active:scale-95 transition-all cursor-pointer"
            title="上一首"
          >
            <SkipBack className="w-4 h-4 fill-current" />
          </button>

          <button
            onClick={() => handleControl('play-pause')}
            className="w-8 h-8 rounded-full bg-white text-black flex items-center justify-center hover:scale-105 active:scale-95 transition-all shadow-md cursor-pointer"
            title={songInfo.isPlaying ? '暂停' : '播放'}
          >
            {songInfo.isPlaying ? (
              <Pause className="w-4 h-4 fill-black" />
            ) : (
              <Play className="w-4 h-4 fill-black translate-x-0.5" />
            )}
          </button>

          <button
            onClick={() => handleControl('next')}
            className="p-2 rounded-full text-white/70 hover:text-white hover:bg-white/10 active:scale-95 transition-all cursor-pointer"
            title="下一首"
          >
            <SkipForward className="w-4 h-4 fill-current" />
          </button>

          <button
            onClick={handleClose}
            className="p-2 rounded-full text-white/40 hover:text-red-400 hover:bg-white/10 active:scale-95 transition-all cursor-pointer ml-1"
            title="关闭桌面歌词"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
