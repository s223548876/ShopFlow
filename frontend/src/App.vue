<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { watch } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { useRouter } from 'vue-router'
import { ElBadge } from 'element-plus'

import { useAuthStore } from './features/auth/store'
import { useCartBadgeStore } from './features/cart/store'

const auth = useAuthStore()
const cartBadge = useCartBadgeStore()
const router = useRouter()
const { accessToken, isAuthenticated, role } = storeToRefs(auth)
const { itemCount } = storeToRefs(cartBadge)

watch([role, accessToken], ([currentRole, token]) => {
  cartBadge.clearCartBadge()
  if (currentRole === 'CUSTOMER' && token) {
    void cartBadge.refreshCartBadge().catch(() => undefined)
  }
}, { immediate: true })

async function logout(): Promise<void> {
  auth.logout()
  cartBadge.clearCartBadge()
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
        <el-badge v-if="role === 'CUSTOMER'" :value="String(itemCount)" :hidden="itemCount === 0">
          <RouterLink
            to="/cart"
            :aria-label="itemCount > 0 ? `購物車，目前共 ${itemCount} 件商品` : '購物車'"
          >購物車</RouterLink>
        </el-badge>
        <RouterLink v-if="role === 'CUSTOMER'" to="/orders">我的訂單</RouterLink>
        <RouterLink v-if="role === 'ADMIN'" to="/admin/products">商品管理</RouterLink>
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
