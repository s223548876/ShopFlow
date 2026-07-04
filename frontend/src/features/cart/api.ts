import { apiClient } from '../../api/client'

export interface AddCartItemRequest {
  productId: number
  quantity: number
}

export interface UpdateCartItemRequest {
  quantity: number
}

export interface CartItemResponse {
  id: number
  productId: number
  productName: string
  currentUnitPrice: number
  quantity: number
  subtotal: number
  available: boolean
}

export interface CartResponse {
  id: number
  items: CartItemResponse[]
  estimatedTotal: number
}

export async function getCart(): Promise<CartResponse> {
  return (await apiClient.get<CartResponse>('/cart')).data
}

export async function addCartItem(request: AddCartItemRequest): Promise<CartItemResponse> {
  return (await apiClient.post<CartItemResponse>('/cart/items', request)).data
}

export async function updateCartItem(
  itemId: number,
  request: UpdateCartItemRequest,
): Promise<CartItemResponse> {
  return (await apiClient.patch<CartItemResponse>(`/cart/items/${itemId}`, request)).data
}

export async function deleteCartItem(itemId: number): Promise<void> {
  await apiClient.delete(`/cart/items/${itemId}`)
}
