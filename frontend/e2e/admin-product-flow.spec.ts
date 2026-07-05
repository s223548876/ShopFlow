import { expect, test } from '@playwright/test'

import {
  adminCredentials,
  deactivateProductApi,
  loginApi,
  loginViaUi,
  uniqueMarker,
} from './helpers'

let cleanup: { adminToken: string; productIds: number[] } | null = null

test.afterEach(async ({ request }) => {
  if (!cleanup) return
  await Promise.all(cleanup.productIds.map((id) => deactivateProductApi(request, cleanup!.adminToken, id)))
  cleanup = null
})

test('admin creates, edits, stocks, and soft deletes a product', async ({ page, request }) => {
  const credentials = adminCredentials()
  const adminToken = await loginApi(request, credentials)
  const marker = uniqueMarker('admin-product')
  const originalName = `${marker}-original`
  const updatedName = `${marker}-updated`
  let productId: number | null = null
  cleanup = { adminToken, productIds: [] }

  await loginViaUi(page, credentials)
    await page.getByRole('link', { name: '商品管理' }).click()
    await page.getByRole('link', { name: '新增商品' }).click()

    await page.getByLabel('商品名稱').fill(originalName)
    await page.getByLabel('商品描述').fill(`${marker} original description`)
    await page.getByRole('combobox').press('ArrowDown')
    await page.getByRole('option', { name: 'Electronics', exact: true }).click()
    await page.getByRole('spinbutton').nth(0).fill('88.50')
    await page.getByRole('spinbutton').nth(1).fill('9')
    const createResponse = page.waitForResponse((response) =>
      response.url().endsWith('/api/admin/products') && response.request().method() === 'POST',
    )
    await page.getByRole('button', { name: '建立商品' }).click()
    const created = await createResponse
    expect(created.status()).toBe(201)
    productId = ((await created.json()) as { id: number }).id
    cleanup.productIds.push(productId)
    await expect(page).toHaveURL(/\/admin\/products$/)

    await page.getByRole('textbox', { name: '搜尋商品' }).fill(`  ${originalName}  `)
    await page.getByRole('button', { name: '套用' }).click()
    const originalRow = page.getByRole('row').filter({ hasText: originalName })
    await expect(originalRow).toBeVisible()
    await originalRow.getByRole('link', { name: '編輯' }).click()

    await page.getByLabel('商品名稱').fill(updatedName)
    await page.getByLabel('商品描述').fill(`${marker} updated description`)
    await page.getByRole('spinbutton').fill('99.75')
    const updateResponse = page.waitForResponse((response) =>
      response.url().endsWith(`/api/admin/products/${productId}`) && response.request().method() === 'PUT',
    )
    await page.getByRole('button', { name: '儲存變更' }).click()
    expect((await updateResponse).status()).toBe(200)
    await expect(page.getByLabel('商品名稱')).toHaveValue(updatedName)
    await expect(page.getByLabel('商品描述')).toHaveValue(`${marker} updated description`)
    await expect(page.getByRole('spinbutton')).toHaveValue('99.75')

    await page.getByRole('link', { name: '返回商品管理' }).click()
    await page.getByRole('textbox', { name: '搜尋商品' }).fill(updatedName)
    await page.getByRole('button', { name: '套用' }).click()
    let updatedRow = page.getByRole('row').filter({ hasText: updatedName })
    await expect(updatedRow).toContainText('99.75')
    await updatedRow.getByRole('button', { name: '設定庫存' }).click()
    const stockDialog = page.getByRole('dialog', { name: '設定庫存' })
    await expect(stockDialog).toContainText('目前庫存：9')
    await stockDialog.getByRole('spinbutton').fill('17')
    const stockResponse = page.waitForResponse((response) =>
      response.url().endsWith(`/api/admin/products/${productId}/stock`) && response.request().method() === 'PATCH',
    )
    await stockDialog.getByRole('button', { name: '儲存庫存' }).click()
    expect((await stockResponse).status()).toBe(200)
    updatedRow = page.getByRole('row').filter({ hasText: updatedName })
    await expect(updatedRow).toContainText('17')

    await updatedRow.getByRole('button', { name: '停用' }).click()
    const confirmDialog = page.getByRole('dialog', { name: '確認停用商品' })
    await expect(confirmDialog).toContainText('歷史訂單不受影響')
    const deleteResponse = page.waitForResponse((response) =>
      response.url().endsWith(`/api/admin/products/${productId}`) && response.request().method() === 'DELETE',
    )
    await confirmDialog.getByRole('button', { name: '確認停用' }).click()
    expect((await deleteResponse).status()).toBe(204)
    await expect(page.getByRole('row').filter({ hasText: updatedName })).toContainText('停用')

    await page.getByRole('button', { name: '登出' }).click()
    await page.goto('/products')
    await page.getByRole('textbox', { name: '搜尋商品' }).fill(updatedName)
    await page.getByRole('button', { name: '套用' }).click()
  await expect(page.getByText('找不到符合條件的商品')).toBeVisible()
  await expect(page.getByText(updatedName)).toHaveCount(0)
})
