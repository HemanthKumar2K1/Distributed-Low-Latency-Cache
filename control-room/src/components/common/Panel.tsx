import type { ReactNode } from 'react';

interface PanelProps {
  title: string;
  /** One short line under the title: what this panel shows. */
  hint?: ReactNode;
  /** Small text on the right of the title, like a run number. */
  aside?: ReactNode;
  children: ReactNode;
}

/** The card every part of the dashboard sits in. */
export function Panel({ title, hint, aside, children }: PanelProps) {
  return (
    <section className="panel">
      <header className="panel-head">
        <h2>{title}</h2>
        {aside && <span className="panel-aside">{aside}</span>}
      </header>
      {hint && <p className="hint">{hint}</p>}
      {children}
    </section>
  );
}
