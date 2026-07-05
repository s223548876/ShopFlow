import { ref } from 'vue'
import { defineStore } from 'pinia'

import { useAuthStore } from '../auth/store'
import { getCart, type CartResponse } from './api'

export const useCartBadgeStore = defineStore('cartBadge', () => {
  const auth = useAuthStore()
  const itemCount = ref(0)
  const isLoading = ref(false)
  let generation = 0
  let activeRequest: AbortController | null = null

  function totalQuantity(cart: CartResponse): number {
    return cart.items.reduce((sum, item) => sum + item.quantity, 0)
  }

  function setItemCountFromCart(cart: CartResponse): void {
    activeRequest?.abort()
    activeRequest = null
    generation += 1
    isLoading.value = false
    itemCount.value = totalQuantity(cart)
  }

  function clearCartBadge(): void {
    activeRequest?.abort()
    activeRequest = null
    generation += 1
    isLoading.value = false
    itemCount.value = 0
  }

  async function refreshCartBadge(): Promise<void> {
    if (auth.role !== 'CUSTOMER' || !auth.getValidAccessToken()) {
      clearCartBadge()
      return
    }

    activeRequest?.abort()
    const controller = new AbortController()
    activeRequest = controller
    const requestGeneration = ++generation
    isLoading.value = true
    try {
      const cart = await getCart(controller.signal)
      if (requestGeneration === generation) {
        itemCount.value = totalQuantity(cart)
      }
    } finally {
      if (requestGeneration === generation) {
        activeRequest = null
        isLoading.value = false
      }
    }
  }

  return { itemCount, isLoading, refreshCartBadge, setItemCountFromCart, clearCartBadge }
})
