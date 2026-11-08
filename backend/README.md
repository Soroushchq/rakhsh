# backend/ — Rakhsh Services (our own optional backend — NEVER touches Telegram traffic)

> Scope rule (project constraints): this backend handles **only** our features:
> subscriptions, cloud settings sync, ad config. It never proxies, reads or stores
> users' Telegram messages. There is no message-content field anywhere in the schema —
> by design and by review.

## Stack
- Node.js 20 + TypeScript + Fastify (small, fast, schema-first)
- SQLite at first (single-node), migration path documented
- No third-party SDKs server-side beyond Google Play Developer API (RTDN verification)

## Planned endpoints (v1)

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/devices/register` | anonymous device identity (random client UUID + attest token), returns short-lived JWT |
| POST | `/v1/settings/sync` | E2E-encrypted settings blob: client derives key from passphrase (Argon2id) → AES-256-GCM blob; server stores **opaque ciphertext only** |
| GET | `/v1/settings/sync` | fetch own blob (by device JWT) |
| GET | `/v1/subscription/entitlements` | Premium state (from Play Billing server verification) |
| POST | `/v1/billing/rtdn` | Google Play Real-Time Developer Notifications webhook (purchase/ renewal/ cancel) |
| GET | `/v1/ads/config` | ad slot config (enabled, frequency cap). **No user profiling, no message data.** |
| GET | `/healthz` | liveness |

## Security invariants
1. Server can never decrypt settings sync blobs (zero-knowledge by construction).
2. No endpoint accepts or returns message content, chat lists, or contact data.
3. All endpoints authenticated by device JWT; no email/phone collection.
4. Privacy policy will list exactly these data classes (opaque blob, device UUID, billing state).

## Status
- v0 skeleton scheduled as autopilot task (see docs/ROADMAP.md / PROGRESS.md).
- Implementation starts after Milestone 2 per milestone discipline; design frozen here.
