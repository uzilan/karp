import { useState } from 'react'
import { api } from '../api/client'
import type { QueryResponse } from '../types'

interface Message {
  role: 'user' | 'assistant'
  text: string
  response?: QueryResponse
}

interface Props {
  allTags: string[]
  selectedTags: string[]
  onTagToggle: (tag: string) => void
  onRefresh: () => void
}

export default function RightPanel({ allTags, selectedTags, onTagToggle, onRefresh }: Props) {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [fileBackName, setFileBackName] = useState('')

  const send = async () => {
    if (!input.trim() || loading) return
    const question = input.trim()
    setInput('')
    setMessages(m => [...m, { role: 'user', text: question }])
    setLoading(true)
    try {
      const res = await api.query(question)
      setMessages(m => [...m, { role: 'assistant', text: res.answer, response: res }])
    } catch (e) {
      setMessages(m => [...m, { role: 'assistant', text: `Error: ${e}` }])
    } finally {
      setLoading(false)
    }
  }

  const fileBack = async (text: string) => {
    const name = fileBackName.trim() || 'query-answer'
    await api.fileBack(name, text)
    setFileBackName('')
    onRefresh()
  }

  const panelStyle: React.CSSProperties = {
    width: 320, borderLeft: '1px solid #ddd', display: 'flex',
    flexDirection: 'column', fontSize: 13, background: '#fff'
  }

  return (
    <div style={panelStyle}>
      {allTags.length > 0 && (
        <div style={{ padding: '8px 10px', borderBottom: '1px solid #eee', display: 'flex', flexWrap: 'wrap', gap: 5 }}>
          {allTags.map(tag => {
            const active = selectedTags.includes(tag)
            return (
              <span
                key={tag}
                onClick={() => onTagToggle(tag)}
                style={{
                  cursor: 'pointer', fontSize: 11, padding: '2px 8px', borderRadius: 12,
                  background: active ? '#1a73e8' : '#f1f3f4',
                  color: active ? '#fff' : '#555',
                  border: `1px solid ${active ? '#1a73e8' : '#ddd'}`,
                  userSelect: 'none',
                }}
              >
                {tag}
              </span>
            )
          })}
        </div>
      )}
      <div style={{ padding: '10px 12px', borderBottom: '1px solid #ddd', fontWeight: 700, fontSize: 13 }}>
        Chat
      </div>
      <div style={{ flex: 1, overflow: 'auto', padding: 12, display: 'flex', flexDirection: 'column', gap: 10 }}>
        {messages.length === 0 && (
          <p style={{ color: '#aaa', fontSize: 12, textAlign: 'center', marginTop: 24 }}>
            Ask anything about your knowledge base.
          </p>
        )}
        {messages.map((m, i) => (
          <div key={i} style={{ display: 'flex', flexDirection: 'column', alignItems: m.role === 'user' ? 'flex-end' : 'flex-start' }}>
            <div style={{
              maxWidth: '92%',
              background: m.role === 'user' ? '#1a73e8' : '#f1f3f4',
              color: m.role === 'user' ? '#fff' : '#000',
              borderRadius: 8, padding: '7px 11px', lineHeight: 1.5
            }}>
              {m.text}
            </div>
            {m.role === 'assistant' && (
              <div style={{ display: 'flex', gap: 4, marginTop: 4, width: '92%' }}>
                <input
                  placeholder="page name…"
                  value={fileBackName}
                  onChange={e => setFileBackName(e.target.value)}
                  style={{ flex: 1, fontSize: 11, padding: '2px 6px', border: '1px solid #ddd', borderRadius: 3 }}
                />
                <button
                  onClick={() => fileBack(m.text)}
                  style={{ fontSize: 11, padding: '2px 8px', cursor: 'pointer' }}>
                  File to wiki
                </button>
              </div>
            )}
          </div>
        ))}
        {loading && <div style={{ color: '#888', fontSize: 12 }}>Thinking…</div>}
      </div>
      <div style={{ padding: 10, borderTop: '1px solid #ddd', display: 'flex', gap: 8 }}>
        <input
          style={{ flex: 1, padding: '7px 10px', borderRadius: 6, border: '1px solid #ccc', fontSize: 13 }}
          placeholder="Ask anything…"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && send()}
        />
        <button
          onClick={send}
          disabled={loading}
          style={{ padding: '7px 14px', borderRadius: 6, background: '#1a73e8', color: '#fff', border: 'none', cursor: 'pointer', fontSize: 13 }}>
          Send
        </button>
      </div>
    </div>
  )
}
