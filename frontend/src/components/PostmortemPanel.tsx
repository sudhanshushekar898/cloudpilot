import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  approvePostmortem,
  generatePostmortem,
  getPostmortem,
  savePostmortem,
  type Postmortem,
  type PostmortemContent,
} from '../api/postmortems'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../lib/api'
import { timeAgo } from '../lib/time'

const timeFormat = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' })

/**
 * Postmortem for a resolved incident: drafted by the system, edited and approved by an SRE or
 * admin, then locked. The timeline comes from recorded events and can't be edited.
 */
export default function PostmortemPanel({ incidentId, resolved }: { incidentId: string; resolved: boolean }) {
  const { user } = useAuth()
  const canEdit = user?.role === 'SRE' || user?.role === 'ADMIN'
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)

  const query = useQuery({
    queryKey: ['postmortem', incidentId],
    queryFn: () => getPostmortem(incidentId),
    enabled: resolved,
  })
  const update = (result: Postmortem) => {
    setError(null)
    queryClient.setQueryData(['postmortem', incidentId], result)
  }
  const onError = (err: unknown) => setError(err instanceof ApiError ? err.message : 'Something went wrong.')
  const generate = useMutation({ mutationFn: () => generatePostmortem(incidentId), onSuccess: update, onError })

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4" aria-label="Postmortem">
      <h2 className="font-medium">Postmortem</h2>
      {!resolved && (
        <p className="mt-1 text-sm text-slate-500">Available after the incident is resolved.</p>
      )}
      {error && <p role="alert" className="mt-2 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}

      {resolved && query.isSuccess && query.data === null && (
        <div className="mt-2 text-sm text-slate-600">
          {canEdit ? (
            <button onClick={() => generate.mutate()} disabled={generate.isPending}
                    className="rounded-md bg-brand-500 px-3 py-1.5 text-white hover:bg-brand-600 disabled:opacity-60">
              {generate.isPending ? 'Drafting…' : 'Draft postmortem'}
            </button>
          ) : (
            <p>No postmortem yet.</p>
          )}
        </div>
      )}

      {query.data && (
        query.data.status === 'DRAFT' && canEdit ? (
          <PostmortemEditor key={query.data.updatedAt} postmortem={query.data} incidentId={incidentId}
                            onSaved={update} onError={onError}
                            onRegenerate={() => generate.mutate()} regenerating={generate.isPending} />
        ) : (
          <PostmortemDocument postmortem={query.data} />
        )
      )}
    </section>
  )
}

function Meta({ postmortem }: { postmortem: Postmortem }) {
  return (
    <p className="mt-1 text-xs text-slate-500">
      <span className={`mr-2 rounded px-1.5 py-0.5 font-medium ${postmortem.status === 'APPROVED'
        ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'}`}>
        {postmortem.status === 'APPROVED' ? 'Approved' : 'Draft'}
      </span>
      Drafted by {postmortem.draftedBy === 'claude' ? `Claude (${postmortem.model})` : 'template'} · last edited by{' '}
      {postmortem.updatedBy} {timeAgo(postmortem.updatedAt)}
      {postmortem.approvedBy && ` · Approved by ${postmortem.approvedBy} ${timeAgo(postmortem.approvedAt!)}`}
    </p>
  )
}

function Timeline({ postmortem }: { postmortem: Postmortem }) {
  return (
    <div>
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Timeline (from recorded events)</h3>
      <ol className="mt-1 space-y-0.5 text-sm">
        {postmortem.timeline.map((line, i) => (
          <li key={i} className="flex gap-3">
            <span className="w-14 shrink-0 font-mono text-xs text-slate-500">{timeFormat.format(new Date(line.at))}</span>
            <span>{line.text}</span>
          </li>
        ))}
      </ol>
    </div>
  )
}

function PostmortemDocument({ postmortem }: { postmortem: Postmortem }) {
  const sections: [string, string][] = [
    ['Summary', postmortem.summary],
    ['Impact', postmortem.impact],
    ['Root cause', postmortem.rootCause],
    ['Resolution', postmortem.resolution],
  ]
  return (
    <div className="mt-2 space-y-3 text-sm">
      <Meta postmortem={postmortem} />
      {sections.map(([title, text]) => (
        <div key={title}>
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</h3>
          <p className="mt-0.5 whitespace-pre-wrap">{text}</p>
        </div>
      ))}
      <div>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Prevention actions</h3>
        <ul className="mt-0.5 list-disc pl-5">{postmortem.prevention.map((p) => <li key={p}>{p}</li>)}</ul>
      </div>
      <Timeline postmortem={postmortem} />
    </div>
  )
}

interface EditorProps {
  postmortem: Postmortem
  incidentId: string
  onSaved: (p: Postmortem) => void
  onError: (err: unknown) => void
  onRegenerate: () => void
  regenerating: boolean
}

function PostmortemEditor({ postmortem, incidentId, onSaved, onError, onRegenerate, regenerating }: EditorProps) {
  const [form, setForm] = useState({
    summary: postmortem.summary,
    impact: postmortem.impact,
    rootCause: postmortem.rootCause,
    resolution: postmortem.resolution,
    prevention: postmortem.prevention.join('\n'),
  })
  const content = (): PostmortemContent => ({
    ...form,
    prevention: form.prevention.split('\n').map((line) => line.trim()).filter(Boolean),
  })

  const save = useMutation({ mutationFn: () => savePostmortem(incidentId, content()), onSuccess: onSaved, onError })
  const approve = useMutation({
    // Save first so what gets approved is exactly what's on screen.
    mutationFn: async () => {
      await savePostmortem(incidentId, content())
      return approvePostmortem(incidentId)
    },
    onSuccess: onSaved,
    onError,
  })

  const field = (key: keyof typeof form, label: string, rows: number) => (
    <label className="block text-sm">
      <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</span>
      <textarea rows={rows} value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })}
                className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none" />
    </label>
  )
  const busy = save.isPending || approve.isPending || regenerating

  return (
    <div className="mt-2 space-y-3">
      <Meta postmortem={postmortem} />
      {field('summary', 'Summary', 2)}
      {field('impact', 'Impact', 3)}
      {field('rootCause', 'Root cause', 3)}
      {field('resolution', 'Resolution', 2)}
      {field('prevention', 'Prevention actions (one per line)', 4)}
      <Timeline postmortem={postmortem} />
      <div className="flex flex-wrap gap-2">
        <button onClick={() => save.mutate()} disabled={busy}
                className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-60">
          Save draft
        </button>
        <button onClick={() => window.confirm('Approve this postmortem? It can no longer be edited afterwards.') && approve.mutate()}
                disabled={busy}
                className="rounded-md bg-emerald-600 px-3 py-1.5 text-sm text-white hover:bg-emerald-700 disabled:opacity-60">
          Approve
        </button>
        <button onClick={() => window.confirm('Replace this draft with a new one? Your edits will be lost.') && onRegenerate()}
                disabled={busy} className="ml-auto text-sm text-slate-500 hover:underline disabled:opacity-60">
          Redraft
        </button>
      </div>
    </div>
  )
}
