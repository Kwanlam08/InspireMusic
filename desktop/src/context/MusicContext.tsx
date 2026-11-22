import React, { createContext, useContext, useState, useEffect, useRef } from 'react';
import { Song, Album, Artist, Playlist, PlayMode, DiaryDaySummary, AppSettings, ListeningRecord } from '../types';

interface MusicContextType {
  songs: Song[];
  albums: Album[];
  artists: Artist[];
  playlists: Playlist[];
  favorites: Set<string>;
  toggleFavorite: (songId: string) => void;
  currentSong: Song | null;
  isPlaying: boolean;
  currentTime: number;
  duration: number;
  volume: number;
  isMuted: boolean;
  playMode: PlayMode;
  queue: Song[];
  history: Song[];
  diarySummaries: DiaryDaySummary[];
  settings: AppSettings;
  updateSettings: (newSettings: Partial<AppSettings>) => void;
  isNowPlayingOpen: boolean;
  setIsNowPlayingOpen: (open: boolean) => void;
  isLyricsActive: boolean;
  setIsLyricsActive: (active: boolean) => void;
  isQueueDrawerOpen: boolean;
  setIsQueueDrawerOpen: (open: boolean) => void;
  
  // Actions
  playSong: (song: Song, newQueue?: Song[]) => void;
  togglePlay: () => void;
  seek: (time: number) => void;
  setVolume: (vol: number) => void;
  toggleMute: () => void;
  nextTrack: () => void;
  prevTrack: () => void;
  cyclePlayMode: () => void;
  
  // Queue Actions
  playNext: (song: Song) => void;
  addToQueue: (song: Song) => void;
  removeFromQueue: (songId: string, index?: number) => void;
  reorderQueue: (startIndex: number, endIndex: number) => void;
  clearQueue: () => void;

  // Library Actions
  scanFolder: () => Promise<void>;
  isScanning: boolean;
  scanProgress: { current: number; total: number } | null;
  createPlaylist: (name: string, description?: string) => Playlist;
  deletePlaylist: (playlistId: string) => void;
  renamePlaylist: (playlistId: string, name: string) => void;
  addSongToPlaylist: (playlistId: string, songId: string) => void;
  removeSongFromPlaylist: (playlistId: string, songId: string) => void;
  smartPlaylists: Playlist[];
  themeMode: 'system' | 'dark' | 'light';
  setThemeMode: (mode: 'system' | 'dark' | 'light') => void;

  // Desktop lyrics & mini mode
  isDesktopLyricsOpen: boolean;
  toggleDesktopLyrics: () => void;
  isMiniMode: boolean;
  toggleMiniMode: () => void;
}

const MusicContext = createContext<MusicContextType | null>(null);

// Built-in demonstration songs so user experiences Liquid Glass & Apple Music lyrics immediately
const DEMO_SONGS: Song[] = [
  {
    id: 'demo_1',
    path: '',
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3',
    title: 'imagine',
    artist: 'Ariana Grande',
    album: 'thank u, next',
    albumArtist: 'Ariana Grande',
    duration: 212,
    year: 2019,
    genre: 'Pop / R&B',
    coverUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800&auto=format&fit=crop&q=80',
    lyrics: `[00:00.00]imagine - Ariana Grande
[00:05.10]Step up the two of us, nobody knows us
[00:10.50]Get in the car like, "Skrrt"
[00:14.20]Staying up all night, order me pad thai
[00:18.70]Then we gon' sleep 'til noon
[00:22.50]Me with no makeup, you in the bathtub
[00:27.10]Bubbles and kiss, so good
[00:31.00]Can you imagine it?
[00:35.30]Can you imagine it?
[00:39.60]Kisses on kisses and kisses on kisses
[00:44.20]Promise me love, like we're never gon' break it
[00:48.50]Love like we're falling, like we're in slow motion
[00:52.80]Imagine a world like that
[00:57.20]Imagine a world like that
[01:01.50]We go until I'm 'sleep on your chest
[01:06.00]Love how my face fits so good in your neck
[01:10.50]Why can't you imagine a world like that?
[01:15.00]Imagine that`,
  },
  {
    id: 'demo_2',
    path: '',
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3',
    title: 'thank u, next',
    artist: 'Ariana Grande',
    album: 'thank u, next',
    albumArtist: 'Ariana Grande',
    duration: 207,
    year: 2019,
    genre: 'Pop',
    coverUrl: 'https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop&q=80',
    lyrics: `[00:00.00]thank u, next - Ariana Grande
[00:04.20]Thought I'd end up with Sean
[00:07.10]But he wasn't a match
[00:09.50]Wrote some songs about Ricky
[00:12.30]Now I listen and laugh
[00:15.20]Even almost got married
[00:17.80]And for Pete, I'm so thankful
[00:20.50]Wish I could say, "Thank you" to Malcolm
[00:23.50]'Cause he was an angel
[00:26.20]One taught me love
[00:29.00]One taught me patience
[00:31.80]And one taught me pain
[00:34.50]Now, I'm so amazing
[00:37.20]Say I've loved and I've lost
[00:40.00]But that's not what I see
[00:42.80]So, look what I got
[00:45.50]Look what you taught me
[00:48.20]And for that, I say
[00:50.10]Thank u, next (Next)
[00:53.00]Thank u, next (Next)
[00:55.80]Thank u, next
[00:58.50]I'm so fuckin' grateful for my ex
[01:01.20]Thank u, next (Next)`,
  },
  {
    id: 'demo_3',
    path: '',
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3',
    title: 'Lavender Haze',
    artist: 'Taylor Swift',
    album: 'Midnights',
    albumArtist: 'Taylor Swift',
    duration: 202,
    year: 2022,
    genre: 'Synth-Pop',
    coverUrl: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&auto=format&fit=crop&q=80',
    lyrics: `[00:00.00]Lavender Haze - Taylor Swift
[00:06.00]Meet me at midnight
[00:11.20]Ooh, ooh, ooh
[00:16.80]Starin' at the ceilin' with you
[00:21.00]Oh, you don't ever say too much
[00:25.50]And you don't really read into
[00:30.20]My melancholia
[00:34.00]I've been under scrutiny
[00:38.20]You handled it beautifully
[00:43.00]All this shit is new to me
[00:47.80]I feel the lavender haze creepin' up on me
[00:53.20]Surreal, I'm damned if I do give a damn what people say
[00:59.50]No deal, the 1950s shit they want from me
[01:05.20]I just wanna stay in that lavender haze`,
  },
  {
    id: 'demo_4',
    path: '',
    url: 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3',
    title: 'Take Yourself Home',
    artist: 'Troye Sivan',
    album: 'In A Dream',
    albumArtist: 'Troye Sivan',
    duration: 249,
    year: 2020,
    genre: 'Electropop',
    coverUrl: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&auto=format&fit=crop&q=80',
    lyrics: `[00:00.00]Take Yourself Home - Troye Sivan
[00:08.50]I'm tired of the city, scream if you're with me
[00:15.00]If I'm gonna die, let's die somewhere pretty
[00:21.50]Sad in the summer, city needs a mother
[00:27.50]If I'm gonna waste my time then it's time to go
[00:34.00]Take yourself home
[00:40.00]Talk to yourself, unpack what you can
[00:46.50]Take yourself home
[00:52.50]Go home, go home, go home`,
  }
];

const DEFAULT_SETTINGS: AppSettings = {
  musicFolders: [],
  themeMode: 'system',
  lyricFontSize: 28,
  lyricBlurStrength: 8,
  lyricDelayOffsetMs: 0,
  crossfadeDuration: 2,
  aiProvider: 'deepseek',
  aiApiUrl: 'https://api.deepseek.com/v1',
  aiApiKey: '',
  aiModel: 'deepseek-chat',
};

export const MusicProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // Songs and collections
  const [songs, setSongs] = useState<Song[]>(() => {
    const cached = localStorage.getItem('inspire_music_songs');
    if (cached) {
      try {
        const parsed = JSON.parse(cached);
        if (parsed.length > 0) return parsed;
      } catch (e) {
        // ignore
      }
    }
    return DEMO_SONGS;
  });

  const [favorites, setFavorites] = useState<Set<string>>(() => {
    const cached = localStorage.getItem('inspire_music_favs');
    return cached ? new Set(JSON.parse(cached)) : new Set(['demo_1', 'demo_3']);
  });

  const [playlists, setPlaylists] = useState<Playlist[]>(() => {
    const cached = localStorage.getItem('inspire_music_playlists');
    if (cached) {
      try { return JSON.parse(cached); } catch (e) {}
    }
    return [
      {
        id: 'pl_favorites',
        name: '我的喜爱',
        description: '你标记红心的所有歌曲',
        songIds: ['demo_1', 'demo_3'],
        createdAt: Date.now() - 86400000 * 5,
        isSmart: true,
      },
      {
        id: 'pl_night',
        name: '夜间微醺 🌙',
        description: '安静流淌的深夜旋律',
        songIds: ['demo_1', 'demo_3', 'demo_4'],
        createdAt: Date.now() - 86400000 * 2,
      }
    ];
  });

  // Playback state
  const [currentSong, setCurrentSong] = useState<Song | null>(DEMO_SONGS[0]);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(DEMO_SONGS[0].duration);
  const [volume, setVolumeState] = useState<number>(0.8);
  const [isMuted, setIsMuted] = useState<boolean>(false);
  const [playMode, setPlayMode] = useState<PlayMode>('sequence');
  const [queue, setQueue] = useState<Song[]>(DEMO_SONGS);
  const [history, setHistory] = useState<Song[]>([]);

  // UI state
  const [isNowPlayingOpen, setIsNowPlayingOpen] = useState<boolean>(false);
  const [isLyricsActive, setIsLyricsActive] = useState<boolean>(false);
  const [isQueueDrawerOpen, setIsQueueDrawerOpen] = useState<boolean>(false);

  // Diary records
  const [diaryRecords, setDiaryRecords] = useState<ListeningRecord[]>(() => {
    const cached = localStorage.getItem('inspire_music_records');
    if (cached) {
      try { return JSON.parse(cached); } catch (e) {}
    }
    // Seed some initial records
    const now = Date.now();
    return [
      { id: 'rec_1', songId: 'demo_1', title: 'imagine', artist: 'Ariana Grande', album: 'thank u, next', timestamp: now - 3600000 * 2, durationPlayed: 212 },
      { id: 'rec_2', songId: 'demo_2', title: 'thank u, next', artist: 'Ariana Grande', album: 'thank u, next', timestamp: now - 3600000 * 1.5, durationPlayed: 207 },
      { id: 'rec_3', songId: 'demo_3', title: 'Lavender Haze', artist: 'Taylor Swift', album: 'Midnights', timestamp: now - 3600000 * 0.8, durationPlayed: 202 },
      { id: 'rec_4', songId: 'demo_4', title: 'Take Yourself Home', artist: 'Troye Sivan', album: 'In A Dream', timestamp: now - 3600000 * 0.2, durationPlayed: 249 },
    ];
  });

  // Settings
  const [settings, setSettings] = useState<AppSettings>(() => {
    const cached = localStorage.getItem('inspire_music_settings');
    if (cached) {
      try { return { ...DEFAULT_SETTINGS, ...JSON.parse(cached) }; } catch (e) {}
    }
    return DEFAULT_SETTINGS;
  });

  // Scanning state
  const [isScanning, setIsScanning] = useState<boolean>(false);
  const [scanProgress, setScanProgress] = useState<{ current: number; total: number } | null>(null);

  // Audio HTML5 element ref
  const audioRef = useRef<HTMLAudioElement | null>(null);

  // Initialize audio element
  useEffect(() => {
    const audio = new Audio();
    audioRef.current = audio;
    audio.volume = volume;

    const onTimeUpdate = () => {
      setCurrentTime(audio.currentTime);
    };
    const onLoadedMetadata = () => {
      if (audio.duration && !isNaN(audio.duration)) {
        setDuration(audio.duration);
      }
    };
    const onEnded = () => {
      handleTrackEnd();
    };

    audio.addEventListener('timeupdate', onTimeUpdate);
    audio.addEventListener('loadedmetadata', onLoadedMetadata);
    audio.addEventListener('ended', onEnded);

    return () => {
      audio.removeEventListener('timeupdate', onTimeUpdate);
      audio.removeEventListener('loadedmetadata', onLoadedMetadata);
      audio.removeEventListener('ended', onEnded);
      audio.pause();
    };
  }, []);

  // Save favorites & settings
  useEffect(() => {
    localStorage.setItem('inspire_music_favs', JSON.stringify(Array.from(favorites)));
  }, [favorites]);

  useEffect(() => {
    localStorage.setItem('inspire_music_settings', JSON.stringify(settings));
  }, [settings]);

  useEffect(() => {
    localStorage.setItem('inspire_music_playlists', JSON.stringify(playlists));
  }, [playlists]);

  useEffect(() => {
    localStorage.setItem('inspire_music_records', JSON.stringify(diaryRecords));
  }, [diaryRecords]);

  // Handle track end according to playMode
  const handleTrackEnd = () => {
    if (playMode === 'loop-one') {
      if (audioRef.current) {
        audioRef.current.currentTime = 0;
        audioRef.current.play();
      }
    } else {
      nextTrack();
    }
  };

  // Play a song
  const playSong = (song: Song, newQueue?: Song[]) => {
    if (newQueue) {
      setQueue(newQueue);
    }
    setCurrentSong(song);
    setCurrentTime(0);
    setDuration(song.duration || 180);

    if (audioRef.current) {
      let playUrl = song.url;
      if (song.path && (!playUrl || !playUrl.startsWith('http'))) {
        const normalized = song.path.replace(/\\/g, '/');
        const parts = normalized.split('/');
        const encoded = parts.map((p, idx) => (idx === 0 && /^[a-zA-Z]:$/.test(p) ? p : encodeURIComponent(p))).join('/');
        playUrl = `file:///${encoded.replace(/^\/+/, '')}`;
      }
      audioRef.current.src = playUrl;
      audioRef.current.currentTime = 0;
      audioRef.current.play().then(() => {
        setIsPlaying(true);
      }).catch(err => {
        console.warn('Playback error (possibly autoplay policy):', err);
        setIsPlaying(false);
      });
    }

    // Add to history and record listening diary
    setHistory(prev => [song, ...prev.filter(s => s.id !== song.id)].slice(0, 50));
    setDiaryRecords(prev => [
      {
        id: `rec_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
        songId: song.id,
        title: song.title,
        artist: song.artist,
        album: song.album,
        timestamp: Date.now(),
        durationPlayed: 0,
      },
      ...prev
    ]);
  };

  const togglePlay = () => {
    if (!audioRef.current || !currentSong) return;
    if (isPlaying) {
      audioRef.current.pause();
      setIsPlaying(false);
    } else {
      audioRef.current.play().then(() => {
        setIsPlaying(true);
      }).catch(console.error);
    }
  };

  const seek = (time: number) => {
    if (!audioRef.current) return;
    audioRef.current.currentTime = time;
    setCurrentTime(time);
  };

  const setVolume = (vol: number) => {
    const clamped = Math.max(0, Math.min(1, vol));
    setVolumeState(clamped);
    if (audioRef.current) {
      audioRef.current.volume = isMuted ? 0 : clamped;
    }
  };

  const toggleMute = () => {
    if (!audioRef.current) return;
    const nextMute = !isMuted;
    setIsMuted(nextMute);
    audioRef.current.volume = nextMute ? 0 : volume;
  };

  const nextTrack = () => {
    if (queue.length === 0) return;
    const currentIndex = queue.findIndex(s => s.id === currentSong?.id);
    let nextIndex = 0;

    if (playMode === 'shuffle') {
      nextIndex = Math.floor(Math.random() * queue.length);
    } else {
      nextIndex = (currentIndex + 1) % queue.length;
    }

    playSong(queue[nextIndex]);
  };

  const prevTrack = () => {
    if (queue.length === 0) return;
    // If played more than 3 seconds, replay from start
    if (currentTime > 3) {
      seek(0);
      return;
    }
    const currentIndex = queue.findIndex(s => s.id === currentSong?.id);
    let prevIndex = currentIndex - 1;
    if (prevIndex < 0) prevIndex = queue.length - 1;
    playSong(queue[prevIndex]);
  };

  const cyclePlayMode = () => {
    const modes: PlayMode[] = ['sequence', 'loop', 'loop-one', 'shuffle'];
    const curIdx = modes.indexOf(playMode);
    const nextMode = modes[(curIdx + 1) % modes.length];
    setPlayMode(nextMode);
  };

  const toggleFavorite = (songId: string) => {
    setFavorites(prev => {
      const next = new Set(prev);
      if (next.has(songId)) {
        next.delete(songId);
      } else {
        next.add(songId);
      }
      return next;
    });
  };

  // Windows 10/11 System Media Transport Controls (SMTC) integration
  useEffect(() => {
    if (!('mediaSession' in navigator) || !currentSong) return;

    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: currentSong.title,
        artist: currentSong.artist || 'Inspire Music',
        album: currentSong.album || '资料库',
        artwork: currentSong.coverUrl
          ? [
              { src: currentSong.coverUrl, sizes: '96x96' },
              { src: currentSong.coverUrl, sizes: '256x256' },
              { src: currentSong.coverUrl, sizes: '512x512' },
            ]
          : [],
      });
    } catch (e) {
      console.warn('MediaSession metadata error:', e);
    }
  }, [currentSong]);

  useEffect(() => {
    if (!('mediaSession' in navigator)) return;
    navigator.mediaSession.playbackState = isPlaying ? 'playing' : 'paused';
  }, [isPlaying]);

  useEffect(() => {
    if (!('mediaSession' in navigator)) return;
    try {
      navigator.mediaSession.setActionHandler('play', () => {
        if (audioRef.current) {
          audioRef.current.play().then(() => setIsPlaying(true)).catch(console.error);
        }
      });
      navigator.mediaSession.setActionHandler('pause', () => {
        if (audioRef.current) {
          audioRef.current.pause();
          setIsPlaying(false);
        }
      });
      navigator.mediaSession.setActionHandler('previoustrack', () => prevTrack());
      navigator.mediaSession.setActionHandler('nexttrack', () => nextTrack());
      navigator.mediaSession.setActionHandler('seekto', (details) => {
        if (details.seekTime !== undefined) {
          seek(details.seekTime);
        }
      });
    } catch (e) {
      console.warn('MediaSession action handlers error:', e);
    }
  }, [currentSong, queue]);

  // Scan folder for music files via Electron IPC
  const scanFolder = async () => {
    if (!window.electronAPI) {
      alert('请在 Electron 桌面环境中扫描本地文件夹');
      return;
    }

    const dirPath = await window.electronAPI.selectDirectory();
    if (!dirPath) return;

    setIsScanning(true);
    setScanProgress({ current: 0, total: 100 });

    const removeListener = window.electronAPI.onScanProgress((prog) => {
      setScanProgress(prog);
    });

    try {
      const result = await window.electronAPI.scanLibrary(dirPath);
      removeListener();
      setIsScanning(false);
      setScanProgress(null);

      if (result.success && result.songs.length > 0) {
        const merged = [...result.songs];
        // Ensure no duplicates by path
        const map = new Map<string, Song>();
        for (const s of merged) {
          map.set(s.path || s.id, s);
        }
        const updated = Array.from(map.values());
        setSongs(updated);
        setQueue(updated);
        try {
          // Sanitize songs to ensure no giant base64 strings blow up localStorage
          const safeSongs = updated.map(s => ({
            ...s,
            coverUrl: s.coverUrl && s.coverUrl.startsWith('data:') && s.coverUrl.length > 5000 ? null : s.coverUrl,
          }));
          localStorage.setItem('inspire_music_songs', JSON.stringify(safeSongs));
        } catch (storageErr) {
          console.warn('LocalStorage save failed:', storageErr);
        }

        // Add to settings folders
        if (!settings.musicFolders.includes(dirPath)) {
          updateSettings({
            musicFolders: [...settings.musicFolders, dirPath],
          });
        }
      }
    } catch (e) {
      console.error('Folder scan error:', e);
      setIsScanning(false);
      setScanProgress(null);
    }
  };

  const updateSettings = (newSettings: Partial<AppSettings>) => {
    setSettings(prev => ({ ...prev, ...newSettings }));
  };

  const createPlaylist = (name: string, description?: string): Playlist => {
    const pl: Playlist = {
      id: `pl_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
      name: name.trim() || '未命名歌单',
      description: description?.trim() || '',
      songIds: [],
      createdAt: Date.now(),
    };
    setPlaylists(prev => [pl, ...prev]);
    return pl;
  };

  const deletePlaylist = (playlistId: string) => {
    setPlaylists(prev => prev.filter(pl => pl.id !== playlistId));
  };

  const renamePlaylist = (playlistId: string, name: string) => {
    setPlaylists(prev =>
      prev.map(pl => (pl.id === playlistId ? { ...pl, name: name.trim() } : pl))
    );
  };

  const addSongToPlaylist = (playlistId: string, songId: string) => {
    setPlaylists(prev =>
      prev.map(pl => {
        if (pl.id === playlistId && !pl.songIds.includes(songId)) {
          return { ...pl, songIds: [...pl.songIds, songId] };
        }
        return pl;
      })
    );
  };

  const removeSongFromPlaylist = (playlistId: string, songId: string) => {
    setPlaylists(prev =>
      prev.map(pl => {
        if (pl.id === playlistId) {
          return { ...pl, songIds: pl.songIds.filter(id => id !== songId) };
        }
        return pl;
      })
    );
  };

  // Sync playlists to localStorage
  useEffect(() => {
    try {
      localStorage.setItem('inspire_music_playlists', JSON.stringify(playlists));
    } catch (e) {
      console.warn('Playlist save error:', e);
    }
  }, [playlists]);

  // Queue Actions
  const playNext = (song: Song) => {
    setQueue(prev => {
      const filtered = prev.filter(s => s.id !== song.id);
      if (!currentSong) {
        playSong(song);
        return [song];
      }
      const currentIndex = filtered.findIndex(s => s.id === currentSong.id);
      if (currentIndex === -1) {
        return [currentSong, song, ...filtered];
      }
      const updated = [...filtered];
      updated.splice(currentIndex + 1, 0, song);
      return updated;
    });
  };

  const addToQueue = (song: Song) => {
    setQueue(prev => {
      if (prev.some(s => s.id === song.id)) {
        return [...prev.filter(s => s.id !== song.id), song];
      }
      return [...prev, song];
    });
  };

  const removeFromQueue = (songId: string, index?: number) => {
    setQueue(prev => {
      if (index !== undefined && index >= 0 && index < prev.length) {
        const next = [...prev];
        next.splice(index, 1);
        return next;
      }
      return prev.filter(s => s.id !== songId);
    });
  };

  const reorderQueue = (startIndex: number, endIndex: number) => {
    setQueue(prev => {
      if (startIndex < 0 || startIndex >= prev.length || endIndex < 0 || endIndex >= prev.length) {
        return prev;
      }
      const next = [...prev];
      const [moved] = next.splice(startIndex, 1);
      next.splice(endIndex, 0, moved);
      return next;
    });
  };

  const clearQueue = () => {
    if (currentSong) {
      setQueue([currentSong]);
    } else {
      setQueue([]);
    }
  };

  // Desktop Floating Lyrics & Mini Mode State
  const [isDesktopLyricsOpen, setIsDesktopLyricsOpen] = useState<boolean>(false);
  const [isMiniMode, setIsMiniMode] = useState<boolean>(false);

  useEffect(() => {
    if (!window.electronAPI) return;
    const unsubMini = window.electronAPI.onMiniModeChange?.((mini) => {
      setIsMiniMode(mini);
    });
    const unsubControl = window.electronAPI.onPlayerControl?.((action) => {
      if (action === 'play-pause') togglePlay();
      else if (action === 'next') nextTrack();
      else if (action === 'prev') prevTrack();
    });
    return () => {
      unsubMini?.();
      unsubControl?.();
    };
  }, []);

  const toggleDesktopLyrics = async () => {
    if (window.electronAPI?.toggleDesktopLyrics) {
      const state = await window.electronAPI.toggleDesktopLyrics();
      setIsDesktopLyricsOpen(state);
    } else {
      setIsDesktopLyricsOpen(prev => !prev);
    }
  };

  const toggleMiniMode = async () => {
    if (window.electronAPI?.toggleMiniMode) {
      const state = await window.electronAPI.toggleMiniMode();
      setIsMiniMode(state);
    } else {
      setIsMiniMode(prev => !prev);
    }
  };

  // Sync lyrics state to desktop floating window
  useEffect(() => {
    if (window.electronAPI?.syncLyricsState) {
      window.electronAPI.syncLyricsState({
        title: currentSong?.title || '',
        artist: currentSong?.artist || '',
        lyrics: currentSong?.lyrics || '',
        currentTime,
        duration,
        isPlaying,
      });
    }
  }, [currentSong, currentTime, duration, isPlaying]);

  // Compute derived Albums and Artists from songs
  const albums: Album[] = React.useMemo(() => {
    const map = new Map<string, Album>();
    for (const song of songs) {
      const key = `${song.album}___${song.albumArtist || song.artist}`;
      if (!map.has(key)) {
        map.set(key, {
          id: key,
          title: song.album || '未知专辑',
          artist: song.albumArtist || song.artist || '未知艺人',
          coverUrl: song.coverUrl,
          year: song.year,
          songCount: 1,
          songs: [song],
        });
      } else {
        const existing = map.get(key)!;
        existing.songCount++;
        existing.songs.push(song);
        if (!existing.coverUrl && song.coverUrl) {
          existing.coverUrl = song.coverUrl;
        }
      }
    }
    return Array.from(map.values());
  }, [songs]);

  const artists: Artist[] = React.useMemo(() => {
    const map = new Map<string, Artist>();
    for (const song of songs) {
      const key = song.artist || '未知艺人';
      if (!map.has(key)) {
        map.set(key, {
          id: key,
          name: key,
          avatarUrl: song.coverUrl,
          songCount: 1,
          albumCount: 1,
          songs: [song],
        });
      } else {
        const existing = map.get(key)!;
        existing.songCount++;
        existing.songs.push(song);
        if (!existing.avatarUrl && song.coverUrl) {
          existing.avatarUrl = song.coverUrl;
        }
      }
    }
    return Array.from(map.values());
  }, [songs]);

  // Compute diary summaries grouped by date
  const diarySummaries: DiaryDaySummary[] = React.useMemo(() => {
    const dayMap = new Map<string, ListeningRecord[]>();
    for (const record of diaryRecords) {
      const d = new Date(record.timestamp);
      const dateStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      if (!dayMap.has(dateStr)) {
        dayMap.set(dateStr, []);
      }
      dayMap.get(dateStr)!.push(record);
    }

    const summaries: DiaryDaySummary[] = [];
    dayMap.forEach((records, dateStr) => {
      const d = new Date(records[0].timestamp);
      const displayDate = `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;

      // Top song
      const songCounts = new Map<string, number>();
      const artistCounts = new Map<string, number>();
      for (const r of records) {
        songCounts.set(r.title, (songCounts.get(r.title) || 0) + 1);
        artistCounts.set(r.artist, (artistCounts.get(r.artist) || 0) + 1);
      }

      let topSong: { title: string; count: number } | null = null;
      let maxSongCount = 0;
      songCounts.forEach((count, title) => {
        if (count > maxSongCount) {
          maxSongCount = count;
          topSong = { title, count };
        }
      });

      let topArtist: { name: string; count: number } | null = null;
      let maxArtistCount = 0;
      artistCounts.forEach((count, name) => {
        if (count > maxArtistCount) {
          maxArtistCount = count;
          topArtist = { name, count };
        }
      });

      summaries.push({
        dateStr,
        displayDate,
        playCount: records.length,
        songCount: new Set(records.map(r => r.songId)).size,
        totalDurationMinutes: Math.round(records.reduce((acc, r) => acc + (r.durationPlayed || 180), 0) / 60),
        topSong,
        topArtist,
        records,
        aiAnalysis: '今天你的听歌风格整体偏向 R&B 与梦幻流行，在黄昏与深夜时段尤为沉浸，音乐仿佛为你构筑了一座安静的心灵避风港。',
      });
    });

    return summaries.sort((a, b) => b.dateStr.localeCompare(a.dateStr));
  }, [diaryRecords]);

  // System theme synchronization
  useEffect(() => {
    const applyTheme = (isDark: boolean) => {
      if (isDark) {
        document.documentElement.classList.add('dark');
      } else {
        document.documentElement.classList.remove('dark');
      }
    };

    if (settings.themeMode === 'system') {
      const media = window.matchMedia('(prefers-color-scheme: dark)');
      applyTheme(media.matches);
      const listener = (e: MediaQueryListEvent) => applyTheme(e.matches);
      media.addEventListener('change', listener);
      return () => media.removeEventListener('change', listener);
    } else if (settings.themeMode === 'dark') {
      applyTheme(true);
    } else {
      applyTheme(false);
    }
  }, [settings.themeMode]);

  const setThemeMode = (mode: 'system' | 'dark' | 'light') => {
    updateSettings({ themeMode: mode });
  };

  // 5 Smart Playlists (100% aligned with Android MusicViewModel.kt)
  const smartPlaylists: Playlist[] = React.useMemo(() => {
    const now = Date.now();

    // 1. 最近添加 (smart:recent)
    const recentSongs = [...songs].slice(0, 30);

    // 2. 很久没听 (smart:dormant)
    const playedSongIds = new Set(diaryRecords.map(r => r.songId));
    const dormantSongs = songs.filter(s => !playedSongIds.has(s.id));

    // 3. 夜间常听 (smart:night) - 22:00 to 06:00
    const nightSongIds = new Set(
      diaryRecords
        .filter(r => {
          const hour = new Date(r.timestamp).getHours();
          return hour >= 22 || hour < 6;
        })
        .map(r => r.songId)
    );

    // 4. 播放最多 (smart:frequent)
    const playCounts = new Map<string, number>();
    for (const r of diaryRecords) {
      playCounts.set(r.songId, (playCounts.get(r.songId) || 0) + 1);
    }
    const frequentSongIds = [...songs]
      .sort((a, b) => (playCounts.get(b.id) || 0) - (playCounts.get(a.id) || 0))
      .map(s => s.id);

    // 5. 收藏但少听 (smart:favorites_rare)
    const rareFavIds = songs
      .filter(s => favorites.has(s.id) && (playCounts.get(s.id) || 0) <= 2)
      .map(s => s.id);

    return [
      {
        id: 'smart:recent',
        name: '最近添加',
        description: '自动收录最新加入资料库的音乐',
        songIds: recentSongs.map(s => s.id),
        createdAt: now,
        isSmart: true,
      },
      {
        id: 'smart:frequent',
        name: '播放最多',
        description: '根据本地播放次数自动排序',
        songIds: frequentSongIds,
        createdAt: now,
        isSmart: true,
      },
      {
        id: 'smart:night',
        name: '夜间常听 🌙',
        description: '晚间 22:00–06:00 常播的沉浸夜曲',
        songIds: Array.from(nightSongIds),
        createdAt: now,
        isSmart: true,
      },
      {
        id: 'smart:dormant',
        name: '很久没听',
        description: '好久未播放或从未听过的宝藏旋律',
        songIds: dormantSongs.map(s => s.id),
        createdAt: now,
        isSmart: true,
      },
      {
        id: 'smart:favorites_rare',
        name: '收藏但少听',
        description: '已加入红心喜爱但播放较少的歌曲',
        songIds: rareFavIds,
        createdAt: now,
        isSmart: true,
      },
    ];
  }, [songs, diaryRecords, favorites]);

  return (
    <MusicContext.Provider
      value={{
        songs,
        albums,
        artists,
        playlists,
        smartPlaylists,
        themeMode: settings.themeMode,
        setThemeMode,
        favorites,
        toggleFavorite,
        currentSong,
        isPlaying,
        currentTime,
        duration,
        volume,
        isMuted,
        playMode,
        queue,
        history,
        diarySummaries,
        settings,
        updateSettings,
        isNowPlayingOpen,
        setIsNowPlayingOpen,
        isLyricsActive,
        setIsLyricsActive,
        isQueueDrawerOpen,
        setIsQueueDrawerOpen,
        playSong,
        togglePlay,
        seek,
        setVolume,
        toggleMute,
        nextTrack,
        prevTrack,
        cyclePlayMode,
        playNext,
        addToQueue,
        removeFromQueue,
        reorderQueue,
        clearQueue,
        scanFolder,
        isScanning,
        scanProgress,
        createPlaylist,
        deletePlaylist,
        renamePlaylist,
        addSongToPlaylist,
        removeSongFromPlaylist,
        isDesktopLyricsOpen,
        toggleDesktopLyrics,
        isMiniMode,
        toggleMiniMode,
      }}
    >
      {children}
    </MusicContext.Provider>
  );
};

export const useMusic = () => {
  const context = useContext(MusicContext);
  if (!context) {
    throw new Error('useMusic must be used within MusicProvider');
  }
  return context;
};
