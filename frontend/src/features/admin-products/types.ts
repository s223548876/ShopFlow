import type { CategoryResponse, ProductSort } from '../catalog/api'

export interface AdminProductResponse {
  id: number
  name: string
  description: string
  price: number
  stock: number
  active: boolean
  category: CategoryResponse
  createdAt: string
  updatedAt: string
}

export interface AdminProductQuery {
  q?: string
  categoryId?: number
  active?: boolean
  page: number
  size: number
  sort: ProductSort
}

export interface CreateProductRequest {
  categoryId: number
  name: string
  description: string
  price: number
  stock: number
}

export interface UpdateProductRequest {
  categoryId: number
  name: string
  description: string
  price: number
  active: boolean
}

export interface StockResponse {
  productId: number
  stock: number
  updatedAt: string
}

export interface ProductFormModel {
  categoryId?: number
  name: string
  description: string
  price: number
  stock: number
  active: boolean
}
