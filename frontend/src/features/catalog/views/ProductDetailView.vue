<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElInputNumber, ElMessage, ElResult, ElSkeleton } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { apiErrorMessage, getApiError } from '../../../api/errors'
import { addCartItem } from '../../cart/api'
import { useAuthStore } from '../../auth/store'
import { formatAmount } from '../../../lib/format'
import { getProduct, type ProductDetailResponse } from '../api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const product = ref<ProductDetailResponse | null>(null)
const quantity = ref(1)
const loading = ref(false)
const adding = ref(false)
const notFound = ref(false)
const message = ref('')
const productId = Number(route.params.id)
const maxQuantity = computed(() => Math.min(999, product.value?.stock ?? 1))

async function loadProduct(): Promise<void> {
  if (!Number.isSafeInteger(productId) || productId <= 0) {
    notFound.value = true
    return
  }
  loading.value = true
  try {
    product.value = await getProduct(productId)
  } catch (error) {
    if (getApiError(error)?.code === 'PRODUCT_NOT_FOUND') {
      notFound.value = true
    } else {
      message.value = apiErrorMessage(error)
    }
  } finally {
    loading.value = false
  }
}

async function addToCart(): Promise<void> {
  if (!product.value) {
    return
  }
  if (!auth.getValidAccessToken()) {
    await router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (auth.role !== 'CUSTOMER') {
    ElMessage.error('沒有權限使用購物車')
    return
  }

  adding.value = true
  try {
    await addCartItem({ productId: product.value.id, quantity: quantity.value })
    ElMessage.success('已加入購物車')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    adding.value = false
  }
}

onMounted(loadProduct)
</script>

<template>
  <el-result v-if="notFound" icon="warning" title="找不到商品" sub-title="商品不存在或已停止販售">
    <template #extra><RouterLink class="secondary-link" to="/products">返回商品列表</RouterLink></template>
  </el-result>
  <section v-else aria-labelledby="product-title">
    <el-skeleton v-if="loading" :rows="6" animated />
    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <article v-if="product" class="detail-card">
      <div>
        <p class="eyebrow">{{ product.category.name }}</p>
        <h1 id="product-title">{{ product.name }}</h1>
        <p class="product-description">{{ product.description }}</p>
      </div>
      <aside class="purchase-panel">
        <p class="price">{{ formatAmount(product.price) }}</p>
        <p :class="product.stock > 0 ? 'stock-ok' : 'stock-empty'">
          {{ product.stock > 0 ? `庫存 ${product.stock} 件` : '目前無庫存' }}
        </p>
        <el-input-number v-model="quantity" :min="1" :max="maxQuantity" :disabled="product.stock === 0" />
        <el-button type="primary" :loading="adding" :disabled="product.stock === 0" @click="addToCart">
          加入購物車
        </el-button>
      </aside>
    </article>
  </section>
</template>
