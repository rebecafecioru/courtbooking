import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // In development the frontend calls /api on its own address and Vite
    // passes it on to Spring Boot, so the browser sees one site (no CORS).
    // changeOrigin: false keeps the browser's Host header: Spring compares it
    // with the Origin header, and a mismatch makes it refuse POSTs as cross-site.
    // (Also used by `npm run preview`.)
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  // npm test: components rendered in a simulated browser (jsdom), with
  // fetch faked by src/test/fakeBackend.js, so no backend is needed
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
  },
})
