import ApiStatus from './components/ApiStatus'

const NAV_ITEMS = ['Dashboard', 'Alerts', 'Incidents', 'Runbooks', 'Reports']

export default function App() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-2">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-500 font-bold text-white">
              C
            </span>
            <span className="text-lg font-semibold">CloudPilot</span>
          </div>
          <ApiStatus />
        </div>
        <nav className="mx-auto flex max-w-6xl gap-1 overflow-x-auto px-4" aria-label="Main">
          {NAV_ITEMS.map((item) => (
            <span
              key={item}
              className="cursor-not-allowed whitespace-nowrap px-3 py-2 text-sm text-slate-400"
              title="Coming in a later milestone"
            >
              {item}
            </span>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-10">
        <h1 className="text-2xl font-semibold">Cloud monitoring and incident management</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          CloudPilot watches your cloud resources, groups related alerts into incidents and
          helps you find the root cause with evidence-backed AI analysis.
        </p>
      </main>
    </div>
  )
}
