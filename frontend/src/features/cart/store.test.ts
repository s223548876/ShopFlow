import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getCart, type CartResponse } from './api'
import { useCartBadgeStore } from './store'

vi.mock('./api', () => ({ getCart: vi.fn() }))

class MemoryStorage implements Storage {
  private values = new Map<string, string>()
  get length(): number { return this.values.size }
  clear(): void { this.values.clear() }
  getItem(key: string): string | null { return this.values.get(key) ?? null }
  key(index: number): string | null { return [...this.values.keys()][index] ?? null }
  removeItem(key: string): void { this.values.delete(key) }
  setItem(key: string, value: string): void { this.values.set(key, value) }
}

function token(role: 'CUSTOMER' | 'ADMIN'): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode({ alg: 'HS256' })}.${encode({ role, exp: 4_102_444_800 })}.signature`
}

function cart(quantities: number[]): CartResponse {
  return {
    id: 1,
    items: quantities.map((quantity, index) => ({
      id: index + 1,
      productId: index + 101,
      productName: `Product ${index + 1}`,
      currentUnitPrice: 10,
      quantity,
      subtotal: 10 * quantity,
      available: true,
    })),
    estimatedTotal: quantities.reduce((sum, quantity) => sum + quantity * 10, 0),
  }
}

beforeEach(() => {
  Object.defineProperty(globalThis, 'sessionStorage', {
    configurable: true,
    value: new MemoryStorage(),
  })
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('cart badge store', () => {
  it('sums item quantities from the backend cart response', () => {
    const store = useCartBadgeStore()

    store.setItemCountFromCart(cart([2, 3]))

    expect(store.itemCount).toBe(5)
  })

  it('refreshes only for an authenticated customer', async () => {
    vi.mocked(getCart).mockResolvedValue(cart([4]))
    const anonymous = useCartBadgeStore()
    await anonymous.refreshCartBadge()
    expect(getCart).not.toHaveBeenCalled()

    sessionStorage.setItem('shopflow.accessToken', token('ADMIN'))
    setActivePinia(createPinia())
    const admin = useCartBadgeStore()
    await admin.refreshCartBadge()
    expect(getCart).not.toHaveBeenCalled()

    sessionStorage.setItem('shopflow.accessToken', token('CUSTOMER'))
    setActivePinia(createPinia())
    const customer = useCartBadgeStore()
    await customer.refreshCartBadge()
    expect(getCart).toHaveBeenCalledOnce()
    expect(customer.itemCount).toBe(4)
  })

  it('preserves the current count when refresh fails', async () => {
    sessionStorage.setItem('shopflow.accessToken', token('CUSTOMER'))
    const store = useCartBadgeStore()
    store.setItemCountFromCart(cart([3]))
    vi.mocked(getCart).mockRejectedValue(new Error('network error'))

    await expect(store.refreshCartBadge()).rejects.toThrow('network error')

    expect(store.itemCount).toBe(3)
  })

  it('clears immediately and ignores an older pending response', async () => {
    sessionStorage.setItem('shopflow.accessToken', token('CUSTOMER'))
    let resolveCart!: (value: CartResponse) => void
    vi.mocked(getCart).mockReturnValue(new Promise((resolve) => { resolveCart = resolve }))
    const store = useCartBadgeStore()

    const refresh = store.refreshCartBadge()
    await vi.waitFor(() => expect(getCart).toHaveBeenCalledOnce())
    const signal = vi.mocked(getCart).mock.calls[0][0]
    store.clearCartBadge()
    resolveCart(cart([9]))
    await refresh

    expect(signal).toBeInstanceOf(AbortSignal)
    expect(signal?.aborted).toBe(true)
    expect(store.itemCount).toBe(0)
    expect(store.isLoading).toBe(false)
  })
})
