import { describe, expect, it } from 'vitest'

import { parseJwtSession, safeRedirect } from './jwt'

function token(payload: object): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.signature`
}

describe('parseJwtSession', () => {
  it('returns the UX role and expiration for a current supported token', () => {
    expect(parseJwtSession(token({ role: 'CUSTOMER', exp: 2_000 }), 1_000)).toEqual({
      role: 'CUSTOMER',
      expiresAt: 2_000,
    })
  })

  it('rejects expired, malformed, and unsupported-role tokens', () => {
    expect(parseJwtSession(token({ role: 'CUSTOMER', exp: 999 }), 1_000)).toBeNull()
    expect(parseJwtSession(token({ role: 'OWNER', exp: 2_000 }), 1_000)).toBeNull()
    expect(parseJwtSession('not-a-jwt', 1_000)).toBeNull()
  })
})

describe('safeRedirect', () => {
  it('accepts only internal absolute paths', () => {
    expect(safeRedirect('/cart')).toBe('/cart')
    expect(safeRedirect('/products/501?from=cart')).toBe('/products/501?from=cart')
    expect(safeRedirect('https://example.com')).toBe('/')
    expect(safeRedirect('//example.com')).toBe('/')
    expect(safeRedirect(undefined)).toBe('/')
  })
})
