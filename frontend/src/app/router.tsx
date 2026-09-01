import type { RouteObject } from 'react-router';
import { RootLayout } from './RootLayout';
import { HealthStatus } from './HealthStatus';
import { RoleGuard } from './RoleGuard';
import { GuestGuard } from './GuestGuard';
import { RegisterForm } from '../features/auth/RegisterForm';
import { LoginForm } from '../features/auth/LoginForm';
import { ClientShell } from '../features/shells/client/ClientShell';
import { MyQuotes } from '../features/quote/MyQuotes';
import { QuoteDetail } from '../features/quote/QuoteDetail';
import { MyPolicies } from '../features/policy/MyPolicies';
import { PolicyDetail } from '../features/policy/PolicyDetail';
import { AgentShell } from '../features/shells/agent/AgentShell';
import { LiquidatorShell } from '../features/shells/liquidator/LiquidatorShell';
import { AdministratorShell } from '../features/shells/administrator/AdministratorShell';

// React Router v8 owns all routing (AD-10). Story 2.2 adds the four
// role navigation shells (client at `index`, three staff routes) as bare
// targets for role-based post-login routing; Story 2.3 fills in their real
// content and Story 2.4 nests each shell under a `RoleGuard` instance so
// only its own role can render it. `HealthStatus` (the Story 1.1 backend
// round-trip) moves off `index` to `/health`. `health` stays unguarded in
// both directions — not role-restricted, and reachable whether or not the
// visitor is authenticated. `register`/`login` are Story 7.2's `GuestGuard`
// now: unguarded against role (any anonymous or session-less visitor
// reaches them), but gated against an *already-authenticated* visitor, who
// is sent to their own role home instead.
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
        // Story 6.3: /quotes and /quotes/:id join the client shell under
        // the same CLIENT-only guard - no new guard logic (UX EXPERIENCE.md,
        // Information Architecture).
        element: <RoleGuard role="CLIENT" />,
        children: [
          { index: true, element: <ClientShell /> },
          { path: 'quotes', element: <MyQuotes /> },
          { path: 'quotes/:id', element: <QuoteDetail /> },
          // Story 8.3: /policies and /policies/:id join the same CLIENT-only
          // guard as the quote screens - again no new guard logic.
          { path: 'policies', element: <MyPolicies /> },
          { path: 'policies/:id', element: <PolicyDetail /> },
        ],
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
      {
        element: <GuestGuard />,
        children: [
          { path: 'register', element: <RegisterForm /> },
          { path: 'login', element: <LoginForm /> },
        ],
      },
    ],
  },
];
