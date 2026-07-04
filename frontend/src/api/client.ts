import axios, { type AxiosInstance } from 'axios'

import { getApiError } from './errors'

export interface ApiClientHooks {
  getAccessToken: () => string | null
  onAuthenticationRequired: () => void
  onAccessDenied: () => void
}

export function createApiClient(hooks: ApiClientHooks): AxiosInstance {
  const client = axios.create({ baseURL: '/api' })

  client.interceptors.request.use((config) => {
    const token = hooks.getAccessToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  client.interceptors.response.use(undefined, (error: unknown) => {
    const apiError = getApiError(error)
    if (apiError?.code === 'AUTHENTICATION_REQUIRED') {
      hooks.onAuthenticationRequired()
    } else if (apiError?.code === 'ACCESS_DENIED') {
      hooks.onAccessDenied()
    }
    return Promise.reject(error)
  })

  return client
}

let activeHooks: ApiClientHooks = {
  getAccessToken: () => null,
  onAuthenticationRequired: () => undefined,
  onAccessDenied: () => undefined,
}

export const apiClient = createApiClient({
  getAccessToken: () => activeHooks.getAccessToken(),
  onAuthenticationRequired: () => activeHooks.onAuthenticationRequired(),
  onAccessDenied: () => activeHooks.onAccessDenied(),
})

export function configureApiClient(hooks: ApiClientHooks): void {
  activeHooks = hooks
}
