import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      port: 5174,
      proxy: {
        '/api': env.VITE_BACKEND_URL || 'http://localhost:8000',
        '/ws': {
          target: env.VITE_BACKEND_WS_URL || 'ws://localhost:8000',
          ws: true,
        },
      },
    },
  }
})
