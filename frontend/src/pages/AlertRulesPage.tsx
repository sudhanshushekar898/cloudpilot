import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  createAlertRule,
  listAlertRules,
  updateAlertRule,
  type AlertRule,
  type AlertRuleRequest,
} from '../api/alerts'
import { METRIC_TYPES } from '../api/resources'
import { useAuth } from '../auth/AuthContext'
import RuleForm from '../components/RuleForm'
import SeverityBadge from '../components/SeverityBadge'
import { ApiError } from '../lib/api'
import { formatMetric, METRIC_LABELS } from '../lib/format'

function describeCondition(rule: AlertRule) {
  const symbol = rule.operator === 'GT' ? '>' : '<'
  return `${METRIC_LABELS[rule.metric]} ${symbol} ${formatMetric(rule.metric, rule.threshold)} for ${rule.durationMinutes} min`
}

function toRequest(rule: AlertRule): AlertRuleRequest {
  return {
    name: rule.name,
    metric: rule.metric,
    operator: rule.operator,
    threshold: rule.threshold,
    durationMinutes: rule.durationMinutes,
    severity: rule.severity,
    resourceType: rule.resourceType,
    enabled: rule.enabled,
  }
}

const EMPTY_RULE: AlertRuleRequest = {
  name: '',
  metric: METRIC_TYPES[0]!,
  operator: 'GT',
  threshold: 90,
  durationMinutes: 5,
  severity: 'WARNING',
  resourceType: null,
  enabled: true,
}

export default function AlertRulesPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'ADMIN'
  const queryClient = useQueryClient()
  const { data, isPending } = useQuery({ queryKey: ['alert-rules'], queryFn: listAlertRules })

  // null = form closed; { id: undefined } = creating; { id } = editing that rule.
  const [editing, setEditing] = useState<{ id?: string; initial: AlertRuleRequest } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: ({ id, rule }: { id?: string; rule: AlertRuleRequest }) =>
      id ? updateAlertRule(id, rule) : createAlertRule(rule),
    onSuccess: () => {
      setEditing(null)
      setError(null)
      void queryClient.invalidateQueries({ queryKey: ['alert-rules'] })
      void queryClient.invalidateQueries({ queryKey: ['alerts'] })
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : 'Could not save the rule.'),
  })

  return (
    <section>
      <Link to="/alerts" className="text-sm text-brand-600 hover:underline">← Alerts</Link>
      <div className="mt-2 mb-4 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Alert rules</h1>
          {!isAdmin && <p className="text-sm text-slate-600">Only admins can change alert rules.</p>}
        </div>
        {isAdmin && !editing && (
          <button
            onClick={() => {
              setError(null)
              setEditing({ initial: EMPTY_RULE })
            }}
            className="rounded-md bg-brand-500 px-3 py-1.5 text-sm text-white hover:bg-brand-600"
          >
            New rule
          </button>
        )}
      </div>

      {editing && (
        <RuleForm
          initial={editing.initial}
          title={editing.id ? 'Edit rule' : 'New rule'}
          error={error}
          saving={save.isPending}
          onCancel={() => setEditing(null)}
          onSubmit={(rule) => save.mutate({ id: editing.id, rule })}
        />
      )}

      {isPending && <p className="text-slate-500">Loading rules…</p>}
      {data && (
        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-2">Rule</th>
                <th className="px-4 py-2">Condition</th>
                <th className="px-4 py-2">Applies to</th>
                <th className="px-4 py-2">Severity</th>
                <th className="px-4 py-2">Status</th>
                {isAdmin && <th className="px-4 py-2"><span className="sr-only">Actions</span></th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {data.map((rule) => (
                <tr key={rule.id} className={rule.enabled ? '' : 'text-slate-400'}>
                  <td className="px-4 py-2 font-medium">{rule.name}</td>
                  <td className="px-4 py-2">{describeCondition(rule)}</td>
                  <td className="px-4 py-2">{rule.resourceType ?? 'All resources'}</td>
                  <td className="px-4 py-2"><SeverityBadge severity={rule.severity} /></td>
                  <td className="px-4 py-2">{rule.enabled ? 'Enabled' : 'Disabled'}</td>
                  {isAdmin && (
                    <td className="whitespace-nowrap px-4 py-2 text-right">
                      <button
                        onClick={() => {
                          setError(null)
                          setEditing({ id: rule.id, initial: toRequest(rule) })
                        }}
                        className="mr-2 text-brand-600 hover:underline"
                        aria-label={`Edit ${rule.name}`}
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => save.mutate({ id: rule.id, rule: { ...toRequest(rule), enabled: !rule.enabled } })}
                        disabled={save.isPending}
                        className="text-slate-600 hover:underline"
                        aria-label={`${rule.enabled ? 'Disable' : 'Enable'} ${rule.name}`}
                      >
                        {rule.enabled ? 'Disable' : 'Enable'}
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
