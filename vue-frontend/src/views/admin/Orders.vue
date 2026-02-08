<script setup>
import { ref, onMounted } from 'vue'
import Sidebar from '@/components/admin/Sidebar.vue'
import { adminApi } from '@/api'

const orders = ref([])
const loading = ref(true)
const statusFilter = ref('')

onMounted(async () => {
  await loadOrders()
})

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

const filteredOrders = () => {
  if (!statusFilter.value) return orders.value
  return orders.value.filter(o => o.status === parseInt(statusFilter.value))
}

function getStatusInfo(status) {
  const map = {
    0: { text: '已取消', class: 'badge-danger' },
    1: { text: '待支付', class: 'badge-warning' },
    2: { text: '已支付', class: 'badge-success' },
    3: { text: '已完成', class: 'badge-success' }
  }
  return map[status] || { text: '未知', class: '' }
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
        <h1>订单管理</h1>
        <select v-model="statusFilter" class="form-control filter-select">
          <option value="">全部状态</option>
          <option value="1">待支付</option>
          <option value="2">已支付</option>
          <option value="3">已完成</option>
          <option value="0">已取消</option>
        </select>
      </div>

      <div v-if="loading" class="text-center mt-2">
        <div class="spinner"></div>
      </div>

      <table v-else class="data-table">
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
          <tr v-if="filteredOrders().length === 0">
            <td colspan="8" class="text-center">暂无订单</td>
          </tr>
          <tr v-for="o in filteredOrders()" :key="o.id">
            <td>{{ o.orderNo }}</td>
            <td>{{ o.username || '-' }}</td>
            <td>¥{{ o.parkingFee || 0 }}</td>
            <td>¥{{ o.serviceFee || 0 }}</td>
            <td>¥{{ o.chargingFee || 0 }}</td>
            <td class="total-amount">¥{{ o.totalAmount || 0 }}</td>
            <td>
              <span class="badge" :class="getStatusInfo(o.status).class">
                {{ getStatusInfo(o.status).text }}
              </span>
            </td>
            <td>{{ formatDate(o.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
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
  margin-bottom: 25px;
}

.top-bar h1 {
  font-size: 1.8rem;
}

.filter-select {
  width: 150px;
}

.total-amount {
  font-weight: 600;
  color: var(--primary);
}
</style>
