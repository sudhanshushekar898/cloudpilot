import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { MetricPoint, MetricType } from '../api/resources'
import { formatMetric, METRIC_LABELS } from '../lib/format'

/** Where a metric turns the resource "degraded" (same thresholds as the backend's HealthEvaluator). */
const THRESHOLDS: Partial<Record<MetricType, number>> = {
  CPU: 90,
  MEMORY: 90,
  LATENCY_P95: 1000,
  ERROR_RATE: 5,
  AVAILABILITY: 99,
}

const timeFormat = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' })

interface Props {
  metric: MetricType
  points: MetricPoint[]
}

export default function MetricChart({ metric, points }: Props) {
  const data = points.map((p) => ({ time: new Date(p.timestamp).getTime(), value: p.value }))
  const latest = points.at(-1)?.value
  const threshold = THRESHOLDS[metric]

  return (
    <figure className="rounded-xl border border-slate-200 bg-white p-4">
      <figcaption className="mb-2 flex items-baseline justify-between">
        <span className="text-sm font-medium">{METRIC_LABELS[metric]}</span>
        <span className="text-sm tabular-nums text-slate-600">{formatMetric(metric, latest)}</span>
      </figcaption>
      {data.length === 0 ? (
        <p className="grid h-40 place-items-center text-sm text-slate-400">No data in this range</p>
      ) : (
        <div className="h-40">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
              <CartesianGrid stroke="#e2e8f0" vertical={false} />
              <XAxis
                dataKey="time"
                type="number"
                domain={['dataMin', 'dataMax']}
                scale="time"
                tickFormatter={(t: number) => timeFormat.format(t)}
                tick={{ fontSize: 11, fill: '#64748b' }}
                minTickGap={40}
              />
              <YAxis
                width={56}
                tick={{ fontSize: 11, fill: '#64748b' }}
                tickFormatter={(v: number) => formatMetric(metric, v)}
                domain={metric === 'AVAILABILITY' ? ['auto', 100] : [0, 'auto']}
              />
              <Tooltip
                labelFormatter={(t) => timeFormat.format(Number(t))}
                formatter={(v) => [formatMetric(metric, Number(v)), METRIC_LABELS[metric]]}
              />
              {threshold !== undefined && (
                <ReferenceLine y={threshold} stroke="#f59e0b" strokeDasharray="4 4" ifOverflow="extendDomain" />
              )}
              <Line type="monotone" dataKey="value" stroke="#2563eb" strokeWidth={2} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </figure>
  )
}
