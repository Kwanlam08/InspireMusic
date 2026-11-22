/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        glass: {
          bg: 'rgba(18, 18, 22, 0.72)',
          surface: 'rgba(255, 255, 255, 0.06)',
          hover: 'rgba(255, 255, 255, 0.12)',
          border: 'rgba(255, 255, 255, 0.12)',
          highlight: 'rgba(255, 255, 255, 0.25)',
          pill: 'rgba(255, 255, 255, 0.08)',
        },
        apple: {
          pink: '#FA2D48',
          purple: '#A855F7',
          blue: '#38BDF8',
          orange: '#FB923C'
        }
      },
      backdropBlur: {
        '2xl': '40px',
        '3xl': '64px',
      },
      boxShadow: {
        'liquid': '0 20px 50px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.15)',
        'glass-card': '0 8px 32px 0 rgba(0, 0, 0, 0.36), inset 0 1px 0 0 rgba(255, 255, 255, 0.1)',
        'capsule': '0 10px 30px rgba(0, 0, 0, 0.3), inset 0 0.5px 0.5px rgba(255, 255, 255, 0.3)',
      },
      animation: {
        'pulse-slow': 'pulse 4s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'mesh-spin': 'meshSpin 20s ease-in-out infinite alternate',
      },
      keyframes: {
        meshSpin: {
          '0%': { transform: 'scale(1) rotate(0deg)' },
          '50%': { transform: 'scale(1.2) rotate(180deg)' },
          '100%': { transform: 'scale(1) rotate(360deg)' },
        }
      }
    },
  },
  plugins: [],
}
