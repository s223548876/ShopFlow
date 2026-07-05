<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElEmpty, ElPagination, ElSkeleton, ElTag } from 'element-plus'

import { apiErrorMessage } from '../../../api/errors'
import type { PageResponse } from '../../../api/types'
import { formatAmount, formatDateTime } from '../../../lib/format'
import { getOrders } from '../api'
import { statusLabel, statusTagType } from '../status'
import type { OrderSummaryResponse } from '../types'

const orders = ref<PageResponse<OrderSummaryResponse> | null>(null)
const page = ref(0)
const loading = ref(false)
const message = ref('')

async function loadOrders(): Promise<void> {
  loading.value = true
  message.value = ''
  try {
    orders.value = await getOrders({ page: page.value, size: 20, sort: 'createdAt,desc' })
  } catch (error) {
    message.value = apiErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function changePage(nextPage: number): Promise<void> {
  page.value = nextPage - 1
  await loadOrders()
}

onMounted(loadOrders)
</script>

<template>
  <section aria-labelledby="orders-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Customer orders</p>
        <h1 id="orders-title">我的訂單</h1>
      </div>
      <p v-if="orders">共 {{ orders.totalElements }} 筆訂單</p>
    </div>

    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <el-skeleton v-if="loading" :rows="6" animated />
    <div v-else class="order-list">
      <el-empty v-if="orders?.content.length === 0" description="目前沒有訂單" />
      <RouterLink
        v-for="order in orders?.content ?? []"
        :key="order.id"
        class="order-card"
        :to="`/orders/${order.id}`"
      >
        <div>
          <strong>訂單 #{{ order.id }}</strong>
          <p>{{ formatDateTime(order.createdAt) }}</p>
        </div>
        <el-tag :type="statusTagType(order.status)">{{ statusLabel(order.status) }}</el-tag>
        <span>共 {{ order.itemCount }} 件</span>
        <strong>{{ formatAmount(order.totalAmount) }}</strong>
      </RouterLink>
    </div>

    <el-pagination
      v-if="orders && orders.totalElements > orders.size"
      class="pagination"
      background
      layout="prev, pager, next"
      :current-page="orders.page + 1"
      :page-size="orders.size"
      :total="orders.totalElements"
      @current-change="changePage"
    />
  </section>
</template>
