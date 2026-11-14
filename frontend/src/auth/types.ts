export type Role = 'ADMIN' | 'SRE' | 'DEVELOPER'

export interface User {
  id: string
  email: string
  displayName: string
  role: Role
}

/** Shape of POST /api/v1/auth/login responses (tokenType omitted: always Bearer). */
export interface Session {
  accessToken: string
  expiresAt: string
  user: User
}
