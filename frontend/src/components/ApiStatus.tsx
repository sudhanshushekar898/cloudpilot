import { useEffect, useState } from 'react'

type Status = 'checking' | 'online' | 'offline'

const LABELS: Record<Status, string> = {
  checking: 'Checking API…',
  online: 'API online',
  offline: 'API offline',
}

const DOT_COLOURS: Record<Status, string> = {
  checking: 'bg-slate-400',
  online: 'bg-emerald-500',
  offline: 'bg-rose-500',
}

export default function ApiStatus() {
  const [status, setStatus] = useState<Status>('checking')

  useEffect(() => {
    const controller = new AbortController()

    fetch('/api/v1/ping', { signal: controller.signal })
      .then((res) => setStatus(res.ok ? 'online' : 'offline'))
      .catch((err: unknown) => {
        // Unmounting aborts the request; that is not an outage.
        if (err instanceof DOMException && err.name === 'AbortError') return
        setStatus('offline')
      })

    return () => controller.abort()
  }, [])

  return (
    <span
      role="status"
      className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1 text-sm"
    >
      <span className={`h-2 w-2 rounded-full ${DOT_COLOURS[status]}`} aria-hidden="true" />
      {LABELS[status]}
    </span>
  )
}
