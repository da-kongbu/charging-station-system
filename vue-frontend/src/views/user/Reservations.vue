<script setup>
import { ref, onMounted } from 'vue'
import Header from '@/components/common/Header.vue'
import ChargingMonitor from '@/components/ChargingMonitor.vue'
import { reservationApi } from '@/api'

const reservations = ref([])
const loading = ref(true)
const showMonitor = ref(false)
const monitorReservationId = ref(0)
const monitorPilePower = ref(7)

// Toast通知
const toast = ref({ show: false, message: '', type: 'success' })
let toastTimer = null

function showToast(message, type = 'success') {
  if (toastTimer) clearTimeout(toastTimer)
  toast.value = { show: true, message, type }
  toastTimer = setTimeout(() => {
    toast.value.show = false
  }, 3000)
}

onMounted(async () => {
  await loadReservations()
})

async function loadReservations() {
  loading.value = true
  try {
    const response = await reservationApi.getMyReservations()
    reservations.value = response.data.data || []
  } catch (error) {
    console.error('Failed to load reservations:', error)
  } finally {
    loading.value = false
  }
}

async function handleCheckIn(id) {
  if (!confirm('确认签到？')) return
  try {
    await reservationApi.checkIn(id)
    await loadReservations()
    showToast('✅ 签到成功！开始充电吧', 'success')
  } catch (error) {
    showToast(error.response?.data?.message || '签到失败', 'error')
  }
}

async function handleCheckOut(id) {
  if (!confirm('确认结束充电？将生成订单。')) return
  try {
    await reservationApi.checkOut(id)
    await loadReservations()
    showToast('✅ 充电结束！请前往订单页面支付', 'success')
  } catch (error) {
    showToast(error.response?.data?.message || '操作失败', 'error')
  }
}

async function handleCancel(id) {
  if (!confirm('确认取消预约？')) return
  try {
    await reservationApi.cancel(id)
    await loadReservations()
    showToast('预约已取消', 'success')
  } catch (error) {
    showToast(error.response?.data?.message || '取消失败', 'error')
  }
}

function getStatusInfo(status) {
  const map = {
    0: { text: '已取消', class: 'badge-danger' },
    1: { text: '待签到', class: 'badge-warning' },
    2: { text: '充电中', class: 'badge-success' },
    3: { text: '已完成', class: 'badge-success' }
  }
  return map[status] || { text: '未知', class: '' }
}

function formatDate(str) {
  if (!str) return '-'
  return new Date(str).toLocaleString('zh-CN')
}

function openMonitor(id, power) {
  monitorReservationId.value = id
  monitorPilePower.value = power || 7
  showMonitor.value = true
}
</script>

<template>
  <div class="page">
    <Header />
    
    <main class="main container">
      <h1 class="page-title">我的预约</h1>

      <div v-if="loading" class="text-center mt-2">
        <div class="spinner"></div>
      </div>

      <div v-else-if="reservations.length === 0" class="empty-state">
        <p>暂无预约记录</p>
        <RouterLink to="/" class="btn btn-primary">去预约</RouterLink>
      </div>

      <div v-else class="reservations-list">
        <div v-for="r in reservations" :key="r.id" class="reservation-card card">
          <div class="reservation-header">
            <span class="badge" :class="getStatusInfo(r.status).class">
              {{ getStatusInfo(r.status).text }}
            </span>
            <span class="reservation-id">#{{ r.id }}</span>
          </div>

          <div class="reservation-body">
            <p><strong>充电站：</strong>{{ r.stationName }}</p>
            <p><strong>充电桩：</strong>{{ r.pileCode }}</p>
            <p><strong>车位：</strong>{{ r.spotCode }}</p>
            <p><strong>预约时间：</strong>{{ formatDate(r.startTime) }} - {{ formatDate(r.endTime) }}</p>
            <p><strong>预估停车费：</strong>¥{{ r.estimatedCost || 0 }}</p>
          </div>


          <div class="reservation-actions">
            <template v-if="r.status === 1">
              <button class="btn btn-primary btn-sm" @click="handleCheckIn(r.id)">签到</button>
              <small class="checkin-hint">（可提前5分钟签到，过期失效）</small>
              <button class="btn btn-danger btn-sm" @click="handleCancel(r.id)">取消</button>
            </template>
            <button v-if="r.status === 2" class="btn btn-info btn-sm" @click="openMonitor(r.id, r.pilePower)">监控</button>
            <button v-if="r.status === 2" class="btn btn-primary btn-sm" @click="handleCheckOut(r.id)">结束充电</button>
          </div>
        </div>
      </div>
    </main>

    <ChargingMonitor 
      :visible="showMonitor" 
      :reservation-id="monitorReservationId"
      :pile-power="monitorPilePower"
      @close="showMonitor = false" 
    />

    <!-- Toast通知 -->
    <Transition name="toast">
      <div v-if="toast.show" class="toast" :class="toast.type">
        {{ toast.message }}
      </div>
    </Transition>
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

.reservation-card {
  margin-bottom: 15px;
}

.reservation-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.reservation-id {
  color: var(--text-light);
  font-size: 0.9rem;
}

.reservation-body p {
  margin-bottom: 8px;
  color: var(--text);
}

.reservation-body strong {
  color: var(--text-light);
  font-weight: 500;
}

.reservation-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #eee;
}

.checkin-hint {
  color: #999;
  font-size: 12px;
}

/* Toast Notification */
.toast {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 24px;
  border-radius: 8px;
  font-weight: 500;
  z-index: 9999;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.toast.success {
  background: linear-gradient(135deg, #00b894, #00cec9);
  color: white;
}

.toast.error {
  background: linear-gradient(135deg, #d63031, #e74c3c);
  color: white;
}

.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-20px);
}
</style>
