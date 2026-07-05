import type { Role } from '../features/auth/jwt'

export type CartAccess = 'login' | 'allow' | 'forbidden'

export function roleAccess(role: Role | null, requiredRole: Role): CartAccess {
  if (role === null) {
    return 'login'
  }
  return role === requiredRole ? 'allow' : 'forbidden'
}

export function cartAccess(role: Role | null): CartAccess {
  return roleAccess(role, 'CUSTOMER')
}
