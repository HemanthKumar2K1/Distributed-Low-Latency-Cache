import type { CacheNode, Run, RunListItem } from '../api/types';

/** Short texts for the run picker and the pop-up messages. */

/** One line in the run picker: "#12 · Push hard · cache on · redis ×1". */
export const runOptionLabel = (r: RunListItem): string => `#${r.id} · ${r.label ?? ''}`;

/** The command that starts a stopped node again. Redis has its own script; ours take the port. */
export function restartCommand(node: CacheNode, backend: string | null | undefined): string {
  return backend?.startsWith('redis')
    ? 'redis/start-redis.sh'
    : `cache-system/start-nodes.sh ${node.address.split(':').pop()}`;
}

/** The message shown when a run ends. `bad` makes it red (only for a failed run). */
export function finishedMessage(run: Run): { text: string; bad: boolean } {
  return { text: `Run #${run.id} ${run.status.toLowerCase()}. Results per step are below.`, bad: run.status === 'FAILED' };
}
