import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Proxy verso il backend Spring Boot in sviluppo: evita di dover configurare
// CORS lato server (SecurityConfig non lo prevede) e fa si' che il client
// STOMP possa collegarsi a /ws-kds come same-origin.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws-kds': {
        target: 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
})
