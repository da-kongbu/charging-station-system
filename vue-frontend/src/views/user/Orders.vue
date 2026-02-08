<script setup>
import { ref, onMounted } from 'vue'
import Header from '@/components/common/Header.vue'
import { orderApi } from '@/api'

const orders = ref([])
const loading = ref(true)

onMounted(async () => {
  await loadOrders()
})

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

async function handlePay(id) {
  if (!confirm('确认支付？')) return
  try {
    await orderApi.pay(id, 'WECHAT')
    alert('支付成功！')
    await loadOrders()
  } catch (error) {
    alert(error.response?.data?.message || '支付失败')
  }
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
  <div class="page">
    <Header />
    
    <main class="main container">
      <h1 class="page-title">我的订单</h1>

      <div v-if="loading" class="text-center mt-2">
        <div class="spinner"></div>
      </div>

      <div v-else-if="orders.length === 0" class="empty-state">
        <p>暂无订单记录</p>
        <RouterLink to="/" class="btn btn-primary">去预约充电</RouterLink>
      </div>

      <div v-else class="orders-list">
        <div v-for="o in orders" :key="o.id" class="order-card card">
          <div class="order-header">
            <span class="order-no">{{ o.orderNo }}</span>
            <span class="badge" :class="getStatusInfo(o.status).class">
              {{ getStatusInfo(o.status).text }}
            </span>
          </div>

          <div class="order-body">
            <div class="fee-row">
              <span>停车费</span>
              <span>¥{{ o.parkingFee }}</span>
            </div>
            <div class="fee-row">
              <span>服务费</span>
              <span>¥{{ o.serviceFee }}</span>
            </div>
            <div class="fee-row">
              <span>充电费</span>
              <span>¥{{ o.chargingFee }}</span>
            </div>
            <div class="fee-row total">
              <span>总计</span>
              <span>¥{{ o.totalAmount }}</span>
            </div>
            <p class="order-time">创建时间：{{ formatDate(o.createdAt) }}</p>
          </div>

          <div class="order-actions" v-if="o.status === 1">
            <button class="btn btn-primary" @click="handlePay(o.id)">立即支付</button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.main {
  padding: 30px 20px;
}

.page-title {
  font-size: 1.8rem;
  margin-bottom: 25px;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
  color: var(--text-light);
}

.empty-state p {
  margin-bottom: 20px;
  font-size: 1.1rem;
}

.order-card {
  margin-bottom: 15px;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding-bottom: 15px;
  border-bottom: 1px solid #eee;
}

.order-no {
  font-weight: 600;
  color: var(--text);
}

.fee-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  color: var(--text-light);
}

.fee-row.total {
  font-weight: 600;
  font-size: 1.1rem;
  color: var(--primary);
  border-top: 1px dashed #eee;
  margin-top: 10px;
  padding-top: 15px;
}

.order-time {
  font-size: 0.85rem;
  color: var(--text-light);
  margin-top: 15px;
}

.order-actions {
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #eee;
}

.order-actions .btn {
  width: 100%;
}
</style>
