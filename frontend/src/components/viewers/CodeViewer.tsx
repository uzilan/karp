import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism'

const EXT_TO_LANG: Record<string, string> = {
  kt: 'kotlin', java: 'java', py: 'python', ts: 'typescript',
  tsx: 'tsx', js: 'javascript', jsx: 'jsx', go: 'go',
  rs: 'rust', cpp: 'cpp', c: 'c', cs: 'csharp', rb: 'ruby',
  sh: 'bash', sql: 'sql'
}

interface Props { content: string; fileName: string }

export default function CodeViewer({ content, fileName }: Props) {
  const ext = fileName.split('.').pop()?.toLowerCase() ?? ''
  const lang = EXT_TO_LANG[ext] ?? 'text'
  // Strip "Language: xxx\n\n" prefix added by CodeReader
  const code = content.replace(/^Language: \w+\n\n/, '')

  return (
    <SyntaxHighlighter
      language={lang}
      style={oneLight}
      customStyle={{ fontSize: 13, borderRadius: 4 }}
      showLineNumbers>
      {code}
    </SyntaxHighlighter>
  )
}
