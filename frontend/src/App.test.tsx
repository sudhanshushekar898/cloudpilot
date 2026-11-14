import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

describe('App', () => {
  it('renders the product name and the API status badge', () => {
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise(() => {}))

    render(<App />)

    expect(screen.getByText('CloudPilot')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Checking API…')
  })
})
