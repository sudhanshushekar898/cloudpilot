import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { Analysis } from '../api/analysis'
import { jsonResponse, mockApi, renderWithProviders } from '../test/utils'
import AnalysisPanel from './AnalysisPanel'

const analysis: Analysis = {
  id: 'an1',
  incidentId: 'i1',
  question: 'Why did checkout fail?',
  summary: 'orders-db ran out of connections; checkout-api timed out waiting.',
  observedEvidence: [{ text: 'orders-db logged too many clients', citations: ['L1'] }],
  likelyCause: { text: 'orders-db connection pool exhausted', citations: ['A1', 'L1'] },
  confidence: 0.72,
  confidenceReason: 'Alerts, logs and timing agree.',
  verification: [{ text: 'Check active connections against the limit.', citations: ['R1'] }],
  remediation: [
    { text: 'Restart one checkout-api pod at a time.', citations: ['R2'], requiresApproval: false },
    { text: 'Terminate sessions idle in transaction for 5 minutes.', citations: ['R2'], requiresApproval: true },
  ],
  warnings: ['1 remediation step changes or deletes something and must be approved by a person before anyone runs it.'],
  evidence: [
    { id: 'A1', kind: 'ALERT', resourceName: 'orders-db', occurredAt: '2026-11-15T10:00:00Z', text: 'orders-db cpu 97% > 90% for 3 min', source: 'First alert' },
    { id: 'L1', kind: 'LOG', resourceName: 'orders-db', occurredAt: '2026-11-15T10:00:10Z', text: 'too many clients already', source: 'WARN' },
    { id: 'R1', kind: 'RUNBOOK', resourceName: null, occurredAt: null, text: 'Check connections.', source: 'Orders DB > Verify' },
    { id: 'R2', kind: 'RUNBOOK', resourceName: null, occurredAt: null, text: 'Restart pods.', source: 'Orders DB > Safe mitigation' },
  ],
  analyzer: 'claude',
  model: 'claude-opus-5-5',
  requestedBy: 'sre@cloudpilot.dev',
  createdAt: '2026-11-15T10:06:00Z',
}

describe('AnalysisPanel', () => {
  it('invites a question when there is no analysis yet', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({}))   // GET -> 404
    renderWithProviders(<AnalysisPanel incidentId="i1" />, { role: 'DEVELOPER' })

    expect(await screen.findByText(/No analysis yet/)).toBeInTheDocument()
    expect(screen.getByLabelText('Question')).toHaveValue('What is the most likely root cause, and what should we check first?')
  })

  it('asks the question and shows the cited, structured answer', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      if ((init as RequestInit | undefined)?.method === 'POST') return Promise.resolve(jsonResponse(analysis, 201))
      return mockApi({})(input, init)
    })
    renderWithProviders(<AnalysisPanel incidentId="i1" />, { role: 'DEVELOPER' })

    const question = await screen.findByLabelText('Question')
    await userEvent.clear(question)
    await userEvent.type(question, 'Why did checkout fail?')
    await userEvent.click(screen.getByRole('button', { name: 'Analyze' }))

    expect(await screen.findByText('orders-db connection pool exhausted')).toBeInTheDocument()
    const post = spy.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'POST')!
    expect(JSON.parse((post[1] as RequestInit).body as string)).toEqual({ question: 'Why did checkout fail?' })

    expect(screen.getByText('72%')).toBeInTheDocument()
    expect(screen.getByText('High confidence')).toBeInTheDocument()
    expect(screen.getByText(/Claude · claude-opus-5-5/)).toBeInTheDocument()
  })

  it('links every citation to its evidence item', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/incidents/i1/analysis': analysis }))
    renderWithProviders(<AnalysisPanel incidentId="i1" />, { role: 'SRE' })

    const cause = (await screen.findByText('orders-db connection pool exhausted')).closest('section')!
    const chip = within(cause).getByRole('link', { name: 'A1' })
    expect(chip).toHaveAttribute('href', '#evidence-A1')
    expect(chip).toHaveAttribute('title', 'orders-db cpu 97% > 90% for 3 min')
    expect(document.getElementById('evidence-A1')).not.toBeNull()
  })

  it('marks risky remediation steps and shows the safety notice', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/incidents/i1/analysis': analysis }))
    renderWithProviders(<AnalysisPanel incidentId="i1" />, { role: 'SRE' })

    const risky = (await screen.findByText('Terminate sessions idle in transaction for 5 minutes.')).closest('li')!
    expect(within(risky).getByText('Needs approval')).toBeInTheDocument()
    const safe = screen.getByText('Restart one checkout-api pod at a time.').closest('li')!
    expect(within(safe).queryByText('Needs approval')).not.toBeInTheDocument()
    expect(screen.getByText(/CloudPilot never runs these steps/)).toBeInTheDocument()
    expect(screen.getByRole('note')).toHaveTextContent('must be approved by a person')
  })

  it('labels rule-based answers clearly', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({ '/api/v1/incidents/i1/analysis': { ...analysis, analyzer: 'rule-based', model: null, confidence: 0.35 } }),
    )
    renderWithProviders(<AnalysisPanel incidentId="i1" />, { role: 'SRE' })

    expect(await screen.findByText(/Rule-based/)).toBeInTheDocument()
    expect(screen.getByText('Low confidence')).toBeInTheDocument()
  })
})
