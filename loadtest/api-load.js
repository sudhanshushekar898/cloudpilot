// k6 load test for the read-heavy API paths a dashboard hits all day.
//
// Run against the Docker Compose stack (through nginx, like a browser):
//   docker run --rm --network cloudpilot_default -v "$PWD/loadtest:/scripts" \
//     grafana/k6:0.54.0 run /scripts/api-load.js
//
// Pass/fail thresholds are below; k6 exits non-zero when one is missed.
import http from 'k6/http'
import { check, group, sleep } from 'k6'

const BASE_URL = __ENV.BASE_URL || 'http://frontend:8081'
const EMAIL = __ENV.EMAIL || 'dev@cloudpilot.dev'
const PASSWORD = __ENV.PASSWORD || 'CloudPilot#2026'

export const options = {
  scenarios: {
    dashboard_users: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },   // ramp up
        { duration: '1m', target: 20 },    // steady load
        { duration: '15s', target: 0 },    // ramp down
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],                  // under 1% errors
    http_req_duration: ['p(95)<500'],                // p95 under 500 ms overall
    'http_req_duration{name:resources}': ['p(95)<300'],
    checks: ['rate>0.99'],
  },
}

/** Log in once; every virtual user reuses the token (like a signed-in browser tab). */
export function setup() {
  const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email: EMAIL, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
  })
  check(res, { 'login succeeded': (r) => r.status === 200 })
  const resources = http.get(`${BASE_URL}/api/v1/resources`, {
    headers: { Authorization: `Bearer ${res.json('accessToken')}` },
  })
  return { token: res.json('accessToken'), resourceIds: resources.json().map((r) => r.id) }
}

export default function (data) {
  const params = (name) => ({ headers: { Authorization: `Bearer ${data.token}` }, tags: { name } })

  group('dashboard poll', () => {
    const resources = http.get(`${BASE_URL}/api/v1/resources`, params('resources'))
    check(resources, { 'resources 200': (r) => r.status === 200 })

    const summary = http.get(`${BASE_URL}/api/v1/alerts/summary`, params('alert-summary'))
    check(summary, { 'alert summary 200': (r) => r.status === 200 })

    const incidents = http.get(`${BASE_URL}/api/v1/incidents?scope=active`, params('incidents'))
    check(incidents, { 'incidents 200': (r) => r.status === 200 })
  })

  // One in five iterations opens a resource's charts (the heaviest read: bucketed history).
  if (Math.random() < 0.2) {
    const id = data.resourceIds[Math.floor(Math.random() * data.resourceIds.length)]
    const history = http.get(`${BASE_URL}/api/v1/resources/${id}/metrics?minutes=60`, params('metric-history'))
    check(history, { 'history 200': (r) => r.status === 200 })
  }

  sleep(1)   // a user glances at the page before the next poll
}
