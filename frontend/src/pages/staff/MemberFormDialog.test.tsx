import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { MemberFormDialog } from './MemberFormDialog';
import { renderApp } from '../../test/render';
import { server } from '../../test/server';

describe('MemberFormDialog', () => {
  it('validates national code locally and submits normalized data', async () => {
    let posted: Record<string, unknown> | null = null;
    server.use(
      http.get('/api/users/by-role', () => HttpResponse.json([{ id: 9, fullName: 'مربی تست', role: 'COACH' }])),
      http.post('/api/members', async ({ request }) => {
        posted = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ id: 77, fullName: posted.fullName }, { status: 201 });
      }),
    );
    const onSaved = vi.fn();
    renderApp(<MemberFormDialog open onClose={() => {}} onSaved={onSaved} />, { as: 'ADMIN' });
    await userEvent.type(screen.getByLabelText(/نام و نام خانوادگی/), 'سارا رضایی');
    await userEvent.type(screen.getByLabelText(/شماره موبایل/), '۰۹۱۲۳۴۵۶۷۸۹');
    await userEvent.type(screen.getByLabelText('کد ملی'), '1234567890');
    await userEvent.click(screen.getByRole('button', { name: 'ذخیره' }));
    expect(await screen.findByText('کد ملی نامعتبر است')).toBeInTheDocument();
    expect(posted).toBeNull();

    await userEvent.clear(screen.getByLabelText('کد ملی'));
    await userEvent.type(screen.getByLabelText('کد ملی'), '0499370899');
    await userEvent.click(screen.getByRole('button', { name: 'ذخیره' }));
    await vi.waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(posted).toMatchObject({ fullName: 'سارا رضایی', phone: '09123456789', nationalCode: '0499370899', coachId: null, password: null });
  });
});
