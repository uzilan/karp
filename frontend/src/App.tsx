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

export default function App() {
  const [selection, setSelection] = useState<Selection>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [sources, setSources] = useState<SourceFile[]>([])
  const [selectedTags, setSelectedTags] = useState<string[]>([])
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    api.sources.list().then(setSources).catch(() => {})
  }, [refreshKey])

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
      <header style={{ padding: '8px 16px', borderBottom: '1px solid #ddd', display: 'flex', alignItems: 'center', gap: 16, background: '#fff' }}>
        <strong style={{ fontSize: 15 }}>Karp Wiki</strong>
        <button
          onClick={async () => {
            if (window.confirm('Wipe all data? This cannot be undone.')) {
              await api.wipe()
              setSelection(null)
              setRefreshKey(k => k + 1)
            }
          }}
          style={{ color: 'red', marginLeft: 'auto', cursor: 'pointer' }}
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
