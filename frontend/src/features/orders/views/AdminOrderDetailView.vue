<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElMessage, ElMessageBox, ElResult, ElSkeleton, ElTag } from 'element-plus'
import { useRoute } from 'vue-router'

import { apiErrorMessage, getApiError } from '../../../api/errors'
import { formatAmount, formatDateTime } from '../../../lib/format'
import { refreshAfter } from '../actions'
import { getAdminOrder, updateAdminOrderStatus } from '../api'
import { adminNextStatuses, confirmAdminStatusChange, statusLabel, statusTagType } from '../status'
import type { AdminOrderResponse, OrderStatus } from '../types'

const route = useRoute()
const orderId = Number(route.params.id)
const order = ref<AdminOrderResponse | null>(null)
const loading = ref(false)
const updatingStatus = ref<OrderStatus | null>(null)
const notFound = ref(false)
const message = ref('')

async function loadOrder(): Promise<void> {
  if (!Number.isSafeInteger(orderId) || orderId <= 0) {
    notFound.value = true
    return
  }
  loading.value = true
  message.value = ''
  try {
    order.value = await getAdminOrder(orderId)
  } catch (error) {
    if (getApiError(error)?.code === 'ORDER_NOT_FOUND') {
      notFound.value = true
    } else {
      message.value = apiErrorMessage(error)
    }
  } finally {
    loading.value = false
  }
}

async function updateStatus(nextStatus: OrderStatus): Promise<void> {
  const confirmed = await confirmAdminStatusChange(nextStatus, () => ElMessageBox.confirm(
    '取消後將回補商品庫存，確定要取消此訂單嗎？',
    '確認取消訂單',
    { confirmButtonText: '確認取消', cancelButtonText: '返回', type: 'warning' },
  ))
  if (!confirmed) return

  updatingStatus.value = nextStatus
  try {
    await refreshAfter(() => updateAdminOrderStatus(orderId, nextStatus), loadOrder)
    ElMessage.success('訂單狀態已更新')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    updatingStatus.value = null
  }
}

onMounted(loadOrder)
</script>

<template>
  <el-result v-if="notFound" icon="warning" title="找不到訂單">
    <template #extra><RouterLink class="secondary-link" to="/admin/orders">返回訂單管理</RouterLink></template>
  </el-result>
  <section v-else aria-labelledby="admin-order-title">
    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <el-skeleton v-if="loading && !order" :rows="7" animated />
    <article v-if="order" class="order-detail">
      <div class="order-detail-heading">
        <div>
          <p class="eyebrow">Admin order</p>
          <h1 id="admin-order-title">訂單 #{{ order.id }}</h1>
          <p>Customer：{{ order.user.displayName }}（{{ order.user.email }}）</p>
          <p>建立時間：{{ formatDateTime(order.createdAt) }}</p>
          <p v-if="order.paidAt">付款時間：{{ formatDateTime(order.paidAt) }}</p>
        </div>
        <el-tag :type="statusTagType(order.status)" size="large">{{ statusLabel(order.status) }}</el-tag>
      </div>

      <div class="table-scroll">
        <table class="order-table">
          <thead><tr><th>商品名稱</th><th>單價</th><th>數量</th><th>小計</th></tr></thead>
          <tbody>
            <tr v-for="item in order.items" :key="item.productId">
              <td>{{ item.productName }}</td>
              <td>{{ formatAmount(item.unitPrice) }}</td>
              <td>{{ item.quantity }}</td>
              <td>{{ formatAmount(item.subtotal) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="order-total"><span>訂單總額</span><strong>{{ formatAmount(order.totalAmount) }}</strong></div>
      <div class="order-actions">
        <RouterLink class="secondary-link" to="/admin/orders">返回訂單管理</RouterLink>
        <el-button
          v-for="nextStatus in adminNextStatuses(order.status)"
          :key="nextStatus"
          :type="nextStatus === 'CANCELLED' ? 'danger' : 'primary'"
          :plain="nextStatus === 'CANCELLED'"
          :loading="updatingStatus === nextStatus"
          :disabled="updatingStatus !== null || loading"
          @click="updateStatus(nextStatus)"
        >{{ statusLabel(nextStatus) }}</el-button>
      </div>
    </article>
  </section>
</template>
