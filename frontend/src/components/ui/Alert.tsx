import type { HTMLAttributes } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from './cn';

// The one message-banner treatment every screen uses (Story 5.6, FR-9). The
// four variants are exactly AD-6's fixed status vocabulary — a screen showing
// a domain-specific state maps it to one of these before choosing a colour,
// and never invents a fifth.
const alertVariants = cva('rounded-md border px-3 py-2 text-sm', {
  variants: {
    variant: {
      danger: 'border-danger/30 bg-danger/10 text-danger',
      warning: 'border-warning/30 bg-warning/10 text-warning',
      success: 'border-success/30 bg-success/10 text-success',
      info: 'border-info/30 bg-info/10 text-info',
    },
  },
  defaultVariants: { variant: 'danger' },
});

export interface AlertProps
  extends HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof alertVariants> {}

/**
 * Renders `role="alert"` so a message appearing after an action is announced
 * without the user having to go looking for it. Callers pass their own
 * `data-testid` straight through, which is what lets the existing suites keep
 * pinning `login-error`/`register-error`/`quote-error` unchanged.
 *
 * Deliberately not used for `FormField`'s field-level error: AD-5 makes
 * `FormField` the owner of that message, and it is already a single shared
 * treatment. This component is for form- and screen-level feedback.
 */
export function Alert({ className, variant, ...props }: AlertProps) {
  return <div role="alert" className={cn(alertVariants({ variant }), className)} {...props} />;
}
