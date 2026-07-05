import type { InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it } from 'vitest'

import { apiClient } from '../../api/client'
import {
  createAdminProduct,
  deactivateAdminProduct,
  getAdminProduct,
  getAdminProducts,
  updateAdminProduct,
  updateAdminProductStock,
} from './api'

let request: InternalAxiosRequestConfig

beforeEach(() => {
  apiClient.defaults.adapter = async (config) => {
    request = config
    return {
      data: config.method === 'get' && config.url === '/admin/products'
        ? { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true }
        : { id: 501 },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }
  }
})

describe('admin product API contract', () => {
  it('sends documented list filters and trims q', async () => {
    await getAdminProducts({
      q: '  keyboard  ',
      categoryId: 3,
      active: false,
      page: 2,
      size: 20,
      sort: 'price,asc',
    })

    expect(request.url).toBe('/admin/products')
    expect(request.params).toEqual({
      q: 'keyboard',
      categoryId: 3,
      active: false,
      page: 2,
      size: 20,
      sort: 'price,asc',
    })
  })

  it.each([undefined, '', '   '])('omits empty q and optional filters', async (q) => {
    await getAdminProducts({ q, page: 0, size: 20, sort: 'createdAt,desc' })

    expect(request.params).toEqual({ page: 0, size: 20, sort: 'createdAt,desc' })
  })

  it('uses the detail, create, update, stock and deactivate contracts', async () => {
    await getAdminProduct(501)
    expect(request).toMatchObject({ method: 'get', url: '/admin/products/501' })

    const createRequest = {
      categoryId: 3,
      name: 'Keyboard',
      description: 'Mechanical keyboard',
      price: 2500,
      stock: 12,
    }
    await createAdminProduct(createRequest)
    expect(request).toMatchObject({ method: 'post', url: '/admin/products', data: JSON.stringify(createRequest) })

    const updateRequest = {
      categoryId: 3,
      name: 'Keyboard Pro',
      description: 'Updated',
      price: 3000,
      active: true,
    }
    await updateAdminProduct(501, updateRequest)
    expect(request).toMatchObject({
      method: 'put',
      url: '/admin/products/501',
      data: JSON.stringify(updateRequest),
    })

    await updateAdminProductStock(501, 9)
    expect(request).toMatchObject({
      method: 'patch',
      url: '/admin/products/501/stock',
      data: JSON.stringify({ quantity: 9 }),
    })

    await deactivateAdminProduct(501)
    expect(request).toMatchObject({ method: 'delete', url: '/admin/products/501' })
  })
})
