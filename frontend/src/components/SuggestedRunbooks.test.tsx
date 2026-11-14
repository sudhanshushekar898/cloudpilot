import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { RunbookHit } from '../api/runbooks'
import { mockApi, renderWithProviders } from '../test/utils'
import SuggestedRunbooks from './SuggestedRunbooks'

const hit: RunbookHit = {
  chunkId: 'c1', runbookId: '1', runbookSlug: 'orders-db-connection-exhaustion',
  runbookTitle: 'Orders DB connection exhaustion', heading: 'Orders DB connection exhaustion > Verify',
  content: '...', snippet: 'Check active [[connections]] against the limit', score: 0.5,
}

describe('SuggestedRunbooks', () => {
  it('shows matching runbook sections for the incident', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/runbooks/suggestions': [hit] }))
    renderWithProviders(<SuggestedRunbooks incidentId="i1" />, { role: 'SRE' })

    expect(await screen.findByText('Orders DB connection exhaustion > Verify')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Open runbook/ })).toHaveAttribute('href', '/runbooks/orders-db-connection-exhaustion')
    expect(spy).toHaveBeenCalledWith('/api/v1/runbooks/suggestions?incidentId=i1', expect.anything())
  })

  it('says so when no runbook matches', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/runbooks/suggestions': [] }))
    renderWithProviders(<SuggestedRunbooks incidentId="i1" />, { role: 'SRE' })

    expect(await screen.findByText(/No runbook matches this incident yet/)).toBeInTheDocument()
  })
})
