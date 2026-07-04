<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import { useRouter } from 'vue-router'

import { apiErrorMessage, fieldErrors } from '../../../api/errors'
import { register } from '../api'

const router = useRouter()
const form = reactive({ email: '', password: '', displayName: '' })
const errors = ref<Record<string, string>>({})
const message = ref('')
const loading = ref(false)

async function submit(): Promise<void> {
  errors.value = {}
  message.value = ''
  loading.value = true
  try {
    await register(form)
    await router.replace({ name: 'login', query: { registered: '1' } })
  } catch (error) {
    errors.value = fieldErrors(error)
    message.value = apiErrorMessage(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="form-card" aria-labelledby="register-title">
    <p class="eyebrow">Create account</p>
    <h1 id="register-title">註冊</h1>
    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <el-form :model="form" label-position="top" @keyup.enter="submit">
      <el-form-item label="顯示名稱" :error="errors.displayName">
        <el-input v-model="form.displayName" autocomplete="name" maxlength="100" />
      </el-form-item>
      <el-form-item label="Email" :error="errors.email">
        <el-input v-model="form.email" type="email" autocomplete="email" maxlength="254" />
      </el-form-item>
      <el-form-item label="密碼（8–72 字元）" :error="errors.password">
        <el-input
          v-model="form.password"
          type="password"
          autocomplete="new-password"
          minlength="8"
          maxlength="72"
          show-password
        />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="submit">建立帳號</el-button>
    </el-form>
    <p class="form-footer">已有帳號？<RouterLink to="/login">前往登入</RouterLink></p>
  </section>
</template>
