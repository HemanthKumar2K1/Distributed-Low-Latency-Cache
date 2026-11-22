/** Number and time formatting. Every function returns '–' for missing values: never a fake 0. */

const whole = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });
const oneDecimal = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 });

const missing = (v: number | null | undefined): v is null | undefined =>
  v === null || v === undefined || Number.isNaN(v);

export const int = (v: number | null | undefined) => (missing(v) ? '–' : whole.format(v));
export const pct = (v: number | null | undefined) => (missing(v) ? '–' : `${oneDecimal.format(v)}%`);

/** A fraction (0.25) shown as a percent (25%). Used for CPU, which Micrometer reports as 0–1. */
export const pctOfFraction = (v: number | null | undefined) => (missing(v) ? '–' : pct(v * 100));

/** 512 B, 4.2 KB, 3.1 MB */
export function bytes(v: number | null | undefined): string {
  if (missing(v)) return '–';
  if (v >= 1024 * 1024) return `${oneDecimal.format(v / (1024 * 1024))} MB`;
  if (v >= 1024) return `${oneDecimal.format(v / 1024)} KB`;
  return `${whole.format(v)} B`;
}

/** 0.84 ms, 19.2 ms, 240 ms, 1.6 s */
export function ms(v: number | null | undefined): string {
  if (missing(v)) return '–';
  if (v >= 1000) return `${oneDecimal.format(v / 1000)} s`;
  if (v >= 100) return `${whole.format(v)} ms`;
  if (v >= 10) return `${oneDecimal.format(v)} ms`;
  return `${Math.round(v * 100) / 100} ms`;
}

/** 45s, 3m 20s, 1h 5m */
export function duration(seconds: number | null | undefined): string {
  if (missing(seconds)) return '–';
  const s = Math.round(seconds);
  if (s >= 3600) return `${Math.floor(s / 3600)}h ${Math.floor((s % 3600) / 60)}m`;
  if (s >= 60) return `${Math.floor(s / 60)}m ${s % 60}s`;
  return `${s}s`;
}
