import React, { useState } from 'react';
import { useMusic } from '../context/MusicContext';
import { Song, Album, Playlist } from '../types';
import { ContextMenu } from '../components/common/ContextMenu';
import { CreatePlaylistModal } from '../components/common/CreatePlaylistModal';
import {
  ListMusic,
  User,
  Disc,
  Music2,
  Star,
  Search,
  Play,
  Clock,
  ChevronRight,
  FolderOpen,
  Sparkles,
  Wrench,
  CheckCircle2,
  AlertTriangle,
  FileQuestion,
  Plus,
  Trash2,
  X,
} from 'lucide-react';

function formatDuration(sec: number): string {
  if (isNaN(sec) || sec <= 0) return '0:00';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

type LibrarySubView = 'all' | 'songs' | 'albums' | 'artists' | 'playlists' | 'favorites' | 'organizer';

export const LibraryView: React.FC = () => {
  const {
    songs,
    albums,
    artists,
    playlists,
    smartPlaylists,
    favorites,
    playSong,
    currentSong,
    isPlaying,
    toggleFavorite,
    scanFolder,
    deletePlaylist,
    removeSongFromPlaylist,
  } = useMusic();

  const [subView, setSubView] = useState<LibrarySubView>('all');
  const [selectedPlaylist, setSelectedPlaylist] = useState<Playlist | null>(null);
  const [selectedAlbum, setSelectedAlbum] = useState<Album | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [displayLimit, setDisplayLimit] = useState(80);
  const [albumDisplayLimit, setAlbumDisplayLimit] = useState(60);
  const [isCreatePlaylistOpen, setIsCreatePlaylistOpen] = useState(false);
  const [contextMenuData, setContextMenuData] = useState<{ song: Song; x: number; y: number } | null>(null);

  // Filter songs based on search
  const filteredSongs = React.useMemo(() => {
    if (!searchQuery.trim()) return songs;
    const q = searchQuery.toLowerCase();
    return songs.filter((s) =>
      s.title.toLowerCase().includes(q) ||
      s.artist.toLowerCase().includes(q) ||
      s.album.toLowerCase().includes(q)
    );
  }, [songs, searchQuery]);

  // Filter albums based on search
  const filteredAlbums = React.useMemo(() => {
    if (!searchQuery.trim()) return albums;
    const q = searchQuery.toLowerCase();
    return albums.filter((a) =>
      a.title.toLowerCase().includes(q) ||
      a.artist.toLowerCase().includes(q)
    );
  }, [albums, searchQuery]);

  const categoryCards = [
    {
      id: 'playlists' as LibrarySubView,
      title: '播放列表',
      icon: ListMusic,
      count: playlists.length + smartPlaylists.length,
      bg: 'bg-emerald-500/15 text-emerald-500 dark:text-emerald-400',
    },
    {
      id: 'artists' as LibrarySubView,
      title: '艺人',
      icon: User,
      count: artists.length,
      bg: 'bg-sky-500/15 text-sky-500 dark:text-sky-400',
    },
    {
      id: 'albums' as LibrarySubView,
      title: '专辑',
      icon: Disc,
      count: albums.length,
      bg: 'bg-indigo-500/15 text-indigo-500 dark:text-indigo-400',
    },
    {
      id: 'songs' as LibrarySubView,
      title: '歌曲',
      icon: Music2,
      count: songs.length,
      bg: 'bg-pink-500/15 text-pink-500 dark:text-pink-400',
    },
    {
      id: 'favorites' as LibrarySubView,
      title: '我的喜爱',
      icon: Star,
      count: favorites.size,
      bg: 'bg-amber-500/15 text-amber-500 dark:text-amber-400',
    },
    {
      id: 'organizer' as LibrarySubView,
      title: '资料整理中心',
      icon: Wrench,
      count: '健康检测',
      bg: 'bg-purple-500/15 text-purple-500 dark:text-purple-400',
    },
  ];

  // Organizer issues computation
  const issues = React.useMemo(() => {
    const missingCover = songs.filter((s) => !s.coverUrl);
    const missingArtist = songs.filter((s) => !s.artist || s.artist === '未知艺人');
    const missingLyrics = songs.filter((s) => !s.lyrics);
    return {
      missingCover,
      missingArtist,
      missingLyrics,
      totalIssues: missingCover.length + missingArtist.length + missingLyrics.length,
    };
  }, [songs]);

  return (
    <div className="flex-1 h-full overflow-y-auto p-8 pb-32 space-y-8 select-none text-neutral-900 dark:text-white transition-colors">
      {/* Header with Title and Search */}
      <div className="flex items-center justify-between">
        <div>
          {selectedAlbum ? (
            <button
              onClick={() => setSelectedAlbum(null)}
              className="text-xs font-semibold text-pink-500 dark:text-pink-400 hover:underline mb-1 flex items-center gap-1 cursor-pointer"
            >
              ← 返回全部专辑
            </button>
          ) : subView !== 'all' ? (
            <button
              onClick={() => {
                setSubView('all');
                setSelectedPlaylist(null);
                setSelectedAlbum(null);
              }}
              className="text-xs font-semibold text-pink-500 dark:text-pink-400 hover:underline mb-1 flex items-center gap-1 cursor-pointer"
            >
              ← 返回资料库主页
            </button>
          ) : null}
          <h1 className="text-3xl font-extrabold tracking-tight text-neutral-900 dark:text-white/95">
            {selectedAlbum
              ? selectedAlbum.title
              : selectedPlaylist
              ? selectedPlaylist.name
              : subView === 'all'
              ? '资料库'
              : subView === 'songs'
              ? '所有歌曲'
              : subView === 'albums'
              ? '全部专辑'
              : subView === 'artists'
              ? '艺人名录'
              : subView === 'playlists'
              ? '播放列表'
              : subView === 'organizer'
              ? '音乐资料整理中心'
              : '我的喜爱'}
          </h1>
          {selectedAlbum ? (
            <p className="text-xs text-neutral-500 dark:text-white/40 mt-1">
              {selectedAlbum.artist} · 共 {selectedAlbum.songs.length} 首歌
            </p>
          ) : selectedPlaylist && selectedPlaylist.description ? (
            <p className="text-xs text-neutral-500 dark:text-white/40 mt-1">
              {selectedPlaylist.description}
            </p>
          ) : null}
        </div>

        <div className="flex items-center gap-3">
          {/* Search Box */}
          <div className="relative">
            <Search className="w-4 h-4 text-neutral-400 dark:text-white/40 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="搜索歌曲、专辑或艺人..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="
                w-64 pl-10 pr-4 py-2 rounded-2xl
                bg-black/5 dark:bg-white/[0.06] hover:bg-black/10 dark:hover:bg-white/[0.09] focus:bg-black/10 dark:focus:bg-white/[0.12]
                border border-black/5 dark:border-white/10 focus:border-black/20 dark:focus:border-white/25 focus:outline-none
                text-xs text-neutral-900 dark:text-white placeholder-neutral-400 dark:placeholder-white/30 transition-all
              "
            />
          </div>

          <button
            onClick={scanFolder}
            className="p-2 rounded-2xl bg-black/5 dark:bg-white/[0.06] hover:bg-black/10 dark:hover:bg-white/[0.12] border border-black/5 dark:border-white/10 text-neutral-700 dark:text-white/80 transition-all"
            title="选择音乐文件夹"
          >
            <FolderOpen className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Responsive Liquid Glass Category Cards (Stretches smoothly with window) */}
      {subView === 'all' && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5 w-full max-w-5xl">
          {categoryCards.map((card) => {
            const Icon = card.icon;
            return (
              <div
                key={card.id}
                onClick={() => setSubView(card.id)}
                className="
                  group relative flex items-center justify-between p-4 px-5 rounded-2xl
                  bg-white/70 dark:bg-white/[0.04] hover:bg-white dark:hover:bg-white/[0.08] active:scale-[0.99]
                  border border-black/[0.06] dark:border-white/[0.08] hover:border-black/15 dark:hover:border-white/[0.18]
                  shadow-[0_4px_20px_rgba(0,0,0,0.04)] dark:shadow-[0_4px_20px_rgba(0,0,0,0.15)]
                  transition-all duration-200 cursor-pointer
                "
              >
                <div className="flex items-center gap-4">
                  <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${card.bg} shadow-inner`}>
                    <Icon className="w-5 h-5" />
                  </div>
                  <span className="font-semibold text-base text-neutral-800 dark:text-white/90 group-hover:text-black dark:group-hover:text-white">
                    {card.title}
                  </span>
                </div>

                <div className="flex items-center gap-2 text-neutral-400 dark:text-white/40 group-hover:text-neutral-800 dark:group-hover:text-white/80 transition-colors">
                  <span className="text-xs font-medium">{card.count}</span>
                  <ChevronRight className="w-4 h-4" />
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Library Organizer View */}
      {subView === 'organizer' && (
        <div className="space-y-6 w-full max-w-5xl">
          <div className="p-6 rounded-3xl bg-white/70 dark:bg-white/[0.04] border border-black/5 dark:border-white/10 shadow-lg space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="w-11 h-11 rounded-2xl bg-purple-500/15 border border-purple-500/25 flex items-center justify-center text-purple-500">
                  <Wrench className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-bold text-base">资料库健康状态报告</h3>
                  <p className="text-xs text-neutral-500 dark:text-white/40">
                    已扫描 {songs.length} 首本地歌曲 · 发现 {issues.totalIssues} 项可优化元数据
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 text-xs font-semibold">
                <CheckCircle2 className="w-3.5 h-3.5" />
                <span>无损整理 · 不修改原音频文件</span>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2">
              <div className="p-4 rounded-2xl bg-black/[0.02] dark:bg-white/[0.03] border border-black/5 dark:border-white/5 space-y-1">
                <span className="text-xs text-neutral-500 dark:text-white/40">缺失封面</span>
                <div className="text-xl font-bold">{issues.missingCover.length} 首</div>
              </div>
              <div className="p-4 rounded-2xl bg-black/[0.02] dark:bg-white/[0.03] border border-black/5 dark:border-white/5 space-y-1">
                <span className="text-xs text-neutral-500 dark:text-white/40">未知艺人</span>
                <div className="text-xl font-bold">{issues.missingArtist.length} 首</div>
              </div>
              <div className="p-4 rounded-2xl bg-black/[0.02] dark:bg-white/[0.03] border border-black/5 dark:border-white/5 space-y-1">
                <span className="text-xs text-neutral-500 dark:text-white/40">暂无同步歌词</span>
                <div className="text-xl font-bold">{issues.missingLyrics.length} 首</div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Playlists View (Custom User Playlists + 5 Android Smart Playlists) */}
      {(subView === 'playlists' || subView === 'all') && !selectedPlaylist && !selectedAlbum && (
        <div className="space-y-6 w-full max-w-5xl animate-in fade-in duration-300">
          {/* Section 1: Custom User Playlists */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <h2 className="text-xl font-bold tracking-tight">自建歌单</h2>
                <span className="text-xs text-neutral-400 dark:text-white/40">
                  {playlists.filter((p) => !p.isSmart).length} 个专属歌单
                </span>
              </div>

              <button
                onClick={() => setIsCreatePlaylistOpen(true)}
                className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-full bg-pink-500/10 hover:bg-pink-500/20 text-pink-600 dark:text-pink-400 text-xs font-semibold transition-all cursor-pointer"
              >
                <Plus className="w-3.5 h-3.5" />
                <span>新建歌单</span>
              </button>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {/* Add New Playlist Shortcut Card */}
              <div
                onClick={() => setIsCreatePlaylistOpen(true)}
                className="group p-4 rounded-2xl border-2 border-dashed border-black/10 dark:border-white/10 hover:border-pink-500/50 flex items-center gap-3.5 cursor-pointer transition-colors"
              >
                <div className="w-11 h-11 rounded-xl bg-pink-500/10 text-pink-500 flex items-center justify-center group-hover:scale-105 transition-transform">
                  <Plus className="w-5 h-5" />
                </div>
                <div>
                  <div className="font-semibold text-sm group-hover:text-pink-500 transition-colors">新建歌单</div>
                  <div className="text-xs text-neutral-400 dark:text-white/40">收集属于你的音乐旋律</div>
                </div>
              </div>

              {playlists
                .filter((pl) => !pl.isSmart)
                .map((pl) => (
                  <div
                    key={pl.id}
                    onClick={() => {
                      setSelectedPlaylist(pl);
                      setSubView('playlists');
                    }}
                    className="
                      group relative p-4 rounded-2xl bg-white/70 dark:bg-white/[0.04] hover:bg-white dark:hover:bg-white/[0.08]
                      border border-black/[0.06] dark:border-white/[0.08] hover:border-black/15 dark:hover:border-white/20
                      shadow-sm hover:shadow-md transition-all cursor-pointer space-y-2
                    "
                  >
                    <div className="flex items-center justify-between">
                      <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-purple-500/20 to-pink-500/20 border border-purple-500/30 flex items-center justify-center text-purple-500">
                        <ListMusic className="w-5 h-5" />
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-semibold text-neutral-400 dark:text-white/40">
                          {pl.songIds.length} 首
                        </span>
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            if (confirm(`确定要删除歌单“${pl.name}”吗？`)) {
                              deletePlaylist(pl.id);
                            }
                          }}
                          className="opacity-0 group-hover:opacity-100 p-1 rounded-md hover:bg-red-500/10 text-neutral-400 hover:text-red-500 transition-all"
                          title="删除歌单"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                    <div>
                      <h3 className="font-bold text-sm text-neutral-900 dark:text-white/95 group-hover:text-pink-500 transition-colors">
                        {pl.name}
                      </h3>
                      <p className="text-xs text-neutral-500 dark:text-white/40 mt-0.5 line-clamp-1">
                        {pl.description || '暂无描述'}
                      </p>
                    </div>
                  </div>
                ))}
            </div>
          </div>

          {/* Section 2: Smart Playlists */}
          <div className="space-y-4 pt-2">
            <div className="flex items-center justify-between">
              <h2 className="text-xl font-bold tracking-tight">智能歌单</h2>
              <span className="text-xs text-neutral-400 dark:text-white/40">Android 原版对齐</span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {smartPlaylists.map((pl) => (
                <div
                  key={pl.id}
                  onClick={() => {
                    setSelectedPlaylist(pl);
                    setSubView('playlists');
                  }}
                  className="
                    group p-4 rounded-2xl bg-white/70 dark:bg-white/[0.04] hover:bg-white dark:hover:bg-white/[0.08]
                    border border-black/[0.06] dark:border-white/[0.08] hover:border-black/15 dark:hover:border-white/20
                    shadow-sm hover:shadow-md transition-all cursor-pointer space-y-2
                  "
                >
                  <div className="flex items-center justify-between">
                    <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-pink-500/20 to-purple-500/20 border border-pink-500/30 flex items-center justify-center text-pink-500">
                      <Sparkles className="w-5 h-5" />
                    </div>
                    <span className="text-xs font-semibold text-neutral-400 dark:text-white/40">
                      {pl.songIds.length} 首
                    </span>
                  </div>
                  <div>
                    <h3 className="font-bold text-sm text-neutral-900 dark:text-white/95 group-hover:text-pink-500 transition-colors">
                      {pl.name}
                    </h3>
                    <p className="text-xs text-neutral-500 dark:text-white/40 mt-0.5 line-clamp-1">
                      {pl.description}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Playlist Detail View (when a playlist is selected) */}
      {selectedPlaylist && !selectedAlbum && (
        <div className="space-y-6 w-full max-w-5xl animate-in fade-in duration-300">
          {/* Playlist Header Banner */}
          <div className="flex flex-col sm:flex-row items-center sm:items-start gap-6 p-6 rounded-3xl bg-white/70 dark:bg-white/[0.03] border border-black/[0.06] dark:border-white/[0.08] shadow-sm">
            <div className="w-40 h-40 rounded-2xl bg-gradient-to-tr from-pink-500 via-purple-600 to-indigo-600 flex items-center justify-center shadow-xl border border-white/20 shrink-0">
              <ListMusic className="w-16 h-16 text-white" />
            </div>

            <div className="flex-1 text-center sm:text-left space-y-2">
              <span className="text-xs font-bold uppercase tracking-wider text-pink-500">
                {selectedPlaylist.isSmart ? '智能歌单' : '自建歌单'}
              </span>
              <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight">{selectedPlaylist.name}</h2>
              {selectedPlaylist.description && (
                <p className="text-sm font-medium text-neutral-600 dark:text-white/70">
                  {selectedPlaylist.description}
                </p>
              )}
              <p className="text-xs text-neutral-400 dark:text-white/40">
                共 {selectedPlaylist.songIds.length} 首歌曲
              </p>

              <div className="flex items-center gap-3 pt-3 justify-center sm:justify-start">
                <button
                  onClick={() => {
                    const plSongs = songs.filter((s) => selectedPlaylist.songIds.includes(s.id));
                    if (plSongs.length > 0) {
                      playSong(plSongs[0], plSongs);
                    }
                  }}
                  className="px-5 py-2 rounded-full bg-pink-500 hover:bg-pink-600 text-white font-semibold text-xs flex items-center gap-2 shadow-lg shadow-pink-500/25 active:scale-95 transition-all cursor-pointer"
                >
                  <Play className="w-3.5 h-3.5 fill-white" />
                  <span>播放全部</span>
                </button>

                {!selectedPlaylist.isSmart && (
                  <button
                    onClick={() => {
                      if (confirm(`确定要删除歌单“${selectedPlaylist.name}”吗？`)) {
                        deletePlaylist(selectedPlaylist.id);
                        setSelectedPlaylist(null);
                      }
                    }}
                    className="px-4 py-2 rounded-full bg-red-500/10 hover:bg-red-500/20 text-red-600 dark:text-red-400 font-semibold text-xs flex items-center gap-1.5 active:scale-95 transition-all cursor-pointer"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    <span>删除歌单</span>
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Recently Added Section (in Library Home view) */}
      {subView === 'all' && !selectedAlbum && albums.length > 0 && (
        <div className="space-y-4 w-full max-w-5xl">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-bold tracking-tight">最近添加</h2>
            {albums.length > 12 && (
              <button
                onClick={() => setSubView('albums')}
                className="text-xs font-semibold text-pink-500 dark:text-pink-400 hover:underline flex items-center gap-1 cursor-pointer"
              >
                查看全部 ({albums.length} 张) →
              </button>
            )}
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
            {albums.slice(0, 12).map((album) => (
              <div
                key={album.id}
                onClick={() => {
                  setSelectedAlbum(album);
                  setSubView('albums');
                }}
                className="group relative cursor-pointer"
              >
                <div className="aspect-square rounded-2xl overflow-hidden bg-black/5 dark:bg-white/[0.05] border border-black/5 dark:border-white/10 shadow-sm relative mb-2.5 transition-transform duration-300 group-hover:-translate-y-1">
                  {album.coverUrl ? (
                    <img
                      src={album.coverUrl}
                      alt={album.title}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                    />
                  ) : (
                    <div className="w-full h-full bg-gradient-to-tr from-purple-800 to-indigo-700 flex items-center justify-center">
                      <Disc className="w-10 h-10 text-white/40" />
                    </div>
                  )}

                  <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                    <div
                      onClick={(e) => {
                        e.stopPropagation();
                        if (album.songs.length > 0) {
                          playSong(album.songs[0], album.songs);
                        }
                      }}
                      className="w-12 h-12 rounded-full bg-white text-black flex items-center justify-center shadow-xl transform scale-90 group-hover:scale-100 transition-transform cursor-pointer"
                      title="直接播放专辑"
                    >
                      <Play className="w-5 h-5 fill-black translate-x-0.5" />
                    </div>
                  </div>
                </div>

                <h3 className="font-semibold text-sm truncate group-hover:text-pink-500 transition-colors">
                  {album.title}
                </h3>
                <p className="text-xs text-neutral-500 dark:text-white/40 truncate">{album.artist}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Album Detail View (when an album is selected) */}
      {subView === 'albums' && selectedAlbum && (
        <div className="space-y-6 w-full max-w-5xl animate-in fade-in duration-300">
          {/* Album Header Banner */}
          <div className="flex flex-col sm:flex-row items-center sm:items-start gap-6 p-6 rounded-3xl bg-white/70 dark:bg-white/[0.03] border border-black/[0.06] dark:border-white/[0.08] shadow-sm">
            <div className="w-44 h-44 rounded-2xl overflow-hidden shadow-2xl border border-black/10 dark:border-white/10 shrink-0">
              {selectedAlbum.coverUrl ? (
                <img src={selectedAlbum.coverUrl} alt={selectedAlbum.title} className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full bg-gradient-to-tr from-purple-800 to-indigo-700 flex items-center justify-center">
                  <Disc className="w-14 h-14 text-white/40" />
                </div>
              )}
            </div>
            <div className="flex-1 text-center sm:text-left space-y-2">
              <span className="text-xs font-bold uppercase tracking-wider text-pink-500">专辑</span>
              <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight">{selectedAlbum.title}</h2>
              <p className="text-sm font-medium text-neutral-600 dark:text-white/70">{selectedAlbum.artist}</p>
              <p className="text-xs text-neutral-400 dark:text-white/40">
                {selectedAlbum.songs.length} 首歌 {selectedAlbum.year ? `· ${selectedAlbum.year} 年` : ''} · 共{' '}
                {formatDuration(selectedAlbum.songs.reduce((acc, s) => acc + (s.duration || 0), 0))}
              </p>
              <div className="flex items-center gap-3 pt-3 justify-center sm:justify-start">
                <button
                  onClick={() => {
                    if (selectedAlbum.songs.length > 0) {
                      playSong(selectedAlbum.songs[0], selectedAlbum.songs);
                    }
                  }}
                  className="px-5 py-2 rounded-full bg-pink-500 hover:bg-pink-600 text-white font-semibold text-xs flex items-center gap-2 shadow-lg shadow-pink-500/25 active:scale-95 transition-all cursor-pointer"
                >
                  <Play className="w-3.5 h-3.5 fill-white" />
                  <span>播放全部</span>
                </button>
              </div>
            </div>
          </div>

          {/* Album Tracks Table */}
          <div className="rounded-2xl border border-black/[0.06] dark:border-white/[0.08] bg-white/70 dark:bg-white/[0.02] backdrop-blur-xl overflow-hidden shadow-sm">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-black/[0.05] dark:border-white/[0.06] text-neutral-500 dark:text-white/40 uppercase tracking-wider font-semibold">
                <tr>
                  <th className="py-3 pl-4 w-12 text-center">#</th>
                  <th className="py-3 px-4">标题</th>
                  <th className="py-3 px-4 w-16 text-center">
                    <Clock className="w-3.5 h-3.5 mx-auto" />
                  </th>
                  <th className="py-3 pr-4 w-12 text-center"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-black/[0.04] dark:divide-white/[0.04]">
                {selectedAlbum.songs.map((song, idx) => {
                  const isCurrent = currentSong?.id === song.id;
                  const isFav = favorites.has(song.id);

                  return (
                    <tr
                      key={song.id}
                      onClick={() => playSong(song, selectedAlbum.songs)}
                      onContextMenu={(e) => {
                        e.preventDefault();
                        setContextMenuData({ song, x: e.clientX, y: e.clientY });
                      }}
                      className={`
                        group hover:bg-black/5 dark:hover:bg-white/[0.07] cursor-pointer transition-colors
                        ${isCurrent ? 'bg-pink-500/10 text-pink-600 dark:text-pink-400' : 'text-neutral-800 dark:text-white/80'}
                      `}
                    >
                      <td className="py-3 pl-4 text-center font-medium">
                        {isCurrent && isPlaying ? (
                          <div className="w-3 h-3 mx-auto flex items-end justify-center gap-0.5">
                            <span className="w-0.5 h-3 bg-pink-500 animate-pulse" />
                            <span className="w-0.5 h-2 bg-pink-500 animate-pulse delay-75" />
                            <span className="w-0.5 h-2.5 bg-pink-500 animate-pulse delay-150" />
                          </div>
                        ) : (
                          <span className="text-neutral-400 dark:text-white/40 group-hover:hidden">{idx + 1}</span>
                        )}
                        <Play className="w-3.5 h-3.5 mx-auto fill-current hidden group-hover:block" />
                      </td>

                      <td className="py-3 px-4">
                        <div className="font-semibold truncate text-sm">
                          {song.title}
                        </div>
                        <div className="text-neutral-500 dark:text-white/40 truncate text-[11px]">{song.artist}</div>
                      </td>

                      <td className="py-3 px-4 text-center text-neutral-400 dark:text-white/40 tabular-nums">
                        {formatDuration(song.duration)}
                      </td>

                      <td
                        className="py-3 pr-4 text-center"
                        onClick={(e) => {
                          e.stopPropagation();
                          toggleFavorite(song.id);
                        }}
                      >
                        <button className="p-1 text-neutral-300 dark:text-white/30 hover:text-yellow-400 transition-colors">
                          <Star
                            className={`w-3.5 h-3.5 ${
                              isFav ? 'text-yellow-400 fill-yellow-400' : ''
                            }`}
                          />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Full Albums Grid View (when viewing all albums) */}
      {subView === 'albums' && !selectedAlbum && (
        <div className="space-y-4 w-full max-w-5xl animate-in fade-in duration-300">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-bold tracking-tight">全部专辑</h2>
            <span className="text-xs text-neutral-500 dark:text-white/40">
              共 {filteredAlbums.length} 张专辑
            </span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-5">
            {filteredAlbums.slice(0, albumDisplayLimit).map((album) => (
              <div
                key={album.id}
                onClick={() => setSelectedAlbum(album)}
                className="group relative cursor-pointer"
              >
                <div className="aspect-square rounded-2xl overflow-hidden bg-black/5 dark:bg-white/[0.05] border border-black/5 dark:border-white/10 shadow-sm relative mb-2.5 transition-transform duration-300 group-hover:-translate-y-1">
                  {album.coverUrl ? (
                    <img
                      src={album.coverUrl}
                      alt={album.title}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                    />
                  ) : (
                    <div className="w-full h-full bg-gradient-to-tr from-purple-800 to-indigo-700 flex items-center justify-center">
                      <Disc className="w-10 h-10 text-white/40" />
                    </div>
                  )}

                  <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                    <div
                      onClick={(e) => {
                        e.stopPropagation();
                        if (album.songs.length > 0) {
                          playSong(album.songs[0], album.songs);
                        }
                      }}
                      className="w-12 h-12 rounded-full bg-white text-black flex items-center justify-center shadow-xl transform scale-90 group-hover:scale-100 transition-transform cursor-pointer"
                      title="直接播放专辑"
                    >
                      <Play className="w-5 h-5 fill-black translate-x-0.5" />
                    </div>
                  </div>
                </div>

                <h3 className="font-semibold text-sm truncate group-hover:text-pink-500 transition-colors">
                  {album.title}
                </h3>
                <p className="text-xs text-neutral-500 dark:text-white/40 truncate">{album.artist}</p>
                <p className="text-[11px] text-neutral-400 dark:text-white/30">{album.songs.length} 首歌曲</p>
              </div>
            ))}
          </div>

          {filteredAlbums.length > albumDisplayLimit && (
            <div className="flex justify-center p-4">
              <button
                onClick={() => setAlbumDisplayLimit((prev) => prev + 60)}
                className="px-6 py-2.5 rounded-2xl bg-black/5 dark:bg-white/10 hover:bg-black/10 dark:hover:bg-white/15 text-xs font-semibold text-neutral-800 dark:text-white/90 transition-all shadow-sm active:scale-95 cursor-pointer"
              >
                加载更多专辑 (已显示 {Math.min(albumDisplayLimit, filteredAlbums.length)} / 共 {filteredAlbums.length} 张)
              </button>
            </div>
          )}
        </div>
      )}

      {/* Artists View */}
      {subView === 'artists' && !selectedAlbum && (
        <div className="space-y-4 w-full max-w-5xl animate-in fade-in duration-300">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-bold tracking-tight">全部艺人</h2>
            <span className="text-xs text-neutral-500 dark:text-white/40">
              共 {artists.length} 位艺人
            </span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
            {artists.map((artist) => (
              <div
                key={artist.id}
                onClick={() => {
                  if (artist.songs.length > 0) {
                    playSong(artist.songs[0], artist.songs);
                  }
                }}
                className="group p-4 rounded-2xl bg-white/70 dark:bg-white/[0.04] hover:bg-white dark:hover:bg-white/[0.08] border border-black/[0.06] dark:border-white/[0.08] shadow-sm hover:shadow-md transition-all cursor-pointer flex flex-col items-center text-center space-y-3"
              >
                <div className="w-20 h-20 rounded-full bg-gradient-to-tr from-sky-500/20 to-indigo-500/20 border border-sky-500/30 flex items-center justify-center text-sky-500 shadow-sm group-hover:scale-105 transition-transform">
                  <User className="w-8 h-8" />
                </div>
                <div className="min-w-0 w-full">
                  <h3 className="font-semibold text-sm truncate group-hover:text-pink-500 transition-colors">
                    {artist.name}
                  </h3>
                  <p className="text-xs text-neutral-500 dark:text-white/40">{artist.songs.length} 首歌曲</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Song List Table (Screen-03.png style) */}
      {!selectedAlbum && (subView === 'all' || subView === 'songs' || subView === 'favorites' || selectedPlaylist) && (
        <div className="space-y-4 w-full max-w-5xl">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-bold tracking-tight">
              {selectedPlaylist
                ? `歌单曲目 (${selectedPlaylist.songIds.length})`
                : subView === 'favorites'
                ? '我喜爱的歌曲'
                : '全部歌曲'}
            </h2>
            <span className="text-xs text-neutral-500 dark:text-white/40">
              共{' '}
              {selectedPlaylist
                ? selectedPlaylist.songIds.length
                : subView === 'favorites'
                ? favorites.size
                : filteredSongs.length}{' '}
              首
            </span>
          </div>

          <div className="rounded-2xl border border-black/[0.06] dark:border-white/[0.08] bg-white/70 dark:bg-white/[0.02] backdrop-blur-xl overflow-hidden shadow-sm">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-black/[0.05] dark:border-white/[0.06] text-neutral-500 dark:text-white/40 uppercase tracking-wider font-semibold">
                <tr>
                  <th className="py-3 pl-4 w-12 text-center">#</th>
                  <th className="py-3 px-4">标题</th>
                  <th className="py-3 px-4 hidden md:table-cell">专辑</th>
                  <th className="py-3 px-4 w-16 text-center">
                    <Clock className="w-3.5 h-3.5 mx-auto" />
                  </th>
                  <th className="py-3 pr-4 w-12 text-center"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-black/[0.04] dark:divide-white/[0.04]">
                {(selectedPlaylist
                  ? songs.filter((s) => selectedPlaylist.songIds.includes(s.id))
                  : subView === 'favorites'
                  ? filteredSongs.filter((s) => favorites.has(s.id))
                  : filteredSongs
                ).slice(0, displayLimit).map((song, idx) => {
                  const isCurrent = currentSong?.id === song.id;
                  const isFav = favorites.has(song.id);

                  return (
                    <tr
                      key={song.id}
                      onClick={() => playSong(song, filteredSongs)}
                      onContextMenu={(e) => {
                        e.preventDefault();
                        setContextMenuData({ song, x: e.clientX, y: e.clientY });
                      }}
                      className={`
                        group hover:bg-black/5 dark:hover:bg-white/[0.07] cursor-pointer transition-colors
                        ${isCurrent ? 'bg-pink-500/10 text-pink-600 dark:text-pink-400' : 'text-neutral-800 dark:text-white/80'}
                      `}
                    >
                      <td className="py-3 pl-4 text-center font-medium">
                        {isCurrent && isPlaying ? (
                          <div className="w-3 h-3 mx-auto flex items-end justify-center gap-0.5">
                            <span className="w-0.5 h-3 bg-pink-500 animate-pulse" />
                            <span className="w-0.5 h-2 bg-pink-500 animate-pulse delay-75" />
                            <span className="w-0.5 h-2.5 bg-pink-500 animate-pulse delay-150" />
                          </div>
                        ) : (
                          <span className="text-neutral-400 dark:text-white/40 group-hover:hidden">{idx + 1}</span>
                        )}
                        <Play className="w-3.5 h-3.5 mx-auto fill-current hidden group-hover:block" />
                      </td>

                      <td className="py-3 px-4">
                        <div className="flex items-center gap-3">
                          <div className="w-9 h-9 rounded-lg overflow-hidden shrink-0 border border-black/5 dark:border-white/10 bg-black/5 dark:bg-white/5">
                            {song.coverUrl ? (
                              <img src={song.coverUrl} alt="" className="w-full h-full object-cover" />
                            ) : (
                              <div className="w-full h-full bg-violet-600/30 flex items-center justify-center text-[10px] text-white/50">
                                ♪
                              </div>
                            )}
                          </div>
                          <div className="min-w-0">
                            <div className={`font-semibold truncate text-sm ${isCurrent ? 'text-pink-600 dark:text-pink-400' : 'text-neutral-900 dark:text-white/95'}`}>
                              {song.title}
                            </div>
                            <div className="text-neutral-500 dark:text-white/40 truncate text-[11px]">{song.artist}</div>
                          </div>
                        </div>
                      </td>

                      <td className="py-3 px-4 hidden md:table-cell text-neutral-500 dark:text-white/50 truncate max-w-xs">
                        {song.album}
                      </td>

                      <td className="py-3 px-4 text-center text-neutral-400 dark:text-white/40 tabular-nums">
                        {formatDuration(song.duration)}
                      </td>

                      <td
                        className="py-3 pr-4 text-center"
                        onClick={(e) => e.stopPropagation()}
                      >
                        <div className="flex items-center justify-center gap-1">
                          <button
                            onClick={() => toggleFavorite(song.id)}
                            className="p-1 text-neutral-300 dark:text-white/30 hover:text-yellow-400 transition-colors"
                            title={isFav ? '取消喜爱' : '收藏'}
                          >
                            <Star
                              className={`w-3.5 h-3.5 ${
                                isFav ? 'text-yellow-400 fill-yellow-400' : ''
                              }`}
                            />
                          </button>

                          {selectedPlaylist && !selectedPlaylist.isSmart && (
                            <button
                              onClick={() => removeSongFromPlaylist(selectedPlaylist.id, song.id)}
                              className="opacity-0 group-hover:opacity-100 p-1 text-neutral-400 hover:text-red-500 transition-all"
                              title="从歌单移出"
                            >
                              <X className="w-3.5 h-3.5" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {(selectedPlaylist
              ? songs.filter((s) => selectedPlaylist.songIds.includes(s.id))
              : subView === 'favorites'
              ? filteredSongs.filter((s) => favorites.has(s.id))
              : filteredSongs
            ).length > displayLimit && (
              <div className="flex justify-center p-4 border-t border-black/[0.05] dark:border-white/[0.05]">
                <button
                  onClick={() => setDisplayLimit((prev) => prev + 80)}
                  className="px-6 py-2.5 rounded-2xl bg-black/5 dark:bg-white/10 hover:bg-black/10 dark:hover:bg-white/15 text-xs font-semibold text-neutral-800 dark:text-white/90 transition-all shadow-sm active:scale-95 cursor-pointer"
                >
                  加载更多歌曲 (已显示 {Math.min(displayLimit, (selectedPlaylist ? songs.filter((s) => selectedPlaylist.songIds.includes(s.id)) : subView === 'favorites' ? filteredSongs.filter((s) => favorites.has(s.id)) : filteredSongs).length)} / 共 {(selectedPlaylist ? songs.filter((s) => selectedPlaylist.songIds.includes(s.id)) : subView === 'favorites' ? filteredSongs.filter((s) => favorites.has(s.id)) : filteredSongs).length} 首)
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {/* New Playlist Modal */}
      <CreatePlaylistModal
        isOpen={isCreatePlaylistOpen}
        onClose={() => setIsCreatePlaylistOpen(false)}
        onCreated={(newPlId) => {
          const pl = playlists.find((p) => p.id === newPlId);
          if (pl) {
            setSelectedPlaylist(pl);
            setSubView('playlists');
          }
        }}
      />

      {/* Global Right Click Context Menu */}
      {contextMenuData && (
        <ContextMenu
          song={contextMenuData.song}
          x={contextMenuData.x}
          y={contextMenuData.y}
          onClose={() => setContextMenuData(null)}
          onRequestNewPlaylist={() => setIsCreatePlaylistOpen(true)}
        />
      )}
    </div>
  );
};
