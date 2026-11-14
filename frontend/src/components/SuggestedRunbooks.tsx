import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { runbookSuggestions } from '../api/runbooks'
import Snippet from './Snippet'

/**
 * Runbook sections retrieved from the incident's own evidence (alerts, resources, error logs).
 * The same retrieval will ground the AI analysis in M7.
 */
export default function SuggestedRunbooks({ incidentId }: { incidentId: string }) {
  const { data, isPending } = useQuery({
    queryKey: ['runbook-suggestions', incidentId],
    queryFn: () => runbookSuggestions(incidentId),
    // New alerts and logs change what's relevant, but not every few seconds.
    refetchInterval: 60_000,
  })

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4">
      <h2 className="font-medium">Suggested runbooks</h2>
      <p className="mb-2 text-xs text-slate-500">Found by searching runbooks for this incident's alerts and error logs.</p>
      {isPending && <p className="text-sm text-slate-500">Searching runbooks…</p>}
      {data?.length === 0 && (
        <p className="text-sm text-slate-500">No runbook matches this incident yet. Consider writing one afterwards.</p>
      )}
      <ul className="space-y-3">
        {data?.map((hit) => (
          <li key={hit.chunkId} className="text-sm">
            <p className="font-medium">{hit.heading}</p>
            <p className="text-slate-700"><Snippet text={hit.snippet} /></p>
            <Link to={`/runbooks/${hit.runbookSlug}`} className="text-xs text-brand-600 hover:underline">
              Open runbook: {hit.runbookTitle}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
