<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElButton, ElEmpty, ElInputNumber, ElMessage, ElSkeleton, ElTag, ElAlert } from 'element-plus'
import { useRouter } from 'vue-router'

import { apiErrorMessage, getApiError } from '../../../api/errors'
import { formatAmount } from '../../../lib/format'
import { checkoutAndNavigate } from '../../orders/actions'
import { createOrder } from '../../orders/api'
import {
  deleteCartItem,
  getCart,
  updateCartItem,
  type CartItemResponse,
  type CartResponse,
} from '../api'

const cart = ref<CartResponse | null>(null)
const router = useRouter()
const quantities = ref<Record<number, number>>({})
const loading = ref(false)
const pendingItemId = ref<number | null>(null)
const checkoutLoading = ref(false)
const message = ref('')

async function loadCart(): Promise<void> {
  loading.value = true
  message.value = ''
  try {
    cart.value = await getCart()
    quantities.value = Object.fromEntries(cart.value.items.map((item) => [item.id, item.quantity]))
  } catch (error) {
    message.value = apiErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function update(item: CartItemResponse): Promise<void> {
  const quantity = quantities.value[item.id]
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 999) {
    ElMessage.error('數量必須介於 1 到 999')
    return
  }
  pendingItemId.value = item.id
  try {
    await updateCartItem(item.id, { quantity })
    await loadCart()
    ElMessage.success('數量已更新')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
    if (getApiError(error)?.code === 'CART_ITEM_NOT_FOUND') {
      await loadCart()
    }
  } finally {
    pendingItemId.value = null
  }
}

async function remove(itemId: number): Promise<void> {
  pendingItemId.value = itemId
  try {
    await deleteCartItem(itemId)
    await loadCart()
    ElMessage.success('商品已移除')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
    if (getApiError(error)?.code === 'CART_ITEM_NOT_FOUND') {
      await loadCart()
    }
  } finally {
    pendingItemId.value = null
  }
}

async function checkout(): Promise<void> {
  if (!cart.value?.items.length || checkoutLoading.value) return
  checkoutLoading.value = true
  try {
    await checkoutAndNavigate(
      createOrder,
      loadCart,
      (orderId) => router.push({ name: 'order-detail', params: { id: orderId } }),
    )
    ElMessage.success('訂單建立成功')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    checkoutLoading.value = false
  }
}

onMounted(loadCart)
</script>

<template>
  <section aria-labelledby="cart-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Customer cart</p>
        <h1 id="cart-title">購物車</h1>
      </div>
    </div>
    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <el-skeleton v-if="loading" :rows="6" animated />
    <div v-else class="cart-card">
      <el-empty v-if="cart?.items.length === 0" description="購物車目前是空的">
        <RouterLink class="secondary-link" to="/products">前往選購商品</RouterLink>
      </el-empty>
      <article v-for="item in cart?.items ?? []" :key="item.id" class="cart-row">
        <div class="cart-product">
          <RouterLink :to="`/products/${item.productId}`">{{ item.productName }}</RouterLink>
          <p>目前單價：{{ formatAmount(item.currentUnitPrice) }}</p>
          <el-tag v-if="!item.available" type="danger">目前無法購買</el-tag>
        </div>
        <div class="cart-actions">
          <el-input-number
            v-model="quantities[item.id]"
            :min="1"
            :max="999"
            :disabled="!item.available || pendingItemId === item.id"
          />
          <el-button
            :loading="pendingItemId === item.id"
            :disabled="!item.available"
            @click="update(item)"
          >更新</el-button>
          <el-button type="danger" plain :disabled="pendingItemId === item.id" @click="remove(item.id)">刪除</el-button>
        </div>
        <strong class="subtotal">小計 {{ formatAmount(item.subtotal) }}</strong>
      </article>
      <div v-if="cart?.items.length" class="cart-total">
        <span>預估總額</span>
        <strong>{{ formatAmount(cart.estimatedTotal) }}</strong>
        <el-button
          type="primary"
          :loading="checkoutLoading"
          :disabled="pendingItemId !== null"
          @click="checkout"
        >建立訂單</el-button>
      </div>
    </div>
    <p class="estimate-note">購物車金額僅供估算；建立訂單時由後端重新確認價格與庫存。</p>
  </section>
</template>
