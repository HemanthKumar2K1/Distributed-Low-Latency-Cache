import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { TargetCache } from '../api/types';

/** How often node health is re-read, so a stopped Cache System shows up within seconds. */
const POLL_MS = 5000;

/** Product Catalogue's cache switch and Cache System's state, as the server reports them. Re-read when refreshKey changes. */
export function useCacheSwitch(refreshKey: unknown) {
  const [cache, setCache] = useState<TargetCache | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    setCache((await api.targetCache().catch(() => null)) ?? { available: false, reason: 'load tester unreachable' });
  }, []);

  useEffect(() => { void refresh(); }, [refresh, refreshKey]);
  useEffect(() => {
    const id = setInterval(() => void refresh(), POLL_MS);
    return () => clearInterval(id);
  }, [refresh]);

  /** Returns an error message, or null on success. The server refuses (409) during a run. */
  const toggle = useCallback(async (): Promise<string | null> => {
    if (!cache?.available) return cache?.reason ?? 'cache status unknown';
    setBusy(true);
    try {
      setCache(await api.setTargetCache(!cache.enabled));
      return null;
    } catch (e) {
      return (e as Error).message;
    } finally {
      setBusy(false);
    }
  }, [cache]);

  /**
   * Failure test: stop one node on purpose. Allowed during a run. Returns an error message, or null.
   * The node exits about 0.1 s after it answers, so the status is re-read a moment later.
   */
  const killNode = useCallback(async (name: string): Promise<string | null> => {
    try {
      await api.killNode(name);
      return null;
    } catch (e) {
      return (e as Error).message;
    } finally {
      setTimeout(() => void refresh(), 1000);
    }
  }, [refresh]);

  return { cache, busy, toggle, killNode };
}
