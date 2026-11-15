import { ApiError, apiFetch } from '../lib/api'

export interface PostmortemContent {
  summary: string
  impact: string
  rootCause: string
  resolution: string
  prevention: string[]
}

export interface Postmortem extends PostmortemContent {
  id: string
  incidentId: string
  incidentKey: string
  incidentTitle: string
  status: 'DRAFT' | 'APPROVED'
  timeline: { at: string; text: string }[]
  draftedBy: 'claude' | 'template'
  model: string | null
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  approvedBy: string | null
  approvedAt: string | null
}

const path = (incidentId: string) => `/api/v1/incidents/${encodeURIComponent(incidentId)}/postmortem`

/** The postmortem, or null if none has been drafted yet. */
export async function getPostmortem(incidentId: string): Promise<Postmortem | null> {
  try {
    return await apiFetch<Postmortem>(path(incidentId))
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null
    throw err
  }
}

export const generatePostmortem = (incidentId: string) =>
  apiFetch<Postmortem>(`${path(incidentId)}/generate`, { method: 'POST' })

export const savePostmortem = (incidentId: string, content: PostmortemContent) =>
  apiFetch<Postmortem>(path(incidentId), { method: 'PUT', json: content })

export const approvePostmortem = (incidentId: string) =>
  apiFetch<Postmortem>(`${path(incidentId)}/approve`, { method: 'POST' })
