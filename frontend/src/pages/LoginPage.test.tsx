import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppProviders } from '../App';
import { ROUTER_FUTURE } from '../AppRoutes';
import { jsonResponse, mockFetch, testUser } from '../test/utils';
import LoginPage from './LoginPage';

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']} future={ROUTER_FUTURE}>
      <AppProviders>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>Workspace home</p>} />
        </Routes>
      </AppProviders>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  it('validates required fields before calling the API', async () => {
    const fetchMock = mockFetch();
    renderLogin();

    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Username is mandatory')).toBeInTheDocument();
    expect(screen.getByText('Password is mandatory')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('signs in, stores the session and continues to the workspace', async () => {
    const fetchMock = mockFetch({
      '/auth/login': () =>
        jsonResponse({ accessToken: 'jwt-abc', tokenType: 'Bearer', expiresIn: 28800, user: testUser(['RECEPTIONIST'], { username: 'reception' }) }),
    });
    renderLogin();

    await userEvent.type(screen.getByLabelText('Username'), 'reception');
    await userEvent.type(screen.getByLabelText('Password'), 'Reception@123');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Workspace home')).toBeInTheDocument();
    const [, init] = fetchMock.mock.calls[0];
    expect(JSON.parse(String(init?.body))).toEqual({ username: 'reception', password: 'Reception@123' });
    const stored = JSON.parse(window.sessionStorage.getItem('lims.session')!);
    expect(stored.accessToken).toBe('jwt-abc');
    expect(stored.expiresAt).toBeGreaterThan(Date.now());
  });

  it('shows a generic message on bad credentials and clears the password', async () => {
    mockFetch({ '/auth/login': () => jsonResponse({ title: 'Authentication Failed', detail: 'Bad credentials' }, 401) });
    renderLogin();

    await userEvent.type(screen.getByLabelText('Username'), 'reception');
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-pass');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Invalid username or password.')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Password')).toHaveValue(''));
    expect(window.sessionStorage.getItem('lims.session')).toBeNull();
  });

  it('shows the lockout message from the server', async () => {
    mockFetch({
      '/auth/login': () => jsonResponse({ title: 'Account Locked', detail: 'Account is temporarily locked after repeated failed sign-ins' }, 423),
    });
    renderLogin();

    await userEvent.type(screen.getByLabelText('Username'), 'reception');
    await userEvent.type(screen.getByLabelText('Password'), 'x');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText(/temporarily locked/)).toBeInTheDocument();
  });
});
