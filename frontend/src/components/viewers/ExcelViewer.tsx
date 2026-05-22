export default function ExcelViewer({ content }: { content: string }) {
  const sections = content.split('## Sheet:').filter(Boolean)

  if (sections.length === 0) {
    return <pre style={{ fontSize: 13 }}>{content}</pre>
  }

  return (
    <div style={{ fontSize: 13, overflow: 'auto' }}>
      {sections.map((section, si) => {
        const lines = section.trim().split('\n')
        const sheetName = lines[0]?.trim() ?? `Sheet ${si + 1}`
        const dataLines = lines.slice(1).filter(l => l.trim() && !l.startsWith('[truncated'))

        return (
          <div key={si} style={{ marginBottom: 32 }}>
            <h3 style={{ marginBottom: 8 }}>📊 {sheetName}</h3>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ borderCollapse: 'collapse', minWidth: '100%' }}>
                <tbody>
                  {dataLines.map((row, ri) => (
                    <tr key={ri} style={{ background: ri % 2 === 0 ? '#f9f9f9' : '#fff' }}>
                      {row.split(' | ').map((cell, ci) => (
                        <td key={ci} style={{
                          border: '1px solid #ddd', padding: '4px 10px',
                          fontWeight: ri === 0 ? 700 : 400
                        }}>
                          {cell.trim()}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )
      })}
    </div>
  )
}
