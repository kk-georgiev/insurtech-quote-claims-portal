import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from './cn';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  title?: string;
  /**
   * Heading level for `title` (default `h3`, the common case: a card
   * subordinate to a screen's own `h1`/`h2`). Still a flat prop, not a
   * compound-component API (AD-2) — a screen using `Card` as its *own* page
   * heading (e.g. a form screen with no separate `h2`) passes `titleAs="h2"`
   * to avoid skipping a heading level, per review-loop finding (Story 5.2:
   * blind-hunter + verification-gap, both independently, on LoginForm's and
   * RegisterForm's `<h2>` demoted to `<h3>` with no test catching it).
   */
  titleAs?: 'h2' | 'h3';
  footer?: ReactNode;
  children?: ReactNode;
}

/**
 * Flat props only — never a compound-component API (`Card.Header`/
 * `Card.Body`), per AD-2, keeping the four-component set's surface small
 * and single-shaped.
 */
export function Card({ className, title, titleAs = 'h3', footer, children, ...props }: CardProps) {
  const Heading = titleAs;
  return (
    <div
      className={cn(
        // Story 5.5: padding steps down on phones. A Card nested in a Card
        // (QuoteResult inside QuoteForm) otherwise spends 96px of a 375px
        // viewport on padding alone, squeezing the breakdown's two columns
        // to ~106px so nearly every Bulgarian label wraps.
        'rounded-lg border border-border bg-surface p-4 shadow-sm sm:p-6',
        className,
      )}
      {...props}
    >
      {title && <Heading className="mb-4 mt-0 text-base font-semibold text-text">{title}</Heading>}
      {children}
      {footer && <div className="mt-4 border-t border-border pt-4">{footer}</div>}
    </div>
  );
}
