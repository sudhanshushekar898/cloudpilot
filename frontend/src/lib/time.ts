const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

export function timeAgo(iso: string, now: Date = new Date()): string {
  const diff = now.getTime() - new Date(iso).getTime()
  if (diff < MINUTE) return 'just now'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)} min ago`
  if (diff < DAY) return `${Math.floor(diff / HOUR)} h ago`
  return `${Math.floor(diff / DAY)} d ago`
}

export function formatDuration(ms: number): string {
  if (ms < MINUTE) return '<1 min'
  const hours = Math.floor(ms / HOUR)
  const minutes = Math.floor((ms % HOUR) / MINUTE)
  if (hours === 0) return `${minutes} min`
  return minutes === 0 ? `${hours} h` : `${hours} h ${minutes} min`
}
