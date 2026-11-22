import { useEffect, useReducer, useRef } from 'react';
import { api } from '../api/client';
import { FINISHED, type Run, type RunListItem, type Sample, type StepSummary } from '../api/types';
import { runReducer, type RunEvent } from './runReducer';

/**
 * Follows one run live over Server-Sent Events.
 * Pass null to follow nothing. When the run finishes, the full saved run is
 * fetched once more so every summary is final, then onFinished is called.
 */
export function useRunStream(runId: number | null, onFinished?: (run: Run) => void): Run | null {
  const [run, dispatch] = useReducer(runReducer, null);

  // Keep the latest onFinished in a ref, so a new callback each render does not reopen the stream.
  const onFinishedRef = useRef(onFinished);
  useEffect(() => { onFinishedRef.current = onFinished; }, [onFinished]);

  useEffect(() => {
    if (runId === null) {
      dispatch({ type: 'clear' });
      return;
    }
    const source = new EventSource(api.streamUrl(runId));
    let closed = false;       // no more live events wanted
    let current = true;       // false once the page has moved to another run
    const close = () => {
      closed = true;
      source.close();
    };

    // Each server event name maps to one reducer event.
    const listen = <T,>(name: string, toEvent: (data: T) => RunEvent) =>
      source.addEventListener(name, (e) => {
        if (!closed) dispatch(toEvent(JSON.parse((e as MessageEvent<string>).data) as T));
      });

    listen<Run>('snapshot', (run) => {
      if (FINISHED.has(run.status)) close();          // a finished run needs no live updates
      return { type: 'snapshot', run };
    });
    listen<RunListItem>('state', (item) => ({ type: 'state', item }));
    listen<{ index: number; targetRps: number }>('step-start', (d) => ({ type: 'step-start', ...d }));
    listen<Sample>('sample', (sample) => ({ type: 'sample', sample }));
    listen<{ index: number; status: string; summary: StepSummary }>('step', (d) => ({ type: 'step', ...d }));
    source.addEventListener('finished', async () => {
      close();
      const full = await api.getRun(runId).catch(() => null);
      if (!full) return;
      // The page may have opened another run while we waited: then do not show this one.
      if (current) dispatch({ type: 'snapshot', run: full });
      onFinishedRef.current?.(full);
    });
    // On a dropped connection EventSource reconnects by itself, and the next snapshot restores everything.

    return () => {
      current = false;
      close();
    };
  }, [runId]);

  return run;
}
