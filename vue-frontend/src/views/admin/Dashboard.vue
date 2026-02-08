<script setup>
import { ref, onMounted } from 'vue'
import Sidebar from '@/components/admin/Sidebar.vue'
import { adminApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const stats = ref({})
const recentOrders = ref([])
const loading = ref(true)

onMounted(async () => {
  await loadDashboard()
})

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
</script>

<template>
  <div class="admin-layout">
    <Sidebar />
    
    <main class="admin-main">
      <div class="top-bar">
        <h1>仪表盘</h1>
        <span>欢迎，{{ authStore.user.username }}</span>
      </div>

      <div v-if="loading" class="text-center mt-2">
        <div class="spinner"></div>
      </div>

      <template v-else>
        <div class="stats-grid">
          <div class="stat-card card">
            <h3>{{ stats.totalUsers || 0 }}</h3>
            <p>注册用户数</p>
          </div>
          <div class="stat-card card">
            <h3>{{ stats.totalStations || 0 }}</h3>
            <p>充电站数量</p>
          </div>
          <div class="stat-card card">
            <h3>{{ stats.todayReservations || 0 }}</h3>
            <p>今日预约</p>
          </div>
          <div class="stat-card card">
            <h3>¥{{ stats.todayRevenue || 0 }}</h3>
            <p>今日收入</p>
          </div>
        </div>

        <h2 class="section-title">最近订单</h2>
        <table class="data-table">
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
              <td colspan="5" class="text-center">暂无订单</td>
            </tr>
            <tr v-for="order in recentOrders" :key="order.id">
              <td>{{ order.orderNo }}</td>
              <td>{{ order.username || '-' }}</td>
              <td>¥{{ order.totalAmount }}</td>
              <td>
                <span class="badge" :class="order.status === 2 ? 'badge-success' : 'badge-warning'">
                  {{ order.status === 2 ? '已支付' : '待支付' }}
                </span>
              </td>
              <td>{{ formatDate(order.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </template>
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
}

.admin-main {
  margin-left: 240px;
  flex: 1;
  padding: 30px;
  background: var(--bg);
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
}

.top-bar h1 {
  font-size: 1.8rem;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 30px;
}

.stat-card {
  text-align: center;
}

.stat-card h3 {
  font-size: 2rem;
  color: var(--primary);
  margin-bottom: 5px;
}

.stat-card p {
  color: var(--text-light);
}

.section-title {
  font-size: 1.3rem;
  margin-bottom: 15px;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
