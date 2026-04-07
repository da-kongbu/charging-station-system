<script setup>
import { ref, onMounted, computed } from 'vue'
import Sidebar from '@/components/admin/Sidebar.vue'
import { adminApi } from '@/api'

const orders = ref([])
const loading = ref(true)
const statusFilter = ref('')

onMounted(async () => { await loadOrders() })

async function loadOrders() {
  loading.value = true
  try {
    const response = await adminApi.getOrders()
    orders.value = response.data.data || []
  } catch (error) {
    console.error('Failed to load orders:', error)
  } finally {
    loading.value = false
  }
}

const filteredOrders = computed(() => {
  if (!statusFilter.value) return orders.value
  return orders.value.filter(o => o.status === parseInt(statusFilter.value))
})

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

const statusOptions = [
  { title: '全部状态', value: '' },
  { title: '待支付', value: '1' },
  { title: '已支付', value: '2' },
  { title: '已完成', value: '3' },
  { title: '已取消', value: '0' },
]
</script>

<template>
  <div class="d-flex" style="min-height: 100vh;">
    <Sidebar />

    <v-main>
      <v-container class="pa-6">
        <div class="d-flex align-center justify-space-between mb-6">
          <h1 class="text-h5 font-weight-bold">订单管理</h1>
          <v-select
            v-model="statusFilter"
            :items="statusOptions"
            item-title="title"
            item-value="value"
            variant="outlined"
            density="compact"
            hide-details
            style="max-width: 180px;"
            prepend-inner-icon="mdi-filter-variant"
          />
        </div>

        <div v-if="loading" class="text-center py-8">
          <v-progress-circular indeterminate color="primary" size="48" />
        </div>

        <v-card v-else rounded="lg">
          <v-table hover>
            <thead>
              <tr>
                <th>订单号</th>
                <th>用户</th>
                <th>停车费</th>
                <th>服务费</th>
                <th>充电费</th>
                <th>总额</th>
                <th>状态</th>
                <th>创建时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="filteredOrders.length === 0">
                <td colspan="8" class="text-center text-grey py-6">暂无订单</td>
              </tr>
              <tr v-for="o in filteredOrders" :key="o.id">
                <td class="text-body-2 font-weight-medium">{{ o.orderNo }}</td>
                <td>{{ o.username || '-' }}</td>
                <td>¥{{ o.parkingFee || 0 }}</td>
                <td>¥{{ o.serviceFee || 0 }}</td>
                <td>¥{{ o.chargingFee || 0 }}</td>
                <td class="font-weight-bold text-primary">¥{{ o.totalAmount || 0 }}</td>
                <td>
                  <v-chip :color="getStatusColor(o.status)" size="small" variant="tonal">
                    {{ getStatusText(o.status) }}
                  </v-chip>
                </td>
                <td class="text-body-2 text-grey">{{ formatDate(o.createdAt) }}</td>
              </tr>
            </tbody>
          </v-table>
        </v-card>
      </v-container>
    </v-main>
  </div>
</template>
