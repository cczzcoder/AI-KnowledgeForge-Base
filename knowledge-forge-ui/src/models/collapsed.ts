import { useState, useCallback } from 'react';

export function useCollapsedModel() {
  const [menuCollapsed, setMenuCollapsed] = useState(false);

  const toggleCollapsed = useCallback(() => {
    setMenuCollapsed((prev) => !prev);
  }, []);

  return {
    menuCollapsed,
    setMenuCollapsed,
    toggleCollapsed,
  };
}