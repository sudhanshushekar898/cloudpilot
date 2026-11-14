import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { listAlerts, type Alert, type AlertStatus } from '../api/alerts'
import SeverityBadge from '../components/SeverityBadge'
import { formatDuration, timeAgo } from '../lib/time'

const TABS: { label: string; status: AlertStatus | undefined }[] = [
  { label: 'Firing', status: 'FIRING' },
  { label: 'All', status: undefined },
]

export default function AlertsPage() {
  const [status, setStatus] = useState<AlertStatus | undefined>('FIRING')
  const { data, isPending, isError } = useQuery({
    queryKey: ['alerts', status ?? 'ALL'],
    queryFn: () => listAlerts(status),
    refetchInterval: 10_000,
  })

  return (
    <section>
      <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Alerts</h1>
          <p className="text-sm text-slate-600">
            An alert fires when a rule's condition holds for its full duration.
          </p>
        </div>
        <Link to="/alerts/rules" className="text-sm text-brand-600 hover:underline">
          Alert rules
        </Link>
      </div>

      <div role="tablist" className="mb-4 flex gap-1 border-b border-slate-200">
        {TABS.map((tab) => (
          <button
            key={tab.label}
            role="tab"
            aria-selected={status === tab.status}
            onClick={() => setStatus(tab.status)}
            className={`-mb-px border-b-2 px-3 py-2 text-sm ${
              status === tab.status ? 'border-brand-500 font-medium text-brand-600' : 'border-transparent text-slate-600'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {isPending && <p className="text-slate-500">Loading alerts…</p>}
      {isError && (
        <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">
          Could not load alerts.
        </p>
      )}
      {data && data.length === 0 && (
        <p className="rounded-xl border border-dashed border-slate-300 bg-white p-8 text-center text-slate-500">
          {status === 'FIRING' ? 'No alerts are firing. All rules are within their thresholds.' : 'No alerts yet.'}
        </p>
      )}
      {data && data.length > 0 && <AlertTable alerts={data} />}
    </section>
  )
}

function AlertTable({ alerts }: { alerts: Alert[] }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
      <table className="w-full text-left text-sm">
        <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th className="px-4 py-2">Severity</th>
            <th className="px-4 py-2">Resource</th>
            <th className="px-4 py-2">What happened</th>
            <th className="px-4 py-2">Fired</th>
            <th className="px-4 py-2">Status</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {alerts.map((alert) => (
            <tr key={alert.id} className={alert.status === 'RESOLVED' ? 'text-slate-500' : ''}>
              <td className="px-4 py-2"><SeverityBadge severity={alert.severity} /></td>
              <td className="px-4 py-2">
                <Link to={`/resources/${alert.resourceId}`} className="font-medium text-brand-600 hover:underline">
                  {alert.resourceName}
                </Link>
              </td>
              <td className="px-4 py-2">
                <p>{alert.message}</p>
                <p className="text-xs text-slate-500">{alert.ruleName}</p>
              </td>
              <td className="whitespace-nowrap px-4 py-2" title={new Date(alert.firedAt).toLocaleString()}>
                {timeAgo(alert.firedAt)}
              </td>
              <td className="whitespace-nowrap px-4 py-2">
                {alert.status === 'FIRING' ? (
                  <span className="font-medium text-rose-700">Firing</span>
                ) : (
                  `Resolved after ${formatDuration(new Date(alert.resolvedAt!).getTime() - new Date(alert.firedAt).getTime())}`
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
