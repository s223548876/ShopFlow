import axios from 'axios'

import type { ApiErrorResponse } from './types'

const messages: Record<string, string> = {
  EMAIL_ALREADY_EXISTS: '此 Email 已經註冊',
  INVALID_CREDENTIALS: 'Email 或密碼錯誤',
  VALIDATION_ERROR: '請檢查輸入內容',
  MALFORMED_REQUEST: '請求內容格式錯誤',
  PRODUCT_NOT_FOUND: '找不到此商品',
  CART_ITEM_ALREADY_EXISTS: '此商品已在購物車中',
  CART_ITEM_NOT_FOUND: '找不到此購物車品項',
  CART_EMPTY: '購物車目前是空的',
  PRODUCT_UNAVAILABLE: '此商品目前無法購買',
  INSUFFICIENT_STOCK: '商品庫存不足',
  ORDER_NOT_FOUND: '找不到此訂單',
  INVALID_ORDER_TRANSITION: '目前訂單狀態不允許此操作',
  AUTHENTICATION_REQUIRED: '登入已失效，請重新登入',
  ACCESS_DENIED: '沒有權限執行此操作',
}

export function getApiError(error: unknown): ApiErrorResponse | null {
  if (!axios.isAxiosError(error)) {
    return null
  }
  const data: unknown = error.response?.data
  if (!data || typeof data !== 'object') {
    return null
  }
  const candidate = data as Partial<ApiErrorResponse>
  if (
    typeof candidate.status !== 'number'
    || typeof candidate.code !== 'string'
    || typeof candidate.message !== 'string'
    || !Array.isArray(candidate.fieldErrors)
  ) {
    return null
  }
  return candidate as ApiErrorResponse
}

export function fieldErrors(error: unknown): Record<string, string> {
  return Object.fromEntries(
    (getApiError(error)?.fieldErrors ?? []).map(({ field, message }) => [field, message]),
  )
}

export function apiErrorMessage(error: unknown): string {
  const apiError = getApiError(error)
  if (!apiError) {
    return '無法連線，請稍後再試'
  }
  return messages[apiError.code] ?? apiError.message
}
