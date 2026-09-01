// The single place the frontend turns a backend failure into words a user
// reads (Story 3.2b). Every form routes through here rather than re-deriving
// its own `switch`, so a new backend code needs one catalog entry and no
// component change.
//
// AD-7's contract, enforced here: the backend's `code` is the *only* thing
// that selects copy. `ApiRequestError.message` and `ApiFieldError.message`
// are developer/log-facing and are never rendered - not as a fallback, not
// in a corner case. They stay reachable on the error object for logging.

import { ApiRequestError, type ApiFieldError } from '../api/client';

/**
 * Minimal structural shape of `useTranslation()`'s `t`. Deliberately narrower
 * than i18next's `TFunction` so this module (and its tests) do not depend on
 * i18next's generic machinery.
 */
export type Translate = (key: string, options?: Record<string, unknown>) => string;

/**
 * Catalog namespace holding a form's per-field messages. Per *form*, not per
 * field name, because the same field name carries different constraints on
 * different endpoints - `password` is 8-100 characters on register but only
 * capped at 100 on login, and a shared message could not honestly describe
 * both.
 */
export type FieldErrorNamespace = 'auth.login' | 'auth.register' | 'quote.form';

/**
 * What a form *remembers* about a failure, as opposed to what it shows.
 *
 * Forms hold one of these in state and call the resolvers during render, so
 * an error already on screen re-translates the moment the language changes.
 * Storing the resolved string instead would freeze it in whichever language
 * was active when the request failed, and the user would have to resubmit to
 * see it in the other one.
 *
 * `source` is the thrown value itself - an `ApiRequestError`, a network
 * error, or `null` for a client-side failure with no error object. All of
 * them are language-neutral, and {@link resolveFormError} already knows how
 * to turn any of them into words.
 */
export type FormFailure = { source: unknown } | null;

/** The field-level counterpart of {@link FormFailure}. */
export type FieldFailure = { fieldErrors: ApiFieldError[]; code?: string } | null;

/**
 * Codes that describe one specific field rather than the request as a whole,
 * mapped to the field each one is about. When the envelope carries one of
 * these, its message is more specific than that field's own catch-all and
 * wins - the precedence rule the spec fixes once, here, rather than per form.
 *
 * Mapped to a field name rather than kept as a bare set on purpose: a
 * response may carry several `fieldErrors` alongside one of these codes, and
 * only the field the code is actually about should be overridden. Every other
 * field keeps its own message.
 */
const FIELD_SPECIFIC_CODES: Record<string, string> = {
  PRICING_UNKNOWN_REGION: 'regionCode',
  PRICING_UNSUPPORTED_INSTALLMENTS: 'installments',
  PRICING_UNKNOWN_BONUS_MALUS_CLASS: 'bonusMalusClass',
  // Story 8.1's acceptance errors. Registered with the codes rather than
  // with the form that will render them (Story 8.2): the backend attaches
  // `fieldErrors` for exactly these two, and an unregistered code silently
  // falls back to the generic message, discarding the copy the same change
  // added to both catalogs.
  QUOTE_COVERAGE_START_IN_PAST: 'coverageStart',
  QUOTE_VEHICLE_IDENTIFIER_REQUIRED: 'vehicleRegistration',
};

/**
 * The form-level message for any thrown value: a translated entry for a known
 * backend `code`, otherwise the generic fallback.
 *
 * An unrecognized code, a missing code, and a non-`ApiRequestError` (network
 * failure, unexpected throw) all degrade to the same generic message. A new
 * backend code therefore ships safely even before its catalog entry lands -
 * it reads as a generic failure rather than rendering a raw key or a blank.
 */
export function resolveFormError(error: unknown, t: Translate): string {
  if (error instanceof ApiRequestError && error.code) {
    const message = t(`errors.codes.${error.code}`, { defaultValue: '' });
    if (message) return message;
  }
  return t('errors.generic');
}

/**
 * Per-field messages keyed by field name, translated from the field name plus
 * the envelope's `code` - never from the backend's prose.
 *
 * Because the backend sends no per-rule code, one message must cover a
 * field's whole constraint set. The catalog entries are written that way on
 * purpose; see the spec's Design Notes.
 */
export function resolveFieldErrors(
  fieldErrors: ApiFieldError[] | undefined,
  namespace: FieldErrorNamespace,
  code: string | undefined,
  t: Translate,
): Record<string, string> {
  const map: Record<string, string> = {};
  if (!fieldErrors) return map;

  for (const fieldError of fieldErrors) {
    if (code && FIELD_SPECIFIC_CODES[code] === fieldError.field) {
      const specific = t(`errors.codes.${code}`, { defaultValue: '' });
      if (specific) {
        map[fieldError.field] = specific;
        continue;
      }
    }
    // A field the catalog does not know still gets words, never a raw key.
    map[fieldError.field] = t(`${namespace}.fieldErrors.${fieldError.field}`, {
      defaultValue: t('errors.generic'),
    });
  }
  return map;
}
