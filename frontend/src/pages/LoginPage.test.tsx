import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import LoginPage from './LoginPage'

function renderLogin(from?: string) {
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[{ pathname: '/login', state: from ? { from } : undefined }]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>Dashboard page</p>} />
          <Route path="/incidents" element={<p>Incidents page</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

const okLogin = () =>
  new Response(
    JSON.stringify({
      accessToken: 't',
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
      user: { id: '1', email: 'sre@cloudpilot.dev', displayName: 'Rahul SRE', role: 'SRE' },
    }),
    { status: 200 },
  )

beforeEach(() => sessionStorage.clear())

describe('LoginPage', () => {
  it('signs in and goes to the dashboard', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(okLogin())
    renderLogin()

    await userEvent.type(screen.getByLabelText('Email'), 'sre@cloudpilot.dev')
    await userEvent.type(screen.getByLabelText('Password'), 'secret')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Dashboard page')).toBeInTheDocument()
    const body = JSON.parse(fetchSpy.mock.calls[0]![1]!.body as string)
    expect(body).toEqual({ email: 'sre@cloudpilot.dev', password: 'secret' })
  })

  it('returns to the page the user originally asked for', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(okLogin())
    renderLogin('/incidents')

    await userEvent.type(screen.getByLabelText('Email'), 'sre@cloudpilot.dev')
    await userEvent.type(screen.getByLabelText('Password'), 'secret')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Incidents page')).toBeInTheDocument()
  })

  it('shows the server error message when sign-in fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      new Response(JSON.stringify({ detail: 'Invalid email or password' }), { status: 401 }),
    )
    renderLogin()

    await userEvent.type(screen.getByLabelText('Email'), 'sre@cloudpilot.dev')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(screen.queryByText('Dashboard page')).not.toBeInTheDocument()
  })

  it('fills the form from a demo account button', async () => {
    renderLogin()

    await userEvent.click(screen.getByRole('button', { name: /Admin/ }))

    expect(screen.getByLabelText('Email')).toHaveValue('admin@cloudpilot.dev')
    expect(screen.getByLabelText('Password')).not.toHaveValue('')
  })
})
