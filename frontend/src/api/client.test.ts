import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it, vi } from 'vitest'

import { createApiClient } from './client'
import type { ApiErrorResponse } from './types'

const authenticationRequired: ApiErrorResponse = {
  timestamp: '2026-07-04T00:00:00Z',
  status: 401,
  code: 'AUTHENTICATION_REQUIRED',
  message: 'A valid access token is required',
  path: '/api/cart',
  fieldErrors: [],
}

function rejectingAdapter(data: ApiErrorResponse) {
  return async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    const response: AxiosResponse = {
      data,
      status: data.status,
      statusText: 'Error',
      headers: {},
      config,
    }
    throw new AxiosError('Request failed', undefined, config, undefined, response)
  }
}

describe('API client interceptors', () => {
  it('adds the current Bearer token', async () => {
    let authorization: unknown
    const client = createApiClient({
      getAccessToken: () => 'signed-token',
      onAuthenticationRequired: vi.fn(),
      onAccessDenied: vi.fn(),
    })
    client.defaults.adapter = async (config) => {
      authorization = config.headers.Authorization
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    }

    await client.get('/cart')

    expect(authorization).toBe('Bearer signed-token')
  })

  it('handles only AUTHENTICATION_REQUIRED as an expired session', async () => {
    const onAuthenticationRequired = vi.fn()
    const client = createApiClient({
      getAccessToken: () => 'expired-token',
      onAuthenticationRequired,
      onAccessDenied: vi.fn(),
    })
    client.defaults.adapter = rejectingAdapter(authenticationRequired)

    await expect(client.get('/cart')).rejects.toBeInstanceOf(AxiosError)
    expect(onAuthenticationRequired).toHaveBeenCalledOnce()

    client.defaults.adapter = rejectingAdapter({
      ...authenticationRequired,
      code: 'INVALID_CREDENTIALS',
      path: '/api/auth/login',
    })
    await expect(client.post('/auth/login')).rejects.toBeInstanceOf(AxiosError)
    expect(onAuthenticationRequired).toHaveBeenCalledOnce()
  })

  it('reports ACCESS_DENIED without clearing authentication', async () => {
    const onAuthenticationRequired = vi.fn()
    const onAccessDenied = vi.fn()
    const client = createApiClient({
      getAccessToken: () => 'admin-token',
      onAuthenticationRequired,
      onAccessDenied,
    })
    client.defaults.adapter = rejectingAdapter({
      ...authenticationRequired,
      status: 403,
      code: 'ACCESS_DENIED',
    })

    await expect(client.get('/cart')).rejects.toBeInstanceOf(AxiosError)
    expect(onAccessDenied).toHaveBeenCalledOnce()
    expect(onAuthenticationRequired).not.toHaveBeenCalled()
  })
})
