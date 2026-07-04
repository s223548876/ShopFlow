import { describe, expect, it, vi } from 'vitest'

import { checkoutAndNavigate, refreshAfter } from './actions'

describe('order UI actions', () => {
  it('refreshes the cart and navigates to the created order', async () => {
    const events: string[] = []
    const create = vi.fn(async () => {
      events.push('create')
      return { id: 701 }
    })
    const refreshCart = vi.fn(async () => { events.push('refresh-cart') })
    const navigate = vi.fn(async (id: number) => { events.push(`navigate-${id}`) })

    await checkoutAndNavigate(create, refreshCart, navigate)

    expect(events).toEqual(['create', 'refresh-cart', 'navigate-701'])
  })

  it('reloads server state after a successful payment or status update', async () => {
    const events: string[] = []
    await refreshAfter(
      async () => { events.push('mutate') },
      async () => { events.push('reload') },
    )
    expect(events).toEqual(['mutate', 'reload'])
  })
})
