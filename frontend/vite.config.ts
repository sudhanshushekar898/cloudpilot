import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The dev server proxies /api to Spring Boot, so the browser sees one origin and
// we don't need CORS. In Docker, nginx does the same job.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
})
