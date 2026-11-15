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
    coverage: {
      provider: 'v8',
      include: ['src/**'],
      exclude: ['src/main.tsx', 'src/test/**', 'src/**/*.test.*'],
      reporter: ['text-summary', 'html', 'lcov'],
      // Measured at 94.6% lines / 83.9% branches when the gate was added; the gate sits a little
      // below so normal changes pass but a large untested feature fails CI.
      thresholds: { lines: 85, statements: 85, functions: 85, branches: 75 },
    },
  },
})
