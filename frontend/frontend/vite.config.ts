import { defineConfig } from 'vite'
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
  }
})
