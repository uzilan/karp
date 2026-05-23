import { useState, useEffect } from 'react'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneLight, oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism'

const EXT_TO_LANG: Record<string, string> = {
  kt: 'kotlin', java: 'java', py: 'python', ts: 'typescript',
  tsx: 'tsx', js: 'javascript', jsx: 'jsx', go: 'go',
  rs: 'rust', cpp: 'cpp', c: 'c', cs: 'csharp', rb: 'ruby',
  sh: 'bash', sql: 'sql'
}

function useIsDark(): boolean {
  const [isDark, setIsDark] = useState(
    () => document.documentElement.dataset.theme === 'dark'
  )
  useEffect(() => {
    const observer = new MutationObserver(() => {
      setIsDark(document.documentElement.dataset.theme === 'dark')
    })
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    return () => observer.disconnect()
  }, [])
  return isDark
}

interface Props { content: string; fileName: string }

export default function CodeViewer({ content, fileName }: Props) {
  const isDark = useIsDark()
  const ext = fileName.split('.').pop()?.toLowerCase() ?? ''
  const lang = EXT_TO_LANG[ext] ?? 'text'
  // Strip "Language: xxx\n\n" prefix added by CodeReader
  const code = content.replace(/^Language: \w+\n\n/, '')

  return (
    <SyntaxHighlighter
      language={lang}
      style={isDark ? oneDark : oneLight}
      customStyle={{ fontSize: 13, borderRadius: 4 }}
      showLineNumbers>
      {code}
    </SyntaxHighlighter>
  )
}
