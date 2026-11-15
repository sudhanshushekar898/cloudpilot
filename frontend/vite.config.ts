import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The dev server proxies /api to Spring Boot, so the browser sees one origin and
// we don't need CORS. In Docker, nginx does the same job.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // The 5 s default is too tight for userEvent typing on a loaded machine or CI runner.
    testTimeout: 15_000,
  },
})
