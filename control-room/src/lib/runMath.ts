import { FINISHED, type Run, type Sample, type StepRecord, type StepSummary } from '../api/types';

/** True while a run is open and not finished yet. */
export const isRunning = (run: Run | null): boolean => run !== null && !FINISHED.has(run.status);

export const currentStep = (run: Run): StepRecord | undefined => run.steps[run.steps.length - 1];

/** The latest second that is not a drain second: what the live numbers show. */
export function latestLiveSample(step: StepRecord | undefined): Sample | undefined {
  if (!step) return undefined;
  for (let i = step.samples.length - 1; i >= 0; i--) {
    const s = step.samples[i];
    if (s && s.phase !== 'drain') return s;
  }
  return undefined;
}

/** The step the server says ran fully and kept up, at the highest delivered rate. */
export function bestSustainable(run: Run): StepSummary | undefined {
  return run.steps
    .flatMap((s) => (s.summary && !s.summary.stopped && !s.summary.saturated ? [s.summary] : []))
    .reduce<StepSummary | undefined>((best, s) => (!best || s.achievedRps > best.achievedRps ? s : best), undefined);
}
