import { Link, Outlet } from 'react-router';

/**
 * Root layout. Near-empty this story (AD-10: router owns routing, routes
 * are near-empty this milestone) - grows role-based navigation shell etc.
 * in later stories. The `/register`, `/login`, and `/health` links are
 * plain, unstyled entry points (Stories 1.2/1.3/2.2) - without them the
 * routes were only reachable by typing the URL directly; a real nav is a
 * later story's job. `/health` was the index route (Story 1.1) until Story
 * 2.2 moved it here to free `/` for the client shell.
 */
export function RootLayout() {
  return (
    <div>
      <header>
        <h1>Motor Insurance Quote &amp; Claims Portal</h1>
        <nav>
          <Link to="/register">Register</Link>
          <Link to="/login">Login</Link>
          <Link to="/health">Health</Link>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
