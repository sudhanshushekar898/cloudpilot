import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Markdown from 'react-markdown'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { deleteRunbook, getRunbook } from '../api/runbooks'
import { useAuth } from '../auth/AuthContext'
import { timeAgo } from '../lib/time'

export default function RunbookPage() {
  const { slug = '' } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { data: runbook, isPending, isError } = useQuery({ queryKey: ['runbook', slug], queryFn: () => getRunbook(slug) })

  const remove = useMutation({
    mutationFn: () => deleteRunbook(slug),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['runbooks'] })
      navigate('/runbooks')
    },
  })

  if (isPending) return <p className="text-slate-500">Loading…</p>
  if (isError) {
    return (
      <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">
        Runbook not found. <Link to="/runbooks" className="underline">Back to runbooks</Link>
      </p>
    )
  }

  return (
    <section>
      <Link to="/runbooks" className="text-sm text-brand-600 hover:underline">← Runbooks</Link>
      <div className="mt-2 mb-4 flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-slate-500">Updated {timeAgo(runbook.updatedAt)} by {runbook.createdBy}</p>
        {user?.role === 'ADMIN' && (
          <button
            onClick={() => window.confirm(`Delete "${runbook.title}"?`) && remove.mutate()}
            disabled={remove.isPending}
            className="rounded-md border border-rose-300 px-3 py-1 text-sm text-rose-700 hover:bg-rose-50"
          >
            Delete runbook
          </button>
        )}
      </div>
      {/* react-markdown renders Markdown to React elements and ignores raw HTML by default. */}
      <article className="markdown rounded-xl border border-slate-200 bg-white p-6">
        <Markdown>{runbook.content}</Markdown>
      </article>
    </section>
  )
}
