import { QuoteForm } from '../../quote/QuoteForm';

/**
 * CLIENT navigation shell, mounted at `/`. Story 1.7 fills this in with the
 * quote flow (FR-8/FR-9) - form and breakdown render in place here, no new
 * route (spec Boundaries & Constraints). No route guard yet (Story 2.4's
 * job) and no other shell chrome yet (Story 2.3, concurrent, owns
 * `AgentShell`/`LiquidatorShell`/`AdministratorShell` and is out of scope
 * here).
 */
export function ClientShell() {
  return (
    <section data-testid="client-shell">
      <h2>Client</h2>
      <QuoteForm />
    </section>
  );
}
