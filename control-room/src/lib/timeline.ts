/**
 * Lays every step of a run end to end on one time axis, and builds the chart lines.
 * Each step starts where the previous one ended, plus a small gap.
 */
import type { Run, Sample } from '../api/types';
import type { Band, ChartData, StepMark } from '../components/common/LineChart';

/** Empty seconds drawn between two steps. */
const GAP_SECONDS = 2;

/** Colours are defined once in styles.css. */
const COLOR = { green: 'var(--c-green)', grey: 'var(--c-grey)', red: 'var(--c-red)', blue: 'var(--c-blue)', amber: 'var(--c-amber)' };

/** One step placed on the time axis: where it starts, how long it lasted, and its seconds. */
interface Placed {
  offset: number;
  span: number;
  samples: Sample[];
}

function place(run: Run): Placed[] {
  let offset = 0;
  return run.steps.map((step) => {
    const span = step.samples.reduce((m, s) => Math.max(m, s.t), 0);
    const placed = { offset, span, samples: step.samples };
    offset += Math.max(span, 1) + GAP_SECONDS;
    return placed;
  });
}

/** One line per metric. A null after each step keeps the gap between steps visible. */
function line(placed: Placed[], pick: (s: Sample) => number | null | undefined): [number, number | null][] {
  return placed.flatMap(({ offset, samples }) => [
    ...samples.map((s) => [offset + s.t, pick(s) ?? null] as [number, number | null]),
    [offset, null] as [number, number | null],
  ]);
}

/** Grey = warm-up (not counted), amber = drain (late requests after the step ended), and a label where each step begins. */
function bandsAndSteps(run: Run, placed: Placed[]): { bands: Band[]; steps: StepMark[] } {
  const warm = run.config.warmupSeconds;
  const end = warm + run.config.durationSeconds;
  const bands: Band[] = [];
  const steps: StepMark[] = [];
  run.steps.forEach((step, i) => {
    const p = placed[i];
    if (!p) return;
    if (warm > 0 && p.span > 0) bands.push({ from: p.offset, to: p.offset + Math.min(warm, p.span), kind: 'warmup' });
    if (p.span > end) bands.push({ from: p.offset + end, to: p.offset + p.span, kind: 'drain' });
    if (run.steps.length > 1) steps.push({ x: p.offset, label: `${step.targetRps.toLocaleString()}/s` });
  });
  return { bands, steps };
}

export const EMPTY_CHART: ChartData = { series: [], bands: [], steps: [] };

/** Throughput (left axis) and latency (right, log axis). */
export function throughputAndLatency(run: Run): ChartData {
  const placed = place(run);
  return {
    ...bandsAndSteps(run, placed),
    series: [
      { key: 'rps', label: 'Requests per second', color: COLOR.green, axis: 'left', points: line(placed, (s) => s.rps) },
      { key: 'p50', label: 'p50 latency', color: COLOR.blue, axis: 'right', points: line(placed, (s) => s.p50Ms) },
      { key: 'p99', label: 'p99 latency', color: COLOR.red, axis: 'right', points: line(placed, (s) => s.p99Ms) },
      { key: 'hit', label: 'p99 · cache hit', color: COLOR.green, axis: 'right', dashed: true, points: line(placed, (s) => s.hitP99Ms) },
      { key: 'miss', label: 'p99 · database read', color: COLOR.amber, axis: 'right', dashed: true, points: line(placed, (s) => s.missP99Ms) },
    ],
  };
}

/** The database connection pool: the bottleneck without a cache. */
export function connectionPool(run: Run): ChartData {
  const placed = place(run);
  return {
    ...bandsAndSteps(run, placed),
    series: [
      { key: 'active', label: 'In use', color: COLOR.blue, axis: 'left', points: line(placed, (s) => s.poolActive) },
      { key: 'pending', label: 'Requests waiting', color: COLOR.red, axis: 'left', points: line(placed, (s) => s.poolPending) },
      { key: 'max', label: 'Pool size', color: COLOR.grey, axis: 'left', dashed: true, points: line(placed, (s) => s.poolMax) },
    ],
  };
}
