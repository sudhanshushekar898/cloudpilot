import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { IncidentDetail, TimelineItem } from '../api/incidents'
import { mockApi, renderWithProviders } from '../test/utils'
import IncidentDetailPage from './IncidentDetailPage'

const incident = (status: IncidentDetail['status']): IncidentDetail => ({
  id: 'i1',
  key: 'INC-7',
  title: 'orders-db cpu 97% > 90% for 3 min',
  status,
  severity: 'CRITICAL',
  assignee: null,
  openedAt: '2026-11-15T10:00:00Z',
  acknowledgedAt: null,
  investigatingAt: null,
  resolvedAt: null,
  resolution: null,
  allAlertsResolved: false,
  alerts: [
    {
      alertId: 'a1', resourceId: 'db', resourceName: 'orders-db', severity: 'CRITICAL',
      message: 'orders-db cpu 97% > 90% for 3 min', reason: 'First alert',
      firedAt: '2026-11-15T10:00:00Z', resolvedAt: null,
    },
    {
      alertId: 'a2', resourceId: 'api', resourceName: 'checkout-api', severity: 'CRITICAL',
      message: 'checkout-api error rate 18% > 5% for 5 min', reason: 'checkout-api depends on orders-db, already in INC-7',
      firedAt: '2026-11-15T10:01:00Z', resolvedAt: null,
    },
  ],
  resources: [
    { id: 'db', name: 'orders-db', type: 'RDS', probableOrigin: true },
    { id: 'api', name: 'checkout-api', type: 'API', probableOrigin: false },
  ],
})

const timeline: TimelineItem[] = [
  { type: 'DEPLOYMENT', occurredAt: '2026-11-15T09:56:00Z', message: 'Deployed v3.2.1: Update product image CDN host',
    actor: 'ci-pipeline', resourceId: 'cat', resourceName: 'catalog-api', level: null },
  { type: 'LOG', occurredAt: '2026-11-15T10:00:30Z', message: 'too many clients already',
    actor: null, resourceId: 'db', resourceName: 'orders-db', level: 'ERROR' },
]

function renderPage(role: 'ADMIN' | 'SRE' | 'DEVELOPER', status: IncidentDetail['status'] = 'OPEN') {
  const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(
    mockApi({
      '/api/v1/incidents/i1/timeline': timeline,
      '/api/v1/incidents/i1': incident(status),
      'POST /api/v1/incidents/i1/': incident(status),
      '/api/v1/users/assignable': [{ id: 'u2', displayName: 'Ananya Admin', email: 'a@x', role: 'ADMIN' }],
    }),
  )
  renderWithProviders(<IncidentDetailPage />, { role, route: '/incidents/i1', path: '/incidents/:id' })
  return fetchSpy
}

/** Waits for the POST to `path` (mutations send it asynchronously) and returns its call args. */
async function postedTo(spy: { mock: { calls: unknown[][] } }, path: string) {
  let call: unknown[] | undefined
  await vi.waitFor(() => {
    call = spy.mock.calls.find((args) => args[0] === path && (args[1] as RequestInit | undefined)?.method === 'POST')
    expect(call).toBeDefined()
  })
  return call!
}

describe('IncidentDetailPage', () => {
  it('shows the incident, why alerts were grouped and the probable origin', async () => {
    renderPage('DEVELOPER')

    expect(await screen.findByRole('heading', { name: /INC-7/ })).toBeInTheDocument()
    expect(screen.getByText('checkout-api depends on orders-db, already in INC-7')).toBeInTheDocument()
    const origin = screen.getByText('Probable origin').closest('li')!
    expect(within(origin).getByText('orders-db')).toBeInTheDocument()
  })

  it('lets an SRE acknowledge an open incident', async () => {
    const spy = renderPage('SRE', 'OPEN')

    await userEvent.click(await screen.findByRole('button', { name: 'Acknowledge' }))

    await postedTo(spy, '/api/v1/incidents/i1/acknowledge')
  })

  it('offers the next step only', async () => {
    renderPage('SRE', 'ACKNOWLEDGED')

    expect(await screen.findByRole('button', { name: 'Start investigating' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Acknowledge' })).not.toBeInTheDocument()
  })

  it('requires a resolution note before resolving', async () => {
    const spy = renderPage('SRE', 'INVESTIGATING')

    const resolve = await screen.findByRole('button', { name: 'Resolve incident' })
    expect(resolve).toBeDisabled()
    await userEvent.type(screen.getByLabelText('What fixed it?'), 'Raised max_connections to 300')
    await userEvent.click(resolve)

    const call = await postedTo(spy, '/api/v1/incidents/i1/resolve')
    expect(JSON.parse((call[1] as RequestInit).body as string)).toEqual({ resolution: 'Raised max_connections to 300' })
  })

  it('lets an SRE assign the incident', async () => {
    const spy = renderPage('SRE')

    // The list of people loads separately; wait until it's there.
    await screen.findByRole('option', { name: 'Ananya Admin' })
    await userEvent.selectOptions(screen.getByLabelText('Owner'), 'u2')

    const call = await postedTo(spy, '/api/v1/incidents/i1/assign')
    expect(JSON.parse((call[1] as RequestInit).body as string)).toEqual({ userId: 'u2' })
  })

  it('shows developers no lifecycle controls but lets them comment', async () => {
    const spy = renderPage('DEVELOPER')

    await screen.findByRole('heading', { name: /INC-7/ })
    expect(screen.queryByRole('button', { name: 'Acknowledge' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Owner')).not.toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('Add a comment'), 'Seeing this in our service too')
    await userEvent.click(screen.getByRole('button', { name: 'Comment' }))

    const call = await postedTo(spy, '/api/v1/incidents/i1/comments')
    expect(JSON.parse((call[1] as RequestInit).body as string)).toEqual({ text: 'Seeing this in our service too' })
  })

  it('shows the timeline and filters it by type', async () => {
    const spy = renderPage('DEVELOPER')

    expect(await screen.findByText('Deployed v3.2.1: Update product image CDN host')).toBeInTheDocument()
    expect(screen.getByText('too many clients already')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Logs' }))

    await vi.waitFor(() =>
      expect(spy).toHaveBeenCalledWith('/api/v1/incidents/i1/timeline?type=LOG', expect.anything()),
    )
  })
})
