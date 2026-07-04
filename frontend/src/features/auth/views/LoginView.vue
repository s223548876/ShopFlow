<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElAlert, ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'

import { apiErrorMessage, fieldErrors } from '../../../api/errors'
import { safeRedirect } from '../jwt'
import { useAuthStore } from '../store'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const form = reactive({ email: '', password: '' })
const errors = ref<Record<string, string>>({})
const message = ref('')
const loading = ref(false)

async function submit(): Promise<void> {
  errors.value = {}
  message.value = ''
  loading.value = true
  try {
    await auth.login(form)
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error) {
    errors.value = fieldErrors(error)
    message.value = apiErrorMessage(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="form-card" aria-labelledby="login-title">
    <p class="eyebrow">Customer access</p>
    <h1 id="login-title">登入</h1>
    <el-alert
      v-if="route.query.registered === '1'"
      title="註冊完成，請登入"
      type="success"
      :closable="false"
      show-icon
    />
    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <el-form :model="form" label-position="top" @keyup.enter="submit">
      <el-form-item label="Email" :error="errors.email">
        <el-input v-model="form.email" type="email" autocomplete="email" maxlength="254" />
      </el-form-item>
      <el-form-item label="密碼" :error="errors.password">
        <el-input
          v-model="form.password"
          type="password"
          autocomplete="current-password"
          minlength="8"
          maxlength="72"
          show-password
        />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="submit">登入</el-button>
    </el-form>
    <p class="form-footer">還沒有帳號？<RouterLink to="/register">前往註冊</RouterLink></p>
  </section>
</template>
