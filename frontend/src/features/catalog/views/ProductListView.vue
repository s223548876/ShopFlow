<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElEmpty,
  ElInput,
  ElOption,
  ElPagination,
  ElSelect,
  ElSkeleton,
} from 'element-plus'

import { apiErrorMessage } from '../../../api/errors'
import type { PageResponse } from '../../../api/types'
import { formatAmount } from '../../../lib/format'
import {
  getCategories,
  getProducts,
  type CategoryResponse,
  type ProductSort,
  type ProductSummaryResponse,
} from '../api'

const categories = ref<CategoryResponse[]>([])
const products = ref<PageResponse<ProductSummaryResponse> | null>(null)
const search = ref('')
const categoryId = ref<number | undefined>()
const sort = ref<ProductSort>('createdAt,desc')
const page = ref(0)
const loading = ref(false)
const message = ref('')

const sortOptions: Array<{ label: string; value: ProductSort }> = [
  { label: '最新上架', value: 'createdAt,desc' },
  { label: '最早上架', value: 'createdAt,asc' },
  { label: '名稱 A–Z', value: 'name,asc' },
  { label: '名稱 Z–A', value: 'name,desc' },
  { label: '價格低至高', value: 'price,asc' },
  { label: '價格高至低', value: 'price,desc' },
]

async function loadCategories(): Promise<void> {
  try {
    categories.value = await getCategories()
  } catch (error) {
    message.value = apiErrorMessage(error)
  }
}

async function loadProducts(): Promise<void> {
  loading.value = true
  message.value = ''
  try {
    products.value = await getProducts({
      q: search.value.trim() || undefined,
      categoryId: categoryId.value,
      page: page.value,
      size: 20,
      sort: sort.value,
    })
  } catch (error) {
    message.value = apiErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function applyFilters(): Promise<void> {
  page.value = 0
  await loadProducts()
}

async function changePage(nextPage: number): Promise<void> {
  page.value = nextPage - 1
  await loadProducts()
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadProducts()])
})
</script>

<template>
  <section aria-labelledby="products-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Catalog</p>
        <h1 id="products-title">商品目錄</h1>
      </div>
      <p v-if="products">共 {{ products.totalElements }} 項商品</p>
    </div>

    <form class="filter-bar" @submit.prevent="applyFilters">
      <el-input v-model="search" clearable placeholder="搜尋商品名稱或描述" aria-label="搜尋商品" />
      <el-select v-model="categoryId" clearable placeholder="全部分類" aria-label="商品分類">
        <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
      </el-select>
      <el-select v-model="sort" aria-label="商品排序">
        <el-option v-for="option in sortOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-button type="primary" native-type="submit">套用</el-button>
    </form>

    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <el-skeleton v-if="loading" :rows="5" animated />
    <div v-else class="product-grid" aria-live="polite">
      <article v-for="product in products?.content ?? []" :key="product.id" class="product-card">
        <p class="product-category">{{ product.category.name }}</p>
        <h2>{{ product.name }}</h2>
        <p class="price">{{ formatAmount(product.price) }}</p>
        <p :class="product.stock > 0 ? 'stock-ok' : 'stock-empty'">
          {{ product.stock > 0 ? `尚有 ${product.stock} 件` : '目前無庫存' }}
        </p>
        <RouterLink class="secondary-link" :to="`/products/${product.id}`">查看詳情</RouterLink>
      </article>
      <el-empty v-if="products?.content.length === 0" description="找不到符合條件的商品" />
    </div>

    <el-pagination
      v-if="products && products.totalElements > products.size"
      class="pagination"
      background
      layout="prev, pager, next"
      :current-page="products.page + 1"
      :page-size="products.size"
      :total="products.totalElements"
      @current-change="changePage"
    />
  </section>
</template>
