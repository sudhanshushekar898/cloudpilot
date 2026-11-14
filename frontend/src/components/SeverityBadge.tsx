import type { Severity } from '../api/alerts'

const STYLES: Record<Severity, string> = {
  CRITICAL: 'bg-rose-600 text-white',
  WARNING: 'bg-amber-100 text-amber-800',
  INFO: 'bg-slate-100 text-slate-700',
}

export default function SeverityBadge({ severity }: { severity: Severity }) {
  return (
    <span className={`inline-block rounded px-1.5 py-0.5 text-xs font-semibold tracking-wide ${STYLES[severity]}`}>
      {severity}
    </span>
  )
}
