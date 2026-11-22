interface KpiProps {
  label: string;
  value: string;
  /** One short line: what the number means. */
  hint: string;
}

/** One number with its label above and its meaning below. */
export function Kpi({ label, value, hint }: KpiProps) {
  return (
    <div className="kpi">
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">{value}</div>
      <div className="kpi-hint">{hint}</div>
    </div>
  );
}
