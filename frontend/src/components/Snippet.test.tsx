import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import Snippet from './Snippet'

describe('Snippet', () => {
  it('highlights words wrapped in [[ ]] with <mark>', () => {
    const { container } = render(<Snippet text="Errors like [[too]] many [[clients]] already" />)

    const marks = container.querySelectorAll('mark')
    expect([...marks].map((m) => m.textContent)).toEqual(['too', 'clients'])
    expect(container.textContent).toBe('Errors like too many clients already')
  })

  it('never renders HTML from the snippet', () => {
    const { container } = render(<Snippet text={'<img src=x onerror=alert(1)> [[db]]'} />)

    expect(container.querySelector('img')).toBeNull()
    expect(container.textContent).toContain('<img src=x onerror=alert(1)>')
  })
})
