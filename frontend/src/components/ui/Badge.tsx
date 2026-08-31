import type { HTMLAttributes } from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from './cn';

// The one status-chip treatment (Story 6.3 - the first consumer of the
// AD-6 status vocabulary Milestone 2 fixed without a component to render
// it). Mirrors Alert's own variant→token mapping exactly, one visual step
// down (control shape, small text) - the same four tokens, never a fifth.
const badgeVariants = cva(
  'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
  {
    variants: {
      variant: {
        danger: 'bg-danger/10 text-danger',
        warning: 'bg-warning/10 text-warning',
        success: 'bg-success/10 text-success',
        info: 'bg-info/10 text-info',
        neutral: 'bg-surface-muted text-text-muted',
      },
    },
    defaultVariants: { variant: 'neutral' },
  },
);

export interface BadgeProps
  extends HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

/**
 * Renders a native `<span>` (AD-3) - never interactive, never a `<button>`:
 * a status is not a control. The colour is redundant reinforcement only -
 * callers always pass a translated label as `children`, which is what
 * actually carries the meaning (UX EXPERIENCE.md, Component Patterns). A
 * status must survive being read in grayscale.
 */
export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}
