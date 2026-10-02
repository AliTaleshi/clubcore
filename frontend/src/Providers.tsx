import { useMemo, useState, type ReactNode } from 'react';
import { CacheProvider } from '@emotion/react';
import { CssBaseline, ThemeProvider } from '@mui/material';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDateFnsJalali } from '@mui/x-date-pickers/AdapterDateFnsJalali';
import { faIR as pickersFaIR } from '@mui/x-date-pickers/locales';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './auth/AuthContext';
import { NotifyProvider } from './components/Notify';
import { ColorModeContext } from './components/ColorMode';
import { buildTheme, rtlCache } from './theme';

function initialMode(): 'light' | 'dark' {
  try {
    const saved = localStorage.getItem('cc.mode');
    if (saved === 'light' || saved === 'dark') return saved;
  } catch {
    /* ignore */
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 15_000 } },
  });
}

export function Providers({ children, queryClient }: { children: ReactNode; queryClient?: QueryClient }) {
  const [client] = useState(() => queryClient ?? createQueryClient());
  const [mode, setMode] = useState<'light' | 'dark'>(initialMode);
  const theme = useMemo(() => buildTheme(mode), [mode]);
  const colorMode = useMemo(() => ({
    mode,
    toggle: () => setMode((m) => {
      const next = m === 'light' ? 'dark' : 'light';
      try { localStorage.setItem('cc.mode', next); } catch { /* ignore */ }
      return next;
    }),
  }), [mode]);

  return (
    <CacheProvider value={rtlCache}>
      <ColorModeContext.Provider value={colorMode}>
        <ThemeProvider theme={theme}>
          <LocalizationProvider dateAdapter={AdapterDateFnsJalali}
            localeText={pickersFaIR.components.MuiLocalizationProvider.defaultProps.localeText}>
            <CssBaseline />
            <QueryClientProvider client={client}>
              <NotifyProvider>
                <AuthProvider>{children}</AuthProvider>
              </NotifyProvider>
            </QueryClientProvider>
          </LocalizationProvider>
        </ThemeProvider>
      </ColorModeContext.Provider>
    </CacheProvider>
  );
}
