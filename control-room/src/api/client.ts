import type { AppConfig, Run, RunListItem, RunRequest, TargetCache } from './types';

async function request<T>(path: string, init: RequestInit = {}): Promise<T | null> {
  const res = await fetch(path, { headers: { 'Content-Type': 'application/json' }, ...init });
  if (res.status === 204) return null;
  const text = await res.text();
  const body: unknown = text ? JSON.parse(text) : null;
  if (!res.ok) {
    // Spring returns RFC 7807 ProblemDetail: { title, detail, status }
    const problem = body as { detail?: string; title?: string } | null;
    throw new Error(problem?.detail ?? problem?.title ?? `HTTP ${res.status}`);
  }
  return body as T;
}

/** Every call to the load tester lives here, so an endpoint change is a one-line edit. */
export const api = {
  config: () => request<AppConfig>('/api/config'),
  listRuns: () => request<RunListItem[]>('/api/runs'),
  activeRun: () => request<RunListItem>('/api/runs/active'),
  getRun: (id: number) => request<Run>(`/api/runs/${id}`),
  startRun: (body: RunRequest) =>
    request<{ id: number }>('/api/runs', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  stopRun: (id: number) => request<void>(`/api/runs/${id}/stop`, { method: 'POST' }),
  streamUrl: (id: number) => `/api/runs/${id}/stream`,
  targetCache: () => request<TargetCache>('/api/target/cache'),
  setTargetCache: (enabled: boolean) =>
    request<TargetCache>('/api/target/cache', { method: 'PUT', body: JSON.stringify({ enabled }) }),
  killNode: (name: string) =>
    request<unknown>(`/api/target/cache/nodes/${encodeURIComponent(name)}/kill`, { method: 'POST' }),
};
