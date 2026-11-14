import { useAuth } from '../auth/AuthContext'

const ROLE_HINTS = {
  ADMIN: 'You can manage users, alert rules and runbooks, and handle incidents.',
  SRE: 'You can acknowledge, investigate and resolve incidents.',
  DEVELOPER: 'You can view dashboards and incidents and ask the AI assistant.',
} as const

export default function DashboardPage() {
  const { user } = useAuth()
  if (!user) return null

  return (
    <section>
      <h1 className="text-2xl font-semibold">Welcome, {user.displayName}</h1>
      <p className="mt-2 text-slate-600">{ROLE_HINTS[user.role]}</p>
      <div className="mt-6 rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center text-slate-500">
        Resource health cards and metric charts arrive in M3.
      </div>
    </section>
  )
}
