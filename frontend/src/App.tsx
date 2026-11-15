import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth from './auth/RequireAuth'
import AppLayout from './components/AppLayout'
import AlertRulesPage from './pages/AlertRulesPage'
import AlertsPage from './pages/AlertsPage'
import DashboardPage from './pages/DashboardPage'
import IncidentDetailPage from './pages/IncidentDetailPage'
import IncidentsPage from './pages/IncidentsPage'
import LoginPage from './pages/LoginPage'
import ReportsPage from './pages/ReportsPage'
import RunbooksPage from './pages/RunbooksPage'

// Recharts is large; load it only when someone opens a resource's charts.
const ResourceDetailPage = lazy(() => import('./pages/ResourceDetailPage'))
// react-markdown is only needed when reading a runbook.
const RunbookPage = lazy(() => import('./pages/RunbookPage'))

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="alerts" element={<AlertsPage />} />
          <Route path="alerts/rules" element={<AlertRulesPage />} />
          <Route path="incidents" element={<IncidentsPage />} />
          <Route path="incidents/:id" element={<IncidentDetailPage />} />
          <Route path="runbooks" element={<RunbooksPage />} />
          <Route path="reports" element={<ReportsPage />} />
          <Route
            path="runbooks/:slug"
            element={
              <Suspense fallback={<p className="text-slate-500">Loading…</p>}>
                <RunbookPage />
              </Suspense>
            }
          />
          <Route
            path="resources/:id"
            element={
              <Suspense fallback={<p className="text-slate-500">Loading…</p>}>
                <ResourceDetailPage />
              </Suspense>
            }
          />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
