import React, { useEffect, useRef, useState } from 'react';
import { Song, Playlist } from '../../types';
import { useMusic } from '../../context/MusicContext';
import {
  Play,
  ListPlus,
  Radio,
  PlusSquare,
  Star,
  Folder,
  Trash2,
  ChevronRight,
  Plus,
} from 'lucide-react';

export interface ContextMenuProps {
  song: Song;
  x: number;
  y: number;
  onClose: () => void;
  isInQueue?: boolean;
  onRemoveFromQueue?: () => void;
  onRequestNewPlaylist?: () => void;
}

export const ContextMenu: React.FC<ContextMenuProps> = ({
  song,
  x,
  y,
  onClose,
  isInQueue,
  onRemoveFromQueue,
  onRequestNewPlaylist,
}) => {
  const {
    playSong,
    playNext,
    addToQueue,
    playlists,
    addSongToPlaylist,
    favorites,
    toggleFavorite,
  } = useMusic();

  const menuRef = useRef<HTMLDivElement>(null);
  const [showPlaylistSubmenu, setShowPlaylistSubmenu] = useState(false);
  const [adjustedPos, setAdjustedPos] = useState({ left: x, top: y });

  const isFav = favorites.has(song.id);
  const customPlaylists = playlists.filter((pl) => !pl.isSmart);

  // Clamp menu inside window boundaries
  useEffect(() => {
    if (menuRef.current) {
      const rect = menuRef.current.getBoundingClientRect();
      let left = x;
      let top = y;

      if (left + rect.width > window.innerWidth - 10) {
        left = Math.max(10, window.innerWidth - rect.width - 15);
      }
      if (top + rect.height > window.innerHeight - 10) {
        top = Math.max(10, window.innerHeight - rect.height - 15);
      }
      setAdjustedPos({ left, top });
    }
  }, [x, y]);

  // Click outside or Escape to close
  useEffect(() => {
    const handleOutsideClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };

    window.addEventListener('mousedown', handleOutsideClick);
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('mousedown', handleOutsideClick);
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [onClose]);

  const handleShowInFolder = () => {
    if (song.path && window.electronAPI?.showItemInFolder) {
      window.electronAPI.showItemInFolder(song.path);
    }
    onClose();
  };

  return (
    <div
      ref={menuRef}
      style={{ left: adjustedPos.left, top: adjustedPos.top }}
      className="fixed z-[9999] min-w-[210px] p-1.5 rounded-2xl bg-[#1c1c24]/95 backdrop-blur-3xl border border-white/15 shadow-[0_15px_40px_rgba(0,0,0,0.6)] text-xs text-white/90 select-none animate-in fade-in zoom-in-95 duration-150"
    >
      {/* Song Header Preview */}
      <div className="flex items-center gap-2.5 px-2.5 py-2 mb-1 border-b border-white/10">
        <div className="w-8 h-8 rounded-lg overflow-hidden shrink-0 bg-white/10 border border-white/10">
          {song.coverUrl ? (
            <img src={song.coverUrl} alt="" className="w-full h-full object-cover" />
          ) : (
            <div className="w-full h-full bg-pink-500/30 flex items-center justify-center text-[10px] text-white">
              ♪
            </div>
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="font-semibold text-white truncate text-[11px]">{song.title}</div>
          <div className="text-[10px] text-white/50 truncate">{song.artist}</div>
        </div>
      </div>

      {/* 1. Play Now */}
      <button
        onClick={() => {
          playSong(song);
          onClose();
        }}
        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-left hover:bg-white/15 text-white/90 hover:text-white transition-colors cursor-pointer"
      >
        <Play className="w-3.5 h-3.5 fill-current text-pink-400" />
        <span className="font-medium">立即播放</span>
      </button>

      {/* 2. Play Next (插播) */}
      <button
        onClick={() => {
          playNext(song);
          onClose();
        }}
        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-left hover:bg-white/15 text-white/90 hover:text-white transition-colors cursor-pointer"
      >
        <Radio className="w-3.5 h-3.5 text-sky-400" />
        <span className="font-medium">下一首播放 (插播)</span>
      </button>

      {/* 3. Add to Queue */}
      <button
        onClick={() => {
          addToQueue(song);
          onClose();
        }}
        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-left hover:bg-white/15 text-white/90 hover:text-white transition-colors cursor-pointer"
      >
        <ListPlus className="w-3.5 h-3.5 text-emerald-400" />
        <span className="font-medium">添加到待播清单</span>
      </button>

      {/* 4. Add to Playlist (Cascading Submenu) */}
      <div
        className="relative"
        onMouseEnter={() => setShowPlaylistSubmenu(true)}
        onMouseLeave={() => setShowPlaylistSubmenu(false)}
      >
        <button className="w-full flex items-center justify-between px-3 py-2 rounded-xl text-left hover:bg-white/15 text-white/90 hover:text-white transition-colors cursor-pointer">
          <div className="flex items-center gap-2.5">
            <PlusSquare className="w-3.5 h-3.5 text-purple-400" />
            <span className="font-medium">添加到歌单</span>
          </div>
          <ChevronRight className="w-3.5 h-3.5 text-white/40" />
        </button>

        {showPlaylistSubmenu && (
          <div className="absolute left-full -top-1 ml-1 min-w-[180px] p-1.5 rounded-2xl bg-[#1c1c24]/95 backdrop-blur-3xl border border-white/15 shadow-[0_15px_40px_rgba(0,0,0,0.6)] text-xs text-white/90 animate-in fade-in duration-100">
            <button
              onClick={() => {
                onRequestNewPlaylist?.();
                onClose();
              }}
              className="w-full flex items-center gap-2 px-3 py-2 rounded-xl text-left hover:bg-pink-500/20 text-pink-400 hover:text-pink-300 font-medium transition-colors cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>新建歌单...</span>
            </button>

            {customPlaylists.length > 0 && <div className="my-1 border-t border-white/10" />}

            {customPlaylists.map((pl) => {
              const alreadyIn = pl.songIds.includes(song.id);
              return (
                <button
                  key={pl.id}
                  onClick={() => {
                    addSongToPlaylist(pl.id, song.id);
                    onClose();
                  }}
                  className="w-full flex items-center justify-between px-3 py-2 rounded-xl text-left hover:bg-white/15 text-white/80 hover:text-white transition-colors cursor-pointer"
                >
                  <span className="truncate">{pl.name}</span>
                  {alreadyIn && <span className="text-[10px] text-white/40 shrink-0 ml-2">已添加</span>}
                </button>
              );
            })}
          </div>
        )}
      </div>

      <div className="my-1 border-t border-white/10" />

      {/* 5. Toggle Favorite */}
      <button
        onClick={() => {
          toggleFavorite(song.id);
          onClose();
        }}
        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-left hover:bg-white/15 text-white/90 hover:text-white transition-colors cursor-pointer"
      >
        <Star className={`w-3.5 h-3.5 ${isFav ? 'text-yellow-400 fill-yellow-400' : 'text-white/60'}`} />
        <span className="font-medium">{isFav ? '取消喜爱' : '添加到喜爱'}</span>
      </button>

      {/* 6. Show in Explorer */}
      {song.path && (
        <button
          onClick={handleShowInFolder}
          className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-left hover:bg-white/15 text-white/90 hover:text-white transition-colors cursor-pointer"
        >
          <Folder className="w-3.5 h-3.5 text-amber-400" />
          <span className="font-medium">在资源管理器中显示</span>
        </button>
      )}

      {/* 7. Remove from queue if applicable */}
      {isInQueue && onRemoveFromQueue && (
        <>
          <div className="my-1 border-t border-white/10" />
          <button
            onClick={() => {
              onRemoveFromQueue();
              onClose();
            }}
            className="w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-left hover:bg-red-500/20 text-red-400 hover:text-red-300 transition-colors cursor-pointer"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span className="font-medium">从待播清单移除</span>
          </button>
        </>
      )}
    </div>
  );
};
