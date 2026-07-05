// @vitest-environment jsdom

import { createPinia, type Pinia } from 'pinia'
import { createApp, defineComponent, nextTick, type App as VueApp } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App.vue'
import { login } from './features/auth/api'
import { useAuthStore } from './features/auth/store'
import { getCart, type CartResponse } from './features/cart/api'
import { useCartBadgeStore } from './features/cart/store'

vi.mock('./features/auth/api', () => ({ login: vi.fn(), register: vi.fn() }))
vi.mock('./features/cart/api', () => ({ getCart: vi.fn() }))

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

let mounted: { app: VueApp; pinia: Pinia; router: Router; root: HTMLDivElement } | null = null

async function mountApp(): Promise<NonNullable<typeof mounted>> {
  const pinia = createPinia()
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: defineComponent({ template: '<div>Home</div>' }) },
      { path: '/cart', name: 'cart', component: defineComponent({ template: '<div>Cart</div>' }) },
      { path: '/products', name: 'products', component: defineComponent({ template: '<div>Products</div>' }) },
      { path: '/orders', name: 'orders', component: defineComponent({ template: '<div>Orders</div>' }) },
    ],
  })
  await router.push('/')
  await router.isReady()
  const root = document.createElement('div')
  document.body.append(root)
  const app = createApp(App)
  app.use(pinia).use(router)
  app.mount(root)
  mounted = { app, pinia, router, root }
  return mounted
}

async function settle(): Promise<void> {
  await vi.waitFor(() => expect(vi.mocked(getCart).mock.settledResults.length).toBeGreaterThan(0))
  await nextTick()
}

beforeEach(() => {
  sessionStorage.clear()
  document.body.innerHTML = ''
  vi.clearAllMocks()
})

afterEach(() => {
  mounted?.app.unmount()
  mounted = null
})

describe('cart badge navigation', () => {
  it('shows the customer total quantity and navigates to cart', async () => {
    sessionStorage.setItem('shopflow.accessToken', token('CUSTOMER'))
    vi.mocked(getCart).mockResolvedValue(cart([2, 3]))
    const { root, router } = await mountApp()
    await settle()

    expect(root.querySelector('.el-badge__content')?.textContent).toBe('5')
    const cartLink = root.querySelector<HTMLAnchorElement>('a[href="/cart"]')
    expect(cartLink).not.toBeNull()
    cartLink?.click()
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/cart'))
  })

  it('shows totals above 99 exactly and exposes the count in the cart link label', async () => {
    sessionStorage.setItem('shopflow.accessToken', token('CUSTOMER'))
    vi.mocked(getCart).mockResolvedValue(cart([105]))
    const { root } = await mountApp()
    await settle()

    expect(root.querySelector('.el-badge__content')?.textContent).toBe('105')
    expect(root.querySelector('a[href="/cart"]')?.getAttribute('aria-label')).toBe('購物車，目前共 105 件商品')
  })

  it('hides the badge number for an empty cart', async () => {
    sessionStorage.setItem('shopflow.accessToken', token('CUSTOMER'))
    vi.mocked(getCart).mockResolvedValue(cart([]))
    const { root } = await mountApp()
    await settle()

    expect(root.querySelector('a[href="/cart"]')).not.toBeNull()
    expect(root.querySelector('.el-badge__content')).toBeNull()
  })

  it.each(['ADMIN', null] as const)('does not load or display cart for %s', async (role) => {
    if (role) sessionStorage.setItem('shopflow.accessToken', token(role))
    const { root } = await mountApp()
    await nextTick()

    expect(getCart).not.toHaveBeenCalled()
    expect(root.querySelector('a[href="/cart"]')).toBeNull()
    expect(root.querySelector('.el-badge__content')).toBeNull()
  })

  it('loads the badge after customer login without blocking navigation', async () => {
    vi.mocked(getCart).mockResolvedValue(cart([2]))
    vi.mocked(login).mockResolvedValue({
      accessToken: token('CUSTOMER'),
      tokenType: 'Bearer',
      expiresIn: 1800,
    })
    const { pinia, root } = await mountApp()

    await useAuthStore(pinia).login({ email: 'customer@example.com', password: 'password-123' })
    await settle()

    expect(getCart).toHaveBeenCalledOnce()
    expect(root.querySelector('.el-badge__content')?.textContent).toBe('2')
  })

  it('replaces the badge when a different customer token is set', async () => {
    sessionStorage.setItem('shopflow.accessToken', token('CUSTOMER'))
    vi.mocked(getCart).mockResolvedValueOnce(cart([3])).mockResolvedValueOnce(cart([1]))
    vi.mocked(login).mockResolvedValue({
      accessToken: `${token('CUSTOMER')}new`,
      tokenType: 'Bearer',
      expiresIn: 1800,
    })
    const { pinia, root } = await mountApp()
    await settle()

    await useAuthStore(pinia).login({ email: 'other@example.com', password: 'password-123' })
    await vi.waitFor(() => expect(getCart).toHaveBeenCalledTimes(2))
    await nextTick()

    expect(root.querySelector('.el-badge__content')?.textContent).toBe('1')
  })

  it('clears the badge when the auth session is cleared by logout or a 401 hook', async () => {
    sessionStorage.setItem('shopflow.accessToken', token('CUSTOMER'))
    vi.mocked(getCart).mockResolvedValue(cart([3]))
    const { pinia, root } = await mountApp()
    await settle()

    useAuthStore(pinia).logout()
    await nextTick()

    expect(useCartBadgeStore(pinia).itemCount).toBe(0)
    expect(root.querySelector('.el-badge__content')).toBeNull()
  })
})
