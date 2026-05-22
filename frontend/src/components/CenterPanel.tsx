import { useState, useEffect } from 'react'
import { api } from '../api/client'
import type { Selection } from '../App'

interface Props { selection: Selection }

export default function CenterPanel({ selection }: Props) {
  const [content, setContent] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!selection) { setContent(null); return }
    setLoading(true)
    const load = async () => {
      try {
        if (selection.type === 'wiki') {
          const page = await api.wiki.get(selection.name)
          setContent(page.content)
        } else {
          const data = await api.sources.getData(selection.name)
          setContent(data.text)
        }
      } catch {
        setContent('Failed to load content.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [selection])

  const style: React.CSSProperties = { flex: 1, overflow: 'auto', padding: 24 }

  if (!selection) return (
    <div style={style}>
      <p style={{ color: '#888', marginTop: 40, textAlign: 'center' }}>
        Select a wiki page or source file to view it here.
      </p>
    </div>
  )

  if (loading) return <div style={style}><p style={{ color: '#888' }}>Loading…</p></div>

  return (
    <div style={style}>
      <pre style={{ whiteSpace: 'pre-wrap', fontSize: 13, fontFamily: 'inherit' }}>{content}</pre>
    </div>
  )
}
