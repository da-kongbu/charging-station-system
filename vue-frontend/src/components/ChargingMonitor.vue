<script setup>
import { ref, onUnmounted, watch } from 'vue'
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'

const props = defineProps({
  reservationId: { type: Number, required: true },
  visible: { type: Boolean, default: false },
  pilePower: { type: Number, default: 7 },
  actualArrivalTime: { type: [String, Number, Date], default: '' }
})

const emit = defineEmits(['close'])

const stompClient = ref(null)
const chargingData = ref(null)
const connected = ref(false)
const socketError = ref(null)
const demoMode = ref(false)
const elapsedSeconds = ref(0)
let demoInterval = null
let connectionTimeout = null
let elapsedTimer = null

// 模块级别缓存，组件重新挂载也不会丢失进度
const demoStateCache = new Map()

watch(() => props.visible, (newVal) => {
  if (newVal) {
    startElapsedTimer()
    connectWebSocket()
  } else {
    disconnectWebSocket()
    stopDemo()
    stopElapsedTimer()
  }
})

watch(() => props.actualArrivalTime, () => {
  if (props.visible) {
    startElapsedTimer()
  }
})

onUnmounted(() => {
  disconnectWebSocket()
  stopDemo()
  stopElapsedTimer()
})

function parseTimestamp(value) {
  if (!value) return null
  if (value instanceof Date) return value.getTime()
  if (typeof value === 'number') return value
  if (typeof value !== 'string') return null
  const parsed = Date.parse(value.includes('T') ? value : value.replace(' ', 'T'))
  return Number.isNaN(parsed) ? null : parsed
}

function getChargingStartTimestamp() {
  return parseTimestamp(props.actualArrivalTime)
}

function updateElapsedSeconds() {
  const startTime = getChargingStartTimestamp()
  if (!startTime) {
    elapsedSeconds.value = 0
    return
  }
  elapsedSeconds.value = Math.max(0, Math.floor((Date.now() - startTime) / 1000))
}

function startElapsedTimer() {
  stopElapsedTimer()
  updateElapsedSeconds()
  if (!getChargingStartTimestamp()) return
  elapsedTimer = setInterval(updateElapsedSeconds, 1000)
}

function stopElapsedTimer() {
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

function connectWebSocket() {
  // 重置状态
  connected.value = false
  socketError.value = null

  try {
    const socket = new SockJS('http://localhost:8080/ws')
    stompClient.value = new Client({
      webSocketFactory: () => socket,
      debug: (str) => console.log('STOMP: ' + str),
      reconnectDelay: 0, // 禁用自动重连，避免与演示模式冲突
      onConnect: (frame) => {
        connected.value = true
        socketError.value = null
        clearTimeout(connectionTimeout)
        stopDemo() // 连接成功，停止演示模式
        stompClient.value.subscribe(`/topic/charging/${props.reservationId}`, (message) => {
          chargingData.value = JSON.parse(message.body)
        })
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers?.['message'])
        startDemoFallback()
      },
      onWebSocketClose: () => {
        connected.value = false
        // 只在未处于演示模式时启动回退
        if (!demoMode.value) startDemoFallback()
      },
      onWebSocketError: () => {
        if (!demoMode.value) startDemoFallback()
      }
    })
    stompClient.value.activate()
    connectionTimeout = setTimeout(() => {
      if (!connected.value && !demoMode.value) startDemoFallback()
    }, 5000)
  } catch (e) {
    console.error('WebSocket init error:', e)
    startDemoFallback()
  }
}

function startDemoFallback() {
  // 先清理旧的 interval，防止重复
  if (demoInterval) { clearInterval(demoInterval); demoInterval = null }
  if (connectionTimeout) { clearTimeout(connectionTimeout); connectionTimeout = null }

  demoMode.value = true
  connected.value = true
  socketError.value = null

  const rid = props.reservationId
  const POWER_KW = props.pilePower || 7.0
  const BATTERY_CAPACITY_KWH = 60.0
  const baseVoltage = POWER_KW > 50 ? 750 : 220
  const baseCurrent = (POWER_KW * 1000) / baseVoltage
  const chargingStartTime = getChargingStartTimestamp() || Date.now()

  // 复用缓存状态，保持进度连续
  let state = demoStateCache.get(rid)
  if (!state) {
    const startSoc = POWER_KW > 50 ? (10 + Math.floor(Math.random() * 20)) : (20 + Math.floor(Math.random() * 30))
    state = { startSoc, startTime: chargingStartTime, powerKw: POWER_KW }
    demoStateCache.set(rid, state)
  } else if (chargingStartTime < state.startTime) {
    state.startTime = chargingStartTime
  }

  state.powerKw = POWER_KW

  const { startSoc, startTime } = state

  function calcData() {
    const elapsedHours = (Date.now() - startTime) / 3600000
    const energy = POWER_KW * elapsedHours
    const socGain = (energy / BATTERY_CAPACITY_KWH) * 100
    const currentSoc = Math.min(100, Math.round(startSoc + socGain))
    const voltage = (baseVoltage + (Math.random() * 6 - 3)).toFixed(1)
    const current = (baseCurrent + (Math.random() * 3 - 1.5)).toFixed(1)
    const power = ((voltage * current) / 1000).toFixed(2)
    const remainingKwh = ((100 - currentSoc) / 100) * BATTERY_CAPACITY_KWH
    return {
      reservationId: rid, status: currentSoc >= 100 ? 'COMPLETED' : 'CHARGING',
      voltage: parseFloat(voltage), current: parseFloat(current), power: parseFloat(power),
      soc: currentSoc, remainingTime: Math.round((remainingKwh / POWER_KW) * 60),
      chargedEnergy: parseFloat(energy.toFixed(2))
    }
  }

  chargingData.value = calcData()
  demoInterval = setInterval(() => {
    chargingData.value = calcData()
    if (chargingData.value.soc >= 100) { clearInterval(demoInterval); demoInterval = null }
  }, 1000)
}

function stopDemo() {
  if (demoInterval) { clearInterval(demoInterval); demoInterval = null }
  if (connectionTimeout) { clearTimeout(connectionTimeout); connectionTimeout = null }
  demoMode.value = false
}

function disconnectWebSocket() {
  if (stompClient.value) {
    stompClient.value.deactivate()
    stompClient.value = null
  }
  connected.value = false
  chargingData.value = null
}

function getBatteryColor(soc) {
  if (!soc) return 'grey'
  if (soc < 20) return 'error'
  if (soc < 50) return 'warning'
  return 'success'
}

function getBatteryHexColor(soc) {
  if (!soc) return '#e0e0e0'
  if (soc < 20) return '#ef4444'
  if (soc < 50) return '#f59e0b'
  return '#10b981'
}

function formatElapsed(totalSeconds) {
  const seconds = Math.max(0, totalSeconds || 0)
  const hours = String(Math.floor(seconds / 3600)).padStart(2, '0')
  const minutes = String(Math.floor((seconds % 3600) / 60)).padStart(2, '0')
  const remainingSeconds = String(seconds % 60).padStart(2, '0')
  return `${hours}:${minutes}:${remainingSeconds}`
}
</script>

<template>
  <v-dialog :model-value="visible" max-width="500" @update:model-value="$emit('close')">
    <v-card rounded="lg">
      <!-- Header -->
      <v-card-title class="d-flex align-center justify-space-between pa-4" style="background: linear-gradient(135deg, #3b82f6, #2563eb); color: white;">
        <div class="d-flex align-center ga-2">
          <v-icon color="white">mdi-battery-charging</v-icon>
          <span>实时充电监控</span>
        </div>
        <v-btn icon="mdi-close" variant="text" size="small" color="white" @click="$emit('close')" />
      </v-card-title>

      <v-card-text class="pa-6">
        <!-- Loading -->
        <div v-if="!connected && !socketError" class="text-center py-8">
          <v-progress-circular indeterminate color="primary" size="40" />
          <p class="text-grey mt-3">正在连接充电桩...</p>
          <p v-if="elapsedSeconds > 0" class="text-caption text-grey mt-2">已签到 {{ formatElapsed(elapsedSeconds) }}</p>
        </div>

        <!-- Error -->
        <v-alert v-if="socketError" type="error" variant="tonal">{{ socketError }}</v-alert>

        <!-- Waiting -->
        <div v-if="connected && !chargingData" class="text-center py-8">
          <v-progress-circular indeterminate color="primary" size="40" />
          <p class="text-grey mt-3">等待数据传输...</p>
          <p v-if="elapsedSeconds > 0" class="text-caption text-grey mt-2">已签到 {{ formatElapsed(elapsedSeconds) }}，监控数据会持续刷新</p>
        </div>

        <!-- Dashboard -->
        <div v-if="chargingData">
          <div v-if="demoMode" class="text-center mb-4">
            <v-chip color="warning" size="small" variant="tonal" prepend-icon="mdi-test-tube">仿真演示数据</v-chip>
          </div>
          <div v-else class="text-center mb-4">
            <span class="text-caption text-grey">数据来源于充电桩实时上报</span>
          </div>

          <!-- Battery Visual -->
          <div class="text-center mb-6">
            <div class="battery-shell mx-auto">
              <div class="battery-level" :style="{ width: chargingData.soc + '%', background: getBatteryHexColor(chargingData.soc) }">
                <span class="battery-text">{{ chargingData.soc }}%</span>
              </div>
              <div class="battery-tip" />
            </div>
            <p class="mt-3 text-body-1 font-weight-medium" :class="chargingData.status === 'COMPLETED' ? 'text-success' : 'text-primary'">
              {{ chargingData.status === 'COMPLETED' ? '充电完成' : '正在充电...' }}
            </p>
          </div>

          <!-- Metrics -->
          <v-row dense class="mb-4">
            <v-col cols="6">
              <v-sheet rounded="lg" class="pa-3 text-center" color="grey-lighten-4">
                <div class="text-caption text-grey mb-1">电压</div>
                <div class="text-h6 font-weight-bold">{{ chargingData.voltage }} <small class="text-body-2 text-grey">V</small></div>
              </v-sheet>
            </v-col>
            <v-col cols="6">
              <v-sheet rounded="lg" class="pa-3 text-center" color="grey-lighten-4">
                <div class="text-caption text-grey mb-1">电流</div>
                <div class="text-h6 font-weight-bold">{{ chargingData.current }} <small class="text-body-2 text-grey">A</small></div>
              </v-sheet>
            </v-col>
            <v-col cols="6">
              <v-sheet rounded="lg" class="pa-3 text-center" color="grey-lighten-4">
                <div class="text-caption text-grey mb-1">功率</div>
                <div class="text-h6 font-weight-bold">{{ chargingData.power }} <small class="text-body-2 text-grey">kW</small></div>
              </v-sheet>
            </v-col>
            <v-col cols="6">
              <v-sheet rounded="lg" class="pa-3 text-center" color="grey-lighten-4">
                <div class="text-caption text-grey mb-1">已充</div>
                <div class="text-h6 font-weight-bold">{{ chargingData.chargedEnergy }} <small class="text-body-2 text-grey">kWh</small></div>
              </v-sheet>
            </v-col>
          </v-row>

          <!-- Progress -->
          <v-progress-linear
            :model-value="chargingData.soc"
            :color="getBatteryColor(chargingData.soc)"
            height="12"
            rounded
            class="mb-2"
          />
          <p v-if="elapsedSeconds > 0" class="text-center text-body-2 text-grey mb-1">已充时长: {{ formatElapsed(elapsedSeconds) }}</p>
          <p class="text-center text-body-2 text-grey">预计剩余时间: {{ chargingData.remainingTime }} 分钟</p>
        </div>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<style scoped>
.battery-shell {
  width: 160px;
  height: 70px;
  border: 4px solid #333;
  border-radius: 10px;
  padding: 4px;
  position: relative;
  display: flex;
  align-items: center;
}
.battery-tip {
  width: 10px;
  height: 24px;
  background: #333;
  position: absolute;
  right: -14px;
  top: 50%;
  transform: translateY(-50%);
  border-radius: 0 4px 4px 0;
}
.battery-level {
  height: 100%;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: width 0.5s ease, background 0.5s ease;
  min-width: 10%;
}
.battery-text {
  color: white;
  font-weight: bold;
  font-size: 1.2rem;
  text-shadow: 0 1px 2px rgba(0,0,0,0.3);
}
</style>
