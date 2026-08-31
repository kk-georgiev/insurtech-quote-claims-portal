import { useTranslation } from 'react-i18next';
import { Card } from '../../../components/ui/Card';

/**
 * ADMINISTRATOR navigation shell, mounted at `/administrator` behind a
 * `RoleGuard` (Story 2.4). Story 2.3 made it a static, clearly
 * Administrator-labeled placeholder: a heading naming the *area* plus one
 * coming-soon line. It labels the area, not the viewer.
 *
 * Static and non-interactive by contract (epics.md AC, PRD §4.2): no
 * buttons, links, inputs, or handlers, and no per-role sub-navigation.
 *
 * Story 5.4 applies the shared "workspace" look: a page-level `<h2>` naming
 * the area, then a `Card` holding the coming-soon copy. The `<section>`
 * drops the legacy `main > section` card chrome so the `Card` is the only
 * card. Copy, testid, and `aria-labelledby` wiring are unchanged.
 */
export function AdministratorShell() {
  const { t } = useTranslation();

  return (
    <section
      data-testid="administrator-shell"
      aria-labelledby="administrator-shell-heading"
      className="border-0 bg-transparent p-0"
    >
      <h2
        id="administrator-shell-heading"
        className="mb-4 mt-0 text-2xl font-semibold tracking-tight text-text"
      >
        {t('shells.administrator.heading')}
      </h2>
      <Card>
        <p className="text-sm text-text-muted">{t('shells.administrator.comingSoon')}</p>
      </Card>
    </section>
  );
}
