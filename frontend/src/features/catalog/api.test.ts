import type { InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it } from 'vitest'

import { apiClient } from '../../api/client'
import { getCategories, getProduct, getProducts } from './api'

let request: InternalAxiosRequestConfig

beforeEach(() => {
  apiClient.defaults.adapter = async (config) => {
    request = config
    return {
      data: config.url === '/categories'
        ? []
        : config.url === '/products/501'
          ? { id: 501 }
          : { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    }
  }
})

describe('catalog API contract', () => {
  it('uses the public category and product detail paths', async () => {
    await getCategories()
    expect(request.url).toBe('/categories')

    await getProduct(501)
    expect(request.url).toBe('/products/501')
  })

  it('sends only documented product filters and sorting', async () => {
    await getProducts({
      q: 'keyboard',
      categoryId: 1,
      page: 2,
      size: 20,
      sort: 'price,asc',
    })

    expect(request.url).toBe('/products')
    expect(request.params).toEqual({
      q: 'keyboard',
      categoryId: 1,
      page: 2,
      size: 20,
      sort: 'price,asc',
    })
  })

  it('sends a text wildcard when no keyword is entered', async () => {
    await getProducts({ page: 0, size: 20, sort: 'createdAt,desc' })

    expect(request.params).toEqual({
      q: '%',
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    })
  })
})
