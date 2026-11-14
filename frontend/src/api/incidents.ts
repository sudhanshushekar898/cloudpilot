import { apiFetch } from '../lib/api'
import type { Severity } from './alerts'
import type { ResourceType } from './resources'

export type IncidentStatus = 'OPEN' | 'ACKNOWLEDGED' | 'INVESTIGATING' | 'RESOLVED'
export type IncidentScope = 'active' | 'resolved' | 'all'
export type TimelineType =
  | 'ALERT_FIRED'
  | 'ALERT_RESOLVED'
  | 'STATUS_CHANGED'
  | 'ASSIGNED'
  | 'COMMENT'
  | 'LOG'
  | 'DEPLOYMENT'

export interface IncidentSummary {
  id: string
  key: string
  title: string
  status: IncidentStatus
  severity: Severity
  assigneeName: string | null
  alertCount: number
  activeAlertCount: number
  resourceNames: string[]
  openedAt: string
  resolvedAt: string | null
}

export interface IncidentAlert {
  alertId: string
  resourceId: string
  resourceName: string
  severity: Severity
  message: string
  reason: string
  firedAt: string
  resolvedAt: string | null
}

export interface IncidentResource {
  id: string
  name: string
  type: ResourceType
  probableOrigin: boolean
}

export interface IncidentDetail {
  id: string
  key: string
  title: string
  status: IncidentStatus
  severity: Severity
  assignee: { id: string; displayName: string } | null
  openedAt: string
  acknowledgedAt: string | null
  investigatingAt: string | null
  resolvedAt: string | null
  resolution: string | null
  allAlertsResolved: boolean
  alerts: IncidentAlert[]
  resources: IncidentResource[]
}

export interface TimelineItem {
  type: TimelineType
  occurredAt: string
  message: string
  actor: string | null
  resourceId: string | null
  resourceName: string | null
  level: 'INFO' | 'WARN' | 'ERROR' | null
}

export interface Person {
  id: string
  displayName: string
  email: string
  role: string
}

const base = (id: string) => `/api/v1/incidents/${encodeURIComponent(id)}`

export const listIncidents = (scope: IncidentScope) => apiFetch<IncidentSummary[]>(`/api/v1/incidents?scope=${scope}`)

export const getIncidentSummary = () => apiFetch<{ active: number }>('/api/v1/incidents/summary')

export const getIncident = (id: string) => apiFetch<IncidentDetail>(base(id))

export function getTimeline(id: string, types: TimelineType[], resourceId?: string) {
  const params = new URLSearchParams()
  types.forEach((t) => params.append('type', t))
  if (resourceId) params.set('resourceId', resourceId)
  const query = params.toString()
  return apiFetch<TimelineItem[]>(`${base(id)}/timeline${query ? `?${query}` : ''}`)
}

export const acknowledgeIncident = (id: string) => apiFetch<IncidentDetail>(`${base(id)}/acknowledge`, { method: 'POST' })

export const investigateIncident = (id: string) => apiFetch<IncidentDetail>(`${base(id)}/investigate`, { method: 'POST' })

export const resolveIncident = (id: string, resolution: string) =>
  apiFetch<IncidentDetail>(`${base(id)}/resolve`, { method: 'POST', json: { resolution } })

export const assignIncident = (id: string, userId: string) =>
  apiFetch<IncidentDetail>(`${base(id)}/assign`, { method: 'POST', json: { userId } })

export const commentOnIncident = (id: string, text: string) =>
  apiFetch<void>(`${base(id)}/comments`, { method: 'POST', json: { text } })

export const listAssignable = () => apiFetch<Person[]>('/api/v1/users/assignable')
