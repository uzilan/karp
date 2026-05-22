import type { IngestPreview } from '../types'

interface Props {
  preview: IngestPreview
  onConfirm: (fileName: string, tags: string[], category: string) => void
  onCancel: () => void
}

export default function IngestModal({ preview, onConfirm, onCancel }: Props) {
  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100
    }}>
      <div style={{ background: '#fff', borderRadius: 8, padding: 24, width: 400, boxShadow: '0 8px 32px rgba(0,0,0,0.2)' }}>
        <h3 style={{ margin: '0 0 12px' }}>Ingest File?</h3>
        <p style={{ fontSize: 13, color: '#555' }}><strong>{preview.fileName}</strong></p>
        <p style={{ fontSize: 12, color: '#888' }}>{preview.preview}</p>
        <p style={{ fontSize: 12 }}>Category: {preview.suggestedCategory} | Tags: {preview.suggestedTags.join(', ')}</p>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 16 }}>
          <button onClick={onCancel}>Cancel</button>
          <button
            onClick={() => onConfirm(preview.fileName, preview.suggestedTags, preview.suggestedCategory)}
            style={{ background: '#1a73e8', color: '#fff', border: 'none', borderRadius: 4, padding: '6px 16px', cursor: 'pointer' }}>
            Ingest
          </button>
        </div>
      </div>
    </div>
  )
}
