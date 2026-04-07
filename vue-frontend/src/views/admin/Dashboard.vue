<script setup>
import { ref, onMounted } from 'vue'
import Sidebar from '@/components/admin/Sidebar.vue'
import { adminApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const stats = ref({})
const recentOrders = ref([])
const loading = ref(true)

onMounted(async () => { await loadDashboard() })

async function loadDashboard() {
  loading.value = true
  try {
    const response = await adminApi.getDashboard()
    const data = response.data.data
    stats.value = data
    recentOrders.value = data.recentOrders || []
  } catch (error) {
    console.error('Failed to load dashboard:', error)
  } finally {
    loading.value = false
  }
}

function formatDate(str) {
  if (!str) return '-'
  return new Date(str).toLocaleString('zh-CN')
}

const statCards = [
  { key: 'totalUsers', label: '注册用户数', icon: 'mdi-account-group', color: 'primary' },
  { key: 'totalStations', label: '充电站数量', icon: 'mdi-ev-station', color: 'success' },
  { key: 'todayReservations', label: '今日预约', icon: 'mdi-calendar-check', color: 'warning' },
  { key: 'todayRevenue', label: '今日收入', icon: 'mdi-currency-cny', color: 'info', prefix: '¥' },
]
</script>

<template>
  <div class="d-flex" style="min-height: 100vh;">
    <Sidebar />

    <v-main>
      <v-container class="pa-6">
        <div class="d-flex align-center justify-space-between mb-6">
          <h1 class="text-h5 font-weight-bold">仪表盘</h1>
          <span class="text-body-2 text-grey">欢迎，{{ authStore.user?.username }}</span>
        </div>

        <div v-if="loading" class="text-center py-8">
          <v-progress-circular indeterminate color="primary" size="48" />
        </div>

        <template v-else>
          <!-- Stats -->
          <v-row class="mb-6">
            <v-col v-for="card in statCards" :key="card.key" cols="12" sm="6" md="3">
              <v-card rounded="lg" class="pa-4">
                <div class="d-flex align-center justify-space-between">
                  <div>
                    <div class="text-caption text-grey mb-1">{{ card.label }}</div>
                    <div class="text-h5 font-weight-bold" :class="`text-${card.color}`">
                      {{ card.prefix || '' }}{{ stats[card.key] || 0 }}
                    </div>
                  </div>
                  <v-avatar :color="card.color" variant="tonal" size="48">
                    <v-icon :icon="card.icon" />
                  </v-avatar>
                </div>
              </v-card>
            </v-col>
          </v-row>

          <!-- Recent Orders -->
          <h2 class="text-subtitle-1 font-weight-bold mb-3">
            <v-icon class="mr-1">mdi-clipboard-text-clock</v-icon>最近订单
          </h2>

          <v-card rounded="lg">
            <v-table hover>
              <thead>
                <tr>
                  <th>订单号</th>
                  <th>用户</th>
                  <th>金额</th>
                  <th>状态</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="recentOrders.length === 0">
                  <td colspan="5" class="text-center text-grey py-6">暂无订单</td>
                </tr>
                <tr v-for="order in recentOrders" :key="order.id">
                  <td class="text-body-2">{{ order.orderNo }}</td>
                  <td>{{ order.username || '-' }}</td>
                  <td class="font-weight-bold text-primary">¥{{ order.totalAmount }}</td>
                  <td>
                    <v-chip :color="order.status === 2 ? 'success' : 'warning'" size="small" variant="tonal">
                      {{ order.status === 2 ? '已支付' : '待支付' }}
                    </v-chip>
                  </td>
                  <td class="text-body-2 text-grey">{{ formatDate(order.createdAt) }}</td>
                </tr>
              </tbody>
            </v-table>
          </v-card>
        </template>
      </v-container>
    </v-main>
  </div>
</template>
