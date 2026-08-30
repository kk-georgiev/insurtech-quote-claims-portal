import { createBrowserRouter, RouterProvider } from 'react-router';
import { routes } from './router';

// The one live browser-history router for the running app. `router.tsx`
// stays a side-effect-free route table so tests can mount it under
// `createMemoryRouter` without spinning up browser history on import.
const router = createBrowserRouter(routes);

export function App() {
  return <RouterProvider router={router} />;
}
