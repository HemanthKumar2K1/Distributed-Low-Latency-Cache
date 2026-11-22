import type { Run } from '../../api/types';
import { int, ms, pct, pctOfFraction } from '../../lib/format';
import { Panel } from '../common/Panel';

/** One row per step: the measured seconds only, summed up by the load tester. */
export function StepResults({ run }: { run: Run | null }) {
  const steps = (run?.steps ?? []).filter((s) => s.summary !== null);
  return (
    <Panel title="Results per step"
      hint="Measured seconds only (warm-up excluded). Kept up = at least 95% of the target delivered, at most 0.5% errors, and requests rarely waiting for a database connection.">
      {steps.length === 0 ? (
        <p className="empty">No finished step yet.</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th className="num">Target</th><th className="num">Delivered</th><th className="num">p50</th><th className="num">p99</th>
                <th className="num">Errors</th><th className="num">Hit rate</th><th className="num">p99 hit</th><th className="num">p99 DB</th>
                <th className="num">CPU</th><th className="num">Heap</th><th className="num">DB conns</th><th>Kept up?</th>
              </tr>
            </thead>
            <tbody>
              {steps.map(({ index, targetRps, summary: s }) => s && (s.measuredSeconds === 0 ? (
                <tr key={index}>
                  <td className="num">{int(targetRps)}/s</td>
                  <td colSpan={10} className="muted">Stopped during warm-up: nothing was measured.</td>
                  <td><span className="muted">stopped</span></td>
                </tr>
              ) : (
                <tr key={index}>
                  <td className="num">{int(s.targetRps)}/s</td>
                  <td className="num">{int(s.achievedRps)}/s</td>
                  <td className="num">{ms(s.latency.p50Ms)}</td>
                  <td className="num">{ms(s.latency.p99Ms)}</td>
                  <td className="num">{pct(s.errorPct)}</td>
                  <td className="num">{pct(s.server.hitRatePctAvg)}</td>
                  <td className="num">{ms(s.server.hitP99MsAvg)}</td>
                  <td className="num">{ms(s.server.missP99MsAvg)}</td>
                  <td className="num">{pctOfFraction(s.server.cpuProcessAvg)}</td>
                  <td className="num">{s.server.heapMbMax === null ? '–' : `${int(s.server.heapMbMax)} MB`}</td>
                  <td className="num">{int(s.server.poolActiveMax)} of {int(s.server.poolMax)}</td>
                  <td>{s.stopped ? <span className="muted">stopped</span>
                    : <span className={`status ${s.saturated ? 'bad' : 'ok'}`}>{s.saturated ? 'No' : 'Yes'}</span>}</td>
                </tr>
              )))}
            </tbody>
          </table>
        </div>
      )}
      <p className="footnote">CPU is the average for Product Catalogue; heap and DB connections are the peak. p99 hit / DB are timed inside the server.</p>
    </Panel>
  );
}
