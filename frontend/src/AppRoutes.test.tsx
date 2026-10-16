import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { EMPTY_PAGE, jsonResponse, mockFetch, renderApp, seedSession, testUser } from './test/utils';

describe('routing and access control', () => {
  it('sends anonymous users to sign-in', async () => {
    mockFetch();
    renderApp('/accessioning');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('shows only the screens a technician may use', async () => {
    seedSession(['TECHNICIAN']);
    mockFetch({ '/auth/me': () => jsonResponse(testUser(['TECHNICIAN'])) });
    renderApp('/accessioning');

    expect(await screen.findByRole('heading', { name: /Specimen Accessioning/ })).toBeInTheDocument();
    const nav = screen.getAllByRole('navigation', { name: 'Main' })[0];
    expect(within(nav).getByRole('link', { name: /Accessioning/ })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: /Results/ })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: /Registration/ })).not.toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: /Admin/ })).not.toBeInTheDocument();
  });

  it('blocks a receptionist from administration', async () => {
    seedSession(['RECEPTIONIST']);
    mockFetch({ '/auth/me': () => jsonResponse(testUser(['RECEPTIONIST'])) });
    renderApp('/admin/users');

    expect(await screen.findByRole('heading', { name: 'Access restricted' })).toBeInTheDocument();
  });

  it('gives a receptionist a read-only accessioning worklist', async () => {
    seedSession(['RECEPTIONIST']);
    mockFetch({ '/auth/me': () => jsonResponse(testUser(['RECEPTIONIST'])) });
    renderApp('/accessioning');

    expect(await screen.findByText(/Read-only view/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Specimen barcode')).not.toBeInTheDocument();
  });

  it('lands each role on its primary screen', async () => {
    seedSession(['PATHOLOGIST']);
    mockFetch({ '/auth/me': () => jsonResponse(testUser(['PATHOLOGIST'])), '/orders': () => jsonResponse(EMPTY_PAGE) });
    renderApp('/');

    expect(await screen.findByRole('heading', { name: /Result Entry/ })).toBeInTheDocument();
  });

  it('lands the next user on their own workspace after a sign-out (shared workstation)', async () => {
    seedSession(['RECEPTIONIST']);
    mockFetch({
      '/auth/me': () => jsonResponse(testUser(['RECEPTIONIST'])),
      '/catalog/tests': () => jsonResponse([]),
      '/auth/login': () => jsonResponse({ accessToken: 'tech-token', tokenType: 'Bearer', expiresIn: 3600, user: testUser(['TECHNICIAN']) }),
    });
    renderApp('/register');

    await userEvent.click(await screen.findByRole('button', { name: /Account menu/ }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Sign out' }));
    await userEvent.type(await screen.findByLabelText('Username'), 'technician');
    await userEvent.type(screen.getByLabelText('Password'), 'Technician@123');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('heading', { name: /Specimen Accessioning/ })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Access restricted' })).not.toBeInTheDocument();
  });

  it('returns to sign-in with an explanation when the session is rejected', async () => {
    seedSession(['TECHNICIAN']);
    mockFetch({
      '/auth/me': () => jsonResponse(testUser(['TECHNICIAN'])),
      '/accessioning/samples': () => jsonResponse({ title: 'Authentication Required', status: 401 }, 401),
    });
    renderApp('/accessioning');

    expect(await screen.findByText(/Your session expired/)).toBeInTheDocument();
    expect(window.sessionStorage.getItem('lims.session')).toBeNull();
  });
});
