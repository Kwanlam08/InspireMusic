import React, { useState } from 'react';
import { useMusic } from '../context/MusicContext';
import { Sparkles, Send, Coffee, Moon, CloudRain, Sun, Compass, Play } from 'lucide-react';

export const InspireView: React.FC = () => {
  const { songs, playSong } = useMusic();
  const [prompt, setPrompt] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [recommendationResult, setRecommendationResult] = useState<{
    title: string;
    description: string;
    songIds: string[];
  } | null>(null);

  const presetThemes = [
    {
      title: '深夜静谧工位',
      subtitle: '适合注意力集中与夜间深思',
      icon: Moon,
      gradient: 'from-purple-900/60 to-indigo-900/40',
      keywords: ['ambient', 'r&b', 'calm'],
    },
    {
      title: '雨天窗边咖啡厅',
      subtitle: '温暖低保真与木吉他慢节奏',
      icon: CloudRain,
      gradient: 'from-blue-900/60 to-slate-900/40',
      keywords: ['lo-fi', 'chill', 'rain'],
    },
    {
      title: '黄昏海风兜风',
      subtitle: '惬意落日合成波与律动微风',
      icon: Sun,
      gradient: 'from-orange-900/60 to-pink-900/40',
      keywords: ['synth', 'pop', 'summer'],
    },
    {
      title: '周末清晨醒神',
      subtitle: '清新民谣与明媚日光旋律',
      icon: Coffee,
      gradient: 'from-emerald-900/60 to-teal-900/40',
      keywords: ['folk', 'acoustic', 'morning'],
    },
  ];

  const handleGenerate = (customText?: string) => {
    const textToUse = customText || prompt;
    if (!textToUse) return;

    setIsGenerating(true);
    setTimeout(() => {
      // Pick 3-5 random songs from current library as smart generated playlist
      const shuffled = [...songs].sort(() => 0.5 - Math.random());
      const selected = shuffled.slice(0, Math.min(4, songs.length));

      setRecommendationResult({
        title: textToUse.length > 15 ? textToUse.slice(0, 15) + '...' : textToUse,
        description: `灵感模型为你精心挑选了 ${selected.length} 首旋律，营造出充满共鸣的听觉空间。`,
        songIds: selected.map((s) => s.id),
      });
      setIsGenerating(false);
    }, 800);
  };

  const recommendedSongs = songs.filter((s) =>
    recommendationResult?.songIds.includes(s.id)
  );

  return (
    <div className="flex-1 h-full overflow-y-auto p-8 pb-32 space-y-8 select-none text-neutral-900 dark:text-white transition-colors">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2 text-pink-500 dark:text-pink-400 font-semibold text-xs tracking-wider uppercase mb-1">
          <Sparkles className="w-3.5 h-3.5" />
          <span>AI 灵感小宇宙</span>
        </div>
        <h1 className="text-3xl font-extrabold tracking-tight text-neutral-900 dark:text-white/95">
          灵感
        </h1>
        <p className="text-sm text-neutral-500 dark:text-white/50 mt-1 max-w-xl">
          不知道听什么？用一句话、一种心情或此刻的天气，让灵感音乐为你点亮一串音符。
        </p>
      </div>

      {/* Input Box with Liquid Glass Styling (Stretches smoothly) */}
      <div className="w-full max-w-5xl">
        <div className="relative rounded-3xl p-1.5 bg-white/70 dark:bg-white/[0.05] backdrop-blur-2xl border border-black/5 dark:border-white/10 shadow-[0_10px_30px_rgba(0,0,0,0.05)] dark:shadow-[0_10px_30px_rgba(0,0,0,0.3)]">
          <div className="flex items-center gap-3 px-4 py-2">
            <Compass className="w-5 h-5 text-neutral-400 dark:text-white/40 shrink-0" />
            <input
              type="text"
              placeholder="例如：深夜敲代码想听点放松的、适合阴雨天发呆的歌..."
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleGenerate()}
              className="w-full bg-transparent text-sm text-neutral-900 dark:text-white placeholder-neutral-400 dark:placeholder-white/30 focus:outline-none"
            />
            <button
              onClick={() => handleGenerate()}
              disabled={isGenerating || !prompt.trim()}
              className="
                px-4 py-2 rounded-2xl bg-gradient-to-r from-pink-500 to-violet-600
                hover:from-pink-600 hover:to-violet-700 active:scale-95 disabled:opacity-40
                text-white text-xs font-semibold shadow-lg shadow-pink-500/20
                flex items-center gap-1.5 transition-all cursor-pointer
              "
            >
              <Send className="w-3 h-3" />
              <span>{isGenerating ? '生成中...' : '探索'}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Generated Result Card (Stretches smoothly) */}
      {recommendationResult && (
        <div className="w-full max-w-5xl rounded-3xl p-6 bg-gradient-to-br from-pink-500/10 via-purple-500/10 to-transparent border border-black/5 dark:border-white/15 backdrop-blur-2xl shadow-xl space-y-4 animate-in fade-in duration-300">
          <div className="flex items-center justify-between">
            <div>
              <span className="text-xs font-semibold text-pink-500 dark:text-pink-400">✨ 灵感生成歌单</span>
              <h3 className="text-xl font-bold text-neutral-900 dark:text-white/95 mt-0.5">
                {recommendationResult.title}
              </h3>
              <p className="text-xs text-neutral-500 dark:text-white/60 mt-1">
                {recommendationResult.description}
              </p>
            </div>
            {recommendedSongs.length > 0 && (
              <button
                onClick={() => playSong(recommendedSongs[0], recommendedSongs)}
                className="w-11 h-11 rounded-full bg-pink-500 text-white flex items-center justify-center shadow-lg hover:scale-105 active:scale-95 transition-all cursor-pointer"
                title="播放该歌单"
              >
                <Play className="w-5 h-5 fill-white translate-x-0.5" />
              </button>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
            {recommendedSongs.map((song) => (
              <div
                key={song.id}
                onClick={() => playSong(song, recommendedSongs)}
                className="flex items-center gap-3 p-3 rounded-2xl bg-white/[0.04] hover:bg-white/[0.09] border border-white/10 cursor-pointer transition-colors"
              >
                <div className="w-11 h-11 rounded-xl overflow-hidden shrink-0 border border-white/10">
                  {song.coverUrl ? (
                    <img src={song.coverUrl} alt="" className="w-full h-full object-cover" />
                  ) : (
                    <div className="w-full h-full bg-purple-600/40" />
                  )}
                </div>
                <div className="min-w-0">
                  <div className="font-semibold text-xs text-white/90 truncate">
                    {song.title}
                  </div>
                  <div className="text-[11px] text-white/40 truncate">{song.artist}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Preset Inspiration Cards */}
      <div className="space-y-4">
        <h2 className="text-xl font-bold text-white/90 tracking-tight">场景与心境</h2>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 max-w-5xl">
          {presetThemes.map((theme, i) => {
            const Icon = theme.icon;
            return (
              <div
                key={i}
                onClick={() => handleGenerate(theme.title)}
                className={`
                  group p-5 rounded-3xl cursor-pointer
                  bg-gradient-to-br ${theme.gradient}
                  border border-white/10 hover:border-white/20
                  shadow-[0_8px_30px_rgba(0,0,0,0.25)]
                  hover:-translate-y-1 hover:shadow-2xl transition-all duration-300
                  flex flex-col justify-between h-44
                `}
              >
                <div className="flex items-center justify-between">
                  <div className="w-10 h-10 rounded-2xl bg-white/10 border border-white/10 flex items-center justify-center">
                    <Icon className="w-5 h-5 text-white" />
                  </div>
                  <Sparkles className="w-4 h-4 text-white/30 group-hover:text-pink-300 transition-colors" />
                </div>

                <div>
                  <h3 className="font-bold text-base text-white/95 group-hover:text-pink-200 transition-colors">
                    {theme.title}
                  </h3>
                  <p className="text-xs text-white/50 mt-1 leading-relaxed">
                    {theme.subtitle}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
