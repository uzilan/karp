import { useState } from 'react'
import LeftPanel from './components/LeftPanel'
import CenterPanel from './components/CenterPanel'
import RightPanel from './components/RightPanel'
import IngestModal from './components/IngestModal'
import { api } from './api/client'
import type { IngestPreview } from './types'

export type Selection =
  | { type: 'wiki'; name: string }
  | { type: 'source'; name: string }
  | null

export default function App() {
  const [selection, setSelection] = useState<Selection>(null)
  const [ingestPreview, setIngestPreview] = useState<IngestPreview | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const handleFileDrop = async (file: File) => {
    try {
      const result = await api.sources.upload(file)
      if ('duplicate' in result) {
        const reIngest = window.confirm(`"${result.fileName}" already exists. Re-ingest to update?`)
        if (reIngest) {
          const preview = await api.sources.upload(file, true)
          if (!('duplicate' in preview)) setIngestPreview(preview)
        }
      } else {
        setIngestPreview(result)
      }
    } catch (e) {
      alert(`Upload failed: ${e}`)
    }
  }

  const handleIngestConfirm = async (fileName: string, tags: string[], category: string) => {
    await api.sources.confirm(fileName, tags, category)
    setIngestPreview(null)
    setRefreshKey(k => k + 1)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', fontFamily: 'system-ui, sans-serif' }}>
      <header style={{ padding: '8px 16px', borderBottom: '1px solid #ddd', display: 'flex', alignItems: 'center', gap: 16, background: '#fff' }}>
        <strong style={{ fontSize: 15 }}>Karp Wiki</strong>
      </header>
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        <LeftPanel
          refreshKey={refreshKey}
          selection={selection}
          onSelect={setSelection}
          onFileDrop={handleFileDrop}
        />
        <CenterPanel selection={selection} />
        <RightPanel />
      </div>
      {ingestPreview && (
        <IngestModal
          preview={ingestPreview}
          onConfirm={handleIngestConfirm}
          onCancel={() => setIngestPreview(null)}
        />
      )}
    </div>
  )
}
