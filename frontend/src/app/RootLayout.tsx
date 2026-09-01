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

  // `min-h-11` (44px) only below `sm`: a bare inline link is ~20px tall, too
  // small to hit reliably on a phone (Story 5.5). The links carry no
  // background, so the extra height is invisible — and it is dropped again at
  // `sm:` so the desktop header keeps its original proportions.
  const navLinkClass =
    'inline-flex min-h-11 items-center text-sm font-medium text-white/80 transition-colors hover:text-white sm:min-h-0';

  return (
    <div className="flex min-h-screen flex-col bg-surface-muted font-sans text-text">
      <header className="bg-primary text-white">
        {/* Story 6.3 (UX-DR13): matches `<main>`'s `max-w-2xl` so the header
            and content share a left edge - the misalignment Story 5.4/5.5
            deferred. Previously `max-w-5xl`, to keep the long Bulgarian
            title on one line; the existing `flex-wrap` already handles the
            narrower container by wrapping the nav row below the title
            rather than wrapping the title's own text, so the title itself
            still never breaks mid-word. */}
        <div className="mx-auto flex w-full max-w-2xl flex-wrap items-center justify-between gap-4 px-4 py-3 sm:px-6 sm:py-4">
          <h1 className="text-base font-semibold tracking-tight sm:text-lg">{t('app.title')}</h1>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 sm:gap-x-6 sm:gap-y-3">
            <nav className="flex flex-wrap items-center gap-x-4 gap-y-2 sm:gap-x-5">
              {currentRole === 'CLIENT' && (
                <>
                  <Link className={navLinkClass} to="/quotes">
                    {t('app.nav.myQuotes')}
                  </Link>
                  {/* Story 8.3. The nav wraps rather than collapsing behind
                      a disclosure control, so a fourth entry costs a row on
                      a phone, not a menu. */}
                  <Link className={navLinkClass} to="/policies">
                    {t('app.nav.myPolicies')}
                  </Link>
                </>
              )}
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
      <main className="mx-auto my-0 w-full max-w-2xl flex-1 px-4 py-6 sm:px-6 sm:py-10">
        <Outlet />
      </main>
    </div>
  );
}
