import React, { useState } from 'react';
import { MusicProvider, useMusic } from './context/MusicContext';
import { TitleBar } from './components/common/TitleBar';
import { Sidebar, NavTab } from './components/sidebar/Sidebar';
import { LibraryView } from './views/LibraryView';
import { InspireView } from './views/InspireView';
import { DiaryView } from './views/DiaryView';
import { SettingsView } from './views/SettingsView';
import { MiniPlayer } from './components/player/MiniPlayer';
import { NowPlayingModal } from './components/player/NowPlayingModal';
import { MiniPlayerMode } from './components/player/MiniPlayerMode';
import { DesktopLyricsWindow } from './components/lyrics/DesktopLyricsWindow';
import { FluidMeshGradient } from './components/common/FluidMeshGradient';
import { ErrorBoundary } from './components/common/ErrorBoundary';

const MainLayout: React.FC = () => {
  const [currentTab, setCurrentTab] = useState<NavTab>('library');
  const { currentSong, setIsNowPlayingOpen, isMiniMode } = useMusic();

  if (isMiniMode) {
    return <MiniPlayerMode />;
  }

  return (
    <div className="flex flex-col w-screen h-screen bg-[#f8f9fa] text-neutral-900 dark:bg-[#0c0c11] dark:text-white overflow-hidden relative transition-colors duration-300">
      {/* Dynamic Ambient Fluid Mesh in the Background */}
      <FluidMeshGradient coverUrl={currentSong?.coverUrl} intensity={0.25} className="opacity-40 dark:opacity-40" />

      {/* Windows Frameless TitleBar */}
      <TitleBar />

      {/* Main Workspace (Sidebar + Views) */}
      <div className="flex-1 flex overflow-hidden relative z-10">
        <Sidebar currentTab={currentTab} onSelectTab={setCurrentTab} />

        <main className="flex-1 h-full overflow-hidden relative">
          <div key={currentTab} className="h-full w-full view-transition overflow-hidden">
            {currentTab === 'library' && <LibraryView />}
            {currentTab === 'inspire' && <InspireView />}
            {currentTab === 'diary' && <DiaryView />}
            {currentTab === 'settings' && <SettingsView />}
          </div>
        </main>
      </div>

      {/* Bottom Floating MiniPlayer */}
      <MiniPlayer />

      {/* Fullscreen Apple Music Now Playing Modal */}
      <ErrorBoundary fallbackTitle="正在播放页面加载异常" onReset={() => setIsNowPlayingOpen(false)}>
        <NowPlayingModal />
      </ErrorBoundary>
    </div>
  );
};

export default function App() {
  const isDesktopLyrics = new URLSearchParams(window.location.search).get('mode') === 'desktop-lyrics';

  if (isDesktopLyrics) {
    return <DesktopLyricsWindow />;
  }

  return (
    <MusicProvider>
      <MainLayout />
    </MusicProvider>
  );
}
