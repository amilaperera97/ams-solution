import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    proxy: {
      '/api': {
        // Overridable so start.sh can run the backend on a non-default port.
        target: process.env.BACKEND_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // e2e/ holds Playwright specs, which have their own runner.
    exclude: ['node_modules/**', 'e2e/**', 'dist/**'],
  }
})
