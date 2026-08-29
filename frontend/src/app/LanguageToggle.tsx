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
    <div role="group" aria-label={t('app.language.label')} data-testid="language-toggle">
      {SUPPORTED_LANGUAGES.map((language) => (
        <button
          key={language}
          type="button"
          lang={language}
          aria-pressed={language === active}
          onClick={() => select(language)}
        >
          {t(`app.language.${language}`)}
        </button>
      ))}
    </div>
  );
}
