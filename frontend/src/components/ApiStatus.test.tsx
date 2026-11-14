import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ApiStatus from './ApiStatus'

function mockFetchOnce(response: Promise<Response>) {
  return vi.spyOn(globalThis, 'fetch').mockReturnValueOnce(response)
}

describe('ApiStatus', () => {
  it('shows a checking state before the ping returns', () => {
    mockFetchOnce(new Promise(() => {}))

    render(<ApiStatus />)

    expect(screen.getByText('Checking API…')).toBeInTheDocument()
  })

  it('shows online when the ping succeeds', async () => {
    const fetchSpy = mockFetchOnce(
      Promise.resolve(new Response(JSON.stringify({ status: 'ok' }), { status: 200 })),
    )

    render(<ApiStatus />)

    expect(await screen.findByText('API online')).toBeInTheDocument()
    expect(fetchSpy).toHaveBeenCalledWith('/api/v1/ping', expect.anything())
  })

  it('shows offline when the API returns an error status', async () => {
    mockFetchOnce(Promise.resolve(new Response('', { status: 503 })))

    render(<ApiStatus />)

    expect(await screen.findByText('API offline')).toBeInTheDocument()
  })

  it('shows offline when the network request fails', async () => {
    mockFetchOnce(Promise.reject(new TypeError('Failed to fetch')))

    render(<ApiStatus />)

    expect(await screen.findByText('API offline')).toBeInTheDocument()
  })
})
