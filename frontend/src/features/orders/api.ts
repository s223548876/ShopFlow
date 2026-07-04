import { apiClient } from '../../api/client'
import type { PageResponse } from '../../api/types'
import { isOrderStatus } from './status'
import type {
  AdminOrderListQuery,
  AdminOrderResponse,
  OrderListQuery,
  OrderResponse,
  OrderStatus,
  OrderSummaryResponse,
} from './types'

export async function createOrder(): Promise<OrderResponse> {
  return (await apiClient.post<OrderResponse>('/orders')).data
}

export async function getOrders(query: OrderListQuery): Promise<PageResponse<OrderSummaryResponse>> {
  return (await apiClient.get<PageResponse<OrderSummaryResponse>>('/orders', { params: query })).data
}

export async function getOrder(orderId: number): Promise<OrderResponse> {
  return (await apiClient.get<OrderResponse>(`/orders/${orderId}`)).data
}

export async function payOrder(orderId: number): Promise<OrderResponse> {
  return (await apiClient.post<OrderResponse>(`/orders/${orderId}/pay`)).data
}

export async function getAdminOrders(
  query: AdminOrderListQuery,
): Promise<PageResponse<OrderSummaryResponse>> {
  const { status, ...params } = query
  return (await apiClient.get<PageResponse<OrderSummaryResponse>>('/admin/orders', {
    params: isOrderStatus(status) ? { status, ...params } : params,
  })).data
}

export async function getAdminOrder(orderId: number): Promise<AdminOrderResponse> {
  return (await apiClient.get<AdminOrderResponse>(`/admin/orders/${orderId}`)).data
}

export async function updateAdminOrderStatus(
  orderId: number,
  status: OrderStatus,
): Promise<AdminOrderResponse> {
  return (await apiClient.patch<AdminOrderResponse>(`/admin/orders/${orderId}/status`, { status })).data
}
