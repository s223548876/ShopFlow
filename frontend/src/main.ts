import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import { configureApiClient } from './api/client'
import { safeRedirect } from './features/auth/jwt'
import { useAuthStore } from './features/auth/store'
import router from './router'
import './style.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia).use(router)

const auth = useAuthStore(pinia)
configureApiClient({
  getAccessToken: () => auth.getValidAccessToken(),
  onAuthenticationRequired: () => {
    const redirect = safeRedirect(router.currentRoute.value.fullPath)
    auth.logout()
    if (router.currentRoute.value.name !== 'login') {
      void router.replace({ name: 'login', query: { redirect } })
    }
  },
  onAccessDenied: () => {
    ElMessage.error('沒有權限執行此操作')
  },
})

app.mount('#app')
