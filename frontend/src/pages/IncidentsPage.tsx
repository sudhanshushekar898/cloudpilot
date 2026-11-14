import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { listIncidents, type IncidentScope } from '../api/incidents'
import IncidentStatusBadge from '../components/IncidentStatusBadge'
import SeverityBadge from '../components/SeverityBadge'
import { formatDuration, timeAgo } from '../lib/time'

const TABS: { label: string; scope: IncidentScope }[] = [
  { label: 'Active', scope: 'active' },
  { label: 'Resolved', scope: 'resolved' },
]

export default function IncidentsPage() {
  const [scope, setScope] = useState<IncidentScope>('active')
  const { data, isPending, isError } = useQuery({
    queryKey: ['incidents', scope],
    queryFn: () => listIncidents(scope),
    refetchInterval: 10_000,
  })

  return (
    <section>
      <h1 className="text-2xl font-semibold">Incidents</h1>
      <p className="mb-4 text-sm text-slate-600">
        Related alerts are grouped into one incident using the resource dependency graph.
      </p>

      <div role="tablist" className="mb-4 flex gap-1 border-b border-slate-200">
        {TABS.map((tab) => (
          <button
            key={tab.scope}
            role="tab"
            aria-selected={scope === tab.scope}
            onClick={() => setScope(tab.scope)}
            className={`-mb-px border-b-2 px-3 py-2 text-sm ${
              scope === tab.scope ? 'border-brand-500 font-medium text-brand-600' : 'border-transparent text-slate-600'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {isPending && <p className="text-slate-500">Loading incidents…</p>}
      {isError && (
        <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">Could not load incidents.</p>
      )}
      {data && data.length === 0 && (
        <p className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center text-slate-500">
          {scope === 'active' ? 'No active incidents.' : 'No resolved incidents yet.'}
        </p>
      )}
      {data && data.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-2">Incident</th>
                <th className="px-4 py-2">Severity</th>
                <th className="px-4 py-2">Status</th>
                <th className="px-4 py-2">Owner</th>
                <th className="px-4 py-2">Alerts</th>
                <th className="px-4 py-2">{scope === 'active' ? 'Opened' : 'Duration'}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.map((incident) => (
                <tr key={incident.id}>
                  <td className="px-4 py-2">
                    <Link to={`/incidents/${incident.id}`} className="font-mono font-medium text-brand-600 hover:underline">
                      {incident.key}
                    </Link>
                    <p className="text-slate-800">{incident.title}</p>
                    <p className="text-xs text-slate-500">{incident.resourceNames.join(', ')}</p>
                  </td>
                  <td className="px-4 py-2"><SeverityBadge severity={incident.severity} /></td>
                  <td className="px-4 py-2"><IncidentStatusBadge status={incident.status} /></td>
                  <td className="px-4 py-2">{incident.assigneeName ?? <span className="text-slate-400">Unassigned</span>}</td>
                  <td className="whitespace-nowrap px-4 py-2">
                    {incident.activeAlertCount} of {incident.alertCount} alerts firing
                  </td>
                  <td className="whitespace-nowrap px-4 py-2">
                    {incident.resolvedAt
                      ? formatDuration(new Date(incident.resolvedAt).getTime() - new Date(incident.openedAt).getTime())
                      : timeAgo(incident.openedAt)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
