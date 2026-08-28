/**
 * ADMINISTRATOR navigation shell — bare route target for Story 2.2's
 * role-based post-login routing, mounted at `/administrator`. Story 2.3
 * builds the real content and chrome; Story 2.4 adds the route guard.
 */
export function AdministratorShell() {
  return (
    <section data-testid="administrator-shell">
      <h2>Administrator</h2>
    </section>
  );
}
