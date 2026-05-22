const METHOD_COLORS: Record<string, string> = {
  GET: '#61affe', POST: '#49cc90', PUT: '#fca130',
  DELETE: '#f93e3e', PATCH: '#50e3c2', HEAD: '#9012fe', OPTIONS: '#0d5aa7'
}

export default function OpenApiViewer({ content }: { content: string }) {
  const lines = content.split('\n')

  interface Endpoint { method: string; path: string; summary: string }
  const endpoints: Endpoint[] = []
  let title = ''
  let currentSummary = ''

  lines.forEach(line => {
    if (line.startsWith('# ')) title = line.replace('# ', '')
    const match = line.match(/^## (GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS) (.+)/)
    if (match) {
      if (endpoints.length > 0) endpoints[endpoints.length - 1].summary = currentSummary
      endpoints.push({ method: match[1], path: match[2], summary: '' })
      currentSummary = ''
    } else if (line.trim() && !line.startsWith('-') && !line.startsWith('#') && endpoints.length > 0) {
      currentSummary = line.trim()
    }
  })
  if (endpoints.length > 0 && currentSummary) {
    endpoints[endpoints.length - 1].summary = currentSummary
  }

  if (endpoints.length === 0) {
    return <pre style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>{content}</pre>
  }

  return (
    <div style={{ fontSize: 13 }}>
      {title && <h2 style={{ marginBottom: 16 }}>{title}</h2>}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {endpoints.map((ep, i) => (
          <div key={i} style={{
            display: 'flex', alignItems: 'center', gap: 12,
            padding: '8px 12px', border: '1px solid #e0e0e0', borderRadius: 4,
            borderLeft: `4px solid ${METHOD_COLORS[ep.method] ?? '#ccc'}`
          }}>
            <span style={{
              background: METHOD_COLORS[ep.method] ?? '#ccc',
              color: '#fff', borderRadius: 3, padding: '2px 8px',
              fontWeight: 700, fontSize: 11, minWidth: 65, textAlign: 'center'
            }}>{ep.method}</span>
            <code style={{ flex: 1, fontSize: 13 }}>{ep.path}</code>
            {ep.summary && <span style={{ color: '#666', fontSize: 12 }}>{ep.summary}</span>}
          </div>
        ))}
      </div>
    </div>
  )
}
