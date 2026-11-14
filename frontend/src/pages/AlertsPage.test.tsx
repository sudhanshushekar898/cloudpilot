import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Alert } from '../api/alerts'
import { mockApi, renderWithProviders } from '../test/utils'
import AlertsPage from './AlertsPage'

const firing: Alert = {
  id: 'a1',
  ruleId: 'r1',
  ruleName: 'API error rate above 5%',
  resourceId: 'checkout',
  resourceName: 'checkout-api',
  status: 'FIRING',
  severity: 'CRITICAL',
  message: 'checkout-api error rate 18% > 5% for 5 min',
  observedValue: 18,
  firedAt: new Date(Date.now() - 4 * 60_000).toISOString(),
  resolvedAt: null,
}

const resolved: Alert = {
  ...firing,
  id: 'a2',
  status: 'RESOLVED',
  severity: 'WARNING',
  message: 'orders-db cpu 95% > 90% for 3 min',
  resourceId: 'orders',
  resourceName: 'orders-db',
  firedAt: new Date(Date.now() - 60 * 60_000).toISOString(),
  resolvedAt: new Date(Date.now() - 48 * 60_000).toISOString(),
}

describe('AlertsPage', () => {
  it('shows firing alerts by default with severity, resource link and age', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/alerts': [firing] }))
    renderWithProviders(<AlertsPage />, { role: 'SRE' })

    const row = (await screen.findByText(firing.message)).closest('tr')!
    expect(within(row).getByText('CRITICAL')).toBeInTheDocument()
    expect(within(row).getByRole('link', { name: 'checkout-api' })).toHaveAttribute('href', '/resources/checkout')
    expect(within(row).getByText('4 min ago')).toBeInTheDocument()
    expect(fetchSpy).toHaveBeenCalledWith('/api/v1/alerts?status=FIRING', expect.anything())
  })

  it('switches to all alerts and shows how long resolved ones lasted', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({ '/api/v1/alerts?status=FIRING': [firing], '/api/v1/alerts': [firing, resolved] }),
    )
    renderWithProviders(<AlertsPage />, { role: 'SRE' })
    await screen.findByText(firing.message)

    await userEvent.click(screen.getByRole('tab', { name: 'All' }))

    expect(await screen.findByText(resolved.message)).toBeInTheDocument()
    expect(screen.getByText('Resolved after 12 min')).toBeInTheDocument()
    expect(fetchSpy).toHaveBeenCalledWith('/api/v1/alerts', expect.anything())
  })

  it('shows a calm empty state when nothing is firing', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/alerts': [] }))
    renderWithProviders(<AlertsPage />, { role: 'DEVELOPER' })

    expect(await screen.findByText('No alerts are firing. All rules are within their thresholds.')).toBeInTheDocument()
  })

  it('links to the alert rules page', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/alerts': [] }))
    renderWithProviders(<AlertsPage />, { role: 'DEVELOPER' })

    expect(screen.getByRole('link', { name: 'Alert rules' })).toHaveAttribute('href', '/alerts/rules')
  })
})
