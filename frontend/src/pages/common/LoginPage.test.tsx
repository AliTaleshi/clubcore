import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import App from '../../App';
import { renderApp } from '../../test/render';
import { server, users } from '../../test/server';

describe('LoginPage', () => {
  it('validates the phone number before calling the API', async () => {
    renderApp(<App />, { route: '/login' });
    await userEvent.type(await screen.findByLabelText('شماره موبایل'), '123');
    await userEvent.click(screen.getByRole('button', { name: 'ورود' }));
    expect(await screen.findByText('شماره موبایل را به‌درستی وارد کنید')).toBeInTheDocument();
  });

  it('shows the server error message for wrong credentials', async () => {
    server.use(http.post('/api/auth/login', () =>
      HttpResponse.json({ status: 401, detail: 'شماره موبایل یا رمز عبور اشتباه است' }, { status: 401 })));
    renderApp(<App />, { route: '/login' });
    await userEvent.type(await screen.findByLabelText('شماره موبایل'), '09120000000');
    await userEvent.type(screen.getByLabelText('رمز عبور'), 'bad');
    await userEvent.click(screen.getByRole('button', { name: 'ورود' }));
    expect(await screen.findByText('شماره موبایل یا رمز عبور اشتباه است')).toBeInTheDocument();
  });

  it('normalizes Persian digits, logs in and stores tokens', async () => {
    let sentPhone = '';
    server.use(
      http.post('/api/auth/login', async ({ request }) => {
        sentPhone = ((await request.json()) as { phone: string }).phone;
        return HttpResponse.json({ accessToken: 'a1', refreshToken: 'r1', expiresIn: 1800, user: users.MEMBER });
      }),
      http.get('/api/me/member', () => HttpResponse.json({ message: 'skip' }, { status: 500 })),
      http.get('/api/me/qr', () => HttpResponse.json({ token: 't', expiresAt: 1 })),
    );
    renderApp(<App />, { route: '/login' });
    await userEvent.type(await screen.findByLabelText('شماره موبایل'), '۰۹۱۲۱۱۱۲۲۳۳');
    await userEvent.type(screen.getByLabelText('رمز عبور'), 'Secret123');
    await userEvent.click(screen.getByRole('button', { name: 'ورود' }));
    await waitFor(() => expect(localStorage.getItem('cc.access')).toBe('a1'));
    expect(sentPhone).toBe('09121112233');
    expect(await screen.findByText('کارت ورود من')).toBeInTheDocument();
  });

  it('supports one-time-code login', async () => {
    server.use(
      http.post('/api/auth/otp/request', () => HttpResponse.json({ message: 'کد ارسال شد' })),
      http.post('/api/auth/otp/verify', async ({ request }) => {
        const body = (await request.json()) as { code: string };
        return body.code === '12345'
          ? HttpResponse.json({ accessToken: 'a2', refreshToken: 'r2', expiresIn: 1800, user: users.ADMIN })
          : HttpResponse.json({ detail: 'کد نامعتبر' }, { status: 401 });
      }),
      http.get('/api/dashboard', () => HttpResponse.json({}, { status: 500 })),
    );
    renderApp(<App />, { route: '/login' });
    await userEvent.click(await screen.findByRole('tab', { name: 'کد یکبار مصرف' }));
    await userEvent.type(screen.getByLabelText('شماره موبایل'), '09120000000');
    await userEvent.click(screen.getByRole('button', { name: 'دریافت کد' }));
    expect(await screen.findByText('کد ارسال شد')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /ارسال مجدد/ })).toBeDisabled();
    await userEvent.type(screen.getByLabelText('کد تأیید'), '۱۲۳۴۵');
    await userEvent.click(screen.getByRole('button', { name: 'تأیید و ورود' }));
    await waitFor(() => expect(localStorage.getItem('cc.access')).toBe('a2'));
  });
});
