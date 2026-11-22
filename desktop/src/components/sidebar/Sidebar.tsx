import React from 'react';
import { Sparkles, Library, Calendar, Settings, FolderPlus, Music } from 'lucide-react';
import { useMusic } from '../../context/MusicContext';
import logoImg from '../../assets/logo.png';

export type NavTab = 'inspire' | 'library' | 'diary' | 'settings';

interface SidebarProps {
  currentTab: NavTab;
  onSelectTab: (tab: NavTab) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ currentTab, onSelectTab }) => {
  const { scanFolder, isScanning, scanProgress } = useMusic();

  const navItems: { tab: NavTab; label: string; icon: React.FC<{ className?: string }> }[] = [
    { tab: 'library', label: '资料库', icon: Library },
    { tab: 'inspire', label: '灵感', icon: Sparkles },
    { tab: 'diary', label: '音乐日记', icon: Calendar },
    { tab: 'settings', label: '设置', icon: Settings },
  ];

  return (
    <aside className="w-60 h-full bg-white/80 dark:bg-black/25 backdrop-blur-2xl border-r border-neutral-200/80 dark:border-white/[0.08] flex flex-col p-4 select-none shrink-0 z-30 transition-colors">
      {/* App Branding */}
      <div className="flex items-center gap-3 px-3 py-4 mb-4">
        <div className="w-10 h-10 rounded-2xl overflow-hidden shadow-lg shadow-pink-500/20 border border-white/20 bg-black/5 dark:bg-black/10 shrink-0">
          <img src={logoImg} alt="Inspire Music" className="w-full h-full object-cover" />
        </div>
        <div>
          <h2 className="text-base font-bold tracking-tight text-neutral-900 dark:text-white/95 leading-none">
            灵感音乐
          </h2>
          <span className="text-[11px] text-neutral-500 dark:text-white/40 font-medium tracking-wide">
            Inspire Music
          </span>
        </div>
      </div>

      {/* Navigation Links */}
      <nav className="space-y-1.5 flex-1">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = currentTab === item.tab;

          return (
            <button
              key={item.tab}
              onClick={() => onSelectTab(item.tab)}
              className={`
                w-full flex items-center gap-3 px-4 py-3 rounded-2xl text-sm font-semibold transition-all duration-200
                ${
                  isActive
                    ? 'bg-white dark:bg-white/15 text-neutral-900 dark:text-white shadow-sm dark:shadow-[0_4px_20px_rgba(0,0,0,0.3)] border border-black/5 dark:border-white/20'
                    : 'text-neutral-600 dark:text-white/60 hover:text-black dark:hover:text-white hover:bg-black/5 dark:hover:bg-white/[0.07]'
                }
              `}
            >
              <Icon className={`w-4 h-4 ${isActive ? 'text-pink-500 dark:text-pink-400' : 'text-neutral-500 dark:text-white/60'}`} />
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>

      {/* Local Folder Import Button */}
      <div className="pt-4 border-t border-black/[0.06] dark:border-white/[0.08] space-y-3">
        <button
          onClick={scanFolder}
          disabled={isScanning}
          className="
            w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-2xl
            bg-black/5 dark:bg-white/[0.07] hover:bg-black/10 dark:hover:bg-white/[0.12] active:scale-98
            border border-black/5 dark:border-white/10 text-xs font-semibold text-neutral-800 dark:text-white/90
            transition-all duration-200 shadow-sm
          "
        >
          <FolderPlus className="w-4 h-4 text-pink-400" />
          <span>{isScanning ? '正在扫描音乐库...' : '导入本地文件夹'}</span>
        </button>

        {isScanning && scanProgress && (
          <div className="space-y-1 px-1">
            <div className="flex justify-between text-[10px] text-neutral-500 dark:text-white/40">
              <span>正在提取元数据...</span>
              <span>
                {scanProgress.current}/{scanProgress.total}
              </span>
            </div>
            <div className="h-1 bg-white/10 rounded-full overflow-hidden">
              <div
                className="h-full bg-pink-500 rounded-full transition-all duration-150"
                style={{
                  width: `${Math.min(100, (scanProgress.current / scanProgress.total) * 100)}%`,
                }}
              />
            </div>
          </div>
        )}
      </div>
    </aside>
  );
};
