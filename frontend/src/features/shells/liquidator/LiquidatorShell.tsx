import { useTranslation } from 'react-i18next';
import { Card } from '../../../components/ui/Card';

/**
 * LIQUIDATOR navigation shell, mounted at `/liquidator` behind a `RoleGuard`
 * (Story 2.4). Story 2.3 made it a static, clearly Liquidator-labeled
 * placeholder: a heading naming the *area* plus one coming-soon line. It
 * labels the area, not the viewer.
 *
 * Static and non-interactive by contract (epics.md AC, PRD §4.2): no
 * buttons, links, inputs, or handlers, and no per-role sub-navigation.
 *
 * Story 5.4 applies the shared "workspace" look: a page-level `<h2>` naming
 * the area, then a `Card` holding the coming-soon copy. The `<section>`
 * drops the legacy `main > section` card chrome so the `Card` is the only
 * card. Copy, testid, and `aria-labelledby` wiring are unchanged.
 */
export function LiquidatorShell() {
  const { t } = useTranslation();

  return (
    <section
      data-testid="liquidator-shell"
      aria-labelledby="liquidator-shell-heading"
      className="border-0 bg-transparent p-0"
    >
      <h2
        id="liquidator-shell-heading"
        className="mb-4 mt-0 text-2xl font-semibold tracking-tight text-text"
      >
        {t('shells.liquidator.heading')}
      </h2>
      <Card>
        <p className="text-sm text-text-muted">{t('shells.liquidator.comingSoon')}</p>
      </Card>
    </section>
  );
}
