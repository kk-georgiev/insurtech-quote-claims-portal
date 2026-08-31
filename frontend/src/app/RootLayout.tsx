import { Link, Outlet, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { LanguageToggle } from './LanguageToggle';
import { Button } from '../components/ui/Button';
import { clearToken } from '../api/authToken';
import { getCurrentRole } from './roleHome';

/**
 * Root layout. Near-empty this milestone (AD-10: router owns routing) - the
 * `/register`, `/login`, and `/health` links are plain, unstyled entry points
 * (Stories 1.2/1.3/2.2) without which those routes would only be reachable by
 * typing the URL. `/health` was the index route (Story 1.1) until Story 2.2
 * moved it here to free `/` for the client shell.
 *
 * Story 3.1 makes this header the app's only translated surface: the title
 * and the three nav labels come from the `app.*` i18n namespace, and
 * `LanguageToggle` sits alongside them so the language control is reachable
 * from every screen.
 *
 * Story 3.2a translated everything below `<Outlet />` too - the auth forms,
 * quote flow, and role shells. What is still English is the error and
 * validation messaging, which Story 3.2b owns.
 *
 * Story 2.5 makes the nav auth-aware: `getCurrentRole()` (`./roleHome`) is
 * derived fresh on every render, same pattern as `RoleGuard` - no separate
 * tracked auth state. Authenticated visitors see a Logout control in place
 * of Register/Login; clicking it clears the stored token and navigates to
 * `/login`, client-side only (AD-3: no backend call, no revocation this
 * milestone). Health stays visible either way.
 *
 * Story 5.4 restyles this shared chrome with the Milestone 2 design system:
 * a navy (`bg-primary`) header, Inter (`font-sans`), nav links as quiet
 * white text, the Logout control routed through `Button` (its `ghost`
 * variant, for dark surfaces), and `LanguageToggle` as a segmented pill.
 * Markup semantics (`<header>`/`<h1>`/`<nav>`/`<main>`) and every behaviour
 * are unchanged.
 */
export function RootLayout() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const currentRole = getCurrentRole();

  function handleLogout() {
    clearToken();
    navigate('/login', { replace: true });
  }

  const navLinkClass = 'text-sm font-medium text-white/80 transition-colors hover:text-white';

  return (
    <div className="flex min-h-screen flex-col bg-surface-muted font-sans text-text">
      <header className="bg-primary text-white">
        <div className="mx-auto flex w-full max-w-5xl flex-wrap items-center justify-between gap-4 px-6 py-4">
          <h1 className="text-lg font-semibold tracking-tight">{t('app.title')}</h1>
          <div className="flex flex-wrap items-center gap-x-6 gap-y-3">
            <nav className="flex flex-wrap items-center gap-x-5 gap-y-2">
              {currentRole ? (
                <Button variant="ghost" size="sm" onClick={handleLogout}>
                  {t('app.nav.logout')}
                </Button>
              ) : (
                <>
                  <Link className={navLinkClass} to="/register">
                    {t('app.nav.register')}
                  </Link>
                  <Link className={navLinkClass} to="/login">
                    {t('app.nav.login')}
                  </Link>
                </>
              )}
              <Link className={navLinkClass} to="/health">
                {t('app.nav.health')}
              </Link>
            </nav>
            <LanguageToggle />
          </div>
        </div>
      </header>
      <main className="mx-auto my-0 w-full max-w-2xl flex-1 px-6 py-10">
        <Outlet />
      </main>
    </div>
  );
}
