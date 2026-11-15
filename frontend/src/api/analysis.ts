import { ApiError, apiFetch } from '../lib/api'

export type EvidenceKind = 'ALERT' | 'LOG' | 'DEPLOYMENT' | 'RUNBOOK'

export interface EvidenceItem {
  id: string
  kind: EvidenceKind
  resourceName: string | null
  occurredAt: string | null
  text: string
  source: string | null
}

export interface Claim {
  text: string
  citations: string[]
}

export interface RemediationStep extends Claim {
  requiresApproval: boolean
}

export interface Analysis {
  id: string
  incidentId: string
  question: string
  summary: string
  observedEvidence: Claim[]
  likelyCause: Claim
  confidence: number
  confidenceReason: string
  verification: Claim[]
  remediation: RemediationStep[]
  warnings: string[]
  evidence: EvidenceItem[]
  analyzer: 'claude' | 'rule-based'
  model: string | null
  requestedBy: string
  createdAt: string
}

const path = (incidentId: string) => `/api/v1/incidents/${encodeURIComponent(incidentId)}/analysis`

/** The latest analysis, or null if nobody has asked yet. */
export async function getLatestAnalysis(incidentId: string): Promise<Analysis | null> {
  try {
    return await apiFetch<Analysis>(path(incidentId))
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null
    throw err
  }
}

export const requestAnalysis = (incidentId: string, question: string) =>
  apiFetch<Analysis>(path(incidentId), { method: 'POST', json: { question } })
