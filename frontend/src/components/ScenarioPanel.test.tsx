import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { SimulatorState } from '../api/resources'
import { mockApi, renderWithProviders } from '../test/utils'
import ScenarioPanel from './ScenarioPanel'

const catalogue: SimulatorState['scenarios'] = [
  {
    type: 'BAD_DEPLOYMENT',
    title: 'Bad checkout-api deployment',
    description: 'A new release throws errors.',
    durationMinutes: 10,
    affectedResources: ['checkout-api', 'web-frontend'],
  },
  {
    type: 'QUEUE_BACKLOG',
    title: 'Payments queue backlog',
    description: 'Messages pile up.',
    durationMinutes: 15,
    affectedResources: ['payments-queue'],
  },
]

const idle: SimulatorState = { scenarios: catalogue, active: null }
const running: SimulatorState = {
  scenarios: catalogue,
  active: {
    type: 'BAD_DEPLOYMENT',
    startedAt: new Date().toISOString(),
    endsAt: new Date(Date.now() + 600_000).toISOString(),
    startedBy: 'admin@cloudpilot.dev',
  },
}

describe('ScenarioPanel', () => {
  it('lets an admin start a scenario', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({ '/api/v1/simulator': idle, 'POST /api/v1/simulator/scenarios/': running.active }),
    )
    renderWithProviders(<ScenarioPanel />, { role: 'ADMIN' })

    await userEvent.click(await screen.findByRole('button', { name: 'Start Bad checkout-api deployment' }))

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/v1/simulator/scenarios/BAD_DEPLOYMENT/start',
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('shows the running scenario and lets an admin stop it', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({ '/api/v1/simulator': running, 'POST /api/v1/simulator/stop': null }),
    )
    renderWithProviders(<ScenarioPanel />, { role: 'ADMIN' })

    expect(await screen.findByText(/Bad checkout-api deployment is running/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Stop scenario' }))

    expect(fetchSpy).toHaveBeenCalledWith('/api/v1/simulator/stop', expect.objectContaining({ method: 'POST' }))
  })

  it('shows developers the running scenario without controls', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/simulator': running }))
    renderWithProviders(<ScenarioPanel />, { role: 'DEVELOPER' })

    expect(await screen.findByText(/Bad checkout-api deployment is running/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Start/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Stop scenario' })).not.toBeInTheDocument()
  })

  it('renders nothing for non-admins when no scenario is running', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/simulator': idle }))
    const { container } = renderWithProviders(<ScenarioPanel />, { role: 'SRE' })

    await vi.waitFor(() => expect(fetchSpy).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })
})
