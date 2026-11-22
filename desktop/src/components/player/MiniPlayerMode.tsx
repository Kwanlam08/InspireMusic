import React from 'react';
import { useMusic } from '../../context/MusicContext';
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Star,
  Maximize2,
  X,
  Music,
} from 'lucide-react';

function formatTime(sec: number): string {
  if (isNaN(sec) || sec < 0) return '0:00';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

export const MiniPlayerMode: React.FC = () => {
  const {
    currentSong,
    isPlaying,
    currentTime,
    duration,
    togglePlay,
    nextTrack,
    prevTrack,
    seek,
    favorites,
    toggleFavorite,
    toggleMiniMode,
  } = useMusic();

  if (!currentSong) return null;

  const isFav = favorites.has(currentSong.id);
  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <div
      className="w-screen h-screen bg-[#101016] text-white flex flex-col justify-between p-3.5 select-none overflow-hidden font-sans border border-white/15 rounded-2xl shadow-2xl relative"
      style={{ WebkitAppRegion: 'drag' } as React.CSSProperties}
    >
      {/* Dynamic Ambient Glow */}
      {currentSong.coverUrl && (
        <div
          className="absolute inset-0 opacity-25 blur-2xl pointer-events-none scale-125"
          style={{
            backgroundImage: `url(${currentSong.coverUrl})`,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
          }}
        />
      )}

      {/* Top Bar: Title & Controls */}
      <div className="relative z-10 flex items-center justify-between gap-2">
        <div className="flex items-center gap-2.5 min-w-0 flex-1">
          <div className="w-11 h-11 rounded-xl overflow-hidden shrink-0 border border-white/15 bg-white/10 shadow-md">
            {currentSong.coverUrl ? (
              <img src={currentSong.coverUrl} alt="" className="w-full h-full object-cover" />
            ) : (
              <div className="w-full h-full bg-pink-500/30 flex items-center justify-center text-white">
                <Music className="w-5 h-5" />
              </div>
            )}
          </div>

          <div className="min-w-0 flex-1">
            <div className="font-bold text-xs text-white truncate">{currentSong.title}</div>
            <div className="text-[11px] text-white/50 truncate mt-0.5">{currentSong.artist}</div>
          </div>
        </div>

        {/* Restore & Close Buttons */}
        <div
          className="flex items-center gap-1 shrink-0"
          style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}
        >
          <button
            onClick={toggleMiniMode}
            className="p-1.5 rounded-lg text-white/60 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
            title="还原主窗口"
          >
            <Maximize2 className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => window.electronAPI?.close()}
            className="p-1.5 rounded-lg text-white/40 hover:text-red-400 hover:bg-white/10 transition-colors cursor-pointer"
            title="关闭软件"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Bottom Bar: Progress & Playback Controls */}
      <div
        className="relative z-10 space-y-1.5 mt-2"
        style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}
      >
        {/* Scrubber */}
        <div
          className="relative h-1.5 w-full flex items-center cursor-pointer group"
          onClick={(e) => {
            const rect = e.currentTarget.getBoundingClientRect();
            const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
            seek(ratio * duration);
          }}
        >
          <div className="w-full h-1 bg-white/15 rounded-full overflow-hidden group-hover:h-1.5 transition-all">
            <div
              className="h-full bg-pink-500 rounded-full"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </div>

        {/* Buttons Row */}
        <div className="flex items-center justify-between text-xs pt-0.5">
          <span className="text-[10px] text-white/40 tabular-nums">
            {formatTime(currentTime)}
          </span>

          <div className="flex items-center gap-3">
            <button
              onClick={() => toggleFavorite(currentSong.id)}
              className="p-1 text-white/40 hover:text-yellow-400 transition-colors cursor-pointer"
            >
              <Star className={`w-3.5 h-3.5 ${isFav ? 'text-yellow-400 fill-yellow-400' : ''}`} />
            </button>

            <button
              onClick={prevTrack}
              className="p-1 text-white/70 hover:text-white active:scale-90 transition-all cursor-pointer"
            >
              <SkipBack className="w-4 h-4 fill-current" />
            </button>

            <button
              onClick={togglePlay}
              className="w-7 h-7 rounded-full bg-white text-black flex items-center justify-center hover:scale-105 active:scale-95 transition-all shadow-md cursor-pointer"
            >
              {isPlaying ? (
                <Pause className="w-3.5 h-3.5 fill-black" />
              ) : (
                <Play className="w-3.5 h-3.5 fill-black translate-x-0.5" />
              )}
            </button>

            <button
              onClick={nextTrack}
              className="p-1 text-white/70 hover:text-white active:scale-90 transition-all cursor-pointer"
            >
              <SkipForward className="w-4 h-4 fill-current" />
            </button>
          </div>

          <span className="text-[10px] text-white/40 tabular-nums">
            {formatTime(duration)}
          </span>
        </div>
      </div>
    </div>
  );
};
