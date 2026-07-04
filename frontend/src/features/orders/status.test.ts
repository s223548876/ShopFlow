import { describe, expect, it, vi } from 'vitest'

import {
  adminNextStatuses,
  canPayOrder,
  confirmAdminStatusChange,
  statusLabel,
} from './status'

describe('order status presentation and transitions', () => {
  it('only allows customer payment while pending', () => {
    expect(canPayOrder('PENDING_PAYMENT')).toBe(true)
    expect(canPayOrder('PAID')).toBe(false)
    expect(canPayOrder('CANCELLED')).toBe(false)
  })

  it('returns only legal admin next statuses', () => {
    expect(adminNextStatuses('PENDING_PAYMENT')).toEqual(['CANCELLED'])
    expect(adminNextStatuses('PAID')).toEqual(['PROCESSING', 'CANCELLED'])
    expect(adminNextStatuses('PROCESSING')).toEqual(['SHIPPED', 'CANCELLED'])
    expect(adminNextStatuses('SHIPPED')).toEqual(['COMPLETED'])
    expect(adminNextStatuses('COMPLETED')).toEqual([])
    expect(adminNextStatuses('CANCELLED')).toEqual([])
  })

  it('requires confirmation only before cancellation', async () => {
    const confirm = vi.fn().mockResolvedValue(undefined)
    expect(await confirmAdminStatusChange('CANCELLED', confirm)).toBe(true)
    expect(confirm).toHaveBeenCalledOnce()

    confirm.mockClear()
    expect(await confirmAdminStatusChange('PROCESSING', confirm)).toBe(true)
    expect(confirm).not.toHaveBeenCalled()
  })

  it('stops cancellation when confirmation is dismissed', async () => {
    const confirm = vi.fn().mockRejectedValue(new Error('dismissed'))
    expect(await confirmAdminStatusChange('CANCELLED', confirm)).toBe(false)
  })

  it('uses readable Chinese status labels', () => {
    expect(statusLabel('PENDING_PAYMENT')).toBe('待付款')
    expect(statusLabel('COMPLETED')).toBe('已完成')
  })
})
