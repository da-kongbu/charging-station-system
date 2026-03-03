<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'

const props = defineProps({
  reservationId: {
    type: Number,
    required: true
  },
  visible: {
    type: Boolean,
    default: false
  },
  pilePower: {
    type: Number,
    default: 7
  }
})

const emit = defineEmits(['close'])

const stompClient = ref(null)
const chargingData = ref(null)
const connected = ref(false)
const socketError = ref(null)
const demoMode = ref(false)
let demoInterval = null
let connectionTimeout = null

watch(() => props.visible, (newVal) => {
  if (newVal) {
    connectWebSocket()
  } else {
    disconnectWebSocket()
    stopDemo()
  }
})

onUnmounted(() => {
  disconnectWebSocket()
  stopDemo()
})

function connectWebSocket() {
  try {
    const socket = new SockJS('http://localhost:8080/ws')
    
    stompClient.value = new Client({
      webSocketFactory: () => socket,
      debug: (str) => {
        console.log('STOMP: ' + str)
      },
      reconnectDelay: 5000,
      onConnect: (frame) => {
        connected.value = true
        socketError.value = null
        demoMode.value = false
        clearTimeout(connectionTimeout)
        console.log('Connected: ' + frame)
        
        stompClient.value.subscribe(`/topic/charging/${props.reservationId}`, (message) => {
          chargingData.value = JSON.parse(message.body)
        })
      },
      onStompError: (frame) => {
        console.error('STOMP error: ' + frame.headers['message'])
        startDemoFallback()
      },
      onWebSocketClose: () => {
        connected.value = false
        if (!demoMode.value) {
          startDemoFallback()
        }
      },
      onWebSocketError: () => {
        startDemoFallback()
      }
    })

    stompClient.value.activate()

    // 5秒超时：如果连不上就启用演示模式
    connectionTimeout = setTimeout(() => {
      if (!connected.value) {
        startDemoFallback()
      }
    }, 5000)

  } catch (e) {
    console.error('WebSocket init error:', e)
    startDemoFallback()
  }
}

// 全局缓存：关闭面板后重新打开时可以接上之前的数据
const demoStateCache = new Map()

function startDemoFallback() {
  if (demoMode.value) return
  demoMode.value = true
  connected.value = true
  socketError.value = null
  console.log('进入演示模式 (Demo Mode)')

  const rid = props.reservationId
  const POWER_KW = props.pilePower || 7.0
  const BATTERY_CAPACITY_KWH = 60.0
  const baseVoltage = POWER_KW > 50 ? 750 : 220
  const baseCurrent = (POWER_KW * 1000) / baseVoltage

  // 如果有缓存，接上之前的进度；否则生成新的起点
  let state = demoStateCache.get(rid)
  if (!state) {
    const startSoc = POWER_KW > 50 ? (10 + Math.floor(Math.random() * 20)) : (20 + Math.floor(Math.random() * 30))
    state = { startSoc, startTime: Date.now(), powerKw: POWER_KW }
    demoStateCache.set(rid, state)
  }

  const { startSoc, startTime } = state

  // 计算当前进度（基于首次打开以来的真实时间）
  function calcData() {
    const elapsedSeconds = (Date.now() - startTime) / 1000
    const elapsedHours = elapsedSeconds / 3600
    const energy = POWER_KW * elapsedHours
    const socGain = (energy / BATTERY_CAPACITY_KWH) * 100
    const currentSoc = Math.min(100, Math.round(startSoc + socGain))
    const voltage = (baseVoltage + (Math.random() * 6 - 3)).toFixed(1)
    const current = (baseCurrent + (Math.random() * 3 - 1.5)).toFixed(1)
    const power = ((voltage * current) / 1000).toFixed(2)
    const remainingKwh = ((100 - currentSoc) / 100) * BATTERY_CAPACITY_KWH
    const remainingMinutes = Math.round((remainingKwh / POWER_KW) * 60)

    return {
      reservationId: rid,
      status: currentSoc >= 100 ? 'COMPLETED' : 'CHARGING',
      voltage: parseFloat(voltage),
      current: parseFloat(current),
      power: parseFloat(power),
      soc: currentSoc,
      remainingTime: remainingMinutes,
      chargedEnergy: parseFloat(energy.toFixed(2))
    }
  }

  // 立即显示当前进度
  chargingData.value = calcData()

  // 每秒刷新
  demoInterval = setInterval(() => {
    chargingData.value = calcData()
    if (chargingData.value.soc >= 100) {
      clearInterval(demoInterval)
      demoInterval = null
    }
  }, 1000)
}

function stopDemo() {
  if (demoInterval) {
    clearInterval(demoInterval)
    demoInterval = null
  }
  if (connectionTimeout) {
    clearTimeout(connectionTimeout)
    connectionTimeout = null
  }
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

function close() {
  emit('close')
}

function getBatteryColor(soc) {
  if (!soc) return '#e0e0e0'
  if (soc < 20) return '#ef4444'
  if (soc < 50) return '#f59e0b'
  return '#10b981'
}
</script>

<template>
  <div v-if="visible" class="modal-overlay" @click.self="close">
    <div class="modal-content">
      <div class="modal-header">
        <h3>实时充电监控</h3>
        <button class="close-btn" @click="close">×</button>
      </div>
      
      <div class="monitor-body">
        <div v-if="!connected && !socketError" class="loading-state">
          <div class="spinner"></div>
          <p>正在连接充电桩...</p>
        </div>

        <div v-if="socketError" class="error-state">
          <p>{{ socketError }}</p>
        </div>

        <div v-if="connected && !chargingData" class="waiting-state">
           <div class="spinner"></div>
           <p>等待数据传输...</p>
        </div>

        <div v-if="chargingData" class="dashboard">
          <!-- 演示模式提示 -->
          <div v-if="demoMode" class="demo-badge">演示模式</div>

          <div class="battery-section">
            <div class="battery-shell">
              <div class="battery-level" 
                   :style="{ width: chargingData.soc + '%', background: getBatteryColor(chargingData.soc) }">
                <div class="battery-text">{{ chargingData.soc }}%</div>
              </div>
              <div class="battery-tip"></div>
            </div>
            <p class="status-text">{{ chargingData.status === 'COMPLETED' ? '充电完成' : '正在充电...' }}</p>
          </div>

          <div class="metrics-grid">
            <div class="metric-card">
              <span class="label">电压</span>
              <span class="value">{{ chargingData.voltage }} <small>V</small></span>
            </div>
            <div class="metric-card">
               <span class="label">电流</span>
               <span class="value">{{ chargingData.current }} <small>A</small></span>
            </div>
            <div class="metric-card">
               <span class="label">功率</span>
               <span class="value">{{ chargingData.power }} <small>kW</small></span>
            </div>
            <div class="metric-card">
               <span class="label">已充</span>
               <span class="value">{{ chargingData.chargedEnergy }} <small>kWh</small></span>
            </div>
          </div>
          
          <div class="chart-area">
             <div class="progress-bar-track">
               <div class="progress-bar-fill" :style="{ width: chargingData.soc + '%', background: getBatteryColor(chargingData.soc) }"></div>
             </div>
             <p class="remaining-time">预计剩余时间: {{ chargingData.remainingTime }} 分钟</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  backdrop-filter: blur(4px);
}

.modal-content {
  background: white;
  width: 90%;
  max-width: 500px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 10px 25px rgba(0,0,0,0.2);
  animation: popIn 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

@keyframes popIn {
  from { transform: scale(0.8); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.modal-header {
  padding: 15px 20px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.close-btn {
  background: none;
  border: none;
  color: white;
  font-size: 24px;
  cursor: pointer;
}

.monitor-body {
  padding: 25px;
  min-height: 300px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.loading-state, .waiting-state, .error-state {
  text-align: center;
  color: #666;
}

.demo-badge {
  text-align: center;
  background: #fef3c7;
  color: #d97706;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 0.8rem;
  margin-bottom: 15px;
  display: inline-block;
  align-self: center;
}

.battery-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 30px;
}

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

.status-text {
  margin-top: 10px;
  font-size: 1.1rem;
  color: #4b5563;
  font-weight: 500;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { opacity: 0.6; }
  50% { opacity: 1; }
  100% { opacity: 0.6; }
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 15px;
  margin-bottom: 25px;
}

.metric-card {
  background: #f3f4f6;
  padding: 15px;
  border-radius: 12px;
  text-align: center;
}

.metric-card .label {
  display: block;
  font-size: 0.85rem;
  color: #6b7280;
  margin-bottom: 4px;
}

.metric-card .value {
  font-size: 1.4rem;
  font-weight: 700;
  color: #1f2937;
}

.metric-card small {
  font-size: 0.8rem;
  color: #9ca3af;
}

.progress-bar-track {
  height: 16px;
  background: #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 10px;
}

.progress-bar-fill {
  height: 100%;
  border-radius: 8px;
  transition: width 1s ease;
}

.remaining-time {
  text-align: center;
  color: #6b7280;
  font-size: 0.9rem;
}

.spinner {
  border: 4px solid rgba(0, 0, 0, 0.1);
  border-left-color: #3b82f6;
  border-radius: 50%;
  width: 30px;
  height: 30px;
  animation: spin 1s linear infinite;
  margin: 0 auto 10px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
