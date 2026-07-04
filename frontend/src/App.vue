<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { RouterLink, RouterView } from 'vue-router'
import { useRouter } from 'vue-router'

import { useAuthStore } from './features/auth/store'

const auth = useAuthStore()
const router = useRouter()
const { isAuthenticated, role } = storeToRefs(auth)

async function logout(): Promise<void> {
  auth.logout()
  await router.push({ name: 'home' })
}
</script>

<template>
  <header class="site-header">
    <div class="nav-shell">
      <RouterLink class="brand" to="/">ShopFlow</RouterLink>
      <nav aria-label="主要導覽">
        <RouterLink to="/">首頁</RouterLink>
        <RouterLink to="/products">商品</RouterLink>
        <RouterLink v-if="role === 'CUSTOMER'" to="/cart">購物車</RouterLink>
        <RouterLink v-if="role === 'CUSTOMER'" to="/orders">我的訂單</RouterLink>
        <RouterLink v-if="role === 'ADMIN'" to="/admin/orders">訂單管理</RouterLink>
        <template v-if="!isAuthenticated">
          <RouterLink to="/login">登入</RouterLink>
          <RouterLink to="/register">註冊</RouterLink>
        </template>
        <button v-else class="nav-button" type="button" @click="logout">登出</button>
      </nav>
    </div>
  </header>

  <main class="page-shell">
    <RouterView />
  </main>
</template>
