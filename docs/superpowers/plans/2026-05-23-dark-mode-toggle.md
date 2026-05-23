# Dark Mode Toggle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persistent dark/light mode toggle to Karp Wiki that auto-detects OS preference on first load.

**Architecture:** CSS custom properties defined once on `[data-theme]` replace all hardcoded hex colors in inline styles. `App.tsx` owns `isDark` state, syncs `document.documentElement.dataset.theme` + localStorage. A flash-prevention inline script sets `data-theme` before React hydrates. `CodeViewer` observes `data-theme` via `MutationObserver` to swap syntax themes without prop drilling.

**Tech Stack:** React 18, TypeScript, Vite, react-syntax-highlighter (oneLight/oneDark), no CSS framework

---

## File Map

| File | Change |
|------|--------|
| `frontend/index.html` | Add `<style>` with CSS vars, inline flash-prevention `<script>` |
| `frontend/src/App.tsx` | Theme state + effect + toggle button; replace header colors |
| `frontend/src/components/LeftPanel.tsx` | Replace hex colors with CSS vars |
| `frontend/src/components/CenterPanel.tsx` | Replace hex colors with CSS vars |
| `frontend/src/components/RightPanel.tsx` | Replace hex colors with CSS vars |
| `frontend/src/components/SourceTree.tsx` | Replace hex colors with CSS vars |
| `frontend/src/components/viewers/CodeViewer.tsx` | `useIsDark` hook; swap `oneLight`↔`oneDark` |
| `frontend/src/components/viewers/OpenApiViewer.tsx` | Replace hex colors with CSS vars |
| `frontend/src/components/viewers/ExcelViewer.tsx` | Replace hex colors with CSS vars |

---

## Task 1: Add CSS variables and flash-prevention to index.html

**Files:**
- Modify: `frontend/index.html`

No test framework exists in this project — verify manually in Task 9.

- [ ] **Step 1: Add style block and flash-prevention script to index.html**

Replace the full file with:

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Karp Wiki</title>
    <style>
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
        --color-accent:        #1a73e8;
        --color-chip-bg:       #f1f3f4;
        --color-row-alt:       #f9f9f9;
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
        --color-accent:        #1a73e8;
        --color-chip-bg:       #2a2a2a;
        --color-row-alt:       #2a2a2e;
      }
      body {
        background: var(--color-bg);
        color: var(--color-text);
        margin: 0;
      }
    </style>
    <script>
      (function() {
        var stored = localStorage.getItem('karp-dark-mode');
        var dark = stored !== null
          ? stored === 'true'
          : window.matchMedia('(prefers-color-scheme: dark)').matches;
        document.documentElement.dataset.theme = dark ? 'dark' : 'light';
      })();
    </script>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/index.html
git commit -m "feat: add CSS vars and flash-prevention for dark mode"
```

---

## Task 2: Add theme state and toggle to App.tsx

**Files:**
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Add theme state, effect, and toggle button**

Replace the content of `App.tsx` with:

```tsx
import { useState, useRef, useEffect } from 'react'
import LeftPanel from './components/LeftPanel'
import CenterPanel from './components/CenterPanel'
import RightPanel from './components/RightPanel'
import { api } from './api/client'
import type { SourceFile } from './types'

export type Selection =
  | { type: 'wiki'; name: string }
  | { type: 'source'; name: string }
  | null

function getInitialTheme(): boolean {
  const stored = localStorage.getItem('karp-dark-mode')
  if (stored !== null) return stored === 'true'
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export default function App() {
  const [selection, setSelection] = useState<Selection>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [sources, setSources] = useState<SourceFile[]>([])
  const [selectedTags, setSelectedTags] = useState<string[]>([])
  const [isDark, setIsDark] = useState(getInitialTheme)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    api.sources.list().then(setSources).catch(() => {})
  }, [refreshKey])

  useEffect(() => {
    document.documentElement.dataset.theme = isDark ? 'dark' : 'light'
    localStorage.setItem('karp-dark-mode', String(isDark))
  }, [isDark])

  const allTags = Array.from(new Set(sources.flatMap(s => s.tags ?? []))).sort()

  const toggleTag = (tag: string) =>
    setSelectedTags(prev => prev.includes(tag) ? prev.filter(t => t !== tag) : [...prev, tag])

  const pollUntilComplete = (fileName: string) => {
    if (pollRef.current) clearInterval(pollRef.current)
    pollRef.current = setInterval(async () => {
      const status = await api.sources.pollStatus(fileName)
      if (status.status === 'COMPLETE' || status.status === 'ERROR') {
        clearInterval(pollRef.current!)
        pollRef.current = null
        setRefreshKey(k => k + 1)
      }
    }, 2000)
  }

  const handleFileDrop = async (file: File) => {
    try {
      const result = await api.sources.upload(file)
      if ('duplicate' in result) {
        const reIngest = window.confirm(`"${result.fileName}" already exists. Re-ingest to update?`)
        if (reIngest) {
          const r = await api.sources.upload(file, true)
          if (!('duplicate' in r)) { setRefreshKey(k => k + 1); pollUntilComplete(r.fileName) }
        }
      } else {
        setRefreshKey(k => k + 1)
        pollUntilComplete(result.fileName)
      }
    } catch (e) {
      alert(`Upload failed: ${e}`)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', fontFamily: 'system-ui, sans-serif' }}>
      <header style={{ padding: '8px 16px', borderBottom: '1px solid var(--color-border)', display: 'flex', alignItems: 'center', gap: 16, background: 'var(--color-surface)' }}>
        <strong style={{ fontSize: 15 }}>Karp Wiki</strong>
        <button
          onClick={() => setIsDark(d => !d)}
          style={{ marginLeft: 'auto', cursor: 'pointer', fontSize: 16, background: 'none', border: 'none', padding: '0 4px' }}
          title={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
        >
          {isDark ? '☀️' : '🌙'}
        </button>
        <button
          onClick={async () => {
            if (window.confirm('Wipe all data? This cannot be undone.')) {
              await api.wipe()
              setSelection(null)
              setRefreshKey(k => k + 1)
            }
          }}
          style={{ color: 'red', cursor: 'pointer' }}
        >
          Wipe Data
        </button>
      </header>
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        <LeftPanel
          refreshKey={refreshKey}
          sources={sources}
          selectedTags={selectedTags}
          selection={selection}
          onSelect={setSelection}
          onFileDrop={handleFileDrop}
        />
        <CenterPanel selection={selection} refreshKey={refreshKey} />
        <RightPanel allTags={allTags} selectedTags={selectedTags} onTagToggle={toggleTag} onRefresh={() => setRefreshKey(k => k + 1)} />
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/App.tsx
git commit -m "feat: add dark mode state and toggle button"
```

---

## Task 3: Update LeftPanel.tsx

**Files:**
- Modify: `frontend/src/components/LeftPanel.tsx`

- [ ] **Step 1: Replace hardcoded hex colors with CSS vars**

Replace `panelStyle`, `sectionStyle`, `itemStyle`, `clusterHeaderStyle`, and drag zone styles:

```tsx
  const panelStyle: React.CSSProperties = {
    width: 220, borderRight: '1px solid var(--color-border)', overflow: 'hidden',
    display: 'flex', flexDirection: 'column', fontSize: 13, background: 'var(--color-bg)',
    height: '100%'
  }
  const sectionStyle: React.CSSProperties = {
    padding: '10px 12px 4px', fontWeight: 700, color: 'var(--color-text-muted)',
    fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.05em',
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    cursor: 'pointer', userSelect: 'none'
  }
  const itemStyle = (active: boolean, indent = 0): React.CSSProperties => ({
    padding: `4px 16px 4px ${16 + indent * 14}px`, cursor: 'pointer',
    background: active ? 'var(--color-selected-bg)' : 'transparent',
    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
    color: active ? 'var(--color-selected-text)' : 'var(--color-text)'
  })
  const clusterHeaderStyle: React.CSSProperties = {
    padding: '4px 8px', cursor: 'pointer', userSelect: 'none',
    display: 'flex', alignItems: 'center', gap: 4, fontSize: 12,
    color: 'var(--color-text-muted)', fontWeight: 600,
  }
```

Also replace the drag zone inline style (the `onDrop` div at the bottom):

```tsx
        style={{
          marginTop: 'auto', padding: 16, textAlign: 'center', fontSize: 12,
          color: 'var(--color-text-faint)',
          borderTop: '1px dashed var(--color-border-dashed)',
          background: dragging ? 'var(--color-drag-bg)' : 'transparent',
          cursor: 'pointer', transition: 'background 0.15s'
        }}
```

And the cluster collapse arrow span:

```tsx
                  <span style={{ fontSize: 9, color: 'var(--color-text-faint)', minWidth: 10 }}>
```

And the add-folder `＋` button in the Sources header:

```tsx
              style={{ cursor: 'pointer', fontSize: 14, color: 'var(--color-text-faint)', lineHeight: 1, paddingRight: 2 }}
```

And the "No pages yet" placeholder:

```tsx
              <div style={{ padding: '4px 16px', color: 'var(--color-text-faint)', fontSize: 12 }}>No pages yet</div>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/LeftPanel.tsx
git commit -m "feat: apply dark mode CSS vars to LeftPanel"
```

---

## Task 4: Update CenterPanel.tsx

**Files:**
- Modify: `frontend/src/components/CenterPanel.tsx`

- [ ] **Step 1: Replace hardcoded hex colors**

Replace the "Select a page" and "Loading…" paragraphs:

```tsx
      <p style={{ color: 'var(--color-text-faint)', fontSize: 14 }}>Select a page or file to view.</p>
```

```tsx
      <p style={{ color: 'var(--color-text-faint)' }}>Loading…</p>
```

Replace the title bar div:

```tsx
      <div style={{ padding: '8px 24px', borderBottom: '1px solid var(--color-border)', fontSize: 12, color: 'var(--color-text-faint)', display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
```

Replace the tag span:

```tsx
          <span key={tag} style={{ background: 'var(--color-selected-bg)', color: 'var(--color-selected-text)', borderRadius: 10, padding: '1px 8px' }}>#{tag}</span>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/CenterPanel.tsx
git commit -m "feat: apply dark mode CSS vars to CenterPanel"
```

---

## Task 5: Update RightPanel.tsx

**Files:**
- Modify: `frontend/src/components/RightPanel.tsx`

- [ ] **Step 1: Replace hardcoded hex colors**

Replace `panelStyle`:

```tsx
  const panelStyle: React.CSSProperties = {
    width: 320, borderLeft: '1px solid var(--color-border)', display: 'flex',
    flexDirection: 'column', fontSize: 13, background: 'var(--color-surface)'
  }
```

Replace the tags bar div:

```tsx
        <div style={{ padding: '8px 10px', borderBottom: '1px solid var(--color-border)', display: 'flex', flexWrap: 'wrap', gap: 5 }}>
```

Replace the tag span style:

```tsx
              style={{
                cursor: 'pointer', fontSize: 11, padding: '2px 8px', borderRadius: 12,
                background: active ? 'var(--color-accent)' : 'var(--color-chip-bg)',
                color: active ? '#fff' : 'var(--color-text-muted)',
                border: `1px solid ${active ? 'var(--color-accent)' : 'var(--color-border)'}`,
                userSelect: 'none',
              }}
```

Replace the "Chat" header div:

```tsx
      <div style={{ padding: '10px 12px', borderBottom: '1px solid var(--color-border)', fontWeight: 700, fontSize: 13 }}>
```

Replace the empty-state paragraph:

```tsx
          <p style={{ color: 'var(--color-text-faint)', fontSize: 12, textAlign: 'center', marginTop: 24 }}>
```

Replace the message bubble div:

```tsx
            <div style={{
              maxWidth: '92%',
              background: m.role === 'user' ? 'var(--color-accent)' : 'var(--color-chip-bg)',
              color: m.role === 'user' ? '#fff' : 'var(--color-text)',
              borderRadius: 8, padding: '7px 11px', lineHeight: 1.5
            }}>
```

Replace the file-back input:

```tsx
                  style={{ flex: 1, fontSize: 11, padding: '2px 6px', border: '1px solid var(--color-border)', borderRadius: 3 }}
```

Replace "Thinking…" div:

```tsx
        {loading && <div style={{ color: 'var(--color-text-faint)', fontSize: 12 }}>Thinking…</div>}
```

Replace the bottom input bar div:

```tsx
      <div style={{ padding: 10, borderTop: '1px solid var(--color-border)', display: 'flex', gap: 8 }}>
```

Replace the chat input:

```tsx
          style={{ flex: 1, padding: '7px 10px', borderRadius: 6, border: '1px solid var(--color-border)', fontSize: 13, background: 'var(--color-surface)', color: 'var(--color-text)' }}
```

Replace the Send button:

```tsx
          style={{ padding: '7px 14px', borderRadius: 6, background: 'var(--color-accent)', color: '#fff', border: 'none', cursor: 'pointer', fontSize: 13 }}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/RightPanel.tsx
git commit -m "feat: apply dark mode CSS vars to RightPanel"
```

---

## Task 6: Update SourceTree.tsx

**Files:**
- Modify: `frontend/src/components/SourceTree.tsx`

- [ ] **Step 1: Replace hardcoded hex colors in renderNode**

Source node style (inside `renderNode`, `node.type === 'source'` branch):

```tsx
          style={{
            paddingLeft: indent + 16, paddingRight: 8, paddingTop: 3, paddingBottom: 3,
            cursor: 'pointer', userSelect: 'none',
            background: active ? 'var(--color-selected-bg)' : 'transparent',
            color: active ? 'var(--color-selected-text)' : 'var(--color-text)',
            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', fontSize: 13,
          }}
```

Folder node row style:

```tsx
          style={{
            paddingLeft: indent, paddingRight: 8, paddingTop: 3, paddingBottom: 3,
            cursor: 'pointer', userSelect: 'none',
            background: selected ? 'var(--color-selected-bg)' : dragOver ? 'var(--color-drag-bg)' : 'transparent',
            color: selected ? 'var(--color-selected-text)' : 'var(--color-text)',
            display: 'flex', alignItems: 'center', gap: 4, fontSize: 13,
          }}
```

Collapse arrow span:

```tsx
            style={{ fontSize: 9, color: 'var(--color-text-faint)', minWidth: 10, lineHeight: 1 }}
```

Rename input:

```tsx
              style={{ fontSize: 12, border: '1px solid var(--color-border)', borderRadius: 3, padding: '1px 4px', width: 90, outline: 'none', background: 'var(--color-surface)', color: 'var(--color-text)' }}
```

Add-subfolder `＋` span:

```tsx
              style={{ fontSize: 11, color: 'var(--color-text-faint)', padding: '0 2px', borderRadius: 3 }}
```

Root drop zone div (the outer `return` div):

```tsx
      style={{ minHeight: 8, background: dropTarget === '__root__' ? 'var(--color-drag-bg)' : 'transparent' }}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/SourceTree.tsx
git commit -m "feat: apply dark mode CSS vars to SourceTree"
```

---

## Task 7: Update CodeViewer.tsx

**Files:**
- Modify: `frontend/src/components/viewers/CodeViewer.tsx`

CodeViewer uses `react-syntax-highlighter` with an inline-style theme object, so CSS vars don't reach it. Instead, observe `data-theme` via `MutationObserver` and swap the theme object.

- [ ] **Step 1: Add useIsDark hook and swap syntax theme**

Replace the full file:

```tsx
import { useState, useEffect } from 'react'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneLight, oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism'

const EXT_TO_LANG: Record<string, string> = {
  kt: 'kotlin', java: 'java', py: 'python', ts: 'typescript',
  tsx: 'tsx', js: 'javascript', jsx: 'jsx', go: 'go',
  rs: 'rust', cpp: 'cpp', c: 'c', cs: 'csharp', rb: 'ruby',
  sh: 'bash', sql: 'sql'
}

function useIsDark(): boolean {
  const [isDark, setIsDark] = useState(
    () => document.documentElement.dataset.theme === 'dark'
  )
  useEffect(() => {
    const observer = new MutationObserver(() => {
      setIsDark(document.documentElement.dataset.theme === 'dark')
    })
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    return () => observer.disconnect()
  }, [])
  return isDark
}

interface Props { content: string; fileName: string }

export default function CodeViewer({ content, fileName }: Props) {
  const isDark = useIsDark()
  const ext = fileName.split('.').pop()?.toLowerCase() ?? ''
  const lang = EXT_TO_LANG[ext] ?? 'text'
  // Strip "Language: xxx\n\n" prefix added by CodeReader
  const code = content.replace(/^Language: \w+\n\n/, '')

  return (
    <SyntaxHighlighter
      language={lang}
      style={isDark ? oneDark : oneLight}
      customStyle={{ fontSize: 13, borderRadius: 4 }}
      showLineNumbers>
      {code}
    </SyntaxHighlighter>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/components/viewers/CodeViewer.tsx
git commit -m "feat: swap syntax highlighter theme on dark mode toggle"
```

---

## Task 8: Update OpenApiViewer.tsx and ExcelViewer.tsx

**Files:**
- Modify: `frontend/src/components/viewers/OpenApiViewer.tsx`
- Modify: `frontend/src/components/viewers/ExcelViewer.tsx`

- [ ] **Step 1: Update OpenApiViewer.tsx**

Replace the endpoint card div style:

```tsx
          <div key={i} style={{
            display: 'flex', alignItems: 'center', gap: 12,
            padding: '8px 12px', border: '1px solid var(--color-border)', borderRadius: 4,
            borderLeft: `4px solid ${METHOD_COLORS[ep.method] ?? 'var(--color-border)'}`
          }}>
```

Replace the summary span:

```tsx
            {ep.summary && <span style={{ color: 'var(--color-text-muted)', fontSize: 12 }}>{ep.summary}</span>}
```

- [ ] **Step 2: Update ExcelViewer.tsx**

Replace the table row style:

```tsx
                    <tr key={ri} style={{ background: ri % 2 === 0 ? 'var(--color-row-alt)' : 'var(--color-surface)' }}>
```

Replace the table cell style:

```tsx
                        <td key={ci} style={{
                          border: '1px solid var(--color-border)', padding: '4px 10px',
                          fontWeight: ri === 0 ? 700 : 400
                        }}>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/viewers/OpenApiViewer.tsx frontend/src/components/viewers/ExcelViewer.tsx
git commit -m "feat: apply dark mode CSS vars to viewer components"
```

---

## Task 9: Manual browser verification

**Files:** None — verification only.

- [ ] **Step 1: Start the dev server**

```bash
cd frontend && npm run dev
```

Open `http://localhost:5173` in browser.

- [ ] **Step 2: Verify light mode (default)**

- Header shows 🌙 button
- Background is off-white (`#fafafa`), header is white, right panel is white
- Left panel border, section labels, item colors all visible
- Select a wiki page — tag chips show blue-on-light-blue
- Select a source file with code — `oneLight` syntax theme active
- Upload drop zone border is dashed and subtle

- [ ] **Step 3: Click the 🌙 toggle**

- All panels switch to dark backgrounds
- Header shows ☀️ button
- Text is light-colored and legible
- Selected items show `#2d3f66` background with `#6ea4f0` text
- Code viewer switches to `oneDark` theme

- [ ] **Step 4: Verify persistence**

- Reload the page — dark mode persists (no white flash before React loads)
- Open browser DevTools → Application → Local Storage → verify `karp-dark-mode = "true"`

- [ ] **Step 5: Verify OS preference detection**

- Clear `karp-dark-mode` from localStorage
- Reload — theme should match OS preference (`prefers-color-scheme`)

- [ ] **Step 6: Commit if any fixes were needed, then stop dev server**
