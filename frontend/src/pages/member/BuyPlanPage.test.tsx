import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import BuyPlanPage from './BuyPlanPage';
import { renderApp } from '../../test/render';
import { server } from '../../test/server';

const plans = [
  { id: 1, name: 'یک ماهه', description: null, durationDays: 30, sessionLimit: null, price: 1500000, maxFreezeDays: 7, active: true },
  { id: 2, name: '۱۲ جلسه‌ای', description: null, durationDays: 45, sessionLimit: 12, price: 1100000, maxFreezeDays: 0, active: true },
];

describe('BuyPlanPage', () => {
  it('lists plans, applies a discount code and redirects to the gateway', async () => {
    const assign = vi.fn();
    Object.defineProperty(window, 'location', { value: { ...window.location, assign, origin: 'http://localhost:3000' }, writable: true });
    server.use(
      http.get('/api/public/plans', () => HttpResponse.json(plans)),
      http.get('/api/me/memberships/quote', ({ request }) => {
        const code = new URL(request.url).searchParams.get('discountCode');
        if (code === 'BAD') return HttpResponse.json({ detail: 'کد تخفیف نامعتبر است' }, { status: 400 });
        return HttpResponse.json({ price: 1500000, tierDiscountPercent: 5, tierDiscount: 75000, codeDiscount: code ? 200000 : 0,
          total: code ? 1225000 : 1425000 });
      }),
      http.post('/api/me/memberships', () => HttpResponse.json({ invoiceId: 44, total: 1225000, paid: false, membership: {} }, { status: 201 })),
      http.post('/api/payments/online', () => HttpResponse.json({ paymentId: 9, redirectUrl: 'https://gateway.test/pay/9', gateway: 'MOCK' })),
    );
    renderApp(<BuyPlanPage />, { as: 'MEMBER' });
    expect(await screen.findByText('۱٬۵۰۰٬۰۰۰ تومان')).toBeInTheDocument();
    expect(screen.getByText('۱۲ جلسه')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: 'انتخاب' })[0]);

    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('۱٬۴۲۵٬۰۰۰ تومان')).toBeInTheDocument();
    expect(within(dialog).getByText('تخفیف سطح (۵٪)')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByLabelText('کد تخفیف جایزه'), 'bad');
    await userEvent.click(within(dialog).getByRole('button', { name: 'اعمال' }));
    expect(await within(dialog).findByText('کد تخفیف نامعتبر است')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'پرداخت و فعال‌سازی' })).toBeDisabled();

    await userEvent.clear(within(dialog).getByLabelText('کد تخفیف جایزه'));
    await userEvent.type(within(dialog).getByLabelText('کد تخفیف جایزه'), 'good');
    await userEvent.click(within(dialog).getByRole('button', { name: 'اعمال' }));
    expect(await within(dialog).findByText('۱٬۲۲۵٬۰۰۰ تومان')).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'پرداخت و فعال‌سازی' }));
    await vi.waitFor(() => expect(assign).toHaveBeenCalledWith('https://gateway.test/pay/9'));
  });
});
