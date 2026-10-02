import { createTheme } from '@mui/material/styles';
import createCache from '@emotion/cache';
import { prefixer } from 'stylis';
import rtlPlugin from 'stylis-plugin-rtl';
import { faIR } from '@mui/material/locale';

export const rtlCache = createCache({ key: 'muirtl', stylisPlugins: [prefixer, rtlPlugin] });

const font = '"Vazirmatn", "Tahoma", sans-serif';

export function buildTheme(mode: 'light' | 'dark') {
  return createTheme(
    {
      direction: 'rtl',
      palette: {
        mode,
        primary: { main: '#0f766e' },
        secondary: { main: '#c2410c' },
        background: mode === 'light' ? { default: '#f4f6f8', paper: '#ffffff' } : { default: '#0b1215', paper: '#131c20' },
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
