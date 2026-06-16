<template>
  <!-- FAB -->
  <v-btn
    v-if="!isOpen"
    icon
    color="primary"
    size="default"
    position="fixed"
    location="bottom end"
    class="ma-4"
    elevation="4"
    @click="toggleChat"
  >
    <v-badge v-if="messages.length > 0" dot color="error" floating>
      <v-icon size="22">mdi-robot-outline</v-icon>
    </v-badge>
    <v-icon v-else size="22">mdi-robot-outline</v-icon>
    <v-tooltip activator="parent" location="left">智充助手</v-tooltip>
  </v-btn>

  <!-- Chat Window -->
  <Transition :name="isFullscreen ? 'chat-fade' : 'chat-slide'">
    <v-card
      v-if="isOpen"
      rounded="lg"
      elevation="8"
      :width="isFullscreen ? '100%' : 400"
      :class="isFullscreen ? 'chat-fullscreen' : 'chat-window'"
    >
      <!-- Header -->
      <div class="chat-header pa-3 d-flex align-center justify-space-between">
        <div class="d-flex align-center ga-2">
          <v-avatar color="rgba(255,255,255,0.25)" size="32">
            <v-icon size="18" color="white">mdi-robot-outline</v-icon>
          </v-avatar>
          <div class="text-subtitle-2 font-weight-bold text-white">智充助手</div>
        </div>
        <div class="d-flex align-center ga-1">
          <v-btn
            :icon="isFullscreen ? 'mdi-arrow-collapse' : 'mdi-arrow-expand'"
            variant="text"
            size="small"
            color="white"
            @click="isFullscreen = !isFullscreen"
          />
          <v-btn icon="mdi-close" variant="text" size="small" color="white" @click="toggleChat" />
        </div>
      </div>

      <!-- Messages -->
      <div class="chat-messages" ref="messagesContainer">
        <div class="message assistant">
          <v-avatar size="30" color="grey-lighten-3" class="flex-shrink-0">
            <v-icon size="18" color="primary">mdi-robot</v-icon>
          </v-avatar>
          <div class="message-bubble assistant-bubble">
            {{ contextHints.greeting }}
            <div class="text-caption text-grey-darken-1 mt-2 mb-1">你可以问我：</div>
            <span v-html="welcomeTips"></span>
          </div>
        </div>

        <div v-for="(msg, index) in messages" :key="index">
          <div class="message" :class="msg.role">
            <v-avatar v-if="msg.role === 'user'" size="30" color="primary" class="flex-shrink-0">
              <v-icon size="18" color="white">mdi-account</v-icon>
            </v-avatar>
            <v-avatar v-else size="30" color="grey-lighten-3" class="flex-shrink-0">
              <v-icon size="18" color="primary">mdi-robot</v-icon>
            </v-avatar>

            <div class="message-content">
              <div v-if="msg.content" class="message-bubble" :class="msg.role === 'user' ? 'user-bubble' : 'assistant-bubble'" v-html="formatMessage(msg.content)" />

              <!-- 充电站列表卡片 -->
              <div v-if="msg.cardType === 'stations' && msg.cardData?.length" class="card-container">
                <div v-for="station in msg.cardData" :key="station.id" class="station-card" @click="goToStation(station.id)">
                  <div class="d-flex justify-space-between align-center mb-1">
                    <strong class="text-body-2" style="color:#2d3436">{{ station['\u540D\u79F0'] || station.name }}</strong>
                    <v-chip v-if="station['\u8DDD\u79BB(km)'] != null" size="x-small" color="primary" variant="tonal">{{ station['\u8DDD\u79BB(km)'] }}km</v-chip>
                  </div>
                  <div class="text-caption text-grey mb-1">{{ station['\u5730\u5740'] || station.address }}</div>
                  <div class="text-caption">
                    可用桩 <strong :style="{color: station['当前可用桩数'] > 0 ? '#00b894' : '#d63031'}">{{ station['当前可用桩数'] ?? '?' }}</strong>/{{ station['总桩数'] ?? '?' }}
                    <span class="text-grey ml-1">— 点击查看详情</span>
                  </div>
                </div>
              </div>

              <!-- 站点详情卡片 -->
              <div v-if="msg.cardType === 'station_detail' && msg.cardData" class="card-container">
                <div class="station-card">
                  <strong class="text-body-2">{{ msg.cardData['\u540D\u79F0'] }}</strong>
                  <div class="text-caption text-grey mt-1">{{ msg.cardData['\u5730\u5740'] }}</div>
                  <div class="text-caption mt-1">
                    可用桩 <strong>{{ msg.cardData['\u5F53\u524D\u53EF\u7528\u6869\u6570'] }}</strong>/{{ msg.cardData['\u603B\u6869\u6570'] }}
                    <span v-if="msg.cardData['\u8425\u4E1A\u65F6\u95F4']" class="ml-2">{{ msg.cardData['\u8425\u4E1A\u65F6\u95F4'] }}</span>
                  </div>
                  <div v-if="msg.cardData['\u5145\u7535\u6869\u8BE6\u60C5']" class="mt-2">
                    <div v-for="(pile, pi) in msg.cardData['\u5145\u7535\u6869\u8BE6\u60C5']" :key="pi" class="d-flex align-center ga-2 text-caption py-1">
                      <v-icon size="14" :color="pile['\u72B6\u6001'] === '\u7A7A\u95F2\u53EF\u7528' ? 'success' : pile['\u72B6\u6001'] === '\u5145\u7535\u4E2D' ? 'warning' : 'grey'">mdi-flash</v-icon>
                      {{ pile['\u6869\u7F16\u53F7'] }} · {{ pile['\u7C7B\u578B'] }} · {{ pile['\u529F\u7387'] }} · {{ pile['\u72B6\u6001'] }}
                    </div>
                  </div>
                </div>
              </div>

              <!-- 可用车位卡片（可点击选择） -->
              <div v-if="msg.cardType === 'available_spots' && msg.cardData?.length" class="card-container">
                <div v-for="(spot, si) in msg.cardData" :key="spot.spot_id" class="station-card spot-selectable" style="border-left: 3px solid #0984e3;" @click="selectSpot(spot)">
                  <div class="d-flex justify-space-between align-center mb-1">
                    <strong class="text-body-2">{{ spot['车位编号'] || '未知' }}</strong>
                    <v-chip size="x-small" :color="spot['充电类型'] === '直流快充' ? 'orange' : 'primary'" variant="tonal">{{ spot['充电类型'] || '标准' }}</v-chip>
                  </div>
                  <div class="text-caption text-grey">
                    {{ spot['每小时价格'] }}元/小时 · 服务费{{ spot['服务费(元/度)'] }}元/度
                  </div>
                </div>
              </div>

              <!-- AI 推荐预约卡片 -->
              <div v-if="msg.cardType === 'recommend_booking' && msg.cardData" class="card-container">
                <div class="station-card" style="border-left: 3px solid #0984e3;">
                  <div class="d-flex align-center ga-2 mb-2">
                    <v-icon color="primary" size="20">mdi-lightbulb-outline</v-icon>
                    <strong class="text-body-2" style="color:#0984e3">AI 推荐预约</strong>
                  </div>
                  <div class="text-caption"><v-icon size="14" class="mr-1">mdi-ev-station</v-icon>{{ msg.cardData.station_name }}</div>
                  <div class="text-caption"><v-icon size="14" class="mr-1">mdi-parking</v-icon>车位 {{ msg.cardData.spot_code }}<span v-if="msg.cardData.charging_type"> · {{ msg.cardData.charging_type }}</span></div>
                  <div class="text-caption"><v-icon size="14" class="mr-1">mdi-clock-outline</v-icon>{{ formatTime(msg.cardData.start_time) }} ~ {{ formatTime(msg.cardData.end_time) }}</div>
                  <div class="text-caption"><v-icon size="14" class="mr-1">mdi-currency-cny</v-icon>¥{{ msg.cardData.price_per_hour }}/小时</div>
                  <div v-if="msg.cardData.reason" class="text-caption text-grey mt-1">{{ msg.cardData.reason }}</div>
                  <div class="mt-2">
                    <v-btn size="x-small" variant="flat" color="primary"
                      @click="goToRecommendBooking(msg.cardData)">去预约</v-btn>
                  </div>
                </div>
              </div>

              <!-- 待确认预约卡片 -->
              <div v-if="msg.cardType === 'reservation_pending' && msg.cardData" class="card-container">
                <div class="station-card" style="border-left: 3px solid #00b894;">
                  <div class="d-flex align-center justify-space-between ga-2 mb-2">
                    <div class="d-flex align-center ga-2">
                      <v-icon color="success" size="20">mdi-calendar-check-outline</v-icon>
                      <strong class="text-body-2" style="color:#00b894">待确认预约</strong>
                    </div>
                    <v-chip v-if="msg.cardData.confirmed" size="x-small" color="success" variant="tonal">已确认</v-chip>
                  </div>
                  <div class="text-caption"><v-icon size="14" class="mr-1">mdi-ev-station</v-icon>{{ msg.cardData.station_name }}</div>
                  <div class="text-caption"><v-icon size="14" class="mr-1">mdi-parking</v-icon>车位 {{ msg.cardData.spot_code }}<span v-if="msg.cardData.charging_type"> · {{ msg.cardData.charging_type }}</span></div>
                  <div class="text-caption"><v-icon size="14" class="mr-1">mdi-clock-outline</v-icon>{{ formatTime(msg.cardData.start_time) }} ~ {{ formatTime(msg.cardData.end_time) }}</div>
                  <div class="text-caption"><v-icon size="14" class="mr-1">mdi-currency-cny</v-icon>¥{{ msg.cardData.price_per_hour }}/小时</div>
                  <div v-if="msg.cardData.reason" class="text-caption text-grey mt-1">{{ msg.cardData.reason }}</div>
                  <div class="mt-2 d-flex align-center ga-2">
                    <v-btn
                      size="x-small"
                      variant="flat"
                      color="success"
                      :loading="confirmingToken === msg.cardData.confirm_token"
                      :disabled="msg.cardData.confirmed"
                      @click="confirmPendingReservation(msg)"
                    >确认预约</v-btn>
                    <v-btn
                      size="x-small"
                      variant="text"
                      color="primary"
                      @click="goToRecommendBooking(msg.cardData)"
                    >查看详情</v-btn>
                  </div>
                </div>
              </div>

              <!-- 预约列表卡片 -->
              <div v-if="msg.cardType === 'reservations' && msg.cardData?.length" class="card-container">
                <div v-for="r in msg.cardData" :key="r.reservation_id" class="station-card" style="border-left: 3px solid #0984e3;">
                  <div class="d-flex justify-space-between align-center mb-1">
                    <strong class="text-body-2">{{ r.station_name }}</strong>
                    <v-chip :color="r.status === '\u5F85\u4F7F\u7528' ? 'warning' : r.status === '\u4F7F\u7528\u4E2D' ? 'success' : 'grey'" size="x-small" variant="tonal">{{ r.status }}</v-chip>
                  </div>
                  <div class="text-caption text-grey">{{ r.spot_code }} · {{ r.start_time }} ~ {{ r.end_time }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="isLoading" class="message assistant">
          <v-avatar size="30" color="grey-lighten-3" class="flex-shrink-0">
            <v-icon size="18" color="primary">mdi-robot</v-icon>
          </v-avatar>
          <div class="message-bubble assistant-bubble typing">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
        </div>
      </div>

      <!-- Input -->
      <div class="pa-3 border-t">
        <v-text-field
          v-model="inputText"
          variant="outlined"
          density="compact"
          rounded="pill"
          placeholder="输入您的问题..."
          hide-details
          :disabled="isLoading"
          @keydown.enter="sendMessage"
          append-inner-icon="mdi-send"
          @click:append-inner="sendMessage"
        />
      </div>
    </v-card>
  </Transition>

  <!-- 推荐预约和待确认预约都支持跳转站点详情，待确认预约也可直接在聊天窗口确认 -->
</template>

<script setup>
import { ref, nextTick, watch, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import api from '@/api'
import { useLocation } from '@/composables/useLocation'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const isOpen = ref(false)
const isFullscreen = ref(false)
const isLoading = ref(false)
const inputText = ref('')
const messages = ref([])
const messagesContainer = ref(null)
const confirmingToken = ref('')

// 上下文感知的欢迎语和提示建议
const contextHints = computed(() => {
  const path = route.path
  if (path.startsWith('/station/')) {
    return {
      greeting: '当前页面是充电站详情，我可以帮你比较车位和时段。',
      tips: ['哪个时段充电最便宜？', '帮我比较两个车位的费用', '快充和慢充价格差多少']
    }
  }
  if (path === '/reservations') {
    return {
      greeting: '关于预约流程、签到、充电时长的问题都可以问我。',
      tips: ['签到后多久开始计费？', '如何取消预约？', '充电中可以提前结束吗？']
    }
  }
  if (path === '/orders') {
    return {
      greeting: '想了解计费规则？我可以解释费用构成和峰谷电价。',
      tips: ['停车费怎么算的？', '峰谷电价是什么？', '服务费包含什么？']
    }
  }
  // 默认（首页）
  return {
    greeting: '告诉我你的充电需求，我帮你推荐合适的站点。',
    tips: ['附近有什么充电站？', '附近有快充站吗？', '帮我预约明天下午3点快充']
  }
})

const welcomeTips = computed(() => contextHints.value.tips.map(t => `• ${t}`).join('<br>'))

const API_BASE = '/api/ai'

const { userLocation, getUserLocation } = useLocation()

onMounted(async () => { await getUserLocation() })

function toggleChat() {
  isOpen.value = !isOpen.value
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  })
}

function goToStation(id) {
  router.push(`/station/${id}`)
}

/** 点击推荐卡片 → 跳转 StationDetail 预填参数 */
function goToRecommendBooking(data) {
  if (!data.station_id) return
  const query = {}
  if (data.spot_id) query.spotId = data.spot_id
  if (data.start_time) query.startTime = data.start_time
  if (data.end_time) query.endTime = data.end_time
  if (data.reason) query.aiHint = data.reason
  if (data.confirm_token) query.confirmToken = data.confirm_token
  router.push({ path: `/station/${data.station_id}`, query })
}

/** 点击车位卡片 → 跳转 StationDetail（需要先找到 stationId） */
function selectSpot(spot) {
  // 从最近的消息中找到 stationId
  const stationMsg = [...messages.value].reverse().find(m =>
    m.cardType === 'station_detail' && m.cardData?.id
  )
  if (stationMsg) {
    router.push({
      path: `/station/${stationMsg.cardData.id}`,
      query: { spotId: spot.spot_id }
    })
  }
}

function formatTime(isoStr) {
  if (!isoStr) return ''
  try {
    const d = new Date(isoStr)
    return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  } catch { return isoStr }
}

function extractErrorMessage(error, fallback = '操作失败，请稍后再试。') {
  return error?.response?.data?.message || error?.response?.data?.data || fallback
}

async function confirmPendingReservation(message) {
  const data = message?.cardData
  if (!data?.confirm_token) {
    goToRecommendBooking(data || {})
    return
  }
  if (!authStore.isAuthenticated) {
    router.push('/login')
    return
  }

  confirmingToken.value = data.confirm_token
  try {
    const response = await api.post('/ai/agent/reservations/confirm', {
      confirmToken: data.confirm_token
    }, { timeout: 30000 })
    const json = response.data
    data.confirmed = true
    messages.value.push({
      role: 'assistant',
      content: json?.message || json?.data || '预约成功，已为你创建预约记录。'
    })
  } catch (error) {
    console.error('确认预约失败:', error)
    messages.value.push({
      role: 'assistant',
      content: extractErrorMessage(error, '预约失败，请稍后再试。')
    })
  } finally {
    confirmingToken.value = ''
    scrollToBottom()
  }
}

function formatMessage(text) {
  if (!text) return ''
  // XSS 过滤：先转义 HTML（保留反引号供 code 解析）
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  const lines = escaped.split('\n')
  let html = ''
  let inRootList = false, inOrderedList = false, orderedItemOpen = false, inNestedList = false
  const closeRootList = () => { if (inRootList) { html += '</ul>'; inRootList = false } }
  const closeNestedList = () => { if (inNestedList) { html += '</ul>'; inNestedList = false } }
  const closeOrderedItem = () => { closeNestedList(); if (orderedItemOpen) { html += '</li>'; orderedItemOpen = false } }
  const closeOrderedList = () => { closeOrderedItem(); if (inOrderedList) { html += '</ol>'; inOrderedList = false } }
  const closeAllLists = () => { closeRootList(); closeOrderedList() }

  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim()
    const orderedMatch = trimmed.match(/^(\d+)\.\s+(.+)/)
    // 只匹配单 * 或 - 后跟空格的无序列表，排除 ** 开头的粗体
    const unorderedMatch = trimmed.match(/^(\*|-(?!-))\s+(.+)/)

    if (/^### (.+)/.test(trimmed)) {
      closeAllLists()
      html += `<div class="md-h3">${md(trimmed.replace(/^### /, ''))}</div>`
      continue
    }
    if (/^## (.+)/.test(trimmed)) {
      closeAllLists()
      html += `<div class="md-h2">${md(trimmed.replace(/^## /, ''))}</div>`
      continue
    }
    if (trimmed === '') {
      if (inRootList || inOrderedList || orderedItemOpen || inNestedList) continue
      html += '<div class="md-blank"></div>'
      continue
    }
    if (orderedMatch) {
      closeRootList()
      if (!inOrderedList) { html += '<ol class="md-ol">'; inOrderedList = true } else closeOrderedItem()
      html += `<li>${md(orderedMatch[2])}`
      orderedItemOpen = true
      continue
    }
    if (unorderedMatch) {
      const content = unorderedMatch[2]
      if (orderedItemOpen) {
        if (!inNestedList) { html += '<ul class="md-ul-nested">'; inNestedList = true }
        html += `<li>${md(content)}</li>`
      } else {
        closeOrderedList()
        if (!inRootList) { html += '<ul class="md-ul">'; inRootList = true }
        html += `<li>${md(content)}</li>`
      }
      continue
    }
    if (orderedItemOpen) {
      closeNestedList()
      html += `<div class="md-para">${md(trimmed)}</div>`
      continue
    }
    closeAllLists()
    html += `<div class="md-para">${md(trimmed)}</div>`
  }
  closeAllLists()
  return html
}

function md(text) {
  return text
    // `inline code`
    .replace(/`([^`]+)`/g, '<code class="md-code">$1</code>')
    // **粗体**
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    // *斜体*（排除已被粗体消耗的 *）
    .replace(/(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>')
}

/**
 * 构建发送给后端的对话历史内容
 * 关键设计：对于带卡片数据的 assistant 消息，拼接精简的站点/预约摘要（含 ID），
 * 让 LLM 能理解"第二个"、"帮我预约那个"等上下文指代。
 * 对于纯文本消息，截断到 200 字防止 Token 膨胀。
 */
function buildHistoryContent(m) {
  if (m.role !== 'assistant') return m.content || ''

  // 有站点卡片数据 → 拼接精简摘要（含 ID，供 LLM 做指代消解）
  if (m.cardType === 'stations' && m.cardData?.length) {
    const summary = m.cardData.map((s, i) =>
      `${i + 1}. id=${s.id} ${s['名称'] || s.name || ''} 可用桩${s['当前可用桩数'] ?? '?'}/${s['总桩数'] ?? '?'}`
    ).join('\n')
    return (m.content || '').slice(0, 100) + '\n[站点数据]\n' + summary
  }

  // 站点详情卡片
  if (m.cardType === 'station_detail' && m.cardData) {
    const d = m.cardData
    return `[站点详情] id=${d.id} ${d['名称'] || ''} 可用桩${d['当前可用桩数'] ?? '?'}/${d['总桩数'] ?? '?'}`
  }

  // 可用车位卡片 → 拼接带 spot_id 的摘要，让 LLM 能通过"第一个"找到对应 spot_id
  if (m.cardType === 'available_spots' && m.cardData?.length) {
    const summary = m.cardData.map((s, i) =>
      `${i + 1}. spot_id=${s.spot_id} ${s['车位编号'] || ''} ${s['充电类型'] || s['类型'] || ''} ${s['每小时价格']}元/h`
    ).join('\n')
    return (m.content || '').slice(0, 100) + '\n[可用车位]\n' + summary
  }

  // AI 推荐预约卡片 — 携带 station_id 和 spot_id 供 LLM 理解上下文
  if (m.cardType === 'recommend_booking' && m.cardData) {
    return `[推荐预约] station_id=${m.cardData.station_id} spot_id=${m.cardData.spot_id} ${m.cardData.station_name} ${m.cardData.spot_code} ${m.cardData.start_time}~${m.cardData.end_time} 已引导用户前往确认页面`
  }

  if (m.cardType === 'reservation_pending' && m.cardData) {
    const chargingType = m.cardData.charging_type ? ` charging_type=${m.cardData.charging_type}` : ''
    return `[待确认预约] confirm_token=${m.cardData.confirm_token || ''} station_id=${m.cardData.station_id} spot_id=${m.cardData.spot_id} ${m.cardData.station_name} ${m.cardData.spot_code} ${m.cardData.start_time}~${m.cardData.end_time}${chargingType}`
  }

  // 预约列表卡片
  if (m.cardType === 'reservations' && m.cardData?.length) {
    const list = m.cardData.map(r => `${r.station_name} ${r.spot_code} ${r.status}`).join(', ')
    return `[预约记录] ` + list
  }

  // 纯文本：截断到 200 字
  return (m.content || '').slice(0, 200)
}

async function sendMessage() {
  const question = inputText.value.trim()
  if (!question || isLoading.value) return

  messages.value.push({ role: 'user', content: question })
  inputText.value = ''
  isLoading.value = true
  scrollToBottom()

  try {
    // 构建对话历史：assistant 回复需携带关键上下文（如站点ID），让 LLM 能理解"第二个"等指代
    const history = messages.value.slice(-20).map(m => ({
      role: m.role,
      content: buildHistoryContent(m)
    }))

    const body = { question }
    if (userLocation.value) {
      body.lat = userLocation.value.latitude
      body.lng = userLocation.value.longitude
    }
    body.history = history

    const response = await api.post('/ai/agent', body, { timeout: 65000 })
    const json = response.data
    isLoading.value = false

    if (json.code === 200 && json.data) {
      const data = json.data
      messages.value.push({
        role: 'assistant',
        content: typeof data === 'string' ? data : (data.content || ''),
        cardType: typeof data === 'object' && data.type !== 'text' ? data.type : null,
        cardData: typeof data === 'object' ? data.data : null
      })
    } else {
      const answer = typeof json.data === 'string' ? json.data : (json.message || '暂无回复')
      messages.value.push({ role: 'assistant', content: answer })
    }
  } catch (error) {
    console.error('AI 对话失败:', error)
    isLoading.value = false
    messages.value.push({
      role: 'assistant',
      content: extractErrorMessage(error, '抱歉，智充助手暂时无法响应，请稍后再试。')
    })
  } finally {
    scrollToBottom()
  }
}

watch(messages, scrollToBottom, { deep: true })
</script>

<style scoped>
/* Popup mode (default) */
.chat-window {
  position: fixed;
  bottom: 72px;
  right: 20px;
  z-index: 9999;
  height: 520px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* Fullscreen mode */
.chat-fullscreen {
  position: fixed !important;
  inset: 0 !important;
  z-index: 9999;
  height: 100% !important;
  border-radius: 0 !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  background: linear-gradient(135deg, #00b894, #00a884);
  flex-shrink: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f5f6fa;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}

/* Fullscreen messages wider */
.chat-fullscreen .chat-messages {
  padding: 24px;
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
}

.chat-fullscreen .message-content {
  max-width: 65%;
}

.chat-messages::-webkit-scrollbar { width: 4px; }
.chat-messages::-webkit-scrollbar-thumb { background: #ccc; border-radius: 4px; }

.message {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  animation: fadeInUp 0.3s ease;
}
.message.user { flex-direction: row-reverse; }

.message-content {
  max-width: 70%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-bubble {
  max-width: 100%;
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 0.95rem;
  line-height: 1.6;
  word-break: break-word;
}

.assistant-bubble {
  background: white;
  color: #2d3436;
  border-bottom-left-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}
.assistant-bubble :deep(.md-h2) {
  font-size: 0.95em;
  font-weight: 700;
  margin: 10px 0 4px;
  color: #2d3436;
}
.assistant-bubble :deep(.md-h3) {
  font-size: 0.9em;
  font-weight: 600;
  margin: 8px 0 4px;
  color: #636e72;
}
.assistant-bubble :deep(.md-para) {
  margin: 2px 0;
  line-height: 1.65;
}
.assistant-bubble :deep(.md-blank) {
  height: 6px;
}
.assistant-bubble :deep(.md-ol) {
  margin: 4px 0;
  padding-left: 20px;
}
.assistant-bubble :deep(.md-ul) {
  margin: 4px 0;
  padding-left: 18px;
  list-style: none;
}
.assistant-bubble :deep(.md-ul li)::before {
  content: '•';
  color: #00b894;
  font-weight: bold;
  margin-right: 6px;
}
.assistant-bubble :deep(.md-ul-nested) {
  margin: 2px 0;
  padding-left: 16px;
  list-style: none;
}
.assistant-bubble :deep(.md-ul-nested li)::before {
  content: '◦';
  color: #0984e3;
  margin-right: 6px;
}
.assistant-bubble :deep(.md-ol li) {
  margin: 2px 0;
  line-height: 1.65;
  padding-left: 2px;
}
.assistant-bubble :deep(.md-ul li) {
  margin: 2px 0;
  line-height: 1.65;
}
.assistant-bubble :deep(.md-code) {
  background: #f0f0f0;
  color: #e17055;
  padding: 1px 5px;
  border-radius: 3px;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 0.85em;
}
.assistant-bubble :deep(strong) {
  color: #2d3436;
  font-weight: 600;
}
.assistant-bubble :deep(em) {
  color: #636e72;
  font-style: italic;
}

.user-bubble {
  background: linear-gradient(135deg, #00b894, #00cec9);
  color: white;
  border-bottom-right-radius: 4px;
}

.typing {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 12px 18px !important;
}

.dot {
  width: 8px;
  height: 8px;
  background: #00b894;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out;
}
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }

/* 卡片样式 */
.card-container {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: 100%;
}

.station-card {
  background: #fafbfc;
  border-radius: 10px;
  padding: 10px 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  cursor: pointer;
  transition: all 0.2s;
  border-left: 3px solid #00b894;
}
.station-card:hover {
  box-shadow: 0 3px 12px rgba(0,0,0,0.10);
  background: #f5f7f9;
  transform: translateY(-1px);
}

.spot-selectable {
  cursor: pointer;
  transition: all 0.2s;
}
.spot-selectable:hover {
  border-left-color: #0984e3 !important;
  background: #f0f7ff;
  box-shadow: 0 3px 12px rgba(0,0,0,0.12);
}

/* Popup slide animation */
.chat-slide-enter-active, .chat-slide-leave-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}
.chat-slide-enter-from, .chat-slide-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}

/* Fullscreen fade animation */
.chat-fade-enter-active, .chat-fade-leave-active {
  transition: all 0.25s ease;
}
.chat-fade-enter-from, .chat-fade-leave-to {
  opacity: 0;
}

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 480px) {
  .message-content { max-width: 85%; }
  .chat-messages { padding: 12px; }
}
</style>
