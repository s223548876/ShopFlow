<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElMessage, ElResult, ElSkeleton } from 'element-plus'
import { useRoute } from 'vue-router'

import { apiErrorMessage, fieldErrors, getApiError } from '../../../api/errors'
import { getCategories, type CategoryResponse } from '../../catalog/api'
import { saveAndApply } from '../actions'
import { getAdminProduct, updateAdminProduct } from '../api'
import ProductForm from '../components/ProductForm.vue'
import { updateRequest } from '../form'
import type { AdminProductResponse, ProductFormModel } from '../types'

const route = useRoute()
const productId = Number(route.params.id)
const categories = ref<CategoryResponse[]>([])
const product = ref<AdminProductResponse | null>(null)
const form = ref<ProductFormModel>({ name: '', description: '', price: 0.01, stock: 0, active: true })
const errors = ref<Record<string, string>>({})
const message = ref('')
const loading = ref(false)
const submitting = ref(false)
const notFound = ref(false)

function applyProduct(saved: AdminProductResponse): void {
  product.value = saved
  form.value = {
    categoryId: saved.category.id,
    name: saved.name,
    description: saved.description,
    price: saved.price,
    stock: saved.stock,
    active: saved.active,
  }
}

async function load(): Promise<void> {
  if (!Number.isSafeInteger(productId) || productId <= 0) {
    notFound.value = true
    return
  }
  loading.value = true
  message.value = ''
  try {
    const [saved, categoryList] = await Promise.all([getAdminProduct(productId), getCategories()])
    categories.value = categoryList
    applyProduct(saved)
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

async function submit(): Promise<void> {
  errors.value = {}
  message.value = ''
  submitting.value = true
  try {
    await saveAndApply(
      () => updateAdminProduct(productId, updateRequest(form.value)),
      applyProduct,
    )
    ElMessage.success('商品已更新')
  } catch (error) {
    errors.value = fieldErrors(error)
    message.value = apiErrorMessage(error)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-result v-if="notFound" icon="warning" title="找不到商品">
    <template #extra><RouterLink class="secondary-link" to="/admin/products">返回商品管理</RouterLink></template>
  </el-result>
  <section v-else class="form-card product-form-card" aria-labelledby="edit-product-title">
    <p class="eyebrow">Admin products</p>
    <h1 id="edit-product-title">編輯商品<span v-if="product"> #{{ product.id }}</span></h1>
    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <el-skeleton v-if="loading && !product" :rows="7" animated />
    <ProductForm
      v-if="product"
      v-model="form"
      :categories="categories"
      :errors="errors"
      mode="edit"
      :submitting="submitting"
      @submit="submit"
    />
  </section>
</template>
