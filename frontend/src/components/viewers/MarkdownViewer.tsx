import ReactMarkdown from 'react-markdown'

export default function MarkdownViewer({ content }: { content: string }) {
  return (
    <div style={{ maxWidth: 800, lineHeight: 1.7, fontSize: 14 }}>
      <ReactMarkdown>{content}</ReactMarkdown>
    </div>
  )
}
