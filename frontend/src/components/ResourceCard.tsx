import { Link } from 'react-router-dom'
import type { MetricType, Resource } from '../api/resources'
import { formatMetric, METRIC_LABELS } from '../lib/format'
import StatusBadge from './StatusBadge'

const CARD_METRICS: MetricType[] = ['CPU', 'LATENCY_P95', 'ERROR_RATE', 'AVAILABILITY']

const BORDER: Record<Resource['status'], string> = {
  HEALTHY: 'border-slate-200',
  DEGRADED: 'border-amber-300',
  DOWN: 'border-rose-400',
  UNKNOWN: 'border-slate-200',
}

export default function ResourceCard({ resource }: { resource: Resource }) {
  return (
    <Link
      to={`/resources/${resource.id}`}
      className={`block rounded-xl border bg-white p-4 shadow-sm transition hover:shadow-md ${BORDER[resource.status]}`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate font-medium">{resource.name}</p>
          <p className="text-xs text-slate-500">
            <span className="font-mono">{resource.type}</span> · {resource.region}
          </p>
        </div>
        <StatusBadge status={resource.status} />
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
        {CARD_METRICS.map((metric) => (
          <div key={metric}>
            <dt className="text-xs text-slate-500">{METRIC_LABELS[metric]}</dt>
            <dd className="font-medium tabular-nums">{formatMetric(metric, resource.latest[metric])}</dd>
          </div>
        ))}
      </dl>
    </Link>
  )
}
