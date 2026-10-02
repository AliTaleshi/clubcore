import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { Tokens } from './types';

const ACCESS = 'cc.access';
const REFRESH = 'cc.refresh';

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    /* storage unavailable (private mode); tokens live only in memory */
  }
}

// In-memory copies are only a fallback for when storage is unavailable; storage is the source of truth so that
// several tabs share one session.
let memAccess: string | null = null;
let memRefresh: string | null = null;
let onSessionExpired: (() => void) | null = null;

export const tokenStore = {
  get access() {
    return read(ACCESS) ?? memAccess;
  },
  get refresh() {
    return read(REFRESH) ?? memRefresh;
  },
  set(tokens: Pick<Tokens, 'accessToken' | 'refreshToken'>) {
    memAccess = tokens.accessToken;
    memRefresh = tokens.refreshToken;
    write(ACCESS, memAccess);
    write(REFRESH, memRefresh);
  },
  clear() {
    memAccess = null;
    memRefresh = null;
    write(ACCESS, null);
    write(REFRESH, null);
  },
  onExpired(cb: () => void) {
    onSessionExpired = cb;
  },
};

export const api = axios.create({ baseURL: '/api', timeout: 120_000 });

api.interceptors.request.use((config) => {
  const token = tokenStore.access;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let refreshing: Promise<string | null> | null = null;

async function refreshAccess(): Promise<string | null> {
  const refreshToken = tokenStore.refresh;
  if (!refreshToken) return null;
  try {
    const { data } = await axios.post<Tokens>('/api/auth/refresh', { refreshToken });
    tokenStore.set(data);
    return data.accessToken;
  } catch {
    return null;
  }
}

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const isAuthCall = original?.url?.startsWith('/auth/');
    if (error.response?.status === 401 && original && !original._retry && !isAuthCall) {
      original._retry = true;
      refreshing = refreshing ?? refreshAccess().finally(() => (refreshing = null));
      const token = await refreshing;
      if (token) {
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      }
      tokenStore.clear();
      onSessionExpired?.();
    }
    return Promise.reject(error);
  },
);

/** Extracts the Persian message from a ProblemDetail error response. */
export function errorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { detail?: string; errors?: Record<string, string> } | undefined;
    if (data?.errors) {
      const first = Object.values(data.errors)[0];
      if (first) return first;
    }
    if (data?.detail) return data.detail;
    if (!err.response) return 'ارتباط با سرور برقرار نشد';
  }
  return 'خطای غیرمنتظره رخ داد';
}
