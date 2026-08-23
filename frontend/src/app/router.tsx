import { createBrowserRouter } from 'react-router';
import { RootLayout } from './RootLayout';
import { HealthStatus } from './HealthStatus';

// React Router v8 owns all routing (AD-10). Near-empty this story: a single
// index route proving the wiring via the health round-trip. Feature routes
// (auth, quote, role-restricted shells + the role-guard wrapper) are added
// by the stories that introduce those screens.
export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [{ index: true, element: <HealthStatus /> }],
  },
]);
