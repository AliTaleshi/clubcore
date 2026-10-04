import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
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
  const queryClient = useQueryClient();

  // Cached server data belongs to the signed-in user; drop it whenever the user changes so the next person on a
  // shared reception PC never sees the previous user's data.
  const resetCache = useCallback(() => queryClient.clear(), [queryClient]);

  useEffect(() => {
    tokenStore.onExpired(() => {
      resetCache();
      setUser(null);
    });
    if (!tokenStore.access) return;
    api
      .get<User>('/auth/me')
      .then((r) => setUser(r.data))
      .catch(() => tokenStore.clear())
      .finally(() => setLoading(false));
  }, [resetCache]);

  const loginWithTokens = useCallback((tokens: Tokens) => {
    resetCache();
    tokenStore.set(tokens);
    setUser(tokens.user);
    return tokens.user;
  }, [resetCache]);

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
    resetCache();
    setUser(null);
    if (refreshToken) await api.post('/auth/logout', { refreshToken }).catch(() => undefined);
  }, [resetCache]);

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
