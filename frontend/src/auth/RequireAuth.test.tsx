import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider, SESSION_KEY } from './AuthContext'
import RequireAuth from './RequireAuth'

function LoginProbe() {
  const location = useLocation()
  return <p>Login page, from {(location.state as { from?: string } | null)?.from}</p>
}

function renderAt(path: string) {
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/login" element={<LoginProbe />} />
          <Route element={<RequireAuth />}>
            <Route path="/incidents" element={<p>Secret incidents</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

beforeEach(() => sessionStorage.clear())

describe('RequireAuth', () => {
  it('redirects signed-out users to login and remembers where they were going', () => {
    renderAt('/incidents')

    expect(screen.getByText('Login page, from /incidents')).toBeInTheDocument()
    expect(screen.queryByText('Secret incidents')).not.toBeInTheDocument()
  })

  it('renders the page for signed-in users', () => {
    sessionStorage.setItem(
      SESSION_KEY,
      JSON.stringify({
        accessToken: 't',
        expiresAt: new Date(Date.now() + 60_000).toISOString(),
        user: { id: '1', email: 'dev@cloudpilot.dev', displayName: 'Dev', role: 'DEVELOPER' },
      }),
    )

    renderAt('/incidents')

    expect(screen.getByText('Secret incidents')).toBeInTheDocument()
  })
})
