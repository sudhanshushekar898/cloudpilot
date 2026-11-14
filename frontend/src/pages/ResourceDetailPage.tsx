import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getMetricHistory, getResource, listResources, METRIC_TYPES } from '../api/resources'
import MetricChart from '../components/MetricChart'
import StatusBadge from '../components/StatusBadge'

const RANGES = [
  { minutes: 15, label: '15 min' },
  { minutes: 60, label: '1 h' },
  { minutes: 360, label: '6 h' },
  { minutes: 1440, label: '24 h' },
]

export default function ResourceDetailPage() {
  const { id = '' } = useParams()
  const [minutes, setMinutes] = useState(60)

  const resource = useQuery({ queryKey: ['resource', id], queryFn: () => getResource(id), refetchInterval: 10_000 })
  const history = useQuery({
    queryKey: ['history', id, minutes],
    queryFn: () => getMetricHistory(id, minutes),
    refetchInterval: 10_000,
    enabled: resource.isSuccess,
  })
  // Shares the dashboard's cache, so dependency names usually cost no extra request.
  const all = useQuery({ queryKey: ['resources'], queryFn: listResources })

  if (resource.isPending) return <p className="text-slate-500">Loading…</p>
  if (resource.isError) {
    return (
      <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">
        Resource not found. <Link to="/" className="underline">Back to dashboard</Link>
      </p>
    )
  }

  const r = resource.data
  const dependencies = (all.data ?? []).filter((other) => r.dependsOn.includes(other.id))
  const dependents = (all.data ?? []).filter((other) => other.dependsOn.includes(r.id))

  return (
    <section>
      <Link to="/" className="text-sm text-brand-600 hover:underline">← All resources</Link>

      <div className="mt-2 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold">{r.name}</h1>
            <StatusBadge status={r.status} />
          </div>
          <p className="mt-1 text-sm text-slate-600">{r.description}</p>
          <p className="text-xs text-slate-500">
            <span className="font-mono">{r.type}</span> · {r.region}
          </p>
        </div>

        <div className="flex rounded-lg border border-slate-200 bg-white p-0.5" role="group" aria-label="Time range">
          {RANGES.map((range) => (
            <button
              key={range.minutes}
              onClick={() => setMinutes(range.minutes)}
              aria-pressed={minutes === range.minutes}
              className={`rounded-md px-3 py-1 text-sm ${
                minutes === range.minutes ? 'bg-brand-500 text-white' : 'text-slate-600 hover:bg-slate-50'
              }`}
            >
              {range.label}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-4 flex flex-wrap gap-6 text-sm">
        <DependencyList title="Depends on" items={dependencies} empty="Nothing" />
        <DependencyList title="Used by" items={dependents} empty="Nothing" />
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        {METRIC_TYPES.map((metric) => (
          <MetricChart key={metric} metric={metric} points={history.data?.series[metric] ?? []} />
        ))}
      </div>
    </section>
  )
}

function DependencyList({ title, items, empty }: { title: string; items: { id: string; name: string }[]; empty: string }) {
  return (
    <div>
      <p className="text-xs uppercase tracking-wide text-slate-500">{title}</p>
      <div className="mt-1 flex flex-wrap gap-2">
        {items.length === 0 && <span className="text-slate-400">{empty}</span>}
        {items.map((item) => (
          <Link key={item.id} to={`/resources/${item.id}`} className="rounded-md border border-slate-200 bg-white px-2 py-0.5 hover:bg-slate-50">
            {item.name}
          </Link>
        ))}
      </div>
    </div>
  )
}
