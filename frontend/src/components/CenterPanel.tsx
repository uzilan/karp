import { useState, useEffect } from 'react'
import { api } from '../api/client'
import type { Selection } from '../App'
import MarkdownViewer from './viewers/MarkdownViewer'
import JsonViewer from './viewers/JsonViewer'
import ExcelViewer from './viewers/ExcelViewer'
import OpenApiViewer from './viewers/OpenApiViewer'
import CodeViewer from './viewers/CodeViewer'

interface Props { selection: Selection; refreshKey?: number }

function getViewerType(name: string): 'markdown' | 'excel' | 'json' | 'openapi' | 'code' {
  const lower = name.toLowerCase()
  const ext = lower.split('.').pop() ?? ''

  if (['xlsx', 'xls'].includes(ext)) return 'excel'
  if (['md', 'txt', 'rst'].includes(ext)) return 'markdown'

  const openApiNames = ['openapi', 'swagger', 'api-spec', 'api_spec']
  if (openApiNames.some(n => lower.includes(n))) return 'openapi'

  if (['json', 'yaml', 'yml'].includes(ext)) return 'json'

  return 'code'
}

export default function CenterPanel({ selection, refreshKey }: Props) {
  const [content, setContent] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [title, setTitle] = useState('')
  const [tags, setTags] = useState<string[]>([])
  const [sourceFile, setSourceFile] = useState<string | null>(null)

  useEffect(() => {
    if (!selection) { setContent(null); setTitle(''); setTags([]); setSourceFile(null); return }
    setLoading(true)
    const load = async () => {
      try {
        if (selection.type === 'wiki') {
          const page = await api.wiki.get(selection.name)
          setContent(page.content)
          setTitle(selection.name)
          setTags([])
          setSourceFile(page.source ?? null)
        } else {
          const data = await api.sources.getData(selection.name)
          setContent(data.text)
          setTitle(selection.name)
          setTags(data.tags ?? [])
          setSourceFile(selection.name)
        }
      } catch {
        setContent('Failed to load content.')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [selection, refreshKey])

  const outerStyle: React.CSSProperties = { flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column' }
  const innerStyle: React.CSSProperties = { padding: 24, flex: 1 }

  if (!selection) return (
    <div style={{ ...outerStyle, alignItems: 'center', justifyContent: 'center' }}>
      <p style={{ color: 'var(--color-text-faint)', fontSize: 14 }}>Select a page or file to view.</p>
    </div>
  )

  if (loading) return (
    <div style={{ ...outerStyle, alignItems: 'center', justifyContent: 'center' }}>
      <p style={{ color: 'var(--color-text-faint)' }}>Loading…</p>
    </div>
  )

  if (!content) return <div style={outerStyle} />

  return (
    <div style={outerStyle}>
      <div style={{ padding: '8px 24px', borderBottom: '1px solid var(--color-border)', fontSize: 12, color: 'var(--color-text-faint)', display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
        {sourceFile ? (
          <a
            href={`/api/sources/${encodeURIComponent(sourceFile)}/raw`}
            target="_blank"
            rel="noreferrer"
            style={{ color: 'var(--color-selected-text)', textDecoration: 'underline' }}
          >
            {title}
          </a>
        ) : (
          <span>{title}</span>
        )}
        {tags.map(tag => (
          <span key={tag} style={{ background: 'var(--color-selected-bg)', color: 'var(--color-selected-text)', borderRadius: 10, padding: '1px 8px' }}>#{tag}</span>
        ))}
      </div>
      <div style={innerStyle}>
        {selection.type === 'wiki' ? (
          <MarkdownViewer content={content} />
        ) : (() => {
          const vt = getViewerType(selection.name)
          if (vt === 'excel') return <ExcelViewer content={content} />
          if (vt === 'json') return <JsonViewer content={content} />
          if (vt === 'openapi') return <OpenApiViewer content={content} />
          if (vt === 'code') return <CodeViewer content={content} fileName={selection.name} />
          return <MarkdownViewer content={content} />
        })()}
      </div>
    </div>
  )
}
