import React, { useState } from 'react';
import { useMusic } from '../context/MusicContext';
import { Calendar as CalendarIcon, Sparkles, ChevronDown, ChevronUp, Music } from 'lucide-react';

type DiaryTab = 'day' | 'week' | 'month';

export const DiaryView: React.FC = () => {
  const { diarySummaries, playSong, songs } = useMusic();
  const [activeTab, setActiveTab] = useState<DiaryTab>('day');
  const [expandedDate, setExpandedDate] = useState<string | null>(
    diarySummaries[0]?.dateStr || null
  );

  return (
    <div className="flex-1 h-full overflow-y-auto p-8 pb-32 space-y-7 select-none">
      {/* Title & Description */}
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight text-neutral-900 dark:text-white/95">
          音乐日记
        </h1>
        <p className="text-xs text-neutral-500 dark:text-white/40 mt-1 max-w-lg leading-relaxed">
          日记、周记和月记会保存在本地，安静记录每一次与好旋律相遇的时刻。
        </p>
      </div>

      {/* Segmented Switch: 日记 / 周记 / 月记 (Screen-20.png style) */}
      <div className="inline-flex p-1 rounded-2xl bg-neutral-200/70 dark:bg-white/[0.05] border border-neutral-300/60 dark:border-white/[0.08] backdrop-blur-xl">
        {(['day', 'week', 'month'] as DiaryTab[]).map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`
              px-8 py-2 rounded-xl text-xs font-semibold transition-all duration-200
              ${
                activeTab === tab
                  ? 'bg-white text-neutral-900 shadow-sm'
                  : 'text-neutral-600 dark:text-white/60 hover:text-neutral-900 dark:hover:text-white'
              }
            `}
          >
            {tab === 'day' ? '日记' : tab === 'week' ? '周记' : '月记'}
          </button>
        ))}
      </div>

      {/* Diary Entries List */}
      <div className="space-y-6 w-full max-w-5xl">
        {diarySummaries.map((summary) => {
          const isExpanded = expandedDate === summary.dateStr;

          return (
            <div
              key={summary.dateStr}
              className="
                p-6 rounded-3xl
                bg-white/80 dark:bg-white/[0.03] backdrop-blur-2xl
                border border-black/[0.06] dark:border-white/[0.09]
                shadow-[0_4px_20px_rgba(0,0,0,0.04)] dark:shadow-[0_10px_30px_rgba(0,0,0,0.25)]
                space-y-5 transition-all
              "
            >
              {/* Header Info */}
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3.5">
                  <div className="w-11 h-11 rounded-2xl bg-teal-500/15 border border-teal-500/25 flex items-center justify-center text-teal-600 dark:text-teal-400">
                    <CalendarIcon className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-bold text-lg text-neutral-900 dark:text-white/95 leading-snug">
                      {summary.displayDate}
                    </h3>
                    <p className="text-xs text-neutral-500 dark:text-white/40 mt-0.5">
                      {summary.playCount} 次播放 · {summary.songCount} 首歌 · {summary.totalDurationMinutes}m
                    </p>
                  </div>
                </div>

                <div className="w-9 h-9 rounded-full bg-black/[0.04] dark:bg-white/[0.06] border border-black/5 dark:border-white/10 flex items-center justify-center text-pink-500 dark:text-pink-400" title="AI 听歌分析">
                  <Sparkles className="w-4 h-4" />
                </div>
              </div>

              {/* Two Pill Stat Cards (Screen-20.png style) */}
              <div className="grid grid-cols-2 gap-3">
                <div className="p-3.5 rounded-2xl bg-neutral-100/80 dark:bg-white/[0.03] border border-neutral-200/60 dark:border-white/[0.07]">
                  <span className="text-[11px] text-neutral-400 dark:text-white/40 font-medium">常听歌曲</span>
                  <div className="font-bold text-sm text-neutral-800 dark:text-white/90 truncate mt-1">
                    {summary.topSong ? summary.topSong.title : '暂无'}
                  </div>
                </div>

                <div className="p-3.5 rounded-2xl bg-neutral-100/80 dark:bg-white/[0.03] border border-neutral-200/60 dark:border-white/[0.07]">
                  <span className="text-[11px] text-neutral-400 dark:text-white/40 font-medium">常听艺人</span>
                  <div className="font-bold text-sm text-neutral-800 dark:text-white/90 truncate mt-1">
                    {summary.topArtist ? summary.topArtist.name : '暂无'}
                  </div>
                </div>
              </div>

              {/* AI Mood Insight Banner */}
              {summary.aiAnalysis && (
                <div className="p-3.5 rounded-2xl bg-gradient-to-r from-pink-500/10 to-purple-500/10 border border-pink-500/20 dark:border-white/10 text-xs text-neutral-700 dark:text-white/70 leading-relaxed">
                  <span className="font-semibold text-pink-500 dark:text-pink-400 mr-1">✦ 灵感回眸:</span>
                  {summary.aiAnalysis}
                </div>
              )}

              {/* Song list preview */}
              <div className="space-y-2 pt-1">
                {(isExpanded ? summary.records : summary.records.slice(0, 4)).map((record, i) => {
                  const matchedSong = songs.find((s) => s.id === record.songId);

                  return (
                    <div
                      key={`${record.id}_${i}`}
                      onClick={() => matchedSong && playSong(matchedSong)}
                      className="
                        flex items-center gap-3.5 p-2 rounded-xl
                        hover:bg-black/[0.04] dark:hover:bg-white/[0.06] cursor-pointer transition-colors
                      "
                    >
                      <div className="w-10 h-10 rounded-xl overflow-hidden shrink-0 border border-black/10 dark:border-white/10 bg-neutral-100 dark:bg-white/5">
                        {matchedSong?.coverUrl ? (
                          <img
                            src={matchedSong.coverUrl}
                            alt=""
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          <div className="w-full h-full bg-violet-600/30 flex items-center justify-center">
                            <Music className="w-4 h-4 text-neutral-400 dark:text-white/40" />
                          </div>
                        )}
                      </div>

                      <div className="min-w-0 flex-1">
                        <div className="font-semibold text-xs text-neutral-900 dark:text-white/90 truncate">
                          {record.title}
                        </div>
                        <div className="text-[11px] text-neutral-500 dark:text-white/40 truncate">
                          {record.artist} · {Math.round((record.durationPlayed || 180) / 60)}m
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Expand / Collapse Button */}
              {summary.records.length > 4 && (
                <button
                  onClick={() => setExpandedDate(isExpanded ? null : summary.dateStr)}
                  className="
                    w-full py-2 flex items-center justify-center gap-1.5
                    text-xs font-semibold text-teal-600 dark:text-teal-400 hover:text-teal-700 dark:hover:text-teal-300 transition-colors
                  "
                >
                  <span>
                    {isExpanded
                      ? '收起'
                      : `展开全部 ${summary.records.length} 首`}
                  </span>
                  {isExpanded ? (
                    <ChevronUp className="w-3.5 h-3.5" />
                  ) : (
                    <ChevronDown className="w-3.5 h-3.5" />
                  )}
                </button>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};
