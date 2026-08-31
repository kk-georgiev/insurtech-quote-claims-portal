import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Select } from './Select';

describe('Select', () => {
  it('renders a real <select> element', () => {
    render(
      <Select aria-label="class">
        <option value="a">A</option>
      </Select>,
    );
    expect(screen.getByRole('combobox', { name: 'class' })).toBeInTheDocument();
  });

  it('carries no error-visual class when invalid is omitted', () => {
    render(
      <Select aria-label="class">
        <option value="a">A</option>
      </Select>,
    );
    expect(screen.getByRole('combobox').className).not.toContain('border-danger');
  });

  it('carries the error-visual class when invalid is true', () => {
    render(
      <Select aria-label="class" invalid>
        <option value="a">A</option>
      </Select>,
    );
    expect(screen.getByRole('combobox').className).toContain('border-danger');
  });

  it('sets aria-invalid when invalid is true, and omits it otherwise', () => {
    const { rerender } = render(
      <Select aria-label="class" invalid>
        <option value="a">A</option>
      </Select>,
    );
    expect(screen.getByRole('combobox')).toHaveAttribute('aria-invalid', 'true');

    rerender(
      <Select aria-label="class" invalid={false}>
        <option value="a">A</option>
      </Select>,
    );
    expect(screen.getByRole('combobox')).not.toHaveAttribute('aria-invalid');
  });

  it('never renders error text itself - that belongs to FormField (AD-5)', () => {
    render(
      <Select aria-label="class" invalid>
        <option value="a">A</option>
      </Select>,
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
