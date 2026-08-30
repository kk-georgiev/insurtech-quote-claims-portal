import type { RouteObject } from 'react-router';
import { RootLayout } from './RootLayout';
import { HealthStatus } from './HealthStatus';
import { RoleGuard } from './RoleGuard';
import { RegisterForm } from '../features/auth/RegisterForm';
import { LoginForm } from '../features/auth/LoginForm';
import { ClientShell } from '../features/shells/client/ClientShell';
import { AgentShell } from '../features/shells/agent/AgentShell';
import { LiquidatorShell } from '../features/shells/liquidator/LiquidatorShell';
import { AdministratorShell } from '../features/shells/administrator/AdministratorShell';

// React Router v8 owns all routing (AD-10). Story 2.2 adds the four
// role navigation shells (client at `index`, three staff routes) as bare
// targets for role-based post-login routing; Story 2.3 fills in their real
// content and Story 2.4 nests each shell under a `RoleGuard` instance so
// only its own role can render it. `HealthStatus` (the Story 1.1 backend
// round-trip) moves off `index` to `/health`. `health`/`register`/`login`
// stay unguarded — they are not role-restricted.
//
// This module is a pure route table with no side effects — `App.tsx`
// instantiates the router. Tests mount the same table under
// `createMemoryRouter` and assert where a login lands.
export const routes: RouteObject[] = [
  {
    path: '/',
    element: <RootLayout />,
    children: [
      {
        element: <RoleGuard role="CLIENT" />,
        children: [{ index: true, element: <ClientShell /> }],
      },
      { path: 'health', element: <HealthStatus /> },
      {
        element: <RoleGuard role="AGENT" />,
        children: [{ path: 'agent', element: <AgentShell /> }],
      },
      {
        element: <RoleGuard role="LIQUIDATOR" />,
        children: [{ path: 'liquidator', element: <LiquidatorShell /> }],
      },
      {
        element: <RoleGuard role="ADMINISTRATOR" />,
        children: [{ path: 'administrator', element: <AdministratorShell /> }],
      },
      { path: 'register', element: <RegisterForm /> },
      { path: 'login', element: <LoginForm /> },
    ],
  },
];
