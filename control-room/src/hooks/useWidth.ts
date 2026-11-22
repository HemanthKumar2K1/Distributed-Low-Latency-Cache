import { useEffect, useRef, useState } from 'react';

/** Width before the first measurement. */
const INITIAL_WIDTH = 800;
/** Charts never draw narrower than this. */
const MIN_WIDTH = 320;

/** Measures an element's width and updates on resize, so charts draw at real pixel size. */
export function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(INITIAL_WIDTH);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry) setWidth(Math.max(MIN_WIDTH, Math.round(entry.contentRect.width)));
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  return { ref, width };
}
