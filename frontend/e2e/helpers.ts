import { randomUUID } from 'node:crypto'
import { expect, type APIRequestContext, type APIResponse, type Page } from '@playwright/test'

export interface Credentials {
  email: string
  password: string
}

export interface E2eProduct {
  id: number
  name: string
  description: string
  price: number
  stock: number
}

export interface E2eOrder {
  id: number
  status: string
}

export function uniqueMarker(label: string): string {
  return `e2e-${label}-${Date.now()}-${randomUUID().slice(0, 8)}`
}

export function customerCredentials(label: string): Credentials {
  return {
    email: `${uniqueMarker(label)}@example.test`,
    password: `E2e-${randomUUID()}-Aa9!`,
  }
}

export function adminCredentials(): Credentials {
  const email = process.env.E2E_ADMIN_EMAIL
  const password = process.env.E2E_ADMIN_PASSWORD
  if (!email || !password) {
    throw new Error('Set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD')
  }
  return { email, password }
}

async function requireOk(response: APIResponse, action: string): Promise<APIResponse> {
  if (!response.ok()) {
    throw new Error(`${action} failed (${response.status()}): ${await response.text()}`)
  }
  return response
}

export async function loginViaUi(page: Page, credentials: Credentials): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('Email').fill(credentials.email)
  await page.getByLabel('密碼').fill(credentials.password)
  await page.getByRole('button', { name: '登入' }).click()
  await expect(page.getByRole('button', { name: '登出' })).toBeVisible()
}

export async function loginApi(api: APIRequestContext, credentials: Credentials): Promise<string> {
  const response = await requireOk(await api.post('/api/auth/login', { data: credentials }), 'login')
  return ((await response.json()) as { accessToken: string }).accessToken
}

export async function registerCustomerApi(
  api: APIRequestContext,
  credentials: Credentials,
  displayName: string,
): Promise<void> {
  await requireOk(await api.post('/api/auth/register', {
    data: { ...credentials, displayName },
  }), 'register customer')
}

export async function createProductApi(
  api: APIRequestContext,
  adminToken: string,
  marker: string,
  overrides: Partial<Pick<E2eProduct, 'name' | 'description' | 'price' | 'stock'>> = {},
): Promise<E2eProduct> {
  const categories = await requireOk(await api.get('/api/categories'), 'load categories')
  const categoryId = ((await categories.json()) as Array<{ id: number }>)[0]?.id
  if (!categoryId) throw new Error('No category is available for E2E product setup')

  const response = await requireOk(await api.post('/api/admin/products', {
    headers: { Authorization: `Bearer ${adminToken}` },
    data: {
      categoryId,
      name: overrides.name ?? `${marker}-product`,
      description: overrides.description ?? `${marker} E2E product`,
      price: overrides.price ?? 1299,
      stock: overrides.stock ?? 30,
    },
  }), 'create product')
  return (await response.json()) as E2eProduct
}

export async function deactivateProductApi(
  api: APIRequestContext,
  adminToken: string,
  productId: number,
): Promise<void> {
  const response = await api.delete(`/api/admin/products/${productId}`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  })
  if (!response.ok() && response.status() !== 404) {
    throw new Error(`deactivate product failed (${response.status()}): ${await response.text()}`)
  }
}

export async function createPaidOrderApi(
  api: APIRequestContext,
  adminToken: string,
  label: string,
): Promise<{ credentials: Credentials; order: E2eOrder; product: E2eProduct }> {
  const marker = uniqueMarker(label)
  const credentials = customerCredentials(label)
  const product = await createProductApi(api, adminToken, marker)
  await registerCustomerApi(api, credentials, marker)
  const customerToken = await loginApi(api, credentials)
  const headers = { Authorization: `Bearer ${customerToken}` }
  await requireOk(await api.post('/api/cart/items', {
    headers,
    data: { productId: product.id, quantity: 2 },
  }), 'add E2E order item')
  const orderResponse = await requireOk(await api.post('/api/orders', { headers }), 'create E2E order')
  const order = (await orderResponse.json()) as E2eOrder
  const paid = await requireOk(await api.post(`/api/orders/${order.id}/pay`, { headers }), 'pay E2E order')
  return { credentials, order: (await paid.json()) as E2eOrder, product }
}
