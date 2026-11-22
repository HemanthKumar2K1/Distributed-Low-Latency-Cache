# Control Room (React + TypeScript)

The dashboard for Module 6. It starts load tests, shows them live, and compares backends.
Every number comes from the load tester's API (inside Product Catalogue). Nothing is simulated or calculated here.

## Run it

Needs Node 18+. Start the Cache System nodes (7001–7003) and Product Catalogue (8080) first: see `../README.md`.

```bash
npm install
npm run dev          # http://localhost:5173
npm run typecheck    # strict TypeScript
npm run build        # production files in dist/
```

Vite forwards `/api` to Product Catalogue on 8080, so there is no CORS setup.
Different port: `LOADTEST_URL=http://localhost:9000 npm run dev`

## What is on the page

Every panel has one short grey line under its title that says what it shows.

| Panel | Shows |
|---|---|
| Top bar | Cache on/off switch, and a picker to open any saved run |
| New load test (left) | Rates, duration, warm-up, connections, key pattern (even / Zipf / hot 1%), max id, label. Start / Stop |
| Current run | Which run is open, its settings, and the step and phase it is in |
| Live numbers | Only while a test runs: throughput, p50, p99, errors, hit rate, hit vs DB p99, CPU, heap, DB connections |
| Over time | Throughput and latency per second, and the DB connection pool |
| Results per step | One row per step, measured seconds only, and whether it kept up |
| Cache nodes | Each node: up/down, keys, circuit breaker in words, and a Stop node button for the failure test |
| Compare backends | PostgreSQL only vs our cache (1 node, 3 nodes) vs Redis, on runs with the same settings |

## Where things live

```
src/
├── api/types.ts            JSON shapes, as far as the dashboard reads them
├── api/client.ts           every API call, in one place
├── hooks/                  runReducer (pure), useRunStream (SSE), useAppData, useCacheSwitch, useWidth
├── lib/                    plain functions: format, runMath, runForm, compare, labels, timeline
├── components/common/      Panel, Kpi, LineChart (hand-written SVG)
├── components/panels/      one file per panel
├── components/Header.tsx   title, cache switch, run picker
├── App.tsx                 wires data to the page
└── styles.css              every colour defined once at the top
```

## How data flows

```
load tester ──SSE──▶ useRunStream ──▶ runReducer ──▶ run ──▶ the panels
            ◀─REST── api/client.ts ◀── Start / Stop / cache switch / Stop node
```

1. Open a run: the server sends a full `snapshot`.
2. Then small events: `sample` every second, `step-start`, `step`, `state`, `finished`.
3. `runReducer` applies each one. Duplicate samples are ignored.
4. On `finished`, the saved run is fetched once more so every summary is final.
