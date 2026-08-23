import { Outlet } from 'react-router';

/**
 * Root layout. Near-empty this story (AD-10: router owns routing, routes
 * are near-empty this milestone) - grows role-based navigation shell etc.
 * in later stories.
 */
export function RootLayout() {
  return (
    <div>
      <header>
        <h1>Motor Insurance Quote &amp; Claims Portal</h1>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
