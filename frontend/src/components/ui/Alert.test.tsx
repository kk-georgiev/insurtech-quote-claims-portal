import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Alert } from './Alert';

describe('Alert', () => {
  it('renders role="alert" so the message is announced when it appears', () => {
    render(<Alert>Something went wrong</Alert>);
    expect(screen.getByRole('alert')).toHaveTextContent('Something went wrong');
  });

  // The reason the existing LoginForm/RegisterForm/QuoteForm suites keep
  // passing unmodified: they pin the literal testid, not the element type.
  it('forwards a caller-supplied data-testid', () => {
    render(<Alert data-testid="login-error">Bad credentials</Alert>);
    expect(screen.getByTestId('login-error')).toHaveTextContent('Bad credentials');
  });

  // AD-6 fixes the status vocabulary at exactly these four. Each must resolve
  // to its own colour treatment, or two states would look identical.
  it.each(['danger', 'warning', 'success', 'info'] as const)(
    'gives the %s variant its own colour treatment',
    (variant) => {
      const { unmount } = render(<Alert variant="danger">x</Alert>);
      const dangerClasses = screen.getByRole('alert').className;
      unmount();

      render(<Alert variant={variant}>x</Alert>);
      const classes = screen.getByRole('alert').className;
      expect(classes).toContain(variant);
      if (variant !== 'danger') expect(classes).not.toBe(dangerClasses);
    },
  );

  it('merges a className override without dropping its own variant classes', () => {
    render(<Alert className="mt-4">x</Alert>);
    const alert = screen.getByRole('alert');
    expect(alert.className).toContain('mt-4');
    expect(alert.className).toContain('rounded-md');
  });
});
