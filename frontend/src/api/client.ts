import type { WikiPage, SourceFile, IngestPreview, QueryResponse, LintIssue, IngestStatus, SourceData } from '../types'

const BASE = '/api'

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`)
  if (!res.ok) throw new Error(`GET ${path} failed: ${res.status}`)
  return res.json()
}

async function post<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) throw new Error(`POST ${path} failed: ${res.status}`)
  return res.json()
}

async function put(path: string, body: unknown): Promise<void> {
  const res = await fetch(`${BASE}${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`PUT ${path} failed: ${res.status}`)
}

export const api = {
  wiki: {
    list: () => get<string[]>('/wiki'),
    get: (name: string) => get<WikiPage>(`/wiki/${name}`),
    update: (name: string, content: string) => put(`/wiki/${name}`, { content }),
  },
  sources: {
    list: () => get<SourceFile[]>('/sources'),
    upload: async (file: File, overwrite = false): Promise<IngestPreview | { duplicate: true; fileName: string }> => {
      const fd = new FormData()
      fd.append('file', file)
      const res = await fetch(`${BASE}/sources/upload?overwrite=${overwrite}`, { method: 'POST', body: fd })
      if (res.status === 409) {
        const body = await res.json()
        return { duplicate: true, fileName: body.fileName }
      }
      if (!res.ok) throw new Error(`Upload failed: ${res.status}`)
      return res.json()
    },
    confirm: (fileName: string, tags: string[], category: string) =>
      fetch(`${BASE}/sources/confirm`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fileName, tags, category }),
      }),
    pollStatus: (fileName: string) => get<IngestStatus>(`/sources/${fileName}/status`),
    getData: (name: string) => get<SourceData>(`/sources/${name}/data`),
  },
  query: (question: string, tags: string[] = [], category?: string) =>
    post<QueryResponse>('/query', { question, tags, category }),
  fileBack: (pageName: string, content: string) =>
    post<void>('/query/file-back', { pageName, content }),
  lint: () => post<LintIssue[]>('/lint'),
  wipe: () => fetch(`${BASE}/wipe`, { method: 'POST' }),
}
