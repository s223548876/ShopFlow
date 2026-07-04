import type { InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it } from 'vitest'

import { apiClient } from '../../api/client'
import { login, register } from './api'

let request: InternalAxiosRequestConfig

beforeEach(() => {
  apiClient.defaults.adapter = async (config) => {
    request = config
    return {
      data: config.url?.endsWith('/login')
        ? { accessToken: 'token', tokenType: 'Bearer', expiresIn: 1800 }
        : { id: 101, email: 'alice@example.com', displayName: 'Alice', role: 'CUSTOMER', createdAt: 'now' },
      status: config.url?.endsWith('/login') ? 200 : 201,
      statusText: 'OK',
      headers: {},
      config,
    }
  }
})

describe('auth API contract', () => {
  it('posts only email and password to login', async () => {
    const response = await login({ email: 'alice@example.com', password: 'password-123' })

    expect(request.method).toBe('post')
    expect(request.url).toBe('/auth/login')
    expect(JSON.parse(String(request.data))).toEqual({
      email: 'alice@example.com',
      password: 'password-123',
    })
    expect(response.expiresIn).toBe(1800)
  })

  it('posts the documented registration fields without role or userId', async () => {
    await register({
      email: 'alice@example.com',
      password: 'password-123',
      displayName: 'Alice',
    })

    expect(request.url).toBe('/auth/register')
    expect(JSON.parse(String(request.data))).toEqual({
      email: 'alice@example.com',
      password: 'password-123',
      displayName: 'Alice',
    })
  })
})
