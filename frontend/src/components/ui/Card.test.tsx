import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Card } from './Card';

describe('Card', () => {
  it('renders children', () => {
    render(<Card>Content</Card>);
    expect(screen.getByText('Content')).toBeInTheDocument();
  });

  it('renders a title when provided', () => {
    render(<Card title="Quote summary">Content</Card>);
    expect(screen.getByText('Quote summary')).toBeInTheDocument();
  });

  it('renders no title element when omitted', () => {
    const { container } = render(<Card>Content</Card>);
    expect(container.querySelector('h3')).not.toBeInTheDocument();
  });

  it('renders a footer when provided', () => {
    render(<Card footer={<span>Footer text</span>}>Content</Card>);
    expect(screen.getByText('Footer text')).toBeInTheDocument();
  });

  it('merges a className override without dropping its own layout classes', () => {
    const { container } = render(<Card className="mt-4">Content</Card>);
    const card = container.firstElementChild;
    expect(card?.className).toContain('mt-4');
    expect(card?.className).toContain('rounded-lg');
  });
});
