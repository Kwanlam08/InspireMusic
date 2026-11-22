import React from 'react';

interface LiquidGlassSurfaceProps {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
  hoverEffect?: boolean;
}

/**
 * 对应 Inspire Music 的 LiquidGlassSurface：
 * 采用硬件加速的 backdrop-filter 高斯模糊、微白高光内渐变与极细微边框。
 */
export const LiquidGlassSurface: React.FC<LiquidGlassSurfaceProps> = ({
  children,
  className = '',
  onClick,
  hoverEffect = false,
}) => {
  return (
    <div
      onClick={onClick}
      className={`
        relative overflow-hidden
        bg-white/[0.04] backdrop-blur-2xl
        border border-white/[0.12]
        shadow-[0_12px_40px_rgba(0,0,0,0.35),inset_0_1px_0_rgba(255,255,255,0.18)]
        transition-all duration-300
        ${hoverEffect ? 'hover:bg-white/[0.08] hover:border-white/[0.2] hover:shadow-[0_16px_50px_rgba(0,0,0,0.45),inset_0_1px_0_rgba(255,255,255,0.3)] hover:-translate-y-0.5 active:translate-y-0' : ''}
        ${onClick ? 'cursor-pointer' : ''}
        ${className}
      `}
    >
      {/* 玻璃微光内涂层 (Wash highlight) */}
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-white/[0.06] via-transparent to-black/[0.08]" />
      <div className="relative z-10">{children}</div>
    </div>
  );
};

interface GlassButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  active?: boolean;
  size?: 'sm' | 'md' | 'lg' | 'icon';
}

export const GlassButton: React.FC<GlassButtonProps> = ({
  children,
  className = '',
  active = false,
  size = 'md',
  ...props
}) => {
  const sizeClasses = {
    sm: 'px-3 py-1.5 text-xs rounded-full',
    md: 'px-4 py-2 text-sm rounded-full',
    lg: 'px-6 py-3 text-base rounded-full',
    icon: 'p-2.5 rounded-full aspect-square flex items-center justify-center',
  }[size];

  return (
    <button
      className={`
        relative group overflow-hidden transition-all duration-200 select-none
        border backdrop-blur-xl flex items-center gap-2 font-medium
        ${
          active
            ? 'bg-white text-black border-white shadow-lg shadow-white/20'
            : 'bg-white/[0.07] text-white/90 border-white/[0.12] hover:bg-white/[0.14] hover:border-white/[0.22] hover:text-white active:scale-95'
        }
        ${sizeClasses}
        ${className}
      `}
      {...props}
    >
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-white/[0.05] to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
      <span className="relative z-10 flex items-center gap-2">{children}</span>
    </button>
  );
};
