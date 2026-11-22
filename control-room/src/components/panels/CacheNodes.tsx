import type { CacheNode, TargetCache } from '../../api/types';
import { int } from '../../lib/format';
import { Panel } from '../common/Panel';

/** The circuit breaker state in plain words. */
const BREAKER: Record<string, string> = {
  closed: 'Closed · calls go through',
  open: 'Open · node skipped',
  half_open: 'Half-open · one trial call',
};

/** Every cache node, as Product Catalogue sees it (re-read every 5 s), and the failure test. */
export function CacheNodes({ cache, onStop }: { cache: TargetCache | null; onStop: (node: CacheNode) => void }) {
  const nodes = cache?.available ? cache.nodes ?? [] : [];
  return (
    <Panel title="Cache nodes"
      hint="Keys are spread over the nodes by a hash ring. After 3 failed calls in a row a node is skipped for 5 s, and its keys are read from PostgreSQL.">
      {nodes.length === 0 ? (
        <p className="empty">{cache?.reason ?? 'Checking the cache nodes…'}</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>Node</th><th>Address</th><th>Status</th><th className="num">Keys</th><th>Circuit breaker</th><th /></tr>
            </thead>
            <tbody>
              {nodes.map((n) => (
                <tr key={n.address}>
                  <td>{n.name}</td>
                  <td className="mono">{n.address}</td>
                  <td><span className={`status ${n.up ? 'ok' : 'bad'}`}>{n.up ? 'Up' : 'Down'}</span></td>
                  <td className="num">{int(n.keys)}</td>
                  <td className="muted">{(n.breaker && BREAKER[n.breaker]) ?? '–'}</td>
                  <td className="num">
                    <button type="button" className="btn btn-small" disabled={!n.up} onClick={() => onStop(n)}
                      title="Failure test: stop this node, even during a run">Stop node</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}
