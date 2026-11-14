import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { mockApi, renderWithProviders } from '../test/utils'
import AppLayout from './AppLayout'

function mockSummaries(firing: number, active: number) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(
    mockApi({
      '/api/v1/ping': { status: 'ok' },
      '/api/v1/alerts/summary': { firing, critical: firing, warning: 0 },
      '/api/v1/incidents/summary': { active },
    }),
  )
}

describe('AppLayout', () => {
  it('shows the number of firing alerts next to Alerts', async () => {
    mockSummaries(3, 0)
    renderWithProviders(<AppLayout />, { role: 'SRE' })

    expect(await screen.findByLabelText('3 alerts firing')).toHaveTextContent('3')
    expect(screen.getByRole('link', { name: /Alerts/ })).toHaveAttribute('href', '/alerts')
  })

  it('shows the number of active incidents next to Incidents', async () => {
    mockSummaries(0, 2)
    renderWithProviders(<AppLayout />, { role: 'SRE' })

    expect(await screen.findByLabelText('2 active incidents')).toHaveTextContent('2')
    expect(screen.getByRole('link', { name: /Incidents/ })).toHaveAttribute('href', '/incidents')
  })

  it('shows no badges when all is quiet', async () => {
    const fetchSpy = mockSummaries(0, 0)
    renderWithProviders(<AppLayout />, { role: 'SRE' })

    await vi.waitFor(() => expect(fetchSpy).toHaveBeenCalledWith('/api/v1/incidents/summary', expect.anything()))
    expect(screen.queryByLabelText(/alerts firing|active incidents/)).not.toBeInTheDocument()
  })
})
