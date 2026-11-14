import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { getIncident, type IncidentDetail } from '../api/incidents'
import { useAuth } from '../auth/AuthContext'
import AnalysisPanel from '../components/AnalysisPanel'
import IncidentActions from '../components/IncidentActions'
import IncidentStatusBadge, { STATUS_LABELS } from '../components/IncidentStatusBadge'
import IncidentTimeline from '../components/IncidentTimeline'
import SeverityBadge from '../components/SeverityBadge'
import SuggestedRunbooks from '../components/SuggestedRunbooks'
import { formatDuration, timeAgo } from '../lib/time'

const STEPS: IncidentDetail['status'][] = ['OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED']

export default function IncidentDetailPage() {
  const { id = '' } = useParams()
  const { user } = useAuth()
  const canAct = user?.role === 'SRE' || user?.role === 'ADMIN'
  const { data: incident, isPending, isError } = useQuery({
    queryKey: ['incident', id],
    queryFn: () => getIncident(id),
    refetchInterval: 10_000,
  })

  if (isPending) return <p className="text-slate-500">Loading…</p>
  if (isError) {
    return (
      <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">
        Incident not found. <Link to="/incidents" className="underline">Back to incidents</Link>
      </p>
    )
  }

  const currentStep = STEPS.indexOf(incident.status)

  return (
    <section className="space-y-6">
      <div>
        <Link to="/incidents" className="text-sm text-brand-600 hover:underline">← Incidents</Link>
        <div className="mt-2 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold">
            <span className="font-mono">{incident.key}</span> · {incident.title}
          </h1>
          <SeverityBadge severity={incident.severity} />
          <IncidentStatusBadge status={incident.status} />
        </div>
        <p className="mt-1 text-sm text-slate-600">
          Opened {timeAgo(incident.openedAt)} · Owner: {incident.assignee?.displayName ?? 'unassigned'}
          {incident.resolvedAt &&
            ` · Resolved after ${formatDuration(new Date(incident.resolvedAt).getTime() - new Date(incident.openedAt).getTime())}`}
        </p>
        <ol className="mt-3 flex flex-wrap gap-2 text-xs" aria-label="Lifecycle">
          {STEPS.map((step, index) => (
            <li key={step}
                className={`rounded-full px-2.5 py-0.5 ${index <= currentStep ? 'bg-brand-500 text-white' : 'bg-slate-100 text-slate-500'}`}>
              {index + 1}. {STATUS_LABELS[step]}
            </li>
          ))}
        </ol>
        {incident.allAlertsResolved && incident.status !== 'RESOLVED' && (
          <p className="mt-3 rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            All alerts have recovered. Confirm the fix and resolve the incident with a note.
          </p>
        )}
        {incident.resolution && (
          <p className="mt-3 rounded-md bg-slate-50 px-3 py-2 text-sm"><strong>Resolution:</strong> {incident.resolution}</p>
        )}
      </div>

      {canAct && <IncidentActions incident={incident} />}

      <div className="grid gap-6 lg:grid-cols-3">
        <section className="rounded-xl border border-slate-200 bg-white p-4">
          <h2 className="mb-2 font-medium">Affected resources</h2>
          <ul className="space-y-1 text-sm">
            {incident.resources.map((r) => (
              <li key={r.id} className="flex items-center gap-2">
                <Link to={`/resources/${r.id}`} className="text-brand-600 hover:underline">{r.name}</Link>
                <span className="font-mono text-xs text-slate-500">{r.type}</span>
                {r.probableOrigin && (
                  <span className="rounded bg-amber-100 px-1.5 text-xs text-amber-800"
                        title="Has alerts but depends on nothing else in this incident">
                    Probable origin
                  </span>
                )}
              </li>
            ))}
          </ul>
        </section>

        <section className="rounded-xl border border-slate-200 bg-white p-4 lg:col-span-2">
          <h2 className="mb-2 font-medium">Grouped alerts ({incident.alerts.length})</h2>
          <ul className="divide-y divide-slate-100 text-sm">
            {incident.alerts.map((a) => (
              <li key={a.alertId} className="py-2">
                <div className="flex flex-wrap items-center gap-2">
                  <SeverityBadge severity={a.severity} />
                  <span className={a.resolvedAt ? 'text-slate-500 line-through' : ''}>{a.message}</span>
                </div>
                <p className="mt-0.5 text-xs text-slate-500">
                  <span className="font-medium">Why grouped: </span>
                  <span>{a.reason}</span>
                </p>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <AnalysisPanel incidentId={incident.id} />

      <SuggestedRunbooks incidentId={incident.id} />

      <IncidentTimeline incidentId={incident.id} resources={incident.resources} />
    </section>
  )
}
