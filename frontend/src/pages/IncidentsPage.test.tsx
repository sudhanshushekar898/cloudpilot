import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { IncidentSummary } from '../api/incidents'
import { mockApi, renderWithProviders } from '../test/utils'
import IncidentsPage from './IncidentsPage'

const active: IncidentSummary = {
  id: 'i1',
  key: 'INC-7',
  title: 'orders-db cpu 97% > 90% for 3 min',
  status: 'ACKNOWLEDGED',
  severity: 'CRITICAL',
  assigneeName: 'Rahul SRE',
  alertCount: 4,
  activeAlertCount: 3,
  resourceNames: ['orders-db', 'checkout-api', 'web-frontend'],
  openedAt: new Date(Date.now() - 6 * 60_000).toISOString(),
  resolvedAt: null,
}

describe('IncidentsPage', () => {
  it('lists active incidents with key, status, owner and grouped resources', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/incidents': [active] }))
    renderWithProviders(<IncidentsPage />, { role: 'SRE' })

    const row = (await screen.findByRole('link', { name: 'INC-7' })).closest('tr')!
    expect(within(row).getByRole('link', { name: 'INC-7' })).toHaveAttribute('href', '/incidents/i1')
    expect(within(row).getByText('Acknowledged')).toBeInTheDocument()
    expect(within(row).getByText('Rahul SRE')).toBeInTheDocument()
    expect(within(row).getByText('3 of 4 alerts firing')).toBeInTheDocument()
    expect(within(row).getByText('orders-db, checkout-api, web-frontend')).toBeInTheDocument()
    expect(fetchSpy).toHaveBeenCalledWith('/api/v1/incidents?scope=active', expect.anything())
  })

  it('switches to resolved incidents', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/incidents': [] }))
    renderWithProviders(<IncidentsPage />, { role: 'SRE' })

    await userEvent.click(screen.getByRole('tab', { name: 'Resolved' }))

    await vi.waitFor(() =>
      expect(fetchSpy).toHaveBeenCalledWith('/api/v1/incidents?scope=resolved', expect.anything()),
    )
  })

  it('shows an empty state when there are no active incidents', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/incidents': [] }))
    renderWithProviders(<IncidentsPage />, { role: 'DEVELOPER' })

    expect(await screen.findByText('No active incidents.')).toBeInTheDocument()
  })
})
