<script setup>
import { ref, onMounted } from 'vue'
import ChargingMonitor from '@/components/ChargingMonitor.vue'
import { reservationApi } from '@/api'

const reservations = ref([])
const loading = ref(true)
const showMonitor = ref(false)
const monitorReservationId = ref(0)
const monitorPilePower = ref(7)
const snackbar = ref({ show: false, message: '', color: 'success' })

// Confirm dialog
const confirmDialog = ref({ show: false, title: '', message: '', action: null })

onMounted(async () => { await loadReservations() })

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

function showConfirm(title, message, action) {
  confirmDialog.value = { show: true, title, message, action }
}

async function handleConfirm() {
  confirmDialog.value.show = false
  if (confirmDialog.value.action) await confirmDialog.value.action()
}

async function handleCheckIn(id) {
  try {
    await reservationApi.checkIn(id)
    await loadReservations()
    snackbar.value = { show: true, message: '签到成功！开始充电吧', color: 'success' }
  } catch (error) {
    snackbar.value = { show: true, message: error.response?.data?.message || '签到失败', color: 'error' }
  }
}

async function handleCheckOut(id) {
  try {
    await reservationApi.checkOut(id)
    await loadReservations()
    snackbar.value = { show: true, message: '充电结束！请前往订单页面支付', color: 'success' }
  } catch (error) {
    snackbar.value = { show: true, message: error.response?.data?.message || '操作失败', color: 'error' }
  }
}

async function handleCancel(id) {
  try {
    await reservationApi.cancel(id)
    await loadReservations()
    snackbar.value = { show: true, message: '预约已取消', color: 'success' }
  } catch (error) {
    snackbar.value = { show: true, message: error.response?.data?.message || '取消失败', color: 'error' }
  }
}

function getStatusColor(status) {
  const map = { 0: 'error', 1: 'warning', 2: 'success', 3: 'info' }
  return map[status] || 'grey'
}

function getStatusText(status) {
  const map = { 0: '已取消', 1: '待签到', 2: '充电中', 3: '已完成' }
  return map[status] || '未知'
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
  <div>
    <v-container class="py-6">
      <h1 class="text-h5 font-weight-bold mb-6">
        <v-icon class="mr-2">mdi-calendar-clock</v-icon>我的预约
      </h1>

      <div v-if="loading" class="text-center py-8">
        <v-progress-circular indeterminate color="primary" size="48" />
      </div>

      <div v-else-if="reservations.length === 0" class="text-center py-12">
        <v-icon size="64" color="grey-lighten-1">mdi-calendar-clock</v-icon>
        <p class="text-grey mt-4 mb-4">暂无预约记录</p>
        <v-btn to="/" color="primary" prepend-icon="mdi-ev-station">去预约</v-btn>
      </div>

      <div v-else>
        <v-card v-for="r in reservations" :key="r.id" class="mb-4" rounded="lg">
          <v-card-text class="pa-5">
            <div class="d-flex align-center justify-space-between mb-3">
              <v-chip :color="getStatusColor(r.status)" size="small" variant="tonal">
                {{ getStatusText(r.status) }}
              </v-chip>
              <span class="text-body-2 text-grey">#{{ r.id }}</span>
            </div>

            <v-row dense>
              <v-col cols="12" sm="6">
                <div class="text-body-2 mb-1"><span class="text-grey">充电站：</span>{{ r.stationName }}</div>
              </v-col>
              <v-col cols="12" sm="6">
                <div class="text-body-2 mb-1"><span class="text-grey">充电桩：</span>{{ r.pileCode }}</div>
              </v-col>
              <v-col cols="12" sm="6">
                <div class="text-body-2 mb-1"><span class="text-grey">车位：</span>{{ r.spotCode }}</div>
              </v-col>
              <v-col cols="12" sm="6">
                <div class="text-body-2 mb-1"><span class="text-grey">预估停车费：</span>¥{{ r.estimatedCost || 0 }}</div>
              </v-col>
              <v-col cols="12">
                <div class="text-body-2"><span class="text-grey">预约时间：</span>{{ formatDate(r.startTime) }} - {{ formatDate(r.endTime) }}</div>
              </v-col>
            </v-row>
          </v-card-text>

          <v-divider />

          <v-card-actions class="pa-3">
            <template v-if="r.status === 1">
              <v-btn color="primary" size="small" variant="flat" @click="showConfirm('确认签到', '确认签到？', () => handleCheckIn(r.id))">
                <v-icon class="mr-1">mdi-login</v-icon>签到
              </v-btn>
              <v-btn color="error" size="small" variant="outlined" @click="showConfirm('取消预约', '确认取消预约？', () => handleCancel(r.id))">取消</v-btn>
              <span class="text-caption text-grey ml-2">可提前5分钟签到，过期失效</span>
            </template>
            <template v-if="r.status === 2">
              <v-btn color="info" size="small" variant="outlined" @click="openMonitor(r.id, r.pilePower)">
                <v-icon class="mr-1">mdi-monitor</v-icon>监控
              </v-btn>
              <v-btn color="primary" size="small" variant="flat" @click="showConfirm('结束充电', '确认结束充电？将生成订单。', () => handleCheckOut(r.id))">
                结束充电
              </v-btn>
            </template>
          </v-card-actions>
        </v-card>
      </div>
    </v-container>

    <ChargingMonitor
      :visible="showMonitor"
      :reservation-id="monitorReservationId"
      :pile-power="monitorPilePower"
      @close="showMonitor = false"
    />

    <!-- Confirm Dialog -->
    <v-dialog v-model="confirmDialog.show" max-width="360">
      <v-card rounded="lg">
        <v-card-title>{{ confirmDialog.title }}</v-card-title>
        <v-card-text>{{ confirmDialog.message }}</v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="confirmDialog.show = false">取消</v-btn>
          <v-btn color="primary" variant="flat" @click="handleConfirm">确认</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Snackbar -->
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="3000" location="top">
      {{ snackbar.message }}
    </v-snackbar>
  </div>
</template>
