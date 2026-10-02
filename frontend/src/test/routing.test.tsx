import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import App from '../App';
import { renderApp } from './render';
import { server } from './server';

const memberSummary = {
  member: { id: 5, userId: 7, fullName: 'علی محمدی', phone: '09121112233', membershipNo: '1001', cardNo: null, nationalCode: null,
    gender: null, birthDate: null, address: null, emergencyPhone: null, coachId: null, coachName: null, referralCode: 'ABC123',
    goal: null, notes: null, active: true, createdAt: '2026-01-01T00:00:00Z' },
  currentMembership: null,
  entryBlockReason: 'این عضو اشتراکی ندارد',
  loyalty: { balance: 120, lifetimePoints: 120, tier: 'BRONZE', tierTitle: 'برنزی', nextTier: 'SILVER', pointsToNextTier: 880,
    discountPercent: 0, referralCode: 'ABC123', referrals: 0 },
  visitsLast30: 3, totalVisits: 9, lastVisit: null, inside: false,
};

describe('routing and role guards', () => {
  it('sends anonymous users to the login page', async () => {
    renderApp(<App />, { route: '/dashboard' });
    expect(await screen.findByRole('button', { name: 'ورود' })).toBeInTheDocument();
  });

  it('redirects a member away from staff pages to their own panel', async () => {
    server.use(
      http.get('/api/me/member', () => HttpResponse.json(memberSummary)),
      http.get('/api/me/qr', () => HttpResponse.json({ token: '5.9999999999.sig', expiresAt: 9999999999 })),
    );
    renderApp(<App />, { route: '/accounting', as: 'MEMBER' });
    expect(await screen.findByText('سلام علی!')).toBeInTheDocument();
    expect(screen.getByText('این عضو اشتراکی ندارد')).toBeInTheDocument();
    // Navigation only shows member items.
    expect(screen.queryByText('حسابداری')).not.toBeInTheDocument();
    expect(screen.getByText('کارت ورود من')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('qr-box').querySelector('svg')).not.toBeNull());
  });

  it('shows accountant navigation without CRM or kiosk', async () => {
    server.use(
      http.get('/api/accounting/reports/income-statement', () => HttpResponse.json({ from: '', to: '', income: [], expenses: [],
        totalIncome: 0, totalExpense: 0, netProfit: 0 })),
      http.get('/api/accounting/reports/series', () => HttpResponse.json([])),
      http.get('/api/accounting/reports/sales-by-plan', () => HttpResponse.json([])),
    );
    renderApp(<App />, { route: '/', as: 'ACCOUNTANT' });
    expect(await screen.findByText('صورت سود و زیان')).toBeInTheDocument();
    expect(screen.getAllByText('حسابداری').length).toBeGreaterThan(0);
    expect(screen.queryByText('کیوسک ورود و خروج')).not.toBeInTheDocument();
    expect(screen.queryByText('مدیریت ارتباط با مشتری')).not.toBeInTheDocument();
  });
});
