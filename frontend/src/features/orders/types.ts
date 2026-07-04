export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'PROCESSING'
  | 'SHIPPED'
  | 'COMPLETED'
  | 'CANCELLED'

export type OrderSort = 'createdAt,asc' | 'createdAt,desc'

export interface OrderItemResponse {
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  subtotal: number
}

export interface OrderSummaryResponse {
  id: number
  status: OrderStatus
  totalAmount: number
  itemCount: number
  createdAt: string
}

export interface OrderResponse {
  id: number
  status: OrderStatus
  totalAmount: number
  paidAt: string | null
  createdAt: string
  items: OrderItemResponse[]
}

export interface AdminOrderUserResponse {
  id: number
  email: string
  displayName: string
}

export interface AdminOrderResponse extends OrderResponse {
  user: AdminOrderUserResponse
}

export interface OrderListQuery {
  page: number
  size: number
  sort: OrderSort
}

export interface AdminOrderListQuery extends OrderListQuery {
  status?: OrderStatus | ''
}
