import type { Run } from '../../api/types';
import { int } from '../../lib/format';
import { currentStep, isRunning, latestLiveSample } from '../../lib/runMath';
import { Panel } from '../common/Panel';

const PHASE: Record<string, string> = { warmup: 'warming up (not counted)', measure: 'measuring', drain: 'finishing late requests' };
const KEYS: Record<string, string> = { UNIFORM: 'even keys', ZIPFIAN: 'Zipf keys', HOTSPOT: 'hot 1% keys' };

/** Which run is open, and where it is. */
export function RunStatus({ run }: { run: Run | null }) {
  if (!run) {
    return <Panel title="Current run" hint="No run yet. Fill in the form and press Start."><span /></Panel>;
  }
  const step = currentStep(run);
  const sample = latestLiveSample(step);
  const c = run.config;
  return (
    <Panel title="Current run" aside={<span className={`status ${run.status === 'FAILED' ? 'bad' : run.status === 'RUNNING' ? 'live' : ''}`}>{run.status.toLowerCase()}</span>}>
      <p className="run-title">#{run.id} · {run.label ?? 'Untitled'}</p>
      <p className="muted">
        {c.rates.length} step{c.rates.length === 1 ? '' : 's'} · {c.durationSeconds} s each after {c.warmupSeconds} s warm-up
        · {int(c.connections)} connections · {KEYS[c.distribution]} · seed {c.seed}
      </p>
      {isRunning(run) && step && (
        <p>Step {step.index + 1} of {c.rates.length} · target {int(step.targetRps)} req/s
          {sample ? ` · ${PHASE[sample.phase]} · ${int(sample.t)} s` : ' · starting'}</p>
      )}
    </Panel>
  );
}
