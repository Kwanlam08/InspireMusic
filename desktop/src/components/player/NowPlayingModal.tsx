import React, { useState, useEffect } from 'react';
import { useMusic } from '../../context/MusicContext';
import { FluidMeshGradient } from '../common/FluidMeshGradient';
import { DynamicLyricsView } from '../lyrics/DynamicLyricsView';
import { ContextMenu } from '../common/ContextMenu';
import {
  ChevronDown,
  Play,
  Pause,
  SkipBack,
  SkipForward,
  Star,
  Volume2,
  VolumeX,
  Volume1,
  Quote,
  ListMusic,
  Shuffle,
  Repeat,
  Repeat1,
  Minus,
  Square,
  Copy,
  X,
  Maximize2,
  Minimize2,
  GripVertical,
} from 'lucide-react';

function formatTime(seconds: number): string {
  if (isNaN(seconds) || seconds < 0) return '0:00';
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
}

export const NowPlayingModal: React.FC = () => {
  const {
    currentSong,
    isPlaying,
    currentTime,
    duration,
    volume,
    isMuted,
    playMode,
    favorites,
    isNowPlayingOpen,
    setIsNowPlayingOpen,
    togglePlay,
    nextTrack,
    prevTrack,
    seek,
    setVolume,
    toggleMute,
    cyclePlayMode,
    toggleFavorite,
    queue,
    playSong,
    removeFromQueue,
    reorderQueue,
  } = useMusic();

  const [rightTab, setRightTab] = useState<'lyrics' | 'queue' | 'closed'>('lyrics');
  const [isMaximized, setIsMaximized] = useState(false);
  const [isFullScreen, setIsFullScreen] = useState(false);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);
  const [contextMenu, setContextMenu] = useState<{
    song: any;
    x: number;
    y: number;
    index: number;
  } | null>(null);

  // Fullscreen IPC listener
  useEffect(() => {
    if (window.electronAPI?.isFullScreen) {
      window.electronAPI.isFullScreen().then(setIsFullScreen);
    }
    const unsub = window.electronAPI?.onFullScreenChange?.((fs) => {
      setIsFullScreen(fs);
    });
    return () => unsub?.();
  }, []);

  const handleToggleFullScreen = async () => {
    if (window.electronAPI?.setFullScreen) {
      const next = await window.electronAPI.setFullScreen();
      setIsFullScreen(next);
    }
  };

  // Keyboard shortcuts: Esc to exit fullscreen or collapse modal; F11 to toggle fullscreen
  useEffect(() => {
    if (!isNowPlayingOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'F11') {
        e.preventDefault();
        handleToggleFullScreen();
      } else if (e.key === 'Escape') {
        if (isFullScreen) {
          window.electronAPI?.setFullScreen?.(false);
          setIsFullScreen(false);
        } else {
          setIsNowPlayingOpen(false);
        }
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isNowPlayingOpen, isFullScreen, setIsNowPlayingOpen]);

  if (!isNowPlayingOpen || !currentSong) return null;

  const isFav = favorites.has(currentSong.id);
  const remainingTime = Math.max(0, duration - currentTime);
  const progressPercent = duration > 0 ? (currentTime / duration) * 100 : 0;

  // Window control helpers
  const handleMinimize = () => window.electronAPI?.minimize();
  const handleMaximize = async () => {
    if (window.electronAPI) {
      const state = await window.electronAPI.maximize();
      setIsMaximized(state);
    }
  };
  const handleClose = () => window.electronAPI?.close();

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-[#0b0a10] text-white overflow-hidden animate-in fade-in duration-300 select-none">
      {/* Colorful Fluid Mesh Gradient Layer */}
      <FluidMeshGradient coverUrl={currentSong.coverUrl} intensity={0.9} isModal={true} />

      {/* Top Header Bar */}
      <div className="relative z-30 h-14 w-full flex items-center justify-between px-6 select-none bg-black/10 backdrop-blur-md border-b border-white/[0.05]">
        {/* Left: Collapse Down Arrow Button */}
        <div
          className="flex items-center gap-3"
          style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}
        >
          <button
            onClick={() => setIsNowPlayingOpen(false)}
            className="w-10 h-10 rounded-full bg-white/[0.12] hover:bg-white/[0.25] active:scale-90 border border-white/[0.18] flex items-center justify-center transition-all shadow-md cursor-pointer"
            title="收起播放页 (Esc)"
          >
            <ChevronDown className="w-6 h-6 text-white" />
          </button>
          <span className="text-xs font-semibold text-white/60 tracking-wider uppercase hidden sm:inline-block">
            正在播放
          </span>
        </div>

        {/* Center: Draggable area */}
        <div
          className="flex-1 h-full cursor-default"
          style={{ WebkitAppRegion: 'drag' } as React.CSSProperties}
        />

        {/* Right: Window Controls */}
        <div
          className="flex items-center h-full -mr-6"
          style={{ WebkitAppRegion: 'no-drag' } as React.CSSProperties}
        >
          <button
            onClick={handleToggleFullScreen}
            className="h-14 w-12 flex items-center justify-center hover:bg-white/10 text-white/70 hover:text-white transition-colors"
            title={isFullScreen ? '退出全屏 (F11 / Esc)' : '进入全屏 (F11)'}
          >
            {isFullScreen ? (
              <Minimize2 className="w-3.5 h-3.5" />
            ) : (
              <Maximize2 className="w-3.5 h-3.5" />
            )}
          </button>
          <button
            onClick={handleMinimize}
            className="h-14 w-12 flex items-center justify-center hover:bg-white/10 text-white/70 hover:text-white transition-colors"
            title="最小化"
          >
            <Minus className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={handleMaximize}
            className="h-14 w-12 flex items-center justify-center hover:bg-white/10 text-white/70 hover:text-white transition-colors"
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
            className="h-14 w-12 flex items-center justify-center hover:bg-red-500/80 text-white/70 hover:text-white transition-colors"
            title="关闭"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Main iPad-Style Dual-Column Split Screen (or Centered Single-Column if closed) */}
      <div
        className={`relative z-20 flex-1 flex flex-col md:flex-row overflow-hidden max-w-7xl mx-auto w-full px-6 lg:px-12 py-4 gap-8 lg:gap-14 ${
          rightTab === 'closed' ? 'justify-center items-center' : ''
        }`}
      >
        {/* ================= LEFT COLUMN: Cover Artwork + Playback Controls ================= */}
        <div
          className={`w-full flex flex-col items-center justify-center h-full py-4 space-y-6 transition-all duration-500 ${
            rightTab === 'closed' ? 'max-w-xl mx-auto' : 'md:w-1/2 max-w-lg mx-auto md:max-w-none'
          }`}
        >
          
          {/* Big Album Art */}
          <div className="relative flex items-center justify-center w-full my-auto">
            <div
              className={`
                relative rounded-3xl overflow-hidden
                border border-white/15
                shadow-[0_25px_60px_rgba(0,0,0,0.55)]
                transition-all duration-500 ease-out
                ${
                  rightTab === 'closed'
                    ? 'w-72 h-72 sm:w-80 sm:h-80 md:w-96 md:h-96 lg:w-[420px] lg:h-[420px]'
                    : 'w-60 h-60 sm:w-72 sm:h-72 lg:w-80 lg:h-80 xl:w-92 xl:h-92'
                }
                ${
                  isPlaying
                    ? 'scale-100'
                    : 'scale-[0.93] opacity-80'
                }
              `}
            >
              {currentSong.coverUrl ? (
                <img
                  src={currentSong.coverUrl}
                  alt={currentSong.title}
                  className="w-full h-full object-cover select-none"
                />
              ) : (
                <div className="w-full h-full bg-gradient-to-tr from-pink-500 via-purple-600 to-indigo-600 flex items-center justify-center">
                  <span className="text-white text-6xl font-bold">♪</span>
                </div>
              )}
              {/* Glossy sheen reflection */}
              <div className="pointer-events-none absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.08] to-transparent opacity-60" />
            </div>
          </div>

          {/* Song Metadata + Favorite */}
          <div className="w-full px-2">
            <div className="flex items-center justify-between gap-4">
              <div className="min-w-0 flex-1">
                <h1 className="text-2xl lg:text-3xl font-bold tracking-tight text-white line-clamp-1">
                  {currentSong.title}
                </h1>
                <p className="text-base lg:text-lg font-medium text-white/60 mt-1 line-clamp-1">
                  {currentSong.artist}
                </p>
                <p className="text-xs text-white/40 mt-0.5 line-clamp-1">
                  {currentSong.album} {currentSong.year ? `· ${currentSong.year}` : ''}
                </p>
              </div>

              <button
                onClick={() => toggleFavorite(currentSong.id)}
                className={`p-3 rounded-full bg-white/[0.06] hover:bg-white/[0.14] border border-white/10 transition-all ${
                  isFav ? 'text-yellow-400 fill-yellow-400' : 'text-white/60 hover:text-white'
                }`}
                title={isFav ? '已收藏' : '添加到喜爱'}
              >
                <Star className={`w-5 h-5 ${isFav ? 'fill-current' : ''}`} />
              </button>
            </div>

            {/* Scrubber Progress Bar */}
            <div className="mt-5 space-y-2">
              <div
                className="relative h-2 w-full flex items-center cursor-pointer group"
                onClick={(e) => {
                  const rect = e.currentTarget.getBoundingClientRect();
                  const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
                  seek(ratio * duration);
                }}
              >
                <div className="w-full h-1 bg-white/20 rounded-full overflow-hidden group-hover:h-1.5 transition-all">
                  <div
                    className="h-full bg-white rounded-full transition-all duration-100"
                    style={{ width: `${progressPercent}%` }}
                  />
                </div>
                <div
                  className="absolute w-3.5 h-3.5 bg-white rounded-full shadow-lg transform -translate-x-1/2 pointer-events-none opacity-0 group-hover:opacity-100 transition-opacity"
                  style={{ left: `${progressPercent}%` }}
                />
              </div>

              <div className="flex justify-between text-xs font-semibold text-white/40 tabular-nums">
                <span>{formatTime(currentTime)}</span>
                <span>-{formatTime(remainingTime)}</span>
              </div>
            </div>

            {/* Main Playback Control Buttons */}
            <div className="flex items-center justify-between px-2 sm:px-4 mt-4">
              <button
                onClick={cyclePlayMode}
                className="p-2.5 text-white/40 hover:text-white hover:bg-white/10 rounded-full transition-colors"
                title={`播放模式: ${playMode}`}
              >
                {playMode === 'shuffle' && <Shuffle className="w-5 h-5 text-pink-400" />}
                {playMode === 'loop-one' && <Repeat1 className="w-5 h-5 text-pink-400" />}
                {playMode === 'loop' && <Repeat className="w-5 h-5 text-pink-400" />}
                {playMode === 'sequence' && <Repeat className="w-5 h-5 text-white/40" />}
              </button>

              <button
                onClick={prevTrack}
                className="p-2.5 text-white/80 hover:text-white active:scale-90 transition-all rounded-full"
              >
                <SkipBack className="w-7 h-7 fill-current" />
              </button>

              {/* Central Play/Pause button with tactile spring feedback */}
              <button
                onClick={togglePlay}
                className="w-16 h-16 rounded-full bg-white text-black flex items-center justify-center shadow-[0_10px_35px_rgba(255,255,255,0.3)] hover:scale-105 active:scale-95 transition-all duration-200"
              >
                {isPlaying ? (
                  <Pause className="w-7 h-7 fill-black" />
                ) : (
                  <Play className="w-7 h-7 fill-black translate-x-0.5" />
                )}
              </button>

              <button
                onClick={nextTrack}
                className="p-2.5 text-white/80 hover:text-white active:scale-90 transition-all rounded-full"
              >
                <SkipForward className="w-7 h-7 fill-current" />
              </button>

              <div className="flex items-center gap-1">
                {/* Lyrics toggle button */}
                <button
                  onClick={() => setRightTab(prev => (prev === 'lyrics' ? 'closed' : 'lyrics'))}
                  className={`p-2.5 rounded-full transition-all cursor-pointer ${
                    rightTab === 'lyrics'
                      ? 'bg-white/20 text-pink-400 shadow-sm'
                      : 'text-white/40 hover:text-white hover:bg-white/10'
                  }`}
                  title={rightTab === 'lyrics' ? '关闭歌词' : '显示歌词'}
                >
                  <Quote className="w-5 h-5" />
                </button>

                {/* Queue toggle button */}
                <button
                  onClick={() => setRightTab(prev => (prev === 'queue' ? 'closed' : 'queue'))}
                  className={`p-2.5 rounded-full transition-all cursor-pointer ${
                    rightTab === 'queue'
                      ? 'bg-white/20 text-pink-400 shadow-sm'
                      : 'text-white/40 hover:text-white hover:bg-white/10'
                  }`}
                  title={rightTab === 'queue' ? '关闭待播清单' : '显示待播清单'}
                >
                  <ListMusic className="w-5 h-5" />
                </button>
              </div>
            </div>

            {/* Volume Slider Bar */}
            <div className="flex items-center gap-3 px-6 mt-4">
              <button onClick={toggleMute} className="text-white/40 hover:text-white transition-colors">
                {isMuted || volume === 0 ? (
                  <VolumeX className="w-4 h-4" />
                ) : volume < 0.5 ? (
                  <Volume1 className="w-4 h-4" />
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
                className="w-full h-1 bg-white/20 rounded-lg appearance-none cursor-pointer accent-white"
              />
            </div>
          </div>
        </div>

        {/* ================= RIGHT COLUMN: Lyrics OR Queue (iPad Style Switcher) ================= */}
        {rightTab !== 'closed' && (
          <div className="w-full md:w-1/2 h-full flex flex-col rounded-3xl bg-white/[0.04] backdrop-blur-2xl border border-white/[0.09] shadow-[0_15px_50px_rgba(0,0,0,0.3)] overflow-hidden animate-in fade-in slide-in-from-right-4 duration-300">
            
            {/* Segmented Pill Tabs Header: [ 歌词 | 播放队列 ] + Close Button */}
            <div className="p-4 border-b border-white/[0.06] flex items-center justify-between">
              <div className="inline-flex p-1 rounded-2xl bg-black/20 border border-white/10 backdrop-blur-md">
                <button
                  onClick={() => setRightTab('lyrics')}
                  className={`
                    flex items-center gap-2 px-5 py-2 rounded-xl text-xs font-semibold transition-all duration-300
                    ${
                      rightTab === 'lyrics'
                        ? 'bg-white text-black shadow-md'
                        : 'text-white/60 hover:text-white'
                    }
                  `}
                >
                  <Quote className="w-3.5 h-3.5" />
                  <span>歌词</span>
                </button>

                <button
                  onClick={() => setRightTab('queue')}
                  className={`
                    flex items-center gap-2 px-5 py-2 rounded-xl text-xs font-semibold transition-all duration-300
                    ${
                      rightTab === 'queue'
                        ? 'bg-white text-black shadow-md'
                        : 'text-white/60 hover:text-white'
                    }
                  `}
                >
                  <ListMusic className="w-3.5 h-3.5" />
                  <span>待播清单</span>
                  <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${rightTab === 'queue' ? 'bg-black/15 text-black' : 'bg-white/10 text-white/70'}`}>
                    {queue.length}
                  </span>
                </button>
              </div>

              {/* Close right panel button */}
              <button
                onClick={() => setRightTab('closed')}
                className="w-8 h-8 rounded-full flex items-center justify-center text-white/50 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
                title="关闭面板 (切换为纯封面)"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Dynamic Content Panel */}
            <div className="flex-1 overflow-hidden relative">
              {rightTab === 'lyrics' ? (
                <div className="h-full w-full animate-in fade-in duration-300">
                  <DynamicLyricsView className="h-full" fontSize={30} />
                </div>
              ) : (
                /* Queue Panel */
                <div className="h-full overflow-y-auto p-4 space-y-2.5 animate-in fade-in duration-300">
                  <div className="text-xs font-semibold text-white/40 px-2 py-1 uppercase tracking-wider">
                    正在播放
                  </div>
                  
                  {/* Currently playing item card */}
                  <div className="flex items-center gap-3.5 p-3 rounded-2xl bg-white/15 border border-white/20 shadow-sm">
                    <div className="w-12 h-12 rounded-xl overflow-hidden shrink-0 border border-white/10">
                      {currentSong.coverUrl ? (
                        <img src={currentSong.coverUrl} alt="" className="w-full h-full object-cover" />
                      ) : (
                        <div className="w-full h-full bg-pink-500/40" />
                      )}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="font-bold text-sm text-pink-400 truncate">
                        {currentSong.title}
                      </div>
                      <div className="text-xs text-white/60 truncate mt-0.5">
                        {currentSong.artist}
                      </div>
                    </div>
                    <div className="w-2.5 h-2.5 rounded-full bg-pink-500 animate-ping mr-2" />
                  </div>

                  <div className="flex items-center justify-between text-xs font-semibold text-white/40 px-2 pt-4 pb-1 uppercase tracking-wider">
                    <span>接下来播放 ({Math.max(0, queue.length - 1)})</span>
                    <span className="text-[10px] text-white/30 lowercase font-normal">可拖拽排序 / 右键管理</span>
                  </div>

                  {queue.map((song, queueIdx) => {
                    if (song.id === currentSong.id) return null;
                    const isDragOver = dragOverIndex === queueIdx;

                    return (
                      <div
                        key={`${song.id}_${queueIdx}`}
                        draggable={true}
                        onDragStart={(e) => {
                          e.dataTransfer.setData('text/plain', String(queueIdx));
                          e.dataTransfer.effectAllowed = 'move';
                        }}
                        onDragOver={(e) => {
                          e.preventDefault();
                          e.dataTransfer.dropEffect = 'move';
                          setDragOverIndex(queueIdx);
                        }}
                        onDragLeave={() => setDragOverIndex(null)}
                        onDrop={(e) => {
                          e.preventDefault();
                          setDragOverIndex(null);
                          const from = parseInt(e.dataTransfer.getData('text/plain'), 10);
                          if (!isNaN(from) && from !== queueIdx) {
                            reorderQueue(from, queueIdx);
                          }
                        }}
                        onContextMenu={(e) => {
                          e.preventDefault();
                          setContextMenu({ song, x: e.clientX, y: e.clientY, index: queueIdx });
                        }}
                        onClick={() => playSong(song)}
                        className={`
                          group flex items-center gap-3 p-2.5 rounded-2xl cursor-pointer transition-all duration-150 border
                          ${
                            isDragOver
                              ? 'bg-pink-500/25 border-pink-500 shadow-md scale-[1.01]'
                              : 'hover:bg-white/[0.08] border-transparent hover:border-white/10'
                          }
                        `}
                      >
                        {/* Drag Handle */}
                        <div
                          className="text-white/20 group-hover:text-white/60 hover:text-white p-0.5 cursor-grab active:cursor-grabbing transition-colors shrink-0"
                          title="按住上下拖拽调整顺序"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <GripVertical className="w-4 h-4" />
                        </div>

                        {/* Song Cover */}
                        <div className="w-10 h-10 rounded-xl overflow-hidden shrink-0 border border-white/10 relative">
                          {song.coverUrl ? (
                            <img src={song.coverUrl} alt="" className="w-full h-full object-cover" />
                          ) : (
                            <div className="w-full h-full bg-indigo-600/30" />
                          )}
                          <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity">
                            <Play className="w-3.5 h-3.5 text-white fill-white translate-x-0.5" />
                          </div>
                        </div>

                        {/* Song Title & Artist */}
                        <div className="min-w-0 flex-1">
                          <div className="font-semibold text-sm text-white/90 group-hover:text-pink-400 truncate transition-colors">
                            {song.title}
                          </div>
                          <div className="text-xs text-white/40 truncate">
                            {song.artist} · {song.album}
                          </div>
                        </div>

                        {/* Duration */}
                        <span className="text-xs text-white/30 tabular-nums font-medium pr-1">
                          {formatTime(song.duration)}
                        </span>

                        {/* Remove from queue button */}
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            removeFromQueue(song.id, queueIdx);
                          }}
                          className="p-1 rounded-lg text-white/20 group-hover:text-white/50 hover:text-red-400 hover:bg-white/10 transition-colors"
                          title="从队列移除"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        )}

      </div>

      {/* Global Right Click Context Menu */}
      {contextMenu && (
        <ContextMenu
          song={contextMenu.song}
          x={contextMenu.x}
          y={contextMenu.y}
          isInQueue={true}
          onRemoveFromQueue={() => {
            removeFromQueue(contextMenu.song.id, contextMenu.index);
            setContextMenu(null);
          }}
          onClose={() => setContextMenu(null)}
        />
      )}
    </div>
  );
};
