import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // Keeps the browser on one origin in dev, so no CORS config is needed
      // on the Spring side. `/api/**` and the SSE stream both go to Boot.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // Plain `web/dist`. Maven copies it into the jar at build time (see the
    // frontend-maven-plugin + maven-resources-plugin wiring in pom.xml) —
    // build output never lands in src/.
    outDir: 'dist',
    emptyOutDir: true,
  },
})
