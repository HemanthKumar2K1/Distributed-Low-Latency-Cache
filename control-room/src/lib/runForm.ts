import type { AppConfig, KeyDistribution, RunRequest } from '../api/types';

/** Everything the form edits. Kept as text where the user types, so half-typed values are allowed. */
export interface FormState {
  rates: string;
  durationSeconds: string;
  warmupSeconds: string;
  connections: string;
  maxId: string;
  distribution: KeyDistribution;
  zipfExponent: number;
  label: string;
}

/** The form as it first appears: the server's defaults. */
export function initialForm(c: AppConfig): FormState {
  const d = c.defaults;
  return {
    rates: d.rates.join(', '),
    durationSeconds: String(d.durationSeconds),
    warmupSeconds: String(d.warmupSeconds),
    connections: String(d.connections),
    maxId: String(d.maxId),
    distribution: d.distribution,
    zipfExponent: d.zipfExponent,
    label: '',
  };
}

/** A problem message for each field that is wrong. The server checks everything again. */
type Problems = Partial<Record<keyof FormState, string>>;

/** "42" → 42. Anything that is not a whole number → NaN. */
const parseWhole = (s: string) => (/^\d+$/.test(s.trim()) ? Number(s) : NaN);

/** "100, 200 400" → [100, 200, 400]. null when any part is not a whole number above 0. */
function parseRates(text: string): number[] | null {
  const parts = text.split(/[,\s]+/).filter(Boolean).map(parseWhole);
  return parts.length > 0 && parts.every((n) => n > 0) ? parts : null;
}

/** Turns the form into the request body, or returns what is wrong. */
export function buildRequest(f: FormState, c: AppConfig): { request: RunRequest | null; problems: Problems } {
  const limits = c.limits;
  const problems: Problems = {};
  const rates = parseRates(f.rates);
  const duration = parseWhole(f.durationSeconds);
  const warmup = parseWhole(f.warmupSeconds);
  const connections = parseWhole(f.connections);
  const maxId = parseWhole(f.maxId);

  if (!rates) problems.rates = 'Whole numbers above 0, separated by commas.';
  else if (rates.some((r) => r > limits.maxRate)) problems.rates = `At most ${limits.maxRate} req/s.`;
  else if (rates.length > limits.maxSteps) problems.rates = `At most ${limits.maxSteps} steps.`;
  if (!(duration >= 1 && duration <= limits.maxDurationSeconds)) problems.durationSeconds = `1 to ${limits.maxDurationSeconds} s.`;
  if (!(warmup >= 0)) problems.warmupSeconds = 'A whole number, 0 or more.';
  if (!(connections >= 1 && connections <= limits.maxConnections)) problems.connections = `1 to ${limits.maxConnections}.`;
  if (!(maxId >= 1)) problems.maxId = 'A whole number above 0.';

  if (Object.keys(problems).length > 0 || !rates) return { request: null, problems };
  return {
    problems,
    request: {
      label: f.label.trim() || null,
      rates,
      durationSeconds: duration,
      warmupSeconds: warmup,
      connections,
      distribution: f.distribution,
      zipfExponent: f.zipfExponent,
      maxId,
    },
  };
}
