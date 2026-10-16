# Grove — local-first habit & goal tracker

Full-stack Scala demo of **local-first** software with optional multi-device sync.

| Module | Stack | Role |
|--------|--------|------|
| `shared` | Scala 3 (JVM + JS) | Models, validation, LWW/CRDT merge |
| `frontend` | Scala.js + Laminar | Reactive UI, offline persistence |
| `backend` | http4s Ember | Optional auth + sync API + static hosting |

The UI works with **no server**. Sign-in and sync are progressive enhancement.

The habits view shows how many habits are done today and a streak of consecutive days with at least one check-in. Today stays open until you check something in, so the streak does not drop to zero first thing in the morning.

## Quick start

**Requirements:** JDK 17+, [sbt 2.x runner](https://www.scala-sbt.org/download/) (`sbt --script-version` should report 2.0+)

```bash
export PATH="$HOME/tools/sbt-2.0.4/bin:$PATH"
sbt 'frontendJS/fastLinkJS; backend/run'
```

```bash
# 1) Build the Scala.js bundle into web/js
sbt frontendJS/fastLinkJS

# 2) Run shared tests
sbt shared/test

# 3) Start the optional sync server (also serves web/)
sbt backend/run
```

Project ids (sbt 2 matrix): `shared` / `sharedJS`, `frontendJS`, `backend`.

Open [http://localhost:8080](http://localhost:8080).

Client-only (no backend): after `fastLinkJS`, serve the `web/` folder with any static server, e.g. `python3 -m http.server 8000 --directory web`.

## Architecture

```
┌─────────────────────┐     optional sync      ┌──────────────────┐
│  Laminar frontend   │ ─────────────────────► │  http4s backend  │
│  localStorage doc   │ ◄── LWW merge result ─ │  in-memory store │
└─────────┬───────────┘                        └────────┬─────────┘
          │                                             │
          └──────────────────┬──────────────────────────┘
                             ▼
                      shared (JVM + JS)
                 models · validation · Merge
```

### Conflict resolution

Entities carry `updatedAt` + `deviceId` and soft-delete via `deleted` tombstones.

- **LWW register** per entity: later timestamp wins; ties break on `deviceId`
- **LWW-Map** for collections: union keys, merge each value
- Same `Merge.mergeDocuments` runs on client and server

### API (optional)

| Method | Path | Auth | Body |
|--------|------|------|------|
| `POST` | `/auth/register` | — | `{ username, password }` |
| `POST` | `/auth/login` | — | `{ username, password }` |
| `POST` | `/sync` | Bearer token | `{ document: SyncDocument }` |
| `GET` | `/health` | — | — |

## Project layout

```
shared/     cross-compiled domain
frontend/   Scala.js + Laminar app
backend/    http4s sync service
web/        index.html + styles + linked JS
```

## Design notes

- Start purely client-side; add the server when you need a second device
- Persistence is localStorage with a document blob — easy to swap for IndexedDB
- Auth is demo-grade (SHA-256 password hash, in-memory users/tokens)
