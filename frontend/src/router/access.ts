import type { Role } from '../features/auth/jwt'

export type CartAccess = 'login' | 'allow' | 'forbidden'

export function cartAccess(role: Role | null): CartAccess {
  if (role === null) {
    return 'login'
  }
  return role === 'CUSTOMER' ? 'allow' : 'forbidden'
}
