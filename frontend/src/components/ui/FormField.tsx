import {
  cloneElement,
  isValidElement,
  useId,
  type HTMLAttributes,
  type ReactElement,
  type ReactNode,
} from 'react';
import { cn } from './cn';

export interface FormFieldProps extends Omit<HTMLAttributes<HTMLDivElement>, 'children'> {
  /** The field's visible label text. */
  label: ReactNode;
  /** The already-translated error message, or omitted for no error (AD-5). */
  error?: string;
  /**
   * Overrides the generated `useId()` value for both the error `<span id>`
   * and the `aria-describedby` wired onto the control. Needed when a caller
   * (e.g. `LoginForm`/`RegisterForm`) has an existing test suite asserting a
   * literal id string (`'login-email-error'`) rather than just presence of
   * an id. Falls back to the generated id when omitted.
   */
  errorId?: string;
  /** The control (e.g. `Input`) the label wraps and associates with. */
  children: ReactNode;
}

/**
 * Renders a real `<label>` (AD-3) wrapping its control, so the association
 * is implicit — no `htmlFor`/`id` wiring required by the caller. Owns the
 * field-level error message (AD-5): `Input` only signals `invalid` for
 * styling, this component renders the actual text, only when `error` is
 * provided.
 *
 * The error text is a sibling of the `<label>`, not nested inside it —
 * review-loop finding (verification-gap, iteration 1): a `<label>`'s
 * accessible name is computed from its full text content, so nesting the
 * error message inside it would silently fold "Email is required" into the
 * field's *label* text (breaking `getByLabelText('Email')`-style queries
 * and confusing what a screen reader announces as the label vs. the error).
 * `aria-describedby` on the control is what actually links the two, without
 * polluting the label's name.
 */
export function FormField({
  className,
  label,
  error,
  errorId: errorIdOverride,
  children,
  ...props
}: FormFieldProps) {
  const generatedId = useId();
  const errorId = errorIdOverride ?? generatedId;
  const control =
    error && isValidElement(children)
      ? cloneElement(children as ReactElement<{ 'aria-describedby'?: string }>, {
          'aria-describedby': errorId,
        })
      : children;

  return (
    <div className={cn('space-y-1', className)} {...props}>
      <label className="block text-sm font-medium text-text">
        <span>{label}</span>
        {control}
      </label>
      {error && (
        <span id={errorId} role="alert" className="block text-xs font-normal text-danger">
          {error}
        </span>
      )}
    </div>
  );
}
