import { expect, test } from '@playwright/test'

import {
  adminCredentials,
  createProductApi,
  customerCredentials,
  deactivateProductApi,
  loginApi,
  loginViaUi,
  registerCustomerApi,
  uniqueMarker,
} from './helpers'

let cleanup: { adminToken: string; productIds: number[] } | null = null

test.afterEach(async ({ request }) => {
  if (!cleanup) return
  await Promise.all(cleanup.productIds.map((id) => deactivateProductApi(request, cleanup!.adminToken, id)))
  cleanup = null
})

test('anonymous users are redirected from protected routes', async ({ page }) => {
  for (const path of ['/cart', '/orders', '/admin/products', '/admin/orders']) {
    await page.goto(path)
    await expect(page).toHaveURL((url) => url.pathname === '/login' && url.searchParams.get('redirect') === path)
    const current = new URL(page.url())
    expect(current.pathname).toBe('/login')
    expect(current.searchParams.get('redirect')).toBe(path)
  }
})

test('customer can use customer routes but not admin routes', async ({ page, request }) => {
  const credentials = customerCredentials('security-customer')
  await registerCustomerApi(request, credentials, uniqueMarker('security-customer'))
  await loginViaUi(page, credentials)

  await page.goto('/cart')
  await expect(page.getByRole('heading', { name: '購物車' })).toBeVisible()
  await page.goto('/orders')
  await expect(page.getByRole('heading', { name: '我的訂單' })).toBeVisible()
  await expect(page.getByRole('link', { name: '商品管理' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: '訂單管理' })).toHaveCount(0)

  await page.goto('/admin/products')
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByText('沒有權限使用此頁面')).toBeVisible()
  await page.goto('/admin/orders')
  await expect(page).toHaveURL(/\/$/)
})

test('admin can use admin routes without requesting the cart badge', async ({ page }) => {
  const cartRequests: string[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/cart')) cartRequests.push(request.url())
  })

  await loginViaUi(page, adminCredentials())
  await page.goto('/admin/products')
  await expect(page.getByRole('heading', { name: '商品管理' })).toBeVisible()
  await page.goto('/admin/orders')
  await expect(page.getByRole('heading', { name: '訂單管理' })).toBeVisible()
  await expect(page.getByRole('link', { name: /購物車/ })).toHaveCount(0)
  expect(cartRequests).toEqual([])
})

test('a cart 401 clears the customer session and badge', async ({ page, request }) => {
  const adminToken = await loginApi(request, adminCredentials())
  cleanup = { adminToken, productIds: [] }
  const marker = uniqueMarker('security-401')
  const product = await createProductApi(request, adminToken, marker)
  cleanup.productIds.push(product.id)
  const credentials = customerCredentials('security-401')

  await registerCustomerApi(request, credentials, marker)
    const customerToken = await loginApi(request, credentials)
    const addResponse = await request.post('/api/cart/items', {
      headers: { Authorization: `Bearer ${customerToken}` },
      data: { productId: product.id, quantity: 2 },
    })
    expect(addResponse.status()).toBe(201)

    await loginViaUi(page, credentials)
    const cartLink = page.getByRole('link', { name: '購物車，目前共 2 件商品' })
    await expect(cartLink).toBeVisible()
    await page.route('**/api/cart', async (route) => {
      if (route.request().method() !== 'GET') return route.continue()
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 401,
          code: 'AUTHENTICATION_REQUIRED',
          message: 'Authentication is required',
          path: '/api/cart',
          fieldErrors: [],
        }),
      })
    })
    await cartLink.click()
    await expect(page).toHaveURL((url) =>
      url.pathname === '/login' && url.searchParams.get('redirect') === '/cart',
    )
  await expect(page.getByRole('link', { name: /購物車/ })).toHaveCount(0)
  expect(await page.evaluate(() => sessionStorage.getItem('shopflow.accessToken'))).toBeNull()
})
