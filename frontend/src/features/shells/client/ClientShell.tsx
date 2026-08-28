/**
 * CLIENT navigation shell — bare route target for Story 2.2's role-based
 * post-login routing, mounted at `/`. Story 2.3 builds the real labeled
 * content and chrome; the client shell's actual home is Epic 1's quote flow
 * (a frontend that does not exist yet — see `deferred-work.md`).
 */
export function ClientShell() {
  return (
    <section data-testid="client-shell">
      <h2>Client</h2>
    </section>
  );
}
