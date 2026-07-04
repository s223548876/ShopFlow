import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useAuthStore } from '../features/auth/store'
import HomeView from '../views/HomeView.vue'
import { cartAccess } from './access'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../features/auth/views/LoginView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../features/auth/views/RegisterView.vue'),
    },
    {
      path: '/products',
      name: 'products',
      component: () => import('../features/catalog/views/ProductListView.vue'),
    },
    {
      path: '/products/:id',
      name: 'product-detail',
      component: () => import('../features/catalog/views/ProductDetailView.vue'),
    },
    {
      path: '/cart',
      name: 'cart',
      component: () => import('../features/cart/views/CartView.vue'),
      meta: { requiresCustomer: true },
    },
  ],
})

router.beforeEach((to) => {
  if (!to.meta.requiresCustomer) {
    return true
  }

  const auth = useAuthStore()
  const role = auth.getValidAccessToken() ? auth.role : null
  const access = cartAccess(role)
  if (access === 'login') {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (access === 'forbidden') {
    ElMessage.error('沒有權限使用購物車')
    return { name: 'home' }
  }
  return true
})

export default router
