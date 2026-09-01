import type { SelectHTMLAttributes } from 'react';
import { cva } from 'class-variance-authority';
import { cn } from './cn';

// Mirrors Input.tsx's own cva pattern exactly (Story 6.1 - the first field
// this design system needs that isn't a free-typed value, e.g. the bonus-
// malus class). `invalid` drives visual styling only — `FormField` owns the
// error message (AD-5).
const selectVariants = cva(
  'w-full rounded-md border bg-surface px-3 py-2 text-base text-text focus:outline-none focus:ring-2 disabled:bg-surface-muted disabled:text-text-muted sm:text-sm',
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

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  /** Visual-only invalid state (AD-5) — the error message itself belongs to `FormField`. */
  invalid?: boolean;
}

/** Renders a real `<select>` (AD-3) so label association and role queries keep working. */
export function Select({ className, invalid = false, children, ...props }: SelectProps) {
  return (
    <select
      aria-invalid={invalid || undefined}
      className={cn(selectVariants({ invalid }), className)}
      {...props}
    >
      {children}
    </select>
  );
}
