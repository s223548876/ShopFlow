import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { login } from './api'
import { useAuthStore } from './store'

vi.mock('./api', () => ({ login: vi.fn(), register: vi.fn() }))

class MemoryStorage implements Storage {
  private values = new Map<string, string>()
  get length(): number { return this.values.size }
  clear(): void { this.values.clear() }
  getItem(key: string): string | null { return this.values.get(key) ?? null }
  key(index: number): string | null { return [...this.values.keys()][index] ?? null }
  removeItem(key: string): void { this.values.delete(key) }
  setItem(key: string, value: string): void { this.values.set(key, value) }
}

function token(payload: object): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode({ alg: 'HS256' })}.${encode(payload)}.signature`
}

beforeEach(() => {
  Object.defineProperty(globalThis, 'sessionStorage', {
    configurable: true,
    value: new MemoryStorage(),
  })
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('auth store', () => {
  it('restores only a valid token and derives role for UX', () => {
    sessionStorage.setItem('shopflow.accessToken', token({ role: 'CUSTOMER', exp: 4_102_444_800 }))

    const store = useAuthStore()

    expect(store.isAuthenticated).toBe(true)
    expect(store.role).toBe('CUSTOMER')
  })

  it('clears malformed persisted tokens', () => {
    sessionStorage.setItem('shopflow.accessToken', 'invalid')

    const store = useAuthStore()

    expect(store.isAuthenticated).toBe(false)
    expect(sessionStorage.getItem('shopflow.accessToken')).toBeNull()
  })

  it('persists a valid login token and logout removes it', async () => {
    const accessToken = token({ role: 'ADMIN', exp: 4_102_444_800 })
    vi.mocked(login).mockResolvedValue({ accessToken, tokenType: 'Bearer', expiresIn: 1800 })
    const store = useAuthStore()

    await store.login({ email: 'admin@example.com', password: 'password-123' })
    expect(store.role).toBe('ADMIN')
    expect(sessionStorage.getItem('shopflow.accessToken')).toBe(accessToken)

    store.logout()
    expect(store.isAuthenticated).toBe(false)
    expect(sessionStorage.getItem('shopflow.accessToken')).toBeNull()
  })
})
