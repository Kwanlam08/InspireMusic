import React, { useState, useEffect, useRef } from 'react';
import { useMusic } from '../../context/MusicContext';
import { ListPlus, X } from 'lucide-react';

interface CreatePlaylistModalProps {
  isOpen: boolean;
  onClose: () => void;
  onCreated?: (playlistId: string) => void;
  initialSongIdToAdd?: string;
}

export const CreatePlaylistModal: React.FC<CreatePlaylistModalProps> = ({
  isOpen,
  onClose,
  onCreated,
  initialSongIdToAdd,
}) => {
  const { createPlaylist, addSongToPlaylist } = useMusic();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (isOpen) {
      setName('');
      setDescription('');
      setTimeout(() => inputRef.current?.focus(), 50);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    const newPl = createPlaylist(name.trim(), description.trim());
    if (initialSongIdToAdd) {
      addSongToPlaylist(newPl.id, initialSongIdToAdd);
    }
    onCreated?.(newPl.id);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-[10000] flex items-center justify-center bg-black/60 backdrop-blur-md p-4 animate-in fade-in duration-200">
      <div className="relative w-full max-w-md p-6 rounded-3xl bg-[#1c1c24]/95 text-white border border-white/15 shadow-[0_25px_60px_rgba(0,0,0,0.7)] space-y-5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-gradient-to-tr from-pink-500 to-purple-600 flex items-center justify-center text-white shadow-md">
              <ListPlus className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-base">新建播放列表</h3>
              <p className="text-xs text-white/50">为你喜爱的歌曲创建专属歌单</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-full text-white/50 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-white/70">歌单名称 *</label>
            <input
              ref={inputRef}
              type="text"
              required
              placeholder="例如：深夜专注、动感跑步"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full px-4 py-2.5 rounded-2xl bg-white/10 border border-white/15 focus:border-pink-500 focus:outline-none text-sm text-white placeholder-white/30 transition-colors"
            />
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-white/70">描述 (选填)</label>
            <textarea
              rows={2}
              placeholder="简要描述这个歌单的氛围..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-4 py-2.5 rounded-2xl bg-white/10 border border-white/15 focus:border-pink-500 focus:outline-none text-xs text-white placeholder-white/30 transition-colors resize-none"
            />
          </div>

          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-5 py-2 rounded-2xl bg-white/10 hover:bg-white/15 text-xs font-semibold text-white/80 transition-colors cursor-pointer"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={!name.trim()}
              className="px-6 py-2 rounded-2xl bg-pink-500 hover:bg-pink-600 disabled:opacity-50 disabled:cursor-not-allowed text-xs font-semibold text-white shadow-lg shadow-pink-500/25 transition-all active:scale-95 cursor-pointer"
            >
              立即创建
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
