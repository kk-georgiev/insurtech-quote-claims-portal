import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Spinner } from './Spinner';

describe('Spinner', () => {
  // The contract that keeps it out of the accessibility tree: every caller
  // pairs it with visible text that already states what is happening, so an
  // announced spinner would duplicate that. `aria-hidden` is what guarantees
  // a submit button's accessible name stays the submitting label alone.
  it('is decorative — hidden from assistive technology', () => {
    const { container } = render(<Spinner />);
    const spinner = container.firstElementChild;
    expect(spinner).toHaveAttribute('aria-hidden', 'true');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('animates, so it reads as a loading indicator rather than a dot', () => {
    const { container } = render(<Spinner />);
    expect(container.firstElementChild?.className).toContain('animate-spin');
  });

  // Colour is inherited (`border-current`), never hardcoded (AD-1) — that is
  // what lets one spinner work on a navy button and next to muted body text.
  it('takes its colour from the surrounding context', () => {
    const { container } = render(<Spinner />);
    expect(container.firstElementChild?.className).toContain('border-current');
  });

  it('produces a different class set per size', () => {
    const { container: small } = render(<Spinner size="sm" />);
    const { container: medium } = render(<Spinner size="md" />);
    expect(small.firstElementChild?.className).not.toBe(medium.firstElementChild?.className);
  });

  it('merges a className override without dropping its own classes', () => {
    const { container } = render(<Spinner className="mr-2" />);
    const classes = container.firstElementChild?.className ?? '';
    expect(classes).toContain('mr-2');
    expect(classes).toContain('animate-spin');
  });
});
