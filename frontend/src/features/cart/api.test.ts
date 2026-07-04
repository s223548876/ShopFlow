import type { InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it } from 'vitest'

import { apiClient } from '../../api/client'
import { addCartItem, deleteCartItem, getCart, updateCartItem } from './api'

let request: InternalAxiosRequestConfig

beforeEach(() => {
  apiClient.defaults.adapter = async (config) => {
    request = config
    return {
      data: config.url === '/cart'
        ? { id: 301, items: [], estimatedTotal: 0 }
        : { id: 401, productId: 501, quantity: 2 },
      status: config.method === 'post' ? 201 : config.method === 'delete' ? 204 : 200,
      statusText: 'OK',
      headers: {},
      config,
    }
  }
})

describe('cart API contract', () => {
  it('gets the authenticated cart', async () => {
    await getCart()
    expect(request.method).toBe('get')
    expect(request.url).toBe('/cart')
  })

  it('posts only productId and quantity when adding', async () => {
    await addCartItem({ productId: 501, quantity: 2 })
    expect(request.url).toBe('/cart/items')
    expect(JSON.parse(String(request.data))).toEqual({ productId: 501, quantity: 2 })
  })

  it('patches quantity and deletes by itemId', async () => {
    await updateCartItem(401, { quantity: 3 })
    expect(request.method).toBe('patch')
    expect(request.url).toBe('/cart/items/401')
    expect(JSON.parse(String(request.data))).toEqual({ quantity: 3 })

    await deleteCartItem(401)
    expect(request.method).toBe('delete')
    expect(request.url).toBe('/cart/items/401')
  })
})
