import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ErrorBoundary } from './ErrorBoundary';

const Boom: React.FC = () => {
  throw new Error('render failure');
};

describe('ErrorBoundary', () => {
  it('contains a render crash and offers recovery', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});

    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('This screen ran into a problem');
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('clears the error when the reset key changes', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    let shouldThrow = true;
    const MaybeBoom: React.FC = () => {
      if (shouldThrow) throw new Error('x');
      return <p>Recovered</p>;
    };

    const { rerender } = render(
      <ErrorBoundary resetKey="/a">
        <MaybeBoom />
      </ErrorBoundary>,
    );
    shouldThrow = false;
    rerender(
      <ErrorBoundary resetKey="/b">
        <MaybeBoom />
      </ErrorBoundary>,
    );

    expect(screen.getByText('Recovered')).toBeInTheDocument();
  });
});
