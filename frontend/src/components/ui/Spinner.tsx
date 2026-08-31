import type { HTMLAttributes } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from './cn';

// The one loading indicator every screen uses (Story 5.6, FR-9). Colour comes
// from `border-current`, so it inherits whatever context it sits in — white
// inside a primary `Button`, muted next to body text — instead of each caller
// picking a colour (AD-1: no hardcoded values).
const spinnerVariants = cva(
  'inline-block shrink-0 animate-spin rounded-full border-current border-t-transparent',
  {
    variants: {
      size: {
        sm: 'size-3.5 border-2',
        md: 'size-5 border-2',
      },
    },
    defaultVariants: { size: 'sm' },
  },
);

export interface SpinnerProps
  extends HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof spinnerVariants> {}

/**
 * Purely decorative: `aria-hidden` so assistive tech never announces the
 * spinner itself. Every caller pairs it with visible text that already says
 * what is happening ("Влизане…", "Проверка на сървъра…"), so the state is
 * conveyed once, in words — which also means this component needs no
 * translated label of its own and adds no i18n keys.
 */
export function Spinner({ className, size, ...props }: SpinnerProps) {
  return (
    <span aria-hidden="true" className={cn(spinnerVariants({ size }), className)} {...props} />
  );
}
