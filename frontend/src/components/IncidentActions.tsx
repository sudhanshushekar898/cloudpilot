import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  acknowledgeIncident,
  assignIncident,
  investigateIncident,
  listAssignable,
  resolveIncident,
  type IncidentDetail,
} from '../api/incidents'
import { ApiError } from '../lib/api'

/**
 * Lifecycle controls for SREs and admins. Only the next valid step is offered, matching the
 * backend state machine; the backend still rejects anything else with 409.
 */
export default function IncidentActions({ incident }: { incident: IncidentDetail }) {
  const queryClient = useQueryClient()
  const [resolution, setResolution] = useState('')
  const [error, setError] = useState<string | null>(null)
  const { data: people } = useQuery({ queryKey: ['assignable'], queryFn: listAssignable })

  const action = useMutation({
    mutationFn: (run: () => Promise<unknown>) => run(),
    onSuccess: () => {
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['incident', incident.id] })
      void queryClient.invalidateQueries({ queryKey: ['timeline', incident.id] })
      void queryClient.invalidateQueries({ queryKey: ['incidents'] })
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Something went wrong.'),
  })

  if (incident.status === 'RESOLVED') return null

  const button = 'rounded-md bg-brand-500 px-3 py-1.5 text-sm text-white hover:bg-brand-600 disabled:opacity-60'

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4" aria-label="Incident actions">
      {error && <p role="alert" className="mb-3 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}

      <div className="flex flex-wrap items-end gap-4">
        {incident.status === 'OPEN' && (
          <button className={button} disabled={action.isPending}
                  onClick={() => action.mutate(() => acknowledgeIncident(incident.id))}>
            Acknowledge
          </button>
        )}
        {incident.status === 'ACKNOWLEDGED' && (
          <button className={button} disabled={action.isPending}
                  onClick={() => action.mutate(() => investigateIncident(incident.id))}>
            Start investigating
          </button>
        )}

        <label className="text-sm">
          Owner
          <select
            className="ml-2 rounded-md border border-slate-300 px-2 py-1 text-sm"
            value={incident.assignee?.id ?? ''}
            onChange={(e) => {
              // Read the value now: the select is controlled, so React resets it before the mutation runs.
              const userId = e.target.value
              if (userId) action.mutate(() => assignIncident(incident.id, userId))
            }}
          >
            <option value="" disabled>Unassigned</option>
            {(people ?? []).map((p) => <option key={p.id} value={p.id}>{p.displayName}</option>)}
          </select>
        </label>
      </div>

      {incident.status === 'INVESTIGATING' && (
        <div className="mt-4">
          <label htmlFor="resolution" className="block text-sm font-medium">What fixed it?</label>
          <textarea
            id="resolution"
            rows={3}
            value={resolution}
            onChange={(e) => setResolution(e.target.value)}
            placeholder="e.g. Rolled back checkout-api to v2.13.4"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand-500 focus:outline-none"
          />
          <button className={`${button} mt-2`} disabled={!resolution.trim() || action.isPending}
                  onClick={() => action.mutate(() => resolveIncident(incident.id, resolution.trim()))}>
            Resolve incident
          </button>
        </div>
      )}
    </section>
  )
}
