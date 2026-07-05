import { expect, test } from '@playwright/test'

import {
  adminCredentials,
  createProductApi,
  customerCredentials,
  deactivateProductApi,
  loginApi,
  uniqueMarker,
} from './helpers'

let cleanup: { adminToken: string; productIds: number[] } | null = null

test.afterEach(async ({ request }) => {
  if (!cleanup) return
  await Promise.all(cleanup.productIds.map((id) => deactivateProductApi(request, cleanup!.adminToken, id)))
  cleanup = null
})

test('customer completes registration, cart, checkout, payment, and order review', async ({ page, request }) => {
  const adminToken = await loginApi(request, adminCredentials())
  cleanup = { adminToken, productIds: [] }
  const marker = uniqueMarker('customer-flow')
  const keyboard = await createProductApi(request, adminToken, marker, {
    name: `${marker}-keyboard`,
    price: 1299,
  })
  cleanup.productIds.push(keyboard.id)
  const mouse = await createProductApi(request, adminToken, marker, {
    name: `${marker}-mouse`,
    price: 599,
  })
  cleanup.productIds.push(mouse.id)
  const customer = customerCredentials('customer-flow')
  const productRequests: string[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/products') productRequests.push(request.url())
  })

  await page.goto('/register')
    await page.getByLabel('顯示名稱').fill(marker)
    await page.getByLabel('Email').fill(customer.email)
    await page.getByLabel('密碼（8–72 字元）').fill(customer.password)
    await page.getByRole('button', { name: '建立帳號' }).click()
    await expect(page).toHaveURL(/\/login\?registered=1$/)
    await expect(page.getByText('註冊完成，請登入')).toBeVisible()

    await page.getByLabel('Email').fill(customer.email)
    await page.getByLabel('密碼').fill(customer.password)
    await page.getByRole('button', { name: '登入' }).click()
    await expect(page.getByRole('link', { name: '購物車', exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: '我的訂單' })).toBeVisible()

    await page.goto('/products')
    await page.getByRole('textbox', { name: '搜尋商品' }).fill(`  ${keyboard.name}  `)
    await page.getByRole('button', { name: '套用' }).click()
    const keyboardCard = page.getByRole('article').filter({ hasText: keyboard.name })
    await expect(keyboardCard).toBeVisible()
    await keyboardCard.getByRole('link', { name: '查看詳情' }).click()
    await page.getByRole('spinbutton').fill('2')
    await page.getByRole('button', { name: '加入購物車' }).click()
    await expect(page.getByRole('link', { name: '購物車，目前共 2 件商品' })).toBeVisible()

    await page.goto('/products')
    await page.getByRole('textbox', { name: '搜尋商品' }).fill(mouse.name)
    await page.getByRole('button', { name: '套用' }).click()
    const mouseCard = page.getByRole('article').filter({ hasText: mouse.name })
    await mouseCard.getByRole('link', { name: '查看詳情' }).click()
    await page.getByRole('spinbutton').fill('3')
    await page.getByRole('button', { name: '加入購物車' }).click()
    await expect(page.getByRole('link', { name: '購物車，目前共 5 件商品' })).toBeVisible()

    await page.getByRole('link', { name: '購物車，目前共 5 件商品' }).click()
    const keyboardRow = page.getByRole('article').filter({ hasText: keyboard.name })
    await keyboardRow.getByRole('spinbutton').fill('4')
    await keyboardRow.getByRole('button', { name: '更新' }).click()
    await expect(page.getByRole('link', { name: '購物車，目前共 7 件商品' })).toBeVisible()

    const mouseRow = page.getByRole('article').filter({ hasText: mouse.name })
    await mouseRow.getByRole('button', { name: '刪除' }).click()
    await expect(page.getByRole('link', { name: '購物車，目前共 4 件商品' })).toBeVisible()
    await expect(mouseRow).toHaveCount(0)

    await page.getByRole('button', { name: '建立訂單' }).click()
    await expect(page).toHaveURL(/\/orders\/\d+$/)
    const orderId = Number(new URL(page.url()).pathname.split('/').pop())
    await expect(page.getByText('待付款', { exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: '購物車', exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: /購物車，目前共/ })).toHaveCount(0)
    const orderRow = page.getByRole('row').filter({ hasText: keyboard.name })
    await expect(orderRow).toContainText('1,299.00')
    await expect(orderRow).toContainText('4')
    await expect(orderRow).toContainText('5,196.00')
    await expect(page.getByText('5,196.00', { exact: true }).last()).toBeVisible()

    await page.getByRole('button', { name: '模擬付款' }).click()
    await expect(page.getByText('已付款', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '模擬付款' })).toHaveCount(0)
    await page.getByRole('link', { name: '返回訂單列表' }).click()
    await expect(page.getByRole('link', { name: new RegExp(`訂單 #${orderId}`) })).toContainText('已付款')

  for (const requestUrl of productRequests) {
    expect(new URL(requestUrl).searchParams.get('q')).not.toBe('%')
  }
})
