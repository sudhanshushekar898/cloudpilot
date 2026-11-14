import { useState, type FormEvent } from 'react'
import type { AlertRuleRequest, Operator, Severity } from '../api/alerts'
import { METRIC_TYPES, type MetricType, type ResourceType } from '../api/resources'
import { METRIC_LABELS } from '../lib/format'

const RESOURCE_TYPES: ResourceType[] = ['EC2', 'RDS', 'API', 'QUEUE', 'POD']
const SEVERITIES: Severity[] = ['CRITICAL', 'WARNING', 'INFO']

interface Props {
  initial: AlertRuleRequest
  title: string
  error: string | null
  saving: boolean
  onSubmit: (rule: AlertRuleRequest) => void
  onCancel: () => void
}

const inputClass = 'mt-1 w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm focus:border-brand-500 focus:outline-none'

export default function RuleForm({ initial, title, error, saving, onSubmit, onCancel }: Props) {
  const [rule, setRule] = useState<AlertRuleRequest>(initial)
  const set = <K extends keyof AlertRuleRequest>(key: K, value: AlertRuleRequest[K]) =>
    setRule((current) => ({ ...current, [key]: value }))

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onSubmit(rule)
  }

  return (
    <form onSubmit={handleSubmit} className="mb-6 rounded-xl border border-slate-200 bg-white p-4">
      <h2 className="mb-3 font-medium">{title}</h2>
      {error && (
        <p role="alert" className="mb-3 rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>
      )}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <label className="text-sm sm:col-span-2">
          Name
          <input className={inputClass} required minLength={3} maxLength={100} value={rule.name}
                 onChange={(e) => set('name', e.target.value)} />
        </label>
        <label className="text-sm">
          Metric
          <select className={inputClass} value={rule.metric} onChange={(e) => set('metric', e.target.value as MetricType)}>
            {METRIC_TYPES.map((m) => <option key={m} value={m}>{METRIC_LABELS[m]}</option>)}
          </select>
        </label>
        <label className="text-sm">
          Applies to
          <select className={inputClass} value={rule.resourceType ?? ''}
                  onChange={(e) => set('resourceType', (e.target.value || null) as ResourceType | null)}>
            <option value="">All resources</option>
            {RESOURCE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </label>
        <label className="text-sm">
          Operator
          <select className={inputClass} value={rule.operator} onChange={(e) => set('operator', e.target.value as Operator)}>
            <option value="GT">is above</option>
            <option value="LT">is below</option>
          </select>
        </label>
        <label className="text-sm">
          Threshold
          <input className={inputClass} type="number" step="any" required value={rule.threshold}
                 onChange={(e) => set('threshold', Number(e.target.value))} />
        </label>
        <label className="text-sm">
          For (minutes)
          <input className={inputClass} type="number" min={1} max={60} required value={rule.durationMinutes}
                 onChange={(e) => set('durationMinutes', Number(e.target.value))} />
        </label>
        <label className="text-sm">
          Severity
          <select className={inputClass} value={rule.severity} onChange={(e) => set('severity', e.target.value as Severity)}>
            {SEVERITIES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
      </div>
      <div className="mt-4 flex gap-2">
        <button type="submit" disabled={saving}
                className="rounded-md bg-brand-500 px-3 py-1.5 text-sm text-white hover:bg-brand-600 disabled:opacity-60">
          Save rule
        </button>
        <button type="button" onClick={onCancel} className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50">
          Cancel
        </button>
      </div>
    </form>
  )
}
