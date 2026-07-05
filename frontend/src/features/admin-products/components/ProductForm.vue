<script setup lang="ts">
import { computed } from 'vue'
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus'

import type { CategoryResponse } from '../../catalog/api'
import { isProductFormValid } from '../form'
import type { ProductFormModel } from '../types'

const props = defineProps<{
  categories: CategoryResponse[]
  errors: Record<string, string>
  mode: 'create' | 'edit'
  submitting: boolean
}>()
const emit = defineEmits<{ submit: [] }>()
const form = defineModel<ProductFormModel>({ required: true })
const valid = computed(() => isProductFormValid(form.value, props.mode))
</script>

<template>
  <el-form :model="form" label-position="top">
    <el-form-item label="商品名稱" :error="errors.name">
      <el-input v-model="form.name" maxlength="200" show-word-limit />
    </el-form-item>
    <el-form-item label="商品描述" :error="errors.description">
      <el-input v-model="form.description" type="textarea" :rows="5" maxlength="5000" show-word-limit />
    </el-form-item>
    <el-form-item label="分類" :error="errors.categoryId">
      <el-select v-model="form.categoryId" placeholder="選擇分類">
        <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
      </el-select>
    </el-form-item>
    <el-form-item label="價格" :error="errors.price">
      <el-input-number v-model="form.price" :min="0.01" :max="9999999999.99" :precision="2" :step="1" />
    </el-form-item>
    <el-form-item v-if="mode === 'create'" label="初始庫存" :error="errors.stock">
      <el-input-number v-model="form.stock" :min="0" :max="2147483647" :precision="0" />
    </el-form-item>
    <el-form-item v-else label="公開販售" :error="errors.active">
      <el-switch v-model="form.active" active-text="啟用" inactive-text="停用" />
    </el-form-item>
    <div class="form-actions">
      <RouterLink class="secondary-link" to="/admin/products">返回商品管理</RouterLink>
      <el-button type="primary" :loading="submitting" :disabled="!valid || submitting" @click="emit('submit')">
        {{ mode === 'create' ? '建立商品' : '儲存變更' }}
      </el-button>
    </div>
  </el-form>
</template>
