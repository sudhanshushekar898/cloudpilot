import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { RunbookHit, RunbookSummary } from '../api/runbooks'
import { mockApi, renderWithProviders } from '../test/utils'
import RunbooksPage from './RunbooksPage'

const runbooks: RunbookSummary[] = [
  { id: '1', slug: 'orders-db-connection-exhaustion', title: 'Orders DB connection exhaustion', chunkCount: 5,
    createdBy: 'system', updatedAt: '2026-11-15T09:00:00Z' },
  { id: '2', slug: 'payments-queue-backlog', title: 'Payments queue backlog', chunkCount: 4,
    createdBy: 'system', updatedAt: '2026-11-15T09:00:00Z' },
]

const hit: RunbookHit = {
  chunkId: 'c1', runbookId: '1', runbookSlug: 'orders-db-connection-exhaustion',
  runbookTitle: 'Orders DB connection exhaustion', heading: 'Orders DB connection exhaustion > Symptoms',
  content: '...', snippet: 'orders-db logs [[too]] many [[clients]] already', score: 0.4,
}

/** Waits for the POST (mutations send it asynchronously) and returns its JSON body. */
async function postBody(spy: { mock: { calls: unknown[][] } }) {
  let call: unknown[] | undefined
  await vi.waitFor(() => {
    call = spy.mock.calls.find((args) => (args[1] as RequestInit | undefined)?.method === 'POST')
    expect(call).toBeDefined()
  })
  return JSON.parse((call![1] as RequestInit).body as string)
}

describe('RunbooksPage', () => {
  it('lists runbooks with links', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/runbooks': runbooks }))
    renderWithProviders(<RunbooksPage />, { role: 'DEVELOPER' })

    expect(await screen.findByRole('link', { name: 'Orders DB connection exhaustion' }))
      .toHaveAttribute('href', '/runbooks/orders-db-connection-exhaustion')
    expect(screen.getByText(/^5 sections · updated/)).toBeInTheDocument()
  })

  it('searches and shows highlighted sections', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({ '/api/v1/runbooks/search': [hit], '/api/v1/runbooks': runbooks }),
    )
    renderWithProviders(<RunbooksPage />, { role: 'DEVELOPER' })

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search runbooks' }), 'too many clients{Enter}')

    expect(await screen.findByText('Orders DB connection exhaustion > Symptoms')).toBeInTheDocument()
    expect(screen.getByText('clients').tagName).toBe('MARK')
    expect(spy).toHaveBeenCalledWith('/api/v1/runbooks/search?q=too%20many%20clients', expect.anything())
  })

  it('lets an admin add a runbook', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({ '/api/v1/runbooks': runbooks, 'POST /api/v1/runbooks': { ...runbooks[0], slug: 'new' } }),
    )
    renderWithProviders(<RunbooksPage />, { role: 'ADMIN' })

    await userEvent.click(await screen.findByRole('button', { name: 'Add runbook' }))
    await userEvent.type(screen.getByLabelText('Title'), 'Redis eviction storm')
    await userEvent.type(screen.getByLabelText('Markdown'), '## Symptoms{Enter}{Enter}Keys evicted.')
    await userEvent.click(screen.getByRole('button', { name: 'Save runbook' }))

    expect(await postBody(spy)).toEqual({ title: 'Redis eviction storm', content: '## Symptoms\n\nKeys evicted.' })
  })

  it('hides the add button from non-admins', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/runbooks': runbooks }))
    renderWithProviders(<RunbooksPage />, { role: 'SRE' })

    await screen.findByText('Payments queue backlog')
    expect(screen.queryByRole('button', { name: 'Add runbook' })).not.toBeInTheDocument()
  })
})
