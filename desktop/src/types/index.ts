export interface Song {
  id: string;
  path: string;
  url: string;
  title: string;
  artist: string;
  album: string;
  albumArtist?: string;
  duration: number; // in seconds
  year?: number | null;
  genre?: string | null;
  coverUrl?: string | null;
  lyrics?: string | null;
  playCount?: number;
  lastPlayed?: number;
}

export interface Album {
  id: string;
  title: string;
  artist: string;
  coverUrl?: string | null;
  year?: number | null;
  songCount: number;
  songs: Song[];
}

export interface Artist {
  id: string;
  name: string;
  avatarUrl?: string | null;
  songCount: number;
  albumCount: number;
  songs: Song[];
}

export interface Playlist {
  id: string;
  name: string;
  description?: string;
  coverUrl?: string | null;
  songIds: string[];
  createdAt: number;
  isSmart?: boolean;
}

export interface LrcLine {
  time: number; // in seconds
  text: string;
  translation?: string;
}

export type PlayMode = 'sequence' | 'loop' | 'loop-one' | 'shuffle';

export interface ListeningRecord {
  id: string;
  songId: string;
  title: string;
  artist: string;
  album: string;
  timestamp: number; // ms
  durationPlayed: number; // seconds
}

export interface DiaryDaySummary {
  dateStr: string; // YYYY-MM-DD
  displayDate: string; // 2026年7月11日
  playCount: number;
  songCount: number;
  totalDurationMinutes: number;
  topSong: { title: string; count: number } | null;
  topArtist: { name: string; count: number } | null;
  records: ListeningRecord[];
  aiAnalysis?: string;
}

export interface MeshGradientColors {
  baseBackground: string;
  primary: string;
  secondary: string;
  tertiary: string;
  accent: string;
}

export type ThemeMode = 'system' | 'dark' | 'light';

export interface AppSettings {
  musicFolders: string[];
  themeMode: ThemeMode;
  lyricFontSize: number;
  lyricBlurStrength: number;
  lyricDelayOffsetMs: number;
  crossfadeDuration: number;
  aiProvider: 'deepseek' | 'openai' | 'custom';
  aiApiUrl: string;
  aiApiKey: string;
  aiModel: string;
}

declare global {
  interface Window {
    electronAPI?: {
      minimize: () => Promise<void>;
      maximize: () => Promise<boolean>;
      close: () => Promise<void>;
      isMaximized: () => Promise<boolean>;
      onMaximizedChange: (cb: (maximized: boolean) => void) => () => void;
      setFullScreen: (flag?: boolean) => Promise<boolean>;
      isFullScreen: () => Promise<boolean>;
      onFullScreenChange: (cb: (fullScreen: boolean) => void) => () => void;
      toggleMiniMode: () => Promise<boolean>;
      onMiniModeChange: (cb: (mini: boolean) => void) => () => void;
      toggleDesktopLyrics: () => Promise<boolean>;
      closeDesktopLyrics: () => void;
      syncLyricsState: (data: {
        title: string;
        artist: string;
        lyrics: string;
        currentTime: number;
        duration: number;
        isPlaying: boolean;
      }) => void;
      onLyricsStateUpdate: (cb: (data: any) => void) => () => void;
      sendLyricsControl: (action: string) => void;
      onPlayerControl: (cb: (action: string) => void) => () => void;
      showItemInFolder: (filePath: string) => Promise<boolean>;
      selectDirectory: () => Promise<string | null>;
      scanLibrary: (dirPath: string) => Promise<{ success: boolean; songs: Song[]; error?: string }>;
      readLyricsFile: (filePath: string) => Promise<string | null>;
      onScanProgress: (cb: (data: { current: number; total: number }) => void) => () => void;
    };
  }
}
