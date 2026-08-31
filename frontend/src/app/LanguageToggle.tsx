import { useTranslation } from 'react-i18next';
import { SUPPORTED_LANGUAGES, saveLanguage, type Language } from '../i18n/language';

/**
 * The app-wide display-language control (Story 3.1, FR-14). Mounted once in
 * `RootLayout`'s header, so it is reachable from every screen - guarded or
 * not, logged in or not.
 *
 * Changing the language re-renders in place: `changeLanguage` updates the
 * i18next instance and react-i18next re-renders subscribed components. There
 * is no reload, no `navigate()`, and no route change, so the current route,
 * scroll position, and any in-progress form state all survive the switch.
 *
 * Each option is labeled in its *own* language ("Български" / "English"),
 * never translated - a visitor who cannot read the current language still has
 * to be able to find the one they want.
 *
 * Rendered as a `role="group"` of `aria-pressed` toggle buttons rather than a
 * `<select>`: with only two options both are worth showing at once, and the
 * pressed state is what tells a screen-reader user which one is active. The
 * `lang` attribute on each button stops a screen reader from reading
 * "Български" with an English voice.
 *
 * Story 5.4 styles this as a segmented pill control that sits on the navy
 * header. It is a two-option toggle, not one of the four base primitives
 * (Button/Input/FormField/Card), so per the Milestone 2 architecture its
 * native `<button>`s are styled directly with semantic-token utilities —
 * the active pill is driven off the same `aria-pressed` state, via the
 * `aria-pressed:` Tailwind variant, so sighted and AT users get one source
 * of truth.
 */
export function LanguageToggle() {
  const { t, i18n } = useTranslation();
  const active = i18n.resolvedLanguage;

  function select(language: Language): void {
    if (language === active) return;
    void i18n.changeLanguage(language);
    saveLanguage(language);
  }

  return (
    <div
      role="group"
      aria-label={t('app.language.label')}
      data-testid="language-toggle"
      className="flex items-center gap-1 rounded-full border border-white/20 p-1"
    >
      {SUPPORTED_LANGUAGES.map((language) => (
        <button
          key={language}
          type="button"
          lang={language}
          aria-pressed={language === active}
          onClick={() => select(language)}
          className="inline-flex min-h-11 items-center rounded-full bg-transparent px-3 py-0.5 text-xs font-medium text-white/70 transition-colors hover:bg-white/10 hover:text-white aria-pressed:bg-white aria-pressed:text-primary aria-pressed:hover:bg-white aria-pressed:hover:text-primary sm:min-h-0 sm:px-2.5"
        >
          {t(`app.language.${language}`)}
        </button>
      ))}
    </div>
  );
}
