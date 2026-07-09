import type { AuthVO } from '@/services/authController';

export type ThemeType = 'dark' | 'light';

export interface GlobalType {
  authVO?: AuthVO;
  theme?: ThemeType;
}
