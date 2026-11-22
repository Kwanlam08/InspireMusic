import React, { useEffect, useState } from 'react';
import { extractMeshColors } from '../../services/colorExtractor';
import { MeshGradientColors } from '../../types';

interface FluidMeshGradientProps {
  coverUrl?: string | null;
  intensity?: number;
  className?: string;
  isModal?: boolean;
}

export const FluidMeshGradient: React.FC<FluidMeshGradientProps> = ({
  coverUrl,
  intensity = 0.65,
  className = '',
  isModal = false,
}) => {
  const [colors, setColors] = useState<MeshGradientColors>({
    baseBackground: '#0c0b11',
    primary: '#6b3ba7',
    secondary: '#d83a6f',
    tertiary: '#2a5bb5',
    accent: '#e87a36',
  });

  useEffect(() => {
    let active = true;
    extractMeshColors(coverUrl).then((res) => {
      if (active) {
        setColors(res);
      }
    });
    return () => {
      active = false;
    };
  }, [coverUrl]);

  return (
    <div
      className={`absolute inset-0 overflow-hidden pointer-events-none transition-colors duration-1000 ${className}`}
    >
      {/* Base background tint */}
      <div
        className={`absolute inset-0 ${isModal ? 'block' : 'hidden dark:block'} transition-colors duration-1000`}
        style={{ backgroundColor: colors.baseBackground }}
      />

      {/* Mesh Blob 1 (Primary - Top Left) */}
      <div
        className={`absolute -top-[25%] -left-[20%] w-[85vw] h-[85vw] rounded-full blur-[110px] ${isModal ? 'opacity-70' : 'opacity-25 dark:opacity-70'} transition-all duration-1000 animate-pulse-slow`}
        style={{
          background: `radial-gradient(circle, ${colors.primary} 0%, transparent 70%)`,
          transform: `scale(${intensity * 1.5})`,
        }}
      />

      {/* Mesh Blob 2 (Secondary - Bottom Right) */}
      <div
        className={`absolute -bottom-[20%] -right-[15%] w-[80vw] h-[80vw] rounded-full blur-[130px] ${isModal ? 'opacity-60' : 'opacity-20 dark:opacity-60'} transition-all duration-1000`}
        style={{
          background: `radial-gradient(circle, ${colors.secondary} 0%, transparent 68%)`,
          transform: `scale(${intensity * 1.4})`,
        }}
      />

      {/* Mesh Blob 3 (Tertiary - Center Left) */}
      <div
        className={`absolute top-[35%] -left-[10%] w-[60vw] h-[60vw] rounded-full blur-[90px] ${isModal ? 'opacity-50' : 'opacity-15 dark:opacity-50'} transition-all duration-1000`}
        style={{
          background: `radial-gradient(circle, ${colors.tertiary} 0%, transparent 65%)`,
        }}
      />

      {/* Mesh Blob 4 (Accent - Center Right) */}
      <div
        className={`absolute top-[20%] right-[10%] w-[65vw] h-[65vw] rounded-full blur-[120px] ${isModal ? 'opacity-45' : 'opacity-15 dark:opacity-45'} transition-all duration-1000`}
        style={{
          background: `radial-gradient(circle, ${colors.accent} 0%, transparent 70%)`,
        }}
      />

      {/* Darkening overlay for immersion in modal or dark mode */}
      <div className={`absolute inset-0 ${isModal ? 'block' : 'hidden dark:block'} bg-black/40 backdrop-blur-[2px]`} />
    </div>
  );
};
