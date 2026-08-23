import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // Read .env* from the repo root, not frontend/ - there is a single
  // .env.example at the repo root (AD-9) shared by docker-compose and the
  // frontend, not one per project.
  envDir: fileURLToPath(new URL('..', import.meta.url)),
});
