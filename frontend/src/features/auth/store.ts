import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { login as requestLogin, type LoginRequest } from './api'
import { parseJwtSession, type JwtSession } from './jwt'

const STORAGE_KEY = 'shopflow.accessToken'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const session = ref<JwtSession | null>(null)

  const isAuthenticated = computed(() => accessToken.value !== null && session.value !== null)
  const role = computed(() => session.value?.role ?? null)

  function logout(): void {
    accessToken.value = null
    session.value = null
    sessionStorage.removeItem(STORAGE_KEY)
  }

  function setToken(token: string): boolean {
    const parsed = parseJwtSession(token)
    if (!parsed) {
      logout()
      return false
    }
    accessToken.value = token
    session.value = parsed
    sessionStorage.setItem(STORAGE_KEY, token)
    return true
  }

  function getValidAccessToken(): string | null {
    if (!accessToken.value || !setToken(accessToken.value)) {
      return null
    }
    return accessToken.value
  }

  async function login(request: LoginRequest): Promise<void> {
    const response = await requestLogin(request)
    if (response.tokenType !== 'Bearer' || !setToken(response.accessToken)) {
      throw new Error('Login returned an invalid access token')
    }
  }

  const storedToken = sessionStorage.getItem(STORAGE_KEY)
  if (storedToken) {
    setToken(storedToken)
  }

  return { accessToken, isAuthenticated, role, getValidAccessToken, login, logout }
})
