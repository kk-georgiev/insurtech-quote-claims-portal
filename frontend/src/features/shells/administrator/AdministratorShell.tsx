import { useTranslation } from 'react-i18next';

/**
 * ADMINISTRATOR navigation shell, mounted at `/administrator`. Story 2.3
 * turns Story 2.2's bare route target into a static, clearly
 * Administrator-labeled placeholder screen: a heading naming the *area* plus
 * one coming-soon line. It labels the area and not the viewer — there is no
 * route guard until Story 2.4, so an anonymous visitor can reach this URL
 * and the copy must not assert an auth state the app has not verified.
 *
 * Static and non-interactive by contract (epics.md AC, PRD §4.2): no
 * buttons, links, inputs, or handlers, and no per-role sub-navigation.
 */
export function AdministratorShell() {
  const { t } = useTranslation();

  return (
    <section data-testid="administrator-shell" aria-labelledby="administrator-shell-heading">
      <h2 id="administrator-shell-heading">{t('shells.administrator.heading')}</h2>
      <p>{t('shells.administrator.comingSoon')}</p>
    </section>
  );
}
