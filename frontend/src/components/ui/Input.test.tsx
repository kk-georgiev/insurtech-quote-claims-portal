import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Input } from './Input';

describe('Input', () => {
  it('renders a real <input> element', () => {
    render(<Input aria-label="email" />);
    expect(screen.getByRole('textbox', { name: 'email' })).toBeInTheDocument();
  });

  it('carries no error-visual class when invalid is omitted', () => {
    render(<Input aria-label="email" />);
    expect(screen.getByRole('textbox').className).not.toContain('border-danger');
  });

  it('carries no error-visual class when invalid is explicitly false', () => {
    render(<Input aria-label="email" invalid={false} />);
    expect(screen.getByRole('textbox').className).not.toContain('border-danger');
  });

  it('carries the error-visual class when invalid is true', () => {
    render(<Input aria-label="email" invalid />);
    expect(screen.getByRole('textbox').className).toContain('border-danger');
  });

  it('never renders error text itself - that belongs to FormField (AD-5)', () => {
    render(<Input aria-label="email" invalid />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('sets aria-invalid when invalid is true, and omits it otherwise (review-loop finding)', () => {
    const { rerender } = render(<Input aria-label="email" invalid />);
    expect(screen.getByRole('textbox')).toHaveAttribute('aria-invalid', 'true');

    rerender(<Input aria-label="email" invalid={false} />);
    expect(screen.getByRole('textbox')).not.toHaveAttribute('aria-invalid');
  });

  it('merges a className override without dropping the invalid-state class', () => {
    render(<Input aria-label="email" invalid className="mt-2" />);
    const input = screen.getByRole('textbox');
    expect(input.className).toContain('mt-2');
    expect(input.className).toContain('border-danger');
  });
});
