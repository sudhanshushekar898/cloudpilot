import type { MetricType } from '../api/resources'

export const METRIC_LABELS: Record<MetricType, string> = {
  CPU: 'CPU',
  MEMORY: 'Memory',
  LATENCY_P95: 'Latency (p95)',
  ERROR_RATE: 'Error rate',
  AVAILABILITY: 'Availability',
}

function trimNumber(value: number, decimals: number): string {
  return Number(value.toFixed(decimals)).toString()
}

export function formatMetric(metric: MetricType, value: number | undefined): string {
  if (value === undefined || Number.isNaN(value)) return '—'

  switch (metric) {
    case 'LATENCY_P95':
      return value >= 1000 ? `${trimNumber(value / 1000, 1)} s` : `${Math.round(value)} ms`
    case 'AVAILABILITY':
      return `${trimNumber(value, 2)}%`
    case 'ERROR_RATE':
      return `${trimNumber(value, value < 1 ? 2 : 1)}%`
    default:
      return `${trimNumber(value, 1)}%`
  }
}
