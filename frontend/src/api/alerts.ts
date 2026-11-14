import { apiFetch } from '../lib/api'
import type { MetricType, ResourceType } from './resources'

export type Severity = 'CRITICAL' | 'WARNING' | 'INFO'
export type AlertStatus = 'FIRING' | 'RESOLVED'
export type Operator = 'GT' | 'LT'

export interface Alert {
  id: string
  ruleId: string
  ruleName: string
  resourceId: string
  resourceName: string
  status: AlertStatus
  severity: Severity
  message: string
  observedValue: number
  firedAt: string
  resolvedAt: string | null
}

export interface AlertSummary {
  firing: number
  critical: number
  warning: number
}

export interface AlertRule {
  id: string
  name: string
  metric: MetricType
  operator: Operator
  threshold: number
  durationMinutes: number
  severity: Severity
  resourceType: ResourceType | null
  enabled: boolean
  updatedAt: string
}

export type AlertRuleRequest = Omit<AlertRule, 'id' | 'updatedAt'>

export const listAlerts = (status?: AlertStatus) =>
  apiFetch<Alert[]>(status ? `/api/v1/alerts?status=${status}` : '/api/v1/alerts')

export const getAlertSummary = () => apiFetch<AlertSummary>('/api/v1/alerts/summary')

export const listAlertRules = () => apiFetch<AlertRule[]>('/api/v1/alert-rules')

export const createAlertRule = (rule: AlertRuleRequest) =>
  apiFetch<AlertRule>('/api/v1/alert-rules', { method: 'POST', json: rule })

export const updateAlertRule = (id: string, rule: AlertRuleRequest) =>
  apiFetch<AlertRule>(`/api/v1/alert-rules/${encodeURIComponent(id)}`, { method: 'PUT', json: rule })
