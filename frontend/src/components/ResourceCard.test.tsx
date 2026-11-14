import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Resource } from '../api/resources'
import { renderWithProviders } from '../test/utils'
import ResourceCard from './ResourceCard'

const ordersDb: Resource = {
  id: '00000000-0000-0000-0000-000000000004',
  name: 'orders-db',
  type: 'RDS',
  region: 'ap-south-1',
  description: 'PostgreSQL database for orders',
  status: 'DEGRADED',
  latest: { CPU: 97.2, LATENCY_P95: 2400, ERROR_RATE: 9.1, AVAILABILITY: 100 },
  dependsOn: [],
}

describe('ResourceCard', () => {
  it('shows name, type, status and formatted key metrics', () => {
    renderWithProviders(<ResourceCard resource={ordersDb} />)

    expect(screen.getByText('orders-db')).toBeInTheDocument()
    expect(screen.getByText('RDS')).toBeInTheDocument()
    expect(screen.getByText('Degraded')).toBeInTheDocument()
    expect(screen.getByText('97.2%')).toBeInTheDocument()
    expect(screen.getByText('2.4 s')).toBeInTheDocument()
    expect(screen.getByText('9.1%')).toBeInTheDocument()
  })

  it('links to the resource detail page', () => {
    renderWithProviders(<ResourceCard resource={ordersDb} />)

    expect(screen.getByRole('link', { name: /orders-db/ })).toHaveAttribute(
      'href',
      '/resources/00000000-0000-0000-0000-000000000004',
    )
  })

  it('shows dashes when there is no data yet', () => {
    renderWithProviders(<ResourceCard resource={{ ...ordersDb, status: 'UNKNOWN', latest: {} }} />)

    expect(screen.getByText('No data')).toBeInTheDocument()
    expect(screen.getAllByText('—')).toHaveLength(4)
  })
})
