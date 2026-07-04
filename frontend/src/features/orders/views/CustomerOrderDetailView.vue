<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElMessage, ElResult, ElSkeleton, ElTag } from 'element-plus'
import { useRoute } from 'vue-router'

import { apiErrorMessage, getApiError } from '../../../api/errors'
import { formatAmount, formatDateTime } from '../../../lib/format'
import { refreshAfter } from '../actions'
import { getOrder, payOrder } from '../api'
import { canPayOrder, statusLabel, statusTagType } from '../status'
import type { OrderResponse } from '../types'

const route = useRoute()
const orderId = Number(route.params.id)
const order = ref<OrderResponse | null>(null)
const loading = ref(false)
const paying = ref(false)
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
    order.value = await getOrder(orderId)
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

async function pay(): Promise<void> {
  if (!order.value || !canPayOrder(order.value.status)) return
  paying.value = true
  try {
    await refreshAfter(() => payOrder(orderId), loadOrder)
    ElMessage.success('付款成功')
  } catch (error) {
    ElMessage.error(apiErrorMessage(error))
  } finally {
    paying.value = false
  }
}

onMounted(loadOrder)
</script>

<template>
  <el-result v-if="notFound" icon="warning" title="找不到訂單" sub-title="訂單不存在或不屬於目前使用者">
    <template #extra><RouterLink class="secondary-link" to="/orders">返回訂單列表</RouterLink></template>
  </el-result>
  <section v-else aria-labelledby="order-title">
    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <el-skeleton v-if="loading && !order" :rows="7" animated />
    <article v-if="order" class="order-detail">
      <div class="order-detail-heading">
        <div>
          <p class="eyebrow">Customer order</p>
          <h1 id="order-title">訂單 #{{ order.id }}</h1>
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
        <RouterLink class="secondary-link" to="/orders">返回訂單列表</RouterLink>
        <el-button
          v-if="canPayOrder(order.status)"
          type="primary"
          :loading="paying"
          :disabled="loading"
          @click="pay"
        >模擬付款</el-button>
      </div>
    </article>
  </section>
</template>
