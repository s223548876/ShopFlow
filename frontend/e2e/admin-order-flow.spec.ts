import { expect, test } from '@playwright/test'

import {
  adminCredentials,
  createPaidOrderApi,
  deactivateProductApi,
  loginApi,
  loginViaUi,
} from './helpers'

let cleanup: { adminToken: string; productIds: number[] } | null = null

test.afterEach(async ({ request }) => {
  if (!cleanup) return
  await Promise.all(cleanup.productIds.map((id) => deactivateProductApi(request, cleanup!.adminToken, id)))
  cleanup = null
})

test('admin filters orders, advances one order, and confirms cancellation of another', async ({ page, request }) => {
  const credentials = adminCredentials()
  const adminToken = await loginApi(request, credentials)
  cleanup = { adminToken, productIds: [] }
  const advancing = await createPaidOrderApi(request, adminToken, 'admin-order-advance')
  cleanup.productIds.push(advancing.product.id)
  const cancelling = await createPaidOrderApi(request, adminToken, 'admin-order-cancel')
  cleanup.productIds.push(cancelling.product.id)

  await loginViaUi(page, credentials)
    await page.getByRole('link', { name: '訂單管理' }).click()
    await page.getByRole('combobox', { name: '訂單狀態' }).press('ArrowDown')
    await page.getByRole('option', { name: '已付款', exact: true }).click()
    await page.getByRole('button', { name: '套用' }).click()
    const advancingLink = page.getByRole('link', { name: new RegExp(`訂單 #${advancing.order.id}`) })
    const cancellingLink = page.getByRole('link', { name: new RegExp(`訂單 #${cancelling.order.id}`) })
    await expect(advancingLink).toContainText('已付款')
    await expect(cancellingLink).toContainText('已付款')

    await advancingLink.click()
    await expect(page.getByRole('button', { name: '模擬付款' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '處理中' })).toBeVisible()
    await expect(page.getByRole('button', { name: '已取消' })).toBeVisible()
    await expect(page.getByRole('button', { name: '已完成' })).toHaveCount(0)
    const processingResponse = page.waitForResponse((response) =>
      response.url().endsWith(`/api/admin/orders/${advancing.order.id}/status`)
      && response.request().method() === 'PATCH',
    )
    await page.getByRole('button', { name: '處理中' }).click()
    expect((await processingResponse).status()).toBe(200)
    await expect(page.getByText('處理中', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '已出貨' })).toBeVisible()
    await expect(page.getByRole('button', { name: '已付款' })).toHaveCount(0)

    await page.goto(`/admin/orders/${cancelling.order.id}`)
    await expect(page.getByText('已付款', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: '已取消' }).click()
    const cancelDialog = page.getByRole('dialog', { name: '確認取消訂單' })
    await expect(cancelDialog).toContainText('回補商品庫存')
    const cancelResponse = page.waitForResponse((response) =>
      response.url().endsWith(`/api/admin/orders/${cancelling.order.id}/status`)
      && response.request().method() === 'PATCH',
    )
    await cancelDialog.getByRole('button', { name: '確認取消' }).click()
    expect((await cancelResponse).status()).toBe(200)
    await expect(page.getByText('已取消', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '處理中' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '已出貨' })).toHaveCount(0)
})
