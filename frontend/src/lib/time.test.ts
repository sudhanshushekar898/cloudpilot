import { describe, expect, it } from 'vitest'
import { formatDuration, timeAgo } from './time'

const NOW = new Date('2026-11-15T10:00:00Z')

describe('timeAgo', () => {
  it.each([
    ['2026-11-15T09:59:50Z', 'just now'],
    ['2026-11-15T09:58:00Z', '2 min ago'],
    ['2026-11-15T07:00:00Z', '3 h ago'],
    ['2026-11-13T10:00:00Z', '2 d ago'],
  ])('%s -> %s', (iso, expected) => {
    expect(timeAgo(iso, NOW)).toBe(expected)
  })
})

describe('formatDuration', () => {
  it.each([
    [30_000, '<1 min'],
    [5 * 60_000, '5 min'],
    [65 * 60_000, '1 h 5 min'],
    [3 * 3_600_000, '3 h'],
  ])('%i ms -> %s', (ms, expected) => {
    expect(formatDuration(ms)).toBe(expected)
  })
})
