import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { AuthProvider, SESSION_KEY } from './auth/AuthContext'

function renderApp(path = '/') {
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </AuthProvider>,
  )
}

function signInAs(role: 'ADMIN' | 'SRE' | 'DEVELOPER', displayName: string) {
  sessionStorage.setItem(
    SESSION_KEY,
    JSON.stringify({
      accessToken: 't',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      user: { id: '1', email: 'x@cloudpilot.dev', displayName, role },
    }),
  )
}

beforeEach(() => {
  sessionStorage.clear()
  // ApiStatus pings the backend; keep it pending so tests don't depend on it.
  vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}))
})

describe('App', () => {
  it('sends signed-out visitors to the login page', () => {
    renderApp('/')

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('shows the dashboard with the user name and role when signed in', () => {
    signInAs('SRE', 'Rahul SRE')

    renderApp('/')

    expect(screen.getByRole('heading', { name: 'Welcome, Rahul SRE' })).toBeInTheDocument()
    expect(screen.getByText('SRE')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Checking API…')
  })

  it('signing out returns to the login page', async () => {
    signInAs('ADMIN', 'Ananya Admin')
    renderApp('/')

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})
