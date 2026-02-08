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
  
  // Set default times
  const now = new Date()
  const start = new Date(now.getTime() + 30 * 60000)
  const end = new Date(start.getTime() + 2 * 60 * 60000)
  
  reservationForm.value.startTime = formatDateTimeLocal(start)
  reservationForm.value.endTime = formatDateTimeLocal(end)
  selectedDate.value = formatDateTimeLocal(start).slice(0, 10)
  
  loadOccupiedSlots(spot.id, selectedDate.value)
}

function formatDateTimeLocal(date) {
  return date.toISOString().slice(0, 16)
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
    0: { text: '空闲', class: 'badge-success' },
    1: { text: '占用', class: 'badge-danger' },
    2: { text: '预约中', class: 'badge-warning' },
    3: { text: '使用中', class: 'badge-info' }
  }
  return statusMap[status] || { text: '未知', class: '' }
}

function getSegmentStyle(slot) {
  const start = new Date(slot.startTime)
  const end = new Date(slot.endTime)
  const dayStart = new Date(start)
  dayStart.setHours(0, 0, 0, 0)
  
  const totalMinutes = 24 * 60
  const startMinutes = (start - dayStart) / 60000
  const durationMinutes = (end - start) / 60000
  
  const left = (startMinutes / totalMinutes) * 100
  const width = (durationMinutes / totalMinutes) * 100
  
  return {
    left: `${left}%`,
    width: `${width}%`
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
            <span class="badge" :class="pile.status === 1 ? 'badge-success' : 'badge-danger'">
              {{ pile.status === 1 ? '正常' : '故障' }}
            </span>
          </div>
          <p class="pile-type">类型：{{ pile.pileType === 'DC' ? '快充' : '慢充' }} | 功率：{{ pile.power }}kW</p>

          <div class="spots-grid">
            <div 
              v-for="spot in pile.parkingSpots" 
              :key="spot.id" 
              class="spot-item"
              :class="{ available: spot.status === 0 }"
              @click="spot.status === 0 && openBookingModal(spot)"
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
                  :style="getSegmentStyle(slot)"
                  :title="`${slot.startTime.slice(11,16)} - ${slot.endTime.slice(11,16)} 已占用`"
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
            <small class="text-hint">红色区域表示已被预约的时间段</small>
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
  background-color: #ff4d4f;
  opacity: 0.8;
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
</style>
