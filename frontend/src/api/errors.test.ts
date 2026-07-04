import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it } from 'vitest'

import { apiErrorMessage, fieldErrors, getApiError } from './errors'
import type { ApiErrorResponse } from './types'

function axiosError(data: ApiErrorResponse): AxiosError<ApiErrorResponse> {
  const config = { headers: {} } as InternalAxiosRequestConfig
  const response: AxiosResponse<ApiErrorResponse> = {
    data,
    status: data.status,
    statusText: 'Error',
    headers: {},
    config,
  }
  return new AxiosError('Request failed', undefined, config, undefined, response)
}

const validationError: ApiErrorResponse = {
  timestamp: '2026-07-04T00:00:00Z',
  status: 400,
  code: 'VALIDATION_ERROR',
  message: 'Request validation failed',
  path: '/api/auth/register',
  fieldErrors: [{ field: 'email', message: 'must be a valid email' }],
}

describe('API error mapping', () => {
  it('extracts the backend schema and field errors', () => {
    const error = axiosError(validationError)

    expect(getApiError(error)).toEqual(validationError)
    expect(fieldErrors(error)).toEqual({ email: 'must be a valid email' })
  })

  it('maps known business errors and falls back for network failures', () => {
    expect(apiErrorMessage(axiosError({ ...validationError, code: 'INSUFFICIENT_STOCK' })))
      .toContain('庫存不足')
    expect(apiErrorMessage(new Error('offline'))).toBe('無法連線，請稍後再試')
  })
})
