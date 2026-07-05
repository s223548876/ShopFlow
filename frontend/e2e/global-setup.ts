import { expect, request } from '@playwright/test'

export default async function globalSetup(): Promise<void> {
  if (!process.env.E2E_ADMIN_EMAIL || !process.env.E2E_ADMIN_PASSWORD) {
    throw new Error('Set E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD to an existing ADMIN account')
  }

  const api = await request.newContext({
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:3000',
  })
  try {
    await expect.poll(async () => {
      try {
        return (await api.get('/actuator/health')).ok()
      } catch {
        return false
      }
    }, {
      message: 'Docker Compose health endpoint did not become ready',
      timeout: 30_000,
    }).toBe(true)
  } finally {
    await api.dispose()
  }
}
