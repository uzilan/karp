import JsonView from '@uiw/react-json-view'

export default function JsonViewer({ content }: { content: string }) {
  try {
    const data = JSON.parse(content) as Record<string, unknown>
    return <JsonView value={data} style={{ fontSize: 13, fontFamily: 'monospace' }} />
  } catch {
    return <pre style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>{content}</pre>
  }
}
