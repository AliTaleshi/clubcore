import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import App from '../App';
import { renderApp } from './render';
import { server, users } from './server';

const memberSummary = {
  member: { id: 5, userId: 7, fullName: 'علی محمدی', phone: '09121112233', membershipNo: '1001', cardNo: null, nationalCode: null,
    gender: null, birthDate: null, address: null, emergencyPhone: null, coachId: null, coachName: null, referralCode: 'ABC123',
    goal: null, notes: null, active: true, createdAt: '2026-01-01T00:00:00Z' },
  currentMembership: null, entryBlockReason: 'این عضو اشتراکی ندارد',
  loyalty: { balance: 0, lifetimePoints: 0, tier: 'BRONZE', tierTitle: 'برنزی', nextTier: 'SILVER', pointsToNextTier: 1000,
    discountPercent: 0, referralCode: 'ABC123', referrals: 0 },
  visitsLast30: 0, totalVisits: 0, lastVisit: null, inside: false,
};

describe('session handling', () => {
  it('refreshes an expired access token on startup instead of logging the user out', async () => {
    localStorage.setItem('cc.access', 'expired-access');
    localStorage.setItem('cc.refresh', 'valid-refresh');
    server.use(
      http.get('/api/auth/me', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer fresh-access'
          ? HttpResponse.json(users.ADMIN)
          : HttpResponse.json({ detail: 'expired' }, { status: 401 })),
      http.post('/api/auth/refresh', () =>
        HttpResponse.json({ accessToken: 'fresh-access', refreshToken: 'fresh-refresh', expiresIn: 1800, user: users.ADMIN })),
      http.get('/api/notifications', () => HttpResponse.json([])),
    );
    renderApp(<App />, { route: '/notifications' });
    expect(await screen.findByRole('heading', { name: 'اعلان‌ها' })).toBeInTheDocument();
    expect(localStorage.getItem('cc.access')).toBe('fresh-access');
    expect(localStorage.getItem('cc.refresh')).toBe('fresh-refresh');
  });

  it("clears the previous user's cached data on logout", async () => {
    server.use(
      http.get('/api/me/member', () => HttpResponse.json(memberSummary)),
      http.get('/api/me/qr', () => HttpResponse.json({ token: 't', expiresAt: 9999999999 })),
      http.post('/api/auth/logout', () => new HttpResponse(null, { status: 200 })),
    );
    const { client } = renderApp(<App />, { route: '/me', as: 'MEMBER' });
    expect(await screen.findByText('سلام علی!')).toBeInTheDocument();
    expect(client.getQueryData(['me-member'])).toBeDefined();

    await userEvent.click(screen.getByRole('button', { name: 'حساب کاربری' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'خروج' }));
    expect(await screen.findByRole('button', { name: 'ورود' })).toBeInTheDocument();
    await waitFor(() => expect(client.getQueryData(['me-member'])).toBeUndefined());
    expect(localStorage.getItem('cc.access')).toBeNull();
  });
});
