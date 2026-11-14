import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { AlertRule } from '../api/alerts'
import { jsonResponse, mockApi, renderWithProviders } from '../test/utils'
import AlertRulesPage from './AlertRulesPage'

const apiErrors: AlertRule = {
  id: 'r1',
  name: 'API error rate above 5%',
  metric: 'ERROR_RATE',
  operator: 'GT',
  threshold: 5,
  durationMinutes: 5,
  severity: 'CRITICAL',
  resourceType: 'API',
  enabled: true,
  updatedAt: '2026-11-15T09:00:00Z',
}

const availability: AlertRule = {
  ...apiErrors,
  id: 'r2',
  name: 'Availability below 99%',
  metric: 'AVAILABILITY',
  operator: 'LT',
  threshold: 99,
  durationMinutes: 2,
  resourceType: null,
}

function bodyOf(spy: ReturnType<typeof vi.spyOn>, method: string) {
  const call = spy.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === method)!
  return JSON.parse((call[1] as RequestInit).body as string)
}

describe('AlertRulesPage', () => {
  it('lists rules with a readable condition and scope', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/alert-rules': [apiErrors, availability] }))
    renderWithProviders(<AlertRulesPage />, { role: 'DEVELOPER' })

    const row = (await screen.findByText('API error rate above 5%')).closest('tr')!
    expect(within(row).getByText('Error rate > 5% for 5 min')).toBeInTheDocument()
    expect(within(row).getByText('API')).toBeInTheDocument()
    const all = screen.getByText('Availability below 99%').closest('tr')!
    expect(within(all).getByText('Availability < 99% for 2 min')).toBeInTheDocument()
    expect(within(all).getByText('All resources')).toBeInTheDocument()
  })

  it('hides editing controls from non-admins', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(mockApi({ '/api/v1/alert-rules': [apiErrors] }))
    renderWithProviders(<AlertRulesPage />, { role: 'SRE' })

    await screen.findByText('API error rate above 5%')
    expect(screen.queryByRole('button', { name: 'New rule' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Disable/ })).not.toBeInTheDocument()
    expect(screen.getByText('Only admins can change alert rules.')).toBeInTheDocument()
  })

  it('lets an admin disable a rule', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({ '/api/v1/alert-rules': [apiErrors], 'PUT /api/v1/alert-rules/r1': { ...apiErrors, enabled: false } }),
    )
    renderWithProviders(<AlertRulesPage />, { role: 'ADMIN' })

    await userEvent.click(await screen.findByRole('button', { name: 'Disable API error rate above 5%' }))

    expect(bodyOf(fetchSpy, 'PUT')).toEqual({
      name: 'API error rate above 5%',
      metric: 'ERROR_RATE',
      operator: 'GT',
      threshold: 5,
      durationMinutes: 5,
      severity: 'CRITICAL',
      resourceType: 'API',
      enabled: false,
    })
  })

  it('lets an admin create a rule', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(
      mockApi({ '/api/v1/alert-rules': [], 'POST /api/v1/alert-rules': { ...apiErrors, id: 'new' } }),
    )
    renderWithProviders(<AlertRulesPage />, { role: 'ADMIN' })

    await userEvent.click(await screen.findByRole('button', { name: 'New rule' }))
    await userEvent.type(screen.getByLabelText('Name'), 'Web CPU high')
    await userEvent.selectOptions(screen.getByLabelText('Metric'), 'CPU')
    await userEvent.clear(screen.getByLabelText('Threshold'))
    await userEvent.type(screen.getByLabelText('Threshold'), '80')
    await userEvent.selectOptions(screen.getByLabelText('Applies to'), 'EC2')
    await userEvent.click(screen.getByRole('button', { name: 'Save rule' }))

    expect(bodyOf(fetchSpy, 'POST')).toMatchObject({
      name: 'Web CPU high',
      metric: 'CPU',
      operator: 'GT',
      threshold: 80,
      resourceType: 'EC2',
      enabled: true,
    })
  })

  it('shows the server error when saving fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => {
      if ((init as RequestInit | undefined)?.method === 'POST') {
        return Promise.resolve(jsonResponse({ detail: "A rule named 'Dup' already exists" }, 409))
      }
      return mockApi({ '/api/v1/alert-rules': [] })(input, init)
    })
    renderWithProviders(<AlertRulesPage />, { role: 'ADMIN' })

    await userEvent.click(await screen.findByRole('button', { name: 'New rule' }))
    await userEvent.type(screen.getByLabelText('Name'), 'Dup')
    await userEvent.click(screen.getByRole('button', { name: 'Save rule' }))

    expect(await screen.findByRole('alert')).toHaveTextContent("A rule named 'Dup' already exists")
  })
})
