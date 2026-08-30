import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { FormField } from './FormField';
import { Input } from './Input';

describe('FormField', () => {
  it('renders a real <label> wrapping its control, associated with it', () => {
    render(
      <FormField label="Email">
        <Input />
      </FormField>,
    );
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
  });

  it('renders no error text when error is omitted', () => {
    render(
      <FormField label="Email">
        <Input />
      </FormField>,
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('renders the error text when error is provided', () => {
    render(
      <FormField label="Email" error="Email is required">
        <Input invalid />
      </FormField>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Email is required');
  });

  it('links the error to its control via aria-describedby (review-loop finding)', () => {
    render(
      <FormField label="Email" error="Email is required">
        <Input invalid />
      </FormField>,
    );
    const input = screen.getByLabelText('Email');
    const errorId = screen.getByRole('alert').id;
    expect(errorId).toBeTruthy();
    expect(input).toHaveAttribute('aria-describedby', errorId);
  });

  it('merges a className override without dropping its own layout classes', () => {
    const { container } = render(
      <FormField label="Email" className="mt-4">
        <Input />
      </FormField>,
    );
    const wrapper = container.firstElementChild;
    expect(wrapper?.className).toContain('mt-4');
    expect(wrapper?.className).toContain('space-y-1');
  });

  it('keeps the error text out of the label so the label name stays just the field name', () => {
    render(
      <FormField label="Email" error="Email is required">
        <Input invalid />
      </FormField>,
    );
    // Would throw if "Email is required" leaked into the <label>'s accessible
    // name (review-loop finding, verification-gap iteration 1).
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
  });
});
