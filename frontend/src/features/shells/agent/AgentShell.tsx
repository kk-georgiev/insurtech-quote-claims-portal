/**
 * AGENT navigation shell — bare route target for Story 2.2's role-based
 * post-login routing, mounted at `/agent`. Story 2.3 builds the real labeled
 * content, layout, and nav chrome; Story 2.4 adds the route guard (typing
 * `/agent` renders this stub today).
 */
export function AgentShell() {
  return (
    <section data-testid="agent-shell">
      <h2>Agent</h2>
    </section>
  );
}
