import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { getReport } from '../api/reports'
import { formatDuration } from '../lib/time'

const WINDOWS = [
  { days: 7, label: '7 days' },
  { days: 30, label: '30 days' },
  { days: 90, label: '90 days' },
]

const minutes = (value: number | null) => (value === null ? 'No data' : formatDuration(Math.round(value) * 60_000))

/** A headline number. Text stays in ink colours; the number is the figure. */
function StatTile({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <p className="text-sm text-slate-600">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums text-slate-900">{value}</p>
      {note && <p className="mt-0.5 text-xs text-slate-500">{note}</p>}
    </div>
  )
}

export default function ReportsPage() {
  const [days, setDays] = useState(30)
  const { data: report, isPending, isError } = useQuery({ queryKey: ['report', days], queryFn: () => getReport(days) })

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Reliability reports</h1>
          <p className="text-sm text-slate-600">Incidents opened in the selected period.</p>
        </div>
        <div className="flex rounded-lg border border-slate-200 bg-white p-0.5" role="group" aria-label="Time window">
          {WINDOWS.map((w) => (
            <button key={w.days} onClick={() => setDays(w.days)} aria-pressed={days === w.days}
                    className={`rounded-md px-3 py-1 text-sm ${days === w.days ? 'bg-brand-500 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>
              {w.label}
            </button>
          ))}
        </div>
      </div>

      {isPending && <p className="text-slate-500">Loading report…</p>}
      {isError && <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">Could not load the report.</p>}

      {report && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile label="Incidents opened" value={String(report.opened)}
                      note={`${report.resolved} resolved, ${report.active} still active`} />
            <StatTile label="Mean time to acknowledge" value={minutes(report.mttaMinutes)} />
            <StatTile label="Mean time to resolve" value={minutes(report.mttrMinutes)} />
            <StatTile label="Resolved within SLA"
                      value={report.slaCompliancePercent === null ? 'No data' : `${Math.round(report.slaCompliancePercent)}%`} />
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <Table title="SLA by severity" headers={['Severity', 'Target', 'Within target', 'Status']}>
              {report.sla.map((s) => (
                <tr key={s.severity}>
                  <td className="px-4 py-2 font-medium">{s.severity}</td>
                  <td className="px-4 py-2">{formatDuration(s.targetMinutes * 60_000)}</td>
                  <td className="px-4 py-2">{s.resolved === 0 ? '-' : `${s.withinTarget} of ${s.resolved}`}</td>
                  <td className="px-4 py-2">
                    {s.resolved === 0 ? <span className="text-slate-400">No incidents</span>
                      : s.withinTarget === s.resolved
                        ? <span className="text-emerald-700"><span aria-hidden="true">✓ </span><span>Meets target</span></span>
                        : <span className="text-rose-700"><span aria-hidden="true">⚠ </span><span>Below target</span></span>}
                  </td>
                </tr>
              ))}
            </Table>

            <Table title="Resources with the most incidents" headers={['Resource', 'Incidents']}>
              {report.topResources.map((r) => (
                <tr key={r.resource}>
                  <td className="px-4 py-2 font-medium">{r.resource}</td>
                  <td className="px-4 py-2 tabular-nums">{r.incidents}</td>
                </tr>
              ))}
            </Table>
          </div>

          <section>
            <h2 className="mb-2 font-medium">Recurring failures</h2>
            {report.recurring.length === 0 ? (
              <p className="rounded-xl border border-dashed border-slate-300 bg-white p-4 text-sm text-slate-500">
                No failure happened more than once in this period.
              </p>
            ) : (
              <Table headers={['Resource', 'Failure', 'Times', 'Last seen']}>
                {report.recurring.map((r) => (
                  <tr key={r.resource + r.signature}>
                    <td className="px-4 py-2 font-medium">{r.resource}</td>
                    <td className="px-4 py-2 font-mono text-xs">{r.signature}</td>
                    <td className="px-4 py-2 tabular-nums">{r.occurrences}</td>
                    <td className="px-4 py-2">{new Date(r.lastSeen).toLocaleDateString()}</td>
                  </tr>
                ))}
              </Table>
            )}
          </section>

          <Table title="Responders" headers={['Name', 'Incidents resolved', 'Mean time to resolve']}>
            {report.responders.map((r) => (
              <tr key={r.name}>
                <td className="px-4 py-2 font-medium">{r.name}</td>
                <td className="px-4 py-2 tabular-nums">{r.resolved}</td>
                <td className="px-4 py-2">{minutes(r.mttrMinutes)}</td>
              </tr>
            ))}
          </Table>
        </>
      )}
    </section>
  )
}

function Table({ title, headers, children }: { title?: string; headers: string[]; children: React.ReactNode }) {
  return (
    <section>
      {title && <h2 className="mb-2 font-medium">{title}</h2>}
      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>{headers.map((h) => <th key={h} className="px-4 py-2">{h}</th>)}</tr>
          </thead>
          <tbody className="divide-y divide-slate-100">{children}</tbody>
        </table>
      </div>
    </section>
  )
}
