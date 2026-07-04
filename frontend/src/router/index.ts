import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useAuthStore } from '../features/auth/store'
import HomeView from '../views/HomeView.vue'
import { roleAccess } from './access'

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
    {
      path: '/orders',
      name: 'orders',
      component: () => import('../features/orders/views/CustomerOrderListView.vue'),
      meta: { requiresCustomer: true },
    },
    {
      path: '/orders/:id',
      name: 'order-detail',
      component: () => import('../features/orders/views/CustomerOrderDetailView.vue'),
      meta: { requiresCustomer: true },
    },
    {
      path: '/admin/orders',
      name: 'admin-orders',
      component: () => import('../features/orders/views/AdminOrderListView.vue'),
      meta: { requiresAdmin: true },
    },
    {
      path: '/admin/orders/:id',
      name: 'admin-order-detail',
      component: () => import('../features/orders/views/AdminOrderDetailView.vue'),
      meta: { requiresAdmin: true },
    },
  ],
})

router.beforeEach((to) => {
  const requiredRole = to.meta.requiresAdmin ? 'ADMIN' : to.meta.requiresCustomer ? 'CUSTOMER' : null
  if (!requiredRole) {
    return true
  }

  const auth = useAuthStore()
  const role = auth.getValidAccessToken() ? auth.role : null
  const access = roleAccess(role, requiredRole)
  if (access === 'login') {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (access === 'forbidden') {
    ElMessage.error(to.name === 'cart' ? '沒有權限使用購物車' : '沒有權限使用此頁面')
    return { name: 'home' }
  }
  return true
})

export default router
