<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElEmpty,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElSkeleton,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'

import { apiErrorMessage } from '../../../api/errors'
import type { PageResponse } from '../../../api/types'
import { formatAmount, formatDateTime } from '../../../lib/format'
import { getCategories, type CategoryResponse, type ProductSort } from '../../catalog/api'
import { confirmThenRefresh } from '../actions'
import { deactivateAdminProduct, getAdminProducts, updateAdminProductStock } from '../api'
import type { AdminProductResponse } from '../types'

const categories = ref<CategoryResponse[]>([])
const products = ref<PageResponse<AdminProductResponse> | null>(null)
const search = ref('')
const categoryId = ref<number | undefined>()
const active = ref<boolean | undefined>()
const sort = ref<ProductSort>('createdAt,desc')
const page = ref(0)
const loading = ref(false)
const message = ref('')
const stockProduct = ref<AdminProductResponse | null>(null)
const stockQuantity = ref<number | undefined>()
const updatingStock = ref(false)
const deletingId = ref<number | null>(null)

const sortOptions: Array<{ label: string; value: ProductSort }> = [
  { label: '最近建立', value: 'createdAt,desc' },
  { label: '最早建立', value: 'createdAt,asc' },
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
    products.value = await getAdminProducts({
      q: search.value,
      categoryId: categoryId.value,
      active: active.value,
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

function openStockDialog(product: AdminProductResponse): void {
  stockProduct.value = product
  stockQuantity.value = product.stock
}

async function setStock(): Promise<void> {
  if (!stockProduct.value || !Number.isSafeInteger(stockQuantity.value) || (stockQuantity.value ?? -1) < 0) return
  updatingStock.value = true
  try {
    await updateAdminProductStock(stockProduct.value.id, stockQuantity.value!)
    await loadProducts()
    stockProduct.value = null
    ElMessage.success('庫存已更新')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    updatingStock.value = false
  }
}

async function deactivate(product: AdminProductResponse): Promise<void> {
  deletingId.value = product.id
  try {
    const changed = await confirmThenRefresh(
      () => ElMessageBox.confirm(
        `確定停用「${product.name}」嗎？商品將從公開列表移除，歷史訂單不受影響。`,
        '確認停用商品',
        { confirmButtonText: '確認停用', cancelButtonText: '返回', type: 'warning' },
      ),
      () => deactivateAdminProduct(product.id),
      loadProducts,
    )
    if (changed) ElMessage.success('商品已停用')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    deletingId.value = null
  }
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadProducts()])
})
</script>

<template>
  <section aria-labelledby="admin-products-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin products</p>
        <h1 id="admin-products-title">商品管理</h1>
      </div>
      <RouterLink class="primary-link admin-create-link" to="/admin/products/new">新增商品</RouterLink>
    </div>

    <form class="filter-bar admin-product-filter" @submit.prevent="applyFilters">
      <el-input v-model="search" clearable placeholder="搜尋商品名稱或描述" aria-label="搜尋商品" />
      <el-select v-model="categoryId" clearable placeholder="全部分類" aria-label="商品分類">
        <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
      </el-select>
      <el-select v-model="active" clearable placeholder="全部狀態" aria-label="商品狀態">
        <el-option label="啟用" :value="true" />
        <el-option label="停用" :value="false" />
      </el-select>
      <el-select v-model="sort" aria-label="商品排序">
        <el-option v-for="option in sortOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-button type="primary" native-type="submit" :loading="loading" :disabled="loading">套用</el-button>
    </form>

    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <el-skeleton v-if="loading" :rows="7" animated />
    <div v-else class="admin-product-table table-scroll">
      <el-empty v-if="products?.content.length === 0" description="找不到符合條件的商品" />
      <el-table v-else :data="products?.content ?? []">
        <el-table-column prop="name" label="商品名稱" min-width="180" />
        <el-table-column label="分類" min-width="120">
          <template #default="scope">{{ (scope.row as AdminProductResponse).category.name }}</template>
        </el-table-column>
        <el-table-column label="價格" min-width="120">
          <template #default="scope">{{ formatAmount((scope.row as AdminProductResponse).price) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="庫存" width="90" />
        <el-table-column label="狀態" width="90">
          <template #default="scope">
            <el-tag :type="(scope.row as AdminProductResponse).active ? 'success' : 'info'">
              {{ (scope.row as AdminProductResponse).active ? '啟用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新時間" min-width="170">
          <template #default="scope">{{ formatDateTime((scope.row as AdminProductResponse).updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="240" fixed="right">
          <template #default="scope">
            <div class="admin-actions">
              <RouterLink class="secondary-link" :to="`/admin/products/${(scope.row as AdminProductResponse).id}/edit`">編輯</RouterLink>
              <el-button @click="openStockDialog(scope.row as AdminProductResponse)">設定庫存</el-button>
              <el-button
                v-if="(scope.row as AdminProductResponse).active"
                type="danger"
                plain
                :loading="deletingId === (scope.row as AdminProductResponse).id"
                :disabled="deletingId !== null"
                @click="deactivate(scope.row as AdminProductResponse)"
              >停用</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
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

    <el-dialog
      :model-value="stockProduct !== null"
      title="設定庫存"
      width="min(420px, 90vw)"
      @close="stockProduct = null"
    >
      <p v-if="stockProduct">{{ stockProduct.name }}目前庫存：{{ stockProduct.stock }}</p>
      <el-input-number v-model="stockQuantity" :min="0" :max="2147483647" :precision="0" />
      <p v-if="stockQuantity !== undefined">設定後庫存：{{ stockQuantity }}</p>
      <template #footer>
        <el-button :disabled="updatingStock" @click="stockProduct = null">取消</el-button>
        <el-button
          type="primary"
          :loading="updatingStock"
          :disabled="!Number.isSafeInteger(stockQuantity) || (stockQuantity ?? -1) < 0"
          @click="setStock"
        >儲存庫存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
