import type { Run } from '../../api/types';
import { int, ms, pct, pctOfFraction } from '../../lib/format';
import { currentStep, latestLiveSample } from '../../lib/runMath';
import { Kpi } from '../common/Kpi';
import { Panel } from '../common/Panel';

/** The latest measured second of the running step. Shown only while a test runs. */
export function LiveNumbers({ run }: { run: Run }) {
  const step = currentStep(run);
  const s = latestLiveSample(step);
  const warming = s?.phase === 'warmup';
  return (
    <Panel title="Live numbers"
      hint={warming ? 'Warming up: these seconds are not counted in the results.' : 'The latest second of the running step. Updates every second.'}>
      <div className="kpi-grid">
        <Kpi label="Throughput" value={s ? `${int(s.rps)} req/s` : '–'} hint={`successful responses · target ${int(step?.targetRps)}`} />
        <Kpi label="p50 latency" value={ms(s?.p50Ms)} hint="half of the requests were faster" />
        <Kpi label="p99 latency" value={ms(s?.p99Ms)} hint="99 of 100 requests were faster" />
        <Kpi label="Errors" value={s ? int(s.errors) : '–'} hint="failed requests in this second" />
        <Kpi label="Cache hit rate" value={pct(s?.hitRatePct)} hint="reads answered by the cache" />
        <Kpi label="p99 · cache hit vs database" value={`${ms(s?.hitP99Ms)} / ${ms(s?.missP99Ms)}`} hint="timed inside Product Catalogue" />
        <Kpi label="CPU" value={pctOfFraction(s?.cpuProcess)} hint={`Product Catalogue · whole machine ${pctOfFraction(s?.cpuSystem)}`} />
        <Kpi label="Heap memory" value={s ? `${int(s.heapMb)} MB` : '–'} hint="Product Catalogue JVM heap in use" />
        <Kpi label="DB connections" value={s ? `${int(s.poolActive)} of ${int(s.poolMax)}` : '–'} hint={`in use · ${int(s?.poolPending)} requests waiting`} />
      </div>
    </Panel>
  );
}
