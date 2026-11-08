# UIUX-BASELINE.md — "Rakhsh" Design System v0.1 (feeding Milestone 2)

> Basis: current design knowledge as of 2026-11 (Material 3 Expressive era) + constraints
> from Telegram API ToS (§2.3/2.4: zero Telegram-like trade dress). Re-validated by the
> autopilot cron during M2 preparation.

## 1. Brand — Rakhsh (رخش)
- Meaning: Rustam's legendary horse — speed, loyalty, reliability. Zero paper-plane imagery.
- Wordmark: lowercase `rakhsh` geometric wordmark; icon concept: **speed chevron / hoof-streak** (abstract, NOT a plane, NOT a bird).
- One-liner for store (draft): "Fast, private messaging built on open source."

## 2. Color tokens (all distinct from Telegram blue)

| Token | Light | Dark | AMOLED |
|---|---|---|---|
| background | `#FAF7F2` warm paper | `#171412` | `#000000` |
| surface | `#FFFFFF` | `#211D1A` | `#0A0908` |
| text primary | `#201D1A` | `#F3EEE8` | `#F3EEE8` |
| brand primary | `#FF6D3B` ember coral | `#FF854F` | `#FF854F` |
| brand on-primary | `#FFFFFF` | `#1A0E06` | `#1A0E06` |
| accent | `#FFB300` amber | `#FFC633` | `#FFC633` |
| success / danger | `#34C77B` / `#E5484D` | same | same |

5 curated accent palettes (Premium unlocks 3 extra + icon packs): **Ember** (default, above),
**Jade** `#2FBF8F`, **Rosewood** `#E5607C`, **Sand** `#D8A24A`, **Violet Ash** `#8B7FD4`.
Rule: no default blue/indigo family; accents must keep ≥4.5:1 for text on their surfaces.

## 3. Typography
- Single geometric-humanist family (system `Inter`/`Roboto Flex` stack; optional bundled
  `Vazirmatn` for first-class **RTL/Persian** — decide at M2, ~+250 KB).
- Scale: display 30/700 (screen titles), title 20/600, body 15/400, caption 12.5/500.
- **Tabular numerals** in chat-list timestamps and counters (no jitter on updates).

## 4. Chat bubbles & conversation
- Tail-less grouped bubbles; outer radius 20, 8 on the joining corner; max-width 78%.
- Grouping: consecutive same-sender messages share one background block (6px gaps).
- Media bubbles: full-bleed rounded-16, caption overlaid on gradient scrim.
- Reaction chips float on the bubble corner with spring overshoot (see §6).

## 5. Chat list layouts (the three shipped in the free tier)
1. **Compact** — dense two-line rows, 56dp min height.
2. **Cards** — 3-line elevated cards, avatars 52dp, section headers ("Today/Yesterday").
3. **Folders as tabs** — top tab bar with animated indicator, swipe-between folders,
   pinned row inside each folder.

## 6. Motion (60fps budget on mid-range: 4×A53/A73 class)
- Springs: dampingRatio ≈ 0.85, medium stiffness; durations 120–240 ms.
- Navigation: shared-axis X; predictive-back enabled; stagger ≤ 16 ms per list item.
- Animate **transform/opacity only**; never blur/gradient in scroll hot path.
- Respect `ANIMATOR_DURATION_SCALE=0` (reduce-motion) → durations collapse to 1 ms.
- Haptics: `HapticFeedbackConstants` on send/reaction/unread-clear (light, 20 ms).

## 7. Accessibility (non-negotiable)
- WCAG AA 4.5:1 text contrast in every theme × accent combination (CI-checked list at M2).
- 48dp minimum touch targets; content descriptions; TalkBack order in list rows.
- Font scale to 1.3 without layout breakage (fluid grids, ellipsis rules).

## 8. Implementation strategy for M2 (important scoping decision)
- Do **NOT** rewrite Telegram's UI framework. Implement Rakhsh DS on top of the existing
  upstream theming engine (`Theme`, `ThemeColors`, `ThemeSet`, `AndroidUtilities.lerp`),
  new `res/values` color tokens, and a small `RakhshTheme` Kotlin module.
- Micro-animations via existing `SpringAnimation`/`RecyclerListView` hooks.
- Byte-diff proof for MTProto/E2E paths required after M2 (COMPLIANCE §6).
