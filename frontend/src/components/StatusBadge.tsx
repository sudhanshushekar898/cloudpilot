import type { ResourceStatus } from '../api/resources'

const STYLES: Record<ResourceStatus, { label: string; className: string; dot: string }> = {
  HEALTHY: { label: 'Healthy', className: 'bg-emerald-50 text-emerald-700', dot: 'bg-emerald-500' },
  DEGRADED: { label: 'Degraded', className: 'bg-amber-50 text-amber-800', dot: 'bg-amber-500' },
  DOWN: { label: 'Down', className: 'bg-rose-50 text-rose-700', dot: 'bg-rose-500' },
  UNKNOWN: { label: 'No data', className: 'bg-slate-100 text-slate-600', dot: 'bg-slate-400' },
}

/** Status is shown as colour *and* text, so it still reads for colour-blind users. */
export default function StatusBadge({ status }: { status: ResourceStatus }) {
  const style = STYLES[status]
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium ${style.className}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} aria-hidden="true" />
      {style.label}
    </span>
  )
}
