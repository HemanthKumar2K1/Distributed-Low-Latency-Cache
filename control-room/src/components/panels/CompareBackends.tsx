import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Run, RunListItem } from '../../api/types';
import { BACKENDS, type Backend, type Column, columnOf, matchingRuns } from '../../lib/compare';
import { bytes, int, ms } from '../../lib/format';
import { isRunning } from '../../lib/runMath';
import { Panel } from '../common/Panel';

const ROWS: { label: string; value: (c: Column) => string }[] = [
  { label: 'Run', value: (c) => `#${c.runId}` },
  { label: 'Highest rate kept up', value: (c) => (c.best ? `${int(c.best.achievedRps)} req/s` : 'none') },
  { label: 'p50 at that rate', value: (c) => ms(c.best?.latency.p50Ms) },
  { label: 'p99 at that rate', value: (c) => ms(c.best?.latency.p99Ms) },
  { label: 'p99 · cache hit', value: (c) => ms(c.best?.server.hitP99MsAvg) },
  { label: 'Cache memory at end', value: (c) => bytes(c.memoryBytes) },
];

/** PostgreSQL alone vs our cache vs Redis, on runs with the open run's exact settings. */
export function CompareBackends({ run, runs }: { run: Run | null; runs: RunListItem[] }) {
  const ready = run !== null && !isRunning(run);
  const ids = ready ? matchingRuns(run, runs) : {};
  const key = BACKENDS.map((b) => ids[b.key] ?? '').join(',');
  const [columns, setColumns] = useState<Partial<Record<Backend, Column>>>({});

  useEffect(() => {
    setColumns({});
    if (!ready || !run) return;
    let live = true;
    for (const b of BACKENDS) {
      const id = ids[b.key];
      if (id === undefined) continue;
      const load = id === run.id ? Promise.resolve(run) : api.getRun(id);
      load.then((r) => { if (live && r) setColumns((c) => ({ ...c, [b.key]: columnOf(r) })); }).catch(() => undefined);
    }
    return () => { live = false; };
    // `key` changes exactly when the matched run ids change, so it stands in for `ids`.
  }, [key, run?.id, ready]);

  return (
    <Panel title="Compare backends"
      hint="The newest finished run of each backend with exactly the same settings as the open run (rates, duration, warm-up, connections, keys, seed). Filled in once the open run has finished.">
      <div className="table-wrap">
        <table className="table compare">
          <thead>
            <tr><th />{BACKENDS.map((b) => <th key={b.key} className="num">{b.title}</th>)}</tr>
          </thead>
          <tbody>
            {ROWS.map((r) => (
              <tr key={r.label}>
                <td>{r.label}</td>
                {BACKENDS.map((b) => {
                  const col = columns[b.key];
                  return <td key={b.key} className="num">{col ? r.value(col) : '–'}</td>;
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="footnote">
        To fill a column, run the same test with the cache off, with one node (profile one-node), with three nodes, and on Redis
        (profile redis). Memory: Redis used_memory vs the JVM heap of our node(s), garbage included.
      </p>
    </Panel>
  );
}
