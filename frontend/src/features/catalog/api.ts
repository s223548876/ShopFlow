import { apiClient } from '../../api/client'
import type { PageResponse } from '../../api/types'

export type ProductSort =
  | 'name,asc'
  | 'name,desc'
  | 'price,asc'
  | 'price,desc'
  | 'createdAt,asc'
  | 'createdAt,desc'

export interface CategoryResponse {
  id: number
  name: string
}

export interface ProductSummaryResponse {
  id: number
  name: string
  price: number
  stock: number
  category: CategoryResponse
}

export interface ProductDetailResponse extends ProductSummaryResponse {
  description: string
}

export interface ProductQuery {
  q?: string
  categoryId?: number
  page: number
  size: number
  sort: ProductSort
}

export async function getCategories(): Promise<CategoryResponse[]> {
  return (await apiClient.get<CategoryResponse[]>('/categories')).data
}

export async function getProducts(query: ProductQuery): Promise<PageResponse<ProductSummaryResponse>> {
  const { q, ...params } = query
  const keyword = q?.trim()
  return (await apiClient.get<PageResponse<ProductSummaryResponse>>('/products', {
    params: keyword ? { ...params, q: keyword } : params,
  })).data
}

export async function getProduct(productId: number): Promise<ProductDetailResponse> {
  return (await apiClient.get<ProductDetailResponse>(`/products/${productId}`)).data
}
