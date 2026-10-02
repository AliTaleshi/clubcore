import { createContext, useContext } from 'react';

export const ColorModeContext = createContext<{ mode: 'light' | 'dark'; toggle: () => void }>({
  mode: 'light',
  toggle: () => undefined,
});

export const useColorMode = () => useContext(ColorModeContext);
