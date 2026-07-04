import type { InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it } from 'vitest'

import { apiClient } from '../../api/client'
import {
  createOrder,
  getAdminOrder,
  getAdminOrders,
  getOrder,
  getOrders,
  payOrder,
  updateAdminOrderStatus,
} from './api'

let request: InternalAxiosRequestConfig

beforeEach(() => {
  apiClient.defaults.adapter = async (config) => {
    request = config
    return {
      data: config.url?.endsWith('/orders')
        ? { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true }
        : { id: 701, status: 'PENDING_PAYMENT', items: [] },
      status: config.method === 'post' && config.url === '/orders' ? 201 : 200,
      statusText: 'OK',
      headers: {},
      config,
    }
  }
})

describe('order API contract', () => {
  it('creates, lists, reads, and pays customer orders using documented paths', async () => {
    await createOrder()
    expect(request.method).toBe('post')
    expect(request.url).toBe('/orders')
    expect(request.data).toBeUndefined()

    await getOrders({ page: 2, size: 20, sort: 'createdAt,asc' })
    expect(request.method).toBe('get')
    expect(request.url).toBe('/orders')
    expect(request.params).toEqual({ page: 2, size: 20, sort: 'createdAt,asc' })

    await getOrder(701)
    expect(request.url).toBe('/orders/701')

    await payOrder(701)
    expect(request.method).toBe('post')
    expect(request.url).toBe('/orders/701/pay')
    expect(request.data).toBeUndefined()
  })

  it('omits an empty admin status filter while preserving pagination and sorting', async () => {
    await getAdminOrders({ status: '', page: 1, size: 20, sort: 'createdAt,desc' })

    expect(request.url).toBe('/admin/orders')
    expect(request.params).toEqual({ page: 1, size: 20, sort: 'createdAt,desc' })
  })

  it('sends a valid admin status filter', async () => {
    await getAdminOrders({ status: 'PAID', page: 0, size: 20, sort: 'createdAt,asc' })

    expect(request.params).toEqual({ status: 'PAID', page: 0, size: 20, sort: 'createdAt,asc' })
  })

  it('reads and updates admin orders using the documented body', async () => {
    await getAdminOrder(701)
    expect(request.url).toBe('/admin/orders/701')

    await updateAdminOrderStatus(701, 'PROCESSING')
    expect(request.method).toBe('patch')
    expect(request.url).toBe('/admin/orders/701/status')
    expect(JSON.parse(String(request.data))).toEqual({ status: 'PROCESSING' })
  })
})
