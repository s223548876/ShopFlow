import { describe, expect, it } from 'vitest'

import { cartAccess, roleAccess } from './access'

describe('cart route access', () => {
  it('redirects guests, allows customers, and rejects admins', () => {
    expect(cartAccess(null)).toBe('login')
    expect(cartAccess('CUSTOMER')).toBe('allow')
    expect(cartAccess('ADMIN')).toBe('forbidden')
  })
})

describe('role-protected route access', () => {
  it('protects customer order routes', () => {
    expect(roleAccess(null, 'CUSTOMER')).toBe('login')
    expect(roleAccess('CUSTOMER', 'CUSTOMER')).toBe('allow')
    expect(roleAccess('ADMIN', 'CUSTOMER')).toBe('forbidden')
  })

  it('protects admin order routes', () => {
    expect(roleAccess(null, 'ADMIN')).toBe('login')
    expect(roleAccess('CUSTOMER', 'ADMIN')).toBe('forbidden')
    expect(roleAccess('ADMIN', 'ADMIN')).toBe('allow')
  })
})
