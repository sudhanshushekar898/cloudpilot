import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Postmortem } from '../api/postmortems'
import { jsonResponse, mockApi, renderWithProviders } from '../test/utils'
import PostmortemPanel from './PostmortemPanel'

const draft: Postmortem = {
  id: 'p1',
  incidentId: 'i1',
  incidentKey: 'INC-7',
  incidentTitle: 'checkout-api errors',
  status: 'DRAFT',
  summary: 'Checkout failed for 18 minutes.',
  impact: 'About 38% of checkouts failed.',
  rootCause: 'Release v2.14.0 broke payment intent creation.',
  resolution: 'Rolled back to v2.13.4.',
  prevention: ['Add a contract test', 'Canary releases for checkout-api'],
  timeline: [{ at: '2026-11-15T10:00:00Z', text: 'Alert fired: checkout-api error rate 38%' }],
  draftedBy: 'template',
  model: null,
  createdBy: 'sre@cloudpilot.dev',
  createdAt: '2026-11-15T10:30:00Z',
  updatedBy: 'sre@cloudpilot.dev',
  updatedAt: '2026-11-15T10:30:00Z',
  approvedBy: null,
  approvedAt: null,
}

function lastCall(spy: { mock: { calls: unknown[][] } }, method: string) {
  return spy.mock.calls.filter((c) => (c[1] as RequestInit | undefined)?.method === method).at(-1)
}

describe('PostmortemPanel', () => {
  it('explains it is only available once the incident is resolved', () => {
    renderWithProviders(<PostmortemPanel incidentId="i1" resolved={false} />, { role: 'SRE' })

    expect(screen.getByText(/after the incident is resolved/)).toBeInTheDocument()
  })

  it('lets an SRE draft a postmortem', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      if ((init as RequestInit | undefined)?.method === 'POST') return Promise.resolve(jsonResponse(draft, 201))
      return mockApi({})(input, init)   // GET -> 404, none yet
    })
    renderWithProviders(<PostmortemPanel incidentId="i1" resolved />, { role: 'SRE' })

    await userEvent.click(await screen.findByRole('button', { name: 'Draft postmortem' }))

    expect(lastCall(spy, 'POST')![0]).toBe('/api/v1/incidents/i1/postmortem/generate')
    expect(await screen.findByLabelText('Root cause')).toHaveValue('Release v2.14.0 broke payment intent creation.')
  })

  it('saves edits with prevention actions one per line', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      if ((init as RequestInit | undefined)?.method === 'PUT') return Promise.resolve(jsonResponse(draft))
      return mockApi({ '/api/v1/incidents/i1/postmortem': draft })(input, init)
    })
    renderWithProviders(<PostmortemPanel incidentId="i1" resolved />, { role: 'SRE' })

    const prevention = await screen.findByLabelText('Prevention actions (one per line)')
    await userEvent.clear(prevention)
    await userEvent.type(prevention, 'Contract test{Enter}{Enter}Canary releases')
    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }))

    await vi.waitFor(() => expect(lastCall(spy, 'PUT')).toBeDefined())
    expect(JSON.parse((lastCall(spy, 'PUT')![1] as RequestInit).body as string).prevention)
      .toEqual(['Contract test', 'Canary releases'])
  })

  it('approves after confirmation and then shows it read-only', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const approved = { ...draft, status: 'APPROVED' as const, approvedBy: 'lead@cloudpilot.dev', approvedAt: '2026-11-15T11:00:00Z' }
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      if ((init as RequestInit | undefined)?.method === 'POST') return Promise.resolve(jsonResponse(approved))
      return mockApi({ '/api/v1/incidents/i1/postmortem': draft })(input, init)
    })
    renderWithProviders(<PostmortemPanel incidentId="i1" resolved />, { role: 'ADMIN' })

    await userEvent.click(await screen.findByRole('button', { name: 'Approve' }))

    expect(await screen.findByText(/Approved by lead@cloudpilot.dev/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Root cause')).not.toBeInTheDocument()
    expect(screen.getByText('Release v2.14.0 broke payment intent creation.')).toBeInTheDocument()
  })

  it('shows developers the draft read-only', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/incidents/i1/postmortem': draft }))
    renderWithProviders(<PostmortemPanel incidentId="i1" resolved />, { role: 'DEVELOPER' })

    expect(await screen.findByText('Release v2.14.0 broke payment intent creation.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.getByText('Draft')).toBeInTheDocument()
    expect(screen.getByText('Alert fired: checkout-api error rate 38%')).toBeInTheDocument()
  })
})
