import { useState } from 'react'
import type { IngestPreview } from '../types'

interface Props {
  preview: IngestPreview
  onConfirm: (fileName: string, tags: string[], category: string) => void
  onCancel: () => void
}

export default function IngestModal({ preview, onConfirm, onCancel }: Props) {
  const [category, setCategory] = useState(preview.suggestedCategory)
  const [tagsInput, setTagsInput] = useState(preview.suggestedTags.join(', '))

  const confirm = () => {
    const tags = tagsInput
      .split(',')
      .map(t => t.trim())
      .filter(Boolean)
    onConfirm(preview.fileName, tags, category)
  }

  const overlay: React.CSSProperties = {
    position: 'fixed', inset: 0,
    background: 'rgba(0,0,0,0.45)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    zIndex: 100
  }
  const modal: React.CSSProperties = {
    background: '#fff', borderRadius: 10, padding: 28,
    width: 500, maxWidth: '90vw',
    boxShadow: '0 12px 48px rgba(0,0,0,0.25)'
  }
  const label: React.CSSProperties = {
    display: 'block', marginBottom: 5,
    fontWeight: 600, fontSize: 12, color: '#444'
  }
  const inputStyle: React.CSSProperties = {
    width: '100%', padding: '8px 10px', fontSize: 13,
    borderRadius: 5, border: '1px solid #ccc',
    boxSizing: 'border-box', outline: 'none'
  }

  return (
    <div style={overlay} onClick={e => { if (e.target === e.currentTarget) onCancel() }}>
      <div style={modal}>
        <h3 style={{ margin: '0 0 6px', fontSize: 16 }}>Confirm Ingest</h3>
        <p style={{ fontSize: 12, color: '#888', margin: '0 0 6px' }}>
          <strong style={{ color: '#333' }}>{preview.fileName}</strong>
        </p>
        <p style={{
          fontSize: 12, color: '#666', margin: '0 0 20px',
          padding: '8px 10px', background: '#f8f8f8', borderRadius: 4,
          fontStyle: 'italic'
        }}>
          {preview.preview}
        </p>

        <div style={{ marginBottom: 14 }}>
          <label style={label}>Category</label>
          <input
            style={inputStyle}
            value={category}
            onChange={e => setCategory(e.target.value)}
            placeholder="e.g. Finance, Technology, Research"
          />
        </div>

        <div style={{ marginBottom: 22 }}>
          <label style={label}>Tags <span style={{ fontWeight: 400, color: '#999' }}>(comma-separated)</span></label>
          <input
            style={inputStyle}
            value={tagsInput}
            onChange={e => setTagsInput(e.target.value)}
            placeholder="e.g. budget, q3, report"
          />
          {tagsInput && (
            <div style={{ marginTop: 6, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
              {tagsInput.split(',').map(t => t.trim()).filter(Boolean).map(tag => (
                <span key={tag} style={{
                  background: '#e8f0fe', color: '#1a73e8',
                  borderRadius: 12, padding: '2px 8px', fontSize: 11
                }}>
                  #{tag}
                </span>
              ))}
            </div>
          )}
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <button
            onClick={onCancel}
            style={{
              padding: '7px 18px', borderRadius: 5, border: '1px solid #ccc',
              background: '#fff', cursor: 'pointer', fontSize: 13
            }}>
            Cancel
          </button>
          <button
            onClick={confirm}
            style={{
              padding: '7px 18px', borderRadius: 5, border: 'none',
              background: '#1a73e8', color: '#fff', cursor: 'pointer', fontSize: 13,
              fontWeight: 600
            }}>
            Ingest
          </button>
        </div>
      </div>
    </div>
  )
}
