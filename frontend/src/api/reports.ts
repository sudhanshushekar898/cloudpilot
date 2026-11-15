import { apiFetch } from '../lib/api'

export interface Report {
  from: string
  to: string
  opened: number
  resolved: number
  active: number
  mttaMinutes: number | null
  mttrMinutes: number | null
  slaCompliancePercent: number | null
  sla: { severity: string; targetMinutes: number; resolved: number; withinTarget: number }[]
  bySeverity: Record<string, number>
  recurring: { resource: string; signature: string; occurrences: number; lastSeen: string }[]
  topResources: { resource: string; incidents: number }[]
  responders: { name: string; resolved: number; mttrMinutes: number }[]
}

export const getReport = (days: number) => apiFetch<Report>(`/api/v1/reports/summary?days=${days}`)
