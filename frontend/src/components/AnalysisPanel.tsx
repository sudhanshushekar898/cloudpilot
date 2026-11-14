import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { getLatestAnalysis, requestAnalysis, type Analysis, type Claim, type EvidenceItem } from '../api/analysis'
import { ApiError } from '../lib/api'
import { timeAgo } from '../lib/time'

const DEFAULT_QUESTION = 'What is the most likely root cause, and what should we check first?'

function confidenceLabel(confidence: number) {
  if (confidence >= 0.7) return { label: 'High confidence', className: 'bg-emerald-500' }
  if (confidence >= 0.4) return { label: 'Medium confidence', className: 'bg-amber-500' }
  return { label: 'Low confidence', className: 'bg-rose-500' }
}

/**
 * Ask-and-answer panel for incident analysis. Every claim shows the evidence ids behind it,
 * linked to the evidence list, so an engineer can check each statement in one click.
 */
export default function AnalysisPanel({ incidentId }: { incidentId: string }) {
  const queryClient = useQueryClient()
  const [question, setQuestion] = useState(DEFAULT_QUESTION)
  const [error, setError] = useState<string | null>(null)

  const latest = useQuery({ queryKey: ['analysis', incidentId], queryFn: () => getLatestAnalysis(incidentId) })
  const ask = useMutation({
    mutationFn: () => requestAnalysis(incidentId, question.trim()),
    onSuccess: (result) => {
      setError(null)
      queryClient.setQueryData(['analysis', incidentId], result)
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'The analysis could not be completed.'),
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    ask.mutate()
  }

  return (
    <section className="rounded-xl border border-violet-200 bg-white p-4" aria-label="AI analysis">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="font-medium">AI root-cause analysis</h2>
        <p className="text-xs text-slate-500">Answers use only this incident's evidence and runbooks.</p>
      </div>

      <form onSubmit={submit} className="mt-3 flex flex-col gap-2 sm:flex-row">
        <label htmlFor="analysis-question" className="sr-only">Question</label>
        <input
          id="analysis-question"
          maxLength={500}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          className="flex-1 rounded-md border border-slate-300 px-3 py-1.5 text-sm focus:border-violet-500 focus:outline-none"
        />
        <button type="submit" disabled={ask.isPending}
                className="rounded-md bg-violet-600 px-4 py-1.5 text-sm text-white hover:bg-violet-700 disabled:opacity-60">
          {ask.isPending ? 'Analyzing…' : 'Analyze'}
        </button>
      </form>
      {ask.isPending && <p className="mt-2 text-xs text-slate-500">Reading the evidence. This can take up to a minute.</p>}
      {error && <p role="alert" className="mt-2 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}

      {latest.isSuccess && latest.data === null && !ask.isPending && (
        <p className="mt-3 text-sm text-slate-500">No analysis yet. Ask a question to get an evidence-backed hypothesis.</p>
      )}
      {latest.data && <AnalysisView analysis={latest.data} />}
    </section>
  )
}

function AnalysisView({ analysis }: { analysis: Analysis }) {
  const evidence = new Map(analysis.evidence.map((e) => [e.id, e]))
  const confidence = confidenceLabel(analysis.confidence)

  return (
    <div className="mt-4 space-y-4 text-sm">
      <div className="flex flex-wrap items-center gap-3 text-xs text-slate-500">
        <span className="rounded bg-violet-100 px-1.5 py-0.5 font-medium text-violet-800">
          {analysis.analyzer === 'claude' ? `Claude · ${analysis.model}` : 'Rule-based'}
        </span>
        <span>“{analysis.question}” · asked by {analysis.requestedBy} · {timeAgo(analysis.createdAt)}</span>
      </div>

      {analysis.warnings.length > 0 && (
        <ul role="note" className="space-y-1 rounded-md bg-amber-50 px-3 py-2 text-xs text-amber-900">
          {analysis.warnings.map((w) => <li key={w}>{w}</li>)}
        </ul>
      )}

      <p>{analysis.summary}</p>

      <section className="rounded-lg bg-slate-50 p-3">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Likely cause</h3>
        <p className="mt-1 font-medium">{analysis.likelyCause.text}</p>
        <Citations claim={analysis.likelyCause} evidence={evidence} />
        <div className="mt-2 flex items-center gap-2">
          <div className="h-2 w-32 overflow-hidden rounded-full bg-slate-200" aria-hidden="true">
            <div className={`h-full ${confidence.className}`} style={{ width: `${Math.round(analysis.confidence * 100)}%` }} />
          </div>
          <span className="tabular-nums">{Math.round(analysis.confidence * 100)}%</span>
          <span className="text-xs text-slate-600">{confidence.label}</span>
        </div>
        <p className="mt-1 text-xs text-slate-500">{analysis.confidenceReason}</p>
      </section>

      <ClaimList title="Observed evidence" claims={analysis.observedEvidence} evidence={evidence} />
      <ClaimList title="Verify first (read-only checks)" claims={analysis.verification} evidence={evidence} ordered />

      <section>
        <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">Safe remediation</h3>
        <ol className="mt-1 list-decimal space-y-1 pl-5">
          {analysis.remediation.map((step) => (
            <li key={step.text}>
              {step.text}{' '}
              {step.requiresApproval && (
                <span className="rounded bg-rose-100 px-1.5 text-xs font-medium text-rose-700">Needs approval</span>
              )}
              <Citations claim={step} evidence={evidence} />
            </li>
          ))}
        </ol>
        <p className="mt-2 text-xs text-slate-500">
          Suggestions only. CloudPilot never runs these steps: a person verifies and performs them.
        </p>
      </section>

      <details className="rounded-lg border border-slate-200 p-3">
        <summary className="cursor-pointer text-xs font-semibold uppercase tracking-wide text-slate-500">
          Evidence the analysis was given ({analysis.evidence.length})
        </summary>
        <ul className="mt-2 space-y-1">
          {analysis.evidence.map((item) => <EvidenceRow key={item.id} item={item} />)}
        </ul>
      </details>
    </div>
  )
}

function ClaimList({ title, claims, evidence, ordered = false }: {
  title: string
  claims: Claim[]
  evidence: Map<string, EvidenceItem>
  ordered?: boolean
}) {
  if (claims.length === 0) return null
  const List = ordered ? 'ol' : 'ul'
  return (
    <section>
      <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</h3>
      <List className={`mt-1 space-y-1 pl-5 ${ordered ? 'list-decimal' : 'list-disc'}`}>
        {claims.map((claim) => (
          <li key={claim.text}>
            {claim.text} <Citations claim={claim} evidence={evidence} />
          </li>
        ))}
      </List>
    </section>
  )
}

function Citations({ claim, evidence }: { claim: Claim; evidence: Map<string, EvidenceItem> }) {
  return (
    <span className="ml-1 inline-flex flex-wrap gap-1 align-middle">
      {claim.citations.map((id) => (
        <a key={id} href={`#evidence-${id}`} title={evidence.get(id)?.text}
           className="rounded bg-slate-200 px-1 font-mono text-[11px] text-slate-700 hover:bg-violet-200">
          {id}
        </a>
      ))}
    </span>
  )
}

function EvidenceRow({ item }: { item: EvidenceItem }) {
  return (
    <li id={`evidence-${item.id}`} className="flex gap-2 text-xs">
      <span className="w-8 shrink-0 font-mono font-medium">{item.id}</span>
      <span className="min-w-0">
        <span className="text-slate-500">
          {[item.kind.toLowerCase(), item.resourceName, item.source].filter(Boolean).join(' · ')}:{' '}
        </span>
        <span className={item.kind === 'LOG' ? 'break-words font-mono' : ''}>{item.text}</span>
      </span>
    </li>
  )
}
