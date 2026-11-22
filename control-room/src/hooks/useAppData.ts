import { useCallback, useEffect, useState } from 'react';
import { api } from '../api/client';
import type { AppConfig, RunListItem } from '../api/types';

/** Loads what the page needs once: config and run history. */
export function useAppData() {
  const [config, setConfig] = useState<AppConfig | null>(null);
  const [runs, setRuns] = useState<RunListItem[]>([]);
  const [error, setError] = useState<string | null>(null);

  const refreshRuns = useCallback(async () => {
    setRuns((await api.listRuns().catch(() => null)) ?? []);
  }, []);

  useEffect(() => {
    api
      .config()
      .then(setConfig)
      .catch((e: Error) => setError(`Cannot reach the load tester: ${e.message}`));
    void refreshRuns();
  }, [refreshRuns]);

  return { config, runs, error, refreshRuns };
}
