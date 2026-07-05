// @vitest-environment jsdom

import { createPinia, type Pinia } from 'pinia'
import { createApp, nextTick, type App as VueApp, type Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import ProductDetailView from '../catalog/views/ProductDetailView.vue'
import { getProduct } from '../catalog/api'
import { createOrder } from '../orders/api'
import { addCartItem, deleteCartItem, getCart, updateCartItem, type CartResponse } from './api'
import { useCartBadgeStore } from './store'
import CartView from './views/CartView.vue'

vi.mock('../catalog/api', () => ({ getProduct: vi.fn(), getCategories: vi.fn(), getProducts: vi.fn() }))
vi.mock('../orders/api', () => ({ createOrder: vi.fn() }))
vi.mock('./api', () => ({
  addCartItem: vi.fn(),
  deleteCartItem: vi.fn(),
  getCart: vi.fn(),
  updateCartItem: vi.fn(),
}))

function token(): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode({ alg: 'HS256' })}.${encode({ role: 'CUSTOMER', exp: 4_102_444_800 })}.signature`
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

let mounted: { app: VueApp; pinia: Pinia; router: Router; root: HTMLDivElement } | null = null

async function mountView(component: Component, path: string): Promise<NonNullable<typeof mounted>> {
  const pinia = createPinia()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/cart', name: 'cart', component: CartView },
      { path: '/products/:id', name: 'product-detail', component: ProductDetailView },
      { path: '/orders/:id', name: 'order-detail', component: { template: '<div>Order</div>' } },
      { path: '/', name: 'home', component: { template: '<div>Home</div>' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  const root = document.createElement('div')
  document.body.append(root)
  const app = createApp(component)
  app.use(pinia).use(router)
  app.mount(root)
  mounted = { app, pinia, router, root }
  return mounted
}

function button(root: HTMLElement, text: string): HTMLButtonElement {
  const match = [...root.querySelectorAll('button')].find((candidate) => candidate.textContent?.trim() === text)
  if (!(match instanceof HTMLButtonElement)) throw new Error(`Button not found: ${text}`)
  return match
}

beforeEach(() => {
  sessionStorage.clear()
  sessionStorage.setItem('shopflow.accessToken', token())
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

afterEach(() => {
  mounted?.app.unmount()
  mounted = null
})

describe('cart badge mutation flows', () => {
  it('refreshes the badge after adding a product and preserves it on mutation failure', async () => {
    vi.mocked(getProduct).mockResolvedValue({
      id: 101,
      name: 'Keyboard',
      description: 'Mechanical keyboard',
      price: 10,
      stock: 10,
      category: { id: 1, name: 'Electronics' },
    })
    vi.mocked(addCartItem).mockResolvedValue({
      id: 1,
      productId: 101,
      productName: 'Keyboard',
      currentUnitPrice: 10,
      quantity: 2,
      subtotal: 20,
      available: true,
    })
    vi.mocked(getCart).mockResolvedValue(cart([2]))
    const { pinia, root } = await mountView(ProductDetailView, '/products/101')
    await vi.waitFor(() => expect(getProduct).toHaveBeenCalledOnce())

    button(root, '加入購物車').click()
    await vi.waitFor(() => expect(getCart).toHaveBeenCalledOnce())
    expect(useCartBadgeStore(pinia).itemCount).toBe(2)

    useCartBadgeStore(pinia).setItemCountFromCart(cart([5]))
    vi.mocked(addCartItem).mockRejectedValue(new Error('mutation failed'))
    button(root, '加入購物車').click()
    await vi.waitFor(() => expect(addCartItem).toHaveBeenCalledTimes(2))
    await nextTick()
    expect(useCartBadgeStore(pinia).itemCount).toBe(5)
  })

  it('updates and removes items using the latest backend cart', async () => {
    vi.mocked(getCart)
      .mockResolvedValueOnce(cart([1]))
      .mockResolvedValueOnce(cart([4]))
      .mockResolvedValueOnce(cart([]))
    vi.mocked(updateCartItem).mockResolvedValue({
      ...cart([4]).items[0],
      quantity: 4,
      subtotal: 40,
    })
    vi.mocked(deleteCartItem).mockResolvedValue()
    const { pinia, root } = await mountView(CartView, '/cart')
    await vi.waitFor(() => expect(useCartBadgeStore(pinia).itemCount).toBe(1))

    button(root, '更新').click()
    await vi.waitFor(() => expect(useCartBadgeStore(pinia).itemCount).toBe(4))
    expect(updateCartItem).toHaveBeenCalledWith(1, { quantity: 1 })

    button(root, '刪除').click()
    await vi.waitFor(() => expect(useCartBadgeStore(pinia).itemCount).toBe(0))
    expect(deleteCartItem).toHaveBeenCalledWith(1)
  })

  it('clears the badge after successful checkout before navigating', async () => {
    vi.mocked(getCart).mockResolvedValueOnce(cart([2])).mockResolvedValueOnce(cart([]))
    vi.mocked(createOrder).mockResolvedValue({
      id: 701,
      status: 'PENDING_PAYMENT',
      totalAmount: 20,
      paidAt: null,
      createdAt: '2026-07-05T00:00:00Z',
      items: [],
    })
    const { pinia, root, router } = await mountView(CartView, '/cart')
    await vi.waitFor(() => expect(useCartBadgeStore(pinia).itemCount).toBe(2))

    button(root, '建立訂單').click()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/orders/701'))

    expect(useCartBadgeStore(pinia).itemCount).toBe(0)
  })

  it('keeps the badge cleared when checkout succeeds but cart reload fails', async () => {
    vi.mocked(getCart).mockResolvedValueOnce(cart([2])).mockRejectedValueOnce(new Error('reload failed'))
    vi.mocked(createOrder).mockResolvedValue({
      id: 702,
      status: 'PENDING_PAYMENT',
      totalAmount: 20,
      paidAt: null,
      createdAt: '2026-07-05T00:00:00Z',
      items: [],
    })
    const { pinia, root } = await mountView(CartView, '/cart')
    await vi.waitFor(() => expect(useCartBadgeStore(pinia).itemCount).toBe(2))

    button(root, '建立訂單').click()
    await vi.waitFor(() => expect(createOrder).toHaveBeenCalledOnce())
    await vi.waitFor(() => expect(getCart).toHaveBeenCalledTimes(2))

    expect(useCartBadgeStore(pinia).itemCount).toBe(0)
  })
})
