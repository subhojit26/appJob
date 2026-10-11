import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Build output goes straight into Spring Boot's static resources so the JAR serves the SPA.
// In dev, `npm run dev` proxies API calls to the running Spring Boot app on :8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://127.0.0.1:8080',
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
});

