import type { Run, RunConfig, RunListItem, StepSummary } from '../api/types';
import { bestSustainable } from './runMath';

/**
 * Compare backends: the same test on PostgreSQL alone, our cache (1 and 3 nodes) and Redis.
 * Only runs that were really measured, and only runs with exactly the same test settings.
 */

export type Backend = 'db' | 'ours1' | 'ours3' | 'redis';

export const BACKENDS: { key: Backend; title: string }[] = [
  { key: 'db', title: 'PostgreSQL only' },
  { key: 'ours1', title: 'Our cache · 1 node' },
  { key: 'ours3', title: 'Our cache · 3 nodes' },
  { key: 'redis', title: 'Redis · 1 instance' },
];

/** Which backend a run used, from what it recorded at the start. null for older runs and other setups. */
function backendOf(cache: string | null | undefined, cacheBackend: string | null | undefined): Backend | null {
  if (cache === 'off') return 'db';
  if (cache !== 'on' || !cacheBackend) return null;
  if (cacheBackend === 'redis ×1') return 'redis';
  if (cacheBackend === 'cache-system ×1') return 'ours1';
  if (cacheBackend === 'cache-system ×3') return 'ours3';
  return null;
}

/** Same experiment: same rates, duration, warm-up, connections, key pattern, max id and seed. */
function sameSetup(a: RunConfig, b: RunConfig): boolean {
  return a.rates.join(',') === b.rates.join(',')
    && a.durationSeconds === b.durationSeconds
    && a.warmupSeconds === b.warmupSeconds
    && a.connections === b.connections
    && a.distribution === b.distribution
    && a.zipfExponent === b.zipfExponent
    && a.maxId === b.maxId
    && a.seed === b.seed;
}

/** For each backend, the newest completed run with the same setup as `run` (the open run counts too). */
export function matchingRuns(run: Run, runs: RunListItem[]): Partial<Record<Backend, number>> {
  const out: Partial<Record<Backend, number>> = {};
  for (const r of [...runs].sort((a, b) => b.id - a.id)) {
    if (r.status !== 'COMPLETED' || !r.config || !sameSetup(r.config, run.config)) continue;
    const kind = backendOf(r.cache, r.cacheBackend);
    if (kind && out[kind] === undefined) out[kind] = r.id;
  }
  return out;
}

/** One column of the comparison: numbers at the highest rate the run kept up with. */
export interface Column {
  runId: number;
  best: StepSummary | undefined;
  memoryBytes: number | null;
}

export const columnOf = (run: Run): Column => ({
  runId: run.id,
  best: bestSustainable(run),
  memoryBytes: run.environment.cacheUsedMemoryAtEnd ?? null,
});
