import React from 'react';

interface Props {
  children: React.ReactNode;
  fallbackTitle?: string;
  onReset?: () => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/80 backdrop-blur-xl text-white select-none">
          <div className="max-w-md w-full p-6 rounded-3xl bg-neutral-900 border border-white/10 shadow-2xl text-center space-y-4">
            <div className="w-12 h-12 mx-auto rounded-2xl bg-rose-500/20 text-rose-400 flex items-center justify-center text-xl font-bold">
              !
            </div>
            <div>
              <h2 className="text-lg font-bold text-white">
                {this.props.fallbackTitle || '播放器视图渲染异常'}
              </h2>
              <p className="text-xs text-white/50 mt-1 line-clamp-2">
                {this.state.error?.message || '未知错误'}
              </p>
            </div>
            <button
              onClick={() => {
                this.setState({ hasError: false, error: null });
                this.props.onReset?.();
              }}
              className="px-6 py-2.5 rounded-xl bg-white text-black text-xs font-bold hover:bg-white/90 active:scale-95 transition-all shadow-md cursor-pointer"
            >
              关闭并返回
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
