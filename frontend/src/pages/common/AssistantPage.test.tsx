import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import AssistantPage from './AssistantPage';
import { renderApp } from '../../test/render';
import { server } from '../../test/server';

describe('AssistantPage', () => {
  it('sends a message and renders the markdown reply from history', async () => {
    const history: { id: number; role: string; content: string; createdAt: string }[] = [];
    server.use(
      http.get('/api/ai/status', () => HttpResponse.json({ enabled: false, model: 'local' })),
      http.get('/api/ai/chat/history', () => HttpResponse.json(history)),
      http.post('/api/ai/chat', async ({ request }) => {
        const { message } = (await request.json()) as { message: string };
        history.push({ id: 1, role: 'user', content: message, createdAt: '' });
        history.push({ id: 2, role: 'assistant', content: '**سه جلسه** در هفته کافی است', createdAt: '' });
        return HttpResponse.json({ content: '**سه جلسه** در هفته کافی است', source: 'local' });
      }),
    );
    renderApp(<AssistantPage />, { as: 'MEMBER' });
    expect(await screen.findByText('حالت آفلاین (پاسخ‌های داخلی)')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('پیام'), 'چند جلسه تمرین کنم؟');
    await userEvent.click(screen.getByRole('button', { name: 'ارسال' }));
    expect(await screen.findByText('سه جلسه', { selector: 'strong' })).toBeInTheDocument();
    expect(screen.getByText('چند جلسه تمرین کنم؟')).toBeInTheDocument();
  });

  it('offers role-specific suggestions on an empty conversation', async () => {
    server.use(
      http.get('/api/ai/status', () => HttpResponse.json({ enabled: true, model: 'claude-opus-5' })),
      http.get('/api/ai/chat/history', () => HttpResponse.json([])),
    );
    renderApp(<AssistantPage />, { as: 'ADMIN' });
    expect(await screen.findByText('وضعیت باشگاه در ۳۰ روز اخیر چطور است؟')).toBeInTheDocument();
    expect(screen.getByText('متصل به هوش مصنوعی')).toBeInTheDocument();
  });
});
