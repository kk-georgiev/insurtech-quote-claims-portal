import type { InputHTMLAttributes } from 'react';
import { cva } from 'class-variance-authority';
import { cn } from './cn';

// `invalid` drives visual styling only (border/ring color) — it never
// renders error text itself. `FormField` owns the message (AD-5).
const inputVariants = cva(
  'w-full rounded-md border bg-surface px-3 py-2 text-sm text-text placeholder:text-text-muted focus:outline-none focus:ring-2 disabled:bg-surface-muted disabled:text-text-muted',
  {
    variants: {
      invalid: {
        true: 'border-danger focus:ring-danger',
        false: 'border-border focus:ring-accent',
      },
    },
    defaultVariants: { invalid: false },
  },
);

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  /** Visual-only invalid state (AD-5) — the error message itself belongs to `FormField`. */
  invalid?: boolean;
}

/** Renders a real `<input>` (AD-3) so label association and role queries keep working. */
export function Input({ className, invalid = false, ...props }: InputProps) {
  return (
    <input
      aria-invalid={invalid || undefined}
      className={cn(inputVariants({ invalid }), className)}
      {...props}
    />
  );
}
