import { describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ApiRequestError } from '../api/client';
import { useFormSubmission } from './useFormSubmission';
import type { Translate } from '../i18n/errorMessages';

// A stub `t` rather than the real catalogs: these cases are about the
// hook's routing (which failure bucket a thrown value lands in), not about
// translation, which errorMessages.test.ts already covers end to end.
const t: Translate = (key) => key;

function Harness({
  action,
  knownFields,
  formLevelCodes,
}: {
  action: (isCancelled: () => boolean) => Promise<void>;
  knownFields?: ReadonlySet<string>;
  formLevelCodes?: readonly string[];
}) {
  const { submitting, formError, fieldErrors, submit } = useFormSubmission('quote.form', t, {
    knownFields,
    formLevelCodes,
  });

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        void submit(action);
      }}
    >
      <button type="submit" disabled={submitting}>
        {submitting ? 'submitting' : 'submit'}
      </button>
      {formError && <p data-testid="form-error">{formError}</p>}
      {Object.entries(fieldErrors).map(([field, message]) => (
        <p key={field} data-testid={`field-error-${field}`}>
          {message}
        </p>
      ))}
    </form>
  );
}

describe('useFormSubmission', () => {
  it('disables the control while in flight and re-enables it after', async () => {
    const user = userEvent.setup();
    let release: () => void = () => {};
    const action = vi.fn(async () => {
      await new Promise<void>((resolve) => {
        release = resolve;
      });
    });

    render(<Harness action={action} />);
    await user.click(screen.getByRole('button'));

    expect(screen.getByRole('button')).toBeDisabled();
    await act(async () => {
      release();
    });
    expect(screen.getByRole('button')).toBeEnabled();
  });

  it('runs the action once when submitted twice before it resolves', async () => {
    const user = userEvent.setup();
    let release: () => void = () => {};
    const action = vi.fn(async () => {
      await new Promise<void>((resolve) => {
        release = resolve;
      });
    });

    render(<Harness action={action} />);
    const button = screen.getByRole('button');
    await user.click(button);
    await user.click(button);

    expect(action).toHaveBeenCalledTimes(1);
    await act(async () => {
      release();
    });
  });

  it('routes an ApiRequestError carrying fieldErrors to the field bucket', async () => {
    const user = userEvent.setup();
    const action = async () => {
      throw new ApiRequestError('dev', 400, 'SHARED_VALIDATION_ERROR', [
        { field: 'regionCode', message: 'raw backend prose' },
      ]);
    };

    render(<Harness action={action} knownFields={new Set(['regionCode'])} />);
    await user.click(screen.getByRole('button'));

    expect(screen.getByTestId('field-error-regionCode')).toBeInTheDocument();
    // The backend's own prose is never rendered (AD-7/AD-8) - the resolver
    // picks copy from the catalog by field name.
    expect(screen.getByTestId('field-error-regionCode')).not.toHaveTextContent('raw backend prose');
    expect(screen.queryByTestId('form-error')).not.toBeInTheDocument();
  });

  it('adds a form-level error when the named field is not one this form renders', async () => {
    const user = userEvent.setup();
    const action = async () => {
      throw new ApiRequestError('dev', 400, 'SHARED_VALIDATION_ERROR', [
        { field: 'somethingElse', message: 'dev prose' },
      ]);
    };

    render(<Harness action={action} knownFields={new Set(['regionCode'])} />);
    await user.click(screen.getByRole('button'));

    // Otherwise the message would be stored and never shown, and the submit
    // would look like it silently did nothing.
    expect(screen.getByTestId('form-error')).toBeInTheDocument();
  });

  it('treats a declared form-level code as form-level even when fieldErrors are present', async () => {
    const user = userEvent.setup();
    const action = async () => {
      throw new ApiRequestError('dev', 401, 'AUTH_INVALID_CREDENTIALS', [
        { field: 'password', message: 'dev prose' },
      ]);
    };

    render(<Harness action={action} formLevelCodes={['AUTH_INVALID_CREDENTIALS']} />);
    await user.click(screen.getByRole('button'));

    expect(screen.getByTestId('form-error')).toBeInTheDocument();
    expect(screen.queryByTestId('field-error-password')).not.toBeInTheDocument();
  });

  it('falls back to a form-level error for a non-ApiRequestError', async () => {
    const user = userEvent.setup();
    const action = async () => {
      throw new Error('network down');
    };

    render(<Harness action={action} />);
    await user.click(screen.getByRole('button'));

    expect(screen.getByTestId('form-error')).toBeInTheDocument();
  });

  it('clears a previous failure when the form is resubmitted', async () => {
    const user = userEvent.setup();
    let shouldFail = true;
    const action = async () => {
      if (shouldFail) throw new Error('first attempt fails');
    };

    render(<Harness action={action} />);
    await user.click(screen.getByRole('button'));
    expect(screen.getByTestId('form-error')).toBeInTheDocument();

    shouldFail = false;
    await user.click(screen.getByRole('button'));
    expect(screen.queryByTestId('form-error')).not.toBeInTheDocument();
  });

  it('reports the component as cancelled once it unmounts mid-submit', async () => {
    const user = userEvent.setup();
    let release: () => void = () => {};
    let cancelledAtResolve: boolean | null = null;
    const action = async (isCancelled: () => boolean) => {
      await new Promise<void>((resolve) => {
        release = resolve;
      });
      cancelledAtResolve = isCancelled();
    };

    const { unmount } = render(<Harness action={action} />);
    await user.click(screen.getByRole('button'));
    unmount();
    await act(async () => {
      release();
    });

    // This is what every adopting form relies on before setting its own
    // state after an await.
    expect(cancelledAtResolve).toBe(true);
  });
});
