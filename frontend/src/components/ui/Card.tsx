import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from './cn';

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  title?: string;
  footer?: ReactNode;
  children?: ReactNode;
}

/**
 * Flat props only — never a compound-component API (`Card.Header`/
 * `Card.Body`), per AD-2, keeping the four-component set's surface small
 * and single-shaped.
 */
export function Card({ className, title, footer, children, ...props }: CardProps) {
  return (
    <div
      className={cn('rounded-lg border border-border bg-surface p-6 shadow-sm', className)}
      {...props}
    >
      {title && <h3 className="mb-4 mt-0 text-base font-semibold text-text">{title}</h3>}
      {children}
      {footer && <div className="mt-4 border-t border-border pt-4">{footer}</div>}
    </div>
  );
}
