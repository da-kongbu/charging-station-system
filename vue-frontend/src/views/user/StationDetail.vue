<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Header from '@/components/common/Header.vue'
import { stationApi, reservationApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const station = ref(null)
const loading = ref(true)
const selectedSpot = ref(null)
const showModal = ref(false)
const bookingLoading = ref(false)

const reservationForm = ref({
  startTime: '',
  endTime: ''
})

const estimatedPrice = ref(0)
const pricingRules = [
  { time: '08:00-10:00', rate: '1.5倍', label: '早高峰' },
  { time: '17:00-21:00', rate: '1.5倍', label: '晚高峰' },
  { time: '23:00-07:00', rate: '0.5倍', label: '谷电优惠' },
  { time: '其他时段', rate: '1.0倍', label: '平峰' }
]

watch(() => reservationForm.value, calculateEstimate, { deep: true })

function calculateEstimate() {
  if (!reservationForm.value.startTime || !reservationForm.value.endTime || !selectedSpot.value) {
    estimatedPrice.value = 0
    return
  }

  const start = new Date(reservationForm.value.startTime)
  const end = new Date(reservationForm.value.endTime)
  if (start >= end) {
    estimatedPrice.value = 0
    return
  }

  const pricePerHour = selectedSpot.value.pricePerHour
  const pricePerMinute = pricePerHour / 60
  let total = 0
  let current = new Date(start)

  while (current < end) {
    const hour = current.getHours()
    let multiplier = 1.0

    if ((hour >= 8 && hour < 10) || (hour >= 17 && hour < 21)) {
      multiplier = 1.5
    } else if (hour >= 23 || hour < 7) {
      multiplier = 0.5
    }

    total += pricePerMinute * multiplier
    current.setMinutes(current.getMinutes() + 1)
  }
  
  // Minimum 1 hour base price
  if (total < pricePerHour) total = pricePerHour
  
  estimatedPrice.value = total.toFixed(2)
}

const selectedDate = ref(new Date().toISOString().slice(0, 10))
const occupiedSlots = ref([])

onMounted(async () => {
  await loadStation()
})

watch(selectedDate, async (newDate) => {
  if (selectedSpot.value) {
    await loadOccupiedSlots(selectedSpot.value.id, newDate)
  }
})

async function loadOccupiedSlots(spotId, date) {
  try {
    const response = await reservationApi.getBySpotAndDate(spotId, date)
    occupiedSlots.value = response.data.data
  } catch (error) {
    console.error('Failed to load occupied slots:', error)
  }
}

async function loadStation() {
  loading.value = true
  try {
    const response = await stationApi.getById(route.params.id)
    station.value = response.data.data
  } catch (error) {
    console.error('Failed to load station:', error)
  } finally {
    loading.value = false
  }
}

function openBookingModal(spot) {
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return
  }
  selectedSpot.value = spot
  showModal.value = true
  
  // Set default times - 开始时间为当前时间，结束时间为2小时后
  const now = new Date()
  // 取整到5分钟
  now.setMinutes(Math.ceil(now.getMinutes() / 5) * 5, 0, 0)
  const end = new Date(now.getTime() + 2 * 60 * 60000) // 2小时后
  
  reservationForm.value.startTime = formatDateTimeLocal(now)
  reservationForm.value.endTime = formatDateTimeLocal(end)
  selectedDate.value = formatDateTimeLocal(now).slice(0, 10)
  
  loadOccupiedSlots(spot.id, selectedDate.value)
}

function formatDateTimeLocal(date) {
  // 使用本地时间格式，而不是UTC
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

async function submitReservation() {
  const start = new Date(reservationForm.value.startTime)
  const end = new Date(reservationForm.value.endTime)
  
  const hours = (end - start) / (1000 * 60 * 60)
  if (hours > 12) {
    alert('预约时长不能超过12小时')
    return
  }

  bookingLoading.value = true
  try {
    await reservationApi.create({
      spotId: selectedSpot.value.id,
      startTime: reservationForm.value.startTime,
      endTime: reservationForm.value.endTime
    })
    alert('预约成功！')
    showModal.value = false
    router.push('/reservations')
  } catch (error) {
    alert(error.response?.data?.message || '预约失败')
  } finally {
    bookingLoading.value = false
  }
}

function getSpotStatus(status) {
  const statusMap = {
    0: { text: '离线', class: 'badge-secondary' },
    1: { text: '空闲', class: 'badge-success' },
    2: { text: '预约中', class: 'badge-warning' },
    3: { text: '使用中', class: 'badge-danger' }
  }
  return statusMap[status] || { text: '未知', class: '' }
}

// 预约状态（用于时间轴 tooltip，与车位状态码不同）
function getReservationStatusText(status) {
  const map = { 0: '已取消', 1: '已预约', 2: '使用中', 3: '已完成' }
  return map[status] || '未知'
}

function getSegmentStyle(slot) {
  // 1. 解析当前查看日期的 0点 和 24点
  // 假设 selectedDate 是 "2026-02-09"
  const viewDate = new Date(selectedDate.value)
  const dayStart = new Date(viewDate).setHours(0, 0, 0, 0)
  const dayEnd = new Date(viewDate).setHours(24, 0, 0, 0)

  // 2. 解析预约的开始和结束时间
  const resStart = new Date(slot.startTime).getTime()
  const resEnd = new Date(slot.endTime).getTime()

  // 3. 【核心修复逻辑】：计算“有效”的显示区间
  // 如果预约在当天之前就结束了，或者在当天之后才开始，直接隐藏
  if (resEnd <= dayStart || resStart >= dayEnd) {
    return { display: 'none' }
  }

  // 裁剪时间：开始时间不能早于0点，结束时间不能晚于24点
  const effectiveStart = Math.max(resStart, dayStart)
  const effectiveEnd = Math.min(resEnd, dayEnd)

  // 4. 计算百分比
  const totalDayMillis = 24 * 60 * 60 * 1000 // 一天的毫秒数

  // Left = (有效开始时间 - 0点) / 一天总时间
  const leftPercent = ((effectiveStart - dayStart) / totalDayMillis) * 100

  // Width = (有效结束时间 - 有效开始时间) / 一天总时间
  const widthPercent = ((effectiveEnd - effectiveStart) / totalDayMillis) * 100
  
  return {
    left: `${leftPercent}%`,
    width: `${widthPercent}%`
  }
}
</script>

<template>
  <div class="page">
    <Header />
    
    <main class="main container">
      <div v-if="loading" class="text-center mt-2">
        <div class="spinner"></div>
      </div>

      <template v-else-if="station">
        <div class="station-header">
          <RouterLink to="/" class="back-btn">← 返回</RouterLink>
          <h1>{{ station.name }}</h1>
          <p class="station-address">📍 {{ station.city }} {{ station.district }} {{ station.address }}</p>
          <div class="station-info">
            <span>🕐 {{ station.businessHours || '24小时营业' }}</span>
            <span>📞 {{ station.contact || '暂无' }}</span>
          </div>
        </div>

        <h2 class="section-title">充电桩列表</h2>

        <div v-for="pile in station.piles" :key="pile.id" class="pile-card card mb-2">
        <div class="pile-header">
            <h3>{{ pile.pileCode }} 充电桩</h3>
            <span class="badge" :class="{
              'badge-secondary': pile.status === 0,
              'badge-success': pile.status === 1,
              'badge-warning': pile.status === 2,
              'badge-danger': pile.status === 3
            }">
              {{ pile.status === 0 ? '离线' : pile.status === 1 ? '正常' : pile.status === 2 ? '充电中' : '故障' }}
            </span>
          </div>
          <p class="pile-type">类型：{{ pile.pileType === 'DC' ? '快充' : '慢充' }} | 功率：{{ pile.power }}kW</p>

          <div class="spots-grid">
            <div 
              v-for="spot in pile.parkingSpots" 
              :key="spot.id" 
              class="spot-item"
              :class="{ 
                'available': spot.status === 1,
                'occupied': spot.status === 2 || spot.status === 3,
                'offline': spot.status === 0
              }"
              @click="spot.status !== 0 && openBookingModal(spot)"
            >
              <span class="spot-no">{{ spot.spotCode }}</span>
              <span class="badge" :class="getSpotStatus(spot.status).class">
                {{ getSpotStatus(spot.status).text }}
              </span>
              <p class="spot-price">¥{{ spot.pricePerHour }}/小时</p>
            </div>
          </div>
        </div>
      </template>
    </main>

    <!-- Booking Modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal-content card">
        <h2>预约车位 {{ selectedSpot?.spotCode }}</h2>
        <form @submit.prevent="submitReservation">
          <div class="form-group">
            <label>日期查看</label>
            <input type="date" v-model="selectedDate" class="form-control mb-2">
            
            <div class="timeline-visual">
              <div class="timeline-bar">
                <div 
                  v-for="slot in occupiedSlots" 
                  :key="slot.id"
                  class="occupied-segment"
                  :class="'segment-status-' + slot.status"
                  :style="getSegmentStyle(slot)"
                  :title="`${slot.startTime.slice(11,16)} - ${slot.endTime.slice(11,16)} [${getReservationStatusText(slot.status)}]`"
                ></div>
              </div>
              <div class="timeline-labels">
                <span>00:00</span>
                <span>06:00</span>
                <span>12:00</span>
                <span>18:00</span>
                <span>23:59</span>
              </div>
            </div>
            <div class="timeline-legend">
              <span class="legend-item"><i class="legend-dot" style="background:#ff4d4f"></i>使用中</span>
              <span class="legend-item"><i class="legend-dot" style="background:#faad14"></i>已预约</span>
              <span class="legend-item"><i class="legend-dot" style="background:#e8e8e8"></i>空闲可约</span>
            </div>
          </div>

          <div class="form-group">
            <label>开始时间</label>
            <input v-model="reservationForm.startTime" type="datetime-local" class="form-control" required />
          </div>
          <div class="form-group">
            <label>结束时间</label>
            <input v-model="reservationForm.endTime" type="datetime-local" class="form-control" required />
            <small class="text-hint">注意：单次预约最长不超过12小时</small>
          </div>

          <div class="pricing-info">
            <div class="price-estimate">
              <span>预估停车费：</span>
              <span class="price-value">¥{{ estimatedPrice }}</span>
            </div>
            <div class="pricing-rules">
              <p>计费规则：基础价 ¥{{ selectedSpot?.pricePerHour }}/小时</p>
              <ul>
                <li v-for="(rule, index) in pricingRules" :key="index">
                   {{ rule.label }} ({{ rule.time }}): {{ rule.rate }}
                </li>
              </ul>
            </div>
          </div>
          <div class="modal-actions">
            <button type="button" class="btn btn-outline" @click="showModal = false">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="bookingLoading">
              {{ bookingLoading ? '提交中...' : '确认预约' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.main {
  padding: 30px 20px;
}

.station-header {
  margin-bottom: 30px;
}

.back-btn {
  color: var(--primary);
  font-weight: 500;
  margin-bottom: 15px;
  display: inline-block;
}

.station-header h1 {
  font-size: 2rem;
  margin-bottom: 10px;
}

.station-address {
  color: var(--text-light);
  margin-bottom: 10px;
}

.station-info {
  display: flex;
  gap: 20px;
  color: var(--text-light);
}

.section-title {
  font-size: 1.4rem;
  margin-bottom: 20px;
}

.pile-card {
  margin-bottom: 20px;
}

.pile-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.pile-header h3 {
  font-size: 1.1rem;
}

.pile-type {
  color: var(--text-light);
  font-size: 0.9rem;
  margin-bottom: 15px;
}

.spots-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 15px;
}

.spot-item {
  background: #f8f9fa;
  padding: 15px;
  border-radius: 10px;
  text-align: center;
  cursor: default;
}

.spot-item.available {
  cursor: pointer;
  transition: all 0.3s;
}

.spot-item.available:hover {
  background: var(--primary);
  color: white;
}

.spot-item.available:hover .badge {
  background: rgba(255,255,255,0.3);
  color: white;
}

.spot-item.available:hover .spot-price {
  color: white;
}

.spot-item.occupied {
  cursor: pointer;
  border: 1px solid #ffc107;
  background: #fffdf5;
}

.spot-item.occupied:hover {
  background: #ffe58f;
}

.spot-item.offline {
  opacity: 0.6;
  cursor: not-allowed;
  background: #e9ecef;
}

.spot-no {
  font-weight: 600;
  font-size: 1.1rem;
  display: block;
  margin-bottom: 8px;
}

.spot-price {
  font-size: 0.85rem;
  color: var(--text-light);
  margin-top: 8px;
}

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: 90%;
  max-width: 400px;
  animation: slideUp 0.3s ease;
}

.modal-content h2 {
  margin-bottom: 20px;
}

.modal-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.modal-actions button {
  flex: 1;
}

@keyframes slideUp {
  from { transform: translateY(30px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

/* 可视化样式 */
.timeline-visual {
  margin: 15px 0;
  padding: 10px;
  background: #f5f5f5;
  border-radius: 8px;
}

.timeline-bar {
  height: 20px;
  background: #e0e0e0;
  border-radius: 10px;
  position: relative;
  margin-bottom: 5px;
  overflow: hidden;
}

.occupied-segment {
  position: absolute;
  top: 0;
  bottom: 0;
  opacity: 0.8;
  border-right: 1px solid rgba(255,255,255,0.3);
}

.segment-status-1 { /* 待使用/预约中 */
  background-color: #faad14; 
}
.segment-status-2 { /* 占用/进行中 */
  background-color: #ff4d4f;
}
.segment-status-3 { /* 已完成 */
  background-color: #d9d9d9;
}
.segment-status-0 { /* 已取消/离线 */
  background-color: #8c8c8c;
}

.timeline-legend {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #666;
}

.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

.timeline-labels {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #666;
}

.text-hint {
  color: #f59e0b;
  font-size: 0.85em;
  margin-top: 4px;
  display: block;
}

.pricing-info {
  margin-top: 15px;
  background: #f8f9fa;
  padding: 10px;
  border-radius: 6px;
}

.price-estimate {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: bold;
}

.price-value {
  color: var(--primary);
  font-size: 1.2rem;
}

.pricing-rules {
  font-size: 0.85rem;
  color: #666;
}

.pricing-rules ul {
  padding-left: 20px;
  margin: 5px 0 0;
}
</style>
