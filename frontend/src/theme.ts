import { createTheme } from '@mui/material/styles';
import createCache from '@emotion/cache';
import { prefixer } from 'stylis';
import rtlPlugin from 'stylis-plugin-rtl';
import { faIR } from '@mui/material/locale';

export const rtlCache = createCache({ key: 'muirtl', stylisPlugins: [prefixer, rtlPlugin] });

const font = '"Vazirmatn", "Tahoma", sans-serif';

/** Brand palette: indigo primary with an amber accent. */
export const brand = {
  indigo: '#4f46e5',
  indigoLight: '#818cf8',
  amber: '#f59e0b',
  rose: '#f43f5e',
};

/** Chart colors per mode; income uses the brand color, expense stays a warm red so the meaning is obvious. */
export function chartColors(mode: 'light' | 'dark') {
  return {
    primary: mode === 'light' ? brand.indigo : brand.indigoLight,
    accent: brand.amber,
    income: mode === 'light' ? brand.indigo : brand.indigoLight,
    expense: brand.rose,
  };
}

export function buildTheme(mode: 'light' | 'dark') {
  return createTheme(
    {
      direction: 'rtl',
      palette: {
        mode,
        primary: { main: mode === 'light' ? brand.indigo : brand.indigoLight },
        secondary: { main: brand.amber, contrastText: '#1f1300' },
        background: mode === 'light' ? { default: '#f5f6fb', paper: '#ffffff' } : { default: '#0d0f1c', paper: '#151829' },
      },
      shape: { borderRadius: 12 },
      typography: {
        fontFamily: font,
        h4: { fontWeight: 800 },
        h5: { fontWeight: 700 },
        h6: { fontWeight: 700 },
        button: { fontWeight: 600, textTransform: 'none' },
      },
      components: {
        MuiCard: { defaultProps: { variant: 'outlined' } },
        MuiButton: { defaultProps: { disableElevation: true } },
        MuiTextField: { defaultProps: { size: 'small', fullWidth: true } },
        MuiTableCell: { styleOverrides: { head: { fontWeight: 700 } } },
      },
    },
    faIR,
  );
}
