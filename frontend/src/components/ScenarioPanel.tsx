import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getSimulator, startScenario, stopScenario, type ScenarioType } from '../api/resources'
import { useAuth } from '../auth/AuthContext'

/**
 * Demo controls. Everyone sees when a simulated failure is running (so nobody mistakes it
 * for a real outage); only admins can start or stop one. The backend enforces the same rule.
 */
export default function ScenarioPanel() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const queryClient = useQueryClient()

  const { data } = useQuery({ queryKey: ['simulator'], queryFn: getSimulator, refetchInterval: 10_000 })

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['simulator'] })
    void queryClient.invalidateQueries({ queryKey: ['resources'] })
  }
  const start = useMutation({ mutationFn: (type: ScenarioType) => startScenario(type), onSuccess: refresh })
  const stop = useMutation({ mutationFn: stopScenario, onSuccess: refresh })

  if (!data) return null
  const active = data.active
  const activeInfo = active ? data.scenarios.find((s) => s.type === active.type) : undefined

  if (!active && !isAdmin) return null

  return (
    <section className="mb-6 rounded-xl border border-slate-200 bg-white p-4" aria-label="Failure simulator">
      {active ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">
          <p>
            <span className="font-medium">Simulation:</span> {activeInfo?.title ?? active.type} is running until{' '}
            {new Date(active.endsAt).toLocaleTimeString()}.
          </p>
          {isAdmin && (
            <button
              onClick={() => stop.mutate()}
              disabled={stop.isPending}
              className="rounded-md border border-amber-300 bg-white px-3 py-1 hover:bg-amber-100 disabled:opacity-60"
            >
              Stop scenario
            </button>
          )}
        </div>
      ) : (
        <p className="text-sm text-slate-600">Start a simulated failure to watch CloudPilot detect it.</p>
      )}

      {isAdmin && (
        <ul className="mt-3 grid gap-2 sm:grid-cols-2">
          {data.scenarios.map((scenario) => (
            <li key={scenario.type} className="flex items-start justify-between gap-3 rounded-lg border border-slate-200 p-3">
              <div className="min-w-0">
                <p className="text-sm font-medium">{scenario.title}</p>
                <p className="text-xs text-slate-500">
                  {scenario.durationMinutes} min · affects {scenario.affectedResources.join(', ')}
                </p>
              </div>
              <button
                onClick={() => start.mutate(scenario.type)}
                disabled={start.isPending}
                aria-label={`Start ${scenario.title}`}
                className="shrink-0 rounded-md bg-brand-500 px-3 py-1 text-sm text-white hover:bg-brand-600 disabled:opacity-60"
              >
                Start
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
