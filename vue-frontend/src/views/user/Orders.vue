<script setup>
import { ref, onMounted } from 'vue'
import { orderApi } from '@/api'

const orders = ref([])
const loading = ref(true)
const snackbar = ref({ show: false, message: '', color: 'success' })
const confirmDialog = ref({ show: false, orderId: null })
const expandedOrderId = ref(null)

onMounted(async () => { await loadOrders() })

async function loadOrders() {
  loading.value = true
  try {
    const response = await orderApi.getMyOrders()
    orders.value = response.data.data || []
  } catch (error) {
    console.error('Failed to load orders:', error)
  } finally {
    loading.value = false
  }
}

async function handlePay() {
  const id = confirmDialog.value.orderId
  confirmDialog.value.show = false
  try {
    await orderApi.pay(id, 'WECHAT')
    snackbar.value = { show: true, message: '支付成功！', color: 'success' }
    await loadOrders()
  } catch (error) {
    snackbar.value = { show: true, message: error.response?.data?.message || '支付失败', color: 'error' }
  }
}

function getStatusColor(status) {
  const map = { 0: 'error', 1: 'warning', 2: 'success', 3: 'info' }
  return map[status] || 'grey'
}

function getStatusText(status) {
  const map = { 0: '已取消', 1: '待支付', 2: '已支付', 3: '已完成' }
  return map[status] || '未知'
}

function formatDate(str) {
  if (!str) return '-'
  return new Date(str).toLocaleString('zh-CN')
}

function toggleFeeDetail(orderId) {
  expandedOrderId.value = expandedOrderId.value === orderId ? null : orderId
}

function buildFeeExplanation(o) {
  const parts = []
  if (o.parkingFee > 0) {
    parts.push(`停车费 ¥${o.parkingFee}（按分时段计费：早高峰 08:00-10:00 / 晚高峰 17:00-21:00 为 1.5 倍率，谷电 23:00-07:00 为 0.5 倍率，其他时段 1.0 倍率）`)
  }
  if (o.chargingFee > 0) {
    parts.push(`充电费 ¥${o.chargingFee}（按实际用电量 × 电价计算）`)
  }
  if (o.serviceFee > 0) {
    parts.push(`服务费 ¥${o.serviceFee}（固定服务费）`)
  }
  return parts.join('；')
}
</script>

<template>
  <div>
    <v-container class="py-6">
      <h1 class="text-h5 font-weight-bold mb-6">
        <v-icon class="mr-2">mdi-receipt-text</v-icon>我的订单
      </h1>

      <div v-if="loading" class="text-center py-8">
        <v-progress-circular indeterminate color="primary" size="48" />
      </div>

      <div v-else-if="orders.length === 0" class="text-center py-12">
        <v-icon size="64" color="grey-lighten-1">mdi-receipt-text</v-icon>
        <p class="text-grey mt-4 mb-4">暂无订单记录</p>
        <v-btn to="/" color="primary" prepend-icon="mdi-ev-station">去预约充电</v-btn>
      </div>

      <div v-else>
        <v-card v-for="o in orders" :key="o.id" class="mb-4" rounded="lg">
          <v-card-text class="pa-5">
            <div class="d-flex align-center justify-space-between mb-4">
              <span class="text-subtitle-2 font-weight-bold">{{ o.orderNo }}</span>
              <v-chip :color="getStatusColor(o.status)" size="small" variant="tonal">
                {{ getStatusText(o.status) }}
              </v-chip>
            </div>

            <v-table density="compact" class="bg-transparent">
              <tbody>
                <tr><td class="text-grey">停车费</td><td class="text-right">¥{{ o.parkingFee }}</td></tr>
                <tr><td class="text-grey">服务费</td><td class="text-right">¥{{ o.serviceFee }}</td></tr>
                <tr><td class="text-grey">充电费</td><td class="text-right">¥{{ o.chargingFee }}</td></tr>
                <tr>
                  <td class="font-weight-bold">总计</td>
                  <td class="text-right text-primary font-weight-bold text-subtitle-2">¥{{ o.totalAmount }}</td>
                </tr>
              </tbody>
            </v-table>

            <div class="text-caption text-grey mt-3">创建时间：{{ formatDate(o.createdAt) }}</div>

            <!-- 费用说明 -->
            <div class="mt-2">
              <v-btn
                variant="text"
                size="x-small"
                density="compact"
                :append-icon="expandedOrderId === o.id ? 'mdi-chevron-up' : 'mdi-chevron-down'"
                @click="toggleFeeDetail(o.id)"
              >
                费用说明
              </v-btn>
              <v-expand-transition>
                <div v-if="expandedOrderId === o.id" class="mt-2">
                  <v-sheet color="grey-lighten-4" rounded="lg" class="pa-3">
                    <div class="text-body-2 mb-1">费用构成：总计 = 停车费 + 充电费 + 服务费</div>
                    <div class="text-caption text-grey">{{ buildFeeExplanation(o) }}</div>
                  </v-sheet>
                </div>
              </v-expand-transition>
            </div>
          </v-card-text>

          <template v-if="o.status === 1">
            <v-divider />
            <v-card-actions class="pa-3">
              <v-btn color="primary" variant="flat" block @click="confirmDialog = { show: true, orderId: o.id }">
                模拟支付
              </v-btn>
            </v-card-actions>
          </template>
        </v-card>
      </div>
    </v-container>

    <!-- Pay Confirm Dialog -->
    <v-dialog v-model="confirmDialog.show" max-width="360">
      <v-card rounded="lg">
        <v-card-title>模拟支付</v-card-title>
        <v-card-text>确认模拟支付？此为演示功能，不会产生实际扣费。</v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="confirmDialog.show = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="handlePay">确认支付</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="3000" location="top">
      {{ snackbar.message }}
    </v-snackbar>
  </div>
</template>
