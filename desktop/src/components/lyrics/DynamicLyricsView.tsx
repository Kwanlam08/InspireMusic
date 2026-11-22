import React, { useEffect, useRef, useMemo } from 'react';
import { useMusic } from '../../context/MusicContext';
import { parseLrc, findActiveLyricIndex } from '../../services/lrcParser';

interface DynamicLyricsViewProps {
  className?: string;
  fontSize?: number;
}

export const DynamicLyricsView: React.FC<DynamicLyricsViewProps> = ({
  className = '',
  fontSize = 28,
}) => {
  const { currentSong, currentTime, seek } = useMusic();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const activeLineRef = useRef<HTMLDivElement | null>(null);

  const parsedLyrics = useMemo(() => {
    if (!currentSong?.lyrics) return [];
    return parseLrc(currentSong.lyrics);
  }, [currentSong?.lyrics]);

  const activeIndex = useMemo(() => {
    return findActiveLyricIndex(parsedLyrics, currentTime);
  }, [parsedLyrics, currentTime]);

  // Smooth scroll active lyric into view
  useEffect(() => {
    if (activeLineRef.current && containerRef.current) {
      activeLineRef.current.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
      });
    }
  }, [activeIndex]);

  if (!currentSong) {
    return (
      <div className="h-full flex items-center justify-center text-white/40">
        <p>未在播放任何歌曲</p>
      </div>
    );
  }

  if (parsedLyrics.length === 0) {
    return (
      <div className="h-full flex flex-col items-center justify-center text-white/50 gap-3 select-none">
        <div className="w-16 h-16 rounded-full bg-white/[0.06] border border-white/10 flex items-center justify-center">
          <span className="text-2xl font-serif">“</span>
        </div>
        <p className="text-lg font-medium text-white/70">暂无同步歌词</p>
        <p className="text-xs text-white/40">支持同名 .lrc 文件或音频内嵌歌词</p>
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      className={`h-full overflow-y-auto px-6 py-32 space-y-7 scroll-smooth scrollbar-none select-none ${className}`}
      style={{
        maskImage: 'linear-gradient(to bottom, transparent 0%, black 15%, black 85%, transparent 100%)',
        WebkitMaskImage: 'linear-gradient(to bottom, transparent 0%, black 15%, black 85%, transparent 100%)',
      }}
    >
      {parsedLyrics.map((line, idx) => {
        const isActive = idx === activeIndex;
        const distance = Math.abs(idx - activeIndex);

        // Apple Music blur falloff logic
        let blurStyle = 'blur(0px)';
        let opacityStyle = 1;
        let scaleStyle = 'scale(1)';

        if (!isActive) {
          if (distance === 1) {
            blurStyle = 'blur(1.5px)';
            opacityStyle = 0.55;
            scaleStyle = 'scale(0.96)';
          } else if (distance === 2) {
            blurStyle = 'blur(3px)';
            opacityStyle = 0.35;
            scaleStyle = 'scale(0.93)';
          } else {
            blurStyle = 'blur(4px)';
            opacityStyle = 0.2;
            scaleStyle = 'scale(0.90)';
          }
        } else {
          scaleStyle = 'scale(1.05)';
        }

        return (
          <div
            key={`${line.time}_${idx}`}
            ref={isActive ? activeLineRef : null}
            onClick={() => seek(line.time)}
            className={`cursor-pointer transition-all duration-700 ease-out origin-left py-2 text-left group`}
            style={{
              filter: blurStyle,
              opacity: opacityStyle,
              transform: scaleStyle,
            }}
          >
            <p
              className={`font-bold tracking-tight leading-relaxed transition-all duration-500 group-hover:!opacity-95 group-hover:!filter-none ${
                isActive
                  ? 'text-white drop-shadow-[0_0_30px_rgba(255,255,255,0.45)]'
                  : 'text-white/80 group-hover:text-white'
              }`}
              style={{ fontSize: `${fontSize}px` }}
            >
              {line.text}
            </p>

            {line.translation && (
              <p
                className={`mt-1 font-normal tracking-wide transition-all ${
                  isActive ? 'text-white/80 text-lg' : 'text-white/40 text-base'
                }`}
              >
                {line.translation}
              </p>
            )}
          </div>
        );
      })}
    </div>
  );
};
