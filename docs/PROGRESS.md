# PROGRESS.md — Rakhsh autopilot log (append-only)

> Each run appends a dated entry. Never include secrets. Persian summaries allowed.

## 2026-11-07 — session 1 (main agent, owner-delegated decisions)

**Environment probe (evidence):** sandbox has no Android SDK/NDK, 2 cores, 4 GB RAM,
7.4 GB free disk → local APK build impossible; build evidence delegated to GitHub CI.

**Compliance (M0 done):** live fetch of Telegram API ToS + api_id guidelines (2026-11-07);
raw copies in `docs/research/`. Finding: **ToS §1.4 bans ghost mode** → dropped; replaced
by Notification Privacy Suite (local-only). ToS §3.3: official Sponsored Messages must
never be interfered with (hard rule for our ads module).

**Owner-delegated decisions (D1–D6) — recorded in ROADMAP.md:**
- D1 name: **Rakhsh (رخش)** — package target `app.rakhsh.messenger` (rename executed at M2)
- D2 translation: provisional on-device, open-source engine, no third-party SDK (re-check M3)
- D3 ads: AdMob Native Advanced + UMP approved (the two allowed SDKs; reversible before M4)
- D4 premium folders: local folders / soft-pins (client-side, no server-limit touch)
- D5 GPLv2×Premium note: signed off (durable value server-backed)
- D6 Notification Privacy Suite approved as ghost-mode replacement

**Artifacts:** COMPLIANCE v0.1, BUILD guide, ROADMAP, UIUX-BASELINE v0.1, CI workflow,
validated `BuildVars.java.diff` (git apply --check ✔), gradle snippet, backend design.

**Infrastructure:** GitHub fork of DrKLO/Telegram created + Actions enabled + branch
`fork-kit` pushed (api_id patch + gradle injection + docs + CI with stub keys).
Build status monitored every 20 min by autopilot cron.

**Next queue:** monitor CI run → fix if red → REBRAND-MAP.md (exact rebrand file map)
→ privacy policy draft → M2 checklist expansion.
