export type Role = 'CUSTOMER' | 'ADMIN'

export interface JwtSession {
  role: Role
  expiresAt: number
}

export function parseJwtSession(
  token: string,
  nowSeconds = Math.floor(Date.now() / 1000),
): JwtSession | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) {
      return null
    }
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    const bytes = Uint8Array.from(atob(padded), (character) => character.charCodeAt(0))
    const payload: unknown = JSON.parse(new TextDecoder().decode(bytes))
    if (!payload || typeof payload !== 'object') {
      return null
    }
    const { role, exp } = payload as Record<string, unknown>
    if ((role !== 'CUSTOMER' && role !== 'ADMIN') || typeof exp !== 'number' || exp <= nowSeconds) {
      return null
    }
    return { role, expiresAt: exp }
  } catch {
    return null
  }
}

export function safeRedirect(value: unknown, fallback = '/'): string {
  return typeof value === 'string'
    && value.startsWith('/')
    && !value.startsWith('//')
    && !value.includes('\\')
    ? value
    : fallback
}
