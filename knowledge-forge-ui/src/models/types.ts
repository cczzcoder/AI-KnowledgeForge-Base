export type ThemeType = 'dark' | 'light';

export interface GlobalType {
  authVO?: API.AuthVO;
  theme?: ThemeType;
}