import { useMemo } from 'react';
import { useWidth } from '../../hooks/useWidth';

/**
 * A small, dependency-free SVG line chart.
 * - Left axis: linear (req/s, connections). Right axis: logarithmic (latency spans ms to seconds).
 * - A `null` y value breaks the line, so the gap between two steps stays a gap.
 * - Bands shade warm-up and drain seconds; step marks label where each rate step begins.
 */

/** One line on the chart. color is a CSS variable from styles.css, like 'var(--c-green)'. */
interface Series {
  key: string;
  label: string;
  color: string;
  axis: 'left' | 'right';
  points: ReadonlyArray<readonly [x: number, y: number | null]>;
  dashed?: boolean;
}

/** A shaded time range: warm-up (not counted) or drain (late requests after the step). */
export interface Band {
  from: number;
  to: number;
  kind: 'warmup' | 'drain';
}

/** Where a rate step begins, with its target rate as the label. */
export interface StepMark {
  x: number;
  label: string;
}

export interface ChartData {
  series: Series[];
  bands: Band[];
  steps: StepMark[];
}

interface LineChartProps extends ChartData {
  height: number;
  formatLeft: (v: number) => string;
  formatRight?: (v: number) => string;
  formatX: (v: number) => string;
  leftTitle?: string;
  rightTitle?: string;
  emptyText: string;
}

const PAD = { top: 22, right: 62, bottom: 28, left: 54 };
/** Right padding when there is no right axis. */
const PAD_RIGHT_NO_AXIS = 16;

export function LineChart({ series, bands, steps, height, formatLeft, formatRight = String, formatX,
  leftTitle, rightTitle, emptyText }: LineChartProps) {
  const { ref, width } = useWidth<HTMLDivElement>();
  const scales = useMemo(() => buildScales(series, width, height), [series, width, height]);
  const hasRight = series.some((s) => s.axis === 'right');
  const plotW = width - PAD.left - (hasRight ? PAD.right : PAD_RIGHT_NO_AXIS);

  return (
    <div className="chart" ref={ref}>
      {!scales ? (
        <div className="empty">{emptyText}</div>
      ) : (
        <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`}>
          {bands.map((b, i) => (
            <rect key={i} className={`band band-${b.kind}`} x={scales.x(b.from)} y={PAD.top}
              width={Math.max(0, scales.x(b.to) - scales.x(b.from))} height={height - PAD.top - PAD.bottom} />
          ))}

          {scales.leftTicks.map((v) => (
            <g key={`l${v}`}>
              <line className="grid" x1={PAD.left} x2={PAD.left + plotW} y1={scales.left(v)} y2={scales.left(v)} />
              <text className="tick" x={PAD.left - 8} y={scales.left(v) + 4} textAnchor="end">{formatLeft(v)}</text>
            </g>
          ))}
          {hasRight && scales.rightTicks.map((v) => (
            <text key={`r${v}`} className="tick" x={PAD.left + plotW + 8} y={scales.right(v) + 4}>{formatRight(v)}</text>
          ))}
          {scales.xTicks.map((v) => (
            <text key={`x${v}`} className="tick" x={scales.x(v)} y={height - 8} textAnchor="middle">{formatX(v)}</text>
          ))}
          {leftTitle && <text className="axis-title" x={4} y={PAD.top - 8}>{leftTitle}</text>}
          {hasRight && rightTitle && <text className="axis-title" x={width - 4} y={PAD.top - 8} textAnchor="end">{rightTitle}</text>}

          {steps.map((m, i) => (
            <g key={i}>
              <line className="step-line" x1={scales.x(m.x)} x2={scales.x(m.x)} y1={PAD.top - 4} y2={height - PAD.bottom} />
              <text className="step-label" x={scales.x(m.x) + 4} y={PAD.top - 8}>{m.label}</text>
            </g>
          ))}

          {series.map((s) => (
            <path key={s.key} d={toPath(s.points, scales.x, s.axis === 'left' ? scales.left : scales.right)}
              fill="none" style={{ stroke: s.color }} strokeWidth={s.dashed ? 1.5 : 2} strokeDasharray={s.dashed ? '5 4' : undefined} />
          ))}
        </svg>
      )}

      <div className="legend">
        {series.map((s) => (
          <span key={s.key}><i style={{ background: s.color }} className={s.dashed ? 'dashed' : ''} />{s.label}</span>
        ))}
      </div>
    </div>
  );
}

/* ---------- scales and paths ---------- */

type Scale = (v: number) => number;

interface Scales {
  x: Scale;
  left: Scale;
  right: Scale;
  leftTicks: number[];
  rightTicks: number[];
  xTicks: number[];
}

/** Roughly one x label per this many pixels. */
const PX_PER_X_TICK = 90;
/** Headroom above the highest left-axis value. */
const LEFT_HEADROOM = 1.08;

function buildScales(series: Series[], width: number, height: number): Scales | null {
  const all = series.flatMap((s) => s.points.filter((p): p is readonly [number, number] => p[1] !== null));
  if (all.length === 0) return null;
  const hasRight = series.some((s) => s.axis === 'right');
  const x0 = PAD.left;
  const x1 = width - (hasRight ? PAD.right : PAD_RIGHT_NO_AXIS);
  const y0 = height - PAD.bottom;
  const y1 = PAD.top;

  const xs = all.map((p) => p[0]);
  const xMin = Math.min(...xs);
  const xMax = Math.max(...xs, xMin + 1);

  const leftVals = series.filter((s) => s.axis === 'left').flatMap((s) => s.points.map((p) => p[1] ?? 0));
  const leftMax = niceCeil(Math.max(1, ...leftVals) * LEFT_HEADROOM);

  const rightVals = series
    .filter((s) => s.axis === 'right')
    .flatMap((s) => s.points.map((p) => p[1]))
    .filter((v): v is number => v !== null && v > 0);
  const rLo = 10 ** Math.floor(Math.log10(Math.max(0.1, Math.min(1, ...rightVals))));
  const rHi = 10 ** Math.ceil(Math.log10(Math.max(10, ...rightVals)));

  const linear = (d0: number, d1: number, r0: number, r1: number): Scale =>
    (v) => r0 + ((v - d0) / (d1 - d0 || 1)) * (r1 - r0);
  const log = (d0: number, d1: number, r0: number, r1: number): Scale => {
    const L0 = Math.log10(d0);
    const L1 = Math.log10(d1);
    return (v) => r0 + ((Math.log10(Math.min(d1, Math.max(d0, v))) - L0) / (L1 - L0)) * (r1 - r0);
  };

  const rough = (xMax - xMin) / Math.max(2, Math.floor((x1 - x0) / PX_PER_X_TICK));
  const step = [5, 10, 15, 30, 60, 120, 300, 600].find((s) => s >= rough) ?? 1200;
  const xTicks: number[] = [];
  for (let v = Math.ceil(xMin / step) * step; v <= xMax; v += step) xTicks.push(v);

  const rightTicks: number[] = [];
  for (let v = rLo; v <= rHi * 1.001; v *= 10) rightTicks.push(v);

  return {
    x: linear(xMin, xMax, x0, x1),
    left: linear(0, leftMax, y0, y1),
    right: log(rLo, rHi, y0, y1),
    leftTicks: [0, 1, 2, 3, 4].map((i) => (leftMax * i) / 4),
    rightTicks,
    xTicks,
  };
}

/** Rounds up to 1, 2, 2.5 or 5 × a power of ten, so axis labels are tidy. */
function niceCeil(v: number): number {
  const p = 10 ** Math.floor(Math.log10(v));
  const f = v / p;
  return (f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10) * p;
}

function toPath(points: Series['points'], x: Scale, y: Scale): string {
  let d = '';
  let penDown = false;
  for (const [px, py] of points) {
    if (py === null) {
      penDown = false;
      continue;
    }
    d += `${penDown ? 'L' : 'M'}${x(px).toFixed(1)},${y(py).toFixed(1)}`;
    penDown = true;
  }
  return d;
}
