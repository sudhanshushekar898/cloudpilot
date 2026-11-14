import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { RunbookDetail } from '../api/runbooks'
import { mockApi, renderWithProviders } from '../test/utils'
import RunbookPage from './RunbookPage'

const runbook: RunbookDetail = {
  id: '2',
  slug: 'payments-queue-backlog',
  title: 'Payments queue backlog',
  content: '# Payments queue backlog\n\n## Safe mitigation\n\n- Never purge the queue.\n\n<script>alert(1)</script>',
  createdBy: 'system',
  updatedAt: '2026-11-15T09:00:00Z',
}

function renderPage(role: 'ADMIN' | 'DEVELOPER') {
  const spy = vi.spyOn(globalThis, 'fetch').mockImplementation(
    mockApi({ '/api/v1/runbooks/payments-queue-backlog': runbook, 'DELETE /api/v1/runbooks/': null }),
  )
  renderWithProviders(<RunbookPage />, { role, route: '/runbooks/payments-queue-backlog', path: '/runbooks/:slug' })
  return spy
}

describe('RunbookPage', () => {
  it('renders the Markdown as formatted text without running embedded HTML', async () => {
    renderPage('DEVELOPER')

    expect(await screen.findByRole('heading', { name: 'Safe mitigation' })).toBeInTheDocument()
    expect(screen.getByRole('listitem')).toHaveTextContent('Never purge the queue.')
    expect(document.body.querySelector('script')).toBeNull()
  })

  it('lets an admin delete the runbook after confirming', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const spy = renderPage('ADMIN')

    await userEvent.click(await screen.findByRole('button', { name: 'Delete runbook' }))

    expect(spy).toHaveBeenCalledWith('/api/v1/runbooks/payments-queue-backlog', expect.objectContaining({ method: 'DELETE' }))
  })

  it('hides delete from non-admins', async () => {
    renderPage('DEVELOPER')

    await screen.findByRole('heading', { name: 'Safe mitigation' })
    expect(screen.queryByRole('button', { name: 'Delete runbook' })).not.toBeInTheDocument()
  })
})
