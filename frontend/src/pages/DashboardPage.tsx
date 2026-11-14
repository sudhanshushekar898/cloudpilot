import { useQuery } from '@tanstack/react-query'
import { listResources, type Resource, type ResourceStatus } from '../api/resources'
import { useAuth } from '../auth/AuthContext'
import ResourceCard from '../components/ResourceCard'
import ScenarioPanel from '../components/ScenarioPanel'

/** Worst first: what needs attention should be at the top. */
const SEVERITY: Record<ResourceStatus, number> = { DOWN: 0, DEGRADED: 1, UNKNOWN: 2, HEALTHY: 3 }

const SUMMARY: { status: ResourceStatus; label: string; className: string }[] = [
  { status: 'HEALTHY', label: 'Healthy', className: 'text-emerald-700' },
  { status: 'DEGRADED', label: 'Degraded', className: 'text-amber-700' },
  { status: 'DOWN', label: 'Down', className: 'text-rose-700' },
]

function sortBySeverity(resources: Resource[]) {
  return [...resources].sort((a, b) => SEVERITY[a.status] - SEVERITY[b.status] || a.name.localeCompare(b.name))
}

export default function DashboardPage() {
  const { user } = useAuth()
  const { data, isPending, isError } = useQuery({
    queryKey: ['resources'],
    queryFn: listResources,
    // The simulator writes every 10 s, so refreshing faster would show nothing new.
    refetchInterval: 10_000,
  })

  return (
    <section>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Resource health</h1>
          <p className="text-sm text-slate-600">Signed in as {user?.displayName}. Updates every 10 seconds.</p>
        </div>
        {data && (
          <dl className="flex gap-6 text-sm">
            {SUMMARY.map(({ status, label, className }) => (
              <div key={status} className="text-center">
                <dd data-testid={`summary-${status}`} className={`text-2xl font-semibold tabular-nums ${className}`}>
                  {data.filter((r) => r.status === status).length}
                </dd>
                <dt className="text-slate-500">{label}</dt>
              </div>
            ))}
          </dl>
        )}
      </div>

      <ScenarioPanel />

      {isPending && <p className="text-slate-500">Loading resources…</p>}
      {isError && (
        <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">
          Could not load resources. Check that the API is running.
        </p>
      )}
      {data && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {sortBySeverity(data).map((resource) => (
            <ResourceCard key={resource.id} resource={resource} />
          ))}
        </div>
      )}
    </section>
  )
}
