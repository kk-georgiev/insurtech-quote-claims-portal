import { useState } from 'react';
import { ApiRequestError } from '../api/client';
import { resolveFieldErrors, resolveFormError } from '../i18n/errorMessages';
import type { FieldErrorNamespace, FieldFailure, FormFailure, Translate } from '../i18n/errorMessages';
import { useCancelledRef } from './useCancelledRef';

export type FormPhase = 'editing' | 'submitting';

export interface UseFormSubmissionOptions {
  /**
   * The fields this form actually renders an inline error next to. When the
   * backend names a field outside this set, its message would be stored and
   * never shown, so a generic form-level error is raised alongside it and
   * the user always sees something. Omit when the form shows no field-level
   * errors of its own.
   *
   * Checked against the raw field names the backend sent, never a resolved
   * map: which fields were named is language-neutral and must not depend on
   * translation.
   */
  knownFields?: ReadonlySet<string>;
  /**
   * Backend codes this form treats as form-level even though the response
   * may also carry `fieldErrors` — e.g. `AUTH_INVALID_CREDENTIALS`, which
   * describes the attempt rather than one input.
   */
  formLevelCodes?: readonly string[];
}

export interface FormSubmission {
  /** True while a submission is in flight — drives `disabled` and the spinner. */
  submitting: boolean;
  /** The translated form-level message, or `null`. Resolved per render. */
  formError: string | null;
  /** Translated per-field messages keyed by field name. Resolved per render. */
  fieldErrors: Record<string, string>;
  /**
   * Runs `action` as this form's submission: guards against a double press,
   * flips to `submitting`, clears both failures, and routes anything thrown
   * into the right failure bucket. The action owns what success *means* —
   * navigating, setting a success phase, rendering a result — because that
   * differs per form and does not belong in shared mechanics.
   *
   * `isCancelled` is the unmount guard: an action must check it before any
   * `setState` of its own, exactly as the hand-rolled `cancelledRef` checks
   * did, because the hook cannot intercept state the action sets itself.
   */
  submit: (action: (isCancelled: () => boolean) => Promise<void>) => Promise<void>;
  /**
   * Raises a generic form-level failure for a request that *succeeded* but
   * whose result is unusable — `LoginForm`'s 200 carrying a token that does
   * not decode. There is no thrown error and no backend `code` to resolve,
   * so it cannot go through {@link submit}'s catch.
   */
  reportFailure: (source: unknown) => void;
}

/**
 * The mechanics every submitting form in this app repeats (Story 8.2,
 * Epic 5 retro item 41): the unmount guard, the `editing`/`submitting`
 * phase, the double-submit early return, clearing failures on submit,
 * returning to `editing` on any error, and splitting an `ApiRequestError`
 * into field-level and form-level failures.
 *
 * <p>Extracted rather than copied a fourth time when Story 8.2 added the
 * acceptance form. Behaviour is deliberately identical to what the three
 * existing forms already did — the extraction is a refactor, and every one
 * of their tests passes against it unmodified.
 *
 * <p>Failures are held as their language-neutral sources and resolved on
 * every render, never stored resolved: an error already on screen must
 * re-translate the moment the language changes, with no resubmit.
 *
 * <p>What this hook deliberately does NOT own: the form's own inputs, and
 * what success looks like. `RegisterForm` keeps its own `success` phase and
 * `LoginForm` its own navigation — folding those in would make one shared
 * hook carry three unrelated success shapes.
 */
export function useFormSubmission(
  namespace: FieldErrorNamespace,
  t: Translate,
  options: UseFormSubmissionOptions = {},
): FormSubmission {
  const { knownFields, formLevelCodes } = options;
  const [phase, setPhase] = useState<FormPhase>('editing');
  const [formFailure, setFormFailure] = useState<FormFailure>(null);
  const [fieldFailure, setFieldFailure] = useState<FieldFailure>(null);
  const cancelledRef = useCancelledRef();

  const isCancelled = () => cancelledRef.current;

  async function submit(action: (isCancelled: () => boolean) => Promise<void>) {
    if (phase === 'submitting') return;
    setPhase('submitting');
    setFormFailure(null);
    setFieldFailure(null);

    try {
      await action(isCancelled);
      if (cancelledRef.current) return;
      // The action decides what success looks like; the phase returns to
      // editing either way, so a form whose success keeps it mounted (an
      // in-place result) is immediately usable again.
      setPhase('editing');
    } catch (error) {
      if (cancelledRef.current) return;

      // The form stays editable after any error - never locked, never cleared.
      setPhase('editing');

      if (error instanceof ApiRequestError) {
        if (error.code && formLevelCodes?.includes(error.code)) {
          setFormFailure({ source: error });
          return;
        }
        if (error.fieldErrors && error.fieldErrors.length > 0) {
          setFieldFailure({ fieldErrors: error.fieldErrors, code: error.code });
          if (knownFields && !error.fieldErrors.some((fieldError) => knownFields.has(fieldError.field))) {
            setFormFailure({ source: error });
          }
          return;
        }
      }
      setFormFailure({ source: error });
    }
  }

  function reportFailure(source: unknown) {
    setPhase('editing');
    setFormFailure({ source });
  }

  return {
    submitting: phase === 'submitting',
    formError: formFailure ? resolveFormError(formFailure.source, t) : null,
    fieldErrors: fieldFailure
      ? resolveFieldErrors(fieldFailure.fieldErrors, namespace, fieldFailure.code, t)
      : {},
    submit,
    reportFailure,
  };
}
