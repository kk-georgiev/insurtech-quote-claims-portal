import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merge helper every `components/ui/` primitive uses to combine its own
 * cva-generated variant classes with a caller-supplied `className` override
 * (AD-2). `clsx` handles conditional/falsy inputs; `tailwind-merge` then
 * resolves conflicting Tailwind utilities (e.g. a later `px-6` winning over
 * an earlier `px-4`) instead of leaving both in the class list.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
