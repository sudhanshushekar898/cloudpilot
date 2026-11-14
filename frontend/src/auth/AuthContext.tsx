import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { apiFetch, setAuthToken, setUnauthorizedHandler } from '../lib/api'
import type { Session, User } from './types'

export const SESSION_KEY = 'cloudpilot.session'

interface AuthContextValue {
  user: User | null
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * The session lives in sessionStorage: it survives a page refresh but is cleared when
 * the tab closes. Trade-off (see ADR-0003): JavaScript can read it, so XSS could steal
 * it; the short token lifetime and strict output escaping in React limit that risk.
 */
function loadStoredSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      sessionStorage.removeItem(SESSION_KEY)
      return null
    }
    return session
  } catch {
    return null
  }
}

function initialSession(): Session | null {
  const session = loadStoredSession()
  setAuthToken(session?.accessToken ?? null)
  return session
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(initialSession)

  const logout = useCallback(() => {
    setAuthToken(null)
    try {
      sessionStorage.removeItem(SESSION_KEY)
    } catch {
      // storage unavailable: nothing to clear
    }
    setSession(null)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const result = await apiFetch<Session>('/api/v1/auth/login', {
      method: 'POST',
      json: { email, password },
    })
    setAuthToken(result.accessToken)
    try {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(result))
    } catch {
      // storage unavailable: the session still works until the page reloads
    }
    setSession(result)
  }, [])

  // Any 401 on an authenticated call means the token expired or was revoked.
  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  const value = useMemo(() => ({ user: session?.user ?? null, login, logout }), [session, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>')
  return context
}
