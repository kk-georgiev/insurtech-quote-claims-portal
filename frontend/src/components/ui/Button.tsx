import type { ButtonHTMLAttributes } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from './cn';

// The one variant surface a screen composes a button from (AD-2). `primary`
// is the navy-filled call-to-action; `secondary` is the outlined, lower-
// emphasis alternative — both trace to the `@theme` tokens in index.css,
// never a hardcoded hex (AD-1).
const buttonVariants = cva(
  'inline-flex items-center justify-center rounded-full font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2',
  {
    variants: {
      variant: {
        primary: 'bg-primary text-white hover:bg-primary-dark',
        secondary: 'bg-transparent border border-primary text-primary hover:bg-surface-muted',
      },
      size: {
        md: 'px-4 py-2 text-sm',
        sm: 'px-3 py-1.5 text-xs',
      },
    },
    defaultVariants: { variant: 'primary', size: 'md' },
  },
);

export interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

/**
 * Renders a real `<button>` (AD-3) — never a styled non-semantic stand-in —
 * so existing `screen.getByRole('button', ...)` queries keep working once a
 * screen adopts this component. Defaults to `type="button"` so it never
 * accidentally submits a form; pass `type="submit"` explicitly where needed.
 */
export function Button({ className, variant, size, type = 'button', ...props }: ButtonProps) {
  return (
    <button type={type} className={cn(buttonVariants({ variant, size }), className)} {...props} />
  );
}
