import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { Providers, createQueryClient } from '../Providers';
import { server, users } from './server';

/** Renders inside all app providers. When `as` is given, a logged-in session for that role is simulated. */
export function renderApp(ui: ReactElement, { route = '/', as }: { route?: string; as?: keyof typeof users } = {}) {
  if (as) {
    localStorage.setItem('cc.access', 'test-access');
    localStorage.setItem('cc.refresh', 'test-refresh');
    server.use(http.get('/api/auth/me', () => HttpResponse.json(users[as])));
  }
  const client = createQueryClient();
  client.setDefaultOptions({ queries: { retry: false, staleTime: 0 } });
  return render(
    <MemoryRouter initialEntries={[route]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <Providers queryClient={client}>{ui}</Providers>
    </MemoryRouter>,
  );
}
