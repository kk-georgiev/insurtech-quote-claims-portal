/**
 * LIQUIDATOR navigation shell — bare route target for Story 2.2's
 * role-based post-login routing, mounted at `/liquidator`. Story 2.3 builds
 * the real content and chrome; Story 2.4 adds the route guard.
 */
export function LiquidatorShell() {
  return (
    <section data-testid="liquidator-shell">
      <h2>Liquidator</h2>
    </section>
  );
}
