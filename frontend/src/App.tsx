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
          style={{ color: 'red', cursor: 'pointer', background: 'none', border: 'none' }}
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
