import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import PlansPage from './PlansPage';
import { renderApp } from '../../test/render';
import { server } from '../../test/server';

describe('PlansPage form', () => {
  it('accepts prices typed with Persian digits and separators, and rejects non-numbers', async () => {
    const posted: Record<string, unknown>[] = [];
    server.use(
      http.get('/api/plans', () => HttpResponse.json([])),
      http.post('/api/plans', async ({ request }) => {
        posted.push((await request.json()) as Record<string, unknown>);
        return HttpResponse.json({ id: 1 }, { status: 201 });
      }),
    );
    renderApp(<PlansPage />, { as: 'ADMIN' });
    await userEvent.click(await screen.findByRole('button', { name: 'پلن جدید' }));
    const dialog = screen.getByRole('dialog');
    await userEvent.type(within(dialog).getByLabelText('نام پلن'), 'ماهانه');

    await userEvent.type(within(dialog).getByLabelText('قیمت (تومان)'), 'هزار');
    await userEvent.click(within(dialog).getByRole('button', { name: 'ذخیره' }));
    expect(await within(dialog).findByText('قیمت را به‌صورت عدد صحیح وارد کنید')).toBeInTheDocument();
    expect(posted).toHaveLength(0);

    await userEvent.clear(within(dialog).getByLabelText('قیمت (تومان)'));
    await userEvent.type(within(dialog).getByLabelText('قیمت (تومان)'), '۱٬۵۰۰٬۰۰۰');
    await userEvent.click(within(dialog).getByRole('button', { name: 'ذخیره' }));
    await vi.waitFor(() => expect(posted).toHaveLength(1));
    expect(posted[0]).toMatchObject({ name: 'ماهانه', price: 1500000, durationDays: 30, sessionLimit: null, maxFreezeDays: 0 });
  });
});
