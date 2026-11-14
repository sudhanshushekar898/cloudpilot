import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { MetricHistory, Resource } from '../api/resources'
import { mockApi, renderWithProviders } from '../test/utils'
import ResourceDetailPage from './ResourceDetailPage'

const checkout: Resource = {
  id: 'checkout',
  name: 'checkout-api',
  type: 'API',
  region: 'ap-south-1',
  description: 'Places orders and starts payment',
  status: 'DEGRADED',
  latest: { ERROR_RATE: 18 },
  dependsOn: ['orders'],
}

const ordersDb: Resource = { ...checkout, id: 'orders', name: 'orders-db', type: 'RDS', status: 'HEALTHY', dependsOn: [] }

const history: MetricHistory = {
  resourceId: 'checkout',
  minutes: 60,
  bucketSeconds: 30,
  series: {
    ERROR_RATE: [
      { timestamp: '2026-11-15T10:00:00Z', value: 0.4 },
      { timestamp: '2026-11-15T10:00:30Z', value: 18 },
    ],
  },
}

function renderPage() {
  return renderWithProviders(<ResourceDetailPage />, {
    role: 'DEVELOPER',
    route: '/resources/checkout',
    path: '/resources/:id',
  })
}

describe('ResourceDetailPage', () => {
  it('shows the resource, its dependencies and a chart per metric', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({
        '/api/v1/resources/checkout/metrics': history,
        '/api/v1/resources/checkout': checkout,
        '/api/v1/resources': [checkout, ordersDb],
      }),
    )
    renderPage()

    expect(await screen.findByRole('heading', { name: 'checkout-api' })).toBeInTheDocument()
    expect(screen.getByText('Places orders and starts payment')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: /orders-db/ })).toHaveAttribute('href', '/resources/orders')
    expect(await screen.findAllByRole('figure')).toHaveLength(5)
    expect(screen.getAllByText('No data in this range')).toHaveLength(4)
  })

  it('reloads history when another time range is chosen', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({
        '/api/v1/resources/checkout/metrics': history,
        '/api/v1/resources/checkout': checkout,
        '/api/v1/resources': [checkout, ordersDb],
      }),
    )
    renderPage()
    await screen.findByRole('heading', { name: 'checkout-api' })

    await userEvent.click(screen.getByRole('button', { name: '6 h' }))

    await vi.waitFor(() =>
      expect(fetchSpy).toHaveBeenCalledWith('/api/v1/resources/checkout/metrics?minutes=360', expect.anything()),
    )
    expect(screen.getByRole('button', { name: '6 h' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('shows not found for an unknown resource', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({}))
    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Resource not found')
  })
})
