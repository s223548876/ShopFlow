<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElAlert, ElButton, ElEmpty, ElOption, ElPagination, ElSelect, ElSkeleton, ElTag } from 'element-plus'

import { apiErrorMessage } from '../../../api/errors'
import type { PageResponse } from '../../../api/types'
import { formatAmount, formatDateTime } from '../../../lib/format'
import { getAdminOrders } from '../api'
import { ORDER_STATUSES, statusLabel, statusTagType } from '../status'
import type { OrderSort, OrderStatus, OrderSummaryResponse } from '../types'

const orders = ref<PageResponse<OrderSummaryResponse> | null>(null)
const status = ref<OrderStatus | ''>('')
const sort = ref<OrderSort>('createdAt,desc')
const page = ref(0)
const loading = ref(false)
const message = ref('')

async function loadOrders(): Promise<void> {
  loading.value = true
  message.value = ''
  try {
    orders.value = await getAdminOrders({ status: status.value, page: page.value, size: 20, sort: sort.value })
  } catch (error) {
    message.value = apiErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function applyFilters(): Promise<void> {
  page.value = 0
  await loadOrders()
}

async function changePage(nextPage: number): Promise<void> {
  page.value = nextPage - 1
  await loadOrders()
}

onMounted(loadOrders)
</script>

<template>
  <section aria-labelledby="admin-orders-title">
    <div class="page-heading">
      <div>
        <p class="eyebrow">Admin orders</p>
        <h1 id="admin-orders-title">訂單管理</h1>
      </div>
      <p v-if="orders">共 {{ orders.totalElements }} 筆訂單</p>
    </div>

    <form class="filter-bar order-filter" @submit.prevent="applyFilters">
      <el-select v-model="status" clearable placeholder="全部狀態" aria-label="訂單狀態">
        <el-option v-for="value in ORDER_STATUSES" :key="value" :label="statusLabel(value)" :value="value" />
      </el-select>
      <el-select v-model="sort" aria-label="建立時間排序">
        <el-option label="最新建立" value="createdAt,desc" />
        <el-option label="最早建立" value="createdAt,asc" />
      </el-select>
      <el-button type="primary" native-type="submit">套用</el-button>
    </form>

    <el-alert v-if="message" :title="message" type="error" :closable="false" show-icon />
    <el-skeleton v-if="loading" :rows="6" animated />
    <div v-else class="order-list">
      <el-empty v-if="orders?.content.length === 0" description="找不到符合條件的訂單" />
      <RouterLink
        v-for="order in orders?.content ?? []"
        :key="order.id"
        class="order-card"
        :to="`/admin/orders/${order.id}`"
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
