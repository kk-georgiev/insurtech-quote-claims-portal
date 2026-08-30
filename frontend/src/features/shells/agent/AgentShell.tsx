import { useTranslation } from 'react-i18next';

/**
 * AGENT navigation shell, mounted at `/agent`. Story 2.3 turns Story 2.2's
 * bare route target into a static, clearly Agent-labeled placeholder screen:
 * a heading naming the *area* plus one coming-soon line. It deliberately
 * labels the area and not the viewer ("Agent workspace", never "you are
 * signed in as AGENT") — there is no route guard until Story 2.4, so an
 * anonymous visitor can reach this URL and the copy must not assert an auth
 * state the app has not verified.
 *
 * Static and non-interactive by contract (epics.md AC, PRD §4.2): no
 * buttons, links, inputs, or handlers, and no per-role sub-navigation. The
 * three staff shells stay separate near-identical files rather than one
 * shared component — each grows different real functionality later.
 */
export function AgentShell() {
  const { t } = useTranslation();

  return (
    <section data-testid="agent-shell" aria-labelledby="agent-shell-heading">
      <h2 id="agent-shell-heading">{t('shells.agent.heading')}</h2>
      <p>{t('shells.agent.comingSoon')}</p>
    </section>
  );
}
