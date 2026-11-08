# ROADMAP.md — "[APP NAME]" execution plan & decisions log

> Milestones are executed strictly in order, stopping for review after each one.

## 1. Milestone status

| # | Milestone | Status | Evidence / Notes |
|---|---|---|---|
| M0 | Read Telegram API ToS + api_id guidelines **before any implementation** (client constraint) | ✅ **done** | `docs/COMPLIANCE.md` §2-3 (quotes verbatim, fetched 2026-11-07); raw copies in `docs/research/` |
| M1 | Build unmodified fork + build docs + CI | 🟡 **artifacts ready; execution pending** | `docs/BUILD.md`, `ci/android-ci.yml`, `patches/*`. Sandbox has no Android SDK (see README.md) → the actual `assembleDebug` + device login must run on your machine or via the provided CI. **Stop point: your review.** |
| M2 | Rebrand + theme engine | ⬜ planned | rebrand entry points already identified: `APP_PACKAGE` (`TMessagesProj_App/build.gradle:28`), `BuildVars.java`, launcher assets, `TMessagesProj/src/main/res`. Starts after M1 sign-off. |
| M3 | Free features (list below) | ⬜ planned — **needs your list approval** | §3 below |
| M4 | Premium + Play Billing; ads + UMP consent | ⬜ planned | §4-5 below |
| M5 | COMPLIANCE final, privacy policy, Play listing draft, security self-review | ⬜ planned | COMPLIANCE.md §8 checklist |

## 2. Critical finding that shapes the plan

**Telegram API ToS §1.4 (current text, fetched 2026-11-07) explicitly bans ghost mode** —
"preventing last seen and online statuses from being displayed correctly, tampering with
the 'read' statuses of messages (e.g. implementing a 'ghost mode'), preventing typing
statuses from being sent/displayed".

→ Per your instruction, this feature will **not** be implemented and has been removed
from the free-feature selection.

## 3. Free feature set — proposed 8 (your approval required before M3)

| # | Feature | Why valuable | Compliance notes |
|---|---|---|---|
| 1 | **Smart message scheduler + reply templates** | schedule via the official `schedule_date` capability (reliable, survives offline); reusable text snippets | ✅ official API surface; no interference |
| 2 | **Local chat backup/export (JSON + HTML)** | data ownership, migration | ✅ self-contained files (ToS §1.3); user-initiated only |
| 3 | **In-app message translation** | high daily utility | ✅ on-demand only; **engine decision needed** (§5) |
| 4 | **Advanced media viewer** (zoom, gestures, batch save) | top-requested UX upgrade | ✅ extends existing PhotoViewer |
| 5 | **Per-chat biometric app-lock** | privacy for shared devices | ✅ platform BiometricPrompt, local-only |
| 6 | **Storage cleaner with analytics** | Android storage pressure is real | ✅ file-size stats only; no message-content reading |
| 7 | **Quick-action gestures + customizable bottom bar** | power-user retention | ✅ pure UI |
| 8 | **Notification Privacy Suite** *(replaces banned ghost mode)* | local-only privacy: hide message previews in the Android shade, per-chat notification override, quiet-hours profiles | ✅ device-local; **does not touch** online/typing/read statuses (ToS §1.4) |

Dropped: **Advanced ghost mode** — ⛔ banned by ToS §1.4 (see §2).
*Alternative considered but not selected:* surfacing Telegram's official last-seen
privacy controls inside a "Privacy Center" screen — can be folded into #8 later if you wish.

## 4. Premium tier — scope guardrails

Planned contents (your spec): extra themes + icon packs, more folders/pinned chats,
cloud-synced app settings (our backend, E2E-encrypted), advanced backup scheduler,
ad removal, priority support. Billing: Google Play Billing, monthly/yearly/lifetime.

⚠️ **Conflict flagged — "more folders / pinned chats":**
folder and pinned-chat counts are **server-enforced** Telegram limits. Exceeding them is
impossible without touching server-side restrictions, which your acceptance criteria
forbid. Compliant implementation options (pick one):
- **(a)** "Local folders / soft pins": extra client-side grouping layers stored in the app's
  local DB — purely visual organization, no API calls beyond server caps. ✅ recommended
- **(b)** Drop the item and compensate with more themes/icon packs.

⚠️ **GPLv2 × Premium gating:** durable Premium value should live in our backend
(sync, scheduled jobs, license validation); client-side extras are accepted as forkable.
Sign-off required (COMPLIANCE.md §1 note).

Premium must never present itself as Telegram's official Premium or unlock official
Premium entitlements (upstream `BillingController` entitlements stay untouched).

## 5. Decisions log (owner-delegated, 2026-11-07 — "full autonomy" granted)

| # | Decision | Resolution |
|---|---|---|
| D1 | App name | ✅ **Rakhsh (رخش)** — Rustam's legendary horse (speed & loyalty); zero Telegram trade-dress overlap. Package target: `app.rakhsh.messenger` (applied at M2). Brand tokens in `UIUX-BASELINE.md`. |
| D2 | Translation engine | ⏳ provisional: on-device open-source engine, no third-party SDK; final check at M3 |
| D3 | Ad provider | ✅ AdMob Native Advanced + UMP approved (owner delegation; reversible before M4; ToS §3.3 Sponsored-Messages rule enforced in ads design) |
| D4 | Premium folders/pins | ✅ local folders / soft-pins (client-side layer, no server-limit touch) |
| D5 | GPLv2 × Premium note | ✅ signed off — durable Premium value server-backed |
| D6 | Ghost-mode replacement | ✅ Notification Privacy Suite approved (ToS §1.4 respected) |

## 6. Standing rules (from your constraints, enforced in every milestone)

- Kotlin for new code; no new third-party SDKs without explicit approval (Billing + UMP only).
- MTProto/E2E/security code byte-identical to upstream; verified by diff at M2/M5.
- Only Telegram's servers and our own backend may be contacted by the app.
- Stop-and-ask triggers: dependencies, signing/auth code, non-Telegram network calls,
  publishing, ads/billing legal decisions.
- No secrets committed; api_id only via `local.properties` (verified gitignored).
