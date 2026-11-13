# COMPLIANCE.md — "Amber" (unofficial client based on the Telegram open-source code)

> **Status: v0.1 — compliance research baseline (pre-Milestone-1).**
> The final, fully-checked version is due at Milestone 5. Items marked ☐ are verified only
> when the corresponding milestone is executed and evidence (build logs / diffs) exists.
>
> Sources below were fetched and read on **2026-11-07**. Every clause quote in §2 is
> verbatim from the official pages (raw copies archived in `docs/research/`).

---

## 0. Sources consulted (official, public)

| # | Document | URL | Accessed |
|---|----------|-----|----------|
| S1 | Telegram API Terms of Service | https://core.telegram.org/api/terms (machine-readable: https://core.telegram.org/api/terms.md) | 2026-11-07 |
| S2 | Creating your Telegram Application / Obtaining api_id | https://core.telegram.org/api/obtaining_api_id (machine-readable: https://core.telegram.org/api/obtaining_api_id.md) | 2026-11-07 |
| S3 | MTProto Security Guidelines | https://core.telegram.org/mtproto/security_guidelines (linked from upstream README) | 2026-11-07 (linked; full review due M5) |
| S4 | GNU General Public License v2 | https://www.gnu.org/licenses/old-licenses/gpl-2.0.html + `LICENSE` file in repo root | 2026-11-07 |
| S5 | Upstream repo README (build/branding requirements) | https://github.com/DrKLO/Telegram | 2026-11-07 |
| S6 | Google Play Policies (Ads, Impersonation & IP, Data safety) | https://policy.play.google.com | reviewed at M4/M5 |
| S7 | AdMob + UMP consent (GDPR) docs | https://developers.google.com/admob/android/privacy | reviewed at M4 |
| S8 | Telegram Reproducible Builds note | https://core.telegram.org/reproducible-builds | 2026-11-07 (linked) |

---

## 1. Upstream license (GPLv2) — obligations of our fork

| Obligation | How we comply | Status |
|---|---|---|
| Keep the GPLv2 license text and upstream copyright headers intact | Rule: never edit/remove upstream license headers; keep root `LICENSE` | ☐ rule adopted (M2+) |
| State the changes we made | `CHANGELOG.md` + `NOTICE.md` maintained from M2 onward | ☐ |
| License our **client** derivative under GPLv2 | Whole fork published under GPLv2 | ☐ (published at M5) |
| Provide corresponding source to recipients | Public GitHub repo + in-app link in Settings ▸ About | ☐ |
| No additional restrictions on recipients' GPLv2 rights | Premium does **not** restrict code rights — see the note below | ☐ |
| Include attribution that the app is based on Telegram open source | Settings ▸ About: "Unofficial app based on Telegram open source" | ☐ (M2) |

> **Important design note (GPLv2 × Premium gating):**
> Any Premium feature implemented purely client-side exists in the published source, so a
> modified build could enable it. That is *legally acceptable* (we may not restrict code
> rights), but the business design must account for it:
> the **durable** Premium value must live in our own backend services (E2E-encrypted
> settings sync, backup scheduler jobs, license validation), while client-side Premium
> features are accepted as forkable conveniences. **Flagged to the client for sign-off.**

---

## 2. Telegram API Terms of Service — clause-by-clause impact (verbatim quotes, S1)

### 2.1 Privacy & Security (ToS §1)
- **§1.1** "All client apps must, therefore, guard their users' privacy with utmost care and comply with our Security Guidelines."
  → *Impact:* MTProto/E2E code stays byte-identical to upstream; no telemetry of message content or contacts; security self-review at M5.
- **§1.2** "Developers are welcome to add new features or improve and extend existing Telegram features provided that these modifications do not violate these Terms of Service."
  → *Impact:* our free/Premium feature set is permitted **insofar as** no clause below is violated.
- **§1.3** "…all the basic features of the main Telegram apps function correctly… It is forbidden to force users of other Telegram clients to download your app in order to view certain messages and content sent using your app."
  → *Impact:* chat export (JSON/HTML) must produce self-contained, universally readable files.
- **§1.4** "It is forbidden to interfere with the basic functionality of Telegram. This includes but is not limited to: … **preventing last seen and online statuses from being displayed correctly, tampering with the 'read' statuses of messages (e.g. implementing a 'ghost mode'), preventing typing statuses from being sent/displayed**, etc."
  → **Impact: ⛔ the requested "Advanced ghost mode" feature is explicitly prohibited.**
  It will **NOT** be implemented (per the client's own constraint). Compliant alternatives proposed in `ROADMAP.md` §3.
- **§1.5** "…you are prohibited from using, accessing or aggregating data obtained from the Telegram platform to train, fine-tune or otherwise engage in the development, enhancement or deployment of artificial intelligence, machine learning models and similar technologies."
  → *Impact:* in-app translation must be strictly **on-demand, per-message, user-initiated**; no bulk text aggregation, no AI training on any platform data. Engine choice recorded in `ROADMAP.md` §5 (decision needed).

### 2.2 Transparency (ToS §2)
- **§2.1** "You must obtain your own api_id for your application."
  → *Impact:* own api_id/api_hash read from `local.properties` (gitignored upstream — verified `.gitignore` line 6), never committed. Upstream sample `APP_ID = 4` is server-limited (`API_ID_PUBLISHED_FLOOD` for end users, S2) — own api_id mandatory before any release.
- **§2.2** "…your users must be aware of the fact that your app uses the Telegram API and is part of the Telegram ecosystem. This fact must be featured prominently in the app's description in the app stores and in the in-app intro if your app has it."
  → *Impact:* Play listing + in-app intro must contain a prominent statement.
- **§2.3** "…the title of your app must not include the word 'Telegram'…"
  → *Impact:* our stricter rule — no "Telegram" anywhere in branding (matches client requirement).
- **§2.4** "You must not use the official Telegram logo…"
  → *Impact:* original logo; no paper-plane mark; no confusable trade dress.

### 2.3 Advertising & Monetization (ToS §3)
- **§3.1** "Developers are allowed to monetize their coding efforts through advertising or other legitimate means."
  → *Impact:* ads + our own Premium subscription are permitted.
- **§3.2** "…you must clearly mention all the methods of monetization that are used in your app in all its app store descriptions."
  → *Impact:* Play listing must disclose **both** ads and in-app subscription.
- **§3.3** "If your app allows accessing content from Telegram channels, you must include support for official sponsored messages in Telegram channels and may not interfere with this functionality."
  → **Impact: our own native ad slot must never filter, hide, replace or delay official Sponsored Messages.** Hard rule for the ads module (M4).

### 2.4 Breach of terms (ToS §4)
- 10-day cure window after a breach notice, then API access revocation + store removal requests.
  → *Impact:* keep the developer account phone reachable (api_id notifications go there, S2).

---

## 3. api_id usage rules (S2, verbatim highlights)

- "If you use the Telegram API for flooding, spamming, faking subscriber and view counters of channels, you will be banned forever."
- "…all accounts that log in using unofficial Telegram API clients are automatically put under observation…"
- "Each number can only have one api_id connected to it."
- "Please remember to publish your code as well in order to comply with the GNU GPL licenses."

→ *Impact:* api_id belongs to a responsible, reachable account; zero automation that could look like spam; repo public from M1.

---

## 4. Branding & trademarks checklist

| Rule | Source | Status |
|---|---|---|
| No "Telegram" in app title | S1 §2.3 | ☐ M2 |
| No official logo or confusably similar mark/trade dress | S1 §2.4 + client requirement | ☐ M2 |
| New package/applicationId (not `org.telegram.*`) | client requirement | ☐ M2 (`APP_PACKAGE` in `TMessagesProj_App/build.gradle`) |
| "Unofficial app based on Telegram open source" in Settings ▸ About | S1 §2.2 + client requirement | ☐ M2 |
| Prominent statement in store listing + in-app intro | S1 §2.2 | ☐ M5 (listing draft) |

---

## 5. Ads, consent & privacy (client-imposed, stricter than required)

1. **One** native ad slot at the bottom of the chat list only. Never inside conversations.
2. Consent (UMP/GDPR) **before** the first ad request; no ads fire before consent.
3. No personalized targeting derived from message data (we do not read message content for ads — and per S1 §1.5 we must not aggregate platform data anyway).
4. Official channel Sponsored Messages (ToS §3.3) must keep working untouched.
5. A persistent "Remove ads" entry point in Settings.
6. Our own privacy policy + ToS screens in-app (client requirement; draft at M5).

---

## 6. Security commitments

- MTProto / libtgnet / E2E (secret chats) code: **unmodified** (byte-diff check against upstream at M2 and M5). ☐
- No SDK that can read message content or contacts. New SDKs require explicit client approval (only allowed: Google Play Billing + UMP). ☐
- api_id/api_hash only via `local.properties`; CI uses stubs. ☐ (design adopted; verified in CI recipe)
- Security self-review of our own new code (permissions, storage, logging) at M5. ☐

---

## 7. Feature-compliance verdicts (initial, against S1)

| Requested feature | Verdict | Basis |
|---|---|---|
| Advanced ghost mode | ⛔ **NOT implemented** | S1 §1.4 explicitly bans it |
| Message scheduler | ✅ compliant | uses official `schedule_date` API capability; no interference |
| Smart reply templates | ✅ compliant | local text snippets; no AI training on platform data (S1 §1.5) |
| Local backup/export (JSON/HTML) | ✅ compliant | S1 §1.3 satisfied (self-contained files); user-initiated only |
| Per-chat notification profiles | ✅ compliant | device-local settings only |
| In-app translation | ✅ compliant **with conditions** | on-demand only; engine decision open (S1 §1.5) |
| Advanced media viewer | ✅ compliant | UI extension of existing viewer |
| Per-chat biometric app-lock | ✅ compliant | device-local (BiometricPrompt) |
| Storage cleaner + analytics | ✅ compliant | file-size statistics only, no message-content reading |
| Quick gestures + custom bottom bar | ✅ compliant | pure UI |
| "More folders / pinned chats" in Premium | ⚠️ **server-limit conflict** — see ROADMAP §4; must be a client-side local layer or dropped | client acceptance criterion: "No Premium feature touches server-side restrictions" |

---

## 8. Open items to complete before the final compliance sign-off (M5)

- [ ] M1: successful `assembleDebug` + real-account login with our own api_id (evidence: build log, screenshot)
- [ ] M2: branding sweep — zero "Telegram"/logo occurrences in UI, manifest, store assets (evidence: grep log + QA checklist)
- [ ] M2: byte-diff proof that MTProto/E2E/mtproto-related native code is untouched
- [ ] M3: each shipped free feature re-checked against §7 verdicts
- [ ] M4: UMP consent flow tested (EEA behavior), ad request gating test, Sponsored Messages regression test
- [ ] M4: Play Billing products configured (monthly/yearly/lifetime) without touching upstream entitlements
- [ ] M5: privacy policy + ToS texts published and linked in-app
- [ ] M5: Play listing draft disclosing: "uses Telegram API", "contains ads", "in-app purchases"
- [ ] M5: full source published (public repo) + in-app link
- [ ] M5: security self-review of all fork-added code
