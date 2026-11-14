import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Resource } from '../api/resources'
import { mockApi, renderWithProviders } from '../test/utils'
import DashboardPage from './DashboardPage'

const resource = (name: string, status: Resource['status']): Resource => ({
  id: name,
  name,
  type: 'API',
  region: 'ap-south-1',
  description: name,
  status,
  latest: { CPU: 30, LATENCY_P95: 200, ERROR_RATE: 0.5, AVAILABILITY: 100 },
  dependsOn: [],
})

const simulatorIdle = { scenarios: [], active: null }

describe('DashboardPage', () => {
  it('shows a card per resource and a health summary', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({
        '/api/v1/resources': [
          resource('catalog-api', 'HEALTHY'),
          resource('checkout-api', 'DOWN'),
          resource('orders-db', 'DEGRADED'),
          resource('web-frontend', 'HEALTHY'),
        ],
        '/api/v1/simulator': simulatorIdle,
      }),
    )
    renderWithProviders(<DashboardPage />, { role: 'SRE' })

    expect(await screen.findByText('checkout-api')).toBeInTheDocument()
    expect(screen.getAllByRole('link')).toHaveLength(4)
    expect(screen.getByTestId('summary-HEALTHY')).toHaveTextContent('2')
    expect(screen.getByTestId('summary-DEGRADED')).toHaveTextContent('1')
    expect(screen.getByTestId('summary-DOWN')).toHaveTextContent('1')
  })

  it('lists unhealthy resources first', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({
        '/api/v1/resources': [resource('a-healthy', 'HEALTHY'), resource('z-down', 'DOWN')],
        '/api/v1/simulator': simulatorIdle,
      }),
    )
    renderWithProviders(<DashboardPage />, { role: 'SRE' })

    await screen.findByText('z-down')
    const names = screen.getAllByRole('link').map((link) => link.textContent)
    expect(names[0]).toContain('z-down')
  })

  it('shows an error message when resources cannot be loaded', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/simulator': simulatorIdle }))
    renderWithProviders(<DashboardPage />, { role: 'SRE' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load resources')
  })
})
