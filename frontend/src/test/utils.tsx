import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider, SESSION_KEY } from '../auth/AuthContext'
import type { Role } from '../auth/types'

interface Options {
  /** Signed-in role; omit to render signed out. */
  role?: Role
  /** URL to start at. */
  route?: string
  /** Route pattern for the element, e.g. "/resources/:id". */
  path?: string
}

/** Renders with the same providers as the real app: React Query, auth and router. */
export function renderWithProviders(ui: ReactElement, { role, route = '/', path = '/' }: Options = {}) {
  sessionStorage.clear()
  if (role) {
    sessionStorage.setItem(
      SESSION_KEY,
      JSON.stringify({
        accessToken: 'test-token',
        expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
        user: { id: 'u1', email: `${role.toLowerCase()}@cloudpilot.dev`, displayName: `Test ${role}`, role },
      }),
    )
  }
  // No retries in tests: a failed request should fail the test straight away.
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })

  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={[route]}>
          <Routes>
            <Route path={path} element={ui} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
}

export function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** Routes mocked fetch calls by URL prefix, so a page can make several requests. */
export function mockApi(routes: Record<string, unknown>) {
  return (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString()
    const method = init?.method ?? 'GET'
    const key = Object.keys(routes)
      .sort((a, b) => b.length - a.length)
      .find((candidate) => {
        const [candidateMethod, candidatePath] = candidate.includes(' ') ? candidate.split(' ') : ['GET', candidate]
        return candidateMethod === method && url.startsWith(candidatePath!)
      })
    if (!key) return Promise.resolve(new Response('not mocked: ' + method + ' ' + url, { status: 404 }))
    const body = routes[key]
    return Promise.resolve(body === null ? new Response(null, { status: 204 }) : jsonResponse(body))
  }
}
