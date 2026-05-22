import { useState, useEffect } from 'react'
import { api } from '../api/client'
import type { Selection } from '../App'
import type { SourceFile } from '../types'

interface Props {
  refreshKey: number
  selection: Selection
  onSelect: (s: Selection) => void
  onFileDrop: (file: File) => void
}

export default function LeftPanel({ refreshKey, selection, onSelect, onFileDrop }: Props) {
  const [wikiPages, setWikiPages] = useState<string[]>([])
  const [sources, setSources] = useState<SourceFile[]>([])
  const [dragging, setDragging] = useState(false)

  useEffect(() => {
    api.wiki.list().then(setWikiPages).catch(() => {})
    api.sources.list().then(setSources).catch(() => {})
  }, [refreshKey])

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) onFileDrop(file)
  }

  const panelStyle: React.CSSProperties = {
    width: 220, borderRight: '1px solid #ddd', overflow: 'auto',
    display: 'flex', flexDirection: 'column', fontSize: 13, background: '#fafafa'
  }
  const sectionStyle: React.CSSProperties = {
    padding: '10px 12px 4px', fontWeight: 700, color: '#555',
    fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.05em'
  }
  const itemStyle = (active: boolean): React.CSSProperties => ({
    padding: '4px 16px', cursor: 'pointer',
    background: active ? '#e8f0fe' : 'transparent',
    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
    color: active ? '#1a73e8' : '#333'
  })

  return (
    <div style={panelStyle}>
      <div style={sectionStyle}>Wiki Pages</div>
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

      <div style={sectionStyle}>Sources</div>
      {sources.length === 0 && (
        <div style={{ padding: '4px 16px', color: '#999', fontSize: 12 }}>No sources yet</div>
      )}
      {sources.map(src => (
        <div key={src.name}
          style={itemStyle(selection?.type === 'source' && selection.name === src.name)}
          onClick={() => onSelect({ type: 'source', name: src.name })}>
          📎 {src.name}
        </div>
      ))}

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
