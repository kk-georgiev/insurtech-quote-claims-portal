import { QuoteForm } from '../../quote/QuoteForm';
import { useTranslation } from 'react-i18next';

/**
 * CLIENT navigation shell, mounted at `/` behind a `RoleGuard` (Story 2.4).
 * Story 1.7 fills it in with the quote flow - `QuoteForm` renders the form
 * and, on success, the breakdown in place here (no separate route).
 *
 * Story 5.4 gives it the shared "workspace" look every role shell now uses:
 * a page-level `<h2>` naming the area, then the screen's real content. The
 * wrapping `<section>` drops the legacy `main > section` card chrome
 * (`border-0 bg-transparent p-0`) so `QuoteForm`'s own `Card` is the only
 * card, not a card-in-card.
 */
export function ClientShell() {
  const { t } = useTranslation();

  return (
    <section
      data-testid="client-shell"
      aria-labelledby="client-shell-heading"
      className="border-0 bg-transparent p-0"
    >
      <h2
        id="client-shell-heading"
        className="mb-4 mt-0 text-2xl font-semibold tracking-tight text-text"
      >
        {t('shells.client.heading')}
      </h2>
      <QuoteForm />
    </section>
  );
}
