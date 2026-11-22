import type { Run } from '../../api/types';
import { duration, int, ms } from '../../lib/format';
import { connectionPool, EMPTY_CHART, throughputAndLatency } from '../../lib/timeline';
import { LineChart } from '../common/LineChart';
import { Panel } from '../common/Panel';

/** Throughput, latency and the DB pool over time: one point per second, every step side by side. */
export function OverTime({ run }: { run: Run | null }) {
  const speed = run ? throughputAndLatency(run) : EMPTY_CHART;
  const pool = run ? connectionPool(run) : EMPTY_CHART;
  return (
    <Panel title="Over time"
      hint="One point per second. Grey = warm-up (not counted). Amber = late requests finishing after a step ended.">
      <h3 className="sub-title">Throughput and latency</h3>
      <LineChart {...speed} height={260} leftTitle="req/s" rightTitle="latency (log)"
        formatLeft={int} formatRight={ms} formatX={duration}
        emptyText="Start a test, or open a run, to see requests per second and latency." />
      <h3 className="sub-title">Database connections</h3>
      <LineChart {...pool} height={150} formatLeft={int} formatX={duration}
        emptyText="No connection pool data for this run." />
    </Panel>
  );
}
