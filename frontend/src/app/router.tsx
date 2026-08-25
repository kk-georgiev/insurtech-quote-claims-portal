import { createBrowserRouter } from 'react-router';
import { RootLayout } from './RootLayout';
import { HealthStatus } from './HealthStatus';
import { RegisterForm } from '../features/auth/RegisterForm';
import { LoginForm } from '../features/auth/LoginForm';

// React Router v8 owns all routing (AD-10). Story 1.2 added the first real
// feature route (client self-registration); Story 1.3 adds login. The
// role-guard wrapper and remaining feature/shell routes are added by later
// stories.
export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, element: <HealthStatus /> },
      { path: 'register', element: <RegisterForm /> },
      { path: 'login', element: <LoginForm /> },
    ],
  },
]);
