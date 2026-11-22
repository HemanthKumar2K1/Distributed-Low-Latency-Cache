/**
 * The load tester's JSON, as far as the dashboard reads it.
 * Field names match the Java records exactly (Jackson writes record component names as JSON keys).
 * `| null` means "not measured", e.g. no server metrics for that second.
 */

export type RunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'STOPPED' | 'FAILED';
export type KeyDistribution = 'UNIFORM' | 'ZIPFIAN' | 'HOTSPOT';
type Phase = 'warmup' | 'measure' | 'drain';

export const FINISHED: ReadonlySet<RunStatus> = new Set(['COMPLETED', 'STOPPED', 'FAILED']);

/** What the form sends to POST /api/runs. */
export interface RunRequest {
  label: string | null;
  rates: number[];
  durationSeconds: number;
  warmupSeconds: number;
  connections: number;
  distribution: KeyDistribution;
  zipfExponent: number;
  maxId: number;
}

/** RunConfig.java: the request after validation, stored with the run. */
export interface RunConfig extends RunRequest {
  seed: number;
}

/** Sample.java: one second of one step. */
export interface Sample {
  step: number;
  phase: Phase;
  t: number;
  /** Load generator: successful responses this second, errors, latency from the intended send time. */
  rps: number | null;
  errors: number;
  p50Ms: number | null;
  p99Ms: number | null;
  /** Product Catalogue (/actuator/prometheus): DB pool, CPU (0–1), heap, cache. */
  poolActive: number | null;
  poolPending: number | null;
  poolMax: number | null;
  cpuProcess: number | null;
  cpuSystem: number | null;
  heapMb: number | null;
  hitRatePct: number | null;
  /** p99 of cache hits and of reads that went to the database, timed inside the server. Missing on older runs. */
  hitP99Ms?: number | null;
  missP99Ms?: number | null;
}

/** StepSummary.java: one step, over its measured seconds only. */
export interface StepSummary {
  targetRps: number;
  achievedRps: number;
  deliveredPct: number;
  /** Seconds that counted. 0 when the run was stopped during warm-up. */
  measuredSeconds: number;
  errorPct: number;
  latency: { p50Ms: number; p99Ms: number };
  server: {
    poolMax: number | null;
    poolActiveMax: number | null;
    poolPendingMax: number | null;
    cpuProcessAvg: number | null;
    cpuSystemAvg: number | null;
    heapMbMax: number | null;
    hitRatePctAvg: number | null;
    hitP99MsAvg?: number | null;
    missP99MsAvg?: number | null;
  };
  /** The run was stopped during this step, so its numbers are partial. */
  stopped: boolean;
  /** Did not keep up: under 95% delivered, over 0.5% errors, or DB waits in 20%+ of seconds. */
  saturated: boolean;
}

export interface StepRecord {
  index: number;
  targetRps: number;
  status: string;
  samples: Sample[];
  summary: StepSummary | null;
}

/** What the run recorded about its setup. Missing on older runs. */
interface RunEnvironment {
  /** "on" or "off": was the cache in use? */
  cache?: string;
  /** "cache-system ×3", "redis ×1", … */
  cacheBackend?: string;
  /** Memory the cache processes used when the run ended (bytes). */
  cacheUsedMemoryAtEnd?: number | null;
}

/** Run.java: everything about one run. */
export interface Run {
  id: number;
  label: string | null;
  status: RunStatus;
  config: RunConfig;
  environment: RunEnvironment;
  steps: StepRecord[];
}

/** RunListItem.java: one row of history. */
export interface RunListItem {
  id: number;
  label: string | null;
  status: RunStatus;
  config: RunConfig | null;
  cache?: string | null;
  cacheBackend?: string | null;
}

/** GET /api/config */
export interface AppConfig {
  defaults: Omit<RunRequest, 'label'>;
  limits: { maxRate: number; maxDurationSeconds: number; maxConnections: number; maxSteps: number };
}

/** GET /api/target/cache: the cache switch and every node, as Product Catalogue reports them. */
export interface TargetCache {
  available: boolean;
  enabled?: boolean;
  reason?: string;
  nodes?: CacheNode[];
  /** "cache-system ×3" or "redis ×1"; null when no node answered. */
  backend?: string | null;
}

/** One cache node. keys is null when it did not answer. */
export interface CacheNode {
  name: string;
  address: string;
  up: boolean;
  keys: number | null;
  breaker?: 'closed' | 'open' | 'half_open' | null;
}
