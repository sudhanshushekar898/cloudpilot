import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, setAuthToken, setUnauthorizedHandler } from './api'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

afterEach(() => {
  setAuthToken(null)
  setUnauthorizedHandler(null)
})

describe('apiFetch', () => {
  it('returns parsed JSON for a successful response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({ status: 'ok' }))

    await expect(apiFetch('/api/v1/ping')).resolves.toEqual({ status: 'ok' })
  })

  it('adds the bearer token when one is set', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({}))
    setAuthToken('abc.def.ghi')

    await apiFetch('/api/v1/auth/me')

    const headers = new Headers(fetchSpy.mock.calls[0]![1]!.headers)
    expect(headers.get('Authorization')).toBe('Bearer abc.def.ghi')
  })

  it('sends a JSON body with the right content type', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse({}))

    await apiFetch('/api/v1/auth/login', { method: 'POST', json: { email: 'a@b.c' } })

    const init = fetchSpy.mock.calls[0]![1]!
    expect(init.body).toBe('{"email":"a@b.c"}')
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  })

  it('throws ApiError with the problem detail message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse({ title: 'Authentication failed', detail: 'Invalid email or password' }, 401),
    )

    await expect(apiFetch('/api/v1/auth/login', { method: 'POST' })).rejects.toEqual(
      new ApiError(401, 'Invalid email or password'),
    )
  })

  it('calls the unauthorized handler when an authenticated request gets 401', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response('', { status: 401 }))
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    setAuthToken('expired-token')

    await expect(apiFetch('/api/v1/auth/me')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })
})
