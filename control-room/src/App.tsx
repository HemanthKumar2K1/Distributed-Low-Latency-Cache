import { useEffect, useState } from 'react';
import { api } from './api/client';
import type { CacheNode, Run, RunRequest } from './api/types';
import { Header } from './components/Header';
import { CacheNodes } from './components/panels/CacheNodes';
import { CompareBackends } from './components/panels/CompareBackends';
import { LiveNumbers } from './components/panels/LiveNumbers';
import { NewTest } from './components/panels/NewTest';
import { OverTime } from './components/panels/OverTime';
import { RunStatus } from './components/panels/RunStatus';
import { StepResults } from './components/panels/StepResults';
import { useAppData } from './hooks/useAppData';
import { useCacheSwitch } from './hooks/useCacheSwitch';
import { useRunStream } from './hooks/useRunStream';
import { finishedMessage, restartCommand } from './lib/labels';
import { isRunning } from './lib/runMath';

/** How long a message stays on screen. Errors stay longer. */
const TOAST_MS = 4000;
const TOAST_ERROR_MS = 7000;

export function App() {
  const { config, runs, error, refreshRuns } = useAppData();
  const [runId, setRunId] = useState<number | null>(null);
  const [toast, setToast] = useState<{ text: string; bad?: boolean } | null>(null);

  const say = (text: string, bad = false) => setToast({ text, bad });
  useEffect(() => {
    if (!toast) return;
    const id = setTimeout(() => setToast(null), toast.bad ? TOAST_ERROR_MS : TOAST_MS);
    return () => clearTimeout(id);
  }, [toast]);

  // Open the running run if there is one, otherwise the newest.
  useEffect(() => {
    if (runId !== null) return;
    api.activeRun()
      .then((active) => setRunId(active?.id ?? runs[0]?.id ?? null))
      .catch(() => setRunId(runs[0]?.id ?? null));
  }, [runs, runId]);

  const run = useRunStream(runId, (finished: Run) => {
    void refreshRuns();
    const message = finishedMessage(finished);
    say(message.text, message.bad);
  });
  const running = isRunning(run);
  const cacheSwitch = useCacheSwitch(run?.status);            // re-read when a run starts or ends

  const start = async (request: RunRequest) => {
    try {
      const res = await api.startRun(request);
      if (!res) return;
      setRunId(res.id);
      void refreshRuns();
      say(`Run #${res.id} started.`);
    } catch (e) {
      say((e as Error).message, true);
    }
  };

  const stop = async () => {
    if (runId === null) return;
    try {
      await api.stopRun(runId);
      say('Stopping…');
    } catch (e) {
      say((e as Error).message, true);
    }
  };

  const stopNode = async (node: CacheNode) => {
    const backend = cacheSwitch.cache?.backend;                 // read before the stop: afterwards no node may answer
    const problem = await cacheSwitch.killNode(node.name);
    if (problem) say(problem, true);
    else say(`${node.name} stopped: its keys now come from PostgreSQL. Start it again with ${restartCommand(node, backend)}`);
  };

  const toggleCache = async () => {
    const problem = await cacheSwitch.toggle();
    if (problem) say(problem, true);
    else say('Cache switched. It is empty now, so the next run starts cold.');
  };

  if (error) return <div className="fatal">{error}. Start Product Catalogue on port 8080 and reload.</div>;
  if (!config) return <div className="fatal muted">Loading…</div>;

  return (
    <>
      <Header runs={runs} runId={runId} onPickRun={setRunId} cache={cacheSwitch.cache} cacheBusy={cacheSwitch.busy}
        running={running} onToggleCache={() => void toggleCache()} />
      <main className="layout">
        <aside className="side">
          <NewTest config={config} running={running} onStart={(r) => void start(r)} onStop={() => void stop()} />
        </aside>
        <div className="main">
          <RunStatus run={run} />
          {run && running && <LiveNumbers run={run} />}
          <OverTime run={run} />
          <StepResults run={run} />
          <CacheNodes cache={cacheSwitch.cache} onStop={(n) => void stopNode(n)} />
          <CompareBackends run={run} runs={runs} />
        </div>
      </main>
      {toast && <div className={`toast ${toast.bad ? 'bad' : ''}`} role="status">{toast.text}</div>}
    </>
  );
}
