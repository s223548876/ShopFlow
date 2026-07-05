<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'

import { apiErrorMessage, fieldErrors } from '../../../api/errors'
import { getCategories, type CategoryResponse } from '../../catalog/api'
import { createAndNavigate } from '../actions'
import { createAdminProduct } from '../api'
import ProductForm from '../components/ProductForm.vue'
import { createRequest } from '../form'
import type { ProductFormModel } from '../types'

const router = useRouter()
const categories = ref<CategoryResponse[]>([])
const form = ref<ProductFormModel>({
  categoryId: undefined,
  name: '',
  description: '',
  price: 0.01,
  stock: 0,
  active: true,
})
const errors = ref<Record<string, string>>({})
const message = ref('')
const submitting = ref(false)

async function loadCategories(): Promise<void> {
  try {
    categories.value = await getCategories()
  } catch (error) {
    message.value = apiErrorMessage(error)
  }
}

async function submit(): Promise<void> {
  errors.value = {}
  message.value = ''
  submitting.value = true
  try {
    await createAndNavigate(
      () => createAdminProduct(createRequest(form.value)),
      () => router.push({ name: 'admin-products' }),
    )
    ElMessage.success('商品已建立')
  } catch (error) {
    errors.value = fieldErrors(error)
    message.value = apiErrorMessage(error)
  } finally {
    submitting.value = false
  }
}

onMounted(loadCategories)
</script>

<template>
  <section class="form-card product-form-card" aria-labelledby="create-product-title">
    <p class="eyebrow">Admin products</p>
    <h1 id="create-product-title">新增商品</h1>
    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <ProductForm
      v-model="form"
      :categories="categories"
      :errors="errors"
      mode="create"
      :submitting="submitting"
      @submit="submit"
    />
  </section>
</template>
