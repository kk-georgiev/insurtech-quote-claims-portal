import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Button } from './Button';

describe('Button', () => {
  it('renders a real <button> element', () => {
    render(<Button>Click me</Button>);
    expect(screen.getByRole('button', { name: 'Click me' })).toBeInTheDocument();
  });

  it('defaults to type="button" so it never accidentally submits a form', () => {
    render(<Button>Click me</Button>);
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });

  it('accepts a type="submit" override', () => {
    render(<Button type="submit">Submit</Button>);
    expect(screen.getByRole('button')).toHaveAttribute('type', 'submit');
  });

  it('produces visibly different, non-overlapping class sets for primary vs. secondary', () => {
    const { unmount } = render(<Button variant="primary">Primary</Button>);
    const primaryClasses = screen.getByRole('button').className.split(' ');
    unmount();

    render(<Button variant="secondary">Secondary</Button>);
    const secondaryClasses = screen.getByRole('button').className.split(' ');

    // The color-bearing classes must differ - the two variants shouldn't
    // resolve to the same visual treatment.
    const primaryOnly = primaryClasses.filter((c) => !secondaryClasses.includes(c));
    const secondaryOnly = secondaryClasses.filter((c) => !primaryClasses.includes(c));
    expect(primaryOnly.length).toBeGreaterThan(0);
    expect(secondaryOnly.length).toBeGreaterThan(0);
  });

  it('renders the ghost variant as a real <button> with its own color treatment', () => {
    const { unmount } = render(<Button variant="primary">Primary</Button>);
    const primaryClasses = screen.getByRole('button').className.split(' ');
    unmount();

    render(<Button variant="ghost">Ghost</Button>);
    const ghost = screen.getByRole('button', { name: 'Ghost' });
    expect(ghost.tagName).toBe('BUTTON');
    const ghostOnly = ghost.className.split(' ').filter((c) => !primaryClasses.includes(c));
    expect(ghostOnly.length).toBeGreaterThan(0);
  });

  it('merges a className override without dropping the variant classes', () => {
    render(<Button className="mt-4">Click me</Button>);
    const button = screen.getByRole('button');
    expect(button.className).toContain('mt-4');
    expect(button.className).toContain('rounded-full');
  });
});
