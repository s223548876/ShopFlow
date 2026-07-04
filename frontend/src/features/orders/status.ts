import type { OrderStatus } from './types'

export const ORDER_STATUSES: readonly OrderStatus[] = [
  'PENDING_PAYMENT',
  'PAID',
  'PROCESSING',
  'SHIPPED',
  'COMPLETED',
  'CANCELLED',
]

const labels: Record<OrderStatus, string> = {
  PENDING_PAYMENT: '待付款',
  PAID: '已付款',
  PROCESSING: '處理中',
  SHIPPED: '已出貨',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const nextStatuses: Record<OrderStatus, readonly OrderStatus[]> = {
  PENDING_PAYMENT: ['CANCELLED'],
  PAID: ['PROCESSING', 'CANCELLED'],
  PROCESSING: ['SHIPPED', 'CANCELLED'],
  SHIPPED: ['COMPLETED'],
  COMPLETED: [],
  CANCELLED: [],
}

export type OrderTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

export function isOrderStatus(value: unknown): value is OrderStatus {
  return typeof value === 'string' && ORDER_STATUSES.some((status) => status === value)
}

export function statusLabel(status: OrderStatus): string {
  return labels[status]
}

export function statusTagType(status: OrderStatus): OrderTagType {
  if (status === 'PENDING_PAYMENT') return 'warning'
  if (status === 'CANCELLED') return 'danger'
  if (status === 'COMPLETED' || status === 'PAID') return 'success'
  return 'primary'
}

export function canPayOrder(status: OrderStatus): boolean {
  return status === 'PENDING_PAYMENT'
}

export function adminNextStatuses(status: OrderStatus): readonly OrderStatus[] {
  return nextStatuses[status]
}

export async function confirmAdminStatusChange(
  status: OrderStatus,
  confirm: () => Promise<unknown>,
): Promise<boolean> {
  if (status !== 'CANCELLED') return true
  try {
    await confirm()
    return true
  } catch {
    return false
  }
}
