import { useState, type ReactNode } from 'react';
import type { AppConfig, KeyDistribution, RunRequest } from '../../api/types';
import { buildRequest, initialForm, type FormState } from '../../lib/runForm';
import { Panel } from '../common/Panel';

const KEY_PATTERNS: { value: KeyDistribution; label: string; help: string }[] = [
  { value: 'UNIFORM', label: 'Even', help: 'Every product equally likely.' },
  { value: 'ZIPFIAN', label: 'Zipf', help: 'A few products are very popular, most are rare, like real shoppers.' },
  { value: 'HOTSPOT', label: 'Hot 1%', help: '90% of requests go to 1% of the products.' },
];

interface NewTestProps {
  config: AppConfig;
  running: boolean;
  onStart: (request: RunRequest) => void;
  onStop: () => void;
}

/** The load test form: what to send, how fast, for how long. */
export function NewTest({ config, running, onStart, onStop }: NewTestProps) {
  const [form, setForm] = useState<FormState>(() => initialForm(config));
  const [showErrors, setShowErrors] = useState(false);
  const set = (patch: Partial<FormState>) => setForm((f) => ({ ...f, ...patch }));
  const { request, problems } = buildRequest(form, config);
  const error = (field: keyof FormState) => (showErrors ? problems[field] : undefined);

  const start = () => {
    setShowErrors(true);
    if (request) onStart(request);
  };

  return (
    <Panel title="New load test"
      hint="Requests go out on a fixed schedule (open loop), and latency counts from when each one was due. Fixed seed 42, so runs repeat.">
      <Field label="Rates (requests per second)" help="One number = one step. Several = a sweep, run in order." error={error('rates')}>
        <input type="text" className="mono" value={form.rates} disabled={running} onChange={(e) => set({ rates: e.target.value })} />
      </Field>
      <div className="two">
        <Field label="Duration per step (s)" error={error('durationSeconds')}>
          <input type="number" min={1} value={form.durationSeconds} disabled={running} onChange={(e) => set({ durationSeconds: e.target.value })} />
        </Field>
        <Field label="Warm-up per step (s)" help="Not counted." error={error('warmupSeconds')}>
          <input type="number" min={0} value={form.warmupSeconds} disabled={running} onChange={(e) => set({ warmupSeconds: e.target.value })} />
        </Field>
      </div>
      <div className="two">
        <Field label="Connections" help="Most requests in flight." error={error('connections')}>
          <input type="number" min={1} value={form.connections} disabled={running} onChange={(e) => set({ connections: e.target.value })} />
        </Field>
        <Field label="Max product id" error={error('maxId')}>
          <input type="number" min={1} value={form.maxId} disabled={running} onChange={(e) => set({ maxId: e.target.value })} />
        </Field>
      </div>
      <Field label="Which products are asked for" help={KEY_PATTERNS.find((k) => k.value === form.distribution)?.help}>
        <div className="segmented" role="radiogroup">
          {KEY_PATTERNS.map((k) => (
            <button key={k.value} type="button" role="radio" aria-checked={k.value === form.distribution}
              className={k.value === form.distribution ? 'on' : ''} disabled={running} onClick={() => set({ distribution: k.value })}>
              {k.label}
            </button>
          ))}
        </div>
      </Field>
      {form.distribution === 'ZIPFIAN' && (
        <Field label={`Zipf skew: ${form.zipfExponent.toFixed(2)}`} help="Higher = popular products get even more of the traffic.">
          <input type="range" min={0.5} max={1.5} step={0.01} value={form.zipfExponent} disabled={running}
            onChange={(e) => set({ zipfExponent: Number(e.target.value) })} />
        </Field>
      )}
      <Field label="Label (optional)">
        <input type="text" maxLength={80} placeholder="e.g. cache off, pool 10" value={form.label} disabled={running}
          onChange={(e) => set({ label: e.target.value })} />
      </Field>
      <div className="actions">
        <button type="button" className="btn btn-primary" onClick={start} disabled={running}>{running ? 'Running…' : 'Start'}</button>
        <button type="button" className="btn" onClick={onStop} disabled={!running}>Stop</button>
      </div>
    </Panel>
  );
}

function Field({ label, help, error, children }: { label: string; help?: string; error?: string; children: ReactNode }) {
  return (
    <label className={`field ${error ? 'has-error' : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="field-error">{error}</span> : help && <span className="field-help">{help}</span>}
    </label>
  );
}
