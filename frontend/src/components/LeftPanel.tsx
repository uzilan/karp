import { useState, useEffect } from 'react'
import { api } from '../api/client'
import type { Selection } from '../App'
import type { SourceFile } from '../types'
import SourceTree from './SourceTree'

interface Props {
  refreshKey: number
  sources: SourceFile[]
  selectedTags: string[]
  selection: Selection
  onSelect: (s: Selection) => void
  onFileDrop: (file: File) => void
}

export default function LeftPanel({ refreshKey, sources, selectedTags, selection, onSelect, onFileDrop }: Props) {
  const [wikiPages, setWikiPages] = useState<string[]>([])
  const [dragging, setDragging] = useState(false)
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null)
  const [addFolderKey, setAddFolderKey] = useState(0)
  const [wikiOpen, setWikiOpen] = useState(true)
  const [sourcesOpen, setSourcesOpen] = useState(true)

  useEffect(() => {
    api.wiki.list().then(setWikiPages).catch(() => {})
  }, [refreshKey])

  const filteredSources = selectedTags.length === 0
    ? sources
    : sources.filter(s => selectedTags.some(t => (s.tags ?? []).includes(t)))

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) onFileDrop(file)
  }

  const panelStyle: React.CSSProperties = {
    width: 220, borderRight: '1px solid #ddd', overflow: 'hidden',
    display: 'flex', flexDirection: 'column', fontSize: 13, background: '#fafafa',
    height: '100%'
  }
  const sectionStyle: React.CSSProperties = {
    padding: '10px 12px 4px', fontWeight: 700, color: '#555',
    fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.05em',
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    cursor: 'pointer', userSelect: 'none'
  }
  const itemStyle = (active: boolean): React.CSSProperties => ({
    padding: '4px 16px', cursor: 'pointer',
    background: active ? '#e8f0fe' : 'transparent',
    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
    color: active ? '#1a73e8' : '#333'
  })

  return (
    <div style={panelStyle}>
      <div style={{ display: 'flex', flexDirection: 'column', ...(wikiOpen ? { flex: 1, minHeight: 0 } : { flexShrink: 0 }) }}>
        <div onClick={() => setWikiOpen(o => !o)} style={sectionStyle}>
          <span>Wiki Pages</span>
          <span style={{ fontSize: 10 }}>{wikiOpen ? '▾' : '▸'}</span>
        </div>
        {wikiOpen && (
          <div style={{ overflow: 'auto', flex: 1, minHeight: 0 }}>
            {wikiPages.length === 0 && (
              <div style={{ padding: '4px 16px', color: '#999', fontSize: 12 }}>No pages yet</div>
            )}
            {wikiPages.map(name => (
              <div key={name}
                style={itemStyle(selection?.type === 'wiki' && selection.name === name)}
                onClick={() => onSelect({ type: 'wiki', name })}>
                📄 {name}
              </div>
            ))}
          </div>
        )}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', ...(sourcesOpen ? { flex: 1, minHeight: 0 } : { flexShrink: 0 }) }}>
        <div onClick={() => setSourcesOpen(o => !o)} style={sectionStyle}>
          <span>Sources</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span
              title="Add folder"
              onClick={e => { e.stopPropagation(); setAddFolderKey(k => k + 1) }}
              style={{ cursor: 'pointer', fontSize: 14, color: '#888', lineHeight: 1, paddingRight: 2 }}
            >＋</span>
            <span style={{ fontSize: 10 }}>{sourcesOpen ? '▾' : '▸'}</span>
          </span>
        </div>
        {sourcesOpen && (
          <div style={{ overflow: 'auto', flex: 1, minHeight: 0 }}>
            <SourceTree
              serverSources={filteredSources.map(s => s.name)}
              refreshKey={refreshKey}
              selection={selection}
              onSelect={onSelect}
              selectedFolderId={selectedFolderId}
              onSelectFolder={setSelectedFolderId}
              addRootFolderKey={addFolderKey}
            />
          </div>
        )}
      </div>

      <div
        onDragOver={e => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        style={{
          marginTop: 'auto', padding: 16, textAlign: 'center', fontSize: 12, color: '#888',
          borderTop: '1px dashed #ccc',
          background: dragging ? '#f0f4ff' : 'transparent',
          cursor: 'pointer', transition: 'background 0.15s'
        }}
        onClick={() => {
          const input = document.createElement('input')
          input.type = 'file'
          input.onchange = (e) => {
            const file = (e.target as HTMLInputElement).files?.[0]
            if (file) onFileDrop(file)
          }
          input.click()
        }}>
        {dragging ? '📂 Drop to upload' : '+ Drop files here'}
      </div>
    </div>
  )
}
