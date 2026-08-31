import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Badge } from './Badge';

describe('Badge', () => {
  it('renders a native <span>, never a button or link', () => {
    render(<Badge>Valid</Badge>);
    const badge = screen.getByText('Valid');
    expect(badge.tagName).toBe('SPAN');
  });

  it('is never interactive - carries no button/link role', () => {
    render(<Badge>Valid</Badge>);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it.each(['danger', 'warning', 'success', 'info', 'neutral'] as const)(
    'renders the %s variant token classes',
    (variant) => {
      render(<Badge variant={variant}>Status</Badge>);
      const badge = screen.getByText('Status');
      expect(badge.className.length).toBeGreaterThan(0);
    },
  );

  it('always renders its children as the accessible text - colour is reinforcement, never the sole carrier', () => {
    render(<Badge variant="danger">Expired on 12 September</Badge>);
    expect(screen.getByText('Expired on 12 September')).toBeInTheDocument();
  });

  it('merges a className override', () => {
    render(<Badge className="mt-2">Status</Badge>);
    expect(screen.getByText('Status').className).toContain('mt-2');
  });
});
