import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';

export const users = {
  ADMIN: { id: 1, phone: '09120000000', fullName: 'مدیر سیستم', role: 'ADMIN', active: true, createdAt: '2026-01-01T00:00:00Z' },
  MEMBER: { id: 7, phone: '09121112233', fullName: 'علی محمدی', role: 'MEMBER', active: true, createdAt: '2026-01-01T00:00:00Z' },
  ACCOUNTANT: { id: 3, phone: '09120000002', fullName: 'بهروز حسابدار', role: 'ACCOUNTANT', active: true, createdAt: '2026-01-01T00:00:00Z' },
} as const;

/** Handlers every page needs (layout chrome). Tests add their own on top. */
export const baseHandlers = [
  http.get('/api/public/gym-info', () => HttpResponse.json({ name: 'باشگاه تست', phone: '', address: '' })),
  http.get('/api/notifications/unread-count', () => HttpResponse.json({ unread: 2 })),
  http.get('/api/auth/me', () => HttpResponse.json({ detail: 'ابتدا وارد شوید' }, { status: 401 })),
  http.post('/api/auth/refresh', () => HttpResponse.json({ detail: 'نشست منقضی شده' }, { status: 401 })),
];

export const server = setupServer(...baseHandlers);
