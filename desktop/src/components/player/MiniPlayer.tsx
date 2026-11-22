import React, { useState } from 'react';
import { useMusic } from '../../context/MusicContext';
import { ContextMenu } from '../common/ContextMenu';
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Volume2,
  VolumeX,
  Star,
  Quote,
  ListMusic,
  Maximize2,
  Repeat,
  Repeat1,
  Shuffle,
  PictureInPicture2,
} from 'lucide-react';

function formatTime(seconds: number): string {
  if (isNaN(seconds) || seconds < 0) return '0:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
}

export const MiniPlayer: React.FC = () => {
  const {
    currentSong,
    isPlaying,
    currentTime,
    duration,
    volume,
    isMuted,
    playMode,
    favorites,
    togglePlay,
    nextTrack,
    prevTrack,
    seek,
    setVolume,
    toggleMute,
    cyclePlayMode,
    toggleFavorite,
    setIsNowPlayingOpen,
    isLyricsActive,
    setIsLyricsActive,
    isQueueDrawerOpen,
    setIsQueueDrawerOpen,
    isDesktopLyricsOpen,
    toggleDesktopLyrics,
    toggleMiniMode,
  } = useMusic();

  const [isHoveringProgress, setIsHoveringProgress] = useState(false);
  const [contextMenuData, setContextMenuData] = useState<{ x: number; y: number } | null>(null);

  if (!currentSong) return null;

  const isFav = favorites.has(currentSong.id);
  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  return (
    <div className="fixed bottom-4 left-6 right-6 z-40">
      <div
        className="
          relative mx-auto max-w-5xl h-20 px-4 rounded-3xl overflow-hidden
          bg-white/90 dark:bg-black/[0.55] backdrop-blur-2xl
          border border-black/10 dark:border-white/[0.15]
          shadow-[0_12px_40px_rgba(0,0,0,0.08)] dark:shadow-[0_20px_50px_rgba(0,0,0,0.5),inset_0_1px_0_rgba(255,255,255,0.22)]
          flex items-center justify-between gap-4 select-none
          transition-all duration-300
        "
      >
        {/* Subtle top wash highlight */}
        <div className="pointer-events-none absolute inset-0 rounded-3xl bg-gradient-to-b from-black/[0.02] dark:from-white/[0.08] via-transparent to-black/[0.05] dark:to-black/[0.1]" />

        {/* Left Section: Cover + Song Info + Favorite */}
        <div
          onContextMenu={(e) => {
            e.preventDefault();
            setContextMenuData({ x: e.clientX, y: e.clientY });
          }}
          className="flex items-center gap-3.5 min-w-0 w-1/4 z-10"
        >
          <div
            onClick={() => setIsNowPlayingOpen(true)}
            className="group relative w-12 h-12 min-w-[48px] min-h-[48px] max-w-[48px] max-h-[48px] rounded-2xl overflow-hidden shadow-md cursor-pointer shrink-0 border border-black/10 dark:border-white/10"
          >
            {currentSong.coverUrl ? (
              <img
                src={currentSong.coverUrl}
                alt={currentSong.title}
                className="w-full h-full object-cover block group-hover:scale-105 transition-transform duration-300"
              />
            ) : (
              <div className="w-full h-full bg-gradient-to-br from-indigo-500 to-pink-500 flex items-center justify-center">
                <span className="text-white text-xs font-bold">♪</span>
              </div>
            )}
            <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
              <Maximize2 className="w-4 h-4 text-white" />
            </div>
          </div>

          <div className="min-w-0 flex-1">
            <div
              onClick={() => setIsNowPlayingOpen(true)}
              className="font-semibold text-sm text-neutral-900 dark:text-white/95 truncate hover:text-pink-500 cursor-pointer transition-colors"
            >
              {currentSong.title}
            </div>
            <div className="text-xs text-neutral-500 dark:text-white/50 truncate hover:text-neutral-800 dark:hover:text-white/80 cursor-pointer">
              {currentSong.artist}
            </div>
          </div>

          <button
            onClick={() => toggleFavorite(currentSong.id)}
            className={`p-2 rounded-full hover:bg-black/5 dark:hover:bg-white/10 transition-colors ${
              isFav ? 'text-yellow-400 fill-yellow-400' : 'text-neutral-400 dark:text-white/40 hover:text-neutral-700 dark:hover:text-white'
            }`}
            title={isFav ? '取消收藏' : '收藏'}
          >
            <Star className={`w-4 h-4 ${isFav ? 'fill-current' : ''}`} />
          </button>
        </div>

        {/* Center Section: Controls & Scrubber */}
        <div className="flex flex-col items-center justify-center flex-1 max-w-xl z-10">
          <div className="flex items-center gap-5 mb-1.5">
            {/* Play Mode */}
            <button
              onClick={cyclePlayMode}
              className="p-1.5 text-neutral-400 dark:text-white/40 hover:text-neutral-800 dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10 rounded-full transition-colors"
              title={`模式: ${playMode}`}
            >
              {playMode === 'shuffle' && <Shuffle className="w-4 h-4 text-pink-500 dark:text-pink-400" />}
              {playMode === 'loop-one' && <Repeat1 className="w-4 h-4 text-pink-500 dark:text-pink-400" />}
              {playMode === 'loop' && <Repeat className="w-4 h-4 text-pink-500 dark:text-pink-400" />}
              {playMode === 'sequence' && <Repeat className="w-4 h-4 text-neutral-400 dark:text-white/40" />}
            </button>

            {/* Prev Track */}
            <button
              onClick={prevTrack}
              className="p-2 text-neutral-700 dark:text-white/80 hover:text-neutral-950 dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10 active:scale-95 rounded-full transition-all"
            >
              <SkipBack className="w-5 h-5 fill-current" />
            </button>

            {/* Play / Pause button */}
            <button
              onClick={togglePlay}
              className="w-10 h-10 rounded-full bg-neutral-900 text-white dark:bg-white dark:text-black flex items-center justify-center shadow-lg hover:scale-105 active:scale-95 transition-all"
            >
              {isPlaying ? (
                <Pause className="w-5 h-5 fill-current" />
              ) : (
                <Play className="w-5 h-5 fill-current translate-x-0.5" />
              )}
            </button>

            {/* Next Track */}
            <button
              onClick={nextTrack}
              className="p-2 text-neutral-700 dark:text-white/80 hover:text-neutral-950 dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10 active:scale-95 rounded-full transition-all"
            >
              <SkipForward className="w-5 h-5 fill-current" />
            </button>
          </div>

          {/* Scrubber Bar */}
          <div className="w-full flex items-center gap-3">
            <span className="text-[11px] font-medium text-neutral-400 dark:text-white/40 w-9 text-right tabular-nums">
              {formatTime(currentTime)}
            </span>

            <div
              className="relative flex-1 h-2 flex items-center cursor-pointer group"
              onMouseEnter={() => setIsHoveringProgress(true)}
              onMouseLeave={() => setIsHoveringProgress(false)}
              onClick={(e) => {
                const rect = e.currentTarget.getBoundingClientRect();
                const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
                seek(ratio * duration);
              }}
            >
              {/* Background Track */}
              <div className="w-full h-1 bg-black/10 dark:bg-white/20 rounded-full overflow-hidden group-hover:h-1.5 transition-all">
                <div
                  className="h-full bg-gradient-to-r from-pink-500 to-violet-500 rounded-full transition-all duration-100"
                  style={{ width: `${progressPercent}%` }}
                />
              </div>

              {/* Thumb */}
              <div
                className={`absolute w-3 h-3 bg-neutral-900 dark:bg-white rounded-full shadow-md pointer-events-none transform -translate-x-1/2 transition-all ${
                  isHoveringProgress ? 'scale-100 opacity-100' : 'scale-0 opacity-0'
                }`}
                style={{ left: `${progressPercent}%` }}
              />
            </div>

            <span className="text-[11px] font-medium text-neutral-400 dark:text-white/40 w-9 text-left tabular-nums">
              {formatTime(duration)}
            </span>
          </div>
        </div>

        {/* Right Section: Volume & Extra actions */}
        <div className="flex items-center justify-end gap-3 w-1/4 z-10">
          {/* Lyrics Button */}
          <button
            onClick={() => {
              setIsNowPlayingOpen(true);
              setIsLyricsActive(true);
            }}
            className={`p-2 rounded-full transition-colors ${
              isLyricsActive ? 'text-pink-500 bg-pink-500/10 dark:text-pink-400 dark:bg-white/10' : 'text-neutral-600 dark:text-white/60 hover:text-neutral-900 dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10'
            }`}
            title="歌词"
          >
            <Quote className="w-4 h-4" />
          </button>

          {/* Queue Drawer Button */}
          <button
            onClick={() => setIsQueueDrawerOpen(!isQueueDrawerOpen)}
            className={`p-2 rounded-full transition-colors ${
              isQueueDrawerOpen ? 'text-pink-500 bg-pink-500/10 dark:text-pink-400 dark:bg-white/10' : 'text-neutral-600 dark:text-white/60 hover:text-neutral-900 dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10'
            }`}
            title="待播队列"
          >
            <ListMusic className="w-4 h-4" />
          </button>

          {/* Desktop Floating Lyrics Button */}
          <button
            onClick={toggleDesktopLyrics}
            className={`p-2 rounded-full transition-colors ${
              isDesktopLyricsOpen ? 'text-pink-500 bg-pink-500/10 dark:text-pink-400 dark:bg-white/10' : 'text-neutral-600 dark:text-white/60 hover:text-neutral-900 dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10'
            }`}
            title={isDesktopLyricsOpen ? '关闭桌面歌词' : '开启桌面悬浮歌词'}
          >
            <span className="text-[11px] font-bold px-0.5 leading-none select-none">词</span>
          </button>

          {/* Mini Mode Button */}
          <button
            onClick={toggleMiniMode}
            className="p-2 rounded-full text-neutral-600 dark:text-white/60 hover:text-neutral-900 dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/10 transition-colors"
            title="迷你模式"
          >
            <PictureInPicture2 className="w-4 h-4" />
          </button>

          {/* Volume Slider */}
          <div className="flex items-center gap-2 w-28 group">
            <button
              onClick={toggleMute}
              className="text-neutral-600 dark:text-white/60 hover:text-neutral-900 dark:hover:text-white transition-colors"
            >
              {isMuted || volume === 0 ? (
                <VolumeX className="w-4 h-4 text-neutral-400 dark:text-white/40" />
              ) : (
                <Volume2 className="w-4 h-4" />
              )}
            </button>
            <input
              type="range"
              min="0"
              max="1"
              step="0.01"
              value={isMuted ? 0 : volume}
              onChange={(e) => setVolume(parseFloat(e.target.value))}
              className="w-full h-1 bg-black/10 dark:bg-white/20 rounded-lg appearance-none cursor-pointer accent-neutral-800 dark:accent-white"
            />
          </div>
        </div>
      </div>

      {contextMenuData && (
        <ContextMenu
          song={currentSong}
          x={contextMenuData.x}
          y={contextMenuData.y}
          onClose={() => setContextMenuData(null)}
        />
      )}
    </div>
  );
};
