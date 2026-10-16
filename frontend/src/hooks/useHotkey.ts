import { useEffect, useRef } from 'react';

/**
 * Alt+<letter> shortcut for the keyboard-first workflows. Matches on `event.code` too, because
 * on macOS Option+letter produces a different `event.key`.
 */
export function useAltHotkey(letter: string, handler: (event: KeyboardEvent) => void, enabled = true): void {
  const handlerRef = useRef(handler);
  handlerRef.current = handler;

  useEffect(() => {
    if (!enabled) return;
    const lower = letter.toLowerCase();
    const code = `Key${letter.toUpperCase()}`;
    const onKeyDown = (event: KeyboardEvent) => {
      if (!event.altKey || event.ctrlKey || event.metaKey) return;
      if (event.key.toLowerCase() === lower || event.code === code) {
        event.preventDefault();
        handlerRef.current(event);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [letter, enabled]);
}
