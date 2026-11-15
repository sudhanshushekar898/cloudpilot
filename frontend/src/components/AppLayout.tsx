import { useQuery } from '@tanstack/react-query'
import { NavLink, Outlet } from 'react-router-dom'
import { getAlertSummary } from '../api/alerts'
import { getIncidentSummary } from '../api/incidents'
import { useAuth } from '../auth/AuthContext'
import type { Role } from '../auth/types'
import ApiStatus from './ApiStatus'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', ready: true },
  { to: '/alerts', label: 'Alerts', ready: true },
  { to: '/incidents', label: 'Incidents', ready: true },
  { to: '/runbooks', label: 'Runbooks', ready: true },
  { to: '/reports', label: 'Reports', ready: true },
]

const ROLE_STYLES: Record<Role, string> = {
  ADMIN: 'bg-violet-100 text-violet-700',
  SRE: 'bg-sky-100 text-sky-700',
  DEVELOPER: 'bg-slate-100 text-slate-700',
}

export default function AppLayout() {
  const { user, logout } = useAuth()
  const { data: alertSummary } = useQuery({
    queryKey: ['alerts', 'summary'],
    queryFn: getAlertSummary,
    refetchInterval: 15_000,
  })
  const { data: incidentSummary } = useQuery({
    queryKey: ['incidents', 'summary'],
    queryFn: getIncidentSummary,
    refetchInterval: 15_000,
  })
  const badges: Record<string, { count: number; label: string }> = {
    '/alerts': { count: alertSummary?.firing ?? 0, label: 'alerts firing' },
    '/incidents': { count: incidentSummary?.active ?? 0, label: 'active incidents' },
  }

  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <div className="flex items-center gap-2">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-500 font-bold text-white">C</span>
            <span className="text-lg font-semibold">CloudPilot</span>
          </div>
          <div className="flex items-center gap-3">
            <ApiStatus />
            {user && (
              <>
                <span className="hidden text-sm text-slate-700 sm:inline">{user.displayName}</span>
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${ROLE_STYLES[user.role]}`}>
                  {user.role}
                </span>
                <button
                  onClick={logout}
                  className="rounded-md border border-slate-300 px-3 py-1 text-sm hover:bg-slate-50"
                >
                  Sign out
                </button>
              </>
            )}
          </div>
        </div>
        <nav className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-4" aria-label="Main">
          {NAV_ITEMS.map((item) =>
            item.ready ? (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                className={({ isActive }) =>
                  `inline-flex items-center gap-1.5 whitespace-nowrap border-b-2 px-3 py-2 text-sm ${
                    isActive ? 'border-brand-500 font-medium text-brand-600' : 'border-transparent text-slate-600'
                  }`
                }
              >
                {item.label}
                {(badges[item.to]?.count ?? 0) > 0 && (
                  <span
                    aria-label={`${badges[item.to]!.count} ${badges[item.to]!.label}`}
                    className="rounded-full bg-rose-600 px-1.5 text-xs font-semibold leading-5 text-white"
                  >
                    {badges[item.to]!.count}
                  </span>
                )}
              </NavLink>
            ) : (
              <span
                key={item.to}
                className="cursor-not-allowed whitespace-nowrap px-3 py-2 text-sm text-slate-400"
                title="Coming in a later milestone"
              >
                {item.label}
              </span>
            ),
          )}
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
