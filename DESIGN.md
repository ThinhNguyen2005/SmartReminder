---
version: 1.1.0
name: Cue Calm Intelligence
description: Single source of truth for Cue (AI Reminder & Schedule) Android design tokens, typography, motion, and interaction rules.

colors:
  # Neutral Canvas (~80%)
  background: "#FAFAF9"
  surface: "#FFFFFF"
  surface-subtle: "#F4F4F5"

  text-primary: "#18181B"
  text-secondary: "#71717A"
  text-tertiary: "#A1A1AA"

  border: "#E4E4E7"
  border-strong: "#D4D4D8"

  # Intelligence / Active Accent (~15%)
  accent: "#4F46E5"
  accent-strong: "#4338CA"
  accent-container: "#EEF2FF"

  # Action
  cta: "#18181B"
  on-cta: "#FFFFFF"

  # Semantic States (~5% - strictly for status, never decorative)
  success: "#15803D"
  success-container: "#F0FDF4"

  warning: "#B45309"
  warning-container: "#FFFBEB"

  error: "#B91C1C"
  error-container: "#FEF2F2"

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
Cue is a **Personal AI Scheduler**, not a flashy dashboard or a standard task tracker. Its interface prioritizes clarity, calm focus, and deliberate intelligence.

```text
Warm white canvas (#FAFAF9)
        ↓
Strong black typography & CTA (#18181B)
        ↓
Subtle neutral borders (#E4E4E7)
        ↓
Almost no heavy drop shadows
        ↓
Indigo appears only when Cue is being "smart" or selected (#4F46E5 / #EEF2FF)
```

## 2. Color Balance Rules (The 80 / 15 / 5 Rule)
1. **~80% Neutral**: Canvas (`#FAFAF9`), Surface (`#FFFFFF`), Primary text (`#18181B`), Secondary text (`#71717A`), Borders (`#E4E4E7`).
2. **~15% Indigo**: Intelligence, selected states, and AI recommendations (`#4F46E5`, `#4338CA`, `#EEF2FF`).
3. **~5% Semantic**: Strict status cues only (overdue, conflict, warning, success), never for decorative category tagging.

## 3. Typography & Hierarchy
- **Headline (30–32sp / 600)**: Hero titles on onboarding and major views.
- **Title (20sp / 600)**: Card or section group headlines.
- **Section Title (16sp / 600)**: Natural sentence-case titles (`Up next`, `Today's routines`).
- **Eyebrow (12sp / 600 Uppercase)**: Used very sparingly for small category tags.
- **Body (16sp / 400)**: Primary readable copy with `#18181B`.
- **Body Small (14sp / 400)**: Secondary supporting copy with `#71717A`.
- **Text Tertiary (`#A1A1AA`)**: Reserved strictly for timestamps, inactive lines, and placeholder hints.

## 4. Components & Shapes
- **No Card-Everywhere**: Prefer clean vertical lists with subtle dividers (`#E4E4E7`) over nested card containers.
- **No Pill-Everywhere**: Primary CTA buttons use `16dp` rounded corners (`rounded.xl`), not full pills. Full pill (`9999px`) is reserved for pagination dots and status badges.
- **AI is Visual Language**: `ai-highlight` seamlessly blends into timeline or task items (e.g. `✦ Start assignment · 15:00`) rather than being forced into heavy standalone cards.

## 5. Motion Principles
- **Page Transitions**: Directional horizontal slide + fade (280ms).
- **Tactility**: Native Android Ripple / tonal press feedback.
- **Selection**: Background color interpolation (180ms) + spring indicator bar expansion.
- **AI Entrance**: Subtle fade + 4dp vertical translation (240ms).
