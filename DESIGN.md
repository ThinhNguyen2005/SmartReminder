---
version: 1.2.0
name: Cue Calm Intelligence
description: Single source of truth for Cue (AI Reminder & Schedule) Android design tokens, typography, motion, and interaction rules.

colors:
  # Light Mode Neutral Canvas (~80%)
  background: "#FAFAF9"
  surface: "#FFFFFF"
  surface-subtle: "#F5F5F4"

  text-primary: "#18181B"
  text-secondary: "#696972"
  text-muted: "#A1A1AA"

  border: "#E4E4E7"
  border-strong: "#D4D4D8"

  # Light Intelligence Accent (~15%)
  accent: "#4F46E5"
  accent-strong: "#4338CA"
  accent-container: "#EEF2FF"

  # Light Action
  cta: "#18181B"
  on-cta: "#FFFFFF"

  # Dark Mode Neutral Canvas (~80%)
  dark-background: "#111113"
  dark-surface: "#18181B"
  dark-surface-subtle: "#242427"

  dark-text-primary: "#ECECEF"
  dark-text-secondary: "#A1A1AA"
  dark-text-muted: "#81818A"

  dark-border: "#2C2C30"
  dark-border-strong: "#52525B"

  # Dark Intelligence Accent (~15%)
  dark-accent: "#818CF8"
  dark-accent-strong: "#A5B4FC"
  dark-accent-container: "#272554"

  # Dark Action
  dark-cta: "#4F46E5"
  dark-on-cta: "#FFFFFF"

  # Semantic States (~5% - strictly for status, never decorative)
  success: "#15803D"
  success-container: "#F0FDF4"
  dark-success: "#22C55E"
  dark-success-container: "#052E16"

  warning: "#B45309"
  warning-container: "#FFFBEB"
  dark-warning: "#F59E0B"
  dark-warning-container: "#451A03"

  error: "#B91C1C"
  error-container: "#FEF2F2"
  dark-error: "#F87171"
  dark-error-container: "#450A0A"

typography:
  display:
    fontFamily: Inter
    fontSize: 40px
    fontWeight: 600
    lineHeight: 1.15
  headline:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: 600
    lineHeight: 1.25
  title:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: 600
    lineHeight: 1.4
  section-title:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: 600
    lineHeight: 1.35
  body:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: 400
    lineHeight: 1.5
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.43
  label:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: 500
    lineHeight: 1.43
  eyebrow:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: 600
    lineHeight: 1.33
    letterSpacing: 0.05em
    textTransform: uppercase

rounded:
  sm: 4px       # minimal tag / indicator
  md: 8px       # chip / small control / time pill
  lg: 12px      # row container / input / selection row
  xl: 16px      # prominent surface / sheet / primary CTA button
  full: 9999px  # avatar / tiny status badge / progress dot

spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  xxl: 32px
  xxxl: 48px    # large structural spacing on onboarding / empty states

motion:
  fast: 120ms
  normal: 200ms
  emphasized: 280ms
  easing-standard: cubic-bezier(0.2, 0.0, 0, 1.0) # FastOutSlowIn

components:
  primary-cta:
    backgroundColor: "{colors.cta}"
    textColor: "{colors.on-cta}"
    rounded: "{rounded.xl}"
    height: 52px
  time-pill:
    backgroundColor: "{colors.surface-subtle}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.md}"
    padding: 12px
  selection-row:
    backgroundColor: "{colors.surface}"
    borderColor: "{colors.border}"
    rounded: "{rounded.lg}"
  selection-row-active:
    backgroundColor: "{colors.accent-container}"
    indicatorColor: "{colors.accent}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.lg}"
  ai-highlight:
    backgroundColor: "{colors.accent-container}"
    textColor: "{colors.accent-strong}"
    sparkleColor: "{colors.accent-strong}"
    rounded: "{rounded.md}"
---

# Cue Design Specification & Identity

## 1. Philosophy: Calm Intelligence
Cue is a **Personal AI Scheduler**, not a noisy dashboard or a generic task list. Its interface prioritizes clarity, calm focus, and deliberate intelligence.

```text
Neutral Canvas (#FAFAF9 / #111113)
        ↓
Strong Typography (#18181B / #ECECEF)
        ↓
Subtle Neutral Borders (#E4E4E7 / #2C2C30)
        ↓
Almost no heavy drop shadows
        ↓
Indigo appears only when Cue is being "smart" or selected (#4F46E5 / #818CF8)
```

## 2. Color Balance Rules (The 80 / 15 / 5 Rule)
1. **~80% Neutral**: 
   - **Light**: Canvas (`#FAFAF9`), Surface (`#FFFFFF`), Subtle Surface (`#F5F5F4`), Text Primary (`#18181B`), Text Secondary (`#696972` - WCAG AA ~5.2:1), Muted (`#A1A1AA`), Borders (`#E4E4E7`), Strong Borders (`#D4D4D8`).
   - **Dark**: Canvas (`#111113`), Surface (`#18181B`), Subtle Surface (`#242427`), Text Primary (`#ECECEF`), Text Secondary (`#A1A1AA`), Muted (`#81818A`), Borders (`#2C2C30`), Strong Borders (`#52525B`).
2. **~15% Indigo**: Intelligence, selected states, and AI recommendations:
   - **Light**: `#4F46E5`, `#4338CA`, `#EEF2FF`.
   - **Dark**: `#818CF8`, `#A5B4FC`, `#272554`.
3. **Primary CTA**:
   - **Light Mode**: Ink Black (`#18181B`) with White text (`#FFFFFF`).
   - **Dark Mode**: Radiant Indigo Accent (`#4F46E5`) with White text (`#FFFFFF`).
4. **~5% Semantic Status**: Strict status cues only (completed, due soon, overdue), never for decorative category tagging.

## 3. Strict Semantic Token Usage Guide
| Token | Purpose & Permitted Usages |
|---|---|
| `textPrimary` | Titles, tasks, events, critical metrics, time values |
| `textSecondary` | Subtitles, descriptions, metadata, timestamps, Next wake labels |
| `textMuted` | Disabled items, text input placeholders, decorative hint lines |
| `accent` | Active navigation, selected items, Cue AI suggestion tags |
| `success` | Completed states (`✓`) |
| `warning` | Approaching deadline / high urgency (`⚠`) |
| `error` | Conflict / overdue state (`!`) |

## 4. Typography & Hierarchy
- **Headline (30–32sp / 600)**: Hero titles on onboarding and major views.
- **Title (20sp / 600)**: Card or section group headlines.
- **Section Title (16sp / 600)**: Natural sentence-case titles (`Up next`, `Today's routines`).
- **Eyebrow (12sp / 600 Uppercase)**: Used very sparingly for small category tags.
- **Body (16sp / 400)**: Primary readable copy.
- **Body Small (14sp / 400)**: Secondary supporting copy (`textSecondary`).

## 5. Components & Shapes
- **No Card-Everywhere**: Prefer clean vertical lists with subtle dividers over nested card containers.
- **No Pill-Everywhere**: Primary CTA buttons use `16dp` rounded corners (`rounded.xl`), not full pills. Full pill (`9999px`) is reserved for pagination dots and status badges.
- **AI is Visual Language**: `ai-highlight` seamlessly blends into timeline or task items (e.g. `✦ Start assignment · 15:00`) rather than being forced into heavy standalone cards.

## 6. Motion Principles
- **Theme Transitions**: Fast 200ms tween (`FastOutSlowInEasing`).
- **Page Transitions**: Directional horizontal slide + fade (280ms).
- **Tactility**: Native Android Ripple / tonal press feedback.
- **Selection**: Background color interpolation (180ms) + spring indicator bar expansion.
- **AI Entrance**: Subtle fade + 4dp vertical translation (240ms).
