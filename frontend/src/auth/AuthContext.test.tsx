import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, SESSION_KEY, useAuth } from './AuthContext'
import type { Session } from './types'

const session = (expiresInMs: number): Session => ({
  accessToken: 'token-123',
  expiresAt: new Date(Date.now() + expiresInMs).toISOString(),
  user: { id: 'u1', email: 'sre@cloudpilot.dev', displayName: 'Rahul SRE', role: 'SRE' },
})

function Probe() {
  const { user, login, logout } = useAuth()
  return (
    <div>
      <p>{user ? `Signed in as ${user.displayName} (${user.role})` : 'Signed out'}</p>
      <button onClick={() => login('sre@cloudpilot.dev', 'pw')}>login</button>
      <button onClick={logout}>logout</button>
    </div>
  )
}

beforeEach(() => sessionStorage.clear())
afterEach(() => sessionStorage.clear())

describe('AuthProvider', () => {
  it('starts signed out when nothing is stored', () => {
    render(<AuthProvider><Probe /></AuthProvider>)

    expect(screen.getByText('Signed out')).toBeInTheDocument()
  })

  it('logs in, stores the session and uses the token for later requests', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify(session(3_600_000)), { status: 200 }),
    )
    render(<AuthProvider><Probe /></AuthProvider>)

    await userEvent.click(screen.getByText('login'))

    expect(await screen.findByText('Signed in as Rahul SRE (SRE)')).toBeInTheDocument()
    expect(fetchSpy).toHaveBeenCalledWith('/api/v1/auth/login', expect.objectContaining({ method: 'POST' }))
    expect(JSON.parse(sessionStorage.getItem(SESSION_KEY)!).accessToken).toBe('token-123')
  })

  it('restores a stored session that has not expired', () => {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session(60_000)))

    render(<AuthProvider><Probe /></AuthProvider>)

    expect(screen.getByText('Signed in as Rahul SRE (SRE)')).toBeInTheDocument()
  })

  it('ignores and removes an expired session', () => {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session(-1_000)))

    render(<AuthProvider><Probe /></AuthProvider>)

    expect(screen.getByText('Signed out')).toBeInTheDocument()
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('logout clears the session', async () => {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session(60_000)))
    render(<AuthProvider><Probe /></AuthProvider>)

    await act(() => userEvent.click(screen.getByText('logout')))

    expect(screen.getByText('Signed out')).toBeInTheDocument()
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull()
  })
})
