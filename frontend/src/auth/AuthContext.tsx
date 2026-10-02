import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, tokenStore } from '../api/client';
import type { Tokens, User } from '../api/types';
import type { Role } from '../utils/labels';

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (phone: string, password: string) => Promise<User>;
  loginWithTokens: (tokens: Tokens) => User;
  logout: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(Boolean(tokenStore.access));

  useEffect(() => {
    tokenStore.onExpired(() => setUser(null));
    if (!tokenStore.access) return;
    api
      .get<User>('/auth/me')
      .then((r) => setUser(r.data))
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false));
  }, []);

  const loginWithTokens = useCallback((tokens: Tokens) => {
    tokenStore.set(tokens);
    setUser(tokens.user);
    return tokens.user;
  }, []);

  const login = useCallback(
    async (phone: string, password: string) => {
      const { data } = await api.post<Tokens>('/auth/login', { phone, password });
      return loginWithTokens(data);
    },
    [loginWithTokens],
  );

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.refresh;
    tokenStore.clear();
    setUser(null);
    if (refreshToken) await api.post('/auth/logout', { refreshToken }).catch(() => undefined);
  }, []);

  const hasRole = useCallback((...roles: Role[]) => !!user && roles.includes(user.role), [user]);

  const value = useMemo(
    () => ({ user, loading, login, loginWithTokens, logout, hasRole }),
    [user, loading, login, loginWithTokens, logout, hasRole],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

/** Landing route for each role after login. */
export function homeFor(role: Role): string {
  switch (role) {
    case 'MEMBER':
      return '/me';
    case 'COACH':
      return '/coach';
    case 'ACCOUNTANT':
      return '/accounting';
    default:
      return '/dashboard';
  }
}
