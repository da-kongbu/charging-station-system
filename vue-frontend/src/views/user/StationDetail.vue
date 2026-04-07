<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
const snackbar = ref({ show: false, message: '', color: 'success' })

const reservationForm = ref({ startTime: '', endTime: '' })

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
  if (start >= end) { estimatedPrice.value = 0; return }

  const pricePerHour = selectedSpot.value.pricePerHour
  const pricePerMinute = pricePerHour / 60
  let total = 0
  let current = new Date(start)

  while (current < end) {
    const hour = current.getHours()
    let multiplier = 1.0
    if ((hour >= 8 && hour < 10) || (hour >= 17 && hour < 21)) multiplier = 1.5
    else if (hour >= 23 || hour < 7) multiplier = 0.5
    total += pricePerMinute * multiplier
    current.setMinutes(current.getMinutes() + 1)
  }

  if (total < pricePerHour) total = pricePerHour
  estimatedPrice.value = total.toFixed(2)
}

const selectedDate = ref(new Date().toISOString().slice(0, 10))
const occupiedSlots = ref([])

onMounted(async () => { await loadStation() })

watch(selectedDate, async (newDate) => {
  if (selectedSpot.value) await loadOccupiedSlots(selectedSpot.value.id, newDate)
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
  if (!authStore.isAuthenticated) { router.push('/login'); return }
  selectedSpot.value = spot
  showModal.value = true

  const now = new Date()
  now.setMinutes(Math.ceil(now.getMinutes() / 5) * 5, 0, 0)
  const end = new Date(now.getTime() + 2 * 60 * 60000)

  reservationForm.value.startTime = formatDateTimeLocal(now)
  reservationForm.value.endTime = formatDateTimeLocal(end)
  selectedDate.value = formatDateTimeLocal(now).slice(0, 10)

  loadOccupiedSlots(spot.id, selectedDate.value)
}

function formatDateTimeLocal(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d}T${h}:${min}`
}

async function submitReservation() {
  const start = new Date(reservationForm.value.startTime)
  const end = new Date(reservationForm.value.endTime)
  if ((end - start) / (1000 * 60 * 60) > 12) {
    snackbar.value = { show: true, message: '预约时长不能超过12小时', color: 'error' }
    return
  }

  bookingLoading.value = true
  try {
    await reservationApi.create({
      spotId: selectedSpot.value.id,
      startTime: reservationForm.value.startTime,
      endTime: reservationForm.value.endTime
    })
    snackbar.value = { show: true, message: '预约成功！', color: 'success' }
    showModal.value = false
    router.push('/reservations')
  } catch (error) {
    snackbar.value = { show: true, message: error.response?.data?.message || '预约失败', color: 'error' }
  } finally {
    bookingLoading.value = false
  }
}

function getSpotStatusColor(status) {
  const map = { 0: 'grey', 1: 'success', 2: 'warning', 3: 'error' }
  return map[status] || 'grey'
}

function getSpotStatusText(status) {
  const map = { 0: '离线', 1: '空闲', 2: '预约中', 3: '使用中' }
  return map[status] || '未知'
}

function getPileStatusColor(status) {
  const map = { 0: 'grey', 1: 'success', 2: 'warning', 3: 'error' }
  return map[status] || 'grey'
}

function getPileStatusText(status) {
  const map = { 0: '离线', 1: '正常', 2: '充电中', 3: '故障' }
  return map[status] || '未知'
}

function getSegmentStyle(slot) {
  const viewDate = new Date(selectedDate.value)
  const dayStart = new Date(viewDate).setHours(0, 0, 0, 0)
  const dayEnd = new Date(viewDate).setHours(24, 0, 0, 0)
  const resStart = new Date(slot.startTime).getTime()
  const resEnd = new Date(slot.endTime).getTime()

  if (resEnd <= dayStart || resStart >= dayEnd) return { display: 'none' }

  const effectiveStart = Math.max(resStart, dayStart)
  const effectiveEnd = Math.min(resEnd, dayEnd)
  const totalDayMillis = 24 * 60 * 60 * 1000

  return {
    left: `${((effectiveStart - dayStart) / totalDayMillis) * 100}%`,
    width: `${((effectiveEnd - effectiveStart) / totalDayMillis) * 100}%`
  }
}
</script>

<template>
  <div>
    <v-container class="py-6">
      <div v-if="loading" class="text-center py-8">
        <v-progress-circular indeterminate color="primary" size="48" />
      </div>

      <template v-else-if="station">
        <!-- Breadcrumbs -->
        <v-breadcrumbs :items="[{ title: '首页', to: '/' }, { title: station.name }]" class="pa-0 mb-4" />

        <!-- Station Info Card -->
        <v-card class="mb-6" rounded="lg">
          <v-card-text class="pa-6">
            <div class="d-flex align-start justify-space-between flex-wrap ga-4">
              <div>
                <h1 class="text-h5 font-weight-bold mb-2">{{ station.name }}</h1>
                <div class="text-body-2 text-grey-darken-1 mb-2">
                  <v-icon size="18" class="mr-1">mdi-map-marker</v-icon>
                  {{ station.city }} {{ station.district }} {{ station.address }}
                </div>
                <div class="d-flex ga-4 text-body-2 text-grey">
                  <span><v-icon size="16" class="mr-1">mdi-clock-outline</v-icon>{{ station.businessHours || '24小时营业' }}</span>
                  <span><v-icon size="16" class="mr-1">mdi-phone-outline</v-icon>{{ station.contact || '暂无' }}</span>
                </div>
              </div>
              <v-chip :color="station.status === 1 ? 'success' : 'grey'" variant="tonal">
                {{ station.status === 1 ? '营业中' : '未营业' }}
              </v-chip>
            </div>
          </v-card-text>
        </v-card>

        <!-- Piles -->
        <h2 class="text-h6 font-weight-bold mb-4">
          <v-icon class="mr-1">mdi-ev-plug-type2</v-icon>充电桩列表
        </h2>

        <v-card v-for="pile in station.piles" :key="pile.id" class="mb-4" rounded="lg">
          <v-card-title class="d-flex align-center justify-space-between">
            <div class="d-flex align-center ga-2">
              <v-icon :color="pile.pileType === 'DC' ? 'warning' : 'info'">
                {{ pile.pileType === 'DC' ? 'mdi-flash' : 'mdi-current-ac' }}
              </v-icon>
              <span>{{ pile.pileCode }} 充电桩</span>
            </div>
            <v-chip :color="getPileStatusColor(pile.status)" size="small" variant="tonal">
              {{ getPileStatusText(pile.status) }}
            </v-chip>
          </v-card-title>

          <v-card-subtitle class="pb-0">
            类型：{{ pile.pileType === 'DC' ? '快充' : '慢充' }} | 功率：{{ pile.power }}kW
          </v-card-subtitle>

          <v-card-text class="pt-4">
            <v-row>
              <v-col v-for="spot in pile.parkingSpots" :key="spot.id" cols="6" sm="4" md="3">
                <v-sheet
                  rounded="lg"
                  class="pa-4 text-center cursor-pointer spot-sheet"
                  :color="spot.status === 0 ? 'grey-lighten-2' : spot.status === 1 ? 'green-lighten-5' : 'amber-lighten-5'"
                  @click="spot.status !== 0 && openBookingModal(spot)"
                >
                  <div class="text-subtitle-2 font-weight-bold mb-1">{{ spot.spotCode }}</div>
                  <v-chip :color="getSpotStatusColor(spot.status)" size="x-small" variant="tonal">
                    {{ getSpotStatusText(spot.status) }}
                  </v-chip>
                  <div class="text-caption text-grey mt-2">¥{{ spot.pricePerHour }}/时</div>
                </v-sheet>
              </v-col>
            </v-row>
          </v-card-text>
        </v-card>
      </template>
    </v-container>

    <!-- Booking Dialog -->
    <v-dialog v-model="showModal" max-width="480">
      <v-card rounded="lg">
        <v-card-title class="d-flex align-center justify-space-between">
          <span>预约车位 {{ selectedSpot?.spotCode }}</span>
          <v-btn icon="mdi-close" variant="text" size="small" @click="showModal = false" />
        </v-card-title>

        <v-card-text>
          <!-- Timeline -->
          <div class="mb-4">
            <div class="text-body-2 text-grey mb-2">日期查看</div>
            <v-text-field v-model="selectedDate" type="date" variant="outlined" density="compact" hide-details class="mb-3" />

            <div class="timeline-visual">
              <div class="timeline-bar">
                <div
                  v-for="slot in occupiedSlots"
                  :key="slot.id"
                  class="occupied-segment"
                  :class="'segment-status-' + slot.status"
                  :style="getSegmentStyle(slot)"
                  :title="`${slot.startTime?.slice(11,16)} - ${slot.endTime?.slice(11,16)}`"
                />
              </div>
              <div class="timeline-labels">
                <span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>23:59</span>
              </div>
            </div>
            <div class="timeline-legend">
              <span class="legend-item"><i class="legend-dot" style="background:#f44336"></i>使用中</span>
              <span class="legend-item"><i class="legend-dot" style="background:#ff9800"></i>已预约</span>
              <span class="legend-item"><i class="legend-dot" style="background:#e0e0e0"></i>空闲可约</span>
            </div>
          </div>

          <v-text-field
            v-model="reservationForm.startTime"
            type="datetime-local"
            label="开始时间"
            variant="outlined"
            class="mb-2"
          />
          <v-text-field
            v-model="reservationForm.endTime"
            type="datetime-local"
            label="结束时间"
            variant="outlined"
            hint="单次预约最长不超过12小时"
            persistent-hint
            class="mb-3"
          />

          <!-- Pricing -->
          <v-sheet color="grey-lighten-4" rounded="lg" class="pa-3">
            <div class="d-flex justify-space-between align-center mb-2">
              <span class="font-weight-medium">预估停车费</span>
              <span class="text-h6 font-weight-bold text-primary">¥{{ estimatedPrice }}</span>
            </div>
            <div class="text-caption text-grey">
              基础价 ¥{{ selectedSpot?.pricePerHour }}/小时
            </div>
          </v-sheet>
        </v-card-text>

        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="text" @click="showModal = false">取消</v-btn>
          <v-btn color="primary" variant="flat" :loading="bookingLoading" @click="submitReservation">确认预约</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Snackbar -->
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="3000" location="top">
      {{ snackbar.message }}
    </v-snackbar>
  </div>
</template>

<style scoped>
.spot-sheet {
  transition: all 0.2s ease;
}
.spot-sheet:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

/* Timeline visualizer - custom UI */
.timeline-visual {
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
.segment-status-1 { background-color: #ff9800; }
.segment-status-2 { background-color: #f44336; }
.segment-status-3 { background-color: #bdbdbd; }
.segment-status-0 { background-color: #9e9e9e; }
.timeline-labels {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #666;
}
.timeline-legend {
  display: flex;
  gap: 12px;
  margin-top: 8px;
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
</style>
