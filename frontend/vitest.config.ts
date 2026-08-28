import { mergeConfig, defineConfig } from 'vitest/config';
import viteConfig from './vite.config';

// First frontend test toolchain (Story 2.2). Extends the app's Vite config
// (React plugin, root-level envDir) so tests resolve modules and env the
// same way the app does, then layers the jsdom + Testing Library setup on
// top. Reused by Stories 2.3, 2.4, and Epic 3.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      // Reset every `vi.fn()` (call history + implementation) before each
      // test so suites don't hand-roll `mockReset()` in `beforeEach`. Sets
      // the isolation pattern for Stories 2.3, 2.4, and Epic 3.
      mockReset: true,
      // `api/client.ts` reads `import.meta.env.VITE_API_URL` at module load
      // and warns if it is unset. `apiFetch` itself is mocked in tests, but
      // the real module is still imported (for `ApiRequestError`), so give
      // it a value to keep the suite output clean.
      env: {
        VITE_API_URL: 'http://localhost:8080',
      },
    },
  }),
);
