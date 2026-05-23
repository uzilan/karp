# Dark Mode Toggle — Design Spec

**Date:** 2026-05-23  
**Status:** Approved

## Overview

Add a dark/light mode toggle to Karp Wiki. Persists to localStorage. Auto-detects OS preference on first load.

## Approach

CSS variables + `data-theme` attribute on `<html>`. One CSS block defines the full palette for both themes. Inline styles across all components replace hardcoded hex values with `var(--color-xxx)`. No new files, no React context, no prop drilling.

## Color Palette

```css
:root[data-theme="light"] {
  --color-bg:            #fafafa;
  --color-surface:       #ffffff;
  --color-border:        #dddddd;
  --color-border-dashed: #cccccc;
  --color-text:          #333333;
  --color-text-muted:    #555555;
  --color-text-faint:    #888888;
  --color-selected-bg:   #e8f0fe;
  --color-selected-text: #1a73e8;
  --color-drag-bg:       #f0f4ff;
}
:root[data-theme="dark"] {
  --color-bg:            #1e1e1e;
  --color-surface:       #252526;
  --color-border:        #3c3c3c;
  --color-border-dashed: #404040;
  --color-text:          #d4d4d4;
  --color-text-muted:    #aaaaaa;
  --color-text-faint:    #777777;
  --color-selected-bg:   #2d3f66;
  --color-selected-text: #6ea4f0;
  --color-drag-bg:       #1e2a44;
}
```

## State & Persistence

In `App.tsx`:

- `getInitialTheme()` reads localStorage key `karp-dark-mode`; falls back to `window.matchMedia('(prefers-color-scheme: dark)').matches`
- `useState(getInitialTheme)` holds `isDark: boolean`
- `useEffect` syncs `document.documentElement.dataset.theme` and writes to localStorage on every change

## Toggle Button

Placed in existing header, left of "Wipe Data" button. Renders `☀️` in dark mode, `🌙` in light mode.

## Flash Prevention

Inline `<script>` in `index.html` before `<body>` reads localStorage synchronously and sets `data-theme` before React hydrates — prevents white flash on dark-mode page load.

## Files Changed

| File | Change |
|------|--------|
| `index.html` | Add `<style>` with CSS vars, add inline flash-prevention script |
| `src/App.tsx` | Theme state, effect, toggle button |
| `src/components/LeftPanel.tsx` | Replace hex colors with CSS vars |
| `src/components/CenterPanel.tsx` | Replace hex colors with CSS vars |
| `src/components/RightPanel.tsx` | Replace hex colors with CSS vars |
| `src/components/SourceTree.tsx` | Replace hex colors with CSS vars |
| `src/components/viewers/*.tsx` | Replace hex colors with CSS vars |

## What's Out of Scope

- Animated theme transitions
- Per-component theme overrides
- System preference change listener (OS toggle while app is open)
