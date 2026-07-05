import { apiClient } from '../../api/client'
import type { PageResponse } from '../../api/types'
import type {
  AdminProductQuery,
  AdminProductResponse,
  CreateProductRequest,
  StockResponse,
  UpdateProductRequest,
} from './types'

export async function getAdminProducts(
  query: AdminProductQuery,
): Promise<PageResponse<AdminProductResponse>> {
  const { q, categoryId, active, ...page } = query
  const keyword = q?.trim()
  const params = {
    ...page,
    ...(keyword ? { q: keyword } : {}),
    ...(categoryId === undefined ? {} : { categoryId }),
    ...(typeof active === 'boolean' ? { active } : {}),
  }
  return (await apiClient.get<PageResponse<AdminProductResponse>>('/admin/products', { params })).data
}

export async function getAdminProduct(productId: number): Promise<AdminProductResponse> {
  return (await apiClient.get<AdminProductResponse>(`/admin/products/${productId}`)).data
}

export async function createAdminProduct(request: CreateProductRequest): Promise<AdminProductResponse> {
  return (await apiClient.post<AdminProductResponse>('/admin/products', request)).data
}

export async function updateAdminProduct(
  productId: number,
  request: UpdateProductRequest,
): Promise<AdminProductResponse> {
  return (await apiClient.put<AdminProductResponse>(`/admin/products/${productId}`, request)).data
}

export async function updateAdminProductStock(productId: number, quantity: number): Promise<StockResponse> {
  return (await apiClient.patch<StockResponse>(`/admin/products/${productId}/stock`, { quantity })).data
}

export async function deactivateAdminProduct(productId: number): Promise<void> {
  await apiClient.delete(`/admin/products/${productId}`)
}
