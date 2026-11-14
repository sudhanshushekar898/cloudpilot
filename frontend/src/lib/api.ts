/**
 * Thin wrapper around fetch for the CloudPilot API:
 * - attaches the JWT as a Bearer token
 * - sends and parses JSON
 * - turns error responses (RFC 9457 problem details) into ApiError
 * - reports 401s on authenticated calls so the app can log the user out
 */

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

let authToken: string | null = null
let onUnauthorized: (() => void) | null = null

export function setAuthToken(token: string | null) {
  authToken = token
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

type ApiOptions = Omit<RequestInit, 'body'> & { json?: unknown }

export async function apiFetch<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const { json, headers: extraHeaders, ...init } = options
  const headers = new Headers(extraHeaders)
  headers.set('Accept', 'application/json')
  if (json !== undefined) headers.set('Content-Type', 'application/json')
  if (authToken) headers.set('Authorization', `Bearer ${authToken}`)

  const response = await fetch(path, {
    ...init,
    headers,
    body: json !== undefined ? JSON.stringify(json) : undefined,
  })

  if (!response.ok) {
    if (response.status === 401 && authToken) onUnauthorized?.()
    throw new ApiError(response.status, await errorMessage(response))
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const problem = (await response.json()) as { detail?: string; title?: string }
    return problem.detail ?? problem.title ?? `Request failed (${response.status})`
  } catch {
    return `Request failed (${response.status})`
  }
}
