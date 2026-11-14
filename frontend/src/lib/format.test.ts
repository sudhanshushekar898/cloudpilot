import { describe, expect, it } from 'vitest'
import { formatMetric, METRIC_LABELS } from './format'

describe('formatMetric', () => {
  it.each([
    ['CPU', 45.678, '45.7%'],
    ['MEMORY', 100, '100%'],
    ['ERROR_RATE', 0.04, '0.04%'],
    ['ERROR_RATE', 12.345, '12.3%'],
    ['AVAILABILITY', 99.95, '99.95%'],
    ['LATENCY_P95', 220.4, '220 ms'],
    ['LATENCY_P95', 2400, '2.4 s'],
  ] as const)('%s %s -> %s', (metric, value, expected) => {
    expect(formatMetric(metric, value)).toBe(expected)
  })

  it('shows a dash when there is no value', () => {
    expect(formatMetric('CPU', undefined)).toBe('—')
  })

  it('has a readable label for every metric', () => {
    expect(METRIC_LABELS.LATENCY_P95).toBe('Latency (p95)')
  })
})
