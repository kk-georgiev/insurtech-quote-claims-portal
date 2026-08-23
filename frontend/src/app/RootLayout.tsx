import { Link, Outlet } from 'react-router';

/**
 * Root layout. Near-empty this story (AD-10: router owns routing, routes
 * are near-empty this milestone) - grows role-based navigation shell etc.
 * in later stories. The `/register` link is a plain, unstyled entry point
 * (Story 1.2) - without it the route was only reachable by typing the URL
 * directly; a real nav is a later story's job.
 */
export function RootLayout() {
  return (
    <div>
      <header>
        <h1>Motor Insurance Quote &amp; Claims Portal</h1>
        <nav>
          <Link to="/register">Register</Link>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
