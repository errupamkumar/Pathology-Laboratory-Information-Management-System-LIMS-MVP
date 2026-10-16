/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          primary: '#23408e',       // rgb(35, 64, 142) - Top navbar, main CTAs, active states
          secondary: '#385399',     // rgb(56, 83, 153) - Secondary buttons, subtle hovers
          panic: '#ed1b24',         // rgb(237, 27, 36) - Critical out-of-range values, STAT
          grid: '#cccccc',          // rgb(204, 204, 204) - Table gridlines, card borders
          canvas: '#ffffff',        // Primary background, printable report
          surface: '#f8fafc',
          card: '#ffffff',
          dark: '#0f172a'
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace']
      },
      keyframes: {
        'toast-in': {
          '0%': { opacity: '0', transform: 'translateY(8px) scale(0.98)' },
          '100%': { opacity: '1', transform: 'translateY(0) scale(1)' }
        }
      },
      animation: {
        'toast-in': 'toast-in 0.18s ease-out'
      }
    },
  },
  plugins: [],
}
