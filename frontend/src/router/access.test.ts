import { describe, expect, it } from 'vitest'

import { cartAccess } from './access'

describe('cart route access', () => {
  it('redirects guests, allows customers, and rejects admins', () => {
    expect(cartAccess(null)).toBe('login')
    expect(cartAccess('CUSTOMER')).toBe('allow')
    expect(cartAccess('ADMIN')).toBe('forbidden')
  })
})
