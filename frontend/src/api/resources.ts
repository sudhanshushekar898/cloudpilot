import { apiFetch } from '../lib/api'

export type MetricType = 'CPU' | 'MEMORY' | 'LATENCY_P95' | 'ERROR_RATE' | 'AVAILABILITY'
export type ResourceType = 'EC2' | 'RDS' | 'API' | 'QUEUE' | 'POD'
export type ResourceStatus = 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'UNKNOWN'

export const METRIC_TYPES: MetricType[] = ['CPU', 'MEMORY', 'LATENCY_P95', 'ERROR_RATE', 'AVAILABILITY']

export interface Resource {
  id: string
  name: string
  type: ResourceType
  region: string
  description: string
  status: ResourceStatus
  latest: Partial<Record<MetricType, number>>
  dependsOn: string[]
}

export interface MetricPoint {
  timestamp: string
  value: number
}

export interface MetricHistory {
  resourceId: string
  minutes: number
  bucketSeconds: number
  series: Partial<Record<MetricType, MetricPoint[]>>
}

export type ScenarioType = 'DB_CONNECTION_EXHAUSTION' | 'BAD_DEPLOYMENT' | 'QUEUE_BACKLOG' | 'POD_MEMORY_LEAK'

export interface ScenarioInfo {
  type: ScenarioType
  title: string
  description: string
  durationMinutes: number
  affectedResources: string[]
}

export interface ActiveScenario {
  type: ScenarioType
  startedAt: string
  endsAt: string
  startedBy: string
}

export interface SimulatorState {
  scenarios: ScenarioInfo[]
  active: ActiveScenario | null
}

export const listResources = () => apiFetch<Resource[]>('/api/v1/resources')

export const getResource = (id: string) => apiFetch<Resource>(`/api/v1/resources/${encodeURIComponent(id)}`)

export const getMetricHistory = (id: string, minutes: number) =>
  apiFetch<MetricHistory>(`/api/v1/resources/${encodeURIComponent(id)}/metrics?minutes=${minutes}`)

export const getSimulator = () => apiFetch<SimulatorState>('/api/v1/simulator')

export const startScenario = (type: ScenarioType) =>
  apiFetch<ActiveScenario>(`/api/v1/simulator/scenarios/${type}/start`, { method: 'POST' })

export const stopScenario = () => apiFetch<void>('/api/v1/simulator/stop', { method: 'POST' })
