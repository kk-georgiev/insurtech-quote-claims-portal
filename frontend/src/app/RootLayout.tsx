import { Link, Outlet } from 'react-router';

/**
 * Root layout. Near-empty this story (AD-10: router owns routing, routes
 * are near-empty this milestone) - grows role-based navigation shell etc.
 * in later stories. The `/register` and `/login` links are plain, unstyled
 * entry points (Stories 1.2/1.3) - without them the routes were only
 * reachable by typing the URL directly; a real nav is a later story's job.
 */
export function RootLayout() {
  return (
    <div>
      <header>
        <h1>Motor Insurance Quote &amp; Claims Portal</h1>
        <nav>
          <Link to="/register">Register</Link>
          <Link to="/login">Login</Link>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
