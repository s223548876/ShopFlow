import { describe, expect, it, vi } from 'vitest'

import { confirmThenRefresh, createAndNavigate, saveAndApply } from './actions'

describe('admin product actions', () => {
  it('navigates to the list after creating a product', async () => {
    const events: string[] = []

    await createAndNavigate(
      async () => { events.push('create') },
      async () => { events.push('navigate') },
    )

    expect(events).toEqual(['create', 'navigate'])
  })

  it('applies the server response after saving', async () => {
    const saved = { id: 501, name: 'Server name' }
    const apply = vi.fn()

    await saveAndApply(() => Promise.resolve(saved), apply)

    expect(apply).toHaveBeenCalledWith(saved)
  })

  it('refreshes from the server after confirmed deactivation', async () => {
    const calls: string[] = []

    const changed = await confirmThenRefresh(
      async () => { calls.push('confirm') },
      async () => { calls.push('deactivate') },
      async () => { calls.push('refresh') },
    )

    expect(changed).toBe(true)
    expect(calls).toEqual(['confirm', 'deactivate', 'refresh'])
  })

  it('does nothing when deactivation confirmation is dismissed', async () => {
    const mutate = vi.fn()
    const refresh = vi.fn()

    const changed = await confirmThenRefresh(
      () => Promise.reject(new Error('cancelled')),
      mutate,
      refresh,
    )

    expect(changed).toBe(false)
    expect(mutate).not.toHaveBeenCalled()
    expect(refresh).not.toHaveBeenCalled()
  })
})
