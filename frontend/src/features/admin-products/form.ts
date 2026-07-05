import type {
  CreateProductRequest,
  ProductFormModel,
  UpdateProductRequest,
} from './types'

export function isProductFormValid(form: ProductFormModel, mode: 'create' | 'edit'): boolean {
  return Number.isSafeInteger(form.categoryId)
    && (form.categoryId ?? 0) > 0
    && form.name.trim().length > 0
    && form.name.trim().length <= 200
    && form.description.length <= 5000
    && Number.isFinite(form.price)
    && form.price > 0
    && form.price <= 9_999_999_999.99
    && (mode === 'edit' || Number.isSafeInteger(form.stock) && form.stock >= 0)
}

export function createRequest(form: ProductFormModel): CreateProductRequest {
  return {
    categoryId: form.categoryId!,
    name: form.name.trim(),
    description: form.description,
    price: form.price,
    stock: form.stock,
  }
}

export function updateRequest(form: ProductFormModel): UpdateProductRequest {
  return {
    categoryId: form.categoryId!,
    name: form.name.trim(),
    description: form.description,
    price: form.price,
    active: form.active,
  }
}
