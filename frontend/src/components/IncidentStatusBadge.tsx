import type { IncidentStatus } from '../api/incidents'

export const STATUS_LABELS: Record<IncidentStatus, string> = {
  OPEN: 'Open',
  ACKNOWLEDGED: 'Acknowledged',
  INVESTIGATING: 'Investigating',
  RESOLVED: 'Resolved',
}

const STYLES: Record<IncidentStatus, string> = {
  OPEN: 'bg-rose-50 text-rose-700 ring-rose-200',
  ACKNOWLEDGED: 'bg-amber-50 text-amber-800 ring-amber-200',
  INVESTIGATING: 'bg-sky-50 text-sky-700 ring-sky-200',
  RESOLVED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
}

export default function IncidentStatusBadge({ status }: { status: IncidentStatus }) {
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ring-1 ${STYLES[status]}`}>
      {STATUS_LABELS[status]}
    </span>
  )
}
