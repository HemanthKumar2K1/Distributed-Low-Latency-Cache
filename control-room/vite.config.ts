import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dashboard talks to loadtest-service. In dev, Vite forwards /api to it,
// so the browser sees one origin and no CORS setup is needed.
// The load tester runs inside Product Catalogue on 8080. Cache System (7001) is never called from here.
const LOADTEST_SERVICE = process.env.LOADTEST_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': LOADTEST_SERVICE } },
  preview: { port: 4173, proxy: { '/api': LOADTEST_SERVICE } },
});
