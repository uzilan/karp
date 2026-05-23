export interface WikiPage {
  name: string
  content: string
}

export interface SourceFile {
  name: string
  extension: string
  tags: string[]
}

export interface IngestStatus {
  status: 'PENDING' | 'PROCESSING' | 'COMPLETE' | 'ERROR'
}

export interface QueryResponse {
  answer: string
  sourcedFrom: string[]
}

export interface LintIssue {
  type: string
  page: string
  description: string
}

export interface SourceData {
  text: string
  metadata: Record<string, unknown>
  preview: string
  tags?: string[]
}
