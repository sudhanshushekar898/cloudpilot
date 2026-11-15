import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Report } from '../api/reports'
import { mockApi, renderWithProviders } from '../test/utils'
import ReportsPage from './ReportsPage'

const report: Report = {
  from: '2026-10-16T12:00:00Z',
  to: '2026-11-15T12:00:00Z',
  opened: 4,
  resolved: 3,
  active: 1,
  mttaMinutes: 11.3,
  mttrMinutes: 106.7,
  slaCompliancePercent: 66.7,
  sla: [
    { severity: 'CRITICAL', targetMinutes: 60, resolved: 2, withinTarget: 1 },
    { severity: 'WARNING', targetMinutes: 240, resolved: 1, withinTarget: 1 },
    { severity: 'INFO', targetMinutes: 1440, resolved: 0, withinTarget: 0 },
  ],
  bySeverity: { CRITICAL: 3, WARNING: 1 },
  recurring: [{ resource: 'orders-db', signature: 'orders-db cpu #% > #% for # min', occurrences: 2, lastSeen: '2026-11-14T12:00:00Z' }],
  topResources: [{ resource: 'orders-db', incidents: 2 }],
  responders: [{ name: 'Rahul SRE', resolved: 2, mttrMinutes: 60 }],
}

describe('ReportsPage', () => {
  it('shows headline numbers as stat tiles', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/reports/summary': report }))
    renderWithProviders(<ReportsPage />, { role: 'DEVELOPER' })

    // Tile labels are <p>; the same words also appear as a table header.
    const tile = (label: string) => screen.getByText(label, { selector: 'p' }).closest('div')!
    await screen.findByText('Incidents opened')
    expect(within(tile('Incidents opened')).getByText('4')).toBeInTheDocument()
    expect(within(tile('Mean time to acknowledge')).getByText('11 min')).toBeInTheDocument()
    expect(within(tile('Mean time to resolve')).getByText('1 h 47 min')).toBeInTheDocument()
    expect(within(tile('Resolved within SLA')).getByText('67%')).toBeInTheDocument()
  })

  it('shows SLA per severity, recurring failures and responders', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/reports/summary': report }))
    renderWithProviders(<ReportsPage />, { role: 'SRE' })

    const critical = (await screen.findByText('CRITICAL', { selector: 'td' })).closest('tr')!
    expect(within(critical).getByText('1 of 2')).toBeInTheDocument()
    expect(within(critical).getByText('Below target')).toBeInTheDocument()
    expect(screen.getByText('orders-db cpu #% > #% for # min')).toBeInTheDocument()
    expect(screen.getByText('Rahul SRE')).toBeInTheDocument()
  })

  it('changes the time window', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/reports/summary': report }))
    renderWithProviders(<ReportsPage />, { role: 'SRE' })
    await screen.findByText('Incidents opened')

    await userEvent.click(screen.getByRole('button', { name: '7 days' }))

    await vi.waitFor(() => expect(spy).toHaveBeenCalledWith('/api/v1/reports/summary?days=7', expect.anything()))
  })

  it('shows "no data" instead of zero when nothing was resolved', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({
      '/api/v1/reports/summary': { ...report, opened: 0, resolved: 0, active: 0, mttaMinutes: null, mttrMinutes: null,
        slaCompliancePercent: null, recurring: [], topResources: [], responders: [] },
    }))
    renderWithProviders(<ReportsPage />, { role: 'SRE' })

    expect(await screen.findAllByText('No data')).toHaveLength(3)
    expect(screen.getByText('No failure happened more than once in this period.')).toBeInTheDocument()
  })
})
