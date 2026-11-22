import type { RunListItem, TargetCache } from '../api/types';
import { runOptionLabel } from '../lib/labels';

interface HeaderProps {
  runs: RunListItem[];
  runId: number | null;
  onPickRun: (id: number) => void;
  cache: TargetCache | null;
  cacheBusy: boolean;
  running: boolean;
  onToggleCache: () => void;
}

/** Title, the cache on/off switch and the run picker. */
export function Header({ runs, runId, onPickRun, cache, cacheBusy, running, onToggleCache }: HeaderProps) {
  const on = cache?.enabled === true;
  const canSwitch = cache?.available === true && !cacheBusy && !running;
  const title = running
    ? 'Finish or stop the run first: one run measures one setup.'
    : `Turn the cache ${on ? 'off' : 'on'}. Switching empties it, so the next run starts cold.`;
  return (
    <header className="top-bar">
      <div className="brand">Control Room <small>Distributed cache · load tests</small></div>
      <span className="spacer" />
      <button type="button" className={`btn cache-switch ${on ? 'on' : ''}`} onClick={onToggleCache} disabled={!canSwitch}
        title={cache?.available ? title : cache?.reason} aria-pressed={on}>
        Cache: {cache?.available ? (cacheBusy ? '…' : on ? 'on' : 'off') : 'unknown'}
      </button>
      <label className="run-picker">
        <span>Open run</span>
        <select value={runId ?? ''} onChange={(e) => onPickRun(Number(e.target.value))}>
          {runId === null && <option value="">No runs yet</option>}
          {runs.map((r) => <option key={r.id} value={r.id}>{runOptionLabel(r)}</option>)}
        </select>
      </label>
    </header>
  );
}
