import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  commentOnIncident,
  getTimeline,
  type IncidentResource,
  type TimelineItem,
  type TimelineType,
} from '../api/incidents'

const FILTERS: { label: string; types: TimelineType[] }[] = [
  { label: 'Everything', types: [] },
  { label: 'Alerts', types: ['ALERT_FIRED', 'ALERT_RESOLVED'] },
  { label: 'Status', types: ['STATUS_CHANGED', 'ASSIGNED'] },
  { label: 'Comments', types: ['COMMENT'] },
  { label: 'Logs', types: ['LOG'] },
  { label: 'Deployments', types: ['DEPLOYMENT'] },
]

const DOT: Record<TimelineType, string> = {
  ALERT_FIRED: 'bg-rose-500',
  ALERT_RESOLVED: 'bg-emerald-500',
  STATUS_CHANGED: 'bg-sky-500',
  ASSIGNED: 'bg-sky-300',
  COMMENT: 'bg-violet-500',
  LOG: 'bg-slate-400',
  DEPLOYMENT: 'bg-amber-500',
}

const timeFormat = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' })

interface Props {
  incidentId: string
  resources: IncidentResource[]
}

export default function IncidentTimeline({ incidentId, resources }: Props) {
  const [filter, setFilter] = useState(FILTERS[0]!)
  const [resourceId, setResourceId] = useState('')

  const { data, isPending } = useQuery({
    queryKey: ['timeline', incidentId, filter.label, resourceId],
    queryFn: () => getTimeline(incidentId, filter.types, resourceId || undefined),
    refetchInterval: 10_000,
  })

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-medium">Timeline</h2>
        <div className="flex flex-wrap items-center gap-1">
          {FILTERS.map((f) => (
            <button
              key={f.label}
              onClick={() => setFilter(f)}
              aria-pressed={filter.label === f.label}
              className={`rounded-full px-2.5 py-0.5 text-xs ${
                filter.label === f.label ? 'bg-brand-500 text-white' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
              }`}
            >
              {f.label}
            </button>
          ))}
          <select
            aria-label="Filter by resource"
            value={resourceId}
            onChange={(e) => setResourceId(e.target.value)}
            className="ml-2 rounded-md border border-slate-300 px-2 py-0.5 text-xs"
          >
            <option value="">All resources</option>
            {resources.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
          </select>
        </div>
      </div>

      {isPending && <p className="text-sm text-slate-500">Loading timeline…</p>}
      {data && data.length === 0 && <p className="text-sm text-slate-500">Nothing to show for this filter.</p>}
      {data && data.length > 0 && (
        <ol className="space-y-2">
          {data.map((item, index) => <TimelineRow key={`${item.occurredAt}-${index}`} item={item} />)}
        </ol>
      )}

      <CommentForm incidentId={incidentId} />
    </section>
  )
}

function TimelineRow({ item }: { item: TimelineItem }) {
  return (
    <li className="flex gap-3 text-sm">
      <span className="w-20 shrink-0 pt-0.5 font-mono text-xs text-slate-500">
        {timeFormat.format(new Date(item.occurredAt))}
      </span>
      <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${DOT[item.type]}`} aria-hidden="true" />
      <div className="min-w-0">
        <p className={item.type === 'LOG' ? 'break-words font-mono text-xs' : ''}>
          {item.level && <span className={item.level === 'ERROR' ? 'text-rose-600' : 'text-amber-700'}>{item.level} </span>}
          {item.message}
        </p>
        <p className="text-xs text-slate-500">
          {[item.resourceName, item.actor].filter(Boolean).join(' · ')}
        </p>
      </div>
    </li>
  )
}

function CommentForm({ incidentId }: { incidentId: string }) {
  const queryClient = useQueryClient()
  const [text, setText] = useState('')
  const comment = useMutation({
    mutationFn: () => commentOnIncident(incidentId, text.trim()),
    onSuccess: () => {
      setText('')
      void queryClient.invalidateQueries({ queryKey: ['timeline', incidentId] })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    if (text.trim()) comment.mutate()
  }

  return (
    <form onSubmit={submit} className="mt-4 flex gap-2 border-t border-slate-100 pt-4">
      <label htmlFor="comment" className="sr-only">Add a comment</label>
      <input
        id="comment"
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder="Add context for the team…"
        className="flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm focus:border-brand-500 focus:outline-none"
      />
      <button type="submit" disabled={!text.trim() || comment.isPending}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-60">
        Comment
      </button>
    </form>
  )
}
