import { apiFetch } from '../lib/api'

export interface RunbookSummary {
  id: string
  slug: string
  title: string
  chunkCount: number
  createdBy: string
  updatedAt: string
}

export interface RunbookDetail {
  id: string
  slug: string
  title: string
  content: string
  createdBy: string
  updatedAt: string
}

/** A retrieved runbook section. Matched words in `snippet` are wrapped in [[ ]]. */
export interface RunbookHit {
  chunkId: string
  runbookId: string
  runbookSlug: string
  runbookTitle: string
  heading: string
  content: string
  snippet: string
  score: number
}

export const listRunbooks = () => apiFetch<RunbookSummary[]>('/api/v1/runbooks')

export const getRunbook = (slug: string) => apiFetch<RunbookDetail>(`/api/v1/runbooks/${encodeURIComponent(slug)}`)

export const searchRunbooks = (q: string) =>
  apiFetch<RunbookHit[]>(`/api/v1/runbooks/search?q=${encodeURIComponent(q)}`)

export const runbookSuggestions = (incidentId: string) =>
  apiFetch<RunbookHit[]>(`/api/v1/runbooks/suggestions?incidentId=${encodeURIComponent(incidentId)}`)

export const createRunbook = (title: string, content: string) =>
  apiFetch<RunbookSummary>('/api/v1/runbooks', { method: 'POST', json: { title, content } })

export const deleteRunbook = (slug: string) =>
  apiFetch<void>(`/api/v1/runbooks/${encodeURIComponent(slug)}`, { method: 'DELETE' })
