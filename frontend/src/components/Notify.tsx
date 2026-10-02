import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import { Alert, Snackbar, type AlertColor } from '@mui/material';

type NotifyFn = (message: string, severity?: AlertColor) => void;

const NotifyContext = createContext<NotifyFn>(() => undefined);

export function NotifyProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ message: string; severity: AlertColor; key: number } | null>(null);
  const notify = useCallback<NotifyFn>((message, severity = 'success') => {
    setState({ message, severity, key: Date.now() });
  }, []);
  return (
    <NotifyContext.Provider value={notify}>
      {children}
      <Snackbar
        key={state?.key}
        open={!!state}
        autoHideDuration={4500}
        onClose={() => setState(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {state ? (
          <Alert severity={state.severity} variant="filled" onClose={() => setState(null)} sx={{ width: '100%' }}>
            {state.message}
          </Alert>
        ) : undefined}
      </Snackbar>
    </NotifyContext.Provider>
  );
}

export const useNotify = () => useContext(NotifyContext);
