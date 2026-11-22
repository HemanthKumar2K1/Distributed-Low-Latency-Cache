import type { Run, RunListItem, Sample, StepRecord, StepSummary } from '../api/types';

/**
 * How live events change the run on screen.
 * Pure function: same state + same event = same result.
 *
 * The server sends a full `snapshot` first, then small events. A sample may arrive
 * twice (once inside the snapshot, once as an event), so samples are de-duplicated.
 */
export type RunEvent =
  | { type: 'snapshot'; run: Run }
  | { type: 'state'; item: RunListItem }
  | { type: 'step-start'; index: number; targetRps: number }
  | { type: 'sample'; sample: Sample }
  | { type: 'step'; index: number; status: string; summary: StepSummary }
  | { type: 'clear' };

/** A step that has just started: no samples, no summary yet. */
const newStep = (index: number, targetRps: number): StepRecord =>
  ({ index, targetRps, status: 'RUNNING', samples: [], summary: null });

export function runReducer(run: Run | null, event: RunEvent): Run | null {
  if (event.type === 'clear') return null;
  if (event.type === 'snapshot') return event.run;
  if (!run) return run;

  switch (event.type) {
    case 'state':
      return { ...run, status: event.item.status };

    case 'step-start':
      if (run.steps.some((s) => s.index === event.index)) return run;
      return { ...run, steps: [...run.steps, newStep(event.index, event.targetRps)] };

    case 'sample': {
      const s = event.sample;
      const known = run.steps.some((st) => st.index === s.step);
      let steps = run.steps;
      if (!known) {
        // A sample came before its step-start. Take the target from the run's rates.
        // A step the rates do not list would need a made-up target (a fake 0), so skip the sample.
        // The full run, loaded again after a reconnect or at the end, still has it.
        const targetRps = run.config.rates[s.step];
        if (targetRps === undefined) return run;
        steps = [...run.steps, newStep(s.step, targetRps)];
      }
      return {
        ...run,
        steps: steps.map((st) =>
          st.index !== s.step || st.samples.some((x) => x.t === s.t && x.phase === s.phase)
            ? st
            : { ...st, samples: [...st.samples, s] },
        ),
      };
    }

    case 'step':
      return {
        ...run,
        steps: run.steps.map((st) => (st.index === event.index ? { ...st, status: event.status, summary: event.summary } : st)),
      };
  }
}
