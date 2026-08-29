import { Link, Outlet } from 'react-router';
import { useTranslation } from 'react-i18next';
import { LanguageToggle } from './LanguageToggle';

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
 * from every screen. The nav is deliberately still *not* auth-aware and has
 * no logout - that is a separate proposed story (Epic 2 retrospective item
 * 5), kept out of Story 3.1 so this file changes for one reason only.
 *
 * Story 3.2a translated everything below `<Outlet />` too - the auth forms,
 * quote flow, and role shells. What is still English is the error and
 * validation messaging, which Story 3.2b owns.
 */
export function RootLayout() {
  const { t } = useTranslation();

  return (
    <div>
      <header>
        <h1>{t('app.title')}</h1>
        <nav>
          <Link to="/register">{t('app.nav.register')}</Link>
          <Link to="/login">{t('app.nav.login')}</Link>
          <Link to="/health">{t('app.nav.health')}</Link>
        </nav>
        <LanguageToggle />
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}
