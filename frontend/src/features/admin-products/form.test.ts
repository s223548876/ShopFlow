import { describe, expect, it } from 'vitest'

import { createRequest, isProductFormValid, updateRequest } from './form'
import type { ProductFormModel } from './types'

const validForm: ProductFormModel = {
  categoryId: 3,
  name: '  Keyboard  ',
  description: 'Mechanical keyboard',
  price: 2500,
  stock: 12,
  active: true,
}

describe('admin product form', () => {
  it('requires valid documented values', () => {
    expect(isProductFormValid(validForm, 'create')).toBe(true)
    expect(isProductFormValid({ ...validForm, name: '   ' }, 'create')).toBe(false)
    expect(isProductFormValid({ ...validForm, price: 0 }, 'create')).toBe(false)
    expect(isProductFormValid({ ...validForm, stock: -1 }, 'create')).toBe(false)
    expect(isProductFormValid({ ...validForm, categoryId: undefined }, 'edit')).toBe(false)
  })

  it('maps create and update bodies without leaking unrelated fields', () => {
    expect(createRequest(validForm)).toEqual({
      categoryId: 3,
      name: 'Keyboard',
      description: 'Mechanical keyboard',
      price: 2500,
      stock: 12,
    })
    expect(updateRequest({ ...validForm, active: false })).toEqual({
      categoryId: 3,
      name: 'Keyboard',
      description: 'Mechanical keyboard',
      price: 2500,
      active: false,
    })
  })
})
