// Global test setup (Story 2.2 — first frontend test toolchain).
// - `@testing-library/jest-dom/vitest` registers the DOM matchers
//   (`toBeInTheDocument`, `toHaveTextContent`, …) and augments Vitest's
//   `expect` types.
// - RTL does not auto-cleanup unless Vitest globals are enabled (they are
//   not here — tests import `describe`/`it`/`expect` explicitly), so the
//   render cleanup is wired manually.
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';

afterEach(() => {
  cleanup();
  localStorage.clear();
});
