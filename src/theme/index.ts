import { createContext, useContext } from 'react';
import type { ConfidenceCode } from '../domain/types';

export interface Palette {
  background: string;
  surface: string;
  surfaceAlt: string;
  text: string;
  textMuted: string;
  border: string;
  primary: string;
  onPrimary: string;
  danger: string;
  warning: string;
  success: string;
  verified: string;
  estimated: string;
  timeOnly: string;
  unavailable: string;
  track: string;
}

export const light: Palette = {
  background: '#F6F7F9',
  surface: '#FFFFFF',
  surfaceAlt: '#EEF1F5',
  text: '#15181D',
  textMuted: '#5B6470',
  border: '#DCE1E8',
  primary: '#2F6BFF',
  onPrimary: '#FFFFFF',
  danger: '#C62828',
  warning: '#A15C00',
  success: '#1B7F3B',
  verified: '#1B7F3B',
  estimated: '#2F6BFF',
  timeOnly: '#7A5AF8',
  unavailable: '#8A929C',
  track: '#E3E7ED',
};

export const dark: Palette = {
  background: '#0F1216',
  surface: '#181C22',
  surfaceAlt: '#222831',
  text: '#F1F3F6',
  textMuted: '#A3ACB8',
  border: '#2C333D',
  primary: '#7CA2FF',
  onPrimary: '#0F1216',
  danger: '#FF8A80',
  warning: '#FFB74D',
  success: '#6FD08C',
  verified: '#6FD08C',
  estimated: '#7CA2FF',
  timeOnly: '#B9A6FF',
  unavailable: '#7D8691',
  track: '#2C333D',
};

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 } as const;
export const radius = { sm: 8, md: 12, lg: 16 } as const;

export const ThemeContext = createContext<{
  palette: Palette;
  isDark: boolean;
}>({
  palette: light,
  isDark: false,
});

export const useTheme = () => useContext(ThemeContext);

export function confidenceColor(
  palette: Palette,
  code: ConfidenceCode,
): string {
  switch (code) {
    case 'A':
      return palette.verified;
    case 'B':
      return palette.estimated;
    case 'C':
      return palette.timeOnly;
    default:
      return palette.unavailable;
  }
}
