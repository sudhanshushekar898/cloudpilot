import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { createRunbook, listRunbooks, searchRunbooks } from '../api/runbooks'
import { useAuth } from '../auth/AuthContext'
import Snippet from '../components/Snippet'
import { ApiError } from '../lib/api'
import { timeAgo } from '../lib/time'

export default function RunbooksPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const [adding, setAdding] = useState(false)

  const runbooks = useQuery({ queryKey: ['runbooks'], queryFn: listRunbooks })
  const results = useQuery({
    queryKey: ['runbook-search', query],
    queryFn: () => searchRunbooks(query),
    enabled: query.length > 0,
  })

  function submitSearch(event: FormEvent) {
    event.preventDefault()
    setQuery(input.trim())
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Runbooks</h1>
          <p className="text-sm text-slate-600">
            The team's runbooks, split into sections. Incidents and the AI assistant search them for evidence.
          </p>
        </div>
        {isAdmin && !adding && (
          <button onClick={() => setAdding(true)}
                  className="rounded-md bg-brand-500 px-3 py-1.5 text-sm text-white hover:bg-brand-600">
            Add runbook
          </button>
        )}
      </div>

      {adding && <AddRunbookForm onDone={() => setAdding(false)} />}

      <form onSubmit={submitSearch} role="search">
        <input
          type="search"
          aria-label="Search runbooks"
          placeholder="Search, e.g. too many clients, OOMKilled, rollback"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
        />
      </form>

      {query && (
        <section aria-label="Search results" className="space-y-2">
          {results.isPending && <p className="text-sm text-slate-500">Searching…</p>}
          {results.data?.length === 0 && <p className="text-sm text-slate-500">No sections match "{query}".</p>}
          {results.data?.map((hit) => (
            <article key={hit.chunkId} className="rounded-lg border border-slate-200 bg-white p-3">
              <p className="text-sm font-medium">{hit.heading}</p>
              <p className="mt-1 text-sm text-slate-700"><Snippet text={hit.snippet} /></p>
              <Link to={`/runbooks/${hit.runbookSlug}`} className="mt-1 inline-block text-xs text-brand-600 hover:underline">
                Open runbook
              </Link>
            </article>
          ))}
        </section>
      )}

      <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200 bg-white">
        {runbooks.data?.map((r) => (
          <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
            <Link to={`/runbooks/${r.slug}`} className="font-medium text-brand-600 hover:underline">{r.title}</Link>
            <span className="text-xs text-slate-500">
              {r.chunkCount} sections · updated {timeAgo(r.updatedAt)} by {r.createdBy}
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}

function AddRunbookForm({ onDone }: { onDone: () => void }) {
  const queryClient = useQueryClient()
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => createRunbook(title.trim(), content),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['runbooks'] })
      onDone()
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the runbook.'),
  })

  /** Read a .md file in the browser; the API only ever receives text. */
  async function loadFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return
    const text = await file.text()
    setContent(text)
    const heading = text.split('\n').find((line) => line.startsWith('# '))
    if (!title && heading) setTitle(heading.slice(2).trim())
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate()
      }}
      className="space-y-3 rounded-xl border border-slate-200 bg-white p-4"
    >
      {error && <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}
      <label className="block text-sm">
        Title
        <input required maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)}
               className="mt-1 w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm" />
      </label>
      <label className="block text-sm">
        Upload a .md file
        <input type="file" accept=".md,text/markdown" onChange={loadFile} className="mt-1 block text-sm" />
      </label>
      <label className="block text-sm">
        Markdown
        <textarea required rows={10} value={content} onChange={(e) => setContent(e.target.value)}
                  className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 font-mono text-xs" />
      </label>
      <div className="flex gap-2">
        <button type="submit" disabled={save.isPending}
                className="rounded-md bg-brand-500 px-3 py-1.5 text-sm text-white hover:bg-brand-600 disabled:opacity-60">
          Save runbook
        </button>
        <button type="button" onClick={onDone} className="rounded-md border border-slate-300 px-3 py-1.5 text-sm">
          Cancel
        </button>
      </div>
    </form>
  )
}
