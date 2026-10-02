import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import KioskPage from './KioskPage';
import { renderApp } from '../../test/render';
import { server } from '../../test/server';

describe('KioskPage', () => {
  it('checks a member in with a card number and shows the welcome screen', async () => {
    let body: unknown;
    server.use(http.post('/api/attendance/scan', async ({ request }) => {
      body = await request.json();
      return HttpResponse.json({
        action: 'CHECK_IN', memberName: 'مریم صادقی', membershipNo: '1002', planName: 'یک ماهه', endDate: '2026-11-01',
        sessionsRemaining: 2, message: 'ورود مریم صادقی ثبت شد',
        attendance: { id: 1, memberId: 2, memberName: 'مریم صادقی', membershipNo: '1002', checkInAt: '2026-10-02T10:00:00Z', checkOutAt: null, method: 'CARD' },
      });
    }));
    renderApp(<KioskPage />, { as: 'ADMIN' });
    await userEvent.type(screen.getByLabelText('شناسه عضو'), 'CARD۰۰۰۰۲{enter}');
    expect(await screen.findByText('خوش آمدید')).toBeInTheDocument();
    expect(screen.getByText('مریم صادقی')).toBeInTheDocument();
    expect(screen.getByText(/۲ جلسه باقیمانده/)).toBeInTheDocument();
    expect(body).toEqual({ method: 'CARD', value: 'CARD00002' });
  });

  it('shows the reason when entry is refused', async () => {
    server.use(http.post('/api/attendance/scan', () =>
      HttpResponse.json({ detail: 'اشتراک این عضو منقضی شده است' }, { status: 400 })));
    renderApp(<KioskPage />, { as: 'ADMIN' });
    await userEvent.click(screen.getByRole('tab', { name: 'دستی' }));
    await userEvent.type(screen.getByLabelText('شناسه عضو'), '09121112233{enter}');
    expect(await screen.findByText('اشتراک این عضو منقضی شده است')).toBeInTheDocument();
  });
});
