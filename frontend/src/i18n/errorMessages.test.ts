import { describe, expect, it } from 'vitest';
import i18n from './index';
import { resolveFieldErrors, resolveFormError, type Translate } from './errorMessages';
import { ApiRequestError } from '../api/client';
import bg from './bg.json';
import en from './en.json';

// The real `t`, not a stub: the point of these cases is that the catalogs and
// the resolver agree, and a stub would prove only that the resolver calls a
// function. `src/test/setup.ts` resets the language to `bg` after each test.
const t: Translate = (key, options) => i18n.t(key, options) as string;

const CODES = [
  'AUTH_UNAUTHENTICATED',
  'AUTH_FORBIDDEN',
  'AUTH_INVALID_CREDENTIALS',
  'AUTH_EMAIL_TAKEN',
  'PRICING_UNKNOWN_REGION',
  'PRICING_UNSUPPORTED_INSTALLMENTS',
  'QUOTE_NOT_FOUND',
  'SHARED_VALIDATION_ERROR',
  'SHARED_NOT_FOUND',
  'SHARED_INTERNAL_ERROR',
] as const;

describe('resolveFormError', () => {
  // Every code the backend can emit, enumerated from the Java sources. If the
  // backend adds one, this list and the catalogs must grow together (AD-7).
  it.each(CODES)('resolves %s to its own translated message', (code) => {
    const message = resolveFormError(new ApiRequestError('dev-facing', 400, code), t);

    expect(message).toBe(bg.errors.codes[code]);
    expect(message).not.toBe(bg.errors.generic);
  });

  it('covers exactly the codes the catalogs define — no more, no fewer', () => {
    expect(Object.keys(bg.errors.codes).sort()).toEqual([...CODES].sort());
    expect(Object.keys(en.errors.codes).sort()).toEqual([...CODES].sort());
  });

  it('falls back to the generic message for a code with no catalog entry', () => {
    const message = resolveFormError(
      new ApiRequestError('dev-facing', 400, 'SOME_FUTURE_CODE'),
      t,
    );

    expect(message).toBe(bg.errors.generic);
    // Never the raw key, and never the backend's prose.
    expect(message).not.toContain('SOME_FUTURE_CODE');
    expect(message).not.toContain('dev-facing');
  });

  it('falls back to the generic message when the envelope carries no code', () => {
    expect(resolveFormError(new ApiRequestError('dev-facing', 401), t)).toBe(bg.errors.generic);
  });

  it.each([
    ['a plain Error', new Error('boom')],
    ['a string', 'boom'],
    ['null', null],
    ['undefined', undefined],
  ])('falls back to the generic message for %s', (_label, thrown) => {
    expect(resolveFormError(thrown, t)).toBe(bg.errors.generic);
  });

  it('never renders the backend message, even when it is the only thing present', () => {
    const error = new ApiRequestError('Constraint violation on column foo', 500);
    expect(resolveFormError(error, t)).not.toContain('Constraint violation');
  });

  it('follows the active language', async () => {
    await i18n.changeLanguage('en');
    const error = new ApiRequestError('dev-facing', 401, 'AUTH_INVALID_CREDENTIALS');

    expect(resolveFormError(error, t)).toBe(en.errors.codes.AUTH_INVALID_CREDENTIALS);
  });
});

describe('resolveFieldErrors', () => {
  it('maps by field name, ignoring the backend prose entirely', () => {
    const error = new ApiRequestError('dev', 400, 'SHARED_VALIDATION_ERROR', [
      { field: 'driverAge', message: 'must be greater than or equal to 18' },
      { field: 'engineCc', message: 'must not be null' },
    ]);

    const map = resolveFieldErrors(error.fieldErrors, 'quote.form', error.code, t);

    expect(map).toEqual({
      driverAge: bg.quote.form.fieldErrors.driverAge,
      engineCc: bg.quote.form.fieldErrors.engineCc,
    });
    expect(Object.values(map).join(' ')).not.toContain('must be');
  });

  // The precedence rule the spec fixes once, in the resolver: a code that
  // describes one specific field beats that field's catch-all message.
  it.each([
    ['PRICING_UNKNOWN_REGION', 'regionCode'],
    ['PRICING_UNSUPPORTED_INSTALLMENTS', 'installments'],
  ] as const)('prefers the more specific %s over the per-field message', (code, field) => {
    const error = new ApiRequestError('dev', 400, code, [{ field, message: 'raw backend prose' }]);

    const map = resolveFieldErrors(error.fieldErrors, 'quote.form', error.code, t);

    expect(map[field]).toBe(bg.errors.codes[code]);
    expect(map[field]).not.toBe(bg.quote.form.fieldErrors[field]);
  });

  it('uses the per-field message for a general validation code', () => {
    const error = new ApiRequestError('dev', 400, 'SHARED_VALIDATION_ERROR', [
      { field: 'regionCode', message: 'must not be blank' },
    ]);

    const map = resolveFieldErrors(error.fieldErrors, 'quote.form', error.code, t);
    expect(map.regionCode).toBe(bg.quote.form.fieldErrors.regionCode);
  });

  // Same field name, different constraints per endpoint — which is why the
  // namespace is the form, not the field.
  it('gives password a different message on register than on login', () => {
    const fieldErrors = [{ field: 'password', message: 'size must be between 8 and 100' }];

    const onRegister = resolveFieldErrors(fieldErrors, 'auth.register', 'SHARED_VALIDATION_ERROR', t);
    const onLogin = resolveFieldErrors(fieldErrors, 'auth.login', 'SHARED_VALIDATION_ERROR', t);

    expect(onRegister.password).toBe(bg.auth.register.fieldErrors.password);
    expect(onLogin.password).toBe(bg.auth.login.fieldErrors.password);
    expect(onRegister.password).not.toBe(onLogin.password);
  });

  it('gives an unknown field the generic message, never a raw key or backend prose', () => {
    const error = new ApiRequestError('dev', 400, 'SHARED_VALIDATION_ERROR', [
      { field: 'someUnrenderedField', message: 'raw backend prose' },
    ]);

    const map = resolveFieldErrors(error.fieldErrors, 'quote.form', error.code, t);

    expect(map.someUnrenderedField).toBe(bg.errors.generic);
    expect(map.someUnrenderedField).not.toContain('fieldErrors');
    expect(map.someUnrenderedField).not.toContain('raw backend prose');
  });

  it('returns an empty map when there are no field errors', () => {
    expect(resolveFieldErrors(undefined, 'quote.form', 'SHARED_VALIDATION_ERROR', t)).toEqual({});
    expect(resolveFieldErrors([], 'quote.form', undefined, t)).toEqual({});
  });
});
