import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { mockApi, renderWithProviders } from '../test/utils'
import AppLayout from './AppLayout'

describe('AppLayout', () => {
  it('shows the number of firing alerts next to Alerts', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({ '/api/v1/ping': { status: 'ok' }, '/api/v1/alerts/summary': { firing: 3, critical: 2, warning: 1 } }),
    )
    renderWithProviders(<AppLayout />, { role: 'SRE' })

    expect(await screen.findByLabelText('3 alerts firing')).toHaveTextContent('3')
    expect(screen.getByRole('link', { name: /Alerts/ })).toHaveAttribute('href', '/alerts')
  })

  it('shows no badge when nothing is firing', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({ '/api/v1/ping': { status: 'ok' }, '/api/v1/alerts/summary': { firing: 0, critical: 0, warning: 0 } }),
    )
    renderWithProviders(<AppLayout />, { role: 'SRE' })

    await vi.waitFor(() => expect(fetchSpy).toHaveBeenCalledWith('/api/v1/alerts/summary', expect.anything()))
    expect(screen.queryByLabelText(/alerts firing/)).not.toBeInTheDocument()
  })
})
