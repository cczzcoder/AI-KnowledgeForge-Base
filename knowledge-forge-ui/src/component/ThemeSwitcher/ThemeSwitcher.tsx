import React from 'react';

interface ThemeSwitcherProps {
  value: 'light' | 'dark';
  onChange: (mode: 'light' | 'dark') => void;
}

const ThemeSwitcher: React.FC<ThemeSwitcherProps> = ({ value, onChange }) => {
  return value === 'light' ? (
    <span
      style={{ cursor: 'pointer', fontSize: 20, userSelect: 'none' }}
      onClick={() => onChange('dark')}
      title="切换为暗黑模式"
    >
      🌙
    </span>
  ) : (
    <span
      style={{ cursor: 'pointer', fontSize: 20, userSelect: 'none' }}
      onClick={() => onChange('light')}
      title="切换为明亮模式"
    >
      ☀️
    </span>
  );
};

export default ThemeSwitcher;
